package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.componentes.MapaGpsEventosCanvas;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.CardKanbanExterno;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.EventoMapaGps;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aba 6: Clima, Rios, Mapa Topográfico GPS & Estratégia Climatológica.
 * Organizada em 3 sub-abas sob demanda:
 * (6.1) Monitor Climático, Hidrológico, Seleção de Cidade/Bacia, Pendências e Série Sazonal;
 * (6.2) Mapa Topográfico GPS Interativo (relevo, zoom funcional e tela cheia) & Eventos com Previsão do Tempo;
 * (6.3) Estratégia Climatológica & Mapeamento de Demanda (Previsão 7 dias × Clientes × Eventos × Fornecedores).
 */
public class AbaMapaEventosGpsView extends VBox {

    private record CidadeAmazonica(String rotulo, String cidade, String uf, double lat, double lon, String baciaPadrao) {}

    private static final Map<String, CidadeAmazonica> CIDADES = new LinkedHashMap<>();

    static {
        registrar("Manaus / AM", "Manaus", "AM", -3.0950, -60.0250, "Rio Negro");
        registrar("Parintins / AM", "Parintins", "AM", -2.6283, -56.7358, "Rio Amazonas");
        registrar("Itacoatiara / AM", "Itacoatiara", "AM", -3.1431, -58.4442, "Rio Amazonas");
        registrar("Manacapuru / AM", "Manacapuru", "AM", -3.2997, -60.6206, "Rio Solimões");
        registrar("Tefé / AM", "Tefé", "AM", -3.3536, -64.7114, "Rio Solimões");
        registrar("Belém / PA", "Belém", "PA", -1.4558, -48.4902, "Baía de Guajará");
        registrar("Santarém / PA", "Santarém", "PA", -2.4431, -54.7083, "Rio Tapajós");
    }

    private static void registrar(String rotulo, String cidade, String uf, double lat, double lon, String bacia) {
        CIDADES.put(rotulo, new CidadeAmazonica(rotulo, cidade, uf, lat, lon, bacia));
    }

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabMonitorClima = new Tab("🌤️ Monitor Climático & Rios");
    private final Tab tabMapaGps = new Tab("🗺️ Mapa Relevo GPS & Eventos");
    private final Tab tabEstrategiaClima = new Tab("🌦️ Estratégia Climatológica & Demanda");

    // 6.1 Monitor Climático
    private final Label lblClimaCardValor = FormatadorSeguroUI.criarValorKpi("-- °C", "#B45309");
    private final Label lblClimaCardDesc = FormatadorSeguroUI.criarLabel("", 12, false, "#475569");
    private final Label lblRioCardValor = FormatadorSeguroUI.criarValorKpi("-- m", "#0369A1");
    private final Label lblRioCardDesc = FormatadorSeguroUI.criarLabel("", 12, false, "#475569");
    private final ComboBox<String> cbCidade = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbBacia = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final Label lblStatusMonitor = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private final TableView<Object[]> tabelaAnaliseSazonal = new TableView<>();

    // 6.2 Mapa GPS & Eventos
    private final MapaGpsEventosCanvas mapaCanvas = new MapaGpsEventosCanvas();
    private final ToggleButton tgTodos = new ToggleButton("Todos");
    private final ToggleButton tgPublicos = new ToggleButton("Públicos (MinC)");
    private final ToggleButton tgPrivados = new ToggleButton("Privados (Sympla)");
    private final VBox boxListaEventos = new VBox(6);
    private final Label lblEvTitulo = FormatadorSeguroUI.criarLabel("Selecione um evento no mapa ou na lista ao lado", 13, true, "#0F172A");
    private final VBox boxEvPrevisaoDestaque = new VBox(4);
    private final GridPane gradeEvMetadados = new GridPane();
    private final Button btnEvDetalhes = FormatadorSeguroUI.criarBotaoCartoon("⤢ Expandir Ficha Completa", "#0F172A", "#0F172A", "#FFFFFF");
    private final Button btnEvKanban = FormatadorSeguroUI.criarBotaoCartoon("📤 Enviar p/ Kanban", "#EFF6FF", "#93C5FD", "#1E40AF");
    private final Button btnEvEstrategiaIA = FormatadorSeguroUI.criarBotaoIA("💬 Estratégia p/ Evento", null);
    private final Button btnEvPrepararCompra = FormatadorSeguroUI.criarBotaoCartoon("📥 Preparar Compra na Bandeja", "#047857", "#065F46", "#FFFFFF");
    private final Label lblFeedbackMapa = FormatadorSeguroUI.criarLabel("", 11, true, "#047857");

    // 6.3 Estratégia Climatológica & Demanda
    private final FlowPane boxPrevisao7Dias = new FlowPane(10, 10);
    private final Canvas canvasMeteo7Dias = new Canvas(760, 210);
    private final VBox boxEstrategiaPilares = new VBox(10);
    private final Label lblStatusEstrategia = FormatadorSeguroUI.criarLabel("", 12, true, "#047857");

    private List<EventoMapaGps> eventosCache = new ArrayList<>();
    private List<ClimaDiario> previsoesCache = new ArrayList<>();
    private EventoMapaGps eventoSelecionado;

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaMapaEventosGpsView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();

        tabMonitorClima.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaMonitorClima()));
        tabMapaGps.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaMapaGps()));
        tabEstrategiaClima.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaEstrategiaClimatologica()));

        tabPane.getTabs().addAll(tabMonitorClima, tabMapaGps, tabEstrategiaClima);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(topo, tabPane);

        mapaCanvas.setAoSelecionarEvento(ev -> {
            selecionarEvento(ev);
            abrirModalDetalhesEvento(ev);
        });
        atualizarCardsMonitor(ponte.getGerenciadorTema().getSnapshotAtual());

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) {
                carregarSeNecessario();
            }
        });
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("🌤️", "Clima, Rios Amazônicos & Mapa Topográfico de Eventos", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Monitoramento ambiental em tempo real, relevo GPS interativo, previsão meteorológica nos eventos e estratégia climatológica.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Sincronizar Clima & Eventos", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        HBox topo = new HBox(10, boxTit, btnAtualizar);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    // ==================== 6.1 MONITOR CLIMÁTICO & HIDROLÓGICO ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaMonitorClima() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        GridPane cardsEnv = new GridPane();
        cardsEnv.setHgap(12);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        cardsEnv.getColumnConstraints().addAll(c1, c2);

        Button btnExpClima = FormatadorSeguroUI.criarBotaoIA("💬 Explicar Clima Hoje",
                () -> conectorIA.explicarClimaDeHoje(ponte.getGerenciadorTema().getSnapshotAtual()));
        VBox cardClima = new VBox(6,
                new HBox(8, FormatadorSeguroUI.criarLabel("☀️ Clima & Precipitação Atual (clique p/ expandir)", 13, true, "#92400E"), btnExpClima),
                lblClimaCardValor, lblClimaCardDesc);
        cardClima.setPadding(new Insets(12));
        cardClima.setCursor(Cursor.HAND);
        cardClima.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFBEB", "#FCD34D"));
        cardClima.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) abrirDetalhesClimaAtual();
        });

        Button btnExpRio = FormatadorSeguroUI.criarBotaoIA("💬 Impacto Fluvial", () -> {
            var s = ponte.getGerenciadorTema().getSnapshotAtual();
            conectorIA.perguntarAoCopiloto("Regime dos Rios",
                    "O nível do rio em " + s.bacia() + " está em " + s.nivelRioMetros() + " m (" + s.regimeRio().getRotulo()
                            + "). Quais os impactos comerciais para vendas e reposição com fornecedores?");
        });
        VBox cardRio = new VBox(6,
                new HBox(8, FormatadorSeguroUI.criarLabel("🌊 Nível Hidrológico & Bacia (clique p/ expandir)", 13, true, "#075985"), btnExpRio),
                lblRioCardValor, lblRioCardDesc);
        cardRio.setPadding(new Insets(12));
        cardRio.setCursor(Cursor.HAND);
        cardRio.setStyle(FormatadorSeguroUI.estiloCardCartoon("#F0F9FF", "#7DD3FC"));
        cardRio.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) abrirDetalhesRioAtual();
        });

        cardsEnv.add(cardClima, 0, 0);
        cardsEnv.add(cardRio, 1, 0);

        VBox cardControles = new VBox(8);
        cardControles.setPadding(new Insets(12));
        cardControles.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        cbCidade.getItems().setAll(CIDADES.keySet());
        cbCidade.getSelectionModel().selectFirst();
        cbBacia.getItems().setAll("Rio Negro", "Rio Amazonas", "Rio Solimões", "Baía de Guajará", "Rio Tapajós");
        cbBacia.getSelectionModel().selectFirst();
        cbCidade.setPrefWidth(200);
        cbBacia.setPrefWidth(175);

        cbCidade.setOnAction(e -> {
            CidadeAmazonica cid = CIDADES.get(cbCidade.getValue());
            if (cid != null) cbBacia.setValue(cid.baciaPadrao());
        });

        Button btnAplicarCidade = FormatadorSeguroUI.criarBotaoCartoon("Aplicar Região", "#0F172A", "#0F172A", "#FFFFFF");
        btnAplicarCidade.setOnAction(e -> {
            CidadeAmazonica cid = CIDADES.get(cbCidade.getValue());
            String bac = cbBacia.getValue() != null ? cbBacia.getValue() : "Rio Negro";
            if (cid != null) {
                ponte.aplicarCidadeManual(cid.cidade(), cid.uf(), cid.lat(), cid.lon(), bac);
                lblStatusMonitor.setText("✔ Região atualizada para " + cid.cidade() + "/" + cid.uf() + " (" + bac + ").");
                precisaRecarregar = true;
                carregarSeNecessario();
            }
        });

        Button btnGpsAuto = FormatadorSeguroUI.criarBotaoCartoon("📍 Detectar GPS", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnGpsAuto.setOnAction(e -> {
            lblStatusMonitor.setText("Detectando coordenadas...");
            ponte.atualizarClimaERioAtualAsync(true, snap -> {
                atualizarCardsMonitor(snap);
                lblStatusMonitor.setText("✔ GPS detectado: " + snap.cidade() + "/" + snap.uf());
            });
        });

        Button btnResolverPend = FormatadorSeguroUI.criarBotaoCartoon("🔧 Resolver Pendências Ambientais", "#047857", "#065F46", "#FFFFFF");
        btnResolverPend.setOnAction(e -> ponte.executarRotaAsync(RotaSistema.FATOR_EXTERNO_RESOLVER_PENDENCIAS, Map.of(), resp ->
                lblStatusMonitor.setText(resp.sucesso() ? "✔ " + FormatadorSeguroUI.tratarParaExibicao(resp.dados(), "Pendências verificadas!")
                        : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));

        FlowPane linhaControles = new FlowPane(8, 8,
                FormatadorSeguroUI.criarLabel("Cidade/Base:", 12, true, "#334155"), cbCidade,
                FormatadorSeguroUI.criarLabel("Bacia:", 12, true, "#334155"), cbBacia,
                btnAplicarCidade, btnGpsAuto, btnResolverPend);
        linhaControles.setAlignment(Pos.CENTER_LEFT);
        cardControles.getChildren().addAll(linhaControles, lblStatusMonitor);

        VBox cardTabela = new VBox(8);
        cardTabela.setPadding(new Insets(12));
        cardTabela.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaAnaliseSazonal, 260);
        tabelaAnaliseSazonal.getColumns().setAll(
                colObj("Data", 1, 95, false),
                colObj("Bloco", 2, 55, false),
                colObj("Cidade", 3, 105, false),
                colObj("Temp (°C)", 7, 75, false),
                colObj("Chuva (mm)", 8, 80, false),
                colObj("Nível Rio", 9, 85, false),
                colObj("Eventos", 10, 65, false),
                colObj("Notas Venda", 11, 80, false),
                colObj("Faturamento", 12, 105, true)
        );
        tabelaAnaliseSazonal.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setCursor(Cursor.HAND);
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty()) abrirDetalhesLinhaSazonal(row.getItem());
            });
            return row;
        });

        cardTabela.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📊 Histórico Integrado Clima × Nível do Rio × Eventos × Faturamento (clique na linha p/ detalhes)",
                        13, true, "#0F172A"),
                tabelaAnaliseSazonal);

        raiz.getChildren().addAll(cardsEnv, cardControles, cardTabela);
        return raiz;
    }

    // ==================== 6.2 MAPA TOPOGRÁFICO GPS & EVENTOS ====================

    private VBox construirSubAbaMapaGps() {
        VBox raiz = new VBox(10);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        VBox cardMapa = new VBox(6);
        cardMapa.setPadding(new Insets(10));
        cardMapa.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        FlowPane legenda = new FlowPane(14, 4,
                FormatadorSeguroUI.criarLabel("■ Loja Ativa", 11, true, "#0F172A"),
                FormatadorSeguroUI.criarLabel("● Evento Público (MinC)", 11, true, "#047857"),
                FormatadorSeguroUI.criarLabel("● Evento Privado (Sympla)", 11, true, "#1D4ED8"),
                FormatadorSeguroUI.criarLabel("〰 Curvas de Nível (20m–100m) & Bacia Hidrográfica", 11, false, "#475569"),
                FormatadorSeguroUI.criarLabel("🖱️ Role o mouse sobre o mapa para Zoom ou clique em '⛶ Expandir'", 11, true, "#1D4ED8")
        );
        cardMapa.getChildren().addAll(mapaCanvas, legenda);

        GridPane bottom = new GridPane();
        bottom.setHgap(12);
        ColumnConstraints b1 = new ColumnConstraints();
        b1.setPercentWidth(52);
        b1.setHgrow(Priority.ALWAYS);
        ColumnConstraints b2 = new ColumnConstraints();
        b2.setPercentWidth(48);
        b2.setHgrow(Priority.ALWAYS);
        bottom.getColumnConstraints().addAll(b1, b2);

        ToggleGroup grpEv = new ToggleGroup();
        tgTodos.setToggleGroup(grpEv);
        tgPublicos.setToggleGroup(grpEv);
        tgPrivados.setToggleGroup(grpEv);
        tgTodos.setSelected(true);
        Runnable filtrar = () -> {
            atualizarEstilosFiltroEventos();
            renderizarListaEventos();
        };
        tgTodos.setOnAction(e -> filtrar.run());
        tgPublicos.setOnAction(e -> filtrar.run());
        tgPrivados.setOnAction(e -> filtrar.run());
        atualizarEstilosFiltroEventos();

        VBox esq = new VBox(8,
                FormatadorSeguroUI.criarLabel("📋 Agenda Regional (clique no card do evento para abrir informações completas)", 12, true, "#0F172A"),
                new FlowPane(6, 6, tgTodos, tgPublicos, tgPrivados),
                boxListaEventos);
        esq.setPadding(new Insets(12));
        esq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        btnEvDetalhes.setOnAction(e -> abrirModalDetalhesEvento(eventoSelecionado));
        btnEvKanban.setOnAction(e -> enviarEventoParaKanban(eventoSelecionado));
        btnEvEstrategiaIA.setOnAction(e -> conectorIA.perguntarEstrategiaEventoGps(eventoSelecionado));
        btnEvPrepararCompra.setOnAction(e -> conectorIA.prepararCompraParaEventoNaBandeja(eventoSelecionado));
        btnEvDetalhes.setDisable(true);
        btnEvKanban.setDisable(true);
        btnEvEstrategiaIA.setDisable(true);
        btnEvPrepararCompra.setDisable(true);

        boxEvPrevisaoDestaque.setPadding(new Insets(8, 10, 8, 10));
        boxEvPrevisaoDestaque.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
        boxEvPrevisaoDestaque.getChildren().add(FormatadorSeguroUI.criarLabel(
                "🌦️ A previsão meteorológica exata do evento aparecerá aqui ao selecionar.", 12, false, "#1E40AF"));

        gradeEvMetadados.setHgap(8);
        gradeEvMetadados.setVgap(8);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            cc.setHgrow(Priority.ALWAYS);
            gradeEvMetadados.getColumnConstraints().add(cc);
        }

        VBox dir = new VBox(8,
                FormatadorSeguroUI.criarLabel("📍 Resumo do Evento & Previsão do Tempo", 13, true, "#0F172A"),
                lblEvTitulo,
                boxEvPrevisaoDestaque,
                gradeEvMetadados,
                new FlowPane(8, 6, btnEvDetalhes, btnEvKanban, btnEvEstrategiaIA, btnEvPrepararCompra),
                lblFeedbackMapa);
        dir.setPadding(new Insets(12));
        dir.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        bottom.add(esq, 0, 0);
        bottom.add(dir, 1, 0);

        raiz.getChildren().addAll(cardMapa, bottom);
        return raiz;
    }

    // ==================== 6.3 ESTRATÉGIA CLIMATOLÓGICA & DEMANDA ====================

    private VBox construirSubAbaEstrategiaClimatologica() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        Label titPrev = FormatadorSeguroUI.criarLabel("🌦️ Previsão Meteorológica dos Próximos 7 Dias na Região", 14, true, "#0F172A");
        Label subPrev = FormatadorSeguroUI.criarSubtitulo(
                "Cruze chuva, temperatura e regime do rio com oportunidades práticas para clientes, eventos e pedidos a fornecedores.", "#64748B");

        Button btnGerarIdeiasIA = FormatadorSeguroUI.criarBotaoIA("🤖 Gerar Ideias de Produtos Sob Demanda (IA)", () -> {
            var snap = ponte.getGerenciadorTema().getSnapshotAtual();
            StringBuilder prevResumo = new StringBuilder();
            for (int i = 0; i < Math.min(5, previsoesCache.size()); i++) {
                ClimaDiario cd = previsoesCache.get(i);
                prevResumo.append(String.format(FormatadorSeguroUI.LOCALE_BR, "%s (%.0f°C, %.1fmm); ",
                        FormatadorSeguroUI.formatarDataAmigavel(cd.data()), cd.temperaturaMedia(), cd.precipitacaoMm()));
            }
            conectorIA.perguntarAoCopiloto(
                    this,
                    "Ideias de Produtos Sob Demanda (Clima + Rio + Eventos)",
                    "Cidade: " + snap.cidade() + " | Rio: " + snap.regimeRio().getRotulo() + " | Eventos próximos: " + eventosCache.size(),
                    "Com base na previsão dos próximos dias (" + prevResumo + "), no regime do rio (" + snap.regimeRio().getRotulo()
                            + ") e nos " + eventosCache.size() + " eventos mapeados próximos à loja, sugira 5 produtos/combos sob demanda para vender perto dos eventos "
                            + "(ex: guarda-chuvas/capas em dias chuvosos, bebidas/gelo no calor) e indique quais comprar do fornecedor antecipadamente.");
        });

        HBox topoPrev = new HBox(10, new VBox(2, titPrev, subPrev), btnGerarIdeiasIA);
        HBox.setHgrow(topoPrev.getChildren().get(0), Priority.ALWAYS);
        topoPrev.setAlignment(Pos.CENTER_LEFT);

        VBox cardPrevisao = new VBox(10, topoPrev);
        cardPrevisao.setPadding(new Insets(12));
        cardPrevisao.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));

        VBox boxGraficoMeteo = new VBox(4);
        boxGraficoMeteo.setPadding(new Insets(8, 10, 8, 10));
        boxGraficoMeteo.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
        Label lTitGraf = FormatadorSeguroUI.criarLabel(
                "📈 Painel Especializado 7 Dias: Termometria Máx/Mín (#EA580C) • Chuva mm (#38BDF8) • Cota Fluvial Estimada (#0284C7)",
                11, true, "#0F172A");
        canvasMeteo7Dias.widthProperty().bind(cardPrevisao.widthProperty().subtract(48));
        canvasMeteo7Dias.widthProperty().addListener((o, a, b) -> desenharGraficoMeteo7Dias());
        boxGraficoMeteo.getChildren().addAll(lTitGraf, canvasMeteo7Dias);

        cardPrevisao.getChildren().addAll(boxGraficoMeteo, boxPrevisao7Dias, lblStatusEstrategia);

        VBox cardMatriz = new VBox(10,
                FormatadorSeguroUI.criarLabel("🧭 Matriz de Estratégia Climatológica (Cliente × Eventos × Fornecedores)", 13, true, "#0F172A"),
                boxEstrategiaPilares);
        cardMatriz.setPadding(new Insets(12));
        cardMatriz.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        raiz.getChildren().addAll(cardPrevisao, cardMatriz);
        return raiz;
    }

    // ==================== CICLO LAZY & DADOS ====================

    public void setAtiva(boolean ativa) {
        this.abaAtiva = ativa;
        if (ativa) {
            carregarSeNecessario();
        }
    }

    public void carregarSeNecessario() {
        if (!precisaRecarregar || carregando) return;
        precisaRecarregar = false;
        carregando = true;

        ponte.atualizarClimaERioAtualAsync(false, this::atualizarCardsMonitor);

        Thread.ofVirtual().start(() -> {
            try {
                Coordenada loja = ponte.getGeolocalizacaoService().obterCoordenadaLojaAtiva();
                List<ClimaDiario> previsoes = ponte.buscarPrevisaoProximosDias(7);
                List<EventoMapaGps> eventos = ponte.carregarEventosGpsOrdenados();
                List<Object[]> analiseSaz = ponte.consultarRelatorioAnaliseSazonalVendas();

                Platform.runLater(() -> {
                    this.previsoesCache = previsoes;
                    this.eventosCache = eventos;
                    mapaCanvas.definirDados(loja, eventos);
                    renderizarListaEventos();
                    if (eventoSelecionado == null && !eventos.isEmpty()) {
                        selecionarEvento(eventos.get(0));
                    } else if (eventoSelecionado != null) {
                        selecionarEvento(eventoSelecionado);
                    }
                    tabelaAnaliseSazonal.setItems(FXCollections.observableArrayList(analiseSaz));
                    renderizarPrevisao7Dias();
                    renderizarEstrategiaClimatologica();
                });
            } finally {
                carregando = false;
            }
        });
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
            atualizarCardsMonitor(snap);
            desenharGraficoMeteo7Dias();
            renderizarEstrategiaClimatologica();
        }
    }

    private void atualizarCardsMonitor(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        double temp = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;
        double chuva = snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;
        lblClimaCardValor.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C • %s", temp, snap.estado().getTitulo()));
        lblClimaCardDesc.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "Cidade: %s/%s • Chuva prevista/atual: %.1f mm", snap.cidade(), snap.uf(), chuva));

        lblRioCardValor.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m • %s",
                snap.nivelRioMetros(), snap.regimeRio().getRotulo()));
        lblRioCardDesc.setText("Bacia monitorada: " + snap.bacia());
    }

    private void atualizarEstilosFiltroEventos() {
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgTodos, tgTodos.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgPublicos, tgPublicos.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#047857", "#065F46", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgPrivados, tgPrivados.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#1D4ED8", "#1E40AF", "#FFFFFF");
    }

    private void renderizarListaEventos() {
        boxListaEventos.getChildren().clear();
        int exibidos = 0;
        for (EventoMapaGps ev : eventosCache) {
            if (tgPublicos.isSelected() && !ev.publico()) continue;
            if (tgPrivados.isSelected() && ev.publico()) continue;
            exibidos++;
            if (exibidos > 10) break;

            Label badge = FormatadorSeguroUI.criarBadgeCartoon(
                    ev.publico() ? "PÚBLICO" : "PRIVADO",
                    ev.publico() ? "#ECFDF5" : "#EFF6FF",
                    ev.publico() ? "#6EE7B7" : "#93C5FD",
                    ev.publico() ? "#065F46" : "#1E40AF");
            Label badgeClima = FormatadorSeguroUI.criarBadgeCartoon(
                    String.format(FormatadorSeguroUI.LOCALE_BR, "%.0f°C • %.1fmm", ev.tempPrevistaC(), ev.chuvaPrevistaMm()),
                    ev.chuvaPrevistaMm() >= 2.0 ? "#EFF6FF" : "#FFFBEB",
                    ev.chuvaPrevistaMm() >= 2.0 ? "#93C5FD" : "#FCD34D",
                    ev.chuvaPrevistaMm() >= 2.0 ? "#1E40AF" : "#92400E");
            Label tit = FormatadorSeguroUI.criarLabel(ev.nome(), 12, true, "#0F172A");
            Label sub = FormatadorSeguroUI.criarLabel(String.format("%s • %s • %s",
                    FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()), ev.localNome(), ev.dataHorario()),
                    11, false, "#475569");
            Label prev = FormatadorSeguroUI.criarLabel("Previsão: " + ev.previsaoTempo(), 11, true, "#1D4ED8");

            VBox info = new VBox(3, new FlowPane(6, 4, badge, badgeClima, tit), sub, prev);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnVer = FormatadorSeguroUI.criarBotaoCartoon("Detalhes", "#FFFFFF", "#CBD5E1", "#0F172A");
            btnVer.setOnAction(e -> {
                selecionarEvento(ev);
                mapaCanvas.focarEvento(ev);
                abrirModalDetalhesEvento(ev);
            });

            HBox row = new HBox(8, info, btnVer);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.setCursor(Cursor.HAND);
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
            row.setOnMouseClicked(e -> {
                if (!(e.getTarget() instanceof Button)) {
                    selecionarEvento(ev);
                    mapaCanvas.focarEvento(ev);
                    abrirModalDetalhesEvento(ev);
                }
            });
            boxListaEventos.getChildren().add(row);
        }
        if (exibidos == 0) {
            boxListaEventos.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum evento encontrado neste filtro.", 12, false, "#64748B"));
        }
    }

    private void selecionarEvento(EventoMapaGps ev) {
        this.eventoSelecionado = ev;
        if (ev == null) return;
        lblEvTitulo.setText(ev.nome() + " (" + ev.categoria() + ")");

        boxEvPrevisaoDestaque.getChildren().setAll(
                FormatadorSeguroUI.criarLabel("🌦️ Previsão do Tempo no Evento", 11, true, "#1E40AF"),
                FormatadorSeguroUI.criarLabel(ev.previsaoTempo(), 12, true, "#0F172A")
        );

        gradeEvMetadados.getChildren().clear();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Local / Bairro", ev.localNome() + " (" + ev.bairro() + ")");
        meta.put("Cidade / Distância", ev.cidadeUf() + " • " + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()));
        meta.put("Data & Horário", ev.dataHorario());
        meta.put("Impacto Comercial", ev.resumoOportunidade());

        int i = 0;
        for (var entry : meta.entrySet()) {
            gradeEvMetadados.add(
                    FormatadorSeguroUI.criarMiniCardInfo(entry.getKey(), entry.getValue(), "#F8FAFC", "#E2E8F0", "#0F172A"),
                    i % 2, i / 2);
            i++;
        }

        btnEvDetalhes.setDisable(false);
        btnEvKanban.setDisable(false);
        btnEvEstrategiaIA.setDisable(false);
        btnEvPrepararCompra.setDisable(false);
    }

    private void abrirModalDetalhesEvento(EventoMapaGps ev) {
        if (ev == null) return;
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        boolean chuvoso = ev.chuvaPrevistaMm() >= 2.0;
        boolean calor = ev.tempPrevistaC() >= 29.5;
        String mixRecomendado = chuvoso
                ? "Guarda-chuvas, capas impermeáveis, embalagens seladas, bebidas quentes e entregas protegidas."
                : (calor ? "Água mineral gelada, gelo, isotônicos, refrigerantes, protetor solar e bonés."
                         : "Kits conveniência, snacks rápidos, água e artigos sazonais de frente de caixa.");

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Evento Mapeado", ev.nome());
        campos.put("Fonte / Modalidade", ev.categoria() + " (" + ev.fonte() + ")");
        campos.put("Data & Horário", ev.dataHorario());
        campos.put("Local & Bairro", ev.localNome() + " • " + ev.bairro());
        campos.put("Cidade & Distância da Loja", ev.cidadeUf() + " • " + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()));
        if (ev.coordenada() != null) {
            campos.put("Coordenadas GPS (Lat / Lon)", String.format(Locale.US, "%.5f , %.5f",
                    ev.coordenada().latitude(), ev.coordenada().longitude()));
        }
        campos.put("Previsão Meteorológica Exata", ev.previsaoTempo());
        campos.put("Temperatura / Precipitação", String.format(FormatadorSeguroUI.LOCALE_BR,
                "%.1f °C • %.1f mm", ev.tempPrevistaC(), ev.chuvaPrevistaMm()));
        campos.put("Regime Hidrográfico Regional", snap.bacia() + " — " + snap.regimeRio().getRotulo()
                + String.format(FormatadorSeguroUI.LOCALE_BR, " (%.1f m)", snap.nivelRioMetros()));
        campos.put("Mix Recomendado pela Engenharia Sazonal", mixRecomendado);
        campos.put("Oportunidade Comercial", ev.resumoOportunidade());
        if (ev.url() != null && !ev.url().isBlank()) {
            campos.put("Link de Referência", ev.url());
        }

        // Painel Visual Micro-Clima & Alinhamento Hidrológico no Modal
        Canvas gaugeEv = new Canvas(560, 88);
        GraphicsContext gg = gaugeEv.getGraphicsContext2D();
        gg.setFill(Color.web(chuvoso ? "#EFF6FF" : (calor ? "#FFF7ED" : "#ECFDF5")));
        gg.fillRoundRect(0, 0, 560, 88, 10, 10);
        gg.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gg.setFill(Color.web("#EA580C"));
        gg.fillText(String.format(FormatadorSeguroUI.LOCALE_BR, "🌡️ Temp. Evento: %.1f °C", ev.tempPrevistaC()), 16, 28);
        gg.setFill(Color.web("#E2E8F0"));
        gg.fillRoundRect(16, 36, 150, 10, 5, 5);
        gg.setFill(Color.web("#EA580C"));
        gg.fillRoundRect(16, 36, Math.min(150, Math.max(15, (ev.tempPrevistaC() / 42.0) * 150)), 10, 5, 5);

        gg.setFill(Color.web("#0284C7"));
        gg.fillText(String.format(FormatadorSeguroUI.LOCALE_BR, "🌧️ Chuva Prevista: %.1f mm", ev.chuvaPrevistaMm()), 196, 28);
        gg.setFill(Color.web("#E2E8F0"));
        gg.fillRoundRect(196, 36, 150, 10, 5, 5);
        gg.setFill(Color.web("#0284C7"));
        gg.fillRoundRect(196, 36, Math.min(150, Math.max(10, (ev.chuvaPrevistaMm() / 25.0) * 150)), 10, 5, 5);

        gg.setFill(Color.web("#047857"));
        gg.fillText(String.format(FormatadorSeguroUI.LOCALE_BR, "🌊 Rio: %.1f m (%s)", snap.nivelRioMetros(), snap.regimeRio().getRotulo()), 376, 28);
        gg.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        gg.setFill(Color.web("#334155"));
        gg.fillText("Sugestão rápida: " + (mixRecomendado.length() > 82 ? mixRecomendado.substring(0, 79) + "..." : mixRecomendado), 16, 72);

        Button btnPushKanban = FormatadorSeguroUI.criarBotaoCartoon("📤 Enviar ao Kanban de Demanda", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnPushKanban.setOnAction(e -> enviarEventoParaKanban(ev));

        Button btnPrepCompra = FormatadorSeguroUI.criarBotaoCartoon("📥 Sugerir Compra na Bandeja (IA)", "#ECFDF5", "#6EE7B7", "#065F46");
        btnPrepCompra.setOnAction(e -> conectorIA.prepararCompraParaEventoNaBandeja(ev));

        VBox boxExtra = new VBox(8, gaugeEv, new FlowPane(8, 8, btnPushKanban, btnPrepCompra));
        DialogoCorporativoModal.exibirCardDetalhes(
                this,
                ev.publico() ? "🎭" : "🎟️",
                "Ficha Completa — " + ev.nome(),
                ev.categoria() + " • " + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()),
                ev.publico() ? "#047857" : "#1D4ED8",
                campos,
                boxExtra,
                null,
                "🗂️ Abrir Kanban de Demanda",
                () -> ponte.navegarParaAba("KANBAN"),
                () -> conectorIA.explicarComContextoOrigem(
                        this,
                        "Clima, Rios & Mapa — Evento GPS",
                        ev.nome() + " (" + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()) + ")",
                        campos,
                        "Crie uma estratégia comercial completa para o evento '" + ev.nome()
                                + "' considerando previsão (" + ev.previsaoTempo() + "), proximidade da loja, regime do rio e compras com fornecedores.")
        );
    }

    private void enviarEventoParaKanban(EventoMapaGps ev) {
        if (ev == null) return;
        String prodSug = ev.chuvaPrevistaMm() >= 2.0
                ? "Guarda-chuva / Capa Impermeável / Café Quente"
                : (ev.tempPrevistaC() >= 29.5 ? "Água Mineral / Gelo / Refrigerante / Boné" : "Kits Conveniência & Lanches");
        String strat = ev.resumoOportunidade();
        ponte.adicionarCardKanban(new CardKanbanExterno(
                "EV-" + ev.id(),
                "Evento: " + ev.nome(),
                ev.publico() ? "Evento Público + Clima" : "Evento Privado + Clima",
                ev.dataHorario() + " • " + ev.previsaoTempo() + " (" + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()) + ")",
                prodSug,
                "Verificar melhor fornecedor no Plano de Abastecimento",
                strat,
                1
        ));
        lblFeedbackMapa.setText("✔ Evento '" + ev.nome() + "' enviado para o Kanban de Planejamento de Demanda!");
    }

    private void desenharGraficoMeteo7Dias() {
        double w = canvasMeteo7Dias.getWidth();
        double h = canvasMeteo7Dias.getHeight();
        if (w < 80 || h < 60) return;

        GraphicsContext g = canvasMeteo7Dias.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        boolean dark = ponte.getGerenciadorTema().isModoEscuro();
        g.setFill(Color.web(dark ? "#0F172A" : "#FFFFFF"));
        g.fillRoundRect(0, 0, w, h, 8, 8);

        if (previsoesCache == null || previsoesCache.isEmpty()) {
            g.setFill(Color.web("#64748B"));
            g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("Carregando curva meteorológica e hidrológica de 7 dias...", w / 2, h / 2);
            return;
        }

        int n = Math.min(7, previsoesCache.size());
        double padL = 44, padR = 54, padT = 22, padB = 34;
        double plotW = w - padL - padR, plotH = h - padT - padB;
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        double cotaBase = snap.nivelRioMetros();

        double minT = 18.0, maxT = 40.0, maxChuva = 25.0;
        for (int i = 0; i < n; i++) {
            ClimaDiario d = previsoesCache.get(i);
            minT = Math.min(minT, d.temperaturaMin() - 2);
            maxT = Math.max(maxT, d.temperaturaMax() + 2);
            maxChuva = Math.max(maxChuva, d.precipitacaoMm() * 1.25);
        }

        // Grade
        g.setStroke(Color.web(dark ? "#334155" : "#E2E8F0"));
        g.setLineWidth(1.0);
        g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
        for (int k = 0; k <= 4; k++) {
            double y = padT + (k / 4.0) * plotH;
            g.strokeLine(padL, y, padL + plotW, y);
            double tv = maxT - (k / 4.0) * (maxT - minT);
            g.setFill(Color.web("#EA580C"));
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(String.format("%.0f°C", tv), padL - 6, y + 3);

            double cv = maxChuva * (1.0 - k / 4.0);
            g.setFill(Color.web("#0284C7"));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(String.format("%.0fmm", cv), padL + plotW + 6, y + 3);
        }

        double[] xs = new double[n];
        double[] ysMax = new double[n];
        double[] ysMin = new double[n];
        double[] ysRio = new double[n];
        double acumulado = 0;
        double slot = plotW / Math.max(1, n);

        for (int i = 0; i < n; i++) {
            ClimaDiario d = previsoesCache.get(i);
            xs[i] = padL + i * slot + slot / 2.0;

            // Barra de Precipitação (mm)
            double barH = Math.max(2.0, (d.precipitacaoMm() / maxChuva) * plotH);
            double bw = Math.min(34, slot * 0.45);
            g.setFill(Color.rgb(56, 189, 248, 0.48));
            g.fillRoundRect(xs[i] - bw / 2.0, padT + plotH - barH, bw, barH, 5, 5);
            if (d.precipitacaoMm() > 0.2) {
                g.setFill(Color.web(dark ? "#7DD3FC" : "#0369A1"));
                g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(String.format(Locale.US, "%.0fmm", d.precipitacaoMm()), xs[i], Math.max(padT + 10, padT + plotH - barH - 4));
            }

            ysMax[i] = padT + plotH - ((d.temperaturaMax() - minT) / Math.max(1.0, maxT - minT)) * plotH;
            ysMin[i] = padT + plotH - ((d.temperaturaMin() - minT) / Math.max(1.0, maxT - minT)) * plotH;
            acumulado += (d.precipitacaoMm() - 2.0) * 0.03;
            double cotaDia = Math.max(2.0, cotaBase + acumulado);
            ysRio[i] = padT + plotH - Math.min(0.85, Math.max(0.15, cotaDia / Math.max(12.0, cotaBase * 1.6))) * plotH;

            g.setFill(Color.web(dark ? "#CBD5E1" : "#334155"));
            g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.format("%02d/%02d", d.data().getDayOfMonth(), d.data().getMonthValue()), xs[i], padT + plotH + 17);
        }

        // Linha Estimada do Rio (#0284C7)
        g.setStroke(Color.web("#0284C7"));
        g.setLineWidth(2.0);
        g.setLineDashes(4, 4);
        for (int i = 0; i < n - 1; i++) g.strokeLine(xs[i], ysRio[i], xs[i + 1], ysRio[i + 1]);
        g.setLineDashes(null);

        // Linha Temp Máx (#EA580C) e Temp Mín (#F59E0B)
        g.setStroke(Color.web("#EA580C"));
        g.setLineWidth(2.6);
        for (int i = 0; i < n - 1; i++) g.strokeLine(xs[i], ysMax[i], xs[i + 1], ysMax[i + 1]);
        g.setStroke(Color.web("#F59E0B"));
        g.setLineWidth(1.8);
        for (int i = 0; i < n - 1; i++) g.strokeLine(xs[i], ysMin[i], xs[i + 1], ysMin[i + 1]);

        for (int i = 0; i < n; i++) {
            ClimaDiario d = previsoesCache.get(i);
            g.setFill(Color.web("#EA580C"));
            g.fillOval(xs[i] - 3.5, ysMax[i] - 3.5, 7, 7);
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.format("%.0f°", d.temperaturaMax()), xs[i], Math.max(padT + 10, ysMax[i] - 7));
        }

        // Legenda topo compacta
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.setFill(Color.web("#EA580C"));
        g.fillText("━━ Temp Máx/Mín (°C)", padL + 6, 14);
        g.setFill(Color.web("#0284C7"));
        g.fillText("▬ Chuva (mm)   ┅┅ Tendência Rio (m)", padL + 165, 14);
    }

    private void renderizarPrevisao7Dias() {
        desenharGraficoMeteo7Dias();
        boxPrevisao7Dias.getChildren().clear();
        if (previsoesCache == null || previsoesCache.isEmpty()) {
            boxPrevisao7Dias.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Sem dados de previsão estendida disponíveis no momento.", 12, false, "#64748B"));
            return;
        }
        for (ClimaDiario dia : previsoesCache) {
            boolean chuvoso = dia.precipitacaoMm() >= 2.0;
            boolean calor = dia.temperaturaMedia() >= 29.5;
            String icone = chuvoso ? "🌧️" : (calor ? "☀️" : "⛅");
            String acao = chuvoso ? "Chuva prevista: priorize guarda-chuvas e capas"
                    : (calor ? "Calor intenso: reforce bebidas geladas" : "Movimento padrão de balcão");

            VBox card = new VBox(4);
            card.setPrefWidth(175);
            card.setMinWidth(165);
            card.setPadding(new Insets(9, 10, 9, 10));
            card.setCursor(Cursor.HAND);
            card.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(
                    chuvoso ? "#EFF6FF" : (calor ? "#FFFBEB" : "#F8FAFC"),
                    chuvoso ? "#93C5FD" : (calor ? "#FCD34D" : "#CBD5E1")));

            Label lData = FormatadorSeguroUI.criarLabel(icone + " " + FormatadorSeguroUI.formatarDataAmigavel(dia.data()), 12, true, "#0F172A");
            Label lTemp = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                    "%.1f°C (Mín %.0f° / Máx %.0f°)", dia.temperaturaMedia(), dia.temperaturaMin(), dia.temperaturaMax()), 11, true, "#1E293B");
            Label lChuva = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                    "Chuva: %.1f mm • Vento %.0f km/h", dia.precipitacaoMm(), dia.ventoMaxKmh()), 11, false, "#475569");
            Label lAcao = FormatadorSeguroUI.criarLabel(acao, 11, true, chuvoso ? "#1D4ED8" : (calor ? "#B45309" : "#334155"));

            card.getChildren().addAll(lData, lTemp, lChuva, lAcao);
            card.setOnMouseClicked(e -> {
                Map<String, String> campos = new LinkedHashMap<>();
                campos.put("Data Prevista", FormatadorSeguroUI.formatarDataAmigavel(dia.data()));
                campos.put("Temperatura Média", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C", dia.temperaturaMedia()));
                campos.put("Mínima / Máxima", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C / %.1f °C", dia.temperaturaMin(), dia.temperaturaMax()));
                campos.put("Precipitação Estimada", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f mm", dia.precipitacaoMm()));
                campos.put("Vento Máximo", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f km/h", dia.ventoMaxKmh()));
                campos.put("Estratégia Recomendada", acao);
                DialogoCorporativoModal.exibirCardDetalhes(
                        this, icone, "Previsão & Estratégia — " + FormatadorSeguroUI.formatarDataAmigavel(dia.data()),
                        chuvoso ? "Frente Chuvosa" : (calor ? "Pico Térmico" : "Estabilidade"),
                        chuvoso ? "#1D4ED8" : (calor ? "#D97706" : "#047857"),
                        campos, null, null,
                        () -> conectorIA.perguntarAoCopiloto(
                                this,
                                "Planejamento para " + FormatadorSeguroUI.formatarDataAmigavel(dia.data()),
                                acao,
                                "Para o dia " + FormatadorSeguroUI.formatarDataAmigavel(dia.data())
                                        + " a previsão indica " + String.format(Locale.US, "%.1f°C e %.1f mm", dia.temperaturaMedia(), dia.precipitacaoMm())
                                        + ". Quais itens devo colocar em destaque para meus clientes e quanto reforçar na compra?")
                );
            });
            boxPrevisao7Dias.getChildren().add(card);
        }
    }

    private void renderizarEstrategiaClimatologica() {
        boxEstrategiaPilares.getChildren().clear();
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        long diasChuvosos = previsoesCache.stream().filter(d -> d.precipitacaoMm() >= 2.0).count();
        EventoMapaGps evProx = eventosCache.isEmpty() ? null : eventosCache.get(0);

        String resumoCli = (diasChuvosos >= 2 ? "Sinal de chuva em " + diasChuvosos + " dia(s) da semana: destaque guarda-chuvas, capas, calçados impermeáveis e entregas."
                : "Semana com predominância de sol/calor amazônico: priorize bebidas geladas, protetor, gelo e snacks rápidos.");
        String resumoEv = evProx != null
                ? "Evento mais próximo: " + evProx.nome() + " (" + FormatadorSeguroUI.formatarDistanciaKm(evProx.distanciaKm()) + ") — " + evProx.previsaoTempo()
                : "Sincronize os eventos no mapa para cruzar agenda pública e privada.";
        String resumoForn = "Rio em " + snap.regimeRio().getRotulo() + " (" + String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m", snap.nivelRioMetros()) + "): "
                + snap.regimeRio().getOrientacaoOperacional();

        boxEstrategiaPilares.getChildren().addAll(
                criarCardPilarEstrategia("👤 Estratégia para Clientes (Vendas Sob Demanda)", resumoCli, "#FFFBEB", "#FCD34D", "#B45309",
                        "📤 Criar Card Cliente no Kanban", () -> {
                            ponte.adicionarCardKanban(new CardKanbanExterno(
                                    "CLIMA-CLI-" + diasChuvosos,
                                    diasChuvosos >= 2 ? "Campanha Chuva nos Pontos de Fluxo" : "Campanha Calor & Hidratação",
                                    "Estratégia Climatológica",
                                    snap.cidade() + " • " + diasChuvosos + " dia(s) com chuva prevista",
                                    diasChuvosos >= 2 ? "Guarda-chuva compacto / Capa / Bebida Quente" : "Água / Gelo / Refrigerante / Combo Verão",
                                    "Repositor Principal",
                                    resumoCli,
                                    1
                            ));
                            lblStatusEstrategia.setText("✔ Estratégia de Cliente adicionada ao Kanban de Demanda!");
                        }),
                criarCardPilarEstrategia("🎪 Cruzamento Eventos × Previsão Meteorológica", resumoEv, "#EFF6FF", "#93C5FD", "#1D4ED8",
                        "📤 Enviar Evento-Chave p/ Kanban", () -> {
                            if (evProx != null) enviarEventoParaKanban(evProx);
                            lblStatusEstrategia.setText(evProx != null ? "✔ Evento '" + evProx.nome() + "' enviado para o Kanban!" : "Nenhum evento em cache ainda.");
                        }),
                criarCardPilarEstrategia("🚚 Compras & Logística com Fornecedores (Nível dos Rios)", resumoForn, "#ECFDF5", "#6EE7B7", "#047857",
                        "📥 Criar Card Fornecedor no Kanban", () -> {
                            ponte.adicionarCardKanban(new CardKanbanExterno(
                                    "RIO-FORN-" + snap.regimeRio().name(),
                                    "Compra Preventiva (" + snap.regimeRio().getRotulo() + ")",
                                    "Hidrologia & Fornecedores",
                                    snap.bacia() + " • Cota " + String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m", snap.nivelRioMetros()),
                                    "Itens de maior giro e proteção logística",
                                    "Fornecedores locais prioritários",
                                    resumoForn,
                                    2
                            ));
                            lblStatusEstrategia.setText("✔ Card de Fornecedor adicionado à coluna Abastecimento do Kanban!");
                        })
        );
    }

    private VBox criarCardPilarEstrategia(String titulo, String desc, String bgHex, String borderHex, String accentHex,
                                          String rotuloBotao, Runnable acaoBotao) {
        Label lTit = FormatadorSeguroUI.criarLabel(titulo, 13, true, accentHex);
        Label lDesc = FormatadorSeguroUI.criarLabel(desc, 12, false, "#334155");
        Button btn = FormatadorSeguroUI.criarBotaoCartoon(rotuloBotao, "#FFFFFF", borderHex, accentHex);
        btn.setOnAction(e -> acaoBotao.run());
        HBox row = new HBox(12, new VBox(4, lTit, lDesc), btn);
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(row);
        box.setPadding(new Insets(10, 12, 10, 12));
        box.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(bgHex, borderHex));
        return box;
    }

    private void abrirDetalhesClimaAtual() {
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Cidade Monitorada", snap.cidade() + "/" + snap.uf());
        campos.put("Condição Climática", snap.estado().getTitulo());
        campos.put("Temperatura Média", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C",
                snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5));
        campos.put("Precipitação Atual", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f mm",
                snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0));
        campos.put("Impacto no Balcão", lblClimaCardDesc.getText());
        DialogoCorporativoModal.exibirCardDetalhes(
                this, "☀️", "Condição Climática Atual — " + snap.cidade(),
                snap.estado().getTitulo(), "#EA580C", campos, null, null,
                "🌦️ Ver Estratégia 7 Dias", () -> tabPane.getSelectionModel().select(tabEstrategiaClima),
                () -> conectorIA.explicarClimaDeHoje(snap));
    }

    private void abrirDetalhesRioAtual() {
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Bacia Hidrográfica", snap.bacia());
        campos.put("Cota Medida", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f metros", snap.nivelRioMetros()));
        campos.put("Regime Hidrológico", snap.regimeRio().getRotulo());
        campos.put("Orientação Logística", snap.regimeRio().getOrientacaoOperacional());
        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🌊", "Monitor Hidrológico — " + snap.bacia(),
                snap.regimeRio().getRotulo(), "#0284C7", campos, null, null,
                "🗺️ Abrir Planejamento Kanban", () -> ponte.navegarParaAba("KANBAN"),
                () -> conectorIA.explicarComContextoOrigem(this, "Clima & Rios — Regime Hidrológico",
                        snap.bacia() + " (" + snap.regimeRio().getRotulo() + ")", campos,
                        "O nível em " + snap.bacia() + " está em " + snap.nivelRioMetros() + " m (" + snap.regimeRio().getRotulo() + "). Como ajustar compras e entregas?"));
    }

    private void abrirDetalhesLinhaSazonal(Object[] arr) {
        if (arr == null) return;
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Data", FormatadorSeguroUI.tratarParaExibicao(arr.length > 1 ? arr[1] : null, "-"));
        campos.put("Bloco", FormatadorSeguroUI.tratarParaExibicao(arr.length > 2 ? arr[2] : null, "-"));
        campos.put("Cidade", FormatadorSeguroUI.tratarParaExibicao(arr.length > 3 ? arr[3] : null, "-"));
        campos.put("Temperatura", FormatadorSeguroUI.tratarParaExibicao(arr.length > 7 ? arr[7] : null, "-") + " °C");
        campos.put("Chuva", FormatadorSeguroUI.tratarParaExibicao(arr.length > 8 ? arr[8] : null, "-") + " mm");
        campos.put("Nível / Vazão Rio", FormatadorSeguroUI.tratarParaExibicao(arr.length > 9 ? arr[9] : null, "-"));
        campos.put("Eventos na Região", FormatadorSeguroUI.tratarParaExibicao(arr.length > 10 ? arr[10] : null, "0"));
        campos.put("Notas de Venda", FormatadorSeguroUI.tratarParaExibicao(arr.length > 11 ? arr[11] : null, "0"));
        BigDecimal fat = BigDecimal.ZERO;
        if (arr.length > 12 && arr[12] instanceof BigDecimal b) fat = b;
        else if (arr.length > 12 && arr[12] instanceof Number n) fat = BigDecimal.valueOf(n.doubleValue());
        campos.put("Faturamento do Dia", FormatadorSeguroUI.formatarMoeda(fat));

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "📅", "Consolidado Ambiental & Vendas (" + campos.get("Data") + ")",
                campos.get("Cidade"), "#0F172A", campos, null, null,
                () -> conectorIA.perguntarAoCopiloto("Análise Diária Sazonal",
                        "No dia " + campos.get("Data") + " em " + campos.get("Cidade")
                                + ", temperatura " + campos.get("Temperatura") + ", chuva " + campos.get("Chuva")
                                + ", faturamento " + campos.get("Faturamento do Dia")
                                + ". O que explica esse desempenho e como repetir ou melhorar em dias semelhantes?"));
    }

    private TableColumn<Object[], String> colObj(String tit, int idx, double w, boolean moeda) {
        TableColumn<Object[], String> col = new TableColumn<>(tit);
        col.setPrefWidth(w);
        col.setCellValueFactory(c -> {
            Object[] arr = c.getValue();
            Object v = (arr != null && idx < arr.length) ? arr[idx] : null;
            if (moeda) {
                BigDecimal bd = BigDecimal.ZERO;
                if (v instanceof BigDecimal b) bd = b;
                else if (v instanceof Number n) bd = BigDecimal.valueOf(n.doubleValue());
                return new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(bd));
            }
            return new SimpleStringProperty(FormatadorSeguroUI.tratarParaExibicao(v, "-"));
        });
        return col;
    }
}
