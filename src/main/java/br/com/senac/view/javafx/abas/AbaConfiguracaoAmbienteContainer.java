package br.com.senac.view.javafx.abas;

import br.com.senac.view.javafx.componentes.PainelLeitorCsvContainer;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Properties;

/**
 * Aba de Controle de Chaves, Configuração Pré-Definida de APIs de IA, Ambiente (Banco)
 * e Leitor CSV com mapeamento de colunas.
 */
public class AbaConfiguracaoAmbienteContainer extends ScrollPane {

    private static final String ARQ_IA = "config/ia.properties";
    private static final String ARQ_GEMINI = "config/gemini.properties";

    private final SistemaGateway gateway;
    private final ComboBox<String> cmbProvedor = new ComboBox<>();
    private final TextField txtFailover = new TextField();
    private final TextField txtTimeoutSegundos = new TextField();
    private final TextField txtOpenAiUrl = new TextField();
    private final TextField txtOpenAiModelo = new TextField();
    private final TextField txtGroqModelo = new TextField();
    private final TextField txtOpenRouterModelo = new TextField();
    private final TextField txtCerebrasModelo = new TextField();
    private final TextField txtMistralModelo = new TextField();

    private final PasswordField txtGeminiChave = new PasswordField();
    private final PasswordField txtGroqChave = new PasswordField();
    private final PasswordField txtOpenRouterChave = new PasswordField();
    private final PasswordField txtCerebrasChave = new PasswordField();
    private final PasswordField txtMistralChave = new PasswordField();

    private final TextField txtGeminiVisivel = new TextField();
    private final TextField txtGroqVisivel = new TextField();
    private final TextField txtOpenRouterVisivel = new TextField();
    private final TextField txtCerebrasVisivel = new TextField();
    private final TextField txtMistralVisivel = new TextField();

    private final CheckBox chkMostrarChaves = new CheckBox("Mostrar chaves");
    private final Label lblStatusConfig = new Label();

    public AbaConfiguracaoAmbienteContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox conteudo = new VBox(16);
        conteudo.setPadding(new Insets(18));

        VBox cardConfig = criarCardConfiguracaoChaves();
        VBox cardAmbiente = criarCardAmbienteBanco();
        PainelLeitorCsvContainer painelCsv = new PainelLeitorCsvContainer(gateway);

        conteudo.getChildren().addAll(cardConfig, cardAmbiente, painelCsv);
        setContent(conteudo);
        carregarConfiguracoes();
    }

    private VBox criarCardConfiguracaoChaves() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card-container");

        Label lblTitulo = new Label("CONFIGURAÇÃO PADRÃO DE APIs DE IA & CONTROLE DE CHAVES");
        lblTitulo.getStyleClass().add("section-title");

        cmbProvedor.getItems().addAll("gemini", "groq", "openrouter", "cerebras", "mistral", "openai");

        vincularCampoSenha(txtGeminiChave, txtGeminiVisivel);
        vincularCampoSenha(txtGroqChave, txtGroqVisivel);
        vincularCampoSenha(txtOpenRouterChave, txtOpenRouterVisivel);
        vincularCampoSenha(txtCerebrasChave, txtCerebrasVisivel);
        vincularCampoSenha(txtMistralChave, txtMistralVisivel);

        chkMostrarChaves.setOnAction(e -> {
            boolean mostrar = chkMostrarChaves.isSelected();
            alternarVisibilidade(txtGeminiChave, txtGeminiVisivel, mostrar);
            alternarVisibilidade(txtGroqChave, txtGroqVisivel, mostrar);
            alternarVisibilidade(txtOpenRouterChave, txtOpenRouterVisivel, mostrar);
            alternarVisibilidade(txtCerebrasChave, txtCerebrasVisivel, mostrar);
            alternarVisibilidade(txtMistralChave, txtMistralVisivel, mostrar);
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        grid.addRow(0, new Label("Provedor Principal:"), cmbProvedor, new Label("Ordem Failover:"), txtFailover);
        grid.addRow(1, new Label("Chave Gemini:"), criarStackCampo(txtGeminiChave, txtGeminiVisivel), new Label("Timeout IA (s):"), txtTimeoutSegundos);
        grid.addRow(2, new Label("Chave Groq:"), criarStackCampo(txtGroqChave, txtGroqVisivel), new Label("Modelo Groq:"), txtGroqModelo);
        grid.addRow(3, new Label("Chave OpenRouter:"), criarStackCampo(txtOpenRouterChave, txtOpenRouterVisivel), new Label("Modelo OpenRouter:"), txtOpenRouterModelo);
        grid.addRow(4, new Label("Chave Cerebras:"), criarStackCampo(txtCerebrasChave, txtCerebrasVisivel), new Label("Modelo Cerebras:"), txtCerebrasModelo);
        grid.addRow(5, new Label("Chave Mistral:"), criarStackCampo(txtMistralChave, txtMistralVisivel), new Label("Modelo Mistral:"), txtMistralModelo);
        grid.addRow(6, new Label("URL Local (Ollama/OpenAI):"), txtOpenAiUrl, new Label("Modelo Local:"), txtOpenAiModelo);
        grid.addRow(7, chkMostrarChaves, new Label(""), new Label(""), new Label(""));

        Button btnPredefinicao = new Button("Aplicar Predefinição Padrão");
        btnPredefinicao.getStyleClass().add("btn-secondary");
        btnPredefinicao.setOnAction(e -> aplicarPredefinicaoPadrao());

        Button btnSalvar = new Button("Salvar Chaves & Recarregar IA");
        btnSalvar.getStyleClass().add("btn-primary");
        btnSalvar.setOnAction(e -> salvarConfiguracoes());

        Button btnRecarregar = new Button("Recarregar do Disco");
        btnRecarregar.getStyleClass().add("btn-secondary");
        btnRecarregar.setOnAction(e -> carregarConfiguracoes());

        lblStatusConfig.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");

        HBox botoes = new HBox(10, btnPredefinicao, btnSalvar, btnRecarregar, lblStatusConfig);
        botoes.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(lblTitulo, grid, botoes);
        return card;
    }

    private VBox criarCardAmbienteBanco() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-container");

        Label lblTitulo = new Label("STATUS DO AMBIENTE & PERSISTÊNCIA");
        lblTitulo.getStyleClass().add("section-title");

        String os = System.getProperty("os.name", "Desconhecido");
        String javaVer = System.getProperty("java.version", "21");
        Label lblInfo = new Label("Runtime Java: " + javaVer + " | SO: " + os
                + " | Arquivos de Config: config/ia.properties e config/gemini.properties"
                + " | Fallback local: Ollama (http://localhost:11434/v1)");
        lblInfo.setWrapText(true);
        lblInfo.setStyle("-fx-text-fill: #94A3B8;");

        card.getChildren().addAll(lblTitulo, lblInfo);
        return card;
    }

    private void aplicarPredefinicaoPadrao() {
        cmbProvedor.setValue("gemini");
        txtFailover.setText("gemini,groq,openrouter,cerebras,mistral,openai");
        txtTimeoutSegundos.setText("30");
        txtGroqModelo.setText("llama-3.3-70b-versatile");
        txtOpenRouterModelo.setText("meta-llama/llama-3.3-70b-instruct:free");
        txtCerebrasModelo.setText("llama3.1-8b");
        txtMistralModelo.setText("mistral-small-latest");
        txtOpenAiUrl.setText("http://localhost:11434/v1");
        txtOpenAiModelo.setText("llama3.1");
        lblStatusConfig.setText("Predefinições padrão preenchidas! Clique em 'Salvar Chaves & Recarregar IA' para persistir.");
    }

    private void vincularCampoSenha(PasswordField pwd, TextField vis) {
        vis.setManaged(false);
        vis.setVisible(false);
        vis.textProperty().bindBidirectional(pwd.textProperty());
    }

    private void alternarVisibilidade(PasswordField pwd, TextField vis, boolean mostrar) {
        vis.setManaged(mostrar);
        vis.setVisible(mostrar);
        pwd.setManaged(!mostrar);
        pwd.setVisible(!mostrar);
    }

    private HBox criarStackCampo(PasswordField pwd, TextField vis) {
        pwd.setPrefWidth(220);
        vis.setPrefWidth(220);
        return new HBox(pwd, vis);
    }

    private void carregarConfiguracoes() {
        Properties ia = gateway.carregarPropriedades(ARQ_IA);
        Properties gemini = gateway.carregarPropriedades(ARQ_GEMINI);

        cmbProvedor.setValue(ia.getProperty("ia.provedor", "gemini"));
        txtFailover.setText(ia.getProperty("ia.provedores.failover", "gemini,groq,openrouter,cerebras,mistral,openai"));
        txtTimeoutSegundos.setText(ia.getProperty("ia.timeout.segundos", "30"));

        txtGroqModelo.setText(ia.getProperty("ia.groq.modelo", "llama-3.3-70b-versatile"));
        txtOpenRouterModelo.setText(ia.getProperty("ia.openrouter.modelo", "meta-llama/llama-3.3-70b-instruct:free"));
        txtCerebrasModelo.setText(ia.getProperty("ia.cerebras.modelo", "llama3.1-8b"));
        txtMistralModelo.setText(ia.getProperty("ia.mistral.modelo", "mistral-small-latest"));
        txtOpenAiUrl.setText(ia.getProperty("ia.openai.url", "http://localhost:11434/v1"));
        txtOpenAiModelo.setText(ia.getProperty("ia.openai.modelo", "llama3.1"));

        txtGroqChave.setText(ia.getProperty("ia.groq.chave", ""));
        txtOpenRouterChave.setText(ia.getProperty("ia.openrouter.chave", ""));
        txtCerebrasChave.setText(ia.getProperty("ia.cerebras.chave", ""));
        txtMistralChave.setText(ia.getProperty("ia.mistral.chave", ""));
        txtGeminiChave.setText(gemini.getProperty("gemini.api.key", ""));
        lblStatusConfig.setText("Configurações carregadas de config/ (com padrões prontos para uso).");
    }

    private void salvarConfiguracoes() {
        try {
            Properties ia = gateway.carregarPropriedades(ARQ_IA);
            ia.setProperty("ia.provedor", cmbProvedor.getValue() != null ? cmbProvedor.getValue() : "gemini");
            ia.setProperty("ia.provedores.failover", txtFailover.getText().trim());
            ia.setProperty("ia.timeout.segundos", txtTimeoutSegundos.getText().trim());
            ia.setProperty("ia.groq.modelo", txtGroqModelo.getText().trim());
            ia.setProperty("ia.openrouter.modelo", txtOpenRouterModelo.getText().trim());
            ia.setProperty("ia.cerebras.modelo", txtCerebrasModelo.getText().trim());
            ia.setProperty("ia.mistral.modelo", txtMistralModelo.getText().trim());
            ia.setProperty("ia.openai.url", txtOpenAiUrl.getText().trim());
            ia.setProperty("ia.openai.modelo", txtOpenAiModelo.getText().trim());
            ia.setProperty("ia.groq.chave", txtGroqChave.getText().trim());
            ia.setProperty("ia.openrouter.chave", txtOpenRouterChave.getText().trim());
            ia.setProperty("ia.cerebras.chave", txtCerebrasChave.getText().trim());
            ia.setProperty("ia.mistral.chave", txtMistralChave.getText().trim());
            gateway.salvarPropriedades(ARQ_IA, ia, "Configuracao de provedores IA - Sazonalisei");

            Properties gemini = gateway.carregarPropriedades(ARQ_GEMINI);
            gemini.setProperty("gemini.api.key", txtGeminiChave.getText().trim());
            gateway.salvarPropriedades(ARQ_GEMINI, gemini, "Configuracao Gemini API - Sazonalisei");

            gateway.recarregarConfiguracoesIA();
            lblStatusConfig.setText("Salvo e recarregado com sucesso!");
        } catch (Exception ex) {
            lblStatusConfig.setText("Erro ao salvar: " + ex.getMessage());
        }
    }
}
