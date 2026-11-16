package br.com.senac.view.javafx.componentes;

import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.PontoDiarioSazonal;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gráfico Corporativo Sazonal Ampliado com:
 * - Filtro 'Hoje', 7D, 30D, 90D, Tudo e Período Customizado;
 * - Cores executivas em todos os filtros e botões de foco ativos;
 * - Compensação de subida (escala compressiva suave sem estouro de topo para 100, 300 ... 1000);
 * - Curvas fluidas suavizadas com clique no dia para abrir Card Detalhado;
 * - Faixa inferior em cards estruturados (sem texto bruto estilo terminal).
 */
public class GraficoSazonalPeriodoCanvas extends VBox {

    public enum DimensaoTempo {
        HOJE("Hoje", 1),
        SETE_DIAS("7D", 7),
        TRINTA_DIAS("30D", 30),
        NOVENTA_DIAS("90D", 90),
        TUDO("Tudo", Integer.MAX_VALUE),
        CUSTOM("Período", 30);

        private final String rotulo;
        private final int dias;

        DimensaoTempo(String rotulo, int dias) {
            this.rotulo = rotulo;
            this.dias = dias;
        }

        public String getRotulo() { return rotulo; }
        public int getDias() { return dias; }
    }

    private final ConectorProblemasIA conectorIA;

    private List<PontoDiarioSazonal> serieCompleta = new ArrayList<>();
    private List<PontoDiarioSazonal> serieFiltrada = new ArrayList<>();
    private DimensaoTempo dimensaoAtual = DimensaoTempo.TRINTA_DIAS;
    private LocalDate epicenterDataDestacada = null;
    private String tituloEpicentroAtivo = null;
    private String codigoEpicentroAtivo = null;

    private final ToggleGroup grupoDimensao = new ToggleGroup();
    private final List<ToggleButton> botoesDimensao = new ArrayList<>();
    private final HBox barraCustomIntervalo = new HBox(8);
    private final DatePicker dpInicio = new DatePicker();
    private final DatePicker dpFim = new DatePicker();

    private final ComboBox<String> cbMes = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbRio = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbClima = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final Map<String, Button> botoesEpicentro = new LinkedHashMap<>();
    private final ToggleButton tgCompensarPicos = new ToggleButton("⚖️ Compensar Subida (Escala)");

    private final Label lblTituloGrafico;
    private final Label lblSubtituloPeriodo;
    private final GridPane gridCardsResumo = new GridPane();

    private final Canvas canvas;
    private final StackPane areaCanvas;
    private final VBox tooltipBox;
    private final Label lblTooltipData;
    private final Label lblTooltipFin;
    private final Label lblTooltipAmbiente;
    private final Button btnExplicarIA;

    private int indiceHover = -1;
    private final double padLeft = 82;
    private final double padRight = 78;
    private final double padTop = 22;
    private final double padBottom = 34;
    private GerenciadorTemaClimatico.SnapshotTemaClimatico ultimoTema;

    public GraficoSazonalPeriodoCanvas(ConectorProblemasIA conectorIA) {
        this.conectorIA = conectorIA;
        setSpacing(10);
        setPadding(new Insets(12, 14, 12, 14));
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxWidth(Double.MAX_VALUE);
        setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        this.lblTituloGrafico = FormatadorSeguroUI.criarTituloSecao(
                "📈", "Sazonalidade de Vendas, Lucro Líquido, Clima e Rio", "#0F172A");
        this.lblSubtituloPeriodo = FormatadorSeguroUI.criarSubtitulo(
                "Curva contínua diária com compensação inteligente de picos. Clique em qualquer ponto do gráfico para detalhar o dia.",
                "#64748B");

        VBox boxTitulos = new VBox(2, lblTituloGrafico, lblSubtituloPeriodo);
        HBox.setHgrow(boxTitulos, Priority.ALWAYS);

        HBox pilulasTempo = new HBox(6);
        pilulasTempo.setAlignment(Pos.CENTER_RIGHT);
        for (DimensaoTempo d : DimensaoTempo.values()) {
            ToggleButton tb = new ToggleButton(d.getRotulo());
            tb.setToggleGroup(grupoDimensao);
            tb.setUserData(d);
            if (d == DimensaoTempo.TRINTA_DIAS) tb.setSelected(true);
            tb.setOnAction(e -> {
                dimensaoAtual = d;
                limparFocoEpicentro();
                atualizarEstilosBotoesTempo();
                boolean mostrarCustom = (d == DimensaoTempo.CUSTOM);
                barraCustomIntervalo.setVisible(mostrarCustom);
                barraCustomIntervalo.setManaged(mostrarCustom);
                if (!mostrarCustom) recalcularEAtualizar();
            });
            botoesDimensao.add(tb);
            pilulasTempo.getChildren().add(tb);
        }

        HBox topoLinha = new HBox(12, boxTitulos, pilulasTempo);
        topoLinha.setAlignment(Pos.CENTER_LEFT);

        // Barra customizada de período
        dpInicio.setValue(LocalDate.now().minusDays(30));
        dpFim.setValue(LocalDate.now());
        dpInicio.setPrefHeight(30);
        dpFim.setPrefHeight(30);
        Button btnAplicarCustom = FormatadorSeguroUI.criarBotaoCartoon("Aplicar Período", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAplicarCustom.setOnAction(e -> {
            limparFocoEpicentro();
            recalcularEAtualizar();
        });
        barraCustomIntervalo.getChildren().setAll(
                FormatadorSeguroUI.criarLabel("De:", 12, true, "#0F172A"), dpInicio,
                FormatadorSeguroUI.criarLabel("Até:", 12, true, "#0F172A"), dpFim,
                btnAplicarCustom
        );
        barraCustomIntervalo.setAlignment(Pos.CENTER_LEFT);
        barraCustomIntervalo.setPadding(new Insets(6, 10, 6, 10));
        barraCustomIntervalo.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
        barraCustomIntervalo.setVisible(false);
        barraCustomIntervalo.setManaged(false);

        // Filtros coloridos + Botões de Foco + Compensação de Subida
        cbMes.getItems().add("Mês: Todos");
        for (int m = 1; m <= 12; m++) {
            String nomeMes = Month.of(m).getDisplayName(TextStyle.SHORT, FormatadorSeguroUI.LOCALE_BR);
            nomeMes = nomeMes.substring(0, 1).toUpperCase(FormatadorSeguroUI.LOCALE_BR) + nomeMes.substring(1);
            cbMes.getItems().add(String.format("%02d - %s", m, nomeMes));
        }
        cbMes.getSelectionModel().selectFirst();
        cbMes.setPrefWidth(135);

        cbRio.getItems().addAll("Rio: Todos", "Cheia (>= 27m)", "Normal (19-27m)", "Seca (<= 19m)");
        cbRio.getSelectionModel().selectFirst();
        cbRio.setPrefWidth(150);

        cbClima.getItems().addAll("Clima: Todos", "Chuva Forte", "Chuva Leve", "Sol / Quente");
        cbClima.getSelectionModel().selectFirst();
        cbClima.setPrefWidth(145);

        cbMes.setOnAction(e -> { limparFocoEpicentro(); atualizarCoresCombosFiltros(); recalcularEAtualizar(); });
        cbRio.setOnAction(e -> { limparFocoEpicentro(); atualizarCoresCombosFiltros(); recalcularEAtualizar(); });
        cbClima.setOnAction(e -> { limparFocoEpicentro(); atualizarCoresCombosFiltros(); recalcularEAtualizar(); });

        tgCompensarPicos.setSelected(true);
        tgCompensarPicos.setOnAction(e -> {
            atualizarEstiloToggleCompensar();
            desenharGrafico();
        });
        atualizarEstiloToggleCompensar();

        Button btnLimparFiltros = FormatadorSeguroUI.criarBotaoCartoon("✕ Limpar Filtros", "#F8FAFC", "#CBD5E1", "#475569");
        btnLimparFiltros.setOnAction(e -> {
            cbMes.getSelectionModel().selectFirst();
            cbRio.getSelectionModel().selectFirst();
            cbClima.getSelectionModel().selectFirst();
            limparFocoEpicentro();
            atualizarCoresCombosFiltros();
            recalcularEAtualizar();
        });

        this.btnExplicarIA = FormatadorSeguroUI.criarBotaoIA("💬 Analisar Período", () -> {
            if (conectorIA != null) {
                String rotulo = tituloEpicentroAtivo != null ? tituloEpicentroAtivo : dimensaoAtual.getRotulo();
                conectorIA.explicarPeriodoGrafico(rotulo, serieFiltrada);
            }
        });

        FlowPane barraFiltros = new FlowPane(8, 6);
        barraFiltros.setAlignment(Pos.CENTER_LEFT);
        barraFiltros.getChildren().addAll(
                cbMes, cbRio, cbClima,
                criarBotaoEpicentro("Pico Venda", "PICO_FAT"),
                criarBotaoEpicentro("Menor Venda", "BAIXA_FAT"),
                criarBotaoEpicentro("Maior Perda", "MAIOR_PERDA"),
                criarBotaoEpicentro("Cheia Rio", "CHEIA_RIO"),
                criarBotaoEpicentro("Seca Rio", "SECA_RIO"),
                criarBotaoEpicentro("Chuvas", "CHUVAS"),
                tgCompensarPicos,
                btnLimparFiltros,
                btnExplicarIA
        );

        // Canvas ampliado e fluido
        this.canvas = new Canvas(780, 345);
        this.areaCanvas = new StackPane();
        this.areaCanvas.setMinHeight(330);
        this.areaCanvas.setPrefHeight(365);
        this.areaCanvas.setMaxHeight(460);
        this.areaCanvas.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-color: #CBD5E1; -fx-border-width: 1px; -fx-cursor: hand;");
        VBox.setVgrow(areaCanvas, Priority.ALWAYS);

        this.lblTooltipData = FormatadorSeguroUI.criarLabel("", 12, true, "#0F172A");
        this.lblTooltipFin = FormatadorSeguroUI.criarLabel("", 12, true, "#047857");
        this.lblTooltipAmbiente = FormatadorSeguroUI.criarLabel("", 11, false, "#334155");

        this.tooltipBox = new VBox(3, lblTooltipData, lblTooltipFin, lblTooltipAmbiente,
                FormatadorSeguroUI.criarLabel("Clique no ponto para ver o Card do dia", 11, true, "#1D4ED8"));
        this.tooltipBox.setPadding(new Insets(8, 10, 8, 10));
        this.tooltipBox.setMaxWidth(275);
        this.tooltipBox.setMaxHeight(Region.USE_PREF_SIZE);
        this.tooltipBox.setMouseTransparent(true);
        this.tooltipBox.setVisible(false);
        this.tooltipBox.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#1D4ED8"));
        StackPane.setAlignment(tooltipBox, Pos.TOP_LEFT);

        this.areaCanvas.getChildren().addAll(canvas, tooltipBox);
        this.areaCanvas.widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(Math.max(400, n.doubleValue() - 4));
            desenharGrafico();
        });
        this.areaCanvas.heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(Math.max(320, n.doubleValue() - 4));
            desenharGrafico();
        });

        configurarInteracaoMouse();

        // Grade estruturada para os dados formatados abaixo do dashboard
        gridCardsResumo.setHgap(8);
        gridCardsResumo.setVgap(8);
        for (int i = 0; i < 5; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(20);
            cc.setHgrow(Priority.ALWAYS);
            gridCardsResumo.getColumnConstraints().add(cc);
        }

        FlowPane legenda = new FlowPane(14, 4);
        legenda.setAlignment(Pos.CENTER_LEFT);
        legenda.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("━━ Faturamento (R$)", 11, true, "#059669"),
                FormatadorSeguroUI.criarLabel("┅┅ Lucro Líquido (R$)", 11, true, "#475569"),
                FormatadorSeguroUI.criarLabel("━━ Perdas (R$)", 11, true, "#DC2626"),
                FormatadorSeguroUI.criarLabel("━━ Temperatura (°C)", 11, true, "#EA580C"),
                FormatadorSeguroUI.criarLabel("〰 Cota do Rio (m)", 11, true, "#0284C7"),
                FormatadorSeguroUI.criarLabel("■ Fundo = Clima do Período", 11, true, "#B45309")
        );

        getChildren().addAll(topoLinha, barraCustomIntervalo, barraFiltros, areaCanvas, gridCardsResumo, legenda);
        atualizarEstilosBotoesTempo();
        atualizarCoresCombosFiltros();
        atualizarCoresBotoesEpicentro();
    }

    private Button criarBotaoEpicentro(String texto, String codigo) {
        Button b = FormatadorSeguroUI.criarBotaoCartoon(texto, "#F8FAFC", "#CBD5E1", "#334155");
        b.setOnAction(e -> aplicarEpicentro(codigo, texto));
        botoesEpicentro.put(codigo, b);
        return b;
    }

    private void limparFocoEpicentro() {
        this.epicenterDataDestacada = null;
        this.tituloEpicentroAtivo = null;
        this.codigoEpicentroAtivo = null;
        atualizarCoresBotoesEpicentro();
    }

    public void definirSerieDados(List<PontoDiarioSazonal> serie) {
        this.serieCompleta = serie != null ? new ArrayList<>(serie) : new ArrayList<>();
        recalcularEAtualizar();
    }

    public List<PontoDiarioSazonal> getSerieFiltradaAtual() {
        return Collections.unmodifiableList(serieFiltrada);
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        this.ultimoTema = snap;
        if (snap == null) return;
        setStyle(FormatadorSeguroUI.estiloCardCartoon(snap.resolverBgCard(), snap.resolverBorderCard()));
        atualizarEstilosBotoesTempo();
        desenharGrafico();
    }

    private void atualizarEstiloToggleCompensar() {
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(
                tgCompensarPicos, tgCompensarPicos.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155",
                "#0F766E", "#115E59", "#FFFFFF");
    }

    private void atualizarEstilosBotoesTempo() {
        String accent = ultimoTema != null ? ultimoTema.estado().getAccentMain() : "#1D4ED8";
        for (ToggleButton tb : botoesDimensao) {
            DimensaoTempo d = (DimensaoTempo) tb.getUserData();
            String corSelecionada = (d == DimensaoTempo.HOJE) ? "#047857" : accent;
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(
                    tb, tb.isSelected(),
                    "#F8FAFC", "#CBD5E1", "#334155",
                    corSelecionada, corSelecionada, "#FFFFFF");
        }
    }

    private void atualizarCoresCombosFiltros() {
        estilizarComboFiltro(cbMes, cbMes.getSelectionModel().getSelectedIndex() > 0, "#7C3AED");
        estilizarComboFiltro(cbRio, cbRio.getSelectionModel().getSelectedIndex() > 0, "#0284C7");
        estilizarComboFiltro(cbClima, cbClima.getSelectionModel().getSelectedIndex() > 0, "#D97706");
    }

    private void estilizarComboFiltro(ComboBox<String> cb, boolean ativo, String corHex) {
        int px = FormatadorSeguroUI.garantirPisoFonte(12);
        if (ativo) {
            cb.setStyle(String.format(
                    "-fx-background-color: #EFF6FF; -fx-border-color: %s; -fx-border-width: 2px; "
                            + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                            + "-fx-padding: 2px 8px; -fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: %s;",
                    corHex, px, corHex));
        } else {
            cb.setStyle(String.format(
                    "-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-width: 1px; "
                            + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                            + "-fx-padding: 2px 8px; -fx-font-size: %dpx; -fx-text-fill: #0F172A;",
                    px));
        }
    }

    private void atualizarCoresBotoesEpicentro() {
        for (var e : botoesEpicentro.entrySet()) {
            boolean ativo = e.getKey().equals(codigoEpicentroAtivo);
            if (!ativo) {
                FormatadorSeguroUI.aplicarEstiloBotaoCartoon(e.getValue(), "#F8FAFC", "#CBD5E1", "#334155", 12);
            } else {
                String bg = switch (e.getKey()) {
                    case "PICO_FAT" -> "#047857";
                    case "BAIXA_FAT" -> "#B45309";
                    case "MAIOR_PERDA" -> "#DC2626";
                    case "CHEIA_RIO", "SECA_RIO" -> "#0284C7";
                    default -> "#1D4ED8";
                };
                FormatadorSeguroUI.aplicarEstiloBotaoCartoon(e.getValue(), bg, bg, "#FFFFFF", 12);
            }
        }
    }

    private void aplicarEpicentro(String tipo, String rotulo) {
        if (serieCompleta.isEmpty()) return;
        int idxEpicentro = serieCompleta.size() - 1;
        switch (tipo) {
            case "PICO_FAT" -> {
                double max = -1;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    if (serieCompleta.get(i).faturamento() > max) {
                        max = serieCompleta.get(i).faturamento();
                        idxEpicentro = i;
                    }
                }
            }
            case "BAIXA_FAT" -> {
                double min = Double.MAX_VALUE;
                boolean achou = false;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    var p = serieCompleta.get(i);
                    if (p.possuiMovimentoReal() && p.faturamento() < min) {
                        min = p.faturamento();
                        idxEpicentro = i;
                        achou = true;
                    }
                }
                if (!achou) idxEpicentro = Math.max(0, serieCompleta.size() / 2);
            }
            case "MAIOR_PERDA" -> {
                double max = -1;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    if (serieCompleta.get(i).perdas() > max) {
                        max = serieCompleta.get(i).perdas();
                        idxEpicentro = i;
                    }
                }
            }
            case "CHEIA_RIO" -> {
                double max = -1;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    if (serieCompleta.get(i).nivelRioMetros() > max) {
                        max = serieCompleta.get(i).nivelRioMetros();
                        idxEpicentro = i;
                    }
                }
            }
            case "SECA_RIO" -> {
                double min = Double.MAX_VALUE;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    if (serieCompleta.get(i).nivelRioMetros() < min) {
                        min = serieCompleta.get(i).nivelRioMetros();
                        idxEpicentro = i;
                    }
                }
            }
            case "CHUVAS" -> {
                double max = -1;
                for (int i = 0; i < serieCompleta.size(); i++) {
                    if (serieCompleta.get(i).precipitacaoMm() > max) {
                        max = serieCompleta.get(i).precipitacaoMm();
                        idxEpicentro = i;
                    }
                }
            }
        }

        cbMes.getSelectionModel().selectFirst();
        cbRio.getSelectionModel().selectFirst();
        cbClima.getSelectionModel().selectFirst();
        atualizarCoresCombosFiltros();

        int janela = (dimensaoAtual == DimensaoTempo.TUDO || dimensaoAtual == DimensaoTempo.CUSTOM || dimensaoAtual == DimensaoTempo.HOJE)
                ? 30 : dimensaoAtual.getDias();
        int metade = janela / 2;
        int inicio = Math.max(0, idxEpicentro - metade);
        int fim = Math.min(serieCompleta.size(), inicio + janela);
        if (fim - inicio < janela && inicio > 0) inicio = Math.max(0, fim - janela);

        this.epicenterDataDestacada = serieCompleta.get(idxEpicentro).data();
        this.tituloEpicentroAtivo = rotulo;
        this.codigoEpicentroAtivo = tipo;
        this.serieFiltrada = new ArrayList<>(serieCompleta.subList(inicio, fim));
        atualizarCoresBotoesEpicentro();
        atualizarCardsResumoFormatado();
        desenharGrafico();
    }

    private void recalcularEAtualizar() {
        if (serieCompleta.isEmpty()) {
            serieFiltrada = new ArrayList<>();
            atualizarCardsResumoFormatado();
            desenharGrafico();
            return;
        }
        List<PontoDiarioSazonal> base = new ArrayList<>();
        String mesSel = cbMes.getValue();
        int mesFiltro = 0;
        if (mesSel != null && mesSel.length() >= 2 && Character.isDigit(mesSel.charAt(0))) {
            mesFiltro = Integer.parseInt(mesSel.substring(0, 2));
        }
        String rioSel = cbRio.getValue() != null ? cbRio.getValue() : "";
        String climaSel = cbClima.getValue() != null ? cbClima.getValue() : "";

        for (PontoDiarioSazonal p : serieCompleta) {
            if (mesFiltro > 0 && p.data().getMonthValue() != mesFiltro) continue;
            if (rioSel.contains("Cheia") && p.nivelRioMetros() < 27.0) continue;
            if (rioSel.contains("Normal") && (p.nivelRioMetros() >= 27.0 || p.nivelRioMetros() <= 19.0)) continue;
            if (rioSel.contains("Seca") && p.nivelRioMetros() > 19.0) continue;

            if (climaSel.contains("Chuva Forte") && p.precipitacaoMm() < 5.0) continue;
            if (climaSel.contains("Chuva Leve") && (p.precipitacaoMm() < 1.0 || p.precipitacaoMm() >= 5.0)) continue;
            if (climaSel.contains("Sol") && (p.precipitacaoMm() >= 2.0 || p.tempMedia() < 28.0)) continue;

            if (dimensaoAtual == DimensaoTempo.HOJE) {
                if (!p.data().equals(LocalDate.now())) continue;
            } else if (dimensaoAtual == DimensaoTempo.CUSTOM) {
                LocalDate ini = dpInicio.getValue();
                LocalDate fim = dpFim.getValue();
                if (ini != null && p.data().isBefore(ini)) continue;
                if (fim != null && p.data().isAfter(fim)) continue;
            }
            base.add(p);
        }

        if (dimensaoAtual == DimensaoTempo.HOJE && base.isEmpty() && !serieCompleta.isEmpty()) {
            base.add(serieCompleta.get(serieCompleta.size() - 1));
        }

        if (dimensaoAtual != DimensaoTempo.TUDO && dimensaoAtual != DimensaoTempo.CUSTOM && dimensaoAtual != DimensaoTempo.HOJE
                && base.size() > dimensaoAtual.getDias()) {
            base = new ArrayList<>(base.subList(base.size() - dimensaoAtual.getDias(), base.size()));
        }

        this.serieFiltrada = base;
        atualizarCardsResumoFormatado();
        desenharGrafico();
    }

    private void atualizarCardsResumoFormatado() {
        gridCardsResumo.getChildren().clear();
        if (serieFiltrada.isEmpty()) {
            VBox vazio = FormatadorSeguroUI.criarMiniCardInfo(
                    "Sem Movimentação no Filtro",
                    "Nenhum dia atende aos filtros selecionados. Clique em 'Limpar Filtros' ou escolha 'Tudo'.",
                    "#F8FAFC", "#CBD5E1", "#64748B");
            gridCardsResumo.add(vazio, 0, 0, 5, 1);
            return;
        }

        double fat = 0, luc = 0, per = 0, sumTemp = 0, sumRio = 0;
        double fatChuva = 0, fatSol = 0;
        int diasChuva = 0, diasSol = 0, diasAtivos = 0;
        PontoDiarioSazonal melhorDia = serieFiltrada.get(0);

        for (var p : serieFiltrada) {
            fat += p.faturamento();
            luc += p.lucroLiquido();
            per += p.perdas();
            sumTemp += p.tempMedia();
            sumRio += p.nivelRioMetros();
            if (p.faturamento() > melhorDia.faturamento()) melhorDia = p;
            if (p.possuiMovimentoReal() || p.faturamento() > 0) diasAtivos++;
            if (p.precipitacaoMm() >= 5.0) { fatChuva += p.faturamento(); diasChuva++; }
            else if (p.tempMedia() >= 28.5) { fatSol += p.faturamento(); diasSol++; }
        }
        double margem = fat > 0 ? (luc / fat) * 100.0 : 0.0;
        double mediaDia = diasAtivos > 0 ? fat / diasAtivos : 0.0;
        double mediaSol = diasSol > 0 ? fatSol / diasSol : 0.0;
        double mediaChuva = diasChuva > 0 ? fatChuva / diasChuva : 0.0;

        String focoRecorte = tituloEpicentroAtivo != null
                ? tituloEpicentroAtivo + " (" + serieFiltrada.size() + "d)"
                : dimensaoAtual.getRotulo() + " (" + serieFiltrada.size() + "d)";
        String intervalo = FormatadorSeguroUI.formatarDataAmigavel(serieFiltrada.get(0).data())
                + " → " + FormatadorSeguroUI.formatarDataAmigavel(serieFiltrada.get(serieFiltrada.size() - 1).data());

        String tendenciaClima = (mediaChuva > mediaSol && diasChuva > 0)
                ? "Forte sob chuva (" + FormatadorSeguroUI.formatarMoeda(mediaChuva) + "/dia)"
                : (mediaSol > 0 ? "Forte no calor (" + FormatadorSeguroUI.formatarMoeda(mediaSol) + "/dia)"
                : String.format(FormatadorSeguroUI.LOCALE_BR, "Média %.1f°C • Rio %.1fm",
                sumTemp / serieFiltrada.size(), sumRio / serieFiltrada.size()));

        VBox c1 = FormatadorSeguroUI.criarMiniCardInfo(
                "🎯 Recorte Selecionado",
                focoRecorte + "\n" + intervalo,
                "#EFF6FF", "#93C5FD", "#1E40AF");
        VBox c2 = FormatadorSeguroUI.criarMiniCardInfo(
                "💰 Faturamento Total",
                FormatadorSeguroUI.formatarMoeda(fat) + "\nLucro: " + FormatadorSeguroUI.formatarMoeda(luc)
                        + String.format(FormatadorSeguroUI.LOCALE_BR, " (%.1f%%)", margem),
                "#ECFDF5", "#6EE7B7", "#047857");
        VBox c3 = FormatadorSeguroUI.criarMiniCardInfo(
                "🏆 Recorde na Janela",
                FormatadorSeguroUI.formatarMoeda(melhorDia.faturamento()) + "\nem "
                        + FormatadorSeguroUI.formatarDataAmigavel(melhorDia.data()),
                "#FFFBEB", "#FCD34D", "#B45309");
        VBox c4 = FormatadorSeguroUI.criarMiniCardInfo(
                "📊 Média Ativa & Perdas",
                "Média/dia: " + FormatadorSeguroUI.formatarMoeda(mediaDia)
                        + "\nPerdas: " + FormatadorSeguroUI.formatarMoeda(per),
                per > 0 ? "#FEF2F2" : "#F8FAFC",
                per > 0 ? "#FCA5A5" : "#CBD5E1",
                per > 0 ? "#991B1B" : "#334155");
        VBox c5 = FormatadorSeguroUI.criarMiniCardInfo(
                "🌦️ Sensibilidade Clima/Rio",
                tendenciaClima + String.format(FormatadorSeguroUI.LOCALE_BR, "\nRio médio %.1f m • %d dias ativos",
                        sumRio / serieFiltrada.size(), diasAtivos),
                "#F0F9FF", "#7DD3FC", "#0369A1");

        gridCardsResumo.add(c1, 0, 0);
        gridCardsResumo.add(c2, 1, 0);
        gridCardsResumo.add(c3, 2, 0);
        gridCardsResumo.add(c4, 3, 0);
        gridCardsResumo.add(c5, 4, 0);
    }

    private void configurarInteracaoMouse() {
        canvas.setOnMouseMoved(e -> {
            if (serieFiltrada.isEmpty()) {
                tooltipBox.setVisible(false);
                return;
            }
            double x = e.getX();
            double plotW = Math.max(10, canvas.getWidth() - padLeft - padRight);
            if (x < padLeft || x > padLeft + plotW) {
                if (indiceHover != -1) {
                    indiceHover = -1;
                    tooltipBox.setVisible(false);
                    desenharGrafico();
                }
                return;
            }
            int n = serieFiltrada.size();
            int idx = n == 1 ? 0 : (int) Math.round(((x - padLeft) / plotW) * (n - 1));
            idx = Math.max(0, Math.min(n - 1, idx));
            if (idx != indiceHover) {
                indiceHover = idx;
                desenharGrafico();
            }
            atualizarTooltip(idx, e.getX(), e.getY());
        });

        canvas.setOnMouseClicked(e -> {
            if (serieFiltrada.isEmpty() || indiceHover < 0 || indiceHover >= serieFiltrada.size()) return;
            PontoDiarioSazonal p = serieFiltrada.get(indiceHover);
            abrirCardDetalhesDia(p);
        });

        canvas.setOnMouseExited(e -> {
            indiceHover = -1;
            tooltipBox.setVisible(false);
            desenharGrafico();
        });
    }

    private void abrirCardDetalhesDia(PontoDiarioSazonal p) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Data de Referência", FormatadorSeguroUI.formatarDataAmigavel(p.data()));
        meta.put("Faturamento do Dia", FormatadorSeguroUI.formatarMoeda(p.faturamento()));
        meta.put("Lucro Líquido Contábil", FormatadorSeguroUI.formatarMoeda(p.lucroLiquido()));
        meta.put("Perdas Registradas", FormatadorSeguroUI.formatarMoeda(p.perdas()));
        meta.put("Temperatura Média", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C", p.tempMedia()));
        meta.put("Precipitação (Chuva)", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f mm", p.precipitacaoMm()));
        meta.put("Cota Fluvial", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m (%s)", p.nivelRioMetros(), p.regimeRio()));
        meta.put("Eventos Regionais", FormatadorSeguroUI.tratarParaExibicao(p.resumoEventos(), "Dia operacional regular"));

        DialogoCorporativoModal.exibirCardDetalhes(
                this,
                "📅",
                "Desempenho Diário — " + FormatadorSeguroUI.formatarDataAmigavel(p.data()),
                p.possuiMovimentoReal() ? "Movimentação Real Registrada" : "Monitoramento Ambiental",
                "#0F172A",
                meta,
                null,
                null,
                conectorIA != null ? () -> conectorIA.explicarPeriodoGrafico(
                        "Dia " + FormatadorSeguroUI.formatarDataAmigavel(p.data()), List.of(p)) : null
        );
    }

    private void atualizarTooltip(int idx, double mouseX, double mouseY) {
        if (idx < 0 || idx >= serieFiltrada.size()) return;
        PontoDiarioSazonal p = serieFiltrada.get(idx);
        lblTooltipData.setText(FormatadorSeguroUI.formatarDataAmigavel(p.data()));
        String fin = "Vendas: " + FormatadorSeguroUI.formatarMoeda(p.faturamento())
                + " | Lucro: " + FormatadorSeguroUI.formatarMoeda(p.lucroLiquido());
        if (p.perdas() > 0) fin += "\nPerdas: " + FormatadorSeguroUI.formatarMoeda(p.perdas());
        lblTooltipFin.setText(fin);
        lblTooltipAmbiente.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "Rio: %.1f m (%s) | %.1f°C | %.1f mm",
                p.nivelRioMetros(), p.regimeRio(), p.tempMedia(), p.precipitacaoMm()));

        double w = areaCanvas.getWidth();
        double h = areaCanvas.getHeight();
        tooltipBox.setTranslateX(Math.min(Math.max(8, mouseX + 14), Math.max(8, w - 285)));
        tooltipBox.setTranslateY(Math.min(Math.max(8, mouseY - 24), Math.max(8, h - 120)));
        tooltipBox.setVisible(true);
    }

    private void desenharGrafico() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 40 || h <= 40) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        boolean dark = ultimoTema != null && ultimoTema.modoEscuro();
        g.clearRect(0, 0, w, h);
        if (dark) {
            g.setFill(Color.web("#0F172A"));
            g.fillRect(0, 0, w, h);
        }

        double plotW = Math.max(40, w - padLeft - padRight);
        double plotH = Math.max(40, h - padTop - padBottom);

        if (serieFiltrada.isEmpty()) {
            g.setFill(Color.web(dark ? "#0F172A" : "#F8FAFC"));
            g.fillRect(0, 0, w, h);
            g.setFill(Color.web(dark ? "#94A3B8" : "#64748B"));
            g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("Sem registros no filtro selecionado.", w / 2, h / 2);
            return;
        }

        double somaTemp = 0, somaChuva = 0, maiorRio = 20.0;
        double minTempObs = Double.MAX_VALUE, maxTempObs = -Double.MAX_VALUE;
        boolean temPerdas = false;
        double maxFinBruto = 100.0;
        List<Double> valoresPositivos = new ArrayList<>();

        for (PontoDiarioSazonal p : serieFiltrada) {
            somaTemp += p.tempMedia();
            minTempObs = Math.min(minTempObs, p.tempMedia());
            maxTempObs = Math.max(maxTempObs, p.tempMedia());
            somaChuva += p.precipitacaoMm();
            maiorRio = Math.max(maiorRio, p.nivelRioMetros());
            if (p.perdas() > 0) temPerdas = true;
            double m = Math.max(p.faturamento(), Math.max(p.lucroLiquido(), p.perdas()));
            maxFinBruto = Math.max(maxFinBruto, m);
            if (p.faturamento() > 0) valoresPositivos.add(p.faturamento());
        }
        double minTempEscala = Math.min(18.0, Math.floor((minTempObs == Double.MAX_VALUE ? 22.0 : minTempObs) - 2.0));
        double maxTempEscala = Math.max(38.0, Math.ceil((maxTempObs == -Double.MAX_VALUE ? 32.0 : maxTempObs) + 2.0));
        double spanTempEscala = Math.max(10.0, maxTempEscala - minTempEscala);
        double avgTemp = somaTemp / serieFiltrada.size();
        double avgChuva = somaChuva / serieFiltrada.size();

        // Escala máxima segura (NUNCA abaixo de maxFinBruto, evita qualquer estouro)
        double escalaTopo = Math.max(100.0, maxFinBruto * 1.12);
        Collections.sort(valoresPositivos);
        double medPositivo = valoresPositivos.isEmpty() ? 0 : valoresPositivos.get(valoresPositivos.size() / 2);
        boolean usarCompressaoSubida = tgCompensarPicos.isSelected()
                && ((medPositivo > 0 && maxFinBruto > medPositivo * 2.2) || maxFinBruto >= 500.0);

        g.save();
        g.beginPath();
        g.rect(padLeft, padTop, plotW, plotH);
        g.clip();

        // Fundo climático suave com profundidade
        LinearGradient gradCeu;
        RadialGradient flare;
        if (avgTemp < 26.0 || avgChuva >= 5.0) {
            gradCeu = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(30, 58, 138, 0.24)),
                    new Stop(0.65, Color.rgb(71, 85, 105, 0.10)),
                    new Stop(1.0, Color.rgb(255, 255, 255, 0.02)));
            flare = new RadialGradient(0, 0, 0.14, 0.16, 0.42, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(191, 219, 254, 0.48)),
                    new Stop(1.0, Color.rgb(191, 219, 254, 0.0)));
        } else if (avgTemp > 28.5) {
            gradCeu = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(234, 88, 12, 0.20)),
                    new Stop(0.65, Color.rgb(245, 158, 11, 0.10)),
                    new Stop(1.0, Color.rgb(255, 255, 255, 0.02)));
            flare = new RadialGradient(0, 0, 0.88, 0.14, 0.36, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(251, 146, 60, 0.50)),
                    new Stop(1.0, Color.rgb(251, 146, 60, 0.0)));
        } else {
            gradCeu = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(16, 185, 129, 0.18)),
                    new Stop(0.65, Color.rgb(245, 158, 11, 0.08)),
                    new Stop(1.0, Color.rgb(255, 255, 255, 0.02)));
            flare = new RadialGradient(0, 0, 0.88, 0.14, 0.36, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(253, 224, 71, 0.42)),
                    new Stop(1.0, Color.rgb(253, 224, 71, 0.0)));
        }
        g.setFill(gradCeu);
        g.fillRect(padLeft, padTop, plotW, plotH);
        g.setFill(flare);
        g.fillRect(padLeft, padTop, plotW, plotH);

        double maxRioEscala = maiorRio * 1.9;
        int n = serieFiltrada.size();
        double[] xs = new double[n];
        double[] ysRio = new double[n];
        double[] ysTemp = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? (padLeft + plotW / 2.0) : padLeft + ((double) i / (n - 1)) * plotW;
            double fracRio = Math.min(0.95, Math.max(0.06, serieFiltrada.get(i).nivelRioMetros() / maxRioEscala));
            ysRio[i] = padTop + plotH - (fracRio * plotH);
            double tObs = serieFiltrada.get(i).tempMedia();
            double fracTemp = Math.min(0.95, Math.max(0.06, (tObs - minTempEscala) / spanTempEscala));
            ysTemp[i] = padTop + plotH - (fracTemp * plotH);
        }

        // Onda fluvial fluida
        g.beginPath();
        if (n == 1) {
            g.moveTo(padLeft, padTop + plotH);
            g.lineTo(padLeft, ysRio[0]);
            g.lineTo(padLeft + plotW, ysRio[0]);
            g.lineTo(padLeft + plotW, padTop + plotH);
        } else {
            g.moveTo(xs[0], padTop + plotH);
            g.lineTo(xs[0], ysRio[0]);
            for (int i = 0; i < n - 1; i++) {
                double midX = (xs[i] + xs[i + 1]) / 2.0;
                g.bezierCurveTo(midX, ysRio[i], midX, ysRio[i + 1], xs[i + 1], ysRio[i + 1]);
            }
            g.lineTo(xs[n - 1], padTop + plotH);
        }
        g.closePath();

        LinearGradient gradRio = new LinearGradient(0, 0.4, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(2, 132, 199, 0.24)),
                new Stop(1.0, Color.rgb(2, 132, 199, 0.46)));
        g.setFill(gradRio);
        g.fill();

        // Linha da crista fluvial em destaque azul hidrológico (#0284C7)
        g.setStroke(Color.web("#0284C7"));
        g.setLineWidth(2.0);
        g.setLineDashes(null);
        desenharCurvaFluida(g, xs, ysRio);

        // Linhas horizontais de grade nos ticks reais calculados
        double[] ticksValor = calcularTicksEscala(escalaTopo, usarCompressaoSubida);
        g.setStroke(Color.rgb(148, 163, 184, 0.35));
        g.setLineWidth(1.0);
        for (double tv : ticksValor) {
            double gy = calcularYFinanceiro(tv, escalaTopo, plotH, usarCompressaoSubida);
            g.strokeLine(padLeft, gy, padLeft + plotW, gy);
        }

        if (epicenterDataDestacada != null) {
            for (int i = 0; i < n; i++) {
                if (serieFiltrada.get(i).data().equals(epicenterDataDestacada)) {
                    g.setStroke(Color.web("#D97706"));
                    g.setLineWidth(2.2);
                    g.setLineDashes(5, 4);
                    g.strokeLine(xs[i], padTop, xs[i], padTop + plotH);
                    g.setLineDashes(null);
                    break;
                }
            }
        }

        double[] ysFat = new double[n];
        double[] ysLuc = new double[n];
        double[] ysPer = new double[n];
        for (int i = 0; i < n; i++) {
            PontoDiarioSazonal p = serieFiltrada.get(i);
            ysFat[i] = calcularYFinanceiro(p.faturamento(), escalaTopo, plotH, usarCompressaoSubida);
            ysLuc[i] = calcularYFinanceiro(p.lucroLiquido(), escalaTopo, plotH, usarCompressaoSubida);
            ysPer[i] = calcularYFinanceiro(p.perdas(), escalaTopo, plotH, usarCompressaoSubida);
        }

        // Área suave sob faturamento para profundidade visual
        if (n >= 2) {
            g.beginPath();
            g.moveTo(xs[0], padTop + plotH);
            g.lineTo(xs[0], ysFat[0]);
            for (int i = 0; i < n - 1; i++) {
                double midX = (xs[i] + xs[i + 1]) / 2.0;
                g.bezierCurveTo(midX, ysFat[i], midX, ysFat[i + 1], xs[i + 1], ysFat[i + 1]);
            }
            g.lineTo(xs[n - 1], padTop + plotH);
            g.closePath();
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(5, 150, 105, 0.22)),
                    new Stop(1.0, Color.rgb(5, 150, 105, 0.02))));
            g.fill();
        }

        g.setStroke(Color.web("#059669"));
        g.setLineWidth(2.8);
        desenharCurvaFluida(g, xs, ysFat);

        g.setStroke(Color.web(dark ? "#E2E8F0" : "#0F172A"));
        g.setLineWidth(2.0);
        g.setLineDashes(5, 5);
        desenharCurvaFluida(g, xs, ysLuc);

        if (temPerdas) {
            g.setStroke(Color.web("#DC2626"));
            g.setLineWidth(2.2);
            g.setLineDashes(null);
            desenharCurvaFluida(g, xs, ysPer);
        }

        // Curva de Temperatura Média (°C) sincronizada com o card termômetro (#EA580C)
        g.setStroke(Color.web("#EA580C"));
        g.setLineWidth(2.2);
        g.setLineDashes(null);
        desenharCurvaFluida(g, xs, ysTemp);

        // Marcadores visíveis sem sobrecarga
        int stepMarcador = n <= 45 ? 1 : Math.max(2, n / 35);
        for (int i = 0; i < n; i++) {
            boolean ehPicoOuHover = (i == indiceHover)
                    || (serieFiltrada.get(i).faturamento() >= maxFinBruto * 0.90 && maxFinBruto > 0);
            if (!ehPicoOuHover && (i % stepMarcador != 0)) continue;
            double r = (i == indiceHover) ? 5.5 : 3.2;
            g.setFill(dark ? Color.web("#0F172A") : Color.WHITE);
            g.fillOval(xs[i] - r, ysFat[i] - r, r * 2, r * 2);
            g.setStroke(Color.web(i == indiceHover ? "#1D4ED8" : "#059669"));
            g.setLineWidth(i == indiceHover ? 2.5 : 2.0);
            g.strokeOval(xs[i] - r, ysFat[i] - r, r * 2, r * 2);

            if (i == indiceHover) {
                g.setFill(Color.web("#EA580C"));
                g.fillOval(xs[i] - 3.5, ysTemp[i] - 3.5, 7, 7);
                g.setFill(Color.web("#0284C7"));
                g.fillOval(xs[i] - 3.5, ysRio[i] - 3.5, 7, 7);
            }

            // Rótulo discreto no pico máximo sem estourar o topo
            if (n > 1 && serieFiltrada.get(i).faturamento() >= maxFinBruto * 0.99 && maxFinBruto > 0) {
                String lblMax = formatarCurtoMoeda(serieFiltrada.get(i).faturamento());
                double textX = Math.min(padLeft + plotW - 36, Math.max(padLeft + 36, xs[i]));
                double textY = Math.max(padTop + 13, ysFat[i] - 8);
                g.setFill(Color.web(dark ? "#34D399" : "#065F46"));
                g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(lblMax, textX, textY);
            }
        }

        if (indiceHover >= 0 && indiceHover < n) {
            g.setStroke(Color.rgb(29, 78, 216, 0.45));
            g.setLineWidth(1.2);
            g.strokeLine(xs[indiceHover], padTop, xs[indiceHover], padTop + plotH);
        }
        g.restore();

        // Borda do gráfico
        g.setStroke(Color.web(dark ? "#334155" : "#CBD5E1"));
        g.setLineWidth(1.2);
        g.strokeRect(padLeft, padTop, plotW, plotH);

        // Eixo Y esquerdo (Moeda com ticks adaptativos 100, 300 ... 1000 sem estourar)
        Font fonteEixo = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11);
        g.setFont(fonteEixo);
        g.setFill(Color.web(dark ? "#CBD5E1" : "#334155"));
        g.setTextAlign(TextAlignment.RIGHT);
        double ultimoTickY = Double.MAX_VALUE;
        for (double tv : ticksValor) {
            double y = calcularYFinanceiro(tv, escalaTopo, plotH, usarCompressaoSubida);
            if (Math.abs(ultimoTickY - y) < 18) continue; // evita sobreposição de texto
            ultimoTickY = y;
            g.fillText(formatarCurtoMoeda(tv), padLeft - 8, y + 4);
        }

        if (usarCompressaoSubida) {
            g.setFill(Color.web(dark ? "#2DD4BF" : "#0F766E"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText("⚖️ Escala compensada", padLeft + 8, padTop + 13);
        }

        // Eixo Y direito duplo: Cota Fluvial (#0284C7) + Temperatura (°C em #EA580C)
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.setTextAlign(TextAlignment.LEFT);
        for (int k = 1; k <= 4; k++) {
            double frac = k / 4.0;
            double valRio = maxRioEscala * (1.0 - frac);
            double valTemp = minTempEscala + spanTempEscala * (1.0 - frac);
            double y = padTop + frac * plotH + 4;
            g.setFill(Color.web(dark ? "#38BDF8" : "#0284C7"));
            g.fillText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.0fm", valRio), padLeft + plotW + 6, y);
            g.setFill(Color.web(dark ? "#FB923C" : "#EA580C"));
            g.fillText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.0f°", valTemp), padLeft + plotW + 40, y);
        }

        // Eixo X (Datas)
        g.setFont(fonteEixo);
        g.setFill(Color.web(dark ? "#94A3B8" : "#475569"));
        g.setTextAlign(TextAlignment.CENTER);
        int passo = Math.max(1, n / 6);
        for (int i = 0; i < n; i += passo) {
            LocalDate d = serieFiltrada.get(i).data();
            g.fillText(String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()), xs[i], padTop + plotH + 18);
        }
    }

    private double[] calcularTicksEscala(double escalaTopo, boolean compensarSubida) {
        if (!compensarSubida) {
            return new double[]{0, escalaTopo * 0.25, escalaTopo * 0.50, escalaTopo * 0.75, escalaTopo};
        }
        // Ticks progressivos inspirados na leitura 0 -> valores baixos/médios (100, 300...) -> topo (1000+)
        return new double[]{
                0.0,
                Math.round(escalaTopo * 0.10),
                Math.round(escalaTopo * 0.30),
                Math.round(escalaTopo * 0.60),
                escalaTopo
        };
    }

    private double calcularYFinanceiro(double valor, double escalaTopo, double plotH, boolean compensarSubida) {
        double vLimpo = Math.min(escalaTopo, Math.max(0.0, valor));
        double norm = escalaTopo > 0 ? (vLimpo / escalaTopo) : 0.0;
        if (compensarSubida) {
            // Compressão quadrática inversa suave: eleva valores 10%-30% (ex.: 100 e 300) mantendo o pico 1000 sem estourar
            norm = Math.pow(norm, 0.52);
        }
        double utilH = Math.max(20.0, plotH - 16.0);
        double y = padTop + plotH - 4.0 - (norm * utilH);
        return Math.max(padTop + 10.0, Math.min(padTop + plotH - 3.0, y));
    }

    private void desenharCurvaFluida(GraphicsContext g, double[] xs, double[] ys) {
        if (xs.length == 0) return;
        if (xs.length == 1) {
            g.strokeOval(xs[0] - 3.5, ys[0] - 3.5, 7, 7);
            return;
        }
        g.beginPath();
        g.moveTo(xs[0], ys[0]);
        for (int i = 0; i < xs.length - 1; i++) {
            double midX = (xs[i] + xs[i + 1]) / 2.0;
            g.bezierCurveTo(midX, ys[i], midX, ys[i + 1], xs[i + 1], ys[i + 1]);
        }
        g.stroke();
    }

    private String formatarCurtoMoeda(double valor) {
        if (valor >= 1000.0) return String.format(FormatadorSeguroUI.LOCALE_BR, "R$ %.1fk", valor / 1000.0);
        return String.format(FormatadorSeguroUI.LOCALE_BR, "R$ %.0f", valor);
    }
}
