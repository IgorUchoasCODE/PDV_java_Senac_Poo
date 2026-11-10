package br.com.senac.integracao.gemini;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP Móvel Embutido (JDK 21) para controle do sistema pelo Celular via Wi-Fi.
 * Disponibiliza uma interface web responsiva com:
 * 1. Gravação de comandos de voz diretamente do microfone do celular
 * 2. Captura de fotos de notas e produtos usando a câmera do celular
 * 3. Entrada de texto e exibição do retorno das 60 Tools do Gemini
 * 4. Botões de confirmação para ações destrutivas (Human-in-the-Loop)
 */
public class GeminiMobileServer {

    private final int porta;
    private final GeminiAgenteOperacional agente;
    private final GeminiClientService geminiClient;
    private final RoteadorController roteador;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public GeminiMobileServer() {
        this(8080);
    }

    public GeminiMobileServer(int porta) {
        this.porta = porta;
        this.agente = new GeminiAgenteOperacional();
        this.geminiClient = this.agente.getGeminiClient();
        this.roteador = this.agente.getRoteadorController();
        this.objectMapper = new ObjectMapper();
    }

    public void iniciar() throws IOException {
        server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new PaginaWebHandler());
        server.createContext("/api/comando/texto", new ComandoTextoHandler());
        server.createContext("/api/comando/audio", new ComandoAudioHandler());
        server.createContext("/api/comando/imagem", new ComandoImagemHandler());
        server.createContext("/api/config/personalidade", new ConfigPersonalidadeHandler());
        server.createContext("/api/ia/manual", new ManualIAHandler());
        server.createContext("/api/ia/executar", new ExecutarRespostaExternaHandler());
        server.createContext("/api/ia/provedor", new ProvedorInfoHandler());

        server.start();

        String ipLocal = obterIpLocal();
        System.out.println("================================================================================");
        System.out.println(" SERVIDOR MÓVEL GEMINI INICIADO COM SUCESSO!");
        System.out.println("================================================================================");
        System.out.println(" No seu COMPUTADOR, acesse: http://localhost:" + porta);
        System.out.println(" No seu CELULAR (conectado no mesmo Wi-Fi), acesse:");
        System.out.println(" >>> http://" + ipLocal + ":" + porta + " <<<");
        System.out.println("================================================================================");
    }

    public void parar() {
        if (server != null) {
            server.stop(1);
            System.out.println("Servidor Móvel Gemini encerrado.");
        }
    }

    public String obterIpLocal() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    // ==================== HANDLER DA PÁGINA WEB (HTML5 / JS / CSS) ====================

    private class PaginaWebHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = gerarHtmlPaginaMobile();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ==================== HANDLER DE TEXTO ====================

    private class ComandoTextoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(corpo);
            String mensagem = json.path("mensagem").asText("");
            String sessao = json.path("sessao").asText("mobile-user");

            String resposta = agente.processarMensagem(sessao, mensagem);

            enviarJsonResposta(exchange, 200, Map.of("resposta", resposta));
        }
    }

    // ==================== HANDLER DE ÁUDIO (VOZ DO CELULAR) ====================

    private class ComandoAudioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(corpo);
                String base64 = json.path("base64").asText("");
                String mimeType = json.path("mimeType").asText("audio/webm");
                String sessao = json.path("sessao").asText("mobile-user");

                if (base64.isBlank()) {
                    enviarJsonResposta(exchange, 400, Map.of("erro", "Payload de áudio Base64 vazio."));
                    return;
                }

                byte[] audioBytes = Base64.getDecoder().decode(limparBase64(base64));
                String resultadoFinal = agente.processarAudio(sessao, audioBytes, mimeType);
                enviarJsonResposta(exchange, 200, Map.of("resposta", resultadoFinal));

            } catch (Exception e) {
                enviarJsonResposta(exchange, 500, Map.of("erro", "Falha ao processar comando de voz: " + e.getMessage()));
            }
        }
    }

    // ==================== HANDLER DE IMAGEM (FOTO/SCAN DA CÂMERA DO CELULAR) ====================

    private class ComandoImagemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(corpo);
                String base64 = json.path("base64").asText("");
                String mimeType = json.path("mimeType").asText("image/jpeg");
                String prompt = json.path("prompt").asText("Analise esta imagem/documento e execute a operação correspondente no sistema de vendas.");
                String sessao = json.path("sessao").asText("mobile-user");

                if (base64.isBlank()) {
                    enviarJsonResposta(exchange, 400, Map.of("erro", "Payload de imagem Base64 vazio."));
                    return;
                }

                byte[] imagemBytes = Base64.getDecoder().decode(limparBase64(base64));
                String resultadoFinal = agente.processarImagem(sessao, imagemBytes, mimeType, prompt);
                enviarJsonResposta(exchange, 200, Map.of("resposta", resultadoFinal));

            } catch (Exception e) {
                enviarJsonResposta(exchange, 500, Map.of("erro", "Falha ao processar imagem: " + e.getMessage()));
            }
        }
    }

    // ==================== HANDLER DE CONFIGURAÇÃO DE PERSONALIDADE ====================

    private class ConfigPersonalidadeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            GeminiConfig cfg = GeminiConfig.getInstancia();

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, Object> dados = new LinkedHashMap<>();
                dados.put("personalidade", cfg.getPersonalidade());
                dados.put("instrucoes", cfg.getInstrucoesCustomizadas());
                enviarJsonResposta(exchange, 200, dados);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(corpo);
                String novaPersona = json.path("personalidade").asText("");
                String novasInstrucoes = json.path("instrucoes").asText("");

                cfg.salvarPersonalidade(novaPersona, novasInstrucoes);

                enviarJsonResposta(exchange, 200, Map.of(
                        "sucesso", true,
                        "mensagem", "Personalidade e instruções salvas com sucesso!"
                ));
                return;
            }

            exchange.sendResponseHeaders(405, -1);
        }
    }

    // ==================== PONTE SEM API (IA EXTERNA: GEMINI DO CELULAR, ETC.) ====================

    /** Devolve o manual autoexplicativo em texto puro, pronto para colar em qualquer IA. */
    private class ManualIAHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bytes = br.com.senac.integracao.ia.ManualInstrucoesIA.gerar().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /** Recebe o que a IA externa respondeu (JSON do manual) e executa com validação e confirmação. */
    private class ExecutarRespostaExternaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(corpo);
            String texto = json.path("texto").asText("");
            String sessao = json.path("sessao").asText("mobile-user");

            enviarJsonResposta(exchange, 200, Map.of("resposta", agente.processarRespostaExterna(sessao, texto)));
        }
    }

    /** Informa qual IA está ativa (ou se está no modo sem API). */
    private class ProvedorInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            configurarCors(exchange);
            var p = agente.getProvedor();
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("provedor", p != null ? p.getId() : "manual");
            dados.put("descricao", p != null ? p.getDescricao() : "Modo sem API (IA externa via copiar/colar)");
            dados.put("modoSemApi", p == null);
            enviarJsonResposta(exchange, 200, dados);
        }
    }

    // ==================== AUXILIARES ====================

    private String limparBase64(String raw) {
        int idx = raw.indexOf(',');
        if (idx >= 0) {
            return raw.substring(idx + 1).trim();
        }
        return raw.trim();
    }

    private void configurarCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void enviarJsonResposta(HttpExchange exchange, int status, Map<String, Object> dados) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(dados);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== TEMPLATE HTML5 MOBILE RESPONSIVO ====================

    private String gerarHtmlPaginaMobile() {
        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Sazonalisei AI - Controle Móvel</title>
    <style>
        :root {
            --bg: #0f172a;
            --card: #1e293b;
            --primary: #38bdf8;
            --primary-hover: #0284c7;
            --danger: #ef4444;
            --success: #22c55e;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --border: #334155;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); padding: 16px; min-height: 100vh; display: flex; flex-direction: column; }
        header { text-align: center; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--border); }
        h1 { font-size: 1.3rem; color: var(--primary); }
        p.sub { font-size: 0.8rem; color: var(--text-muted); margin-top: 2px; }
        
        .chat-container { flex: 1; background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; overflow-y: auto; max-height: 52vh; display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
        .msg { padding: 12px 14px; border-radius: 10px; font-size: 0.9rem; line-height: 1.4; max-width: 90%; white-space: pre-wrap; word-break: break-word; }
        .msg-user { background: #0369a1; align-self: flex-end; border-bottom-right-radius: 2px; }
        .msg-ia { background: #334155; align-self: flex-start; border-bottom-left-radius: 2px; }
        .msg-loading { color: var(--primary); font-style: italic; }
        
        .controls { display: flex; flex-direction: column; gap: 10px; }
        .action-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
        
        button { border: none; border-radius: 10px; padding: 13px; font-size: 0.95rem; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; transition: 0.2s; color: #fff; }
        .btn-mic { background: #2563eb; }
        .btn-mic.recording { background: var(--danger); animation: pulse 1s infinite; }
        .btn-cam { background: #059669; }
        .btn-send { background: var(--primary); color: #0f172a; }
        
        @keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.5; } 100% { opacity: 1; } }
        
        .text-input-row { display: flex; gap: 8px; }
        input[type="text"] { flex: 1; background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 12px; color: var(--text); font-size: 1rem; outline: none; }
        input[type="text"]:focus { border-color: var(--primary); }
        input[type="file"] { display: none; }
    </style>
</head>
<body>
    <header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
            <div style="text-align:left;">
                <h1>⚡ Sazonalisei Gemini AI</h1>
                <p class="sub">Voz, Câmera e Operações em Tempo Real</p>
            </div>
            <button id="btnOpenSemApi" style="padding:8px 12px; font-size:0.8rem; background:#7c3aed; border-radius:8px;">🧩 Sem API</button>
            <button id="btnOpenPersona" style="padding:8px 12px; font-size:0.8rem; background:#334155; border-radius:8px;">⚙️ Personalidade</button>
        </div>
    </header>

    <div class="chat-container" id="chat">
        <div class="msg msg-ia">Olá! Estou pronta para operar o sistema. Você pode falar no microfone, tirar uma foto de uma nota ou digitar um comando.</div>
    </div>

    <div class="controls">
        <div class="action-row">
            <button class="btn-mic" id="btnMic">🎤 Gravar Voz</button>
            <button class="btn-cam" onclick="document.getElementById('fileInput').click()">📷 Foto / Scan</button>
        </div>
        <input type="file" id="fileInput" accept="image/*" capture="environment">

        <div class="text-input-row">
            <input type="text" id="txtInput" placeholder="Ex: Quanto vendemos ontem?">
            <button class="btn-send" id="btnSend">Enviar</button>
        </div>
    </div>

    <!-- MODAL SEM API: USAR O GEMINI DO CELULAR/PC EM VEZ DA API -->
    <div id="modalSemApi" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.75); z-index:100; justify-content:center; align-items:center; padding:16px;">
        <div style="background:var(--card); border:1px solid var(--border); border-radius:12px; padding:20px; max-width:520px; width:100%; max-height:92vh; overflow-y:auto; display:flex; flex-direction:column; gap:10px;">
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <h2 style="font-size:1.1rem; color:var(--primary);">🧩 Usar a IA do celular (sem API)</h2>
                <button id="btnCloseSemApi" style="background:none; border:none; font-size:1.2rem; cursor:pointer;">✖</button>
            </div>
            <p style="font-size:0.8rem; color:var(--text-muted);"><b>1.</b> Copie o manual e cole UMA vez numa conversa nova do Gemini (app do celular ou site).</p>
            <button id="btnCopiarManual" style="background:#2563eb; color:#fff; padding:11px; border-radius:8px;">📋 Copiar manual para a IA</button>
            <textarea id="txtManual" rows="5" readonly style="display:none; background:#0f172a; border:1px solid var(--border); border-radius:8px; padding:8px; color:#94a3b8; font-size:0.7rem; width:100%;"></textarea>
            <p style="font-size:0.8rem; color:var(--text-muted);"><b>2.</b> Fale ou digite o pedido para o Gemini (ex: "apaga o produto 86"). Ele responde com um JSON.</p>
            <p style="font-size:0.8rem; color:var(--text-muted);"><b>3.</b> Copie a resposta dele, cole abaixo e execute. Eu valido, peço confirmação nas ações perigosas e executo.</p>
            <textarea id="txtRespostaIA" rows="6" style="background:#0f172a; border:1px solid var(--border); border-radius:8px; padding:10px; color:#fff; font-size:0.8rem; width:100%; resize:vertical;" placeholder="Cole aqui a resposta da IA..."></textarea>
            <button id="btnExecutarIA" style="background:var(--success); color:#fff; padding:12px; font-weight:600; border-radius:8px;">▶️ Executar resposta da IA</button>
        </div>
    </div>

    <!-- MODAL DE PERSONALIDADE E INSTRUÇÕES -->
    <div id="modalPersona" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.75); z-index:100; justify-content:center; align-items:center; padding:16px;">
        <div style="background:var(--card); border:1px solid var(--border); border-radius:12px; padding:20px; max-width:500px; width:100%; display:flex; flex-direction:column; gap:12px;">
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <h2 style="font-size:1.1rem; color:var(--primary);">🎭 Personalidade & Instruções da IA</h2>
                <button id="btnClosePersona" style="background:none; border:none; font-size:1.2rem; cursor:pointer;">✖</button>
            </div>
            <p style="font-size:0.8rem; color:var(--text-muted);">Defina o tom de voz e as instruções customizadas para a IA:</p>
            
            <div style="display:flex; gap:6px; flex-wrap:wrap;">
                <button type="button" class="preset-btn" onclick="aplicarPreset('amigavel')" style="padding:6px 10px; font-size:0.75rem; background:#334155;">😊 Amigável</button>
                <button type="button" class="preset-btn" onclick="aplicarPreset('formal')" style="padding:6px 10px; font-size:0.75rem; background:#334155;">👔 Formal</button>
                <button type="button" class="preset-btn" onclick="aplicarPreset('tecnico')" style="padding:6px 10px; font-size:0.75rem; background:#334155;">⚡ Rápido/Direto</button>
                <button type="button" class="preset-btn" onclick="aplicarPreset('vendedor')" style="padding:6px 10px; font-size:0.75rem; background:#334155;">💼 Consultor Vendas</button>
            </div>

            <label style="font-size:0.85rem; font-weight:600; color:var(--text);">Tom de Voz / Personalidade:</label>
            <textarea id="txtPersonalidade" rows="4" style="background:#0f172a; border:1px solid var(--border); border-radius:8px; padding:10px; color:#fff; font-size:0.85rem; width:100%; resize:vertical;" placeholder="Ex: Você é um assistente animado e bem-humorado..."></textarea>

            <label style="font-size:0.85rem; font-weight:600; color:var(--text);">Instruções Específicas Adicionais:</label>
            <textarea id="txtInstrucoes" rows="3" style="background:#0f172a; border:1px solid var(--border); border-radius:8px; padding:10px; color:#fff; font-size:0.85rem; width:100%; resize:vertical;" placeholder="Ex: Sempre que listar produtos, lembre de sugerir formas de pagamento..."></textarea>

            <button id="btnSalvarPersona" style="background:var(--success); color:#fff; padding:12px; font-weight:600; border-radius:8px;">💾 Salvar e Aplicar Agora</button>
        </div>
    </div>

    <script>
        const chat = document.getElementById('chat');
        const txtInput = document.getElementById('txtInput');
        const btnSend = document.getElementById('btnSend');
        const btnMic = document.getElementById('btnMic');
        const fileInput = document.getElementById('fileInput');

        // Modal Persona
        const modalPersona = document.getElementById('modalPersona');
        const btnOpenPersona = document.getElementById('btnOpenPersona');
        const btnClosePersona = document.getElementById('btnClosePersona');
        const btnSalvarPersona = document.getElementById('btnSalvarPersona');
        const txtPersonalidade = document.getElementById('txtPersonalidade');
        const txtInstrucoes = document.getElementById('txtInstrucoes');

        btnOpenPersona.onclick = () => { modalPersona.style.display = 'flex'; carregarPersona(); };
        btnClosePersona.onclick = () => { modalPersona.style.display = 'none'; };

        const presets = {
            amigavel: {
                persona: "Você é um assistente super simpático, acolhedor e atencioso. Use emojis moderados e seja prestativo ao máximo em todas as respostas.",
                inst: "Sempre deseje um excelente dia de trabalho ao usuário."
            },
            formal: {
                persona: "Você é um consultor financeiro e operacional sênior. Responda com linguagem culta, sóbria, objetiva e extremamente profissional.",
                inst: "Priorize métricas financeiras, conformidade contábil e exatidão nos valores."
            },
            tecnico: {
                persona: "Você é um operador de sistema direto e sintético. Responda em poucas linhas, priorizando listas com marcadores e dados diretos.",
                inst: "Evite introduções longas ou saudações repetitivas. Vá direto aos fatos."
            },
            vendedor: {
                persona: "Você é um consultor de vendas experiente e dinâmico, focado em alta rotatividade de estoque e rentabilidade.",
                inst: "Sempre que consultar produtos, aponte a oportunidade comercial e sugira pagamento via PIX para fluxo de caixa imediato."
            }
        };

        function aplicarPreset(tipo) {
            const p = presets[tipo];
            if (p) {
                txtPersonalidade.value = p.persona;
                txtInstrucoes.value = p.inst;
            }
        }

        async function carregarPersona() {
            try {
                const res = await fetch('/api/config/personalidade');
                const data = await res.json();
                txtPersonalidade.value = data.personalidade || '';
                txtInstrucoes.value = data.instrucoes || '';
            } catch (ignored) {}
        }

        btnSalvarPersona.onclick = async () => {
            const pers = txtPersonalidade.value;
            const inst = txtInstrucoes.value;
            try {
                const res = await fetch('/api/config/personalidade', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ personalidade: pers, instrucoes: inst })
                });
                const data = await res.json();
                modalPersona.style.display = 'none';
                appendMsg('🎭 [Sistema] ' + (data.mensagem || 'Personalidade atualizada com sucesso!'));
            } catch (err) {
                alert('Erro ao salvar: ' + err.message);
            }
        };

        // ==================== MODO SEM API ====================
        const modalSemApi = document.getElementById('modalSemApi');
        const txtManual = document.getElementById('txtManual');
        const txtRespostaIA = document.getElementById('txtRespostaIA');
        document.getElementById('btnOpenSemApi').onclick = () => { modalSemApi.style.display = 'flex'; };
        document.getElementById('btnCloseSemApi').onclick = () => { modalSemApi.style.display = 'none'; };

        document.getElementById('btnCopiarManual').onclick = async () => {
            try {
                const res = await fetch('/api/ia/manual');
                txtManual.value = await res.text();
                txtManual.style.display = 'block';
                try {
                    await navigator.clipboard.writeText(txtManual.value);
                    appendMsg('📋 Manual copiado! Cole na conversa com o Gemini.');
                } catch (e) {
                    txtManual.focus();
                    txtManual.select();
                    document.execCommand('copy');
                    appendMsg('📋 Manual selecionado. Se não copiou, segure o texto e escolha Copiar.');
                }
            } catch (err) {
                alert('Erro ao obter o manual: ' + err.message);
            }
        };

        document.getElementById('btnExecutarIA').onclick = async () => {
            const texto = txtRespostaIA.value.trim();
            if (!texto) return;
            modalSemApi.style.display = 'none';
            appendMsg('🧩 [Resposta da IA externa enviada para execução]', true);
            const loading = appendMsg('Validando e executando as ações...', false, true);
            try {
                const res = await fetch('/api/ia/executar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ texto: texto, sessao: 'celular' })
                });
                const data = await res.json();
                loading.remove();
                appendMsg(data.resposta || data.erro);
                txtRespostaIA.value = '';
            } catch (err) {
                loading.remove();
                appendMsg('Erro de conexão: ' + err.message);
            }
        };

        function appendMsg(text, isUser = false, isLoading = false) {
            const div = document.createElement('div');
            div.className = 'msg ' + (isUser ? 'msg-user' : 'msg-ia') + (isLoading ? ' msg-loading' : '');
            div.innerText = text;
            chat.appendChild(div);
            chat.scrollTop = chat.scrollHeight;
            return div;
        }

        async function enviarTexto(msg) {
            if (!msg.trim()) return;
            appendMsg(msg, true);
            txtInput.value = '';

            const loading = appendMsg('Processando comando...', false, true);
            try {
                const res = await fetch('/api/comando/texto', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mensagem: msg, sessao: 'celular' })
                });
                const data = await res.json();
                loading.remove();
                appendMsg(data.resposta || data.erro);
            } catch (err) {
                loading.remove();
                appendMsg('Erro de conexão: ' + err.message);
            }
        }

        btnSend.addEventListener('click', () => enviarTexto(txtInput.value));
        txtInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') enviarTexto(txtInput.value); });

        // ==================== GRAVAÇÃO DE VOZ ====================
        let mediaRecorder;
        let audioChunks = [];
        let isRecording = false;

        btnMic.addEventListener('click', async () => {
            if (!isRecording) {
                try {
                    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                    mediaRecorder = new MediaRecorder(stream);
                    audioChunks = [];

                    mediaRecorder.ondataavailable = e => { if (e.data.size > 0) audioChunks.push(e.data); };
                    mediaRecorder.onstop = async () => {
                        const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
                        const reader = new FileReader();
                        reader.readAsDataURL(audioBlob);
                        reader.onloadend = async () => {
                            const base64Audio = reader.result;
                            appendMsg('🎤 [Áudio de voz enviado]', true);
                            const loading = appendMsg('Ouvindo e executando...', false, true);
                            try {
                                const res = await fetch('/api/comando/audio', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ base64: base64Audio, mimeType: 'audio/webm', sessao: 'celular' })
                                });
                                const data = await res.json();
                                loading.remove();
                                appendMsg(data.resposta || data.erro);
                            } catch (err) {
                                loading.remove();
                                appendMsg('Erro ao enviar áudio: ' + err.message);
                            }
                        };
                    };

                    mediaRecorder.start();
                    isRecording = true;
                    btnMic.classList.add('recording');
                    btnMic.innerText = '⏹ Parar e Enviar';
                } catch (err) {
                    alert('Permissão de microfone negada ou indisponível: ' + err.message);
                }
            } else {
                mediaRecorder.stop();
                isRecording = false;
                btnMic.classList.remove('recording');
                btnMic.innerText = '🎤 Gravar Voz';
            }
        });

        // ==================== FOTO / DOCUMENTO DA CÂMERA ====================
        fileInput.addEventListener('change', () => {
            const file = fileInput.files[0];
            if (!file) return;

            appendMsg('📷 [Foto enviada: ' + file.name + ']', true);
            const loading = appendMsg('Analisando documento/imagem...', false, true);

            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onloadend = async () => {
                const base64 = reader.result;
                try {
                    const res = await fetch('/api/comando/imagem', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            base64: base64,
                            mimeType: file.type || 'image/jpeg',
                            prompt: 'Analise este documento ou foto e execute a ferramenta necessária no sistema.',
                            sessao: 'celular'
                        })
                    });
                    const data = await res.json();
                    loading.remove();
                    appendMsg(data.resposta || data.erro);
                } catch (err) {
                    loading.remove();
                    appendMsg('Erro ao processar imagem: ' + err.message);
                }
            };
        });
    </script>
</body>
</html>
""";
    }

    public static void main(String[] args) throws IOException {
        GeminiMobileServer server = new GeminiMobileServer(8080);
        server.iniciar();
    }
}
