package br.com.senac.integracao.ia;

import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine;
import br.com.senac.integracao.ia.estrategia.ContextoCompilacao;
import br.com.senac.integracao.ia.ferramentas.ConversorData;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.ResolvedorPessoa;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.ErroSintaxe;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.integracao.ia.parser.ParserInstrucao;
import br.com.senac.integracao.ia.parser.TipoOperacao;
import br.com.senac.integracao.ia.registro.RegistroEstrategias;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.time.LocalDate;
import java.util.*;

/**
 * Compilador determinístico e orquestrador da DSL {@code $$}.
 *
 * <p>Refatorado segundo os princípios SOLID:
 * <ul>
 *   <li><b>Single Responsibility:</b> Orquestra o ciclo de vida do comando (parse AST, concorrência e bandeja),
 *       delegando a lógica de negócio para as estratégias de domínio ({@link br.com.senac.integracao.ia.estrategia.EstrategiaInstrucao}).</li>
 *   <li><b>Open/Closed:</b> Novas entidades e regras são adicionadas via {@link RegistroEstrategias} sem modificar esta classe.</li>
 * </ul>
 *
 * <p>Regras de execução:
 * <ul>
 *   <li><b>Consultas ({@code R=}):</b> Executadas imediatamente, em sequência e na ordem escrita
 *       ({@link ConcorrenciaSeguraEngine}), retornando os dados formatados sem fechar nem interferir
 *       nas instruções em espera na {@link BandejaOperacoesContexto}.</li>
 *   <li><b>Mutações ({@code C=}, {@code U=}, {@code D=}):</b> Validadas sequencialmente e mantidas em espera
 *       na {@link BandejaOperacoesContexto} até a confirmação explícita ({@code SIM}) ou descarte ({@code NAO} / {@code close::}).</li>
 * </ul>
 */
public class CompiladorInstrucoesCurtaService {

    private final RoteadorController roteador;
    private final ConcorrenciaSeguraEngine concorrenciaEngine;
    private final ParserInstrucao parserInstrucao = new ParserInstrucao();
    private final RegistroEstrategias registroEstrategias;
    private final Map<String, RetornoChatBotUI.FocoBuscaSessao> focosPorSessao = new java.util.concurrent.ConcurrentHashMap<>();

    public CompiladorInstrucoesCurtaService(RoteadorController roteador) {
        this(roteador, ConcorrenciaSeguraEngine.getInstancia(), RegistroEstrategias.criarPadrao());
    }

    public CompiladorInstrucoesCurtaService(RoteadorController roteador, ConcorrenciaSeguraEngine concorrenciaEngine) {
        this(roteador, concorrenciaEngine, RegistroEstrategias.criarPadrao());
    }

    public CompiladorInstrucoesCurtaService(
            RoteadorController roteador,
            ConcorrenciaSeguraEngine concorrenciaEngine,
            RegistroEstrategias registroEstrategias) {
        this.roteador = roteador;
        this.concorrenciaEngine = concorrenciaEngine;
        this.registroEstrategias = registroEstrategias != null ? registroEstrategias : RegistroEstrategias.criarPadrao();
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

        // Validação Atômica de Sintaxe em memória antes de qualquer inserção na bandeja
        List<NoInstrucao> nos;
        try {
            nos = parserInstrucao.parsear(comandoBruto);
        } catch (ErroSintaxe e) {
            return e.formatarMensagemAmigavel();
        }

        Map<Integer, String> resultadosOrdenados = new LinkedHashMap<>();
        boolean teveMutacaoNesteTurno = false;
        ContextoCompilacao ctx = new ContextoCompilacao(roteador, foco);

        for (int i = 0; i < nos.size(); i++) {
            final NoInstrucao no = nos.get(i);
            if (no.getOperacao() == TipoOperacao.R || no.getOperacao() == TipoOperacao.ABRIR_JANELA) {
                try {
                    resultadosOrdenados.put(i, concorrenciaEngine.executar(
                            () -> executarNo(no, bandeja, ctx),
                            null));
                } catch (Exception e) {
                    resultadosOrdenados.put(i, ContextoIABuilder.propriedade("ia.msg.erro.generico",
                            "Não foi possível concluir a consulta solicitada."));
                }
            } else {
                teveMutacaoNesteTurno = true;
                resultadosOrdenados.put(i, executarNo(no, bandeja, ctx));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nos.size(); i++) {
            String res = resultadosOrdenados.get(i);
            if (res != null && !res.isBlank()) {
                sb.append(res).append("\n\n");
            }
        }

        if (bandeja.temItens()) {
            sb.append(bandeja.formatarVisualizacao());
        }
        return sb.toString().trim();
    }

    private String executarNo(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        // 0. Abertura de janela gráfica JavaFX
        if (no.getOperacao() == TipoOperacao.ABRIR_JANELA) {
            RetornoChatBotUI.ComandoJanelaUI cmdJanela = RetornoChatBotUI.resolverComandoJanela(no.getTextoOriginal(), bandeja);
            if (cmdJanela != null) {
                ctx.foco().registrarRecord(cmdJanela);
                return cmdJanela.formatarTexto();
            }
            return ContextoIABuilder.formatarPropriedade("ia.ui.janela.nao_mapeada",
                    "Janela '%s' não encontrada no catálogo gráfico.", no.getTextoOriginal().trim());
        }

        // 1. Abortar instrução específica da bandeja: close<OP-1> ou close<>
        if (no.getOperacao() == TipoOperacao.CLOSE) {
            String idAlvo = no.getOuPadrao("id", "");
            ItemBandeja removido = bandeja.removerItemPorIdOuIndice(idAlvo);
            if (removido != null) {
                return String.format("Instrução [%s] removida da lista de espera (%s).",
                        removido.getIdItem(), removido.getDescricaoAmigavel());
            }
            return ContextoIABuilder.formatarPropriedade("ia.erro.instrucao.nao_encontrada",
                    "Não encontrei a instrução %s na lista de espera.", idAlvo.isEmpty() ? "recente" : idAlvo);
        }

        // 2. Edição de instrução em memória na bandeja: U= id> OP-1
        String idAttr = no.getOuPadrao("id", "");
        if (no.getOperacao() == TipoOperacao.U && idAttr.toUpperCase(Locale.ROOT).startsWith("OP-")) {
            return atualizarInstrucaoEmMemoria(no, bandeja);
        }

        // 3. Despacho polimórfico para a Estratégia de Domínio correspondente
        var estrategiaOpt = registroEstrategias.obter(no.getEntidade());
        if (estrategiaOpt.isEmpty()) {
            return ContextoIABuilder.formatarPropriedade("ia.erro.entidade.desconhecida",
                    "Entidade '#%s' não reconhecida.", no.getEntidade());
        }

        var estrategia = estrategiaOpt.get();
        return switch (no.getOperacao()) {
            case C -> estrategia.compilarCriacao(no, bandeja, ctx);
            case U -> estrategia.compilarAtualizacao(no, bandeja, ctx);
            case D -> estrategia.compilarExclusao(no, bandeja, ctx);
            case R -> estrategia.processarConsulta(no, ctx);
            default -> ContextoIABuilder.formatarPropriedade("ia.erro.operacao.desconhecida",
                    "Operação '%s' não reconhecida.", no.getOperacao());
        };
    }

    @SuppressWarnings("unchecked")
    private String atualizarInstrucaoEmMemoria(NoInstrucao no, BandejaOperacoesContexto bandeja) {
        String idOp = no.getOuPadrao("id", "");
        ItemBandeja item = bandeja.buscarItemPorIdOuIndice(idOp);
        if (item == null) {
            return "Não encontrei a instrução " + idOp + " na lista de espera.";
        }

        Map<String, String> attrs = no.getAtributos();
        Map<String, Object> mod = item.getParametrosModificaveis();
        if (attrs.containsKey("dt")) {
            LocalDate novaData = ConversorData.resolverDataRelativa(attrs.get("dt"));
            mod.put("dataNota", novaData.toString());
        }
        if (attrs.containsKey("vv") || attrs.containsKey("val") || attrs.containsKey("vl")) {
            String v = ExtratorAtributos.obter(attrs, "vv", ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00")));
            if (mod.containsKey("novoPreco")) mod.put("novoPreco", v);
            else if (mod.containsKey("precoVenda")) mod.put("precoVenda", v);
            else mod.put("valor", v);
        }
        if (attrs.containsKey("novo_nm")) {
            mod.put("novoNome", attrs.get("novo_nm"));
        }

        if (mod.get("itens") instanceof List<?> listaItensRaw) {
            List<Map<String, Object>> listaItens = (List<Map<String, Object>>) listaItensRaw;
            for (NoInstrucao sub : no.getSubInstrucoesAdicionar()) {
                Map<String, String> pa = sub.getAtributos();
                String nm = ExtratorAtributos.obter(pa, "nm", "Produto");
                Produto p = ResolvedorProduto.localizarProdutoUnico(roteador, ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(pa, "id", null)), nm);
                Map<String, Object> novoItem = new LinkedHashMap<>();
                if (p != null) {
                    novoItem.put("idProduto", p.getId());
                    novoItem.put("nomeProduto", p.getNome());
                } else {
                    novoItem.put("nomeProduto", nm);
                }
                novoItem.put("quantidade", ExtratorAtributos.obter(pa, "q", "1"));
                novoItem.put("valorUnitario", ExtratorAtributos.obter(pa, "vc", ExtratorAtributos.obter(pa, "vv", ExtratorAtributos.obter(pa, "val", "0.00"))));
                listaItens.add(novoItem);
            }
            for (NoInstrucao sub : no.getSubInstrucoesRemover()) {
                Map<String, String> pa = sub.getAtributos();
                String nmRem = ExtratorAtributos.obter(pa, "nm", "").toLowerCase(Locale.ROOT);
                listaItens.removeIf(m -> String.valueOf(m.getOrDefault("nomeProduto", "")).toLowerCase(Locale.ROOT).contains(nmRem));
            }
        }

        List<String> faltantes = ManualInstrucoesIA.obrigatoriosFaltando(item.getRota(), mod);
        item.mesclarParametros(Map.of(), faltantes);
        return String.format("Atualizei a instrução [%s] na lista de espera: %s.",
                item.getIdItem(), item.getParametros());
    }

    // ==================== MÉTODOS PÚBLICOS DE COMPATIBILIDADE E CONSULTA ====================

    public String processarLeitura(String idSessao, String entidade, Map<String, String> attrs) {
        NoInstrucao no = new NoInstrucao(TipoOperacao.R, entidade, attrs, List.of(), List.of(), 0, 0, "");
        ContextoCompilacao ctx = new ContextoCompilacao(roteador, obterFocoSessao(idSessao));
        return registroEstrategias.obter(entidade)
                .map(e -> e.processarConsulta(no, ctx))
                .orElse("Consulta concluída.");
    }

    public String processarLeitura(String entidade, Map<String, String> attrs) {
        return processarLeitura("default", entidade, attrs);
    }

    public String processarLeituraComFocoECascata(
            String entidade,
            Map<String, String> attrs,
            List<String> alvosCascata,
            RetornoChatBotUI.FocoBuscaSessao foco) {
        NoInstrucao no = new NoInstrucao(TipoOperacao.R, entidade, attrs, List.of(), alvosCascata, 0, 0, "");
        ContextoCompilacao ctx = new ContextoCompilacao(roteador, foco);
        return registroEstrategias.obter(entidade)
                .map(e -> e.processarConsulta(no, ctx))
                .orElse("Consulta concluída.");
    }

    public static String limparNomeExibicao(String nome, String siglaUm) {
        return ResolvedorProduto.limparNomeExibicao(nome, siglaUm);
    }

    public static List<Produto> filtrarProdutosExibicaoLimpa(List<Produto> produtos) {
        return ResolvedorProduto.filtrarProdutosExibicaoLimpa(produtos);
    }

    public static List<Pessoa> filtrarPessoasExibicaoLimpa(List<Pessoa> pessoas) {
        return ResolvedorPessoa.filtrarPessoasExibicaoLimpa(pessoas);
    }

    public static LocalDate resolverDataRelativa(String expr) {
        return ConversorData.resolverDataRelativa(expr);
    }
}
