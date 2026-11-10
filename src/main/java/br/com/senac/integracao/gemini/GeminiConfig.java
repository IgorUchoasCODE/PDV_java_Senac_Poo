package br.com.senac.integracao.gemini;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Gerenciador central de credenciais e parâmetros de configuração da IA Gemini.
 * Suporta autenticação em cascata dupla:
 * 1. Variável de Ambiente: System.getenv("GEMINI_API_KEY")
 * 2. Propriedade JVM: System.getProperty("GEMINI_API_KEY")
 * 3. Arquivo local seguro: config/gemini.properties
 * 4. Fallback no classpath: gemini.properties
 */
public class GeminiConfig {

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private String apiKey;
    private String model = DEFAULT_MODEL;
    private double temperature = 0.2;
    private int maxOutputTokens = 4096;
    private int maxConcurrentReads = 20;
    private int timeoutSeconds = 60;

    private String personalidade = "Você é um assistente operacional prestativo, ágil e educado, especializado no Sistema de Vendas Sazonalisei.";
    private String instrucoesCustomizadas = "";

    private static GeminiConfig instancia;

    public static synchronized GeminiConfig getInstancia() {
        if (instancia == null) {
            instancia = new GeminiConfig();
            instancia.carregarConfiguracoes();
        }
        return instancia;
    }

    public GeminiConfig() {
    }

    public synchronized void carregarConfiguracoes() {
        Properties props = new Properties();

        // 1. Tenta carregar do arquivo config/gemini.properties no disco
        File configFile = new File("config/gemini.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (Exception ignored) {
            }
        }

        // 2. Se não encontrou, tenta buscar em src/main/resources/gemini.properties
        if (props.isEmpty()) {
            try (InputStream is = getClass().getResourceAsStream("/gemini.properties")) {
                if (is != null) {
                    props.load(is);
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Aplica valores do arquivo se disponíveis
        if (props.containsKey("gemini.api.key") && !props.getProperty("gemini.api.key").isBlank()) {
            this.apiKey = props.getProperty("gemini.api.key").trim();
        }
        if (props.containsKey("gemini.model") && !props.getProperty("gemini.model").isBlank()) {
            this.model = props.getProperty("gemini.model").trim();
        }
        if (props.containsKey("gemini.temperature")) {
            try {
                this.temperature = Double.parseDouble(props.getProperty("gemini.temperature").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (props.containsKey("gemini.max.output.tokens")) {
            try {
                this.maxOutputTokens = Integer.parseInt(props.getProperty("gemini.max.output.tokens").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (props.containsKey("gemini.max.concurrent.reads")) {
            try {
                this.maxConcurrentReads = Integer.parseInt(props.getProperty("gemini.max.concurrent.reads").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (props.containsKey("gemini.timeout.seconds")) {
            try {
                this.timeoutSeconds = Integer.parseInt(props.getProperty("gemini.timeout.seconds").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (props.containsKey("gemini.personalidade") && !props.getProperty("gemini.personalidade").isBlank()) {
            this.personalidade = props.getProperty("gemini.personalidade").trim();
        }
        if (props.containsKey("gemini.instrucoes") && !props.getProperty("gemini.instrucoes").isBlank()) {
            this.instrucoesCustomizadas = props.getProperty("gemini.instrucoes").trim();
        }

        // Carrega arquivo dedicado config/personalidade.txt se presente
        File filePersona = new File("config/personalidade.txt");
        if (filePersona.exists()) {
            try {
                String txt = java.nio.file.Files.readString(filePersona.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!txt.isBlank()) {
                    this.personalidade = txt;
                }
            } catch (Exception ignored) {}
        }

        // 4. Sobrescreve com Propriedade JVM (-DGEMINI_API_KEY=...) se presente
        String jvmKey = System.getProperty("GEMINI_API_KEY");
        if (jvmKey != null && !jvmKey.isBlank()) {
            this.apiKey = jvmKey.trim();
        }

        // 5. Sobrescreve com Variável de Ambiente (GEMINI_API_KEY) se presente (prioridade máxima)
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            this.apiKey = envKey.trim();
        }
    }

    public boolean isConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getEndpointUrl() {
        return getEndpointUrl(this.model);
    }

    public String getEndpointUrl(String modelName) {
        if (!isConfigurado()) {
            throw new IllegalStateException("Chave de API do Gemini não configurada. Defina a variável de ambiente GEMINI_API_KEY ou configure em config/gemini.properties.");
        }
        String m = (modelName != null && !modelName.isBlank()) ? modelName : model;
        return GEMINI_API_BASE_URL + m + ":generateContent?key=" + apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model;
        }
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxConcurrentReads() {
        return maxConcurrentReads;
    }

    public void setMaxConcurrentReads(int maxConcurrentReads) {
        this.maxConcurrentReads = maxConcurrentReads;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getPersonalidade() {
        return personalidade;
    }

    public void setPersonalidade(String personalidade) {
        if (personalidade != null && !personalidade.isBlank()) {
            this.personalidade = personalidade.trim();
        }
    }

    public String getInstrucoesCustomizadas() {
        return instrucoesCustomizadas;
    }

    public void setInstrucoesCustomizadas(String instrucoesCustomizadas) {
        this.instrucoesCustomizadas = instrucoesCustomizadas != null ? instrucoesCustomizadas.trim() : "";
    }

    public synchronized void salvarPersonalidade(String novaPersonalidade, String novasInstrucoes) {
        if (novaPersonalidade != null && !novaPersonalidade.isBlank()) {
            this.personalidade = novaPersonalidade.trim();
        }
        if (novasInstrucoes != null) {
            this.instrucoesCustomizadas = novasInstrucoes.trim();
        }
        try {
            File dir = new File("config");
            if (!dir.exists()) dir.mkdirs();
            File f = new File("config/personalidade.txt");
            java.nio.file.Files.writeString(f.toPath(), this.personalidade, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Aviso: Falha ao salvar config/personalidade.txt: " + e.getMessage());
        }
    }
}
