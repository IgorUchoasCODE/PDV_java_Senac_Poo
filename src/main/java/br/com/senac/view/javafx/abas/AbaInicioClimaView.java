package br.com.senac.view.javafx.abas;

import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.CardMacioExpansivel;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.componentes.GraficoSazonalPeriodoCanvas;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.PontoDiarioSazonal;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ResumoDashboardKPIs;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aba 1: Home Executiva.
 * Dashboard com profundidade cromática, 4 cards superiores expansíveis com confirmação IA,
 * gráfico sazonal ampliado com filtro 'Hoje' e compensação de subida, cards analíticos estruturados
 * (zero formato terminal) e avisos operacionais expansíveis sob demanda.
 */
public class AbaInicioClimaView extends VBox {

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final Label lblStatusSync = FormatadorSeguroUI.criarBadgeCartoon(
            "Aguardando leitura", "#F1F5F9", "#CBD5E1", "#475569");
    private final GraficoSazonalPeriodoCanvas graficoSazonal;

    // Labels dos 4 Cards Executivos (sincronizadas com as curvas do gráfico: Temp #EA580C e Rio #0284C7)
    private final Label lblClimaTempValor = FormatadorSeguroUI.criarValorKpi("-- °C", "#C2410C");
    private final Label lblClimaDetalhe = FormatadorSeguroUI.criarLabel("Carregando clima...", 11, false, "#9A3412");
    private final Label lblRioValor = FormatadorSeguroUI.criarValorKpi("-- m", "#0284C7");
    private final Label lblRioDetalhe = FormatadorSeguroUI.criarLabel("Carregando nível do rio...", 11, false, "#0369A1");
    private final Label lblVendasValor = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#065F46");
    private final Label lblVendasDetalhe = FormatadorSeguroUI.criarLabel("Lucro e margem do período", 11, false, "#064E3B");
    private final Label lblAvisosValor = FormatadorSeguroUI.criarValorKpi("0 alertas", "#991B1B");
    private final Label lblAvisosDetalhe = FormatadorSeguroUI.criarLabel("Estoque e títulos em aberto", 11, false, "#7F1D1D");

    // Grade estruturada substituindo saídas cruas estilo terminal
    private final GridPane gridResumoSazonal = new GridPane();
    private final VBox boxListaAvisos = new VBox(8);

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabGrafico = new Tab("📈 Painel & Gráfico Sazonal");
    private final Tab tabAvisos = new Tab("⚠️ Avisos Operacionais Expansíveis");

    private List<PontoDiarioSazonal> serieCache = new ArrayList<>();
    private ResumoDashboardKPIs kpisCache;
    private List<AvisoOperacional> avisosCache = new ArrayList<>();

    private GerenciadorTemaClimatico.SnapshotTemaClimatico ultimoTema;
    private volatile boolean precisaRecarregar = true;
    private volatile boolean climaJaSolicitado = false;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaInicioClimaView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;
        this.graficoSazonal = new GraficoSazonalPeriodoCanvas(conectorIA);

        setSpacing(12);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();
        GridPane gridKpis = criarGridKpis();

        VBox conteudoGrafico = new VBox(12, graficoSazonal, criarCartaoResumoSazonal());
        conteudoGrafico.setPadding(new Insets(10, 2, 10, 2));
        conteudoGrafico.setFillWidth(true);
        VBox.setVgrow(graficoSazonal, Priority.ALWAYS);
        tabGrafico.setContent(FormatadorSeguroUI.envolverScrollSuave(conteudoGrafico));

        boxListaAvisos.setPadding(new Insets(10, 2, 10, 2));
        boxListaAvisos.setFillWidth(true);
        tabAvisos.setContent(FormatadorSeguroUI.envolverScrollSuave(boxListaAvisos));

        tabPane.getTabs().addAll(tabGrafico, tabAvisos);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(topo, gridKpis, tabPane);

        this.ultimoTema = ponte.getGerenciadorTema().getSnapshotAtual();
        atualizarCardsClimaERio(ultimoTema);
        atualizarResumoSazonalCards(List.of());

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) {
                carregarSeNecessario();
            }
        });
    }

    private HBox criarCabecalho() {
        Label titulo = FormatadorSeguroUI.criarTituloSecao("🏠", "Painel Executivo & Inteligência Sazonal", "#0F172A");
        Label subtitulo = FormatadorSeguroUI.criarSubtitulo(
                "Indicadores em tempo real com expansão interativa de cards, análise causal e previsão climática.", "#64748B");
        VBox boxTit = new VBox(2, titulo, subtitulo);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Sincronizar", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            climaJaSolicitado = false;
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        Button btnBriefing = FormatadorSeguroUI.criarBotaoIA("🤖 Briefing Executivo IA", () -> {
            var snap = ponte.getGerenciadorTema().getSnapshotAtual();
            String resumo = String.format(FormatadorSeguroUI.LOCALE_BR,
                    "%s/%s • %s • Rio %.1f m\nFaturamento: %s • Alertas: %s",
                    snap.cidade(), snap.uf(), snap.descreverClimaCurto(), snap.nivelRioMetros(),
                    lblVendasValor.getText(), lblAvisosValor.getText());
            String prompt = String.format(FormatadorSeguroUI.LOCALE_BR,
                    "Me dê um briefing executivo diário da loja em %s/%s: clima atual '%s', rio em %s (%.1f m). "
                            + "Resumo na tela: %s | %s. Quais ações imediatas priorizar hoje no balcão?",
                    snap.cidade(), snap.uf(), snap.descreverClimaCurto(),
                    snap.regimeRio().getRotulo(), snap.nivelRioMetros(),
                    lblVendasValor.getText(), lblAvisosValor.getText());
            conectorIA.perguntarAoCopiloto(this, "Briefing Executivo Diário", resumo, prompt);
        });

        HBox acoes = new HBox(8, lblStatusSync, btnAtualizar, btnBriefing);
        acoes.setAlignment(Pos.CENTER_RIGHT);

        HBox topo = new HBox(12, boxTit, acoes);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    private GridPane criarGridKpis() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            col.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(col);
        }

        Button btnClimaExpandir = FormatadorSeguroUI.criarBotaoCartoon("⤢ Expandir", "#FFEDD5", "#EA580C", "#9A3412");
        btnClimaExpandir.setOnAction(e -> abrirCardExpandidoClima());

        Button btnRioExpandir = FormatadorSeguroUI.criarBotaoCartoon("⤢ Expandir", "#E0F2FE", "#0284C7", "#0369A1");
        btnRioExpandir.setOnAction(e -> abrirCardExpandidoRio());

        Button btnVendasExpandir = FormatadorSeguroUI.criarBotaoCartoon("⤢ Expandir", "#D1FAE5", "#10B981", "#065F46");
        btnVendasExpandir.setOnAction(e -> abrirCardExpandidoVendas());

        Button btnAvisosExpandir = FormatadorSeguroUI.criarBotaoCartoon("⤢ Expandir", "#FEE2E2", "#EF4444", "#991B1B");
        btnAvisosExpandir.setOnAction(e -> abrirCardExpandidoAvisos());

        VBox cClima = montarCardKpiProfundo("🌡️ Temperatura & Clima", lblClimaTempValor, lblClimaDetalhe,
                btnClimaExpandir, "#FFF7ED", "#FFEDD5", "#EA580C", this::abrirCardExpandidoClima);
        VBox cRio = montarCardKpiProfundo("🌊 Regime Hidrológico", lblRioValor, lblRioDetalhe,
                btnRioExpandir, "#F0F9FF", "#E0F2FE", "#0284C7", this::abrirCardExpandidoRio);
        VBox cVendas = montarCardKpiProfundo("📈 Sazonal de Vendas", lblVendasValor, lblVendasDetalhe,
                btnVendasExpandir, "#F6FEF9", "#DCFCE7", "#059669", this::abrirCardExpandidoVendas);
        VBox cAvisos = montarCardKpiProfundo("⚠️ Avisos Prioritários", lblAvisosValor, lblAvisosDetalhe,
                btnAvisosExpandir, "#FFF8F8", "#FEE2E2", "#DC2626", this::abrirCardExpandidoAvisos);

        grid.add(cClima, 0, 0);
        grid.add(cRio, 1, 0);
        grid.add(cVendas, 2, 0);
        grid.add(cAvisos, 3, 0);
        return grid;
    }

    private VBox montarCardKpiProfundo(
            String titulo,
            Label lblValor,
            Label lblDetalhe,
            Button acao,
            String bgTopoHex,
            String bgBaseHex,
            String acentoHex,
            Runnable aoClicarCard) {

        Label lTit = FormatadorSeguroUI.criarLabel(titulo, 12, true, "#0F172A");
        HBox.setHgrow(lTit, Priority.ALWAYS);
        HBox linhaTopo = new HBox(6, lTit, acao);
        linhaTopo.setAlignment(Pos.CENTER_LEFT);

        Label hint = FormatadorSeguroUI.criarLabel("🔍 Clique no card para detalhes e análise IA", 11, false, acentoHex);

        VBox card = new VBox(5, linhaTopo, lblValor, lblDetalhe, hint);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(String.format(
                "-fx-background-color: linear-gradient(to bottom right, %s, %s); "
                        + "-fx-border-color: %s #CBD5E1 #CBD5E1 %s; "
                        + "-fx-border-width: 3px 1px 1px 4px; "
                        + "-fx-background-radius: 8px; -fx-border-radius: 8px; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(15, 23, 42, 0.12), 12, 0, 0, 4); "
                        + "-fx-cursor: hand;",
                bgTopoHex, bgBaseHex, acentoHex, acentoHex));
        card.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button) && aoClicarCard != null) {
                aoClicarCard.run();
            }
        });
        return card;
    }

    private void abrirCardExpandidoClima() {
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        double temp = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;
        double min = snap.clima() != null ? snap.clima().temperaturaMin() : temp - 3.0;
        double max = snap.clima() != null ? snap.clima().temperaturaMax() : temp + 3.0;
        double chuva = snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;
        double vento = snap.clima() != null ? snap.clima().ventoMaxKmh() : 9.0;

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Localidade Monitorada", snap.cidade() + " / " + snap.uf());
        meta.put("Cenário Climático Atual", snap.estado().getIcone() + " " + snap.estado().getTitulo());
        meta.put("Temperatura Média Hoje", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C (Mín %.1f°C / Máx %.1f°C)", temp, min, max));
        meta.put("Precipitação Acumulada", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f mm", chuva));
        meta.put("Velocidade do Vento", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f km/h", vento));
        meta.put("Recomendação Imediata", chuva >= 5.0
                ? "Priorize entregas, embalagens secas, guarda-chuvas e itens térmicos."
                : (temp >= 29.0 ? "Destaque bebidas geladas, gelo, água e proteção contra calor em estoque."
                : "Movimento estável ideal para expositores de frente de caixa."));

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🌡️", "Monitor Climático da Loja", snap.estado().getTitulo(), "#EA580C",
                meta, null, null,
                "➔ Abrir Aba Clima & Mapa", () -> ponte.navegarParaAba("CLIMA"),
                () -> conectorIA.explicarClimaDeHoje(snap));
    }

    private void abrirCardExpandidoRio() {
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Bacia Hidrográfica", snap.bacia());
        meta.put("Cota Fluvial Atual", String.format(FormatadorSeguroUI.LOCALE_BR, "%.2f metros", snap.nivelRioMetros()));
        meta.put("Regime Hidrológico", snap.regimeRio().getRotulo());
        meta.put("Impacto Logístico", descreverImpactoLogistico(snap.regimeRio()));
        meta.put("Planejamento de Compras", snap.regimeRio() == GerenciadorTemaClimatico.RegimeHidrologicoRio.VAZANTE_SECA
                ? "Atenção ao frete fluvial: antecipe reposição de fornecedores regionais em 10 a 15 dias."
                : "Malha logística fluvial regular para recebimento de notas de entrada.");

        String resumo = String.format(FormatadorSeguroUI.LOCALE_BR, "%s • Cota %.1f m (%s)",
                snap.bacia(), snap.nivelRioMetros(), snap.regimeRio().getRotulo());
        String prompt = String.format(FormatadorSeguroUI.LOCALE_BR,
                "O rio em %s está com %s (cota %.1f m). Como isso impacta entregas, custos de frete fluvial e o estoque hoje?",
                snap.bacia(), snap.regimeRio().getRotulo(), snap.nivelRioMetros());

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🌊", "Regime Hidrológico & Logística Fluvial", snap.regimeRio().getRotulo(), "#0284C7",
                meta, null, null,
                "➔ Abrir Aba Clima & Mapa", () -> ponte.navegarParaAba("CLIMA"),
                () -> conectorIA.explicarComContextoOrigem(this, "Home — Regime Hidrológico",
                        "Bacia " + snap.bacia() + " (" + snap.regimeRio().getRotulo() + ")", meta, prompt));
    }

    private void abrirCardExpandidoVendas() {
        Map<String, String> meta = new LinkedHashMap<>();
        if (kpisCache != null) {
            double margem = kpisCache.faturamentoPeriodo().doubleValue() > 0
                    ? (kpisCache.lucroLiquidoPeriodo().doubleValue() / kpisCache.faturamentoPeriodo().doubleValue()) * 100.0
                    : 0.0;
            meta.put("Faturamento Consolidado", FormatadorSeguroUI.formatarMoeda(kpisCache.faturamentoPeriodo()));
            meta.put("Lucro Líquido Real", FormatadorSeguroUI.formatarMoeda(kpisCache.lucroLiquidoPeriodo())
                    + String.format(FormatadorSeguroUI.LOCALE_BR, " (%.1f%%)", margem));
            meta.put("Total de Perdas", FormatadorSeguroUI.formatarMoeda(kpisCache.perdasPeriodo()));
            meta.put("Produtos Cadastrados", kpisCache.totalProdutos() + " itens (" + kpisCache.produtosSaudaveis() + " saudáveis)");
            meta.put("Clientes Ativos", String.valueOf(kpisCache.totalClientes()));
            meta.put("Indicador do Período", kpisCache.comparativoTexto());
        } else {
            meta.put("Faturamento", lblVendasValor.getText());
            meta.put("Resumo", lblVendasDetalhe.getText());
        }

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "📈", "Desempenho Comercial & Margem", "Consolidado Sazonal", "#047857",
                meta, null, null,
                "➔ Ver Estatísticas Completas", () -> ponte.navegarParaAba("ESTATISTICA"),
                () -> conectorIA.explicarPeriodoGrafico("Painel Executivo Home", graficoSazonal.getSerieFiltradaAtual()));
    }

    private void abrirCardExpandidoAvisos() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Total de Alertas Ativos", String.valueOf(avisosCache.size()));
        meta.put("Situação de Estoque", lblAvisosDetalhe.getText());
        long criticos = avisosCache.stream().filter(a -> "CRÍTICO".equals(a.nivel()) || "RESSARCÍVEL".equals(a.nivel())).count();
        long financeiros = avisosCache.stream().filter(a -> "FINANCEIRO".equals(a.nivel())).count();
        meta.put("Alertas Críticos / Perdas", String.valueOf(criticos));
        meta.put("Pendências Financeiras", String.valueOf(financeiros));

        VBox listaRapida = new VBox(6);
        for (int i = 0; i < Math.min(6, avisosCache.size()); i++) {
            AvisoOperacional a = avisosCache.get(i);
            listaRapida.getChildren().add(FormatadorSeguroUI.criarMiniCardInfo(
                    a.nivel() + " • " + a.categoria(),
                    a.titulo() + " — " + a.descricao(),
                    a.bgHex(), a.borderHex(), a.textHex()));
        }
        if (avisosCache.isEmpty()) {
            listaRapida.getChildren().add(FormatadorSeguroUI.criarLabel("Nenhuma pendência crítica no momento.", 12, true, "#047857"));
        }

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "⚠️", "Central de Avisos Prioritários", avisosCache.size() + " alertas", "#B91C1C",
                meta, listaRapida, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Priorização de Alertas",
                        lblAvisosValor.getText() + " • " + lblAvisosDetalhe.getText(),
                        "Tenho " + avisosCache.size() + " alertas operacionais (" + lblAvisosDetalhe.getText()
                                + "). Em que ordem devo agir primeiro hoje para proteger o caixa e evitar perdas?"));
    }

    private VBox criarCartaoResumoSazonal() {
        Label tit = FormatadorSeguroUI.criarLabel("📊 Indicadores Sazonais Consolidados", 13, true, "#0F172A");
        Label sub = FormatadorSeguroUI.criarLabel("Leitura estruturada da série histórica e ambiental em memória", 11, false, "#64748B");

        gridResumoSazonal.setHgap(10);
        gridResumoSazonal.setVgap(8);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            gridResumoSazonal.getColumnConstraints().add(cc);
        }

        VBox box = new VBox(8, new HBox(10, tit, sub), gridResumoSazonal);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        return box;
    }

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

        Platform.runLater(() -> {
            lblStatusSync.setText("Sincronizando...");
            lblStatusSync.setStyle("-fx-background-color: #DBEAFE; -fx-border-color: #93C5FD; -fx-border-width: 1px; "
                    + "-fx-background-radius: 4px; -fx-border-radius: 4px; -fx-padding: 2px 8px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1E40AF;");
        });

        if (!climaJaSolicitado) {
            climaJaSolicitado = true;
            ponte.atualizarClimaERioAtualAsync(false, this::atualizarCardsClimaERio);
        }

        Thread.ofVirtual().start(() -> {
            try {
                List<PontoDiarioSazonal> serie = ponte.carregarSerieSazonalDiaria();
                ResumoDashboardKPIs kpis = ponte.carregarKpisDashboard(serie);
                List<Item<? extends Produto>> lotes = ponte.listarLotesComSaldo();
                List<Nota> notas = ponte.listarNotas();

                List<AvisoOperacional> avisos = construirListaAvisos(lotes, notas);

                Platform.runLater(() -> {
                    this.serieCache = serie;
                    this.kpisCache = kpis;
                    this.avisosCache = avisos;

                    graficoSazonal.definirSerieDados(serie);
                    atualizarKpisVendasEAvisos(kpis, avisos.size());
                    atualizarResumoSazonalCards(serie);
                    renderizarAvisos(avisos);
                    lblStatusSync.setText("Atualizado");
                    lblStatusSync.setStyle("-fx-background-color: #DCFCE7; -fx-border-color: #86EFAC; -fx-border-width: 1px; "
                            + "-fx-background-radius: 4px; -fx-border-radius: 4px; -fx-padding: 2px 8px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #166534;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblStatusSync.setText("Falha ao carregar");
                    lblStatusSync.setStyle("-fx-background-color: #FEE2E2; -fx-border-color: #FCA5A5; -fx-border-width: 1px; "
                            + "-fx-background-radius: 4px; -fx-border-radius: 4px; -fx-padding: 2px 8px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #991B1B;");
                });
            } finally {
                carregando = false;
            }
        });
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        this.ultimoTema = snap;
        setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
        graficoSazonal.aplicarTema(snap);
        atualizarCardsClimaERio(snap);
    }

    private void atualizarCardsClimaERio(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        double temp = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;
        double chuva = snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;
        lblClimaTempValor.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C • %s", temp, snap.estado().getIcone()));
        lblClimaDetalhe.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "%s • Chuva %.0f mm (%s/%s)",
                snap.estado().getTitulo(), chuva, snap.cidade(), snap.uf()));

        lblRioValor.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m • %s",
                snap.nivelRioMetros(), snap.regimeRio().getRotulo()));
        lblRioDetalhe.setText(String.format(FormatadorSeguroUI.LOCALE_BR, "Bacia: %s • %s",
                snap.bacia(), descreverImpactoLogistico(snap.regimeRio())));
    }

    private String descreverImpactoLogistico(GerenciadorTemaClimatico.RegimeHidrologicoRio regime) {
        return switch (regime) {
            case CHEIA -> "Navegação plena; atenção a prazos em área alagável";
            case VAZANTE_SECA -> "Calado reduzido; antecipar compra de fornecedores";
            case NORMAL -> "Fluxo fluvial regular para abastecimento";
        };
    }

    private void atualizarKpisVendasEAvisos(ResumoDashboardKPIs kpis, int qtdAvisos) {
        lblVendasValor.setText(FormatadorSeguroUI.formatarMoeda(kpis.faturamentoPeriodo()));
        lblVendasDetalhe.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "Lucro %s • Perdas %s • %s",
                FormatadorSeguroUI.formatarMoeda(kpis.lucroLiquidoPeriodo()),
                FormatadorSeguroUI.formatarMoeda(kpis.perdasPeriodo()),
                kpis.comparativoTexto()));

        lblAvisosValor.setText(qtdAvisos == 1 ? "1 alerta ativo" : qtdAvisos + " alertas ativos");
        lblAvisosDetalhe.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "%d vencendo em até 15d • %d vencidos • %d produtos",
                kpis.lotesAtencaoValidade(), kpis.lotesVencidos(), kpis.totalProdutos()));
    }

    private void atualizarResumoSazonalCards(List<PontoDiarioSazonal> serie) {
        gridResumoSazonal.getChildren().clear();
        if (serie == null || serie.isEmpty()) {
            gridResumoSazonal.add(FormatadorSeguroUI.criarMiniCardInfo(
                    "Base Histórica Sazonal",
                    "Nenhum histórico diário encontrado ainda. Emita vendas no PDV ou importe dados na aba Configurações.",
                    "#F8FAFC", "#CBD5E1", "#64748B"), 0, 0, 4, 1);
            return;
        }
        int diasComMov = 0;
        double totalFat = 0, totalLuc = 0, totalPer = 0, sumTemp = 0, sumRio = 0;
        PontoDiarioSazonal melhorDia = null;
        PontoDiarioSazonal diaMaiorPerda = null;

        for (PontoDiarioSazonal p : serie) {
            sumTemp += p.tempMedia();
            sumRio += p.nivelRioMetros();
            totalFat += p.faturamento();
            totalLuc += p.lucroLiquido();
            totalPer += p.perdas();
            if (p.possuiMovimentoReal()) {
                diasComMov++;
                if (melhorDia == null || p.faturamento() > melhorDia.faturamento()) melhorDia = p;
                if (p.perdas() > 0 && (diaMaiorPerda == null || p.perdas() > diaMaiorPerda.perdas())) diaMaiorPerda = p;
            }
        }
        double mediaTemp = sumTemp / serie.size();
        double mediaRio = sumRio / serie.size();
        double margem = totalFat > 0 ? (totalLuc / totalFat) * 100.0 : 0.0;

        VBox c1 = FormatadorSeguroUI.criarMiniCardInfo(
                "📅 Janela Monitorada",
                serie.size() + " dias totais\n" + diasComMov + " dias com movimento real",
                "#EFF6FF", "#93C5FD", "#1E40AF");
        VBox c2 = FormatadorSeguroUI.criarMiniCardInfo(
                "💰 Resultado Acumulado",
                FormatadorSeguroUI.formatarMoeda(totalFat) + "\nLucro: " + FormatadorSeguroUI.formatarMoeda(totalLuc)
                        + String.format(FormatadorSeguroUI.LOCALE_BR, " (%.1f%%)", margem),
                "#ECFDF5", "#6EE7B7", "#047857");
        VBox c3 = FormatadorSeguroUI.criarMiniCardInfo(
                "🌦️ Médias Ambientais",
                String.format(FormatadorSeguroUI.LOCALE_BR, "Temp.: %.1f °C\nCota do Rio: %.1f m", mediaTemp, mediaRio),
                "#F0F9FF", "#7DD3FC", "#0369A1");
        String melhorStr = melhorDia != null
                ? FormatadorSeguroUI.formatarDataAmigavel(melhorDia.data()) + " (" + FormatadorSeguroUI.formatarMoeda(melhorDia.faturamento()) + ")"
                : "Sem vendas na janela";
        String perdaStr = diaMaiorPerda != null
                ? "Maior perda: " + FormatadorSeguroUI.formatarMoeda(diaMaiorPerda.perdas())
                : "Perdas totais: " + FormatadorSeguroUI.formatarMoeda(totalPer);
        VBox c4 = FormatadorSeguroUI.criarMiniCardInfo(
                "🏆 Destaque & Perdas",
                melhorStr + "\n" + perdaStr,
                "#FFFBEB", "#FCD34D", "#B45309");

        gridResumoSazonal.add(c1, 0, 0);
        gridResumoSazonal.add(c2, 1, 0);
        gridResumoSazonal.add(c3, 2, 0);
        gridResumoSazonal.add(c4, 3, 0);
    }

    private record AvisoOperacional(
            String nivel,
            String categoria,
            String titulo,
            String descricao,
            Map<String, String> detalhesExpandidos,
            String bgHex,
            String borderHex,
            String textHex,
            String abaDestinoCodigo,
            String rotuloBotaoIrAba,
            Runnable acaoIA
    ) {}

    private List<AvisoOperacional> construirListaAvisos(List<Item<? extends Produto>> lotes, List<Nota> notas) {
        List<AvisoOperacional> avisos = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        lotes.stream()
                .filter(l -> l.getDataValidade() != null && l.getProduto() != null)
                .sorted(Comparator.comparing(Item::getDataValidade))
                .limit(20)
                .forEach(lote -> {
                    long dias = ChronoUnit.DAYS.between(hoje, lote.getDataValidade());
                    if (dias < 0 || dias <= 15) {
                        boolean vencido = dias < 0;
                        String nome = FormatadorSeguroUI.limparNomeProduto(lote.getProduto().getNome());
                        String um = lote.getProduto().getUm() != null ? lote.getProduto().getUm().getSigla() : "UN";
                        String desc = String.format(FormatadorSeguroUI.LOCALE_BR,
                                "Lote #%d • Saldo: %s • Validade: %s (%s)",
                                lote.getId(),
                                FormatadorSeguroUI.formatarQuantidade(lote.getQuantidadeDisponivel(), um),
                                FormatadorSeguroUI.formatarDataAmigavel(lote.getDataValidade()),
                                vencido ? "Vencido há " + Math.abs(dias) + " dias" : "Vence em " + dias + " dias");

                        Map<String, String> det = new LinkedHashMap<>();
                        det.put("Produto", nome);
                        det.put("Identificador do Lote", "Lote #" + lote.getId());
                        det.put("Saldo Disponível", FormatadorSeguroUI.formatarQuantidade(lote.getQuantidadeDisponivel(), um));
                        det.put("Data de Validade", FormatadorSeguroUI.formatarDataAmigavel(lote.getDataValidade()));
                        det.put("Situação", vencido ? "Prazo expirado — recomendar baixa de perda em estoque" : "Próximo ao vencimento — ideal para promoção");
                        det.put("Onde Resolver", vencido
                                ? "Aba Estoque & Produtos → Registrar Perda em Estoque ou Avaliar Fornecedor"
                                : "Aba Balcão PDV ou Estoque & Produtos → Promoção / Baixa Preventiva");

                        avisos.add(new AvisoOperacional(
                                vencido ? "CRÍTICO" : "ATENÇÃO",
                                "Validade de Estoque",
                                nome,
                                desc,
                                det,
                                vencido ? "#FEF2F2" : "#FFFBEB",
                                vencido ? "#FCA5A5" : "#FCD34D",
                                vencido ? "#991B1B" : "#92400E",
                                "ESTOQUE",
                                vencido ? "➔ Ir para Estoque (Baixar Lote)" : "➔ Ir para Estoque & Lotes",
                                () -> conectorIA.explicarComContextoOrigem(
                                        this,
                                        "Home — Avisos de Validade",
                                        "Lote #" + lote.getId() + " (" + nome + ")",
                                        det,
                                        "Explique o risco deste lote específico (#" + lote.getId() + " de " + nome
                                                + ") e dê a melhor ação comercial imediata.")
                        ));
                    }
                });

        for (Nota n : notas) {
            if (n instanceof NotaPerda np && avisos.size() < 24) {
                String forn = np.getPessoa() != null ? np.getPessoa().getNome() : "Fornecedor não vinculado";
                String prodNome = !np.getItens().isEmpty() && np.getItens().get(0).getProduto() != null
                        ? np.getItens().get(0).getProduto().getNome() : "Item de Estoque";
                boolean dentroVal = np.isPerdaDentroDaValidade();
                String motivoTxt = np.getDescricao() != null && !np.getDescricao().isBlank()
                        ? np.getDescricao() : np.getTipoPerda();
                String dataPerdaFmt = np.getDataGerada() != null ? FormatadorSeguroUI.formatarDataHoraAmigavel(np.getDataGerada()) : "Sem data";

                Map<String, String> det = new LinkedHashMap<>();
                det.put("Documento", "Nota de Perda #" + np.getId());
                det.put("Data da Ocorrência", dataPerdaFmt);
                det.put("Produto Envolvido", FormatadorSeguroUI.limparNomeProduto(prodNome));
                det.put("Origem / Fornecedor", forn);
                det.put("Prejuízo Apurado", FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda()));
                det.put("Motivo Declarado", FormatadorSeguroUI.tratarParaExibicao(motivoTxt, "Avaria / validade"));
                det.put("Abatimento com Fornecedor", dentroVal ? "Elegível (deterioração dentro da validade)" : "Não elegível (perda interna)");

                avisos.add(new AvisoOperacional(
                        dentroVal ? "RESSARCÍVEL" : "PERDA INTERNA",
                        "Nota de Perda #" + np.getId(),
                        FormatadorSeguroUI.limparNomeProduto(prodNome),
                        String.format(FormatadorSeguroUI.LOCALE_BR, "Prejuízo: %s • Fornecedor: %s • %s",
                                FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda()),
                                forn,
                                FormatadorSeguroUI.tratarParaExibicao(motivoTxt, "Avaria / validade")),
                        det,
                        "#FEF2F2", "#FECACA", "#991B1B",
                        dentroVal ? "FINANCEIRO" : "ESTOQUE",
                        dentroVal ? "➔ Ir para Financeiro (Ressarcir)" : "➔ Ir para Estoque (Editar Perda)",
                        () -> conectorIA.analisarPerdaERessarcimento(
                                this,
                                "Home — Avisos de Perdas",
                                np.getId(),
                                dataPerdaFmt,
                                prodNome,
                                forn,
                                motivoTxt,
                                np.getValorCustoPerda(),
                                dentroVal,
                                "Tipo de perda: " + FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "AVARIA"))
                ));
            }
            if (n.getPagamentos() != null && avisos.size() < 28) {
                for (NotaPagamento np : n.getPagamentos()) {
                    if (ponte.existeParcelaOuTituloEmAberto(np)) {
                        String pessoa = n.getPessoa() != null ? n.getPessoa().getNome() : "Cliente/Fornecedor";
                        Map<String, String> det = new LinkedHashMap<>();
                        det.put("Nota Vinculada", "#" + n.getId());
                        det.put("Entidade", pessoa);
                        det.put("Data Emissão", n.getDataGerada() != null ? FormatadorSeguroUI.formatarDataHoraAmigavel(n.getDataGerada()) : "-");
                        det.put("Valor em Aberto", FormatadorSeguroUI.formatarMoeda(np.calcularValorFinal()));
                        det.put("Meio de Pagamento", np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "A Prazo");
                        det.put("Onde Quitar", "Aba Financeiro & Contabilidade → Contas a Receber & Pagar");

                        avisos.add(new AvisoOperacional(
                                "FINANCEIRO",
                                "Título em Aberto",
                                "Nota #" + n.getId() + " — " + pessoa,
                                String.format(FormatadorSeguroUI.LOCALE_BR, "Saldo aberto: %s • Forma: %s",
                                        FormatadorSeguroUI.formatarMoeda(np.calcularValorFinal()),
                                        np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "A Prazo"),
                                det,
                                "#EFF6FF", "#BFDBFE", "#1E40AF",
                                "FINANCEIRO",
                                "➔ Ir para Financeiro (Quitar)",
                                () -> conectorIA.explicarComContextoOrigem(
                                        this,
                                        "Home — Título em Aberto",
                                        "Nota #" + n.getId() + " (" + pessoa + ")",
                                        det,
                                        "Analise a pendência financeira específica da Nota #" + n.getId() + " de " + pessoa
                                                + " e sugira como proceder na quitação ou cobrança.")
                        ));
                        break;
                    }
                }
            }
        }
        return avisos;
    }

    private void renderizarAvisos(List<AvisoOperacional> avisos) {
        boxListaAvisos.getChildren().clear();
        if (avisos.isEmpty()) {
            Label ok = FormatadorSeguroUI.criarLabel(
                    "Nenhum alerta operacional crítico no momento. Lotes dentro da validade e títulos quitados.",
                    12, true, "#047857");
            VBox vazio = new VBox(ok);
            vazio.setPadding(new Insets(14));
            vazio.setStyle(FormatadorSeguroUI.estiloCardCartoon("#ECFDF5", "#A7F3D0"));
            boxListaAvisos.getChildren().add(vazio);
            return;
        }

        for (AvisoOperacional a : avisos) {
            CardMacioExpansivel card = new CardMacioExpansivel(
                    "CRÍTICO".equals(a.nivel()) || "RESSARCÍVEL".equals(a.nivel()) ? "🚨" : "📌",
                    a.titulo(),
                    a.descricao(),
                    null,
                    false
            );
            card.configurarBadge(a.nivel() + " • " + a.categoria(), a.bgHex(), a.borderHex(), a.textHex());
            card.configurarBotaoIA("🤖 Explicar IA", a.acaoIA());
            card.definirBordaDestaque(a.borderHex());

            Button btnIrAba = FormatadorSeguroUI.criarBotaoCartoon(
                    a.rotuloBotaoIrAba() != null ? a.rotuloBotaoIrAba() : "➔ Ir para Aba Resolver",
                    "#EFF6FF", "#2563EB", "#1E3A8A");
            btnIrAba.setOnAction(e -> ponte.navegarParaAba(a.abaDestinoCodigo()));

            Button btnFicha = FormatadorSeguroUI.criarBotaoCartoon("🔍 Abrir Ficha Completa", "#FFFFFF", "#CBD5E1", "#0F172A");
            btnFicha.setOnAction(e -> DialogoCorporativoModal.exibirCardDetalhes(
                    this, "⚠️", a.titulo(), a.nivel() + " • " + a.categoria(), a.textHex(),
                    a.detalhesExpandidos(),
                    null,
                    null,
                    a.rotuloBotaoIrAba(),
                    () -> ponte.navegarParaAba(a.abaDestinoCodigo()),
                    a.acaoIA()));

            HBox barraBotoes = new HBox(8, btnIrAba, btnFicha);
            card.definirConteudo(
                    FormatadorSeguroUI.criarGradeMetadadosCards(a.detalhesExpandidos()),
                    barraBotoes
            );
            boxListaAvisos.getChildren().add(card);
        }
    }
}
