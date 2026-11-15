package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.FormatadorDadosUI;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aba unificada de Cadastros & Operações: tudo que pode ser manipulado pelo Chat IA
 * também fica disponível por formulários estruturados via SistemaGateway (SOLID).
 */
public class AbaCadastrosOperacoesContainer extends VBox {

    public enum Especialidade {
        PRODUTOS("CADASTRO ESPECIALIZADO: PRODUTOS, UNIDADES & KITS"),
        PESSOAS("CADASTRO ESPECIALIZADO: CLIENTES & FORNECEDORES"),
        NOTAS("OPERAÇÕES ESPECIALIZADAS: NOTAS DE COMPRA, VENDA FIFO & DEVOLUÇÃO"),
        PAGAMENTOS("GESTÃO ESPECIALIZADA: FORMAS DE PAGAMENTO");

        final String titulo;

        Especialidade(String titulo) {
            this.titulo = titulo;
        }
    }

    private final SistemaGateway gateway;
    private final Label lblStatusGlobal = new Label("Pronto para operar.");
    private final Runnable aoAlterarDados;

    // Produtos
    private final TableView<Produto> tabelaProdutos = new TableView<>();
    // Pessoas
    private final TableView<Pessoa> tabelaPessoas = new TableView<>();
    // Notas
    private final TableView<Nota> tabelaNotas = new TableView<>();
    // Pagamentos
    private final TableView<FormaPagamento> tabelaFormasPagamento = new TableView<>();

    public AbaCadastrosOperacoesContainer(SistemaGateway gateway, Runnable aoAlterarDados) {
        this(gateway, aoAlterarDados, null);
    }

    public AbaCadastrosOperacoesContainer(SistemaGateway gateway, Runnable aoAlterarDados, Especialidade especialidade) {
        this.gateway = gateway;
        this.aoAlterarDados = aoAlterarDados;
        setSpacing(12);
        setPadding(new Insets(16));

        HBox barraTopo = new HBox(12);
        barraTopo.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label(especialidade != null ? especialidade.titulo : "CADASTROS DE ENTIDADES & OPERAÇÕES DO SISTEMA");
        titulo.getStyleClass().add("section-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        lblStatusGlobal.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");
        Button btnRecarregar = new Button("Sincronizar Tabelas");
        btnRecarregar.getStyleClass().add("btn-secondary");
        btnRecarregar.setOnAction(e -> recarregarDados());
        barraTopo.getChildren().addAll(titulo, spacer, lblStatusGlobal, btnRecarregar);

        if (especialidade != null) {
            ScrollPane painelUnico = switch (especialidade) {
                case PRODUTOS -> criarSubAbaProdutos();
                case PESSOAS -> criarSubAbaPessoas();
                case NOTAS -> criarSubAbaNotas();
                case PAGAMENTOS -> criarSubAbaPagamentos();
            };
            VBox.setVgrow(painelUnico, Priority.ALWAYS);
            getChildren().addAll(barraTopo, painelUnico);
        } else {
            TabPane subAbas = new TabPane();
            subAbas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            VBox.setVgrow(subAbas, Priority.ALWAYS);

            subAbas.getTabs().addAll(
                    new Tab("Produtos, Unidades & Kits", criarSubAbaProdutos()),
                    new Tab("Pessoas (Clientes & Fornecedores)", criarSubAbaPessoas()),
                    new Tab("Notas (Compra, Venda & Devolução)", criarSubAbaNotas()),
                    new Tab("Formas de Pagamento", criarSubAbaPagamentos())
            );

            getChildren().addAll(barraTopo, subAbas);
        }
        recarregarDados();
    }

    private ScrollPane criarSubAbaProdutos() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14));

        // Form 1: Unidade de medida
        TextField txtSiglaUm = new TextField("UN");
        TextField txtNomeUm = new TextField("Unidade");
        TextField txtBaseUm = new TextField("1");
        GridPane gUm = criarGrid();
        gUm.add(new Label("Sigla:"), 0, 0); gUm.add(txtSiglaUm, 1, 0);
        gUm.add(new Label("Nome:"), 2, 0); gUm.add(txtNomeUm, 3, 0);
        gUm.add(new Label("Fator Base:"), 4, 0); gUm.add(txtBaseUm, 5, 0);
        Button btnUm = criarBotao("Cadastrar Unidade", "btn-secondary", e -> executarOperacao(
                RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
                Map.of("sigla", txtSiglaUm.getText().trim(), "nome", txtNomeUm.getText().trim(),
                        "descricao", txtNomeUm.getText().trim(), "base", txtBaseUm.getText().trim()),
                false));
        VBox cardUm = criarCard("1. Unidade de Medida", gUm, btnUm);

        // Form 2: Produto Físico / Digital
        ComboBox<String> cmbTipoProd = new ComboBox<>(FXCollections.observableArrayList("FÍSICO", "DIGITAL"));
        cmbTipoProd.setValue("FÍSICO");
        TextField txtNomeProd = new TextField();
        txtNomeProd.setPromptText("Ex: Cartela de Ovos c/ 30");
        TextField txtDescProd = new TextField();
        txtDescProd.setPromptText("Descrição");
        TextField txtPrecoProd = new TextField("0.00");
        TextField txtUmProd = new TextField("UN");

        GridPane gProd = criarGrid();
        gProd.add(new Label("Tipo:"), 0, 0); gProd.add(cmbTipoProd, 1, 0);
        gProd.add(new Label("Nome:"), 2, 0); gProd.add(txtNomeProd, 3, 0);
        gProd.add(new Label("Preço Venda:"), 0, 1); gProd.add(txtPrecoProd, 1, 1);
        gProd.add(new Label("UM (sigla):"), 2, 1); gProd.add(txtUmProd, 3, 1);
        gProd.add(new Label("Descrição:"), 0, 2); gProd.add(txtDescProd, 1, 2, 3, 1);

        Button btnSalvarProd = criarBotao("Cadastrar Produto", "btn-primary", e -> {
            RotaSistema rota = "DIGITAL".equals(cmbTipoProd.getValue())
                    ? RotaSistema.PRODUTO_DIGITAL_CADASTRAR
                    : RotaSistema.PRODUTO_FISICO_CADASTRAR;
            executarOperacao(rota, Map.of(
                    "nome", txtNomeProd.getText().trim(),
                    "descricao", txtDescProd.getText().trim(),
                    "precoVenda", txtPrecoProd.getText().trim(),
                    "siglaUm", txtUmProd.getText().trim()
            ), true);
        });
        VBox cardProd = criarCard("2. Cadastrar Produto", gProd, btnSalvarProd);

        // Form 3: Kit / Produto Composto + Atualizar Preço
        TextField txtIdKit = new TextField(); txtIdKit.setPromptText("ID Produto Composto");
        TextField txtIdComp = new TextField(); txtIdComp.setPromptText("ID Componente");
        TextField txtQtdComp = new TextField("1");
        Button btnKit = criarBotao("Vincular Componente", "btn-secondary", e -> executarOperacao(
                RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                Map.of("idProdutoKit", Long.parseLong(txtIdKit.getText().trim()),
                        "idProdutoComponente", Long.parseLong(txtIdComp.getText().trim()),
                        "quantidade", txtQtdComp.getText().trim()),
                true));

        TextField txtIdPreco = new TextField(); txtIdPreco.setPromptText("ID Produto");
        TextField txtNovoPreco = new TextField(); txtNovoPreco.setPromptText("Novo preço R$");
        Button btnPreco = criarBotao("Atualizar Preço", "btn-secondary", e -> executarOperacao(
                RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                Map.of("idProduto", Long.parseLong(txtIdPreco.getText().trim()),
                        "novoPreco", txtNovoPreco.getText().trim()),
                true));

        GridPane gKit = criarGrid();
        gKit.add(new Label("ID Kit:"), 0, 0); gKit.add(txtIdKit, 1, 0);
        gKit.add(new Label("ID Comp.:"), 2, 0); gKit.add(txtIdComp, 3, 0);
        gKit.add(new Label("Qtd:"), 4, 0); gKit.add(txtQtdComp, 5, 0);
        gKit.add(btnKit, 6, 0);
        gKit.add(new Label("ID Prod.:"), 0, 1); gKit.add(txtIdPreco, 1, 1);
        gKit.add(new Label("Novo Preço:"), 2, 1); gKit.add(txtNovoPreco, 3, 1);
        gKit.add(btnPreco, 4, 1);
        VBox cardKit = criarCard("3. Composição Multinível (Kits) & Reajuste de Preço", gKit);

        configurarTabelaProdutos();
        VBox cardLista = criarCard("Catálogo Atual", tabelaProdutos);

        root.getChildren().addAll(cardUm, cardProd, cardKit, cardLista);
        return envolverScroll(root);
    }

    private ScrollPane criarSubAbaPessoas() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14));

        ComboBox<String> cmbPapel = new ComboBox<>(FXCollections.observableArrayList("CLIENTE", "FORNECEDOR", "AMBOS"));
        cmbPapel.setValue("CLIENTE");
        TextField txtNome = new TextField(); txtNome.setPromptText("Nome completo ou Razão Social");
        TextField txtDoc = new TextField(); txtDoc.setPromptText("CPF ou CNPJ");
        TextField txtLimite = new TextField("1000.00");

        GridPane gP = criarGrid();
        gP.add(new Label("Papel:"), 0, 0); gP.add(cmbPapel, 1, 0);
        gP.add(new Label("Nome / Razão:"), 2, 0); gP.add(txtNome, 3, 0);
        gP.add(new Label("CPF / CNPJ:"), 0, 1); gP.add(txtDoc, 1, 1);
        gP.add(new Label("Limite Crédito:"), 2, 1); gP.add(txtLimite, 3, 1);

        Button btnCadPessoa = criarBotao("Cadastrar Pessoa", "btn-primary", e -> executarOperacao(
                RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                Map.of("nome", txtNome.getText().trim(),
                        "papel", cmbPapel.getValue(),
                        "documento", txtDoc.getText().trim(),
                        "limite", txtLimite.getText().trim()),
                true));

        TextField txtIdLimite = new TextField(); txtIdLimite.setPromptText("ID Pessoa");
        TextField txtNovoLimite = new TextField(); txtNovoLimite.setPromptText("Novo Limite");
        Button btnLim = criarBotao("Atualizar Limite", "btn-secondary", e -> executarOperacao(
                RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE,
                Map.of("idPessoa", Long.parseLong(txtIdLimite.getText().trim()),
                        "novoLimite", txtNovoLimite.getText().trim()),
                true));

        HBox boxLimite = new HBox(10, new Label("Ajustar Limite — ID:"), txtIdLimite, new Label("Novo Limite:"), txtNovoLimite, btnLim);
        boxLimite.setAlignment(Pos.CENTER_LEFT);

        VBox cardCad = criarCard("Cadastrar Cliente / Fornecedor", gP, btnCadPessoa, boxLimite);

        configurarTabelaPessoas();
        VBox cardTab = criarCard("Pessoas Cadastradas", tabelaPessoas);

        root.getChildren().addAll(cardCad, cardTab);
        return envolverScroll(root);
    }

    private ScrollPane criarSubAbaNotas() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14));

        // Compra
        TextField txtIdFornecedor = new TextField(); txtIdFornecedor.setPromptText("ID ou nome fornecedor");
        TextField txtProdCompra = new TextField(); txtProdCompra.setPromptText("ID ou nome produto");
        TextField txtQtdCompra = new TextField("10");
        TextField txtCustoCompra = new TextField("10.00");
        DatePicker dtValidade = new DatePicker(LocalDate.now().plusMonths(6));

        GridPane gCompra = criarGrid();
        gCompra.add(new Label("Fornecedor:"), 0, 0); gCompra.add(txtIdFornecedor, 1, 0);
        gCompra.add(new Label("Produto:"), 2, 0); gCompra.add(txtProdCompra, 3, 0);
        gCompra.add(new Label("Quantidade:"), 0, 1); gCompra.add(txtQtdCompra, 1, 1);
        gCompra.add(new Label("Custo Unit.:"), 2, 1); gCompra.add(txtCustoCompra, 3, 1);
        gCompra.add(new Label("Validade:"), 0, 2); gCompra.add(dtValidade, 1, 2);

        Button btnCompra = criarBotao("Emitir Nota de Compra (Entrada FIFO)", "btn-primary", e -> {
            Map<String, Object> item = new HashMap<>();
            preencherIdOuNomeProduto(item, txtProdCompra.getText().trim());
            item.put("quantidade", txtQtdCompra.getText().trim());
            item.put("valor", txtCustoCompra.getText().trim());
            if (dtValidade.getValue() != null) {
                item.put("dataValidade", dtValidade.getValue());
            }
            Map<String, Object> params = new HashMap<>();
            preencherIdOuNomePessoa(params, "idFornecedor", "fornecedor", txtIdFornecedor.getText().trim());
            params.put("descricao", "Compra via Interface JavaFX");
            params.put("itens", List.of(item));
            executarOperacao(RotaSistema.NOTA_COMPRA_EMITIR, params, true);
        });
        VBox cardCompra = criarCard("1. Emitir Nota de Compra", gCompra, btnCompra);

        // Venda FIFO
        TextField txtClienteVenda = new TextField(); txtClienteVenda.setPromptText("ID/nome (vazio = Balcão)");
        TextField txtProdVenda = new TextField(); txtProdVenda.setPromptText("ID ou nome produto");
        TextField txtQtdVenda = new TextField("1");
        TextField txtDescVenda = new TextField("0.00");

        GridPane gVenda = criarGrid();
        gVenda.add(new Label("Cliente:"), 0, 0); gVenda.add(txtClienteVenda, 1, 0);
        gVenda.add(new Label("Produto:"), 2, 0); gVenda.add(txtProdVenda, 3, 0);
        gVenda.add(new Label("Quantidade:"), 0, 1); gVenda.add(txtQtdVenda, 1, 1);
        gVenda.add(new Label("Desconto R$:"), 2, 1); gVenda.add(txtDescVenda, 3, 1);

        Button btnVenda = criarBotao("Emitir Venda FIFO", "btn-primary", e -> {
            Map<String, Object> item = new HashMap<>();
            preencherIdOuNomeProduto(item, txtProdVenda.getText().trim());
            item.put("quantidade", txtQtdVenda.getText().trim());

            Map<String, Object> params = new HashMap<>();
            params.put("itens", List.of(item));
            String cli = txtClienteVenda.getText().trim();
            if (cli.isBlank()) {
                executarOperacao(RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO, params, true);
            } else {
                preencherIdOuNomePessoa(params, "idCliente", "cliente", cli);
                params.put("descontoGlobal", txtDescVenda.getText().trim());
                params.put("descricao", "Venda FIFO via Interface JavaFX");
                executarOperacao(RotaSistema.NOTA_VENDA_EMITIR_FIFO, params, true);
            }
        });
        VBox cardVenda = criarCard("2. Emitir Nota de Venda (Baixa Automática FIFO)", gVenda, btnVenda);

        // Devolução
        TextField txtIdNotaVenda = new TextField(); txtIdNotaVenda.setPromptText("ID Nota Venda");
        TextField txtIdProdDev = new TextField(); txtIdProdDev.setPromptText("ID Produto");
        TextField txtQtdDev = new TextField("1");
        TextField txtMotivoDev = new TextField("Devolução solicitada pelo cliente");
        Button btnDev = criarBotao("Registrar Devolução", "btn-secondary", e -> executarOperacao(
                RotaSistema.NOTA_DEVOLUCAO_REGISTRAR,
                Map.of("idNotaVenda", Long.parseLong(txtIdNotaVenda.getText().trim()),
                        "idProduto", Long.parseLong(txtIdProdDev.getText().trim()),
                        "quantidadeDevolvida", txtQtdDev.getText().trim(),
                        "motivo", txtMotivoDev.getText().trim()),
                true));
        GridPane gDev = criarGrid();
        gDev.add(new Label("ID Nota Venda:"), 0, 0); gDev.add(txtIdNotaVenda, 1, 0);
        gDev.add(new Label("ID Produto:"), 2, 0); gDev.add(txtIdProdDev, 3, 0);
        gDev.add(new Label("Qtd:"), 0, 1); gDev.add(txtQtdDev, 1, 1);
        gDev.add(new Label("Motivo:"), 2, 1); gDev.add(txtMotivoDev, 3, 1);
        VBox cardDev = criarCard("3. Registrar Devolução", gDev, btnDev);

        configurarTabelaNotas();
        VBox cardHist = criarCard("Notas Recentes", tabelaNotas);

        root.getChildren().addAll(cardCompra, cardVenda, cardDev, cardHist);
        return envolverScroll(root);
    }

    private ScrollPane criarSubAbaPagamentos() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(14));

        TextField txtCod = new TextField(); txtCod.setPromptText("Ex: PIX_EMPRESA");
        TextField txtNome = new TextField(); txtNome.setPromptText("Ex: Pix Corporativo");
        TextField txtTaxa = new TextField("0.00");
        CheckBox chkVista = new CheckBox("À Vista"); chkVista.setSelected(true);
        CheckBox chkPrazo = new CheckBox("A Prazo");
        CheckBox chkParc = new CheckBox("Parcelado");

        GridPane gPag = criarGrid();
        gPag.add(new Label("Código:"), 0, 0); gPag.add(txtCod, 1, 0);
        gPag.add(new Label("Nome:"), 2, 0); gPag.add(txtNome, 3, 0);
        gPag.add(new Label("Taxa (%):"), 4, 0); gPag.add(txtTaxa, 5, 0);
        HBox checks = new HBox(14, chkVista, chkPrazo, chkParc);
        gPag.add(checks, 0, 1, 4, 1);

        Button btnForma = criarBotao("Cadastrar Forma de Pagamento", "btn-primary", e -> executarOperacao(
                RotaSistema.PAGAMENTO_FORMA_CADASTRAR,
                Map.of("codigo", txtCod.getText().trim(),
                        "nome", txtNome.getText().trim(),
                        "descricao", txtNome.getText().trim(),
                        "permiteAVista", chkVista.isSelected(),
                        "permiteAPrazo", chkPrazo.isSelected(),
                        "permiteParcelado", chkParc.isSelected(),
                        "taxaOperadoraPercentual", txtTaxa.getText().trim()),
                false));

        VBox cardForm = criarCard("Nova Forma de Pagamento", gPag, btnForma);

        configurarTabelaPagamentos();
        VBox cardLista = criarCard("Formas de Pagamento Ativas", tabelaFormasPagamento);

        root.getChildren().addAll(cardForm, cardLista);
        return envolverScroll(root);
    }

    private void configurarTabelaProdutos() {
        tabelaProdutos.setPrefHeight(220);
        TableColumn<Produto, String> c1 = new TableColumn<>("ID");
        c1.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        TableColumn<Produto, String> c2 = new TableColumn<>("Nome");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        TableColumn<Produto, String> c3 = new TableColumn<>("UM");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUm() != null ? c.getValue().getUm().getSigla() : "-"));
        TableColumn<Produto, String> c4 = new TableColumn<>("Preço Venda");
        c4.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.moeda(c.getValue().getPrecoVenda())));
        TableColumn<Produto, String> c5 = new TableColumn<>("Tipo");
        c5.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isDigital()
                        ? (c.getValue().isConjunto() ? "Digital / Kit" : "Digital")
                        : (c.getValue().isConjunto() ? "Composto / Kit" : "Físico")));
        tabelaProdutos.getColumns().setAll(List.of(c1, c2, c3, c4, c5));
        tabelaProdutos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configurarTabelaPessoas() {
        tabelaPessoas.setPrefHeight(240);
        TableColumn<Pessoa, String> c1 = new TableColumn<>("ID");
        c1.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        TableColumn<Pessoa, String> c2 = new TableColumn<>("Nome / Razão Social");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        TableColumn<Pessoa, String> c3 = new TableColumn<>("Cliente");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isCliente() ? "Sim" : "Não"));
        TableColumn<Pessoa, String> c4 = new TableColumn<>("Fornecedor");
        c4.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isFornecedor() ? "Sim" : "Não"));
        TableColumn<Pessoa, String> c5 = new TableColumn<>("Limite Crédito");
        c5.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.moeda(c.getValue().getLimite())));
        tabelaPessoas.getColumns().setAll(List.of(c1, c2, c3, c4, c5));
        tabelaPessoas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configurarTabelaNotas() {
        tabelaNotas.setPrefHeight(220);
        TableColumn<Nota, String> c1 = new TableColumn<>("ID");
        c1.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        TableColumn<Nota, String> c2 = new TableColumn<>("Tipo");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClass().getSimpleName()));
        TableColumn<Nota, String> c3 = new TableColumn<>("Pessoa");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPessoa() != null ? FormatadorDadosUI.texto(c.getValue().getPessoa().getNome(), "-") : "-"));
        TableColumn<Nota, String> c4 = new TableColumn<>("Valor Total");
        c4.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.moeda(c.getValue().getValorTotal())));
        TableColumn<Nota, String> c5 = new TableColumn<>("Data");
        c5.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.dataHora(c.getValue().getDataGerada(), "-")));
        tabelaNotas.getColumns().setAll(List.of(c1, c2, c3, c4, c5));
        tabelaNotas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void configurarTabelaPagamentos() {
        tabelaFormasPagamento.setPrefHeight(240);
        TableColumn<FormaPagamento, String> c1 = new TableColumn<>("Código");
        c1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        TableColumn<FormaPagamento, String> c2 = new TableColumn<>("Nome");
        c2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        TableColumn<FormaPagamento, String> c3 = new TableColumn<>("À Vista");
        c3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isPermiteAVista() ? "Sim" : "Não"));
        TableColumn<FormaPagamento, String> c4 = new TableColumn<>("Parcelado");
        c4.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isPermiteParcelado() ? "Até " + c.getValue().getMaxParcelas() + "x" : "Não"));
        TableColumn<FormaPagamento, String> c5 = new TableColumn<>("Taxa (%)");
        c5.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.quantidade(c.getValue().getTaxaOperadoraPercentual()) + "%"));
        tabelaFormasPagamento.getColumns().setAll(List.of(c1, c2, c3, c4, c5));
        tabelaFormasPagamento.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public void recarregarDados() {
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> rProd = gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR, Map.of());
            RespostaEndpoint<Object> rPes = gateway.executarRota(RotaSistema.PESSOA_BUSCAR, Map.of("papel", "TODOS"));
            RespostaEndpoint<Object> rNot = gateway.executarRota(RotaSistema.NOTA_LISTAR_PADRAO, Map.of("limite", 30));
            RespostaEndpoint<Object> rPag = gateway.executarRota(RotaSistema.PAGAMENTO_FORMA_LISTAR_ATIVAS, Map.of());

            Platform.runLater(() -> {
                tabelaProdutos.setItems(extrairLista(rProd, Produto.class));
                tabelaPessoas.setItems(extrairLista(rPes, Pessoa.class));
                tabelaNotas.setItems(extrairLista(rNot, Nota.class));
                tabelaFormasPagamento.setItems(extrairLista(rPag, FormaPagamento.class));
            });
        }, err -> Platform.runLater(() -> lblStatusGlobal.setText("Erro ao sincronizar: " + err.getMessage())));
    }

    private void executarOperacao(RotaSistema rota, Map<String, Object> params, boolean notificarGlobal) {
        lblStatusGlobal.setText("Executando " + rota.name() + "...");
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> resp = gateway.executarRota(rota, params);
            Platform.runLater(() -> {
                if (resp != null && resp.sucesso()) {
                    lblStatusGlobal.setText("Sucesso: " + resp.mensagem());
                    recarregarDados();
                    if (notificarGlobal && aoAlterarDados != null) {
                        aoAlterarDados.run();
                    }
                } else {
                    lblStatusGlobal.setText("Falha: " + (resp != null ? resp.mensagem() : "Erro desconhecido"));
                }
            });
        }, err -> Platform.runLater(() -> lblStatusGlobal.setText("Erro: " + err.getMessage())));
    }

    private <T> ObservableList<T> extrairLista(RespostaEndpoint<Object> resp, Class<T> clazz) {
        ObservableList<T> out = FXCollections.observableArrayList();
        if (resp != null && resp.sucesso() && resp.dados() instanceof List<?> lista) {
            for (Object o : lista) {
                if (clazz.isInstance(o)) {
                    out.add(clazz.cast(o));
                }
            }
        }
        return out;
    }

    private void preencherIdOuNomeProduto(Map<String, Object> target, String valor) {
        try {
            target.put("idProduto", Long.parseLong(valor));
        } catch (NumberFormatException e) {
            target.put("nomeProduto", valor);
        }
    }

    private void preencherIdOuNomePessoa(Map<String, Object> target, String chaveId, String chaveNome, String valor) {
        try {
            target.put(chaveId, Long.parseLong(valor));
        } catch (NumberFormatException e) {
            target.put(chaveNome, valor);
        }
    }

    private GridPane criarGrid() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        return g;
    }

    private VBox criarCard(String titulo, javafx.scene.Node... filhos) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-container");
        Label lbl = new Label(titulo);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #38BDF8;");
        card.getChildren().add(lbl);
        card.getChildren().addAll(filhos);
        return card;
    }

    private Button criarBotao(String texto, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(texto);
        b.getStyleClass().add(styleClass);
        b.setOnAction(handler);
        return b;
    }

    private ScrollPane envolverScroll(VBox root) {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        return sp;
    }
}
