package br.com.senac.integracao.gemini;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.CompiladorInstrucoesCurtaService;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.InterpretadorRespostaIA;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.ProvedorIA.ChamadaFerramenta;
import br.com.senac.integracao.ia.ProvedorIA.PedidoIA;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;
import br.com.senac.integracao.ia.ProvedorIAFactory;
import br.com.senac.integracao.ia.RelatorioExecucaoBandeja;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;
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

    // Bandejas de Operações e Contexto ativas por sessão do usuário
    private final Map<String, BandejaOperacoesContexto> bandejasPorSessao = new ConcurrentHashMap<>();
    // Fila de tickets aguardando confirmação legado/MCP, por sessão
    private final Map<String, Deque<String>> ticketsPendentesPorSessao = new ConcurrentHashMap<>();
    // Histórico de conversação estruturado multi-turn por sessão
    private final Map<String, List<Map<String, Object>>> historicoPorSessao = new ConcurrentHashMap<>();

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
        return texto.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "")
                .replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "")
                .trim();
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
        return bandejasPorSessao.computeIfAbsent(idSessao, BandejaOperacoesContexto::new);
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

    /**
     * Fecha e limpa a janela de contexto da sessão:
     * 1. Limpa e descarta a Bandeja de Contexto
     * 2. Remove o histórico de mensagens multi-turn
     * 3. Descarta eventuais tickets pendentes
     */
    public void fecharJanelaContexto(String idSessao) {
        if (idSessao == null || idSessao.isBlank()) return;
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

    /** Mensagem de texto do usuário, interpretada pelo provedor de IA configurado ou pelo compilador $$ local. */
    public String processarMensagem(String idSessao, String mensagemUsuario) {
        String msg = mensagemUsuario != null ? mensagemUsuario.trim() : "";

        // Se o prompt iniciar com $$, atribui imediatamente à classe de compilação de instruções curtas (0 chamadas à IA)
        if (CompiladorInstrucoesCurtaService.ehComandoEncurtado(msg)) {
            BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
            String respostaCompilada = compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, msg, bandeja, () -> fecharJanelaContexto(idSessao));
            registrarNoHistorico(idSessao, "user", msg);
            registrarNoHistorico(idSessao, "model", respostaCompilada);
            return respostaCompilada;
        }

        // Navegação da bandeja em linguagem simples ("próxima", "executar OP-2", "pular OP-1"):
        // resolvida localmente, sem chamar a IA.
        BandejaOperacoesContexto bandejaAtiva = bandejasPorSessao.get(idSessao);
        if (bandejaAtiva != null && bandejaAtiva.temItens()) {
            var comando = br.com.senac.integracao.ia.bandeja.ComandoBandeja.interpretar(msg);
            if (comando.isPresent()) {
                String respostaBandeja = processarComandoBandeja(idSessao, bandejaAtiva, comando.get());
                registrarNoHistorico(idSessao, "user", msg);
                registrarNoHistorico(idSessao, "model", respostaBandeja);
                return respostaBandeja;
            }
        }

        if (temConfirmacaoPendente(idSessao)) {
            DecisaoConfirmacao d = DecisaoConfirmacao.interpretar(msg);
            if (d != DecisaoConfirmacao.INDEFINIDA) {
                String resposta = processarRespostaConfirmacao(idSessao, msg);
                registrarNoHistorico(idSessao, "user", msg);
                registrarNoHistorico(idSessao, "model", resposta);
                return resposta;
            }
        }

        // Busca direta por sigla fora de $$ (ex: "procura produto o.b.a", "procura prodito >oba", "cliente i.s.u")
        // Executada em sequência, sem fechar a bandeja de espera!
        String buscaRapidaSigla = tentarResolverBuscaSiglaForaDeDsl(idSessao, msg);
        if (buscaRapidaSigla != null) {
            registrarNoHistorico(idSessao, "user", msg);
            registrarNoHistorico(idSessao, "model", buscaRapidaSigla);
            return buscaRapidaSigla;
        }

        String bloqueio = validarProvedor(false);
        if (bloqueio != null) return bloqueio;

        try {
            List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
            var preProc = preProcessador.processar(msg);
            String msgParaIA = msg;
            if (!preProc.entidades().isEmpty()) {
                msgParaIA = msg + "\n\n" + preProc.gerarContextoSemantico();
            }
            RespostaIA respostaIA = provedor.conversar(new PedidoIA(msgParaIA, null, null, historico));
            String resposta = processarRespostaIA(idSessao, respostaIA);
            registrarNoHistorico(idSessao, "user", msg);
            registrarNoHistorico(idSessao, "model", resposta);
            return resposta;
        } catch (Exception e) {
            return br.com.senac.integracao.ia.ContextoIABuilder.propriedade("ia.msg.erro.generico",
                    "Não consegui processar sua solicitação agora. Tente novamente em instantes.");
        }
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
        if (lower.matches(".*\\b(dessa\\s+nota|dessaa\\s+nota|da\\s+nota|nota\\s+atual|desse\\s+fornecedor|desse\\s+cliente|dessa\\s+pessoa|contatos\\s+del[ea])\\b.*")) {
            if (lower.matches(".*\\b(fornecedor|fornecedores)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Fornecedor da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches(".*\\b(cliente|clientes|clinete|clinetes)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Cliente da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches(".*\\b(pessoa|pessoas)\\b.*") && !lower.matches(".*\\b(contato|contatos)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Pessoa da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches(".*\\b(contato|contatos|telefone|whatsapp|email|documento|documentos|registro|registros)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Contato da_pessoa;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches(".*\\b(produto|produtos|prodito|itens|item)\\b.*")) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Produto da_nota;", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
            if (lower.matches(".*\\b(pagamento|pagamentos|parcela|parcelas)\\b.*")) {
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
        if (lower.matches(".*\\b(o\\s*que\\s+vem\\s+nel[ea]|o\\s*que\\s+tem\\s+dentr?o|quais\\s+(?:s[aã]o\\s+)?os\\s+componentes|quais\\s+(?:s[aã]o\\s+)?os\\s+itens|itens\\s+do\\s+kit|componentes\\s+do\\s+kit|esse\\s+produto\\s+[eé]\\s+conjunto|[eé]\\s+conjunto)\\b.*")) {
            var foco = compiladorInstrucoesCurta.obterFocoSessao(idSessao);
            Long idProdFoco = foco != null ? foco.getIdUltimoProduto() : null;
            if (idProdFoco != null) {
                return compiladorInstrucoesCurta.compilarEProcessar(
                        idSessao, "$$ R= #Produto id> " + idProdFoco + ";", obterBandeja(idSessao), () -> fecharJanelaContexto(idSessao));
            }
        }

        // Não intercepta intenções de escrita/exclusão/atualização
        if (lower.matches(".*\\b(criar|cria|cadastrar|cadastra|atualizar|atualiza|mudar|muda|alterar|altera|apagar|apaga|excluir|exclui|deletar|deleta|remover|remove|comprar|vender)\\b.*")) {
            return null;
        }

        boolean mencionaBusca = lower.matches("^(procura|procurar|busca|buscar|acha|achar|localiza|localizar|consulta|consultar|mostra|mostrar|ver|qual|cad[eê])\\b.*");
        boolean mencionaProduto = lower.matches(".*\\b(produto|produtos|prodito|item|estoque)\\b.*");
        boolean mencionaPessoa = lower.matches(".*\\b(pessoa|pessoas|cliente|clientes|fornecedor|fornecedores)\\b.*");

        // Extrai o termo após palavras de comando comuns
        String termo = texto.replaceAll("(?i)^(procura|procurar|busca|buscar|acha|achar|localiza|localizar|consulta|consultar|mostra|mostrar|ver)\\s+", "")
                .replaceAll("(?i)^(o|a|os|as|um|uma)?\\s*(produto|produtos|prodito|item|pessoa|pessoas|cliente|clientes|fornecedor|fornecedores)\\s+", "")
                .trim();

        boolean ehSiglaExplicita = br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(termo)
                || br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(texto);

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

    /** Áudio de voz capturado pelo microfone do celular. */
    public String processarAudio(String idSessao, byte[] audioBytes, String mimeType) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return bloqueio;

        try {
            if (temConfirmacaoPendente(idSessao)) {
                RespostaIA r = provedor.conversar(new PedidoIA(
                        "O usuário está respondendo a uma pergunta de confirmação. Apenas transcreva, em poucas palavras, " +
                        "o que ele disse no áudio. NÃO execute nenhuma ferramenta.",
                        audioBytes, mimeType));
                return processarRespostaConfirmacao(idSessao, r.texto() != null ? r.texto() : "");
            }
            return processarRespostaIA(idSessao, provedor.conversar(new PedidoIA(
                    "Ouça este comando de voz em português. Identifique a intenção e selecione a ferramenta apropriada " +
                    "(consulta, cadastro, atualização ou exclusão).",
                    audioBytes, mimeType)));
        } catch (Exception e) {
            return "❌ Falha ao processar comando de voz: " + e.getMessage();
        }
    }

    /** Foto/documento capturado pela câmera do celular. */
    public String processarImagem(String idSessao, byte[] imagemBytes, String mimeType, String prompt) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return bloqueio;

        try {
            String p = prompt != null && !prompt.isBlank()
                    ? prompt
                    : "Analise esta foto ou documento e execute a operação correspondente no sistema de vendas.";
            return processarRespostaIA(idSessao, provedor.conversar(new PedidoIA(p, imagemBytes, mimeType)));
        } catch (Exception e) {
            return "❌ Falha ao processar imagem: " + e.getMessage();
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
            return compiladorInstrucoesCurta.compilarEProcessar(
                    idSessao, texto, bandeja, () -> fecharJanelaContexto(idSessao));
        }

        if (temConfirmacaoPendente(idSessao)) {
            if (texto.contains("{") || texto.contains("[")) {
                return "⚠️ Ainda há uma confirmação pendente. Responda **SIM** ou **NÃO** antes de enviar novas ações.\n\n"
                        + perguntaAtual(idSessao);
            }
            return processarRespostaConfirmacao(idSessao, texto);
        }
        try {
            return processarRespostaIA(idSessao, InterpretadorRespostaIA.interpretar(texto));
        } catch (IllegalArgumentException e) {
            return "⚠️ " + e.getMessage();
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
        return processarRespostaIAComLoop(idSessao, resp, 0);
    }

    private String processarRespostaIAComLoop(String idSessao, RespostaIA resp, int nivelLoop) {
        if (!resp.temChamadas()) {
            BandejaOperacoesContexto bandeja = bandejasPorSessao.get(idSessao);
            String textoIA = resp.texto() != null && !resp.texto().isBlank()
                    ? resp.texto()
                    : "Comando interpretado, nenhuma operação necessária.";
            if (bandeja != null && bandeja.temItens()) {
                return textoIA + "\n\n" + bandeja.formatarVisualizacao();
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
            String res = despacharChamadaTool(idSessao, c);
            if (res != null && !res.isBlank()) {
                if (resultadosLeitura.length() > 0) resultadosLeitura.append("\n\n");
                resultadosLeitura.append(res);
            }
        }

        // Se a IA chamou apenas ferramentas de leitura, mas o usuário tinha intenção de criar/emitir ou se houver loop
        if (chamadasEscrita.isEmpty() && !chamadasLeitura.isEmpty() && nivelLoop < 2 && provedor != null) {
            try {
                List<Map<String, Object>> historico = obterHistoricoSessao(idSessao);
                String promptFollowUp = "Resultados das consultas realizadas no sistema:\n"
                        + resultadosLeitura.toString() + "\n\n"
                        + "Com base nas entidades e IDs acima, conclua a operação solicitada pelo usuário "
                        + "(ex: emitir nota fiscal de compra/venda, cadastrar produto/kit, etc.) chamando a ferramenta de escrita apropriada com os IDs numéricos encontrados.";
                RespostaIA followUpResp = provedor.conversar(new PedidoIA(promptFollowUp, null, null, historico));
                if (followUpResp.temChamadas()) {
                    return processarRespostaIAComLoop(idSessao, followUpResp, nivelLoop + 1);
                }
            } catch (Exception ignored) {}
        }

        StringBuilder retornoEscritas = new StringBuilder();
        for (ChamadaFerramenta c : chamadasEscrita) {
            String r = despacharChamadaTool(idSessao, c);
            if (r != null && !r.isBlank()) {
                if (retornoEscritas.length() > 0) retornoEscritas.append("\n\n");
                retornoEscritas.append(r);
            }
        }

        BandejaOperacoesContexto bandeja = obterBandeja(idSessao);
        if (bandeja.temItens()) {
            if (bandeja.totalItens() == 1) {
                Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
                if (fila != null && !fila.isEmpty()) {
                    ConfirmacaoAcaoGatekeeper.TicketConfirmacao t = gatekeeper.buscarTicket(fila.peekFirst());
                    if (t != null) return montarPergunta(t);
                }
            }
            return bandeja.formatarVisualizacao();
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
                yield "Atualizar dados do produto " + (id.isEmpty() ? nomeAtual : id) + (!novo.isEmpty() ? " para " + novo : "");
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                String alvo = params.get("idProduto") != null ? "#" + params.get("idProduto") : "'" + params.get("nomeProduto") + "'";
                yield "Atualizar o preço de venda do produto " + alvo + " para R$ " + params.get("novoPreco");
            }
            case PRODUTO_CATALOGO_EXCLUIR -> {
                String alvo = params.get("idProduto") != null ? "#" + params.get("idProduto") : "'" + params.get("nomeProduto") + "'";
                yield "Excluir permanentemente o produto " + alvo + " do catálogo";
            }
            case PRODUTO_FISICO_CADASTRAR -> "Cadastrar novo produto físico '" + params.get("nome") + "' com suas dimensões e peso";
            case PRODUTO_DIGITAL_CADASTRAR -> "Cadastrar novo produto digital '" + params.get("nome") + "'";
            case PRODUTO_UNIDADE_CADASTRAR -> "Cadastrar nova unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_UNIDADE_ATUALIZAR -> "Atualizar dados da unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_UNIDADE_EXCLUIR -> "Excluir unidade de medida '" + params.get("sigla") + "'";
            case PESSOA_SIMPLES_CADASTRAR -> {
                String nomeP = params.get("nome") != null ? params.get("nome").toString() : (params.get("razaoSocial") != null ? params.get("razaoSocial").toString() : "");
                String papelP = params.get("papel") != null ? params.get("papel").toString() : (Boolean.parseBoolean(String.valueOf(params.get("isCliente"))) ? "cliente" : "fornecedor");
                yield "Cadastrar " + papelP.toLowerCase(java.util.Locale.ROOT) + " '" + nomeP + "' (sem exigir CNPJ/CPF)";
            }
            case PESSOA_LISTAR_CLIENTES -> "Listar clientes cadastrados na base de dados";
            case PESSOA_LISTAR_FORNECEDORES -> "Listar fornecedores cadastrados na base de dados";
            case PESSOA_BUSCAR -> "Buscar clientes ou fornecedores pelo termo '" + params.get("termo") + "'";
            case PESSOA_BUSCAR_UNICA -> "Buscar uma única pessoa (cliente ou fornecedor) com os critérios informados";
            case PESSOA_REGISTRO_BUSCAR_UNICO -> "Buscar uma única pessoa pelo documento/registro único";
            case PESSOA_HISTORICO_COMPLETO -> "Consultar histórico 360º completo (notas, produtos e pagamentos) da pessoa";
            case PESSOA_CLIENTE_EXCLUIR -> "Excluir cadastro do cliente/pessoa #" + params.get("idPessoa");
            case PESSOA_CLIENTE_PF_CADASTRAR -> "Cadastrar cliente pessoa física '" + params.get("nome") + "'";
            case PESSOA_CLIENTE_PJ_CADASTRAR -> "Cadastrar cliente pessoa jurídica '" + params.get("razaoSocial") + "'";
            case PESSOA_FORNECEDOR_PJ_CADASTRAR -> "Cadastrar fornecedor pessoa jurídica '" + (params.get("razaoSocial") != null ? params.get("razaoSocial") : params.get("nome")) + "'";
            case PESSOA_FORNECEDOR_PF_CADASTRAR -> "Cadastrar fornecedor pessoa física '" + params.get("nome") + "'";
            case PESSOA_DADOS_ATUALIZAR -> "Atualizar dados cadastrais da pessoa #" + params.get("idPessoa");
            case PAGAMENTO_FORMA_CADASTRAR -> "Cadastrar nova forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_FORMA_EXCLUIR -> "Excluir forma de pagamento '" + params.get("codigo") + "'";
            case ESTOQUE_SALDO_CONSOLIDADO -> "Consultar saldo consolidado do produto #" + params.get("idProduto");
            case ESTOQUE_FILA_LOTES_FIFO -> "Consultar lotes em fila FIFO para o produto #" + params.get("idProduto");
            case ESTOQUE_LOTES_VALIDOS_LISTAR -> "Listar todos os lotes de estoque válidos e não vencidos";
            case ESTOQUE_LOTE_VALIDO_BUSCAR_UNICO -> "Buscar o primeiro lote único com estoque válido (FIFO)";
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> "Baixar perda operacional direta no lote #" + params.get("idLote");
            case NOTA_VENDA_EMITIR_FIFO -> "Emitir nota fiscal de venda com consumo FIFO de estoque";
            case NOTA_COMPRA_EMITIR -> "Emitir nota fiscal de compra e dar entrada no estoque";
            case NOTA_BUSCAR_UNICA -> "Buscar nota operacional única #" + (params.get("idNota") != null ? params.get("idNota") : params.get("id"));
            case NOTA_LISTAR_PADRAO -> "Listar notas operacionais registradas no sistema";
            case NOTA_DEFINIR_DATA -> "Definir/atualizar a data da nota operacional #" + params.get("idNota");
            case FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS -> "Gerar relatório integrado de fatores externos (Rios, Clima, Calendário e Eventos) citando cada Nota";
            case BUSCA_PADRAO_IA -> "Executar busca padrão estruturada de leitura da IA na entidade " + params.get("entidade");
            default -> rota.getDescricaoOperacao();
        };
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
                    sb.append("\n\nA bandeja está vazia, tudo foi tratado.");
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
            default -> {
                return bandeja.formatarVisualizacao();
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
                    return relatorioFormatado;
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
            sb.append(String.format("  %d. %s\n", (i + 1), d.descricao()));
            if (d.sucesso()) {
                if (d.dados() != null) {
                    RotaSistema r = roteadorController.resolverRota(d.rota());
                    Map<String, Object> paramsItem = d.parametros() != null ? d.parametros() : Collections.emptyMap();
                    sb.append("     ").append(formatarResultadoAmigavel(r, paramsItem, d.dados()).replace("\n", "\n     ")).append("\n");
                } else {
                    sb.append("     ").append(d.mensagem()).append("\n");
                }
            } else {
                sb.append("     Motivo: ").append(d.mensagem()).append("\n");
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
                    sb.append(String.format("  #%d - %s — R$ %s (%s)\n",
                            p.getId(),
                            CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), umSigla),
                            p.getPrecoVenda() != null ? p.getPrecoVenda().setScale(2, java.math.RoundingMode.HALF_EVEN) : "0.00",
                            umSigla));
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
                        sb.append(String.format("  Nota #%d (%s) — Data: %s, Pessoa: %s, Total: R$ %s\n",
                                n.getId(), n.getClass().getSimpleName(),
                                n.getDataGerada() != null ? n.getDataGerada().toLocalDate() : "N/A",
                                nomePes, n.getValorTotal()));
                    }
                }
                return sb.toString().trim();
            }
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("relatorioTextoPadronizado")) {
            return String.valueOf(map.get("relatorioTextoPadronizado"));
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("entidadeConsultada") && map.containsKey("dados")) {
            Object interno = map.get("dados");
            return formatarResultadoAmigavel(rota, params, interno);
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
            return "Saldo/Valor: R$ " + bd.toPlainString();
        }
        if (dados instanceof Boolean b) {
            return b ? "Operação concluída com sucesso." : "A operação não teve efeito.";
        }
        return String.valueOf(dados);
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
}
