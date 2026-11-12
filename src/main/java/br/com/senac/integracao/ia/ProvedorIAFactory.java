package br.com.senac.integracao.ia;

import br.com.senac.integracao.gemini.GeminiClientService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fábrica e montador da cadeia de alta disponibilidade ({@link ProvedorIAComFailover})
 * conforme {@code config/ia.properties}.
 *
 * <p>Permite encadear múltiplas IAs (ex: Gemini -> OpenAI/Ollama primário -> OpenAI/LM Studio secundário)
 * para que, caso uma falhe, a próxima assuma automaticamente sem alterar nada no código consumidor.
 */
public final class ProvedorIAFactory {

    private ProvedorIAFactory() {
    }

    public static ProvedorIA criar(GeminiClientService geminiClient) {
        ConfigIA cfg = ConfigIA.getInstancia();
        String idPrincipal = cfg.getProvedor() != null ? cfg.getProvedor().trim().toLowerCase(Locale.ROOT) : "gemini";
        if ("manual".equals(idPrincipal) || "sem-api".equals(idPrincipal) || "sem_api".equals(idPrincipal)) {
            return null;
        }

        Set<String> ordemIds = new LinkedHashSet<>();
        ordemIds.add(idPrincipal);
        if (cfg.getProvedoresFailover() != null && !cfg.getProvedoresFailover().isBlank()) {
            for (String token : cfg.getProvedoresFailover().split(",")) {
                String t = token.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty() && !"manual".equals(t) && !"sem-api".equals(t)) {
                    ordemIds.add(t);
                }
            }
        }

        List<ProvedorIA> cadeia = new ArrayList<>();
        for (String id : ordemIds) {
            ProvedorIA inst = instanciarIndividual(id, geminiClient, cfg);
            if (inst != null) {
                cadeia.add(inst);
            }
        }

        if (cfg.getOpenai2Url() != null && !cfg.getOpenai2Url().isBlank()
                && ordemIds.stream().noneMatch(s -> s.equals("openai2") || s.equals("secundario"))) {
            cadeia.add(new OpenAiCompativelProvedor(
                    "openai-secundario",
                    cfg.getOpenai2Url(),
                    cfg.getOpenai2Modelo() != null && !cfg.getOpenai2Modelo().isBlank() ? cfg.getOpenai2Modelo() : cfg.getOpenaiModelo(),
                    cfg.getOpenai2Chave(),
                    cfg.getOpenaiTimeoutSegundos()
            ));
        }

        if (cadeia.isEmpty()) {
            cadeia.add(new GeminiApiProvedor(geminiClient));
        }

        return new ProvedorIAComFailover(cadeia);
    }

    private static ProvedorIA instanciarIndividual(String id, GeminiClientService geminiClient, ConfigIA cfg) {
        return switch (id) {
            case "gemini" -> new GeminiApiProvedor(geminiClient);
            case "groq" -> new OpenAiCompativelProvedor(
                    "groq",
                    "https://api.groq.com/openai/v1",
                    cfg.getGroqModelo(),
                    cfg.getGroqChave(),
                    30
            );
            case "openrouter" -> new OpenAiCompativelProvedor(
                    "openrouter",
                    "https://openrouter.ai/api/v1",
                    cfg.getOpenrouterModelo(),
                    cfg.getOpenrouterChave(),
                    60
            );
            case "cerebras" -> new OpenAiCompativelProvedor(
                    "cerebras",
                    "https://api.cerebras.ai/v1",
                    cfg.getCerebrasModelo(),
                    cfg.getCerebrasChave(),
                    30
            );
            case "mistral" -> new OpenAiCompativelProvedor(
                    "mistral",
                    "https://api.mistral.ai/v1",
                    cfg.getMistralModelo(),
                    cfg.getMistralChave(),
                    45
            );
            case "openai", "ollama", "lmstudio", "local" -> new OpenAiCompativelProvedor();
            case "openai2", "secundario", "backup" -> new OpenAiCompativelProvedor(
                    "openai-secundario",
                    cfg.getOpenai2Url() != null && !cfg.getOpenai2Url().isBlank() ? cfg.getOpenai2Url() : cfg.getOpenaiUrl(),
                    cfg.getOpenai2Modelo() != null && !cfg.getOpenai2Modelo().isBlank() ? cfg.getOpenai2Modelo() : cfg.getOpenaiModelo(),
                    cfg.getOpenai2Chave(),
                    cfg.getOpenaiTimeoutSegundos()
            );
            default -> null;
        };
    }
}
