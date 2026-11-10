package br.com.senac.integracao.ia;

import br.com.senac.integracao.gemini.GeminiToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Adaptador para qualquer servidor compatível com a API OpenAI (/chat/completions):
 * Ollama, LM Studio, llama.cpp, vLLM, OpenAI, etc. Permite rodar uma IA local na sua rede, sem API do Google.
 * Aponte ia.openai.url para o servidor (ex: http://192.168.0.10:11434/v1).
 */
public class OpenAiCompativelProvedor implements ProvedorIA {

    private final ConfigIA cfg = ConfigIA.getInstancia();
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Override
    public String getId() {
        return "openai";
    }

    @Override
    public String getDescricao() {
        return "IA compatível OpenAI (" + cfg.getOpenaiModelo() + " em " + cfg.getOpenaiUrl() + ")";
    }

    @Override
    public boolean suportaMidia() {
        return false;
    }

    @Override
    public String verificarPronto() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespostaIA conversar(PedidoIA pedido) throws Exception {
        Map<String, Object> sys = GeminiToolRegistry.gerarSystemInstruction();
        String textoSistema = String.valueOf(((Map<String, Object>) ((List<?>) sys.get("parts")).get(0)).get("text"));

        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, Object> f : GeminiToolRegistry.gerarDeclaracoesTodasTools()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", f.get("name"));
            fn.put("description", f.get("description"));
            fn.put("parameters", tiposMinusculos(f.get("parameters")));
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", fn);
            tools.add(tool);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getOpenaiModelo());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", textoSistema),
                Map.of("role", "user", "content", pedido.mensagem())));
        body.put("tools", tools);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getOpenaiUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(cfg.getOpenaiTimeoutSegundos()))
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)));
        if (!cfg.getOpenaiChave().isBlank()) {
            rb.header("Authorization", "Bearer " + cfg.getOpenaiChave());
        }

        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Erro do provedor OpenAI-compatível (HTTP " + resp.statusCode() + "): " + resp.body());
        }

        JsonNode msg = om.readTree(resp.body()).path("choices").path(0).path("message");
        String texto = msg.path("content").asText("");
        List<ChamadaFerramenta> chamadas = new ArrayList<>();

        for (JsonNode tc : msg.path("tool_calls")) {
            JsonNode fn = tc.path("function");
            JsonNode args = fn.path("arguments");
            if (args.isTextual()) {
                String txt = args.asText().trim();
                args = txt.isEmpty() ? om.createObjectNode() : om.readTree(txt);
            }
            Map<String, Object> params = om.convertValue(args, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            chamadas.add(new ChamadaFerramenta(fn.path("name").asText(), params));
        }

        // Modelos locais pequenos às vezes respondem no formato do manual em vez de usar tool_calls.
        if (chamadas.isEmpty() && texto.contains("\"acoes\"")) {
            try {
                return InterpretadorRespostaIA.interpretar(texto);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new RespostaIA(texto, chamadas);
    }

    /** O JSON Schema da API OpenAI usa tipos em minúsculo ("object"), o do Gemini em maiúsculo. */
    private Object tiposMinusculos(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (k.equals("type") && e.getValue() instanceof String s) {
                    r.put(k, s.toLowerCase());
                } else {
                    r.put(k, tiposMinusculos(e.getValue()));
                }
            }
            return r;
        }
        if (o instanceof List<?> l) {
            List<Object> r = new ArrayList<>();
            for (Object x : l) r.add(tiposMinusculos(x));
            return r;
        }
        return o;
    }
}
