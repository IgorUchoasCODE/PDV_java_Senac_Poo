package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAPrazo;
import br.com.senac.repository.modal.pagamento.PagamentoParcelado;
import br.com.senac.repository.modal.pagamento.ParcelaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.Registro;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.PagamentoQuitacaoInput;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.RastreamentoCadeiaNota;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aba 4: Financeiro & Contabilidade.
 * Centraliza apuração de lucro contábil, sub-aba dedicada para Cadastro de Nota(s) de Entrada com múltiplos itens/pagamentos,
 * rastreamento completo de notas com impressão, contas a receber/pagar, clientes/fornecedores formatados
 * e formas de pagamento.
 */
public class AbaFinanceiroContabilidadeView extends VBox {

    private record LinhaTituloFinanceiro(
            Nota nota,
            NotaPagamento pagamento,
            ParcelaPagamento parcelaOpcional,
            String direcao,
            String pessoaNome,
            String formaDescricao,
            LocalDate vencimento,
            BigDecimal valor,
            String status
    ) {}

    private record ItemEntradaRascunho(Produto produto, BigDecimal quantidade, BigDecimal custoUnitario, LocalDate validade) {
        BigDecimal subtotal() { return custoUnitario.multiply(quantidade); }
    }

    private record PagamentoEntradaRascunho(String codigoForma, String nomeForma, String modalidade, int parcelas, LocalDate vencimento, BigDecimal valor) {}

    private record NotaEntradaRascunho(
            Long idFornecedor,
            String nomeFornecedor,
            LocalDate dataEmissao,
            String observacao,
            List<ItemEntradaRascunho> itens,
            List<PagamentoEntradaRascunho> pagamentos
    ) {
        BigDecimal totalItens() {
            return itens.stream().map(ItemEntradaRascunho::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final TabPane tabPane = FormatadorSeguroUI.criarTabPaneCorporativo();
    private final Tab tabRelatorioBacen = new Tab("📊 Relatório Financeiro & Margens");
    private final Tab tabNotaEntrada = new Tab("📥 Cadastro de Nota de Entrada");
    private final Tab tabNotas = new Tab("📑 Todas as Notas");
    private final Tab tabTitulos = new Tab("💳 Contas a Receber & Pagar");
    private final Tab tabPessoas = new Tab("👥 Clientes & Fornecedores");
    private final Tab tabFormasPag = new Tab("🏦 Formas de Pagamento");

    // KPI Cards
    private final Label lblKpiReceita = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#0F172A");
    private final Label lblKpiCmv = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#334155");
    private final Label lblKpiLucroLiq = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#047857");
    private final Label lblKpiAberto = FormatadorSeguroUI.criarValorKpi("R$ 0,00", "#B45309");

    // Dashboard Dinâmico de Saúde Financeira
    private final Label lblScoreSaudeFin = FormatadorSeguroUI.criarValorKpi("-- / 100", "#047857");
    private final Label lblSaudeDetalhesFin = FormatadorSeguroUI.criarLabel("Calculando indicadores dinâmicos de saúde financeira...", 11, false, "#475569");
    private final Canvas canvasSaudeFin = new Canvas(680, 74);

    // Caches
    private List<Nota> notasCache = new ArrayList<>();
    private List<Pessoa> pessoasCache = new ArrayList<>();
    private List<Produto> produtosCache = new ArrayList<>();
    private List<FormaPagamento> formasPagCache = new ArrayList<>();
    private List<Object[]> lucroBacenCache = new ArrayList<>();
    private List<Object[]> perdasFornCache = new ArrayList<>();

    // 4.1 Relatório Financeiro & Perdas Fornecedor
    private final TextField txtFiltroNotaBacen = FormatadorSeguroUI.criarCampoTextoCartoon("Filtrar #Nota de Venda (opcional)...", "#CBD5E1", "#0F172A");
    private final TableView<Object[]> tabelaLucroBacen = new TableView<>();
    private final ComboBox<String> cbFornPerdaRel = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final CheckBox chkApenasPosVendaRel = new CheckBox("Apenas Pós-Venda Elegíveis p/ Abatimento");
    private final TableView<Object[]> tabelaPerdasForn = new TableView<>();

    // 4.2 Cadastro Dedicado de Nota(s) de Entrada + Lote + Pagamentos
    private final ComboBox<String> cbCompraFornecedor = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final DatePicker dpCompraEmissao = new DatePicker(LocalDate.now());
    private final TextField txtCompraObs = FormatadorSeguroUI.criarCampoTextoCartoon("Nº Documento / Observação da Entrada", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbCompraProduto = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtCompraQtd = FormatadorSeguroUI.criarCampoTextoCartoon("10", "#CBD5E1", "#0F172A");
    private final TextField txtCompraCustoUnit = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final DatePicker dpCompraValidade = new DatePicker(LocalDate.now().plusMonths(3));

    private final ComboBox<String> cbCompraFormaPag = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbCompraModalidadePag = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<Integer> cbCompraParcelas = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final DatePicker dpCompraVencPag = new DatePicker(LocalDate.now().plusDays(30));
    private final TextField txtCompraValorPag = FormatadorSeguroUI.criarCampoTextoCartoon("Valor Pgto", "#CBD5E1", "#0F172A");

    private final List<ItemEntradaRascunho> itensEntradaAtual = new ArrayList<>();
    private final List<PagamentoEntradaRascunho> pagamentosEntradaAtual = new ArrayList<>();
    private final List<NotaEntradaRascunho> filaNotasEntrada = new ArrayList<>();
    private final VBox boxItensEntradaAtual = new VBox(5);
    private final VBox boxPagamentosEntradaAtual = new VBox(5);
    private final VBox boxFilaNotasEntrada = new VBox(6);
    private final Label lblTotalEntradaAtual = FormatadorSeguroUI.criarLabel("Total desta Nota de Entrada: R$ 0,00", 13, true, "#047857");
    private final CheckBox chkAbaterPerdasCompra = new CheckBox("Abater perdas pós-venda elegíveis do fornecedor");
    private final Label lblStatusCompra = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");

    // 4.3 Todas as Notas
    private final TextField txtBuscaNotas = FormatadorSeguroUI.criarCampoTextoCartoon("Buscar #nota, pessoa ou item...", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFiltroTipoNota = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFiltroEntidadeNota = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFiltroStatusNota = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TableView<Nota> tabelaTodasNotas = new TableView<>();
    private final Label lblDetalheFatorNota = FormatadorSeguroUI.criarLabel(
            "Clique em qualquer nota da tabela para abrir o Comprovante Completo c/ Impressão ou use '🔍 Causas' para fatores ambientais.", 12, false, "#334155");

    // 4.4 Contas a Receber / Pagar
    private final ComboBox<String> cbFiltroTitulo = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TableView<LinhaTituloFinanceiro> tabelaTitulos = new TableView<>();
    private final Label lblStatusTitulo = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");

    // 4.5 Pessoas (Clientes & Fornecedores Completo)
    private final TextField txtBuscaPessoa = FormatadorSeguroUI.criarCampoTextoCartoon("Buscar cliente ou fornecedor...", "#CBD5E1", "#0F172A");
    private final ComboBox<String> cbFiltroPapelPessoa = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TableView<Pessoa> tabelaPessoas = new TableView<>();
    private final ComboBox<String> cbModoPessoa = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtPesNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome completo / Razão Social", "#CBD5E1", "#0F172A");
    private final TextField txtPesDoc = FormatadorSeguroUI.criarCampoTextoCartoon("CPF ou CNPJ", "#CBD5E1", "#0F172A");
    private final TextField txtPesLimite = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final CheckBox chkPesCliente = new CheckBox("Cliente");
    private final CheckBox chkPesFornecedor = new CheckBox("Fornecedor");
    private final TextField txtPesContatoTipo = FormatadorSeguroUI.criarCampoTextoCartoon("Tipo (TELEFONE, EMAIL, RG, IE, ENDERECO)", "#CBD5E1", "#0F172A");
    private final TextField txtPesContatoValor = FormatadorSeguroUI.criarCampoTextoCartoon("Valor do contato / documento adicional", "#CBD5E1", "#0F172A");
    private final Label lblStatusPessoa = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private final VBox boxDossiePessoaEstruturado = new VBox(8);
    private Pessoa pessoaSelecionada;

    // 4.6 Formas de Pagamento (Contabilidade)
    private final TableView<FormaPagamento> tabelaFormasPag = new TableView<>();
    private final TextField txtFpCodigo = FormatadorSeguroUI.criarCampoTextoCartoon("Código (ex.: PIX, CARTAO_DEB)", "#CBD5E1", "#0F172A");
    private final TextField txtFpNome = FormatadorSeguroUI.criarCampoTextoCartoon("Nome de exibição", "#CBD5E1", "#0F172A");
    private final TextField txtFpDesc = FormatadorSeguroUI.criarCampoTextoCartoon("Descrição contábil", "#CBD5E1", "#0F172A");
    private final CheckBox chkFpVista = new CheckBox("À Vista");
    private final CheckBox chkFpPrazo = new CheckBox("A Prazo");
    private final CheckBox chkFpParcelado = new CheckBox("Parcelado");
    private final TextField txtFpMaxParc = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final TextField txtFpMaxSemJuros = FormatadorSeguroUI.criarCampoTextoCartoon("1", "#CBD5E1", "#0F172A");
    private final TextField txtFpTaxaMdr = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final TextField txtFpTarifaFixa = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final TextField txtFpDescVista = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final TextField txtFpJurosMensal = FormatadorSeguroUI.criarCampoTextoCartoon("0,00", "#CBD5E1", "#0F172A");
    private final Label lblStatusFp = FormatadorSeguroUI.criarLabel("", 12, true, "#334155");
    private FormaPagamento fpSelecionada;

    private volatile boolean precisaRecarregar = true;
    private volatile boolean abaAtiva = false;
    private volatile boolean carregando = false;

    public AbaFinanceiroContabilidadeView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        HBox topo = criarCabecalho();
        GridPane kpis = criarGridKpis();

        tabRelatorioBacen.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaRelatorio()));
        tabNotaEntrada.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaNotaEntrada()));
        tabNotas.setContent(FormatadorSeguroUI.envolverScrollSuave(construirSubAbaTodasNotas()));
        tabTitulos.setContent(construirSubAbaTitulos());
        tabPessoas.setContent(construirSubAbaPessoas());
        tabFormasPag.setContent(construirSubAbaFormasPagamento());

        tabPane.getTabs().addAll(tabRelatorioBacen, tabNotaEntrada, tabNotas, tabTitulos, tabPessoas, tabFormasPag);
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
        Label tit = FormatadorSeguroUI.criarTituloSecao("💰", "Financeiro, Contabilidade, Entradas & Cadastros", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Apuração de margens, emissão de Notas de Entrada e Pagamento em lote c/ impressão, rastreamento e cadastros.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Atualizar", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> {
            precisaRecarregar = true;
            carregarSeNecessario();
        });

        Button btnIaFin = FormatadorSeguroUI.criarBotaoIA("🤖 Analisar Margens IA", () ->
                conectorIA.perguntarAoCopiloto(
                        this,
                        "Diagnóstico Contábil de Margens",
                        lblKpiReceita.getText() + " Receita • " + lblKpiLucroLiq.getText() + " Lucro Líquido • " + lblKpiAberto.getText() + " em aberto",
                        String.format("Resumo financeiro atual: %s de Receita Líquida, %s de CMV, %s de Lucro Líquido e %s em aberto. "
                                        + "Analise minha margem líquida, possíveis perdas com fornecedores e sugira melhorias financeiras.",
                                lblKpiReceita.getText(), lblKpiCmv.getText(), lblKpiLucroLiq.getText(), lblKpiAberto.getText())));

        HBox ac = new HBox(8, btnAtualizar, btnIaFin);
        ac.setAlignment(Pos.CENTER_RIGHT);

        HBox topo = new HBox(10, boxTit, ac);
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
        g.add(criarMiniKpi("Receita Líquida Contábil", lblKpiReceita, "#FFFFFF", "#CBD5E1"), 0, 0);
        g.add(criarMiniKpi("Custo de Mercadoria Vendida (CMV)", lblKpiCmv, "#FFFFFF", "#CBD5E1"), 1, 0);
        g.add(criarMiniKpi("Lucro Líquido Apurado", lblKpiLucroLiq, "#ECFDF5", "#A7F3D0"), 2, 0);
        g.add(criarMiniKpi("Títulos / Parcelas em Aberto", lblKpiAberto, "#FFFBEB", "#FCD34D"), 3, 0);
        return g;
    }

    private VBox criarMiniKpi(String rotulo, Label valor, String bg, String borda) {
        VBox box = new VBox(4, FormatadorSeguroUI.criarLabel(rotulo, 11, true, "#475569"), valor);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon(bg, borda));
        return box;
    }

    // ==================== 4.1 RELATÓRIO FINANCEIRO & MARGENS ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaRelatorio() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        // Painel Dinâmico de Saúde Financeira (Canvas Proporcional + Indicadores)
        VBox cardSaude = new VBox(8);
        cardSaude.setPadding(new Insets(12, 14, 12, 14));
        cardSaude.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));
        HBox topoSaude = new HBox(12,
                FormatadorSeguroUI.criarLabel("🩺 Saúde Financeira & Composição Dinâmica (Receita × CMV × Perdas × Lucro)", 13, true, "#0F172A"),
                lblScoreSaudeFin);
        topoSaude.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(topoSaude.getChildren().get(0), Priority.ALWAYS);
        canvasSaudeFin.widthProperty().bind(cardSaude.widthProperty().subtract(32));
        canvasSaudeFin.widthProperty().addListener((o, a, b) -> redesenharDashboardSaudeFinanceira());
        lblSaudeDetalhesFin.setWrapText(true);
        cardSaude.getChildren().addAll(topoSaude, canvasSaudeFin, lblSaudeDetalhesFin);

        VBox cardLucro = new VBox(8);
        cardLucro.setPadding(new Insets(12));
        cardLucro.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Button btnFiltrarLucro = FormatadorSeguroUI.criarBotaoCartoon("Filtrar Nota", "#0F172A", "#0F172A", "#FFFFFF");
        btnFiltrarLucro.setOnAction(e -> recarregarRelatoriosBacenAsync());
        HBox barLucro = new HBox(8,
                FormatadorSeguroUI.criarLabel("📊 Apuração de Lucro Contábil & Margem por Item (Clique p/ expandir Card)", 13, true, "#0F172A"),
                txtFiltroNotaBacen, btnFiltrarLucro);
        barLucro.setAlignment(Pos.CENTER_LEFT);
        txtFiltroNotaBacen.setPrefWidth(210);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaLucroBacen, 240);
        tabelaLucroBacen.getColumns().setAll(
                colArray("Nota", 0, 55, false),
                colArray("Produto", 1, 175, false),
                colArray("Qtd", 2, 60, false),
                colArray("Custo Lote", 3, 85, true),
                colArray("Preço Venda", 4, 85, true),
                colArray("CMV Total", 5, 90, true),
                colArray("Receita Líq.", 6, 90, true),
                colArray("Lucro Bruto", 7, 90, true),
                colArray("Lucro Líq.", 8, 90, true),
                colPercent("Margem %", 9, 75)
        );
        tabelaLucroBacen.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) abrirCardDetalheApuracaoBacen(row.getItem());
            });
            return row;
        });
        cardLucro.getChildren().addAll(barLucro, tabelaLucroBacen);

        VBox cardPerdas = new VBox(8);
        cardPerdas.setPadding(new Insets(12));
        cardPerdas.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        chkApenasPosVendaRel.setStyle("-fx-font-size: 12px;");
        cbFornPerdaRel.setPrefWidth(230);
        cbFornPerdaRel.setOnAction(e -> recarregarPerdasFornecedorAsync());
        chkApenasPosVendaRel.setOnAction(e -> recarregarPerdasFornecedorAsync());

        HBox barPerda = new HBox(8,
                FormatadorSeguroUI.criarLabel("📉 Perdas Vinculadas a Fornecedores (Clique p/ abrir Card)", 13, true, "#0F172A"),
                cbFornPerdaRel, chkApenasPosVendaRel);
        barPerda.setAlignment(Pos.CENTER_LEFT);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaPerdasForn, 220);
        tabelaPerdasForn.getColumns().setAll(
                colArray("Perda", 0, 55, false),
                colArray("Data", 1, 105, false),
                colArray("Fornecedor", 2, 160, false),
                colArray("Produto", 4, 165, false),
                colArray("Qtd", 5, 60, false),
                colArray("Custo Perda", 7, 95, true),
                colBoolFlag("Pós-Venda?", 9, 80),
                colBoolFlag("Abatível?", 11, 80)
        );
        tabelaPerdasForn.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) abrirCardDetalhePerdaFornecedor(row.getItem());
            });
            return row;
        });
        cardPerdas.getChildren().addAll(barPerda, tabelaPerdasForn);

        raiz.getChildren().addAll(cardSaude, cardLucro, cardPerdas);
        return raiz;
    }

    private void abrirCardDetalheApuracaoBacen(Object[] r) {
        if (r == null) return;
        Long idNota = extrairIdLong(r.length > 0 && r[0] != null ? r[0].toString() : "");
        String prod = r.length > 1 && r[1] != null ? r[1].toString() : "Item";

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Nota de Venda", "#" + (r.length > 0 ? r[0] : "—"));
        meta.put("Produto", prod);
        meta.put("Quantidade Vendida", r.length > 2 && r[2] != null ? r[2].toString() : "—");
        meta.put("Custo Unitário Lote", FormatadorSeguroUI.formatarMoeda(toBd(r, 3)));
        meta.put("Preço Unitário Venda", FormatadorSeguroUI.formatarMoeda(toBd(r, 4)));
        meta.put("CMV Total", FormatadorSeguroUI.formatarMoeda(toBd(r, 5)));
        meta.put("Receita Líquida", FormatadorSeguroUI.formatarMoeda(toBd(r, 6)));
        meta.put("Lucro Bruto", FormatadorSeguroUI.formatarMoeda(toBd(r, 7)));
        meta.put("Lucro Líquido Contábil", FormatadorSeguroUI.formatarMoeda(toBd(r, 8)));
        meta.put("Margem Líquida", FormatadorSeguroUI.formatarDecimal(toBd(r, 9).doubleValue(), 2) + "%");

        Button btnAbrirNota = FormatadorSeguroUI.criarBotaoCartoon("🧾 Abrir & Imprimir Nota #" + idNota, "#EFF6FF", "#93C5FD", "#1E40AF");
        btnAbrirNota.setOnAction(e -> {
            if (idNota != null) abrirComprovanteNotaAsync(idNota, "Nota de Venda #" + idNota);
        });

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "📊", "Apuração Contábil — " + prod, "Nota #" + (r.length > 0 ? r[0] : "—"), "#047857",
                meta, new HBox(btnAbrirNota), null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Análise de Margem — " + prod,
                        "Nota #" + idNota + " • Receita " + meta.get("Receita Líquida") + " • Lucro " + meta.get("Lucro Líquido Contábil"),
                        "Analise o custo, preço, CMV e margem de " + meta.get("Margem Líquida") + " do produto '" + prod + "' na Nota #" + idNota + "."));
    }

    private void abrirCardDetalhePerdaFornecedor(Object[] r) {
        if (r == null) return;
        Long idNotaPerda = extrairIdLong(r.length > 0 && r[0] != null ? r[0].toString() : "");
        String prodFallback = r.length > 4 && r[4] != null ? r[4].toString() : "Produto";
        String fornFallback = r.length > 2 && r[2] != null ? r[2].toString() : "Fornecedor";
        String dataFallback = r.length > 1 && r[1] != null ? r[1].toString() : "—";
        String qtdFallback = r.length > 5 && r[5] != null ? r[5].toString() : "—";
        BigDecimal custoFallback = toBd(r, 7);
        boolean posVendaFallback = r.length > 9 && (r[9] instanceof Boolean b ? b : r[9] instanceof Number n && n.intValue() == 1);
        boolean abatimentoFallback = r.length > 11 && (r[11] instanceof Boolean b ? b : r[11] instanceof Number n && n.intValue() == 1);

        Thread.ofVirtual().start(() -> {
            Nota completa = idNotaPerda != null ? ponte.buscarNotaCompletaPorId(idNotaPerda) : null;
            Platform.runLater(() -> {
                String dataReal = completa != null && completa.getDataGerada() != null
                        ? FormatadorSeguroUI.formatarDataHoraAmigavel(completa.getDataGerada()) : dataFallback;
                String prodReal = completa != null && completa.getItens() != null && !completa.getItens().isEmpty()
                        && completa.getItens().get(0).getProduto() != null
                        ? FormatadorSeguroUI.limparNomeProduto(completa.getItens().get(0).getProduto().getNome())
                        : prodFallback;
                String fornReal = completa != null && completa.getPessoa() != null
                        ? completa.getPessoa().getNome() : fornFallback;
                NotaPerda np = (completa instanceof NotaPerda p) ? p : null;
                String motivoReal = completa != null && completa.getDescricao() != null && !completa.getDescricao().isBlank()
                        ? completa.getDescricao()
                        : (np != null ? FormatadorSeguroUI.tratarParaExibicao(np.getTipoPerda(), "Avaria registrada na nota")
                                      : "Perda vinculada ao lote do fornecedor");
                BigDecimal custoReal = np != null && np.getValorCustoPerda() != null ? np.getValorCustoPerda() : custoFallback;
                boolean dentroValReal = np != null ? np.isPerdaDentroDaValidade() : abatimentoFallback;
                StringBuilder origemCadeia = new StringBuilder();
                if (np != null) {
                    if (np.getNotaCompraOrigem() != null) origemCadeia.append("Entrada #").append(np.getNotaCompraOrigem().getId());
                    if (np.getNotaDevolucao() != null) {
                        if (!origemCadeia.isEmpty()) origemCadeia.append(" • ");
                        origemCadeia.append("Pós-Venda Devolução #").append(np.getNotaDevolucao().getId());
                    }
                    if (np.getNotaCompraAbatimento() != null) {
                        if (!origemCadeia.isEmpty()) origemCadeia.append(" • ");
                        origemCadeia.append("Abatimento Nota #").append(np.getNotaCompraAbatimento().getId());
                    }
                }
                if (origemCadeia.isEmpty()) origemCadeia.append("Nota de Perda #").append(idNotaPerda != null ? idNotaPerda : "—");

                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("Nota de Perda", "#" + (idNotaPerda != null ? idNotaPerda : (r.length > 0 ? r[0] : "—")));
                meta.put("Data da Ocorrência", dataReal);
                meta.put("Fornecedor Responsável", fornReal);
                meta.put("Produto Perdido", prodReal);
                meta.put("Quantidade", qtdFallback);
                meta.put("Custo Total da Perda", FormatadorSeguroUI.formatarMoeda(custoReal));
                meta.put("Motivo Real da Nota", motivoReal);
                meta.put("Cadeia / Origem", origemCadeia.toString());
                meta.put("Pós-Venda?", posVendaFallback ? "SIM" : "NÃO");
                meta.put("Elegível p/ Abatimento?", dentroValReal ? "SIM (Dentro da Validade)" : "NÃO");

                Button btnAbrirComprovante = FormatadorSeguroUI.criarBotaoCartoon(
                        "🧾 Ver & Imprimir Comprovante A4 #" + (idNotaPerda != null ? idNotaPerda : ""),
                        "#EFF6FF", "#93C5FD", "#1E40AF");
                btnAbrirComprovante.setDisable(completa == null);
                btnAbrirComprovante.setOnAction(e -> {
                    if (completa != null) {
                        DialogoCorporativoModal.exibirComprovanteNotaImprimivel(
                                this, completa, "Comprovante da Nota de Perda #" + completa.getId());
                    }
                });

                DialogoCorporativoModal.exibirCardDetalhes(
                        this, "📉", "Detalhe de Perda p/ Fornecedor — Nota #" + (idNotaPerda != null ? idNotaPerda : ""),
                        fornReal + " • " + prodReal, "#B91C1C",
                        meta, new HBox(btnAbrirComprovante), null, null, null,
                        () -> conectorIA.analisarPerdaERessarcimento(
                                this,
                                "Financeiro — Perdas Vinculadas a Fornecedores",
                                idNotaPerda,
                                dataReal,
                                prodReal,
                                fornReal,
                                motivoReal,
                                custoReal,
                                dentroValReal,
                                origemCadeia.toString()));
            });
        });
    }

    // ==================== 4.2 CADASTRO DE NOTA(S) DE ENTRADA (DEDICADA) ====================

    private VBox construirSubAbaNotaEntrada() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        // 1. Cabeçalho da Nota de Entrada atual + Inclusão de múltiplos produtos/lotes
        VBox cardNotaAtual = new VBox(10);
        cardNotaAtual.setPadding(new Insets(14));
        cardNotaAtual.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label tit = FormatadorSeguroUI.criarTituloSecao("📥", "Montar Nota de Entrada (Compra de Fornecedor + Múltiplos Itens & Pagamentos)", "#0F172A");

        GridPane gpCab = new GridPane();
        gpCab.setHgap(8);
        gpCab.setVgap(6);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.3);
            gpCab.getColumnConstraints().add(cc);
        }
        dpCompraEmissao.setMaxWidth(Double.MAX_VALUE);
        gpCab.add(FormatadorSeguroUI.criarLabel("Fornecedor:", 11, true, "#475569"), 0, 0);
        gpCab.add(FormatadorSeguroUI.criarLabel("Data de Emissão:", 11, true, "#475569"), 1, 0);
        gpCab.add(FormatadorSeguroUI.criarLabel("Observação / Nº Nota Fiscal:", 11, true, "#475569"), 2, 0);
        gpCab.add(cbCompraFornecedor, 0, 1);
        gpCab.add(dpCompraEmissao, 1, 1);
        gpCab.add(txtCompraObs, 2, 1);

        // Inclusão de Itens/Lotes
        GridPane gpItem = new GridPane();
        gpItem.setHgap(8);
        gpItem.setVgap(6);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            gpItem.getColumnConstraints().add(cc);
        }
        dpCompraValidade.setMaxWidth(Double.MAX_VALUE);
        gpItem.add(FormatadorSeguroUI.criarLabel("Produto (Entrada de Lote):", 11, true, "#475569"), 0, 0);
        gpItem.add(FormatadorSeguroUI.criarLabel("Quantidade:", 11, true, "#475569"), 1, 0);
        gpItem.add(FormatadorSeguroUI.criarLabel("Custo Unitário (R$):", 11, true, "#475569"), 2, 0);
        gpItem.add(FormatadorSeguroUI.criarLabel("Validade do Lote:", 11, true, "#475569"), 3, 0);
        gpItem.add(cbCompraProduto, 0, 1);
        gpItem.add(txtCompraQtd, 1, 1);
        gpItem.add(txtCompraCustoUnit, 2, 1);
        gpItem.add(dpCompraValidade, 3, 1);

        Button btnAddItemEnt = FormatadorSeguroUI.criarBotaoCartoon("+ Incluir Produto na Nota de Entrada", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAddItemEnt.setOnAction(e -> adicionarItemEntradaAtual());

        boxItensEntradaAtual.setPadding(new Insets(6));
        boxItensEntradaAtual.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
        renderizarItensEntradaAtual();

        // Notas de Pagamento acopladas à Entrada
        cbCompraModalidadePag.getItems().setAll("A Prazo", "À Vista", "Parcelado");
        cbCompraModalidadePag.getSelectionModel().selectFirst();
        cbCompraParcelas.getItems().setAll(1, 2, 3, 4, 5, 6, 10, 12);
        cbCompraParcelas.getSelectionModel().selectFirst();
        dpCompraVencPag.setMaxWidth(Double.MAX_VALUE);

        GridPane gpPg = new GridPane();
        gpPg.setHgap(8);
        gpPg.setVgap(6);
        for (int i = 0; i < 5; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(20);
            gpPg.getColumnConstraints().add(cc);
        }
        gpPg.add(FormatadorSeguroUI.criarLabel("Forma Pagamento:", 11, true, "#475569"), 0, 0);
        gpPg.add(FormatadorSeguroUI.criarLabel("Modalidade:", 11, true, "#475569"), 1, 0);
        gpPg.add(FormatadorSeguroUI.criarLabel("Parcelas:", 11, true, "#475569"), 2, 0);
        gpPg.add(FormatadorSeguroUI.criarLabel("Vencimento:", 11, true, "#475569"), 3, 0);
        gpPg.add(FormatadorSeguroUI.criarLabel("Valor Pgto (R$):", 11, true, "#475569"), 4, 0);
        gpPg.add(cbCompraFormaPag, 0, 1);
        gpPg.add(cbCompraModalidadePag, 1, 1);
        gpPg.add(cbCompraParcelas, 2, 1);
        gpPg.add(dpCompraVencPag, 3, 1);
        gpPg.add(txtCompraValorPag, 4, 1);

        Button btnAddPgEnt = FormatadorSeguroUI.criarBotaoCartoon("+ Incluir Nota de Pagamento", "#0F172A", "#0F172A", "#FFFFFF");
        btnAddPgEnt.setOnAction(e -> adicionarPagamentoEntradaAtual());

        boxPagamentosEntradaAtual.setPadding(new Insets(6));
        boxPagamentosEntradaAtual.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
        renderizarPagamentosEntradaAtual();

        chkAbaterPerdasCompra.setSelected(true);
        chkAbaterPerdasCompra.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");

        Button btnAddFilaLote = FormatadorSeguroUI.criarBotaoCartoon("➕ Adicionar Nota à Fila (Gerar Várias)", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnAddFilaLote.setOnAction(e -> adicionarNotaEntradaNaFila());

        Button btnEmitirUnica = FormatadorSeguroUI.criarBotaoCartoon("✔ Emitir Esta Nota & Imprimir", "#047857", "#065F46", "#FFFFFF");
        btnEmitirUnica.setOnAction(e -> emitirNotaEntradaAtualDireta());

        HBox barraAcoes = new HBox(10, lblTotalEntradaAtual, btnAddFilaLote, btnEmitirUnica);
        HBox.setHgrow(lblTotalEntradaAtual, Priority.ALWAYS);
        barraAcoes.setAlignment(Pos.CENTER_LEFT);

        cardNotaAtual.getChildren().addAll(
                tit, gpCab,
                FormatadorSeguroUI.criarLabel("1) Produtos & Lotes desta Entrada:", 12, true, "#0F172A"),
                gpItem, btnAddItemEnt, boxItensEntradaAtual,
                FormatadorSeguroUI.criarLabel("2) Notas de Pagamento vinculadas (múltiplos títulos/boletos/parcelas):", 12, true, "#0F172A"),
                gpPg, btnAddPgEnt, boxPagamentosEntradaAtual,
                chkAbaterPerdasCompra, barraAcoes, lblStatusCompra);

        // 2. Fila de Emissão de Várias Notas de Entrada em Lote
        VBox cardFila = new VBox(8);
        cardFila.setPadding(new Insets(12, 14, 12, 14));
        cardFila.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Button btnEmitirTodas = FormatadorSeguroUI.criarBotaoCartoon("🚀 Emitir Todas as Notas da Fila & Imprimir", "#047857", "#065F46", "#FFFFFF");
        btnEmitirTodas.setOnAction(e -> emitirFilaNotasEntrada());

        Button btnLimparFila = FormatadorSeguroUI.criarBotaoCartoon("Limpar Fila", "#FEE2E2", "#FCA5A5", "#991B1B");
        btnLimparFila.setOnAction(e -> {
            filaNotasEntrada.clear();
            renderizarFilaNotasEntrada();
        });

        HBox topFila = new HBox(8,
                FormatadorSeguroUI.criarLabel("🗂️ Lote / Fila de Múltiplas Notas de Entrada", 13, true, "#0F172A"),
                btnEmitirTodas, btnLimparFila);
        topFila.setAlignment(Pos.CENTER_LEFT);

        renderizarFilaNotasEntrada();
        cardFila.getChildren().addAll(topFila, boxFilaNotasEntrada);

        raiz.getChildren().addAll(cardNotaAtual, cardFila);
        return raiz;
    }

    private void adicionarItemEntradaAtual() {
        Long idProd = extrairIdPrefixado(cbCompraProduto.getValue());
        Produto prod = produtosCache.stream().filter(p -> p.getId().equals(idProd)).findFirst().orElse(null);
        if (prod == null) {
            lblStatusCompra.setText("⚠️ Selecione um produto válido.");
            return;
        }
        BigDecimal qtd = parseDecimalBr(txtCompraQtd.getText(), new BigDecimal("10"));
        BigDecimal custo = parseDecimalBr(txtCompraCustoUnit.getText(), new BigDecimal("5.00"));
        LocalDate val = dpCompraValidade.getValue() != null ? dpCompraValidade.getValue() : LocalDate.now().plusMonths(3);
        itensEntradaAtual.add(new ItemEntradaRascunho(prod, qtd, custo, val));
        renderizarItensEntradaAtual();
    }

    private void renderizarItensEntradaAtual() {
        boxItensEntradaAtual.getChildren().clear();
        if (itensEntradaAtual.isEmpty()) {
            boxItensEntradaAtual.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhum produto incluído ainda (ou preencha acima e clique em '+ Incluir Produto', ou emita diretamente com o produto selecionado).",
                    11, false, "#64748B"));
            lblTotalEntradaAtual.setText("Total desta Nota de Entrada: R$ 0,00");
            return;
        }
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < itensEntradaAtual.size(); i++) {
            int idx = i;
            ItemEntradaRascunho it = itensEntradaAtual.get(i);
            soma = soma.add(it.subtotal());
            String um = it.produto().getUm() != null ? it.produto().getUm().getSigla() : "UN";
            Label l = FormatadorSeguroUI.criarLabel(
                    String.format("%s × %s @ %s = %s (Val.: %s)",
                            FormatadorSeguroUI.formatarQuantidade(it.quantidade(), um),
                            FormatadorSeguroUI.limparNomeProduto(it.produto().getNome()),
                            FormatadorSeguroUI.formatarMoeda(it.custoUnitario()),
                            FormatadorSeguroUI.formatarMoeda(it.subtotal()),
                            FormatadorSeguroUI.formatarDataAmigavel(it.validade())),
                    12, true, "#0F172A");
            HBox.setHgrow(l, Priority.ALWAYS);
            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setOnAction(e -> {
                itensEntradaAtual.remove(idx);
                renderizarItensEntradaAtual();
            });
            HBox row = new HBox(8, l, btnRem);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", "#CBD5E1"));
            boxItensEntradaAtual.getChildren().add(row);
        }
        lblTotalEntradaAtual.setText("Total desta Nota de Entrada: " + FormatadorSeguroUI.formatarMoeda(soma));
        if (txtCompraValorPag.getText() == null || txtCompraValorPag.getText().isBlank() || pagamentosEntradaAtual.isEmpty()) {
            txtCompraValorPag.setText(soma.toPlainString().replace(".", ","));
        }
    }

    private void adicionarPagamentoEntradaAtual() {
        String cod = cbCompraFormaPag.getValue() != null ? cbCompraFormaPag.getValue() : "BOLETO";
        String mod = cbCompraModalidadePag.getValue() != null ? cbCompraModalidadePag.getValue() : "A Prazo";
        int parc = "Parcelado".equals(mod) && cbCompraParcelas.getValue() != null ? cbCompraParcelas.getValue() : 1;
        LocalDate venc = dpCompraVencPag.getValue() != null ? dpCompraVencPag.getValue() : LocalDate.now().plusDays(30);

        BigDecimal totalItens = itensEntradaAtual.stream().map(ItemEntradaRascunho::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalItens.compareTo(BigDecimal.ZERO) <= 0) {
            totalItens = parseDecimalBr(txtCompraQtd.getText(), new BigDecimal("10"))
                    .multiply(parseDecimalBr(txtCompraCustoUnit.getText(), new BigDecimal("5.00")));
        }
        BigDecimal jaPago = pagamentosEntradaAtual.stream().map(PagamentoEntradaRascunho::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal falta = totalItens.subtract(jaPago).max(BigDecimal.ZERO);
        BigDecimal val = parseDecimalBr(txtCompraValorPag.getText(), falta);
        if (val.compareTo(BigDecimal.ZERO) <= 0) return;

        pagamentosEntradaAtual.add(new PagamentoEntradaRascunho(cod, cod, mod, parc, venc, val));
        BigDecimal novoFalta = totalItens.subtract(jaPago.add(val)).max(BigDecimal.ZERO);
        txtCompraValorPag.setText(novoFalta.toPlainString().replace(".", ","));
        renderizarPagamentosEntradaAtual();
    }

    private void renderizarPagamentosEntradaAtual() {
        boxPagamentosEntradaAtual.getChildren().clear();
        if (pagamentosEntradaAtual.isEmpty()) {
            boxPagamentosEntradaAtual.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Sem notas de pagamento adicionais (será gerada automaticamente 1 Nota de Pagamento pelo valor total).",
                    11, false, "#64748B"));
            return;
        }
        for (int i = 0; i < pagamentosEntradaAtual.size(); i++) {
            int idx = i;
            PagamentoEntradaRascunho pg = pagamentosEntradaAtual.get(i);
            Label l = FormatadorSeguroUI.criarLabel(
                    String.format("%s (%s%s) • %s • Venc.: %s",
                            pg.nomeForma(), pg.modalidade(), pg.parcelas() > 1 ? " " + pg.parcelas() + "x" : "",
                            FormatadorSeguroUI.formatarMoeda(pg.valor()),
                            FormatadorSeguroUI.formatarDataAmigavel(pg.vencimento())),
                    12, true, "#0F172A");
            HBox.setHgrow(l, Priority.ALWAYS);
            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setOnAction(e -> {
                pagamentosEntradaAtual.remove(idx);
                renderizarPagamentosEntradaAtual();
            });
            HBox row = new HBox(8, l, btnRem);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
            boxPagamentosEntradaAtual.getChildren().add(row);
        }
    }

    private NotaEntradaRascunho capturarNotaEntradaRascunhoAtual() {
        Long idForn = extrairIdPrefixado(cbCompraFornecedor.getValue());
        if (idForn == null) {
            lblStatusCompra.setText("⚠️ Selecione um fornecedor válido.");
            return null;
        }
        List<ItemEntradaRascunho> itens = new ArrayList<>(itensEntradaAtual);
        if (itens.isEmpty()) {
            Long idProd = extrairIdPrefixado(cbCompraProduto.getValue());
            Produto prod = produtosCache.stream().filter(p -> p.getId().equals(idProd)).findFirst().orElse(null);
            if (prod == null) {
                lblStatusCompra.setText("⚠️ Inclua ao menos um produto válido na Nota de Entrada.");
                return null;
            }
            itens.add(new ItemEntradaRascunho(
                    prod,
                    parseDecimalBr(txtCompraQtd.getText(), new BigDecimal("10")),
                    parseDecimalBr(txtCompraCustoUnit.getText(), new BigDecimal("5.00")),
                    dpCompraValidade.getValue() != null ? dpCompraValidade.getValue() : LocalDate.now().plusMonths(3)));
        }
        BigDecimal total = itens.stream().map(ItemEntradaRascunho::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PagamentoEntradaRascunho> pgs = new ArrayList<>(pagamentosEntradaAtual);
        if (pgs.isEmpty()) {
            String cod = cbCompraFormaPag.getValue() != null ? cbCompraFormaPag.getValue() : "BOLETO";
            String mod = cbCompraModalidadePag.getValue() != null ? cbCompraModalidadePag.getValue() : "A Prazo";
            int parc = "Parcelado".equals(mod) && cbCompraParcelas.getValue() != null ? cbCompraParcelas.getValue() : 1;
            LocalDate venc = dpCompraVencPag.getValue() != null ? dpCompraVencPag.getValue() : LocalDate.now().plusDays(30);
            pgs.add(new PagamentoEntradaRascunho(cod, cod, mod, parc, venc, total));
        }

        String obs = txtCompraObs.getText() != null && !txtCompraObs.getText().isBlank()
                ? txtCompraObs.getText().trim() : "Nota de Entrada Financeiro";
        return new NotaEntradaRascunho(
                idForn, cbCompraFornecedor.getValue(),
                dpCompraEmissao.getValue() != null ? dpCompraEmissao.getValue() : LocalDate.now(),
                obs, itens, pgs);
    }

    private void adicionarNotaEntradaNaFila() {
        NotaEntradaRascunho rasc = capturarNotaEntradaRascunhoAtual();
        if (rasc == null) return;
        filaNotasEntrada.add(rasc);
        itensEntradaAtual.clear();
        pagamentosEntradaAtual.clear();
        txtCompraObs.clear();
        renderizarItensEntradaAtual();
        renderizarPagamentosEntradaAtual();
        renderizarFilaNotasEntrada();
        lblStatusCompra.setText("✔ Nota adicionada à fila em lote (" + filaNotasEntrada.size() + " pendente(s)).");
    }

    private void renderizarFilaNotasEntrada() {
        boxFilaNotasEntrada.getChildren().clear();
        if (filaNotasEntrada.isEmpty()) {
            boxFilaNotasEntrada.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Nenhuma nota na fila em lote. Monte uma entrada acima e clique em '➕ Adicionar Nota à Fila' para emitir múltiplas notas juntas.",
                    11, false, "#64748B"));
            return;
        }
        for (int i = 0; i < filaNotasEntrada.size(); i++) {
            int idx = i;
            NotaEntradaRascunho nr = filaNotasEntrada.get(i);
            Label l = FormatadorSeguroUI.criarLabel(
                    String.format("Entrada #%d • Fornecedor: %s • %d iten(s) • %d pagamento(s) • Total %s",
                            idx + 1, nr.nomeFornecedor(), nr.itens().size(), nr.pagamentos().size(),
                            FormatadorSeguroUI.formatarMoeda(nr.totalItens())),
                    12, true, "#0F172A");
            HBox.setHgrow(l, Priority.ALWAYS);
            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setOnAction(e -> {
                filaNotasEntrada.remove(idx);
                renderizarFilaNotasEntrada();
            });
            HBox row = new HBox(8, l, btnRem);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFBEB", "#FCD34D"));
            boxFilaNotasEntrada.getChildren().add(row);
        }
    }

    private void emitirNotaEntradaAtualDireta() {
        NotaEntradaRascunho rasc = capturarNotaEntradaRascunhoAtual();
        if (rasc == null) return;
        emitirListaRascunhosEntrada(List.of(rasc), true);
    }

    private void emitirFilaNotasEntrada() {
        if (filaNotasEntrada.isEmpty()) {
            lblStatusCompra.setText("⚠️ A fila de Notas de Entrada está vazia.");
            return;
        }
        emitirListaRascunhosEntrada(new ArrayList<>(filaNotasEntrada), false);
    }

    private void emitirListaRascunhosEntrada(List<NotaEntradaRascunho> lista, boolean limparFormularioAtual) {
        BigDecimal valorLote = lista.stream().map(NotaEntradaRascunho::totalItens).reduce(BigDecimal.ZERO, BigDecimal::add);
        int qtdNotas = lista.size();
        int qtdPags = lista.stream().mapToInt(n -> n.pagamentos().size()).sum();

        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                qtdNotas == 1 ? "Confirmar Nota de Entrada & Pagamentos" : "Confirmar Lote de " + qtdNotas + " Notas de Entrada",
                "Os lotes serão liberados imediatamente no estoque e os títulos financeiros serão lançados em Contas a Pagar.",
                Map.of("Notas de Entrada a Emitir", String.valueOf(qtdNotas),
                       "Notas de Pagamento Vinculadas", String.valueOf(qtdPags),
                       "Abater Perdas Pós-Venda?", chkAbaterPerdasCompra.isSelected() ? "SIM" : "NÃO",
                       "Valor Total do Lote", FormatadorSeguroUI.formatarMoeda(valorLote)),
                () -> {
                    List<Map<String, Object>> payloads = new ArrayList<>();
                    for (NotaEntradaRascunho nr : lista) {
                        List<Map<String, Object>> itensP = new ArrayList<>();
                        for (ItemEntradaRascunho it : nr.itens()) {
                            itensP.add(Map.of(
                                    "idProduto", it.produto().getId(),
                                    "quantidade", it.quantidade(),
                                    "valorUnitario", it.custoUnitario(),
                                    "dataValidade", it.validade().toString()));
                        }
                        List<Map<String, Object>> pgsP = new ArrayList<>();
                        for (PagamentoEntradaRascunho pg : nr.pagamentos()) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("codigoForma", pg.codigoForma());
                            m.put("valor", pg.valor());
                            m.put("parcelas", pg.parcelas());
                            if ("A Prazo".equals(pg.modalidade())) {
                                m.put("modalidade", "PRAZO");
                                m.put("dataValidade", pg.vencimento().toString());
                            }
                            pgsP.add(m);
                        }
                        Map<String, Object> req = new LinkedHashMap<>();
                        req.put("idFornecedor", nr.idFornecedor());
                        req.put("itens", itensP);
                        req.put("pagamentos", pgsP);
                        req.put("descricao", nr.observacao());
                        req.put("dataGerada", nr.dataEmissao().atTime(12, 0));
                        payloads.add(req);
                    }

                    lblStatusCompra.setText("Emitindo " + qtdNotas + " Nota(s) de Entrada...");
                    ponte.emitirLoteNotasCompraAsync(
                            payloads,
                            chkAbaterPerdasCompra.isSelected(),
                            notasGeradas -> {
                                lblStatusCompra.setText("✔ " + notasGeradas.size() + " Nota(s) de Entrada e pagamento(s) gravadas com sucesso!");
                                if (limparFormularioAtual) {
                                    itensEntradaAtual.clear();
                                    pagamentosEntradaAtual.clear();
                                    txtCompraObs.clear();
                                    renderizarItensEntradaAtual();
                                    renderizarPagamentosEntradaAtual();
                                } else {
                                    filaNotasEntrada.clear();
                                    renderizarFilaNotasEntrada();
                                }
                                DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                                        this, notasGeradas, "Notas de Entrada & Pagamentos Emitidos");
                            },
                            erro -> lblStatusCompra.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(erro)));
                });
    }

    // ==================== 4.3 TODAS AS NOTAS ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaTodasNotas() {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10, 2, 10, 2));

        VBox cardRastreio = new VBox(8);
        cardRastreio.setPadding(new Insets(12));
        cardRastreio.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        cbFiltroTipoNota.getItems().setAll("Todos os Tipos", "COMPRA", "VENDA", "DEVOLUCAO", "PERDA");
        cbFiltroTipoNota.getSelectionModel().selectFirst();
        cbFiltroEntidadeNota.getItems().setAll("Todas Entidades", "Clientes", "Fornecedores", "Internas / Loja");
        cbFiltroEntidadeNota.getSelectionModel().selectFirst();
        cbFiltroStatusNota.getItems().setAll("Todos Status", "Quitada / Paga", "Em Aberto / Pendente");
        cbFiltroStatusNota.getSelectionModel().selectFirst();

        txtBuscaNotas.textProperty().addListener((o, a, b) -> filtrarTabelaNotas());
        cbFiltroTipoNota.setOnAction(e -> filtrarTabelaNotas());
        cbFiltroEntidadeNota.setOnAction(e -> filtrarTabelaNotas());
        cbFiltroStatusNota.setOnAction(e -> filtrarTabelaNotas());

        HBox filtros = new HBox(8, txtBuscaNotas, cbFiltroTipoNota, cbFiltroEntidadeNota, cbFiltroStatusNota);
        HBox.setHgrow(txtBuscaNotas, Priority.ALWAYS);
        cbFiltroTipoNota.setPrefWidth(135);
        cbFiltroEntidadeNota.setPrefWidth(150);
        cbFiltroStatusNota.setPrefWidth(160);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaTodasNotas, 340);
        TableColumn<Nota, String> nId = new TableColumn<>("Nota");
        nId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        nId.setPrefWidth(55);
        TableColumn<Nota, String> nTipo = new TableColumn<>("Tipo");
        nTipo.setCellValueFactory(c -> new SimpleStringProperty(traduzirTipoNota(c.getValue())));
        nTipo.setPrefWidth(110);
        TableColumn<Nota, String> nPessoa = new TableColumn<>("Cliente / Fornecedor");
        nPessoa.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPessoa() != null ? c.getValue().getPessoa().getNome() : "Operação Interna"));
        nPessoa.setPrefWidth(175);
        TableColumn<Nota, String> nData = new TableColumn<>("Data");
        nData.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarDataHoraAmigavel(c.getValue().getDataGerada())));
        nData.setPrefWidth(115);
        TableColumn<Nota, String> nValor = new TableColumn<>("Total");
        nValor.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().getValorTotal())));
        nValor.setPrefWidth(95);
        TableColumn<Nota, String> nStatus = new TableColumn<>("Status Financeiro");
        nStatus.setCellValueFactory(c -> new SimpleStringProperty(classificarStatusNota(c.getValue())));
        nStatus.setPrefWidth(120);
        TableColumn<Nota, Void> nAcao = new TableColumn<>("Causas / Fatores");
        nAcao.setPrefWidth(115);
        nAcao.setCellFactory(col -> new TableCell<>() {
            private final Button b = FormatadorSeguroUI.criarBotaoCartoon("🔍 Causas", "#EFF6FF", "#93C5FD", "#1E40AF");
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    Nota nota = getTableView().getItems().get(getIndex());
                    b.setOnAction(e -> inspecionarCausasDaNota(nota));
                    setGraphic(b);
                }
            }
        });

        tabelaTodasNotas.getColumns().setAll(nId, nTipo, nPessoa, nData, nValor, nStatus, nAcao);
        tabelaTodasNotas.setRowFactory(tv -> {
            TableRow<Nota> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && !(e.getTarget() instanceof Button) && row.getItem().getId() != null) {
                    abrirRastreamentoCompletoNotaAsync(row.getItem().getId());
                }
            });
            return row;
        });

        VBox boxCausas = new VBox(4, lblDetalheFatorNota);
        boxCausas.setPadding(new Insets(8, 10, 8, 10));
        boxCausas.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));

        cardRastreio.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("📑 Rastreamento Completo 360º de Notas (Minhas, Clientes e Fornecedores — Clique p/ ver cadeia e imprimir)", 13, true, "#0F172A"),
                filtros, tabelaTodasNotas, boxCausas);

        raiz.getChildren().add(cardRastreio);
        return raiz;
    }

    private void abrirComprovanteNotaAsync(Long idNota, String titulo) {
        Thread.ofVirtual().start(() -> {
            Nota comp = ponte.buscarNotaCompletaPorId(idNota);
            Platform.runLater(() -> {
                if (comp != null) {
                    DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, comp, titulo);
                }
            });
        });
    }

    // ==================== 4.4 CONTAS A RECEBER & PAGAR ====================

    @SuppressWarnings("unchecked")
    private VBox construirSubAbaTitulos() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 0, 4, 0));

        cbFiltroTitulo.getItems().setAll("Em Aberto / Vencidos", "Todos os Títulos", "Apenas A Receber (Clientes)", "Apenas A Pagar (Fornecedores)", "Quitados");
        cbFiltroTitulo.getSelectionModel().selectFirst();
        cbFiltroTitulo.setPrefWidth(240);
        cbFiltroTitulo.setOnAction(e -> atualizarTabelaTitulos());

        HBox top = new HBox(10,
                FormatadorSeguroUI.criarLabel("💳 Controle de Contas a Receber, Contas a Pagar e Parcelamentos (Clique p/ detalhes)", 13, true, "#0F172A"),
                cbFiltroTitulo, lblStatusTitulo);
        top.setAlignment(Pos.CENTER_LEFT);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaTitulos, 430);
        TableColumn<LinhaTituloFinanceiro, String> cDir = new TableColumn<>("Carteira");
        cDir.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().direcao()));
        cDir.setPrefWidth(85);
        TableColumn<LinhaTituloFinanceiro, String> cNota = new TableColumn<>("Nota");
        cNota.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().nota().getId()));
        cNota.setPrefWidth(55);
        TableColumn<LinhaTituloFinanceiro, String> cPes = new TableColumn<>("Cliente / Fornecedor");
        cPes.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().pessoaNome()));
        cPes.setPrefWidth(165);
        TableColumn<LinhaTituloFinanceiro, String> cForma = new TableColumn<>("Forma / Parcela");
        cForma.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().formaDescricao()));
        cForma.setPrefWidth(145);
        TableColumn<LinhaTituloFinanceiro, String> cVenc = new TableColumn<>("Vencimento");
        cVenc.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().vencimento() != null ? FormatadorSeguroUI.formatarDataAmigavel(c.getValue().vencimento()) : "À Vista"));
        cVenc.setPrefWidth(115);
        TableColumn<LinhaTituloFinanceiro, String> cVal = new TableColumn<>("Valor");
        cVal.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().valor())));
        cVal.setPrefWidth(95);
        TableColumn<LinhaTituloFinanceiro, String> cStat = new TableColumn<>("Status");
        cStat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        cStat.setPrefWidth(90);

        TableColumn<LinhaTituloFinanceiro, Void> cQuitar = new TableColumn<>("Baixa");
        cQuitar.setPrefWidth(85);
        cQuitar.setCellFactory(col -> new TableCell<>() {
            private final Button b = FormatadorSeguroUI.criarBotaoCartoon("✔ Quitar", "#047857", "#065F46", "#FFFFFF");
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    LinhaTituloFinanceiro row = getTableView().getItems().get(getIndex());
                    boolean quitado = "QUITADO".equals(row.status());
                    b.setDisable(quitado);
                    b.setOnAction(e -> quitarTituloOuParcela(row));
                    setGraphic(b);
                }
            }
        });

        tabelaTitulos.getColumns().setAll(cDir, cNota, cPes, cForma, cVenc, cVal, cStat, cQuitar);
        tabelaTitulos.setRowFactory(tv -> {
            TableRow<LinhaTituloFinanceiro> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && !(e.getTarget() instanceof Button)) {
                    abrirCardDetalheTitulo(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(tabelaTitulos, Priority.ALWAYS);
        box.getChildren().addAll(top, tabelaTitulos);
        return box;
    }

    private void abrirCardDetalheTitulo(LinhaTituloFinanceiro row) {
        if (row == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Carteira", row.direcao());
        meta.put("Nota Vinculada", "#" + row.nota().getId());
        meta.put("Entidade", row.pessoaNome());
        meta.put("Forma / Parcela", row.formaDescricao());
        meta.put("Vencimento", row.vencimento() != null ? FormatadorSeguroUI.formatarDataAmigavel(row.vencimento()) : "À Vista");
        meta.put("Valor do Título", FormatadorSeguroUI.formatarMoeda(row.valor()));
        meta.put("Situação", row.status());

        Button btnNota = FormatadorSeguroUI.criarBotaoCartoon("🧾 Abrir & Imprimir Nota #" + row.nota().getId(), "#EFF6FF", "#93C5FD", "#1E40AF");
        btnNota.setOnAction(e -> abrirComprovanteNotaAsync(row.nota().getId(), "Nota #" + row.nota().getId()));

        HBox extra = new HBox(8, btnNota);
        if (!"QUITADO".equals(row.status())) {
            Button btnQuitar = FormatadorSeguroUI.criarBotaoCartoon("✔ Quitar Agora", "#047857", "#065F46", "#FFFFFF");
            btnQuitar.setOnAction(e -> quitarTituloOuParcela(row));
            extra.getChildren().add(btnQuitar);
        }

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "💳", "Título Financeiro — " + row.formaDescricao(),
                row.status(), "QUITADO".equals(row.status()) ? "#047857" : "#B45309",
                meta, extra, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Título #" + row.nota().getId(),
                        row.direcao() + " • " + row.pessoaNome() + " • " + FormatadorSeguroUI.formatarMoeda(row.valor()),
                        "Analise o impacto no fluxo de caixa de " + row.direcao() + " de " + FormatadorSeguroUI.formatarMoeda(row.valor())
                                + " referente a " + row.pessoaNome() + " (" + row.status() + ")."));
    }

    // ==================== 4.5 CLIENTES & FORNECEDORES (TRATAMENTO FORMATADO) ====================

    @SuppressWarnings("unchecked")
    private GridPane construirSubAbaPessoas() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setPadding(new Insets(10, 0, 4, 0));

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(48);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(52);
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        VBox esq = new VBox(8);
        esq.setPadding(new Insets(12));
        esq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        cbFiltroPapelPessoa.getItems().setAll("Todas as Entidades", "Apenas Clientes", "Apenas Fornecedores");
        cbFiltroPapelPessoa.getSelectionModel().selectFirst();
        cbFiltroPapelPessoa.setPrefWidth(160);
        cbFiltroPapelPessoa.setOnAction(e -> filtrarTabelaPessoas());
        txtBuscaPessoa.textProperty().addListener((o, a, b) -> filtrarTabelaPessoas());
        HBox.setHgrow(txtBuscaPessoa, Priority.ALWAYS);

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaPessoas, 400);
        TableColumn<Pessoa, String> pId = new TableColumn<>("ID");
        pId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        pId.setPrefWidth(50);
        TableColumn<Pessoa, String> pNome = new TableColumn<>("Nome / Razão Social");
        pNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        pNome.setPrefWidth(165);
        TableColumn<Pessoa, String> pPapel = new TableColumn<>("Papel");
        pPapel.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().isCliente() && c.getValue().isFornecedor()) ? "Cliente/Forn."
                        : (c.getValue().isCliente() ? "Cliente" : "Fornecedor")));
        pPapel.setPrefWidth(95);
        TableColumn<Pessoa, String> pDoc = new TableColumn<>("Documentos / Contatos");
        pDoc.setCellValueFactory(c -> new SimpleStringProperty(resumirRegistrosPessoa(c.getValue())));
        pDoc.setPrefWidth(155);
        TableColumn<Pessoa, String> pLim = new TableColumn<>("Limite");
        pLim.setCellValueFactory(c -> new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(c.getValue().getLimite())));
        pLim.setPrefWidth(80);
        tabelaPessoas.getColumns().setAll(pId, pNome, pPapel, pDoc, pLim);

        tabelaPessoas.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                this.pessoaSelecionada = sel;
                cbModoPessoa.setValue("✏️ Editar Selecionado & Dossiê 360º");
                txtPesNome.setText(sel.getNome() != null ? sel.getNome() : "");
                txtPesLimite.setText(sel.getLimite() != null ? sel.getLimite().toPlainString().replace(".", ",") : "0,00");
                chkPesCliente.setSelected(sel.isCliente());
                chkPesFornecedor.setSelected(sel.isFornecedor());
                carregarDossie360Async(sel);
            }
        });
        tabelaPessoas.setRowFactory(tv -> {
            TableRow<Pessoa> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 2) {
                    abrirCardDetalhePessoa(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(tabelaPessoas, Priority.ALWAYS);
        esq.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("👥 Clientes & Fornecedores (Duplo clique abre Card completo)", 13, true, "#0F172A"),
                new HBox(8, txtBuscaPessoa, cbFiltroPapelPessoa),
                tabelaPessoas);

        VBox formContent = new VBox(8);
        formContent.setPadding(new Insets(12));
        formContent.setFillWidth(true);

        cbModoPessoa.getItems().setAll(
                "Novo Cliente PF (CPF)",
                "Novo Cliente PJ (CNPJ)",
                "Novo Fornecedor PJ (CNPJ)",
                "Novo Fornecedor PF (CPF)",
                "Cadastro Simples (Cliente/Fornecedor)",
                "✏️ Editar Selecionado & Dossiê 360º"
        );
        cbModoPessoa.getSelectionModel().selectFirst();

        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(6);
        ColumnConstraints p1 = new ColumnConstraints();
        p1.setPercentWidth(50);
        ColumnConstraints p2 = new ColumnConstraints();
        p2.setPercentWidth(50);
        gp.getColumnConstraints().addAll(p1, p2);

        gp.add(FormatadorSeguroUI.criarLabel("Nome Completo / Razão Social:", 11, true, "#475569"), 0, 0);
        gp.add(FormatadorSeguroUI.criarLabel("CPF / CNPJ Principal:", 11, true, "#475569"), 1, 0);
        gp.add(txtPesNome, 0, 1);
        gp.add(txtPesDoc, 1, 1);

        gp.add(FormatadorSeguroUI.criarLabel("Limite de Crédito (R$):", 11, true, "#475569"), 0, 2);
        gp.add(FormatadorSeguroUI.criarLabel("Papéis (p/ Simples):", 11, true, "#475569"), 1, 2);
        gp.add(txtPesLimite, 0, 3);
        chkPesCliente.setSelected(true);
        HBox boxPapeis = new HBox(10, chkPesCliente, chkPesFornecedor);
        boxPapeis.setAlignment(Pos.CENTER_LEFT);
        gp.add(boxPapeis, 1, 3);

        Button btnSalvarPes = FormatadorSeguroUI.criarBotaoCartoon("Cadastrar / Salvar Pessoa", "#047857", "#065F46", "#FFFFFF");
        btnSalvarPes.setOnAction(e -> salvarOuAtualizarPessoa());

        Button btnAtLimite = FormatadorSeguroUI.criarBotaoCartoon("Atualizar Limite", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAtLimite.setOnAction(e -> atualizarLimitePessoa());

        Button btnExcPes = FormatadorSeguroUI.criarBotaoCartoon("Excluir", "#FEE2E2", "#FCA5A5", "#991B1B");
        btnExcPes.setOnAction(e -> excluirPessoaSelecionada());

        Button btnCardPes = FormatadorSeguroUI.criarBotaoCartoon("🔍 Abrir Card 360º", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnCardPes.setOnAction(e -> {
            if (pessoaSelecionada != null) abrirCardDetalhePessoa(pessoaSelecionada);
        });

        FlowPane acoesPes = new FlowPane(6, 6, btnSalvarPes, btnAtLimite, btnCardPes, btnExcPes);

        Label titReg = FormatadorSeguroUI.criarLabel("📇 Adicionar Contatos e Documentos Extras (Telefone, E-mail, RG, IE)", 12, true, "#0F172A");
        HBox linhaReg = new HBox(6, txtPesContatoTipo, txtPesContatoValor);
        txtPesContatoTipo.setPrefWidth(160);
        HBox.setHgrow(txtPesContatoValor, Priority.ALWAYS);
        Button btnAddReg = FormatadorSeguroUI.criarBotaoCartoon("+ Vincular Registro", "#0F172A", "#0F172A", "#FFFFFF");
        btnAddReg.setOnAction(e -> adicionarRegistroAPessoa());
        linhaReg.getChildren().add(btnAddReg);

        boxDossiePessoaEstruturado.getChildren().add(FormatadorSeguroUI.criarLabel(
                "Selecione um cliente ou fornecedor para visualizar o Dossiê 360º formatado.", 12, false, "#64748B"));

        VBox boxDossie = new VBox(6,
                FormatadorSeguroUI.criarLabel("🔍 Dossiê 360º (Dados Estruturados, Documentos & Notas):", 12, true, "#0F172A"),
                boxDossiePessoaEstruturado);
        boxDossie.setPadding(new Insets(10));
        boxDossie.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));

        formContent.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("Ficha Cadastral Completa", 13, true, "#0F172A"),
                cbModoPessoa, gp, acoesPes, lblStatusPessoa, titReg, linhaReg, boxDossie);

        VBox dir = new VBox(FormatadorSeguroUI.envolverScrollSuave(formContent));
        dir.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        VBox.setVgrow(dir.getChildren().get(0), Priority.ALWAYS);

        g.add(esq, 0, 0);
        g.add(dir, 1, 0);
        GridPane.setVgrow(esq, Priority.ALWAYS);
        GridPane.setVgrow(dir, Priority.ALWAYS);
        return g;
    }

    private void abrirCardDetalhePessoa(Pessoa pes) {
        if (pes == null) return;
        Map<String, String> meta = construirMapaDadosPessoa(pes);

        VBox listaNotasBox = new VBox(6);
        listaNotasBox.getChildren().add(FormatadorSeguroUI.criarLabel("📑 Últimas Notas Movimentadas por " + pes.getNome(), 12, true, "#0F172A"));
        int c = 0;
        for (Nota n : notasCache) {
            if (n.getPessoa() != null && n.getPessoa().getId().equals(pes.getId()) && c < 8) {
                c++;
                Button btnAbrir = FormatadorSeguroUI.criarBotaoCartoon(
                        String.format("%s #%d • %s • %s", traduzirTipoNota(n), n.getId(),
                                FormatadorSeguroUI.formatarDataAmigavel(n.getDataGerada() != null ? n.getDataGerada().toLocalDate() : null),
                                FormatadorSeguroUI.formatarMoeda(n.getValorTotal())),
                        "#F8FAFC", "#CBD5E1", "#0F172A");
                btnAbrir.setMaxWidth(Double.MAX_VALUE);
                Long idN = n.getId();
                btnAbrir.setOnAction(ev -> abrirComprovanteNotaAsync(idN, "Nota #" + idN));
                listaNotasBox.getChildren().add(btnAbrir);
            }
        }
        if (c == 0) {
            listaNotasBox.getChildren().add(FormatadorSeguroUI.criarLabel("Sem notas emitidas até o momento.", 11, false, "#64748B"));
        }

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "👤", pes.getNome(), meta.get("Perfil"), "#1D4ED8",
                meta, listaNotasBox, null,
                () -> conectorIA.perguntarAoCopiloto(
                        this,
                        "Relacionamento 360º — " + pes.getNome(),
                        meta.get("Perfil") + " • Limite: " + meta.get("Limite de Crédito"),
                        "Faça um resumo comercial e sugestões de relacionamento/crédito para " + pes.getNome()
                                + " (" + meta.get("Perfil") + "), com " + meta.get("Notas Vinculadas") + " notas movimentadas."));
    }

    private Map<String, String> construirMapaDadosPessoa(Pessoa pes) {
        String papel = (pes.isCliente() && pes.isFornecedor()) ? "Cliente e Fornecedor"
                : (pes.isCliente() ? "Cliente" : "Fornecedor");
        int qtdNotas = 0;
        BigDecimal totalVendido = BigDecimal.ZERO;
        BigDecimal totalComprado = BigDecimal.ZERO;
        for (Nota n : notasCache) {
            if (n.getPessoa() != null && n.getPessoa().getId().equals(pes.getId())) {
                qtdNotas++;
                BigDecimal v = n.getValorTotal() != null ? n.getValorTotal() : BigDecimal.ZERO;
                if (n instanceof NotaVenda) totalVendido = totalVendido.add(v);
                if (n instanceof NotaCompra) totalComprado = totalComprado.add(v);
            }
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Código", "#" + pes.getId());
        meta.put("Nome / Razão Social", FormatadorSeguroUI.tratarParaExibicao(pes.getNome(), "Não informado"));
        meta.put("Perfil", papel);
        meta.put("Limite de Crédito", FormatadorSeguroUI.formatarMoeda(pes.getLimite()));
        meta.put("Documentos / Contatos", resumirRegistrosPessoa(pes));
        meta.put("Notas Vinculadas", qtdNotas + " documento(s)");
        if (pes.isCliente()) meta.put("Total em Compras na Loja", FormatadorSeguroUI.formatarMoeda(totalVendido));
        if (pes.isFornecedor()) meta.put("Total Fornecido p/ Loja", FormatadorSeguroUI.formatarMoeda(totalComprado));
        return meta;
    }

    // ==================== 4.6 FORMAS DE PAGAMENTO (CONTABILIDADE) ====================

    @SuppressWarnings("unchecked")
    private GridPane construirSubAbaFormasPagamento() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setPadding(new Insets(10, 0, 4, 0));

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(52);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(48);
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        VBox esq = new VBox(8);
        esq.setPadding(new Insets(12));
        esq.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        FormatadorSeguroUI.configurarTabelaCorporativa(tabelaFormasPag, 400);
        TableColumn<FormaPagamento, String> fCod = new TableColumn<>("Código");
        fCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        fCod.setPrefWidth(100);
        TableColumn<FormaPagamento, String> fNome = new TableColumn<>("Nome");
        fNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        fNome.setPrefWidth(140);
        TableColumn<FormaPagamento, String> fTaxa = new TableColumn<>("Taxa %");
        fTaxa.setCellValueFactory(c -> new SimpleStringProperty(
                FormatadorSeguroUI.formatarDecimal(c.getValue().getTaxaOperadoraPercentual().doubleValue(), 2) + "%"));
        fTaxa.setPrefWidth(65);
        TableColumn<FormaPagamento, String> fParc = new TableColumn<>("Parcelas");
        fParc.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getMaxParcelas() + "x (" + c.getValue().getMaxParcelasSemJuros() + "x s/j)"));
        fParc.setPrefWidth(85);
        TableColumn<FormaPagamento, String> fAtiva = new TableColumn<>("Status");
        fAtiva.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAtivo() ? "ATIVA" : "INATIVA"));
        fAtiva.setPrefWidth(65);

        tabelaFormasPag.getColumns().setAll(fCod, fNome, fTaxa, fParc, fAtiva);
        tabelaFormasPag.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) preencherFormularioFormaPag(sel);
        });
        tabelaFormasPag.setRowFactory(tv -> {
            TableRow<FormaPagamento> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 2) abrirCardFormaPagamento(row.getItem());
            });
            return row;
        });

        VBox.setVgrow(tabelaFormasPag, Priority.ALWAYS);
        esq.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("🏦 Meios de Pagamento Parametrizados (Duplo clique abre Card)", 13, true, "#0F172A"),
                tabelaFormasPag);

        VBox dirInner = new VBox(8);
        dirInner.setPadding(new Insets(12));
        GridPane gf = new GridPane();
        gf.setHgap(8);
        gf.setVgap(6);
        ColumnConstraints fc1 = new ColumnConstraints();
        fc1.setPercentWidth(50);
        ColumnConstraints fc2 = new ColumnConstraints();
        fc2.setPercentWidth(50);
        gf.getColumnConstraints().addAll(fc1, fc2);

        gf.add(FormatadorSeguroUI.criarLabel("Código Único:", 11, true, "#475569"), 0, 0);
        gf.add(FormatadorSeguroUI.criarLabel("Nome de Exibição:", 11, true, "#475569"), 1, 0);
        gf.add(txtFpCodigo, 0, 1);
        gf.add(txtFpNome, 1, 1);

        gf.add(FormatadorSeguroUI.criarLabel("Descrição:", 11, true, "#475569"), 0, 2);
        gf.add(FormatadorSeguroUI.criarLabel("Desc. à Vista (%):", 11, true, "#475569"), 1, 2);
        gf.add(txtFpDesc, 0, 3);
        gf.add(txtFpDescVista, 1, 3);

        gf.add(FormatadorSeguroUI.criarLabel("Taxa Operadora (%):", 11, true, "#475569"), 0, 4);
        gf.add(FormatadorSeguroUI.criarLabel("Tarifa Fixa Op. (R$):", 11, true, "#475569"), 1, 4);
        gf.add(txtFpTaxaMdr, 0, 5);
        gf.add(txtFpTarifaFixa, 1, 5);

        gf.add(FormatadorSeguroUI.criarLabel("Máx. Parcelas:", 11, true, "#475569"), 0, 6);
        gf.add(FormatadorSeguroUI.criarLabel("Parcelas Sem Juros:", 11, true, "#475569"), 1, 6);
        gf.add(txtFpMaxParc, 0, 7);
        gf.add(txtFpMaxSemJuros, 1, 7);

        gf.add(FormatadorSeguroUI.criarLabel("Taxa Juros Mensal (%):", 11, true, "#475569"), 0, 8);
        gf.add(FormatadorSeguroUI.criarLabel("Modalidades Permitidas:", 11, true, "#475569"), 1, 8);
        gf.add(txtFpJurosMensal, 0, 9);
        chkFpVista.setSelected(true);
        gf.add(new FlowPane(8, 4, chkFpVista, chkFpPrazo, chkFpParcelado), 1, 9);

        Button btnFpAdd = FormatadorSeguroUI.criarBotaoCartoon("Cadastrar Nova Forma", "#047857", "#065F46", "#FFFFFF");
        btnFpAdd.setOnAction(e -> cadastrarFormaPagamento());
        Button btnFpRegras = FormatadorSeguroUI.criarBotaoCartoon("Atualizar Taxas/Regras", "#0F172A", "#0F172A", "#FFFFFF");
        btnFpRegras.setOnAction(e -> atualizarRegrasFormaPagamento());
        Button btnFpStatus = FormatadorSeguroUI.criarBotaoCartoon("Ativar/Desativar", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnFpStatus.setOnAction(e -> alternarStatusFormaPagamento());
        Button btnFpExc = FormatadorSeguroUI.criarBotaoCartoon("Excluir", "#FEE2E2", "#FCA5A5", "#991B1B");
        btnFpExc.setOnAction(e -> excluirFormaPagamento());

        dirInner.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("Cadastro & Regras Contábeis da Forma", 13, true, "#0F172A"),
                gf,
                new FlowPane(6, 6, btnFpAdd, btnFpRegras, btnFpStatus, btnFpExc),
                lblStatusFp);

        VBox dir = new VBox(FormatadorSeguroUI.envolverScrollSuave(dirInner));
        dir.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        VBox.setVgrow(dir.getChildren().get(0), Priority.ALWAYS);

        g.add(esq, 0, 0);
        g.add(dir, 1, 0);
        GridPane.setVgrow(esq, Priority.ALWAYS);
        GridPane.setVgrow(dir, Priority.ALWAYS);
        return g;
    }

    private void abrirCardFormaPagamento(FormaPagamento fp) {
        if (fp == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Código", fp.getCodigo());
        meta.put("Nome", fp.getNome());
        meta.put("Status", fp.isAtivo() ? "ATIVA" : "INATIVA");
        meta.put("Taxa Operadora", FormatadorSeguroUI.formatarDecimal(fp.getTaxaOperadoraPercentual().doubleValue(), 2) + "%");
        meta.put("Tarifa Fixa", FormatadorSeguroUI.formatarMoeda(fp.getTarifaFixaOperadora()));
        meta.put("Desconto à Vista Padrão", FormatadorSeguroUI.formatarDecimal(fp.getPercentualDescontoAVistaPadrao().doubleValue(), 2) + "%");
        meta.put("Parcelamento Máximo", fp.getMaxParcelas() + "x (" + fp.getMaxParcelasSemJuros() + "x sem juros)");
        meta.put("Juros Mensal", FormatadorSeguroUI.formatarDecimal(fp.getTaxaJurosMensalPadrao().doubleValue(), 2) + "%");
        meta.put("Descrição Contábil", FormatadorSeguroUI.tratarParaExibicao(fp.getDescricao(), "Regra padrão"));

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🏦", fp.getNome() + " (" + fp.getCodigo() + ")",
                fp.isAtivo() ? "Forma Ativa" : "Inativa", fp.isAtivo() ? "#047857" : "#991B1B",
                meta, null, null, null);
    }

    // ==================== CARREGAMENTO SOB DEMANDA & AÇÕES ====================

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
                List<Nota> notas = ponte.listarNotas();
                List<Pessoa> pessoas = ponte.listarTodasPessoas();
                List<Produto> produtos = ponte.listarProdutos();
                List<FormaPagamento> fps = ponte.listarTodasFormasPagamento();
                Long idNotaFiltro = extrairIdLong(txtFiltroNotaBacen.getText());
                List<Object[]> lucro = ponte.consultarRelatorioLucroBacen(idNotaFiltro);
                List<Object[]> perdas = buscarPerdasTodosFornecedores(pessoas, chkApenasPosVendaRel.isSelected());

                Platform.runLater(() -> {
                    this.notasCache = notas;
                    this.pessoasCache = pessoas;
                    this.produtosCache = produtos;
                    this.formasPagCache = fps;
                    this.lucroBacenCache = lucro;
                    this.perdasFornCache = perdas;

                    atualizarKpis();
                    tabelaLucroBacen.setItems(FXCollections.observableArrayList(lucroBacenCache));
                    tabelaPerdasForn.setItems(FXCollections.observableArrayList(perdasFornCache));
                    popularCombosFinanceiro();
                    filtrarTabelaNotas();
                    atualizarTabelaTitulos();
                    filtrarTabelaPessoas();
                    tabelaFormasPag.setItems(FXCollections.observableArrayList(formasPagCache));
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
        redesenharDashboardSaudeFinanceira();
    }

    private List<Object[]> buscarPerdasTodosFornecedores(List<Pessoa> pessoas, boolean apenasPosVenda) {
        List<Object[]> consolidado = new ArrayList<>();
        for (Pessoa p : pessoas) {
            if (p.isFornecedor()) {
                consolidado.addAll(ponte.consultarRelatorioPerdasFornecedor(p, null, apenasPosVenda));
            }
        }
        return consolidado;
    }

    private void recarregarRelatoriosBacenAsync() {
        Long idNota = extrairIdLong(txtFiltroNotaBacen.getText());
        Thread.ofVirtual().start(() -> {
            List<Object[]> lucro = ponte.consultarRelatorioLucroBacen(idNota);
            Platform.runLater(() -> {
                this.lucroBacenCache = lucro;
                tabelaLucroBacen.setItems(FXCollections.observableArrayList(lucro));
                atualizarKpis();
            });
        });
    }

    private void recarregarPerdasFornecedorAsync() {
        String sel = cbFornPerdaRel.getValue();
        boolean flag = chkApenasPosVendaRel.isSelected();
        Thread.ofVirtual().start(() -> {
            List<Object[]> linhas;
            Long idForn = extrairIdPrefixado(sel);
            if (idForn != null) {
                Pessoa f = pessoasCache.stream().filter(p -> p.getId().equals(idForn)).findFirst().orElse(null);
                linhas = f != null ? ponte.consultarRelatorioPerdasFornecedor(f, null, flag) : List.of();
            } else {
                linhas = buscarPerdasTodosFornecedores(pessoasCache, flag);
            }
            Platform.runLater(() -> {
                this.perdasFornCache = linhas;
                tabelaPerdasForn.setItems(FXCollections.observableArrayList(linhas));
                redesenharDashboardSaudeFinanceira();
            });
        });
    }

    private void atualizarKpis() {
        BigDecimal receita = BigDecimal.ZERO, cmv = BigDecimal.ZERO, lucro = BigDecimal.ZERO;
        for (Object[] r : lucroBacenCache) {
            cmv = cmv.add(toBd(r, 5));
            receita = receita.add(toBd(r, 6));
            lucro = lucro.add(toBd(r, 8));
        }
        BigDecimal aberto = BigDecimal.ZERO;
        for (Nota n : notasCache) {
            if (n.getPagamentos() == null) continue;
            for (NotaPagamento np : n.getPagamentos()) {
                if (ponte.existeParcelaOuTituloEmAberto(np)) {
                    if (np instanceof PagamentoParcelado pp) aberto = aberto.add(pp.getSaldoDevedorParcelas());
                    else aberto = aberto.add(np.calcularValorFinal());
                }
            }
        }
        lblKpiReceita.setText(FormatadorSeguroUI.formatarMoeda(receita));
        lblKpiCmv.setText(FormatadorSeguroUI.formatarMoeda(cmv));
        lblKpiLucroLiq.setText(FormatadorSeguroUI.formatarMoeda(lucro));
        lblKpiAberto.setText(FormatadorSeguroUI.formatarMoeda(aberto));
        redesenharDashboardSaudeFinanceira();
    }

    private void redesenharDashboardSaudeFinanceira() {
        double receita = 0, cmv = 0, lucro = 0;
        for (Object[] r : lucroBacenCache) {
            cmv += toBd(r, 5).doubleValue();
            receita += toBd(r, 6).doubleValue();
            lucro += toBd(r, 8).doubleValue();
        }
        double totalPerdas = 0, creditosAbativeis = 0;
        for (Object[] p : perdasFornCache) {
            double c = toBd(p, 7).doubleValue();
            totalPerdas += c;
            boolean ab = p.length > 11 && (p[11] instanceof Boolean b ? b : p[11] instanceof Number n && n.intValue() == 1);
            if (ab) creditosAbativeis += c;
        }
        double abertoReceber = 0, abertoPagar = 0;
        for (Nota n : notasCache) {
            if (n.getPagamentos() == null) continue;
            for (NotaPagamento np : n.getPagamentos()) {
                if (ponte.existeParcelaOuTituloEmAberto(np)) {
                    double v = (np instanceof PagamentoParcelado pp) ? pp.getSaldoDevedorParcelas().doubleValue()
                            : np.calcularValorFinal().doubleValue();
                    if (n instanceof NotaCompra) abertoPagar += v;
                    else abertoReceber += v;
                }
            }
        }

        double margemPct = receita > 0 ? (lucro / receita) * 100.0 : 0.0;
        double taxaPerdaPct = receita > 0 ? (totalPerdas / receita) * 100.0 : 0.0;
        double pressaoAberto = (receita + 1.0) > 0 ? Math.min(100.0, (abertoPagar / Math.max(100.0, receita)) * 100.0) : 0.0;
        int score = receita <= 0 && notasCache.isEmpty() ? 70
                : (int) Math.max(5, Math.min(100, Math.round(62.0 + Math.min(35.0, margemPct * 1.1) - (taxaPerdaPct * 1.6) - (pressaoAberto * 0.25))));

        String corScore = score >= 75 ? "#047857" : (score >= 50 ? "#B45309" : "#DC2626");
        String diagScore = score >= 75 ? "SAUDÁVEL" : (score >= 50 ? "ATENÇÃO" : "CRÍTICO");
        lblScoreSaudeFin.setText(score + " / 100 (" + diagScore + ")");
        lblScoreSaudeFin.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + corScore + ";");
        lblSaudeDetalhesFin.setText(String.format(FormatadorSeguroUI.LOCALE_BR,
                "Margem Líquida: %.1f%% • A Receber: %s • A Pagar: %s • Perdas Totais: %s (Abatível c/ Fornecedor: %s)",
                margemPct,
                FormatadorSeguroUI.formatarMoeda(BigDecimal.valueOf(abertoReceber)),
                FormatadorSeguroUI.formatarMoeda(BigDecimal.valueOf(abertoPagar)),
                FormatadorSeguroUI.formatarMoeda(BigDecimal.valueOf(totalPerdas)),
                FormatadorSeguroUI.formatarMoeda(BigDecimal.valueOf(creditosAbativeis))));

        double w = canvasSaudeFin.getWidth();
        double h = canvasSaudeFin.getHeight();
        if (w < 80 || h < 20) return;

        GraphicsContext g = canvasSaudeFin.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        double base = Math.max(1.0, cmv + Math.max(0.0, lucro) + totalPerdas);
        double wCmv = (cmv / base) * (w - 20);
        double wLuc = (Math.max(0.0, lucro) / base) * (w - 20);
        double wPer = Math.max(0.0, (w - 20) - wCmv - wLuc);

        double y = 14, barH = 26;
        g.setFill(Color.web("#E2E8F0"));
        g.fillRoundRect(10, y, w - 20, barH, 10, 10);

        double curX = 10;
        if (wCmv > 1) {
            g.setFill(Color.web("#334155"));
            g.fillRoundRect(curX, y, wCmv, barH, 8, 8);
            curX += wCmv;
        }
        if (wLuc > 1) {
            g.setFill(Color.web("#10B981"));
            g.fillRect(curX, y, wLuc, barH);
            curX += wLuc;
        }
        if (wPer > 1) {
            g.setFill(Color.web("#EF4444"));
            g.fillRoundRect(curX, y, wPer, barH, 8, 8);
        }

        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        g.setFill(Color.web("#334155"));
        g.fillText(String.format("■ CMV %.0f%%", (cmv / base) * 100), 12, 58);
        g.setFill(Color.web("#047857"));
        g.fillText(String.format("■ Lucro Líq. %.0f%%", (Math.max(0.0, lucro) / base) * 100), 140, 58);
        g.setFill(Color.web("#DC2626"));
        g.fillText(String.format("■ Perdas %.1f%%", (totalPerdas / base) * 100), 295, 58);
        g.setFill(Color.web("#0284C7"));
        g.fillText("Fluxo Líq.: " + FormatadorSeguroUI.formatarMoeda(BigDecimal.valueOf(abertoReceber - abertoPagar)),
                Math.max(430, w - 210), 58);
    }

    private void popularCombosFinanceiro() {
        String selFornRel = cbFornPerdaRel.getValue();
        cbFornPerdaRel.getItems().clear();
        cbFornPerdaRel.getItems().add("Todos os Fornecedores");
        cbCompraFornecedor.getItems().clear();
        for (Pessoa p : pessoasCache) {
            if (p.isFornecedor()) {
                String s = "#" + p.getId() + " - " + p.getNome();
                cbFornPerdaRel.getItems().add(s);
                cbCompraFornecedor.getItems().add(s);
            }
        }
        if (selFornRel != null && cbFornPerdaRel.getItems().contains(selFornRel)) cbFornPerdaRel.setValue(selFornRel);
        else cbFornPerdaRel.getSelectionModel().selectFirst();
        if (!cbCompraFornecedor.getItems().isEmpty()) cbCompraFornecedor.getSelectionModel().selectFirst();

        cbCompraProduto.getItems().clear();
        for (Produto pr : produtosCache) {
            cbCompraProduto.getItems().add("#" + pr.getId() + " - " + FormatadorSeguroUI.limparNomeProduto(pr.getNome()));
        }
        if (!cbCompraProduto.getItems().isEmpty()) cbCompraProduto.getSelectionModel().selectFirst();

        cbCompraFormaPag.getItems().clear();
        for (FormaPagamento fp : formasPagCache) {
            if (fp.isAtivo()) cbCompraFormaPag.getItems().add(fp.getCodigo());
        }
        if (cbCompraFormaPag.getItems().isEmpty()) cbCompraFormaPag.getItems().add("BOLETO");
        cbCompraFormaPag.getSelectionModel().selectFirst();
    }

    private void filtrarTabelaNotas() {
        String termo = txtBuscaNotas.getText() != null ? txtBuscaNotas.getText().trim().toLowerCase(Locale.ROOT) : "";
        String tipo = cbFiltroTipoNota.getValue() != null ? cbFiltroTipoNota.getValue() : "Todos os Tipos";
        String ent = cbFiltroEntidadeNota.getValue() != null ? cbFiltroEntidadeNota.getValue() : "Todas Entidades";
        String st = cbFiltroStatusNota.getValue() != null ? cbFiltroStatusNota.getValue() : "Todos Status";

        List<Nota> filtradas = new ArrayList<>();
        for (Nota n : notasCache) {
            boolean matchTipo = switch (tipo) {
                case "COMPRA" -> n instanceof NotaCompra;
                case "VENDA" -> n instanceof NotaVenda;
                case "DEVOLUCAO" -> n instanceof NotaDevolucao;
                case "PERDA" -> n instanceof NotaPerda;
                default -> true;
            };
            if (!matchTipo) continue;

            Pessoa p = n.getPessoa();
            boolean matchEnt = switch (ent) {
                case "Clientes" -> p != null && p.isCliente() && (n instanceof NotaVenda || n instanceof NotaDevolucao);
                case "Fornecedores" -> p != null && p.isFornecedor() && (n instanceof NotaCompra || n instanceof NotaPerda);
                case "Internas / Loja" -> p == null || n instanceof NotaPerda;
                default -> true;
            };
            if (!matchEnt) continue;

            String statNota = classificarStatusNota(n);
            boolean matchSt = switch (st) {
                case "Quitada / Paga" -> "Quitada".equals(statNota);
                case "Em Aberto / Pendente" -> !"Quitada".equals(statNota);
                default -> true;
            };
            if (!matchSt) continue;

            if (!termo.isEmpty()) {
                String txt = ("#" + n.getId() + " " + (p != null ? p.getNome() : "") + " " + FormatadorSeguroUI.tratarParaExibicao(n.getDescricao(), ""))
                        .toLowerCase(Locale.ROOT);
                if (!txt.contains(termo)) continue;
            }
            filtradas.add(n);
        }
        tabelaTodasNotas.setItems(FXCollections.observableArrayList(filtradas));
    }

    private void inspecionarCausasDaNota(Nota nota) {
        if (nota == null) return;
        lblDetalheFatorNota.setText("Consultando fatores climáticos, nível do rio e eventos vinculados à Nota #" + nota.getId() + "...");
        Thread.ofVirtual().start(() -> {
            Map<String, List<Map<String, Object>>> mapa = ponte.consultarCausasExternasNota(nota.getId(), null);
            List<Map<String, Object>> fatores = mapa.getOrDefault("fatores_ativos_e_efeito_estatistico", List.of());
            List<Map<String, Object>> eventos = mapa.getOrDefault("eventos_reais_do_snapshot", List.of());

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("Nota Inspecionada", "#" + nota.getId() + " (" + traduzirTipoNota(nota) + ")");
            meta.put("Eventos Regionais no Snapshot", eventos.size() + " evento(s) ativo(s)");
            for (int i = 0; i < Math.min(4, fatores.size()); i++) {
                Map<String, Object> f = fatores.get(i);
                meta.put("Fator " + (i + 1) + ": " + FormatadorSeguroUI.tratarParaExibicao(f.getOrDefault("fator", f.getOrDefault("nome_fator", "Sazonal")), "Sazonal"),
                        FormatadorSeguroUI.tratarParaExibicao(f.getOrDefault("efeito", f.getOrDefault("lift_percentual", "Observado")), "Observado"));
            }
            String txtResumo = fatores.isEmpty()
                    ? "Nota #" + nota.getId() + " sem variações climáticas atípicas registradas."
                    : "Nota #" + nota.getId() + ": " + fatores.size() + " fator(es) climático(s) e " + eventos.size() + " evento(s) mapeado(s).";

            Platform.runLater(() -> {
                lblDetalheFatorNota.setText(txtResumo);
                DialogoCorporativoModal.exibirCardDetalhes(
                        this, "🌦️", "Causas Externas da Nota #" + nota.getId(),
                        traduzirTipoNota(nota), "#1D4ED8", meta, null, null, null, null,
                        () -> conectorIA.explicarComContextoOrigem(this, "Financeiro — Causas da Nota",
                                "Nota #" + nota.getId() + " (" + traduzirTipoNota(nota) + ")", meta,
                                "Explique como clima, nível do rio e eventos influenciaram especificamente a Nota #" + nota.getId() + "."));
            });
        });
    }

    private void abrirRastreamentoCompletoNotaAsync(Long idNota) {
        Thread.ofVirtual().start(() -> {
            RastreamentoCadeiaNota cadeia = ponte.consultarRastreamentoCompletoLoteENota(null, idNota);
            Platform.runLater(() -> {
                Nota principal = cadeia != null ? cadeia.notaPrincipal() : ponte.buscarNotaCompletaPorId(idNota);
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("Nota Rastreada", "#" + idNota);
                if (principal != null) {
                    meta.put("Tipo", traduzirTipoNota(principal));
                    meta.put("Entidade", principal.getPessoa() != null ? principal.getPessoa().getNome() : "Operação Interna");
                    meta.put("Emissão", FormatadorSeguroUI.formatarDataHoraAmigavel(principal.getDataGerada()));
                    meta.put("Valor Total", FormatadorSeguroUI.formatarMoeda(principal.getValorTotal()));
                    meta.put("Observação / Motivo", FormatadorSeguroUI.tratarParaExibicao(principal.getDescricao(), "Regular"));
                }
                if (cadeia != null && cadeia.notaEntradaOrigem() != null) {
                    meta.put("Entrada de Origem", "Compra #" + cadeia.notaEntradaOrigem().getId()
                            + (cadeia.notaEntradaOrigem().getPessoa() != null
                            ? " (" + cadeia.notaEntradaOrigem().getPessoa().getNome() + ")" : ""));
                }

                VBox conteudo = new VBox(8);
                if (cadeia != null && !cadeia.itensDaEntrada().isEmpty()) {
                    VBox bxEnt = new VBox(4);
                    bxEnt.setPadding(new Insets(8));
                    bxEnt.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
                    bxEnt.getChildren().add(FormatadorSeguroUI.criarLabel("📦 Itens Originais da Entrada Vinculada", 12, true, "#0F172A"));
                    for (Item<? extends Produto> it : cadeia.itensDaEntrada()) {
                        String np = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
                        bxEnt.getChildren().add(FormatadorSeguroUI.criarLabel(String.format("• Lote #%d • %s • Qtd %s • Saldo %s • %s",
                                it.getId(), np,
                                FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), ""),
                                FormatadorSeguroUI.formatarQuantidade(it.getQuantidadeDisponivel(), ""),
                                FormatadorSeguroUI.formatarMoeda(it.getValor())), 11, false, "#334155"));
                    }
                    conteudo.getChildren().add(bxEnt);
                }

                List<Nota> dossie = new ArrayList<>();
                if (principal != null) dossie.add(principal);
                if (cadeia != null) {
                    if (cadeia.notaEntradaOrigem() != null && dossie.stream().noneMatch(n -> n.getId().equals(cadeia.notaEntradaOrigem().getId()))) {
                        dossie.add(cadeia.notaEntradaOrigem());
                    }
                    cadeia.vendasRelacionadas().stream().filter(n -> dossie.stream().noneMatch(d -> d.getId().equals(n.getId()))).forEach(dossie::add);
                    cadeia.devolucoesRelacionadas().stream().filter(n -> dossie.stream().noneMatch(d -> d.getId().equals(n.getId()))).forEach(dossie::add);
                    cadeia.perdasRelacionadas().stream().filter(n -> dossie.stream().noneMatch(d -> d.getId().equals(n.getId()))).forEach(dossie::add);
                }

                VBox bxRel = new VBox(4);
                bxRel.setPadding(new Insets(8));
                bxRel.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
                bxRel.getChildren().add(FormatadorSeguroUI.criarLabel("🔗 Cadeia Completa de Documentos (" + dossie.size() + " Nota(s))", 12, true, "#1E40AF"));
                for (Nota dn : dossie) {
                    Button bAbrir = FormatadorSeguroUI.criarBotaoCartoon("📄 " + traduzirTipoNota(dn) + " #" + dn.getId(), "#FFFFFF", "#CBD5E1", "#0F172A");
                    bAbrir.setOnAction(ev -> DialogoCorporativoModal.exibirComprovanteNotaImprimivel(this, dn, traduzirTipoNota(dn) + " #" + dn.getId()));
                    Label lDesc = FormatadorSeguroUI.criarLabel(String.format("%s • %s",
                            FormatadorSeguroUI.formatarDataHoraAmigavel(dn.getDataGerada()),
                            FormatadorSeguroUI.formatarMoeda(dn.getValorTotal())), 11, false, "#334155");
                    HBox h = new HBox(8, bAbrir, lDesc);
                    h.setAlignment(Pos.CENTER_LEFT);
                    bxRel.getChildren().add(h);
                }
                conteudo.getChildren().add(bxRel);

                Runnable imprimirDossie = dossie.isEmpty() ? null : () ->
                        DialogoCorporativoModal.exibirComprovanteLoteNotasImprimivel(
                                this, dossie, "Dossiê Completo — Nota #" + idNota);

                DialogoCorporativoModal.exibirCardDetalhes(
                        this, "🧾", "Rastreamento 360º — Nota #" + idNota,
                        dossie.size() + " nota(s) vinculada(s)", "#1D4ED8",
                        meta, conteudo, imprimirDossie, null, null,
                        () -> conectorIA.explicarComContextoOrigem(
                                this, "Financeiro — Rastreamento de Notas",
                                "Cadeia da Nota #" + idNota, meta,
                                "Explique todo o fluxo rastreado desta Nota #" + idNota + ", sua origem, itens e liquidação financeira."));
            });
        });
    }

    private void atualizarTabelaTitulos() {
        String filtro = cbFiltroTitulo.getValue() != null ? cbFiltroTitulo.getValue() : "Em Aberto / Vencidos";
        List<LinhaTituloFinanceiro> linhas = new ArrayList<>();
        LocalDate hoje = LocalDate.now();

        for (Nota n : notasCache) {
            if (n.getPagamentos() == null) continue;
            String direcao = (n instanceof NotaCompra) ? "A PAGAR" : "A RECEBER";
            String pes = n.getPessoa() != null ? n.getPessoa().getNome() : "Operação Balcão";

            for (NotaPagamento np : n.getPagamentos()) {
                String codForma = np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "Pagamento";
                if (np instanceof PagamentoParcelado pp && pp.getParcelas() != null && !pp.getParcelas().isEmpty()) {
                    for (ParcelaPagamento par : pp.getParcelas()) {
                        String st = par.getDataQuitacao() != null ? "QUITADO"
                                : (par.isVencido(hoje) ? "VENCIDO" : "EM ABERTO");
                        linhas.add(new LinhaTituloFinanceiro(
                                n, np, par, direcao, pes,
                                String.format("%s (%d/%d)", codForma, par.getNumeroParcela(), pp.getParcelas().size()),
                                par.getDataVencimento(), par.getValorTotalParcela(), st));
                    }
                } else if (np instanceof PagamentoAPrazo ap) {
                    String st = ap.isLiquidado() ? "QUITADO" : (ap.isVencido(hoje) ? "VENCIDO" : "EM ABERTO");
                    linhas.add(new LinhaTituloFinanceiro(
                            n, np, null, direcao, pes, codForma + " (Prazo)",
                            ap.getDataVencimento(), ap.calcularValorFinal(), st));
                } else {
                    String st = np.isLiquidado() ? "QUITADO" : "EM ABERTO";
                    linhas.add(new LinhaTituloFinanceiro(
                            n, np, null, direcao, pes, codForma + " (À Vista)",
                            null, np.calcularValorFinal(), st));
                }
            }
        }

        List<LinhaTituloFinanceiro> filtradas = linhas.stream().filter(l -> switch (filtro) {
            case "Em Aberto / Vencidos" -> !"QUITADO".equals(l.status());
            case "Apenas A Receber (Clientes)" -> "A RECEBER".equals(l.direcao());
            case "Apenas A Pagar (Fornecedores)" -> "A PAGAR".equals(l.direcao());
            case "Quitados" -> "QUITADO".equals(l.status());
            default -> true;
        }).toList();

        tabelaTitulos.setItems(FXCollections.observableArrayList(filtradas));
    }

    private void quitarTituloOuParcela(LinhaTituloFinanceiro row) {
        abrirModalQuitacaoDetalhada(row);
    }

    private void abrirModalQuitacaoDetalhada(LinhaTituloFinanceiro row) {
        if (row == null) return;
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("✔ Quitar Título / Parcelas — Nota #" + row.nota().getId());
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());

        VBox header = new VBox(3);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #0F172A;");
        header.getChildren().addAll(
                FormatadorSeguroUI.criarLabel("💳 Quitação Detalhada — Nota #" + row.nota().getId() + " (" + row.direcao() + ")", 15, true, "#FFFFFF"),
                FormatadorSeguroUI.criarLabel(row.pessoaNome() + " • " + row.formaDescricao()
                        + " • Valor a Quitar: " + FormatadorSeguroUI.formatarMoeda(row.valor()), 12, false, "#CBD5E1")
        );

        VBox boxParcelasInfo = new VBox(4);
        boxParcelasInfo.setPadding(new Insets(8));
        boxParcelasInfo.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));
        if (row.pagamento() instanceof PagamentoParcelado pp && pp.getParcelas() != null && !pp.getParcelas().isEmpty()) {
            boxParcelasInfo.getChildren().add(FormatadorSeguroUI.criarLabel("📅 Parcelas deste Financiamento:", 11, true, "#0F172A"));
            for (ParcelaPagamento p : pp.getParcelas()) {
                boolean esta = row.parcelaOpcional() != null && p.getNumeroParcela() == row.parcelaOpcional().getNumeroParcela();
                String st = p.getDataQuitacao() != null ? "PAGA em " + FormatadorSeguroUI.formatarDataAmigavel(p.getDataQuitacao().toLocalDate())
                        : "Venc. " + FormatadorSeguroUI.formatarDataAmigavel(p.getDataVencimento());
                boxParcelasInfo.getChildren().add(FormatadorSeguroUI.criarLabel(String.format("%sParcela %d/%d • %s • %s",
                        esta ? "▶ [SELECIONADA] " : "• ", p.getNumeroParcela(), pp.getParcelas().size(),
                        FormatadorSeguroUI.formatarMoeda(p.getValorTotalParcela()), st),
                        11, esta, esta ? "#1D4ED8" : "#475569"));
            }
        } else {
            boxParcelasInfo.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Título: " + row.formaDescricao() + " • Vencimento: "
                            + (row.vencimento() != null ? FormatadorSeguroUI.formatarDataAmigavel(row.vencimento()) : "Imediato"),
                    11, false, "#334155"));
        }

        List<PagamentoQuitacaoInput> pagamentosLancados = new ArrayList<>();
        VBox boxPgs = new VBox(6);

        ComboBox<String> cbFormaQ = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        for (FormaPagamento fp : formasPagCache) {
            if (fp.isAtivo()) cbFormaQ.getItems().add(fp.getCodigo() + " - " + fp.getNome());
        }
        if (cbFormaQ.getItems().isEmpty()) cbFormaQ.getItems().addAll("PIX - PIX", "DIN - Dinheiro", "TED - Transferência");
        cbFormaQ.getSelectionModel().selectFirst();

        ComboBox<String> cbModQ = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
        cbModQ.getItems().addAll("À Vista", "Crédito / Cartão", "Boleto / A Prazo");
        cbModQ.getSelectionModel().selectFirst();

        TextField txtValorQ = FormatadorSeguroUI.criarCampoTextoCartoon("Valor", "#CBD5E1", "#0F172A");
        txtValorQ.setText(row.valor().toPlainString().replace(".", ","));
        DatePicker dpDataQ = new DatePicker(LocalDate.now());
        Label lblSomaQ = FormatadorSeguroUI.criarLabel("Adicione 1 ou mais pagamentos (múltiplas formas e datas):", 11, true, "#1E40AF");
        Label lblErroQ = FormatadorSeguroUI.criarLabel("", 11, true, "#DC2626");

        Runnable redenderPgs = () -> {
            boxPgs.getChildren().clear();
            BigDecimal soma = BigDecimal.ZERO;
            for (int i = 0; i < pagamentosLancados.size(); i++) {
                int idx = i;
                PagamentoQuitacaoInput pq = pagamentosLancados.get(i);
                soma = soma.add(pq.valor());
                Label l = FormatadorSeguroUI.criarLabel(String.format("%s (%s) • Data: %s • %s",
                        pq.codigoForma(), pq.modalidade(),
                        FormatadorSeguroUI.formatarDataAmigavel(pq.dataPagamento()),
                        FormatadorSeguroUI.formatarMoeda(pq.valor())), 11, true, "#0F172A");
                HBox.setHgrow(l, Priority.ALWAYS);
                Button bDel = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
                bDel.setOnAction(ev -> {
                    pagamentosLancados.remove(idx);
                    boxPgs.getChildren().remove(idx);
                });
                HBox rP = new HBox(8, l, bDel);
                rP.setAlignment(Pos.CENTER_LEFT);
                rP.setPadding(new Insets(5, 8, 5, 8));
                rP.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#ECFDF5", "#A7F3D0"));
                boxPgs.getChildren().add(rP);
            }
            BigDecimal falta = row.valor().subtract(soma).max(BigDecimal.ZERO);
            lblSomaQ.setText(String.format("Lançado: %s / Total Título: %s (Restante: %s)",
                    FormatadorSeguroUI.formatarMoeda(soma), FormatadorSeguroUI.formatarMoeda(row.valor()),
                    FormatadorSeguroUI.formatarMoeda(falta)));
        };

        Button btnAddPq = FormatadorSeguroUI.criarBotaoCartoon("+ Adicionar Pagamento", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnAddPq.setOnAction(e -> {
            String fTxt = cbFormaQ.getValue() != null ? cbFormaQ.getValue().split(" - ")[0].trim() : "PIX";
            BigDecimal valInf = parseDecimalBr(txtValorQ.getText(), row.valor());
            if (valInf.compareTo(BigDecimal.ZERO) <= 0) valInf = row.valor();
            pagamentosLancados.add(new PagamentoQuitacaoInput(fTxt, cbModQ.getValue(), valInf,
                    dpDataQ.getValue() != null ? dpDataQ.getValue() : LocalDate.now()));
            redenderPgs.run();
            BigDecimal soma = pagamentosLancados.stream().map(PagamentoQuitacaoInput::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            txtValorQ.setText(row.valor().subtract(soma).max(BigDecimal.ZERO).toPlainString().replace(".", ","));
        });

        GridPane gForm = new GridPane();
        gForm.setHgap(8);
        gForm.setVgap(6);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            gForm.getColumnConstraints().add(cc);
        }
        gForm.add(FormatadorSeguroUI.criarLabel("Forma Pagamento:", 11, true, "#475569"), 0, 0);
        gForm.add(FormatadorSeguroUI.criarLabel("Modalidade:", 11, true, "#475569"), 1, 0);
        gForm.add(FormatadorSeguroUI.criarLabel("Valor Pago (R$):", 11, true, "#475569"), 2, 0);
        gForm.add(FormatadorSeguroUI.criarLabel("Data do Pagamento:", 11, true, "#475569"), 3, 0);
        gForm.add(cbFormaQ, 0, 1);
        gForm.add(cbModQ, 1, 1);
        gForm.add(txtValorQ, 2, 1);
        gForm.add(dpDataQ, 3, 1);

        ScrollPane spPgs = FormatadorSeguroUI.envolverScrollSuave(boxPgs);
        spPgs.setPrefHeight(110);

        Button btnConfirmar = FormatadorSeguroUI.criarBotaoCartoon("✔ Confirmar Quitação", "#047857", "#065F46", "#FFFFFF");
        btnConfirmar.setOnAction(e -> {
            List<PagamentoQuitacaoInput> listaFinal = new ArrayList<>(pagamentosLancados);
            if (listaFinal.isEmpty()) {
                String fTxt = cbFormaQ.getValue() != null ? cbFormaQ.getValue().split(" - ")[0].trim() : "PIX";
                BigDecimal valInf = parseDecimalBr(txtValorQ.getText(), row.valor());
                listaFinal.add(new PagamentoQuitacaoInput(fTxt, cbModQ.getValue(), valInf,
                        dpDataQ.getValue() != null ? dpDataQ.getValue() : LocalDate.now()));
            }
            btnConfirmar.setDisable(true);
            ponte.quitarTituloComMultiplosPagamentosAsync(
                    row.nota().getId(),
                    row.pagamento().getId(),
                    row.parcelaOpcional() != null ? row.parcelaOpcional().getNumeroParcela() : null,
                    listaFinal,
                    notaAtualizada -> {
                        dlg.close();
                        lblStatusTitulo.setText("✔ Título da Nota #" + row.nota().getId()
                                + " quitado em " + listaFinal.size() + " lançamento(s)!");
                        lblStatusTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                        precisaRecarregar = true;
                        carregarSeNecessario();
                    },
                    erro -> {
                        btnConfirmar.setDisable(false);
                        lblErroQ.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(erro));
                    });
        });

        Button btnCancelar = FormatadorSeguroUI.criarBotaoCartoon("Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnCancelar.setOnAction(e -> dlg.close());

        HBox rod = new HBox(10, btnCancelar, btnConfirmar);
        rod.setAlignment(Pos.CENTER_RIGHT);
        rod.setPadding(new Insets(10, 16, 12, 16));
        rod.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 1px 0 0 0;");

        VBox corpo = new VBox(10, boxParcelasInfo, gForm, new HBox(10, lblSomaQ, btnAddPq), spPgs, lblErroQ);
        corpo.setPadding(new Insets(14, 16, 12, 16));
        corpo.setStyle("-fx-background-color: #FFFFFF;");

        VBox root = new VBox(header, corpo, rod);
        root.setPrefWidth(660);
        dlg.setScene(new Scene(root));
        dlg.showAndWait();
    }

    private void filtrarTabelaPessoas() {
        String termo = txtBuscaPessoa.getText() != null ? txtBuscaPessoa.getText().trim().toLowerCase(Locale.ROOT) : "";
        String papel = cbFiltroPapelPessoa.getValue() != null ? cbFiltroPapelPessoa.getValue() : "Todas as Entidades";
        List<Pessoa> lista = new ArrayList<>();
        for (Pessoa p : pessoasCache) {
            boolean matchPapel = switch (papel) {
                case "Apenas Clientes" -> p.isCliente();
                case "Apenas Fornecedores" -> p.isFornecedor();
                default -> true;
            };
            if (!matchPapel) continue;
            String txt = ("#" + p.getId() + " " + p.getNome() + " " + resumirRegistrosPessoa(p)).toLowerCase(Locale.ROOT);
            if (!termo.isEmpty() && !txt.contains(termo)) continue;
            lista.add(p);
        }
        tabelaPessoas.setItems(FXCollections.observableArrayList(lista));
    }

    private void salvarOuAtualizarPessoa() {
        String modo = cbModoPessoa.getValue();
        String nome = txtPesNome.getText() != null ? txtPesNome.getText().trim() : "";
        String doc = txtPesDoc.getText() != null ? txtPesDoc.getText().trim() : "";
        BigDecimal limite = parseDecimalBr(txtPesLimite.getText(), BigDecimal.ZERO);
        if (nome.isEmpty()) {
            lblStatusPessoa.setText("Informe o nome ou razão social.");
            return;
        }
        if ("✏️ Editar Selecionado & Dossiê 360º".equals(modo) && pessoaSelecionada != null) {
            DialogoCorporativoModal.confirmarAcaoManual(
                    this, "Atualizar Cadastro", "Confirmar alteração de nome/razão social?",
                    Map.of("ID", "#" + pessoaSelecionada.getId(), "Novo Nome", nome),
                    () -> ponte.executarRotaAsync(RotaSistema.PESSOA_DADOS_ATUALIZAR,
                            Map.of("idPessoa", pessoaSelecionada.getId(), "nome", nome),
                            resp -> lblStatusPessoa.setText(resp.sucesso() ? "✔ Nome atualizado!" : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));
            return;
        }

        RotaSistema rota;
        Map<String, Object> params = new LinkedHashMap<>();
        if (modo.contains("Cliente PF")) {
            rota = RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR;
            params.put("nome", nome);
            params.put("cpf", doc);
            params.put("limite", limite);
        } else if (modo.contains("Cliente PJ")) {
            rota = RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR;
            params.put("razaoSocial", nome);
            params.put("cnpj", doc);
            params.put("limite", limite);
        } else if (modo.contains("Fornecedor PJ")) {
            rota = RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR;
            params.put("razaoSocial", nome);
            params.put("cnpj", doc);
        } else if (modo.contains("Fornecedor PF")) {
            rota = RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR;
            params.put("nome", nome);
            params.put("cpf", doc);
        } else {
            rota = RotaSistema.PESSOA_SIMPLES_CADASTRAR;
            params.put("nome", nome);
            params.put("isCliente", chkPesCliente.isSelected());
            params.put("isFornecedor", chkPesFornecedor.isSelected());
            if (!doc.isEmpty()) params.put("documento", doc);
            params.put("limite", limite);
        }

        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Confirmar Cadastro de Entidade", "Deseja gravar este cliente/fornecedor no banco?",
                Map.of("Tipo", modo, "Nome / Razão", nome, "Documento", doc.isEmpty() ? "Opcional" : doc),
                () -> ponte.executarRotaAsync(rota, params, resp -> {
                    if (resp.sucesso()) {
                        lblStatusPessoa.setText("✔ '" + nome + "' cadastrado(a) com sucesso!");
                        txtPesNome.clear();
                        txtPesDoc.clear();
                    } else {
                        lblStatusPessoa.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                    }
                }));
    }

    private void atualizarLimitePessoa() {
        if (pessoaSelecionada == null) return;
        BigDecimal limite = parseDecimalBr(txtPesLimite.getText(), BigDecimal.ZERO);
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Atualizar Limite de Crédito", "Confirmar novo limite?",
                Map.of("Pessoa", pessoaSelecionada.getNome(), "Novo Limite", FormatadorSeguroUI.formatarMoeda(limite)),
                () -> ponte.executarRotaAsync(RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE,
                        Map.of("idPessoa", pessoaSelecionada.getId(), "novoLimite", limite),
                        resp -> lblStatusPessoa.setText(resp.sucesso() ? "✔ Limite atualizado para " + FormatadorSeguroUI.formatarMoeda(limite)
                                : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));
    }

    private void adicionarRegistroAPessoa() {
        if (pessoaSelecionada == null) {
            lblStatusPessoa.setText("Selecione uma pessoa na tabela antes de vincular contato/documento.");
            return;
        }
        String tipo = txtPesContatoTipo.getText() != null && !txtPesContatoTipo.getText().isBlank()
                ? txtPesContatoTipo.getText().trim().toUpperCase(Locale.ROOT) : "TELEFONE";
        String val = txtPesContatoValor.getText() != null ? txtPesContatoValor.getText().trim() : "";
        if (val.isEmpty()) return;

        ponte.executarRotaAsync(RotaSistema.PESSOA_REGISTRO_ADICIONAR,
                Map.of("idPessoa", pessoaSelecionada.getId(), "tipoDocumento", tipo, "numeroRegistro", val),
                resp -> {
                    if (resp.sucesso()) {
                        lblStatusPessoa.setText("✔ Registro " + tipo + " adicionado!");
                        txtPesContatoValor.clear();
                        carregarDossie360Async(pessoaSelecionada);
                    } else {
                        lblStatusPessoa.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                    }
                });
    }

    private void excluirPessoaSelecionada() {
        if (pessoaSelecionada == null) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Excluir Pessoa #" + pessoaSelecionada.getId(),
                "Esta ação excluirá o cadastro selecionado.",
                Map.of("Nome", pessoaSelecionada.getNome()),
                () -> ponte.executarRotaAsync(RotaSistema.PESSOA_CLIENTE_EXCLUIR, Map.of("idPessoa", pessoaSelecionada.getId()), resp -> {
                    if (resp.sucesso()) {
                        lblStatusPessoa.setText("✔ Registro excluído.");
                        pessoaSelecionada = null;
                    } else {
                        lblStatusPessoa.setText("⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                    }
                }));
    }

    private void carregarDossie360Async(Pessoa pes) {
        boxDossiePessoaEstruturado.getChildren().setAll(FormatadorSeguroUI.criarLabel("Carregando Dossiê 360º...", 12, false, "#64748B"));
        Thread.ofVirtual().start(() -> {
            Map<String, Object> dossie = ponte.consultarDossie360Pessoa(pes.getId());
            Map<String, String> metaFormatada = construirMapaDadosPessoa(pes);

            if (dossie != null) {
                Object saldoAberto = dossie.get("saldoAberto");
                if (saldoAberto instanceof BigDecimal bd) {
                    metaFormatada.put("Saldo em Aberto", FormatadorSeguroUI.formatarMoeda(bd));
                }
            }

            Platform.runLater(() -> {
                boxDossiePessoaEstruturado.getChildren().clear();
                boxDossiePessoaEstruturado.getChildren().add(FormatadorSeguroUI.criarGradeMetadadosCards(metaFormatada));
                FlowPane chips = new FlowPane(6, 6);
                if (pes.getRegistros() != null && !pes.getRegistros().isEmpty()) {
                    for (Registro r : pes.getRegistros()) {
                        chips.getChildren().add(FormatadorSeguroUI.criarBadgeCartoon(
                                r.getTipo() + ": " + r.getRegistro(), "#EFF6FF", "#93C5FD", "#1E40AF"));
                    }
                    boxDossiePessoaEstruturado.getChildren().add(chips);
                }
            });
        });
    }

    private void preencherFormularioFormaPag(FormaPagamento fp) {
        this.fpSelecionada = fp;
        txtFpCodigo.setText(fp.getCodigo());
        txtFpNome.setText(fp.getNome());
        txtFpDesc.setText(fp.getDescricao() != null ? fp.getDescricao() : "");
        chkFpVista.setSelected(fp.isPermiteAVista());
        chkFpPrazo.setSelected(fp.isPermiteAPrazo());
        chkFpParcelado.setSelected(fp.isPermiteParcelado());
        txtFpMaxParc.setText(String.valueOf(fp.getMaxParcelas()));
        txtFpMaxSemJuros.setText(String.valueOf(fp.getMaxParcelasSemJuros()));
        txtFpTaxaMdr.setText(fp.getTaxaOperadoraPercentual().toPlainString().replace(".", ","));
        txtFpTarifaFixa.setText(fp.getTarifaFixaOperadora().toPlainString().replace(".", ","));
        txtFpDescVista.setText(fp.getPercentualDescontoAVistaPadrao().toPlainString().replace(".", ","));
        txtFpJurosMensal.setText(fp.getTaxaJurosMensalPadrao().toPlainString().replace(".", ","));
    }

    private void cadastrarFormaPagamento() {
        String cod = txtFpCodigo.getText() != null ? txtFpCodigo.getText().trim().toUpperCase(Locale.ROOT) : "";
        String nome = txtFpNome.getText() != null ? txtFpNome.getText().trim() : "";
        if (cod.isEmpty() || nome.isEmpty()) {
            lblStatusFp.setText("Informe código único e nome da forma de pagamento.");
            return;
        }
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Cadastrar Forma de Pagamento", "Confirmar criação da forma contábil?",
                Map.of("Código", cod, "Nome", nome, "Taxa Operadora (%)", txtFpTaxaMdr.getText()),
                () -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("codigo", cod);
                    p.put("nome", nome);
                    p.put("descricao", txtFpDesc.getText() != null ? txtFpDesc.getText().trim() : "");
                    p.put("permiteAVista", chkFpVista.isSelected());
                    p.put("permiteAPrazo", chkFpPrazo.isSelected());
                    p.put("permiteParcelado", chkFpParcelado.isSelected());
                    p.put("maxParcelas", parseInt(txtFpMaxParc.getText(), 1));
                    p.put("maxParcelasSemJuros", parseInt(txtFpMaxSemJuros.getText(), 1));
                    p.put("taxaOperadoraPercentual", parseDecimalBr(txtFpTaxaMdr.getText(), BigDecimal.ZERO));
                    p.put("tarifaFixaOperadora", parseDecimalBr(txtFpTarifaFixa.getText(), BigDecimal.ZERO));
                    p.put("descontoAVistaPercentual", parseDecimalBr(txtFpDescVista.getText(), BigDecimal.ZERO));
                    p.put("taxaJurosMensal", parseDecimalBr(txtFpJurosMensal.getText(), BigDecimal.ZERO));

                    ponte.executarRotaAsync(RotaSistema.PAGAMENTO_FORMA_CADASTRAR, p, resp ->
                            lblStatusFp.setText(resp.sucesso() ? "✔ Forma '" + cod + "' cadastrada!" : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem())));
                });
    }

    private void atualizarRegrasFormaPagamento() {
        String cod = txtFpCodigo.getText() != null ? txtFpCodigo.getText().trim().toUpperCase(Locale.ROOT) : "";
        if (cod.isEmpty()) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Atualizar Taxas e Regras (" + cod + ")", "Confirma atualização contábil das taxas/parcelamento?",
                Map.of("Código", cod, "Taxa (%)", txtFpTaxaMdr.getText(), "Máx. Parcelas", txtFpMaxParc.getText()),
                () -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("codigo", cod);
                    p.put("taxaOperadoraPercentual", parseDecimalBr(txtFpTaxaMdr.getText(), BigDecimal.ZERO));
                    p.put("tarifaFixaOperadora", parseDecimalBr(txtFpTarifaFixa.getText(), BigDecimal.ZERO));
                    p.put("descontoAVistaPercentual", parseDecimalBr(txtFpDescVista.getText(), BigDecimal.ZERO));
                    p.put("maxParcelas", parseInt(txtFpMaxParc.getText(), 1));
                    p.put("maxParcelasSemJuros", parseInt(txtFpMaxSemJuros.getText(), 1));
                    p.put("taxaJurosMensal", parseDecimalBr(txtFpJurosMensal.getText(), BigDecimal.ZERO));
                    ponte.executarRotaAsync(RotaSistema.PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS, p, resp ->
                            lblStatusFp.setText(resp.sucesso() ? "✔ Taxas e regras de '" + cod + "' atualizadas!"
                                    : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem())));
                });
    }

    private void alternarStatusFormaPagamento() {
        if (fpSelecionada == null) return;
        boolean novoStatus = !fpSelecionada.isAtivo();
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Alterar Status de Forma de Pagamento",
                "Confirmar ativação/desativação?",
                Map.of("Forma", fpSelecionada.getNome(), "Novo Status", novoStatus ? "ATIVA" : "INATIVA"),
                () -> ponte.executarRotaAsync(RotaSistema.PAGAMENTO_FORMA_ALTERAR_STATUS,
                        Map.of("codigo", fpSelecionada.getCodigo(), "ativo", novoStatus),
                        resp -> lblStatusFp.setText(resp.sucesso() ? "✔ Status alterado para " + (novoStatus ? "ATIVA" : "INATIVA")
                                : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));
    }

    private void excluirFormaPagamento() {
        String cod = txtFpCodigo.getText() != null ? txtFpCodigo.getText().trim().toUpperCase(Locale.ROOT) : "";
        if (cod.isEmpty()) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this, "Excluir Forma de Pagamento", "Deseja excluir definitivamente '" + cod + "'?",
                Map.of("Código", cod),
                () -> ponte.executarRotaAsync(RotaSistema.PAGAMENTO_FORMA_EXCLUIR, Map.of("codigo", cod), resp ->
                        lblStatusFp.setText(resp.sucesso() ? "✔ Forma '" + cod + "' excluída!" : "⚠️ " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()))));
    }

    // ==================== HELPERS ====================

    private TableColumn<Object[], String> colArray(String tit, int idx, double w, boolean moeda) {
        TableColumn<Object[], String> col = new TableColumn<>(tit);
        col.setPrefWidth(w);
        col.setCellValueFactory(c -> {
            Object[] arr = c.getValue();
            Object v = (arr != null && idx < arr.length) ? arr[idx] : null;
            if (moeda) return new SimpleStringProperty(FormatadorSeguroUI.formatarMoeda(toBd(arr, idx)));
            return new SimpleStringProperty(FormatadorSeguroUI.tratarParaExibicao(v, "-"));
        });
        return col;
    }

    private TableColumn<Object[], String> colPercent(String tit, int idx, double w) {
        TableColumn<Object[], String> col = new TableColumn<>(tit);
        col.setPrefWidth(w);
        col.setCellValueFactory(c -> {
            BigDecimal v = toBd(c.getValue(), idx);
            return new SimpleStringProperty(FormatadorSeguroUI.formatarDecimal(v.doubleValue(), 1) + "%");
        });
        return col;
    }

    private TableColumn<Object[], String> colBoolFlag(String tit, int idx, double w) {
        TableColumn<Object[], String> col = new TableColumn<>(tit);
        col.setPrefWidth(w);
        col.setCellValueFactory(c -> {
            Object[] arr = c.getValue();
            Object v = (arr != null && idx < arr.length) ? arr[idx] : null;
            boolean sim = v instanceof Boolean b ? b : (v instanceof Number num && num.intValue() == 1);
            return new SimpleStringProperty(sim ? "SIM" : "NÃO");
        });
        return col;
    }

    private String traduzirTipoNota(Nota n) {
        if (n instanceof NotaCompra) return "ENTRADA (Compra)";
        if (n instanceof NotaVenda) return "VENDA";
        if (n instanceof NotaDevolucao) return "DEVOLUÇÃO";
        if (n instanceof NotaPerda) return "PERDA";
        return "NOTA";
    }

    private String classificarStatusNota(Nota n) {
        if (n.getPagamentos() == null || n.getPagamentos().isEmpty()) return "Quitada";
        for (NotaPagamento np : n.getPagamentos()) {
            if (ponte.existeParcelaOuTituloEmAberto(np)) return "Em Aberto";
        }
        return "Quitada";
    }

    private String resumirRegistrosPessoa(Pessoa p) {
        if (p.getRegistros() == null || p.getRegistros().isEmpty()) return "Sem registro extra";
        StringBuilder sb = new StringBuilder();
        for (Registro r : p.getRegistros()) {
            if (!sb.isEmpty()) sb.append(" • ");
            sb.append(r.getTipo()).append(": ").append(r.getRegistro());
        }
        return sb.toString();
    }

    private BigDecimal toBd(Object[] arr, int idx) {
        if (arr == null || idx >= arr.length || arr[idx] == null) return BigDecimal.ZERO;
        Object v = arr[idx];
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Long extrairIdPrefixado(String s) {
        if (s == null || !s.startsWith("#")) return null;
        try {
            return Long.parseLong(s.substring(1).split("\\s*-\\s*", 2)[0].trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long extrairIdLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.replace("#", "").trim());
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

    private int parseInt(String txt, int fallback) {
        if (txt == null || txt.isBlank()) return fallback;
        try {
            return Integer.parseInt(txt.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
