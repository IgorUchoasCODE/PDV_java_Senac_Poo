package br.com.senac.integracao.ia;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Configuração de qual IA está ativa e das APIs gratuitas disponíveis. Arquivo: config/ia.properties
 */
public class ConfigIA {

    private String provedor = "gemini";
    private String provedoresFailover = "gemini,groq,openrouter,cerebras,mistral,openai";

    // Provedor Local / OpenAI compatível
    private String openaiUrl = "http://localhost:11434/v1";
    private String openaiModelo = "llama3.1";
    private String openaiChave = "";
    private int openaiTimeoutSegundos = 120;
    private String openai2Url = "";
    private String openai2Modelo = "";
    private String openai2Chave = "";

    // Provedores Gratuitos de Nuvem
    private String groqChave = "";
    private String groqModelo = "llama-3.3-70b-versatile";

    private String openrouterChave = "";
    private String openrouterModelo = "meta-llama/llama-3.3-70b-instruct:free";

    private String cerebrasChave = "";
    private String cerebrasModelo = "llama3.1-8b";

    private String mistralChave = "";
    private String mistralModelo = "mistral-small-latest";

    private static ConfigIA instancia;

    public static synchronized ConfigIA getInstancia() {
        if (instancia == null) {
            instancia = new ConfigIA();
            instancia.carregar();
        }
        return instancia;
    }

    public synchronized void carregar() {
        Properties p = new Properties();
        File f = new File("config/ia.properties");
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception ignored) {
            }
        }
        provedor = valor(p, "ia.provedor", provedor);
        provedoresFailover = valor(p, "ia.provedores.failover", provedoresFailover);
        openaiUrl = valor(p, "ia.openai.url", openaiUrl);
        openaiModelo = valor(p, "ia.openai.modelo", openaiModelo);
        openaiChave = valor(p, "ia.openai.chave", openaiChave);
        openai2Url = valor(p, "ia.openai2.url", openai2Url);
        openai2Modelo = valor(p, "ia.openai2.modelo", openai2Modelo);
        openai2Chave = valor(p, "ia.openai2.chave", openai2Chave);

        groqChave = valor(p, "ia.groq.chave", groqChave);
        groqModelo = valor(p, "ia.groq.modelo", groqModelo);

        openrouterChave = valor(p, "ia.openrouter.chave", openrouterChave);
        openrouterModelo = valor(p, "ia.openrouter.modelo", openrouterModelo);

        cerebrasChave = valor(p, "ia.cerebras.chave", cerebrasChave);
        cerebrasModelo = valor(p, "ia.cerebras.modelo", cerebrasModelo);

        mistralChave = valor(p, "ia.mistral.chave", mistralChave);
        mistralModelo = valor(p, "ia.mistral.modelo", mistralModelo);

        try {
            openaiTimeoutSegundos = Integer.parseInt(valor(p, "ia.openai.timeout.segundos", String.valueOf(openaiTimeoutSegundos)));
        } catch (NumberFormatException ignored) {
        }
        String env = System.getenv("IA_PROVEDOR");
        if (env != null && !env.isBlank()) {
            provedor = env.trim();
        }
    }

    private String valor(Properties p, String chave, String padrao) {
        String v = p.getProperty(chave);
        return v != null && !v.isBlank() ? v.trim() : padrao;
    }

    public String getProvedor() {
        return provedor;
    }

    public void setProvedor(String provedor) {
        this.provedor = provedor;
    }

    public String getOpenaiUrl() {
        return openaiUrl.endsWith("/") ? openaiUrl.substring(0, openaiUrl.length() - 1) : openaiUrl;
    }

    public String getOpenaiModelo() {
        return openaiModelo;
    }

    public String getOpenaiChave() {
        return openaiChave;
    }

    public int getOpenaiTimeoutSegundos() {
        return openaiTimeoutSegundos;
    }

    public String getProvedoresFailover() {
        return provedoresFailover;
    }

    public void setProvedoresFailover(String provedoresFailover) {
        this.provedoresFailover = provedoresFailover;
    }

    public String getOpenai2Url() {
        return openai2Url.endsWith("/") ? openai2Url.substring(0, openai2Url.length() - 1) : openai2Url;
    }

    public String getOpenai2Modelo() {
        return openai2Modelo;
    }

    public String getOpenai2Chave() {
        return openai2Chave;
    }

    public String getGroqChave() {
        return groqChave;
    }

    public String getGroqModelo() {
        return groqModelo;
    }

    public String getOpenrouterChave() {
        return openrouterChave;
    }

    public String getOpenrouterModelo() {
        return openrouterModelo;
    }

    public String getCerebrasChave() {
        return cerebrasChave;
    }

    public String getCerebrasModelo() {
        return cerebrasModelo;
    }

    public String getMistralChave() {
        return mistralChave;
    }

    public String getMistralModelo() {
        return mistralModelo;
    }
}
