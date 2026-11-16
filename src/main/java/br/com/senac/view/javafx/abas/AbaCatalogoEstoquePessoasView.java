package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoDigital;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import br.com.senac.repository.modal.produto.Um;
import br.com.senac.view.javafx.componentes.CarrosselImagensProduto;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ComponenteKitInput;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ConsolidadoEstoque;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.RastreamentoCadeiaNota;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.ScoreFornecedorProduto;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aba 3: Estoque & Produtos (Hub Corporativo de Inventário e Catálogo).
 * Centraliza todas as rotinas de estoque sem exposição de jargões técnicos internos,
 * com carregamento de fotos sob demanda (Carrossel), Cards de inspeção detalhada ao clicar,
 * confirmação manual para gravações e construtor completo de Kits com múltiplos itens.
 */
public class AbaCatalogoEstoquePessoasView extends VBox {

    private record ItemKitRascunho(Produto produto, BigDecimal quantidade) {
        BigDecimal subtotalIndividual() {
            BigDecimal pv = produto.getPrecoVenda() != null ? produto.getPrecoVenda() : BigDecimal.ZERO;
            return pv.multiply(quantidade);
        }
    }

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabInsights = new Tab("💡 Insights de Estoque");
    private final Tab tabLotes = new Tab("📦 Lotes Disponíveis");
    private final Tab tabPerdaEstoque = new Tab("⚠️ Perda em Estoque");
    private final Tab tabProdutos = new Tab("🏷️ Produtos (Completo + Fotos)");
    private final Tab tabKits = new Tab("🎁 Kits & Conjuntos");
    private final Tab tabUnidadesENotas = new Tab("📏 Unidades & Notas de Estoque");

    // KPI Cards
    private final Label lblKpiValorTotal = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#0F172A");
    private final Label lblKpiLotesAtivos = FormatadorSeguroUI.criarValorKpi("0 lotes", "#047857");
    private final Label lblKpiVencendo = FormatadorSeguroUI.criarValorKpi("0 alertas", "#B45309");
    private final Label lblKpiVencidos = FormatadorSeguroUI.criarValorKpi("0 vencidos", "#DC2626");

    // Caches
    private List<Produto> produtosCache = new ArrayList<>();
    private List<Item<? extends Produto>> lotesCache = new ArrayList<>();
    private List<Um> umsCache = new ArrayList<>();
    private List<Nota> notasCache = new ArrayList<>();
    private ConsolidadoEstoque consolidadoCache;
    private List<ScoreFornecedorProduto> scoresFornecedorCache = new ArrayList<>();

    // 3.1 Insights
    private final VBox boxInsightsConteudo = new VBox(8);

    // 3.2 Lotes Disponíveis
    private final TextField txtBuscaLote = FormatadorSeguroUI.criarCampoTextoCartoon("Filtrar lote ou produto...", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFiltroValidadeLote = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TableView<Item<? extends Produto>> tabelaLotes = new TableView<>();

    // 3.3 Perda em Estoque
    private final ComboBox<String> cbLotePerda = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtQtdPerda = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final TextField txtMotivoPerda = FormatadorSeguroUI.criarCampoTextoCartoon("Ex.: Vencimento, umidade ou avaria de estoque", "#CBD5E1", "#0F172A");
    private final DatePicker dpDataPerda = new DatePicker();
    private final Label lblStatusPerda = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private final VBox boxHistoricoPerdas = new VBox(8);

    // 3.4 Produtos (Completo + Carrossel Sob Demanda)
    private final TextField txtBuscaProduto = FormatadorSeguroUI.criarCampoTextoCartoon("Buscar produto no catálogo...", "#CBD5E1", "#0F172A");
    private final TableView<Produto> tabelaProdutos = new TableView<>();
    private final ToggleButton tgNovoFisico = new ToggleButton("➕ Físico");
    private final ToggleButton tgNovoDigital = new ToggleButton("💻 Digital");
    private final ToggleButton tgEditarSel = new ToggleButton("✏️ Editar & Fotos");
    private final VBox boxFormularioProduto = new VBox(8);
    private final Label lblStatusProduto = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");

    private Produto produtoSelecionado;
    private final TextField txtProdNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome completo do produto", "#CBD5E1", "#0F172A");
    private final TextField txtProdDesc = FormatadorSeguroUI.criarCampoTextoCartoon("Descrição detalhada / Especificação", "#CBD5E1", "#0F172A");
    private final TextField txtProdPreco = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbProdUm = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtAlturaCm = FormatadorSeguroUI.criarCampoTextoCartoon("0", "#CBD5E1", "#0F172A");
    private final TextField txtLarguraCm = FormatadorSeguroUI.criarCampoTextoCartoon("0", "#CBD5E1", "#0F172A");
    private final TextField txtComprimentoCm = FormatadorSeguroUI.criarCampoTextoCartoon("0", "#CBD5E1", "#0F172A");
    private final TextField txtPesoBrutoKg = FormatadorSeguroUI.criarCampoTextoCartoon("0", "#CBD5E1", "#0F172A");
    private final TextField txtPesoLiquidoKg = FormatadorSeguroUI.criarCampoTextoCartoon("0", "#CBD5E1", "#0F172A");
    private final TextField txtLinkTitulo = FormatadorSeguroUI.criarCampoTextoCartoon("Ex.: Manual PDF / Licença", "#CBD5E1", "#0F172A");
    private final TextField txtLinkUrl = FormatadorSeguroUI.criarCampoTextoCartoon("https://...", "#CBD5E1", "#0F172A");
    private final CarrosselImagensProduto carrosselProdutoForm;

    // 3.5 Kits & Conjuntos Multi-Itens
    private final ToggleButton tgKitNovo = new ToggleButton("✨ Criar Novo Kit Multi-Itens");
    private final ToggleButton tgKitExistente = new ToggleButton("✏️ Completar Kit Existente");
    private final ComboBox<String> cbKitPai = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtKitNovoNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome comercial do novo kit (ex.: Kit Churrasco Fim de Semana)", "#CBD5E1", "#0F172A");
    private final TextField txtKitNovoDesc = FormatadorSeguroUI.criarCampoTextoCartoon("Descrição promocional do kit", "#CBD5E1", "#0F172A");
    private final TextField txtKitNovoPreco = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbKitNovoUm = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbKitComponente = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtKitQtd = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final List<ItemKitRascunho> itensKitRascunho = new ArrayList<>();
    private final VBox boxItensKitRascunho = new VBox(5);
    private final Label lblSomaComponentesKit = FormatadorSeguroUI.criarLabel("Componentes selecionados: 0", 12, true, "#1D4ED8");
    private final Label lblStatusKit = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private final VBox boxListaKits = new VBox(8);

    // 3.6 Unidades de Medida & Rastreio Notas Estoque
    private final TableView<Um> tabelaUms = new TableView<>();
    private final TextField txtUmSigla = FormatadorSeguroUI.criarCampoTextoCartoon("Sigla (ex.: KG, CX, UN)", "#CBD5E1", "#0F172A");
    private final TextField txtUmNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome (ex.: Quilograma)", "#CBD5E1", "#0F172A");
    private final TextField txtUmDesc = FormatadorSeguroUI.criarCampoTextoCartoon("Descrição", "#CBD5E1", "#0F172A");
    private final TextField txtUmBase = FormatadorSeguroUI.criarCampoTextoCartoon("Fator base (ex.: 1)", "#CBD5E1", "#0F172A");
    private final Label lblStatusUm = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");

    private final ComboBox<String> cbFiltroTipoNotaEst = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtFiltroProdNotaEst = FormatadorSeguroUI.criarCampoTextoCartoon("Filtrar por produto ou #nota...", "#CBD5E1", "#0F172A");
    private final TableView<Nota> tabelaNotasEstoque = new TableView<>();

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaCatalogoEstoquePessoasView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;
        this.carrosselProdutoForm = new CarrosselImagensProduto(ponte, true, 220, 140);

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();
        GridPane kpis = criarGridKpis();

        tabInsights.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaInsights()));
        tabLotes.setContent(construirSubAbaLotes());
        tabPerdaEstoque.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaPerdaEstoque()));
        tabProdutos.setContent(construirSubAbaProdutos());
        tabKits.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaKits()));
        tabUnidadesENotas.setContent(construirSubAbaUnidadesENotas());

        tabPane.getTabs().addAll(tabInsights, tabLotes, tabPerdaEstoque, tabProdutos, tabKits, tabUnidadesENotas);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(topo, kpis, tabPane);

        ponte.registrarOuvinteDados(() -> {
            precisaRecarregar = true;
            if (abaAtiva) {
                carregarSeNecessario();
            }
        });
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("📦", "Estoque, Lotes, Perdas & Catálogo de Produtos", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Controle de lotes por validade, perdas diretas em estoque, produtos com carrossel de fotos, kits multi-itens e notas.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Atualizar", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        HBox topo = new HBox(10, boxTit, btnAtualizar);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    private GridPane criarGridKpis() {
        GridPane g = new GridPane();
        g.setHgap(10);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25);
            c.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(c);
        }
        g.add(criarMiniKpi("Valor Custo Total em Estoque", lblKpiValorTotal, "#FFFFFF", "#CBD5E1"), 0, 0);
        g.add(criarMiniKpi("Lotes Disponíveis em Estoque", lblKpiLotesAtivos, "#ECFDF5", "#A7F3D0"), 1, 0);
        g.add(criarMiniKpi("Lotes Vencendo (≤ 15 dias)", lblKpiVencendo, "#FFFBEB", "#FCD34D"), 2, 0);
        g.add(criarMiniKpi("Lotes Vencidos em Prateleira", lblKpiVencidos, "#FEF2F2", "#FCA5A5"), 3, 0);
        return g;
    }

    private VBox criarMiniKpi(String rotulo, Label valor, String bg, String borda) {
        VBox box = new VBox(4, FormatadorSeguroUI.criarLabel(rotulo, 11, true, "#475569"), valor);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon(bg, borda));
        return box;
    }

    // ==================== 3.1 INSIGHTS DE ESTOQUE ====================

    private VBox construirSubAbaInsights() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10, 2, 10, 2));
        Label tit = FormatadorSeguroUI.criarLabel(
                "Radar de Giro, Risco de Validade e Diagnóstico Inteligente (Clique no card para abrir Ficha + Fotos)",
                13, true, "#0F172A");
        boxInsightsConteudo.setFillWidth(true);
        box.getChildren().addAll(tit, boxInsightsConteudo);
        return box;
    }

    // ==================== 3.2 LOTES DISPONÍVEIS ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaLotes() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 0, 4, 0));

        cbFiltroValidadeLote.getItems().setAll("Todos com saldo", "Vencendo ≤ 15 dias", "Vencidos", "Saudáveis (> 15d)");
        cbFiltroValidadeLote.getSelectionModel().selectFirst();
        cbFiltroValidadeLote.setPrefWidth(190);

        txtBuscaLote.textProperty().addListener((obs, o, n) -> filtrarTabelaLotes());
        cbFiltroValidadeLote.setOnAction(e -> filtrarTabelaLotes());
        HBox.setHgrow(txtBuscaLote, Priority.ALWAYS);

        HBox barra = new HBox(8, txtBuscaLote, cbFiltroValidadeLote,
                FormatadorSeguroUI.criarLabel("💡 Clique em qualquer lote para abrir a ficha completa e fotos", 11, false, "#64748B"));
        barra.setAlignment(Pos.CENTER_LEFT);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaLotes, 420);
        TableColumn<Item<? extends Produto>, String> cId = new TableColumn<>("Lote");
        cId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        cId.setPrefWidth(65);

        TableColumn<Item<? extends Produto>, String> cProd = new TableColumn<>("Produto");
        cProd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(c.getValue().getProduto().getNome()) : ""));
        cProd.setPrefWidth(210);

        TableColumn<Item<? extends Produto>, String> cSaldo = new TableColumn<>("Saldo");
        cSaldo.setCellValueFactory(c -> {
            Produto p = c.getValue().getProduto();
            String um = p != null && p.getUm() != null ? p.getUm().getSigla() : "UN";
            return new SimpleStringProperty(FormatadorSeguroUI.formatarQuantidade(c.getValue().getQuantidadeDisponivel(), um));
        });
        cSaldo.setPrefWidth(95);

        TableColumn<Item<? extends Produto>, String> cCusto = new TableColumn<>("Custo Unit.");
        cCusto.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().getValor())));
        cCusto.setPrefWidth(95);

        TableColumn<Item<? extends Produto>, String> cVal = new TableColumn<>("Validade");
        cVal.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarDataAmigavel(c.getValue().getDataValidade())));
        cVal.setPrefWidth(120);

        TableColumn<Item<? extends Produto>, String> cSit = new TableColumn<>("Situação de Validade");
        cSit.setCellValueFactory(c -> new SimpleStringProperty(classificarSituacaoLote(c.getValue().getDataValidade())));
        cSit.setPrefWidth(135);

        TableColumn<Item<? extends Produto>, Void> cIA = new TableColumn<>("Ação IA");
        cIA.setPrefWidth(105);
        cIA.setCellFactory(col -> new TableCell<>() {
            private final Button b = FormatadorSeguroUI.criarBotaoIA("💬 Analisar", null);
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Item<? extends Produto> lote = getTableView().getItems().get(getIndex());
                    b.setOnAction(e -> {
                        if (lote != null && lote.getProduto() != null) {
                            conectorIA.sugerirPromocaoOuReposicao(
                                    lote.getProduto().getNome(), lote.getQuantidadeDisponivel(), lote.getDataValidade());
                        }
                    });
                    setGraphic(b);
                }
            }
        });

        tabelaLotes.getColumns().setAll(cId, cProd, cSaldo, cCusto, cVal, cSit, cIA);
        tabelaLotes.setRowFactory(tv -> {
            TableRow<Item<? extends Produto>> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && !(e.getTarget() instanceof Button)) {
                    abrirCardDetalhesLote(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(tabelaLotes, Priority.ALWAYS);
        box.getChildren().addAll(barra, tabelaLotes);
        return box;
    }

    private void abrirCardDetalhesLote(Item<? extends Produto> lote) {
        if (lote == null) return;
        Long idLote = lote.getId();
        Long idNotaCompra = lote.getNota() != null ? lote.getNota().getId() : null;
        Produto prod = lote.getProduto();
        String nomeProd = prod != null ? FormatadorSeguroUI.limparNomeProduto(prod.getNome()) : "Mercadoria";
        String um = prod != null && prod.getUm() != null ? prod.getUm().getSigla() : "UN";
        BigDecimal custoUnit = lote.getValor() != null ? lote.getValor() : BigDecimal.ZERO;
        BigDecimal saldo = lote.getQuantidadeDisponivel() != null ? lote.getQuantidadeDisponivel() : BigDecimal.ZERO;
        BigDecimal qtdOrig = lote.getQuantidade() != null ? lote.getQuantidade() : saldo;
        BigDecimal valorEstoqueLote = custoUnit.multiply(saldo);

        Thread.ofVirtual().start(() -> {
            RastreamentoCadeiaNota cadeia = ponte.consultarRastreamentoCompletoLoteENota(idLote, idNotaCompra);
            Platform.runLater(() -> {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("Lote", "#" + idLote);
                meta.put("Produto", nomeProd);
                meta.put("Saldo Disponível", FormatadorSeguroUI.formatarQuantidade(saldo, um));
                meta.put("Quantidade Original Entrada", FormatadorSeguroUI.formatarQuantidade(qtdOrig, um));
                meta.put("Custo Unitário de Entrada", FormatadorSeguroUI.formatarMoeda(custoUnit));
                meta.put("Capital Imobilizado no Lote", FormatadorSeguroUI.formatarMoeda(valorEstoqueLote));
                meta.put("Preço Atual de Venda", prod != null ? FormatadorSeguroUI.formatarMoeda(prod.getPrecoVenda()) : "—");
                meta.put("Data de Entrada", FormatadorSeguroUI.formatarDataHoraAmigavel(lote.getData()));
                meta.put("Data de Validade", FormatadorSeguroUI.formatarDataAmigavel(lote.getDataValidade()));
                meta.put("Situação de Validade", classificarSituacaoLote(lote.getDataValidade()));

                NotaCompra nc = cadeia != null ? cadeia.notaEntradaOrigem() : null;
                if (nc != null) {
                    meta.put("Nota de Entrada Original", "Nota Compra #" + nc.getId() + " (" + FormatadorSeguroUI.formatarDataHoraAmigavel(nc.getDataGerada()) + ")");
                    meta.put("Fornecedor da Entrada", nc.getPessoa() != null ? nc.getPessoa().getNome() : "Não vinculado");
                    meta.put("Valor Total da Nota de Entrada", FormatadorSeguroUI.formatarMoeda(nc.getValorTotal()));
                }

                VBox conteudoRico = new VBox(10);
                CarrosselImagensProduto carrossel = new CarrosselImagensProduto(ponte, false, 280, 165);
                carrossel.carregarProdutoSobDemanda(prod);
                conteudoRico.getChildren().add(carrossel);

                VBox boxItensEntrada = new VBox(5);
                boxItensEntrada.setPadding(new Insets(10));
                boxItensEntrada.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
                boxItensEntrada.getChildren().add(FormatadorSeguroUI.criarLabel(
                        "📥 Todos os Itens que Vieram na Mesma Nota de Entrada" + (nc != null ? " (#" + nc.getId() + ")" : ""),
                        12, true, "#0F172A"));
                if (cadeia != null && !cadeia.itensDaEntrada().isEmpty()) {
                    for (Item<? extends Produto> itEnt : cadeia.itensDaEntrada()) {
                        String np = itEnt.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(itEnt.getProduto().getNome()) : "Mercadoria";
                        String umIt = itEnt.getProduto() != null && itEnt.getProduto().getUm() != null ? itEnt.getProduto().getUm().getSigla() : "UN";
                        boolean este = idLote != null && idLote.equals(itEnt.getId());
                        Label lIt = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                                "%sLote #%d • %s • Entrada: %s | Saldo: %s • Unit.: %s • Val.: %s",
                                este ? "★ [ATUAL] " : "• ",
                                itEnt.getId(), np,
                                FormatadorSeguroUI.formatarQuantidade(itEnt.getQuantidade(), umIt),
                                FormatadorSeguroUI.formatarQuantidade(itEnt.getQuantidadeDisponivel(), umIt),
                                FormatadorSeguroUI.formatarMoeda(itEnt.getValor()),
                                FormatadorSeguroUI.formatarDataAmigavel(itEnt.getDataValidade())),
                                11, este, este ? "#1D4ED8" : "#334155");
                        lIt.setWrapText(true);
                        boxItensEntrada.getChildren().add(lIt);
                    }
                } else {
                    boxItensEntrada.getChildren().add(FormatadorSeguroUI.criarLabel("Sem outros itens mapeados para esta entrada.", 11, false, "#64748B"));
                }
                conteudoRico.getChildren().add(boxItensEntrada);

                VBox boxCadeiaNotas = construirPainelCadeiaNotasRelacionadas(cadeia);
                conteudoRico.getChildren().add(boxCadeiaNotas);

                List<Nota> dossieNotas = montarListaDossieCadeia(cadeia);
                Runnable acaoImprimirDossie = dossieNotas.isEmpty() ? null : () ->
                        DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                                this, dossieNotas, "Dossiê Completo do Lote #" + idLote);

                DialogoCorporativoModal.exibirCardDetalhes(
                        this, "📦", "Rastreamento Completo do Lote #" + idLote + " — " + nomeProd,
                        classificarSituacaoLote(lote.getDataValidade()), "#0F172A",
                        meta, conteudoRico, acaoImprimirDossie, null, null,
                        () -> conectorIA.explicarComContextoOrigem(
                                this,
                                "Estoque — Inspeção de Lote",
                                "Lote #" + idLote + " (" + nomeProd + ")",
                                meta,
                                "Analise este lote específico (#" + idLote + " de " + nomeProd
                                        + "), sua Nota de Entrada original, os demais itens recebidos e todas as vendas/perdas vinculadas."));
            });
        });
    }

    // ==================== 3.3 PERDA EM ESTOQUE ====================

    private VBox construirSubAbaPerdaEstoque() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        VBox cardForm = new VBox(10);
        cardForm.setPadding(new Insets(14));
        cardForm.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label tit = FormatadorSeguroUI.criarTituloSecao("⚠️", "Registrar Perda em Estoque (Baixa Direta de Lote)", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Indicado para quebras internas, vencimento em prateleira, deterioração por calor/umidade ou extravio.", "#64748B");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        g.getColumnConstraints().addAll(c1, c2);

        dpDataPerda.setMaxWidth(Double.MAX_VALUE);
        g.add(FormatadorSeguroUI.criarLabel("Lote de Estoque Atingido:", 12, true, "#334155"), 0, 0);
        g.add(FormatadorSeguroUI.criarLabel("Quantidade Perdida:", 12, true, "#334155"), 1, 0);
        g.add(cbLotePerda, 0, 1);
        g.add(txtQtdPerda, 1, 1);

        g.add(FormatadorSeguroUI.criarLabel("Motivo / Causa da Perda:", 12, true, "#334155"), 0, 2);
        g.add(FormatadorSeguroUI.criarLabel("Data da Ocorrência (opcional):", 12, true, "#334155"), 1, 2);
        g.add(txtMotivoPerda, 0, 3);
        g.add(dpDataPerda, 1, 3);

        FlowPane atalhosMotivo = new FlowPane(6, 6);
        for (String motivoRapido : List.of("Vencimento em prateleira", "Umidade / Calor excessivo", "Embalagem avariada", "Extravio / Quebra")) {
            Button b = FormatadorSeguroUI.criarBotaoCartoon(motivoRapido, "#F1F5F9", "#CBD5E1", "#334155");
            b.setOnAction(e -> txtMotivoPerda.setText(motivoRapido));
            atalhosMotivo.getChildren().add(b);
        }

        Button btnBaixar = FormatadorSeguroUI.criarBotaoCartoon("Registrar Perda em Estoque", "#DC2626", "#991B1B", "#FFFFFF");
        btnBaixar.setOnAction(e -> registrarPerdaEmEstoque());

        cardForm.getChildren().addAll(tit, sub, g, atalhosMotivo, btnBaixar, lblStatusPerda);

        VBox cardHist = new VBox(8);
        cardHist.setPadding(new Insets(12, 14, 12, 14));
        cardHist.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        cardHist.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📋 Notas de Perda Registradas (Clique em qualquer card para detalhes completos e impressão)", 13, true, "#0F172A"),
                boxHistoricoPerdas);

        raiz.getChildren().addAll(cardForm, cardHist);
        return raiz;
    }

    // ==================== 3.4 PRODUTOS (COMPLETO + CARROSSEL SOB DEMANDA) ====================

    @SuppressWarnings("unchecked")
    private GridPane construirSubAbaProdutos() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setPadding(new Insets(10, 0, 4, 0));

        ColumnConstraints colEsq = new ColumnConstraints();
        colEsq.setPercentWidth(48);
        colEsq.setHgrow(Priority.ALWAYS);
        ColumnConstraints colDir = new ColumnConstraints();
        colDir.setPercentWidth(52);
        colDir.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(colEsq, colDir);

        VBox esq = new VBox(8);
        esq.setPadding(new Insets(12));
        esq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        txtBuscaProduto.textProperty().addListener((obs, o, n) -> filtrarTabelaProdutos());
        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaProdutos, 400);

        TableColumn<Produto, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        colId.setPrefWidth(52);

        TableColumn<Produto, String> colNome = new TableColumn<>("Produto");
        colNome.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.limparNomeProduto(c.getValue().getNome())));
        colNome.setPrefWidth(175);

        TableColumn<Produto, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(c -> {
            Produto p = c.getValue();
            String t = p.isDigital() ? "Digital" : (p.isConjunto() ? "Kit" : "Físico");
            return new SimpleStringProperty(t);
        });
        colTipo.setPrefWidth(65);

        TableColumn<Produto, String> colSaldo = new TableColumn<>("Saldo");
        colSaldo.setCellValueFactory(c -> {
            Produto p = c.getValue();
            BigDecimal s = consolidadoCache != null ? consolidadoCache.saldosPorProduto().getOrDefault(p.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;
            String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
            return new SimpleStringProperty(FormatadorSeguroUI.formatarQuantidade(s, um));
        });
        colSaldo.setPrefWidth(80);

        TableColumn<Produto, String> colPreco = new TableColumn<>("Preço");
        colPreco.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().getPrecoVenda())));
        colPreco.setPrefWidth(80);

        tabelaProdutos.getColumns().setAll(colId, colNome, colTipo, colSaldo, colPreco);
        tabelaProdutos.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                this.produtoSelecionado = sel;
                tgEditarSel.setSelected(true);
                reconstruirFormularioProduto();
                preencherCamposEdicaoProduto(sel);
            }
        });
        tabelaProdutos.setRowFactory(tv -> {
            TableRow<Produto> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 2) {
                    abrirCardDetalhesProduto(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(tabelaProdutos, Priority.ALWAYS);
        esq.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("Catálogo Completo de Produtos (Duplo clique abre Ficha)", 13, true, "#0F172A"),
                txtBuscaProduto,
                tabelaProdutos);

        VBox dir = new VBox(8);
        dir.setPadding(new Insets(12));
        dir.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        ToggleGroup grpModo = new ToggleGroup();
        tgNovoFisico.setToggleGroup(grpModo);
        tgNovoDigital.setToggleGroup(grpModo);
        tgEditarSel.setToggleGroup(grpModo);
        tgNovoFisico.setSelected(true);

        Runnable alternar = this::reconstruirFormularioProduto;
        tgNovoFisico.setOnAction(e -> {
            limparCamposProduto();
            alternar.run();
        });
        tgNovoDigital.setOnAction(e -> {
            limparCamposProduto();
            alternar.run();
        });
        tgEditarSel.setOnAction(e -> alternar.run());

        Button btnFichaProd = FormatadorSeguroUI.criarBotaoCartoon("🔍 Abrir Ficha Completa", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnFichaProd.setOnAction(e -> {
            if (produtoSelecionado != null) abrirCardDetalhesProduto(produtoSelecionado);
        });

        HBox barraModos = new HBox(6, tgNovoFisico, tgNovoDigital, tgEditarSel, btnFichaProd);
        barraModos.setAlignment(Pos.CENTER_LEFT);
        boxFormularioProduto.setFillWidth(true);
        var scrollForm = FormatadorSeguroUI.envolverScrollSuave(boxFormularioProduto);
        VBox.setVgrow(scrollForm, Priority.ALWAYS);

        dir.getChildren().addAll(barraModos, scrollForm, lblStatusProduto);

        reconstruirFormularioProduto();

        g.add(esq, 0, 0);
        g.add(dir, 1, 0);
        GridPane.setVgrow(esq, Priority.ALWAYS);
        GridPane.setVgrow(dir, Priority.ALWAYS);
        return g;
    }

    private void abrirCardDetalhesProduto(Produto p) {
        if (p == null) return;
        BigDecimal saldo = consolidadoCache != null ? consolidadoCache.saldosPorProduto().getOrDefault(p.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;
        LocalDate val = consolidadoCache != null ? consolidadoCache.menorValidadePorProduto().get(p.getId()) : null;
        String um = p.getUm() != null ? p.getUm().getSigla() : "UN";

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Código do Produto", "#" + p.getId());
        meta.put("Nome Comercial", FormatadorSeguroUI.limparNomeProduto(p.getNome()));
        meta.put("Categoria", p.isDigital() ? "Produto Digital" : (p.isConjunto() ? "Kit / Conjunto" : "Produto Físico"));
        meta.put("Saldo Total em Estoque", FormatadorSeguroUI.formatarQuantidade(saldo, um));
        meta.put("Preço de Venda", FormatadorSeguroUI.formatarMoeda(p.getPrecoVenda()));
        meta.put("Próximo Vencimento", val != null ? FormatadorSeguroUI.formatarDataAmigavel(val) : "Sem validade");
        meta.put("Descrição", FormatadorSeguroUI.tratarParaExibicao(p.getDescricao(), "Sem descrição"));
        if (p instanceof ProdutoFisico pf) {
            meta.put("Dimensões (A×L×C)", String.format("%s × %s × %s cm",
                    pf.getAlturaCm().stripTrailingZeros().toPlainString(),
                    pf.getLarguraCm().stripTrailingZeros().toPlainString(),
                    pf.getComprimentoCm().stripTrailingZeros().toPlainString()));
            meta.put("Pesos (Bruto / Líq.)", String.format("%s kg / %s kg",
                    pf.getPesoBrutoKg().stripTrailingZeros().toPlainString(),
                    pf.getPesoLiquidoKg().stripTrailingZeros().toPlainString()));
        }

        CarrosselImagensProduto carrossel = new CarrosselImagensProduto(ponte, true, 300, 175);
        carrossel.carregarProdutoSobDemanda(p);

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🏷️", FormatadorSeguroUI.limparNomeProduto(p.getNome()),
                "Produto #" + p.getId(), "#0F172A",
                meta, carrossel, null,
                "✏️ Editar Este Produto", () -> selecionarProdutoParaEdicao(p),
                () -> conectorIA.sugerirPromocaoOuReposicao(p.getNome(), saldo, val));
    }

    private void selecionarProdutoParaEdicao(Produto p) {
        if (p == null) return;
        tabPane.getSelectionModel().select(tabProdutos);
        this.produtoSelecionado = p;
        tabelaProdutos.getSelectionModel().select(p);
        tgEditarSel.setSelected(true);
        reconstruirFormularioProduto();
        preencherCamposEdicaoProduto(p);
    }

    private void reconstruirFormularioProduto() {
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgNovoFisico, tgNovoFisico.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgNovoDigital, tgNovoDigital.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
        FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgEditarSel, tgEditarSel.isSelected(),
                "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");

        boxFormularioProduto.getChildren().clear();

        if (tgEditarSel.isSelected() && produtoSelecionado == null) {
            boxFormularioProduto.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Clique em um produto na tabela ao lado para editar dados, preço, dimensões ou visualizar o Carrossel de Fotos.", 12, false, "#64748B"));
            return;
        }

        // Exibe Carrossel de Fotos em TODOS os modos (Novo Físico, Novo Digital e Edição)
        boxFormularioProduto.getChildren().add(carrosselProdutoForm);

        GridPane formBase = new GridPane();
        formBase.setHgap(8);
        formBase.setVgap(6);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        formBase.getColumnConstraints().addAll(c1, c2);

        formBase.add(FormatadorSeguroUI.criarLabel("Nome do Produto:", 11, true, "#475569"), 0, 0);
        formBase.add(FormatadorSeguroUI.criarLabel("Preço de Venda (R$):", 11, true, "#475569"), 1, 0);
        formBase.add(txtProdNome, 0, 1);
        formBase.add(txtProdPreco, 1, 1);

        formBase.add(FormatadorSeguroUI.criarLabel("Descrição / Detalhes:", 11, true, "#475569"), 0, 2);
        formBase.add(FormatadorSeguroUI.criarLabel("Unidade de Medida:", 11, true, "#475569"), 1, 2);
        formBase.add(txtProdDesc, 0, 3);
        formBase.add(cbProdUm, 1, 3);
        boxFormularioProduto.getChildren().add(formBase);

        boolean ehFisico = tgNovoFisico.isSelected() || (tgEditarSel.isSelected() && produtoSelecionado != null && !produtoSelecionado.isDigital());
        boolean ehDigital = tgNovoDigital.isSelected() || (tgEditarSel.isSelected() && produtoSelecionado != null && produtoSelecionado.isDigital());

        if (ehFisico) {
            Label lDim = FormatadorSeguroUI.criarLabel("Dimensões & Pesos (Produto Físico):", 12, true, "#0F172A");
            GridPane gDim = new GridPane();
            gDim.setHgap(6);
            gDim.setVgap(6);
            for (int i = 0; i < 3; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(33.3);
                gDim.getColumnConstraints().add(cc);
            }
            gDim.add(FormatadorSeguroUI.criarLabel("Altura (cm):", 11, false, "#475569"), 0, 0);
            gDim.add(FormatadorSeguroUI.criarLabel("Largura (cm):", 11, false, "#475569"), 1, 0);
            gDim.add(FormatadorSeguroUI.criarLabel("Comprim. (cm):", 11, false, "#475569"), 2, 0);
            gDim.add(txtAlturaCm, 0, 1);
            gDim.add(txtLarguraCm, 1, 1);
            gDim.add(txtComprimentoCm, 2, 1);

            gDim.add(FormatadorSeguroUI.criarLabel("Peso Bruto (kg):", 11, false, "#475569"), 0, 2);
            gDim.add(FormatadorSeguroUI.criarLabel("Peso Líq. (kg):", 11, false, "#475569"), 1, 2);
            gDim.add(txtPesoBrutoKg, 0, 3);
            gDim.add(txtPesoLiquidoKg, 1, 3);
            boxFormularioProduto.getChildren().addAll(lDim, gDim);
        }

        if (ehDigital && !tgEditarSel.isSelected()) {
            Label lDig = FormatadorSeguroUI.criarLabel("Link de Entrega Digital:", 12, true, "#0F172A");
            GridPane gDig = new GridPane();
            gDig.setHgap(8);
            gDig.setVgap(6);
            ColumnConstraints d1 = new ColumnConstraints();
            d1.setPercentWidth(40);
            ColumnConstraints d2 = new ColumnConstraints();
            d2.setPercentWidth(60);
            gDig.getColumnConstraints().addAll(d1, d2);
            gDig.add(FormatadorSeguroUI.criarLabel("Título do Link:", 11, false, "#475569"), 0, 0);
            gDig.add(FormatadorSeguroUI.criarLabel("URL do Conteúdo:", 11, false, "#475569"), 1, 0);
            gDig.add(txtLinkTitulo, 0, 1);
            gDig.add(txtLinkUrl, 1, 1);
            boxFormularioProduto.getChildren().addAll(lDig, gDig);
        }

        if (!tgEditarSel.isSelected()) {
            Button btnSalvarNovo = FormatadorSeguroUI.criarBotaoCartoon(
                    tgNovoFisico.isSelected() ? "Cadastrar Produto Físico" : "Cadastrar Produto Digital",
                    "#047857", "#065F46", "#FFFFFF");
            btnSalvarNovo.setOnAction(e -> cadastrarNovoProduto());
            boxFormularioProduto.getChildren().add(btnSalvarNovo);
        } else {
            FlowPane acoesEdicao = new FlowPane(6, 6);
            Button btnAtualizarDados = FormatadorSeguroUI.criarBotaoCartoon("Salvar Nome/Descrição", "#0F172A", "#0F172A", "#FFFFFF");
            btnAtualizarDados.setOnAction(e -> atualizarNomeDescricaoProduto());

            Button btnAtualizarPreco = FormatadorSeguroUI.criarBotaoCartoon("Atualizar Preço", "#1D4ED8", "#1E40AF", "#FFFFFF");
            btnAtualizarPreco.setOnAction(e -> atualizarPrecoProduto());

            acoesEdicao.getChildren().addAll(btnAtualizarDados, btnAtualizarPreco);

            if (ehFisico) {
                Button btnAtualizarDim = FormatadorSeguroUI.criarBotaoCartoon("Atualizar Dimensões", "#0369A1", "#075985", "#FFFFFF");
                btnAtualizarDim.setOnAction(e -> atualizarDimensoesProduto());
                acoesEdicao.getChildren().add(btnAtualizarDim);
            }

            Button btnExcluir = FormatadorSeguroUI.criarBotaoCartoon("Excluir Produto", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnExcluir.setOnAction(e -> excluirProdutoSelecionado());
            acoesEdicao.getChildren().add(btnExcluir);

            boxFormularioProduto.getChildren().add(acoesEdicao);
        }
    }

    private void preencherCamposEdicaoProduto(Produto p) {
        txtProdNome.setText(p.getNome() != null ? p.getNome() : "");
        txtProdDesc.setText(p.getDescricao() != null ? p.getDescricao() : "");
        txtProdPreco.setText(p.getPrecoVenda() != null ? p.getPrecoVenda().toPlainString().replace(".", ",") : "0,00");
        if (p.getUm() != null) {
            cbProdUm.setValue(p.getUm().getSigla());
        }
        if (p instanceof ProdutoFisico pf) {
            txtAlturaCm.setText(pf.getAlturaCm().stripTrailingZeros().toPlainString().replace(".", ","));
            txtLarguraCm.setText(pf.getLarguraCm().stripTrailingZeros().toPlainString().replace(".", ","));
            txtComprimentoCm.setText(pf.getComprimentoCm().stripTrailingZeros().toPlainString().replace(".", ","));
            txtPesoBrutoKg.setText(pf.getPesoBrutoKg().stripTrailingZeros().toPlainString().replace(".", ","));
            txtPesoLiquidoKg.setText(pf.getPesoLiquidoKg().stripTrailingZeros().toPlainString().replace(".", ","));
        } else if (p instanceof ProdutoDigital pd && pd.getLinks() != null && !pd.getLinks().isEmpty()) {
            txtLinkTitulo.setText(pd.getLinks().get(0).getTitulo());
            txtLinkUrl.setText(pd.getLinks().get(0).getUrl());
        }
        carrosselProdutoForm.carregarProdutoSobDemanda(p);
    }

    // ==================== 3.5 KITS & CONJUNTOS MULTI-ITENS ====================

    private VBox construirSubAbaKits() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        VBox cardAdd = new VBox(10);
        cardAdd.setPadding(new Insets(14));
        cardAdd.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label tit = FormatadorSeguroUI.criarTituloSecao("🎁", "Montador de Kits & Combos Multi-Itens", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Adicione quantos componentes desejar à composição antes de salvar o Kit completo.", "#64748B");

        ToggleGroup grpKit = new ToggleGroup();
        tgKitNovo.setToggleGroup(grpKit);
        tgKitExistente.setToggleGroup(grpKit);
        tgKitNovo.setSelected(true);

        VBox boxNovoKitDados = new VBox(6);
        GridPane gpNovo = new GridPane();
        gpNovo.setHgap(8);
        gpNovo.setVgap(6);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25);
            gpNovo.getColumnConstraints().add(c);
        }
        gpNovo.add(FormatadorSeguroUI.criarLabel("Nome do Novo Kit:", 11, true, "#475569"), 0, 0);
        gpNovo.add(FormatadorSeguroUI.criarLabel("Preço de Venda do Kit (R$):", 11, true, "#475569"), 1, 0);
        gpNovo.add(FormatadorSeguroUI.criarLabel("Unidade:", 11, true, "#475569"), 2, 0);
        gpNovo.add(FormatadorSeguroUI.criarLabel("Descrição:", 11, true, "#475569"), 3, 0);
        gpNovo.add(txtKitNovoNome, 0, 1);
        gpNovo.add(txtKitNovoPreco, 1, 1);
        gpNovo.add(cbKitNovoUm, 2, 1);
        gpNovo.add(txtKitNovoDesc, 3, 1);
        boxNovoKitDados.getChildren().add(gpNovo);

        VBox boxKitExistente = new VBox(4,
                FormatadorSeguroUI.criarLabel("Selecione o Produto/Kit Pai existente:", 11, true, "#475569"),
                cbKitPai);
        boxKitExistente.setVisible(false);
        boxKitExistente.setManaged(false);

        Runnable alternarModoKit = () -> {
            boolean novo = tgKitNovo.isSelected();
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgKitNovo, novo,
                    "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgKitExistente, !novo,
                    "#F8FAFC", "#CBD5E1", "#334155", "#0F172A", "#0F172A", "#FFFFFF");
            boxNovoKitDados.setVisible(novo);
            boxNovoKitDados.setManaged(novo);
            boxKitExistente.setVisible(!novo);
            boxKitExistente.setManaged(!novo);
        };
        tgKitNovo.setOnAction(e -> alternarModoKit.run());
        tgKitExistente.setOnAction(e -> alternarModoKit.run());
        alternarModoKit.run();

        HBox barAddComp = new HBox(8, cbKitComponente, txtKitQtd);
        HBox.setHgrow(cbKitComponente, Priority.ALWAYS);
        txtKitQtd.setPrefWidth(80);
        Button btnIncluirItem = FormatadorSeguroUI.criarBotaoCartoon("+ Incluir Item na Lista do Kit", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnIncluirItem.setOnAction(e -> incluirItemRascunhoKit());
        barAddComp.getChildren().add(btnIncluirItem);
        barAddComp.setAlignment(Pos.CENTER_LEFT);

        boxItensKitRascunho.setPadding(new Insets(8));
        boxItensKitRascunho.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
        renderizarRascunhoKit();

        Button btnLimparListaKit = FormatadorSeguroUI.criarBotaoCartoon("Limpar Lista", "#F1F5F9", "#CBD5E1", "#334155");
        btnLimparListaKit.setOnAction(e -> {
            itensKitRascunho.clear();
            renderizarRascunhoKit();
        });
        Button btnSalvarKitCompleto = FormatadorSeguroUI.criarBotaoCartoon("✔ Salvar Kit com Todos os Itens", "#047857", "#065F46", "#FFFFFF");
        btnSalvarKitCompleto.setOnAction(e -> salvarKitComTodosItens());

        HBox rodapeKit = new HBox(10, lblSomaComponentesKit, btnLimparListaKit, btnSalvarKitCompleto);
        HBox.setHgrow(lblSomaComponentesKit, Priority.ALWAYS);
        rodapeKit.setAlignment(Pos.CENTER_LEFT);

        cardAdd.getChildren().addAll(
                tit, sub,
                new HBox(8, tgKitNovo, tgKitExistente),
                boxNovoKitDados,
                boxKitExistente,
                FormatadorSeguroUI.criarLabel("Adicionar Componentes (inclua múltiplos itens antes de salvar):", 12, true, "#0F172A"),
                barAddComp,
                boxItensKitRascunho,
                rodapeKit,
                lblStatusKit);

        VBox cardLista = new VBox(8);
        cardLista.setPadding(new Insets(12, 14, 12, 14));
        cardLista.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        cardLista.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("Kits e Combos Cadastrados (Clique no card para abrir ficha e fotos)", 13, true, "#0F172A"),
                boxListaKits);

        raiz.getChildren().addAll(cardAdd, cardLista);
        return raiz;
    }

    private void incluirItemRascunhoKit() {
        Long idComp = extrairId(cbKitComponente.getValue());
        if (idComp == null) return;
        Produto p = produtosCache.stream().filter(pr -> pr.getId().equals(idComp)).findFirst().orElse(null);
        if (p == null) return;
        BigDecimal qtd = parseDecimalBr(txtKitQtd.getText(), BigDecimal.ONE);
        if (qtd.compareTo(BigDecimal.ZERO) <= 0) qtd = BigDecimal.ONE;

        for (int i = 0; i < itensKitRascunho.size(); i++) {
            if (itensKitRascunho.get(i).produto().getId().equals(idComp)) {
                BigDecimal novaQtd = itensKitRascunho.get(i).quantidade().add(qtd);
                itensKitRascunho.set(i, new ItemKitRascunho(p, novaQtd));
                renderizarRascunhoKit();
                return;
            }
        }
        itensKitRascunho.add(new ItemKitRascunho(p, qtd));
        renderizarRascunhoKit();
    }

    private void renderizarRascunhoKit() {
        boxItensKitRascunho.getChildren().clear();
        if (itensKitRascunho.isEmpty()) {
            boxItensKitRascunho.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum componente adicionado ainda. Selecione um produto acima e clique em '+ Incluir Item na Lista do Kit'.",
                    11, false, "#64748B"));
            lblSomaComponentesKit.setText("Componentes selecionados: 0");
            return;
        }
        BigDecimal somaTotal = BigDecimal.ZERO;
        for (int i = 0; i < itensKitRascunho.size(); i++) {
            int idx = i;
            ItemKitRascunho ik = itensKitRascunho.get(i);
            somaTotal = somaTotal.add(ik.subtotalIndividual());
            String um = ik.produto().getUm() != null ? ik.produto().getUm().getSigla() : "UN";
            Label l = FormatadorSeguroUI.criarLabel(
                    String.format("%s × %s  (%s)",
                            FormatadorSeguroUI.formatarQuantidade(ik.quantidade(), um),
                            FormatadorSeguroUI.limparNomeProduto(ik.produto().getNome()),
                            FormatadorSeguroUI.formatarMoeda(ik.subtotalIndividual())),
                    12, true, "#0F172A");
            HBox.setHgrow(l, Priority.ALWAYS);
            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setOnAction(e -> {
                itensKitRascunho.remove(idx);
                renderizarRascunhoKit();
            });
            HBox row = new HBox(8, l, btnRem);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", "#CBD5E1"));
            boxItensKitRascunho.getChildren().add(row);
        }
        lblSomaComponentesKit.setText("Itens na composição: " + itensKitRascunho.size()
                + " • Soma individual: " + FormatadorSeguroUI.formatarMoeda(somaTotal));
        if (tgKitNovo.isSelected() && (txtKitNovoPreco.getText() == null || txtKitNovoPreco.getText().isBlank() || "0,00".equals(txtKitNovoPreco.getText()))) {
            txtKitNovoPreco.setText(somaTotal.toPlainString().replace(".", ","));
        }
    }

    private void salvarKitComTodosItens() {
        if (itensKitRascunho.isEmpty()) {
            lblStatusKit.setText("⚠️ Inclua ao menos 1 componente na lista antes de salvar o Kit.");
            return;
        }
        boolean criarNovo = tgKitNovo.isSelected();
        Long idKitExist = criarNovo ? null : extrairId(cbKitPai.getValue());
        String nomeKit = criarNovo
                ? (txtKitNovoNome.getText() != null ? txtKitNovoNome.getText().trim() : "")
                : (cbKitPai.getValue() != null ? cbKitPai.getValue() : "Kit");
        if (criarNovo && nomeKit.isEmpty()) {
            lblStatusKit.setText("⚠️ Informe o nome do novo Kit comercial.");
            return;
        }
        if (!criarNovo && idKitExist == null) {
            lblStatusKit.setText("⚠️ Selecione um Kit existente.");
            return;
        }
        BigDecimal precoKit = parseDecimalBr(txtKitNovoPreco.getText(), BigDecimal.ZERO);
        StringBuilder compResumo = new StringBuilder();
        List<ComponenteKitInput> inputs = new ArrayList<>();
        for (ItemKitRascunho ik : itensKitRascunho) {
            if (!compResumo.isEmpty()) compResumo.append(", ");
            compResumo.append(FormatadorSeguroUI.formatarQuantidade(ik.quantidade(), ""))
                    .append("x ").append(FormatadorSeguroUI.limparNomeProduto(ik.produto().getNome()));
            inputs.add(new ComponenteKitInput(ik.produto().getId(), ik.quantidade()));
        }

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Operação", criarNovo ? "Criar Novo Produto Kit + Vincular Itens" : "Adicionar Itens ao Kit Existente");
        meta.put("Kit", nomeKit);
        if (criarNovo) meta.put("Preço do Kit", FormatadorSeguroUI.formatarMoeda(precoKit));
        meta.put("Componentes (" + inputs.size() + ")", compResumo.toString());

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Salvar Kit Multi-Itens",
                "Confirma o salvamento do Kit com toda a lista de componentes selecionada?",
                meta,
                () -> ponte.salvarKitMultiItensAsync(
                        idKitExist,
                        nomeKit,
                        txtKitNovoDesc.getText() != null ? txtKitNovoDesc.getText().trim() : "Kit Comercial",
                        precoKit,
                        cbKitNovoUm.getValue() != null ? cbKitNovoUm.getValue() : "UN",
                        inputs,
                        resp -> {
                            if (resp.sucesso()) {
                                lblStatusKit.setText("✔ " + resp.mensagem());
                                lblStatusKit.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                                itensKitRascunho.clear();
                                txtKitNovoNome.clear();
                                txtKitNovoDesc.clear();
                                renderizarRascunhoKit();
                            } else {
                                lblStatusKit.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                                lblStatusKit.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                            }
                        }));
    }

    // ==================== 3.6 UNIDADES DE MEDIDA & NOTAS DE ESTOQUE ====================

    @SuppressWarnings("unchecked")
    private GridPane construirSubAbaUnidadesENotas() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setPadding(new Insets(10, 0, 4, 0));

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(42);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(58);
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        VBox esq = new VBox(8);
        esq.setPadding(new Insets(12));
        esq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaUms, 200);
        TableColumn<Um, String> uSigla = new TableColumn<>("Sigla");
        uSigla.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSigla()));
        uSigla.setPrefWidth(65);
        TableColumn<Um, String> uNome = new TableColumn<>("Nome");
        uNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        uNome.setPrefWidth(130);
        TableColumn<Um, String> uBase = new TableColumn<>("Base");
        uBase.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBase() != null ? c.getValue().getBase().stripTrailingZeros().toPlainString() : "1"));
        uBase.setPrefWidth(60);
        tabelaUms.getColumns().setAll(uSigla, uNome, uBase);
        tabelaUms.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                txtUmSigla.setText(sel.getSigla());
                txtUmNome.setText(sel.getNome());
                txtUmDesc.setText(sel.getDescricao() != null ? sel.getDescricao() : "");
                txtUmBase.setText(sel.getBase() != null ? sel.getBase().stripTrailingZeros().toPlainString() : "1");
            }
        });

        GridPane formUm = new GridPane();
        formUm.setHgap(6);
        formUm.setVgap(6);
        ColumnConstraints f1 = new ColumnConstraints();
        f1.setPercentWidth(50);
        ColumnConstraints f2 = new ColumnConstraints();
        f2.setPercentWidth(50);
        formUm.getColumnConstraints().addAll(f1, f2);
        formUm.add(txtUmSigla, 0, 0);
        formUm.add(txtUmNome, 1, 0);
        formUm.add(txtUmDesc, 0, 1);
        formUm.add(txtUmBase, 1, 1);

        Button btnUmAdd = FormatadorSeguroUI.criarBotaoCartoon("Cadastrar", "#047857", "#065F46", "#FFFFFF");
        btnUmAdd.setOnAction(e -> executarUm(RotaSistema.PRODUTO_UNIDADE_CADASTRAR));
        Button btnUmAt = FormatadorSeguroUI.criarBotaoCartoon("Atualizar", "#0F172A", "#0F172A", "#FFFFFF");
        btnUmAt.setOnAction(e -> executarUm(RotaSistema.PRODUTO_UNIDADE_ATUALIZAR));
        Button btnUmExc = FormatadorSeguroUI.criarBotaoCartoon("Excluir", "#FEE2E2", "#FCA5A5", "#991B1B");
        btnUmExc.setOnAction(e -> executarUm(RotaSistema.PRODUTO_UNIDADE_EXCLUIR));

        FlowPane acUm = new FlowPane(6, 6, btnUmAdd, btnUmAt, btnUmExc);
        esq.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📏 Unidades de Medida (CRUD)", 13, true, "#0F172A"),
                tabelaUms, formUm, acUm, lblStatusUm);

        VBox dir = new VBox(8);
        dir.setPadding(new Insets(12));
        dir.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        cbFiltroTipoNotaEst.getItems().setAll("Todas de Estoque", "COMPRA (Entrada)", "PERDA (Baixa)", "DEVOLUCAO", "VENDA (Saída)");
        cbFiltroTipoNotaEst.getSelectionModel().selectFirst();
        cbFiltroTipoNotaEst.setPrefWidth(165);
        cbFiltroTipoNotaEst.setOnAction(e -> filtrarTabelaNotasEstoque());
        txtFiltroProdNotaEst.textProperty().addListener((obs, o, n) -> filtrarTabelaNotasEstoque());
        HBox.setHgrow(txtFiltroProdNotaEst, Priority.ALWAYS);

        HBox filtroEst = new HBox(8, txtFiltroProdNotaEst, cbFiltroTipoNotaEst);
        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaNotasEstoque, 380);

        TableColumn<Nota, String> nId = new TableColumn<>("Nota");
        nId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        nId.setPrefWidth(55);

        TableColumn<Nota, String> nTipo = new TableColumn<>("Movimentação");
        nTipo.setCellValueFactory(c -> new SimpleStringProperty(classificarTipoNotaEstoque(c.getValue())));
        nTipo.setPrefWidth(115);

        TableColumn<Nota, String> nData = new TableColumn<>("Data");
        nData.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarDataHoraAmigavel(c.getValue().getDataGerada())));
        nData.setPrefWidth(110);

        TableColumn<Nota, String> nItens = new TableColumn<>("Itens & Rastreio");
        nItens.setCellValueFactory(c -> new SimpleStringProperty(resumirItensNota(c.getValue())));
        nItens.setPrefWidth(220);

        TableColumn<Nota, String> nVal = new TableColumn<>("Total");
        nVal.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().getValorTotal())));
        nVal.setPrefWidth(85);

        tabelaNotasEstoque.getColumns().setAll(nId, nTipo, nData, nItens, nVal);
        tabelaNotasEstoque.setRowFactory(tv -> {
            TableRow<Nota> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && row.getItem().getId() != null) {
                    Long idN = row.getItem().getId();
                    Thread.ofVirtual().start(() -> {
                        RastreamentoCadeiaNota cadeia = ponte.consultarRastreamentoCompletoLoteENota(null, idN);
                        Platform.runLater(() -> abrirModalRastreamentoCompletoNota(idN, cadeia));
                    });
                }
            });
            return row;
        });

        VBox.setVgrow(tabelaNotasEstoque, Priority.ALWAYS);
        dir.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("🧾 Rastreamento Completo de Notas de Estoque (Clique para Ver Cadeia & Imprimir)", 13, true, "#0F172A"),
                filtroEst, tabelaNotasEstoque);

        g.add(esq, 0, 0);
        g.add(dir, 1, 0);
        GridPane.setVgrow(esq, Priority.ALWAYS);
        GridPane.setVgrow(dir, Priority.ALWAYS);
        return g;
    }

    // ==================== CICLO DE CARGA & AÇÕES ====================

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
                List<Produto> prods = ponte.listarProdutos();
                List<Item<? extends Produto>> lotes = ponte.listarLotesComSaldo();
                ConsolidadoEstoque cons = ponte.consultarSaldosConsolidadosPorProduto();
                List<Um> ums = ponte.listarUnidadesMedida();
                List<Nota> notas = ponte.listarNotas();
                List<ScoreFornecedorProduto> scores = ponte.calcularScoreFornecedoresPorProduto(null);

                Platform.runLater(() -> {
                    this.produtosCache = prods;
                    this.lotesCache = lotes;
                    this.consolidadoCache = cons;
                    this.umsCache = ums;
                    this.notasCache = notas;
                    this.scoresFornecedorCache = scores;

                    atualizarKpis();
                    renderizarInsightsEstoque();
                    filtrarTabelaLotes();
                    popularCombosPerdaKitsUms();
                    renderizarHistoricoPerdas();
                    filtrarTabelaProdutos();
                    renderizarListaKits();
                    tabelaUms.setItems(FXCollections.observableArrayList(umsCache));
                    filtrarTabelaNotasEstoque();
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

    private void atualizarKpis() {
        if (consolidadoCache == null) return;
        lblKpiValorTotal.setText(FormatadorSeguroUI.formatarMoeda(consolidadoCache.valorCustoTotalEstoque()));
        lblKpiLotesAtivos.setText(consolidadoCache.lotesAtivos() + " lotes");
        lblKpiVencendo.setText(consolidadoCache.lotesVencendo15Dias() + " em alerta");
        lblKpiVencidos.setText(consolidadoCache.lotesVencidos() + " vencidos");
    }

    private void renderizarInsightsEstoque() {
        boxInsightsConteudo.getChildren().clear();

        // Bloco de Score Comparativo de Fornecedores para o Mesmo Produto
        if (scoresFornecedorCache != null && !scoresFornecedorCache.isEmpty()) {
            VBox boxRankingForn = new VBox(6);
            boxRankingForn.setPadding(new Insets(12));
            boxRankingForn.setStyle(FormatadorSeguroUI.estiloCardCartoon("#F8FAFC", "#93C5FD"));
            boxRankingForn.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "🏆 Score Comparativo de Fornecedores por Mesmo Produto (0–100 pts)", 13, true, "#1E40AF"));

            int limForn = 0;
            for (ScoreFornecedorProduto sc : scoresFornecedorCache) {
                if (limForn >= 10) break;
                limForn++;
                Label bScore = FormatadorSeguroUI.criarBadgeCartoon(
                        sc.scoreGeral0a100() + " pts" + (sc.melhorParaProduto() ? " • 🏆 MELHOR" : ""),
                        sc.melhorParaProduto() ? "#ECFDF5" : "#EFF6FF",
                        sc.melhorParaProduto() ? "#6EE7B7" : "#BFDBFE",
                        sc.melhorParaProduto() ? "#047857" : "#1E40AF");
                Label lInfo = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                        "%s ➔ %s • Custo Médio: %s (Econ.: %s) • Entradas: %d • Avaria/Val.: %.1f%%",
                        FormatadorSeguroUI.limparNomeProduto(sc.nomeProduto()),
                        sc.nomeFornecedor(),
                        FormatadorSeguroUI.formatarMoeda(sc.custoMedioUnitario()),
                        FormatadorSeguroUI.formatarMoeda(sc.economiaVsMaiorCusto()),
                        sc.totalEntradasLotes(),
                        sc.taxaAvariaPercentual()), 11, sc.melhorParaProduto(), "#0F172A");
                lInfo.setWrapText(true);
                HBox.setHgrow(lInfo, Priority.ALWAYS);

                Button btnIaForn = FormatadorSeguroUI.criarBotaoIA("💬 Comparar IA", () -> {
                    Map<String, String> metaSc = new LinkedHashMap<>();
                    metaSc.put("Produto", sc.nomeProduto());
                    metaSc.put("Fornecedor", sc.nomeFornecedor());
                    metaSc.put("Score Geral", sc.scoreGeral0a100() + " / 100");
                    metaSc.put("Custo Médio Unitário", FormatadorSeguroUI.formatarMoeda(sc.custoMedioUnitario()));
                    metaSc.put("Economia vs Maior Custo", FormatadorSeguroUI.formatarMoeda(sc.economiaVsMaiorCusto()));
                    metaSc.put("Taxa de Avaria na Validade", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f%%", sc.taxaAvariaPercentual()));
                    metaSc.put("Diagnóstico", sc.motivoScore());
                    conectorIA.explicarComContextoOrigem(this, "Estoque — Score de Fornecedores",
                            sc.nomeProduto() + " • " + sc.nomeFornecedor(), metaSc,
                            "Explique por que este fornecedor recebeu " + sc.scoreGeral0a100()
                                    + " pontos no produto '" + sc.nomeProduto() + "' e se devemos concentrar ou renegociar compras com ele.");
                });

                HBox linha = new HBox(8, bScore, lInfo, btnIaForn);
                linha.setAlignment(Pos.CENTER_LEFT);
                linha.setPadding(new Insets(6, 8, 6, 8));
                linha.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", "#E2E8F0"));
                boxRankingForn.getChildren().add(linha);
            }
            boxInsightsConteudo.getChildren().add(boxRankingForn);
        }

        if (produtosCache.isEmpty()) {
            boxInsightsConteudo.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum produto cadastrado ainda. Utilize a sub-aba Produtos para cadastrar.", 12, false, "#64748B"));
            return;
        }
        LocalDate hoje = LocalDate.now();
        int exibidos = 0;

        List<Produto> ordenados = new ArrayList<>(produtosCache);
        ordenados.sort(Comparator.comparing(p -> {
            LocalDate mv = consolidadoCache.menorValidadePorProduto().get(p.getId());
            return mv != null ? mv : LocalDate.MAX;
        }));

        for (Produto p : ordenados) {
            if (exibidos >= 16) break;
            exibidos++;
            BigDecimal saldo = consolidadoCache.saldosPorProduto().getOrDefault(p.getId(), BigDecimal.ZERO);
            LocalDate menorVal = consolidadoCache.menorValidadePorProduto().get(p.getId());
            String um = p.getUm() != null ? p.getUm().getSigla() : "UN";

            String statusTxt;
            String bg = "#FFFFFF", bd = "#CBD5E1", tx = "#0F172A";
            if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
                statusTxt = "SEM ESTOQUE";
                bg = "#F8FAFC"; tx = "#475569";
            } else if (menorVal != null && menorVal.isBefore(hoje)) {
                statusTxt = "LOTE VENCIDO";
                bg = "#FEF2F2"; bd = "#FCA5A5"; tx = "#991B1B";
            } else if (menorVal != null && !menorVal.isAfter(hoje.plusDays(15))) {
                statusTxt = "VENCE EM BREVE";
                bg = "#FFFBEB"; bd = "#FCD34D"; tx = "#92400E";
            } else {
                statusTxt = "SAUDÁVEL";
                bg = "#ECFDF5"; bd = "#A7F3D0"; tx = "#065F46";
            }

            Label badge = FormatadorSeguroUI.criarBadgeCartoon(statusTxt, bg, bd, tx);
            Label lTit = FormatadorSeguroUI.criarLabel(
                    FormatadorSeguroUI.limparNomeProduto(p.getNome()) + " (#" + p.getId() + ")", 13, true, "#0F172A");
            String valStr = menorVal != null ? FormatadorSeguroUI.formatarDataAmigavel(menorVal) : "Sem validade definida";
            Label lDesc = FormatadorSeguroUI.criarLabel(String.format(FormatadorSeguroUI.LOCALE_BR,
                    "Saldo consolidado: %s • Próximo vencimento: %s • Preço: %s",
                    FormatadorSeguroUI.formatarQuantidade(saldo, um), valStr,
                    FormatadorSeguroUI.formatarMoeda(p.getPrecoVenda())), 12, false, "#475569");

            VBox info = new VBox(3, new FlowPane(6, 4, badge, lTit), lDesc);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnPromover = FormatadorSeguroUI.criarBotaoIA("💬 Diagnóstico IA", () ->
                    conectorIA.sugerirPromocaoOuReposicao(p.getNome(), saldo, menorVal));

            HBox row = new HBox(10, info, btnPromover);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 12, 10, 12));
            row.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1") + "-fx-cursor: hand;");
            row.setOnMouseClicked(e -> {
                if (!(e.getTarget() instanceof Button)) {
                    abrirCardDetalhesProduto(p);
                }
            });
            boxInsightsConteudo.getChildren().add(row);
        }
    }

    private void filtrarTabelaLotes() {
        String termo = txtBuscaLote.getText() != null ? txtBuscaLote.getText().trim().toLowerCase(Locale.ROOT) : "";
        String modo = cbFiltroValidadeLote.getValue() != null ? cbFiltroValidadeLote.getValue() : "Todos com saldo";
        LocalDate hoje = LocalDate.now();
        List<Item<? extends Produto>> res = new ArrayList<>();
        for (Item<? extends Produto> l : lotesCache) {
            String prodNome = l.getProduto() != null ? l.getProduto().getNome() : "";
            String txt = ("#" + l.getId() + " " + prodNome).toLowerCase(Locale.ROOT);
            if (!termo.isEmpty() && !txt.contains(termo)) continue;
            LocalDate dv = l.getDataValidade();
            boolean matchModo = switch (modo) {
                case "Vencendo ≤ 15 dias" -> dv != null && !dv.isBefore(hoje) && !dv.isAfter(hoje.plusDays(15));
                case "Vencidos" -> dv != null && dv.isBefore(hoje);
                case "Saudáveis (> 15d)" -> dv == null || dv.isAfter(hoje.plusDays(15));
                default -> true;
            };
            if (matchModo) res.add(l);
        }
        tabelaLotes.setItems(FXCollections.observableArrayList(res));
    }

    private String classificarSituacaoLote(LocalDate dv) {
        if (dv == null) return "Normal";
        long d = ChronoUnit.DAYS.between(LocalDate.now(), dv);
        if (d < 0) return "VENCIDO (" + Math.abs(d) + "d)";
        if (d <= 15) return "ALERTA (" + d + "d)";
        return "Válido (" + d + "d)";
    }

    private void popularCombosPerdaKitsUms() {
        cbLotePerda.getItems().clear();
        for (Item<? extends Produto> l : lotesCache) {
            String pNome = l.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(l.getProduto().getNome()) : "Produto";
            String um = l.getProduto() != null && l.getProduto().getUm() != null ? l.getProduto().getUm().getSigla() : "UN";
            cbLotePerda.getItems().add(String.format("Lote #%d • %s (%s • Val: %s)",
                    l.getId(), pNome,
                    FormatadorSeguroUI.formatarQuantidade(l.getQuantidadeDisponivel(), um),
                    l.getDataValidade() != null ? FormatadorSeguroUI.formatarDataAmigavel(l.getDataValidade()) : "Indeterm."));
        }
        if (!cbLotePerda.getItems().isEmpty()) cbLotePerda.getSelectionModel().selectFirst();

        String umAtual = cbProdUm.getValue();
        String umKitAtual = cbKitNovoUm.getValue();
        cbProdUm.getItems().clear();
        cbKitNovoUm.getItems().clear();
        for (Um u : umsCache) {
            cbProdUm.getItems().add(u.getSigla());
            cbKitNovoUm.getItems().add(u.getSigla());
        }
        if (cbProdUm.getItems().isEmpty()) {
            cbProdUm.getItems().add("UN");
            cbKitNovoUm.getItems().add("UN");
        }
        if (umAtual != null && cbProdUm.getItems().contains(umAtual)) cbProdUm.setValue(umAtual);
        else cbProdUm.getSelectionModel().selectFirst();

        if (umKitAtual != null && cbKitNovoUm.getItems().contains(umKitAtual)) cbKitNovoUm.setValue(umKitAtual);
        else cbKitNovoUm.getSelectionModel().selectFirst();

        cbKitPai.getItems().clear();
        cbKitComponente.getItems().clear();
        for (Produto p : produtosCache) {
            String item = "#" + p.getId() + " - " + FormatadorSeguroUI.limparNomeProduto(p.getNome());
            cbKitPai.getItems().add(item);
            cbKitComponente.getItems().add(item);
        }
        if (!cbKitPai.getItems().isEmpty()) cbKitPai.getSelectionModel().selectFirst();
        if (cbKitComponente.getItems().size() > 1) cbKitComponente.getSelectionModel().select(1);
    }

    private void registrarPerdaEmEstoque() {
        int idx = cbLotePerda.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= lotesCache.size()) {
            lblStatusPerda.setText("Selecione um lote disponível para registrar a baixa.");
            return;
        }
        Item<? extends Produto> lote = lotesCache.get(idx);
        BigDecimal qtd = parseDecimalBr(txtQtdPerda.getText(), BigDecimal.ONE);
        String motivo = txtMotivoPerda.getText() != null && !txtMotivoPerda.getText().isBlank()
                ? txtMotivoPerda.getText().trim()
                : "Perda direta em estoque";
        String nomeProd = lote.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(lote.getProduto().getNome()) : "Mercadoria";

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Registrar Perda em Estoque",
                "A quantidade informada será baixada do lote selecionado gerando Nota de Perda contábil.",
                Map.of("Lote Atingido", "#" + lote.getId(),
                       "Produto", nomeProd,
                       "Quantidade Perdida", FormatadorSeguroUI.formatarQuantidade(qtd, ""),
                       "Motivo", motivo),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("idLote", lote.getId());
                    params.put("quantidade", qtd);
                    params.put("motivo", motivo);
                    if (dpDataPerda.getValue() != null) {
                        params.put("dataGerada", dpDataPerda.getValue().atTime(12, 0));
                    }

                    ponte.executarRotaAsync(RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA, params, resp -> {
                        if (resp.sucesso()) {
                            lblStatusPerda.setText("✔ Perda em estoque registrada no Lote #" + lote.getId() + ".");
                            lblStatusPerda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                            txtMotivoPerda.clear();
                            if (resp.dados() instanceof Nota n && n.getId() != null) {
                                Thread.ofVirtual().start(() -> {
                                    Nota comp = ponte.buscarNotaCompletaPorId(n.getId());
                                    Platform.runLater(() -> {
                                        if (comp != null) {
                                            DialogoCorporativoModal.exibirComprovanteNotaImprimivel(
                                                    this, comp, "Comprovante de Perda em Estoque #" + n.getId());
                                        }
                                    });
                                });
                            }
                        } else {
                            lblStatusPerda.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                            lblStatusPerda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                        }
                    });
                });
    }

    private void renderizarHistoricoPerdas() {
        boxHistoricoPerdas.getChildren().clear();
        int cont = 0;
        for (Nota n : notasCache) {
            if (n instanceof NotaPerda np && cont < 20) {
                cont++;
                boolean emEstoque = np.isPerdaEstoque();
                String prod = !np.getItens().isEmpty() && np.getItens().get(0).getProduto() != null
                        ? np.getItens().get(0).getProduto().getNome() : "Mercadoria";
                String forn = np.getPessoa() != null ? np.getPessoa().getNome() : "Estoque interno";
                Label badge = FormatadorSeguroUI.criarBadgeCartoon(
                        emEstoque ? "PERDA EM ESTOQUE" : "PÓS-VENDA",
                        emEstoque ? "#FEF3C7" : "#FEF2F2",
                        emEstoque ? "#FCD34D" : "#FCA5A5",
                        emEstoque ? "#92400E" : "#991B1B");
                Label tit = FormatadorSeguroUI.criarLabel(
                        String.format("#%d • %s • %s", np.getId(), FormatadorSeguroUI.limparNomeProduto(prod),
                                FormatadorSeguroUI.formatarMoeda(np.getValorCustoPerda())),
                        12, true, "#0F172A");
                Label sub = FormatadorSeguroUI.criarLabel(
                        String.format("%s • Origem: %s • Motivo: %s",
                                FormatadorSeguroUI.formatarDataHoraAmigavel(np.getDataGerada()),
                                forn, FormatadorSeguroUI.tratarParaExibicao(np.getDescricao(), np.getTipoPerda())),
                        11, false, "#475569");
                VBox info = new VBox(3, new FlowPane(6, 4, badge, tit), sub);
                HBox.setHgrow(info, Priority.ALWAYS);

                Button btnEditarPerda = FormatadorSeguroUI.criarBotaoCartoon("✏️ Editar", "#FFFBEB", "#F59E0B", "#92400E");
                btnEditarPerda.setOnAction(e -> abrirModalEditarNotaPerdaEstoque(np));

                String dataPerdaFmt = FormatadorSeguroUI.formatarDataHoraAmigavel(np.getDataGerada());
                Button btnIA = FormatadorSeguroUI.criarBotaoIA("💬 Causas IA", () ->
                        conectorIA.analisarPerdaERessarcimento(
                                this,
                                "Estoque — Perdas & Avarias",
                                np.getId(),
                                dataPerdaFmt,
                                prod,
                                forn,
                                np.getDescricao(),
                                np.getValorCustoPerda(),
                                np.isPerdaDentroDaValidade(),
                                "Tipo: " + FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "PERDA_ESTOQUE")));
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
                                    DialogoCorporativoModal.exibirComprovanteNotaImprimivel(
                                            this, comp, "Detalhes da Nota de Perda #" + np.getId());
                                }
                            });
                        });
                    }
                });
                boxHistoricoPerdas.getChildren().add(row);
            }
        }
        if (cont == 0) {
            boxHistoricoPerdas.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhuma Nota de Perda registrada até o momento.", 12, false, "#64748B"));
        }
    }

    private void filtrarTabelaProdutos() {
        String termo = txtBuscaProduto.getText() != null ? txtBuscaProduto.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<Produto> lista = new ArrayList<>();
        for (Produto p : produtosCache) {
            String txt = ("#" + p.getId() + " " + p.getNome()).toLowerCase(Locale.ROOT);
            if (termo.isEmpty() || txt.contains(termo)) lista.add(p);
        }
        tabelaProdutos.setItems(FXCollections.observableArrayList(lista));
    }

    private void limparCamposProduto() {
        this.produtoSelecionado = null;
        carrosselProdutoForm.limpar();
        txtProdNome.clear();
        txtProdDesc.clear();
        txtProdPreco.clear();
        txtAlturaCm.clear();
        txtLarguraCm.clear();
        txtComprimentoCm.clear();
        txtPesoBrutoKg.clear();
        txtPesoLiquidoKg.clear();
        txtLinkTitulo.clear();
        txtLinkUrl.clear();
    }

    private void cadastrarNovoProduto() {
        String nome = txtProdNome.getText() != null ? txtProdNome.getText().trim() : "";
        if (nome.isEmpty()) {
            lblStatusProduto.setText("Informe o nome do produto.");
            return;
        }
        BigDecimal preco = parseDecimalBr(txtProdPreco.getText(), BigDecimal.ZERO);
        boolean digital = tgNovoDigital.isSelected();

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                digital ? "Cadastrar Produto Digital" : "Cadastrar Produto Físico",
                "Confirma a inclusão deste novo item no catálogo comercial?",
                Map.of("Nome", nome,
                       "Tipo", digital ? "Produto Digital" : "Produto Físico (" + cbProdUm.getValue() + ")",
                       "Preço de Venda", FormatadorSeguroUI.formatarMoeda(preco)),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("nome", nome);
                    params.put("descricao", txtProdDesc.getText() != null ? txtProdDesc.getText().trim() : "");
                    params.put("precoVenda", preco);

                    RotaSistema rota;
                    if (digital) {
                        rota = RotaSistema.PRODUTO_DIGITAL_CADASTRAR;
                        params.put("links", ponte.criarLinksDigitais(txtLinkTitulo.getText(), txtLinkUrl.getText()));
                    } else {
                        rota = RotaSistema.PRODUTO_FISICO_CADASTRAR;
                        params.put("siglaUm", cbProdUm.getValue() != null ? cbProdUm.getValue() : "UN");
                        params.put("alturaCm", parseDecimalBr(txtAlturaCm.getText(), BigDecimal.ZERO));
                        params.put("larguraCm", parseDecimalBr(txtLarguraCm.getText(), BigDecimal.ZERO));
                        params.put("comprimentoCm", parseDecimalBr(txtComprimentoCm.getText(), BigDecimal.ZERO));
                        params.put("pesoBrutoKg", parseDecimalBr(txtPesoBrutoKg.getText(), BigDecimal.ZERO));
                        params.put("pesoLiquidoKg", parseDecimalBr(txtPesoLiquidoKg.getText(), BigDecimal.ZERO));
                    }

                    ponte.executarRotaAsync(rota, params, resp -> {
                        if (resp.sucesso()) {
                            if (resp.dados() instanceof Produto novo && novo.getId() != null) {
                                carrosselProdutoForm.salvarImagensPendentesNoProduto(novo.getId());
                            }
                            lblStatusProduto.setText("✔ Produto '" + nome + "' cadastrado (com descrição e fotos salvas)!");
                            lblStatusProduto.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                            limparCamposProduto();
                            precisaRecarregar = true;
                            carregarSeNecessario();
                        } else {
                            lblStatusProduto.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                            lblStatusProduto.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                        }
                    });
                });
    }

    private void atualizarNomeDescricaoProduto() {
        if (produtoSelecionado == null) return;
        String novoNome = txtProdNome.getText() != null ? txtProdNome.getText().trim() : produtoSelecionado.getNome();
        String desc = txtProdDesc.getText() != null ? txtProdDesc.getText().trim() : "";
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Atualizar Dados do Produto #" + produtoSelecionado.getId(),
                "Confirma a alteração cadastral de nome e descrição?",
                Map.of("Novo Nome", novoNome, "Descrição", desc),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("idProduto", produtoSelecionado.getId());
                    params.put("novoNome", novoNome);
                    params.put("descricao", desc);
                    ponte.executarRotaAsync(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR, params, resp ->
                            lblStatusProduto.setText(resp.sucesso() ? "✔ Nome e descrição atualizados."
                                    : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem())));
                });
    }

    private void atualizarPrecoProduto() {
        if (produtoSelecionado == null) return;
        BigDecimal novoPreco = parseDecimalBr(txtProdPreco.getText(), BigDecimal.ZERO);
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Alterar Preço de Venda — " + produtoSelecionado.getNome(),
                "Confirma a atualização do preço de balcão?",
                Map.of("Preço Anterior", FormatadorSeguroUI.formatarMoeda(produtoSelecionado.getPrecoVenda()),
                       "Novo Preço", FormatadorSeguroUI.formatarMoeda(novoPreco)),
                () -> ponte.executarRotaAsync(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                        Map.of("idProduto", produtoSelecionado.getId(), "novoPreco", novoPreco),
                        resp -> lblStatusProduto.setText(resp.sucesso() ? "✔ Preço atualizado."
                                : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));
    }

    private void atualizarDimensoesProduto() {
        if (produtoSelecionado == null) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Atualizar Dimensões & Pesos",
                "Confirma as medidas logísticas de " + produtoSelecionado.getNome() + "?",
                Map.of("Altura/Larg/Comp (cm)", txtAlturaCm.getText() + " x " + txtLarguraCm.getText() + " x " + txtComprimentoCm.getText(),
                       "Peso Bruto / Líq (kg)", txtPesoBrutoKg.getText() + " / " + txtPesoLiquidoKg.getText()),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("idProduto", produtoSelecionado.getId());
                    params.put("alturaCm", parseDecimalBr(txtAlturaCm.getText(), BigDecimal.ZERO));
                    params.put("larguraCm", parseDecimalBr(txtLarguraCm.getText(), BigDecimal.ZERO));
                    params.put("comprimentoCm", parseDecimalBr(txtComprimentoCm.getText(), BigDecimal.ZERO));
                    params.put("pesoBrutoKg", parseDecimalBr(txtPesoBrutoKg.getText(), BigDecimal.ZERO));
                    params.put("pesoLiquidoKg", parseDecimalBr(txtPesoLiquidoKg.getText(), BigDecimal.ZERO));
                    ponte.executarRotaAsync(RotaSistema.PRODUTO_FISICO_ATUALIZAR_DIMENSOES, params, resp ->
                            lblStatusProduto.setText(resp.sucesso() ? "✔ Dimensões e pesos atualizados."
                                    : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem())));
                });
    }

    private void excluirProdutoSelecionado() {
        if (produtoSelecionado == null) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Excluir Produto #" + produtoSelecionado.getId(),
                "Esta ação removerá definitivamente o produto caso não possua saldo positivo ou histórico bloqueante.",
                Map.of("Produto", produtoSelecionado.getNome()),
                () -> ponte.executarRotaAsync(RotaSistema.PRODUTO_CATALOGO_EXCLUIR, Map.of("idProduto", produtoSelecionado.getId()), resp -> {
                    if (resp.sucesso()) {
                        lblStatusProduto.setText("✔ Produto excluído.");
                        limparCamposProduto();
                        tgNovoFisico.setSelected(true);
                        reconstruirFormularioProduto();
                    } else {
                        lblStatusProduto.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                    }
                }));
    }

    private void renderizarListaKits() {
        boxListaKits.getChildren().clear();
        int totalKits = 0;
        for (Produto p : produtosCache) {
            if (p.isConjunto() && p.getItensConjunto() != null && !p.getItensConjunto().isEmpty()) {
                totalKits++;
                StringBuilder sb = new StringBuilder();
                for (ConjuntoProduto cp : p.getItensConjunto()) {
                    if (!sb.isEmpty()) sb.append(" + ");
                    String nm = cp.getItem() != null ? FormatadorSeguroUI.limparNomeProduto(cp.getItem().getNome()) : "Item";
                    sb.append(FormatadorSeguroUI.formatarQuantidade(cp.getQuantidade(), "")).append("x ").append(nm);
                }
                VBox card = new VBox(3,
                        FormatadorSeguroUI.criarLabel("🎁 #" + p.getId() + " " + FormatadorSeguroUI.limparNomeProduto(p.getNome())
                                + " (" + FormatadorSeguroUI.formatarMoeda(p.getPrecoVenda()) + ")", 12, true, "#0F172A"),
                        FormatadorSeguroUI.criarLabel("Composição: " + sb, 11, false, "#475569"));
                card.setPadding(new Insets(8, 10, 8, 10));
                card.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1") + "-fx-cursor: hand;");
                card.setOnMouseClicked(e -> abrirCardDetalhesKit(p));
                boxListaKits.getChildren().add(card);
            }
        }
        if (totalKits == 0) {
            boxListaKits.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum kit montado ainda. Crie um Novo Kit ou selecione um existente acima e inclua seus componentes.", 12, false, "#64748B"));
        }
    }

    private void abrirCardDetalhesKit(Produto kit) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Kit Comercial", "#" + kit.getId() + " — " + FormatadorSeguroUI.limparNomeProduto(kit.getNome()));
        meta.put("Preço do Kit", FormatadorSeguroUI.formatarMoeda(kit.getPrecoVenda()));
        meta.put("Qtd. de Componentes", kit.getItensConjunto() != null ? kit.getItensConjunto().size() + " itens" : "0");
        meta.put("Descrição", FormatadorSeguroUI.tratarParaExibicao(kit.getDescricao(), "Combo comercial"));

        VBox compBox = new VBox(8);
        CarrosselImagensProduto carrossel = new CarrosselImagensProduto(ponte, true, 260, 145);
        carrossel.carregarProdutoSobDemanda(kit);
        compBox.getChildren().add(carrossel);

        if (kit.getItensConjunto() != null) {
            for (ConjuntoProduto cp : kit.getItensConjunto()) {
                if (cp.getItem() == null) continue;
                String um = cp.getItem().getUm() != null ? cp.getItem().getUm().getSigla() : "UN";
                BigDecimal saldoComp = consolidadoCache != null
                        ? consolidadoCache.saldosPorProduto().getOrDefault(cp.getItem().getId(), BigDecimal.ZERO)
                        : BigDecimal.ZERO;
                compBox.getChildren().add(FormatadorSeguroUI.criarMiniCardInfo(
                        FormatadorSeguroUI.limparNomeProduto(cp.getItem().getNome()),
                        String.format("Quantidade por kit: %s • Estoque atual do componente: %s",
                                FormatadorSeguroUI.formatarQuantidade(cp.getQuantidade(), um),
                                FormatadorSeguroUI.formatarQuantidade(saldoComp, um)),
                        "#F8FAFC", "#CBD5E1", "#0F172A"));
            }
        }

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🎁", "Composição do Kit #" + kit.getId(), kit.getNome(), "#1D4ED8",
                meta, compBox, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Análise de Margem do Kit #" + kit.getId(),
                        kit.getNome() + " • Preço: " + FormatadorSeguroUI.formatarMoeda(kit.getPrecoVenda()),
                        "Analise a composição, precificação e atratividade comercial do kit '" + kit.getNome() + "'."
                ));
    }

    private void executarUm(RotaSistema rota) {
        String sigla = txtUmSigla.getText() != null ? txtUmSigla.getText().trim().toUpperCase(Locale.ROOT) : "";
        if (sigla.isEmpty()) {
            lblStatusUm.setText("Informe a sigla da unidade (ex.: UN, KG, CX).");
            return;
        }
        String titulo = rota == RotaSistema.PRODUTO_UNIDADE_EXCLUIR ? "Excluir Unidade de Medida"
                : (rota == RotaSistema.PRODUTO_UNIDADE_ATUALIZAR ? "Atualizar Unidade de Medida" : "Cadastrar Unidade de Medida");

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                titulo,
                "Confirma esta operação na unidade de medida?",
                Map.of("Sigla", sigla, "Nome", txtUmNome.getText() != null ? txtUmNome.getText() : sigla),
                () -> {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("sigla", sigla);
                    if (rota != RotaSistema.PRODUTO_UNIDADE_EXCLUIR) {
                        params.put("nome", txtUmNome.getText() != null ? txtUmNome.getText().trim() : sigla);
                        params.put("descricao", txtUmDesc.getText() != null ? txtUmDesc.getText().trim() : "");
                        params.put("base", parseDecimalBr(txtUmBase.getText(), BigDecimal.ONE));
                    }
                    ponte.executarRotaAsync(rota, params, resp ->
                            lblStatusUm.setText(resp.sucesso() ? "✔ Operação em unidade '" + sigla + "' concluída."
                                    : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem())));
                });
    }

    private void filtrarTabelaNotasEstoque() {
        String tipo = cbFiltroTipoNotaEst.getValue() != null ? cbFiltroTipoNotaEst.getValue() : "Todas de Estoque";
        String termo = txtFiltroProdNotaEst.getText() != null ? txtFiltroProdNotaEst.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<Nota> filtradas = new ArrayList<>();
        for (Nota n : notasCache) {
            boolean matchTipo = switch (tipo) {
                case "COMPRA (Entrada)" -> n instanceof NotaCompra;
                case "PERDA (Baixa)" -> n instanceof NotaPerda;
                case "DEVOLUCAO" -> n instanceof NotaDevolucao;
                case "VENDA (Saída)" -> n instanceof NotaVenda;
                default -> true;
            };
            if (!matchTipo) continue;
            if (!termo.isEmpty()) {
                String texto = ("#" + n.getId() + " " + resumirItensNota(n) + " "
                        + (n.getPessoa() != null ? n.getPessoa().getNome() : "")).toLowerCase(Locale.ROOT);
                if (!texto.contains(termo)) continue;
            }
            filtradas.add(n);
        }
        tabelaNotasEstoque.setItems(FXCollections.observableArrayList(filtradas));
    }

    private String classificarTipoNotaEstoque(Nota n) {
        if (n instanceof NotaCompra) return "ENTRADA (Compra)";
        if (n instanceof NotaPerda np) return np.isPerdaEstoque() ? "PERDA ESTOQUE" : "PERDA PÓS-VENDA";
        if (n instanceof NotaDevolucao) return "DEVOLUÇÃO";
        if (n instanceof NotaVenda) return "SAÍDA (Venda)";
        return "NOTA";
    }

    private String resumirItensNota(Nota n) {
        if (n.getItens() == null || n.getItens().isEmpty()) {
            return FormatadorSeguroUI.tratarParaExibicao(n.getDescricao(), "Sem itens");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, n.getItens().size()); i++) {
            Item<? extends Produto> it = n.getItens().get(i);
            if (!sb.isEmpty()) sb.append("; ");
            String p = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
            sb.append(FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), "")).append("x ").append(p);
        }
        if (n.getItens().size() > 2) sb.append(" +").append(n.getItens().size() - 2);
        return sb.toString();
    }

    private Long extrairId(String s) {
        if (s == null || !s.startsWith("#")) return null;
        try {
            return Long.parseLong(s.substring(1).split("\\s*-\\s*", 2)[0].trim());
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

    private VBox construirPainelCadeiaNotasRelacionadas(RastreamentoCadeiaNota cadeia) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
        box.getChildren().add(FormatadorSeguroUI.criarLabel(
                "🔗 Notas Relacionadas aos Itens desta Cadeia (Vendas, Devoluções e Perdas)", 12, true, "#1E40AF"));

        if (cadeia == null) {
            box.getChildren().add(FormatadorSeguroUI.criarLabel("Sem vínculos downstream mapeados.", 11, false, "#64748B"));
            return box;
        }

        int total = cadeia.vendasRelacionadas().size() + cadeia.devolucoesRelacionadas().size() + cadeia.perdasRelacionadas().size();
        if (total == 0) {
            box.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhuma venda, devolução ou perda downstream vinculada a estes lotes até o momento.", 11, false, "#475569"));
            return box;
        }

        for (NotaVenda nv : cadeia.vendasRelacionadas()) {
            adicionarLinhaNotaRelacionada(box, nv, "🛒 Venda #" + nv.getId(), "#ECFDF5", "#6EE7B7", "#047857");
        }
        for (NotaDevolucao nd : cadeia.devolucoesRelacionadas()) {
            adicionarLinhaNotaRelacionada(box, nd, "↩️ Devolução #" + nd.getId(), "#FFFBEB", "#FCD34D", "#92400E");
        }
        for (NotaPerda np : cadeia.perdasRelacionadas()) {
            adicionarLinhaNotaRelacionada(box, np, "📉 Perda #" + np.getId(), "#FEF2F2", "#FCA5A5", "#991B1B");
        }
        return box;
    }

    private void adicionarLinhaNotaRelacionada(VBox container, Nota nota, String rotulo, String bg, String bd, String tx) {
        Label badge = FormatadorSeguroUI.criarBadgeCartoon(rotulo, bg, bd, tx);
        String pessoa = nota.getPessoa() != null ? nota.getPessoa().getNome() : "Consumidor/Interno";
        Label lDesc = FormatadorSeguroUI.criarLabel(String.format("%s • %s • %s",
                FormatadorSeguroUI.formatarDataHoraAmigavel(nota.getDataGerada()),
                pessoa, FormatadorSeguroUI.formatarMoeda(nota.getValorTotal())), 11, false, "#0F172A");
        lDesc.setWrapText(true);
        HBox.setHgrow(lDesc, Priority.ALWAYS);
        Button btnAbrir = FormatadorSeguroUI.criarBotaoCartoon("📄 Abrir Nota", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAbrir.setOnAction(e -> {
            if (nota.getId() != null) {
                Thread.ofVirtual().start(() -> {
                    Nota comp = ponte.buscarNotaCompletaPorId(nota.getId());
                    Platform.runLater(() -> {
                        if (comp != null) DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, comp, rotulo);
                    });
                });
            }
        });
        HBox row = new HBox(8, badge, lDesc, btnAbrir);
        row.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().add(row);
    }

    private List<Nota> montarListaDossieCadeia(RastreamentoCadeiaNota cadeia) {
        List<Nota> dossie = new ArrayList<>();
        if (cadeia == null) return dossie;
        if (cadeia.notaEntradaOrigem() != null) dossie.add(cadeia.notaEntradaOrigem());
        if (cadeia.notaPrincipal() != null && dossie.stream().noneMatch(n -> n.getId().equals(cadeia.notaPrincipal().getId()))) {
            dossie.add(cadeia.notaPrincipal());
        }
        for (NotaVenda nv : cadeia.vendasRelacionadas()) {
            if (dossie.stream().noneMatch(n -> n.getId().equals(nv.getId()))) dossie.add(nv);
        }
        for (NotaDevolucao nd : cadeia.devolucoesRelacionadas()) {
            if (dossie.stream().noneMatch(n -> n.getId().equals(nd.getId()))) dossie.add(nd);
        }
        for (NotaPerda np : cadeia.perdasRelacionadas()) {
            if (dossie.stream().noneMatch(n -> n.getId().equals(np.getId()))) dossie.add(np);
        }
        return dossie;
    }

    private void abrirModalRastreamentoCompletoNota(Long idNota, RastreamentoCadeiaNota cadeia) {
        Map<String, String> meta = new LinkedHashMap<>();
        Nota principal = cadeia != null ? cadeia.notaPrincipal() : null;
        NotaCompra entrada = cadeia != null ? cadeia.notaEntradaOrigem() : null;
        meta.put("Nota Consultada", "#" + idNota);
        if (principal != null) {
            meta.put("Tipo / Movimento", classificarTipoNotaEstoque(principal));
            meta.put("Data de Emissão", FormatadorSeguroUI.formatarDataHoraAmigavel(principal.getDataGerada()));
            meta.put("Entidade", principal.getPessoa() != null ? principal.getPessoa().getNome() : "Consumidor / Interno");
            meta.put("Total da Nota", FormatadorSeguroUI.formatarMoeda(principal.getValorTotal()));
            meta.put("Descrição / Motivo", FormatadorSeguroUI.tratarParaExibicao(principal.getDescricao(), "Operação regular"));
        }
        if (entrada != null) {
            meta.put("Nota de Entrada Origem", "Compra #" + entrada.getId()
                    + (entrada.getPessoa() != null ? " (" + entrada.getPessoa().getNome() + ")" : ""));
        }

        VBox conteudo = new VBox(10);
        if (cadeia != null && !cadeia.itensDaEntrada().isEmpty()) {
            VBox boxEnt = new VBox(4);
            boxEnt.setPadding(new Insets(8));
            boxEnt.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
            boxEnt.getChildren().add(FormatadorSeguroUI.criarLabel("📦 Itens Originais da Entrada Vinculada", 12, true, "#0F172A"));
            for (Item<? extends Produto> it : cadeia.itensDaEntrada()) {
                String np = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
                String um = it.getProduto() != null && it.getProduto().getUm() != null ? it.getProduto().getUm().getSigla() : "UN";
                Label l = FormatadorSeguroUI.criarLabel(String.format("• Lote #%d • %s • Qtd %s (Saldo %s) • %s",
                        it.getId(), np, FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), um),
                        FormatadorSeguroUI.formatarQuantidade(it.getQuantidadeDisponivel(), um),
                        FormatadorSeguroUI.formatarMoeda(it.getValor())), 11, false, "#334155");
                l.setWrapText(true);
                boxEnt.getChildren().add(l);
            }
            conteudo.getChildren().add(boxEnt);
        }
        conteudo.getChildren().add(construirPainelCadeiaNotasRelacionadas(cadeia));

        List<Nota> dossie = montarListaDossieCadeia(cadeia);
        Runnable imprimir = dossie.isEmpty() ? null : () ->
                DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                        this, dossie, "Dossiê Completo — Nota #" + idNota);

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🧾", "Rastreamento 360º — Nota #" + idNota,
                dossie.size() + " documento(s) na cadeia", "#1D4ED8",
                meta, conteudo, imprimir, null, null,
                () -> conectorIA.explicarComContextoOrigem(
                        this, "Estoque — Rastreamento de Notas",
                        "Cadeia da Nota #" + idNota, meta,
                        "Analise toda a cadeia rastreada da Nota #" + idNota + ", sua entrada de fornecedor e seus desdobramentos."));
    }

    private void abrirModalEditarNotaPerdaEstoque(NotaPerda np) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("✏️ Editar Nota de Perda #" + np.getId());
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());

        Label tit = FormatadorSeguroUI.criarLabel("✏️ Editar Nota de Perda em Estoque #" + np.getId(), 14, true, "#0F172A");
        ComboBox<String> cbTipo = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbTipo.getItems().addAll("PERDA_INTERNA", "VENCIMENTO", "AVARIA_FORNECEDOR", "PERDA_CLIMATICA", "POS_VENDA");
        cbTipo.setEditable(true);
        cbTipo.setValue(FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "PERDA_INTERNA"));

        DatePicker dpData = new DatePicker(np.getDataGerada() != null ? np.getDataGerada().toLocalDate() : LocalDate.now());
        BigDecimal qtdOrig = !np.getItens().isEmpty() && np.getItens().get(0).getQuantidade() != null
                ? np.getItens().get(0).getQuantidade() : BigDecimal.ONE;
        TextField txtQtd = FormatadorSeguroUI.criarCampoTextoCartoon(qtdOrig.toPlainString().replace(".", ","), "#CBD5E1", "#0F172A");
        txtQtd.setText(qtdOrig.toPlainString().replace(".", ","));

        TextArea txtMotivo = FormatadorSeguroUI.criarAreaTextoCartoon("Motivo real da perda em estoque...", "#CBD5E1", "#0F172A");
        txtMotivo.setPrefRowCount(3);
        txtMotivo.setText(np.getDescricao() != null ? np.getDescricao() : "");

        Label lblSt = FormatadorSeguroUI.criarLabel("", 11, true, "#DC2626");
        Button btnSalvar = FormatadorSeguroUI.criarBotaoCartoon("💾 Salvar Alterações", "#047857", "#065F46", "#FFFFFF");
        btnSalvar.setOnAction(e -> {
            BigDecimal novaQtd = parseDecimalBr(txtQtd.getText(), qtdOrig);
            btnSalvar.setDisable(true);
            ponte.editarNotaPerdaAsync(
                    np.getId(), txtMotivo.getText(), cbTipo.getValue(), dpData.getValue(), novaQtd,
                    ok -> {
                        dlg.close();
                        lblStatusPerda.setText("✔ Nota de Perda #" + np.getId() + " atualizada com sucesso.");
                        lblStatusPerda.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                        precisaRecarregar = true;
                        carregarSeNecessario();
                    },
                    err -> {
                        btnSalvar.setDisable(false);
                        lblSt.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(err));
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
}
