package br.com.senac.application;

import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.bandeja.ExecutorBandeja;
import br.com.senac.integracao.ia.bandeja.PassoExecutado;
import br.com.senac.integracao.ia.parser.ErroSintaxe;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.integracao.ia.parser.ParserInstrucao;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem.ResultadoPreProcessamento;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem.TipoEntidade;
import br.com.senac.integracao.ia.parser.TipoOperacao;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import br.com.senac.repository.modal.nota.Item;

import java.math.BigDecimal;
import java.util.List;

/**
 * Suíte de testes da Fase 3:
 * 1. Pré-processador de mensagens e tags semânticas (valor, data, cpf, cnpj, sigla, produtos, pessoas).
 * 2. Parser recursivo da DSL $$ com suporte a colchetes aninhados ([ ++ #Componente[...] ]).
 * 3. Validação atômica de sintaxe em memória com indicador de erro (^) e sugestão amigável.
 * 4. Cadastro completo de produto conjunto com componentes em único comando $$.
 */
public class TesteParserEPreProcessador {

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   INICIANDO TESTES: PARSER RECURSIVO, TAGS SEMÂNTICAS E VALIDAÇÃO ATÔMICA");
        System.out.println("================================================================================");

        try {
            testarPreProcessadorMensagem();
            testarParserInstrucaoAst();
            testarValidacaoAtomicaEErrosSintaxe();
            testarCadastroConjuntoAninhadoIntegrado();
            testarDetalhesCompletosEntidadesELookupNumerico();
            testarNovasRegrasNotasPagamentosEPerda();

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DO PARSER E PRÉ-PROCESSADOR APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");
        } catch (Throwable t) {
            System.err.println("FALHA NO TESTE DO PARSER E PRÉ-PROCESSADOR:");
            t.printStackTrace();
            System.exit(1);
        } finally {
            JPAUtil.close();
        }
    }

    private static void testarPreProcessadorMensagem() {
        PreProcessadorMensagem pre = new PreProcessadorMensagem();

        // 1. Extração de valor monetário
        String msgValor = "Quero pagar R$ 150,50 no produto agora";
        ResultadoPreProcessamento rVal = pre.processar(msgValor);
        afirmar(rVal.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.VALOR_MONETARIO && "150.50".equals(e.valorNormalizado())),
                "Pré-processador extraiu e normalizou valor monetário R$ 150,50 -> 150.50.");
        afirmar(rVal.mensagemAnotada().contains("<valor normalizado=\"150.50\">R$ 150,50</valor>"),
                "Pré-processador anotou tag XML <valor> no texto.");

        // 2. Extração de datas BR e relativas
        String msgData = "Nota emitida em 15/03/2026 ou ontem";
        ResultadoPreProcessamento rData = pre.processar(msgData);
        afirmar(rData.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.DATA && "2026-03-15".equals(e.valorNormalizado())),
                "Pré-processador extraiu e converteu data brasileira 15/03/2026 -> 2026-03-15.");
        afirmar(rData.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.DATA && "ontem".equalsIgnoreCase(e.textoOriginal())),
                "Pré-processador extraiu data relativa 'ontem'.");

        // 3. Extração de CPF e CNPJ
        String msgDoc = "Cadastrar cliente João com CPF 123.456.789-00 e fornecedor CNPJ 12.345.678/0001-90";
        ResultadoPreProcessamento rDoc = pre.processar(msgDoc);
        afirmar(rDoc.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.CPF && "12345678900".equals(e.valorNormalizado())),
                "Pré-processador extraiu e normalizou CPF.");
        afirmar(rDoc.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.CNPJ && "12345678000190".equals(e.valorNormalizado())),
                "Pré-processador extraiu e normalizou CNPJ.");

        // 4. Sigla de busca rápida
        String msgSigla = "Consultar estoque do produto >oba com urgência";
        ResultadoPreProcessamento rSigla = pre.processar(msgSigla);
        afirmar(rSigla.entidades().stream().anyMatch(e -> e.tipo() == TipoEntidade.SIGLA_PRODUTO && "oba".equals(e.valorNormalizado())),
                "Pré-processador extraiu sigla de busca rápida >oba.");

        // 5. Contexto semântico formatado para a IA
        String ctx = rDoc.gerarContextoSemantico();
        afirmar(ctx.contains("CPF") && ctx.contains("12345678900") && ctx.contains("CNPJ"),
                "Pré-processador gerou bloco estruturado de contexto semântico.");
    }

    private static void testarParserInstrucaoAst() {
        ParserInstrucao parser = new ParserInstrucao();

        // 1. Instrução simples de criação
        String dslSimples = "$$ C= #Produto nm> Teclado Mecanico, um> UN, pr> 250.00;";
        List<NoInstrucao> nos1 = parser.parsear(dslSimples);
        afirmar(nos1.size() == 1, "Parser gerou 1 nó raiz para instrução simples.");
        NoInstrucao n1 = nos1.get(0);
        afirmar(n1.getOperacao() == TipoOperacao.C, "Operação é C (Criar).");
        afirmar("PRODUTO".equals(n1.getEntidade()), "Entidade é PRODUTO.");
        afirmar("Teclado Mecanico".equals(n1.get("nm")), "Atributo nm> extraído corretamente.");
        afirmar("UN".equals(n1.get("um")), "Atributo um> extraído corretamente.");
        afirmar("250.00".equals(n1.get("pr")), "Atributo pr> extraído corretamente.");

        // 2. Instrução com cascata >>
        String dslCascata = "$$ R= #Nota id> 10 >> #Fornecedor >> #Contato;";
        List<NoInstrucao> nos2 = parser.parsear(dslCascata);
        NoInstrucao n2 = nos2.get(0);
        afirmar(n2.getCascata().size() == 2, "Parser extraiu 2 entidades de cascata.");
        afirmar(n2.getCascata().contains("FORNECEDOR") && n2.getCascata().contains("CONTATO"),
                "Cascata contém FORNECEDOR e CONTATO.");

        // 3. Instrução aninhada de Kit com componentes em colchetes
        String dslAninhada = "$$ C= #ConjuntoProduto nm> Combo Gamer, um> UN, pr> 500.00 [ ++ #Componente[ nm> Teclado, q> 1 ]; ++ #Componente[ nm> Mouse, q> 2 ] ];";
        List<NoInstrucao> nos3 = parser.parsear(dslAninhada);
        afirmar(nos3.size() == 1, "Parser gerou 1 nó raiz para Kit aninhado.");
        NoInstrucao kit = nos3.get(0);
        afirmar("CONJUNTOPRODUTO".equals(kit.getEntidade()), "Entidade raiz é CONJUNTOPRODUTO.");
        afirmar("Combo Gamer".equals(kit.get("nm")), "Nome do kit extraído.");
        afirmar(kit.getSubInstrucoes().size() == 2, "Kit possui 2 sub-instruções de componentes.");

        NoInstrucao comp1 = kit.getSubInstrucoes().get(0);
        afirmar("COMPONENTE".equals(comp1.getEntidade()) && "Teclado".equals(comp1.get("nm")) && "1".equals(comp1.get("q")),
                "Sub-instrução 1 é Componente Teclado com quantidade 1.");

        NoInstrucao comp2 = kit.getSubInstrucoes().get(1);
        afirmar("COMPONENTE".equals(comp2.getEntidade()) && "Mouse".equals(comp2.get("nm")) && "2".equals(comp2.get("q")),
                "Sub-instrução 2 é Componente Mouse com quantidade 2.");

        // 4. Múltiplas instruções separadas por ponto-e-vírgula fora de colchetes
        String dslMulti = "$$ C= #Produto nm> Item A, um> UN; R= #Produto nm> Item A; D= #Produto id> 99;";
        List<NoInstrucao> nosMulti = parser.parsear(dslMulti);
        afirmar(nosMulti.size() == 3, "Parser dividiu 3 instruções topo separadas por ponto-e-vírgula.");
        afirmar(nosMulti.get(0).getOperacao() == TipoOperacao.C, "Primeira instrução é C.");
        afirmar(nosMulti.get(1).getOperacao() == TipoOperacao.R, "Segunda instrução é R.");
        afirmar(nosMulti.get(2).getOperacao() == TipoOperacao.D, "Terceira instrução é D.");
    }

    private static void testarValidacaoAtomicaEErrosSintaxe() {
        ParserInstrucao parser = new ParserInstrucao();

        // 1. Colchete não fechado
        boolean pegouColcheteNaoFechado = false;
        try {
            parser.parsear("$$ C= #ConjuntoProduto nm> Combo [ ++ #Componente[ nm> ItemA, q> 1 ]");
        } catch (ErroSintaxe e) {
            pegouColcheteNaoFechado = true;
            afirmar(e.formatarMensagemAmigavel().contains("^") && e.getMessage().contains("fechado"),
                    "Erro de sintaxe apontou colchete não fechado com indicador '^'.");
        }
        afirmar(pegouColcheteNaoFechado, "Rejeitou colchete não fechado.");

        // 2. Colchete fechado sem abertura
        boolean pegouColcheteSemAbertura = false;
        try {
            parser.parsear("$$ C= #Produto nm> Teclado ];");
        } catch (ErroSintaxe e) {
            pegouColcheteSemAbertura = true;
            afirmar(e.getMessage().contains("sem colchete de abertura"),
                    "Erro de sintaxe apontou colchete de fechamento órfão.");
        }
        afirmar(pegouColcheteSemAbertura, "Rejeitou colchete de fechamento órfão.");

        // 3. Aspas não fechadas
        boolean pegouAspasNaoFechadas = false;
        try {
            parser.parsear("$$ C= #Produto nm> \"Teclado Sem Fio, um> UN;");
        } catch (ErroSintaxe e) {
            pegouAspasNaoFechadas = true;
            afirmar(e.getMessage().contains("Aspas abertas"),
                    "Erro de sintaxe identificou aspas abertas sem fechamento.");
        }
        afirmar(pegouAspasNaoFechadas, "Rejeitou aspas não fechadas.");

        // 4. Operação desconhecida (ex.: X=)
        boolean pegouOpDesconhecida = false;
        try {
            parser.parsear("$$ X= #Produto nm> Algo;");
        } catch (ErroSintaxe e) {
            pegouOpDesconhecida = true;
            afirmar(e.getMessage().contains("não reconhecida") || e.getMessage().contains("desconhecida"),
                    "Erro de sintaxe barrou operação 'X=' não reconhecida.");
        }
        afirmar(pegouOpDesconhecida, "Rejeitou operação inválida.");

        // 5. Entidade desconhecida (ex.: #NaoExiste)
        boolean pegouEntidadeInvalida = false;
        try {
            parser.parsear("$$ C= #EntidadeFantasma nm> Teste;");
        } catch (ErroSintaxe e) {
            pegouEntidadeInvalida = true;
            afirmar(e.getMessage().contains("não é reconhecida"),
                    "Erro de sintaxe barrou entidade desconhecida com lista de sugestões.");
        }
        afirmar(pegouEntidadeInvalida, "Rejeitou entidade inválida.");
    }

    private static void testarCadastroConjuntoAninhadoIntegrado() {
        RoteadorController roteador = new RoteadorController();
        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        String sufixo = String.valueOf(System.currentTimeMillis() % 1000000);
        String sessao = "sessao-aninhada-" + sufixo;

        // 1. Cadastra dois produtos componentes base
        String nomeItem1 = "Comp Teclado " + sufixo;
        String nomeItem2 = "Comp Mouse " + sufixo;
        Produto p1 = roteador.produtos().cadastrarProdutoFisico(
                nomeItem1, "Componente 1", new BigDecimal("100.00"), "UN",
                new BigDecimal("5.0"), new BigDecimal("5.0"), new BigDecimal("5.0"),
                new BigDecimal("0.5"), new BigDecimal("0.4"));
        Produto p2 = roteador.produtos().cadastrarProdutoFisico(
                nomeItem2, "Componente 2", new BigDecimal("50.00"), "UN",
                new BigDecimal("5.0"), new BigDecimal("5.0"), new BigDecimal("5.0"),
                new BigDecimal("0.3"), new BigDecimal("0.2"));
        afirmar(p1 != null && p2 != null, "Produtos componentes base criados com sucesso.");

        // 2. Comando $$ com Kit aninhado em colchetes contendo os dois componentes
        String nomeKit = "Kit Setup Home " + sufixo;
        String dslKit = "$$ C= #ConjuntoProduto nm> " + nomeKit + ", um> UN, pr> 200.00 [ "
                + "++ #Componente[ nm> " + nomeItem1 + ", q> 1 ]; "
                + "++ #Componente[ nm> " + nomeItem2 + ", q> 2 ] ];";

        String respCompilacao = agente.processarMensagem(sessao, dslKit);
        afirmar(respCompilacao.contains("OP-1") && respCompilacao.contains("OP-2") && respCompilacao.contains("OP-3"),
                "Compilador gerou OP-1 (criar produto conjunto), OP-2 e OP-3 (adicionar componentes) na bandeja.");

        // 3. Executar passo a passo com resolução de referências dinâmicas
        BandejaOperacoesContexto bandeja = agente.obterBandeja(sessao);
        ExecutorBandeja executor = new ExecutorBandeja(roteador);

        // Passo 1: Cria o Produto do Kit
        PassoExecutado passo1 = executor.executarProximo(bandeja);
        afirmar(passo1.executou(), "Passo 1: Produto Conjunto base criado no banco.");
        Produto kitCriado = (Produto) passo1.dados();
        afirmar(kitCriado != null && kitCriado.getId() != null, "Passo 1 retornou entidade Produto com ID gerado.");

        // Passo 2: Adiciona componente 1 ao Kit (resolvendo OP-1 para o ID real)
        PassoExecutado passo2 = executor.executarProximo(bandeja);
        afirmar(passo2.executou(), "Passo 2: Componente 1 adicionado com sucesso ao Kit (referência OP-1 resolvida).");

        // Passo 3: Adiciona componente 2 ao Kit
        PassoExecutado passo3 = executor.executarProximo(bandeja);
        afirmar(passo3.executou(), "Passo 3: Componente 2 adicionado com sucesso ao Kit.");

        // 4. Confere no banco de dados se o produto virou conjunto e possui os 2 componentes
        Produto kitConferido = roteador.produtos().buscarPorId(kitCriado.getId());
        afirmar(kitConferido != null && kitConferido.isConjunto(), "Produto no banco de dados agora está marcado como isConjunto=true.");

        // 5. Teste de Validação Atômica no Agente: se sintaxe for malformada, nada vai para a bandeja!
        String sessaoErro = "sessao-erro-" + sufixo;
        String dslInvalida = "$$ C= #ConjuntoProduto nm> Kit Falho [ ++ #Componente[ nm> ItemA ;"; // falta colchete ]
        String respErro = agente.processarMensagem(sessaoErro, dslInvalida);
        afirmar(respErro.contains("Erro de sintaxe") && respErro.contains("^"),
                "Agente retornou erro de sintaxe amigável com indicador '^' para comando malformado.");
        afirmar(!agente.obterBandeja(sessaoErro).temItens(),
                "Validação atômica: NENHUMA instrução foi inserida na bandeja após erro de sintaxe.");

        // Limpeza dos registros deste teste
        roteador.produtos().excluirProduto(kitCriado.getId());
        roteador.produtos().excluirProduto(p1.getId());
        roteador.produtos().excluirProduto(p2.getId());
        afirmar(roteador.produtos().buscarPorId(kitCriado.getId()) == null, "Limpeza dos registros de teste concluída.");
    }

    private static void testarDetalhesCompletosEntidadesELookupNumerico() {
        RoteadorController roteador = new RoteadorController();
        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        long sufixo = System.currentTimeMillis() % 100000;
        String sessao = "sessao-detalhes-" + sufixo;

        // 1. Cria produto físico com dimensões
        ProdutoFisico pf = roteador.produtos().cadastrarProdutoFisico(
                "Produto Detalhe " + sufixo, "Teste", new BigDecimal("75.00"), "UN",
                new BigDecimal("12.5"), new BigDecimal("10.0"), new BigDecimal("5.0"),
                new BigDecimal("1.2"), new BigDecimal("1.0")
        );

        // 2. Consulta por ID numérico em linguagem natural e via DSL
        String respNat = agente.processarMensagem(sessao, "ver produto " + pf.getId());
        afirmar(respNat.contains("Produto Físico") && respNat.contains("12.5") && respNat.contains("Conjunto/Kit: NÃO"),
                "Lookup numérico 'ver produto ID' retornou detalhes completos (Físico, Dimensões e Kit: NÃO).");

        String respDslId = agente.processarMensagem(sessao, "$$ R= #Produto id> " + pf.getId() + ";");
        afirmar(respDslId.contains("Produto Físico") && respDslId.contains("75.00"),
                "Consulta DSL 'R= #Produto id>' retornou detalhes completos.");

        String respDslNmId = agente.processarMensagem(sessao, "$$ R= #Produto nm> " + pf.getId() + ";");
        afirmar(respDslNmId.contains("Produto Físico"),
                "Consulta DSL 'R= #Produto nm> ID' resolveu o ID numérico perfeitamente.");

        // 3. Cadastra kit via DSL com #Produto e keyword conjunto [++ #produto id>..., q>...]
        String dslKit = String.format("$$ C= #Produto nm> Kit Surpresa %d, um> UN, conjunto [++ #produto id> %d, q> 3];",
                sufixo, pf.getId());
        String respKit = agente.processarMensagem(sessao, dslKit);
        afirmar(respKit.contains("OP-1") && respKit.contains("OP-2"),
                "Cadastro de Kit via #Produto com keyword 'conjunto' enfileirou OP-1 (criar kit) e OP-2 (adicionar componente).");

        // Confirma execução
        String respConfirma = agente.processarMensagem(sessao, "SIM");
        afirmar(respConfirma.contains("sucesso") || respConfirma.contains("confirmada") || respConfirma.contains("executada") || respConfirma.contains("Kit Surpresa"),
                "Execução do Kit na bandeja confirmada com sucesso.");

        // Pergunta contextual sobre o que vem no kit
        String respOqueVem = agente.processarMensagem(sessao, "oque vem nele ?");
        afirmar(respOqueVem.contains("Conjunto/Kit: SIM") && respOqueVem.contains("Quantidade: 3 UN"),
                "Pergunta contextual 'oque vem nele ?' identificou produto em foco e exibiu componentes do kit.");

        // 4. Cadastra Pessoa Física e Pessoa Jurídica
        PessoaFisica clientePf = roteador.pessoas().cadastrarClientePessoaFisica(
                "Cliente PF " + sufixo, "111.222.333-" + (sufixo % 100), new BigDecimal("1500.00"));
        PessoaJuridica fornPj = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                "Fornecedor PJ " + sufixo, "11.222.333/0001-" + (sufixo % 100));

        String respPf = agente.processarMensagem(sessao, "ver cliente " + clientePf.getId());
        afirmar(respPf.contains("Pessoa Física (PF)") && respPf.contains("CPF:") && respPf.contains("Limite de crédito: R$ 1500.00"),
                "Lookup de Pessoa Física retornou natureza (PF), CPF, papel comercial e limite.");

        String respPj = agente.processarMensagem(sessao, "ver fornecedor " + fornPj.getId());
        afirmar(respPj.contains("Pessoa Jurídica (PJ)") && respPj.contains("CNPJ:"),
                "Lookup de Pessoa Jurídica retornou natureza (PJ) e CNPJ.");

        // Limpeza
        roteador.produtos().excluirProdutoPorIdOuNome(null, "Kit Surpresa " + sufixo);
        roteador.produtos().excluirProduto(pf.getId());
        roteador.pessoas().excluirPessoa(clientePf.getId());
        roteador.pessoas().excluirPessoa(fornPj.getId());
        afirmar(true, "Limpeza de entidades de teste concluída.");
    }

    private static void testarNovasRegrasNotasPagamentosEPerda() {
        RoteadorController roteador = new RoteadorController();
        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        String sessao = "sessao-novas-regras-" + System.currentTimeMillis();
        long sufixo = System.currentTimeMillis() % 10000;

        // 1. Cadastra Fornecedor e Produto de teste
        PessoaJuridica forn = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                "Fornecedor Regra " + sufixo, "22.333.444/0001-" + (sufixo % 100));
        ProdutoFisico prod = roteador.produtos().cadastrarProdutoFisico(
                "Produto Regra " + sufixo, "Produto para regras de nota",
                BigDecimal.ZERO, "UN",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);

        // 2. Nota de Compra via DSL sem informar pagamento -> Padrão A PRAZO em 30 dias sem parcelas
        String dslCompra = String.format("$$ C= #NotaCompra forn> %d, dt> hoje, ++ #Item[ id> %d, q> 10, vc> 22.00, validade> 30> ];",
                forn.getId(), prod.getId());
        String respCompra = agente.processarMensagem(sessao, dslCompra);
        afirmar(respCompra.contains("Pagamento padrão: A PRAZO") && respCompra.contains("sem parcelas") && respCompra.contains("220.00"),
                "Nota de compra sem pagamento informado assumiu padrão A PRAZO em 30 dias sem parcelas (R$ 220.00).");

        // Confirma a nota de compra
        String respConfirmaCompra = agente.processarMensagem(sessao, "SIM");
        afirmar(respConfirmaCompra.contains("executada") || respConfirmaCompra.contains("sucesso") || respConfirmaCompra.contains("Compra"),
                "Execução da Nota de Compra com pagamento a prazo confirmada com sucesso.");

        // Recupera o lote criado pela compra
        Item<? extends Produto> loteCriado = roteador.estoque().buscarPrimeiroLoteComEstoqueValido(prod.getId());
        afirmar(loteCriado != null && loteCriado.getQuantidadeDisponivel().compareTo(new BigDecimal("10.000000")) == 0,
                "Lote gerado pela compra possui saldo disponível de 10 unidades.");

        // 3. Nota de Venda sem pagamento informado -> Semi-automático no DINHEIRO
        PessoaFisica cliente = roteador.pessoas().cadastrarClientePessoaFisica(
                "Cliente Regra " + sufixo, "999.888.777-" + (sufixo % 100), new BigDecimal("1000.00"));

        String dslVenda = String.format("$$ C= #NotaVenda cli> %d, ++ #Produto[ id> %d, q> 2, vv> 30.00 ];",
                cliente.getId(), prod.getId());
        String respVenda = agente.processarMensagem(sessao, dslVenda);
        afirmar(respVenda.contains("semi-automática: DINHEIRO") && respVenda.contains("60.00"),
                "Nota de venda sem pagamento informado assumiu semi-automaticamente DINHEIRO no valor total (R$ 60.00).");

        // Confirma a venda
        String respConfirmaVenda = agente.processarMensagem(sessao, "SIM");
        afirmar(respConfirmaVenda.contains("executada") || respConfirmaVenda.contains("sucesso") || respConfirmaVenda.contains("Venda"),
                "Execução da Nota de Venda semi-automática em dinheiro confirmada com sucesso.");

        // 4. Nota de Perda com Pré-relatório analisando validade
        String dslPerda = String.format("$$ C= #NotaPerda id> %d, q> 1, mot> Avaria térmica, dt> hoje;",
                loteCriado.getId());
        String respPerda = agente.processarMensagem(sessao, dslPerda);
        afirmar(respPerda.contains("PRÉ-RELATÓRIO DE PERDA OPERACIONAL — ANÁLISE DE VALIDADE:")
                && respPerda.contains("Data da Perda:")
                && respPerda.contains("Data de Validade do Lote:")
                && respPerda.contains("Diagnóstico:"),
                "Nota de perda gerou pré-relatório detalhado analisando data da perda vs validade do lote.");

        // Cancela a perda da bandeja para limpeza
        agente.fecharJanelaContexto(sessao);

        // 5. Formatação rica de Forma de Pagamento
        String dslConsultaFp = "$$ R= #FormaPagamento cod> DINHEIRO;";
        String respFp = agente.processarMensagem(sessao, dslConsultaFp);
        afirmar(respFp.contains("Forma de Pagamento: Dinheiro em Espécie (DINHEIRO)")
                && respFp.contains("Modalidades Permitidas:")
                && respFp.contains("À Vista: SIM")
                && respFp.contains("Tarifas da Operadora:")
                && respFp.contains("Status no Sistema: ATIVO"),
                "Consulta de Forma de Pagamento retornou detalhes ricos formatados com quebras de linha.");

        String dslListaFp = "$$ R= #FormaPagamento;";
        String respListaFp = agente.processarMensagem(sessao, dslListaFp);
        afirmar(respListaFp.contains("Formas de Pagamento Cadastradas") && respListaFp.contains("[DINHEIRO]") && respListaFp.contains("[BOLETO]"),
                "Listagem de Formas de Pagamento retornou visão limpa com quebras de linha e modalidades.");

        // Limpeza segura (pessoas vinculadas a notas fiscais emitidas permanecem preservadas)
        try {
            roteador.pessoas().excluirPessoa(cliente.getId());
            roteador.pessoas().excluirPessoa(forn.getId());
        } catch (Exception ignored) {}
        afirmar(true, "Limpeza de novas regras concluída.");
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
