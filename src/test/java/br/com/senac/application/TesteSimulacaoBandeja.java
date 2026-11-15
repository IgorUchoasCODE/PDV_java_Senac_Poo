package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine;
import br.com.senac.integracao.gemini.ConfirmacaoAcaoGatekeeper;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiClientService;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.bandeja.ComandoBandeja;
import br.com.senac.integracao.ia.bandeja.SimuladorBandejaService;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.produto.Produto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes da simulação financeira/estoque de itens na Bandeja de Contexto e da proteção
 * contra confirmações ou cancelamentos acidentais em perguntas e simulações.
 */
public class TesteSimulacaoBandeja {

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   INICIANDO TESTES: PERGUNTAS E SIMULAÇÕES NA BANDEJA DE CONTEXTO");
        System.out.println("================================================================================");

        try {
            testarDecisaoConfirmacaoEComandos();
            testarSimuladorEFluxoAgente();

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DE SIMULAÇÃO NA BANDEJA APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");
        } catch (Throwable t) {
            System.err.println("FALHA NO TESTE DE SIMULAÇÃO NA BANDEJA:");
            t.printStackTrace();
            System.exit(1);
        } finally {
            JPAUtil.close();
        }
    }

    private static void testarDecisaoConfirmacaoEComandos() {
        afirmar(DecisaoConfirmacao.interpretar("sim") == DecisaoConfirmacao.SIM, "'sim' => SIM");
        afirmar(DecisaoConfirmacao.interpretar("simm") == DecisaoConfirmacao.SIM, "'simm' => SIM (tolerância de digitação)");
        afirmar(DecisaoConfirmacao.interpretar("Confirmo, pode apagar") == DecisaoConfirmacao.SIM, "'Confirmo, pode apagar' => SIM");
        afirmar(DecisaoConfirmacao.interpretar("não, pode cancelar") == DecisaoConfirmacao.NAO, "'não, pode cancelar' => NAO");
        afirmar(DecisaoConfirmacao.interpretar("não está ok") == DecisaoConfirmacao.NAO, "'não está ok' => NAO");

        afirmar(DecisaoConfirmacao.interpretar("ao executar esse comando, quanto eu lucraria") == DecisaoConfirmacao.INDEFINIDA,
                "Pergunta hipotética com 'executar' NÃO executa a bandeja (INDEFINIDA)");
        afirmar(DecisaoConfirmacao.interpretar("se eu confirmar isso agora, qual será meu lucro?") == DecisaoConfirmacao.INDEFINIDA,
                "Pergunta com 'confirmar' e '?' NÃO executa a bandeja (INDEFINIDA)");
        afirmar(DecisaoConfirmacao.interpretar("não entendi como fez o desconto") == DecisaoConfirmacao.INDEFINIDA,
                "'não entendi...' NÃO cancela a bandeja (INDEFINIDA)");

        var cmdSim = ComandoBandeja.interpretar("simular bandeja");
        afirmar(cmdSim.isPresent() && cmdSim.get().acao() == ComandoBandeja.Acao.SIMULAR,
                "Comando 'simular bandeja' reconhecido localmente.");
        afirmar(ComandoBandeja.interpretar("simule vendendo 10 combos por dia").isEmpty(),
                "Pergunta com cenário ('10 combos por dia') segue para a IA com o contexto da bandeja.");
    }

    private static void testarSimuladorEFluxoAgente() {
        RoteadorController roteador = new RoteadorController();
        roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

        String sufixo = String.valueOf(System.currentTimeMillis() % 1_000_000);
        String nomeBarra = "Barra de Chocolate 100g Sim " + sufixo;
        var respProd = roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                "nome", nomeBarra,
                "siglaUm", "UN",
                "precoVenda", "0.00",
                "alturaCm", "1.0",
                "larguraCm", "5.0",
                "comprimentoCm", "10.0",
                "pesoBrutoKg", "0.100",
                "pesoLiquidoKg", "0.100"
        ));
        afirmar(respProd.sucesso(), "Produto base cadastrado: " + nomeBarra);
        Produto barra = (Produto) respProd.dados();

        List<String> promptsRecebidos = new ArrayList<>();
        AtomicInteger chamadasIA = new AtomicInteger(0);

        ProvedorIA provedorSimulado = new ProvedorIA() {
            @Override public String getId() { return "mock-sim"; }
            @Override public String getDescricao() { return "Mock Simulador"; }
            @Override public boolean suportaMidia() { return false; }
            @Override public String verificarPronto() { return null; }

            @Override
            public RespostaIA conversar(PedidoIA pedido) {
                promptsRecebidos.add(pedido.mensagem());
                int n = chamadasIA.incrementAndGet();
                // 1ª interação: IA decide consultar o produto antes de responder
                if (n == 1) {
                    return new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_catalogo_buscar", Map.of("termo", nomeBarra))
                    ));
                }
                // 2ª interação (follow-up da leitura): IA responde incluindo até um rodapé repetido para testar deduplicação
                if (n == 2) {
                    return new RespostaIA(
                            "Com custo unitário de R$ 8,00: vendendo avulso a R$ 19,99 o lucro é R$ 11,99/un (R$ 479,60 no lote); "
                                    + "via Combo de 2 barras a R$ 29,99 o lucro é R$ 13,99/combo (R$ 279,80 nos 20 combos).\n\n"
                                    + "BANDEJA DE CONTEXTO ATIVA (5 instruções: 5 validadas, 0 pendente):\nDigite SIM para salvar.",
                            List.of()
                    );
                }
                // 3ª interação: usuário pediu nova venda e perguntou lucro no mesmo turno -> IA emite escrita sem texto
                if (n == 3) {
                    return new RespostaIA("", List.of(
                            new ChamadaFerramenta("nota_venda_emitir_balcao_cliente_padrao", Map.of(
                                    "itens", List.of(Map.of("idProduto", barra.getId(), "quantidade", "2"))
                            ))
                    ));
                }
                // 4ª interação (síntese automática após escrita + pergunta)
                return new RespostaIA("Nessa venda simulada de 2 barras a R$ 19,99, a receita será R$ 39,98 e o lucro bruto R$ 23,98.", List.of());
            }
        };

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional(
                new GeminiClientService(),
                roteador,
                ConfirmacaoAcaoGatekeeper.getInstancia(),
                ConcorrenciaSeguraEngine.getInstancia()
        );
        agente.setProvedor(provedorSimulado);

        String sessao = "sessao-sim-" + sufixo;
        BandejaOperacoesContexto bandeja = agente.obterBandeja(sessao);
        bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                Map.of("idProduto", barra.getId(), "novoPreco", "19.99"),
                "Atualizar preço da barra para R$ 19.99");
        bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", "Combo 2 Barras Sim " + sufixo, "siglaUm", "UN", "precoVenda", "29.99"),
                "Cadastrar Combo 2 Barras");
        bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("idProdutoKit", "OP-2", "idProdutoComponente", barra.getId(), "quantidade", "2"),
                "Vincular 2x barra no combo OP-2");
        bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                Map.of("nome", "Fornecedor Chocolates Sim", "papel", "FORNECEDOR"),
                "Cadastrar fornecedor");
        bandeja.adicionarOuAtualizar(RotaSistema.NOTA_COMPRA_EMITIR,
                Map.of("idFornecedor", "OP-4", "itens", List.of(Map.of(
                        "idProduto", barra.getId(),
                        "quantidade", "40",
                        "valorUnitario", "8.00"
                ))),
                "Nota de compra de 40 barras por R$ 320,00");

        // 1. Verifica cálculo determinístico do SimuladorBandejaService
        String simDireta = SimuladorBandejaService.gerarRelatorioSimulacaoUsuario(bandeja, roteador);
        afirmar(simDireta.contains("19.99") && simDireta.contains("8.00") && simDireta.contains("11.99"),
                "Simulador calculou preço R$ 19.99, custo R$ 8.00 e lucro unitário R$ 11.99 da barra.");
        afirmar(simDireta.contains("16.00") && simDireta.contains("13.99") && simDireta.contains("9.99"),
                "Simulador calculou custo R$ 16.00, lucro R$ 13.99 e desconto R$ 9.99 do combo.");
        afirmar(simDireta.contains("479.60") && simDireta.contains("279.80") && simDireta.contains("20 kits possíveis"),
                "Simulador calculou cenários do lote (R$ 479.60 avulso vs R$ 279.80 em 20 kits).");

        // 2. Comando local "simular bandeja" não consome chamada de IA
        int antesChamadas = chamadasIA.get();
        String respLocal = agente.processarMensagem(sessao, "simular bandeja");
        afirmar(chamadasIA.get() == antesChamadas, "'simular bandeja' resolveu localmente sem chamar a IA.");
        afirmar(respLocal.contains("279.80") && respLocal.contains("BANDEJA DE CONTEXTO ATIVA"),
                "Resposta de 'simular bandeja' trouxe simulação e manteve bandeja ativa.");

        // 3. Pergunta perigosa ("ao executar esse comando, quanto eu lucraria") NÃO executa a bandeja e responde via follow-up
        String respPergunta = agente.processarMensagem(sessao, "ao executar esse comando, quanto eu lucraria");
        afirmar(agente.obterBandeja(sessao).totalItens() == 5,
                "Bandeja preservada com 5 itens após pergunta com palavra 'executar'.");
        afirmar(respPergunta.contains("279,80") && respPergunta.contains("479,60"),
                "Resposta trouxe os lucros simulados após consulta READ_ONLY + follow-up.");
        afirmar(contarOcorrencias(respPergunta, "BANDEJA DE CONTEXTO ATIVA") == 1,
                "Rodapé 'BANDEJA DE CONTEXTO ATIVA' exibido apenas 1 vez (sem duplicação).");
        afirmar(promptsRecebidos.size() >= 2 && promptsRecebidos.get(0).contains("Simulação Financeira")
                        && promptsRecebidos.get(1).contains("Simulação Financeira"),
                "Contexto de simulação foi injetado tanto na chamada inicial quanto no follow-up.");

        // 4. Mensagem mista (ação de venda + pergunta de lucro no mesmo turno) sintetiza a simulação
        String respMista = agente.processarMensagem(sessao, "adicione uma venda de 2 barras e me fale quanto vou lucrar");
        afirmar(agente.obterBandeja(sessao).totalItens() == 6,
                "Nova operação adicionada (OP-6) sem perder itens anteriores.");
        afirmar(respMista.contains("23,98") && contarOcorrencias(respMista, "BANDEJA DE CONTEXTO ATIVA") == 1,
                "Mensagem mista exibiu a síntese do lucro (R$ 23,98) junto da bandeja atualizada.");

        agente.fecharJanelaContexto(sessao);
        roteador.produtos().excluirProduto(barra.getId());
    }

    private static int contarOcorrencias(String texto, String alvo) {
        if (texto == null || alvo == null || alvo.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = texto.indexOf(alvo, idx)) >= 0) {
            count++;
            idx += alvo.length();
        }
        return count;
    }

    private static void afirmar(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            aprovados++;
            System.out.println(" [OK] " + mensagem);
        } else {
            System.err.println(" [FALHA] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }
}
