package br.com.senac.integracao.gemini;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Cliente HTTP não-bloqueante para a API Google Gemini 3.8 Flash baseado em Virtual Threads (Java 21+).
 * Suporta:
 * 1. Text Generation e Multi-turn Chat
 * 2. Function Calling / Tool Calling com as 60 rotas do RotaSistema
 * 3. Entrada Multimodal (Áudio de microfone / Voz e Documentos/Imagens)
 * 4. Execução concorrente assíncrona segura
 */
public class GeminiClientService {

    private final GeminiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClientService() {
        this(GeminiConfig.getInstancia());
    }

    public GeminiClientService(GeminiConfig config) {
        this.config = config;
        // Instancia HttpClient nativo do Java 21 gerenciado por Virtual Threads
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Envia uma mensagem de texto simples para o Gemini e retorna a resposta de texto gerada.
     */
    public String conversarTexto(String mensagemUsuario) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", criarConteudoUsuario(mensagemUsuario));
        payload.put("generationConfig", criarGenerationConfig());

        JsonNode root = executarRequisicaoGemini(payload);
        return extrairTextoResposta(root);
    }

    /**
     * Envia uma mensagem acompanhada de todas as 60 Tools do sistema e do System Instruction.
     * Devolve a resposta estruturada contendo texto explicativo ou a lista de chamadas de ferramentas solicitadas.
     */
    public RespostaGemini conversarComTools(String mensagemUsuario, List<Map<String, Object>> historicoOpcional) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();

        // Injeta System Instruction e Ferramentas do Sazonalisei
        payload.put("system_instruction", GeminiToolRegistry.gerarSystemInstruction());
        payload.put("tools", GeminiToolRegistry.gerarToolsWrapper());

        List<Map<String, Object>> contents = new ArrayList<>();
        if (historicoOpcional != null && !historicoOpcional.isEmpty()) {
            contents.addAll(historicoOpcional);
        }
        contents.addAll(criarConteudoUsuario(mensagemUsuario));
        payload.put("contents", contents);

        payload.put("generationConfig", criarGenerationConfig());

        JsonNode root = executarRequisicaoGemini(payload);
        return interpretarResposta(root);
    }

    /**
     * Envia arquivo binário (áudio de voz, foto de nota ou PDF de fatura) com prompt multimodal.
     */
    public RespostaGemini conversarMultimodal(String prompt, byte[] arquivoBytes, String mimeType) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", GeminiToolRegistry.gerarSystemInstruction());
        payload.put("tools", GeminiToolRegistry.gerarToolsWrapper());

        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", Base64.getEncoder().encodeToString(arquivoBytes));

        Map<String, Object> partData = new LinkedHashMap<>();
        partData.put("inline_data", inlineData);

        Map<String, Object> partText = new LinkedHashMap<>();
        partText.put("text", prompt);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", Arrays.asList(partText, partData));

        payload.put("contents", Collections.singletonList(content));
        payload.put("generationConfig", criarGenerationConfig());

        JsonNode root = executarRequisicaoGemini(payload);
        return interpretarResposta(root);
    }

    /**
     * Execução concorrente assíncrona usando CompletableFuture e Virtual Threads.
     */
    public CompletableFuture<RespostaGemini> conversarComToolsAsync(String mensagemUsuario) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return conversarComTools(mensagemUsuario, null);
            } catch (Exception e) {
                throw new RuntimeException("Erro na execução concorrente do Gemini: " + e.getMessage(), e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    private JsonNode executarRequisicaoGemini(Map<String, Object> payloadMap) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(payloadMap);

        List<String> modelosParaTentar = new ArrayList<>();
        modelosParaTentar.add(config.getModel());
        for (String fb : Arrays.asList("gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-3.5-flash-lite")) {
            if (!modelosParaTentar.contains(fb)) {
                modelosParaTentar.add(fb);
            }
        }

        String ultimoErro = "";
        for (String modeloAtual : modelosParaTentar) {
            String endpointUrl = config.getEndpointUrl(modeloAtual);

            for (int tentativa = 1; tentativa <= 2; tentativa++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(endpointUrl))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        return objectMapper.readTree(response.body());
                    }

                    if (response.statusCode() == 503 || response.statusCode() == 429) {
                        ultimoErro = "HTTP " + response.statusCode() + " (" + modeloAtual + "): " + response.body();
                        System.err.println("Aviso: Modelo " + modeloAtual + " com alta demanda (" + response.statusCode() + "). Tentativa " + tentativa + " de 2...");
                        Thread.sleep(1000L * tentativa);
                        continue;
                    }

                    throw new IOException("Erro da API Gemini (HTTP " + response.statusCode() + "): " + response.body());

                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("HTTP 400")) {
                        throw e;
                    }
                    ultimoErro = e.getMessage();
                }
            }
            System.err.println("Alternando automaticamente para o próximo modelo disponível...");
        }

        throw new IOException("Não foi possível obter resposta da API Gemini após tentativas com múltiplos modelos. Último erro: " + ultimoErro);
    }

    private List<Map<String, Object>> criarConteudoUsuario(String texto) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", texto);

        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");
        content.put("parts", Collections.singletonList(textPart));

        return Collections.singletonList(content);
    }

    private Map<String, Object> criarGenerationConfig() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("temperature", config.getTemperature());
        configMap.put("maxOutputTokens", config.getMaxOutputTokens());
        return configMap;
    }

    private String extrairTextoResposta(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        sb.append(part.get("text").asText());
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    private RespostaGemini interpretarResposta(JsonNode root) {
        RespostaGemini resp = new RespostaGemini();
        JsonNode candidates = root.path("candidates");

        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray()) {
                StringBuilder texto = new StringBuilder();
                for (JsonNode p : parts) {
                    if (p.has("text")) {
                        texto.append(p.get("text").asText());
                    }
                    if (p.has("functionCall")) {
                        JsonNode fc = p.get("functionCall");
                        String name = fc.path("name").asText();
                        Map<String, Object> args = new LinkedHashMap<>();
                        if (fc.has("args")) {
                            args = objectMapper.convertValue(fc.get("args"), Map.class);
                        }
                        resp.adicionarChamadaTool(new ChamadaToolIA(name, args));
                    }
                }
                resp.setTexto(texto.toString());
            }
        }

        return resp;
    }

    // ==================== ESTRUTURAS AUXILIARES DE RESPOSTA ====================

    public static class RespostaGemini {
        private String texto = "";
        private final List<ChamadaToolIA> chamadasTools = new ArrayList<>();

        public boolean temChamadasTools() {
            return !chamadasTools.isEmpty();
        }

        public void adicionarChamadaTool(ChamadaToolIA chamada) {
            this.chamadasTools.add(chamada);
        }

        public String getTexto() {
            return texto;
        }

        public void setTexto(String texto) {
            this.texto = texto;
        }

        public List<ChamadaToolIA> getChamadasTools() {
            return chamadasTools;
        }

        @Override
        public String toString() {
            return "RespostaGemini{" +
                    "texto='" + texto + '\'' +
                    ", chamadasTools=" + chamadasTools +
                    '}';
        }
    }

    public record ChamadaToolIA(String nomeFuncao, Map<String, Object> parametros) {
    }
}
