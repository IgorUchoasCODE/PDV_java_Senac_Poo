package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.CarrosselImagensProduto;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ConsolidadoEstoque;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aba 2: PDV Balcão — Frente de Caixa Ágil, Teclado, Comando de Voz e Pós-Venda.
 * Características corporativas:
 * - Operação completa via teclado (F1-F10, Setas, Enter) e comando de voz integrado à IA;
 * - Seleção automática por validade ou seleção manual de lote, com atualização imediata de estoque;
 * - Carrossel de fotos do produto sob demanda ao selecionar qualquer item;
 * - Edição rápida de quantidade e valor unitário (normal ou assistida por IA);
 * - Múltiplos pagamentos por Nota (com restrição estrita À Vista para Cliente Padrão);
 * - Confirmação manual obrigatória e Card da Nota com Impressão imediata após concluir.
 */
public class AbaBalcaoNotasView extends VBox {

    private record ItemCatalogoPDV(
            Produto produto,
            Item<? extends Produto> loteEspecifico,
            String codigoExibicao,
            String siglaProduto,
            String nome,
            String umSigla,
            BigDecimal saldo,
            LocalDate validade,
            BigDecimal preco
    ) {}

    private static class ItemCarrinho {
        final Produto produto;
        final Long idLoteEspecifico;
        final String rotuloLote;
        BigDecimal quantidade;
        BigDecimal precoUnitario;
        boolean atualizarPrecoCatalogo;

        ItemCarrinho(Produto produto, Long idLoteEspecifico, String rotuloLote, BigDecimal quantidade) {
            this.produto = produto;
            this.idLoteEspecifico = idLoteEspecifico;
            this.rotuloLote = rotuloLote;
            this.quantidade = quantidade;
            this.precoUnitario = produto.getPrecoVenda() != null ? produto.getPrecoVenda() : BigDecimal.ZERO;
        }

        BigDecimal getSubtotal() {
            return precoUnitario.multiply(quantidade);
        }
    }

    private record PagamentoCupom(
            String codigoForma,
            String nomeForma,
            String modalidade,
            int parcelas,
            BigDecimal valor
    ) {}

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabCaixa = new Tab("🛒 Caixa / Venda Rápida");
    private final Tab tabPosVenda = new Tab("↩️ Devolução & Perda Pós-Venda");

    // Cache sob demanda
    private List<Produto> produtosCache = new ArrayList<>();
    private List<Item<? extends Produto>> lotesCache = new ArrayList<>();
    private ConsolidadoEstoque consolidadoCache;
    private List<Pessoa> clientesCache = new ArrayList<>();
    private List<FormaPagamento> formasPagamentoCache = new ArrayList<>();
    private List<Nota> notasCache = new ArrayList<>();
    private final List<ItemCarrinho> carrinho = new ArrayList<>();
    private final List<PagamentoCupom> pagamentosCupom = new ArrayList<>();

    // Barra Comando Rápido / Voz / IA
    private final TextField txtComandoRapidoIA = FormatadorSeguroUI.criarCampoTextoCartoon(
            "F1: Comando rápido ou voz (ex.: 'add 2 arroz', 'preco 14,50', 'cliente Ana', 'devolver nota 12')...",
            "#93C5FD", "#0F172A");
    private final Button btnVozPDV = FormatadorSeguroUI.criarBotaoCartoon("🎤 Voz (F6)", "#EFF6FF", "#93C5FD", "#1E40AF");
    private final Button btnExecutarCmdPDV = FormatadorSeguroUI.criarBotaoCartoon("⚡ Executar", "#0F172A", "#0F172A", "#FFFFFF");
    private final Label lblFeedbackCmdPDV = FormatadorSeguroUI.criarLabel(
            "Atalhos: F1 Comando IA • F2 Buscar • ↑↓+Enter Add • F3 Editar Valor • F4 Cliente • F5 +Pagamento • F6 Voz • F8 Caixa/Pós-Venda • F10 Concluir",
            11, false, "#475569");
    private boolean gravandoVozPDV = false;

    // Controles Sub-aba 1: Caixa
    private final ToggleButton tgModoAutomatico = new ToggleButton("Seleção Automática (Validade)");
    private final ToggleButton tgModoLote = new ToggleButton("Escolher Lote Manual");
    private final TextField txtBuscaProduto = FormatadorSeguroUI.criarCampoTextoCartoon(
            "F2: Buscar por sigla (ex.: GC, UN), nome ou #código (Enter adiciona 1º)...", "#CBD5E1", "#0F172A");
    private final TableView<ItemCatalogoPDV> tabelaCatalogo = new TableView<>();
    private final CarrosselImagensProduto carrosselPDV;
    private final Label lblInfoItemSelecionado = FormatadorSeguroUI.criarLabel(
            "Selecione um produto ou lote na tabela para pré-visualizar foto e detalhes.", 11, false, "#475569");

    private final ComboBox<String> cbCliente = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final Label lblRegraClientePadrao = FormatadorSeguroUI.criarBadgeCartoon(
            "Cliente Padrão: pagamento exclusivo À Vista", "#FFFBEB", "#FCD34D", "#92400E");
    private final VBox boxListaCarrinho = new VBox(6);
    private final VBox boxPagamentosCupom = new VBox(5);
    private final TextField txtDesconto = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final TextField txtCupom = FormatadorSeguroUI.criarCampoTextoCartoon("Opcional", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFormaPagamento = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbModalidadePag = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<Integer> cbParcelas = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtValorPagParcial = FormatadorSeguroUI.criarCampoTextoCartoon("Valor", "#CBD5E1", "#0F172A");
    private final Label lblDetalheTaxa = FormatadorSeguroUI.criarLabel("", 11, false, "#475569");
    private final Label lblTotalVenda = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#047857");
    private final Label lblSaldoPagamentos = FormatadorSeguroUI.criarLabel("Faltante: R$ 0,00", 11, true, "#475569");
    private final Label lblStatusCaixa = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private final Button btnAjudaErroCaixa = FormatadorSeguroUI.criarBotaoIA("❓ Resolver com IA", null);

    // Controles Sub-aba 2: Devolução & Perda Pós-Venda
    private final ComboBox<String> cbNotaVendaOrigem = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbItemVendaOrigem = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtQtdDevolvida = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final TextField txtMotivoDevolucao = FormatadorSeguroUI.criarCampoTextoCartoon(
            "Ex.: Produto avariado pós-venda / defeito dentro da validade", "#CBD5E1", "#0F172A");
    private final CheckBox chkGerarPerdaFornecedor = new CheckBox("Gerar Perda Pós-Venda (preferencial — elegível para abatimento do fornecedor)");
    private final Label lblStatusPosVenda = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");

    // Baixa rápida de avaria de balcão (opcional)
    private final ComboBox<String> cbLoteAvariaBalcao = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtQtdAvariaBalcao = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final TextField txtMotivoAvariaBalcao = FormatadorSeguroUI.criarCampoTextoCartoon("Ex.: Queda no balcão / embalagem rasgada", "#CBD5E1", "#0F172A");
    private final VBox boxHistoricoPosVenda = new VBox(8);

    private List<NotaVenda> vendasDisponiveisCombo = new ArrayList<>();
    private List<Item<? extends Produto>> itensDaVendaSelecionada = new ArrayList<>();
    private List<Item<? extends Produto>> lotesAvariaCombo = new ArrayList<>();

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaBalcaoNotasView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;
        this.carrosselPDV = new CarrosselImagensProduto(ponte, false, 165, 105);

        setSpacing(8);
        setPadding(new Insets(12, 16, 12, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();
        VBox barraComandoRapido = criarBarraComandoRapido();

        tabCaixa.setContent(construirSubAbaCaixa());
        tabPosVenda.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaPosVenda()));

        tabPane.getTabs().addAll(tabCaixa, tabPosVenda);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(topo, barraComandoRapido, tabPane);

        configurarAtalhosTecladoPDV();

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) {
                carregarSeNecessario();
            }
        });
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("🛒", "PDV Balcão — Frente de Caixa Rápida & Pós-Venda", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Operação ágil via teclado e comando de voz com IA, múltiplos pagamentos e impressão imediata da nota.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnUltimaNota = FormatadorSeguroUI.criarBotaoCartoon("🧾 Última Venda", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnUltimaNota.setOnAction(e -> abrirUltimaVendaParaImpressao());

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Atualizar Estoque", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        HBox topo = new HBox(8, boxTit, btnUltimaNota, btnAtualizar);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    private VBox criarBarraComandoRapido() {
        HBox.setHgrow(txtComandoRapidoIA, Priority.ALWAYS);
        txtComandoRapidoIA.setOnAction(e -> processarComandoRapidoTextoOuIA());
        btnExecutarCmdPDV.setOnAction(e -> processarComandoRapidoTextoOuIA());
        btnVozPDV.setOnAction(e -> alternarVozPDV());

        Button btnAjudaIA = FormatadorSeguroUI.criarBotaoIA("🤖 Assistente IA", () ->
                conectorIA.perguntarAoCopiloto(
                        this,
                        "Assistente de Frente de Caixa",
                        "Itens no cupom: " + carrinho.size() + " • Total: " + lblTotalVenda.getText(),
                        "Ajude-me na operação atual do balcão (produtos, valores, devoluções pós-venda ou formas de pagamento)."
                ));

        HBox linha = new HBox(8, txtComandoRapidoIA, btnExecutarCmdPDV, btnVozPDV, btnAjudaIA);
        linha.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(4, linha, lblFeedbackCmdPDV);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));
        return box;
    }

    private void configurarAtalhosTecladoPDV() {
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!abaAtiva) return;
            if (e.getCode() == KeyCode.F1) {
                txtComandoRapidoIA.requestFocus();
                e.consume();
            } else if (e.getCode() == KeyCode.F2) {
                tabPane.getSelectionModel().select(tabCaixa);
                txtBuscaProduto.requestFocus();
                txtBuscaProduto.selectAll();
                e.consume();
            } else if (e.getCode() == KeyCode.F3) {
                tabPane.getSelectionModel().select(tabCaixa);
                abrirEdicaoRapidaUltimoItem();
                e.consume();
            } else if (e.getCode() == KeyCode.F4) {
                tabPane.getSelectionModel().select(tabCaixa);
                cbCliente.requestFocus();
                cbCliente.show();
                e.consume();
            } else if (e.getCode() == KeyCode.F5) {
                tabPane.getSelectionModel().select(tabCaixa);
                abrirJanelaPagamentoVenda();
                e.consume();
            } else if (e.getCode() == KeyCode.F6) {
                alternarVozPDV();
                e.consume();
            } else if (e.getCode() == KeyCode.F8) {
                tabPane.getSelectionModel().select(
                        tabPane.getSelectionModel().getSelectedItem() == tabCaixa ? tabPosVenda : tabCaixa);
                e.consume();
            } else if (e.getCode() == KeyCode.F10 || (e.isControlDown() && e.getCode() == KeyCode.ENTER)) {
                tabPane.getSelectionModel().select(tabCaixa);
                abrirJanelaPagamentoVenda();
                e.consume();
            }
        });
    }

    // ==================== SUB-ABA 1: CAIXA / VENDA RÁPIDA ====================

    private GridPane construirSubAbaCaixa() {
        GridPane layout = new GridPane();
        layout.setHgap(12);
        layout.setPadding(new Insets(8, 0, 4, 0));
        layout.setMinWidth(0);

        ColumnConstraints colEsq = new ColumnConstraints();
        colEsq.setPercentWidth(52);
        colEsq.setHgrow(Priority.ALWAYS);
        ColumnConstraints colDir = new ColumnConstraints();
        colDir.setPercentWidth(48);
        colDir.setHgrow(Priority.ALWAYS);
        layout.getColumnConstraints().addAll(colEsq, colDir);

        // Coluna Esquerda: Tabela Catálogo + Preview Sob Demanda (Carrossel)
        VBox painelEsq = new VBox(8);
        painelEsq.setPadding(new Insets(10));
        painelEsq.setMinWidth(0);
        painelEsq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        ToggleGroup grpModo = new ToggleGroup();
        tgModoAutomatico.setToggleGroup(grpModo);
        tgModoLote.setToggleGroup(grpModo);
        tgModoAutomatico.setSelected(true);

        Runnable atualizarEstiloModos = () -> {
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgModoAutomatico, tgModoAutomatico.isSelected(),
                    "#F8FAFC", "#CBD5E1", "#334155", "#1D4ED8", "#1E40AF", "#FFFFFF");
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgModoLote, tgModoLote.isSelected(),
                    "#F8FAFC", "#CBD5E1", "#334155", "#1D4ED8", "#1E40AF", "#FFFFFF");
            filtrarCatalogoNaTabela();
        };
        tgModoAutomatico.setOnAction(e -> atualizarEstiloModos.run());
        tgModoLote.setOnAction(e -> atualizarEstiloModos.run());
        atualizarEstiloModos.run();

        txtBuscaProduto.textProperty().addListener((obs, o, n) -> filtrarCatalogoNaTabela());
        txtBuscaProduto.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.DOWN && !tabelaCatalogo.getItems().isEmpty()) {
                tabelaCatalogo.requestFocus();
                tabelaCatalogo.getSelectionModel().selectFirst();
                ev.consume();
            } else if (ev.getCode() == KeyCode.ENTER && !tabelaCatalogo.getItems().isEmpty()) {
                ItemCatalogoPDV top = tabelaCatalogo.getItems().get(0);
                if (top.saldo().compareTo(BigDecimal.ZERO) > 0) {
                    adicionarAoCarrinho(top);
                    txtBuscaProduto.selectAll();
                }
                ev.consume();
            }
        });
        txtBuscaProduto.setMinWidth(100);
        HBox.setHgrow(txtBuscaProduto, Priority.ALWAYS);

        HBox barraFiltros = new HBox(6, txtBuscaProduto, tgModoAutomatico, tgModoLote);
        barraFiltros.setAlignment(Pos.CENTER_LEFT);
        barraFiltros.setMinWidth(0);

        configurarTabelaCatalogo();
        tabelaCatalogo.setMinWidth(0);
        VBox.setVgrow(tabelaCatalogo, Priority.ALWAYS);

        // Preview compacto + Carrossel sob demanda
        Button btnDetalhesProd = FormatadorSeguroUI.criarBotaoCartoon("🔍 Ver Ficha Completa", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnDetalhesProd.setOnAction(e -> {
            ItemCatalogoPDV sel = tabelaCatalogo.getSelectionModel().getSelectedItem();
            if (sel != null) abrirCardDetalhesCatalogo(sel);
        });

        lblInfoItemSelecionado.setWrapText(true);
        lblInfoItemSelecionado.setMinWidth(0);

        VBox boxInfoSel = new VBox(6,
                FormatadorSeguroUI.criarLabel("📸 Pré-visualização Sob Demanda", 12, true, "#0F172A"),
                lblInfoItemSelecionado,
                btnDetalhesProd);
        boxInfoSel.setMinWidth(0);
        carrosselPDV.setMinWidth(0);
        HBox.setHgrow(boxInfoSel, Priority.ALWAYS);
        HBox.setHgrow(carrosselPDV, Priority.ALWAYS);

        HBox boxPreview = new HBox(10, carrosselPDV, boxInfoSel);
        boxPreview.setAlignment(Pos.CENTER_LEFT);
        boxPreview.setMinWidth(0);
        boxPreview.setPadding(new Insets(6));
        boxPreview.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));

        painelEsq.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("Catálogo & Disponibilidade em Estoque", 13, true, "#0F172A"),
                barraFiltros,
                tabelaCatalogo,
                boxPreview
        );

        VBox painelDir = construirPainelCupomDireito();
        layout.add(painelEsq, 0, 0);
        layout.add(painelDir, 1, 0);
        GridPane.setVgrow(painelEsq, Priority.ALWAYS);
        GridPane.setVgrow(painelDir, Priority.ALWAYS);
        return layout;
    }

    @SuppressWarnings("unchecked")
    private void configurarTabelaCatalogo() {
        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaCatalogo, 280);

        TableColumn<ItemCatalogoPDV, String> colSigla = new TableColumn<>("Sigla/UM");
        colSigla.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().siglaProduto() + " • " + c.getValue().umSigla()));
        colSigla.setPrefWidth(72);

        TableColumn<ItemCatalogoPDV, String> colNome = new TableColumn<>("Produto / Lote");
        colNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigoExibicao() + " • " + c.getValue().nome()));
        colNome.setPrefWidth(180);
        colNome.setMinWidth(120);

        TableColumn<ItemCatalogoPDV, String> colSaldo = new TableColumn<>("Saldo");
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(
                FormatadorSeguroUI.formatarQuantidade(c.getValue().saldo(), c.getValue().umSigla())));
        colSaldo.setPrefWidth(68);

        TableColumn<ItemCatalogoPDV, String> colVal = new TableColumn<>("Validade");
        colVal.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().validade() != null ? FormatadorSeguroUI.formatarDataAmigavel(c.getValue().validade()) : "Indeterm."));
        colVal.setPrefWidth(88);

        TableColumn<ItemCatalogoPDV, String> colPreco = new TableColumn<>("Preço");
        colPreco.setCellValueFactory(c -> new SimpleStringProperty(
                FormatadorSeguroUI.formatarMoeda(c.getValue().preco())));
        colPreco.setPrefWidth(72);

        TableColumn<ItemCatalogoPDV, Void> colAcao = new TableColumn<>("Ação");
        colAcao.setPrefWidth(72);
        colAcao.setMinWidth(68);
        colAcao.setMaxWidth(78);
        colAcao.setCellFactory(col -> new TableCell<>() {
            private final Button btn = FormatadorSeguroUI.criarBotaoCartoon("+ Add", "#047857", "#065F46", "#FFFFFF");
            {
                btn.setOnAction(e -> {
                    ItemCatalogoPDV item = getTableView().getItems().get(getIndex());
                    if (item != null) adicionarAoCarrinho(item);
                });
            }
            @Override
            protected void updateItem(Void unused, boolean empty) {
                super.updateItem(unused, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ItemCatalogoPDV item = getTableView().getItems().get(getIndex());
                    boolean semSaldo = item == null || item.saldo().compareTo(BigDecimal.ZERO) <= 0;
                    btn.setDisable(semSaldo);
                    setGraphic(btn);
                }
            }
        });

        tabelaCatalogo.getColumns().setAll(colSigla, colNome, colSaldo, colVal, colPreco, colAcao);
        tabelaCatalogo.setPlaceholder(FormatadorSeguroUI.criarLabel("Nenhum item encontrado.", 12, false, "#64748B"));

        tabelaCatalogo.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                carrosselPDV.carregarProdutoSobDemanda(sel.produto());
                lblInfoItemSelecionado.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                        "%s • Preço: %s • Saldo: %s%s",
                        sel.nome(), FormatadorSeguroUI.formatarMoeda(sel.preco()),
                        FormatadorSeguroUI.formatarQuantidade(sel.saldo(), sel.umSigla()),
                        sel.validade() != null ? " • Val.: " + FormatadorSeguroUI.formatarDataAmigavel(sel.validade()) : ""));
            }
        });

        tabelaCatalogo.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER || ev.getCode() == KeyCode.SPACE) {
                ItemCatalogoPDV sel = tabelaCatalogo.getSelectionModel().getSelectedItem();
                if (sel != null && sel.saldo().compareTo(BigDecimal.ZERO) > 0) {
                    adicionarAoCarrinho(sel);
                }
                ev.consume();
            }
        });

        tabelaCatalogo.setRowFactory(tv -> {
            TableRow<ItemCatalogoPDV> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (!row.isEmpty() && ev.getClickCount() == 2 && row.getItem().saldo().compareTo(BigDecimal.ZERO) > 0) {
                    adicionarAoCarrinho(row.getItem());
                }
            });
            return row;
        });
    }

    private VBox construirPainelCupomDireito() {
        VBox boxCupom = new VBox(8);
        boxCupom.setPadding(new Insets(12));
        boxCupom.setMinWidth(0);
        boxCupom.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Button btnEditarRapido = FormatadorSeguroUI.criarBotaoCartoon("✏️ Editar (F3)", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnEditarRapido.setTooltip(new Tooltip("Editar quantidade e preço do último item adicionado (F3)"));
        btnEditarRapido.setOnAction(e -> abrirEdicaoRapidaUltimoItem());

        Button btnLimparCarrinho = FormatadorSeguroUI.criarBotaoCartoon("Limpar", "#F1F5F9", "#CBD5E1", "#334155");
        btnLimparCarrinho.setTooltip(new Tooltip("Limpar todos os itens da nota"));
        btnLimparCarrinho.setOnAction(e -> {
            carrinho.clear();
            pagamentosCupom.clear();
            atualizarVisualCarrinho();
            atualizarVisualPagamentosCupom();
            recalcularTotalCupom();
            lblStatusCaixa.setText("Cupom limpo.");
        });

        Label titCupom = FormatadorSeguroUI.criarLabel("🧾 Resumo da Venda", 13, true, "#0F172A");
        titCupom.setWrapText(true);
        titCupom.setMinWidth(0);
        HBox.setHgrow(titCupom, Priority.ALWAYS);
        HBox topoCupom = new HBox(6, titCupom, btnEditarRapido, btnLimparCarrinho);
        topoCupom.setAlignment(Pos.CENTER_LEFT);
        topoCupom.setMinWidth(0);

        boxListaCarrinho.setPadding(new Insets(6));
        boxListaCarrinho.setMinWidth(0);
        var scrollCarrinho = FormatadorSeguroUI.envolverScrollSuave(boxListaCarrinho);
        scrollCarrinho.setPrefHeight(250);
        scrollCarrinho.setMinHeight(130);
        scrollCarrinho.setMinWidth(0);
        scrollCarrinho.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        VBox.setVgrow(scrollCarrinho, Priority.ALWAYS);

        // Cliente & regra estrita Cliente Padrão
        cbCliente.setMinWidth(0);
        cbCliente.setMaxWidth(Double.MAX_VALUE);
        cbCliente.setOnAction(e -> atualizarOpcoesModalidadePagamento());
        HBox linhaCli = new HBox(8, FormatadorSeguroUI.criarLabel("Cliente (F4):", 12, true, "#334155"), cbCliente);
        HBox.setHgrow(cbCliente, Priority.ALWAYS);
        linhaCli.setAlignment(Pos.CENTER_LEFT);
        linhaCli.setMinWidth(0);

        lblRegraClientePadrao.setWrapText(true);
        lblRegraClientePadrao.setMinWidth(0);
        lblRegraClientePadrao.setMaxWidth(Double.MAX_VALUE);

        GridPane gridAjustes = new GridPane();
        gridAjustes.setHgap(8);
        gridAjustes.setVgap(4);
        gridAjustes.setMinWidth(0);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        gridAjustes.getColumnConstraints().addAll(c1, c2);
        txtDesconto.setMinWidth(0);
        txtCupom.setMinWidth(0);
        txtDesconto.textProperty().addListener((obs, o, n) -> recalcularTotalCupom());

        gridAjustes.add(FormatadorSeguroUI.criarLabel("Desconto Global (R$):", 11, true, "#475569"), 0, 0);
        gridAjustes.add(FormatadorSeguroUI.criarLabel("Cupom Promocional:", 11, true, "#475569"), 1, 0);
        gridAjustes.add(txtDesconto, 0, 1);
        gridAjustes.add(txtCupom, 1, 1);

        VBox cardTotal = new VBox(4);
        cardTotal.setPadding(new Insets(8, 12, 8, 12));
        cardTotal.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#ECFDF5", "#6EE7B7"));
        HBox linhaTotal = new HBox(10,
                FormatadorSeguroUI.criarLabel("TOTAL DA NOTA DE VENDA:", 12, true, "#065F46"),
                lblTotalVenda);
        linhaTotal.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(linhaTotal.getChildren().get(0), Priority.ALWAYS);
        Label subFluxo = FormatadorSeguroUI.criarLabel(
                "Revise os itens e avance para registrar o pagamento na janela própria.",
                11, false, "#047857");
        subFluxo.setWrapText(true);
        cardTotal.getChildren().addAll(linhaTotal, subFluxo);

        Button btnAvancarPagamento = FormatadorSeguroUI.criarBotaoCartoon(
                "💳 Ir para Pagamento & Emitir Nota (F10)", "#047857", "#065F46", "#FFFFFF");
        btnAvancarPagamento.setMaxWidth(Double.MAX_VALUE);
        btnAvancarPagamento.setOnAction(e -> abrirJanelaPagamentoVenda());

        btnAjudaErroCaixa.setVisible(false);
        btnAjudaErroCaixa.setManaged(false);

        boxCupom.getChildren().addAll(
                topoCupom, scrollCarrinho, linhaCli, lblRegraClientePadrao,
                gridAjustes, cardTotal, btnAvancarPagamento, lblStatusCaixa, btnAjudaErroCaixa);
        atualizarVisualCarrinho();
        atualizarVisualPagamentosCupom();
        return boxCupom;
    }

    private void abrirJanelaPagamentoVenda() {
        if (carrinho.isEmpty()) {
            lblStatusCaixa.setText("Adicione ao menos 1 produto ao Resumo da Nota antes de avançar ao pagamento.");
            lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #B45309;");
            return;
        }

        BigDecimal total = recalcularTotalCupom();
        atualizarOpcoesModalidadePagamento();
        atualizarVisualPagamentosCupom();

        Stage modalPg = new Stage();
        modalPg.initModality(Modality.APPLICATION_MODAL);
        modalPg.setTitle("Registrar Pagamento da Venda — PDV Balcão");
        if (getScene() != null && getScene().getWindow() != null) {
            modalPg.initOwner(getScene().getWindow());
        }

        Label lblSubHeader = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                "%s • %d item(ns) no cupom • Total Líquido: %s",
                cbCliente.getValue() != null ? cbCliente.getValue() : "Cliente Padrão",
                carrinho.size(), FormatadorSeguroUI.formatarMoeda(total)), 11, false, "#CBD5E1");

        VBox header = new VBox(3);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #0F172A;");
        header.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("💳 Registrar Pagamento da Nota de Venda", 15, true, "#FFFFFF"),
                lblSubHeader
        );

        // Seletor de Cliente Sincronizado dentro do Modal
        ComboBox<String> cbClienteModal = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbClienteModal.getItems().setAll(cbCliente.getItems());
        cbClienteModal.setValue(cbCliente.getValue());
        HBox.setHgrow(cbClienteModal, Priority.ALWAYS);

        Button btnNovoClienteModal = FormatadorSeguroUI.criarBotaoCartoon("+ Novo Cliente", "#047857", "#065F46", "#FFFFFF");
        btnNovoClienteModal.setTooltip(new Tooltip("Cadastrar cliente rápido no balcão"));
        btnNovoClienteModal.setOnAction(e -> abrirModalCadastroRapidoCliente(cbClienteModal, lblSubHeader));

        cbClienteModal.setOnAction(e -> {
            if (cbClienteModal.getValue() != null && !cbClienteModal.getValue().equals(cbCliente.getValue())) {
                cbCliente.setValue(cbClienteModal.getValue());
                atualizarOpcoesModalidadePagamento();
                recalcularTotalCupom();
                lblSubHeader.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                        "%s • %d item(ns) no cupom • Total Líquido: %s",
                        cbClienteModal.getValue(), carrinho.size(), FormatadorSeguroUI.formatarMoeda(recalcularTotalCupom())));
            }
        });

        HBox linhaCliModal = new HBox(8,
                FormatadorSeguroUI.criarLabel("Cliente da Nota (F4):", 12, true, "#334155"),
                cbClienteModal,
                btnNovoClienteModal);
        linhaCliModal.setAlignment(Pos.CENTER_LEFT);

        cbFormaPagamento.setOnAction(e -> atualizarOpcoesModalidadePagamento());
        cbModalidadePag.setOnAction(e -> {
            String mod = cbModalidadePag.getValue();
            if ("Parcelado".equals(mod)) {
                FormaPagamento fp = obterFormaSelecionada();
                if (fp == null || !fp.isPermiteParcelado()) {
                    for (String item : cbFormaPagamento.getItems()) {
                        if (item.toUpperCase().contains("CREDITO") || item.toUpperCase().contains("CARTAO_CREDITO")) {
                            cbFormaPagamento.setValue(item);
                            break;
                        }
                    }
                }
            } else if ("A Prazo".equals(mod)) {
                FormaPagamento fp = obterFormaSelecionada();
                if (fp == null || !fp.isPermiteAPrazo()) {
                    for (String item : cbFormaPagamento.getItems()) {
                        if (item.toUpperCase().contains("BOLETO") || item.toUpperCase().contains("CREDIARIO")) {
                            cbFormaPagamento.setValue(item);
                            break;
                        }
                    }
                }
            }
            atualizarDetalheTaxaPagamento();
        });
        cbParcelas.setOnAction(e -> atualizarDetalheTaxaPagamento());

        GridPane formCheck = new GridPane();
        formCheck.setHgap(8);
        formCheck.setVgap(6);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25.0);
            formCheck.getColumnConstraints().add(c);
        }
        formCheck.add(FormatadorSeguroUI.criarLabel("Forma:", 11, true, "#475569"), 0, 0);
        formCheck.add(FormatadorSeguroUI.criarLabel("Modalidade:", 11, true, "#475569"), 1, 0);
        formCheck.add(FormatadorSeguroUI.criarLabel("Parcelas:", 11, true, "#475569"), 2, 0);
        formCheck.add(FormatadorSeguroUI.criarLabel("Valor Pgto (R$):", 11, true, "#475569"), 3, 0);
        formCheck.add(cbFormaPagamento, 0, 1);
        formCheck.add(cbModalidadePag, 1, 1);
        formCheck.add(cbParcelas, 2, 1);
        formCheck.add(txtValorPagParcial, 3, 1);

        Button btnAddPgto = FormatadorSeguroUI.criarBotaoCartoon("+ Adicionar Pgto", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAddPgto.setOnAction(e -> adicionarPagamentoAoCupom());

        Button btnDiv2 = FormatadorSeguroUI.criarBotaoCartoon("➗ 50%/50%", "#F1F5F9", "#CBD5E1", "#1E293B");
        btnDiv2.setTooltip(new Tooltip("Dividir o total em 2 formas de pagamento iguais"));
        btnDiv2.setOnAction(e -> dividirPagamentoEmPartes(2));

        Button btnDiv3 = FormatadorSeguroUI.criarBotaoCartoon("➗ 3x Formas", "#F1F5F9", "#CBD5E1", "#1E293B");
        btnDiv3.setTooltip(new Tooltip("Dividir o total em 3 formas de pagamento iguais"));
        btnDiv3.setOnAction(e -> dividirPagamentoEmPartes(3));

        Button btnLimparPgs = FormatadorSeguroUI.criarBotaoCartoon("🔄 Limpar", "#FEF2F2", "#FCA5A5", "#991B1B");
        btnLimparPgs.setTooltip(new Tooltip("Limpar formas e reiniciar"));
        btnLimparPgs.setOnAction(e -> {
            pagamentosCupom.clear();
            atualizarVisualPagamentosCupom();
            recalcularTotalCupom();
        });

        HBox barAddPg = new HBox(8, btnAddPgto, btnDiv2, btnDiv3, btnLimparPgs);
        barAddPg.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scrollPgs = FormatadorSeguroUI.envolverScrollSuave(boxPagamentosCupom);
        scrollPgs.setPrefHeight(130);
        scrollPgs.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #CBD5E1; -fx-border-radius: 6px;");

        Label lblErroModal = FormatadorSeguroUI.criarLabel("", 11, true, "#DC2626");
        lblErroModal.setWrapText(true);

        Label lblRegraModal = new Label();
        lblRegraModal.textProperty().bind(lblRegraClientePadrao.textProperty());
        lblRegraModal.styleProperty().bind(lblRegraClientePadrao.styleProperty());
        lblRegraModal.setWrapText(true);
        lblRegraModal.setMaxWidth(Double.MAX_VALUE);

        VBox corpo = new VBox(10,
                linhaCliModal,
                lblRegraModal,
                formCheck,
                lblDetalheTaxa,
                barAddPg,
                lblSaldoPagamentos,
                scrollPgs,
                lblErroModal);
        corpo.setPadding(new Insets(14, 16, 12, 16));
        corpo.setStyle("-fx-background-color: #FFFFFF;");

        Button btnCancelar = FormatadorSeguroUI.criarBotaoCartoon("Voltar ao Resumo", "#F1F5F9", "#CBD5E1", "#334155");
        btnCancelar.setOnAction(e -> modalPg.close());

        Button btnEmitir = FormatadorSeguroUI.criarBotaoCartoon("✔ Confirmar Pagamento & Emitir Nota A4", "#047857", "#065F46", "#FFFFFF");
        btnEmitir.setOnAction(e -> {
            Long idCli = extrairIdPrefixado(cbCliente.getValue());
            boolean clientePadrao = (idCli == null);
            BigDecimal totalLiquido = recalcularTotalCupom();
            List<PagamentoCupom> listaFinalPagamentos = new ArrayList<>(pagamentosCupom);
            if (listaFinalPagamentos.isEmpty()) {
                FormaPagamento fp = obterFormaSelecionada();
                String codigoForma = fp != null ? fp.getCodigo() : "PIX";
                String nomeForma = fp != null ? fp.getNome() : "PIX";
                String mod = clientePadrao ? "À Vista" : (cbModalidadePag.getValue() != null ? cbModalidadePag.getValue() : "À Vista");
                int parc = !clientePadrao && "Parcelado".equals(mod) && cbParcelas.getValue() != null ? cbParcelas.getValue() : 1;
                listaFinalPagamentos.add(new PagamentoCupom(codigoForma, nomeForma, mod, parc, totalLiquido));
            }
            BigDecimal somaFinal = listaFinalPagamentos.stream().map(PagamentoCupom::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (somaFinal.compareTo(totalLiquido) < 0) {
                BigDecimal falta = totalLiquido.subtract(somaFinal);
                lblErroModal.setText(String.format("⚠️ O total dos pagamentos (%s) não cobre a nota (%s). Faltam %s! Adicione outra forma ou ajuste os valores.",
                        FormatadorSeguroUI.formatarMoeda(somaFinal), FormatadorSeguroUI.formatarMoeda(totalLiquido), FormatadorSeguroUI.formatarMoeda(falta)));
                return;
            }
            if (clientePadrao && listaFinalPagamentos.stream().anyMatch(p -> !"À Vista".equals(p.modalidade()))) {
                lblErroModal.setText("⚠️ Cliente Padrão (Balcão) só pode pagar À Vista. Selecione ou cadastre um cliente para parcelar ou comprar a prazo.");
                return;
            }
            modalPg.close();
            executarEmissaoVenda(totalLiquido, idCli, listaFinalPagamentos);
        });

        HBox rodape = new HBox(10, btnCancelar, btnEmitir);
        rodape.setAlignment(Pos.CENTER_RIGHT);
        rodape.setPadding(new Insets(10, 16, 12, 16));
        rodape.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 1px 0 0 0;");

        VBox root = new VBox(header, corpo, rodape);
        root.setPrefWidth(680);
        modalPg.setScene(new Scene(root));
        modalPg.showAndWait();
    }

    private void abrirModalCadastroRapidoCliente(ComboBox<String> cbModal, Label lblSubHeader) {
        Stage modalCad = new Stage();
        modalCad.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) modalCad.initOwner(getScene().getWindow());
        modalCad.setTitle("Cadastro Rápido de Cliente — Balcão");

        TextField tfNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome Completo", "#CBD5E1", "#0F172A");
        TextField tfCpf = FormatadorSeguroUI.criarCampoTextoCartoon("CPF (ex: 123.456.789-00)", "#CBD5E1", "#0F172A");
        TextField tfLimite = FormatadorSeguroUI.criarCampoTextoCartoon("Limite de Crédito (R$)", "#CBD5E1", "#0F172A");
        tfLimite.setText("1000,00");

        Label lblMsg = FormatadorSeguroUI.criarLabel("", 11, true, "#DC2626");

        Button btnSalvar = FormatadorSeguroUI.criarBotaoCartoon("Salvar e Selecionar (Enter)", "#047857", "#065F46", "#FFFFFF");
        btnSalvar.setOnAction(e -> {
            String nome = tfNome.getText() != null ? tfNome.getText().trim() : "";
            if (nome.isBlank()) {
                lblMsg.setText("Informe o nome do cliente.");
                return;
            }
            String cpf = tfCpf.getText() != null ? tfCpf.getText().trim() : "";
            BigDecimal limite = parseDecimalBr(tfLimite.getText(), new BigDecimal("1000.00"));

            btnSalvar.setDisable(true);
            Thread.ofVirtual().start(() -> {
                var resp = ponte.getRoteador().executarEndpoint(RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR,
                        Map.of("nome", nome, "cpf", cpf, "limiteCredito", limite));
                Platform.runLater(() -> {
                    btnSalvar.setDisable(false);
                    if (resp.sucesso() && resp.dados() instanceof Pessoa novaP) {
                        String itemNovo = "#" + novaP.getId() + " - " + novaP.getNome();
                        clientesCache.add(novaP);
                        if (!cbCliente.getItems().contains(itemNovo)) cbCliente.getItems().add(itemNovo);
                        if (!cbModal.getItems().contains(itemNovo)) cbModal.getItems().add(itemNovo);
                        cbCliente.setValue(itemNovo);
                        cbModal.setValue(itemNovo);
                        atualizarOpcoesModalidadePagamento();
                        recalcularTotalCupom();
                        if (lblSubHeader != null) {
                            lblSubHeader.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                                    "%s • %d item(ns) no cupom • Total Líquido: %s",
                                    itemNovo, carrinho.size(), FormatadorSeguroUI.formatarMoeda(recalcularTotalCupom())));
                        }
                        modalCad.close();
                    } else {
                        lblMsg.setText("Erro ao cadastrar: " + resp.mensagem());
                    }
                });
            });
        });

        VBox box = new VBox(10,
                FormatadorSeguroUI.criarLabel("👤 Novo Cliente (Balcão)", 14, true, "#0F172A"),
                FormatadorSeguroUI.criarLabel("Cadastre para liberar crediário, parcelamento e venda a prazo.", 11, false, "#64748B"),
                FormatadorSeguroUI.criarLabel("Nome Completo:", 11, true, "#475569"), tfNome,
                FormatadorSeguroUI.criarLabel("CPF:", 11, true, "#475569"), tfCpf,
                FormatadorSeguroUI.criarLabel("Limite de Crédito Inicial (R$):", 11, true, "#475569"), tfLimite,
                lblMsg,
                btnSalvar
        );
        box.setPadding(new Insets(16));
        box.setPrefWidth(380);
        box.setStyle("-fx-background-color: #FFFFFF;");
        modalCad.setScene(new Scene(box));
        tfNome.requestFocus();
        modalCad.showAndWait();
    }

    // ==================== SUB-ABA 2: DEVOLUÇÃO & PERDA PÓS-VENDA ====================

    private VBox construirSubAbaPosVenda() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        VBox cardPosVenda = new VBox(10);
        cardPosVenda.setPadding(new Insets(14));
        cardPosVenda.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label tit = FormatadorSeguroUI.criarTituloSecao("↩️", "Registrar Devolução / Perda Pós-Venda (Preferencial no PDV)", "#0F172A");
        Label desc = FormatadorSeguroUI.criarSubtitulo(
                "Fluxo fiscal: toda perda pós-venda registra primeiro a Nota de Devolução e, ao marcar abatimento p/ fornecedor, encadeia automaticamente a Nota de Perda.", "#64748B");

        cbNotaVendaOrigem.setOnAction(e -> popularItensVendaSelecionada());
        chkGerarPerdaFornecedor.setSelected(true);
        chkGerarPerdaFornecedor.setWrapText(true);
        chkGerarPerdaFornecedor.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #991B1B;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        grid.add(FormatadorSeguroUI.criarLabel("Nota de Venda Original:", 12, true, "#334155"), 0, 0);
        grid.add(FormatadorSeguroUI.criarLabel("Item Vendido:", 12, true, "#334155"), 1, 0);
        grid.add(cbNotaVendaOrigem, 0, 1);
        grid.add(cbItemVendaOrigem, 1, 1);

        grid.add(FormatadorSeguroUI.criarLabel("Quantidade Devolvida:", 12, true, "#334155"), 0, 2);
        grid.add(FormatadorSeguroUI.criarLabel("Motivo da Devolução / Avaria:", 12, true, "#334155"), 1, 2);
        grid.add(txtQtdDevolvida, 0, 3);
        grid.add(txtMotivoDevolucao, 1, 3);

        Button btnRegistrarDev = FormatadorSeguroUI.criarBotaoCartoon("Registrar Devolução / Perda Pós-Venda", "#B45309", "#92400E", "#FFFFFF");
        btnRegistrarDev.setOnAction(e -> registrarDevolucaoPosVenda());

        cardPosVenda.getChildren().addAll(tit, desc, grid, chkGerarPerdaFornecedor, btnRegistrarDev, lblStatusPosVenda);

        // Cartão Secundário: Avaria Imediata de Balcão
        VBox cardAvariaBalcao = new VBox(8);
        cardAvariaBalcao.setPadding(new Insets(12, 14, 12, 14));
        cardAvariaBalcao.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFBEB", "#FCD34D"));
        Label titAv = FormatadorSeguroUI.criarLabel("⚡ Baixa Rápida de Avaria no Balcão (Opcional — sem vínculo de venda)", 13, true, "#92400E");

        HBox linhaAv = new HBox(8, cbLoteAvariaBalcao, txtQtdAvariaBalcao, txtMotivoAvariaBalcao);
        cbLoteAvariaBalcao.setPrefWidth(260);
        txtQtdAvariaBalcao.setPrefWidth(75);
        HBox.setHgrow(txtMotivoAvariaBalcao, Priority.ALWAYS);

        Button btnBaixarAvaria = FormatadorSeguroUI.criarBotaoCartoon("Registrar Perda Direta", "#DC2626", "#991B1B", "#FFFFFF");
        btnBaixarAvaria.setOnAction(e -> registrarAvariaDiretaBalcao());
        linhaAv.getChildren().add(btnBaixarAvaria);
        linhaAv.setAlignment(Pos.CENTER_LEFT);
        cardAvariaBalcao.getChildren().addAll(titAv, linhaAv);

        VBox cardHist = new VBox(8);
        cardHist.setPadding(new Insets(12, 14, 12, 14));
        cardHist.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        cardHist.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📋 Histórico Recente de Devoluções e Perdas Pós-Venda (Clique para Abrir & Imprimir)", 13, true, "#0F172A"),
                boxHistoricoPosVenda);

        raiz.getChildren().addAll(cardPosVenda, cardAvariaBalcao, cardHist);
        return raiz;
    }

    // ==================== COMANDOS RÁPIDOS, VOZ & IA ====================

    private void alternarVozPDV() {
        if (!gravandoVozPDV) {
            try {
                if (!ponte.getCapturadorVoz().isMicrofoneDisponivel()) {
                    lblFeedbackCmdPDV.setText("⚠️ Dispositivo de áudio não detectado — abrindo Ditado Assistido / Comando de Voz.");
                    abrirModalEntradaVozAssistida();
                    return;
                }
                ponte.getCapturadorVoz().iniciarGravacao();
                gravandoVozPDV = true;
                FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVozPDV, "#FEE2E2", "#EF4444", "#991B1B", 12);
                btnVozPDV.setText("⏹️ Parar Voz (F6)");
                lblFeedbackCmdPDV.setText("🎤 Gravando comando de voz... Fale o produto, quantidade, cliente, preço ou devolução e pressione F6.");
            } catch (Exception ex) {
                lblFeedbackCmdPDV.setText("⚠️ Falha ao abrir microfone (" + FormatadorSeguroUI.traduzirErroAmigavel(ex.getMessage()) + ") — abrindo Ditado.");
                abrirModalEntradaVozAssistida();
            }
        } else {
            gravandoVozPDV = false;
            FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVozPDV, "#EFF6FF", "#93C5FD", "#1E40AF", 12);
            btnVozPDV.setText("🎤 Voz (F6)");
            lblFeedbackCmdPDV.setText("🤖 Transcrevendo e interpretando comando de voz...");
            ponte.pararVozEEnviarAsync(
                    etapa -> lblFeedbackCmdPDV.setText("🤖 " + etapa),
                    res -> {
                        String respTxt = FormatadorSeguroUI.tratarParaExibicao(res.respostaTexto(), "Comando de voz processado.");
                        lblFeedbackCmdPDV.setText("🤖 " + respTxt);
                        if (!res.bandeja().getItens().isEmpty()) {
                            lblFeedbackCmdPDV.setText(lblFeedbackCmdPDV.getText()
                                    + " • Operações preparadas na Bandeja IA (ver Central IA / balão).");
                        }
                    });
        }
    }

    private void abrirModalEntradaVozAssistida() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("🎤 Comando de Voz / Ditado Rápido — PDV");
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());

        Label tit = FormatadorSeguroUI.criarLabel("🎤 Entrada por Ditado ou Voz do Sistema (Win+H no Windows)", 14, true, "#0F172A");
        Label sub = FormatadorSeguroUI.criarLabel(
                "Exemplos reconhecidos na hora: 'add 2 gc', 'adicionar 3 agua', 'preco 15,90', 'cliente Maria', 'pagar', 'devolver nota 5'.",
                11, false, "#475569");
        sub.setWrapText(true);
        TextField txtDitado = FormatadorSeguroUI.criarCampoTextoCartoon(
                "Pressione Win+H para ditar ou digite o comando falado...", "#93C5FD", "#0F172A");

        Button btnOk = FormatadorSeguroUI.criarBotaoCartoon("⚡ Executar no PDV", "#047857", "#065F46", "#FFFFFF");
        Runnable acao = () -> {
            String f = txtDitado.getText() != null ? txtDitado.getText().trim() : "";
            dlg.close();
            if (!f.isEmpty()) executarTextoVozNoPDV(f);
        };
        btnOk.setOnAction(e -> acao.run());
        txtDitado.setOnAction(e -> acao.run());
        Button btnFechar = FormatadorSeguroUI.criarBotaoCartoon("Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnFechar.setOnAction(e -> dlg.close());

        HBox rod = new HBox(8, btnFechar, btnOk);
        rod.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(10, tit, sub, txtDitado, rod);
        box.setPadding(new Insets(16));
        box.setPrefWidth(540);
        box.setStyle("-fx-background-color: #FFFFFF;");
        dlg.setScene(new Scene(box));
        Platform.runLater(txtDitado::requestFocus);
        dlg.showAndWait();
    }

    private void executarTextoVozNoPDV(String comandoFalado) {
        txtComandoRapidoIA.setText(comandoFalado);
        processarComandoRapidoTextoOuIA();
    }

    private void processarComandoRapidoTextoOuIA() {
        String cmd = txtComandoRapidoIA.getText() != null ? txtComandoRapidoIA.getText().trim() : "";
        if (cmd.isEmpty()) return;

        // 1. Atalhos locais instantâneos de balcão (zero latência!)
        String low = cmd.toLowerCase(Locale.ROOT);
        if (low.startsWith("add ") || low.startsWith("adicionar ") || low.startsWith("+")) {
            if (tentarAdicionarPorTexto(cmd)) {
                txtComandoRapidoIA.clear();
                return;
            }
        }
        if (low.startsWith("preco ") || low.startsWith("preço ") || low.startsWith("valor ")) {
            String valStr = cmd.replaceAll("^(?i)(preco|preço|valor)\\s+", "").trim();
            BigDecimal novoPreco = parseDecimalBr(valStr, null);
            if (novoPreco != null && !carrinho.isEmpty()) {
                ItemCarrinho ultimo = carrinho.get(carrinho.size() - 1);
                ultimo.precoUnitario = novoPreco;
                atualizarVisualCarrinho();
                lblFeedbackCmdPDV.setText("✔ Valor unitário de '" + ultimo.produto.getNome()
                        + "' ajustado para " + FormatadorSeguroUI.formatarMoeda(novoPreco) + ".");
                txtComandoRapidoIA.clear();
                return;
            }
        }
        if (low.startsWith("cliente ")) {
            String termoCli = cmd.substring(8).trim().toLowerCase(Locale.ROOT);
            for (String opt : cbCliente.getItems()) {
                if (opt.toLowerCase(Locale.ROOT).contains(termoCli)) {
                    cbCliente.setValue(opt);
                    atualizarOpcoesModalidadePagamento();
                    lblFeedbackCmdPDV.setText("✔ Cliente selecionado: " + opt);
                    txtComandoRapidoIA.clear();
                    return;
                }
            }
        }
        if (low.equals("pagar") || low.equals("pagamento") || low.equals("finalizar") || low.equals("vender") || low.equals("concluir")) {
            txtComandoRapidoIA.clear();
            abrirJanelaPagamentoVenda();
            return;
        }

        // 2. Comando complexo: envia para o Agente Operacional IA
        lblFeedbackCmdPDV.setText("🤖 Executando comando no PDV com IA...");
        ponte.enviarMensagemIAAsync(cmd,
                etapa -> lblFeedbackCmdPDV.setText("🤖 " + etapa),
                res -> {
                    lblFeedbackCmdPDV.setText("🤖 " + FormatadorSeguroUI.tratarParaExibicao(res.respostaTexto(), "Concluído."));
                    txtComandoRapidoIA.clear();
                });
    }

    private boolean tentarAdicionarPorTexto(String cmd) {
        String semVerbo = cmd.replaceAll("^(?i)(adicionar|add|\\+)\\s*", "").trim();
        BigDecimal qtd = BigDecimal.ONE;
        Matcher m = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s+(.+)$").matcher(semVerbo);
        String termo = semVerbo;
        if (m.matches()) {
            qtd = parseDecimalBr(m.group(1), BigDecimal.ONE);
            termo = m.group(2).trim();
        }
        String termoNorm = normalizarSemAcento(termo);
        for (ItemCatalogoPDV item : tabelaCatalogo.getItems()) {
            boolean bateSigla = item.siglaProduto() != null && normalizarSemAcento(item.siglaProduto()).equals(termoNorm);
            boolean bateUm = item.umSigla() != null && normalizarSemAcento(item.umSigla()).equals(termoNorm);
            boolean bateNome = normalizarSemAcento(item.nome()).contains(termoNorm);
            boolean bateCod = normalizarSemAcento(item.codigoExibicao()).equals(termoNorm);
            if ((bateSigla || bateUm || bateNome || bateCod) && item.saldo().compareTo(BigDecimal.ZERO) > 0) {
                adicionarAoCarrinhoComQtd(item, qtd);
                lblFeedbackCmdPDV.setText("✔ Adicionado " + FormatadorSeguroUI.formatarQuantidade(qtd, item.umSigla())
                        + " de " + item.nome() + " (" + item.siglaProduto() + ") ao cupom.");
                return true;
            }
        }
        return false;
    }

    // ==================== CICLO LAZY & DADOS ====================

    public void setAtiva(boolean ativa) {
        this.abaAtiva = ativa;
        if (ativa) {
            carregarSeNecessario();
            Platform.runLater(() -> txtBuscaProduto.requestFocus());
        }
    }

    public void carregarSeNecessario() {
        if (!precisaRecarregar || carregando) return;
        precisaRecarregar = false;
        carregando = true;

        Thread.ofVirtual().start(() -> {
            try {
                List<Produto> prods = ponte.listarProdutos();
                List<Item<? extends Produto>> lotes = ponte.listarLotesComSaldo();
                ConsolidadoEstoque cons = ponte.consultarSaldosConsolidadosPorProduto();
                List<Pessoa> clis = ponte.listarClientes();
                List<FormaPagamento> fps = ponte.listarFormasPagamentoAtivas();
                List<Nota> notas = ponte.listarNotas();

                Platform.runLater(() -> {
                    this.produtosCache = prods;
                    this.lotesCache = lotes;
                    this.consolidadoCache = cons;
                    this.clientesCache = clis;
                    this.formasPagamentoCache = fps;
                    this.notasCache = notas;

                    popularCombosCaixa();
                    filtrarCatalogoNaTabela();
                    popularCombosPosVenda();
                    renderizarHistoricoPosVenda();
                });
            } finally {
                carregando = false;
            }
        });
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
        }
    }

    private void popularCombosCaixa() {
        String selCli = cbCliente.getValue();
        cbCliente.getItems().clear();
        cbCliente.getItems().add("Consumidor Final (Balcão)");
        for (Pessoa c : clientesCache) {
            if (c.getNome() != null && !c.getNome().toLowerCase(Locale.ROOT).contains("não registrado")) {
                cbCliente.getItems().add("#" + c.getId() + " - " + c.getNome());
            }
        }
        if (selCli != null && cbCliente.getItems().contains(selCli)) cbCliente.setValue(selCli);
        else cbCliente.getSelectionModel().selectFirst();

        String selFp = cbFormaPagamento.getValue();
        cbFormaPagamento.getItems().clear();
        for (FormaPagamento fp : formasPagamentoCache) {
            cbFormaPagamento.getItems().add(fp.getCodigo() + " - " + fp.getNome());
        }
        if (selFp != null && cbFormaPagamento.getItems().contains(selFp)) cbFormaPagamento.setValue(selFp);
        else if (!cbFormaPagamento.getItems().isEmpty()) cbFormaPagamento.getSelectionModel().selectFirst();
        atualizarOpcoesModalidadePagamento();
    }

    private boolean isClientePadraoSelecionado() {
        return extrairIdPrefixado(cbCliente.getValue()) == null;
    }

    private void atualizarOpcoesModalidadePagamento() {
        boolean clientePadrao = isClientePadraoSelecionado();
        lblRegraClientePadrao.setVisible(true);
        lblRegraClientePadrao.setManaged(true);

        if (clientePadrao) {
            lblRegraClientePadrao.setText("ℹ️ Consumidor Balcão: múltiplos pagamentos permitidos À Vista (ex: R$ 30 PIX + R$ 20 Dinheiro). Para parcelar ou vender a prazo, selecione ou cadastre um cliente.");
            lblRegraClientePadrao.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #B45309; -fx-background-color: #FEF3C7; -fx-padding: 6px 10px; -fx-background-radius: 6px;");
            if (!pagamentosCupom.isEmpty()) {
                pagamentosCupom.removeIf(p -> !"À Vista".equals(p.modalidade()));
                atualizarVisualPagamentosCupom();
            }
            cbModalidadePag.getItems().setAll("À Vista");
            cbModalidadePag.getSelectionModel().selectFirst();
            cbParcelas.getItems().setAll(1);
            cbParcelas.getSelectionModel().selectFirst();
            cbParcelas.setDisable(true);
        } else {
            lblRegraClientePadrao.setText("✅ Cliente Registrado: " + cbCliente.getValue() + " — Permitido combinar pagamentos À Vista, A Prazo e Parcelado no mesmo cupom!");
            lblRegraClientePadrao.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #047857; -fx-background-color: #D1FAE5; -fx-padding: 6px 10px; -fx-background-radius: 6px;");

            String selMod = cbModalidadePag.getValue();
            cbModalidadePag.getItems().setAll("À Vista", "Parcelado", "A Prazo");
            if (selMod != null && cbModalidadePag.getItems().contains(selMod)) {
                cbModalidadePag.setValue(selMod);
            } else {
                cbModalidadePag.getSelectionModel().selectFirst();
            }

            FormaPagamento fp = obterFormaSelecionada();
            int maxParc = (fp != null && fp.getMaxParcelas() > 1) ? fp.getMaxParcelas() : 12;
            cbParcelas.getItems().clear();
            for (int i = 1; i <= maxParc; i++) cbParcelas.getItems().add(i);
            if ("Parcelado".equals(cbModalidadePag.getValue())) {
                cbParcelas.setValue(cbParcelas.getItems().contains(2) ? 2 : 1);
                cbParcelas.setDisable(false);
            } else {
                cbParcelas.getSelectionModel().selectFirst();
                cbParcelas.setDisable(true);
            }
        }
        atualizarDetalheTaxaPagamento();
    }

    private void atualizarDetalheTaxaPagamento() {
        FormaPagamento fp = obterFormaSelecionada();
        String mod = cbModalidadePag.getValue();
        boolean isParc = "Parcelado".equals(mod) && !isClientePadraoSelecionado();
        cbParcelas.setDisable(!isParc);
        if (isParc) {
            if (cbParcelas.getValue() == null || cbParcelas.getValue() < 2) {
                if (cbParcelas.getItems().contains(2)) cbParcelas.setValue(2);
            }
        } else {
            if (!cbParcelas.getItems().isEmpty()) cbParcelas.getSelectionModel().selectFirst();
        }

        if (fp == null) {
            lblDetalheTaxa.setText("");
            return;
        }
        lblDetalheTaxa.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "Regra %s: Taxa operadora %.2f%% • Desc. à vista %.2f%% • Até %dx (%dx s/ juros)%s",
                fp.getCodigo(),
                fp.getTaxaOperadoraPercentual().doubleValue(),
                fp.getPercentualDescontoAVistaPadrao().doubleValue(),
                fp.getMaxParcelas(),
                fp.getMaxParcelasSemJuros(),
                isParc && cbParcelas.getValue() != null && cbParcelas.getValue() > fp.getMaxParcelasSemJuros()
                        ? String.format(" • Juros parcelamento: %.2f%% a.m.", fp.getTaxaJurosMensalPadrao().doubleValue()) : ""));
    }

    private FormaPagamento obterFormaSelecionada() {
        String txt = cbFormaPagamento.getValue();
        if (txt == null) return null;
        String codigo = txt.split(" - ")[0].trim();
        return formasPagamentoCache.stream()
                .filter(f -> codigo.equalsIgnoreCase(f.getCodigo()))
                .findFirst().orElse(null);
    }

    private static String normalizarSemAcento(String s) {
        if (s == null) return "";
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return norm.toLowerCase(Locale.ROOT).trim();
    }

    private static String gerarSiglaProduto(String nome) {
        String limpo = FormatadorSeguroUI.limparNomeProduto(nome);
        if (limpo.isBlank()) return "PR";
        String[] partes = limpo.replace("-", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            String alfa = Normalizer.normalize(p, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "");
            if (!alfa.isEmpty() && !"de".equalsIgnoreCase(alfa) && !"da".equalsIgnoreCase(alfa)
                    && !"do".equalsIgnoreCase(alfa) && !"e".equalsIgnoreCase(alfa)) {
                sb.append(Character.toUpperCase(alfa.charAt(0)));
            }
            if (sb.length() >= 4) break;
        }
        if (sb.length() < 2) {
            String alfa = Normalizer.normalize(limpo, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
            return alfa.length() >= 3 ? alfa.substring(0, 3) : (alfa.isEmpty() ? "PR" : alfa);
        }
        return sb.toString();
    }

    private void filtrarCatalogoNaTabela() {
        String termo = normalizarSemAcento(txtBuscaProduto.getText());
        List<ItemCatalogoPDV> linhas = new ArrayList<>();

        if (tgModoAutomatico.isSelected()) {
            Map<Long, BigDecimal> saldos = consolidadoCache != null ? consolidadoCache.saldosPorProduto() : Map.of();
            Map<Long, LocalDate> menorVal = consolidadoCache != null ? consolidadoCache.menorValidadePorProduto() : Map.of();
            for (Produto p : produtosCache) {
                String nomeLimpo = FormatadorSeguroUI.limparNomeProduto(p.getNome());
                String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
                String siglaProd = gerarSiglaProduto(nomeLimpo);
                String buscaBase = normalizarSemAcento("#" + p.getId() + " " + siglaProd + " " + um + " " + p.getNome());
                if (!termo.isEmpty() && !buscaBase.contains(termo)) continue;
                BigDecimal saldo = saldos.getOrDefault(p.getId(), BigDecimal.ZERO);
                linhas.add(new ItemCatalogoPDV(
                        p, null, "#" + p.getId(), siglaProd,
                        nomeLimpo, um, saldo, menorVal.get(p.getId()),
                        p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO));
            }
            linhas.sort(Comparator.comparing((ItemCatalogoPDV r) -> r.saldo().compareTo(BigDecimal.ZERO) <= 0)
                    .thenComparing(ItemCatalogoPDV::nome));
        } else {
            for (Item<? extends Produto> l : lotesCache) {
                Produto p = l.getProduto();
                if (p == null) continue;
                String nomeLimpo = FormatadorSeguroUI.limparNomeProduto(p.getNome());
                String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
                String siglaProd = gerarSiglaProduto(nomeLimpo);
                String buscaBase = normalizarSemAcento("lote #" + l.getId() + " #" + p.getId() + " " + siglaProd + " " + um + " " + p.getNome());
                if (!termo.isEmpty() && !buscaBase.contains(termo)) continue;
                linhas.add(new ItemCatalogoPDV(
                        p, l, "Lote #" + l.getId(), siglaProd,
                        nomeLimpo, um, l.getQuantidadeDisponivel(), l.getDataValidade(),
                        p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO));
            }
        }
        tabelaCatalogo.setItems(FXCollections.observableArrayList(linhas));
    }

    private void abrirCardDetalhesCatalogo(ItemCatalogoPDV sel) {
        Produto p = sel.produto();
        CarrosselImagensProduto carrosselModal = new CarrosselImagensProduto(ponte, false, 280, 175);
        carrosselModal.carregarProdutoSobDemanda(p);

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Código", "#" + p.getId());
        meta.put("Produto", sel.nome());
        meta.put("Saldo Disponível", FormatadorSeguroUI.formatarQuantidade(sel.saldo(), sel.umSigla()));
        meta.put("Preço de Venda", FormatadorSeguroUI.formatarMoeda(sel.preco()));
        meta.put("Validade mais Próxima", sel.validade() != null ? FormatadorSeguroUI.formatarDataAmigavel(sel.validade()) : "Indeterminada");
        meta.put("Modo de Baixa", sel.loteEspecifico() != null ? "Lote Manual #" + sel.loteEspecifico().getId() : "Seleção Automática por Validade");

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🏷️", sel.nome(), "Ficha do Produto no PDV", "#1D4ED8",
                meta, carrosselModal, null,
                () -> conectorIA.sugerirPromocaoOuReposicao(sel.nome(), sel.saldo(), sel.validade()));
    }

    private void adicionarAoCarrinho(ItemCatalogoPDV item) {
        adicionarAoCarrinhoComQtd(item, BigDecimal.ONE);
    }

    private void adicionarAoCarrinhoComQtd(ItemCatalogoPDV item, BigDecimal qtdAdd) {
        carrosselPDV.carregarProdutoSobDemanda(item.produto());
        Long idLote = item.loteEspecifico() != null ? item.loteEspecifico().getId() : null;
        for (ItemCarrinho ic : carrinho) {
            boolean mesmoProd = ic.produto.getId().equals(item.produto().getId());
            boolean mesmoLote = (ic.idLoteEspecifico == null && idLote == null)
                    || (ic.idLoteEspecifico != null && ic.idLoteEspecifico.equals(idLote));
            if (mesmoProd && mesmoLote) {
                ic.quantidade = ic.quantidade.add(qtdAdd);
                atualizarVisualCarrinho();
                return;
            }
        }
        String rotulo = idLote != null ? "Lote #" + idLote : "Automático";
        carrinho.add(new ItemCarrinho(item.produto(), idLote, rotulo, qtdAdd));
        atualizarVisualCarrinho();
    }

    private void atualizarVisualCarrinho() {
        boxListaCarrinho.getChildren().clear();
        if (carrinho.isEmpty()) {
            Label lblVazio = FormatadorSeguroUI.criarLabel(
                    "Cupom vazio. Selecione um item no catálogo e pressione Enter ou clique em '+ Add'.", 12, false, "#64748B");
            lblVazio.setWrapText(true);
            lblVazio.setPadding(new Insets(14, 8, 14, 8));
            boxListaCarrinho.getChildren().add(lblVazio);
        } else {
            for (int i = 0; i < carrinho.size(); i++) {
                final ItemCarrinho ic = carrinho.get(i);
                final int idx = i;

                // --- LINHA 1: Nome do Produto + Badge Lote (à esquerda) | Subtotal + Botão Remover (à direita) ---
                String nomeLimpo = FormatadorSeguroUI.limparNomeProduto(ic.produto.getNome());
                Label lNome = FormatadorSeguroUI.criarLabel(nomeLimpo, 12, true, "#0F172A");
                lNome.setWrapText(true);
                lNome.setMinWidth(0);
                lNome.setTooltip(new Tooltip(ic.produto.getNome() + " — " + ic.rotuloLote));

                Label badgeLote = FormatadorSeguroUI.criarBadgeCartoon(ic.rotuloLote, "#F1F5F9", "#CBD5E1", "#475569");

                HBox boxProd = new HBox(6, lNome, badgeLote);
                boxProd.setAlignment(Pos.CENTER_LEFT);
                boxProd.setMinWidth(0);
                HBox.setHgrow(boxProd, Priority.ALWAYS);

                Label lSub = FormatadorSeguroUI.criarLabel(
                        FormatadorSeguroUI.formatarMoeda(ic.getSubtotal()), 13, true, "#047857");
                lSub.setMinWidth(Region.USE_PREF_SIZE);

                Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
                btnRem.setTooltip(new Tooltip("Remover este item do cupom"));
                btnRem.setStyle(btnRem.getStyle() + "-fx-padding: 3px 8px;");
                btnRem.setMinSize(32, 28);
                btnRem.setPrefSize(32, 28);
                btnRem.setOnAction(e -> {
                    carrinho.remove(idx);
                    atualizarVisualCarrinho();
                });

                HBox boxSubRem = new HBox(8, lSub, btnRem);
                boxSubRem.setAlignment(Pos.CENTER_RIGHT);

                HBox linhaTopo = new HBox(8, boxProd, boxSubRem);
                linhaTopo.setAlignment(Pos.CENTER_LEFT);

                // --- LINHA 2: Preço Unitário (à esquerda) | Ajuste de Quantidade (à direita) ---
                Label lblPrecoTit = FormatadorSeguroUI.criarLabel("Un.: R$", 11, false, "#64748B");
                TextField tfValorUnit = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#1D4ED8");
                tfValorUnit.setText(ic.precoUnitario.toPlainString().replace(".", ","));
                tfValorUnit.setPrefWidth(72);
                tfValorUnit.setMaxWidth(80);
                tfValorUnit.setAlignment(Pos.CENTER_RIGHT);

                Runnable salvarPreco = () -> {
                    BigDecimal nv = parseDecimalBr(tfValorUnit.getText(), ic.precoUnitario);
                    if (nv.compareTo(BigDecimal.ZERO) >= 0 && nv.compareTo(ic.precoUnitario) != 0) {
                        ic.precoUnitario = nv;
                        atualizarVisualCarrinho();
                    }
                };
                tfValorUnit.setOnAction(e -> salvarPreco.run());
                tfValorUnit.focusedProperty().addListener((obs, oldF, newF) -> {
                    if (!newF) salvarPreco.run();
                });

                HBox boxPreco = new HBox(4, lblPrecoTit, tfValorUnit);
                boxPreco.setAlignment(Pos.CENTER_LEFT);

                Label lblQtdTit = FormatadorSeguroUI.criarLabel("Qtd:", 11, false, "#64748B");
                Button btnMenos = FormatadorSeguroUI.criarBotaoCartoon("−", "#F1F5F9", "#CBD5E1", "#0F172A");
                btnMenos.setStyle(btnMenos.getStyle() + "-fx-padding: 3px 8px;");
                btnMenos.setMinSize(32, 28);
                btnMenos.setPrefSize(32, 28);
                btnMenos.setOnAction(e -> {
                    if (ic.quantidade.compareTo(BigDecimal.ONE) > 0) {
                        ic.quantidade = ic.quantidade.subtract(BigDecimal.ONE);
                    } else {
                        carrinho.remove(idx);
                    }
                    atualizarVisualCarrinho();
                });

                TextField tfQtd = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
                tfQtd.setText(ic.quantidade.stripTrailingZeros().toPlainString().replace(".", ","));
                tfQtd.setPrefWidth(48);
                tfQtd.setMaxWidth(56);
                tfQtd.setAlignment(Pos.CENTER);

                Runnable salvarQtd = () -> {
                    BigDecimal q = parseDecimalBr(tfQtd.getText(), ic.quantidade);
                    if (q.compareTo(BigDecimal.ZERO) > 0 && q.compareTo(ic.quantidade) != 0) {
                        ic.quantidade = q;
                        atualizarVisualCarrinho();
                    }
                };
                tfQtd.setOnAction(e -> salvarQtd.run());
                tfQtd.focusedProperty().addListener((obs, oldF, newF) -> {
                    if (!newF) salvarQtd.run();
                });

                Button btnMais = FormatadorSeguroUI.criarBotaoCartoon("+", "#F1F5F9", "#CBD5E1", "#0F172A");
                btnMais.setStyle(btnMais.getStyle() + "-fx-padding: 3px 8px;");
                btnMais.setMinSize(32, 28);
                btnMais.setPrefSize(32, 28);
                btnMais.setOnAction(e -> {
                    ic.quantidade = ic.quantidade.add(BigDecimal.ONE);
                    atualizarVisualCarrinho();
                });

                String siglaUm = (ic.produto.getUm() != null && ic.produto.getUm().getSigla() != null)
                        ? ic.produto.getUm().getSigla() : "UN";
                Label lblUm = FormatadorSeguroUI.criarLabel(siglaUm, 11, false, "#64748B");

                HBox boxQtd = new HBox(4, lblQtdTit, btnMenos, tfQtd, btnMais, lblUm);
                boxQtd.setAlignment(Pos.CENTER_RIGHT);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox linhaControles = new HBox(6, boxPreco, spacer, boxQtd);
                linhaControles.setAlignment(Pos.CENTER_LEFT);

                // --- CARD COMPACTO, SEGURO & ESPAÇOSO ---
                VBox cardItem = new VBox(6, linhaTopo, linhaControles);
                cardItem.setPadding(new Insets(8, 10, 8, 10));
                cardItem.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", "#E2E8F0"));
                cardItem.setMaxWidth(Double.MAX_VALUE);

                boxListaCarrinho.getChildren().add(cardItem);
            }
        }
        recalcularTotalCupom();
    }

    private void abrirEdicaoRapidaUltimoItem() {
        if (carrinho.isEmpty()) {
            lblFeedbackCmdPDV.setText("ℹ️ Adicione ao menos um item ao cupom para editar valor ou quantidade.");
            return;
        }
        ItemCarrinho ic = carrinho.get(carrinho.size() - 1);
        javafx.stage.Stage modal = new javafx.stage.Stage();
        modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) modal.initOwner(getScene().getWindow());
        modal.setTitle("Edição Rápida (F3) — " + ic.produto.getNome());

        TextField tfQ = FormatadorSeguroUI.criarCampoTextoCartoon("Quantidade", "#CBD5E1", "#0F172A");
        tfQ.setText(ic.quantidade.stripTrailingZeros().toPlainString().replace(".", ","));
        TextField tfV = FormatadorSeguroUI.criarCampoTextoCartoon("Valor Unitário (R$)", "#CBD5E1", "#0F172A");
        tfV.setText(ic.precoUnitario.toPlainString().replace(".", ","));
        CheckBox chkAtualizarCat = new CheckBox("Atualizar preço também no catálogo permanente do produto");
        chkAtualizarCat.setSelected(ic.atualizarPrecoCatalogo);

        Button btnOk = FormatadorSeguroUI.criarBotaoCartoon("Aplicar (Enter)", "#047857", "#065F46", "#FFFFFF");
        Runnable aplicar = () -> {
            BigDecimal nq = parseDecimalBr(tfQ.getText(), ic.quantidade);
            BigDecimal nv = parseDecimalBr(tfV.getText(), ic.precoUnitario);
            if (nq.compareTo(BigDecimal.ZERO) > 0) ic.quantidade = nq;
            if (nv.compareTo(BigDecimal.ZERO) >= 0) ic.precoUnitario = nv;
            ic.atualizarPrecoCatalogo = chkAtualizarCat.isSelected();
            atualizarVisualCarrinho();
            modal.close();
        };
        btnOk.setOnAction(e -> aplicar.run());
        tfV.setOnAction(e -> aplicar.run());
        tfQ.setOnAction(e -> tfV.requestFocus());

        VBox box = new VBox(10,
                FormatadorSeguroUI.criarLabel("✏️ Ajuste Rápido: " + ic.produto.getNome(), 13, true, "#0F172A"),
                FormatadorSeguroUI.criarLabel("Quantidade:", 11, true, "#475569"), tfQ,
                FormatadorSeguroUI.criarLabel("Valor Unitário (R$):", 11, true, "#475569"), tfV,
                chkAtualizarCat,
                btnOk);
        box.setPadding(new Insets(16));
        box.setPrefWidth(360);
        box.setStyle("-fx-background-color: #FFFFFF;");
        modal.setScene(new javafx.scene.Scene(box));
        tfV.requestFocus();
        tfV.selectAll();
        modal.showAndWait();
    }

    private BigDecimal recalcularTotalCupom() {
        BigDecimal bruto = BigDecimal.ZERO;
        for (ItemCarrinho ic : carrinho) bruto = bruto.add(ic.getSubtotal());
        BigDecimal desc = parseDecimalBr(txtDesconto.getText(), BigDecimal.ZERO);
        BigDecimal liquido = bruto.subtract(desc).max(BigDecimal.ZERO);
        lblTotalVenda.setText(FormatadorSeguroUI.formatarMoeda(liquido));

        BigDecimal pagoAdicionado = BigDecimal.ZERO;
        for (PagamentoCupom pc : pagamentosCupom) pagoAdicionado = pagoAdicionado.add(pc.valor());
        BigDecimal faltante = liquido.subtract(pagoAdicionado).max(BigDecimal.ZERO);

        if (pagamentosCupom.isEmpty()) {
            txtValorPagParcial.setText(liquido.toPlainString().replace(".", ","));
            lblSaldoPagamentos.setText(String.format("Total da Nota: %s | Pronto para pagamento integral ou adicione múltiplas formas:", FormatadorSeguroUI.formatarMoeda(liquido)));
            lblSaldoPagamentos.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #334155;");
        } else {
            txtValorPagParcial.setText(faltante.toPlainString().replace(".", ","));
            if (faltante.compareTo(BigDecimal.ZERO) == 0) {
                lblSaldoPagamentos.setText(String.format("✔ 100%% Coberto: %s informado em %d forma(s) de pagamento.",
                        FormatadorSeguroUI.formatarMoeda(pagoAdicionado), pagamentosCupom.size()));
                lblSaldoPagamentos.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #047857;");
            } else if (pagoAdicionado.compareTo(liquido) > 0) {
                BigDecimal excedente = pagoAdicionado.subtract(liquido);
                lblSaldoPagamentos.setText(String.format("ℹ️ Total informado: %s | Excedente/Troco: %s",
                        FormatadorSeguroUI.formatarMoeda(pagoAdicionado), FormatadorSeguroUI.formatarMoeda(excedente)));
                lblSaldoPagamentos.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1D4ED8;");
            } else {
                lblSaldoPagamentos.setText(String.format("⚠️ Total Nota: %s | Informado: %s | Restante a cobrir: %s",
                        FormatadorSeguroUI.formatarMoeda(liquido),
                        FormatadorSeguroUI.formatarMoeda(pagoAdicionado),
                        FormatadorSeguroUI.formatarMoeda(faltante)));
                lblSaldoPagamentos.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #B45309;");
            }
        }
        return liquido;
    }

    private void adicionarPagamentoAoCupom() {
        BigDecimal total = recalcularTotalCupom();
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;

        FormaPagamento fp = obterFormaSelecionada();
        String codigo = fp != null ? fp.getCodigo() : "PIX";
        String nome = fp != null ? fp.getNome() : "PIX";
        String mod = cbModalidadePag.getValue() != null ? cbModalidadePag.getValue() : "À Vista";

        if (isClientePadraoSelecionado() && !"À Vista".equals(mod)) {
            lblSaldoPagamentos.setText("⚠️ Cliente Padrão não registrado só pode pagar À Vista (pode dividir entre PIX, Dinheiro, Débito, etc.).");
            lblSaldoPagamentos.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
            return;
        }

        int parc = "Parcelado".equals(mod) && cbParcelas.getValue() != null ? cbParcelas.getValue() : 1;
        BigDecimal pagoAtual = pagamentosCupom.stream().map(PagamentoCupom::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restante = total.subtract(pagoAtual).max(BigDecimal.ZERO);
        BigDecimal valorInformado = parseDecimalBr(txtValorPagParcial.getText(), restante);

        if (restante.compareTo(BigDecimal.ZERO) <= 0 && !pagamentosCupom.isEmpty()) {
            // O total já estava 100% coberto pelo pagamento anterior, mas o operador quer adicionar uma nova forma!
            // Exemplo: Estava 100% em PIX, agora quer adicionar Cartão de Crédito 3x ou Dinheiro
            PagamentoCupom primeiro = pagamentosCupom.get(0);
            BigDecimal valorNovo;
            if (valorInformado.compareTo(BigDecimal.ZERO) > 0 && valorInformado.compareTo(primeiro.valor()) < 0) {
                valorNovo = valorInformado;
            } else {
                valorNovo = primeiro.valor().divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_EVEN);
            }
            BigDecimal novoValorPrimeiro = primeiro.valor().subtract(valorNovo).max(BigDecimal.ZERO);
            pagamentosCupom.set(0, new PagamentoCupom(primeiro.codigoForma(), primeiro.nomeForma(), primeiro.modalidade(), primeiro.parcelas(), novoValorPrimeiro));
            pagamentosCupom.add(new PagamentoCupom(codigo, nome, mod, parc, valorNovo));
        } else {
            if (valorInformado.compareTo(BigDecimal.ZERO) <= 0) valorInformado = restante;
            if (valorInformado.compareTo(restante) > 0 && !"DINHEIRO".equalsIgnoreCase(codigo)) {
                valorInformado = restante;
            }
            if (valorInformado.compareTo(BigDecimal.ZERO) <= 0) return;
            pagamentosCupom.add(new PagamentoCupom(codigo, nome, mod, parc, valorInformado));
        }

        atualizarVisualPagamentosCupom();
        recalcularTotalCupom();
    }

    private void dividirPagamentoEmPartes(int partes) {
        BigDecimal total = recalcularTotalCupom();
        if (total.compareTo(BigDecimal.ZERO) <= 0 || partes < 2) return;

        pagamentosCupom.clear();
        BigDecimal valorCada = total.divide(BigDecimal.valueOf(partes), 2, java.math.RoundingMode.DOWN);
        BigDecimal soma = BigDecimal.ZERO;

        FormaPagamento fp = obterFormaSelecionada();
        String cod1 = fp != null ? fp.getCodigo() : "PIX";
        String nome1 = fp != null ? fp.getNome() : "PIX";
        String mod1 = cbModalidadePag.getValue() != null ? cbModalidadePag.getValue() : "À Vista";
        int parc1 = "Parcelado".equals(mod1) && cbParcelas.getValue() != null ? cbParcelas.getValue() : 1;

        for (int i = 0; i < partes; i++) {
            BigDecimal v = (i == partes - 1) ? total.subtract(soma) : valorCada;
            soma = soma.add(v);
            if (i == 0) {
                pagamentosCupom.add(new PagamentoCupom(cod1, nome1, mod1, parc1, v));
            } else if (i == 1) {
                String cod2 = isClientePadraoSelecionado() ? "DINHEIRO" : "CARTAO_CREDITO";
                String nome2 = isClientePadraoSelecionado() ? "Dinheiro em Espécie" : "Cartão de Crédito";
                String mod2 = isClientePadraoSelecionado() ? "À Vista" : "Parcelado";
                int parc2 = isClientePadraoSelecionado() ? 1 : 2;
                pagamentosCupom.add(new PagamentoCupom(cod2, nome2, mod2, parc2, v));
            } else {
                pagamentosCupom.add(new PagamentoCupom("CARTAO_DEBITO", "Cartão de Débito", "À Vista", 1, v));
            }
        }
        atualizarVisualPagamentosCupom();
        recalcularTotalCupom();
    }

    private void atualizarVisualPagamentosCupom() {
        boxPagamentosCupom.getChildren().clear();
        for (int i = 0; i < pagamentosCupom.size(); i++) {
            int idx = i;
            PagamentoCupom pc = pagamentosCupom.get(i);

            Label badge = FormatadorSeguroUI.criarBadgeCartoon(
                    String.format("%s • %s%s", pc.nomeForma(), pc.modalidade(), pc.parcelas() > 1 ? " (" + pc.parcelas() + "x)" : ""),
                    "#DBEAFE", "#93C5FD", "#1E40AF");
            badge.setMinWidth(180);

            TextField tfVal = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
            tfVal.setText(pc.valor().toPlainString().replace(".", ","));
            tfVal.setPrefWidth(95);
            tfVal.setOnAction(e -> {
                BigDecimal nv = parseDecimalBr(tfVal.getText(), pc.valor());
                pagamentosCupom.set(idx, new PagamentoCupom(pc.codigoForma(), pc.nomeForma(), pc.modalidade(), pc.parcelas(), nv));
                recalcularTotalCupom();
            });
            tfVal.focusedProperty().addListener((obs, oldF, newF) -> {
                if (!newF) {
                    BigDecimal nv = parseDecimalBr(tfVal.getText(), pc.valor());
                    pagamentosCupom.set(idx, new PagamentoCupom(pc.codigoForma(), pc.nomeForma(), pc.modalidade(), pc.parcelas(), nv));
                    recalcularTotalCupom();
                }
            });

            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setTooltip(new Tooltip("Remover este pagamento"));
            btnRem.setOnAction(e -> {
                pagamentosCupom.remove(idx);
                atualizarVisualPagamentosCupom();
                recalcularTotalCupom();
            });

            HBox row = new HBox(8, badge, FormatadorSeguroUI.criarLabel("R$", 11, false, "#64748B"), tfVal, btnRem);
            HBox.setHgrow(badge, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F0FDF4", "#BBF7D0"));
            boxPagamentosCupom.getChildren().add(row);
        }
    }

    private void concluirVenda() {
        if (carrinho.isEmpty()) {
            lblStatusCaixa.setText("Adicione ao menos 1 produto ao cupom antes de concluir.");
            lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #B45309;");
            return;
        }

        BigDecimal total = recalcularTotalCupom();
        Long idCli = extrairIdPrefixado(cbCliente.getValue());
        boolean clientePadrao = (idCli == null);

        List<PagamentoCupom> listaFinalPagamentos = new ArrayList<>(pagamentosCupom);
        if (listaFinalPagamentos.isEmpty()) {
            FormaPagamento fp = obterFormaSelecionada();
            String codigoForma = fp != null ? fp.getCodigo() : "PIX";
            String nomeForma = fp != null ? fp.getNome() : "PIX";
            String mod = clientePadrao ? "À Vista" : (cbModalidadePag.getValue() != null ? cbModalidadePag.getValue() : "À Vista");
            int parc = !clientePadrao && "Parcelado".equals(mod) && cbParcelas.getValue() != null ? cbParcelas.getValue() : 1;
            listaFinalPagamentos.add(new PagamentoCupom(codigoForma, nomeForma, mod, parc, total));
        }

        BigDecimal somaFinal = listaFinalPagamentos.stream().map(PagamentoCupom::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaFinal.compareTo(total) < 0) {
            BigDecimal falta = total.subtract(somaFinal);
            lblStatusCaixa.setText(String.format("⚠️ Total dos pagamentos (%s) menor que o cupom (%s). Faltam %s! Complete os pagamentos.",
                    FormatadorSeguroUI.formatarMoeda(somaFinal), FormatadorSeguroUI.formatarMoeda(total), FormatadorSeguroUI.formatarMoeda(falta)));
            lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
            return;
        }

        if (clientePadrao && listaFinalPagamentos.stream().anyMatch(p -> !"À Vista".equals(p.modalidade()))) {
            lblStatusCaixa.setText("⚠️ Regra do PDV: Cliente Padrão aceita exclusivamente pagamento À Vista.");
            lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
            return;
        }

        Map<String, String> resumoConfirmacao = new LinkedHashMap<>();
        resumoConfirmacao.put("Cliente", cbCliente.getValue());
        resumoConfirmacao.put("Quantidade de Itens", carrinho.size() + " produto(s)");
        resumoConfirmacao.put("Valor Total da Nota", FormatadorSeguroUI.formatarMoeda(total));
        StringBuilder sbPg = new StringBuilder();
        for (PagamentoCupom pc : listaFinalPagamentos) {
            if (!sbPg.isEmpty()) sbPg.append(" + ");
            sbPg.append(pc.nomeForma()).append(" (").append(FormatadorSeguroUI.formatarMoeda(pc.valor())).append(")");
        }
        resumoConfirmacao.put("Pagamentos Vinculados", sbPg.toString());

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Concluir Venda e Emitir Nota",
                "Revise o resumo do cupom e dos pagamentos antes de atualizar o estoque.",
                resumoConfirmacao,
                () -> executarEmissaoVenda(total, idCli, listaFinalPagamentos));
    }

    private void executarEmissaoVenda(BigDecimal total, Long idCli, List<PagamentoCupom> listaPgs) {
        // Ajustes de preço: se aumentou preço ou pediu atualização de catálogo, sincroniza preço antes; se baixou, converte diferença em desconto
        BigDecimal descontoExtraPrecos = BigDecimal.ZERO;
        List<ItemCarrinho> prodsParaAtualizarCatalogo = new ArrayList<>();
        for (ItemCarrinho ic : carrinho) {
            BigDecimal orig = ic.produto.getPrecoVenda() != null ? ic.produto.getPrecoVenda() : BigDecimal.ZERO;
            if (ic.atualizarPrecoCatalogo || ic.precoUnitario.compareTo(orig) > 0) {
                if (ic.precoUnitario.compareTo(orig) != 0) prodsParaAtualizarCatalogo.add(ic);
            } else if (ic.precoUnitario.compareTo(orig) < 0) {
                descontoExtraPrecos = descontoExtraPrecos.add(orig.subtract(ic.precoUnitario).multiply(ic.quantidade));
            }
        }

        List<Map<String, Object>> pgsParam = new ArrayList<>();
        for (PagamentoCupom pc : listaPgs) {
            Map<String, Object> pgMap = new LinkedHashMap<>();
            pgMap.put("codigoForma", pc.codigoForma());
            pgMap.put("valor", pc.valor());
            pgMap.put("parcelas", pc.parcelas());
            if ("A Prazo".equals(pc.modalidade())) {
                pgMap.put("modalidade", "PRAZO");
                pgMap.put("dataValidade", LocalDate.now().plusDays(30).toString());
            }
            pgsParam.add(pgMap);
        }

        boolean usaLoteEspecifico = carrinho.stream().anyMatch(c -> c.idLoteEspecifico != null);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pagamentos", pgsParam);
        if (idCli != null) params.put("idCliente", idCli);

        BigDecimal descManual = parseDecimalBr(txtDesconto.getText(), BigDecimal.ZERO);
        BigDecimal descTotal = descManual.add(descontoExtraPrecos);
        String cupom = txtCupom.getText() != null ? txtCupom.getText().trim() : "";

        RotaSistema rota;
        if (usaLoteEspecifico) {
            rota = RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL;
            Map<Long, BigDecimal> lotesQtd = new LinkedHashMap<>();
            for (ItemCarrinho ic : carrinho) {
                if (ic.idLoteEspecifico != null) {
                    lotesQtd.merge(ic.idLoteEspecifico, ic.quantidade, BigDecimal::add);
                } else {
                    lotesCache.stream()
                            .filter(l -> l.getProduto() != null && l.getProduto().getId().equals(ic.produto.getId()))
                            .findFirst()
                            .ifPresent(l -> lotesQtd.merge(l.getId(), ic.quantidade, BigDecimal::add));
                }
            }
            params.put("lotesEQuantidades", lotesQtd);
            params.put("descricao", "Venda Balcão por Lote");
        } else {
            List<Map<String, Object>> itensList = new ArrayList<>();
            for (ItemCarrinho ic : carrinho) {
                itensList.add(Map.of("idProduto", ic.produto.getId(), "quantidade", ic.quantidade));
            }
            params.put("itens", itensList);
            boolean balcaoPadrao = (idCli == null) && descTotal.compareTo(BigDecimal.ZERO) == 0 && cupom.isEmpty();
            if (balcaoPadrao) {
                rota = RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO;
            } else {
                rota = RotaSistema.NOTA_VENDA_EMITIR_FIFO;
                if (descTotal.compareTo(BigDecimal.ZERO) > 0) params.put("descontoGlobal", descTotal);
                if (!cupom.isEmpty()) params.put("cupom", cupom);
                params.put("descricao", "Venda Balcão PDV");
            }
        }

        lblStatusCaixa.setText("Emitindo nota fiscal e atualizando estoque imediatamente...");
        lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1D4ED8;");
        btnAjudaErroCaixa.setVisible(false);
        btnAjudaErroCaixa.setManaged(false);

        Thread.ofVirtual().start(() -> {
            for (ItemCarrinho ic : prodsParaAtualizarCatalogo) {
                ponte.getRoteador().executarEndpoint(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                        Map.of("idProduto", ic.produto.getId(), "novoPreco", ic.precoUnitario));
            }
            var resp = ponte.getRoteador().executarEndpoint(rota, params);
            Nota notaCompleta = (resp.sucesso() && resp.dados() instanceof Nota n && n.getId() != null)
                    ? ponte.buscarNotaCompletaPorId(n.getId()) : null;
            if (resp.sucesso()) ponte.notificarMudancaDados();

            Platform.runLater(() -> {
                if (resp.sucesso()) {
                    carrinho.clear();
                    pagamentosCupom.clear();
                    txtDesconto.clear();
                    txtCupom.clear();
                    atualizarVisualCarrinho();
                    atualizarVisualPagamentosCupom();
                    precisaRecarregar = true;
                    carregarSeNecessario();
                    lblStatusCaixa.setText("✔ " + FormatadorSeguroUI.tratarParaExibicao(resp.mensagem(), "Venda emitida!"));
                    lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                    if (notaCompleta != null) {
                        DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, notaCompleta, "Comprovante de Venda — PDV Balcão");
                    }
                } else {
                    String erro = FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem());
                    lblStatusCaixa.setText("⚠️ " + erro);
                    lblStatusCaixa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                    btnAjudaErroCaixa.setOnAction(e -> conectorIA.resolverErroOuBloqueioComIA("Venda no PDV Balcão", erro));
                    btnAjudaErroCaixa.setVisible(true);
                    btnAjudaErroCaixa.setManaged(true);
                }
            });
        });
    }

    private void abrirUltimaVendaParaImpressao() {
        for (Nota n : notasCache) {
            if (n instanceof NotaVenda nv && nv.getId() != null) {
                Thread.ofVirtual().start(() -> {
                    Nota completa = ponte.buscarNotaCompletaPorId(nv.getId());
                    Platform.runLater(() -> {
                        if (completa != null) {
                            DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, completa, "Reimpressão — Última Nota de Venda");
                        }
                    });
                });
                return;
            }
        }
        lblStatusCaixa.setText("Nenhuma Nota de Venda emitida recentemente.");
    }

    // ==================== PÓS-VENDA & DEVOLUÇÃO ====================

    private void popularCombosPosVenda() {
        vendasDisponiveisCombo = new ArrayList<>();
        cbNotaVendaOrigem.getItems().clear();
        for (Nota n : notasCache) {
            if (n instanceof NotaVenda nv) {
                vendasDisponiveisCombo.add(nv);
                String cli = nv.getPessoa() != null ? nv.getPessoa().getNome() : "Consumidor Final";
                cbNotaVendaOrigem.getItems().add(String.format("#%d • %s • %s (%s)",
                        nv.getId(), cli,
                        FormatadorSeguroUI.formatarDataHoraAmigavel(nv.getDataGerada()),
                        FormatadorSeguroUI.formatarMoeda(nv.getValorTotal())));
            }
        }
        if (!cbNotaVendaOrigem.getItems().isEmpty()) {
            cbNotaVendaOrigem.getSelectionModel().selectFirst();
            popularItensVendaSelecionada();
        } else {
            cbItemVendaOrigem.getItems().clear();
        }

        lotesAvariaCombo = new ArrayList<>(lotesCache);
        cbLoteAvariaBalcao.getItems().clear();
        for (Item<? extends Produto> l : lotesAvariaCombo) {
            String prod = l.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(l.getProduto().getNome()) : "Produto";
            String um = l.getProduto() != null && l.getProduto().getUm() != null ? l.getProduto().getUm().getSigla() : "UN";
            cbLoteAvariaBalcao.getItems().add(String.format("Lote #%d • %s (%s)",
                    l.getId(), prod, FormatadorSeguroUI.formatarQuantidade(l.getQuantidadeDisponivel(), um)));
        }
        if (!cbLoteAvariaBalcao.getItems().isEmpty()) cbLoteAvariaBalcao.getSelectionModel().selectFirst();
    }

    private void popularItensVendaSelecionada() {
        int idx = cbNotaVendaOrigem.getSelectionModel().getSelectedIndex();
        cbItemVendaOrigem.getItems().clear();
        itensDaVendaSelecionada.clear();
        if (idx < 0 || idx >= vendasDisponiveisCombo.size()) return;
        NotaVenda nv = vendasDisponiveisCombo.get(idx);
        if (nv.getItens() != null) {
            for (Item<? extends Produto> it : nv.getItens()) {
                itensDaVendaSelecionada.add(it);
                String prod = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
                String um = it.getProduto() != null && it.getProduto().getUm() != null ? it.getProduto().getUm().getSigla() : "UN";
                cbItemVendaOrigem.getItems().add(String.format("#%d %s (Qtd: %s)",
                        it.getId(), prod, FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), um)));
            }
        }
        if (!cbItemVendaOrigem.getItems().isEmpty()) cbItemVendaOrigem.getSelectionModel().selectFirst();
    }

    private void registrarDevolucaoPosVenda() {
        int idxVenda = cbNotaVendaOrigem.getSelectionModel().getSelectedIndex();
        int idxItem = cbItemVendaOrigem.getSelectionModel().getSelectedIndex();
        if (idxVenda < 0 || idxItem < 0) {
            lblStatusPosVenda.setText("Selecione uma Nota de Venda e um Item vendido.");
            lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #B45309;");
            return;
        }
        NotaVenda nv = vendasDisponiveisCombo.get(idxVenda);
        Item<? extends Produto> it = itensDaVendaSelecionada.get(idxItem);
        BigDecimal qtd = parseDecimalBr(txtQtdDevolvida.getText(), BigDecimal.ONE);
        String motivo = txtMotivoDevolucao.getText() != null && !txtMotivoDevolucao.getText().isBlank()
                ? txtMotivoDevolucao.getText().trim()
                : "Devolução / perda pós-venda registrada no PDV";

        String prodNome = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Confirmar Devolução Pós-Venda",
                "Deseja registrar a devolução e atualizar o estoque/crédito com fornecedor?",
                Map.of("Nota de Origem", "#" + nv.getId(),
                       "Produto", prodNome,
                       "Quantidade", FormatadorSeguroUI.formatarQuantidade(qtd, ""),
                       "Gerar Perda p/ Fornecedor", chkGerarPerdaFornecedor.isSelected() ? "SIM" : "NÃO (Reintegrar ao Lote)",
                       "Motivo", motivo),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("idNotaVenda", nv.getId());
                    params.put("idItemVenda", it.getId());
                    params.put("quantidadeDevolvida", qtd);
                    params.put("motivo", motivo);
                    params.put("geraPerdaFornecedor", chkGerarPerdaFornecedor.isSelected());

                    ponte.executarRotaAsync(RotaSistema.NOTA_DEVOLUCAO_REGISTRAR, params, resp -> {
                        if (resp.sucesso()) {
                            lblStatusPosVenda.setText("✔ Devolução pós-venda registrada com sucesso!");
                            lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                            txtMotivoDevolucao.clear();
                            precisaRecarregar = true;
                            carregarSeNecessario();
                            if (resp.dados() instanceof Nota n && n.getId() != null) {
                                Thread.ofVirtual().start(() -> {
                                    Nota completa = ponte.buscarNotaCompletaPorId(n.getId());
                                    List<Nota> cadeiaPosVenda = new ArrayList<>();
                                    if (completa != null) {
                                        cadeiaPosVenda.add(completa);
                                        if (completa instanceof NotaDevolucao dev
                                                && dev.getNotaPerdaGerada() != null
                                                && dev.getNotaPerdaGerada().getId() != null) {
                                            Nota perdaEnc = ponte.buscarNotaCompletaPorId(dev.getNotaPerdaGerada().getId());
                                            if (perdaEnc != null) cadeiaPosVenda.add(perdaEnc);
                                        }
                                    }
                                    Platform.runLater(() -> {
                                        if (!cadeiaPosVenda.isEmpty()) {
                                            String tituloComp = cadeiaPosVenda.size() > 1
                                                    ? "Comprovante Encadeado: Devolução ➔ Perda Pós-Venda"
                                                    : "Comprovante de Devolução / Pós-Venda";
                                            DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                                                    this, cadeiaPosVenda, tituloComp);
                                        }
                                    });
                                });
                            }
                        } else {
                            lblStatusPosVenda.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                            lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                        }
                    });
                });
    }

    private void registrarAvariaDiretaBalcao() {
        int idx = cbLoteAvariaBalcao.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= lotesAvariaCombo.size()) {
            lblStatusPosVenda.setText("Selecione um lote disponível para baixa rápida de avaria.");
            lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #B45309;");
            return;
        }
        Item<? extends Produto> lote = lotesAvariaCombo.get(idx);
        BigDecimal qtd = parseDecimalBr(txtQtdAvariaBalcao.getText(), BigDecimal.ONE);
        String motivo = txtMotivoAvariaBalcao.getText() != null && !txtMotivoAvariaBalcao.getText().isBlank()
                ? txtMotivoAvariaBalcao.getText().trim()
                : "Avaria rápida no balcão";

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Confirmar Baixa de Avaria no Balcão",
                "A quantidade informada será baixada imediatamente do lote selecionado.",
                Map.of("Lote", "#" + lote.getId(),
                       "Produto", lote.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(lote.getProduto().getNome()) : "Produto",
                       "Quantidade Perdida", FormatadorSeguroUI.formatarQuantidade(qtd, ""),
                       "Motivo", motivo),
                () -> ponte.executarRotaAsync(RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                        Map.of("idLote", lote.getId(), "quantidade", qtd, "motivo", motivo),
                        resp -> {
                            if (resp.sucesso()) {
                                lblStatusPosVenda.setText("✔ Perda direta no lote #" + lote.getId() + " registrada.");
                                lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                                txtMotivoAvariaBalcao.clear();
                                precisaRecarregar = true;
                                carregarSeNecessario();
                                if (resp.dados() instanceof Nota n && n.getId() != null) {
                                    Thread.ofVirtual().start(() -> {
                                        Nota completa = ponte.buscarNotaCompletaPorId(n.getId());
                                        Platform.runLater(() -> {
                                            if (completa != null) {
                                                DialogoCorporativoModal.exibirComprovanteNotaImprimivel(
                                                        this, completa, "Comprovante de Perda — Balcão");
                                            }
                                        });
                                    });
                                }
                            } else {
                                lblStatusPosVenda.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                                lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                            }
                        }));
    }

    private void renderizarHistoricoPosVenda() {
        boxHistoricoPosVenda.getChildren().clear();
        int exibidos = 0;
        for (Nota n : notasCache) {
            if (n instanceof NotaDevolucao nd && exibidos < 15) {
                exibidos++;
                String prod = !nd.getItens().isEmpty() && nd.getItens().get(0).getProduto() != null
                        ? nd.getItens().get(0).getProduto().getNome() : "Mercadoria";
                boolean temPerda = nd.getNotaPerdaGerada() != null;
                Label badge = FormatadorSeguroUI.criarBadgeCartoon(
                        temPerda ? "PÓS-VENDA + PERDA" : "DEVOLUÇÃO",
                        temPerda ? "#FEF2F2" : "#EFF6FF",
                        temPerda ? "#FCA5A5" : "#93C5FD",
                        temPerda ? "#991B1B" : "#1E40AF");
                Label lTit = FormatadorSeguroUI.criarLabel(
                        String.format("Devolução #%d • %s • %s", nd.getId(), FormatadorSeguroUI.limparNomeProduto(prod),
                                FormatadorSeguroUI.formatarDataHoraAmigavel(nd.getDataGerada())),
                        12, true, "#0F172A");
                Label lSub = FormatadorSeguroUI.criarLabel(
                        String.format("Estorno: %s • Motivo: %s",
                                FormatadorSeguroUI.formatarMoeda(nd.calcularValorEstornoCliente()),
                                FormatadorSeguroUI.tratarParaExibicao(nd.getMotivo(), "Devolução")),
                        11, false, "#475569");
                VBox info = new VBox(3, new FlowPane(6, 4, badge, lTit), lSub);
                HBox.setHgrow(info, Priority.ALWAYS);

                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Analisar IA", () ->
                        conectorIA.analisarPerdaERessarcimento(
                                this,
                                "PDV — Devolução Pós-Venda",
                                nd.getId(),
                                FormatadorSeguroUI.formatarDataHoraAmigavel(nd.getDataGerada()),
                                prod,
                                nd.getPessoa() != null ? nd.getPessoa().getNome() : "Fornecedor do Lote",
                                nd.getMotivo(),
                                nd.calcularValorEstornoCliente(),
                                nd.isDevolucaoDentroValidade(),
                                temPerda && nd.getNotaPerdaGerada().getId() != null
                                        ? "Nota de Perda encadeada #" + nd.getNotaPerdaGerada().getId() : "Sem perda encadeada"));

                HBox row = new HBox(10, info, btnIA);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 10, 8, 10));
                row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0") + "-fx-cursor: hand;");
                row.setOnMouseClicked(e -> {
                    if (!(e.getTarget() instanceof Button) && nd.getId() != null) {
                        Thread.ofVirtual().start(() -> {
                            Nota comp = ponte.buscarNotaCompletaPorId(nd.getId());
                            List<Nota> cadeia = new ArrayList<>();
                            if (comp != null) {
                                cadeia.add(comp);
                                if (comp instanceof NotaDevolucao d && d.getNotaPerdaGerada() != null && d.getNotaPerdaGerada().getId() != null) {
                                    Nota pe = ponte.buscarNotaCompletaPorId(d.getNotaPerdaGerada().getId());
                                    if (pe != null) cadeia.add(pe);
                                }
                            }
                            Platform.runLater(() -> {
                                if (!cadeia.isEmpty()) {
                                    DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                                            this, cadeia,
                                            cadeia.size() > 1 ? "Pós-Venda: Devolução #" + nd.getId() + " ➔ Perda" : "Ficha da Nota de Devolução #" + nd.getId());
                                }
                            });
                        });
                    }
                });
                boxHistoricoPosVenda.getChildren().add(row);
            } else if (n instanceof NotaPerda np && exibidos < 15) {
                exibidos++;
                String prod = !np.getItens().isEmpty() && np.getItens().get(0).getProduto() != null
                        ? np.getItens().get(0).getProduto().getNome() : "Item";
                String forn = np.getPessoa() != null ? np.getPessoa().getNome() : "Fornecedor padrão";
                boolean dentroVal = np.isPerdaDentroDaValidade();
                String dataFmt = FormatadorSeguroUI.formatarDataHoraAmigavel(np.getDataGerada());
                Label badge = FormatadorSeguroUI.criarBadgeCartoon(
                        FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "PERDA"),
                        "#FEF2F2", "#FCA5A5", "#991B1B");
                Label lTit = FormatadorSeguroUI.criarLabel(
                        String.format("Perda #%d • %s • %s", np.getId(), FormatadorSeguroUI.limparNomeProduto(prod),
                                FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda())),
                        12, true, "#0F172A");
                Label lSub = FormatadorSeguroUI.criarLabel(
                        String.format("%s • Fornecedor: %s • %s",
                                FormatadorSeguroUI.formatarDataHoraAmigavel(np.getDataGerada()),
                                forn, FormatadorSeguroUI.tratarParaExibicao(np.getDescricao(), "Sem detalhe")),
                        11, false, "#475569");
                VBox info = new VBox(3, new FlowPane(6, 4, badge, lTit), lSub);
                HBox.setHgrow(info, Priority.ALWAYS);

                Button btnEditarPerda = FormatadorSeguroUI.criarBotaoCartoon("✏️ Editar", "#FFFBEB", "#F59E0B", "#92400E");
                btnEditarPerda.setOnAction(e -> abrirModalEditarNotaPerda(np));

                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Ressarcimento IA", () ->
                        conectorIA.analisarPerdaERessarcimento(
                                this,
                                "PDV — Nota de Perda Pós-Venda",
                                np.getId(),
                                dataFmt,
                                prod,
                                forn,
                                np.getDescricao(),
                                np.getValorCustoPerda(),
                                dentroVal,
                                np.getNotaDevolucao() != null ? "Originada da Devolução #" + np.getNotaDevolucao().getId() : "Perda registrada no PDV"));
                HBox row = new HBox(8, info, btnEditarPerda, btnIA);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 10, 8, 10));
                row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0") + "-fx-cursor: hand;");
                row.setOnMouseClicked(e -> {
                    if (!(e.getTarget() instanceof Button) && np.getId() != null) {
                        Thread.ofVirtual().start(() -> {
                            Nota comp = ponte.buscarNotaCompletaPorId(np.getId());
                            Platform.runLater(() -> {
                                if (comp != null) {
                                    DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, comp, "Ficha da Nota de Perda #" + np.getId());
                                }
                            });
                        });
                    }
                });
                boxHistoricoPosVenda.getChildren().add(row);
            }
        }
        if (exibidos == 0) {
            boxHistoricoPosVenda.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhuma devolução ou perda registrada até o momento.", 12, false, "#64748B"));
        }
    }

    private void abrirModalEditarNotaPerda(NotaPerda np) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("✏️ Editar Nota de Perda #" + np.getId());
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());

        Label tit = FormatadorSeguroUI.criarLabel("✏️ Editar Nota de Perda #" + np.getId(), 14, true, "#0F172A");
        ComboBox<String> cbTipo = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbTipo.getItems().addAll("PERDA_INTERNA", "AVARIA_FORNECEDOR", "VENCIMENTO", "POS_VENDA", "PERDA_CLIMATICA");
        cbTipo.setEditable(true);
        cbTipo.setValue(FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "AVARIA_FORNECEDOR"));

        DatePicker dpData = new DatePicker(np.getDataGerada() != null ? np.getDataGerada().toLocalDate() : LocalDate.now());
        BigDecimal qtdOrig = !np.getItens().isEmpty() && np.getItens().get(0).getQuantidade() != null
                ? np.getItens().get(0).getQuantidade() : BigDecimal.ONE;
        TextField txtQtd = FormatadorSeguroUI.criarCampoTextoCartoon(qtdOrig.toPlainString().replace(".", ","), "#CBD5E1", "#0F172A");
        txtQtd.setText(qtdOrig.toPlainString().replace(".", ","));

        TextArea txtMotivo = FormatadorSeguroUI.criarAreaTextoCartoon("Descreva o motivo real desta perda...", "#CBD5E1", "#0F172A");
        txtMotivo.setPrefRowCount(3);
        txtMotivo.setText(np.getDescricao() != null ? np.getDescricao() : "");

        Label lblSt = FormatadorSeguroUI.criarLabel("", 11, true, "#DC2626");
        Button btnSalvar = FormatadorSeguroUI.criarBotaoCartoon("💾 Salvar Alterações", "#047857", "#065F46", "#FFFFFF");
        btnSalvar.setOnAction(e -> {
            BigDecimal novaQtd = parseDecimalBr(txtQtd.getText(), qtdOrig);
            btnSalvar.setDisable(true);
            ponte.editarNotaPerdaAsync(
                    np.getId(),
                    txtMotivo.getText(),
                    cbTipo.getValue(),
                    dpData.getValue(),
                    novaQtd,
                    atualizada -> {
                        dlg.close();
                        lblStatusPosVenda.setText("✔ Nota de Perda #" + np.getId() + " atualizada com sucesso.");
                        lblStatusPosVenda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                        precisaRecarregar = true;
                        carregarSeNecessario();
                    },
                    erro -> {
                        btnSalvar.setDisable(false);
                        lblSt.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(erro));
                    });
        });

        Button btnCancelar = FormatadorSeguroUI.criarBotaoCartoon("Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnCancelar.setOnAction(e -> dlg.close());

        HBox rod = new HBox(8, btnCancelar, btnSalvar);
        rod.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(8,
                tit,
                FormatadorSeguroUI.criarLabel("Tipo da Perda:", 11, true, "#475569"), cbTipo,
                FormatadorSeguroUI.criarLabel("Data da Ocorrência:", 11, true, "#475569"), dpData,
                FormatadorSeguroUI.criarLabel("Quantidade Baixada:", 11, true, "#475569"), txtQtd,
                FormatadorSeguroUI.criarLabel("Motivo / Descrição Real:", 11, true, "#475569"), txtMotivo,
                lblSt, rod);
        box.setPadding(new Insets(16));
        box.setPrefWidth(480);
        box.setStyle("-fx-background-color: #FFFFFF;");
        dlg.setScene(new Scene(box));
        dlg.showAndWait();
    }

    private Long extrairIdPrefixado(String valorCombo) {
        if (valorCombo == null || !valorCombo.startsWith("#")) return null;
        try {
            String[] partes = valorCombo.substring(1).split("\\s*-\\s*", 2);
            return Long.parseLong(partes[0].trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimalBr(String txt, BigDecimal fallback) {
        if (txt == null || txt.isBlank()) return fallback;
        try {
            return new BigDecimal(txt.trim().replace("R$", "").replace(" ", "").replace(",", "."));
        } catch (Exception e) {
            return fallback;
        }
    }
}
