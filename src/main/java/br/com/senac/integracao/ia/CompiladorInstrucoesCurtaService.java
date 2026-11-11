package br.com.senac.integracao.ia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.Um;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compilador determinístico baseado em {@link java.util.stream.Stream} e Virtual Threads para a DSL {@code $$}.
 *
 * <p>Regras de execução:
 * <ul>
 *   <li><b>Consultas ({@code R=}):</b> Executadas imediatamente em paralelo via Virtual Threads
 *       ({@link ConcorrenciaSeguraEngine}), retornando os dados formatados sem fechar nem interferir
 *       nas instruções em espera na {@link BandejaOperacoesContexto}.</li>
 *   <li><b>Mutações ({@code C=}, {@code U=}, {@code D=}):</b> Validadas sequencialmente e mantidas em espera
 *       na {@link BandejaOperacoesContexto} até a confirmação explícita ({@code SIM}) ou descarte ({@code NAO} / {@code close::}).</li>
 * </ul>
 */
public class CompiladorInstrucoesCurtaService {

    private static final Pattern PADRAO_CLOSE_ID = Pattern.compile("(?i)close\\s*<\\s*([^>]*)\\s*>");
    private static final Pattern PADRAO_BLOCO_SUB = Pattern.compile("(\\+\\+|--)\\s*#([A-Za-z0-9_]+)\\s*\\[([^\\]]*)\\]");
    private static final Set<String> ENTIDADES_VALIDAS = Set.of(
            "PRODUTO", "PRODUTOFISICO", "PRODUTODIGITAL", "CONJUNTOPRODUTO", "COMPONENTE", "UM",
            "ESTOQUE", "ITEM", "PESSOA", "PESSOAFISICA", "PESSOAJURIDICA", "CLIENTE", "CLIENTES",
            "FORNECEDOR", "FORNECEDORES", "REGISTRO", "CONTATO", "CONTATOS",
            "FORMAPAGAMENTO", "PAGAMENTO", "PAGAMENTOS", "PAGAMENTOAVISTA", "PAGAMENTOAPRAZO",
            "PAGAMENTOPARCELADO", "NOTAPAGAMENTO", "PARCELAPAGAMENTO",
            "NOTA", "NOTAS", "NOTACOMPRA", "NOTAVENDA", "NOTADEVOLUCAO", "NOTAPERDA",
            "VINCULOFATOREXTERNONOTA", "FATOREXTERNO", "ESTATISTICA", "RELATORIO"
    );

    private final RoteadorController roteador;
    private final ConcorrenciaSeguraEngine concorrenciaEngine;
    private final Map<String, RetornoChatBotUI.FocoBuscaSessao> focosPorSessao = new java.util.concurrent.ConcurrentHashMap<>();

    public CompiladorInstrucoesCurtaService(RoteadorController roteador) {
        this(roteador, ConcorrenciaSeguraEngine.getInstancia());
    }

    public CompiladorInstrucoesCurtaService(RoteadorController roteador, ConcorrenciaSeguraEngine concorrenciaEngine) {
        this.roteador = roteador;
        this.concorrenciaEngine = concorrenciaEngine;
    }

    public RetornoChatBotUI.FocoBuscaSessao obterFocoSessao(String idSessao) {
        String ch = (idSessao != null && !idSessao.isBlank()) ? idSessao : "default";
        return focosPorSessao.computeIfAbsent(ch, k -> new RetornoChatBotUI.FocoBuscaSessao());
    }

    /**
     * Verifica se a mensagem enviada pelo usuário inicia com o gatilho {@code $$} do compilador de instruções curtas
     * ou é um comando de navegação de janela {@code abrir>janela>...}.
     */
    public static boolean ehComandoEncurtado(String mensagem) {
        if (mensagem == null) return false;
        String m = mensagem.trim();
        return m.startsWith("$$") || RetornoChatBotUI.ehComandoAbrirJanela(m);
    }

    /**
     * Compila e processa um bloco de instruções iniciado por {@code $$} (ou {@code abrir>janela>...}).
     * As consultas ({@code R=}) rodam em Virtual Threads sem fechar a bandeja;
     * apenas {@code C=}, {@code U=} e {@code D=} ficam aguardando confirmação na bandeja.
     */
    public String compilarEProcessar(String idSessao, String comandoBruto, BandejaOperacoesContexto bandeja, Runnable acaoFecharSessao) {
        String brutoTrim = comandoBruto != null ? comandoBruto.trim() : "";
        String corpo = brutoTrim.startsWith("$$") ? brutoTrim.substring(2).trim() : brutoTrim;
        if (corpo.isEmpty()) {
            return ContextoIABuilder.propriedade("ia.erro.dsl.vazio",
                    "Digite a instrução após $$. Exemplo: $$ C= #Produto, nm> Ovo, vl> 20, um> UN");
        }

        RetornoChatBotUI.FocoBuscaSessao foco = obterFocoSessao(idSessao);

        if (corpo.toLowerCase(Locale.ROOT).contains("close::")) {
            if (acaoFecharSessao != null) {
                acaoFecharSessao.run();
            } else {
                bandeja.fecharJanela();
            }
            return ContextoIABuilder.propriedade("ia.msg.bandeja.limpa",
                    "Todas as instruções em espera foram canceladas e a bandeja foi limpa.");
        }

        List<String> instrucoesTopo = dividirForaDeColchetes(corpo, ';').stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Verifica se há encadeamento contextual de leitura (ex: R= #Nota id> 1; R= #Fornecedor; R= #Produto da_nota;)
        boolean temDependenciaFocoSequencial = instrucoesTopo.size() > 1 && instrucoesTopo.stream().anyMatch(this::dependeDeFocoAnterior);

        Map<Integer, Future<String>> leiturasParalelas = new LinkedHashMap<>();
        Map<Integer, String> resultadosOrdenados = new LinkedHashMap<>();
        boolean teveMutacaoNesteTurno = false;

        for (int i = 0; i < instrucoesTopo.size(); i++) {
            final int idx = i;
            final String inst = instrucoesTopo.get(i);
            if (ehInstrucaoSomenteLeitura(inst)) {
                if (temDependenciaFocoSequencial) {
                    // Executa em Virtual Thread garantindo a passagem do FocoBuscaSessao do passo N para o passo N+1
                    try {
                        resultadosOrdenados.put(idx, concorrenciaEngine.submeterTarefa(
                                () -> processarInstrucaoUnica(inst, bandeja, foco),
                                null
                        ).get());
                    } catch (Exception e) {
                        resultadosOrdenados.put(idx, ContextoIABuilder.propriedade("ia.msg.erro.generico",
                                "Não foi possível concluir a consulta solicitada."));
                    }
                } else {
                    leiturasParalelas.put(idx, concorrenciaEngine.submeterTarefa(
                            () -> processarInstrucaoUnica(inst, bandeja, foco),
                            null // null = leitura livre paralela em Virtual Thread sem lock
                    ));
                }
            } else {
                teveMutacaoNesteTurno = true;
                resultadosOrdenados.put(idx, processarInstrucaoUnica(inst, bandeja, foco));
            }
        }

        // Coleta o resultado das Virtual Threads de leitura paralela
        for (Map.Entry<Integer, Future<String>> entry : leiturasParalelas.entrySet()) {
            try {
                resultadosOrdenados.put(entry.getKey(), entry.getValue().get());
            } catch (Exception e) {
                resultadosOrdenados.put(entry.getKey(), ContextoIABuilder.propriedade("ia.msg.erro.generico",
                        "Não foi possível concluir a consulta solicitada."));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < instrucoesTopo.size(); i++) {
            String res = resultadosOrdenados.get(i);
            if (res != null && !res.isBlank()) {
                sb.append(res).append("\n\n");
            }
        }

        // Exibe a bandeja de espera apenas para C=, U=, D= (leituras não fecham nem poluem a bandeja, mas preservam o que já estava em espera)
        if (bandeja.temItens()) {
            if (teveMutacaoNesteTurno) {
                sb.append(bandeja.formatarVisualizacao());
            } else {
                sb.append(ContextoIABuilder.formatarPropriedade(
                        "ia.msg.bandeja.dica",
                        "Observação: você continua com %d instrução(ões) de cadastro ou alteração aguardando confirmação na bandeja.",
                        bandeja.totalItens()));
            }
        }
        return sb.toString().trim();
    }

    private boolean dependeDeFocoAnterior(String instrucao) {
        String s = instrucao.toLowerCase(Locale.ROOT);
        if (s.contains("da_nota") || s.contains("dessa_nota") || s.contains("da_pessoa") || s.contains("atual")) {
            return true;
        }
        // R= #Fornecedor, R= #Cliente, R= #Contato, R= #Pagamento sem nm> e sem id> usam o foco da Nota/Pessoa anterior!
        if (ehInstrucaoSomenteLeitura(instrucao) && !s.contains("nm>") && !s.contains(">nm") && !s.contains("id>") && !s.contains(">id") && !s.contains("doc>")) {
            return s.contains("#fornecedor") || s.contains("#cliente") || s.contains("#contato") || s.contains("#pagamento") || s.contains("#produto");
        }
        return false;
    }

    private boolean ehInstrucaoSomenteLeitura(String instrucao) {
        String s = instrucao.trim();
        if (RetornoChatBotUI.ehComandoAbrirJanela(s)) return true;
        if (PADRAO_CLOSE_ID.matcher(s).find()) return false;
        Matcher mOp = Pattern.compile("(?i)^([A-Za-z]+)\\s*=").matcher(s);
        if (mOp.find()) {
            return "R".equalsIgnoreCase(mOp.group(1));
        }
        return true;
    }

    private String processarInstrucaoUnica(String instrucao, BandejaOperacoesContexto bandeja, RetornoChatBotUI.FocoBuscaSessao foco) {
        // 0. Verifica comando de abertura de janela JavaFX: abrir>janela>Registrar>nota
        if (RetornoChatBotUI.ehComandoAbrirJanela(instrucao)) {
            RetornoChatBotUI.ComandoJanelaUI cmdJanela = RetornoChatBotUI.resolverComandoJanela(instrucao, bandeja);
            if (cmdJanela != null) {
                foco.registrarRecord(cmdJanela);
                return cmdJanela.formatarTexto();
            }
            return ContextoIABuilder.formatarPropriedade("ia.ui.janela.nao_mapeada",
                    "Janela '%s' não encontrada no catálogo gráfico.", instrucao.trim());
        }

        // 1. Verifica comando de abortar instrução única: close<> ou close<OP-1>
        Matcher mClose = PADRAO_CLOSE_ID.matcher(instrucao);
        if (mClose.find()) {
            String idAlvo = mClose.group(1).trim();
            ItemBandeja removido = bandeja.removerItemPorIdOuIndice(idAlvo);
            if (removido != null) {
                return String.format("Instrução [%s] removida da lista de espera (%s).",
                        removido.getIdItem(), removido.getDescricaoAmigavel());
            }
            return ContextoIABuilder.formatarPropriedade("ia.erro.instrucao.nao_encontrada",
                    "Não encontrei a instrução %s na lista de espera.", idAlvo.isEmpty() ? "recente" : idAlvo);
        }

        // 1.5 Extrai alvos de cascata ">> #Entidade" se presentes (ex: R= #Nota id> 1 >> #Fornecedor >> #Contato >> #Produto >> #Pagamento)
        List<String> alvosCascata = new ArrayList<>();
        String instrucaoSemCascata = instrucao;
        if (instrucao.contains(">>")) {
            String[] partesCascata = instrucao.split(">>");
            instrucaoSemCascata = partesCascata[0].trim();
            for (int c = 1; c < partesCascata.length; c++) {
                String alvoC = partesCascata[c].trim().replaceFirst("^#", "").replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT);
                if (!alvoC.isEmpty()) {
                    alvosCascata.add(alvoC);
                }
            }
        }

        // 2. Extrai blocos de sub-instruções: ++ #Produto[...] ou -- #Perda[...]
        Map<String, List<String>> subBlocosAdicionar = new LinkedHashMap<>();
        Map<String, List<String>> subBlocosRemover = new LinkedHashMap<>();
        Matcher mSub = PADRAO_BLOCO_SUB.matcher(instrucaoSemCascata);
        StringBuffer cabecalhoLimpo = new StringBuffer();
        while (mSub.find()) {
            String opSub = mSub.group(1);
            String entidadeSub = mSub.group(2).toUpperCase(Locale.ROOT);
            String conteudoSub = mSub.group(3).trim();
            List<String> linhasSub = Arrays.stream(conteudoSub.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if ("++".equals(opSub)) {
                subBlocosAdicionar.computeIfAbsent(entidadeSub, k -> new ArrayList<>()).addAll(linhasSub);
            } else {
                subBlocosRemover.computeIfAbsent(entidadeSub, k -> new ArrayList<>()).addAll(linhasSub);
            }
            mSub.appendReplacement(cabecalhoLimpo, " ");
        }
        mSub.appendTail(cabecalhoLimpo);

        String cabecalho = cabecalhoLimpo.toString().trim();

        // 3. Identifica Operação CRUD (C=, R=, U=, D=) e valida operações desconhecidas via .properties
        String operacao = "R";
        Matcher mOpQualquer = Pattern.compile("(?i)^([A-Za-z0-9_]+)\\s*=").matcher(cabecalho);
        if (mOpQualquer.find()) {
            String opBruta = mOpQualquer.group(1).toUpperCase(Locale.ROOT);
            if (!Set.of("C", "R", "U", "D").contains(opBruta)) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.operacao.desconhecida",
                        "Operação '%s' não reconhecida. Use C=, R=, U=, D= ou abrir>janela>...", opBruta);
            }
            operacao = opBruta;
            cabecalho = cabecalho.substring(mOpQualquer.end()).trim();
        }

        // 4. Identifica Entidade Principal (#Entidade), tolerando vírgula logo após (ex: #Produto, nm> Ovo)
        String entidade = "PRODUTO";
        Matcher mEnt = Pattern.compile("#([A-Za-z0-9_]+)\\s*,?").matcher(cabecalho);
        if (mEnt.find()) {
            entidade = mEnt.group(1).toUpperCase(Locale.ROOT);
            cabecalho = (cabecalho.substring(0, mEnt.start()) + " " + cabecalho.substring(mEnt.end())).trim();
            if (!ENTIDADES_VALIDAS.contains(entidade)) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.entidade.desconhecida",
                        "Entidade '#%s' não reconhecida.", entidade);
            }
        }

        // 5. Extrai mapa de atributos (chave> valor) do cabeçalho + marcadores contextuais (da_nota, da_pessoa)
        Map<String, String> attrs = extrairAtributos(cabecalho);
        String cabLower = cabecalho.toLowerCase(Locale.ROOT);
        if (cabLower.contains("da_nota") || cabLower.contains("dessa_nota") || cabLower.contains("nota_atual")) {
            attrs.put("contexto_foco", "da_nota");
        } else if (cabLower.contains("da_pessoa") || cabLower.contains("dessa_pessoa") || cabLower.contains("dele") || cabLower.contains("dela")) {
            attrs.put("contexto_foco", "da_pessoa");
        }

        // 6. Se for U= apontando para um ID de instrução em memória (ex: id> OP-1), edita o item na bandeja!
        String idAttr = obter(attrs, "id", "");
        if ("U".equals(operacao) && idAttr.toUpperCase(Locale.ROOT).startsWith("OP-")) {
            return atualizarInstrucaoEmMemoria(idAttr, attrs, subBlocosAdicionar, subBlocosRemover, bandeja);
        }

        return switch (operacao) {
            case "R" -> processarLeituraComFocoECascata(entidade, attrs, alvosCascata, foco);
            case "C" -> processarCriacao(entidade, attrs, subBlocosAdicionar, subBlocosRemover, bandeja);
            case "U" -> processarAtualizacao(entidade, attrs, subBlocosAdicionar, subBlocosRemover, bandeja);
            case "D" -> processarExclusao(entidade, attrs, subBlocosRemover, bandeja);
            default -> ContextoIABuilder.formatarPropriedade("ia.erro.operacao.desconhecida",
                    "Operação '%s' não reconhecida.", operacao);
        };
    }

    // ==================== LEITURA PARALELA EM VIRTUAL THREAD (R=) + FOCO E CASCATA (>>) ====================

    private static boolean cascataContem(List<String> alvos, String... prefixos) {
        if (alvos == null || alvos.isEmpty()) return false;
        for (String a : alvos) {
            if (a == null) continue;
            String u = a.trim().toUpperCase(Locale.ROOT);
            for (String p : prefixos) {
                if (u.startsWith(p)) return true;
            }
        }
        return false;
    }

    private static boolean validarNumeroReal(String valor) {
        if (valor == null || valor.isBlank()) return false;
        String limpo = valor.replace("R$", "").replace(" ", "").replace(",", ".").trim();
        try {
            new BigDecimal(limpo);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Consulta pode consultar dados trancados (leitura livre), exibindo apenas um aviso informativo de que o
     * registro está em edição/trancado na Bandeja para alterações de estado.
     */
    private static String sufixoAvisoRegistroTrancadoLeitura(String prefixoDao, Long id) {
        if (id == null) return "";
        br.com.senac.repository.dao.PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = br.com.senac.repository.dao.PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo()) {
            return String.format("\n  [Status Concorrência: Registro trancado para alteração de estado pela sessão '%s' — Consulta liberada]",
                    info.idSessaoBandeja());
        }
        return "";
    }

    /**
     * Bloqueia qualquer tentativa de mudar o estado (U= ou D=) de um registro que esteja trancado por outra sessão.
     */
    private static String verificarBloqueioMutacaoOutraSessao(String prefixoDao, Long id, String nomeExibicao, String idSessaoAtual) {
        if (id == null) return null;
        br.com.senac.repository.dao.PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = br.com.senac.repository.dao.PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo() && (idSessaoAtual == null || !info.idSessaoBandeja().equals(idSessaoAtual))) {
            return ContextoIABuilder.formatarPropriedade(
                    "ia.erro.registro.trancado",
                    "Operacao bloqueada: o registro #%s (%s) esta trancado pela sessao '%s' e nao pode ter seu estado alterado agora (apenas consultas R= sao permitidas).",
                    id, nomeExibicao, info.idSessaoBandeja());
        }
        return null;
    }

    public String processarLeituraComFocoECascata(
            String entidade,
            Map<String, String> attrs,
            List<String> alvosCascata,
            RetornoChatBotUI.FocoBuscaSessao foco) {

        String termo = obter(attrs, "nm", obter(attrs, "doc", obter(attrs, "reg", "")));
        Long id = parseLongSafe(obter(attrs, "id", null));
        Long idNotaRef = parseLongSafe(obter(attrs, "id_nota", obter(attrs, "nota", null)));
        String ctxFoco = obter(attrs, "contexto_foco", "");

        // 1. Busca de NOTA (simples, aproveitando a consulta de cima de Pessoa/Nota, ou encadeada)
        if (entidade.startsWith("NOTA") || "VINCULOFATOREXTERNONOTA".equals(entidade)) {
            Long idAlvo = id != null ? id : idNotaRef;
            if (idAlvo == null && termo.isBlank() && "da_nota".equals(ctxFoco) && foco != null && foco.getIdUltimaNota() != null) {
                idAlvo = foco.getIdUltimaNota();
            }
            Nota notaEncontrada = null;
            if (idAlvo != null) {
                notaEncontrada = roteador.notas().buscarNotaPorId(idAlvo);
                if (notaEncontrada == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                            "Nota #%s não encontrada.", idAlvo);
                }
            } else if (!termo.isBlank()) {
                String tLow = termo.toLowerCase(Locale.ROOT);
                List<Nota> todasNotas = roteador.notas().listarTodasNotas();
                for (Nota cand : todasNotas) {
                    String nmP = cand.getPessoa() != null && cand.getPessoa().getNome() != null
                            ? cand.getPessoa().getNome().toLowerCase(Locale.ROOT) : "";
                    String descN = cand.getDescricao() != null ? cand.getDescricao().toLowerCase(Locale.ROOT) : "";
                    if (nmP.contains(tLow) || descN.contains(tLow)) {
                        if (notaEncontrada == null || cand.getId() > notaEncontrada.getId()) {
                            notaEncontrada = cand;
                        }
                    }
                }
                if (notaEncontrada == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                            "Nota '%s' não encontrada.", termo);
                }
            } else if (foco != null && foco.getIdUltimaPessoa() != null && ("da_pessoa".equals(ctxFoco) || foco.getIdUltimaNota() == null)) {
                // Aproveita a Pessoa consultada logo acima para buscar a última nota dela
                Long idPesFoco = foco.getIdUltimaPessoa();
                for (Nota cand : roteador.notas().listarTodasNotas()) {
                    if (cand.getPessoa() != null && Objects.equals(cand.getPessoa().getId(), idPesFoco)) {
                        if (notaEncontrada == null || cand.getId() > notaEncontrada.getId()) {
                            notaEncontrada = cand;
                        }
                    }
                }
            }

            if (notaEncontrada != null) {
                if (foco != null) {
                    foco.registrarNotaEmFoco(notaEncontrada);
                }
                boolean incContatos = alvosCascata.isEmpty()
                        || cascataContem(alvosCascata, "CONTATO", "REGISTRO", "FORNECEDOR", "CLIENTE", "PESSOA");
                boolean incProds = alvosCascata.isEmpty() || cascataContem(alvosCascata, "PRODUTO", "ITEM");
                boolean incPags = alvosCascata.isEmpty() || cascataContem(alvosCascata, "PAGAMENTO");

                RetornoChatBotUI.RetornoNotaDetalhada recNota = RetornoChatBotUI.construirRecordNota(
                        notaEncontrada, roteador, incContatos, incProds, incPags);
                if (foco != null) {
                    foco.registrarRecord(recNota);
                }

                if (!alvosCascata.isEmpty()) {
                    List<RetornoChatBotUI.RetornoFormatavel> encadeados = new ArrayList<>();
                    encadeados.add(recNota);
                    for (String alvo : alvosCascata) {
                        String u = alvo.toUpperCase(Locale.ROOT);
                        if ((u.startsWith("FORNECEDOR") || u.startsWith("CLIENTE") || u.startsWith("PESSOA"))
                                && notaEncontrada.getPessoa() != null) {
                            RetornoChatBotUI.RetornoPessoaDetalhada rp = RetornoChatBotUI.construirRecordPessoa(
                                    notaEncontrada.getPessoa(), notaEncontrada.getId(), roteador, true, false, false);
                            if (foco != null) foco.registrarRecord(rp);
                            encadeados.add(rp);
                        } else if ((u.startsWith("CONTATO") || u.startsWith("REGISTRO"))
                                && notaEncontrada.getPessoa() != null) {
                            RetornoChatBotUI.RetornoContatosPessoa rc = RetornoChatBotUI.construirRecordContatos(
                                    notaEncontrada.getPessoa(), notaEncontrada.getId(), roteador);
                            if (foco != null) foco.registrarRecord(rc);
                            encadeados.add(rc);
                        } else if (u.startsWith("PRODUTO") || u.startsWith("ITEM")) {
                            RetornoChatBotUI.RetornoProdutosDaNota rpr = RetornoChatBotUI.construirRecordProdutosDaNota(notaEncontrada);
                            if (foco != null) foco.registrarRecord(rpr);
                            encadeados.add(rpr);
                        } else if (u.startsWith("PAGAMENTO")) {
                            RetornoChatBotUI.RetornoPagamentosDaNota rpg = RetornoChatBotUI.construirRecordPagamentosDaNota(
                                    notaEncontrada, roteador);
                            if (foco != null) foco.registrarRecord(rpg);
                            encadeados.add(rpg);
                        }
                    }
                    RetornoChatBotUI.ListaRetornosSequenciais seq = new RetornoChatBotUI.ListaRetornosSequenciais(encadeados);
                    return seq.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("NotaDAO", notaEncontrada.getId());
                }

                return recNota.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("NotaDAO", notaEncontrada.getId());
            }
        }

        // 2. Busca de FORNECEDOR / CLIENTE / PESSOA (aproveitando a Nota consultada acima ou busca direta)
        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            boolean semFiltroExplicito = (id == null && termo.isBlank());
            boolean pedirDaNota = "da_nota".equals(ctxFoco)
                    || idNotaRef != null
                    || (semFiltroExplicito && foco != null && foco.getIdUltimaNota() != null);

            if (pedirDaNota) {
                Long idNotaAlvo = idNotaRef != null ? idNotaRef : (foco != null ? foco.getIdUltimaNota() : null);
                if (idNotaAlvo == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                            "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
                }
                Nota notaFoco = roteador.notas().buscarNotaPorId(idNotaAlvo);
                if (notaFoco == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                            "Nota #%s não encontrada.", idNotaAlvo);
                }
                Pessoa p = notaFoco.getPessoa();
                if (p == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                            "Não encontrei pessoa vinculada à Nota #%s.", idNotaAlvo);
                }
                if (foco != null) {
                    foco.registrarPessoaEmFoco(p);
                }
                boolean incContatos = alvosCascata.isEmpty() || cascataContem(alvosCascata, "CONTATO", "REGISTRO");
                boolean incNotas = cascataContem(alvosCascata, "NOTA");
                boolean incPags = cascataContem(alvosCascata, "PAGAMENTO");
                RetornoChatBotUI.RetornoPessoaDetalhada recPes = RetornoChatBotUI.construirRecordPessoa(
                        p, notaFoco.getId(), roteador, incContatos, incNotas, incPags);
                if (foco != null) {
                    foco.registrarRecord(recPes);
                }
                return recPes.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", p.getId());
            }

            if (id != null || !termo.isBlank()) {
                Pessoa p = localizarPessoaUnica(id, termo);
                if (p == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                            "Não encontrei nenhuma pessoa correspondente a '%s'.", id != null ? "#" + id : termo);
                }
                if (foco != null) {
                    foco.registrarPessoaEmFoco(p);
                }
                if (!alvosCascata.isEmpty() || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
                    boolean incContatos = alvosCascata.isEmpty() || cascataContem(alvosCascata, "CONTATO", "REGISTRO");
                    boolean incNotas = alvosCascata.isEmpty() || cascataContem(alvosCascata, "NOTA");
                    boolean incPags = cascataContem(alvosCascata, "PAGAMENTO");
                    RetornoChatBotUI.RetornoPessoaDetalhada recPes = RetornoChatBotUI.construirRecordPessoa(
                            p, null, roteador, incContatos, incNotas, incPags);
                    if (foco != null) {
                        foco.registrarRecord(recPes);
                    }
                    return recPes.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", p.getId());
                }
            }
        }

        // 3. Busca de CONTATOS / REGISTROS (aproveitando a Pessoa/Fornecedor/Cliente consultada acima ou direto)
        if ("CONTATO".equals(entidade) || "CONTATOS".equals(entidade) || "REGISTRO".equals(entidade)) {
            String docBusca = obter(attrs, "doc", obter(attrs, "reg", ""));
            if ("REGISTRO".equals(entidade) && !docBusca.isBlank() && !attrs.containsKey("pes") && !attrs.containsKey("id_pes")) {
                Pessoa pDoc = roteador.pessoas().buscarPessoaPorRegistroUnico(obter(attrs, "tp", null), docBusca);
                if (pDoc != null) {
                    if (foco != null) {
                        foco.registrarPessoaEmFoco(pDoc);
                        foco.registrarRecord(RetornoChatBotUI.construirRecordContatos(pDoc, foco.getIdUltimaNota(), roteador));
                    }
                    return String.format("Documento %s pertence a #%d - %s.%s",
                            docBusca, pDoc.getId(), limparNomeExibicao(pDoc.getNome(), null),
                            sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", pDoc.getId()));
                }
                return "Nenhuma pessoa encontrada para o documento " + docBusca + ".";
            }

            Long idPesBusca = parseLongSafe(obter(attrs, "id_pes", obter(attrs, "id", null)));
            String nmPesBusca = obter(attrs, "pes", obter(attrs, "nm", ""));
            Pessoa pAlvo = null;
            if (idPesBusca != null || !nmPesBusca.isBlank()) {
                pAlvo = localizarPessoaUnica(idPesBusca, nmPesBusca);
                if (pAlvo == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                            "Não encontrei nenhuma pessoa correspondente a '%s'.", idPesBusca != null ? "#" + idPesBusca : nmPesBusca);
                }
            } else if (foco != null && foco.getIdUltimaPessoa() != null) {
                pAlvo = roteador.pessoas().buscarPorId(foco.getIdUltimaPessoa());
            } else if (foco != null && foco.getIdUltimaNota() != null) {
                Nota nf = roteador.notas().buscarNotaPorId(foco.getIdUltimaNota());
                if (nf != null) pAlvo = nf.getPessoa();
            }

            if (pAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.pessoa.ausente",
                        "Nenhuma pessoa/fornecedor/cliente está em foco na sessão atual.");
            }
            if (foco != null) {
                foco.registrarPessoaEmFoco(pAlvo);
            }
            RetornoChatBotUI.RetornoContatosPessoa recCont = RetornoChatBotUI.construirRecordContatos(
                    pAlvo, foco != null ? foco.getIdUltimaNota() : null, roteador);
            if (foco != null) {
                foco.registrarRecord(recCont);
            }
            return recCont.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", pAlvo.getId());
        }

        // 4. Busca de PRODUTOS DA NOTA (aproveitando a Nota consultada acima) ou PRODUTO EM CASCATA
        if (entidade.startsWith("PRODUTO") || "ITEM".equals(entidade)) {
            boolean semFiltroExplicito = (id == null && termo.isBlank() && !"catalogo".equalsIgnoreCase(obter(attrs, "tp", "")));
            boolean pedirDaNota = "da_nota".equals(ctxFoco)
                    || idNotaRef != null
                    || (semFiltroExplicito && foco != null && foco.getIdUltimaNota() != null);
            if (pedirDaNota) {
                Long idNotaAlvo = idNotaRef != null ? idNotaRef : (foco != null ? foco.getIdUltimaNota() : null);
                if (idNotaAlvo == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                            "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
                }
                Nota notaFoco = roteador.notas().buscarNotaPorId(idNotaAlvo);
                if (notaFoco == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                            "Nota #%s não encontrada.", idNotaAlvo);
                }
                RetornoChatBotUI.RetornoProdutosDaNota recProds = RetornoChatBotUI.construirRecordProdutosDaNota(notaFoco);
                if (foco != null) {
                    foco.registrarRecord(recProds);
                }
                return recProds.formatarTexto();
            }

            if (!alvosCascata.isEmpty() && (id != null || !termo.isBlank())) {
                Produto p = localizarProdutoUnico(id, termo);
                if (p == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.produto.nao.encontrado",
                            "Não encontrei nenhum produto correspondente a '%s'.", id != null ? "#" + id : termo);
                }
                if (foco != null) {
                    foco.registrarProdutoEmFoco(p);
                }
                boolean incEst = cascataContem(alvosCascata, "ESTOQUE", "LOTE") || alvosCascata.isEmpty();
                boolean incForn = cascataContem(alvosCascata, "FORNECEDOR", "PESSOA") || alvosCascata.isEmpty();
                boolean incNotas = cascataContem(alvosCascata, "NOTA") || alvosCascata.isEmpty();
                RetornoChatBotUI.RetornoProdutoCascata recProd = RetornoChatBotUI.construirRecordProdutoCascata(
                        p, roteador, incEst, incForn, incNotas);
                if (foco != null) {
                    foco.registrarRecord(recProd);
                }
                return recProd.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId());
            }
        }

        // 5. Busca de PAGAMENTOS DA NOTA (aproveitando a Nota consultada acima ou por id_nota>)
        if (entidade.startsWith("PAGAMENTO")) {
            Long idNotaAlvo = idNotaRef != null ? idNotaRef : (id != null ? id : (foco != null ? foco.getIdUltimaNota() : null));
            if (idNotaAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                        "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
            }
            Nota notaFoco = roteador.notas().buscarNotaPorId(idNotaAlvo);
            if (notaFoco == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota #%s não encontrada.", idNotaAlvo);
            }
            RetornoChatBotUI.RetornoPagamentosDaNota recPags = RetornoChatBotUI.construirRecordPagamentosDaNota(notaFoco, roteador);
            if (foco != null) {
                foco.registrarRecord(recPags);
            }
            return recPags.formatarTexto();
        }

        return processarLeitura(entidade, attrs);
    }

    public String processarLeitura(String entidade, Map<String, String> attrs) {
        String termo = obter(attrs, "nm", obter(attrs, "doc", obter(attrs, "reg", "")));
        Long id = parseLongSafe(obter(attrs, "id", null));

        if (entidade.startsWith("PRODUTO") || "ESTOQUE".equals(entidade) || "ITEM".equals(entidade)) {
            if ("ESTOQUE".equals(entidade) && (id != null || !termo.isBlank())) {
                Produto p = localizarProdutoUnico(id, termo);
                if (p != null) {
                    BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                    String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                    return String.format("%s (#%d) — Estoque disponível: %s %s, Preço de venda: R$ %s",
                            limparNomeExibicao(p.getNome(), umSigla), p.getId(),
                            saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                            umSigla,
                            moeda(p.getPrecoVenda()));
                }
            }
            List<Produto> todos = entidade.contains("ESTOQUE")
                    ? roteador.produtos().listarProdutosComEstoqueValido()
                    : roteador.produtos().listarTodosProdutos();
            if (id != null) {
                Produto p = roteador.produtos().buscarPorId(id);
                if (p == null) return "Não encontrei nenhum produto com o código #" + id + ".";
                BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                return String.format("#%d - %s (%s) — Preço: R$ %s, Estoque: %s%s",
                        p.getId(), limparNomeExibicao(p.getNome(), umSigla),
                        umSigla,
                        moeda(p.getPrecoVenda()),
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                        sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId()));
            }
            if (!termo.isBlank()) {
                List<Produto> filtradosBrutos = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todos, Produto::getNome, termo);
                List<Produto> filtrados = filtrarProdutosExibicaoLimpa(filtradosBrutos);
                if (filtrados.isEmpty() && !filtradosBrutos.isEmpty()) {
                    filtrados = List.of(filtradosBrutos.get(0));
                }
                if (filtrados.isEmpty()) {
                    return "Não encontrei nenhum produto correspondente a '" + termo + "'.";
                }
                if (filtrados.size() > 1 && temEmpateDeSigla(filtrados, Produto::getNome, termo)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Encontrei %d produtos para a sigla '%s'. Qual deles você deseja?\n",
                            filtrados.size(), termo));
                    for (Produto p : filtrados) {
                        BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                        String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                        sb.append(String.format("  #%d - %s, R$ %s (Estoque: %s %s)\n",
                                p.getId(), limparNomeExibicao(p.getNome(), umSigla), moeda(p.getPrecoVenda()),
                                saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                                umSigla));
                    }
                    return sb.toString().trim();
                }
                Produto p = filtrados.get(0);
                BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                return String.format("#%d - %s (%s) — Preço: R$ %s, Estoque atual: %s%s",
                        p.getId(), limparNomeExibicao(p.getNome(), umSigla),
                        umSigla,
                        moeda(p.getPrecoVenda()),
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                        sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId()));
            }

            List<Produto> limpos = filtrarProdutosExibicaoLimpa(todos);
            if (limpos.isEmpty()) {
                return "Nenhum produto cadastrado no momento.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Produtos no catálogo (%d itens):\n", limpos.size()));
            for (Produto p : limpos) {
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                sb.append(String.format("  #%d - %s — R$ %s (%s)\n",
                        p.getId(), limparNomeExibicao(p.getNome(), umSigla), moeda(p.getPrecoVenda()), umSigla));
            }
            return sb.toString().trim();
        }

        if (entidade.startsWith("PESSOA") || "REGISTRO".equals(entidade)) {
            if ("REGISTRO".equals(entidade) && !termo.isBlank()) {
                Pessoa p = roteador.pessoas().buscarPessoaPorRegistroUnico(obter(attrs, "tp", null), termo);
                if (p != null) {
                    return String.format("Documento %s pertence a #%d - %s.", termo, p.getId(), limparNomeExibicao(p.getNome(), null));
                }
                return "Nenhuma pessoa encontrada para o documento " + termo + ".";
            }
            List<Pessoa> todas = new ArrayList<>();
            todas.addAll(roteador.pessoas().listarClientes());
            for (Pessoa f : roteador.pessoas().listarFornecedores()) {
                if (todas.stream().noneMatch(x -> Objects.equals(x.getId(), f.getId()))) {
                    todas.add(f);
                }
            }
            if (id != null) {
                Pessoa p = roteador.pessoas().buscarPorId(id);
                return p != null
                        ? String.format("#%d - %s (%s)", p.getId(), limparNomeExibicao(p.getNome(), null), descreverPapelPessoa(p))
                        : "Nenhuma pessoa encontrada com o código #" + id + ".";
            }
            if (!termo.isBlank()) {
                List<Pessoa> filtradas = filtrarPessoasExibicaoLimpa(BuscaPalavrasParciaisUtil.filtrarEOrdenar(todas, Pessoa::getNome, termo));
                if (filtradas.isEmpty()) {
                    return "Não encontrei nenhuma pessoa correspondente a '" + termo + "'.";
                }
                if (filtradas.size() > 1 && temEmpateDeSigla(filtradas, Pessoa::getNome, termo)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Encontrei %d pessoas para a sigla '%s'. Qual delas você procura?\n",
                            filtradas.size(), termo));
                    for (Pessoa p : filtradas) {
                        sb.append(String.format("  #%d - %s (%s)\n", p.getId(), limparNomeExibicao(p.getNome(), null), descreverPapelPessoa(p)));
                    }
                    return sb.toString().trim();
                }
                Pessoa p = filtradas.get(0);
                return String.format("#%d - %s (%s)", p.getId(), limparNomeExibicao(p.getNome(), null), descreverPapelPessoa(p));
            }
            List<Pessoa> pessoasLimpas = filtrarPessoasExibicaoLimpa(todas);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Pessoas cadastradas (%d registros):\n", pessoasLimpas.size()));
            for (Pessoa p : pessoasLimpas) {
                sb.append(String.format("  #%d - %s (%s)\n", p.getId(), limparNomeExibicao(p.getNome(), null), descreverPapelPessoa(p)));
            }
            return sb.toString().trim();
        }

        if ("UM".equals(entidade)) {
            List<Um> lista = roteador.produtos().listarUnidadesMedida();
            StringBuilder sb = new StringBuilder(String.format("Unidades de Medida (%d):\n", lista.size()));
            for (Um u : lista) {
                sb.append(String.format("  %s — %s\n", u.getSigla(), u.getNome()));
            }
            return sb.toString().trim();
        }
        if ("FORMAPAGAMENTO".equals(entidade)) {
            List<FormaPagamento> lista = roteador.pagamentos().listarFormasPagamentoAtivas();
            StringBuilder sb = new StringBuilder(String.format("Formas de Pagamento Ativas (%d):\n", lista.size()));
            for (FormaPagamento fp : lista) {
                sb.append(String.format("  %s (%s)\n", fp.getNome(), fp.getCodigo()));
            }
            return sb.toString().trim();
        }
        if (entidade.startsWith("NOTA") || "VINCULOFATOREXTERNONOTA".equals(entidade)) {
            if (id != null) {
                Nota n = roteador.notas().buscarNotaPorId(id);
                return n != null
                        ? String.format("Nota #%d — Valor Total: R$ %s", n.getId(), moeda(n.getValorTotal()))
                        : "Nota #" + id + " não encontrada.";
            }
            List<Nota> notas = roteador.notas().listarTodasNotas();
            StringBuilder sb = new StringBuilder(String.format("Notas Emitidas (%d):\n", notas.size()));
            for (Nota n : notas) {
                sb.append(String.format("  Nota #%d (%s) — Total: R$ %s\n",
                        n.getId(), n.getClass().getSimpleName(), moeda(n.getValorTotal())));
            }
            return sb.toString().trim();
        }

        return "Consulta concluída.";
    }

    // ==================== CRIAÇÃO (C= -> FICA EM ESPERA NA BANDEJA) ====================

    private String processarCriacao(
            String entidade,
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja) {

        String nome = obter(attrs, "nm", "");

        if ("PRODUTO".equals(entidade) || "PRODUTOFISICO".equals(entidade) || "PRODUTODIGITAL".equals(entidade)) {
            if (nome.isBlank()) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.campo.obrigatorio",
                        "Campo obrigatorio '%s' nao informado para #%s (ex: %s).", "#" + entidade, "nm", "nm> Nome do Produto");
            }
            for (String chaveNum : List.of("vv", "val", "vl")) {
                if (attrs.containsKey(chaveNum) && !validarNumeroReal(attrs.get(chaveNum))) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.valor.numerico",
                            "O valor '%s' informado no campo '%s' e invalido.", attrs.get(chaveNum), chaveNum);
                }
            }
            Produto existente = roteador.produtos().buscarPorNome(nome);
            if (existente != null) {
                BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(existente.getId());
                String umSigla = existente.getUm() != null ? existente.getUm().getSigla() : "UN";
                return String.format("O produto %s (#%d) já está cadastrado.\n"
                                + "  Estoque disponível: %s %s, Preço atual: R$ %s\n"
                                + "  Para alterar o preço, envie: $$ U= #Produto id> %d, vv> NOVO_VALOR;",
                        limparNomeExibicao(existente.getNome(), umSigla), existente.getId(),
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                        umSigla,
                        moeda(existente.getPrecoVenda()), existente.getId());
            }

            String um = obter(attrs, "um", "UN").toUpperCase(Locale.ROOT);
            String precoVenda = obter(attrs, "vv", obter(attrs, "val", obter(attrs, "vl", "0.00")));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("nome", nome);
            params.put("descricao", obter(attrs, "desc", "Cadastrado via assistente"));
            params.put("precoVenda", precoVenda);

            RotaSistema rotaAlvo = "PRODUTODIGITAL".equals(entidade)
                    ? RotaSistema.PRODUTO_DIGITAL_CADASTRAR
                    : RotaSistema.PRODUTO_FISICO_CADASTRAR;

            if (rotaAlvo == RotaSistema.PRODUTO_FISICO_CADASTRAR) {
                params.put("siglaUm", um);
                aplicarDimensoes(attrs, params);
            }

            String desc = String.format("Cadastrar produto '%s' (%s) por R$ %s", nome, um, precoVenda);
            ItemBandeja item = bandeja.adicionarOuAtualizar(rotaAlvo, params, desc);
            return String.format("[%s] Preparei o cadastro do produto %s (Unidade: %s, Preço de venda: R$ %s).",
                    item.getIdItem(), nome, um, precoVenda);
        }

        if ("UM".equals(entidade)) {
            String sigla = obter(attrs, "nm", obter(attrs, "um", "UN")).toUpperCase(Locale.ROOT);
            String descUm = obter(attrs, "desc", sigla);
            String base = obter(attrs, "val", obter(attrs, "vl", "1.000000"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sigla", sigla);
            params.put("nome", descUm);
            params.put("descricao", descUm);
            params.put("base", base);
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
                    params,
                    "Cadastrar unidade de medida " + sigla + " (" + descUm + ")");
            return String.format("[%s] Preparei o cadastro da unidade de medida %s (%s).",
                    item.getIdItem(), sigla, descUm);
        }

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            if (nome.isBlank()) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.campo.obrigatorio",
                        "Campo obrigatorio '%s' nao informado para #%s (ex: %s).", "#" + entidade, "nm", "nm> Nome");
            }
            String papelPadrao = "FORNECEDOR".equals(entidade) ? "FORNECEDOR" : ("CLIENTE".equals(entidade) ? "CLIENTE" : "AMBOS");
            String papel = obter(attrs, "tp", papelPadrao).toUpperCase(Locale.ROOT);
            String doc = obter(attrs, "doc", obter(attrs, "reg", ""));
            String limite = obter(attrs, "val", obter(attrs, "vl", "0.00"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("nome", nome);
            params.put("razaoSocial", nome);
            params.put("papel", papel);
            if (!doc.isBlank()) {
                params.put("documento", doc);
                params.put("cpf", doc);
                params.put("cnpj", doc);
            }
            params.put("limite", limite);

            RotaSistema rota = RotaSistema.PESSOA_SIMPLES_CADASTRAR;
            if ("PESSOAFISICA".equals(entidade) && !doc.isBlank()) {
                rota = papel.contains("FORN") ? RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR : RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR;
            } else if ("PESSOAJURIDICA".equals(entidade) && !doc.isBlank()) {
                rota = papel.contains("FORN") ? RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR : RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR;
            }

            ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params,
                    String.format("Cadastrar %s '%s'", papel.toLowerCase(Locale.ROOT), nome));
            return String.format("[%s] Preparei o cadastro de %s como %s%s.",
                    item.getIdItem(), nome, papel.toLowerCase(Locale.ROOT),
                    !doc.isBlank() ? " (Documento: " + doc + ")" : "");
        }

        if ("REGISTRO".equals(entidade) || "CONTATO".equals(entidade) || "CONTATOS".equals(entidade)) {
            String alvoPes = obter(attrs, "pes", nome);
            Pessoa p = localizarPessoaUnica(parseLongSafe(obter(attrs, "id_pes", null)), alvoPes);
            Long idPessoa = p != null ? p.getId() : parseLongSafe(obter(attrs, "id", "1"));
            String tpPadrao = "REGISTRO".equals(entidade) ? "RG" : "WHATSAPP";
            String tpDoc = obter(attrs, "tp", tpPadrao).toUpperCase(Locale.ROOT);
            String numDoc = obter(attrs, "doc", obter(attrs, "reg", obter(attrs, "val", "")));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("idPessoa", idPessoa);
            params.put("tipoDocumento", tpDoc);
            params.put("numeroRegistro", numDoc);
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_REGISTRO_ADICIONAR, params,
                    "Adicionar registro/contato " + tpDoc + " (" + numDoc + ") a " + (p != null ? p.getNome() : "#" + idPessoa));
            return String.format("[%s] Preparei a inclusão de %s: %s para %s.",
                    item.getIdItem(), tpDoc, numDoc, p != null ? limparNomeExibicao(p.getNome(), null) : "#" + idPessoa);
        }

        if ("FORMAPAGAMENTO".equals(entidade)) {
            String cod = obter(attrs, "cod", nome.toUpperCase(Locale.ROOT).replace(" ", "_"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("codigo", cod);
            params.put("nome", nome.isBlank() ? cod : nome);
            params.put("descricao", obter(attrs, "desc", "Forma de pagamento " + cod));
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PAGAMENTO_FORMA_CADASTRAR, params,
                    "Cadastrar forma de pagamento " + params.get("nome"));
            return String.format("[%s] Preparei o cadastro da forma de pagamento %s.",
                    item.getIdItem(), params.get("nome"));
        }

        if (entidade.startsWith("PAGAMENTO")) {
            String codigoForma = normalizarCodigoForma(nome);
            String valor = obter(attrs, "val", obter(attrs, "vl", "0.00"));
            LocalDate dt = resolverDataRelativa(obter(attrs, "dt", "hoje"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("codigoForma", codigoForma);
            params.put("valor", valor);
            RotaSistema rota = RotaSistema.PAGAMENTO_CRIAR_A_VISTA;
            if ("PAGAMENTOAPRAZO".equals(entidade)) {
                rota = RotaSistema.PAGAMENTO_CRIAR_A_PRAZO;
                params.put("dataVencimento", dt);
            } else if ("PAGAMENTOPARCELADO".equals(entidade)) {
                rota = RotaSistema.PAGAMENTO_CRIAR_PARCELADO;
                params.put("quantidadeParcelas", Integer.parseInt(obter(attrs, "q", "2").replaceAll("[^0-9]", "")));
            }
            ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params,
                    "Registrar pagamento em " + codigoForma + " de R$ " + valor + " (" + dt + ")");
            return String.format("[%s] Preparei o pagamento em %s no valor de R$ %s (data: %s).",
                    item.getIdItem(), codigoForma, valor, dt);
        }

        if ("NOTACOMPRA".equals(entidade)) {
            return compilarNotaCompra(attrs, subAdd, subRem, bandeja);
        }
        if ("NOTAVENDA".equals(entidade)) {
            return compilarNotaVenda(attrs, subAdd, bandeja);
        }
        if ("NOTADEVOLUCAO".equals(entidade)) {
            return compilarNotaDevolucao(attrs, subAdd, bandeja);
        }
        if ("NOTAPERDA".equals(entidade)) {
            return compilarNotaPerda(attrs, subAdd, bandeja);
        }

        if ("CONJUNTOPRODUTO".equals(entidade)) {
            Long idKit = parseLongSafe(obter(attrs, "id", "1"));
            List<String> comps = subAdd.getOrDefault("COMPONENTE", subAdd.getOrDefault("PRODUTO", List.of()));
            StringBuilder sb = new StringBuilder();
            for (String linhaComp : comps) {
                Map<String, String> ca = extrairAtributos(linhaComp.replaceFirst("^(\\+\\+|--)\\s*", ""));
                Produto comp = localizarProdutoUnico(parseLongSafe(obter(ca, "id", null)), obter(ca, "nm", ""));
                Long idComp = comp != null ? comp.getId() : 1L;
                String qtd = obter(ca, "q", "1");
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("idProdutoKit", idKit);
                params.put("idProdutoComponente", idComp);
                params.put("quantidade", qtd);
                ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, params,
                        "Adicionar " + qtd + "x " + (comp != null ? comp.getNome() : "#" + idComp) + " ao Kit #" + idKit);
                sb.append(String.format("[%s] Adicionar %s (quantidade: %s) ao Kit.\n",
                        item.getIdItem(), comp != null ? comp.getNome() : "#" + idComp, qtd));
            }
            return sb.toString().trim();
        }

        return "Instrução adicionada à lista de espera.";
    }

    private String compilarNotaCompra(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja) {

        String nomeFornecedor = obter(attrs, "nm", "Fornecedor Padrão");
        String dtTexto = obter(attrs, "dt", "hoje");
        LocalDate dataNota = resolverDataRelativa(dtTexto);

        Pessoa fornecedor = (bandeja != null && bandeja.possuiPessoaEmEstagio(nomeFornecedor))
                ? null
                : localizarPessoaUnica(parseLongSafe(obter(attrs, "id", null)), nomeFornecedor);
        List<Map<String, Object>> itensCompra = new ArrayList<>();
        List<Map<String, Object>> pagamentosCompra = new ArrayList<>();
        BigDecimal subtotalAcumulado = BigDecimal.ZERO;

        StringBuilder resumo = new StringBuilder();
        resumo.append(String.format("Nota de Compra — Fornecedor: %s (Entrega: %s - %s)\n",
                fornecedor != null ? limparNomeExibicao(fornecedor.getNome(), null) : nomeFornecedor,
                descreverDataAmigavel(dtTexto, dataNota), dataNota));

        List<String> linhasProdutos = subAdd.getOrDefault("PRODUTO", List.of());
        if (!linhasProdutos.isEmpty()) {
            resumo.append("  Itens da nota:\n");
            for (String linhaProd : linhasProdutos) {
                Map<String, String> pa = extrairAtributos(linhaProd.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String nmProd = obter(pa, "nm", "");
                Long idProd = parseLongSafe(obter(pa, "id", null));
                Produto prodBanco = (idProd == null && bandeja != null && bandeja.possuiProdutoEmEstagio(nmProd))
                        ? null
                        : localizarProdutoUnico(idProd, nmProd);

                BigDecimal qtd = parseBigDecimalSafe(obter(pa, "q", "1"), BigDecimal.ONE);
                String strCusto = obter(pa, "vc", obter(pa, "val", obter(pa, "vl", "0.00")));
                BigDecimal valorUnitario = parseBigDecimalSafe(strCusto, BigDecimal.ZERO);
                BigDecimal subtotalItem = valorUnitario.multiply(qtd);
                subtotalAcumulado = subtotalAcumulado.add(subtotalItem);

                Map<String, Object> itemMap = new LinkedHashMap<>();
                if (prodBanco != null) {
                    itemMap.put("idProduto", prodBanco.getId());
                    itemMap.put("nomeProduto", prodBanco.getNome());
                } else {
                    itemMap.put("nomeProduto", nmProd);
                }
                itemMap.put("quantidade", qtd.toPlainString());
                itemMap.put("valorUnitario", valorUnitario.toPlainString());
                itensCompra.add(itemMap);

                resumo.append(String.format("    %s — %s un x R$ %s (Subtotal acum.: R$ %s)\n",
                        prodBanco != null ? limparNomeExibicao(prodBanco.getNome(), null) : nmProd,
                        qtd.stripTrailingZeros().toPlainString(),
                        valorUnitario.toPlainString(),
                        subtotalAcumulado.toPlainString()));
            }
        }

        List<String> linhasRessarcimento = subAdd.getOrDefault("RESSARCIMENTO", List.of());
        if (!linhasRessarcimento.isEmpty()) {
            resumo.append("  Ressarcimento de perdas do fornecedor:\n");
            for (String linhaRes : linhasRessarcimento) {
                Map<String, String> ra = extrairAtributos(linhaRes.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String prodPerdido = obter(ra, "perdido", obter(ra, "nm_perd", "Produto perdido"));
                String qPerd = obter(ra, "q_perd", "1");
                String vcPerd = obter(ra, "vc_perd", "0.00");
                String prodRecebido = obter(ra, "recebido", obter(ra, "nm", prodPerdido));
                BigDecimal qRec = parseBigDecimalSafe(obter(ra, "q_rec", obter(ra, "q", "1")), BigDecimal.ONE);
                BigDecimal vcRec = parseBigDecimalSafe(obter(ra, "vc_rec", obter(ra, "vc", vcPerd)), BigDecimal.ZERO);

                Produto prodRecBanco = localizarProdutoUnico(null, prodRecebido);
                Map<String, Object> itemRessarcido = new LinkedHashMap<>();
                if (prodRecBanco != null) {
                    itemRessarcido.put("idProduto", prodRecBanco.getId());
                    itemRessarcido.put("nomeProduto", prodRecBanco.getNome());
                } else {
                    itemRessarcido.put("nomeProduto", prodRecebido);
                }
                itemRessarcido.put("quantidade", qRec.toPlainString());
                itemRessarcido.put("valorUnitario", vcRec.toPlainString());
                itemRessarcido.put("ressarcimentoFornecedor", true);
                itensCompra.add(itemRessarcido);

                resumo.append(String.format("    Troca/Ressarcimento: %s un de %s (R$ %s) compensado por %s de %s (R$ %s) [Marcado como ressarcido]\n",
                        qPerd, prodPerdido, vcPerd, qRec.stripTrailingZeros().toPlainString(), prodRecebido, vcRec.toPlainString()));
            }
        }

        List<String> linhasPagamento = subAdd.getOrDefault("PAGAMENTO", List.of());
        BigDecimal totalPago = BigDecimal.ZERO;
        if (!linhasPagamento.isEmpty()) {
            resumo.append("  Pagamentos programados:\n");
            for (String linhaPag : linhasPagamento) {
                Map<String, String> pga = extrairAtributosPagamentoPosicionalOuChaveado(linhaPag);
                String forma = normalizarCodigoForma(obter(pga, "nm", "PIX"));
                BigDecimal valPag = parseBigDecimalSafe(obter(pga, "val", obter(pga, "vl", "0.00")), BigDecimal.ZERO);
                String dtPagStr = obter(pga, "dt", "hoje");
                LocalDate dtPag = resolverDataRelativa(dtPagStr);
                totalPago = totalPago.add(valPag);

                Map<String, Object> pagMap = new LinkedHashMap<>();
                pagMap.put("codigoForma", forma);
                pagMap.put("valor", valPag.toPlainString());
                pagMap.put("data", dtPag.toString());
                pagamentosCompra.add(pagMap);

                resumo.append(String.format("    %s: R$ %s (%s - %s)\n",
                        forma, valPag.toPlainString(), descreverDataAmigavel(dtPagStr, dtPag), dtPag));
            }
        }

        Map<String, Object> paramsNota = new LinkedHashMap<>();
        if (fornecedor != null) {
            paramsNota.put("idFornecedor", fornecedor.getId());
        }
        paramsNota.put("nomeFornecedor", fornecedor != null ? fornecedor.getNome() : nomeFornecedor);
        paramsNota.put("dataNota", dataNota.toString());
        paramsNota.put("descricao", "Nota de Compra de " + (fornecedor != null ? fornecedor.getNome() : nomeFornecedor));
        paramsNota.put("itens", itensCompra);
        paramsNota.put("pagamentos", pagamentosCompra);

        String resumoCurto = String.format("Emitir Nota de Compra de '%s' (%d ite%s, Total: R$ %s, Pago: R$ %s)",
                fornecedor != null ? limparNomeExibicao(fornecedor.getNome(), null) : nomeFornecedor,
                itensCompra.size(), itensCompra.size() == 1 ? "m" : "ns",
                subtotalAcumulado.toPlainString(),
                totalPago.toPlainString());

        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.NOTA_COMPRA_EMITIR, paramsNota, resumoCurto);
        resumo.append(String.format("[%s] Nota preparada para confirmação (Total dos produtos: R$ %s, Pagamentos: R$ %s).",
                item.getIdItem(), subtotalAcumulado.toPlainString(), totalPago.toPlainString()));
        return resumo.toString();
    }

    private String compilarNotaVenda(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja) {

        String tp = obter(attrs, "tp", "FIFO").toUpperCase(Locale.ROOT);
        String nmCliente = obter(attrs, "nm", "CONSUMIDOR FINAL");
        String dtTexto = obter(attrs, "dt", "hoje");
        LocalDate dataNota = resolverDataRelativa(dtTexto);

        Pessoa cliente = localizarPessoaUnica(parseLongSafe(obter(attrs, "id", null)), nmCliente);
        List<Map<String, Object>> itensVenda = new ArrayList<>();
        List<Map<String, Object>> pagamentosVenda = new ArrayList<>();
        BigDecimal subtotalVenda = BigDecimal.ZERO;

        List<String> linhasProdutos = subAdd.getOrDefault("PRODUTO", subAdd.getOrDefault("ITEM", List.of()));
        for (String linhaProd : linhasProdutos) {
            Map<String, String> pa = extrairAtributos(linhaProd.replaceFirst("^(\\+\\+|--)\\s*", ""));
            Produto prod = localizarProdutoUnico(parseLongSafe(obter(pa, "id", null)), obter(pa, "nm", ""));
            BigDecimal qtd = parseBigDecimalSafe(obter(pa, "q", "1"), BigDecimal.ONE);
            BigDecimal preco = parseBigDecimalSafe(
                    obter(pa, "vv", obter(pa, "val", prod != null && prod.getPrecoVenda() != null ? prod.getPrecoVenda().toPlainString() : "0.00")),
                    BigDecimal.ZERO);
            subtotalVenda = subtotalVenda.add(preco.multiply(qtd));

            Map<String, Object> itemMap = new LinkedHashMap<>();
            if (prod != null) {
                itemMap.put("idProduto", prod.getId());
                itemMap.put("nomeProduto", prod.getNome());
            } else {
                itemMap.put("nomeProduto", obter(pa, "nm", "Produto"));
            }
            itemMap.put("quantidade", qtd.toPlainString());
            itemMap.put("valorUnitario", preco.toPlainString());
            itensVenda.add(itemMap);
        }

        for (String linhaPag : subAdd.getOrDefault("PAGAMENTO", List.of())) {
            Map<String, String> pga = extrairAtributosPagamentoPosicionalOuChaveado(linhaPag);
            Map<String, Object> pagMap = new LinkedHashMap<>();
            pagMap.put("codigoForma", normalizarCodigoForma(obter(pga, "nm", "PIX")));
            pagMap.put("valor", obter(pga, "val", obter(pga, "vl", subtotalVenda.toPlainString())));
            pagMap.put("data", resolverDataRelativa(obter(pga, "dt", "hoje")).toString());
            pagamentosVenda.add(pagMap);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (cliente != null) {
            params.put("idCliente", cliente.getId());
        }
        params.put("nomeCliente", cliente != null ? cliente.getNome() : nmCliente);
        params.put("dataNota", dataNota.toString());
        params.put("itens", itensVenda);
        params.put("pagamentos", pagamentosVenda);

        RotaSistema rota = "BALCAO".equals(tp)
                ? RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO
                : ("LOTE".equals(tp) ? RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL : RotaSistema.NOTA_VENDA_EMITIR_FIFO);

        String resumo = String.format("Emitir Nota de Venda para '%s' (%d itens, Total: R$ %s)",
                cliente != null ? limparNomeExibicao(cliente.getNome(), null) : nmCliente, itensVenda.size(), subtotalVenda.toPlainString());
        ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params, resumo);
        return String.format("[%s] %s.", item.getIdItem(), resumo);
    }

    private String compilarNotaDevolucao(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja) {

        Long idNotaVenda = parseLongSafe(obter(attrs, "id_venda", obter(attrs, "id", "1")));
        boolean geraPerdaFornecedor = "FORN".equalsIgnoreCase(obter(attrs, "resp", "isento"))
                || "FORNECEDOR".equalsIgnoreCase(obter(attrs, "resp", "isento"));
        String motivo = obter(attrs, "mot", "Devolução de mercadoria");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idNotaVenda", idNotaVenda);
        params.put("motivo", motivo);
        params.put("geraPerdaFornecedor", geraPerdaFornecedor);

        List<String> prods = subAdd.getOrDefault("PRODUTO", subAdd.getOrDefault("ITEM", List.of()));
        if (!prods.isEmpty()) {
            Map<String, String> pa = extrairAtributos(prods.get(0).replaceFirst("^(\\+\\+|--)\\s*", ""));
            Produto p = localizarProdutoUnico(parseLongSafe(obter(pa, "id", null)), obter(pa, "nm", ""));
            if (p != null) params.put("idProduto", p.getId());
            params.put("quantidadeDevolvida", obter(pa, "q", "1"));
        }

        String desc = String.format("Registrar devolução da venda #%d (%s)",
                idNotaVenda, geraPerdaFornecedor ? "gera ressarcimento com fornecedor" : "reintegra ao estoque");
        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.NOTA_DEVOLUCAO_REGISTRAR, params, desc);
        return String.format("[%s] %s.", item.getIdItem(), desc);
    }

    private String compilarNotaPerda(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja) {

        boolean isentoFornecedor = !"FORN".equalsIgnoreCase(obter(attrs, "resp", "isento"));
        String motivoBase = obter(attrs, "mot", "Perda operacional");
        String motivoFinal = isentoFornecedor
                ? motivoBase + " [Fornecedor Isento de Ressarcimento]"
                : motivoBase + " [Dentro da validade - Ressarcimento do Fornecedor]";

        Long idLote = parseLongSafe(obter(attrs, "id", null));
        String qtd = obter(attrs, "q", "1");
        List<String> itens = subAdd.getOrDefault("ITEM", subAdd.getOrDefault("PRODUTO", List.of()));
        if (!itens.isEmpty()) {
            Map<String, String> ia = extrairAtributos(itens.get(0).replaceFirst("^(\\+\\+|--)\\s*", ""));
            if (idLote == null) idLote = parseLongSafe(obter(ia, "id", "1"));
            qtd = obter(ia, "q", qtd);
        }
        if (idLote == null) idLote = 1L;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idLote", idLote);
        params.put("quantidade", qtd);
        params.put("motivo", motivoFinal);

        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA, params,
                "Baixar perda no lote #" + idLote + " (qtd: " + qtd + " — " + (isentoFornecedor ? "Fornecedor Isento" : "Ressarcir Fornecedor") + ")");
        return String.format("[%s] Nota de perda preparada para o lote #%d (quantidade: %s — %s).",
                item.getIdItem(), idLote, qtd, motivoFinal);
    }

    // ==================== ATUALIZAÇÃO (U= -> FICA EM ESPERA NA BANDEJA) ====================

    private String processarAtualizacao(
            String entidade,
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja) {

        String termo = obter(attrs, "nm", "");
        Long id = parseLongSafe(obter(attrs, "id", null));

        if (entidade.startsWith("PRODUTO")) {
            for (String chaveNum : List.of("vv", "val", "vl")) {
                if (attrs.containsKey(chaveNum) && !validarNumeroReal(attrs.get(chaveNum))) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.valor.numerico",
                            "O valor '%s' informado no campo '%s' e invalido.", attrs.get(chaveNum), chaveNum);
                }
            }
            Produto alvo = localizarProdutoUnico(id, termo);
            if (alvo == null && !bandeja.possuiProdutoEmEstagio(termo)) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.produto.nao.encontrado",
                        "Não encontrei nenhum produto correspondente a '%s'.", id != null ? "#" + id : termo);
            }

            String nomeAlvoLimpo = alvo != null ? limparNomeExibicao(alvo.getNome(), null) : termo;
            if (alvo != null) {
                String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                        "ProdutoDAO", alvo.getId(), nomeAlvoLimpo, bandeja != null ? bandeja.getIdSessao() : null);
                if (erroTrancado != null) {
                    return erroTrancado;
                }
            }
            StringBuilder resumoDiff = new StringBuilder();
            if (attrs.containsKey("vv") || attrs.containsKey("val") || attrs.containsKey("vl")) {
                String novoPreco = obter(attrs, "vv", obter(attrs, "val", obter(attrs, "vl", "0.00")));
                Map<String, Object> paramsPreco = new LinkedHashMap<>();
                if (alvo != null) {
                    paramsPreco.put("idProduto", alvo.getId());
                    paramsPreco.put("nomeProduto", alvo.getNome());
                } else {
                    paramsPreco.put("nomeProduto", termo);
                }
                paramsPreco.put("novoPreco", novoPreco);
                ItemBandeja item = bandeja.adicionarOuAtualizar(
                        RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                        paramsPreco,
                        String.format("Alterar preço de '%s' para R$ %s", nomeAlvoLimpo, novoPreco));
                resumoDiff.append(String.format("[%s] Alterar o preço de %s de R$ %s para R$ %s.\n",
                        item.getIdItem(), nomeAlvoLimpo,
                        alvo != null ? moeda(alvo.getPrecoVenda()) : "0.00", novoPreco));
            }

            if (attrs.containsKey("novo_nm") || attrs.containsKey("desc")) {
                String novoNome = obter(attrs, "novo_nm", alvo != null ? alvo.getNome() : termo);
                Map<String, Object> paramsCad = new LinkedHashMap<>();
                if (alvo != null) paramsCad.put("idProduto", alvo.getId());
                paramsCad.put("nomeAtual", alvo != null ? alvo.getNome() : termo);
                paramsCad.put("novoNome", novoNome);
                if (attrs.containsKey("desc")) paramsCad.put("descricao", attrs.get("desc"));
                ItemBandeja item = bandeja.adicionarOuAtualizar(
                        RotaSistema.PRODUTO_CATALOGO_ATUALIZAR,
                        paramsCad,
                        "Renomear produto '" + nomeAlvoLimpo + "' para '" + novoNome + "'");
                resumoDiff.append(String.format("[%s] Atualizar o nome de %s para %s.\n",
                        item.getIdItem(), nomeAlvoLimpo, novoNome));
            }

            if (attrs.containsKey("dim") || attrs.containsKey("pb") || attrs.containsKey("pl")) {
                Map<String, Object> paramsDim = new LinkedHashMap<>();
                if (alvo != null) paramsDim.put("idProduto", alvo.getId());
                aplicarDimensoes(attrs, paramsDim);
                ItemBandeja item = bandeja.adicionarOuAtualizar(
                        RotaSistema.PRODUTO_FISICO_ATUALIZAR_DIMENSOES,
                        paramsDim,
                        "Atualizar dimensões e pesos de '" + nomeAlvoLimpo + "'");
                resumoDiff.append(String.format("[%s] Atualizar dimensões e peso de %s.\n",
                        item.getIdItem(), nomeAlvoLimpo));
            }

            return resumoDiff.length() > 0
                    ? resumoDiff.toString().trim()
                    : "Produto localizado, mas nenhum novo valor (vv>, novo_nm>, dim>) foi informado.";
        }

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            Pessoa p = localizarPessoaUnica(id, termo);
            if (p == null) {
                return "Não encontrei a pessoa '" + (id != null ? "#" + id : termo) + "'.";
            }
            String nomePessoaLimpo = limparNomeExibicao(p.getNome(), null);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "PessoaDAO", p.getId(), nomePessoaLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }
            StringBuilder sb = new StringBuilder();
            if (attrs.containsKey("novo_nm")) {
                String novoNm = attrs.get("novo_nm");
                Map<String, Object> params = Map.of("idPessoa", p.getId(), "nome", novoNm);
                ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_DADOS_ATUALIZAR, params,
                        "Alterar nome de '" + nomePessoaLimpo + "' para '" + novoNm + "'");
                sb.append(String.format("[%s] Alterar nome de %s para %s.\n",
                        item.getIdItem(), nomePessoaLimpo, novoNm));
            }
            if (attrs.containsKey("val") || attrs.containsKey("vl")) {
                String novoLim = obter(attrs, "val", obter(attrs, "vl", "0.00"));
                Map<String, Object> params = Map.of("idPessoa", p.getId(), "novoLimite", novoLim);
                ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE, params,
                        "Alterar limite de crédito de '" + nomePessoaLimpo + "' para R$ " + novoLim);
                sb.append(String.format("[%s] Alterar limite de crédito de %s para R$ %s.\n",
                        item.getIdItem(), nomePessoaLimpo, novoLim));
            }
            return sb.toString().trim();
        }

        if ("UM".equals(entidade)) {
            String sigla = obter(attrs, "nm", "UN").toUpperCase(Locale.ROOT);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sigla", sigla);
            params.put("nome", obter(attrs, "desc", sigla));
            params.put("descricao", obter(attrs, "desc", sigla));
            if (attrs.containsKey("val")) params.put("base", attrs.get("val"));
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_UNIDADE_ATUALIZAR, params,
                    "Atualizar unidade de medida " + sigla);
            return String.format("[%s] Atualizar a unidade de medida %s.", item.getIdItem(), sigla);
        }

        if ("NOTAPAGAMENTO".equals(entidade) || "PARCELAPAGAMENTO".equals(entidade)) {
            String notasAlvo = obter(attrs, "notas", obter(attrs, "id_pag", obter(attrs, "id", "1")));
            List<String> baixas = subAdd.getOrDefault("BAIXA", subAdd.getOrDefault("PAGAMENTO", List.of()));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("notasReferenciadas", notasAlvo);
            params.put("baixas", baixas);
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PAGAMENTO_QUITAR_TITULO_OU_PARCELA, params,
                    "Dar baixa/quitar títulos " + notasAlvo);
            return String.format("[%s] Dar baixa/quitação nas notas %s.", item.getIdItem(), notasAlvo);
        }

        return "Atualização adicionada à lista de espera.";
    }

    // ==================== EXCLUSÃO (D= -> FICA EM ESPERA NA BANDEJA) ====================

    private String processarExclusao(
            String entidade,
            Map<String, String> attrs,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja) {

        String termo = obter(attrs, "nm", "");
        Long id = parseLongSafe(obter(attrs, "id", null));

        if (entidade.startsWith("PRODUTO")) {
            Produto alvo = localizarProdutoUnico(id, termo);
            if (alvo == null) {
                return "Não encontrei o produto '" + (id != null ? "#" + id : termo) + "' no catálogo.";
            }

            BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(alvo.getId());
            boolean temEstoque = saldo != null && saldo.compareTo(BigDecimal.ZERO) > 0;
            List<String> blocoPerda = subRem.getOrDefault("NOTAPERDA", subRem.getOrDefault("PERDA", List.of()));
            boolean solicitouAbatimentoPerda = !blocoPerda.isEmpty() || "isento".equalsIgnoreCase(obter(attrs, "resp", ""));
            String umSigla = alvo.getUm() != null ? alvo.getUm().getSigla() : "UN";
            String nomeAlvoLimpo = limparNomeExibicao(alvo.getNome(), umSigla);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "ProdutoDAO", alvo.getId(), nomeAlvoLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }

            if (temEstoque && !solicitouAbatimentoPerda) {
                return String.format("Exclusão bloqueada preventivamente:\n"
                                + "  O produto #%d - %s ainda possui %s %s em estoque.\n"
                                + "  Só é permitido excluir produtos com estoque zerado.\n"
                                + "  Para dar baixa no estoque como perda (isentando o fornecedor) e excluir em seguida, envie:\n"
                                + "  $$ D= #Produto id> %d, -- #NotaPerda[ q> %s, resp> isento, mot> Baixa para exclusao ];",
                        alvo.getId(), nomeAlvoLimpo,
                        saldo.stripTrailingZeros().toPlainString(),
                        umSigla,
                        alvo.getId(), saldo.stripTrailingZeros().toPlainString());
            }

            StringBuilder sb = new StringBuilder();
            if (temEstoque) {
                List<Item<? extends Produto>> lotes = roteador.estoque().listarLotesDisponiveisFIFO(alvo.getId());
                for (Item<? extends Produto> lote : lotes) {
                    if (lote.getQuantidadeDisponivel() != null && lote.getQuantidadeDisponivel().compareTo(BigDecimal.ZERO) > 0) {
                        Map<String, Object> paramsPerda = new LinkedHashMap<>();
                        paramsPerda.put("idLote", lote.getId());
                        paramsPerda.put("quantidade", lote.getQuantidadeDisponivel().toPlainString());
                        paramsPerda.put("motivo", "Abatimento total para exclusão de produto [Fornecedor Isento de Ressarcimento]");
                        ItemBandeja itemPerda = bandeja.adicionarOuAtualizar(
                                RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                                paramsPerda,
                                String.format("Baixar Nota de Perda (Lote #%d, Qtd: %s) - Fornecedor Isento de Ressarcimento",
                                        lote.getId(), lote.getQuantidadeDisponivel().stripTrailingZeros().toPlainString()));
                        sb.append(String.format("[%s] Abater %s %s do lote #%d via Nota de Perda (Fornecedor Isento de Ressarcimento).\n",
                                itemPerda.getIdItem(),
                                lote.getQuantidadeDisponivel().stripTrailingZeros().toPlainString(),
                                umSigla,
                                lote.getId()));
                    }
                }
            }

            Map<String, Object> paramsDel = new LinkedHashMap<>();
            paramsDel.put("idProduto", alvo.getId());
            paramsDel.put("nomeProduto", alvo.getNome());
            ItemBandeja itemDel = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
                    paramsDel,
                    "Excluir o produto '" + nomeAlvoLimpo + "' (#" + alvo.getId() + ")");
            sb.append(String.format("[%s] Excluir o produto #%d - %s após zerar o estoque.",
                    itemDel.getIdItem(), alvo.getId(), nomeAlvoLimpo));
            return sb.toString().trim();
        }

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            Pessoa p = localizarPessoaUnica(id, termo);
            if (p == null) {
                return "Não encontrei a pessoa '" + (id != null ? "#" + id : termo) + "'.";
            }
            String nomePessoaLimpo = limparNomeExibicao(p.getNome(), null);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "PessoaDAO", p.getId(), nomePessoaLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }
            Map<String, Object> params = Map.of("idPessoa", p.getId());
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PESSOA_CLIENTE_EXCLUIR,
                    params,
                    "Excluir cadastro de '" + nomePessoaLimpo + "' (#" + p.getId() + ")");
            return String.format("[%s] Excluir o cadastro de #%d - %s.",
                    item.getIdItem(), p.getId(), nomePessoaLimpo);
        }

        if ("UM".equals(entidade)) {
            String sigla = obter(attrs, "nm", "UN").toUpperCase(Locale.ROOT);
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_UNIDADE_EXCLUIR,
                    Map.of("sigla", sigla),
                    "Excluir unidade de medida " + sigla);
            return String.format("[%s] Excluir a unidade de medida %s.", item.getIdItem(), sigla);
        }

        if ("FORMAPAGAMENTO".equals(entidade)) {
            String cod = obter(attrs, "cod", termo).toUpperCase(Locale.ROOT);
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PAGAMENTO_FORMA_EXCLUIR,
                    Map.of("codigo", cod),
                    "Excluir forma de pagamento " + cod);
            return String.format("[%s] Excluir a forma de pagamento %s.", item.getIdItem(), cod);
        }

        return "Exclusão adicionada à lista de espera.";
    }

    // ==================== EDIÇÃO DE INSTRUÇÃO EM MEMÓRIA (U= id> OP-X) ====================

    @SuppressWarnings("unchecked")
    private String atualizarInstrucaoEmMemoria(
            String idOp,
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja) {

        ItemBandeja item = bandeja.buscarItemPorIdOuIndice(idOp);
        if (item == null) {
            return "Não encontrei a instrução " + idOp + " na lista de espera.";
        }

        Map<String, Object> mod = item.getParametrosModificaveis();
        if (attrs.containsKey("dt")) {
            LocalDate novaData = resolverDataRelativa(attrs.get("dt"));
            mod.put("dataNota", novaData.toString());
        }
        if (attrs.containsKey("vv") || attrs.containsKey("val") || attrs.containsKey("vl")) {
            String v = obter(attrs, "vv", obter(attrs, "val", obter(attrs, "vl", "0.00")));
            if (mod.containsKey("novoPreco")) mod.put("novoPreco", v);
            else if (mod.containsKey("precoVenda")) mod.put("precoVenda", v);
            else mod.put("valor", v);
        }
        if (attrs.containsKey("novo_nm")) {
            mod.put("novoNome", attrs.get("novo_nm"));
        }

        if (mod.get("itens") instanceof List<?> listaItensRaw) {
            List<Map<String, Object>> listaItens = (List<Map<String, Object>>) listaItensRaw;
            for (String linhaAdd : subAdd.getOrDefault("PRODUTO", List.of())) {
                Map<String, String> pa = extrairAtributos(linhaAdd.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String nm = obter(pa, "nm", "Produto");
                Produto p = localizarProdutoUnico(parseLongSafe(obter(pa, "id", null)), nm);
                Map<String, Object> novoItem = new LinkedHashMap<>();
                if (p != null) {
                    novoItem.put("idProduto", p.getId());
                    novoItem.put("nomeProduto", p.getNome());
                } else {
                    novoItem.put("nomeProduto", nm);
                }
                novoItem.put("quantidade", obter(pa, "q", "1"));
                novoItem.put("valorUnitario", obter(pa, "vc", obter(pa, "vv", obter(pa, "val", "0.00"))));
                listaItens.add(novoItem);
            }
            for (String linhaRem : subRem.getOrDefault("PRODUTO", List.of())) {
                Map<String, String> pa = extrairAtributos(linhaRem.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String nmRem = obter(pa, "nm", "").toLowerCase(Locale.ROOT);
                listaItens.removeIf(m -> String.valueOf(m.getOrDefault("nomeProduto", "")).toLowerCase(Locale.ROOT).contains(nmRem));
            }
        }

        List<String> faltantes = ManualInstrucoesIA.obrigatoriosFaltando(item.getRota(), mod);
        item.mesclarParametros(Map.of(), faltantes);
        return String.format("Atualizei a instrução [%s] na lista de espera: %s.",
                item.getIdItem(), item.getParametros());
    }

    // ==================== LIMPEZA E DESDUPLICAÇÃO DE EXIBIÇÃO ====================

    /**
     * Remove sufixos numéricos aleatórios de suítes de teste (ex: "#64884", "1794406169489")
     * e unidades redundantes no nome (ex: "(KG)" quando a unidade já é exibida ao final).
     */
    public static String limparNomeExibicao(String nome, String siglaUm) {
        if (nome == null) return "";
        String limpo = nome
                .replaceAll("\\s+#[0-9]{3,}\\b", "")
                .replaceAll("\\s+[0-9]{10,}\\b", "")
                .trim();
        if (siglaUm != null && !siglaUm.isBlank()) {
            limpo = limpo.replaceAll("(?i)\\s*\\(" + Pattern.quote(siglaUm.trim()) + "\\)\\s*$", "").trim();
        }
        limpo = limpo.replaceAll("(?i)\\s*\\((UN|KG|LT|MT|CX|PC)\\)\\s*$", "").trim();
        return limpo.isEmpty() ? nome.trim() : limpo;
    }

    /**
     * Filtra artefatos temporários de teste e agrupa/desduplica clones gerados por suítes automatizadas,
     * mantendo apenas o registro canônico (primeiro ID) de cada produto para exibição limpa ao usuário.
     */
    public static List<Produto> filtrarProdutosExibicaoLimpa(List<Produto> produtos) {
        if (produtos == null || produtos.isEmpty()) return List.of();
        Map<String, Produto> unicosPorNome = new LinkedHashMap<>();
        for (Produto p : produtos) {
            if (p == null || p.getNome() == null) continue;
            String nLower = p.getNome().toLowerCase(Locale.ROOT);
            if (nLower.contains("smartphone teste foto")
                    || nLower.contains("produto estágio bandeja")
                    || nLower.contains("produto fantasma")) {
                continue;
            }
            String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
            String chaveCanonica = BuscaPalavrasParciaisUtil.normalizar(limparNomeExibicao(p.getNome(), umSigla));
            if (!chaveCanonica.isBlank()) {
                unicosPorNome.putIfAbsent(chaveCanonica, p);
            }
        }
        return new ArrayList<>(unicosPorNome.values());
    }

    /**
     * Desduplica pessoas geradas repetidamente por suítes de teste (mantendo o primeiro registro de cada nome limpo).
     */
    public static List<Pessoa> filtrarPessoasExibicaoLimpa(List<Pessoa> pessoas) {
        if (pessoas == null || pessoas.isEmpty()) return List.of();
        Map<String, Pessoa> unicasPorNome = new LinkedHashMap<>();
        for (Pessoa p : pessoas) {
            if (p == null || p.getNome() == null) continue;
            String chaveCanonica = BuscaPalavrasParciaisUtil.normalizar(limparNomeExibicao(p.getNome(), null));
            if (!chaveCanonica.isBlank()) {
                unicasPorNome.putIfAbsent(chaveCanonica, p);
            }
        }
        return new ArrayList<>(unicasPorNome.values());
    }

    // ==================== AUXILIARES ====================

    private static List<String> dividirForaDeColchetes(String texto, char delimitador) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        int nivelColchetes = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') nivelColchetes++;
            else if (c == ']' && nivelColchetes > 0) nivelColchetes--;

            if (c == delimitador && nivelColchetes == 0) {
                partes.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        if (!atual.isEmpty()) {
            partes.add(atual.toString());
        }
        return partes;
    }

    private static Map<String, String> extrairAtributos(String segmento) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (segmento == null || segmento.isBlank()) return mapa;

        String[] tokens = segmento.split(",");
        for (String tokenBruto : tokens) {
            String t = tokenBruto.trim();
            if (t.isEmpty()) continue;

            Matcher mPadrao = Pattern.compile("^([a-zA-Z0-9_]+)\\s*>\\s*(.+)$").matcher(t);
            if (mPadrao.find()) {
                mapa.put(mPadrao.group(1).toLowerCase(Locale.ROOT), mPadrao.group(2).trim());
                continue;
            }

            Matcher mInvertido = Pattern.compile("^>\\s*([a-zA-Z0-9_]+)\\s+(.+)$").matcher(t);
            if (mInvertido.find()) {
                mapa.put(mInvertido.group(1).toLowerCase(Locale.ROOT), mInvertido.group(2).trim());
            }
        }
        return mapa;
    }

    private static Map<String, String> extrairAtributosPagamentoPosicionalOuChaveado(String linha) {
        String limpa = linha.replaceFirst("^(\\+\\+|--)\\s*", "").trim();
        Map<String, String> attrs = extrairAtributos(limpa);
        String[] partes = limpa.split(",");
        int pos = 0;
        for (String p : partes) {
            String token = p.trim();
            if (token.isEmpty() || token.contains(">")) continue;
            if (pos == 0 && !attrs.containsKey("nm")) {
                attrs.put("nm", token);
                pos++;
            } else if (pos <= 1 && !attrs.containsKey("val") && !attrs.containsKey("vl") && token.matches("^[0-9]+(\\.[0-9]+)?$")) {
                attrs.put("val", token);
                pos = 2;
            } else if (!attrs.containsKey("dt")) {
                attrs.put("dt", token);
            }
        }
        return attrs;
    }

    public static LocalDate resolverDataRelativa(String expr) {
        if (expr == null || expr.isBlank()) return LocalDate.now();
        String s = expr.trim().toLowerCase(Locale.ROOT);
        if ("hoje".equals(s)) return LocalDate.now();
        if ("ontem".equals(s)) return LocalDate.now().minusDays(1);
        if ("amanha".equals(s) || "amanhã".equals(s)) return LocalDate.now().plusDays(1);

        Matcher mAtras = Pattern.compile("^<\\s*([0-9]+)$").matcher(s);
        if (mAtras.find()) {
            return LocalDate.now().minusDays(Long.parseLong(mAtras.group(1)));
        }
        Matcher mFrente = Pattern.compile("^([0-9]+)\\s*>$").matcher(s);
        if (mFrente.find()) {
            return LocalDate.now().plusDays(Long.parseLong(mFrente.group(1)));
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private static String descreverDataAmigavel(String exprBruta, LocalDate dataResolvida) {
        if (exprBruta == null || exprBruta.isBlank() || "hoje".equalsIgnoreCase(exprBruta.trim())) {
            return "hoje";
        }
        String s = exprBruta.trim().toLowerCase(Locale.ROOT);
        if ("ontem".equals(s)) return "ontem";
        if (s.startsWith("<")) return "há " + s.substring(1).trim() + " dias";
        if (s.endsWith(">")) return "daqui a " + s.substring(0, s.length() - 1).trim() + " dias";
        return dataResolvida.toString();
    }

    private static String descreverPapelPessoa(Pessoa p) {
        if (p.isCliente() && p.isFornecedor()) return "Cliente e Fornecedor";
        if (p.isFornecedor()) return "Fornecedor";
        return "Cliente";
    }

    private static String moeda(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private Produto localizarProdutoUnico(Long id, String termo) {
        if (id != null) {
            return roteador.produtos().buscarPorId(id);
        }
        if (termo == null || termo.isBlank()) return null;
        Produto exato = roteador.produtos().buscarPorNome(termo);
        if (exato != null) return exato;
        List<Produto> candidatos = BuscaPalavrasParciaisUtil.filtrarEOrdenar(
                roteador.produtos().listarTodosProdutos(), Produto::getNome, termo);
        return candidatos.isEmpty() ? null : candidatos.get(0);
    }

    private Pessoa localizarPessoaUnica(Long id, String termo) {
        if (id != null) {
            return roteador.pessoas().buscarPorId(id);
        }
        if (termo == null || termo.isBlank()) return null;
        List<Pessoa> todas = new ArrayList<>();
        todas.addAll(roteador.pessoas().listarFornecedores());
        for (Pessoa c : roteador.pessoas().listarClientes()) {
            if (todas.stream().noneMatch(x -> Objects.equals(x.getId(), c.getId()))) {
                todas.add(c);
            }
        }
        List<Pessoa> filtradas = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todas, Pessoa::getNome, termo);
        return filtradas.isEmpty() ? null : filtradas.get(0);
    }

    private <T> boolean temEmpateDeSigla(List<T> filtrados, java.util.function.Function<T, String> extrator, String termo) {
        if (filtrados.size() < 2) return false;
        int s1 = BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(extrator.apply(filtrados.get(0)), termo);
        int s2 = BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(extrator.apply(filtrados.get(1)), termo);
        return s1 == s2 && s1 < 1000;
    }

    private static void aplicarDimensoes(Map<String, String> attrs, Map<String, Object> params) {
        String dim = obter(attrs, "dim", "10x10x10");
        String[] partes = dim.toLowerCase(Locale.ROOT).split("x");
        params.put("alturaCm", partes.length > 0 ? partes[0].trim() : "10.0");
        params.put("larguraCm", partes.length > 1 ? partes[1].trim() : "10.0");
        params.put("comprimentoCm", partes.length > 2 ? partes[2].trim() : "10.0");
        params.put("pesoBrutoKg", obter(attrs, "pb", "1.0"));
        params.put("pesoLiquidoKg", obter(attrs, "pl", "0.9"));
    }

    private static String normalizarCodigoForma(String raw) {
        if (raw == null || raw.isBlank()) return "PIX";
        String s = BuscaPalavrasParciaisUtil.normalizar(raw).toUpperCase(Locale.ROOT);
        if (s.contains("DIN")) return "DINHEIRO";
        if (s.contains("PIX")) return "PIX";
        if (s.contains("CRED")) return "CARTAO_CREDITO";
        if (s.contains("DEB")) return "CARTAO_DEBITO";
        if (s.contains("BOL")) return "BOLETO";
        return raw.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
    }

    private static String obter(Map<String, String> map, String chave, String padrao) {
        String v = map.get(chave);
        return v != null && !v.isBlank() ? v.trim() : padrao;
    }

    private static Long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim().replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimalSafe(String s, BigDecimal padrao) {
        if (s == null || s.isBlank()) return padrao;
        try {
            return new BigDecimal(s.trim().replace(",", "."));
        } catch (Exception e) {
            return padrao;
        }
    }
}
