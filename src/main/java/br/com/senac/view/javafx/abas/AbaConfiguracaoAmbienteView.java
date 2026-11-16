package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.csv.LeitorCsv;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.ia.ConfigIA;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico.EstadoClimaticoAmazonico;
import br.com.senac.view.javafx.util.ProcessadorExpressaoColunaCsv;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aba 7: Configurações & CSV.
 * Organizada em 3 sub-abas corporativas enxutas:
 * (1) Importação de Planilhas CSV;
 * (2) Provedores de IA, Voz, Tema & Escala de Fonte (12px–15px);
 * (3) Inicialização Padrão & Reset Restrito do Banco.
 */
public class AbaConfiguracaoAmbienteView extends VBox {

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabCsv = new Tab("📥 Importação CSV");
    private final Tab tabIaAparencia = new Tab("🤖 IA, Voz & Aparência");
    private final Tab tabManutencao = new Tab("🗄️ Manutenção da Base");

    private final Label lblStatusConfig = FormatadorSeguroUI.criarLabel("", 12, true, "#047857");
    private final VBox boxStatus = new VBox(lblStatusConfig);

    public AbaConfiguracaoAmbienteView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        Label titulo = FormatadorSeguroUI.criarTituloSecao("⚙️", "Configurações do Sistema, IA & Importação CSV", "#0F172A");
        Label subtitulo = FormatadorSeguroUI.criarSubtitulo(
                "Importe cadastros/notas em lote, configure provedores de IA, aparência corporativa e manutenção.", "#64748B");

        boxStatus.setPadding(new Insets(8, 12, 8, 12));
        boxStatus.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#ECFDF5", "#6EE7B7"));
        boxStatus.setVisible(false);
        boxStatus.setManaged(false);

        tabCsv.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaCsv()));
        tabIaAparencia.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaIaAparencia()));
        tabManutencao.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaManutencao()));

        tabPane.getTabs().addAll(tabCsv, tabIaAparencia, tabManutencao);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, titulo, subtitulo), boxStatus, tabPane);
    }

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaCsv() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label tit = FormatadorSeguroUI.criarTituloSecao("📥", "Importação Inteligente CSV — Mapeador Visual & Comandos de Coluna", "#0F172A");
        ComboBox<TipoImportacaoCsv> cbTipoCsv = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbTipoCsv.getItems().addAll(TipoImportacaoCsv.values());
        cbTipoCsv.getSelectionModel().select(TipoImportacaoCsv.PRODUTOS_FISICOS);
        cbTipoCsv.setPrefWidth(250);

        Label lblColunas = FormatadorSeguroUI.criarLabel(cbTipoCsv.getValue().getDescricaoColunas(), 11, false, "#334155");
        lblColunas.setWrapText(true);
        cbTipoCsv.setOnAction(e -> {
            if (cbTipoCsv.getValue() != null) lblColunas.setText(cbTipoCsv.getValue().getDescricaoColunas());
        });

        final TabelaCsv[] tabelaBrutaRef = new TabelaCsv[1];
        final File[] arquivoAtualRef = new File[1];
        final Map<String, ComboBox<String>> combosMapeamento = new LinkedHashMap<>();

        FlowPane boxMapeadorVisual = new FlowPane(10, 8);
        boxMapeadorVisual.setPadding(new Insets(10));
        boxMapeadorVisual.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
        boxMapeadorVisual.getChildren().add(FormatadorSeguroUI.criarLabel(
                "1) Clique em 'Selecionar Planilha CSV' para mapear visualmente cada coluna (Produto, Fornecedor, Valor, Estoque...).",
                12, false, "#64748B"));

        TextArea txtComandosColuna = FormatadorSeguroUI.criarAreaTextoCartoon(
                "Exemplos de comandos personalizados para percorrer toda a coluna:\n"
                        + "C= @col=\"Produto\" -> id>, nm>\n"
                        + "C= @col=\"Preco\" -> val> * 1.10\n"
                        + "C= @col=\"Forn\" -> forn>\n"
                        + "C= @col=\"Estoque\" -> qtd>",
                "#93C5FD", "#0F172A");
        txtComandosColuna.setPrefRowCount(3);

        TableView<Map<String, String>> tabelaPreviewCsv = new TableView<>();
        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaPreviewCsv, 185);
        tabelaPreviewCsv.setPlaceholder(FormatadorSeguroUI.criarLabel("A pré-visualização da planilha mapeada aparecerá aqui.", 12, false, "#64748B"));

        Runnable atualizarPreview = () -> {
            if (tabelaBrutaRef[0] == null) return;
            Map<String, String> mapVisual = new LinkedHashMap<>();
            combosMapeamento.forEach((h, cb) -> mapVisual.put(h, cb.getValue() != null ? cb.getValue() : h));
            var proc = ProcessadorExpressaoColunaCsv.aplicar(tabelaBrutaRef[0], mapVisual, txtComandosColuna.getText());

            tabelaPreviewCsv.getColumns().clear();
            for (String colNome : proc.tabela().cabecalhos()) {
                TableColumn<Map<String, String>, String> tc = new TableColumn<>(colNome);
                tc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOrDefault(colNome, "")));
                tc.setPrefWidth(130);
                tabelaPreviewCsv.getColumns().add(tc);
            }
            var amostra = proc.tabela().linhas().subList(0, Math.min(25, proc.tabela().linhas().size()));
            tabelaPreviewCsv.setItems(FXCollections.observableArrayList(amostra));
            if (!proc.logsAplicacao().isEmpty()) {
                mostrarStatus(true, String.join(" | ", proc.logsAplicacao()));
            }
        };

        Button btnCarregarPreview = FormatadorSeguroUI.criarBotaoCartoon("📂 1. Carregar & Mapear CSV", "#0F172A", "#0F172A", "#FFFFFF");
        btnCarregarPreview.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Selecionar Planilha CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv", "*.txt"));
            File arq = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
            if (arq != null) {
                try {
                    TabelaCsv lida = new LeitorCsv().lerArquivo(arq.toPath());
                    arquivoAtualRef[0] = arq;
                    tabelaBrutaRef[0] = lida;
                    combosMapeamento.clear();
                    boxMapeadorVisual.getChildren().clear();
                    for (String h : lida.cabecalhos()) {
                        ComboBox<String> cbCampo = FormatadorSeguroUI.criarComboBoxCartoon("#93C5FD", "#0F172A");
                        cbCampo.setEditable(true);
                        cbCampo.getItems().addAll(ProcessadorExpressaoColunaCsv.camposDestinoDisponiveis());
                        cbCampo.setValue(ProcessadorExpressaoColunaCsv.sugerirCampoDestino(h));
                        cbCampo.setPrefWidth(155);
                        cbCampo.setOnAction(ev -> atualizarPreview.run());
                        combosMapeamento.put(h, cbCampo);

                        VBox chipCol = new VBox(3,
                                FormatadorSeguroUI.criarLabel("Coluna CSV: \"" + h + "\"", 11, true, "#1E40AF"),
                                cbCampo);
                        chipCol.setPadding(new Insets(6, 8, 6, 8));
                        chipCol.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", "#CBD5E1"));
                        boxMapeadorVisual.getChildren().add(chipCol);
                    }
                    atualizarPreview.run();
                    mostrarStatus(true, String.format("CSV '%s' carregado (%d linhas, delimitador '%s'). Ajuste o mapeamento abaixo ou aplique comandos C= @col=.",
                            arq.getName(), lida.linhas().size(), lida.delimitadorDetectado()));
                } catch (Exception ex) {
                    mostrarStatus(false, FormatadorSeguroUI.traduzirErroAmigavel(ex.getMessage()));
                }
            }
        });

        Button btnAplicarPreview = FormatadorSeguroUI.criarBotaoCartoon("⚡ 2. Testar Comandos & Preview", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAplicarPreview.setOnAction(e -> atualizarPreview.run());

        Button btnImportarMapeado = FormatadorSeguroUI.criarBotaoCartoon("✅ 3. Importar Planilha Mapeada", "#047857", "#065F46", "#FFFFFF");
        btnImportarMapeado.setOnAction(e -> {
            if (tabelaBrutaRef[0] == null || cbTipoCsv.getValue() == null) {
                mostrarStatus(false, "Carregue um arquivo CSV primeiro (Passo 1).");
                return;
            }
            Map<String, String> mapVisual = new LinkedHashMap<>();
            combosMapeamento.forEach((h, cb) -> mapVisual.put(h, cb.getValue() != null ? cb.getValue() : h));
            var proc = ProcessadorExpressaoColunaCsv.aplicar(tabelaBrutaRef[0], mapVisual, txtComandosColuna.getText());
            TipoImportacaoCsv tipo = cbTipoCsv.getValue();
            String origemNome = arquivoAtualRef[0] != null ? arquivoAtualRef[0].getName() : "csv-mapeado";

            mostrarStatus(true, "Importando " + proc.tabela().linhas().size() + " registro(s) mapeado(s)...");
            Thread.ofVirtual().start(() -> {
                try {
                    ResultadoImportacaoCsv res = ponte.importarTabelaCsvMapeada(origemNome, proc.tabela(), tipo);
                    Platform.runLater(() -> mostrarStatus(res.semErros(), res.formatarResumo()));
                } catch (Exception ex) {
                    Platform.runLater(() -> mostrarStatus(false, FormatadorSeguroUI.traduzirErroAmigavel(ex.getMessage())));
                }
            });
        });

        Button btnAjudaCsv = FormatadorSeguroUI.criarBotaoIA("🤖 Sugerir Comandos @col c/ IA", () -> {
            String colsDetectadas = tabelaBrutaRef[0] != null ? String.join(", ", tabelaBrutaRef[0].cabecalhos()) : "nenhum CSV carregado";
            conectorIA.perguntarAoCopiloto(this, "Mapeador de Colunas CSV",
                    "Tipo: " + (cbTipoCsv.getValue() != null ? cbTipoCsv.getValue().name() : "PRODUTOS_FISICOS") + " • Colunas CSV: " + colsDetectadas,
                    "As colunas detectadas no meu CSV são [" + colsDetectadas + "]. Para importar no formato '"
                            + (cbTipoCsv.getValue() != null ? cbTipoCsv.getValue().name() : "PRODUTOS_FISICOS")
                            + "' (" + lblColunas.getText() + "), gere comandos na sintaxe C= @col=\"NomeColuna\" -> id>, nm>, val>, forn>, qtd> prontos para colar.");
        });

        VBox boxCols = new VBox(lblColunas);
        boxCols.setPadding(new Insets(8, 10, 8, 10));
        boxCols.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));

        VBox boxSintaxe = new VBox(6,
                FormatadorSeguroUI.criarLabel("⌨️ Comandos Personalizados p/ Varrer Toda a Coluna (Sintaxe: C= @col=\"Nome\" -> id>, nm>, val>, forn>, qtd>):", 12, true, "#0F172A"),
                txtComandosColuna,
                new FlowPane(8, 6, btnAplicarPreview, btnImportarMapeado));
        boxSintaxe.setPadding(new Insets(10));
        boxSintaxe.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));

        card.getChildren().addAll(
                tit,
                new FlowPane(8, 8, FormatadorSeguroUI.criarLabel("Tipo de Importação:", 12, true, "#334155"),
                        cbTipoCsv, btnCarregarPreview, btnAjudaCsv),
                boxCols,
                FormatadorSeguroUI.criarLabel("🔗 Mapeamento Visual de Colunas (Origem CSV ➔ Campo ERP):", 12, true, "#0F172A"),
                boxMapeadorVisual,
                boxSintaxe,
                FormatadorSeguroUI.criarLabel("👁️ Pré-visualização (primeiras 25 linhas após transformação):", 12, true, "#0F172A"),
                tabelaPreviewCsv);

        VBox wrapper = new VBox(card);
        wrapper.setPadding(new Insets(10, 2, 10, 2));
        return wrapper;
    }

    private VBox construirSubAbaIaAparencia() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        // 1. Provedores IA, Chaves de API & Áudio
        VBox cardIa = new VBox(10);
        cardIa.setPadding(new Insets(14));
        cardIa.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        ConfigIA cfg = ConfigIA.getInstancia();
        GeminiConfig gemCfg = GeminiConfig.getInstancia();

        ComboBox<String> cbProvedor = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbProvedor.getItems().addAll("gemini", "groq", "openrouter", "cerebras", "mistral", "openai", "manual");
        cbProvedor.setValue(cfg.getProvedor());
        cbProvedor.setPrefWidth(175);

        TextField txtFailover = FormatadorSeguroUI.criarCampoTextoCartoon(
                "Ordem failover (ex.: gemini,groq,openrouter)", "#CBD5E1", "#0F172A");
        txtFailover.setText(cfg.getProvedoresFailover());
        HBox.setHgrow(txtFailover, Priority.ALWAYS);

        PasswordField txtGeminiKey = new PasswordField();
        txtGeminiKey.setPromptText("Chave API Google Gemini (AIza...)");
        txtGeminiKey.setText(gemCfg.getApiKey() != null ? gemCfg.getApiKey() : "");
        txtGeminiKey.setStyle(FormatadorSeguroUI.criarCampoTextoCartoon("", "#CBD5E1", "#0F172A").getStyle());

        TextField txtGeminiModelo = FormatadorSeguroUI.criarCampoTextoCartoon("gemini-3.8-flash", "#CBD5E1", "#0F172A");
        txtGeminiModelo.setText(gemCfg.getModel() != null ? gemCfg.getModel() : "gemini-3.8-flash");

        PasswordField txtGroqKey = new PasswordField();
        txtGroqKey.setPromptText("Chave API Groq (gsk_...)");
        txtGroqKey.setText(cfg.getGroqChave() != null ? cfg.getGroqChave() : "");
        txtGroqKey.setStyle(txtGeminiKey.getStyle());

        PasswordField txtOpenRouterKey = new PasswordField();
        txtOpenRouterKey.setPromptText("Chave API OpenRouter (sk-or-...)");
        txtOpenRouterKey.setText(cfg.getOpenrouterChave() != null ? cfg.getOpenrouterChave() : "");
        txtOpenRouterKey.setStyle(txtGeminiKey.getStyle());

        TextField txtOpenAiUrl = FormatadorSeguroUI.criarCampoTextoCartoon("URL OpenAI/Ollama (ex.: http://localhost:11434/v1)", "#CBD5E1", "#0F172A");
        txtOpenAiUrl.setText(cfg.getOpenaiUrl() != null ? cfg.getOpenaiUrl() : "");

        PasswordField txtOpenAiKey = new PasswordField();
        txtOpenAiKey.setPromptText("Chave API OpenAI / Compatível");
        txtOpenAiKey.setText(cfg.getOpenaiChave() != null ? cfg.getOpenaiChave() : "");
        txtOpenAiKey.setStyle(txtGeminiKey.getStyle());

        TextField txtOpenAiModel = FormatadorSeguroUI.criarCampoTextoCartoon("Modelo OpenAI/Local", "#CBD5E1", "#0F172A");
        txtOpenAiModel.setText(cfg.getOpenaiModelo() != null ? cfg.getOpenaiModelo() : "");

        GridPane gridKeys = new GridPane();
        gridKeys.setHgap(8);
        gridKeys.setVgap(6);
        ColumnConstraints k1 = new ColumnConstraints();
        k1.setPercentWidth(33.3);
        ColumnConstraints k2 = new ColumnConstraints();
        k2.setPercentWidth(33.3);
        ColumnConstraints k3 = new ColumnConstraints();
        k3.setPercentWidth(33.3);
        gridKeys.getColumnConstraints().addAll(k1, k2, k3);

        gridKeys.add(FormatadorSeguroUI.criarLabel("Chave Gemini API:", 11, true, "#475569"), 0, 0);
        gridKeys.add(FormatadorSeguroUI.criarLabel("Modelo Gemini:", 11, true, "#475569"), 1, 0);
        gridKeys.add(FormatadorSeguroUI.criarLabel("Chave Groq API:", 11, true, "#475569"), 2, 0);
        gridKeys.add(txtGeminiKey, 0, 1);
        gridKeys.add(txtGeminiModelo, 1, 1);
        gridKeys.add(txtGroqKey, 2, 1);

        gridKeys.add(FormatadorSeguroUI.criarLabel("Chave OpenRouter:", 11, true, "#475569"), 0, 2);
        gridKeys.add(FormatadorSeguroUI.criarLabel("URL OpenAI/Ollama:", 11, true, "#475569"), 1, 2);
        gridKeys.add(FormatadorSeguroUI.criarLabel("Chave OpenAI / Modelo:", 11, true, "#475569"), 2, 2);
        gridKeys.add(txtOpenRouterKey, 0, 3);
        gridKeys.add(txtOpenAiUrl, 1, 3);
        gridKeys.add(new HBox(6, txtOpenAiKey, txtOpenAiModel), 2, 3);

        TextArea txtPersonalidade = FormatadorSeguroUI.criarAreaTextoCartoon(
                "Personalidade do assistente...", "#CBD5E1", "#0F172A");
        txtPersonalidade.setPrefRowCount(3);
        txtPersonalidade.setText(lerPersonalidadeAtual());

        Button btnSalvarIA = FormatadorSeguroUI.criarBotaoCartoon("💾 Salvar Chaves API & Preferências IA", "#047857", "#065F46", "#FFFFFF");
        btnSalvarIA.setOnAction(e -> {
            ponte.salvarConfigIA(cbProvedor.getValue(), txtFailover.getText(), null);
            ponte.salvarChavesApiIA(
                    txtGeminiKey.getText(), txtGeminiModelo.getText(),
                    txtGroqKey.getText(), txtOpenRouterKey.getText(),
                    txtOpenAiUrl.getText(), txtOpenAiKey.getText(), txtOpenAiModel.getText());
            salvarPersonalidade(txtPersonalidade.getText());
            mostrarStatus(true, "Chaves de API, provedores de failover e personalidade salvos!");
        });

        Button btnTestarMic = FormatadorSeguroUI.criarBotaoCartoon("🎤 Testar Microfone", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnTestarMic.setOnAction(e -> {
            boolean ok = ponte.getCapturadorVoz().isMicrofoneDisponivel();
            mostrarStatus(ok, ok
                    ? "Microfone detectado e pronto para uso no PDV e na Central IA!"
                    : "Nenhum dispositivo de áudio disponível para captura (o modo Ditado Win+H funciona normalmente).");
        });

        HBox linProv = new HBox(8,
                FormatadorSeguroUI.criarLabel("Provedor Principal:", 12, true, "#334155"), cbProvedor,
                FormatadorSeguroUI.criarLabel("Failover:", 12, true, "#334155"), txtFailover);
        linProv.setAlignment(Pos.CENTER_LEFT);

        cardIa.getChildren().addAll(
                FormatadorSeguroUI.criarTituloSecao("🔐", "Chaves de API de IA, Failover & Áudio", "#0F172A"),
                linProv,
                gridKeys,
                FormatadorSeguroUI.criarLabel("Personalidade / Tom do Copiloto (config/personalidade.txt):", 12, true, "#334155"),
                txtPersonalidade,
                new HBox(8, btnSalvarIA, btnTestarMic));

        // 2. Tema Dinâmico, Modo Escuro, Cor de Background Customizável & Tipografia (12px–15px)
        VBox cardVisual = new VBox(10);
        cardVisual.setPadding(new Insets(14));
        cardVisual.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        ToggleButton tgModoEscuro = new ToggleButton("🌙 Ativar Tema Escuro");
        tgModoEscuro.setSelected(ponte.getGerenciadorTema().isModoEscuro());
        Runnable syncDark = () -> FormatadorSeguroUI.aplicarEstiloToggleCartoon(
                tgModoEscuro, tgModoEscuro.isSelected(), "#F8FAFC", "#CBD5E1", "#0F172A", "#0F172A", "#38BDF8", "#F8FAFC");
        syncDark.run();
        tgModoEscuro.setOnAction(e -> {
            ponte.getGerenciadorTema().definirModoEscuro(tgModoEscuro.isSelected());
            syncDark.run();
            mostrarStatus(true, tgModoEscuro.isSelected() ? "Tema Escuro ativado!" : "Tema Claro restaurado!");
        });

        ColorPicker colorPickerBg = new ColorPicker(Color.web(ponte.getGerenciadorTema().getSnapshotAtual().resolverBgApp()));
        colorPickerBg.setPrefWidth(135);
        colorPickerBg.setOnAction(e -> {
            Color c = colorPickerBg.getValue();
            String hex = String.format("#%02X%02X%02X",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
            ponte.getGerenciadorTema().definirCorBackgroundPersonalizada(hex);
            mostrarStatus(true, "Cor de fundo personalizada aplicada (" + hex + ")!");
        });

        Button btnRestaurarFundo = FormatadorSeguroUI.criarBotaoCartoon("↺ Restaurar Fundo Automático", "#F1F5F9", "#CBD5E1", "#334155");
        btnRestaurarFundo.setOnAction(e -> {
            ponte.getGerenciadorTema().definirCorBackgroundPersonalizada(null);
            mostrarStatus(true, "Fundo restaurado para a paleta automática do tema!");
        });

        ComboBox<String> cbTema = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbTema.getItems().addAll(
                "Dinâmico pelo Clima da Loja (Recomendado)",
                "Fixar: ☀️ Sol Equatorial (Acentos Âmbar)",
                "Fixar: 🌧️ Toró Amazônico (Acentos Azuis)",
                "Fixar: ⛅ Brisa da Floresta (Acentos Verdes)",
                "Fixar: 🌬️ Friagem (Acentos Violeta)"
        );
        cbTema.getSelectionModel().selectFirst();
        cbTema.setPrefWidth(300);
        cbTema.setOnAction(e -> {
            int idx = cbTema.getSelectionModel().getSelectedIndex();
            var tema = ponte.getGerenciadorTema();
            switch (idx) {
                case 1 -> tema.definirModoDinamico(false, EstadoClimaticoAmazonico.SOL_EQUATORIAL);
                case 2 -> tema.definirModoDinamico(false, EstadoClimaticoAmazonico.TORO_CHUVA);
                case 3 -> tema.definirModoDinamico(false, EstadoClimaticoAmazonico.BRISA_FLORESTA);
                case 4 -> tema.definirModoDinamico(false, EstadoClimaticoAmazonico.FRIAGEM_AMAZONICA);
                default -> tema.definirModoDinamico(true, null);
            }
            mostrarStatus(true, "Paleta corporativa atualizada!");
        });

        Slider sliderFonte = new Slider(12, 15, FormatadorSeguroUI.getFonteBasePx());
        sliderFonte.setMajorTickUnit(1);
        sliderFonte.setMinorTickCount(0);
        sliderFonte.setSnapToTicks(true);
        sliderFonte.setShowTickMarks(true);
        sliderFonte.setPrefWidth(220);

        Label lblEscala = FormatadorSeguroUI.criarLabel("Fonte Base: " + (int) sliderFonte.getValue() + "px", 12, true, "#0F172A");
        sliderFonte.valueProperty().addListener((obs, o, n) -> lblEscala.setText("Fonte Base: " + Math.round(n.doubleValue()) + "px"));

        Button btnAplicarFonte = FormatadorSeguroUI.criarBotaoCartoon("Aplicar Fonte", "#0F172A", "#0F172A", "#FFFFFF");
        btnAplicarFonte.setOnAction(e -> {
            int px = (int) Math.round(sliderFonte.getValue());
            ponte.getGerenciadorTema().definirTamanhoFonteBase(px);
            mostrarStatus(true, "Escala tipográfica ajustada para " + px + "px!");
        });

        cardVisual.getChildren().addAll(
                FormatadorSeguroUI.criarTituloSecao("🎨", "Aparência, Modo Escuro, Cor de Fundo & Tipografia", "#0F172A"),
                new FlowPane(10, 8, tgModoEscuro,
                        FormatadorSeguroUI.criarLabel("Cor de Fundo (Background):", 12, true, "#334155"), colorPickerBg, btnRestaurarFundo),
                new FlowPane(10, 8, FormatadorSeguroUI.criarLabel("Acentos Climáticos:", 12, true, "#334155"), cbTema),
                new FlowPane(10, 8, sliderFonte, lblEscala, btnAplicarFonte));

        raiz.getChildren().addAll(cardIa, cardVisual);
        return raiz;
    }

    private VBox construirSubAbaManutencao() {
        VBox card = new VBox(10);
        card.setPadding(new Insets(14));
        card.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Button btnInit = FormatadorSeguroUI.criarBotaoCartoon("⚡ Inicializar Dados Fundamentais (UM / Pagamentos / Balcão)",
                "#EFF6FF", "#93C5FD", "#1E40AF");
        btnInit.setOnAction(e -> ponte.executarRotaAsync(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of(), resp ->
                mostrarStatus(resp.sucesso(), FormatadorSeguroUI.tratarParaExibicao(resp.dados(), resp.mensagem()))));

        TextField txtToken = FormatadorSeguroUI.criarCampoTextoCartoon("Digite CONFIRMAR_RESET para resetar banco de testes", "#FCA5A5", "#991B1B");
        txtToken.setPrefWidth(340);

        Button btnReset = FormatadorSeguroUI.criarBotaoCartoon("🧹 Resetar Base de Demonstração", "#FEE2E2", "#FCA5A5", "#991B1B");
        btnReset.setOnAction(e -> {
            String token = txtToken.getText() != null ? txtToken.getText().trim() : "";
            if (!"CONFIRMAR_RESET".equals(token)) {
                mostrarStatus(false, "Digite exatamente CONFIRMAR_RESET antes de executar o reset.");
                return;
            }
            DialogoCorporativoModal.confirmarAcaoManual(
                    this,
                    "Resetar Base de Demonstração",
                    "Esta operação apaga produtos, lotes e notas extras mantendo apenas unidades de medida, formas de pagamento e Cliente Padrão.",
                    Map.of("Confirmação", token),
                    () -> ponte.executarRotaAsync(RotaSistema.SISTEMA_RESETAR_BANCO_RESTRITO, Map.of("confirmacao", token), resp -> {
                        if (resp.sucesso()) {
                            txtToken.clear();
                            mostrarStatus(true, FormatadorSeguroUI.tratarParaExibicao(resp.dados(), "Banco resetado mantendo dados fundamentais."));
                        } else {
                            mostrarStatus(false, FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                        }
                    }));
        });

        card.getChildren().addAll(
                FormatadorSeguroUI.criarTituloSecao("🗄️", "Inicialização & Manutenção Restrita do Banco", "#0F172A"),
                FormatadorSeguroUI.criarLabel("Use apenas em ambiente de demonstração/homologação. O reset remove produtos, notas e pessoas extras.", 12, false, "#475569"),
                btnInit,
                new FlowPane(8, 8, txtToken, btnReset));

        VBox wrapper = new VBox(card);
        wrapper.setPadding(new Insets(10, 2, 10, 2));
        return wrapper;
    }

    public void setAtiva(boolean ativa) {
        // Configurações carregam instantaneamente sem consultas pesadas ao banco
    }

    public void carregarSeNecessario() {
        // Sem consulta pesada pendente
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
        }
    }

    private String lerPersonalidadeAtual() {
        try {
            Path p = Paths.get("config/personalidade.txt");
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return "Consultor Comercial Corporativo";
    }

    private void salvarPersonalidade(String texto) {
        if (texto == null || texto.isBlank()) return;
        try {
            Files.writeString(Paths.get("config/personalidade.txt"), texto.trim(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private void mostrarStatus(boolean ok, String msg) {
        lblStatusConfig.setText((ok ? "✔ " : "⚠️ ") + FormatadorSeguroUI.tratarParaExibicao(msg, ""));
        lblStatusConfig.setStyle(String.format("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: %s;",
                ok ? "#047857" : "#991B1B"));
        boxStatus.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(
                ok ? "#ECFDF5" : "#FEE2E2", ok ? "#6EE7B7" : "#FCA5A5"));
        boxStatus.setVisible(true);
        boxStatus.setManaged(true);
    }
}
