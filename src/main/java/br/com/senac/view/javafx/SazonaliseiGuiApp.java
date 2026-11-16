package br.com.senac.view.javafx;

import br.com.senac.view.javafx.abas.AbaBalcaoNotasView;
import br.com.senac.view.javafx.abas.AbaCatalogoEstoquePessoasView;
import br.com.senac.view.javafx.abas.AbaConfiguracaoAmbienteView;
import br.com.senac.view.javafx.abas.AbaControleOperacionalIAView;
import br.com.senac.view.javafx.abas.AbaEstatisticaAvancadaView;
import br.com.senac.view.javafx.abas.AbaFinanceiroContabilidadeView;
import br.com.senac.view.javafx.abas.AbaInicioClimaView;
import br.com.senac.view.javafx.abas.AbaMapaEventosGpsView;
import br.com.senac.view.javafx.abas.AbaPlanejamentoDemandaKanbanView;
import br.com.senac.view.javafx.componentes.PainelCopilotoIAChat;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicação principal JavaFX do Sistema Sazonalisei ERP.
 * Interface corporativa compacta com 9 módulos particionados, barra lateral recolhível,
 * carregamento sob demanda (lazy), barra climática/hidrológica e balão flutuante do Assistente IA.
 */
public class SazonaliseiGuiApp extends Application {

    private record ItemMenuLateral(ToggleButton botao, String icone, String rotuloCompleto) {}

    private GerenciadorTemaClimatico gerenciadorTema;
    private PonteRoteadorIA ponte;
    private ConectorProblemasIA conectorIA;

    private StackPane rootOverlay;
    private BorderPane layoutCorporativo;
    private Scene scene;
    private HBox headerCorporativo;
    private VBox sidebarEsquerda;
    private StackPane areaCentral;

    private PainelCopilotoIAChat painelCopiloto;
    private Button btnBolhaChatFlutuante;
    private boolean copilotoAberto = false;
    private boolean sidebarRecolhida = false;

    private Label lblMarcaTitulo;
    private Label lblMarcaSub;
    private Button btnPilulaClimaRio;
    private Button btnRecolherSidebarHeader;
    private Button btnRecolherSidebarMenu;
    private Label lblTitMenu;
    private Button btnAjudaSidebar;

    private final ToggleGroup grupoAbas = new ToggleGroup();
    private final List<ItemMenuLateral> itensMenu = new ArrayList<>();

    // Os 9 módulos corporativos
    private AbaInicioClimaView abaHome;
    private AbaBalcaoNotasView abaPdvBalcao;
    private AbaCatalogoEstoquePessoasView abaEstoqueProdutos;
    private AbaFinanceiroContabilidadeView abaFinanceiro;
    private AbaEstatisticaAvancadaView abaEstatistica;
    private AbaMapaEventosGpsView abaClimaMapa;
    private AbaPlanejamentoDemandaKanbanView abaPlanejamentoKanban;
    private AbaControleOperacionalIAView abaControleIA;
    private AbaConfiguracaoAmbienteView abaConfig;

    @Override
    public void start(Stage stage) {
        this.gerenciadorTema = new GerenciadorTemaClimatico();
        this.ponte = new PonteRoteadorIA(gerenciadorTema);
        this.conectorIA = new ConectorProblemasIA(ponte);

        // 1. Popover Flutuante do Assistente IA + Balão
        this.painelCopiloto = new PainelCopilotoIAChat(ponte);
        this.painelCopiloto.setAoMinimizar(() -> alternarCopilotoFlutuante(false));
        this.painelCopiloto.setVisible(false);
        this.painelCopiloto.setManaged(false);
        this.conectorIA.vincularPainelCopiloto(painelCopiloto, () -> alternarCopilotoFlutuante(true));

        this.btnBolhaChatFlutuante = new Button("💬 Assistente IA");
        this.btnBolhaChatFlutuante.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-width: 1px; "
                + "-fx-background-radius: 22px; -fx-border-radius: 22px; -fx-padding: 10px 18px; "
                + "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF; -fx-cursor: hand; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(15, 23, 42, 0.30), 14, 0, 0, 4);");
        this.btnBolhaChatFlutuante.setOnAction(e -> alternarCopilotoFlutuante(true));

        ponte.registrarOuvinteBandeja(this::atualizarContadorBolha);

        // 2. As 9 Abas Corporativas (nenhuma consulta pesada no construtor!)
        this.abaHome = new AbaInicioClimaView(ponte, conectorIA);
        this.abaPdvBalcao = new AbaBalcaoNotasView(ponte, conectorIA);
        this.abaEstoqueProdutos = new AbaCatalogoEstoquePessoasView(ponte, conectorIA);
        this.abaFinanceiro = new AbaFinanceiroContabilidadeView(ponte, conectorIA);
        this.abaEstatistica = new AbaEstatisticaAvancadaView(ponte, conectorIA);
        this.abaClimaMapa = new AbaMapaEventosGpsView(ponte, conectorIA);
        this.abaPlanejamentoKanban = new AbaPlanejamentoDemandaKanbanView(ponte, conectorIA);
        this.abaControleIA = new AbaControleOperacionalIAView(ponte, conectorIA);
        this.abaConfig = new AbaConfiguracaoAmbienteView(ponte, conectorIA);

        // 3. Layout Corporativo Base
        this.layoutCorporativo = new BorderPane();
        this.headerCorporativo = criarHeaderCorporativo();
        this.sidebarEsquerda = criarBarraLateral();
        this.areaCentral = new StackPane(abaHome);
        this.areaCentral.setMinWidth(520);

        this.layoutCorporativo.setTop(headerCorporativo);
        this.layoutCorporativo.setLeft(sidebarEsquerda);
        this.layoutCorporativo.setCenter(areaCentral);

        // 4. Overlay com Balão Flutuante ancorado em Pos.BOTTOM_RIGHT
        StackPane.setAlignment(btnBolhaChatFlutuante, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(btnBolhaChatFlutuante, new Insets(0, 22, 22, 0));

        StackPane.setAlignment(painelCopiloto, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(painelCopiloto, new Insets(0, 22, 22, 0));

        this.rootOverlay = new StackPane(layoutCorporativo, painelCopiloto, btnBolhaChatFlutuante);

        this.scene = new Scene(rootOverlay, 1366, 820);
        stage.setMinWidth(1060);
        stage.setMinHeight(680);
        stage.setTitle("Sazonalisei ERP — Gestão Comercial, Estoque Inteligente & Sazonalidade");
        stage.setScene(scene);

        gerenciadorTema.registrarOuvinte(snap -> Platform.runLater(() -> aplicarTemaGlobal(snap)));
        aplicarTemaGlobal(gerenciadorTema.getSnapshotAtual());

        stage.show();

        // Ativa APENAS a Home ao abrir (as demais carregam sob demanda ao clicar)
        ponte.registrarNavegadorAba(this::navegarParaAbaPorCodigo);
        ativarSomenteAba(abaHome);
    }

    private void navegarParaAbaPorCodigo(String codigo) {
        if (codigo == null) return;
        int idx = switch (codigo.toUpperCase(java.util.Locale.ROOT)) {
            case "HOME", "INICIO" -> 0;
            case "PDV", "BALCAO" -> 1;
            case "ESTOQUE", "PRODUTOS" -> 2;
            case "FINANCEIRO", "CONTABILIDADE" -> 3;
            case "ESTATISTICA", "ANALISE" -> 4;
            case "CLIMA", "MAPA", "EVENTOS" -> 5;
            case "KANBAN", "DEMANDA" -> 6;
            case "IA", "CONTROLE_IA" -> 7;
            case "CONFIG", "CSV" -> 8;
            default -> -1;
        };
        if (idx >= 0 && idx < itensMenu.size()) {
            ToggleButton tb = itensMenu.get(idx).botao();
            tb.fire();
        }
    }

    private HBox criarHeaderCorporativo() {
        this.btnRecolherSidebarHeader = FormatadorSeguroUI.criarBotaoCartoon("☰", "#F1F5F9", "#CBD5E1", "#0F172A");
        this.btnRecolherSidebarHeader.setTooltip(new Tooltip("Recolher ou expandir barra lateral"));
        this.btnRecolherSidebarHeader.setOnAction(e -> alternarSidebarRecolhida());

        this.lblMarcaTitulo = FormatadorSeguroUI.criarLabel("📊 Sazonalisei ERP", 15, true, "#0F172A");
        this.lblMarcaTitulo.setMaxWidth(Region.USE_PREF_SIZE);
        this.lblMarcaSub = FormatadorSeguroUI.criarLabel("Gestão Comercial, Estoque & Inteligência Sazonal", 11, false, "#64748B");
        this.lblMarcaSub.setMaxWidth(Region.USE_PREF_SIZE);

        VBox marca = new VBox(1, lblMarcaTitulo, lblMarcaSub);
        marca.setAlignment(Pos.CENTER_LEFT);
        marca.setMinWidth(Region.USE_PREF_SIZE);

        this.btnPilulaClimaRio = FormatadorSeguroUI.criarBotaoCartoon(
                "☀️ Carregando clima e rio...", "#F8FAFC", "#CBD5E1", "#0F172A");
        this.btnPilulaClimaRio.setOnAction(e ->
                conectorIA.explicarClimaDeHoje(gerenciadorTema.getSnapshotAtual()));

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Button btnAbrirIAHeader = FormatadorSeguroUI.criarBotaoCartoon(
                "💬 Assistente IA", "#0F172A", "#0F172A", "#FFFFFF");
        btnAbrirIAHeader.setOnAction(e -> alternarCopilotoFlutuante(!copilotoAberto));

        HBox header = new HBox(12, btnRecolherSidebarHeader, marca, espaco, btnPilulaClimaRio, btnAbrirIAHeader);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 16, 8, 16));
        header.setMinHeight(Region.USE_PREF_SIZE);
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 1px 0;");
        return header;
    }

    private VBox criarBarraLateral() {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10, 8, 12, 8));
        box.setPrefWidth(212);
        box.setMinWidth(200);
        box.setMaxWidth(215);
        box.setStyle("-fx-background-color: #0F172A;");

        this.lblTitMenu = FormatadorSeguroUI.criarLabel("NAVEGAÇÃO", 11, true, "#94A3B8");
        HBox.setHgrow(lblTitMenu, Priority.ALWAYS);

        this.btnRecolherSidebarMenu = FormatadorSeguroUI.criarBotaoCartoon("◀", "#1E293B", "#334155", "#CBD5E1");
        this.btnRecolherSidebarMenu.setTooltip(new Tooltip("Recolher barra lateral"));
        this.btnRecolherSidebarMenu.setOnAction(e -> alternarSidebarRecolhida());

        HBox topoMenu = new HBox(6, lblTitMenu, btnRecolherSidebarMenu);
        topoMenu.setAlignment(Pos.CENTER_LEFT);
        topoMenu.setPadding(new Insets(0, 4, 6, 4));
        box.getChildren().add(topoMenu);

        adicionarBotaoAba(box, "🏠", "Home", abaHome, true);
        adicionarBotaoAba(box, "🛒", "PDV Balcão", abaPdvBalcao, false);
        adicionarBotaoAba(box, "📦", "Estoque & Produtos", abaEstoqueProdutos, false);
        adicionarBotaoAba(box, "💰", "Financeiro & Contab.", abaFinanceiro, false);
        adicionarBotaoAba(box, "📊", "Estatística & Demanda", abaEstatistica, false);
        adicionarBotaoAba(box, "🌤️", "Clima, Rios & Mapa", abaClimaMapa, false);
        adicionarBotaoAba(box, "🗂️", "Kanban Demanda", abaPlanejamentoKanban, false);
        adicionarBotaoAba(box, "🤖", "Controle por IA", abaControleIA, false);
        adicionarBotaoAba(box, "⚙️", "Configurações & CSV", abaConfig, false);

        Region espaco = new Region();
        VBox.setVgrow(espaco, Priority.ALWAYS);

        this.btnAjudaSidebar = FormatadorSeguroUI.criarBotaoCartoon("❓ Guia Rápido IA", "#1E293B", "#334155", "#E2E8F0");
        this.btnAjudaSidebar.setMaxWidth(Double.MAX_VALUE);
        this.btnAjudaSidebar.setTooltip(new Tooltip("Pedir orientação rápida ao Assistente IA"));
        this.btnAjudaSidebar.setOnAction(e -> conectorIA.perguntarAoCopiloto("Guia do Sistema",
                "Me dê um resumo objetivo de onde realizar: vendas rápidas e pagamentos no PDV Balcão, perda em estoque vs pós-venda, "
                        + "notas de entrada e lucros contábeis, causas das flutuações e planejamento Kanban por clima e eventos."));

        box.getChildren().addAll(espaco, btnAjudaSidebar);
        return box;
    }

    private void alternarSidebarRecolhida() {
        this.sidebarRecolhida = !this.sidebarRecolhida;
        if (sidebarRecolhida) {
            sidebarEsquerda.setPrefWidth(62);
            sidebarEsquerda.setMinWidth(60);
            sidebarEsquerda.setMaxWidth(62);
            lblTitMenu.setVisible(false);
            lblTitMenu.setManaged(false);
            btnRecolherSidebarMenu.setText("▶");
            btnRecolherSidebarMenu.setTooltip(new Tooltip("Expandir barra lateral"));
            btnAjudaSidebar.setText("❓");
        } else {
            sidebarEsquerda.setPrefWidth(212);
            sidebarEsquerda.setMinWidth(200);
            sidebarEsquerda.setMaxWidth(215);
            lblTitMenu.setVisible(true);
            lblTitMenu.setManaged(true);
            btnRecolherSidebarMenu.setText("◀");
            btnRecolherSidebarMenu.setTooltip(new Tooltip("Recolher barra lateral"));
            btnAjudaSidebar.setText("❓ Guia Rápido IA");
        }
        atualizarEstilosAbasLaterais();
    }

    private void adicionarBotaoAba(VBox sidebar, String icone, String rotulo, Node conteudoAba, boolean selecionada) {
        ToggleButton tb = new ToggleButton(icone + " " + rotulo);
        tb.setToggleGroup(grupoAbas);
        tb.setSelected(selecionada);
        tb.setMaxWidth(Double.MAX_VALUE);
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.setTooltip(new Tooltip(icone + " " + rotulo));
        tb.setOnAction(e -> {
            tb.setSelected(true);
            areaCentral.getChildren().setAll(conteudoAba);
            atualizarEstilosAbasLaterais();
            ativarSomenteAba(conteudoAba);
        });
        itensMenu.add(new ItemMenuLateral(tb, icone, icone + " " + rotulo));
        sidebar.getChildren().add(tb);
    }

    private void ativarSomenteAba(Node aba) {
        abaHome.setAtiva(aba == abaHome);
        abaPdvBalcao.setAtiva(aba == abaPdvBalcao);
        abaEstoqueProdutos.setAtiva(aba == abaEstoqueProdutos);
        abaFinanceiro.setAtiva(aba == abaFinanceiro);
        abaEstatistica.setAtiva(aba == abaEstatistica);
        abaClimaMapa.setAtiva(aba == abaClimaMapa);
        abaPlanejamentoKanban.setAtiva(aba == abaPlanejamentoKanban);
        abaControleIA.setAtiva(aba == abaControleIA);
        abaConfig.setAtiva(aba == abaConfig);
    }

    private void alternarCopilotoFlutuante(boolean abrir) {
        this.copilotoAberto = abrir;
        painelCopiloto.setVisible(abrir);
        painelCopiloto.setManaged(abrir);
        btnBolhaChatFlutuante.setVisible(!abrir);
        btnBolhaChatFlutuante.setManaged(!abrir);
    }

    private void atualizarContadorBolha() {
        int qtd = ponte.obterBandejaAtiva().getItens().size();
        btnBolhaChatFlutuante.setText(qtd > 0 ? "💬 Assistente IA (" + qtd + ")" : "💬 Assistente IA");
    }

    private void aplicarTemaGlobal(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        var e = snap.estado();

        if (scene != null) {
            scene.getStylesheets().setAll(gerenciadorTema.gerarCssGlobalDataUri());
        }
        String bgEfetivo = snap.resolverBgApp();
        layoutCorporativo.setStyle("-fx-background-color: " + bgEfetivo + ";");

        String textoPilula = snap.descreverClimaCurto() + "  |  " + snap.descreverRioCurto();
        btnPilulaClimaRio.setText(textoPilula);
        FormatadorSeguroUI.aplicarEstiloBotaoCartoon(
                btnPilulaClimaRio, snap.resolverBgSoft(), snap.resolverBorderCard(), snap.resolverTextPrimary(), 12);
        headerCorporativo.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 0 0 1px 0;",
                snap.resolverBgCard(), snap.resolverBorderCard()));
        lblMarcaTitulo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + snap.resolverTextPrimary() + ";");
        lblMarcaSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + snap.resolverTextSecondary() + ";");

        atualizarEstilosAbasLaterais();
        painelCopiloto.aplicarTema(snap);
        abaHome.aplicarTema(snap);
        abaPdvBalcao.aplicarTema(snap);
        abaEstoqueProdutos.aplicarTema(snap);
        abaFinanceiro.aplicarTema(snap);
        abaEstatistica.aplicarTema(snap);
        abaClimaMapa.aplicarTema(snap);
        abaPlanejamentoKanban.aplicarTema(snap);
        abaControleIA.aplicarTema(snap);
        abaConfig.aplicarTema(snap);
        aplicarBgEfetivoNasAbas(bgEfetivo);
    }

    private void aplicarBgEfetivoNasAbas(String bgEfetivo) {
        if (bgEfetivo == null || bgEfetivo.isBlank()) return;
        for (Node n : new Node[]{
                abaHome, abaPdvBalcao, abaEstoqueProdutos, abaFinanceiro,
                abaEstatistica, abaClimaMapa, abaPlanejamentoKanban, abaControleIA, abaConfig
        }) {
            if (n != null) {
                n.setStyle("-fx-background-color: " + bgEfetivo + ";");
            }
        }
    }

    private void atualizarEstilosAbasLaterais() {
        var accent = gerenciadorTema.getSnapshotAtual().estado().getAccentMain();
        for (ItemMenuLateral item : itensMenu) {
            ToggleButton tb = item.botao();
            boolean sel = tb.isSelected();
            tb.setText(sidebarRecolhida ? item.icone() : item.rotuloCompleto());
            tb.setAlignment(sidebarRecolhida ? Pos.CENTER : Pos.CENTER_LEFT);
            tb.setStyle(String.format(
                    "-fx-background-color: %s; -fx-border-color: transparent transparent transparent %s; "
                            + "-fx-border-width: 0 0 0 3px; -fx-background-radius: 6px; "
                            + "-fx-padding: %s; -fx-font-size: %s; -fx-font-weight: %s; "
                            + "-fx-text-fill: %s; -fx-cursor: hand;",
                    sel ? "#1E293B" : "transparent",
                    sel ? accent : "transparent",
                    sidebarRecolhida ? "9px 4px" : "9px 12px",
                    sidebarRecolhida ? "15px" : "13px",
                    sel ? "bold" : "600",
                    sel ? "#FFFFFF" : "#CBD5E1"
            ));
        }
    }
}
