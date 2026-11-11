package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.RelatorioExecucaoBandeja;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Bateria de Testes Automatizados para a Bandeja de Contexto e Janela de Execução em Lote.
 * Valida o ciclo completo:
 * 1. Coleta parcial de parâmetros (PENDENTE_DADOS)
 * 2. Complementação multi-turn (VALIDADO)
 * 3. Acumulação de múltiplas instruções na bandeja
 * 4. Confirmação (SIM) e execução em lote transparente via RoteadorController
 * 5. Encerramento seguro e limpeza da janela de contexto (Clean Slate)
 * 6. Descarte e cancelamento voluntário (NÃO)
 */
public class TesteBandejaContexto {

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   ⚡ INICIANDO TESTES DO SUBSISTEMA: BANDEJA DE CONTEXTO E EXECUÇÃO");
        System.out.println("================================================================================");

        try {
            RoteadorController roteador = new RoteadorController();
            GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
            String sessaoTeste = "sessao-bandeja-teste-" + System.currentTimeMillis();

            // 1. Estado Inicial
            BandejaOperacoesContexto bandeja = agente.obterBandeja(sessaoTeste);
            afirmar(!bandeja.temItens(), "Bandeja inicia vazia.");
            afirmar(!bandeja.temItensValidados(), "Nenhuma instrução validada inicialmente.");
            afirmar(bandeja.isJanelaAberta(), "Janela de contexto aberta.");

            // Garante dados padrão
            roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

            // 2. Cria produto base para testar atualização
            String nomeOvosInicial = "Ovos Caipira Teste " + System.currentTimeMillis();
            var respCriar = roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", nomeOvosInicial,
                    "siglaUm", "UN",
                    "precoVenda", "12.00",
                    "alturaCm", "5.0",
                    "larguraCm", "5.0",
                    "comprimentoCm", "5.0",
                    "pesoBrutoKg", "0.5",
                    "pesoLiquidoKg", "0.4"
            ));
            afirmar(respCriar.sucesso(), "Produto base cadastrado para o teste de atualização: " + nomeOvosInicial);

            // 3. Instrução parcial na Bandeja (Falta novoNome)
            ItemBandeja itemParcial = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_CATALOGO_ATUALIZAR,
                    Map.of("nomeAtual", nomeOvosInicial),
                    "Atualizar dados do produto " + nomeOvosInicial
            );
            afirmar(itemParcial.isPendente(), "Item com dados parciais foi retido como PENDENTE_DADOS.");
            afirmar(itemParcial.getCamposFaltantes().contains("novoNome"), "Detectou campo faltante: novoNome.");
            afirmar(bandeja.temItensPendentes(), "Bandeja possui itens pendentes.");
            afirmar(!bandeja.temItensValidados(), "Bandeja ainda não possui itens validados.");

            // 4. Complementação Multi-turn na Bandeja
            String novoNomeDefinido = "Ovos Branco Tipo A Especial " + System.currentTimeMillis();
            ItemBandeja itemCompleto = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_CATALOGO_ATUALIZAR,
                    Map.of("novoNome", novoNomeDefinido),
                    "Atualizar dados do produto " + nomeOvosInicial + " para " + novoNomeDefinido
            );
            afirmar(itemCompleto.isValido(), "Após fornecer o novoNome, item foi promovido a VALIDADO.");
            afirmar(itemCompleto.getCamposFaltantes().isEmpty(), "Campos faltantes zerados.");
            afirmar(bandeja.temItensValidados(), "Bandeja agora possui instruções validadas.");

            // 5. Adiciona Segunda Instrução Validada na mesma Bandeja (Atualizar Preço)
            ItemBandeja itemPreco = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                    Map.of("nomeProduto", novoNomeDefinido, "novoPreco", "18.50"),
                    "Atualizar preço do produto " + novoNomeDefinido + " para R$ 18.50"
            );
            afirmar(itemPreco.isValido(), "Segunda instrução validada adicionada à bandeja.");
            afirmar(bandeja.totalItens() == 2, "Bandeja acumula 2 instruções validadas.");

            // 6. Visualização formatada da Bandeja
            String visualizacao = bandeja.formatarVisualizacao();
            afirmar(visualizacao.contains("BANDEJA DE CONTEXTO ATIVA"), "Visualização da bandeja contém título oficial.");
            afirmar(visualizacao.contains("VALIDADO"), "Visualização exibe o status VALIDADO das instruções.");
            afirmar(visualizacao.contains("PRODUTO_CATALOGO_ATUALIZAR"), "Visualização lista a rota de atualização.");

            // 7. Confirmação e Execução em Lote da Bandeja
            RelatorioExecucaoBandeja relatorio = bandeja.executarTodasValidadas(roteador);
            afirmar(relatorio.totalOperacoes() == 2, "Relatório confirma 2 operações executadas.");
            afirmar(relatorio.todasComSucesso(), "Todas as 2 operações foram executadas com sucesso.");
            afirmar(relatorio.falhas() == 0, "Zero falhas na execução da bandeja.");

            // 8. Validação Física no Banco de Dados
            Produto prodAtualizado = roteador.produtos().buscarPorNome(novoNomeDefinido);
            afirmar(prodAtualizado != null, "Produto localizado no banco com o novo nome!");
            afirmar(novoNomeDefinido.equalsIgnoreCase(prodAtualizado.getNome()), "Nome do produto foi alterado fisicamente.");

            // 9. Fechamento e Limpeza da Janela de Contexto
            agente.fecharJanelaContexto(sessaoTeste);
            BandejaOperacoesContexto bandejaAposFechamento = agente.obterBandeja(sessaoTeste);
            afirmar(!bandejaAposFechamento.temItens(), "Bandeja está limpa após fecharJanelaContexto.");
            afirmar(!agente.temConfirmacaoPendente(sessaoTeste), "Nenhuma confirmação pendente após fechamento.");

            // 10. Teste de Cancelamento (NÃO)
            String sessaoCancelamento = "sessao-cancel-teste-" + System.currentTimeMillis();
            BandejaOperacoesContexto bandejaCancel = agente.obterBandeja(sessaoCancelamento);
            bandejaCancel.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_FISICO_CADASTRAR,
                    Map.of("nome", "Produto Que Nao Deve Existir", "siglaUm", "UN", "precoVenda", "50.00"),
                    "Cadastro de teste que será cancelado"
            );
            afirmar(bandejaCancel.temItensValidados(), "Instrução aguardando aprovação na bandeja.");
            String respostaCancel = agente.processarMensagem(sessaoCancelamento, "não, pode cancelar");
            afirmar(respostaCancel.contains("cancelada") || respostaCancel.contains("encerrada"), "Agente confirmou o cancelamento.");
            afirmar(!agente.obterBandeja(sessaoCancelamento).temItens(), "Bandeja foi descartada após recusa.");

            // 11. Teste End-to-End da Emissão de Nota de Compra via Parâmetros Raw (Simulação de Tool Calling da IA)
            String sessaoNotaCompra = "sessao-nota-compra-" + System.currentTimeMillis();
            Pessoa fornPadrao = roteador.pessoas().listarFornecedores().stream().findFirst()
                    .orElseGet(() -> roteador.pessoas().cadastrarFornecedorPessoaJuridica("Fornecedor Teste Bandeja", null));

            Map<String, Object> paramsNotaCompra = Map.of(
                    "idFornecedor", fornPadrao.getId(),
                    "descricao", "Compra de ovos para estoque via bandeja",
                    "itens", List.of(Map.of(
                            "idProduto", prodAtualizado.getId(),
                            "quantidade", "120.000000",
                            "valorUnitario", "10.00"
                    ))
            );

            BandejaOperacoesContexto bandejaCompra = agente.obterBandeja(sessaoNotaCompra);
            bandejaCompra.adicionarOuAtualizar(
                    RotaSistema.NOTA_COMPRA_EMITIR,
                    paramsNotaCompra,
                    "Emitir nota fiscal de compra e dar entrada no estoque"
            );
            afirmar(bandejaCompra.temItensValidados(), "Instrução de compra com dados brutos validada na bandeja.");

            String respostaConfirmacaoCompra = agente.processarMensagem(sessaoNotaCompra, "sim");
            afirmar(respostaConfirmacaoCompra.contains("SUCESSO") || respostaConfirmacaoCompra.contains("Nota Fiscal de Compra"),
                    "Nota de compra emitida com sucesso a partir dos parâmetros estruturados da IA.");

            BigDecimal saldoEstoque = roteador.estoque().consultarSaldoTotalProduto(prodAtualizado.getId());
            afirmar(saldoEstoque != null && saldoEstoque.compareTo(new BigDecimal("120")) >= 0,
                    "Estoque físico foi abastecido com 120 unidades da compra!");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DA BANDEJA DE CONTEXTO APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");

        } catch (Throwable t) {
            System.err.println("❌ FALHA NO TESTE DA BANDEJA DE CONTEXTO:");
            t.printStackTrace();
            System.exit(1);
        }
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
