package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.CardKanbanExterno;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.EventoMapaGps;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aba 7: Planejamento de Demanda em Kanban (Eventos × Clima × Rios × Clientes × Fornecedores).
 * Permite mapear demanda antecipada, planejar venda de itens específicos perto de eventos (ex.: guarda-chuvas em dia de chuva),
 * estruturar pedidos a fornecedores e gerar ideias de produtos sob demanda com apoio da IA.
 */
public class AbaPlanejamentoDemandaKanbanView extends VBox {

    private static final String[] TITULOS_COLUNAS = {
            "📡 Radar Externo (Eventos, Clima & Rio)",
            "💡 Estratégia & Demanda do Cliente",
            "🚚 Abastecimento & Fornecedor",
            "🚀 Campanha Ativa / Concluído"
    };

    private static final String[] CORES_COLUNAS = {
            "#1D4ED8", "#B45309", "#047857", "#6D28D9"
    };

    private static final String[] BG_COLUNAS = {
            "#EFF6FF", "#FFFBEB", "#ECFDF5", "#F5F3FF"
    };

    private static class ItemKanban {
        final String id;
        String titulo;
        String foco; // "CLIENTE", "EVENTO", "FORNECEDOR"
        String origem;
        String gatilhoAmbiental;
        String produtoSugerido;
        String fornecedorSugerido;
        String estrategiaPratica;
        BigDecimal precoSugerido;
        BigDecimal qtdCompraSugerida;
        int coluna;

        ItemKanban(String id, String titulo, String foco, String origem, String gatilhoAmbiental,
                   String produtoSugerido, String fornecedorSugerido, String estrategiaPratica,
                   BigDecimal precoSugerido, BigDecimal qtdCompraSugerida, int coluna) {
            this.id = id;
            this.titulo = titulo;
            this.foco = foco;
            this.origem = origem;
            this.gatilhoAmbiental = gatilhoAmbiental;
            this.produtoSugerido = produtoSugerido;
            this.fornecedorSugerido = fornecedorSugerido;
            this.estrategiaPratica = estrategiaPratica;
            this.precoSugerido = precoSugerido;
            this.qtdCompraSugerida = qtdCompraSugerida;
            this.coluna = Math.max(0, Math.min(3, coluna));
        }
    }

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final Label lblContextoChips = FormatadorSeguroUI.criarLabel("Carregando radar sazonal...", 12, true, "#334155");
    private final Label lblStatus = FormatadorSeguroUI.criarLabel("", 12, true, "#047857");

    private final ToggleButton tgTodos = new ToggleButton("Todos os Focos");
    private final ToggleButton tgClientes = new ToggleButton("👤 Clientes");
    private final ToggleButton tgEventos = new ToggleButton("🎪 Eventos & Clima");
    private final ToggleButton tgFornecedores = new ToggleButton("🚚 Fornecedores");

    private final VBox[] caixasColunas = new VBox[4];
    private final Label[] contadoresColunas = new Label[4];
    private final VBox boxCriadorRapido = new VBox(8);

    // Campos do criador manual de card Kanban
    private final TextField txtTituloCard = FormatadorSeguroUI.criarCampoTextoCartoon("Ex.: Guarda-chuvas p/ Festival da Chuva", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFocoCard = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbColunaCard = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtProdutoCard = FormatadorSeguroUI.criarCampoTextoCartoon("Ex.: Guarda-chuva Compacto Reforçado", "#CBD5E1", "#0F172A");
    private final ComboBox<Pessoa> cbFornecedorCard = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtPrecoCard = FormatadorSeguroUI.criarCampoTextoCartoon("35,00", "#CBD5E1", "#0F172A");
    private final TextArea txtEstrategiaCard = FormatadorSeguroUI.criarAreaTextoCartoon("Descreva a abordagem para clientes ou compra com fornecedor...", "#CBD5E1", "#0F172A");

    private final List<ItemKanban> cards = new ArrayList<>();
    private ItemKanban cardSelecionado = null;
    private final VBox boxRadarCatalogoKanban = new VBox(6);
    private List<EventoMapaGps> ultimosEventos = List.of();
    private List<ClimaDiario> ultimasPrevisoes = List.of();
    private List<Pessoa> ultimosFornecedores = List.of();
    private List<Produto> ultimosProdutos = List.of();

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaPlanejamentoDemandaKanbanView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox header = criarCabecalho();
        VBox barraFiltros = criarBarraFiltrosECriador();
        boxRadarCatalogoKanban.setPadding(new Insets(10, 12, 10, 12));
        boxRadarCatalogoKanban.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));
        GridPane quadro = criarQuadroKanban();
        VBox.setVgrow(quadro, Priority.ALWAYS);

        getChildren().addAll(header, barraFiltros, boxCriadorRapido, boxRadarCatalogoKanban, lblStatus, quadro);

        // Setas Esquerda / Direita movem o card selecionado até a esteira de Conclusão
        addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (!abaAtiva || cardSelecionado == null) return;
            if (getScene() != null && getScene().getFocusOwner() instanceof TextInputControl) return;
            if (ev.getCode() == KeyCode.RIGHT && cardSelecionado.coluna < 3) {
                moverColuna(cardSelecionado, cardSelecionado.coluna + 1);
                lblStatus.setText("➡ Card '" + cardSelecionado.titulo + "' movido para: " + TITULOS_COLUNAS[cardSelecionado.coluna]);
                ev.consume();
            } else if (ev.getCode() == KeyCode.LEFT && cardSelecionado.coluna > 0) {
                moverColuna(cardSelecionado, cardSelecionado.coluna - 1);
                lblStatus.setText("⬅ Card '" + cardSelecionado.titulo + "' movido para: " + TITULOS_COLUNAS[cardSelecionado.coluna]);
                ev.consume();
            } else if (ev.getCode() == KeyCode.ENTER) {
                abrirDetalhesCardKanban(cardSelecionado);
                ev.consume();
            }
        });

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) carregarSeNecessario();
        });
        ponte.registrarOuvinteKanban(this::sincronizarCardsExternosERenderizar);
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("🗂️", "Planejamento de Demanda Kanban (Clima × Eventos × Rios × Fornecedores)", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Transforme previsão do tempo, nível dos rios e eventos próximos em estratégias para clientes e pedidos antecipados aos fornecedores.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub, lblContextoChips);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnIdeiasIA = FormatadorSeguroUI.criarBotaoIA("🤖 Criar Ideias Sob Demanda (IA)", this::acionarIAPlanejamentoKanban);
        Button btnRecarregar = FormatadorSeguroUI.criarBotaoCartoon("↻ Atualizar Radar", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnRecarregar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        HBox topo = new HBox(10, boxTit, btnRecarregar, btnIdeiasIA);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    private VBox criarBarraFiltrosECriador() {
        ToggleGroup grp = new ToggleGroup();
        tgTodos.setToggleGroup(grp);
        tgClientes.setToggleGroup(grp);
        tgEventos.setToggleGroup(grp);
        tgFornecedores.setToggleGroup(grp);
        tgTodos.setSelected(true);

        Runnable aoFiltrar = () -> {
            atualizarEstilosFiltros();
            renderizarQuadro();
        };
        tgTodos.setOnAction(e -> aoFiltrar.run());
        tgClientes.setOnAction(e -> aoFiltrar.run());
        tgEventos.setOnAction(e -> aoFiltrar.run());
        tgFornecedores.setOnAction(e -> aoFiltrar.run());
        atualizarEstilosFiltros();

        Button btnToggleCriador = FormatadorSeguroUI.criarBotaoCartoon("➕ Novo Card / Estratégia", "#0F172A", "#0F172A", "#FFFFFF");
        btnToggleCriador.setOnAction(e -> {
            boolean visivel = !boxCriadorRapido.isVisible();
            boxCriadorRapido.setVisible(visivel);
            boxCriadorRapido.setManaged(visivel);
        });

        Button btnProdRapido = FormatadorSeguroUI.criarBotaoCartoon("📦 Cadastrar Produto Sob Demanda", "#047857", "#065F46", "#FFFFFF");
        btnProdRapido.setOnAction(e -> abrirCadastroRapidoProdutoSobDemanda(
                txtProdutoCard.getText().isBlank() ? "Guarda-chuva Compacto Evento" : txtProdutoCard.getText(),
                new BigDecimal("35.00"),
                "Produto cadastrado via planejamento de demanda climática."));

        FlowPane linha = new FlowPane(8, 8, tgTodos, tgClientes, tgEventos, tgFornecedores, btnToggleCriador, btnProdRapido);
        linha.setAlignment(Pos.CENTER_LEFT);

        configurarCriadorRapido();

        VBox box = new VBox(8, linha);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        return box;
    }

    private void configurarCriadorRapido() {
        boxCriadorRapido.setVisible(false);
        boxCriadorRapido.setManaged(false);
        boxCriadorRapido.setPadding(new Insets(12));
        boxCriadorRapido.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));

        cbFocoCard.getItems().setAll("CLIENTE", "EVENTO", "FORNECEDOR");
        cbFocoCard.getSelectionModel().selectFirst();
        cbColunaCard.getItems().setAll(TITULOS_COLUNAS);
        cbColunaCard.getSelectionModel().select(1);
        txtEstrategiaCard.setPrefRowCount(2);
        txtPrecoCard.setPrefWidth(110);

        Button btnSalvarCard = FormatadorSeguroUI.criarBotaoCartoon("✔ Adicionar ao Quadro Kanban", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnSalvarCard.setOnAction(e -> criarCardManualConfirmado());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.3);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Título da Estratégia / Card", 11, true, "#334155"), txtTituloCard), 0, 0);
        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Foco Principal", 11, true, "#334155"), cbFocoCard), 1, 0);
        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Coluna Inicial", 11, true, "#334155"), cbColunaCard), 2, 0);

        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Produto / Combo Alvo", 11, true, "#334155"), txtProdutoCard), 0, 1);
        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Fornecedor Parceiro", 11, true, "#334155"), cbFornecedorCard), 1, 1);
        grid.add(new VBox(3, FormatadorSeguroUI.criarLabel("Preço Venda Sugerido (R$)", 11, true, "#334155"), txtPrecoCard), 2, 1);

        HBox rod = new HBox(10,
                new VBox(3, FormatadorSeguroUI.criarLabel("Ação Prática / Motivo Clima-Rio-Evento", 11, true, "#334155"), txtEstrategiaCard),
                btnSalvarCard);
        HBox.setHgrow(rod.getChildren().get(0), Priority.ALWAYS);
        rod.setAlignment(Pos.BOTTOM_RIGHT);

        boxCriadorRapido.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📝 Criar Nova Estratégia Personalizada no Kanban", 13, true, "#1E40AF"),
                grid, rod);
    }

    private GridPane criarQuadroKanban() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);

        for (int col = 0; col < 4; col++) {
            final int colDestino = col;
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setMinWidth(140);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);

            Label lTit = FormatadorSeguroUI.criarLabel(TITULOS_COLUNAS[col], 12, true, CORES_COLUNAS[col]);
            lTit.setWrapText(true);
            lTit.setMinWidth(0);
            Label lQtd = FormatadorSeguroUI.criarBadgeCartoon("0", "#FFFFFF", "#CBD5E1", CORES_COLUNAS[col]);
            lQtd.setMinWidth(Region.USE_PREF_SIZE);
            contadoresColunas[col] = lQtd;

            HBox headerCol = new HBox(6, lTit, lQtd);
            HBox.setHgrow(lTit, Priority.ALWAYS);
            headerCol.setAlignment(Pos.CENTER_LEFT);
            headerCol.setMinWidth(0);
            headerCol.setPadding(new Insets(8, 10, 8, 10));
            headerCol.setStyle("-fx-background-color: " + BG_COLUNAS[col] + "; -fx-border-color: " + CORES_COLUNAS[col]
                    + "; -fx-border-width: 0 0 2 0; -fx-background-radius: 8 8 0 0;");

            VBox listaCol = new VBox(8);
            listaCol.setPadding(new Insets(8));
            listaCol.setFillWidth(true);
            listaCol.setMinWidth(0);
            listaCol.setMaxWidth(Double.MAX_VALUE);
            caixasColunas[col] = listaCol;

            ScrollPane scrollCol = new ScrollPane(listaCol);
            scrollCol.setFitToWidth(true);
            scrollCol.setMinWidth(0);
            scrollCol.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollCol.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollCol.setPrefHeight(480);
            scrollCol.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
            VBox.setVgrow(scrollCol, Priority.ALWAYS);

            VBox colunaBox = new VBox(headerCol, scrollCol);
            colunaBox.setMinWidth(0);
            colunaBox.setMaxWidth(Double.MAX_VALUE);
            String estiloColNormal = FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1");
            String estiloColDrop = FormatadorSeguroUI.estiloCardCartoon("#EFF6FF", "#2563EB");
            colunaBox.setStyle(estiloColNormal);

            // Drag & Drop receptor em toda a coluna (incluso mover para Conclusão)
            colunaBox.setOnDragOver(ev -> {
                if (ev.getDragboard().hasString()) {
                    ev.acceptTransferModes(TransferMode.MOVE);
                    colunaBox.setStyle(estiloColDrop);
                }
                ev.consume();
            });
            colunaBox.setOnDragExited(ev -> {
                colunaBox.setStyle(estiloColNormal);
                ev.consume();
            });
            colunaBox.setOnDragDropped(ev -> {
                Dragboard db = ev.getDragboard();
                boolean ok = false;
                if (db.hasString()) {
                    String idArrastado = db.getString();
                    cards.stream().filter(c -> c.id.equals(idArrastado)).findFirst()
                            .ifPresent(item -> {
                                moverColuna(item, colDestino);
                                lblStatus.setText("✔ Card '" + item.titulo + "' arrastado para " + TITULOS_COLUNAS[colDestino] + ".");
                            });
                    ok = true;
                }
                colunaBox.setStyle(estiloColNormal);
                ev.setDropCompleted(ok);
                ev.consume();
            });

            GridPane.setVgrow(colunaBox, Priority.ALWAYS);
            grid.add(colunaBox, col, 0);
        }
        return grid;
    }

    // ==================== CICLO LAZY & CARGA INTELIGENTE ====================

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

        Thread.ofVirtual().start(() -> {
            try {
                List<EventoMapaGps> eventos = ponte.carregarEventosGpsOrdenados();
                List<ClimaDiario> previsoes = ponte.buscarPrevisaoProximosDias(7);
                List<Pessoa> fornecedores = ponte.listarFornecedores();
                List<Produto> produtos = ponte.listarProdutos();
                List<Map<String, Object>> planoCompras = ponte.consultarPlanoCompras(15);

                Platform.runLater(() -> {
                    this.ultimosEventos = eventos != null ? eventos : List.of();
                    this.ultimasPrevisoes = previsoes != null ? previsoes : List.of();
                    this.ultimosFornecedores = fornecedores != null ? fornecedores : List.of();
                    this.ultimosProdutos = produtos != null ? produtos : List.of();

                    cbFornecedorCard.getItems().setAll(this.ultimosFornecedores);
                    if (!this.ultimosFornecedores.isEmpty() && cbFornecedorCard.getValue() == null) {
                        cbFornecedorCard.getSelectionModel().selectFirst();
                    }

                    var snap = ponte.getGerenciadorTema().getSnapshotAtual();
                    atualizarContextoChips(snap);
                    atualizarRadarCatalogoKanban(snap);
                    gerarCardsAutomaticosSeVazio(snap, planoCompras);
                    sincronizarCardsExternosERenderizar();
                });
            } finally {
                carregando = false;
            }
        });
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
            atualizarContextoChips(snap);
            atualizarRadarCatalogoKanban(snap);
        }
    }

    private void atualizarContextoChips(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        double temp = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;
        double chuva = snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;
        lblContextoChips.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "📍 %s/%s • ☀️ %.1f°C (Chuva %.1f mm) • 🌊 %s (%.1f m) • 🎪 %d eventos no radar • 🚚 %d fornecedores",
                snap.cidade(), snap.uf(), temp, chuva, snap.regimeRio().getRotulo(), snap.nivelRioMetros(),
                ultimosEventos.size(), ultimosFornecedores.size()));
    }

    private void gerarCardsAutomaticosSeVazio(GerenciadorTemaClimatico.SnapshotTemaClimatico snap, List<Map<String, Object>> planoCompras) {
        cards.removeIf(c -> c.id.startsWith("AUTO-"));

        String fornPadrao = ultimosFornecedores.isEmpty()
                ? "Fornecedor Geral (cadastrar)"
                : FormatadorSeguroUI.tratarParaExibicao(ultimosFornecedores.get(0).getNome(), "Fornecedor Local");

        // 1. Eventos com Previsão Meteorológica -> Radar / Estratégia de Balcão (Ex: guarda-chuva em dia de chuva)
        int evCount = 0;
        for (EventoMapaGps ev : ultimosEventos) {
            if (evCount >= 3) break;
            evCount++;
            boolean vaiChover = ev.chuvaPrevistaMm() >= 1.5;
            String prodSug = vaiChover
                    ? "Guarda-chuva Compacto / Capa de Chuva / Café"
                    : (ev.tempPrevistaC() >= 29.5 ? "Água Gelada / Gelo / Boné / Protetor" : "Kit Conveniência p/ Visitantes");
            String estrat = vaiChover
                    ? String.format(FormatadorSeguroUI.LOCALE_BR,
                            "Previsão de chuva (%.1f mm) durante '%s' (%s da loja). Exponha guarda-chuvas e capas logo na entrada e crie combo impermeável!",
                            ev.chuvaPrevistaMm(), ev.nome(), FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()))
                    : String.format(FormatadorSeguroUI.LOCALE_BR,
                            "Fluxo previsto para '%s' (%s). %s",
                            ev.nome(), FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()), ev.resumoOportunidade());

            cards.add(0, new ItemKanban(
                    "AUTO-EV-" + ev.id(),
                    (vaiChover ? "☔ Vender Guarda-chuva p/ " : "🎪 Oportunidade: ") + ev.nome(),
                    "EVENTO",
                    ev.categoria() + " • " + FormatadorSeguroUI.formatarDistanciaKm(ev.distanciaKm()),
                    ev.previsaoTempo() + " | " + ev.dataHorario(),
                    prodSug,
                    fornPadrao,
                    estrat,
                    vaiChover ? new BigDecimal("39.90") : new BigDecimal("18.00"),
                    new BigDecimal("30"),
                    0
            ));
        }

        // 2. Card Climático de Estratégia para Cliente
        double chuvaMaxSemana = ultimasPrevisoes.stream().mapToDouble(ClimaDiario::precipitacaoMm).max().orElse(0.0);
        boolean riscoChuva = chuvaMaxSemana >= 2.0 || (snap != null && snap.clima() != null && snap.clima().precipitacaoMm() >= 1.5);
        cards.add(0, new ItemKanban(
                "AUTO-CLI-CLIMA",
                riscoChuva ? "☔ Kit Chuva Amazônica para Clientes" : "☀️ Kit Refrescância & Verão Amazônico",
                "CLIENTE",
                "Comportamento Climático dos Próximos 7 Dias",
                String.format(FormatadorSeguroUI.LOCALE_BR, "Pico de chuva previsto: %.1f mm", chuvaMaxSemana),
                riscoChuva ? "Guarda-chuva Automático + Capa Impermeável" : "Bebida Gelada + Gelo + Squeeze",
                fornPadrao,
                riscoChuva
                        ? "Clientes evitam deslocamentos longos na chuva: ofereça guarda-chuva com desconto na compra acima de R$ 50 e destaque entregas rápidas."
                        : "Clima quente eleva busca por itens de consumo rápido e hidratação no balcão.",
                riscoChuva ? new BigDecimal("42.00") : new BigDecimal("15.00"),
                new BigDecimal("25"),
                1
        ));

        // 3. Card Hidrológico / Plano de Compras com Fornecedores
        if (snap != null) {
            cards.add(0, new ItemKanban(
                    "AUTO-FORN-RIO",
                    "🚢 Reposição Antecipada (" + snap.regimeRio().getRotulo() + ")",
                    "FORNECEDOR",
                    "Nível Hidrográfico • " + snap.bacia(),
                    String.format(FormatadorSeguroUI.LOCALE_BR, "Cota %.1f m • %s", snap.nivelRioMetros(), snap.regimeRio().getRotulo()),
                    ultimosProdutos.isEmpty() ? "Itens Curva A de Balcão" : ultimosProdutos.get(0).getNome(),
                    fornPadrao,
                    snap.regimeRio().getOrientacaoOperacional() + " Ajuste estoque de segurança com o fornecedor antes de alterações no frete fluvial.",
                    new BigDecimal("25.00"),
                    new BigDecimal("40"),
                    2
            ));
        }

        if (planoCompras != null && !planoCompras.isEmpty()) {
            Map<String, Object> top = planoCompras.get(0);
            String prodNome = FormatadorSeguroUI.tratarParaExibicao(top.get("produto"), "Item em Baixo Estoque");
            String acao = FormatadorSeguroUI.tratarParaExibicao(top.get("acaoRecomendada"), "Repor estoque junto ao fornecedor");
            cards.add(0, new ItemKanban(
                    "AUTO-FORN-EST",
                    "📦 Repor Demanda: " + prodNome,
                    "FORNECEDOR",
                    "Alerta Preditivo de Ruptura",
                    "Baseado no giro + tendência sazonal",
                    prodNome,
                    fornPadrao,
                    acao,
                    new BigDecimal("20.00"),
                    new BigDecimal("20"),
                    2
            ));
        }
    }

    private void sincronizarCardsExternosERenderizar() {
        List<CardKanbanExterno> externos = ponte.listarCardsKanbanExternos();
        for (CardKanbanExterno ext : externos) {
            ItemKanban existente = cards.stream().filter(c -> c.id.equals(ext.id())).findFirst().orElse(null);
            if (existente == null) {
                cards.add(0, new ItemKanban(
                        ext.id(), ext.titulo(),
                        ext.tipoOrigem().toUpperCase(Locale.ROOT).contains("FORNECEDOR") ? "FORNECEDOR"
                                : (ext.tipoOrigem().toUpperCase(Locale.ROOT).contains("EVENTO") ? "EVENTO" : "CLIENTE"),
                        ext.tipoOrigem(), ext.contextoClimaEvento(),
                        ext.produtoSugerido(), ext.fornecedorSugerido(),
                        ext.estrategiaCliente(), new BigDecimal("35.00"), new BigDecimal("20"),
                        ext.colunaIndex()
                ));
            }
        }
        renderizarQuadro();
    }

    private void renderizarQuadro() {
        for (int c = 0; c < 4; c++) {
            caixasColunas[c].getChildren().clear();
        }
        int[] totais = new int[4];

        for (ItemKanban item : cards) {
            if (tgClientes.isSelected() && !"CLIENTE".equals(item.foco)) continue;
            if (tgEventos.isSelected() && !"EVENTO".equals(item.foco)) continue;
            if (tgFornecedores.isSelected() && !"FORNECEDOR".equals(item.foco)) continue;

            totais[item.coluna]++;
            caixasColunas[item.coluna].getChildren().add(criarVisualCardKanban(item));
        }

        for (int c = 0; c < 4; c++) {
            contadoresColunas[c].setText(String.valueOf(totais[c]));
            if (totais[c] == 0) {
                caixasColunas[c].getChildren().add(FormatadorSeguroUI.criarLabel(
                        "Sem cards nesta coluna para o filtro atual.", 11, false, "#94A3B8"));
            }
        }
    }

    private VBox criarVisualCardKanban(ItemKanban item) {
        String corFocoBg = switch (item.foco) {
            case "EVENTO" -> "#EFF6FF";
            case "FORNECEDOR" -> "#ECFDF5";
            default -> "#FFFBEB";
        };
        String corFocoBorder = switch (item.foco) {
            case "EVENTO" -> "#93C5FD";
            case "FORNECEDOR" -> "#6EE7B7";
            default -> "#FCD34D";
        };
        String corFocoText = switch (item.foco) {
            case "EVENTO" -> "#1E40AF";
            case "FORNECEDOR" -> "#065F46";
            default -> "#92400E";
        };

        Label badge = FormatadorSeguroUI.criarBadgeCartoon(item.foco, corFocoBg, corFocoBorder, corFocoText);
        Label lblTit = FormatadorSeguroUI.criarLabel(item.titulo, 12, true, "#0F172A");
        lblTit.setWrapText(true);
        lblTit.setMinWidth(0);
        lblTit.setMaxWidth(Double.MAX_VALUE);

        Label lblGatilho = FormatadorSeguroUI.criarLabel("🌦️ " + item.gatilhoAmbiental, 11, true, "#1D4ED8");
        lblGatilho.setWrapText(true);
        lblGatilho.setMinWidth(0);

        Label lblProd = FormatadorSeguroUI.criarLabel("📦 Produto: " + item.produtoSugerido, 11, false, "#334155");
        lblProd.setWrapText(true);
        lblProd.setMinWidth(0);

        Label lblForn = FormatadorSeguroUI.criarLabel("🚚 Fornecedor: " + item.fornecedorSugerido, 11, false, "#475569");
        lblForn.setWrapText(true);
        lblForn.setMinWidth(0);

        Button btnEsq = FormatadorSeguroUI.criarBotaoCartoon("◀", "#F8FAFC", "#CBD5E1", "#334155");
        btnEsq.setDisable(item.coluna <= 0);
        btnEsq.setOnAction(e -> moverColuna(item, item.coluna - 1));

        Button btnDir = FormatadorSeguroUI.criarBotaoCartoon("▶", "#F8FAFC", "#CBD5E1", "#334155");
        btnDir.setDisable(item.coluna >= 3);
        btnDir.setOnAction(e -> moverColuna(item, item.coluna + 1));

        Button btnConcluir = FormatadorSeguroUI.criarBotaoCartoon("⏭ Concluir", "#0F172A", "#0F172A", "#FFFFFF");
        btnConcluir.setDisable(item.coluna >= 3);
        btnConcluir.setOnAction(e -> {
            moverColuna(item, 3);
            lblStatus.setText("🏁 Card '" + item.titulo + "' movido para a esteira de Conclusão!");
        });

        Button btnProd = FormatadorSeguroUI.criarBotaoCartoon("📦 Criar", "#ECFDF5", "#6EE7B7", "#065F46");
        btnProd.setOnAction(e -> abrirCadastroRapidoProdutoSobDemanda(item.produtoSugerido, item.precoSugerido, item.estrategiaPratica));

        Button btnBandeja = FormatadorSeguroUI.criarBotaoCartoon("📥 Compra", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnBandeja.setOnAction(e -> {
            ponte.enfileirarCompraSugeridaNaBandeja(item.produtoSugerido, item.qtdCompraSugerida,
                    item.precoSugerido.multiply(new BigDecimal("0.60")),
                    "Kanban Demanda (" + item.titulo + ")");
            lblStatus.setText("✔ Compra de '" + item.produtoSugerido + "' enviada para a Bandeja de Contexto da IA!");
        });

        // FlowPane quebra linha automaticamente sem NUNCA estourar a largura da coluna (mesmo sidebar fechada)
        FlowPane barraAcoes = new FlowPane(4, 4, btnEsq, btnDir, btnConcluir, btnProd, btnBandeja);
        barraAcoes.setMinWidth(0);
        barraAcoes.setPrefWrapLength(180);

        FlowPane topoCard = new FlowPane(6, 4, badge, lblTit);
        topoCard.setMinWidth(0);

        boolean selecionado = cardSelecionado != null && cardSelecionado.id.equals(item.id);
        VBox box = new VBox(5, topoCard, lblGatilho, lblProd, lblForn, barraAcoes);
        box.setPadding(new Insets(9, 10, 9, 10));
        box.setMinWidth(0);
        box.setPrefWidth(10);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setFocusTraversable(true);
        box.setCursor(Cursor.HAND);
        box.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(
                selecionado ? "#EFF6FF" : "#FFFFFF",
                selecionado ? "#2563EB" : "#CBD5E1"));

        box.setOnDragDetected(ev -> {
            cardSelecionado = item;
            Dragboard db = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent ccDrag = new ClipboardContent();
            ccDrag.putString(item.id);
            db.setContent(ccDrag);
            ev.consume();
        });

        box.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) {
                cardSelecionado = item;
                if (e.getClickCount() >= 2) {
                    abrirDetalhesCardKanban(item);
                } else {
                    renderizarQuadro();
                    requestFocus();
                    lblStatus.setText("📌 Selecionado: '" + item.titulo + "' — Use as setas ⬅ / ➡ do teclado ou arraste para mover de coluna.");
                }
            }
        });
        return box;
    }

    private void moverColuna(ItemKanban item, int destino) {
        item.coluna = Math.max(0, Math.min(3, destino));
        ponte.moverCardKanbanExterno(item.id, item.coluna);
        renderizarQuadro();
    }

    private void abrirDetalhesCardKanban(ItemKanban item) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Estratégia", item.titulo);
        campos.put("Pilar / Foco", item.foco + " (" + TITULOS_COLUNAS[item.coluna] + ")");
        campos.put("Origem / Radar", item.origem);
        campos.put("Gatilho Clima/Rio/Evento", item.gatilhoAmbiental);
        campos.put("Produto Sob Demanda", item.produtoSugerido);
        campos.put("Fornecedor Associado", item.fornecedorSugerido);
        campos.put("Preço Estimado (Venda)", FormatadorSeguroUI.formatarMoeda(item.precoSugerido));
        campos.put("Ação Comercial Recomendada", item.estrategiaPratica);

        Button btnColAnt = FormatadorSeguroUI.criarBotaoCartoon("◀ Mover Esquerda", "#F8FAFC", "#CBD5E1", "#0F172A");
        btnColAnt.setDisable(item.coluna <= 0);
        btnColAnt.setOnAction(e -> moverColuna(item, item.coluna - 1));

        Button btnColProx = FormatadorSeguroUI.criarBotaoCartoon("▶ Avançar Etapa", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnColProx.setDisable(item.coluna >= 3);
        btnColProx.setOnAction(e -> moverColuna(item, item.coluna + 1));

        Button btnRegProd = FormatadorSeguroUI.criarBotaoCartoon("📦 Cadastrar Produto no Catálogo", "#047857", "#065F46", "#FFFFFF");
        btnRegProd.setOnAction(e -> abrirCadastroRapidoProdutoSobDemanda(item.produtoSugerido, item.precoSugerido, item.estrategiaPratica));

        FlowPane extras = new FlowPane(8, 8, btnColAnt, btnColProx, btnRegProd);
        DialogoCorporativoModal.exibirCardDetalhes(
                this,
                "🗂️",
                item.titulo,
                item.foco + " • " + item.origem,
                CORES_COLUNAS[item.coluna],
                campos,
                extras,
                null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Plano Kanban: " + item.titulo,
                        item.gatilhoAmbiental + " | Produto: " + item.produtoSugerido,
                        "Detalhe uma estratégia passo a passo para executar este card do Kanban: '" + item.titulo
                                + "' (Gatilho: " + item.gatilhoAmbiental + "). Como abordar o cliente perto do evento/clima, qual margem aplicar no produto '"
                                + item.produtoSugerido + "' e o que negociar com o fornecedor '" + item.fornecedorSugerido + "'?")
        );
    }

    private void criarCardManualConfirmado() {
        String tit = txtTituloCard.getText() != null ? txtTituloCard.getText().trim() : "";
        String prod = txtProdutoCard.getText() != null ? txtProdutoCard.getText().trim() : "";
        if (tit.isEmpty()) {
            lblStatus.setText("⚠️ Informe um título para a estratégia do Kanban.");
            return;
        }
        String foco = cbFocoCard.getValue() != null ? cbFocoCard.getValue() : "CLIENTE";
        int col = Math.max(0, cbColunaCard.getSelectionModel().getSelectedIndex());
        String forn = cbFornecedorCard.getValue() != null ? cbFornecedorCard.getValue().getNome() : "Fornecedor Geral";
        BigDecimal preco = FormatadorSeguroUI.parseDecimalSeguro(txtPrecoCard.getText(), new BigDecimal("35.00"));
        String estrat = txtEstrategiaCard.getText() != null && !txtEstrategiaCard.getText().isBlank()
                ? txtEstrategiaCard.getText().trim()
                : "Estratégia sob demanda mapeada pela gestão.";

        Map<String, String> conf = new LinkedHashMap<>();
        conf.put("Título", tit);
        conf.put("Foco / Pilar", foco);
        conf.put("Coluna", TITULOS_COLUNAS[col]);
        conf.put("Produto Alvo", prod.isEmpty() ? "Personalizado" : prod);
        conf.put("Fornecedor", forn);
        conf.put("Preço Sugerido", FormatadorSeguroUI.formatarMoeda(preco));

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Adicionar Card no Kanban de Demanda",
                "Confirma o registro desta estratégia personalizada no quadro Kanban?",
                conf,
                () -> {
                    cards.add(0, new ItemKanban(
                            "USER-" + System.currentTimeMillis(),
                            tit, foco, "Estratégia Personalizada do Usuário",
                            "Planejamento Manual Clima × Eventos",
                            prod.isEmpty() ? tit : prod, forn, estrat, preco, new BigDecimal("20"), col
                    ));
                    txtTituloCard.clear();
                    txtProdutoCard.clear();
                    txtEstrategiaCard.clear();
                    boxCriadorRapido.setVisible(false);
                    boxCriadorRapido.setManaged(false);
                    lblStatus.setText("✔ Card '" + tit + "' criado na coluna " + TITULOS_COLUNAS[col] + ".");
                    renderizarQuadro();
                }
        );
    }

    private void abrirCadastroRapidoProdutoSobDemanda(String nomeSugerido, BigDecimal precoSugerido, String descSugerida) {
        String nomeLimpo = (nomeSugerido == null || nomeSugerido.isBlank()) ? "Produto Sazonal Evento" : nomeSugerido.trim();
        BigDecimal precoLimpo = (precoSugerido != null && precoSugerido.compareTo(BigDecimal.ZERO) > 0)
                ? precoSugerido : new BigDecimal("29.90");
        String descLimpa = (descSugerida == null || descSugerida.isBlank())
                ? "Item sob demanda cadastrado pelo planejamento Kanban." : descSugerida.trim();

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("Nome do Produto", nomeLimpo);
        campos.put("Unidade", "UN");
        campos.put("Preço de Venda", FormatadorSeguroUI.formatarMoeda(precoLimpo));
        campos.put("Finalidade / Evento", descLimpa);

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Cadastrar Produto Sob Demanda no Catálogo",
                "Deseja registrar este item no estoque/catálogo para venda imediata perto dos eventos?",
                campos,
                () -> ponte.executarRotaAsync(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                        "nome", nomeLimpo,
                        "descricao", descLimpa,
                        "precoVenda", precoLimpo,
                        "siglaUm", "UN"
                ), resp -> {
                    if (resp.sucesso()) {
                        lblStatus.setText("✔ Produto '" + nomeLimpo + "' cadastrado no catálogo para vendas!");
                    } else {
                        lblStatus.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                    }
                })
        );
    }

    private void atualizarRadarCatalogoKanban(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        boxRadarCatalogoKanban.getChildren().clear();
        double chuva = snap != null && snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0;
        double temp = snap != null && snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;

        List<String> desejados = new ArrayList<>();
        if (chuva >= 2.0 || ultimasPrevisoes.stream().anyMatch(d -> d.precipitacaoMm() >= 2.0)) {
            desejados.addAll(List.of("Guarda-chuva Compacto", "Capa de Chuva Impermeável", "Protetor de Calçado", "Café / Bebida Quente"));
        }
        if (temp >= 28.0 || ultimasPrevisoes.stream().anyMatch(d -> d.temperaturaMax() >= 30.0)) {
            desejados.addAll(List.of("Água Mineral 500ml", "Gelo Saco", "Protetor Solar FPS 50", "Boné / Viseira"));
        }
        if (!ultimosEventos.isEmpty()) {
            desejados.addAll(List.of("Kit Conveniência Evento", "Snack / Barra Energética", "Carregador Portátil / Cabo"));
        }
        if (desejados.isEmpty()) {
            desejados.addAll(List.of("Água Mineral", "Guarda-chuva", "Snack Conveniência"));
        }

        List<String> jaTenho = new ArrayList<>();
        List<String> naoTenho = new ArrayList<>();
        for (String cand : desejados) {
            String raiz = cand.split("[ /]+")[0].toLowerCase(Locale.ROOT);
            Produto match = ultimosProdutos.stream()
                    .filter(p -> p.getNome() != null && p.getNome().toLowerCase(Locale.ROOT).contains(raiz))
                    .findFirst().orElse(null);
            if (match != null) {
                jaTenho.add(FormatadorSeguroUI.limparNomeProduto(match.getNome()));
            } else {
                naoTenho.add(cand);
            }
        }

        Label lTitRadar = FormatadorSeguroUI.criarLabel(
                "🧬 Radar de Catálogo Sazonal (Use ⬅ / ➡ no teclado ou arraste cards até Conclusão)",
                12, true, "#1E40AF");
        FlowPane linhaChips = new FlowPane(6, 5);
        for (String jt : jaTenho.stream().distinct().limit(4).toList()) {
            Label b = FormatadorSeguroUI.criarBadgeCartoon("✅ Já Tenho (Reforçar): " + jt, "#ECFDF5", "#6EE7B7", "#047857");
            linhaChips.getChildren().add(b);
        }
        for (String nt : naoTenho.stream().distinct().limit(4).toList()) {
            Button btnAddNovo = FormatadorSeguroUI.criarBotaoCartoon("🆕 Não Tenho (+ Criar): " + nt, "#FFFBEB", "#F59E0B", "#92400E");
            btnAddNovo.setOnAction(ev -> abrirCadastroRapidoProdutoSobDemanda(
                    nt, new BigDecimal("29.90"), "Novo produto sugerido para cobrir lacuna de demanda sazonal."));
            linhaChips.getChildren().add(btnAddNovo);
        }

        boxRadarCatalogoKanban.getChildren().addAll(lTitRadar, linhaChips);
    }

    private void acionarIAPlanejamentoKanban() {
        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        StringBuilder sbCatalogoAtual = new StringBuilder();
        for (int i = 0; i < Math.min(25, ultimosProdutos.size()); i++) {
            if (i > 0) sbCatalogoAtual.append(", ");
            sbCatalogoAtual.append(FormatadorSeguroUI.limparNomeProduto(ultimosProdutos.get(i).getNome()));
        }
        String resumo = String.format(FormatadorSeguroUI.LOCALE_BR,
                "%s/%s • %s (%.1f°C, chuva %.1fmm) • Rio: %s • %d eventos no radar • %d produtos no catálogo",
                snap.cidade(), snap.uf(), snap.estado().getTitulo(),
                snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5,
                snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0,
                snap.regimeRio().getRotulo(), ultimosEventos.size(), ultimosProdutos.size());
        String evTopo = ultimosEventos.isEmpty() ? "nenhum evento específico listado"
                : ultimosEventos.get(0).nome() + " (" + ultimosEventos.get(0).previsaoTempo() + ")";

        Map<String, String> metaOrig = new LinkedHashMap<>();
        metaOrig.put("Cidade / Clima", resumo);
        metaOrig.put("Próximo Evento", evTopo);
        metaOrig.put("Produtos QUE JÁ TENHO", sbCatalogoAtual.isEmpty() ? "Catálogo vazio" : sbCatalogoAtual.toString());

        conectorIA.explicarComContextoOrigem(
                this,
                "Kanban Demanda — Radar de Catálogo (Tenho vs Não Tenho)",
                "Estratégia Sazonal (" + snap.cidade() + ")",
                metaOrig,
                "Analise meu cenário atual (" + resumo + ", evento: " + evTopo + ") e meu CATÁLOGO ATUAL ["
                        + metaOrig.get("Produtos QUE JÁ TENHO") + "]. "
                        + "Responda obrigatoriamente em 2 blocos claros: "
                        + "(1) PRODUTOS QUE EU AINDA NÃO TENHO NO CATÁLOGO: sugira 4 produtos inéditos lucrativos alinhados ao clima/rio/eventos "
                        + "(ex: guarda-chuvas/capas em chuva, isotônicos/gelo em calor) com sugestão de compra inicial; "
                        + "(2) PRODUTOS QUE EU JÁ TENHO NO CATÁLOGO: indique exatamente quais dos itens acima devo reforçar estoque e montar kits de exposição.");
    }

    private void atualizarEstilosFiltros() {
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgTodos, tgTodos.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgClientes, tgClientes.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#B45309", "#92400E", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgEventos, tgEventos.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#1D4ED8", "#1E40AF", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgFornecedores, tgFornecedores.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#047857", "#065F46", "#FFFFFF");
    }
}
