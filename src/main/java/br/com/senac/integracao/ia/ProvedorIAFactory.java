package br.com.senac.integracao.ia;

import br.com.senac.integracao.gemini.GeminiClientService;

/**
 * Escolhe o provedor de IA conforme config/ia.properties.
 * Retorna null no modo "manual": nesse caso nenhuma API é chamada e o usuário usa a ponte
 * (manual copiado para o Gemini do celular/PC + resposta colada de volta no sistema).
 */
public final class ProvedorIAFactory {

    private ProvedorIAFactory() {
    }

    public static ProvedorIA criar(GeminiClientService geminiClient) {
        String id = ConfigIA.getInstancia().getProvedor().toLowerCase();
        return switch (id) {
            case "manual", "sem-api", "sem_api" -> null;
            case "openai", "ollama", "lmstudio", "local" -> new OpenAiCompativelProvedor();
            default -> new GeminiApiProvedor(geminiClient);
        };
    }
}
