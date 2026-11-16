package br.com.senac.view.javafx.abas;

import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.service.AnaliseImpactoExternoService;
import br.com.senac.view.javafx.componentes.CarrosselImagensProduto;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.componentes.GrafoFocoMapaMentalCanvas;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ConsolidadoEstoque;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.PontoDiarioSazonal;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ScoreFornecedorProduto;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aba 5: Estatística Preditiva, Flutuações & Padrões de Demanda.
 * Gera Cards individuais por produto com suas causas de comportamento (avisando quando houver amostra insuficiente),
 * Cards clicáveis em todos os padrões de demanda e perfis de consumo, e Diagrama Analítico com Tela Cheia e Expansão Procedural.
 */
public class AbaEstatisticaAvancadaView extends VBox {

    private record LinhaCausaFlutuacao(
            String fator,
            String condicaoComparada,
            String variacaoImpacto,
            String amostra,
            String interpretacao
    ) {}

    private record LinhaPadraoDemanda(
            String segmento,
            String periodoOuGatilho,
            String volumeNotas,
            String faturamentoTotal,
            String destaque
    ) {}

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabPreditivos = new Tab("🔮 Avisos Preditivos & Insights");
    private final Tab tabCausas = new Tab("🌦️ Causas das Flutuações & Produtos");
    private final Tab tabDemanda = new Tab("🗺️ Padrões de Demanda & Perfis");
    private final Tab tabGrafo = new Tab("🕸️ Diagrama Analítico");

    private final Label lblStatusStat = FormatadorSeguroUI.criarBadgeCartoon(
            "Sob demanda", "#F1F5F9", "#CBD5E1", "#475569");

    // 5.1 Avisos Preditivos
    private final ComboBox<String> cbHorizonteCompra = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final VBox boxAvisosCompra = new VBox(8);
    private final VBox boxRankingFornecedoresStat = new VBox(8);
    private final VBox boxAvisosVenda = new VBox(8);
    private final VBox boxAvisosPerda = new VBox(8);

    // 5.2 Causas e Motivos das Flutuações + Cards de Produtos
    private final ComboBox<String> cbProdutoFlutuacao = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final VBox boxDiagProdutoEstruturado = new VBox(8);
    private final FlowPane flowCardsProdutosCausas = new FlowPane(10, 10);
    private final TableView<LinhaCausaFlutuacao> tabelaCausasFlutuacao = new TableView<>();

    // 5.3 Padrões de Demanda
    private final ComboBox<String> cbMesDemanda = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TableView<LinhaPadraoDemanda> tabelaPadroesDemanda = new TableView<>();
    private final VBox boxPerfilClientes = new VBox(8);

    // 5.4 Diagrama Analítico
    private final GrafoFocoMapaMentalCanvas grafoCorporativo;

    private List<Produto> produtosCache = new ArrayList<>();
    private List<Nota> notasCache = new ArrayList<>();
    private List<ScoreFornecedorProduto> scoresFornecedorCache = new ArrayList<>();
    private ConsolidadoEstoque consCache;
    private String resumoDiagnosticoAtual = "";

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaEstatisticaAvancadaView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;
        this.grafoCorporativo = new GrafoFocoMapaMentalCanvas(ponte, conectorIA);

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();

        tabPreditivos.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaPreditivos()));
        tabCausas.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaCausas()));
        tabDemanda.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaDemanda()));
        tabGrafo.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaGrafo()));

        tabPane.getTabs().addAll(tabPreditivos, tabCausas, tabDemanda, tabGrafo);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(topo, tabPane);

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) {
                carregarSeNecessario();
            }
        });
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("📊", "Estatística Preditiva, Causas por Produto & Demanda", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Diagnóstico causal por produto (com alerta de amostra insuficiente), perfis de consumo e diagrama analítico expansível.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Atualizar", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        Button btnRecalcular = FormatadorSeguroUI.criarBotaoCartoon("⚙️ Recalcular Fatores", "#0F172A", "#0F172A", "#FFFFFF");
        btnRecalcular.setOnAction(e -> recalcularFatoresAsync());

        HBox ac = new HBox(8, lblStatusStat, btnAtualizar, btnRecalcular);
        ac.setAlignment(Pos.CENTER_RIGHT);

        HBox topo = new HBox(10, boxTit, ac);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    // ==================== 5.1 AVISOS PREDITIVOS ====================

    private VBox construirSubAbaPreditivos() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        cbHorizonteCompra.getItems().setAll("Horizonte: 15 dias", "Horizonte: 30 dias", "Horizonte: 60 dias");
        cbHorizonteCompra.getSelectionModel().select(1);
        cbHorizonteCompra.setPrefWidth(185);
        cbHorizonteCompra.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        VBox cardCompra = criarSecaoPreditiva(
                "🛒 Radar Preditivo de Compra & Índice de Vantagem (Ambientado no Clima — Clique no Card p/ Gráfico & Estratégia)",
                "Fileira horizontal com Índice de Vantagem (0–100%), melhor fornecedor pontuado, projeção de demanda e ligação ao Kanban.",
                cbHorizonteCompra, boxAvisosCompra, "#F8FCFF", "#93C5FD");

        VBox cardFornecedores = criarSecaoPreditiva(
                "🏆 Comparativo de Fornecedores por Mesmo Produto (Score 0–100 pts)",
                "Avaliação combinada de custo médio de entrada, economia comparativa e taxa de avaria dentro da validade.",
                null, boxRankingFornecedoresStat, "#FFFFFF", "#CBD5E1");

        VBox cardVenda = criarSecaoPreditiva(
                "📈 Avisos Preditivos de Venda & Insights Estratégicos",
                "Produtos com maior tração sazonal e oportunidades de combo.",
                null, boxAvisosVenda, "#FFFFFF", "#CBD5E1");

        VBox cardPerda = criarSecaoPreditiva(
                "⚠️ Avisos Preditivos de Perda & Causas Raiz",
                "Riscos de vencimento iminente e avarias prematuras.",
                null, boxAvisosPerda, "#FFFBEB", "#FCD34D");

        raiz.getChildren().addAll(cardCompra, cardFornecedores, cardVenda, cardPerda);
        return raiz;
    }

    private VBox criarSecaoPreditiva(String titulo, String subtitulo, ComboBox<String> filtroOpcional,
                                     VBox conteudo, String bg, String borda) {
        Label lTit = FormatadorSeguroUI.criarLabel(titulo, 13, true, "#0F172A");
        Label lSub = FormatadorSeguroUI.criarLabel(subtitulo, 11, false, "#64748B");
        VBox txts = new VBox(2, lTit, lSub);
        HBox.setHgrow(txts, Priority.ALWAYS);

        HBox topo = filtroOpcional != null ? new HBox(10, txts, filtroOpcional) : new HBox(10, txts);
        topo.setAlignment(Pos.CENTER_LEFT);

        conteudo.setFillWidth(true);
        VBox card = new VBox(8, topo, conteudo);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(FormatadorSeguroUI.estiloCardCartoon(bg, borda));
        return card;
    }

    // ==================== 5.2 CAUSAS DAS FLUTUAÇÕES & CARDS DE PRODUTOS ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaCausas() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        // 1. Diagnóstico do Produto Selecionado em formato estruturado
        VBox cardProd = new VBox(8);
        cardProd.setPadding(new Insets(12, 14, 12, 14));
        cardProd.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        cbProdutoFlutuacao.setPrefWidth(270);
        cbProdutoFlutuacao.setOnAction(e -> diagnosticarProdutoSelecionadoAsync());
        Button btnExplicarCausasIA = FormatadorSeguroUI.criarBotaoIA("💬 Explicar Causas c/ IA", () ->
                conectorIA.perguntarAoCopiloto(
                        this,
                        "Causas das Flutuações — " + cbProdutoFlutuacao.getValue(),
                        resumoDiagnosticoAtual,
                        "Com base nos dados (" + resumoDiagnosticoAtual
                                + "), explique os motivos das oscilações de demanda e perda deste produto."));

        HBox barProd = new HBox(10,
                FormatadorSeguroUI.criarLabel("Produto em Foco:", 12, true, "#334155"),
                cbProdutoFlutuacao, btnExplicarCausasIA);
        barProd.setAlignment(Pos.CENTER_LEFT);
        cardProd.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("🌦️ Elasticidade por Produto e Diagnóstico Climático/Sazonal", 13, true, "#0F172A"),
                barProd, boxDiagProdutoEstruturado);

        // 2. Cards Visuais por Produto e Suas Causas de Comportamento (c/ aviso de dados insuficientes)
        VBox cardGaleriaProd = new VBox(8);
        cardGaleriaProd.setPadding(new Insets(12, 14, 12, 14));
        cardGaleriaProd.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        cardGaleriaProd.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📦 Cards de Produtos & Causas de Comportamento (Clique em qualquer card para abrir Ficha e Fotos)", 13, true, "#0F172A"),
                flowCardsProdutosCausas);

        // 3. Matriz Consolidada de Causas
        VBox cardTab = new VBox(8);
        cardTab.setPadding(new Insets(12, 14, 12, 14));
        cardTab.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaCausasFlutuacao, 260);
        TableColumn<LinhaCausaFlutuacao, String> cFat = new TableColumn<>("Fator / Gatilho Externo");
        cFat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fator()));
        cFat.setPrefWidth(175);
        TableColumn<LinhaCausaFlutuacao, String> cCond = new TableColumn<>("Condição Observada");
        cCond.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().condicaoComparada()));
        cCond.setPrefWidth(165);
        TableColumn<LinhaCausaFlutuacao, String> cVar = new TableColumn<>("Variação / Efeito");
        cVar.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().variacaoImpacto()));
        cVar.setPrefWidth(145);
        TableColumn<LinhaCausaFlutuacao, String> cAm = new TableColumn<>("Base / Confiança");
        cAm.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().amostra()));
        cAm.setPrefWidth(120);
        TableColumn<LinhaCausaFlutuacao, String> cInt = new TableColumn<>("Interpretação Executiva");
        cInt.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().interpretacao()));
        cInt.setPrefWidth(260);
        tabelaCausasFlutuacao.getColumns().setAll(cFat, cCond, cVar, cAm, cInt);
        tabelaCausasFlutuacao.setRowFactory(tv -> {
            TableRow<LinhaCausaFlutuacao> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) abrirCardCausaFlutuacao(row.getItem());
            });
            return row;
        });

        cardTab.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📊 Matriz de Causas e Motivos das Flutuações (Clique na linha p/ detalhes)", 13, true, "#0F172A"),
                tabelaCausasFlutuacao);

        raiz.getChildren().addAll(cardProd, cardGaleriaProd, cardTab);
        return raiz;
    }

    private void abrirCardCausaFlutuacao(LinhaCausaFlutuacao lc) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Fator Analisado", lc.fator());
        meta.put("Condição Observada", lc.condicaoComparada());
        meta.put("Impacto / Variação", lc.variacaoImpacto());
        meta.put("Amostra Utilizada", lc.amostra());
        meta.put("Diagnóstico Executivo", lc.interpretacao());
        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🌦️", "Causa de Flutuação — " + lc.fator(), lc.amostra(), "#1D4ED8",
                meta, null, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Impacto: " + lc.fator(),
                        lc.condicaoComparada() + " (" + lc.variacaoImpacto() + ")",
                        "Explique detalhadamente como o fator '" + lc.fator() + "' (" + lc.variacaoImpacto()
                                + ") afeta meu estoque e vendas."));
    }

    // ==================== 5.3 PADRÕES DE DEMANDA ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaDemanda() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        for (int m = 1; m <= 12; m++) {
            cbMesDemanda.getItems().add(String.format("%02d - Mês %02d", m, m));
        }
        cbMesDemanda.getSelectionModel().select(Math.max(0, LocalDate.now().getMonthValue() - 1));
        cbMesDemanda.setPrefWidth(160);
        cbMesDemanda.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        VBox cardDemanda = new VBox(8);
        cardDemanda.setPadding(new Insets(12, 14, 12, 14));
        cardDemanda.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        HBox topoDem = new HBox(10,
                FormatadorSeguroUI.criarLabel("📅 Mapeamento de Padrões de Demanda (Clique na linha para abrir Card)", 13, true, "#0F172A"),
                cbMesDemanda);
        topoDem.setAlignment(Pos.CENTER_LEFT);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaPadroesDemanda, 240);
        TableColumn<LinhaPadraoDemanda, String> dSeg = new TableColumn<>("Dimensão / Padrão");
        dSeg.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().segmento()));
        dSeg.setPrefWidth(155);
        TableColumn<LinhaPadraoDemanda, String> dPer = new TableColumn<>("Janela / Gatilho");
        dPer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().periodoOuGatilho()));
        dPer.setPrefWidth(175);
        TableColumn<LinhaPadraoDemanda, String> dVol = new TableColumn<>("Volume");
        dVol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().volumeNotas()));
        dVol.setPrefWidth(95);
        TableColumn<LinhaPadraoDemanda, String> dFat = new TableColumn<>("Receita Gerada");
        dFat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().faturamentoTotal()));
        dFat.setPrefWidth(120);
        TableColumn<LinhaPadraoDemanda, String> dDest = new TableColumn<>("Comportamento Identificado");
        dDest.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().destaque()));
        dDest.setPrefWidth(240);
        tabelaPadroesDemanda.getColumns().setAll(dSeg, dPer, dVol, dFat, dDest);
        tabelaPadroesDemanda.setRowFactory(tv -> {
            TableRow<LinhaPadraoDemanda> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) {
                    LinhaPadraoDemanda lp = row.getItem();
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("Dimensão", lp.segmento());
                    meta.put("Janela / Gatilho", lp.periodoOuGatilho());
                    meta.put("Volume Apurado", lp.volumeNotas());
                    meta.put("Receita Associada", lp.faturamentoTotal());
                    meta.put("Comportamento Identificado", lp.destaque());
                    DialogoCorporativoModal.exibirCardDetalhes(
                            this, "📅", "Padrão de Demanda — " + lp.periodoOuGatilho(),
                            lp.segmento(), "#1D4ED8", meta, null, null,
                            () -> conectorIA.perguntarAoCopiloto(
                                    this, "Padrão: " + lp.periodoOuGatilho(), lp.destaque(),
                                    "Como aproveitar melhor o padrão de demanda '" + lp.periodoOuGatilho()
                                            + "' (" + lp.faturamentoTotal() + ")?"));
                }
            });
            return row;
        });

        cardDemanda.getChildren().addAll(topoDem, tabelaPadroesDemanda);

        VBox cardPerfis = new VBox(8);
        cardPerfis.setPadding(new Insets(12, 14, 12, 14));
        cardPerfis.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        boxPerfilClientes.setFillWidth(true);
        cardPerfis.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("👥 Perfil de Consumo dos Clientes (Clique em qualquer perfil para abrir Ficha)", 13, true, "#0F172A"),
                boxPerfilClientes);

        raiz.getChildren().addAll(cardDemanda, cardPerfis);
        return raiz;
    }

    // ==================== 5.4 DIAGRAMA ANALÍTICO ====================

    private VBox construirSubAbaGrafo() {
        VBox box = new VBox(grafoCorporativo);
        box.setPadding(new Insets(10, 2, 10, 2));
        return box;
    }

    // ==================== CARGA LAZY & PROCESSAMENTO ====================

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

        Platform.runLater(() -> lblStatusStat.setText("Calculando estatísticas..."));

        int horizonteDias = cbHorizonteCompra.getValue() != null && cbHorizonteCompra.getValue().contains("60") ? 60
                : (cbHorizonteCompra.getValue() != null && cbHorizonteCompra.getValue().contains("15") ? 15 : 30);
        int mesSel = cbMesDemanda.getSelectionModel().getSelectedIndex() + 1;

        Thread.ofVirtual().start(() -> {
            try {
                List<Produto> prods = ponte.listarProdutos();
                ConsolidadoEstoque cons = ponte.consultarSaldosConsolidadosPorProduto();
                List<Item<? extends Produto>> lotes = ponte.listarLotesComSaldo();
                List<Nota> notas = ponte.listarNotas();
                List<PontoDiarioSazonal> serie = ponte.carregarSerieSazonalDiaria();

                List<Map<String, Object>> planoSP = ponte.consultarPlanoCompras(horizonteDias);
                List<Map<String, Object>> destaquesSP = ponte.consultarDestaquesSazonais();
                List<Map<String, Object>> perdasSP = ponte.consultarMotivosPerda();
                List<Map<String, Object>> causasSP = ponte.consultarCausasFlutuacaoProdutos(null);
                List<Map<String, Object>> demandaMensalSP = ponte.consultarDemandaMensal(null, null, mesSel);
                List<Map<String, Object>> picosSP = ponte.consultarPicosDiasEventos();
                List<Map<String, Object>> perfisSP = ponte.consultarPerfilClientes();
                List<ScoreFornecedorProduto> scores = ponte.calcularScoreFornecedoresPorProduto(null);

                List<LinhaCausaFlutuacao> linhasCausas = construirLinhasCausasFlutuacao(causasSP, serie, notas);
                List<LinhaPadraoDemanda> linhasDemanda = construirLinhasPadroesDemanda(demandaMensalSP, picosSP, notas);

                Platform.runLater(() -> {
                    this.produtosCache = prods;
                    this.notasCache = notas;
                    this.scoresFornecedorCache = scores;
                    this.consCache = cons;
                    popularComboProdutoFlutuacao();

                    renderizarAvisosCompra(planoSP, prods, cons, notas, horizonteDias);
                    renderizarRankingFornecedoresStat(scores);
                    renderizarAvisosVenda(destaquesSP, prods, notas);
                    renderizarAvisosPerda(perdasSP, lotes, notas);
                    renderizarCardsProdutosComCausas(prods, cons, notas);
                    tabelaCausasFlutuacao.setItems(FXCollections.observableArrayList(linhasCausas));
                    tabelaPadroesDemanda.setItems(FXCollections.observableArrayList(linhasDemanda));
                    renderizarPerfilClientes(perfisSP, notas);
                    grafoCorporativo.recarregarNos();

                    lblStatusStat.setText("Atualizado");
                    diagnosticarProdutoSelecionadoAsync();
                });
            } finally {
                carregando = false;
            }
        });
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
            grafoCorporativo.aplicarTema(snap);
        }
    }

    private void recalcularFatoresAsync() {
        lblStatusStat.setText("Recalculando fatores...");
        Thread.ofVirtual().start(() -> {
            int atualizados = ponte.recalcularEstatisticasFatores();
            Platform.runLater(() -> {
                lblStatusStat.setText("Recalculado (" + atualizados + " reg.)");
                precisaRecarregar = true;
                carregarSeNecessario();
            });
        });
    }

    private void popularComboProdutoFlutuacao() {
        String atual = cbProdutoFlutuacao.getValue();
        cbProdutoFlutuacao.getItems().clear();
        for (Produto p : produtosCache) {
            cbProdutoFlutuacao.getItems().add("#" + p.getId() + " - " + FormatadorSeguroUI.limparNomeProduto(p.getNome()));
        }
        if (atual != null && cbProdutoFlutuacao.getItems().contains(atual)) cbProdutoFlutuacao.setValue(atual);
        else if (!cbProdutoFlutuacao.getItems().isEmpty()) cbProdutoFlutuacao.getSelectionModel().selectFirst();
    }

    private void renderizarCardsProdutosComCausas(List<Produto> prods, ConsolidadoEstoque cons, List<Nota> notas) {
        flowCardsProdutosCausas.getChildren().clear();
        if (prods.isEmpty()) {
            flowCardsProdutosCausas.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Cadastre produtos no Estoque para gerar os cards de comportamento sazonal.", 12, false, "#64748B"));
            return;
        }

        // Contagem real de vendas por produto para detectar amostra insuficiente
        Map<Long, Integer> vendasPorProd = new HashMap<>();
        Map<Long, BigDecimal> receitaPorProd = new HashMap<>();
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv && nv.getItens() != null) {
                for (Item<? extends Produto> it : nv.getItens()) {
                    if (it.getProduto() != null && it.getProduto().getId() != null) {
                        vendasPorProd.merge(it.getProduto().getId(), 1, Integer::sum);
                        receitaPorProd.merge(it.getProduto().getId(), it.getSubtotal(), BigDecimal::add);
                    }
                }
            }
        }

        int maxCards = Math.min(18, prods.size());
        for (int i = 0; i < maxCards; i++) {
            Produto p = prods.get(i);
            int qtdVendas = vendasPorProd.getOrDefault(p.getId(), 0);
            BigDecimal receita = receitaPorProd.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal saldo = cons != null ? cons.saldosPorProduto().getOrDefault(p.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;
            String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
            var diag = ponte.diagnosticarImpactoProduto(p.getId(), p.getNome());

            boolean dadosInsuficientes = qtdVendas < 3
                    && Math.abs(diag.liftClimaChuvoso() - 1.0) < 0.05
                    && Math.abs(diag.liftEventosCulturais() - 1.0) < 0.05
                    && Math.abs(diag.liftQuintoDiaUtil() - 1.0) < 0.05;

            Label badgeStatus = dadosInsuficientes
                    ? FormatadorSeguroUI.criarBadgeCartoon("⚠️ Dados Insuficientes (" + qtdVendas + " venda(s))", "#FFFBEB", "#FCD34D", "#92400E")
                    : FormatadorSeguroUI.criarBadgeCartoon("🎯 " + diag.fatorMaisImpactante(), "#EFF6FF", "#93C5FD", "#1E40AF");

            Label lNome = FormatadorSeguroUI.criarLabel(FormatadorSeguroUI.limparNomeProduto(p.getNome()), 13, true, "#0F172A");
            Label lFin = FormatadorSeguroUI.criarLabel(String.format("%s • Saldo: %s",
                    FormatadorSeguroUI.formatarMoeda(p.getPrecoVenda()),
                    FormatadorSeguroUI.formatarQuantidade(saldo, um)), 11, true, "#047857");

            Label lCausa = FormatadorSeguroUI.criarLabel(dadosInsuficientes
                    ? "Amostra histórica baixa para isolar elasticidade climática. Registre pelo menos 3 vendas em condições variadas."
                    : String.format(FormatadorSeguroUI.LOCALE_BR,
                    "Chuva: %.2fx • Eventos: %.2fx • 5º Dia: %.2fx • Rio Seca: %.2fx",
                    diag.liftClimaChuvoso(), diag.liftEventosCulturais(),
                    diag.liftQuintoDiaUtil(), diag.liftRioSeca()),
                    11, false, "#475569");

            VBox card = new VBox(5, badgeStatus, lNome, lFin, lCausa);
            card.setPrefWidth(245);
            card.setMaxWidth(260);
            card.setPadding(new Insets(10));
            card.setStyle(FormatadorSeguroUI.estiloCardCartoon(
                    dadosInsuficientes ? "#FFFDF7" : "#FFFFFF",
                    dadosInsuficientes ? "#FCD34D" : "#CBD5E1") + "-fx-cursor: hand;");
            card.setOnMouseClicked(e -> abrirCardProdutoComportamento(p, qtdVendas, receita, saldo, um, diag, dadosInsuficientes));
            flowCardsProdutosCausas.getChildren().add(card);
        }
    }

    private void abrirCardProdutoComportamento(
            Produto p, int qtdVendas, BigDecimal receita, BigDecimal saldo, String um,
            AnaliseImpactoExternoService.DiagnosticoImpacto diag, boolean dadosInsuficientes) {

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Produto", FormatadorSeguroUI.limparNomeProduto(p.getNome()));
        meta.put("Amostra de Vendas", qtdVendas + " nota(s) • Receita: " + FormatadorSeguroUI.formatarMoeda(receita));
        meta.put("Saldo em Estoque", FormatadorSeguroUI.formatarQuantidade(saldo, um));
        meta.put("Fator Mais Impactante", dadosInsuficientes ? "Dados insuficientes (amostra < 3 vendas)" : diag.fatorMaisImpactante());
        meta.put("Efeito Chuva / Clima", String.format(FormatadorSeguroUI.LOCALE_BR, "%.2fx", diag.liftClimaChuvoso()));
        meta.put("Efeito Eventos Regionais", String.format(FormatadorSeguroUI.LOCALE_BR, "%.2fx", diag.liftEventosCulturais()));
        meta.put("Efeito 5º Dia Útil", String.format(FormatadorSeguroUI.LOCALE_BR, "%.2fx", diag.liftQuintoDiaUtil()));
        meta.put("Efeito Vazante / Seca do Rio", String.format(FormatadorSeguroUI.LOCALE_BR, "%.2fx", diag.liftRioSeca()));
        meta.put("Recomendação", dadosInsuficientes
                ? "Continue vendendo normalmente e mantenha os registros atualizados para calibrar a previsão."
                : diag.recomendacaoEstoque());

        CarrosselImagensProduto carrossel = new CarrosselImagensProduto(ponte, false, 280, 155);
        carrossel.carregarProdutoSobDemanda(p);

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "📊", "Comportamento Sazonal — " + FormatadorSeguroUI.limparNomeProduto(p.getNome()),
                dadosInsuficientes ? "Amostra em formação" : diag.fatorMaisImpactante(),
                dadosInsuficientes ? "#B45309" : "#1D4ED8",
                meta, carrossel, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Causas de Demanda: " + p.getNome(),
                        meta.get("Amostra de Vendas") + " • " + meta.get("Fator Mais Impactante"),
                        "Analise o comportamento comercial e fatores sazonais do produto '" + p.getNome()
                                + "'. Amostra: " + qtdVendas + " venda(s)."));
    }

    private void diagnosticarProdutoSelecionadoAsync() {
        String sel = cbProdutoFlutuacao.getValue();
        if (sel == null || !sel.startsWith("#")) return;
        Long idProd;
        try {
            idProd = Long.parseLong(sel.substring(1).split("\\s*-\\s*", 2)[0].trim());
        } catch (Exception e) {
            return;
        }
        String nome = sel.contains(" - ") ? sel.split(" - ", 2)[1] : "Produto";

        Thread.ofVirtual().start(() -> {
            AnaliseImpactoExternoService.DiagnosticoImpacto diag = ponte.diagnosticarImpactoProduto(idProd, nome);
            long vendas = notasCache.stream()
                    .filter(n -> n instanceof NotaVenda && n.getItens() != null && n.getItens().stream()
                            .anyMatch(it -> it.getProduto() != null && it.getProduto().getId().equals(idProd)))
                    .count();
            boolean insuficiente = vendas < 3
                    && Math.abs(diag.liftClimaChuvoso() - 1.0) < 0.05
                    && Math.abs(diag.liftEventosCulturais() - 1.0) < 0.05;

            Platform.runLater(() -> {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("Produto Selecionado", FormatadorSeguroUI.limparNomeProduto(diag.nomeProduto()));
                meta.put("Suficiência da Amostra", insuficiente
                        ? "⚠️ Dados insuficientes (" + vendas + " venda(s) registradas — mínimo recomendado: 3)"
                        : "✔ Amostra consistente (" + vendas + " venda(s))");
                meta.put("Fator Dominante", diag.fatorMaisImpactante());
                meta.put("Impacto Chuva / Eventos / Salário / Rio",
                        String.format(FormatadorSeguroUI.LOCALE_BR, "Chuva %.2fx • Eventos %.2fx • 5º Dia %.2fx • Seca Rio %.2fx",
                                diag.liftClimaChuvoso(), diag.liftEventosCulturais(), diag.liftQuintoDiaUtil(), diag.liftRioSeca()));
                meta.put("Diretriz Estratégica", diag.recomendacaoEstoque());
                this.resumoDiagnosticoAtual = meta.get("Produto Selecionado") + " — " + meta.get("Suficiência da Amostra") + " — " + meta.get("Fator Dominante");
                boxDiagProdutoEstruturado.getChildren().setAll(FormatadorSeguroUI.criarGradeMetadadosCards(meta));
            });
        });
    }

    private void renderizarAvisosCompra(List<Map<String, Object>> planoSP, List<Produto> prods,
                                        ConsolidadoEstoque cons, List<Nota> notas, int horizonte) {
        boxAvisosCompra.getChildren().clear();
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        String cenario = snap.estado().getTitulo();
        double tempHoje = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.0;
        double chuvaHoje = snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;

        HBox faixaClima = new HBox(10,
                FormatadorSeguroUI.criarBadgeCartoon(
                        snap.estado().getIcone() + " " + cenario,
                        chuvaHoje >= 5 ? "#DBEAFE" : (tempHoje >= 29 ? "#FFEDD5" : "#ECFDF5"),
                        chuvaHoje >= 5 ? "#60A5FA" : (tempHoje >= 29 ? "#EA580C" : "#6EE7B7"),
                        chuvaHoje >= 5 ? "#1E40AF" : (tempHoje >= 29 ? "#9A3412" : "#047857")),
                FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                        "🌡️ %.1f°C • 🌧️ %.0fmm • 🌊 Rio %s (%.1fm) — Impacto integrado ao Índice de Vantagem de Compra (%dd).",
                        tempHoje, chuvaHoje, snap.regimeRio().getRotulo(), snap.nivelRioMetros(), horizonte),
                        11, true, "#334155")
        );
        faixaClima.setAlignment(Pos.CENTER_LEFT);
        faixaClima.setPadding(new Insets(6, 10, 6, 10));
        faixaClima.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#BFDBFE"));
        boxAvisosCompra.getChildren().add(faixaClima);

        Map<Long, BigDecimal> vendidoUltimos30d = new HashMap<>();
        LocalDate corte = LocalDate.now().minusDays(30);
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv && nv.getDataGerada() != null && !nv.getDataGerada().toLocalDate().isBefore(corte)) {
                for (Item<? extends Produto> it : nv.getItens()) {
                    if (it.getProduto() != null && it.getProduto().getId() != null) {
                        vendidoUltimos30d.merge(it.getProduto().getId(), it.getQuantidade(), BigDecimal::add);
                    }
                }
            }
        }

        record CardCompraItem(
                Produto produto, String nome, String um, double saldoAtual, double demandaProj,
                double sugerido, int indiceVantagem, ScoreFornecedorProduto melhorFornecedor, String resumoMotivo,
                String nivelTag, String bgHex, String bdHex, String txHex
        ) {}

        List<CardCompraItem> cards = new ArrayList<>();
        if (!planoSP.isEmpty()) {
            for (int i = 0; i < Math.min(10, planoSP.size()); i++) {
                Map<String, Object> m = planoSP.get(i);
                String prodNome = FormatadorSeguroUI.limparNomeProduto(String.valueOf(m.getOrDefault("nome_produto", m.getOrDefault("produto", "Produto"))));
                double qtdSug = Math.max(1.0, toDouble(m.getOrDefault("quantidade_sugerida", m.getOrDefault("sugestao_compra", 12.0))));
                double saldo = toDouble(m.getOrDefault("saldo_atual", m.getOrDefault("estoque_atual", 0.0)));
                double dem = Math.max(qtdSug + saldo, toDouble(m.getOrDefault("demanda_projetada", qtdSug)));
                Produto pMatch = prods.stream().filter(p -> FormatadorSeguroUI.limparNomeProduto(p.getNome()).equalsIgnoreCase(prodNome)).findFirst().orElse(null);
                ScoreFornecedorProduto scForn = localizarMelhorFornecedor(pMatch != null ? pMatch.getId() : null, prodNome);
                int vantagem = calcularIndiceVantagem(saldo, dem, scForn, chuvaHoje, tempHoje);
                String motivo = String.format(FormatadorSeguroUI.LOCALE_BR,
                        "Estoque: %.0f • Demanda %dd: %.0f • Repor: %.0f. %s",
                        saldo, horizonte, dem, qtdSug,
                        saldo <= 1 ? "Risco iminente de ruptura." : "Aderência positiva ao cenário " + cenario + ".");
                boolean crit = saldo <= 2.0 || vantagem >= 82;
                cards.add(new CardCompraItem(pMatch, prodNome,
                        pMatch != null && pMatch.getUm() != null ? pMatch.getUm().getSigla() : "UN",
                        saldo, dem, qtdSug, vantagem, scForn, motivo,
                        crit ? "ALTA PRIORIDADE" : "COMPRA VANTAJOSA",
                        crit ? "#FFF7ED" : "#F0FDF4",
                        crit ? "#FB923C" : "#6EE7B7",
                        crit ? "#9A3412" : "#047857"));
            }
        } else {
            for (Produto p : prods) {
                String nome = FormatadorSeguroUI.limparNomeProduto(p.getNome());
                double saldo = cons != null ? cons.saldosPorProduto().getOrDefault(p.getId(), BigDecimal.ZERO).doubleValue() : 0.0;
                double giro30 = vendidoUltimos30d.getOrDefault(p.getId(), BigDecimal.ZERO).doubleValue();
                double dem = Math.max(giro30 * (horizonte / 30.0), saldo <= 3 ? 10.0 : 4.0);
                double sugerido = Math.max(0.0, dem - saldo);
                if (saldo <= 5.0 || sugerido > 0 || cards.size() < 6) {
                    if (sugerido <= 0) sugerido = Math.max(5.0, Math.round(dem * 0.5));
                    String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
                    ScoreFornecedorProduto scForn = localizarMelhorFornecedor(p.getId(), nome);
                    int vantagem = calcularIndiceVantagem(saldo, dem, scForn, chuvaHoje, tempHoje);
                    boolean crit = saldo <= 0.01;
                    String motivo = String.format(FormatadorSeguroUI.LOCALE_BR,
                            "%s Estoque: %.0f %s • Demanda %dd: %.0f %s • Cenário %s.",
                            crit ? "Ruptura crítica!" : "Giro sazonal ativo.", saldo, um, horizonte, dem, um, cenario);
                    cards.add(new CardCompraItem(p, nome, um, saldo, dem, sugerido, vantagem, scForn, motivo,
                            crit ? "RUPTURA" : (vantagem >= 80 ? "OPORTUNIDADE" : "REPOR " + horizonte + "D"),
                            crit ? "#FEF2F2" : (vantagem >= 80 ? "#ECFDF5" : "#FFFBEB"),
                            crit ? "#FCA5A5" : (vantagem >= 80 ? "#6EE7B7" : "#FCD34D"),
                            crit ? "#991B1B" : (vantagem >= 80 ? "#047857" : "#92400E")));
                }
            }
            cards.sort((a, b) -> Integer.compare(b.indiceVantagem(), a.indiceVantagem()));
            if (cards.size() > 10) cards = cards.subList(0, 10);
        }

        if (cards.isEmpty()) {
            boxAvisosCompra.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Estoque equilibrado para o horizonte de " + horizonte + " dias.", 12, true, "#047857"));
            return;
        }

        HBox linhaHorizontal = new HBox(12);
        linhaHorizontal.setPadding(new Insets(4, 2, 6, 2));
        for (CardCompraItem c : cards) {
            VBox card = new VBox(7);
            card.setPrefWidth(265);
            card.setMinWidth(265);
            card.setMaxWidth(265);
            card.setPadding(new Insets(12));
            card.setStyle(FormatadorSeguroUI.estiloCardCartoon(c.bgHex(), c.bdHex()) + "-fx-cursor: hand;");

            Label badgeUrg = FormatadorSeguroUI.criarBadgeCartoon(c.nivelTag(), "#FFFFFF", c.bdHex(), c.txHex());
            Label badgeVant = FormatadorSeguroUI.criarBadgeCartoon("💎 Vantagem: " + c.indiceVantagem() + "%", "#EFF6FF", "#2563EB", "#1E40AF");
            HBox topoBadges = new HBox(6, badgeUrg, badgeVant);

            Label lNome = FormatadorSeguroUI.criarLabel(c.nome(), 13, true, "#0F172A");
            lNome.setWrapText(true);

            String fornTxt = c.melhorFornecedor() != null
                    ? String.format("🏆 %s (Score %d/100 • %s)", c.melhorFornecedor().nomeFornecedor(),
                    c.melhorFornecedor().scoreGeral0a100(), FormatadorSeguroUI.formatarMoeda(c.melhorFornecedor().custoMedioUnitario()))
                    : "📦 Fornecedor: comparar na próxima entrada";
            Label lForn = FormatadorSeguroUI.criarLabel(fornTxt, 11, true, "#0369A1");
            lForn.setWrapText(true);

            Label lMotivo = FormatadorSeguroUI.criarLabel(c.resumoMotivo(), 11, false, "#334155");
            lMotivo.setWrapText(true);

            Label lCTA = FormatadorSeguroUI.criarLabel("📈 Clique p/ abrir gráfico e plano estratégico", 10, true, c.txHex());
            VBox.setVgrow(lMotivo, Priority.ALWAYS);

            card.getChildren().addAll(topoBadges, lNome, lForn, lMotivo, lCTA);
            card.setOnMouseClicked(e -> abrirModalEstrategiaCompraProduto(
                    c.produto(), c.nome(), c.um(), c.saldoAtual(), c.demandaProj(), c.sugerido(),
                    c.indiceVantagem(), c.melhorFornecedor(), c.resumoMotivo(), horizonte));
            linhaHorizontal.getChildren().add(card);
        }

        ScrollPane scHorizontal = new ScrollPane(linhaHorizontal);
        scHorizontal.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scHorizontal.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scHorizontal.setFitToHeight(true);
        scHorizontal.setMinHeight(205);
        scHorizontal.setPrefHeight(215);
        scHorizontal.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        boxAvisosCompra.getChildren().add(scHorizontal);
    }

    private ScoreFornecedorProduto localizarMelhorFornecedor(Long idProd, String nomeProd) {
        if (scoresFornecedorCache == null) return null;
        return scoresFornecedorCache.stream()
                .filter(s -> s.melhorParaProduto() && ((idProd != null && idProd.equals(s.idProduto()))
                        || FormatadorSeguroUI.limparNomeProduto(s.nomeProduto()).equalsIgnoreCase(nomeProd)))
                .findFirst().orElse(null);
    }

    private int calcularIndiceVantagem(double saldo, double demanda, ScoreFornecedorProduto scForn, double chuva, double temp) {
        double ratio = demanda > 0 ? Math.min(1.5, Math.max(0.0, (demanda - saldo) / demanda)) : 0.4;
        int base = 52 + (int) Math.round(ratio * 28.0);
        if (scForn != null) base += (int) Math.round((scForn.scoreGeral0a100() - 50) * 0.25);
        if (chuva >= 5.0 || temp >= 29.0) base += 6;
        return Math.max(35, Math.min(99, base));
    }

    private void abrirModalEstrategiaCompraProduto(
            Produto prod, String nome, String um, double saldo, double demanda, double sugerido,
            int indiceVantagem, ScoreFornecedorProduto melhorFornecedor, String resumo, int horizonte) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());
        dlg.setTitle("📈 Estratégia Preditiva de Compra — " + nome);

        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        VBox header = new VBox(3);
        header.setPadding(new Insets(12, 18, 12, 18));
        header.setStyle("-fx-background-color: #0F172A;");
        header.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📈 Estratégia Preditiva de Compra: " + nome, 15, true, "#FFFFFF"),
                FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                        "💎 Índice de Vantagem: %d%% • Horizonte %dd • %s (%.1f°C • Rio %.1fm)",
                        indiceVantagem, horizonte, snap.estado().getTitulo(),
                        snap.clima() != null ? snap.clima().temperaturaMedia() : 28.0, snap.nivelRioMetros()),
                        11, false, "#CBD5E1")
        );

        HBox kpis = new HBox(10,
                FormatadorSeguroUI.criarMiniCardInfo("Saldo Atual", FormatadorSeguroUI.formatarQuantidade(saldo, um), "#F8FAFC", "#CBD5E1", "#0F172A"),
                FormatadorSeguroUI.criarMiniCardInfo("Demanda (" + horizonte + "d)", FormatadorSeguroUI.formatarQuantidade(demanda, um), "#EFF6FF", "#93C5FD", "#1D4ED8"),
                FormatadorSeguroUI.criarMiniCardInfo("Compra Sugerida", FormatadorSeguroUI.formatarQuantidade(sugerido, um), "#ECFDF5", "#6EE7B7", "#047857"),
                FormatadorSeguroUI.criarMiniCardInfo("Índice Vantagem", indiceVantagem + "%", "#FFF7ED", "#FB923C", "#EA580C")
        );
        for (var n : kpis.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // Gráfico Canvas dedicado Comparativo Estoque x Demanda x Ponto Seguro x Compra
        Canvas graficoProd = new Canvas(620, 215);
        desenharGraficoEstrategiaCompra(graficoProd, saldo, demanda, sugerido, Math.max(3.0, demanda * 0.35), horizonte);
        VBox boxGrafico = new VBox(4,
                FormatadorSeguroUI.criarLabel("📊 Projeção Comparativa de Estoque, Demanda Sazonal & Reposição (" + horizonte + " dias)", 12, true, "#0F172A"),
                graficoProd);
        boxGrafico.setPadding(new Insets(10));
        boxGrafico.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        String textoEstrategia = String.format(FormatadorSeguroUI.LOCALE_BR,
                "• Por que comprar agora: %s\n"
                        + "• Ligação ao Planejamento & Clima: O cenário atual '%s' somado ao regime do rio (%s — %.1fm) indica "
                        + "necessidade de antecipar a reposição em %s para capturar pico sem ruptura de balcão.\n"
                        + "• Inteligência de Fornecedor: %s",
                resumo, snap.estado().getTitulo(), snap.regimeRio().getRotulo(), snap.nivelRioMetros(),
                FormatadorSeguroUI.formatarQuantidade(sugerido, um),
                melhorFornecedor != null
                        ? String.format("Fornecedor mais vantajoso: %s (Score %d/100, Custo Médio %s, Economia %s vs concorrente).",
                        melhorFornecedor.nomeFornecedor(), melhorFornecedor.scoreGeral0a100(),
                        FormatadorSeguroUI.formatarMoeda(melhorFornecedor.custoMedioUnitario()),
                        FormatadorSeguroUI.formatarMoeda(melhorFornecedor.economiaVsMaiorCusto()))
                        : "Compare cotações de fornecedores na entrada de notas para construir pontuação de score.");

        VBox boxJust = new VBox(6,
                FormatadorSeguroUI.criarLabel("🧠 Justificativa Estratégica & Integração com o Planejamento Sazonal", 12, true, "#1E40AF"),
                FormatadorSeguroUI.criarLabel(textoEstrategia, 12, false, "#1E293B"));
        ((Label) boxJust.getChildren().get(1)).setWrapText(true);
        boxJust.setPadding(new Insets(12));
        boxJust.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));

        Label lblFeedback = FormatadorSeguroUI.criarLabel("", 11, true, "#047857");
        HBox.setHgrow(lblFeedback, Priority.ALWAYS);

        Button btnBandeja = FormatadorSeguroUI.criarBotaoCartoon("📥 Enviar p/ Bandeja Compra", "#047857", "#065F46", "#FFFFFF");
        btnBandeja.setOnAction(e -> {
            ponte.enfileirarCompraSugeridaNaBandeja(nome, BigDecimal.valueOf(Math.max(1.0, sugerido)), null,
                    "Compra preditiva (" + horizonte + "d) • Vantagem " + indiceVantagem + "%");
            lblFeedback.setText("✔ Sugestão enfileirada na Bandeja IA!");
        });

        Button btnKanban = FormatadorSeguroUI.criarBotaoCartoon("🗂️ Abrir Planejamento Kanban", "#0284C7", "#0369A1", "#FFFFFF");
        btnKanban.setOnAction(e -> {
            dlg.close();
            ponte.navegarParaAba("KANBAN");
        });

        Map<String, String> metaOrig = new LinkedHashMap<>();
        metaOrig.put("Produto", nome);
        metaOrig.put("Índice de Vantagem", indiceVantagem + "%");
        metaOrig.put("Saldo Atual", FormatadorSeguroUI.formatarQuantidade(saldo, um));
        metaOrig.put("Demanda Projetada (" + horizonte + "d)", FormatadorSeguroUI.formatarQuantidade(demanda, um));
        metaOrig.put("Compra Sugerida", FormatadorSeguroUI.formatarQuantidade(sugerido, um));
        metaOrig.put("Clima / Rio Atual", snap.estado().getTitulo() + " / " + snap.regimeRio().getRotulo());
        if (melhorFornecedor != null) {
            metaOrig.put("Melhor Fornecedor", melhorFornecedor.nomeFornecedor() + " (Score " + melhorFornecedor.scoreGeral0a100() + "/100)");
        }

        Button btnIA = FormatadorSeguroUI.criarBotaoIA("🤖 Detalhar c/ IA", () -> {
            dlg.close();
            conectorIA.explicarComContextoOrigem(this, "Estatística — Compra Preditiva",
                    nome + " (Vantagem " + indiceVantagem + "%)", metaOrig,
                    "Detalhe a estratégia de compra, estoque de segurança e precificação promocional para '" + nome
                            + "' considerando o Índice de Vantagem de " + indiceVantagem + "% e a ligação com o planejamento sazonal.");
        });

        Button btnFechar = FormatadorSeguroUI.criarBotaoCartoon("Fechar", "#0F172A", "#0F172A", "#FFFFFF");
        btnFechar.setOnAction(e -> dlg.close());

        HBox rodape = new HBox(8, lblFeedback, btnBandeja, btnKanban, btnIA, btnFechar);
        rodape.setAlignment(Pos.CENTER_RIGHT);
        rodape.setPadding(new Insets(10, 16, 12, 16));
        rodape.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 1px 0 0 0;");

        VBox corpo = new VBox(10, kpis, boxGrafico, boxJust);
        corpo.setPadding(new Insets(14, 16, 12, 16));
        corpo.setStyle("-fx-background-color: #FFFFFF;");

        VBox root = new VBox(header, corpo, rodape);
        root.setPrefWidth(680);
        dlg.setScene(new Scene(root));
        dlg.showAndWait();
    }

    private void desenharGraficoEstrategiaCompra(Canvas c, double saldo, double demanda, double sugerido, double seguranca, int horizonte) {
        double w = c.getWidth(), h = c.getHeight();
        GraphicsContext g = c.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#F8FAFC"));
        g.fillRoundRect(0, 0, w, h, 8, 8);

        double maxVal = Math.max(10.0, Math.max(Math.max(saldo, demanda), Math.max(sugerido + saldo, seguranca)) * 1.25);
        double padL = 46, padR = 24, padT = 22, padB = 34;
        double plotW = w - padL - padR, plotH = h - padT - padB;

        g.setStroke(Color.web("#E2E8F0"));
        g.setLineWidth(1.0);
        g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
        for (int k = 0; k <= 4; k++) {
            double y = padT + (k / 4.0) * plotH;
            g.strokeLine(padL, y, padL + plotW, y);
            g.setFill(Color.web("#64748B"));
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(String.format("%.0f", maxVal * (1.0 - k / 4.0)), padL - 6, y + 3);
        }

        String[] labels = {"Estoque Atual", "Ponto Seguro", "Demanda (" + horizonte + "d)", "Compra Sugerida"};
        double[] vals = {saldo, seguranca, demanda, sugerido};
        String[] cores = {"#334155", "#EA580C", "#0284C7", "#059669"};

        double slot = plotW / labels.length;
        double barW = Math.min(72, slot * 0.56);
        for (int i = 0; i < labels.length; i++) {
            double x = padL + i * slot + (slot - barW) / 2.0;
            double barH = Math.max(4.0, (vals[i] / maxVal) * plotH);
            double y = padT + plotH - barH;
            g.setFill(Color.web(cores[i]));
            g.fillRoundRect(x, y, barW, barH, 8, 8);

            g.setFill(Color.web("#0F172A"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.format("%.0f", vals[i]), x + barW / 2.0, Math.max(padT + 10, y - 5));

            g.setFill(Color.web("#334155"));
            g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
            g.fillText(labels[i], x + barW / 2.0, padT + plotH + 18);
        }
    }

    private void renderizarRankingFornecedoresStat(List<ScoreFornecedorProduto> scores) {
        boxRankingFornecedoresStat.getChildren().clear();
        if (scores == null || scores.isEmpty()) {
            boxRankingFornecedoresStat.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Ainda não há entradas suficientes para comparar fornecedores por produto. Cadastre Notas de Entrada no Financeiro.",
                    12, false, "#64748B"));
            return;
        }
        int lim = 0;
        for (ScoreFornecedorProduto sc : scores) {
            if (lim >= 10) break;
            lim++;
            String badge = sc.scoreGeral0a100() + " PTS" + (sc.melhorParaProduto() ? " • 🏆 MELHOR" : "");
            String desc = String.format(FormatadorSeguroUI.LOCALE_BR,
                    "Fornecedor: %s • Custo Médio: %s (Economia: %s) • Entradas: %d • Avaria/Validade: %.1f%% • %s",
                    sc.nomeFornecedor(), FormatadorSeguroUI.formatarMoeda(sc.custoMedioUnitario()),
                    FormatadorSeguroUI.formatarMoeda(sc.economiaVsMaiorCusto()),
                    sc.totalEntradasLotes(), sc.taxaAvariaPercentual(), sc.motivoScore());
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("produto", sc.nomeProduto());
            det.put("fornecedor", sc.nomeFornecedor());
            det.put("score_qualidade", sc.scoreGeral0a100() + " / 100");
            det.put("custo_medio", FormatadorSeguroUI.formatarMoeda(sc.custoMedioUnitario()));
            det.put("economia_comparativa", FormatadorSeguroUI.formatarMoeda(sc.economiaVsMaiorCusto()));
            det.put("taxa_avaria_validade", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f%%", sc.taxaAvariaPercentual()));
            det.put("diagnostico", sc.motivoScore());
            boxRankingFornecedoresStat.getChildren().add(criarLinhaAvisoClicavel(
                    badge,
                    sc.melhorParaProduto() ? "#ECFDF5" : "#EFF6FF",
                    sc.melhorParaProduto() ? "#6EE7B7" : "#BFDBFE",
                    sc.melhorParaProduto() ? "#047857" : "#1E40AF",
                    FormatadorSeguroUI.limparNomeProduto(sc.nomeProduto()) + " ➔ " + sc.nomeFornecedor(),
                    desc, null, det));
        }
    }

    private void renderizarAvisosVenda(List<Map<String, Object>> destaquesSP, List<Produto> prods, List<Nota> notas) {
        boxAvisosVenda.getChildren().clear();
        if (!destaquesSP.isEmpty()) {
            for (int i = 0; i < Math.min(6, destaquesSP.size()); i++) {
                Map<String, Object> m = destaquesSP.get(i);
                String nome = String.valueOf(m.getOrDefault("nome_produto", m.getOrDefault("produto", "Destaque")));
                String resumo = formatarMapaAmigavel(m);
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Campanha IA", () ->
                        conectorIA.perguntarAoCopiloto(this, "Potencializar Venda: " + nome, resumo,
                                "O produto '" + nome + "' está entre os destaques sazonais (" + resumo
                                        + "). Crie uma sugestão de combo ou abordagem de balcão."));
                boxAvisosVenda.getChildren().add(criarLinhaAvisoClicavel(
                        "DESTAQUE SAZONAL", "#ECFDF5", "#6EE7B7", "#065F46",
                        nome, resumo, btnIA, m));
            }
            return;
        }

        Map<Long, BigDecimal> receitaProd = new HashMap<>();
        Map<Long, String> nomesProd = new HashMap<>();
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv) {
                for (Item<? extends Produto> it : nv.getItens()) {
                    if (it.getProduto() != null && it.getProduto().getId() != null) {
                        receitaProd.merge(it.getProduto().getId(), it.getSubtotal(), BigDecimal::add);
                        nomesProd.put(it.getProduto().getId(), FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()));
                    }
                }
            }
        }
        List<Map.Entry<Long, BigDecimal>> top = receitaProd.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5).toList();

        if (top.isEmpty()) {
            boxAvisosVenda.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Ainda sem histórico suficiente de vendas para ranquear destaques sazonais.", 12, false, "#64748B"));
        } else {
            var snap = ponte.getGerenciadorTema().getSnapshotAtual();
            for (var e : top) {
                String nome = nomesProd.get(e.getKey());
                String desc = String.format(FormatadorSeguroUI.LOCALE_BR,
                        "Receita acumulada: %s • Cenário favorável no clima atual (%s)",
                        FormatadorSeguroUI.formatarMoeda(e.getValue()), snap.estado().getTitulo());
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Estratégia IA", () ->
                        conectorIA.perguntarAoCopiloto(this, "Impulsionar " + nome, desc, desc + ". Como aumentar o ticket médio?"));
                Map<String, Object> det = Map.of("Produto", nome, "Receita Acumulada", FormatadorSeguroUI.formatarMoeda(e.getValue()),
                        "Clima Atual", snap.estado().getTitulo());
                boxAvisosVenda.getChildren().add(criarLinhaAvisoClicavel(
                        "ALTA PROCURA", "#ECFDF5", "#6EE7B7", "#065F46", nome, desc, btnIA, det));
            }
        }
    }

    private void renderizarAvisosPerda(List<Map<String, Object>> perdasSP, List<Item<? extends Produto>> lotes, List<Nota> notas) {
        boxAvisosPerda.getChildren().clear();
        if (!perdasSP.isEmpty()) {
            for (int i = 0; i < Math.min(6, perdasSP.size()); i++) {
                Map<String, Object> m = perdasSP.get(i);
                String nome = String.valueOf(m.getOrDefault("nome_produto", m.getOrDefault("motivo", "Risco de Perda")));
                String resumo = formatarMapaAmigavel(m);
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Prevenir IA", () ->
                        conectorIA.perguntarAoCopiloto(this, "Prevenir Perda: " + nome, resumo,
                                "Diagnostique este indicador de perda prematura: " + resumo));
                boxAvisosPerda.getChildren().add(criarLinhaAvisoClicavel(
                        "RISCO PERDA", "#FEF2F2", "#FCA5A5", "#991B1B", nome, resumo, btnIA, m));
            }
            return;
        }

        LocalDate hoje = LocalDate.now();
        int exibidos = 0;
        for (Nota n : notas) {
            if (n instanceof NotaPerda np && exibidos < 4) {
                exibidos++;
                String prod = !np.getItens().isEmpty() && np.getItens().get(0).getProduto() != null
                        ? FormatadorSeguroUI.limparNomeProduto(np.getItens().get(0).getProduto().getNome()) : "Estoque";
                String forn = np.getPessoa() != null ? np.getPessoa().getNome() : "Fornecedor não vinculado";
                String desc = String.format("Prejuízo de %s • Motivo: %s • %s",
                        FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda()),
                        FormatadorSeguroUI.tratarParaExibicao(np.getDescricao(), np.getTipoPerda()),
                        np.isPerdaDentroDaValidade() ? "Elegível p/ abatimento (" + forn + ")" : "Perda por vencimento interno");
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Causa IA", () ->
                        conectorIA.analisarPerdaERessarcimento(prod, forn, np.getDescricao(), np.getValorCustoPerda(), np.isPerdaDentroDaValidade()));
                Map<String, Object> det = Map.of("Nota de Perda", "#" + np.getId(), "Produto", prod,
                        "Fornecedor", forn, "Prejuízo", FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda()));
                boxAvisosPerda.getChildren().add(criarLinhaAvisoClicavel("PERDA REAL", "#FEF2F2", "#FCA5A5", "#991B1B", prod, desc, btnIA, det));
            }
        }
        for (Item<? extends Produto> l : lotes) {
            if (exibidos >= 8) break;
            if (l.getDataValidade() != null && l.getDataValidade().isBefore(hoje.plusDays(15)) && l.getProduto() != null) {
                exibidos++;
                long dias = ChronoUnit.DAYS.between(hoje, l.getDataValidade());
                String prod = FormatadorSeguroUI.limparNomeProduto(l.getProduto().getNome());
                String desc = String.format("Lote #%d (%s) %s. Risco estimado: %s",
                        l.getId(),
                        FormatadorSeguroUI.formatarQuantidade(l.getQuantidadeDisponivel(), "UN"),
                        dias < 0 ? "já venceu há " + Math.abs(dias) + " dias" : "vence em " + dias + " dias",
                        FormatadorSeguroUI.formatarMoeda(l.getQuantidadeDisponivel().multiply(l.getValor())));
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Queima/Combo IA", () ->
                        conectorIA.sugerirPromocaoOuReposicao(prod, l.getQuantidadeDisponivel(), l.getDataValidade()));
                Map<String, Object> det = Map.of("Lote", "#" + l.getId(), "Produto", prod,
                        "Validade", FormatadorSeguroUI.formatarDataAmigavel(l.getDataValidade()));
                boxAvisosPerda.getChildren().add(criarLinhaAvisoClicavel(
                        dias < 0 ? "VENCIDO" : "RISCO VALIDADE", "#FFFBEB", "#FCD34D", "#92400E", prod, desc, btnIA, det));
            }
        }
        if (exibidos == 0) {
            boxAvisosPerda.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Sem riscos de perda ou lotes vencendo nos próximos 15 dias.", 12, true, "#047857"));
        }
    }

    private List<LinhaCausaFlutuacao> construirLinhasCausasFlutuacao(
            List<Map<String, Object>> causasSP, List<PontoDiarioSazonal> serie, List<Nota> notas) {
        List<LinhaCausaFlutuacao> lista = new ArrayList<>();

        for (Map<String, Object> m : causasSP) {
            String fator = String.valueOf(m.getOrDefault("fator_externo", m.getOrDefault("nome_fator", "Fator Sazonal")));
            String cond = String.valueOf(m.getOrDefault("nome_produto", m.getOrDefault("categoria", "Geral")));
            String lift = String.valueOf(m.getOrDefault("lift_percentual", m.getOrDefault("variacao_percentual", "Calculado")));
            String z = String.valueOf(m.getOrDefault("z_welch", m.getOrDefault("z_score", "-")));
            lista.add(new LinhaCausaFlutuacao(fator, cond, lift + "% (Signif. Z=" + z + ")",
                    String.valueOf(m.getOrDefault("qtd_amostra", "Histórico")),
                    formatarMapaAmigavel(m)));
        }

        double sumChuvaFat = 0, sumSecoFat = 0;
        int nChuva = 0, nSeco = 0;
        double sumCalorFat = 0, sumAmenoFat = 0;
        int nCalor = 0, nAmeno = 0;

        for (PontoDiarioSazonal p : serie) {
            if (!p.possuiMovimentoReal()) continue;
            if (p.precipitacaoMm() >= 2.0) {
                sumChuvaFat += p.faturamento();
                nChuva++;
            } else {
                sumSecoFat += p.faturamento();
                nSeco++;
            }
            if (p.tempMedia() >= 30.0) {
                sumCalorFat += p.faturamento();
                nCalor++;
            } else {
                sumAmenoFat += p.faturamento();
                nAmeno++;
            }
        }

        if (nChuva > 0 || nSeco > 0) {
            double medChuva = nChuva > 0 ? sumChuvaFat / nChuva : 0;
            double medSeco = nSeco > 0 ? sumSecoFat / nSeco : 0;
            double diff = (medSeco > 0 && medChuva > 0) ? ((medChuva - medSeco) / medSeco) * 100.0 : 0;
            lista.add(new LinhaCausaFlutuacao(
                    "🌧️ Precipitação / Chuva (≥2mm)",
                    String.format(FormatadorSeguroUI.LOCALE_BR, "Média %s/dia vs %s/dia seco",
                            FormatadorSeguroUI.formatarMoeda(medChuva), FormatadorSeguroUI.formatarMoeda(medSeco)),
                    (nChuva < 2 || nSeco < 2) ? "Amostra baixa"
                            : String.format(FormatadorSeguroUI.LOCALE_BR, "%+.1f%% no faturamento", diff),
                    String.format("%d dias c/ chuva | %d secos", nChuva, nSeco),
                    diff < 0 ? "Chuvas reduzem fluxo presencial; reforçar delivery/itens de despensa."
                            : "Demanda resiliente durante chuvas amazônicas."));
        }

        if (nCalor > 0 || nAmeno > 0) {
            double medCalor = nCalor > 0 ? sumCalorFat / nCalor : 0;
            double medAmeno = nAmeno > 0 ? sumAmenoFat / nAmeno : 0;
            double diff = (medAmeno > 0 && medCalor > 0) ? ((medCalor - medAmeno) / medAmeno) * 100.0 : 0;
            lista.add(new LinhaCausaFlutuacao(
                    "☀️ Temperatura Elevada (≥30°C)",
                    String.format(FormatadorSeguroUI.LOCALE_BR, "Média %s/dia quente vs %s/dia ameno",
                            FormatadorSeguroUI.formatarMoeda(medCalor), FormatadorSeguroUI.formatarMoeda(medAmeno)),
                    (nCalor < 2 || nAmeno < 2) ? "Amostra baixa"
                            : String.format(FormatadorSeguroUI.LOCALE_BR, "%+.1f%% de oscilação", diff),
                    String.format("%d dias ≥30°C | %d amenos", nCalor, nAmeno),
                    "Altas temperaturas impulsionam bebidas geladas e elevam risco de deterioração sem refrigeração."));
        }

        double fat5Dia = 0, fatOutros = 0;
        int n5Dia = 0, nOutros = 0;
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv && nv.getVinculoFatorExterno() != null) {
                VinculoFatorExternoNota v = nv.getVinculoFatorExterno();
                double val = nv.getValorTotal() != null ? nv.getValorTotal().doubleValue() : 0;
                if (v.isQuintoDiaUtil()) {
                    fat5Dia += val;
                    n5Dia++;
                } else {
                    fatOutros += val;
                    nOutros++;
                }
            }
        }
        lista.add(new LinhaCausaFlutuacao(
                "💵 Ciclo Salarial (5º Dia Útil)",
                String.format("%d vendas no 5º dia útil vs %d regulares", n5Dia, nOutros),
                n5Dia > 0 && nOutros > 0
                        ? String.format(FormatadorSeguroUI.LOCALE_BR, "%+.1f%% no ticket", (((fat5Dia / n5Dia) - (fatOutros / nOutros)) / Math.max(1, fatOutros / nOutros)) * 100.0)
                        : "Aguardando mais vendas",
                (n5Dia + nOutros) + " notas com contexto",
                "Nos dias de pagamento salarial há pico de compras de reposição e kits completos."));

        return lista;
    }

    private List<LinhaPadraoDemanda> construirLinhasPadroesDemanda(
            List<Map<String, Object>> demandaMensalSP, List<Map<String, Object>> picosSP, List<Nota> notas) {
        List<LinhaPadraoDemanda> linhas = new ArrayList<>();

        for (Map<String, Object> m : picosSP) {
            linhas.add(new LinhaPadraoDemanda(
                    "Pico / Evento Sazonal",
                    FormatadorSeguroUI.tratarParaExibicao(m.getOrDefault("nome_evento", m.getOrDefault("dia_semana", "Evento")), "Evento"),
                    FormatadorSeguroUI.tratarParaExibicao(m.getOrDefault("total_vendas", "-"), "-"),
                    FormatadorSeguroUI.formatarMoeda(toDouble(m.getOrDefault("faturamento", 0))),
                    formatarMapaAmigavel(m)));
        }
        for (Map<String, Object> m : demandaMensalSP) {
            linhas.add(new LinhaPadraoDemanda(
                    "Projeção Mensal",
                    FormatadorSeguroUI.tratarParaExibicao(m.getOrDefault("nome_produto", "Produto"), "Produto"),
                    FormatadorSeguroUI.tratarParaExibicao(m.getOrDefault("qtd_prevista", m.getOrDefault("quantidade", "-")), "-"),
                    FormatadorSeguroUI.formatarMoeda(toDouble(m.getOrDefault("valor_estimado", 0))),
                    formatarMapaAmigavel(m)));
        }

        Map<DayOfWeek, double[]> porDiaSemana = new LinkedHashMap<>();
        for (DayOfWeek dow : DayOfWeek.values()) porDiaSemana.put(dow, new double[2]);
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv && nv.getDataGerada() != null) {
                double[] arr = porDiaSemana.get(nv.getDataGerada().getDayOfWeek());
                arr[0] += 1;
                arr[1] += nv.getValorTotal() != null ? nv.getValorTotal().doubleValue() : 0.0;
            }
        }
        for (Map.Entry<DayOfWeek, double[]> e : porDiaSemana.entrySet()) {
            if (e.getValue()[0] > 0) {
                String diaBr = e.getKey().getDisplayName(TextStyle.FULL, FormatadorSeguroUI.LOCALE_BR);
                linhas.add(new LinhaPadraoDemanda(
                        "Dia da Semana",
                        diaBr.substring(0, 1).toUpperCase() + diaBr.substring(1),
                        ((int) e.getValue()[0]) + " vendas",
                        FormatadorSeguroUI.formatarMoeda(e.getValue()[1]),
                        "Ticket médio: " + FormatadorSeguroUI.formatarMoeda(e.getValue()[1] / e.getValue()[0])));
            }
        }
        return linhas;
    }

    private void renderizarPerfilClientes(List<Map<String, Object>> perfisSP, List<Nota> notas) {
        boxPerfilClientes.getChildren().clear();
        if (!perfisSP.isEmpty()) {
            for (int i = 0; i < Math.min(8, perfisSP.size()); i++) {
                Map<String, Object> m = perfisSP.get(i);
                String nome = String.valueOf(m.getOrDefault("nome_cliente", m.getOrDefault("cliente", "Cliente")));
                boxPerfilClientes.getChildren().add(criarLinhaAvisoClicavel(
                        "PERFIL", "#EFF6FF", "#93C5FD", "#1E40AF", nome, formatarMapaAmigavel(m), null, m));
            }
            return;
        }

        Map<String, double[]> resumoCli = new LinkedHashMap<>();
        for (Nota n : notas) {
            if (n instanceof NotaVenda nv) {
                String nome = nv.getPessoa() != null ? nv.getPessoa().getNome() : "Consumidor Final (Balcão)";
                double[] acc = resumoCli.computeIfAbsent(nome, k -> new double[2]);
                acc[0] += 1;
                acc[1] += nv.getValorTotal() != null ? nv.getValorTotal().doubleValue() : 0;
            }
        }
        resumoCli.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .limit(8)
                .forEach(e -> {
                    int freq = (int) e.getValue()[0];
                    double tot = e.getValue()[1];
                    String desc = String.format(FormatadorSeguroUI.LOCALE_BR,
                            "%d compra(s) • Total gasto: %s • Ticket médio: %s",
                            freq, FormatadorSeguroUI.formatarMoeda(tot), FormatadorSeguroUI.formatarMoeda(tot / freq));
                    Map<String, Object> det = new LinkedHashMap<>();
                    det.put("Cliente", e.getKey());
                    det.put("Frequência de Compras", freq + " nota(s)");
                    det.put("Valor Total Gasto", FormatadorSeguroUI.formatarMoeda(tot));
                    det.put("Ticket Médio", FormatadorSeguroUI.formatarMoeda(tot / freq));
                    boxPerfilClientes.getChildren().add(criarLinhaAvisoClicavel(
                            freq >= 3 ? "RECORRENTE" : "CONSUMIDOR",
                            freq >= 3 ? "#ECFDF5" : "#F1F5F9",
                            freq >= 3 ? "#6EE7B7" : "#CBD5E1",
                            freq >= 3 ? "#065F46" : "#334155",
                            e.getKey(), desc, null, det));
                });

        if (boxPerfilClientes.getChildren().isEmpty()) {
            boxPerfilClientes.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum perfil de compra apurado ainda.", 12, false, "#64748B"));
        }
    }

    private HBox criarLinhaAvisoClicavel(
            String badgeTxt, String bg, String bd, String tx,
            String titulo, String desc, Button acaoOpcional, Map<String, Object> mapaDetalhes) {
        Label badge = FormatadorSeguroUI.criarBadgeCartoon(badgeTxt, bg, bd, tx);
        Label lTit = FormatadorSeguroUI.criarLabel(titulo, 12, true, "#0F172A");
        Label lDesc = FormatadorSeguroUI.criarLabel(desc, 11, false, "#475569");
        VBox info = new VBox(3, new FlowPane(6, 4, badge, lTit), lDesc);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox row = acaoOpcional != null ? new HBox(10, info, acaoOpcional) : new HBox(10, info);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0") + "-fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("Classificação", badgeTxt);
                meta.put("Registro", titulo);
                if (mapaDetalhes != null) {
                    mapaDetalhes.forEach((k, v) -> {
                        if (v != null) {
                            meta.put(formatarNomeCampoAmigavel(k), FormatadorSeguroUI.tratarParaExibicao(v, "—"));
                        }
                    });
                }
                DialogoCorporativoModal.exibirCardDetalhes(
                        this, "📋", titulo, badgeTxt, tx, meta, null, null, null, null,
                        () -> conectorIA.explicarComContextoOrigem(
                                this, "Estatística & Análise — " + badgeTxt,
                                titulo, meta,
                                "Explique em linguagem clara e estratégica o indicador '" + titulo
                                        + "' (" + desc + ") e gere recomendações práticas conectadas ao clima/estoque."));
            }
        });
        return row;
    }

    private String formatarMapaAmigavel(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!sb.isEmpty()) sb.append(" • ");
            sb.append(formatarNomeCampoAmigavel(e.getKey())).append(": ")
                    .append(FormatadorSeguroUI.tratarParaExibicao(e.getValue(), ""));
        }
        return sb.toString();
    }

    private String formatarNomeCampoAmigavel(String chave) {
        if (chave == null || chave.isBlank()) return "Detalhe";
        String k = chave.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "nome_produto", "produto" -> "Produto";
            case "nome_cliente", "cliente" -> "Cliente";
            case "quantidade_sugerida", "sugestao_compra" -> "Compra Sugerida";
            case "saldo_atual", "estoque_atual" -> "Estoque Atual";
            case "demanda_projetada" -> "Demanda Projetada";
            case "lift_percentual" -> "Impacto na Demanda (%)";
            case "qtd_vendas", "frequencia" -> "Frequência de Vendas";
            case "ticket_medio" -> "Ticket Médio";
            case "fator", "nome_fator" -> "Fator Sazonal";
            case "motivo", "causa" -> "Motivo / Causa";
            default -> {
                String limpo = chave.replace('_', ' ').trim();
                yield limpo.substring(0, 1).toUpperCase(FormatadorSeguroUI.LOCALE_BR) + limpo.substring(1);
            }
        };
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v != null ? Double.parseDouble(v.toString()) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
