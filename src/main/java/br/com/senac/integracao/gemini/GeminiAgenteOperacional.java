package br.com.senac.integracao.gemini;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.CompiladorInstrucoesCurtaService;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.FormatadorSaidaIA;
import br.com.senac.integracao.ia.InterpretadorRespostaIA;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.ProvedorIA.ChamadaFerramenta;
import br.com.senac.integracao.ia.ProvedorIA.PedidoIA;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;
import br.com.senac.integracao.ia.ProvedorIAFactory;
import br.com.senac.integracao.ia.RelatorioExecucaoBandeja;
import br.com.senac.integracao.ia.parser.DecompositorPromptLongo;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;
import br.com.senac.integracao.ia.validacao.ValidadorLoteBandeja;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Agente Operacional do Sistema Sazonalisei.
 * Orquestra: Usuário -> ProvedorIA (qualquer IA) -> validação -> Bandeja de Contexto (Human-in-the-Loop)
 * -> execução sequencial com trava por recurso -> RoteadorController.
 *
 * Funciona de dois jeitos:
 *  1. Com provedor de API (Gemini API, Ollama/OpenAI-compatível): o sistema chama a IA sozinho.
 *  2. Sem API (ia.provedor=manual): o usuário copia o manual para qualquer IA (ex: Gemini do celular),
 *     e cola a resposta em {@link #processarRespostaExterna(String, String)}.
 */
public class GeminiAgenteOperacional {

    private final GeminiClientService geminiClient;
    private final RoteadorController roteadorController;
    private final ConfirmacaoAcaoGatekeeper gatekeeper;
    private final ConcorrenciaSeguraEngine concorrenciaEngine;
    private final CompiladorInstrucoesCurtaService compiladorInstrucoesCurta;
    private final br.com.senac.integracao.ia.bandeja.ExecutorBandeja executorBandeja;
    private final PreProcessadorMensagem preProcessador = new PreProcessadorMensagem();
    private volatile ProvedorIA provedor;
    private final ThreadLocal<java.util.function.Consumer<String>> listenerProgresso = new ThreadLocal<>();

    public void definirListenerProgresso(java.util.function.Consumer<String> listener) {
        if (listener == null) {
            listenerProgresso.remove();
        } else {
            listenerProgresso.set(listener);
        }
    }

    public void limparListenerProgresso() {
        listenerProgresso.remove();
    }

    private void notificarProgresso(String etapa) {
        java.util.function.Consumer<String> listener = listenerProgresso.get();
        if (listener != null && etapa != null && !etapa.isBlank()) {
            try {
                listener.accept(etapa);
            } catch (Exception ignored) {}
        }
    }

    // Bandejas de Operações e Contexto ativas por sessão do usuário
    private final Map<String, BandejaOperacoesContexto> bandejasPorSessao = new ConcurrentHashMap<>();
    // Fila de tickets aguardando confirmação legado/MCP, por sessão
    private final Map<String, Deque<String>> ticketsPendentesPorSessao = new ConcurrentHashMap<>();
    // Histórico de conversação estruturado multi-turn por sessão
    private final Map<String, List<Map<String, Object>>> historicoPorSessao = new ConcurrentHashMap<>();
    // Rastreamento opcional do fluxo (pré-processamento -> IA -> pós-processamento -> retorno)
    private final br.com.senac.integracao.ia.LogFluxoIAContexto logFluxo = new br.com.senac.integracao.ia.LogFluxoIAContexto();

    private List<Map<String, Object>> obterHistoricoSessao(String idSessao) {
        if (idSessao == null || idSessao.isBlank()) return Collections.emptyList();
        List<Map<String, Object>> h = historicoPorSessao.get(idSessao);
        if (h == null || h.isEmpty()) return Collections.emptyList();
        synchronized (h) {
            return new ArrayList<>(h);
        }
    }

    private void registrarNoHistorico(String idSessao, String papel, String texto) {
        if (idSessao == null || idSessao.isBlank() || texto == null || texto.isBlank()) return;
        List<Map<String, Object>> lista = historicoPorSessao.computeIfAbsent(idSessao, k -> Collections.synchronizedList(new ArrayList<>()));
        Map<String, Object> turno = new LinkedHashMap<>();
        turno.put("role", papel);
        turno.put("parts", List.of(Map.of("text", extrairResumoParaHistorico(texto))));
        lista.add(turno);
        while (lista.size() > 20) {
            lista.remove(0);
        }
    }

    private String extrairResumoParaHistorico(String texto) {
        if (texto == null) return "";
        String semBandeja = removerRodapeBandejaDuplicado(texto);
        return semBandeja.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "")
                .replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "")
                .trim();
    }

    private String removerRodapeBandejaDuplicado(String texto) {
        if (texto == null || texto.isBlank()) return "";
        String limpo = texto
                .replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "");
        int idxBandeja = limpo.indexOf("BANDEJA DE CONTEXTO ATIVA");
        if (idxBandeja >= 0) {
            limpo = limpo.substring(0, idxBandeja);
        }
        return limpo.trim();
    }

    public GeminiAgenteOperacional() {
        this(new GeminiClientService(), new RoteadorController(), ConfirmacaoAcaoGatekeeper.getInstancia(), ConcorrenciaSeguraEngine.getInstancia());
    }

    public GeminiAgenteOperacional(
            GeminiClientService geminiClient,
            RoteadorController roteadorController,
            ConfirmacaoAcaoGatekeeper gatekeeper,
            ConcorrenciaSeguraEngine concorrenciaEngine) {
        this.geminiClient = geminiClient;
        this.roteadorController = roteadorController;
        this.gatekeeper = gatekeeper;
        this.concorrenciaEngine = concorrenciaEngine;
        this.compiladorInstrucoesCurta = new CompiladorInstrucoesCurtaService(roteadorController);
        this.executorBandeja = new br.com.senac.integracao.ia.bandeja.ExecutorBandeja(roteadorController);
        this.provedor = ProvedorIAFactory.criar(geminiClient);
    }

    /**
     * Retorna a Bandeja de Operações e Contexto associada à sessão.
     */
    public BandejaOperacoesContexto obterBandeja(String idSessao) {
        if (idSessao == null || idSessao.isBlank()) idSessao = "default";
        BandejaOperacoesContexto bandeja = bandejasPorSessao.computeIfAbsent(idSessao, BandejaOperacoesContexto::new);
        bandeja.vincularRoteadorValidacao(roteadorController);
        return bandeja;
    }

    /**
     * Retorna o foco conversacional de busca da sessão (contendo a última Nota, Pessoa, Produto e os Java Records gerados para a UI).
     */
    public br.com.senac.integracao.ia.RetornoChatBotUI.FocoBuscaSessao obterFocoSessao(String idSessao) {
        return compiladorInstrucoesCurta.obterFocoSessao(idSessao);
    }

    public CompiladorInstrucoesCurtaService getCompiladorInstrucoesCurta() {
        return compiladorInstrucoesCurta;
    }

    public br.com.senac.integracao.ia.LogFluxoIAContexto getLogFluxo() {
        return logFluxo;
    }

    public void ativarSalvamentoLogs(boolean ativo) {
        logFluxo.setAtivo(ativo);
    }

    public boolean isSalvamentoLogsAtivo() {
        return logFluxo.isAtivo();
    }

    public String consumirLogsFluxoSeBandejaFinalizada(String idSessao) {
        return logFluxo.consumirRelatorioSeBandejaFinalizada(idSessao);
    }

    public String consumirLogsFluxoAoSair(String idSessao) {
        return logFluxo.consumirRelatorioAoSair(idSessao);
    }

    /**
     * Fecha e limpa a janela de contexto da sessão:
     * 1. Limpa e descarta a Bandeja de Contexto
     * 2. Remove o histórico de mensagens multi-turn
     * 3. Descarta eventuais tickets pendentes
     */
    public void fecharJanelaContexto(String idSessao) {
        if (idSessao == null || idSessao.isBlank()) return;
        logFluxo.marcarBandejaFinalizada(idSessao);
        BandejaOperacoesContexto b = bandejasPorSessao.remove(idSessao);
        if (b != null) {
            b.fecharJanela();
        }
        historicoPorSessao.remove(idSessao);
        Deque<String> fila = ticketsPendentesPorSessao.remove(idSessao);
        if (fila != null) {
            for (String tId : fila) {
                gatekeeper.cancelarTicket(tId);
            }
        }
    }

    // ==================== ENTRADAS ====================

    /**
     * Processa a mensagem do usuário e retorna o pacote completo já formatado
     * (texto puro com quebra de linha por contador de palavras, ANSI, HTML e segmentos para JavaFX).
     */
    public FormatadorSaidaIA.RespostaFormatadaIA processarMensagemFormatada(String idSessao, String mensagemUsuario) {
        return FormatadorSaidaIA.formatar(processarMensagem(idSessao, mensagemUsuario));
    }

    public FormatadorSaidaIA.RespostaFormatadaIA processarMensagemFormatada(
            String idSessao,
            String mensagemUsuario,
            FormatadorSaidaIA.ConfiguracaoFormatacaoSaida config) {
        return FormatadorSaidaIA.formatar(processarMensagem(idSessao, mensagemUsuario), config);
    }

    private String finalizarRespostaFormatada(String resposta) {
        return FormatadorSaidaIA.formatarTextoParaIA(resposta);
    }

    /** Mensagem de texto do usuário, interpretada pelo provedor de IA configurado ou pelo compilador $$ local. */
    public String processarMensagem(String idSessao, String mensagemUsuario) {
        String msg = mensagemUsuario != null ? mensagemUsuario.trim() : "";

        // Se o prompt iniciar com $$, atribui imediatamente à classe de compilação de instruções curtas (0 chamadas à IA)
        if (CompiladorInstrucoesCurtaService.ehComandoEncurtado(msg)) {
            BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
            String respostaCompilada = finalizarRespostaFormatada(compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, msg, bandeja, () -> fecharJanelaContexto(idSessao)));
            registrarNoHistorico(idSessao, "user", msg);
            registrarNoHistorico(idSessao, "model", respostaCompilada);
            registrarLogTurnoSeAtivo(idSessao, msg,
                    "Comando curto DSL ($$) detectado pelo pré-roteador local",
                    "(Não enviado à IA — compilado localmente via CompiladorInstrucoesCurtaService)",
                    "Execução/enfileiramento na BandejaOperacoesContexto concluído",
                    respostaCompilada);
            return respostaCompilada;
        }

        // Navegação da bandeja em linguagem simples ("próxima", "executar OP-2", "pular OP-1"):
        // resolvida localmente, sem chamar a IA.
        BandejaOperacoesContexto bandejaAtiva = bandejasPorSessao.get(idSessao);
        if (bandejaAtiva != null && bandejaAtiva.temItens()) {
            var comando = br.com.senac.integracao.ia.bandeja.ComandoBandeja.interpretar(msg);
            if (comando.isPresent()) {
                String brutoBandeja = processarComandoBandeja(idSessao, bandejaAtiva, comando.get());
                String respostaBandeja = finalizarRespostaFormatada(brutoBandeja);
                registrarNoHistorico(idSessao, "user", msg);
                registrarNoHistorico(idSessao, "model", respostaBandeja);
                registrarLogTurnoSeAtivo(idSessao, msg,
                        "Comando de navegação da Bandeja detectado: " + comando.get().acao(),
                        "(Não enviado à IA — resolvido localmente na Bandeja)",
                        "Resultado bruto pós-processamento:\n" + brutoBandeja,
                        respostaBandeja);
                return respostaBandeja;
            }
        }

        if (temConfirmacaoPendente(idSessao)) {
            DecisaoConfirmacao d = DecisaoConfirmacao.interpretar(msg);
            if (d != DecisaoConfirmacao.INDEFINIDA) {
                String brutoConfirmacao = processarRespostaConfirmacao(idSessao, msg);
                String resposta = finalizarRespostaFormatada(brutoConfirmacao);
                registrarNoHistorico(idSessao, "user", msg);
                registrarNoHistorico(idSessao, "model", resposta);
                registrarLogTurnoSeAtivo(idSessao, msg,
                        "Decisão de confirmação detectada: " + d,
                        "(Não enviado à IA — confirmação na BandejaOperacoesContexto)",
                        "Resultado bruto da confirmação:\n" + brutoConfirmacao,
                        resposta);
                return resposta;
            }
            if (bandejaAtiva != null && bandejaAtiva.temItens() && ehPedidoCorrecaoDireta(msg)) {
                List<String> correcoes = bandejaAtiva.corrigirConflitosUnidadeNoLote(roteadorController);
                if (!correcoes.isEmpty()) {
                    bandejaAtiva.vincularRoteadorValidacao(roteadorController);
                    StringBuilder sbCorrecao = new StringBuilder();
                    sbCorrecao.append("✔ Correções aplicadas automaticamente na bandeja (sem perda das demais operações):\n");
                    for (String c : correcoes) {
                        sbCorrecao.append("  • ").append(c).append("\n");
                    }
                    sbCorrecao.append("\n").append(bandejaAtiva.formatarVisualizacao());
                    sbCorrecao.append("\n\nResponda 'sim' para confirmar e executar estas operações ou 'não' para cancelar.");
                    String brutoCorrecao = sbCorrecao.toString();
                    String respostaCorrecao = finalizarRespostaFormatada(brutoCorrecao);
                    registrarNoHistorico(idSessao, "user", msg);
                    registrarNoHistorico(idSessao, "model", respostaCorrecao);
                    registrarLogTurnoSeAtivo(idSessao, msg,
                            "Pedido direto de correção na Bandeja detectado (sem acionar confirmação SIM).",
                            "(Não enviado à IA — auto-correção relacional na BandejaOperacoesContexto)",
                            "Correções aplicadas:\n" + brutoCorrecao,
                            respostaCorrecao);
                    return respostaCorrecao;
                }
            }
        }

        // Busca direta por sigla fora de $$ (ex: "procura produto o.b.a", "procura prodito >oba", "cliente i.s.u")
        // Executada em sequência, sem fechar a bandeja de espera!
        String buscaRapidaSigla = tentarResolverBuscaSiglaForaDeDsl(idSessao, msg);
        if (buscaRapidaSigla != null) {
            String buscaFormatada = finalizarRespostaFormatada(buscaRapidaSigla);
            registrarNoHistorico(idSessao, "user", msg);
            registrarNoHistorico(idSessao, "model", buscaFormatada);
            registrarLogTurnoSeAtivo(idSessao, msg,
                    "Atalho local detectado fora de $$ (sigla/ID/contexto)",
                    "(Não enviado à IA — resolvido localmente)",
                    "Resultado bruto da consulta:\n" + buscaRapidaSigla,
                    buscaFormatada);
            return buscaFormatada;
        }

        String bloqueio = validarProvedor(false);
        if (bloqueio != null) return finalizarRespostaFormatada(bloqueio);

        try {
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
            notificarProgresso("Pré-processando...");
            boolean substituirBandeja = (bandejaAtiva != null && bandejaAtiva.temItens()) && ehIntencaoCorrecaoBandeja(msg);
            var decomp = (!ehPerguntaOuSimulacao(msg))
                    ? DecompositorPromptLongo.decompor(msg)
                    : new DecompositorPromptLongo.ResultadoDecomposicao(false, List.of(), "");
            if (decomp.promptLongo()) {
                return processarPromptLongoEmEtapas(idSessao, msg, decomp, historico, substituirBandeja);
            }
            var preProc = preProcessador.processar(msg);
            String msgParaIA = msg.trim().replaceAll("[ \\t]{2,}", " ");
            if (preProc.temContexto()) {
                msgParaIA = msgParaIA + "\n\n" + preProc.gerarContextoSemantico();
            }
            if (bandejaAtiva != null && bandejaAtiva.temItens()) {
                bandejaAtiva.vincularRoteadorValidacao(roteadorController);
                msgParaIA = msgParaIA + "\n\n" + resumirBandejaParaEdicaoIA(bandejaAtiva);
            }
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            notificarProgresso("IA lendo...");
            RespostaIA respostaIA = provedor.conversar(new PedidoIA(msgParaIA, null, null, historico));
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            // Registra o pedido do usuário antes do loop de follow-up para que consultas encadeadas enxerguem a intenção atual.
            registrarNoHistorico(idSessao, "user", msg);
            String posBruto = processarRespostaIAComLoop(idSessao, respostaIA, 0, substituirBandeja);
            String resposta = finalizarRespostaFormatada(posBruto);
            registrarNoHistorico(idSessao, "model", resposta);
            if (logFluxo.isAtivo()) {
                String resumoPre = !preProc.temContexto()
                        ? "Nenhuma entidade extraída; texto normalizado: " + msg.trim().replaceAll("[ \\t]{2,}", " ")
                        : "Contexto semântico extraído (" + preProc.entidades().size() + " entidade(s), "
                          + preProc.rotasSugeridas().size() + " rota(s) prioritária(s)):\n" + preProc.gerarContextoSemantico();
                String resumoEnvio = "Histórico: " + historico.size() + " turno(s)\n" + msgParaIA;
                String resumoPos = resumirRespostaIAParaLog(respostaIA) + "\nSaída pós-processada (antes da formatação final):\n" + posBruto;
                registrarLogTurnoSeAtivo(idSessao, msg, resumoPre, resumoEnvio, resumoPos, resposta);
            }
            return resposta;
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
                return "Operação cancelada pelo usuário.";
            }
            return finalizarRespostaFormatada(br.com.senac.integracao.ia.ContextoIABuilder.propriedade("ia.msg.erro.generico",
                    "Não consegui processar sua solicitação agora. Tente novamente em instantes."));
        }
    }

    /**
     * Divide prompts longos em sub-prompts menores ordenados por dependência, envia etapa a etapa
     * à IA com o contexto acumulado da bandeja e executa pré-validação relacional após cada etapa.
     */
    private String processarPromptLongoEmEtapas(
            String idSessao,
            String msgOriginal,
            DecompositorPromptLongo.ResultadoDecomposicao decomp,
            List<Map<String, Object>> historico,
            boolean substituirBandeja) throws Exception {
        if (substituirBandeja) {
            limparApenasBandejaParaReconstrucao(idSessao);
        }
        BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
        registrarNoHistorico(idSessao, "user", msgOriginal);

        StringBuilder resumoEtapas = new StringBuilder();
        StringBuilder observacoesIA = new StringBuilder();
        StringBuilder logEnvios = new StringBuilder();
        StringBuilder logRespostas = new StringBuilder();
        int totalEtapas = decomp.etapas().size();

        for (DecompositorPromptLongo.EtapaPrompt etapa : decomp.etapas()) {
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            notificarProgresso("Etapa " + etapa.ordem() + "/" + totalEtapas + ": " + etapa.titulo() + "...");

            var preEtapa = preProcessador.processar(etapa.prompt());
            StringBuilder promptEtapa = new StringBuilder();
            promptEtapa.append("[EXECUÇÃO SEGMENTADA DE PROMPT LONGO - ETAPA ")
                       .append(etapa.ordem()).append("/").append(totalEtapas)
                       .append(" (").append(etapa.titulo()).append(")]\n")
                       .append("Enfileire EXCLUSIVAMENTE as operações desta etapa usando as ferramentas do sistema. ")
                       .append("NÃO repita operações já listadas na bandeja abaixo e use exatamente os nomes/OP-N já criados quando referenciar itens anteriores.\n\n")
                       .append("Instrução da etapa:\n").append(etapa.prompt());
            if (preEtapa.temContexto()) {
                promptEtapa.append("\n\n").append(preEtapa.gerarContextoSemantico());
            }
            if (bandeja.temItens()) {
                promptEtapa.append("\n\n").append(formatarResumoCompactoParaEtapa(bandeja));
                ValidadorLoteBandeja.RelatorioValidacaoLote relPrev = ValidadorLoteBandeja.validar(bandeja, roteadorController);
                promptEtapa.append("\n").append(relPrev.formatarParaIA());
            }

            logEnvios.append("--- ETAPA ").append(etapa.ordem()).append("/").append(totalEtapas)
                     .append(" (").append(etapa.titulo()).append(") ---\n").append(promptEtapa).append("\n");

            int qtdAntes = bandeja.totalItens();
            RespostaIA respEtapa = provedor.conversar(new PedidoIA(promptEtapa.toString(), null, null, historico));
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            logRespostas.append("--- RESPOSTA ETAPA ").append(etapa.ordem()).append(" ---\n")
                        .append(resumirRespostaIAParaLog(respEtapa)).append("\n");

            String avisoEtapa = executarLoopEtapaPromptLongo(idSessao, etapa, promptEtapa.toString(), respEtapa, historico, 0);
            if (avisoEtapa != null && !avisoEtapa.isBlank()) {
                if (observacoesIA.length() > 0) observacoesIA.append("\n");
                observacoesIA.append(avisoEtapa);
            }

            int qtdAdicionada = Math.max(0, bandeja.totalItens() - qtdAntes);
            ValidadorLoteBandeja.RelatorioValidacaoLote valEtapa = ValidadorLoteBandeja.validar(bandeja, roteadorController);
            resumoEtapas.append("  • Etapa ").append(etapa.ordem()).append("/").append(totalEtapas)
                        .append(" (").append(etapa.titulo()).append("): ")
                        .append("+").append(qtdAdicionada).append(" instrução(ões)");
            if (valEtapa.todoValido()) {
                resumoEtapas.append(" [pré-validação OK]\n");
            } else {
                resumoEtapas.append(" [⚠ ").append(valEtapa.totalConflitos()).append(" conflito(s) na bandeja até aqui]\n");
            }
        }

        StringBuilder posBruto = new StringBuilder();
        posBruto.append("⚠ [AVISO PROMPT LONGO]: ").append(decomp.avisoUsuario()).append("\n")
                .append("Evite comandos extensos em um único prompt para não perder o contexto nem diminuir a eficiência.\n\n")
                .append("RESUMO POR ETAPA (COM PRÉ-VALIDAÇÃO INCREMENTAL):\n")
                .append(resumoEtapas.toString().trim());
        if (observacoesIA.length() > 0) {
            posBruto.append("\n\n").append(observacoesIA.toString().trim());
        }
        if (bandeja.temItens()) {
            posBruto.append("\n\n").append(bandeja.formatarVisualizacao());
        }

        String resposta = finalizarRespostaFormatada(posBruto.toString());
        registrarNoHistorico(idSessao, "model", resposta);
        if (logFluxo.isAtivo()) {
            registrarLogTurnoSeAtivo(idSessao, msgOriginal,
                    "Prompt longo decomposto em " + totalEtapas + " etapas ordenadas por dependência.",
                    logEnvios.toString(),
                    logRespostas + "\nSaída pós-processada:\n" + posBruto,
                    resposta);
        }
        return resposta;
    }

    private String executarLoopEtapaPromptLongo(
            String idSessao,
            DecompositorPromptLongo.EtapaPrompt etapa,
            String promptOriginalEtapa,
            RespostaIA resp,
            List<Map<String, Object>> historico,
            int nivelLoop) throws Exception {
        BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
        if (resp == null) return "";
        if (!resp.temChamadas()) {
            String texto = resp.texto() != null ? resp.texto().trim() : "";
            String dsl = extrairBlocoDsl(texto);
            if (dsl != null) {
                compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, dsl, bandeja, () -> fecharJanelaContexto(idSessao));
                return "";
            }
            return "";
        }

        List<ChamadaFerramenta> leituras = new ArrayList<>();
        List<ChamadaFerramenta> escritas = new ArrayList<>();
        for (ChamadaFerramenta c : resp.chamadas()) {
            RotaSistema rota = roteadorController.resolverRota(c.rota());
            GeminiToolRegistry.NivelRiscoOperacao risco = rota != null
                    ? GeminiToolRegistry.classificarRisco(rota)
                    : GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY;
            if (risco == GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY) {
                leituras.add(c);
            } else {
                escritas.add(c);
            }
        }

        StringBuilder retornoLeituras = new StringBuilder();
        for (ChamadaFerramenta c : leituras) {
            if (Thread.currentThread().isInterrupted()) return "";
            String r = despacharChamadaTool(idSessao, c);
            if (r != null && !r.isBlank()) {
                if (retornoLeituras.length() > 0) retornoLeituras.append("\n\n");
                retornoLeituras.append(r);
            }
        }

        StringBuilder avisos = new StringBuilder();
        for (ChamadaFerramenta c : escritas) {
            if (Thread.currentThread().isInterrupted()) return avisos.toString();
            String r = despacharChamadaTool(idSessao, c);
            if (r != null && r.contains("⚠️")) {
                if (avisos.length() > 0) avisos.append("\n");
                avisos.append("Etapa ").append(etapa.ordem()).append(": ").append(r);
            }
        }

        if (escritas.isEmpty() && !leituras.isEmpty() && nivelLoop < 1 && provedor != null) {
            String followUp = promptOriginalEtapa
                    + "\n\nResultados das consultas realizadas para esta etapa:\n"
                    + retornoLeituras
                    + "\n\nCom base nesses dados, chame agora as ferramentas de escrita necessárias para concluir esta etapa ("
                    + etapa.titulo() + ").";
            RespostaIA resp2 = provedor.conversar(new PedidoIA(followUp, null, null, historico));
            String extra = executarLoopEtapaPromptLongo(idSessao, etapa, promptOriginalEtapa, resp2, historico, nivelLoop + 1);
            if (!extra.isBlank()) {
                if (avisos.length() > 0) avisos.append("\n");
                avisos.append(extra);
            }
        }
        return avisos.toString();
    }

    private String formatarResumoCompactoParaEtapa(BandejaOperacoesContexto bandeja) {
        StringBuilder sb = new StringBuilder("[Operações já enfileiradas na Bandeja neste lote]:\n");
        for (ItemBandeja item : bandeja.getItens()) {
            sb.append(" - ").append(item.getIdItem())
              .append(" [").append(item.getRota().name()).append("] ")
              .append(item.getDescricaoAmigavel())
              .append(" | params=").append(item.getParametros()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Detecta comandos de janela (abrir>janela>...), buscas em sequência/contexto ("agora o fornecedor dessa nota",
     * "os produtos dessa nota", "os contatos dele", "os pagamentos dessa nota") e buscas por sigla realizadas fora de {@code $$}
     * e executa imediatamente na thread atual, mantendo intactas eventuais instruções de C=, U=, D= na bandeja.
     */
    private String tentarResolverBuscaSiglaForaDeDsl(String idSessao, String msg) {
        if (msg == null || msg.isBlank()) return null;
        String texto = msg.trim();
        String lower = texto.toLowerCase(Locale.ROOT);

        // 1. Comando direto de abertura de janela JavaFX (ex: abrir>janela>Registrar>nota)
        if (lower.startsWith("abrir>janela") || lower.startsWith("abrir > janela")) {
            return compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, "$$ " + texto + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
        }

        // 2. Busca sequencial contextual em linguagem natural (Nota -> Fornecedor/Cliente -> Contatos -> Produtos -> Pagamentos)
        if (lower.matches("(?s).*\\b(dessa\\s+nota|dessaa\\s+nota|da\\s+nota|nota\\s+atual|desse\\s+fornecedor|desse\\s+cliente|dessa\\s+pessoa|contatos\\s+del[ea])\\b.*")) {
            if (lower.matches("(?s).*\\b(fornecedor|fornecedores)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Fornecedor da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches("(?s).*\\b(cliente|clientes|clinete|clinetes)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Cliente da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches("(?s).*\\b(pessoa|pessoas)\\b.*") && !lower.matches("(?s).*\\b(contato|contatos)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Pessoa da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches("(?s).*\\b(contato|contatos|telefone|whatsapp|email|documento|documentos|registro|registros)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Contato da_pessoa;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches("(?s).*\\b(produto|produtos|prodito|itens|item)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Produto da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches("(?s).*\\b(pagamento|pagamentos|parcela|parcelas)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Pagamento da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
        }

        // Busca direta de Nota por número em linguagem natural (ex: "nota 1", "buscar nota #1", "consultar nota 2")
        java.util.regex.Matcher mNota = java.util.regex.Pattern
                .compile("(?i)^(?:buscar|busca|procurar|procura|consultar|consulta|ver|mostrar|mostra)?\\s*nota\\s*#?(\\d+)\\s*$")
                .matcher(texto);
        if (mNota.matches()) {
            return compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, "$$ R= #Nota id> " + mNota.group(1) + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
        }

        // Busca direta de Produto por ID em linguagem natural (ex: "ver produto 189", "id 189", "produto #189", "189")
        java.util.regex.Matcher mProdId = java.util.regex.Pattern
                .compile("(?i)^(?:ver|mostrar|mostra|consultar|consulta|buscar|busca|procurar|procura)?\\s*(?:o\\s+)?(?:produto|item)?\\s*(?:id|c[oó]digo|#)?\\s*#?(\\d+)\\s*$")
                .matcher(texto);
        if (mProdId.matches()) {
            return compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, "$$ R= #Produto id> " + mProdId.group(1) + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
        }

        // Busca direta de Pessoa por ID em linguagem natural (ex: "ver cliente 2", "pessoa 3")
        java.util.regex.Matcher mPesId = java.util.regex.Pattern
                .compile("(?i)^(?:ver|mostrar|mostra|consultar|consulta|buscar|busca|procurar|procura)?\\s*(?:a\\s+|o\\s+)?(?:pessoa|cliente|fornecedor)\\s*(?:id|c[oó]digo|#)?\\s*#?(\\d+)\\s*$")
                .matcher(texto);
        if (mPesId.matches()) {
            return compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, "$$ R= #Pessoa id> " + mPesId.group(1) + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
        }

        // Perguntas contextuais sobre o produto em foco ("o que vem nele?", "quais os componentes?", "é conjunto?", etc.)
        if (!lower.contains("bandeja") && !lower.contains("op-")
                && lower.matches("(?s).*\\b(o\\s*que\\s+vem\\s+nel[ea]|o\\s*que\\s+tem\\s+dentr?o|quais\\s+(?:s[aã]o\\s+)?os\\s+componentes|quais\\s+(?:s[aã]o\\s+)?os\\s+itens|itens\\s+do\\s+kit|componentes\\s+do\\s+kit|esse\\s+produto\\s+[eé]\\s+conjunto|[eé]\\s+conjunto)\\b.*")) {
            var foco = compiladorInstrucoesCurta.obterFocoSessao(idSessao);
            Long idProdFoco = foco != null ? foco.getIdUltimoProduto() : null;
            if (idProdFoco != null) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Produto id> " + idProdFoco + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
        }

        // Não intercepta intenções de escrita/exclusão/atualização
        if (lower.matches("(?s).*\\b(criar|cria|cadastrar|cadastra|atualizar|atualiza|mudar|muda|alterar|altera|modificar|modifica|modifique|colocar|coloque|adicionar|adicione|transformar|transforme|selecione|selecionar|editar|edite|apagar|apaga|excluir|exclui|deletar|deleta|remover|remove|comprar|comprei|vender|vendi|registrar|registre|emitir|emita)\\b.*")) {
            return null;
        }

        boolean referenciaContextual = lower.matches("(?s).*\\b(dess[ae]s?|del[ae]s?|ess[ae]s?|est[ae]s?|anteriores?|acima|ids?)\\b.*");
        boolean ehPerguntaNatural = lower.startsWith("qual ") || lower.startsWith("quais ") || texto.endsWith("?");
        boolean mencionaBusca = !referenciaContextual && !ehPerguntaNatural
                && lower.matches("(?s)^(procura|procurar|busca|buscar|acha|achar|localiza|localizar|consulta|consultar|mostra|mostrar|ver|cad[eê])\\b.*");
        boolean mencionaProduto = lower.matches("(?s).*\\b(produto|produtos|prodito|item|estoque)\\b.*");
        boolean mencionaPessoa = lower.matches("(?s).*\\b(pessoa|pessoas|cliente|clientes|fornecedor|fornecedores)\\b.*");

        // Extrai o termo após palavras de comando comuns
        String termo = texto.replaceAll("(?i)^(procura|procurar|busca|buscar|acha|achar|localiza|localizar|consulta|consultar|mostra|mostrar|ver)\\s+", "")
                .replaceAll("(?i)^(o|a|os|as|um|uma)?\\s*(produto|produtos|prodito|item|pessoa|pessoas|cliente|clientes|fornecedor|fornecedores)\\s+", "")
                .trim();

        boolean ehFraseCurta = texto.split("\\s+").length <= 4;
        boolean ehSiglaExplicita = ehFraseCurta
                && (br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(termo)
                || br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(texto));

        if (!ehSiglaExplicita && !(mencionaBusca && (mencionaProduto || mencionaPessoa) && !termo.isBlank())) {
            return null;
        }

        final String entidadeAlvo = mencionaPessoa ? "PESSOA" : "PRODUTO";
        final String termoFinal = termo.isBlank() ? texto : termo;

        try {
            var foco = compiladorInstrucoesCurta.obterFocoSessao(idSessao);
            String resultadoLeitura = concorrenciaEngine.submeterTarefa(
                    () -> compiladorInstrucoesCurta.processarLeituraComFocoECascata(entidadeAlvo, Map.of("nm", termoFinal), List.of(), foco),
                    null // Leitura livre, sem trava de recurso
            ).get();

            BandejaOperacoesContexto bandeja = bandejasPorSessao.get(idSessao);
            if (bandeja != null && bandeja.temItens()) {
                return resultadoLeitura + "\n\n" + bandeja.formatarVisualizacao();
            }
            return resultadoLeitura;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String prepararPromptComPreProcessamento(String idSessao, String textoBase) {
        String limpo = textoBase != null ? textoBase.trim().replaceAll("[ \\t]{2,}", " ") : "";
        var preProc = preProcessador.processar(limpo);
        String msgParaIA = limpo;
        if (preProc.temContexto()) {
            msgParaIA = msgParaIA + "\n\n" + preProc.gerarContextoSemantico();
        }
        BandejaOperacoesContexto bandejaAtiva = bandejasPorSessao.get(idSessao);
        if (bandejaAtiva != null && bandejaAtiva.temItens()) {
            msgParaIA = msgParaIA + "\n\n" + resumirBandejaParaEdicaoIA(bandejaAtiva);
        }
        return msgParaIA;
    }

    /** Áudio de voz capturado pelo microfone do celular ou desktop. */
    public String processarAudio(String idSessao, byte[] audioBytes, String mimeType) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return finalizarRespostaFormatada(bloqueio);

        try {
            if (temConfirmacaoPendente(idSessao)) {
                RespostaIA r = provedor.conversar(new PedidoIA(
                        "O usuário está respondendo a uma pergunta de confirmação. Apenas transcreva, em poucas palavras, " +
                        "o que ele disse no áudio. NÃO execute nenhuma ferramenta.",
                        audioBytes, mimeType));
                String transcricao = r.texto() != null ? r.texto().trim() : "";
                preProcessador.processar(transcricao);
                return finalizarRespostaFormatada(processarRespostaConfirmacao(idSessao, transcricao));
            }
            notificarProgresso("Pré-processando áudio...");
            List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
            String promptBase = "Ouça este comando de voz em português. Identifique a intenção e selecione a ferramenta apropriada "
                    + "(consulta, cadastro, atualização ou exclusão).";
            String msgParaIA = prepararPromptComPreProcessamento(idSessao, promptBase);
            notificarProgresso("IA analisando voz...");
            RespostaIA respostaIA = provedor.conversar(new PedidoIA(msgParaIA, audioBytes, mimeType, historico));
            registrarNoHistorico(idSessao, "user", "[Comando de voz]");
            String posBruto = processarRespostaIAComLoop(idSessao, respostaIA, 0, false);
            String resposta = finalizarRespostaFormatada(posBruto);
            registrarNoHistorico(idSessao, "model", resposta);
            return resposta;
        } catch (Exception e) {
            return finalizarRespostaFormatada("❌ Falha ao processar comando de voz: " + e.getMessage());
        }
    }

    /** Foto/documento capturado pela câmera do celular ou interface. */
    public String processarImagem(String idSessao, byte[] imagemBytes, String mimeType, String prompt) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return finalizarRespostaFormatada(bloqueio);

        try {
            notificarProgresso("Pré-processando imagem...");
            List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
            String p = prompt != null && !prompt.isBlank()
                    ? prompt
                    : "Analise esta foto ou documento e execute a operação correspondente no sistema de vendas.";
            String msgParaIA = prepararPromptComPreProcessamento(idSessao, p);
            boolean substituirBandeja = ehIntencaoCorrecaoBandeja(p);
            notificarProgresso("IA analisando imagem...");
            RespostaIA respostaIA = provedor.conversar(new PedidoIA(msgParaIA, imagemBytes, mimeType, historico));
            registrarNoHistorico(idSessao, "user", p);
            String posBruto = processarRespostaIAComLoop(idSessao, respostaIA, 0, substituirBandeja);
            String resposta = finalizarRespostaFormatada(posBruto);
            registrarNoHistorico(idSessao, "model", resposta);
            return resposta;
        } catch (Exception e) {
            return finalizarRespostaFormatada("❌ Falha ao processar imagem: " + e.getMessage());
        }
    }

    /**
     * PONTE SEM API: recebe o texto que uma IA externa (ex: Gemini do celular) devolveu seguindo o manual
     * e executa as ações com as mesmas validações e confirmações do modo com API.
     * Se houver confirmação pendente, uma resposta curta ("sim"/"não") é tratada como decisão.
     */
    public String processarRespostaExterna(String idSessao, String textoDaIA) {
        String texto = textoDaIA != null ? textoDaIA.trim() : "";

        if (CompiladorInstrucoesCurtaService.ehComandoEncurtado(texto)) {
            BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
            return finalizarRespostaFormatada(compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, texto, bandeja, () -> fecharJanelaContexto(idSessao)));
        }

        if (temConfirmacaoPendente(idSessao)) {
            if (texto.contains("{") || texto.contains("[")) {
                return finalizarRespostaFormatada("⚠️ Ainda há uma confirmação pendente. Responda **SIM** ou **NÃO** antes de enviar novas ações.\n\n"
                        + perguntaAtual(idSessao));
            }
            return finalizarRespostaFormatada(processarRespostaConfirmacao(idSessao, texto));
        }
        try {
            return finalizarRespostaFormatada(processarRespostaIA(idSessao, InterpretadorRespostaIA.interpretar(texto)));
        } catch (IllegalArgumentException e) {
            return finalizarRespostaFormatada("⚠️ " + e.getMessage());
        }
    }

    // ==================== NÚCLEO ====================

    private String validarProvedor(boolean exigeMidia) {
        if (provedor == null) {
            return "ℹ️ Modo **sem API** ativo. Toque em 🧩 **Sem API**, copie o manual para a sua IA (ex: Gemini do celular) " +
                    "e cole aqui a resposta dela para eu executar.";
        }
        if (exigeMidia && !provedor.suportaMidia()) {
            return "⚠️ O provedor atual (" + provedor.getDescricao() + ") não aceita áudio/imagem. Digite o comando em texto.";
        }
        return provedor.verificarPronto();
    }

    private String processarRespostaIA(String idSessao, RespostaIA resp) {
        return processarRespostaIAComLoop(idSessao, resp, 0, false);
    }

    private String processarRespostaIAComLoop(String idSessao, RespostaIA resp, int nivelLoop, boolean substituirBandejaSeHouverEscrita) {
        if (!resp.temChamadas()) {
            String textoIA = resp.texto() != null && !resp.texto().isBlank()
                    ? resp.texto()
                    : "Comando interpretado, nenhuma operação necessária.";
            String blocoDsl = extrairBlocoDsl(textoIA);
            if (blocoDsl != null) {
                if (substituirBandejaSeHouverEscrita) {
                    limparApenasBandejaParaReconstrucao(idSessao);
                }
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, blocoDsl, obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            BandejaOperacoesContexto bandeja = bandejasPorSessao.get(idSessao);
            if (bandeja != null && bandeja.temItens()) {
                String textoLimpo = removerRodapeBandejaDuplicado(textoIA);
                if (textoLimpo.isBlank() && ehPerguntaOuSimulacao(obterUltimaMensagemUsuario(idSessao))) {
                    textoLimpo = br.com.senac.integracao.ia.bandeja.SimuladorBandejaService
                            .gerarRelatorioSimulacaoUsuario(bandeja, roteadorController);
                }
                return (textoLimpo.isBlank() ? "" : textoLimpo + "\n\n") + bandeja.formatarVisualizacao();
            }
            return textoIA;
        }

        List<ChamadaFerramenta> chamadas = resp.chamadas();
        List<ChamadaFerramenta> chamadasEscrita = new ArrayList<>();
        List<ChamadaFerramenta> chamadasLeitura = new ArrayList<>();

        for (ChamadaFerramenta c : chamadas) {
            RotaSistema rota = roteadorController.resolverRota(c.rota());
            GeminiToolRegistry.NivelRiscoOperacao risco = rota != null
                    ? GeminiToolRegistry.classificarRisco(rota)
                    : GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY;
            if (risco == GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY) {
                chamadasLeitura.add(c);
            } else {
                chamadasEscrita.add(c);
            }
        }

        StringBuilder resultadosLeitura = new StringBuilder();
        for (ChamadaFerramenta c : chamadasLeitura) {
            if (Thread.currentThread().isInterrupted()) {
                return "Operação cancelada pelo usuário.";
            }
            notificarProgresso("Buscando...");
            String res = despacharChamadaTool(idSessao, c);
            if (res != null && !res.isBlank()) {
                if (resultadosLeitura.length() > 0) resultadosLeitura.append("\n\n");
                resultadosLeitura.append(res);
            }
        }

        // Se a IA chamou apenas ferramentas de leitura, decide se precisa de follow-up (bandeja ativa, simulação, escrita pendente ou análise multi-rota)
        BandejaOperacoesContexto bandejaAntesEscrita = bandejasPorSessao.get(idSessao);
        if (chamadasEscrita.isEmpty() && !chamadasLeitura.isEmpty() && nivelLoop < 3 && provedor != null
                && precisaFollowUpLeitura(idSessao, chamadasLeitura)) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    return "Operação cancelada pelo usuário.";
                }
                notificarProgresso("IA correlacionando dados...");
                List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
                String contextoBandejaFollowUp = (bandejaAntesEscrita != null && bandejaAntesEscrita.temItens())
                        ? "\n\n" + resumirBandejaParaEdicaoIA(bandejaAntesEscrita)
                        : "";
                String promptFollowUp = "Resultados das consultas realizadas no sistema:\n"
                        + resultadosLeitura.toString() + contextoBandejaFollowUp + "\n\n"
                        + "Com base nos dados acima e na última solicitação do usuário no histórico:\n"
                        + "1) Se for uma AÇÃO de escrita (cadastrar/atualizar/kit/nota), chame TODAS as ferramentas de escrita necessárias NA MESMA resposta.\n"
                        + "   - Pessoa inexistente: enfileire cadastro (OP-1) E nota usando 'OP-1'.\n"
                        + "   - Kits/conjuntos: cadastre itens faltantes (precoVenda='0.00' se não informado) E chame produto_kit_adicionar_componente usando ID existente ou 'OP-1', 'OP-2'...\n"
                        + "2) Se for uma PERGUNTA ou SIMULAÇÃO sobre itens da Bandeja de Contexto (ex: lucro, margem, custo, desconto, estoque projetado, cenários diários):\n"
                        + "   - NÃO chame ferramentas de escrita e NÃO repita o texto 'BANDEJA DE CONTEXTO ATIVA'. Responda diretamente com cálculos claros em Reais (R$).\n"
                        + "3) Se for uma PERGUNTA ANALÍTICA / CORRELACIONAL (ex: vencidos vendidos x devoluções, temperatura x estrago, distância de eventos x vendas, padrões sazonais):\n"
                        + "   - Se ainda precisar cruzar com outras rotas (ex: busca_padrao_ia, estatistica_*, fator_externo_*, relatorio_*), chame-as agora.\n"
                        + "   - Se já tiver os dados suficientes, NÃO chame ferramentas — responda diretamente com uma análise objetiva, citando números, correlações e limites observados.";
                RespostaIA followUpResp = provedor.conversar(new PedidoIA(promptFollowUp, null, null, historico));
                if (followUpResp.temChamadas()) {
                    return processarRespostaIAComLoop(idSessao, followUpResp, nivelLoop + 1, substituirBandejaSeHouverEscrita);
                }
                if (followUpResp.texto() != null && !followUpResp.texto().isBlank()) {
                    String textoLimpo = removerRodapeBandejaDuplicado(followUpResp.texto());
                    BandejaOperacoesContexto bandejaAtual = bandejasPorSessao.get(idSessao);
                    if (bandejaAtual != null && bandejaAtual.temItens()) {
                        return (textoLimpo.isBlank() ? "" : textoLimpo + "\n\n") + bandejaAtual.formatarVisualizacao();
                    }
                    return textoLimpo.isBlank() ? followUpResp.texto().trim() : textoLimpo;
                }
            } catch (Exception ignored) {}
        }

        if (!chamadasEscrita.isEmpty() && substituirBandejaSeHouverEscrita) {
            limparApenasBandejaParaReconstrucao(idSessao);
        }

        StringBuilder retornoEscritas = new StringBuilder();
        StringBuilder avisosEscritas = new StringBuilder();
        for (ChamadaFerramenta c : chamadasEscrita) {
            String r = despacharChamadaTool(idSessao, c);
            if (r != null && !r.isBlank()) {
                if (retornoEscritas.length() > 0) retornoEscritas.append("\n\n");
                retornoEscritas.append(r);
                if (r.contains("⚠️")) {
                    if (avisosEscritas.length() > 0) avisosEscritas.append("\n");
                    avisosEscritas.append(r);
                }
            }
        }

        BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
        if (bandeja.temItens()) {
            String prefixoAviso = avisosEscritas.length() > 0 ? avisosEscritas + "\n\n" : "";
            String ultimaMsgUsuario = obterUltimaMensagemUsuario(idSessao);
            boolean temPerguntaOuSim = ehPerguntaOuSimulacao(ultimaMsgUsuario);
            String textoBase = removerRodapeBandejaDuplicado(resp.texto());

            // Quando houve apenas leitura (sem escrita) e a bandeja está ativa, nunca descartar os resultados lidos!
            if (chamadasEscrita.isEmpty() && resultadosLeitura.length() > 0) {
                StringBuilder corpoLeitura = new StringBuilder();
                if (!textoBase.isBlank()) {
                    corpoLeitura.append(textoBase).append("\n\n");
                }
                corpoLeitura.append(resultadosLeitura);
                if (temPerguntaOuSim) {
                    String sim = br.com.senac.integracao.ia.bandeja.SimuladorBandejaService
                            .gerarRelatorioSimulacaoUsuario(bandeja, roteadorController);
                    if (!sim.isBlank()) {
                        corpoLeitura.append("\n\n").append(sim);
                    }
                }
                return prefixoAviso + corpoLeitura.toString().trim() + "\n\n" + bandeja.formatarVisualizacao();
            }

            // Se o usuário pediu operações E também fez uma pergunta/simulação na mesma mensagem,
            // gera a resposta da simulação com os itens recém-preparados na bandeja.
            if (temPerguntaOuSim && (textoBase.isBlank() || !contemCalculoOuMoeda(textoBase))) {
                String respostaSimulada = sintetizarSimulacaoBandeja(idSessao, bandeja, resultadosLeitura.toString(), nivelLoop);
                if (respostaSimulada != null && !respostaSimulada.isBlank()) {
                    textoBase = respostaSimulada;
                }
            }

            if (bandeja.totalItens() == 1) {
                Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
                if (fila != null && !fila.isEmpty()) {
                    ConfirmacaoAcaoGatekeeper.TicketConfirmacao t = gatekeeper.buscarTicket(fila.peekFirst());
                    if (t != null) {
                        String prefixoResp = (!textoBase.isBlank()) ? textoBase + "\n\n" : "";
                        return prefixoAviso + prefixoResp + montarPergunta(t);
                    }
                }
            }
            String explicacao = !textoBase.isBlank()
                    ? textoBase
                    : gerarBreveExplicacaoBandeja(bandeja);
            return prefixoAviso + explicacao + "\n\n" + bandeja.formatarVisualizacao();
        }

        StringBuilder finalOutput = new StringBuilder();
        if (resp.texto() != null && !resp.texto().isBlank()) {
            finalOutput.append(resp.texto()).append("\n\n");
        }
        if (retornoEscritas.length() > 0) {
            finalOutput.append(retornoEscritas).append("\n\n");
        }
        if (resultadosLeitura.length() > 0) {
            finalOutput.append(resultadosLeitura);
        }

        String strFinal = finalOutput.toString().trim();
        return strFinal.isBlank() ? "Operação concluída com sucesso." : strFinal;
    }

    private String despacharChamadaTool(String idSessao, ChamadaFerramenta chamada) {
        Map<String, Object> params = chamada.parametros() != null ? chamada.parametros() : new LinkedHashMap<>();

        RotaSistema rota = roteadorController.resolverRota(chamada.rota());
        if (rota == null) {
            return "⚠️ A IA pediu uma operação que não existe no sistema: " + chamada.rota();
        }

        GeminiToolRegistry.NivelRiscoOperacao risco = GeminiToolRegistry.classificarRisco(rota);
        String entendimento = descreverEntendimento(rota, params);
        String regra = switch (risco) {
            case DESTRUTIVO -> "⚠️ Governança Human-in-the-Loop ativa: Requer confirmação explícita do operador antes da exclusão física.";
            case WRITE_SEGURO -> "🔒 Execução sob Demanda: Ação retida na Bandeja de Contexto que exige confirmação prévia antes de persistir no banco.";
            case READ_ONLY -> "⚡ Consulta concorrente livre em Virtual Thread (sem bloqueio de recursos).";
        };

        List<String> faltando = ManualInstrucoesIA.obrigatoriosFaltando(rota, params);
        if (!faltando.isEmpty()) {
            return "⚠️ Não consegui executar \"" + rota.getDescricaoOperacao() + "\": faltam os dados " + faltando
                    + ". Informe e peça novamente.";
        }

        // AÇÕES DE ESCRITA / MUTAÇÃO (WRITE_SEGURO e DESTRUTIVO):
        // Retém na Bandeja de Contexto da sessão para execução conjunta e confirmação Human-in-the-Loop
        if (risco != GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY) {
            BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
            bandeja.adicionarOuAtualizar(rota, params, entendimento);

            String alvo = obterDetalheAlvoAmigavel(rota, params);
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.criarTicket(rota, params, alvo);
            Deque<String> fila = ticketsPendentesPorSessao.computeIfAbsent(idSessao, k -> new ConcurrentLinkedDeque<>());
            fila.addLast(ticket.ticketId());

            if (bandeja.totalItens() == 1) {
                return montarPergunta(ticket);
            }
            return bandeja.formatarVisualizacao();
        }

        // APENAS CONSULTAS (READ_ONLY): Virtual Threads imediatas em paralelo sem bloquear nem fechar a Bandeja
        try {
            String chaveRecurso = extrairChaveRecurso(params);
            RespostaEndpoint<Object> resultado = concorrenciaEngine.submeterTarefa(
                    () -> roteadorController.executarEndpoint(rota, params),
                    chaveRecurso
            ).get();

            StringBuilder sb = new StringBuilder();
            if (resultado.sucesso()) {
                sb.append(formatarResultadoAmigavel(rota, params, resultado.dados()));
            } else {
                sb.append(String.format("⚠️ Não foi possível concluir a consulta: %s", resultado.mensagem()));
            }

            BandejaOperacoesContexto bandeja = bandejasPorSessao.get(idSessao);
            if (bandeja != null && bandeja.temItens()) {
                sb.append("\n\nObservação: você continua com ").append(bandeja.totalItens())
                  .append(" instrução(ões) em espera na bandeja.");
            }
            return sb.toString();
        } catch (Exception e) {
            return br.com.senac.integracao.ia.ContextoIABuilder.propriedade("ia.msg.erro.generico",
                    "Não foi possível concluir a consulta no momento.");
        }
    }

    private String descreverEntendimento(RotaSistema rota, Map<String, Object> params) {
        if (rota == null) return "Operação não identificada";
        return switch (rota) {
            case PRODUTO_CATALOGO_BUSCAR, PRODUTO_BUSCAR_UNICO -> {
                if (params.containsKey("id") || params.containsKey("idProduto")) {
                    yield "Buscar produto único pelo ID #" + (params.get("id") != null ? params.get("id") : params.get("idProduto"));
                }
                if (params.containsKey("nome") || params.containsKey("termo")) {
                    yield "Buscar produto único pelo nome/termo '" + (params.get("nome") != null ? params.get("nome") : params.get("termo")) + "'";
                }
                yield "Consultar catálogo de produtos";
            }
            case PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO -> "Buscar um único produto com estoque válido disponível";
            case PRODUTO_ESTOQUE_VALIDO_LISTAR -> "Listar todos os produtos com estoque válido disponível e dentro da validade";
            case PRODUTO_CATALOGO_LISTAR -> "Listar todos os produtos disponíveis no catálogo";
            case PRODUTO_CATALOGO_ATUALIZAR -> {
                String id = params.get("idProduto") != null ? "#" + params.get("idProduto") : "";
                String nomeAtual = params.get("nomeAtual") != null ? "'" + params.get("nomeAtual") + "'" : "";
                String novo = params.get("novoNome") != null ? "'" + params.get("novoNome") + "'" : (params.get("nome") != null ? "'" + params.get("nome") + "'" : "");
                String desc = params.get("descricao") != null && !params.get("descricao").toString().isBlank() ? " e atualizar descrição" : "";
                yield "Atualizar produto " + (id.isEmpty() ? nomeAtual : id) + (!novo.isEmpty() ? " para " + novo : "") + desc;
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                String alvo = params.get("idProduto") != null ? "#" + params.get("idProduto") : "'" + params.get("nomeProduto") + "'";
                yield "Atualizar o preço de venda do produto " + alvo + " para R$ " + params.get("novoPreco");
            }
            case PRODUTO_CATALOGO_EXCLUIR -> {
                String alvo = params.get("idProduto") != null ? "#" + params.get("idProduto") : "'" + params.get("nomeProduto") + "'";
                yield "Excluir permanentemente o produto " + alvo + " do catálogo";
            }
            case PRODUTO_FISICO_CADASTRAR -> {
                String um = params.get("siglaUm") != null && !params.get("siglaUm").toString().isBlank() ? params.get("siglaUm").toString() : "UN";
                Object preco = params.get("precoVenda");
                String precoTxt = preco != null && !preco.toString().isBlank() ? ", R$ " + preco : "";
                yield "Cadastrar produto físico '" + params.get("nome") + "' (" + um + precoTxt + ")";
            }
            case PRODUTO_FISICO_ATUALIZAR_DIMENSOES -> "Atualizar dimensões/peso do produto #" + params.get("idProduto")
                    + " (" + params.get("alturaCm") + "x" + params.get("larguraCm") + "x" + params.get("comprimentoCm") + " cm)";
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                String kitRef = params.get("nomeKit") != null && !params.get("nomeKit").toString().isBlank()
                        ? "'" + params.get("nomeKit") + "'"
                        : formatarRefOp(params.get("idProdutoKit"));
                String compRef = params.get("nomeComponente") != null && !params.get("nomeComponente").toString().isBlank()
                        ? "'" + params.get("nomeComponente") + "'"
                        : formatarRefOp(params.get("idProdutoComponente"));
                Object qtd = params.get("quantidade");
                yield "Vincular " + (qtd != null ? qtd : "1") + "x do item " + compRef + " dentro do kit/conjunto " + kitRef;
            }
            case PRODUTO_DIGITAL_CADASTRAR -> {
                Object preco = params.get("precoVenda");
                String precoTxt = preco != null && !preco.toString().isBlank() ? " (R$ " + preco + ")" : "";
                yield "Cadastrar produto digital '" + params.get("nome") + "'" + precoTxt;
            }
            case PRODUTO_UNIDADE_CADASTRAR -> "Cadastrar unidade de medida '" + params.get("sigla") + "'"
                    + (params.get("nome") != null ? " (" + params.get("nome") + ")" : "");
            case PRODUTO_UNIDADE_ATUALIZAR -> "Atualizar unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_UNIDADE_EXCLUIR -> "Excluir unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_IMAGEM_ADICIONAR -> "Adicionar imagem ao produto " + formatarRefOp(params.get("idProduto"));
            case PRODUTO_IMAGEM_DEFINIR_PRINCIPAL -> "Definir imagem #" + params.get("idImagem") + " como principal do produto " + formatarRefOp(params.get("idProduto"));
            case PRODUTO_IMAGEM_REMOVER -> "Remover imagem #" + params.get("idImagem") + " do produto";
            case PESSOA_SIMPLES_CADASTRAR -> {
                String nomeP = params.get("nome") != null ? params.get("nome").toString() : (params.get("razaoSocial") != null ? params.get("razaoSocial").toString() : "");
                String papelP = params.get("papel") != null ? params.get("papel").toString() : (Boolean.parseBoolean(String.valueOf(params.get("isCliente"))) ? "cliente" : "fornecedor");
                yield "Cadastrar " + papelP.toLowerCase(Locale.ROOT) + " '" + nomeP + "' (sem exigir CNPJ/CPF)";
            }
            case PESSOA_LISTAR_CLIENTES -> "Listar clientes cadastrados na base de dados";
            case PESSOA_LISTAR_FORNECEDORES -> "Listar fornecedores cadastrados na base de dados";
            case PESSOA_BUSCAR -> "Buscar clientes ou fornecedores pelo termo '" + params.get("termo") + "'";
            case PESSOA_BUSCAR_UNICA -> "Buscar uma única pessoa (cliente ou fornecedor) com os critérios informados";
            case PESSOA_REGISTRO_BUSCAR_UNICO -> "Buscar uma única pessoa pelo documento/registro único";
            case PESSOA_HISTORICO_COMPLETO -> "Consultar histórico 360º completo (notas, produtos e pagamentos) da pessoa";
            case PESSOA_CLIENTE_EXCLUIR -> "Excluir cadastro do cliente/pessoa #" + params.get("idPessoa");
            case PESSOA_CLIENTE_PF_CADASTRAR -> "Cadastrar/atualizar cliente PF '" + params.get("nome") + "'"
                    + (params.get("cpf") != null ? " (CPF " + params.get("cpf") + ")" : "");
            case PESSOA_CLIENTE_PJ_CADASTRAR -> "Cadastrar/atualizar cliente PJ '" + params.get("razaoSocial") + "'"
                    + (params.get("cnpj") != null ? " (CNPJ " + params.get("cnpj") + ")" : "");
            case PESSOA_CLIENTE_ATUALIZAR_LIMITE -> "Atualizar limite de crédito do cliente " + formatarRefOp(params.get("idPessoa"))
                    + (params.get("novoLimite") != null ? " para R$ " + params.get("novoLimite") : "");
            case PESSOA_FORNECEDOR_PJ_CADASTRAR -> "Cadastrar fornecedor PJ '" + (params.get("razaoSocial") != null ? params.get("razaoSocial") : params.get("nome")) + "'"
                    + (params.get("cnpj") != null ? " (CNPJ " + params.get("cnpj") + ")" : "");
            case PESSOA_FORNECEDOR_PF_CADASTRAR -> "Cadastrar fornecedor PF '" + params.get("nome") + "'"
                    + (params.get("cpf") != null ? " (CPF " + params.get("cpf") + ")" : "");
            case PESSOA_DADOS_ATUALIZAR -> "Atualizar dados cadastrais da pessoa " + formatarRefOp(params.get("idPessoa"))
                    + (params.get("nome") != null ? " para '" + params.get("nome") + "'" : "");
            case PESSOA_REGISTRO_ADICIONAR -> "Adicionar documento (" + params.get("tipo") + ": " + params.get("numero") + ") à pessoa " + formatarRefOp(params.get("idPessoa"));
            case PAGAMENTO_FORMA_CADASTRAR -> "Cadastrar forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS -> "Customizar regras/taxas da forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_FORMA_ALTERAR_STATUS -> "Alterar status (ativar/desativar) da forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_FORMA_EXCLUIR -> "Excluir forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_CRIAR_A_VISTA -> "Registrar pagamento à vista na nota #" + params.get("idNota")
                    + (params.get("codigoForma") != null ? " via " + params.get("codigoForma") : "");
            case PAGAMENTO_CRIAR_A_PRAZO -> "Registrar pagamento a prazo na nota #" + params.get("idNota")
                    + (params.get("dataVencimento") != null ? " (venc. " + params.get("dataVencimento") + ")" : "");
            case PAGAMENTO_CRIAR_PARCELADO -> "Registrar pagamento parcelado na nota #" + params.get("idNota")
                    + (params.get("parcelas") != null ? " em " + params.get("parcelas") + "x" : "");
            case PAGAMENTO_QUITAR_TITULO_OU_PARCELA -> "Quitar título/parcela #" + params.get("idPagamento")
                    + (params.get("valor") != null ? " (R$ " + params.get("valor") + ")" : "");
            case ESTOQUE_SALDO_CONSOLIDADO -> "Consultar saldo consolidado do produto #" + params.get("idProduto");
            case ESTOQUE_FILA_LOTES_FIFO -> "Consultar lotes em fila FIFO para o produto #" + params.get("idProduto");
            case ESTOQUE_LOTES_VALIDOS_LISTAR -> "Listar todos os lotes de estoque válidos e não vencidos";
            case ESTOQUE_LOTE_VALIDO_BUSCAR_UNICO -> "Buscar o primeiro lote único com estoque válido (FIFO)";
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> "Baixar perda física"
                    + (params.get("quantidade") != null ? " (" + params.get("quantidade") + ")" : "")
                    + " do lote #" + params.get("idLote");
            case NOTA_VENDA_EMITIR_FIFO -> {
                String cli = params.get("idCliente") != null ? " para cliente " + formatarRefOp(params.get("idCliente")) : "";
                String qtdItens = params.get("itens") instanceof List<?> l ? " (" + l.size() + " item(ns))" : "";
                yield "Emitir nota fiscal de venda FIFO" + cli + qtdItens;
            }
            case NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> {
                String qtdItens = params.get("itens") instanceof List<?> l ? " (" + l.size() + " item(ns))" : "";
                yield "Emitir nota de venda balcão (Consumidor Padrão)" + qtdItens;
            }
            case NOTA_VENDA_EMITIR_LOTE_DESEJAVEL -> {
                String cli = params.get("idCliente") != null ? " para cliente " + formatarRefOp(params.get("idCliente")) : "";
                String qtdItens = params.get("itens") instanceof List<?> l ? " (" + l.size() + " item(ns))" : "";
                yield "Emitir nota de venda com lote específico" + cli + qtdItens;
            }
            case NOTA_VENDA_PROCEDURE_MYSQL_FIFO, NOTA_VENDA_PROCEDURE_MYSQL_LOTE ->
                    "Baixar estoque via procedure para cliente " + formatarRefOp(params.get("idCliente"))
                            + " (produto " + formatarRefOp(params.get("idProduto")) + ", qtd " + params.get("quantidade") + ")";
            case NOTA_COMPRA_EMITIR -> {
                String forn = params.get("idFornecedor") != null ? " do fornecedor " + formatarRefOp(params.get("idFornecedor")) : "";
                String qtdItens = params.get("itens") instanceof List<?> l ? " (" + l.size() + " item(ns))" : "";
                yield "Emitir nota fiscal de compra" + forn + qtdItens + " com entrada no estoque";
            }
            case NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR -> "Abater perdas do fornecedor " + formatarRefOp(params.get("idFornecedor"))
                    + " na nota de compra #" + params.get("idNotaCompra");
            case NOTA_DEVOLUCAO_REGISTRAR -> "Registrar nota de devolução referente à nota #" + params.get("idNota");
            case NOTA_BUSCAR_UNICA -> "Buscar nota operacional única #" + (params.get("idNota") != null ? params.get("idNota") : params.get("id"));
            case NOTA_LISTAR_PADRAO -> "Listar notas operacionais registradas no sistema";
            case NOTA_DEFINIR_DATA -> "Definir/atualizar a data da nota operacional #" + params.get("idNota")
                    + (params.get("dataGerada") != null ? " para " + params.get("dataGerada") : "");
            case FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS -> "Gerar relatório integrado de fatores externos (Rios, Clima, Calendário e Eventos) citando cada Nota";
            case BUSCA_PADRAO_IA -> "Executar busca padrão estruturada de leitura da IA na entidade " + params.get("entidade");
            default -> rota.getDescricaoOperacao();
        };
    }

    private String formatarRefOp(Object ref) {
        if (ref == null) return "?";
        String s = ref.toString().trim();
        if (s.toUpperCase(Locale.ROOT).startsWith("OP-")) return s.toUpperCase(Locale.ROOT);
        return s.startsWith("#") ? s : "#" + s;
    }

    private String gerarBreveExplicacaoBandeja(BandejaOperacoesContexto bandeja) {
        int total = bandeja.totalItens();
        Map<String, Integer> resumoPorTipo = new LinkedHashMap<>();
        for (ItemBandeja item : bandeja.getItens()) {
            String rotulo = switch (item.getRota()) {
                case PRODUTO_FISICO_CADASTRAR, PRODUTO_DIGITAL_CADASTRAR -> "cadastro(s) de produto";
                case PRODUTO_CATALOGO_ATUALIZAR, PRODUTO_CATALOGO_ATUALIZAR_PRECO, PRODUTO_FISICO_ATUALIZAR_DIMENSOES -> "atualização(ões) de produto";
                case PRODUTO_KIT_ADICIONAR_COMPONENTE -> "vínculo(s) de kit/conjunto";
                case PRODUTO_CATALOGO_EXCLUIR -> "exclusão(ões) de produto";
                case PRODUTO_UNIDADE_CADASTRAR, PRODUTO_UNIDADE_ATUALIZAR, PRODUTO_UNIDADE_EXCLUIR -> "ajuste(s) de unidade de medida";
                case PRODUTO_IMAGEM_ADICIONAR, PRODUTO_IMAGEM_DEFINIR_PRINCIPAL, PRODUTO_IMAGEM_REMOVER -> "ajuste(s) de imagem de produto";
                case PESSOA_SIMPLES_CADASTRAR, PESSOA_CLIENTE_PF_CADASTRAR, PESSOA_CLIENTE_PJ_CADASTRAR,
                     PESSOA_FORNECEDOR_PF_CADASTRAR, PESSOA_FORNECEDOR_PJ_CADASTRAR -> "cadastro(s) de pessoa";
                case PESSOA_DADOS_ATUALIZAR, PESSOA_CLIENTE_ATUALIZAR_LIMITE, PESSOA_REGISTRO_ADICIONAR -> "atualização(ões) de pessoa";
                case PESSOA_CLIENTE_EXCLUIR -> "exclusão(ões) de pessoa";
                case PAGAMENTO_FORMA_CADASTRAR, PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS, PAGAMENTO_FORMA_ALTERAR_STATUS, PAGAMENTO_FORMA_EXCLUIR -> "configuração(ões) de forma de pagamento";
                case PAGAMENTO_CRIAR_A_VISTA, PAGAMENTO_CRIAR_A_PRAZO, PAGAMENTO_CRIAR_PARCELADO, PAGAMENTO_QUITAR_TITULO_OU_PARCELA -> "lançamento(s)/quitação(ões) de pagamento";
                case ESTOQUE_BAIXAR_PERDA_DIRETA -> "baixa(s) de perda em estoque";
                case NOTA_COMPRA_EMITIR, NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR -> "emissão(ões)/ajuste(s) de nota de compra";
                case NOTA_VENDA_EMITIR_FIFO, NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO, NOTA_VENDA_EMITIR_LOTE_DESEJAVEL,
                     NOTA_VENDA_PROCEDURE_MYSQL_FIFO, NOTA_VENDA_PROCEDURE_MYSQL_LOTE -> "emissão(ões) de nota de venda";
                case NOTA_DEVOLUCAO_REGISTRAR, NOTA_DEFINIR_DATA -> "registro/ajuste de nota";
                default -> "operação(ões)";
            };
            resumoPorTipo.merge(rotulo, 1, Integer::sum);
        }
        StringBuilder detalhes = new StringBuilder();
        for (Map.Entry<String, Integer> e : resumoPorTipo.entrySet()) {
            if (detalhes.length() > 0) detalhes.append(", ");
            detalhes.append(e.getValue()).append(" ").append(e.getKey());
        }
        return "Preparei " + total + " operação(ões) em lote (" + detalhes + "). Confira abaixo o que será executado antes de confirmar:";
    }

    private String formatarParametrosResumidos(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "Nenhum parâmetro (leitura global)";
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    // ==================== CONFIRMAÇÃO E BANDEJA ====================

    /**
     * Executa um comando de navegação da bandeja (próxima, executar OP-n, pular, retomar, anterior, ir para).
     * Cada execução confere o banco antes e depois. Quando não sobra nada em aberto, a janela é fechada.
     */
    private String processarComandoBandeja(String idSessao, BandejaOperacoesContexto bandeja,
                                           br.com.senac.integracao.ia.bandeja.ComandoBandeja cmd) {
        switch (cmd.acao()) {
            case EXECUTAR_ITEM, EXECUTAR_PROXIMO -> {
                var passo = cmd.acao() == br.com.senac.integracao.ia.bandeja.ComandoBandeja.Acao.EXECUTAR_ITEM
                        ? executorBandeja.executarPorId(bandeja, cmd.alvo())
                        : executorBandeja.executarProximo(bandeja);
                if (passo != null && passo.dados() != null) {
                    var foco = compiladorInstrucoesCurta.obterFocoSessao(idSessao);
                    if (passo.dados() instanceof Produto p) foco.registrarProdutoEmFoco(p);
                    else if (passo.dados() instanceof Pessoa pes) foco.registrarPessoaEmFoco(pes);
                    else if (passo.dados() instanceof Nota n) foco.registrarNotaEmFoco(n);
                }
                StringBuilder sb = new StringBuilder(passo.formatarTexto());
                if (bandeja.itensEmAberto().isEmpty()) {
                    fecharJanelaContexto(idSessao);
                    sb.append("\n\nA bandeja está vazia, tudo foi tratado. Contexto da operação zerado.");
                } else {
                    sb.append("\n\n").append(bandeja.formatarVisualizacao());
                }
                return sb.toString();
            }
            case PULAR -> {
                var item = executorBandeja.pular(bandeja, cmd.alvo());
                if (item == null) return "Não encontrei essa instrução na bandeja.\n\n" + bandeja.formatarVisualizacao();
                return "Deixei " + item.getIdItem() + " para depois. Para voltar a ela, diga \"retomar " + item.getIdItem() + "\".\n\n"
                        + bandeja.formatarVisualizacao();
            }
            case RETOMAR -> {
                var item = executorBandeja.retomar(bandeja, cmd.alvo());
                if (item == null) return "Não encontrei essa instrução na bandeja.\n\n" + bandeja.formatarVisualizacao();
                return item.getIdItem() + " voltou para a fila.\n\n" + bandeja.formatarVisualizacao();
            }
            case ANTERIOR -> {
                var item = bandeja.moverAnterior();
                return item == null ? "A bandeja está vazia." : descreverItemBandeja(item, bandeja);
            }
            case IR_PARA -> {
                var item = bandeja.irPara(cmd.alvo());
                return item == null ? "Não encontrei essa instrução na bandeja.\n\n" + bandeja.formatarVisualizacao()
                        : descreverItemBandeja(item, bandeja);
            }
            case REMOVER -> {
                var removido = bandeja.removerItemPorIdOuIndice(cmd.alvo());
                if (removido == null) {
                    return "Não encontrei essa instrução na bandeja.\n\n" + bandeja.formatarVisualizacao();
                }
                if (!bandeja.temItens()) {
                    fecharJanelaContexto(idSessao);
                    return "Removi " + removido.getIdItem() + ". A bandeja ficou vazia e o contexto foi encerrado.";
                }
                return "Removi " + removido.getIdItem() + " (" + removido.getDescricaoAmigavel() + ") da bandeja.\n\n"
                        + bandeja.formatarVisualizacao();
            }
            case SIMULAR -> {
                String simulacao = br.com.senac.integracao.ia.bandeja.SimuladorBandejaService
                        .gerarRelatorioSimulacaoUsuario(bandeja, roteadorController);
                return simulacao + "\n\n" + bandeja.formatarVisualizacao();
            }
            default -> {
                return bandeja.formatarVisualizacao();
            }
        }
    }

    private String resumirBandejaParaEdicaoIA(BandejaOperacoesContexto bandeja) {
        StringBuilder sb = new StringBuilder("[Bandeja de Contexto Atual - Aguardando Confirmação]:\n");
        for (ItemBandeja item : bandeja.getItens()) {
            sb.append(" - ").append(item.getIdItem())
              .append(" [").append(item.getStatus()).append(" | ").append(item.getRota().name()).append("] ")
              .append(item.getDescricaoAmigavel())
              .append(" | params=").append(item.getParametros()).append("\n");
        }
        ValidadorLoteBandeja.RelatorioValidacaoLote relatorio = ValidadorLoteBandeja.validar(bandeja, roteadorController);
        sb.append("\n").append(relatorio.formatarParaIA()).append("\n");
        String simulacao = br.com.senac.integracao.ia.bandeja.SimuladorBandejaService
                .gerarContextoSimulacaoParaIA(bandeja, roteadorController);
        if (!simulacao.isBlank()) {
            sb.append("\n").append(simulacao).append("\n");
        }
        sb.append("\nINSTRUÇÕES SOBRE A BANDEJA ATIVA:\n")
          .append("1) PERGUNTAS E SIMULAÇÕES (lucro, margem, custo, desconto, estoque projetado, 'como ficaria', 'quanto lucraria', 'se eu vender X'): ")
          .append("NÃO chame ferramentas de escrita e NÃO repita o bloco 'BANDEJA DE CONTEXTO ATIVA'. Responda com cálculos objetivos usando os dados da bandeja e a simulação acima (e consultas READ_ONLY apenas se precisar de dados extras do banco).\n")
          .append("2) CORREÇÃO/SUBSTITUIÇÃO: Se o usuário pedir explicitamente para corrigir ou trocar algo do plano, reemita as ferramentas de escrita corrigidas.\n")
          .append("3) NOVAS ADIÇÕES: Se o usuário pedir novas operações além das atuais, chame APENAS as novas ferramentas (não duplique os itens já listados na bandeja).\n")
          .append("4) AUDITORIA E VALIDAÇÃO: Se o usuário perguntar se o lote/bandeja já foi validado ou se está tudo correto, responda ESTRITAMENTE com base na [AUDITORIA DE PRÉ-VALIDAÇÃO DA BANDEJA] acima. Se STATUS GERAL for REPROVADA, liste exatamente quais OP-N falharão e por quê — NUNCA diga que está tudo válido quando houver conflitos.");
        return sb.toString();
    }

    private boolean ehPedidoCorrecaoDireta(String msg) {
        if (msg == null || msg.isBlank()) return false;
        String lower = msg.toLowerCase(Locale.ROOT).trim();
        if (lower.endsWith("?")) return false;
        return lower.matches("(?s)^(?:pode\\s+|por\\s+favor\\s+)?(?:corrig\\w*|ajust\\w*|arrum\\w*|consert\\w*)(?:\\s+(?:isso|a\\s+bandeja|o\\s+lote|os\\s+(?:erros?|conflitos?|problemas?)|as\\s+(?:instru[cç][oõ]es|opera[cç][oõ]es)))?\\s*[!.]*$");
    }

    private boolean ehIntencaoCorrecaoBandeja(String msg) {
        if (msg == null || msg.isBlank()) return false;
        String lower = msg.toLowerCase(Locale.ROOT).trim();
        if (lower.endsWith("?") || lower.matches("(?s)^(?:n[aã]o\\s+(?:entendi|sei|compreendi))\\b.*")) {
            return false;
        }
        return lower.matches("(?s)^(?:(?:pode\\s+)?(?:corrig\\w*|ajust\\w*|arrum\\w*|consert\\w*|modifi\\w*|alter\\w*|troc\\w*|substitu\\w*|refa[cz]\\w*)|n[aã]o|na\\s+verdade|em\\s+vez|ao\\s+inv[eé]s|quero\\s+que\\s+(?:modifi|alter|troc|corrij|mud)).*");
    }

    private String extrairBlocoDsl(String texto) {
        if (texto == null) return null;
        String limpo = texto.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
        int idx = limpo.indexOf("$$");
        if (idx < 0) return null;
        String candidato = limpo.substring(idx).trim();
        return CompiladorInstrucoesCurtaService.ehComandoEncurtado(candidato) ? candidato : null;
    }

    private String obterUltimaMensagemUsuario(String idSessao) {
        List<Map<String, Object>> hist = historicoPorSessao.get(idSessao);
        if (hist == null || hist.isEmpty()) return "";
        for (int i = hist.size() - 1; i >= 0; i--) {
            if ("user".equals(hist.get(i).get("role"))) {
                Object partsObj = hist.get(i).get("parts");
                if (partsObj instanceof List<?> parts && !parts.isEmpty() && parts.get(0) instanceof Map<?, ?> m) {
                    Object txt = m.get("text");
                    if (txt != null) return txt.toString();
                }
                break;
            }
        }
        return "";
    }

    private boolean ehPerguntaOuSimulacao(String msg) {
        if (msg == null || msg.isBlank()) return false;
        String lower = java.text.Normalizer.normalize(msg, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        if (lower.endsWith("?")) return true;
        return lower.matches("(?s).*\\b(quanto|quantos|quantas|como\\s+ficaria|como\\s+fica|como\\s+fez|como\\s+voce|qual\\s+(?:o|a|e|sera|seria)|quais|por\\s+que|porque|lucro\\w*|lucr\\w*|margem\\w*|simul\\w*|calcul\\w*|estim\\w*|previs\\w*|ficaria|seria|daria|valeria|custaria|ganharia|compensa|vale\\s+a\\s+pena|prejuizo|impacto|me\\s+fale|me\\s+diga|responda|explique|explica)\\b.*");
    }

    private boolean contemCalculoOuMoeda(String texto) {
        if (texto == null || texto.isBlank()) return false;
        return (texto.contains("R$") || texto.contains("%")) && texto.matches("(?s).*\\d.*");
    }

    private String sintetizarSimulacaoBandeja(String idSessao, BandejaOperacoesContexto bandeja, String leituraExtra, int nivelLoop) {
        String resumoSim = br.com.senac.integracao.ia.bandeja.SimuladorBandejaService
                .gerarRelatorioSimulacaoUsuario(bandeja, roteadorController);
        if (provedor != null && nivelLoop < 2) {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    notificarProgresso("Calculando simulação da bandeja...");
                    List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
                    StringBuilder promptSim = new StringBuilder();
                    promptSim.append("As operações solicitadas já foram preparadas na Bandeja de Contexto:\n")
                             .append(resumirBandejaParaEdicaoIA(bandeja)).append("\n");
                    if (leituraExtra != null && !leituraExtra.isBlank()) {
                        promptSim.append("\nConsultas auxiliares realizadas:\n").append(leituraExtra).append("\n");
                    }
                    promptSim.append("\nAgora responda APENAS à pergunta ou simulação feita pelo usuário na última mensagem ")
                             .append("(ex: lucro unitário, lucro diário/total, margem, desconto, estoque projetado), ")
                             .append("usando os valores exatos da simulação acima. ")
                             .append("NÃO chame nenhuma ferramenta e NÃO escreva 'BANDEJA DE CONTEXTO ATIVA'.");
                    RespostaIA respSim = provedor.conversar(new PedidoIA(promptSim.toString(), null, null, historico));
                    if (respSim != null && !respSim.temChamadas() && respSim.texto() != null && !respSim.texto().isBlank()) {
                        String limpo = removerRodapeBandejaDuplicado(respSim.texto());
                        if (!limpo.isBlank()) {
                            return limpo;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return resumoSim;
    }

    private boolean precisaFollowUpLeitura(String idSessao, List<ChamadaFerramenta> chamadasLeitura) {
        if (chamadasLeitura != null && chamadasLeitura.size() > 1) return true;
        BandejaOperacoesContexto bandejaAtiva = bandejasPorSessao.get(idSessao);
        if (bandejaAtiva != null && bandejaAtiva.temItens()) return true;
        String ultimaMsg = obterUltimaMensagemUsuario(idSessao);
        if (ultimaMsg.isBlank()) return true;
        if (ehPerguntaOuSimulacao(ultimaMsg)) return true;
        String ultimaMsgUsuario = ultimaMsg.toLowerCase(Locale.ROOT);
        return ultimaMsgUsuario.matches("(?s).*\\b(criar|cria|crie|cadastr\\w*|atualiz\\w*|mud\\w*|alter\\w*|modifi\\w*|coloc\\w*|adicion\\w*|transform\\w*|edit\\w*|apag\\w*|exclu\\w*|delet\\w*|remov\\w*|compr\\w*|vend\\w*|registr\\w*|emiti\\w*|reajust\\w*|chance\\w*|correla\\w*|vencid\\w*|devolv\\w*|grau\\w*|clima|temperatura|dist[aâ]ncia|impact\\w*|padr[aã]o|padr[oõ]es|sazonal\\w*|estrag\\w*|motivo\\w*|estat[ií]st\\w*|compara\\w*|tend[eê]ncia|previs\\w*|quantos|quantas|soma\\w*|total|m[eé]dia|por\\s+que)\\b.*");
    }

    private void limparApenasBandejaParaReconstrucao(String idSessao) {
        BandejaOperacoesContexto antiga = bandejasPorSessao.remove(idSessao);
        if (antiga != null) {
            antiga.fecharJanela();
        }
        Deque<String> fila = ticketsPendentesPorSessao.remove(idSessao);
        if (fila != null) {
            for (String tId : fila) {
                gatekeeper.cancelarTicket(tId);
            }
        }
    }

    private String descreverItemBandeja(ItemBandeja item, BandejaOperacoesContexto bandeja) {
        return "Instrução " + item.getIdItem() + ": " + item.getDescricaoAmigavel()
                + "\nSituação: " + item.getStatus()
                + (item.isPendente() ? "\nFalta informar: " + item.getCamposFaltantes() : "")
                + (item.getMensagemErro() != null ? "\nÚltimo erro: " + item.getMensagemErro() : "")
                + "\n\nPara executar só esta, diga \"executar " + item.getIdItem() + "\".";
    }

    public boolean temConfirmacaoPendente(String idSessao) {
        BandejaOperacoesContexto b = bandejasPorSessao.get(idSessao);
        if (b != null && b.temItensValidados()) {
            return true;
        }
        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        return fila != null && !fila.isEmpty();
    }

    private String processarRespostaConfirmacao(String idSessao, String msgUsuario) {
        DecisaoConfirmacao decisao = DecisaoConfirmacao.interpretar(msgUsuario);
        BandejaOperacoesContexto bandeja = bandejasPorSessao.get(idSessao);

        if (bandeja != null && bandeja.temItens()) {
            switch (decisao) {
                case SIM -> {
                    if (bandeja.temItensPendentes() && !bandeja.temItensValidados()) {
                        return "Todas as instruções na bandeja estão aguardando dados adicionais antes da execução:\n\n"
                                + bandeja.formatarVisualizacao();
                    }
                    RelatorioExecucaoBandeja rel = bandeja.executarTodasValidadas(roteadorController);
                    String relatorioFormatado = formatarRelatorioExecucaoBandeja(rel);
                    var focoSessao = compiladorInstrucoesCurta.obterFocoSessao(idSessao);
                    if (focoSessao != null && rel != null && rel.detalhes() != null) {
                        for (var det : rel.detalhes()) {
                            if (det.dados() instanceof Produto p) {
                                focoSessao.registrarProdutoEmFoco(p);
                            } else if (det.dados() instanceof Pessoa pes) {
                                focoSessao.registrarPessoaEmFoco(pes);
                            } else if (det.dados() instanceof Nota n) {
                                focoSessao.registrarNotaEmFoco(n);
                            }
                        }
                    }
                    fecharJanelaContexto(idSessao);
                    return relatorioFormatado + "\n\n✔ Contexto da operação zerado (bandeja limpa para a próxima instrução).";
                }
                case NAO -> {
                    fecharJanelaContexto(idSessao);
                    return "Operações canceladas. A bandeja foi limpa e nada foi gravado no banco.";
                }
                default -> {
                    return "Aguardando sua decisão sobre as instruções na bandeja.\n"
                            + "Responda SIM para executar todas ou NÃO para cancelar.\n\n"
                            + bandeja.formatarVisualizacao();
                }
            }
        }

        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        if (fila == null || fila.isEmpty()) {
            return "Nenhuma operação pendente de confirmação.";
        }
        String ticketId = fila.peekFirst();

        switch (decisao) {
            case SIM: {
                fila.pollFirst();
                ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.buscarTicket(ticketId);
                RotaSistema rota = ticket != null ? ticket.rota() : null;
                Map<String, Object> params = ticket != null ? ticket.parametros() : Collections.emptyMap();

                RespostaEndpoint<Object> resp = gatekeeper.confirmarEExecutar(ticketId, roteadorController);
                String resultado;
                if (!resp.sucesso()) {
                    resultado = "Não foi possível executar: " + resp.mensagem();
                } else if (rota == RotaSistema.PRODUTO_CATALOGO_EXCLUIR) {
                    resultado = "Produto #" + params.get("idProduto") + " excluído com sucesso do catálogo.";
                } else if (rota == RotaSistema.PESSOA_CLIENTE_EXCLUIR) {
                    resultado = "Cliente/Pessoa #" + params.get("idPessoa") + " excluído com sucesso do sistema.";
                } else {
                    resultado = "Operação confirmada e executada:\n" + formatarResultadoAmigavel(rota, params, resp.dados());
                }
                if (fila.isEmpty()) {
                    fecharJanelaContexto(idSessao);
                    resultado += "\n\n✔ Contexto da operação zerado (bandeja limpa para a próxima instrução).";
                }
                return resultado + proximaPergunta(idSessao);
            }
            case NAO: {
                fila.pollFirst();
                gatekeeper.cancelarTicket(ticketId);
                if (fila.isEmpty()) {
                    fecharJanelaContexto(idSessao);
                }
                return "Operação cancelada. Nada foi alterado ou excluído." + proximaPergunta(idSessao);
            }
            default:
                return "Aguardando sua decisão. Responda SIM para confirmar ou NÃO para cancelar.\n\n"
                        + perguntaAtual(idSessao);
        }
    }

    private String formatarRelatorioExecucaoBandeja(RelatorioExecucaoBandeja rel) {
        StringBuilder sb = new StringBuilder();
        if (rel.totalOperacoes() == 1 && rel.todasComSucesso()) {
            var d = rel.detalhes().get(0);
            RotaSistema r = roteadorController.resolverRota(d.rota());
            sb.append("Operação confirmada e executada:\n");
            Map<String, Object> paramsItem = d.parametros() != null ? d.parametros() : Collections.emptyMap();
            if (d.dados() != null) {
                sb.append(formatarResultadoAmigavel(r, paramsItem, d.dados())).append("\n");
            } else {
                sb.append("  ").append(d.mensagem()).append("\n");
            }
            return sb.toString().trim();
        }

        if (rel.todasComSucesso()) {
            sb.append(String.format("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO (%d de %d concluídas):\n", rel.sucessos(), rel.totalOperacoes()));
        } else if (rel.sucessos() > 0) {
            sb.append(String.format("Bandeja executada parcialmente (Sucessos: %d, Falhas: %d):\n", rel.sucessos(), rel.falhas()));
        } else {
            sb.append(String.format("Não foi possível executar as %d operações da bandeja:\n", rel.totalOperacoes()));
        }

        for (int i = 0; i < rel.detalhes().size(); i++) {
            var d = rel.detalhes().get(i);
            String prefixoStatus = d.sucesso() ? "" : "[FALHOU - NÃO EXECUTADA] ";
            sb.append(String.format("  %d. %s%s\n", (i + 1), prefixoStatus, d.descricao()));
            if (d.sucesso()) {
                if (d.dados() != null) {
                    RotaSistema r = roteadorController.resolverRota(d.rota());
                    Map<String, Object> paramsItem = d.parametros() != null ? d.parametros() : Collections.emptyMap();
                    sb.append("     ").append(formatarResultadoAmigavel(r, paramsItem, d.dados()).replace("\n", "\n     ")).append("\n");
                } else {
                    sb.append("     ").append(d.mensagem()).append("\n");
                }
            } else {
                sb.append("     Motivo da falha: ").append(d.mensagem()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String perguntaAtual(String idSessao) {
        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        while (fila != null && !fila.isEmpty()) {
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao t = gatekeeper.buscarTicket(fila.peekFirst());
            if (t != null) return montarPergunta(t);
            fila.pollFirst(); // ticket expirado
        }
        return "";
    }

    private String proximaPergunta(String idSessao) {
        String p = perguntaAtual(idSessao);
        return p.isEmpty() ? "" : "\n\n" + p;
    }

    private String montarPergunta(ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket) {
        RotaSistema rota = ticket.rota();
        String entendimento = descreverEntendimento(rota, ticket.parametros());
        boolean isDestrutivo = GeminiToolRegistry.classificarRisco(rota) == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO;

        StringBuilder sb = new StringBuilder();
        sb.append("Resumo da instrução preparada: ").append(entendimento).append("\n");
        if (isDestrutivo) {
            sb.append("Atenção: esta é uma exclusão definitiva (").append(ticket.resumoImpacto()).append(").\n");
            sb.append("Confirma a exclusão? Digite SIM para confirmar ou NÃO para cancelar.\n");
        } else {
            sb.append("Deseja confirmar e salvar agora? Digite SIM para confirmar ou NÃO para cancelar.\n");
        }
        sb.append("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]");
        return sb.toString();
    }

    // ==================== FORMATAÇÃO ====================

    /** Descreve o alvo da ação (ex: nome do produto) antes de pedir confirmação. */
    private String obterDetalheAlvoAmigavel(RotaSistema rota, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "Nenhum detalhe informado";

        if (rota == RotaSistema.PRODUTO_CATALOGO_EXCLUIR) {
            Long id = extrairIdLong(params, "idProduto");
            String nomeProd = (String) params.get("nomeProduto");
            if (id != null) {
                try {
                    Produto p = roteadorController.produtos().buscarPorId(id);
                    if (p != null) {
                        return String.format("Produto #%d: '%s' (Preço Atual: R$ %s)",
                                p.getId(), CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), null),
                                p.getPrecoVenda() != null ? p.getPrecoVenda().toString() : "0.00");
                    }
                    return "Produto ID #" + id + " (não encontrado no catálogo)";
                } catch (Exception ignored) {
                }
                return "Produto ID #" + id;
            } else if (nomeProd != null) {
                return "Produto: '" + nomeProd + "'";
            }
        }

        if (rota == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR) {
            String id = params.get("idProduto") != null ? "#" + params.get("idProduto") : "";
            String nomeAtual = params.get("nomeAtual") != null ? "'" + params.get("nomeAtual") + "'" : "";
            String novo = params.get("novoNome") != null ? "'" + params.get("novoNome") + "'" : (params.get("nome") != null ? "'" + params.get("nome") + "'" : "");
            return "Produto " + (id.isEmpty() ? nomeAtual : id) + (!novo.isEmpty() ? " -> Novo Nome: " + novo : "");
        }

        if (rota == RotaSistema.PESSOA_CLIENTE_EXCLUIR) {
            Long id = extrairIdLong(params, "idPessoa");
            if (id != null) {
                try {
                    Pessoa p = roteadorController.pessoas().buscarPorId(id);
                    if (p != null) {
                        return String.format("Cliente #%d: '%s'", p.getId(), CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), null));
                    }
                    return "Cliente/Pessoa ID #" + id + " (não encontrado)";
                } catch (Exception ignored) {
                }
                return "Cliente/Pessoa ID #" + id;
            }
        }

        if (rota == RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA) {
            Object idLote = params.get("idLote") != null ? params.get("idLote") : params.get("idItemLote");
            Object qtd = params.get("quantidade") != null ? params.get("quantidade") : params.get("quantidadePerda");
            Object mot = params.get("motivo") != null ? params.get("motivo") : params.get("motivoPerda");
            return String.format("Lote #%s, Quantidade: %s, Motivo: %s", idLote, qtd, mot);
        }

        return params.toString();
    }

    /** Converte retornos Java em texto limpo e compreensível sem poluição de markdown. */
    private String formatarResultadoAmigavel(RotaSistema rota, Map<String, Object> params, Object dados) {
        if (rota == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO) {
            if (dados instanceof Produto p) {
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                String nomeProd = CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), umSigla);
                String preco = p.getPrecoVenda() != null
                        ? p.getPrecoVenda().setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
                        : "0.00";
                return String.format("Preço atualizado com sucesso: o produto #%d (%s) agora custa R$ %s.",
                        p.getId(), nomeProd, preco);
            }
            String alvo = (params != null && params.get("idProduto") != null)
                    ? "#" + params.get("idProduto")
                    : (params != null && params.get("nomeProduto") != null ? String.valueOf(params.get("nomeProduto")) : "selecionado");
            String preco = params != null && params.get("novoPreco") != null ? String.valueOf(params.get("novoPreco")) : "0.00";
            return String.format("Preço atualizado com sucesso: o produto %s agora custa R$ %s.",
                    alvo, preco);
        }
        if (rota == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR && dados instanceof Produto p) {
            String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
            return String.format("Produto atualizado: #%d - %s — R$ %s (%s)",
                    p.getId(), CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), umSigla),
                    p.getPrecoVenda() != null ? p.getPrecoVenda().setScale(2, java.math.RoundingMode.HALF_EVEN) : "0.00",
                    umSigla);
        }
        if (dados == null) {
            if (rota == RotaSistema.PRODUTO_CATALOGO_BUSCAR || rota == RotaSistema.PRODUTO_BUSCAR_UNICO
                    || rota == RotaSistema.PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO) {
                return "Nenhum produto encontrado com os dados informados.";
            }
            if (rota == RotaSistema.PESSOA_BUSCAR || rota == RotaSistema.PESSOA_BUSCAR_UNICA
                    || rota == RotaSistema.PESSOA_REGISTRO_BUSCAR_UNICO) {
                return "Nenhuma pessoa ou cliente encontrado com os dados informados.";
            }
            if (rota == RotaSistema.NOTA_BUSCAR_UNICA) {
                return "Nenhuma nota operacional encontrada com o código informado.";
            }
            return "Operação realizada com sucesso.";
        }

        if (dados instanceof Item<?> lote) {
            String nomeProd = lote.getProduto() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(lote.getProduto().getNome(), null) : "Produto";
            return String.format("Lote válido #%d (%s) — Saldo disponível: %s, Valor unitário: R$ %s, Validade: %s",
                    lote.getId(), nomeProd, lote.getQuantidadeDisponivel(), lote.getValor(),
                    lote.getDataValidade() != null ? lote.getDataValidade() : "Indeterminada");
        }

        if (dados instanceof Produto p) {
            return br.com.senac.integracao.ia.ferramentas.ResolvedorProduto.formatarDetalhesCompletosProduto(p, roteadorController);
        }

        if (dados instanceof List<?> lista) {
            if (lista.isEmpty()) {
                if (rota == RotaSistema.PRODUTO_ESTOQUE_VALIDO_LISTAR
                        || rota == RotaSistema.ESTOQUE_LOTES_VALIDOS_LISTAR
                        || rota == RotaSistema.ESTOQUE_FILA_LOTES_FIFO) {
                    String estoqueFisicoFallback = formatarEstoqueFisicoQuandoValidoVazio();
                    if (estoqueFisicoFallback != null) {
                        return estoqueFisicoFallback;
                    }
                    return "Nenhum produto ou lote com estoque físico encontrado.";
                }
                return "Nenhum registro encontrado para os critérios informados.";
            }
            if (lista.get(0) instanceof Produto) {
                @SuppressWarnings("unchecked")
                List<Produto> prodsBrutos = (List<Produto>) lista;
                List<Produto> prodsLimpos = CompiladorInstrucoesCurtaService.filtrarProdutosExibicaoLimpa(prodsBrutos);
                if (prodsLimpos.isEmpty()) {
                    prodsLimpos = prodsBrutos;
                }
                boolean isBuscaEspecifica = rota == RotaSistema.PRODUTO_CATALOGO_BUSCAR
                        || rota == RotaSistema.PRODUTO_BUSCAR_UNICO
                        || rota == RotaSistema.PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO;
                StringBuilder sb = new StringBuilder();
                if (isBuscaEspecifica && prodsLimpos.size() > 1) {
                    sb.append("Encontrei ").append(prodsLimpos.size())
                      .append(" produtos correspondentes (escolha um pelo código ou nome):\n");
                } else {
                    sb.append("Produtos no catálogo (").append(prodsLimpos.size()).append(" itens):\n");
                }
                for (Produto p : prodsLimpos) {
                    String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                    String estoqueTexto = br.com.senac.integracao.ia.ferramentas.ResolvedorProduto
                            .formatarSaldoEstoqueCurto(p, roteadorController, umSigla);
                    sb.append(String.format("  #%d - %s — R$ %s (%s) | %s\n",
                            p.getId(),
                            CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), umSigla),
                            p.getPrecoVenda() != null ? p.getPrecoVenda().setScale(2, java.math.RoundingMode.HALF_EVEN) : "0.00",
                            umSigla,
                            estoqueTexto));
                }
                return sb.toString().trim();
            }
            if (lista.get(0) instanceof Pessoa) {
                @SuppressWarnings("unchecked")
                List<Pessoa> pessoasBrutas = (List<Pessoa>) lista;
                List<Pessoa> pessoasLimpas = CompiladorInstrucoesCurtaService.filtrarPessoasExibicaoLimpa(pessoasBrutas);
                if (pessoasLimpas.isEmpty()) {
                    pessoasLimpas = pessoasBrutas;
                }
                boolean isBuscaEspecifica = rota == RotaSistema.PESSOA_BUSCAR || rota == RotaSistema.PESSOA_BUSCAR_UNICA;
                StringBuilder sb = new StringBuilder();
                if (isBuscaEspecifica && pessoasLimpas.size() > 1) {
                    sb.append("Encontrei ").append(pessoasLimpas.size())
                      .append(" pessoas correspondentes (escolha uma pelo código ou nome):\n");
                } else {
                    sb.append("Clientes e fornecedores cadastrados (").append(pessoasLimpas.size()).append(" registros):\n");
                }
                for (Pessoa pes : pessoasLimpas) {
                    sb.append(String.format("  #%d - %s — Limite: R$ %s\n",
                            pes.getId(),
                            CompiladorInstrucoesCurtaService.limparNomeExibicao(pes.getNome(), null),
                            pes.getLimite() != null ? pes.getLimite().setScale(2, java.math.RoundingMode.HALF_EVEN) : "0.00"));
                }
                return sb.toString().trim();
            }
            if (lista.get(0) instanceof Item<?>) {
                StringBuilder sb = new StringBuilder("Lotes com estoque válido (" + lista.size() + " lotes):\n");
                for (Object item : lista) {
                    if (item instanceof Item<?> lote) {
                        String nomeProd = lote.getProduto() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(lote.getProduto().getNome(), null) : "Produto";
                        sb.append(String.format("  Lote #%d - %s — Saldo FIFO: %s, Validade: %s, Unitário: R$ %s\n",
                                lote.getId(), nomeProd, lote.getQuantidadeDisponivel(),
                                lote.getDataValidade() != null ? lote.getDataValidade() : "Sem vencimento",
                                lote.getValor()));
                    }
                }
                return sb.toString().trim();
            }
            if (lista.get(0) instanceof br.com.senac.repository.modal.pagamento.FormaPagamento) {
                @SuppressWarnings("unchecked")
                List<br.com.senac.repository.modal.pagamento.FormaPagamento> fps = (List<br.com.senac.repository.modal.pagamento.FormaPagamento>) lista;
                return br.com.senac.integracao.ia.ferramentas.ResolvedorPagamento.formatarListaFormasPagamento(fps);
            }
            if (lista.get(0) instanceof Nota) {
                StringBuilder sb = new StringBuilder("Notas operacionais (" + lista.size() + " notas):\n");
                for (Object item : lista) {
                    if (item instanceof Nota n) {
                        String nomePes = n.getPessoa() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(n.getPessoa().getNome(), null) : "Consumidor Padrão";
                        String statusValidade = "";
                        if (n instanceof NotaPerda np) {
                            statusValidade = np.isPerdaDentroDaValidade() ? " [Dentro da validade]" : " [VENCIDO]";
                        }
                        String desc = (n.getDescricao() != null && !n.getDescricao().isBlank())
                                ? " | Obs: " + n.getDescricao().trim()
                                : "";
                        sb.append(String.format("  Nota #%d (%s)%s — Data: %s, Pessoa: %s, Total: R$ %s%s\n",
                                n.getId(), n.getClass().getSimpleName(), statusValidade,
                                n.getDataGerada() != null ? n.getDataGerada().toLocalDate() : "N/A",
                                nomePes, n.getValorTotal(), desc));
                    }
                }
                return sb.toString().trim();
            }
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("relatorioTextoPadronizado")) {
            return String.valueOf(map.get("relatorioTextoPadronizado"));
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("entidadeConsultada") && map.containsKey("dados")) {
            String modoRetorno = map.get("modoRetorno") != null ? String.valueOf(map.get("modoRetorno")) : "";
            if (("RESUMO".equalsIgnoreCase(modoRetorno) || "CONTAGEM".equalsIgnoreCase(modoRetorno))
                    && map.get("resumoTexto") != null) {
                return String.valueOf(map.get("resumoTexto"));
            }
            Object interno = map.get("dados");
            String formatado = formatarResultadoAmigavel(rota, params, interno);
            if (Boolean.TRUE.equals(map.get("temMais")) && map.get("avisoPaginacao") != null) {
                return formatado + "\n\n" + map.get("avisoPaginacao");
            }
            return formatado;
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("pessoa") && map.containsKey("notas")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pMap = (Map<String, Object>) map.get("pessoa");
            @SuppressWarnings("unchecked")
            Map<String, Object> resumo = (Map<String, Object>) map.get("resumoFinanceiro");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> notas = (List<Map<String, Object>>) map.get("notas");

            StringBuilder sb = new StringBuilder();
            sb.append("Histórico completo da pessoa:\n")
              .append("  Código: #").append(pMap.get("id")).append(" - ").append(pMap.get("nome")).append("\n")
              .append("  Documento: ").append(pMap.get("documento")).append(" (").append(pMap.get("tipoPessoa")).append(")\n")
              .append("  Limite de crédito: R$ ").append(pMap.get("limiteCredito")).append("\n\n");

            if (resumo != null) {
                sb.append("Resumo consolidado:\n")
                  .append("  Total de notas: ").append(resumo.get("totalNotasEmitidas")).append("\n")
                  .append("  Valor total movimentado: R$ ").append(resumo.get("valorTotalMovimentado")).append("\n")
                  .append("  Total de pagamentos: R$ ").append(resumo.get("valorTotalPagamentosRegistrados")).append("\n\n");
            }

            if (notas == null || notas.isEmpty()) {
                sb.append("Nenhuma nota vinculada a esta pessoa até o momento.");
            } else {
                sb.append("Notas vinculadas (").append(notas.size()).append("):\n");
                for (Map<String, Object> n : notas) {
                    sb.append(String.format("  Nota #%s (%s) em %s — Total: R$ %s\n",
                            n.get("idNota"), n.get("tipoNota"), n.get("dataGerada"), n.get("valorTotal")));
                }
            }
            return sb.toString().trim();
        }

        if (dados instanceof br.com.senac.repository.modal.pagamento.FormaPagamento fp) {
            return br.com.senac.integracao.ia.ferramentas.ResolvedorPagamento.formatarDetalhesCompletosFormaPagamento(fp);
        }

        if (dados instanceof NotaPerda perda) {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 NOTA DE PERDA OPERACIONAL REGISTRADA (#").append(perda.getId()).append(")\n");
            sb.append("  • Tipo da Perda: ").append(perda.getTipoPerda() != null ? perda.getTipoPerda() : "Estoque").append("\n");
            sb.append("  • Descrição: ").append(perda.getDescricao() != null ? perda.getDescricao() : "Perda operacional").append("\n");
            sb.append("  • Data: ").append(perda.getDataGerada() != null ? perda.getDataGerada().toLocalDate() : java.time.LocalDate.now()).append("\n");
            sb.append("  • Custo Total da Perda: R$ ").append(perda.getValorCustoPerda() != null ? perda.getValorCustoPerda().toPlainString() : "0.00").append("\n");
            sb.append("  • Análise de Validade / Ressarcimento: ")
              .append(perda.isPerdaDentroDaValidade() ? "✔ Dentro da validade (Ressarcimento de fornecedor aplicável)" : "⚠️ Lote expirado (Isento de ressarcimento)").append("\n");
            if (perda.getItens() != null && !perda.getItens().isEmpty()) {
                sb.append("  • Itens baixados (").append(perda.getItens().size()).append("):\n");
                for (Item<? extends Produto> it : perda.getItens()) {
                    String nm = it.getProduto() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(it.getProduto().getNome(), null) : "Item";
                    sb.append(String.format("    - %s: %s un x R$ %s (Validade: %s)\n",
                            nm, it.getQuantidade(), it.getValor(), it.getDataValidade() != null ? it.getDataValidade() : "Sem validade"));
                }
            }
            return sb.toString().trim();
        }

        if (dados instanceof Nota nota) {
            String tipoNota;
            if (nota instanceof NotaCompra) tipoNota = "Nota Fiscal de Compra";
            else if (nota instanceof NotaVenda) tipoNota = "Nota Fiscal de Venda";
            else if (nota instanceof NotaPerda) tipoNota = "Nota de Perda";
            else if (nota instanceof NotaDevolucao) tipoNota = "Nota Fiscal de Devolução";
            else tipoNota = nota.getClass().getSimpleName();

            String pessoaLabel = nota instanceof NotaCompra ? "Fornecedor" : (nota instanceof NotaPerda ? "Responsável" : "Cliente");
            String nomePessoa = nota.getPessoa() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(nota.getPessoa().getNome(), null) : (nota instanceof NotaPerda ? "Estoque Interno" : "Consumidor Padrão");
            StringBuilder sb = new StringBuilder();
            sb.append(tipoNota).append(" #").append(nota.getId()).append(" — ")
              .append(pessoaLabel).append(": ").append(nomePessoa)
              .append(", Data: ").append(nota.getDataGerada() != null ? nota.getDataGerada().toLocalDate() : java.time.LocalDate.now())
              .append(", Total: R$ ").append(nota.getValorTotal() != null ? nota.getValorTotal().toPlainString() : "0.00").append("\n");
            if (nota.getDescricao() != null && !nota.getDescricao().isBlank()) {
                sb.append("  Descrição: ").append(nota.getDescricao().trim()).append("\n");
            }
            if (nota.getItens() != null && !nota.getItens().isEmpty()) {
                sb.append("  Itens (").append(nota.getItens().size()).append("):\n");
                for (Item<? extends Produto> it : nota.getItens()) {
                    String nomeProd = it.getProduto() != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(it.getProduto().getNome(), null) : "Item";
                    String um = it.getProduto() != null && it.getProduto().getUm() != null ? it.getProduto().getUm().getSigla() : "UN";
                    sb.append(String.format("    %s: %s %s x R$ %s = R$ %s\n",
                            nomeProd, it.getQuantidade(), um, it.getValor(), it.getSubtotal()));
                }
            }
            return sb.toString().trim();
        }
        if (dados instanceof Pessoa pes) {
            return br.com.senac.integracao.ia.ferramentas.ResolvedorPessoa.formatarDetalhesCompletosPessoa(pes, roteadorController);
        }
        if (dados instanceof BigDecimal bd) {
            if (rota == RotaSistema.ESTOQUE_SALDO_CONSOLIDADO) {
                Long idProd = params != null ? extrairIdLong(params, "idProduto") : null;
                return formatarSaldoConsolidadoComProduto(idProd, bd);
            }
            return "Saldo/Valor: R$ " + bd.toPlainString();
        }
        if (dados instanceof Boolean b) {
            return b ? "Operação concluída com sucesso." : "A operação não teve efeito.";
        }
        return FormatadorSaidaIA.formatarDadoEstruturado(rota, dados);
    }

    private Long extrairIdLong(Map<String, Object> params, String chave) {
        Object val = params.get(chave);
        if (val instanceof Number n) return n.longValue();
        if (val != null) {
            try {
                return Long.parseLong(val.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Formata o saldo consolidado exibindo a quantidade física e alerta em vermelho quando houver lote vencido. */
    private String formatarSaldoConsolidadoComProduto(Long idProd, BigDecimal bd) {
        BigDecimal saldoValido = bd != null ? bd : BigDecimal.ZERO;
        BigDecimal saldoVencido = br.com.senac.integracao.ia.ferramentas.ResolvedorProduto
                .consultarSaldoVencidoSeguro(idProd, roteadorController);
        BigDecimal saldoFisico = saldoValido.add(saldoVencido);

        String qtd = saldoFisico.compareTo(BigDecimal.ZERO) == 0
                ? "0"
                : saldoFisico.stripTrailingZeros().toPlainString();
        String sufixo = saldoFisico.compareTo(BigDecimal.ZERO) <= 0
                ? " (Sem estoque)"
                : (saldoVencido.compareTo(BigDecimal.ZERO) > 0 ? " ⚠️ Há lote com produto vencido" : "");

        if (idProd != null && roteadorController != null) {
            try {
                Produto p = roteadorController.produtos().buscarPorId(idProd);
                if (p != null) {
                    String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                    String nomeLimpo = CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), umSigla);
                    return String.format("Saldo em estoque — #%d %s: %s %s%s",
                            p.getId(), nomeLimpo, qtd, umSigla, sufixo);
                }
            } catch (Exception ignored) {
            }
        }
        return "Saldo em estoque" + (idProd != null ? " (Produto #" + idProd + ")" : "")
                + ": " + qtd + sufixo;
    }

    /** Lista o estoque físico dos lotes com saldo &gt; 0 quando as rotas de estoque válido retornam vazio. */
    private String formatarEstoqueFisicoQuandoValidoVazio() {
        if (roteadorController == null) return null;
        try {
            List<Item<? extends Produto>> lotes = roteadorController.estoque().listarTodosLotesFisicosComSaldo();
            if (lotes == null || lotes.isEmpty()) return null;

            java.time.LocalDate hoje = java.time.LocalDate.now();
            StringBuilder sb = new StringBuilder();
            sb.append("Estoque físico em lotes (").append(lotes.size()).append("):\n");
            for (Item<? extends Produto> l : lotes) {
                Produto p = l.getProduto();
                String um = p != null && p.getUm() != null ? p.getUm().getSigla() : "UN";
                String nome = p != null
                        ? CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), um)
                        : "Produto";
                Long idProd = p != null ? p.getId() : null;
                String qtd = l.getQuantidadeDisponivel() != null
                        ? l.getQuantidadeDisponivel().stripTrailingZeros().toPlainString()
                        : "0";
                boolean vencido = l.getDataValidade() != null && l.getDataValidade().isBefore(hoje);
                sb.append(String.format("  #%s - %s — Lote #%d: %s %s (Validade: %s)%s\n",
                        idProd != null ? idProd : "?",
                        nome,
                        l.getId(),
                        qtd,
                        um,
                        l.getDataValidade() != null ? l.getDataValidade() : "Indeterminada",
                        vencido ? " ⚠️ Há lote com produto vencido" : ""));
            }
            return sb.toString().trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extrairChaveRecurso(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return null;
        if (params.containsKey("idProduto")) return "PROD_" + params.get("idProduto");
        if (params.containsKey("idPessoa")) return "PESSOA_" + params.get("idPessoa");
        if (params.containsKey("idCliente")) return "CLIENTE_" + params.get("idCliente");
        if (params.containsKey("idNota")) return "NOTA_" + params.get("idNota");
        if (params.containsKey("idItemLote")) return "LOTE_" + params.get("idItemLote");
        return null;
    }

    // ==================== ACESSORES ====================

    public ProvedorIA getProvedor() {
        return provedor;
    }

    /** Troca a IA em tempo de execução (null = modo sem API). */
    public void setProvedor(ProvedorIA provedor) {
        this.provedor = provedor;
    }

    public GeminiClientService getGeminiClient() {
        return geminiClient;
    }

    public RoteadorController getRoteadorController() {
        return roteadorController;
    }

    public ConfirmacaoAcaoGatekeeper getGatekeeper() {
        return gatekeeper;
    }

    public PreProcessadorMensagem getPreProcessador() {
        return preProcessador;
    }

    private void registrarLogTurnoSeAtivo(
            String idSessao,
            String entradaUsuario,
            String preProcessamento,
            String enviadoParaIA,
            String posProcessamento,
            String retornoUsuario) {
        if (!logFluxo.isAtivo()) {
            return;
        }
        logFluxo.registrarTurno(idSessao, entradaUsuario, preProcessamento, enviadoParaIA, posProcessamento, retornoUsuario);
    }

    private String resumirRespostaIAParaLog(RespostaIA respostaIA) {
        if (respostaIA == null) {
            return "RespostaIA nula";
        }
        StringBuilder sb = new StringBuilder();
        if (respostaIA.temChamadas()) {
            sb.append("Chamadas de ferramenta (").append(respostaIA.chamadas().size()).append("): ");
            for (int i = 0; i < respostaIA.chamadas().size(); i++) {
                if (i > 0) sb.append(" | ");
                var c = respostaIA.chamadas().get(i);
                sb.append(c.rota()).append("(").append(c.parametros()).append(")");
            }
        } else {
            sb.append("Sem chamadas de ferramenta.");
        }
        if (respostaIA.texto() != null && !respostaIA.texto().isBlank()) {
            sb.append("\nTexto bruto da IA: ").append(respostaIA.texto().trim());
        }
        return sb.toString();
    }
}
