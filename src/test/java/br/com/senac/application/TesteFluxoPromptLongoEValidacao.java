package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine;
import br.com.senac.integracao.gemini.ConfirmacaoAcaoGatekeeper;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiClientService;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.LogFluxoIAContexto;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.parser.DecompositorPromptLongo;
import br.com.senac.integracao.ia.validacao.ValidadorLoteBandeja;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste de contexto reproduzindo o prompt real de 15 operações do terminal:
 * 1) Evita falso positivo de sigla (> / pontos) em frases longas;
 * 2) Decompõe o prompt longo por ordem de dependência e emite aviso + validação por etapa;
 * 3) Valida antes/durante/após e bloqueia propagação de falhas (ex.: PT/Pacote vs PCT);
 * 4) Confirma execução 15/15 no cenário corrigido, incluindo resolução de fornecedor por nome em idFornecedor.
 */
public class TesteFluxoPromptLongoEValidacao {

    private static final String PROMPT_EXATO_15_OPS =
            "Cadastre a unidade de medida PT (Pacote, base 1). "
            + "Cadastre o fornecedor Atacadão Sabor do Norte com CNPJ 12.345.678/0001-95 "
            + "e o cliente João Pedro Almeida com CPF 111.222.333-44 e limite de crédito de 1500 reais. "
            + "Cadastre a forma de pagamento VOUCHER_FESTA (Voucher Festa) à vista sem juros. "
            + "Cadastre 4 produtos físicos simples: Chocolate Bombom (UN, venda 3,50, 5x5x3 cm, peso bruto 0,05 kg e líquido 0,04 kg), "
            + "Suco de Uva Integral (UN, venda 12,00), Biscoito Amanteigado (PT, venda 8,50) e Café Torrado (PT, venda 18,00); "
            + "e também 1 produto digital chamado Vale-Brinde Festa (venda 10,00). "
            + "Crie agora um kit chamado Cesta Café Especial (CX, venda 95,00) composto por 6 unidades de Chocolate Bombom, "
            + "1 Suco de Uva Integral, 2 pacotes de Biscoito Amanteigado e 1 pacote de Café Torrado. "
            + "Por fim, emita uma nota fiscal de compra do fornecedor Atacadão Sabor do Norte comprando "
            + "100 Chocolate Bombom a 1,80 cada com 60 dias de validade, 30 Suco de Uva Integral a 6,50 cada com 90 dias de validade, "
            + "40 Biscoito Amanteigado a 4,20 cada com 45 dias de validade e 25 Café Torrado a 9,90 cada com 120 dias de validade.";

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   TESTE DE CONTEXTO: PROMPT LONGO (15 OPS), DECOMPOSIÇÃO E VALIDAÇÃO 3 FASES");
        System.out.println("================================================================================");

        try {
            testarDeteccaoSiglaNaoDisparaEmPromptLongo();
            testarLogsDocumentosUnicosEDecisaoPodeCorrigir();
            testarAutoCorrecaoPodeCorrigirComTrayAtiva();
            testarDecomposicaoEPreValidacaoPromptExato();
            testarExecucaoCorrigida15OperacoesComFornecedorPorNome();

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DO FLUXO DE PROMPT LONGO APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");
        } catch (Throwable t) {
            System.err.println("FALHA NO TESTE DO FLUXO DE PROMPT LONGO:");
            t.printStackTrace();
            System.exit(1);
        } finally {
            JPAUtil.close();
        }
    }

    private static void testarDeteccaoSiglaNaoDisparaEmPromptLongo() {
        afirmar(!BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(PROMPT_EXATO_15_OPS),
                "Frase longa com pontos e números NÃO dispara [ATALHO_SIGLA].");
        afirmar(!BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita("Chocolate Bombom a 3.50 reais."),
                "Frase com preço decimal NÃO dispara [ATALHO_SIGLA].");
        afirmar(BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(">oba"), "Atalho '>oba' reconhecido.");
        afirmar(BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita("i.s.u"), "Atalho 'i.s.u' reconhecido.");
        afirmar(BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita("o.b.a"), "Atalho 'o.b.a' reconhecido.");
        afirmar(BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita("i s u"), "Atalho 'i s u' reconhecido.");
    }

    private static void testarDecomposicaoEPreValidacaoPromptExato() {
        RoteadorController roteador = new RoteadorController();
        roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

        var decomp = DecompositorPromptLongo.decompor(PROMPT_EXATO_15_OPS);
        afirmar(decomp.promptLongo(), "Decompositor classificou o prompt exato de 15 operações como longo.");
        afirmar(decomp.etapas().size() == 5,
                "Decompositor dividiu o prompt exato em 5 etapas ordenadas por dependência.");
        afirmar(decomp.etapas().get(0).camada() == DecompositorPromptLongo.CamadaExecucao.BASE_ESTRUTURAL
                        && decomp.etapas().get(4).camada() == DecompositorPromptLongo.CamadaExecucao.NOTAS_E_MOVIMENTACOES,
                "Etapas seguem ordem estrutural -> pessoas -> produtos -> kits -> notas.");

        String sufixo = String.valueOf(System.currentTimeMillis() % 1_000_000);
        String voucherCodigo = "VOUCHER_FESTA_" + sufixo;
        String choc = "Chocolate Bombom Ctx " + sufixo;
        String suco = "Suco Uva Ctx " + sufixo;
        String bisc = "Biscoito PT Ctx " + sufixo;
        String cafe = "Cafe PT Ctx " + sufixo;
        String dig = "Vale Brinde Ctx " + sufixo;
        String kit = "Cesta Cafe Ctx " + sufixo;
        String fornecedor = "Atacadão Sabor Norte " + sufixo;
        String cnpjUnico = gerarCnpjNumerico(sufixo);
        String cpfUnico = gerarCpfNumerico(sufixo);

        List<String> promptsEnviados = new ArrayList<>();
        AtomicInteger chamadas = new AtomicInteger(0);

        ProvedorIA provedorEtapas = new ProvedorIA() {
            @Override public String getId() { return "mock-longo-falha"; }
            @Override public String getDescricao() { return "Mock Prompt Longo Conflito"; }
            @Override public boolean suportaMidia() { return false; }
            @Override public String verificarPronto() { return null; }

            @Override
            public RespostaIA conversar(PedidoIA pedido) {
                promptsEnviados.add(pedido.mensagem());
                int n = chamadas.incrementAndGet();
                return switch (n) {
                    case 1 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_unidade_cadastrar", Map.of(
                                    "sigla", "PT", "nome", "Pacote", "base", "1")),
                            new ChamadaFerramenta("pagamento_forma_cadastrar", Map.of(
                                    "codigo", voucherCodigo, "nome", "Voucher Festa Ctx " + sufixo, "descricao", "Voucher Festa à vista sem juros"))
                    ));
                    case 2 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("pessoa_fornecedor_pj_cadastrar", Map.of(
                                    "razaoSocial", fornecedor, "cnpj", cnpjUnico)),
                            new ChamadaFerramenta("pessoa_cliente_pf_cadastrar", Map.of(
                                    "nome", "João Pedro Almeida " + sufixo, "cpf", cpfUnico, "limiteCredito", "1500"))
                    ));
                    case 3 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", choc, "siglaUm", "UN", "precoVenda", "3.50")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", suco, "siglaUm", "UN", "precoVenda", "12.00")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", bisc, "siglaUm", "PT", "precoVenda", "8.50")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", cafe, "siglaUm", "PT", "precoVenda", "18.00")),
                            new ChamadaFerramenta("produto_digital_cadastrar", Map.of(
                                    "nome", dig, "precoVenda", "10.00"))
                    ));
                    case 4 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", kit, "siglaUm", "CX", "precoVenda", "95.00")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", choc, "quantidade", "6")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", suco, "quantidade", "1")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", bisc, "quantidade", "2")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", cafe, "quantidade", "1"))
                    ));
                    case 5 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("nota_compra_emitir", Map.of(
                                    "idFornecedor", fornecedor,
                                    "itens", List.of(
                                            Map.of("nomeProduto", choc, "quantidade", "100", "valorUnitario", "1.80", "validade", "60d"),
                                            Map.of("nomeProduto", suco, "quantidade", "30", "valorUnitario", "6.50", "validade", "90d"),
                                            Map.of("nomeProduto", bisc, "quantidade", "40", "valorUnitario", "4.20", "validade", "45d"),
                                            Map.of("nomeProduto", cafe, "quantidade", "25", "valorUnitario", "9.90", "validade", "120d")
                                    )
                            ))
                    ));
                    default -> new RespostaIA("AUDITORIA RECEBIDA: Há conflito na OP-1 (Pacote já existe como PCT), bloqueando OP-7, OP-8, OP-13, OP-14 e OP-15.", List.of());
                };
            }
        };

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional(
                new GeminiClientService(),
                roteador,
                ConfirmacaoAcaoGatekeeper.getInstancia(),
                ConcorrenciaSeguraEngine.getInstancia()
        );
        agente.setProvedor(provedorEtapas);

        String sessao = "sessao-ctx-conflito-" + sufixo;
        String respTurno1 = agente.processarMensagem(sessao, PROMPT_EXATO_15_OPS);

        afirmar(chamadas.get() == 5, "Prompt longo disparou exatamente 5 chamadas ordenadas (1 por etapa).");
        afirmar(respTurno1.contains("[AVISO PROMPT LONGO]"),
                "Resposta do Turno 1 exibe aviso explícito de prompt longo.");
        afirmar(respTurno1.contains("PRÉ-VALIDAÇÃO:") && respTurno1.contains("OP-1") && respTurno1.contains("PCT"),
                "Pré-validação detectou conflito Pacote vs PCT na OP-1 já no Turno 1.");

        BandejaOperacoesContexto bandeja = agente.obterBandeja(sessao);
        ValidadorLoteBandeja.RelatorioValidacaoLote audit = bandeja.validarLote(roteador);
        afirmar(!audit.todoValido(), "Auditoria do lote reprovou o plano enquanto OP-1 conflita com PCT.");
        afirmar(audit.conflitosPorOp().containsKey("OP-1")
                        && audit.conflitosPorOp().containsKey("OP-7")
                        && audit.conflitosPorOp().containsKey("OP-8")
                        && audit.conflitosPorOp().containsKey("OP-13")
                        && audit.conflitosPorOp().containsKey("OP-14")
                        && audit.conflitosPorOp().containsKey("OP-15"),
                "Auditoria propagou dependências quebradas de OP-1 (PT) -> OP-7/OP-8 -> OP-13/OP-14 -> OP-15. Encontrados: "
                        + audit.conflitosPorOp());

        // Turno 2: pergunta exatamente como o usuário fez
        agente.processarMensagem(sessao, "ja validou se ta tudo correto ?");
        String promptTurno2 = promptsEnviados.get(promptsEnviados.size() - 1);
        afirmar(promptTurno2.contains("AUDITORIA DE PRÉ-VALIDAÇÃO DA BANDEJA") && promptTurno2.contains("REPROVADA"),
                "Turno 2 ('ja validou se ta tudo correto ?') recebeu auditoria real REPROVADA com conflitos.");

        // Se o usuário insistir com SIM apesar dos alertas, falhas em cascata abortam com segurança (sem NumberFormatException)
        String respExecucao = agente.processarMensagem(sessao, "sim");
        afirmar(!respExecucao.contains("For input string"),
                "Execução NÃO sofre erro 'For input string' ao passar nome do fornecedor em idFornecedor.");
        afirmar(respExecucao.contains("Dependência OP-1") || respExecucao.contains("Pacote"),
                "Relatório final explica claramente a falha de dependência originada na OP-1.");
    }

    private static void testarExecucaoCorrigida15OperacoesComFornecedorPorNome() {
        RoteadorController roteador = new RoteadorController();
        roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

        String sufixo = String.valueOf((System.currentTimeMillis() % 1_000_000) + 17);
        String b36 = ("000" + Long.toString(Long.parseLong(sufixo), 36).toUpperCase());
        String siglaUnica = "T" + b36.substring(b36.length() - 3);
        String nomeUnidade = "Fardo Teste " + sufixo;
        String voucherCodigo = "VOUCHER_OK_" + sufixo;
        String fornecedorNome = "Atacadão Sabor do Norte " + sufixo;
        String clienteNome = "João Pedro Almeida " + sufixo;
        String choc = "Chocolate Bombom OK " + sufixo;
        String suco = "Suco Uva OK " + sufixo;
        String bisc = "Biscoito Amanteigado OK " + sufixo;
        String cafe = "Café Torrado OK " + sufixo;
        String dig = "Vale-Brinde Festa OK " + sufixo;
        String kit = "Cesta Café Especial OK " + sufixo;
        String cnpjUnico = gerarCnpjNumerico(sufixo);
        String cpfUnico = gerarCpfNumerico(sufixo);

        String promptCorrigido =
                "Cadastre a unidade de medida " + siglaUnica + " (" + nomeUnidade + ", base 1). "
                + "Cadastre o fornecedor " + fornecedorNome + " com CNPJ " + cnpjUnico
                + " e o cliente " + clienteNome + " com CPF " + cpfUnico + " e limite de crédito de 1500 reais. "
                + "Cadastre a forma de pagamento " + voucherCodigo + " (Voucher Festa OK) à vista sem juros. "
                + "Cadastre 4 produtos físicos simples: " + choc + " (UN, venda 3,50, 5x5x3 cm, peso bruto 0,05 kg e líquido 0,04 kg), "
                + suco + " (UN, venda 12,00), " + bisc + " (" + siglaUnica + ", venda 8,50) e " + cafe + " (" + siglaUnica + ", venda 18,00); "
                + "e também 1 produto digital chamado " + dig + " (venda 10,00). "
                + "Crie agora um kit chamado " + kit + " (CX, venda 95,00) composto por 6 unidades de " + choc + ", "
                + "1 " + suco + ", 2 pacotes de " + bisc + " e 1 pacote de " + cafe + ". "
                + "Por fim, emita uma nota fiscal de compra do fornecedor " + fornecedorNome + " comprando "
                + "100 " + choc + " a 1,80 cada com 60 dias de validade, 30 " + suco + " a 6,50 cada com 90 dias de validade, "
                + "40 " + bisc + " a 4,20 cada com 45 dias de validade e 25 " + cafe + " a 9,90 cada com 120 dias de validade.";

        AtomicInteger chamada = new AtomicInteger(0);
        String finalSigla = siglaUnica;
        ProvedorIA provedorOk = new ProvedorIA() {
            @Override public String getId() { return "mock-longo-ok"; }
            @Override public String getDescricao() { return "Mock Prompt Longo Valido"; }
            @Override public boolean suportaMidia() { return false; }
            @Override public String verificarPronto() { return null; }

            @Override
            public RespostaIA conversar(PedidoIA pedido) {
                int n = chamada.incrementAndGet();
                return switch (n) {
                    case 1 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_unidade_cadastrar", Map.of(
                                    "sigla", finalSigla, "nome", nomeUnidade, "base", "1")),
                            new ChamadaFerramenta("pagamento_forma_cadastrar", Map.of(
                                    "codigo", voucherCodigo, "nome", "Voucher Festa OK " + sufixo, "descricao", "Voucher Festa OK à vista"))
                    ));
                    case 2 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("pessoa_fornecedor_pj_cadastrar", Map.of(
                                    "razaoSocial", fornecedorNome, "cnpj", cnpjUnico)),
                            new ChamadaFerramenta("pessoa_cliente_pf_cadastrar", Map.of(
                                    "nome", clienteNome, "cpf", cpfUnico, "limiteCredito", "1500"))
                    ));
                    case 3 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", choc, "siglaUm", "UN", "precoVenda", "3.50",
                                    "alturaCm", "5", "larguraCm", "5", "comprimentoCm", "3",
                                    "pesoBrutoKg", "0.05", "pesoLiquidoKg", "0.04")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", suco, "siglaUm", "UN", "precoVenda", "12.00")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", bisc, "siglaUm", finalSigla, "precoVenda", "8.50")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", cafe, "siglaUm", finalSigla, "precoVenda", "18.00")),
                            new ChamadaFerramenta("produto_digital_cadastrar", Map.of(
                                    "nome", dig, "precoVenda", "10.00"))
                    ));
                    case 4 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", kit, "siglaUm", "CX", "precoVenda", "95.00")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", choc, "quantidade", "6")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", suco, "quantidade", "1")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", bisc, "quantidade", "2")),
                            new ChamadaFerramenta("produto_kit_adicionar_componente", Map.of(
                                    "nomeKit", kit, "nomeComponente", cafe, "quantidade", "1"))
                    ));
                    case 5 -> new RespostaIA("", List.of(
                            new ChamadaFerramenta("nota_compra_emitir", Map.of(
                                    "idFornecedor", fornecedorNome,
                                    "itens", List.of(
                                            Map.of("nomeProduto", choc, "quantidade", "100", "valorUnitario", "1.80", "validade", "60d"),
                                            Map.of("nomeProduto", suco, "quantidade", "30", "valorUnitario", "6.50", "validade", "90d"),
                                            Map.of("nomeProduto", bisc, "quantidade", "40", "valorUnitario", "4.20", "validade", "45d"),
                                            Map.of("nomeProduto", cafe, "quantidade", "25", "valorUnitario", "9.90", "validade", "120d")
                                    )
                            ))
                    ));
                    default -> new RespostaIA("Ok.", List.of());
                };
            }
        };

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional(
                new GeminiClientService(),
                roteador,
                ConfirmacaoAcaoGatekeeper.getInstancia(),
                ConcorrenciaSeguraEngine.getInstancia()
        );
        agente.setProvedor(provedorOk);

        String sessao = "sessao-ctx-ok-" + sufixo;
        String resp = agente.processarMensagem(sessao, promptCorrigido);
        BandejaOperacoesContexto bandeja = agente.obterBandeja(sessao);

        afirmar(bandeja.totalItens() == 15, "Bandeja recebeu todas as 15 instruções do fluxo completo.");
        ValidadorLoteBandeja.RelatorioValidacaoLote auditOk = bandeja.validarLote(roteador);
        afirmar(auditOk.todoValido(), "Pré-validação aprovou 100% do lote corrigido sem conflitos.");
        afirmar(!resp.contains("PRÉ-VALIDAÇÃO DETECTOU"), "Visualização não exibe alertas de erro quando lote está válido.");

        String respSim = agente.processarMensagem(sessao, "sim");
        afirmar(respSim.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (15 de 15 concluídas)") && !respSim.contains("[FALHOU"),
                "Execução final concluiu todas as 15 operações sem falhas (incluindo fornecedor por nome em idFornecedor).");

        Produto kitBanco = roteador.produtos().buscarPorNome(kit);
        afirmar(kitBanco != null && kitBanco.isConjunto() && kitBanco.getItensConjunto().size() == 4,
                "Kit no banco possui exatamente 4 componentes vinculados.");

        Produto biscBanco = roteador.produtos().buscarPorNome(bisc);
        BigDecimal saldoBisc = biscBanco != null ? roteador.estoque().consultarSaldoTotalProduto(biscBanco.getId()) : BigDecimal.ZERO;
        afirmar(saldoBisc != null && saldoBisc.compareTo(new BigDecimal("40")) == 0,
                "Nota de compra entrou 40 unidades de " + bisc + " no estoque (saldo=" + saldoBisc + ").");

        Produto chocBanco = roteador.produtos().buscarPorNome(choc);
        BigDecimal saldoChoc = chocBanco != null ? roteador.estoque().consultarSaldoTotalProduto(chocBanco.getId()) : BigDecimal.ZERO;
        afirmar(saldoChoc != null && saldoChoc.compareTo(new BigDecimal("100")) == 0,
                "Nota de compra entrou 100 unidades de " + choc + " no estoque (saldo=" + saldoChoc + ").");
    }

    private static void testarLogsDocumentosUnicosEDecisaoPodeCorrigir() throws Exception {
        afirmar(DecisaoConfirmacao.interpretar("pode corrigir") == DecisaoConfirmacao.INDEFINIDA,
                "DecisaoConfirmacao classifica 'pode corrigir' como INDEFINIDA (não aciona SIM).");
        afirmar(DecisaoConfirmacao.interpretar("pode ajustar OP-1") == DecisaoConfirmacao.INDEFINIDA,
                "DecisaoConfirmacao classifica 'pode ajustar OP-1' como INDEFINIDA.");
        afirmar(DecisaoConfirmacao.interpretar("Confirmo, pode apagar") == DecisaoConfirmacao.SIM,
                "DecisaoConfirmacao preserva 'Confirmo, pode apagar' como SIM.");

        Path dirTemp = Files.createTempDirectory("pdv-logs-unicos-");
        try {
            LogFluxoIAContexto log = new LogFluxoIAContexto(dirTemp);
            log.setAtivo(true);

            Path arqA1 = log.iniciarNovoArquivoSessao("terminal-a");
            log.registrarTurno("terminal-a", "oi", "pre", "ia", "pos", "ok");
            log.registrarTurno("terminal-b", "oi b", "pre b", "ia b", "pos b", "ok b");
            Path arqB = log.getArquivoLogAtual("terminal-b");

            afirmar(!arqA1.equals(arqB), "Sessões distintas geram arquivos de log únicos diferentes.");
            afirmar(Files.exists(arqA1) && Files.exists(arqB),
                    "Ambos os arquivos únicos de log existem no disco.");

            log.marcarBandejaFinalizada("terminal-a");
            String rel = log.consumirRelatorioSeBandejaFinalizada("terminal-a");
            afirmar(rel != null && rel.contains(arqA1.getFileName().toString()),
                    "Relatório ao finalizar bandeja referencia o documento único exato do lote.");

            log.registrarTurno("terminal-a", "novo lote", "pre2", "ia2", "pos2", "ok2");
            Path arqA2 = log.getArquivoLogAtual("terminal-a");
            afirmar(!arqA1.equals(arqA2) && Files.exists(arqA2),
                    "Novo lote na mesma sessão gera um novo documento de log único.");
        } finally {
            try (var walk = Files.walk(dirTemp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    private static void testarAutoCorrecaoPodeCorrigirComTrayAtiva() {
        RoteadorController roteador = new RoteadorController();
        roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

        String sufixo = String.valueOf((System.currentTimeMillis() % 100_000) + 33);
        String biscNome = "Biscoito Pode Corrigir " + sufixo;
        String cafeNome = "Café Pode Corrigir " + sufixo;
        String sessao = "sessao-pode-corrigir-" + sufixo;

        AtomicInteger chamada = new AtomicInteger(0);
        ProvedorIA provedorStaging = new ProvedorIA() {
            @Override public String getId() { return "mock-pode-corrigir"; }
            @Override public String getDescricao() { return "Mock Auto Correcao"; }
            @Override public boolean suportaMidia() { return false; }
            @Override public String verificarPronto() { return null; }
            @Override public RespostaIA conversar(PedidoIA pedido) {
                int n = chamada.incrementAndGet();
                if (n == 1) {
                    return new RespostaIA("", List.of(
                            new ChamadaFerramenta("produto_unidade_cadastrar", Map.of("sigla", "PT", "nome", "Pacote")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", biscNome, "descricao", biscNome, "siglaUm", "PT", "precoVenda", "8.50")),
                            new ChamadaFerramenta("produto_fisico_cadastrar", Map.of(
                                    "nome", cafeNome, "descricao", cafeNome, "siglaUm", "PT", "precoVenda", "18.00"))
                    ));
                }
                throw new AssertionError("Não deveria acionar provedor IA no turno 'pode corrigir' (chamada " + n + ").");
            }
        };

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional(
                new GeminiClientService(),
                roteador,
                ConfirmacaoAcaoGatekeeper.getInstancia(),
                ConcorrenciaSeguraEngine.getInstancia()
        );
        agente.setProvedor(provedorStaging);

        agente.processarMensagem(sessao, "Cadastre PT Pacote, Biscoito e Café.");
        BandejaOperacoesContexto bandeja = agente.obterBandeja(sessao);

        afirmar(!bandeja.validarLote(roteador).todoValido(),
                "Antes de 'pode corrigir', lote com PT (Pacote) está reprovado na pré-validação.");

        String respCorrecao = agente.processarMensagem(sessao, "pode corrigir");
        afirmar(!respCorrecao.contains("Bandeja executada"),
                "'pode corrigir' NÃO executou a bandeja prematuramente.");
        afirmar(bandeja.temItens() && bandeja.totalItens() == 2,
                "'pode corrigir' removeu OP-1 redundante mantendo os 2 produtos na bandeja.");
        afirmar(bandeja.validarLote(roteador).todoValido(),
                "Após 'pode corrigir', Biscoito e Café foram ajustados para PCT e o lote passou 100% na pré-validação.");

        String respConfirmacao = agente.processarMensagem(sessao, "sim");
        afirmar(respConfirmacao.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (2 de 2 concluídas)")
                        && !respConfirmacao.contains("[FALHOU"),
                "Confirmação posterior ('sim') executou os 2 produtos corrigidos com 0 falhas. Recebido: " + respConfirmacao);
    }

    private static String gerarCnpjNumerico(String sufixo) {
        String s = ("000000" + sufixo);
        String dig = s.substring(s.length() - 6);
        return "99." + dig.substring(0, 3) + "." + dig.substring(3, 6) + "/0001-95";
    }

    private static String gerarCpfNumerico(String sufixo) {
        String s = ("000000" + sufixo);
        String dig = s.substring(s.length() - 6);
        return "888." + dig.substring(0, 3) + "." + dig.substring(3, 6) + "-44";
    }

    private static void afirmar(boolean condicao, String descricao) {
        totalTestes++;
        if (condicao) {
            aprovados++;
            System.out.println(" [OK] " + descricao);
        } else {
            throw new AssertionError("FALHOU: " + descricao);
        }
    }
}
