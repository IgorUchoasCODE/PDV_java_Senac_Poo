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

            // 12. Teste End-to-End do Compilador de Encurtadores ($$) via Java Stream
            String sessaoDsl = "sessao-dsl-teste-" + System.currentTimeMillis();
            roteador.pessoas().cadastrarPessoaSimples("Igor da Silva Uchoa", true, true, null, new BigDecimal("5000.00"));
            roteador.pessoas().cadastrarFornecedorPessoaJuridica("Granja Marinho", null);

            String respBuscaPessoaSigla = agente.processarMensagem(sessaoDsl, "$$ R= #Pessoa nm> >isu; R= #Pessoa nm> i.s.u; R= #Produto nm> >otba;");
            afirmar(respBuscaPessoaSigla.contains("Igor da Silva Uchoa"), "Busca por sigla '>isu' e 'i.s.u' localizou Igor da Silva Uchoa.");
            afirmar(respBuscaPessoaSigla.contains("Ovos Branco Tipo A Especial"), "Busca por sigla '>otba' localizou Ovos Branco Tipo A Especial (sem sufixo numérico de teste).");

            String nomeProdA = "Caixa de Ovos DSL " + System.currentTimeMillis();
            String cmdCompraDsl = "$$ C= #Produto nm> " + nomeProdA + ", um> UN, vv> 250.00; "
                    + "C= #NotaCompra nm> Granja Marinho, >dt <4, "
                    + "++ #Produto[ ++ nm> >oba, >vl 20, q> 10; ++ nm> " + nomeProdA + ", vc> 200, q> 5; ] "
                    + "++ #Pagamento[ ++ dinheiro, 1000, dt> <3; ++ pix, 800, hoje; ++ pix, 800, ontem; ];";

            String respCompilacao = agente.processarMensagem(sessaoDsl, cmdCompraDsl);
            afirmar(respCompilacao.contains("OP-1") && respCompilacao.contains("OP-2"), "Compilador $$ gerou OP-1 e OP-2 na Bandeja de Contexto.");
            afirmar(respCompilacao.contains("Subtotal acum.: R$ 1200"), "Compilador $$ calculou o subtotal acumulado dos itens (200 + 1000 = 1200).");

            // Edição cirúrgica em memória (U= id> OP-1) e aborto seletivo / execução
            String respEdicaoMem = agente.processarMensagem(sessaoDsl, "$$ U= id> OP-1, vv> 260.00;");
            afirmar(respEdicaoMem.contains("260.00"), "Instrução OP-1 editada em memória de execução antes do commit.");

            String respExecDsl = agente.processarMensagem(sessaoDsl, "SIM");
            afirmar(respExecDsl.contains("SUCESSO"), "Bloco $$ de criação de produto + NotaCompra com pagamentos executado em lote após SIM!");

            // Regra de bloqueio de exclusão de produto com estoque > 0 e abatimento com perda isenta
            String sessaoDel = "sessao-del-dsl-" + System.currentTimeMillis();
            String respBloqueioDel = agente.processarMensagem(sessaoDel, "$$ D= #Produto id> " + prodAtualizado.getId() + ";");
            afirmar(respBloqueioDel.contains("Exclusão bloqueada preventivamente"), "Bloqueou exclusão direta de produto com estoque > 0.");

            String respAbatePerda = agente.processarMensagem(sessaoDel,
                    "$$ D= #Produto id> " + prodAtualizado.getId() + ", -- #NotaPerda[ q> total, resp> isento, mot> Descarte ];");
            afirmar(respAbatePerda.contains("Fornecedor Isento de Ressarcimento"), "Agendou baixa de Nota de Perda isentando fornecedor antes da exclusão.");
            agente.processarMensagem(sessaoDel, "$$ close::");
            afirmar(!agente.obterBandeja(sessaoDel).temItens(), "Comando close:: limpou toda a bandeja de contexto.");

            // 13. Teste de Sintaxe com Vírgula ($$ C= #Produto, nm> Ovo, vl> 20, um> UN), Consultas Paralelas sem Fechar Bandeja,
            //     Busca por Sigla Fora de $$ e Failover Multi-IA Transparente
            String sessaoAvancada = "sessao-avancada-" + System.currentTimeMillis();
            String traducaoIA = br.com.senac.integracao.ia.ContextoIABuilder.traduzirDslParaLinguagemNatural("$$ C= #Produto, nm> Ovo, vl> 20, um> UN");
            afirmar(traducaoIA.contains("Ovo") && traducaoIA.contains("20") && traducaoIA.contains("UN"),
                    "ContextoIABuilder traduziu comando $$ para leitura de IA: " + traducaoIA);

            String nomeOvoUnico = "Ovo Caipira Extra " + System.currentTimeMillis();
            agente.processarMensagem(sessaoAvancada, "$$ C= #Produto, nm> " + nomeOvoUnico + ", vl> 20, um> UN;");
            afirmar(agente.obterBandeja(sessaoAvancada).totalItens() == 1, "C= #Produto, (com vírgula) retido em espera na bandeja (OP-1).");

            // Executa consulta R= via $$ em Virtual Thread enquanto OP-1 está esperando confirmação -> NÃO deve fechar a bandeja!
            String respListaProdutos = agente.processarMensagem(sessaoAvancada, "$$ R= #Produto;");
            afirmar(respListaProdutos.contains("Produtos no catálogo") && respListaProdutos.contains("#1"),
                    "$$ R= #Produto; listou os produtos diretamente de forma humanizada.");
            afirmar(agente.obterBandeja(sessaoAvancada).totalItens() == 1,
                    "Consulta paralela $$ R= #Produto; NÃO fechou a instrução OP-1 que estava em espera na bandeja!");

            // Busca por sigla FORA de $$ (ex: "procura prodito o.b.a" e ">oba") enquanto OP-1 continua esperando na bandeja
            String respSiglaForaDsl = agente.processarMensagem(sessaoAvancada, "procura prodito o.b.a");
            afirmar(respSiglaForaDsl.contains("Ovos") || respSiglaForaDsl.contains("Ovo"),
                    "Busca por sigla fora de $$ ('procura prodito o.b.a') localizou o produto em Virtual Thread.");
            String respSetaForaDsl = agente.processarMensagem(sessaoAvancada, ">oba");
            afirmar(respSetaForaDsl.contains("Ovos") || respSetaForaDsl.contains("Ovo"),
                    "Busca direta por sigla '>oba' fora de $$ localizou o produto.");
            afirmar(agente.obterBandeja(sessaoAvancada).totalItens() == 1,
                    "Buscas por sigla fora de $$ mantiveram OP-1 intacta em espera na bandeja!");
            agente.fecharJanelaContexto(sessaoAvancada);

            // Teste do ProvedorIAComFailover (IA 1 falha -> IA 2 assume automaticamente sem modificar nada)
            br.com.senac.integracao.ia.ProvedorIA iaFalha = new br.com.senac.integracao.ia.ProvedorIA() {
                @Override public String getId() { return "ia-primaria-offline"; }
                @Override public String getDescricao() { return "IA Primaria Offline"; }
                @Override public boolean suportaMidia() { return true; }
                @Override public String verificarPronto() { return null; }
                @Override public RespostaIA conversar(PedidoIA pedido) throws Exception {
                    throw new java.io.IOException("Timeout / Quota Exceeded na IA 1");
                }
            };
            br.com.senac.integracao.ia.ProvedorIA iaBackup = new br.com.senac.integracao.ia.ProvedorIA() {
                @Override public String getId() { return "ia-secundaria-ativa"; }
                @Override public String getDescricao() { return "IA Secundaria Ativa"; }
                @Override public boolean suportaMidia() { return true; }
                @Override public String verificarPronto() { return null; }
                @Override public RespostaIA conversar(PedidoIA pedido) {
                    return new RespostaIA("Respondido pela IA 2 apos failover: " + pedido.mensagem(), List.of());
                }
            };
            br.com.senac.integracao.ia.ProvedorIAComFailover failover = new br.com.senac.integracao.ia.ProvedorIAComFailover(List.of(iaFalha, iaBackup));
            var respFailover = failover.conversar(new br.com.senac.integracao.ia.ProvedorIA.PedidoIA("$$ C= #Produto, nm> Ovo, vl> 20, um> UN", null, null));
            afirmar("ia-secundaria-ativa".equals(failover.getId()) && respFailover.texto().contains("Leitura interpretada da instrução"),
                    "ProvedorIAComFailover assumiu automaticamente com a 2ª IA após falha da 1ª e traduziu o comando $$!");

            // 14. Testes Complexos de CRUD $$, Tratamento de Erros (.properties), Busca Sequencial/Cascata (Records + String)
            //     para Nota -> Fornecedor/Cliente -> Contatos -> Produtos -> Pagamentos e Mapeamento JavaFX (abrir>janela>Registrar>nota)
            String sessaoSeq = "sessao-sequencial-" + System.currentTimeMillis();

            // 14.1 Validação de erros formatados a partir de ia-contexto.properties
            String errOpInvalida = agente.processarMensagem(sessaoSeq, "$$ X= #Produto nm> Teste;");
            afirmar((errOpInvalida.contains("nao reconhecida") || errOpInvalida.contains("não reconhecida")) && errOpInvalida.contains("C="),
                    "Erro de operação inválida formatado via ia-contexto.properties: " + errOpInvalida);

            String errEntidadeInvalida = agente.processarMensagem(sessaoSeq, "$$ C= #EntidadeFantasma nm> Teste;");
            afirmar(errEntidadeInvalida.contains("ENTIDADEFANTASMA") && errEntidadeInvalida.contains("Entidades suportadas"),
                    "Erro de entidade desconhecida formatado via ia-contexto.properties: " + errEntidadeInvalida);

            String errCampoObrigatorio = agente.processarMensagem(sessaoSeq, "$$ C= #Produto vv> 100.00;");
            afirmar(errCampoObrigatorio.contains("campo obrigatorio") && errCampoObrigatorio.contains("nm"),
                    "Erro de campo obrigatório ausente formatado via ia-contexto.properties: " + errCampoObrigatorio);

            String errValorInvalido = agente.processarMensagem(sessaoSeq, "$$ C= #Produto nm> Teclado Teste, vv> PRECO_ERRADO;");
            afirmar(errValorInvalido.contains("PRECO_ERRADO") && errValorInvalido.contains("invalido"),
                    "Erro de valor numérico inválido formatado via ia-contexto.properties: " + errValorInvalido);

            String errNotaInexistente = agente.processarMensagem(sessaoSeq, "$$ R= #Nota id> 999999;");
            afirmar(errNotaInexistente.contains("999999") && (errNotaInexistente.contains("nao encontrada") || errNotaInexistente.contains("não encontrada")),
                    "Erro de nota inexistente formatado via ia-contexto.properties: " + errNotaInexistente);

            // 14.2 CRUD Complexo em lote (Fornecedor + Contatos + Produtos + NotaCompra com múltiplos itens e pagamentos)
            long sufixoCrud = System.currentTimeMillis() % 100000;
            String nmFornSeq = "Fornecedora Global Tech " + sufixoCrud;
            String nmProdSeq1 = "Processador Industrial Ryzen " + sufixoCrud;
            String nmProdSeq2 = "Placa Controladora CNC " + sufixoCrud;

            Pessoa fornCriado = roteador.pessoas().cadastrarPessoaSimples(nmFornSeq, false, true, "12.345.678/0001-99", new BigDecimal("50000.00"));
            roteador.pessoas().adicionarDocumentoRegistro(fornCriado.getId(), "WHATSAPP", "(11) 98888-7777");
            roteador.pessoas().adicionarDocumentoRegistro(fornCriado.getId(), "EMAIL", "contato@globaltech.com.br");

            // Emite NotaCompra via $$ com os 2 produtos e 2 pagamentos (PIX + BOLETO)
            String cmdCrudNota = "$$ "
                    + "C= #ProdutoFisico nm> " + nmProdSeq1 + ", um> UN, vv> 1250.00; "
                    + "C= #ProdutoFisico nm> " + nmProdSeq2 + ", um> UN, vv> 890.00; "
                    + "C= #NotaCompra id> " + fornCriado.getId() + ", nm> " + nmFornSeq + ", dt> hoje, "
                    + "++ #Produto[ nm> " + nmProdSeq1 + ", q> 4, vc> 800.00, vv> 1250.00 ], "
                    + "++ #Produto[ nm> " + nmProdSeq2 + ", q> 2, vc> 500.00, vv> 890.00 ], "
                    + "++ #Pagamento[ PIX, 2200.00, hoje ], "
                    + "++ #Pagamento[ DINHEIRO, 2000.00, hoje ];";
            String respPreviaCrud = agente.processarMensagem(sessaoSeq, cmdCrudNota);
            afirmar(respPreviaCrud.contains("OP-1") && respPreviaCrud.contains("OP-3"),
                    "Compilou lote CRUD complexo (2 Produtos + 1 NotaCompra com 2 itens e 2 pagamentos) na bandeja.");

            // Testa comando de janela JavaFX (inclusive com erro de digitação 'Registarr') antes do commit para validar pré-carregamento da bandeja!
            String respJanelaNota = agente.processarMensagem(sessaoSeq, "abrir>janela>Registarr>nota");
            afirmar(respJanelaNota.contains("JANELA_REGISTRAR_NOTA") && respJanelaNota.contains("NotaFiscalForm.fxml")
                            && respJanelaNota.contains("Pré-carregada com 3 registro(s)"),
                    "Comando 'abrir>janela>Registarr>nota' mapeou a janela JavaFX e pré-carregou os itens da bandeja: " + respJanelaNota);

            // Confirma e grava a NotaCompra no banco
            String respCommitNota = agente.processarMensagem(sessaoSeq, "SIM");
            afirmar(respCommitNota.contains("SUCESSO"), "NotaCompra complexa gravada com sucesso no banco.");

            List<br.com.senac.repository.modal.nota.Nota> todasNotas = roteador.notas().listarTodasNotas();
            br.com.senac.repository.modal.nota.Nota ultimaNota = todasNotas.stream()
                    .max(java.util.Comparator.comparing(br.com.senac.repository.modal.nota.Nota::getId))
                    .orElseThrow();
            Long idNotaGerada = ultimaNota.getId();

            // 14.3 Busca em Sequência (Passo a Passo):
            // Passo 1: Busca a Nota -> coloca a Nota em foco na sessão e gera RetornoNotaDetalhada (Java Record)
            String passo1Nota = agente.processarMensagem(sessaoSeq, "$$ R= #Nota id> " + idNotaGerada + ";");
            afirmar(passo1Nota.contains("Nota #" + idNotaGerada) && passo1Nota.contains(fornCriado.getNome()) && passo1Nota.contains("4200.00"),
                    "Passo 1 (Busca Sequencial - Nota): Retornou dados tratados da Nota #" + idNotaGerada + ".");

            // Passo 2: Pede "agora o fornecedor dessa nota" (em linguagem natural E via $$) -> usa o foco da Nota!
            String passo2Fornecedor = agente.processarMensagem(sessaoSeq, "agora o fornecedor dessa nota");
            afirmar(passo2Fornecedor.contains(fornCriado.getNome()) && passo2Fornecedor.contains("Vinculado à Nota #" + idNotaGerada),
                    "Passo 2 (Busca Sequencial - Fornecedor dessa Nota): Identificou o fornecedor da Nota em foco: " + passo2Fornecedor);

            // Passo 3: Pede "os contatos dessa pessoa" -> usa o foco do Fornecedor recém-consultado!
            String passo3Contatos = agente.processarMensagem(sessaoSeq, "os contatos dessa pessoa");
            afirmar(passo3Contatos.contains("(11) 98888-7777") && passo3Contatos.contains("contato@globaltech.com.br"),
                    "Passo 3 (Busca Sequencial - Contatos do Fornecedor dessa Nota): Retornou WhatsApp, E-mail e CNPJ: " + passo3Contatos);

            // Passo 4: Pede "os produto dessaa nota" -> usa o foco da Nota para listar todos os itens com quantidades e subtotais!
            String passo4Produtos = agente.processarMensagem(sessaoSeq, "os produto dessaa nota");
            afirmar(passo4Produtos.contains("Processador Industrial Ryzen") && passo4Produtos.contains("Placa Controladora CNC") && passo4Produtos.contains("3200.00"),
                    "Passo 4 (Busca Sequencial - Produtos dessa Nota): Retornou os produtos e lotes da Nota #" + idNotaGerada + ".");

            // Passo 5: Pede "os pagamentos dessa nota" -> usa o foco da Nota para listar formas, valores e vencimentos!
            String passo5Pagamentos = agente.processarMensagem(sessaoSeq, "os pagamentos dessa nota");
            afirmar(passo5Pagamentos.contains("PIX") && passo5Pagamentos.contains("2200.00") && passo5Pagamentos.contains("2000.00"),
                    "Passo 5 (Busca Sequencial - Pagamentos dessa Nota): Retornou os pagamentos detalhados da Nota #" + idNotaGerada + ".");

            // Verifica se os Java Records estruturados (RetornoChatBotUI) foram gerados e armazenados na sessão para a UI JavaFX
            var focoSessao = agente.obterFocoSessao(sessaoSeq);
            var recordsGerados = focoSessao.getUltimosRecordsGerados();
            boolean temRecordNota = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.RetornoNotaDetalhada);
            boolean temRecordPessoa = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.RetornoPessoaDetalhada);
            boolean temRecordContatos = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.RetornoContatosPessoa);
            boolean temRecordProdutos = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.RetornoProdutosDaNota);
            boolean temRecordPagamentos = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.RetornoPagamentosDaNota);
            boolean temRecordJanela = recordsGerados.stream().anyMatch(r -> r instanceof br.com.senac.integracao.ia.RetornoChatBotUI.ComandoJanelaUI);
            afirmar(temRecordNota && temRecordPessoa && temRecordContatos && temRecordProdutos && temRecordPagamentos && temRecordJanela,
                    "Todos os 6 tipos de Java Records (Nota, Pessoa/Fornecedor, Contatos, ProdutosDaNota, PagamentosDaNota e ComandoJanelaUI) foram gerados para a interface JavaFX!");

            // 14.4 Consultas Sequenciais em Cascata aproveitando a consulta de cima ($$ R= #Nota; R= #Fornecedor; R= #Contato; R= #Produto; R= #Pagamento;)
            String sessaoCascataSeq = "sessao-cascata-cima-" + System.currentTimeMillis();
            String respSequencialCima = agente.processarMensagem(sessaoCascataSeq,
                    "$$ R= #Nota id> " + idNotaGerada + "; R= #Fornecedor; R= #Contato; R= #Produto; R= #Pagamento;");
            afirmar(respSequencialCima.contains("Nota #" + idNotaGerada)
                            && respSequencialCima.contains("Vinculado à Nota #" + idNotaGerada)
                            && respSequencialCima.contains("(11) 98888-7777")
                            && respSequencialCima.contains("Produtos da Nota #" + idNotaGerada)
                            && respSequencialCima.contains("Pagamentos da Nota #" + idNotaGerada),
                    "Consultas sequenciais em cascata (R= #Nota; R= #Fornecedor; R= #Contato; R= #Produto; R= #Pagamento;) aproveitaram a consulta de cima sem repetir ID!");

            // 14.5 Busca em Cascata de Produto (>>): Produto >> Estoque >> Fornecedor >> Nota
            Produto prodCriado1 = roteador.produtos().buscarPorNome(nmProdSeq1);
            String respCascataProd = agente.processarMensagem(sessaoSeq,
                    "$$ R= #Produto id> " + prodCriado1.getId() + " >> #Estoque >> #Fornecedor >> #Nota;");
            afirmar(respCascataProd.contains("Processador Industrial Ryzen") && respCascataProd.contains("Lotes em Estoque") && respCascataProd.contains(fornCriado.getNome()),
                    "Busca em Cascata de Produto ($$ R= #Produto >> #Estoque >> #Fornecedor >> #Nota) retornou lotes FIFO, fornecedor e notas!");

            // 14.6 Regra de Concorrência: Consulta pode consultar dados trancados, mas não pode mudar seu estado (U= / D=)
            String sessaoDonaLock = "sessao-lock-dona-" + System.currentTimeMillis();
            String sessaoOutra = "sessao-concorrente-" + System.currentTimeMillis();
            agente.processarMensagem(sessaoDonaLock, "$$ U= #Produto id> " + prodCriado1.getId() + ", vv> 1399.00;");

            // Outra sessão PODE consultar (R=) o registro trancado normalmente
            String respConsultaTrancado = agente.processarMensagem(sessaoOutra, "$$ R= #Produto id> " + prodCriado1.getId() + ";");
            afirmar(respConsultaTrancado.contains("Processador Industrial Ryzen") && respConsultaTrancado.contains("Consulta liberada"),
                    "Consulta (R=) pode consultar dados trancados normalmente sem bloqueio: " + respConsultaTrancado);

            // Outra sessão NÃO PODE mudar o estado (U= ou D=) do registro trancado
            String respMutacaoBloqueada = agente.processarMensagem(sessaoOutra, "$$ U= #Produto id> " + prodCriado1.getId() + ", vv> 999.00;");
            afirmar(respMutacaoBloqueada.contains("Operacao bloqueada") && respMutacaoBloqueada.contains("apenas consultas R= sao permitidas"),
                    "Tentativa de mudar o estado (U=) de registro trancado por outra sessão foi bloqueada: " + respMutacaoBloqueada);
            agente.fecharJanelaContexto(sessaoDonaLock);

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
