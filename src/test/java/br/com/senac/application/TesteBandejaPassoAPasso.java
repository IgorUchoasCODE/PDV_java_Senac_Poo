package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.bandeja.ComandoBandeja;
import br.com.senac.integracao.ia.bandeja.ComandoBandeja.Acao;
import br.com.senac.integracao.ia.bandeja.ExecutorBandeja;
import br.com.senac.integracao.ia.bandeja.PassoExecutado;
import br.com.senac.integracao.ia.bandeja.PassoExecutado.Resultado;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Testes da execução da bandeja passo a passo (Fase 2):
 * comandos em linguagem simples, executar uma instrução por vez, conferência antes e depois,
 * pular/retomar, parada na primeira falha e fluxo completo pelo agente.
 */
public class TesteBandejaPassoAPasso {

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   INICIANDO TESTES: BANDEJA PASSO A PASSO (CONFERENCIA ANTES E DEPOIS)");
        System.out.println("================================================================================");

        RoteadorController roteador = null;
        String sufixo = String.valueOf(System.currentTimeMillis() % 1_000_000);
        String nomeA = "Passo Produto A " + sufixo;
        String nomeB = "Passo Produto B " + sufixo;
        String nomeC = "Passo Produto C " + sufixo;
        String nomeE = "Passo Produto E " + sufixo;
        String nomeF = "Passo Produto F " + sufixo;
        try {
            roteador = new RoteadorController();
            roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());
            ExecutorBandeja executor = new ExecutorBandeja(roteador);

            testarComandos();

            // ---------- Cenário principal: executar um por um, pular, retomar ----------
            BandejaOperacoesContexto b = new BandejaOperacoesContexto("passo-" + sufixo);
            ItemBandeja op1 = b.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeA, "10.00"), "Cadastrar " + nomeA);
            ItemBandeja op2 = b.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeB, "5.00"), "Cadastrar " + nomeB);
            Map<String, Object> pPreco = new LinkedHashMap<>();
            pPreco.put("nomeProduto", nomeA);
            pPreco.put("novoPreco", "25.50");
            ItemBandeja op3 = b.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, pPreco, "Preco de " + nomeA + " para 25.50");
            afirmar(b.totalItens() == 3 && b.getItensValidados().size() == 3, "Bandeja com 3 instrucoes prontas (OP-1, OP-2, OP-3).");

            PassoExecutado p1 = executor.executarProximo(b);
            afirmar(p1.resultado() == Resultado.EXECUTADO && "OP-1".equals(p1.idItem()), "Proxima executou so a OP-1.");
            afirmar(p1.conferenciaDepois().contains("conferido"), "Conferencia depois do cadastro: " + p1.conferenciaDepois());
            afirmar(roteador.produtos().buscarPorNome(nomeA) != null, "Produto A existe no banco depois da OP-1.");
            afirmar(roteador.produtos().buscarPorNome(nomeB) == null, "Produto B ainda NAO existe (OP-2 nao executou).");
            afirmar(op2.isValido() && op3.isValido(), "OP-2 e OP-3 continuam prontas, nada executou em lote.");

            ItemBandeja pulada = executor.pular(b, "OP-2");
            afirmar(pulada == op2 && op2.isPulado(), "OP-2 foi pulada e continua na bandeja.");

            PassoExecutado p2 = executor.executarProximo(b);
            afirmar(p2.executou() && "OP-3".equals(p2.idItem()), "Proxima percorreu a bandeja e executou a OP-3 (pulou a OP-2).");
            afirmar(p2.conferenciaAntes().contains("10.00"), "Conferencia antes mostrou o preco antigo: " + p2.conferenciaAntes());
            afirmar(p2.conferenciaDepois().contains("25.50"), "Conferencia depois confirmou o preco novo: " + p2.conferenciaDepois());
            Produto aDepois = roteador.produtos().buscarPorNome(nomeA);
            afirmar(aDepois.getPrecoVenda().compareTo(new BigDecimal("25.50")) == 0, "Preco 25.50 gravado no banco.");

            PassoExecutado p3 = executor.executarProximo(b);
            afirmar(p3.resultado() == Resultado.NADA_A_EXECUTAR && p3.restantes() == 1, "Sobrou so a OP-2 (pulada): nada pronto para executar.");

            ItemBandeja retomada = executor.retomar(b, "OP-2");
            afirmar(retomada.isValido(), "OP-2 voltou para a fila.");
            PassoExecutado p4 = executor.executarProximo(b);
            afirmar(p4.executou() && "OP-2".equals(p4.idItem()) && p4.restantes() == 0, "OP-2 executou e a bandeja ficou sem pendencias.");

            // ---------- Conferencia ANTES reprova e impede a gravacao ----------
            BandejaOperacoesContexto b2 = new BandejaOperacoesContexto("passo-conf-" + sufixo);
            Map<String, Object> pInexistente = new LinkedHashMap<>();
            pInexistente.put("nomeProduto", "Produto Fantasma " + sufixo);
            pInexistente.put("novoPreco", "99.00");
            ItemBandeja opFantasma = b2.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, pInexistente, "Preco do fantasma");
            PassoExecutado pf = executor.executarItem(b2, opFantasma);
            afirmar(pf.resultado() == Resultado.NAO_EXECUTADO_CONFERENCIA, "Produto inexistente: conferencia antes reprovou.");
            afirmar(pf.conferenciaAntes().contains("Nao encontrei") || pf.conferenciaAntes().contains("Não encontrei"),
                    "Mensagem clara para o usuario: " + pf.conferenciaAntes());
            afirmar(opFantasma.getStatus() == ItemBandeja.StatusItem.FALHA_EXECUCAO, "Instrucao ficou marcada com erro, nada foi gravado.");

            ItemBandeja opDuplicado = b2.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeA, "1.00"), "Cadastrar de novo " + nomeA);
            PassoExecutado pd = executor.executarItem(b2, opDuplicado);
            afirmar(pd.resultado() == Resultado.NAO_EXECUTADO_CONFERENCIA && pd.conferenciaAntes().contains("Já existe"),
                    "Nome duplicado bloqueado antes de salvar: " + pd.conferenciaAntes());

            ItemBandeja opPendente = b2.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR, Map.of("nomeAtual", nomeA), "Renomear " + nomeA);
            PassoExecutado pp = executor.executarItem(b2, opPendente);
            afirmar(opPendente.isPendente() && pp.resultado() == Resultado.FALTAM_DADOS, "Instrucao com dados faltando nao executa.");

            // ---------- Conjunto (kit): conferencia antes e depois ----------
            Long idA = roteador.produtos().buscarPorNome(nomeA).getId();
            Long idB = roteador.produtos().buscarPorNome(nomeB).getId();
            BandejaOperacoesContexto b3 = new BandejaOperacoesContexto("passo-kit-" + sufixo);
            ItemBandeja kitSiMesmo = b3.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, kit(idA, idA, "1"), "Kit com ele mesmo");
            PassoExecutado pk1 = executor.executarItem(b3, kitSiMesmo);
            afirmar(pk1.resultado() == Resultado.NAO_EXECUTADO_CONFERENCIA && pk1.conferenciaAntes().contains("ele mesmo"),
                    "Conjunto com ele mesmo bloqueado: " + pk1.conferenciaAntes());
            ItemBandeja kitZero = b3.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, kit(idA, idB, "0"), "Kit quantidade zero");
            // mescla no item pendente/anterior? o anterior esta com erro (nao pendente), logo e um item novo
            PassoExecutado pk2 = executor.executarItem(b3, kitZero);
            afirmar(pk2.resultado() == Resultado.NAO_EXECUTADO_CONFERENCIA && pk2.conferenciaAntes().contains("maior que zero"),
                    "Quantidade zero bloqueada: " + pk2.conferenciaAntes());
            ItemBandeja kitOk = b3.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, kit(idA, idB, "2"), "Kit A com 2x B");
            PassoExecutado pk3 = executor.executarItem(b3, kitOk);
            afirmar(pk3.resultado() == Resultado.EXECUTADO && pk3.conferenciaDepois().contains("agora tem o item"),
                    "Item incluido no conjunto e conferido: " + pk3.conferenciaDepois());

            // ---------- executarTodos para na primeira falha ----------
            BandejaOperacoesContexto b4 = new BandejaOperacoesContexto("passo-todos-" + sufixo);
            ItemBandeja t1 = b4.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeC, "3.00"), "Cadastrar " + nomeC);
            Map<String, Object> pFalha = new LinkedHashMap<>();
            pFalha.put("nomeProduto", "Outro Fantasma " + sufixo);
            pFalha.put("novoPreco", "1.00");
            b4.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, pFalha, "Preco de fantasma");
            ItemBandeja t3 = b4.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeE, "4.00"), "Cadastrar " + nomeE);
            List<PassoExecutado> todos = executor.executarTodos(b4);
            afirmar(todos.size() == 2 && todos.get(0).executou() && !todos.get(1).executou(), "Executar todos parou na primeira falha (2 passos).");
            afirmar(t1.getStatus() == ItemBandeja.StatusItem.EXECUTADO_SUCESSO && t3.isValido(), "Instrucao seguinte a falha ficou intacta e pronta.");
            afirmar(roteador.produtos().buscarPorNome(nomeE) == null, "Produto da instrucao depois da falha NAO foi criado.");

            // ---------- Fluxo completo pelo agente (linguagem simples, sem IA) ----------
            GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
            String sessao = "passo-agente-" + sufixo;
            BandejaOperacoesContexto ba = agente.obterBandeja(sessao);
            ba.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeE, "7.00"), "Cadastrar " + nomeE);
            ba.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nomeF, "8.00"), "Cadastrar " + nomeF);

            String r1 = agente.processarMensagem(sessao, "próxima");
            afirmar(r1.contains("Pronto!") && r1.contains("OP-1"), "Agente: 'próxima' executou a OP-1: " + primeiraLinha(r1));
            afirmar(roteador.produtos().buscarPorNome(nomeE) != null && roteador.produtos().buscarPorNome(nomeF) == null,
                    "Agente: so a primeira foi gravada.");
            String r2 = agente.processarMensagem(sessao, "pular OP-2");
            afirmar(r2.contains("Deixei OP-2 para depois"), "Agente: 'pular OP-2' respondeu em linguagem simples.");
            String r3 = agente.processarMensagem(sessao, "executar OP-2");
            afirmar(r3.contains("Pronto!") && r3.contains("OP-2"), "Agente: 'executar OP-2' executou a instrucao pulada.");
            afirmar(r3.contains("tudo foi tratado") && !agente.obterBandeja(sessao).temItens(), "Agente: bandeja fechou sozinha ao acabar.");
            afirmar(roteador.produtos().buscarPorNome(nomeF) != null, "Agente: segundo produto gravado.");

            // ---------- Fase 6: Unicidade Composta (nome + unidade + conjunto) ----------
            testarUnicidadeComposta(roteador, sufixo);

            // ---------- Regressão: Referências OP-N aninhadas em listas/mapas + formato de falha ----------
            testarReferenciasAninhadasEmLoteEPasso(roteador, agente, sufixo);

            // ---------- Regressão: Unidade duplicada, kit digital, OP-N falho e lote por nome ----------
            testarRegressaoCorrecoesLoteEKit(roteador, agente, sufixo);

            // ---------- Limpeza (tambem valida D= com conferencia antes e depois) ----------
            BandejaOperacoesContexto bl = new BandejaOperacoesContexto("passo-limpeza-" + sufixo);
            for (String nome : new String[]{nomeA, nomeB, nomeC, nomeE, nomeF}) {
                Produto p = roteador.produtos().buscarPorNome(nome);
                if (p != null) {
                    ItemBandeja del = bl.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
                            Map.of("idProduto", p.getId()), "Excluir " + nome);
                    PassoExecutado pe = executor.executarItem(bl, del);
                    afirmar(pe.executou() && pe.conferenciaDepois().contains("removido"),
                            "Exclusao de " + nome + " conferida: " + pe.conferenciaDepois());
                    bl.removerItemPorIdOuIndice(del.getIdItem());
                }
            }

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DA BANDEJA PASSO A PASSO APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");
        } catch (Throwable t) {
            System.err.println("FALHA NO TESTE DA BANDEJA PASSO A PASSO:");
            t.printStackTrace();
            System.exit(1);
        } finally {
            JPAUtil.close();
        }
    }

    private static void testarComandos() {
        afirmar(acao("próxima") == Acao.EXECUTAR_PROXIMO, "Comando 'próxima' reconhecido.");
        afirmar(acao("Executar a próxima instrução!") == Acao.EXECUTAR_PROXIMO, "Comando 'Executar a próxima instrução!' reconhecido.");
        afirmar(acao("seguinte") == Acao.EXECUTAR_PROXIMO, "Comando 'seguinte' reconhecido.");
        afirmar("OP-2".equals(alvo("executar OP-2")) && acao("executar OP-2") == Acao.EXECUTAR_ITEM, "Comando 'executar OP-2' aponta OP-2.");
        afirmar("OP-3".equals(alvo("executa 3")), "Comando 'executa 3' vira OP-3.");
        afirmar("OP-1".equals(alvo("executar item 1")), "Comando 'executar item 1' vira OP-1.");
        afirmar(acao("pular op-1") == Acao.PULAR && "OP-1".equals(alvo("pular op-1")), "Comando 'pular op-1' reconhecido.");
        afirmar(acao("pula") == Acao.PULAR && alvo("pula") == null, "Comando 'pula' sem alvo usa a instrucao atual.");
        afirmar(acao("retomar 2") == Acao.RETOMAR, "Comando 'retomar 2' reconhecido.");
        afirmar(acao("voltar") == Acao.ANTERIOR, "Comando 'voltar' reconhecido.");
        afirmar(acao("ver 2") == Acao.IR_PARA, "Comando 'ver 2' reconhecido.");
        afirmar(acao("o que falta") == Acao.VER, "Comando 'o que falta' reconhecido.");
        afirmar(ComandoBandeja.interpretar("executar").isEmpty(), "'executar' sozinho NAO e comando de passo (continua sendo 'confirmar tudo').");
        afirmar(ComandoBandeja.interpretar("sim").isEmpty() && ComandoBandeja.interpretar("nao").isEmpty(), "'sim' e 'nao' seguem o fluxo de confirmacao.");
        afirmar(ComandoBandeja.interpretar("ver nota 1").isEmpty(), "'ver nota 1' nao e confundido com a bandeja.");
        afirmar(ComandoBandeja.interpretar("salvar").isEmpty(), "'salvar' sozinho segue o fluxo de confirmacao.");
    }

    private static void testarUnicidadeComposta(RoteadorController roteador, String sufixo) {
        String nomeBase = "Ovo Granja Teste " + sufixo;

        // 1. Cadastrar produto fisico na unidade UN
        Produto pUn = roteador.produtos().cadastrarProdutoFisico(
                nomeBase, "Ovo caipira granja", new BigDecimal("12.00"), "UN",
                new BigDecimal("5.0"), new BigDecimal("5.0"), new BigDecimal("5.0"),
                new BigDecimal("0.5"), new BigDecimal("0.4"));
        afirmar(pUn != null && pUn.getId() != null, "Unicidade composta: produto cadastrado em UN com sucesso.");

        // 2. Cadastrar MESMO nome na unidade CX (deve permitir porque a unidade e diferente)
        Produto pCx = roteador.produtos().cadastrarProdutoFisico(
                nomeBase, "Ovo caipira caixa", new BigDecimal("120.00"), "CX",
                new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0"),
                new BigDecimal("6.0"), new BigDecimal("5.0"));
        afirmar(pCx != null && pCx.getId() != null && !pCx.getId().equals(pUn.getId()),
                "Unicidade composta: mesmo nome em unidade CX cadastrado sem conflito.");

        // 3. Busca por chave unica deve retornar cada um separadamente
        Produto pBuscaUn = roteador.produtos().buscarPorChaveUnica(nomeBase, "UN", false);
        Produto pBuscaCx = roteador.produtos().buscarPorChaveUnica(nomeBase, "CX", false);
        afirmar(pBuscaUn != null && pBuscaUn.getId().equals(pUn.getId()),
                "Unicidade composta: busca exata (nome + UN + simples) retornou o registro correto.");
        afirmar(pBuscaCx != null && pBuscaCx.getId().equals(pCx.getId()),
                "Unicidade composta: busca exata (nome + CX + simples) retornou o registro correto.");

        // 4. Listar por nome exato deve retornar ambos
        List<Produto> porNome = roteador.produtos().listarPorNomeExato(nomeBase);
        afirmar(porNome.size() == 2, "Unicidade composta: listarPorNomeExato retornou as 2 versoes do produto.");

        // 5. Duplicata exata (mesmo nome, mesma unidade UN, mesmo isConjunto=false) deve ser bloqueada
        boolean bloqueouDuplicata = false;
        try {
            roteador.produtos().cadastrarProdutoFisico(
                    nomeBase, "Duplicata proibida", new BigDecimal("15.00"), "UN",
                    new BigDecimal("5.0"), new BigDecimal("5.0"), new BigDecimal("5.0"),
                    new BigDecimal("0.5"), new BigDecimal("0.4"));
        } catch (IllegalArgumentException e) {
            bloqueouDuplicata = true;
            afirmar(e.getMessage().contains("Já existe o produto"),
                    "Unicidade composta: duplicata exata bloqueada com mensagem clara: " + e.getMessage());
        }
        afirmar(bloqueouDuplicata, "Unicidade composta: tentativa de duplicata exata rejeitada com sucesso.");

        // 6. Limpeza dos registros deste teste
        roteador.produtos().excluirProduto(pUn.getId());
        roteador.produtos().excluirProduto(pCx.getId());
        afirmar(roteador.produtos().buscarPorChaveUnica(nomeBase, "UN", false) == null,
                "Unicidade composta: limpeza de registros concluida.");
    }

    private static void testarReferenciasAninhadasEmLoteEPasso(
            RoteadorController roteador,
            GeminiAgenteOperacional agente,
            String sufixo) {
        String sessaoLote = "passo-lote-op-aninhado-" + sufixo;
        BandejaOperacoesContexto bandejaLote = agente.obterBandeja(sessaoLote);
        String nmForn = "Casa Surpresas " + sufixo;
        String nmComp = "Surpresinha " + sufixo;
        String nmKit = "Caixa Surpresas " + sufixo;

        bandejaLote.adicionarOuAtualizar(
                RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                Map.of("nome", nmForn, "papel", "FORNECEDOR"),
                "Cadastrar fornecedor '" + nmForn + "'"
        );
        bandejaLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", nmComp, "siglaUm", "UN", "precoVenda", "0.00"),
                "Cadastrar produto físico '" + nmComp + "'"
        );
        bandejaLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", nmKit, "siglaUm", "CX", "precoVenda", "0.00"),
                "Cadastrar produto físico '" + nmKit + "'"
        );
        bandejaLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("idProdutoKit", "OP-3", "idProdutoComponente", "OP-2", "quantidade", "12"),
                "Vincular 12x de OP-2 em OP-3"
        );
        bandejaLote.adicionarOuAtualizar(
                RotaSistema.NOTA_COMPRA_EMITIR,
                Map.of(
                        "idFornecedor", "OP-1",
                        "descricao", "Compra lote kit surpresas",
                        "itens", List.of(Map.of(
                                "idProduto", "OP-3",
                                "quantidade", "20",
                                "valorUnitario", "40.00",
                                "dataValidade", "30 dias"
                        ))
                ),
                "Emitir nota fiscal de compra do fornecedor OP-1 (1 item(ns))"
        );

        String respLote = agente.processarMensagem(sessaoLote, "sim");
        afirmar(respLote.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (5 de 5 concluídas)"),
                "Lote completo com OP-3 aninhado em itens executou 5 de 5 com sucesso.");
        Produto kitSalvo = roteador.produtos().buscarPorNome(nmKit);
        afirmar(kitSalvo != null && roteador.estoque().consultarSaldoTotalProduto(kitSalvo.getId()).compareTo(new BigDecimal("20")) == 0,
                "Estoque do kit recem-criado recebeu 20 caixas via NOTA_COMPRA_EMITIR.");

        BandejaOperacoesContexto bandejaPassoOp = new BandejaOperacoesContexto("passo-op-aninhado-" + sufixo);
        bandejaPassoOp.adicionarOuAtualizar(
                RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                Map.of("nome", "Forn Passo " + sufixo, "papel", "FORNECEDOR"),
                "Cadastrar fornecedor passo"
        );
        bandejaPassoOp.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", "Prod Passo " + sufixo, "siglaUm", "UN", "precoVenda", "10.00"),
                "Cadastrar produto passo"
        );
        bandejaPassoOp.adicionarOuAtualizar(
                RotaSistema.NOTA_COMPRA_EMITIR,
                Map.of(
                        "idFornecedor", "OP-1",
                        "itens", List.of(Map.of("idProduto", "OP-2", "quantidade", "5", "valorUnitario", "6.00"))
                ),
                "Compra passo com OP-2 em itens"
        );
        ExecutorBandeja executorPasso = new ExecutorBandeja(roteador);
        List<PassoExecutado> passosExec = executorPasso.executarTodos(bandejaPassoOp);
        afirmar(passosExec.size() == 3 && passosExec.stream().allMatch(PassoExecutado::executou),
                "ExecutorBandeja passo a passo resolveu OP-2 aninhado dentro de itens.");

        String sessaoFalha = "passo-falha-fmt-" + sufixo;
        BandejaOperacoesContexto bandejaFalha = agente.obterBandeja(sessaoFalha);
        bandejaFalha.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", "Prod Sucesso " + sufixo, "siglaUm", "UN", "precoVenda", "5.00"),
                "Cadastrar produto valido"
        );
        bandejaFalha.adicionarOuAtualizar(
                RotaSistema.NOTA_COMPRA_EMITIR,
                Map.of("idFornecedor", "99999999", "itens", List.of(Map.of("idProduto", "99999999", "quantidade", "1"))),
                "Emitir nota com produto inexistente"
        );
        String respFalhaFmt = agente.processarMensagem(sessaoFalha, "sim");
        afirmar(respFalhaFmt.contains("[FALHOU - NÃO EXECUTADA]") && respFalhaFmt.contains("Motivo da falha:"),
                "Relatorio parcial exibe marcador [FALHOU - NÃO EXECUTADA] e Motivo da falha.");
    }

    private static void testarRegressaoCorrecoesLoteEKit(
            RoteadorController roteador,
            GeminiAgenteOperacional agente,
            String sufixo) {
        String base36 = Long.toString(System.currentTimeMillis() % (36L * 36L * 36L), 36).toUpperCase();
        String nomeUmUnico = "UnidadeReg " + sufixo;
        RespostaEndpoint<?> respUmOk = roteador.executarEndpoint(
                RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
                Map.of("sigla", "A" + base36, "nome", nomeUmUnico, "descricao", "Unidade teste regressao", "base", "1")
        );
        afirmar(respUmOk.sucesso(), "Unidade de medida unica cadastrada com sucesso.");

        RespostaEndpoint<?> respUmDup = roteador.executarEndpoint(
                RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
                Map.of("sigla", "B" + base36, "nome", nomeUmUnico, "descricao", "Duplicada por nome", "base", "1")
        );
        afirmar(!respUmDup.sucesso()
                        && respUmDup.mensagem().contains("Já existe uma unidade de medida com o nome")
                        && !respUmDup.mensagem().contains("Duplicate entry"),
                "Unidade com nome duplicado retornou validacao amigavel sem erro SQL bruto: " + respUmDup.mensagem());

        String nmDigital = "DigitalReg " + sufixo;
        String nmCompReg = "CompReg " + sufixo;
        roteador.executarEndpoint(RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
                Map.of("nome", nmDigital, "descricao", "Produto digital de teste", "precoVenda", "10.00"));
        roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, cadastro(nmCompReg, "5.00"));

        RespostaEndpoint<?> respAutoRef = roteador.executarEndpoint(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("nomeKit", nmDigital, "nomeComponente", nmDigital, "quantidade", "1")
        );
        afirmar(!respAutoRef.sucesso() && respAutoRef.mensagem().contains("si mesmo"),
                "Auto-referencia no kit bloqueada com mensagem clara: " + respAutoRef.mensagem());

        String sessaoComboDigital = "passo-combo-digital-" + sufixo;
        BandejaOperacoesContexto bandejaComboDigital = agente.obterBandeja(sessaoComboDigital);
        String nmExcel = "Curso Excel " + sufixo;
        String nmWord = "Curso Word " + sufixo;
        String nmLinux = "Curso Linux " + sufixo;
        String nmCombo = "Combo Cursos Info " + sufixo;
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
                Map.of("nome", nmExcel, "descricao", "Curso de Excel", "precoVenda", "50.00"),
                "Cadastrar produto digital '" + nmExcel + "'"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
                Map.of("nome", nmWord, "descricao", "Curso de Word", "precoVenda", "40.00"),
                "Cadastrar produto digital '" + nmWord + "'"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
                Map.of("nome", nmLinux, "descricao", "Curso de Linux", "precoVenda", "60.00"),
                "Cadastrar produto digital '" + nmLinux + "'"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
                Map.of("nome", nmCombo, "descricao", "Combo de cursos de informatica", "precoVenda", "120.00"),
                "Cadastrar combo digital '" + nmCombo + "'"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("nomeKit", nmCombo, "nomeComponente", nmExcel, "quantidade", "1"),
                "Vincular Excel ao combo digital"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("nomeKit", nmCombo, "nomeComponente", nmWord, "quantidade", "1"),
                "Vincular Word ao combo digital"
        );
        bandejaComboDigital.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("nomeKit", nmCombo, "nomeComponente", nmLinux, "quantidade", "1"),
                "Vincular Linux ao combo digital"
        );
        String respComboDigital = agente.processarMensagem(sessaoComboDigital, "sim");
        Produto comboSalvo = roteador.produtos().buscarPorNome(nmCombo);
        afirmar(respComboDigital.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (7 de 7 concluídas)")
                        && comboSalvo != null && comboSalvo.isDigital() && comboSalvo.isConjunto()
                        && comboSalvo.getItensConjunto() != null && comboSalvo.getItensConjunto().size() == 3,
                "Produto digital configurado como combo/kit com 3 cursos digitais em lote.");

        RespostaEndpoint<?> respVendaCombo = roteador.executarEndpoint(
                RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO,
                Map.of("itens", List.of(Map.of("nomeProduto", nmCombo, "quantidade", "1")))
        );
        NotaVenda nvCombo = respVendaCombo.sucesso() && respVendaCombo.dados() instanceof NotaVenda nv ? nv : null;
        afirmar(nvCombo != null
                        && nvCombo.getItens().size() == 3
                        && nvCombo.getValorTotal().compareTo(new BigDecimal("120.00")) == 0,
                "Venda de kit digital desmembrou nos 3 cursos mantendo valor total de R$ 120,00.");

        String sessaoDepFalha = "passo-dep-falha-" + sufixo;
        BandejaOperacoesContexto bandejaDepFalha = agente.obterBandeja(sessaoDepFalha);
        bandejaDepFalha.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", "ProdUmInvalida " + sufixo, "siglaUm", "XXXX", "precoVenda", "10.00"),
                "Cadastrar produto com UM invalida"
        );
        bandejaDepFalha.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("idProdutoKit", "OP-1", "nomeComponente", nmCompReg, "quantidade", "1"),
                "Vincular componente em OP-1 que falhara"
        );
        String respDepFalha = agente.processarMensagem(sessaoDepFalha, "sim");
        afirmar(!respDepFalha.contains("For input string")
                        && (respDepFalha.contains("Dependência OP-1") || respDepFalha.contains("OP-1")),
                "Dependencia OP-1 falha nao gerou NumberFormatException.");

        String sessaoNomeLote = "passo-nome-lote-" + sufixo;
        BandejaOperacoesContexto bandejaNomeLote = agente.obterBandeja(sessaoNomeLote);
        String nmForn = "FornNome " + sufixo;
        String nmCompNome = "CompNome " + sufixo;
        String nmKitNome = "KitNome " + sufixo;
        bandejaNomeLote.adicionarOuAtualizar(
                RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                Map.of("nome", nmForn, "papel", "FORNECEDOR"),
                "Cadastrar fornecedor '" + nmForn + "'"
        );
        bandejaNomeLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", nmCompNome, "siglaUm", "UN", "precoVenda", "3.50"),
                "Cadastrar componente '" + nmCompNome + "'"
        );
        bandejaNomeLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_FISICO_CADASTRAR,
                Map.of("nome", nmKitNome, "siglaUm", "CX", "precoVenda", "50.00"),
                "Cadastrar kit '" + nmKitNome + "'"
        );
        bandejaNomeLote.adicionarOuAtualizar(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("nomeKit", nmKitNome, "nomeComponente", nmCompNome, "quantidade", "4"),
                "Vincular componente por nome ao kit"
        );
        bandejaNomeLote.adicionarOuAtualizar(
                RotaSistema.NOTA_COMPRA_EMITIR,
                Map.of(
                        "idFornecedor", "OP-1",
                        "descricao", "Compra lote por nomeProduto",
                        "itens", List.of(Map.of("nomeProduto", nmCompNome, "quantidade", "10", "valorUnitario", "2.00"))
                ),
                "Emitir nota de compra usando nomeProduto no mesmo lote"
        );
        String respNomeLote = agente.processarMensagem(sessaoNomeLote, "sim");
        afirmar(respNomeLote.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (5 de 5 concluídas)"),
                "Lote resolveu kit, componente e nota de compra por nome no mesmo lote (5 de 5).");
    }

    private static Acao acao(String texto) {
        return ComandoBandeja.interpretar(texto).map(ComandoBandeja::acao).orElse(null);
    }

    private static String alvo(String texto) {
        return ComandoBandeja.interpretar(texto).map(ComandoBandeja::alvo).orElse(null);
    }

    private static Map<String, Object> cadastro(String nome, String preco) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nome", nome);
        p.put("siglaUm", "UN");
        p.put("precoVenda", preco);
        p.put("alturaCm", "5.0");
        p.put("larguraCm", "5.0");
        p.put("comprimentoCm", "5.0");
        p.put("pesoBrutoKg", "0.5");
        p.put("pesoLiquidoKg", "0.4");
        return p;
    }

    private static Map<String, Object> kit(Long idKit, Long idComp, String qtd) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("idProdutoKit", idKit);
        p.put("idProdutoComponente", idComp);
        p.put("quantidade", qtd);
        return p;
    }

    private static String primeiraLinha(String s) {
        int i = s.indexOf('\n');
        return i > 0 ? s.substring(0, i) : s;
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
