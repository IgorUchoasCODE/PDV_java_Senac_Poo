package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aba de Controle de Estoque FIFO, Embalagens/Equivalência (3 níveis),
 * Lotes próximos ao vencimento e Baixa de Perdas Operacionais.
 */
public class AbaEstoqueFifoContainer extends ScrollPane {

    private final SistemaGateway gateway;

    private final TextField txtBuscaProduto = new TextField();
    private final TableView<Produto> tabelaProdutos = new TableView<>();
    private final TableView<Item<?>> tabelaLotesFifo = new TableView<>();
    private final TableView<Item<?>> tabelaLotesVencimento = new TableView<>();

    private final Label lblProdutoSelecionado = new Label("Selecione um produto na tabela para consultar saldo equivalente FIFO.");
    private final Label lblSaldoEquivalente = new Label("Saldo Equivalente: -");
    private final Label lblFeedbackPerda = new Label("");

    private final TextField txtIdLotePerda = new TextField();
    private final TextField txtQtdPerda = new TextField("1");
    private final TextField txtMotivoPerda = new TextField();
    private final DatePicker dtLimiteVencimento = new DatePicker(LocalDate.now().plusMonths(3));

    public AbaEstoqueFifoContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox conteudo = new VBox(16);
        conteudo.setPadding(new Insets(18));

        // Cabeçalho + busca
        HBox topo = new HBox(10);
        topo.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("CONTROLE DE ESTOQUE FIFO & EQUIVALÊNCIA MULTINÍVEL");
        titulo.getStyleClass().add("section-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        txtBuscaProduto.setPromptText("Buscar produto por nome ou ID...");
        txtBuscaProduto.setPrefWidth(260);
        Button btnBuscar = new Button("Buscar");
        btnBuscar.getStyleClass().add("btn-secondary");
        btnBuscar.setOnAction(e -> carregarProdutos(txtBuscaProduto.getText()));

        Button btnRecarregar = new Button("Atualizar Tudo");
        btnRecarregar.getStyleClass().add("btn-primary");
        btnRecarregar.setOnAction(e -> recarregarDados());

        topo.getChildren().addAll(titulo, spacer, txtBuscaProduto, btnBuscar, btnRecarregar);

        // Tabela de produtos + saldo equivalente
        configurarTabelaProdutos();
        VBox cardProdutos = new VBox(8,
                new Label("Catálogo de Produtos (Clique para consultar saldo equivalente e fila FIFO)"),
                tabelaProdutos,
                new HBox(16, lblProdutoSelecionado, lblSaldoEquivalente)
        );
        cardProdutos.getStyleClass().add("card-container");
        lblProdutoSelecionado.setStyle("-fx-text-fill: #94A3B8;");
        lblSaldoEquivalente.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");

        // Fila FIFO de lotes
        configurarTabelaLotes(tabelaLotesFifo);
        tabelaLotesFifo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, novo) -> {
            if (novo != null && novo.getId() != null) {
                txtIdLotePerda.setText(String.valueOf(novo.getId()));
            }
        });
        VBox cardFifo = new VBox(8,
                new Label("Fila de Lotes FIFO Disponíveis (Clique para preencher ID do lote na baixa de perda)"),
                tabelaLotesFifo
        );
        cardFifo.getStyleClass().add("card-container");

        // Lotes próximos ao vencimento + formulário de baixa de perda
        HBox filtroVenc = new HBox(10,
                new Label("Vencendo até:"),
                dtLimiteVencimento,
                criarBotao("Verificar Validades", "btn-secondary", e -> carregarLotesProximosValidade())
        );
        filtroVenc.setAlignment(Pos.CENTER_LEFT);
        configurarTabelaLotes(tabelaLotesVencimento);
        VBox cardValidade = new VBox(8,
                new Label("Alerta de Lotes Próximos ao Vencimento"),
                filtroVenc,
                tabelaLotesVencimento
        );
        cardValidade.getStyleClass().add("card-container");
        HBox.setHgrow(cardValidade, Priority.ALWAYS);

        VBox cardBaixaPerda = montarCardBaixaPerda();
        cardBaixaPerda.setPrefWidth(360);

        HBox linhaInferior = new HBox(14, cardValidade, cardBaixaPerda);

        conteudo.getChildren().addAll(topo, cardProdutos, cardFifo, linhaInferior);
        setContent(conteudo);

        recarregarDados();
    }

    private VBox montarCardBaixaPerda() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-container");
        Label lbl = new Label("Baixa Direta de Perda em Lote");
        lbl.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        txtIdLotePerda.setPromptText("ID do lote (Item)");
        txtQtdPerda.setPromptText("Quantidade perdida");
        txtMotivoPerda.setPromptText("Ex: Avaria, vencimento, calor");

        grid.add(new Label("ID do Lote:"), 0, 0);
        grid.add(txtIdLotePerda, 1, 0);
        grid.add(new Label("Quantidade:"), 0, 1);
        grid.add(txtQtdPerda, 1, 1);
        grid.add(new Label("Motivo:"), 0, 2);
        grid.add(txtMotivoPerda, 1, 2);

        Button btnBaixar = criarBotao("Registrar Perda (FIFO)", "btn-primary", e -> registrarPerdaLote());
        lblFeedbackPerda.setWrapText(true);
        lblFeedbackPerda.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 12px;");

        box.getChildren().addAll(lbl, grid, btnBaixar, lblFeedbackPerda);
        return box;
    }

    private void configurarTabelaProdutos() {
        tabelaProdutos.setPrefHeight(210);
        TableColumn<Produto, String> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        cId.setPrefWidth(60);

        TableColumn<Produto, String> cNome = new TableColumn<>("Produto");
        cNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        cNome.setPrefWidth(220);

        TableColumn<Produto, String> cUm = new TableColumn<>("UM");
        cUm.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUm() != null ? c.getValue().getUm().getSigla() : "-"));
        cUm.setPrefWidth(70);

        TableColumn<Produto, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isDigital()
                        ? (c.getValue().isConjunto() ? "Digital / Kit" : "Digital")
                        : (c.getValue().isConjunto() ? "Composto / Kit" : "Físico")));
        cTipo.setPrefWidth(120);

        TableColumn<Produto, String> cVenda = new TableColumn<>("Preço Venda");
        cVenda.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.moeda(c.getValue().getPrecoVenda())));
        cVenda.setPrefWidth(120);

        tabelaProdutos.getColumns().setAll(List.of(cId, cNome, cUm, cTipo, cVenda));
        tabelaProdutos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tabelaProdutos.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selecionado) -> {
            if (selecionado != null) {
                consultarDetalhesProduto(selecionado);
            }
        });
    }

    private void configurarTabelaLotes(TableView<Item<?>> tabela) {
        tabela.setPrefHeight(200);
        TableColumn<Item<?>, String> cId = new TableColumn<>("ID Lote");
        cId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        cId.setPrefWidth(75);

        TableColumn<Item<?>, String> cProd = new TableColumn<>("Produto");
        cProd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProduto() != null ? FormatadorDadosUI.texto(c.getValue().getProduto().getNome(), "-") : "-"));
        cProd.setPrefWidth(210);

        TableColumn<Item<?>, String> cQtd = new TableColumn<>("Disponível");
        cQtd.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.quantidade(c.getValue().getQuantidadeDisponivel())));
        cQtd.setPrefWidth(100);

        TableColumn<Item<?>, String> cCusto = new TableColumn<>("Custo Unit.");
        cCusto.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.moeda(c.getValue().getValor())));
        cCusto.setPrefWidth(110);

        TableColumn<Item<?>, String> cVal = new TableColumn<>("Validade");
        cVal.setCellValueFactory(c -> new SimpleStringProperty(FormatadorDadosUI.data(c.getValue().getDataValidade(), "Sem validade")));
        cVal.setPrefWidth(115);

        tabela.getColumns().setAll(List.of(cId, cProd, cQtd, cCusto, cVal));
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public void recarregarDados() {
        carregarProdutos(txtBuscaProduto.getText());
        carregarFilaLotesFifo(null);
        carregarLotesProximosValidade();
    }

    private void carregarProdutos(String termo) {
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> resp;
            if (termo != null && !termo.isBlank()) {
                resp = gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("termo", termo.trim()));
            } else {
                resp = gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR, Map.of());
            }
            Platform.runLater(() -> preencherProdutos(resp));
        }, err -> Platform.runLater(() -> lblProdutoSelecionado.setText("Erro ao listar produtos: " + err.getMessage())));
    }

    private void preencherProdutos(RespostaEndpoint<Object> resp) {
        ObservableList<Produto> lista = FXCollections.observableArrayList();
        if (resp != null && resp.sucesso()) {
            if (resp.dados() instanceof Produto p) {
                lista.add(p);
            } else if (resp.dados() instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Produto p) lista.add(p);
                }
            }
        }
        tabelaProdutos.setItems(lista);
    }

    private void consultarDetalhesProduto(Produto p) {
        lblProdutoSelecionado.setText("Produto: " + p.getNome() + " (ID #" + p.getId() + ")");
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respSaldo = gateway.executarRota(RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", p.getId()));
            RespostaEndpoint<Object> respFifo = gateway.executarRota(RotaSistema.ESTOQUE_FILA_LOTES_FIFO, Map.of("idProduto", p.getId()));
            Platform.runLater(() -> {
                if (respSaldo != null && respSaldo.sucesso() && respSaldo.dados() instanceof BigDecimal saldo) {
                    lblSaldoEquivalente.setText("Saldo Equivalente Total: " + FormatadorDadosUI.quantidade(saldo));
                } else {
                    lblSaldoEquivalente.setText("Saldo Equivalente: " + FormatadorDadosUI.texto(respSaldo != null ? respSaldo.dados() : null, "-"));
                }
                preencherLotes(tabelaLotesFifo, respFifo);
            });
        }, err -> Platform.runLater(() -> lblSaldoEquivalente.setText("Erro saldo: " + err.getMessage())));
    }

    private void carregarFilaLotesFifo(Long idProduto) {
        gateway.executarEmBackground(() -> {
            Map<String, Object> params = idProduto == null ? Map.of() : Map.of("idProduto", idProduto);
            RespostaEndpoint<Object> resp = gateway.executarRota(RotaSistema.ESTOQUE_FILA_LOTES_FIFO, params);
            Platform.runLater(() -> preencherLotes(tabelaLotesFifo, resp));
        }, err -> { });
    }

    private void carregarLotesProximosValidade() {
        LocalDate dataLimite = dtLimiteVencimento.getValue() != null ? dtLimiteVencimento.getValue() : LocalDate.now().plusMonths(3);
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> resp = gateway.executarRota(RotaSistema.ESTOQUE_LOTES_PROXIMOS_VALIDADE, Map.of("dataLimite", dataLimite));
            Platform.runLater(() -> preencherLotes(tabelaLotesVencimento, resp));
        }, err -> { });
    }

    private void preencherLotes(TableView<Item<?>> tabela, RespostaEndpoint<Object> resp) {
        ObservableList<Item<?>> lista = FXCollections.observableArrayList();
        if (resp != null && resp.sucesso()) {
            if (resp.dados() instanceof Item<?> item) {
                lista.add(item);
            } else if (resp.dados() instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Item<?> item) lista.add(item);
                }
            }
        }
        tabela.setItems(lista);
    }

    private void registrarPerdaLote() {
        String idTxt = txtIdLotePerda.getText();
        if (idTxt == null || idTxt.isBlank()) {
            lblFeedbackPerda.setText("Informe o ID do lote para registrar a perda.");
            return;
        }
        String qtdTxt = txtQtdPerda.getText() == null || txtQtdPerda.getText().isBlank() ? "1" : txtQtdPerda.getText().trim();
        String motivo = txtMotivoPerda.getText() == null || txtMotivoPerda.getText().isBlank()
                ? "Perda operacional registrada na interface"
                : txtMotivoPerda.getText().trim();

        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> resp = gateway.executarRota(
                    RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                    Map.of("idLote", Long.parseLong(idTxt.trim()), "quantidade", qtdTxt, "motivo", motivo)
            );
            Platform.runLater(() -> {
                if (resp != null && resp.sucesso()) {
                    lblFeedbackPerda.setText("Perda registrada com sucesso: " + resp.mensagem());
                    recarregarDados();
                } else {
                    lblFeedbackPerda.setText("Falha: " + (resp != null ? resp.mensagem() : "Erro desconhecido"));
                }
            });
        }, err -> Platform.runLater(() -> lblFeedbackPerda.setText("Erro: " + err.getMessage())));
    }

    private Button criarBotao(String texto, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(texto);
        b.getStyleClass().add(styleClass);
        b.setOnAction(handler);
        return b;
    }
}
