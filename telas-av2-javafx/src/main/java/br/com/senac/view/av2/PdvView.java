package br.com.senac.view.av2;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static br.com.senac.view.av2.DemoGateway.brl;
import static br.com.senac.view.av2.PdvGateway.*;

/** Views JavaFX; dados e operações são fornecidos pelo contrato PdvGateway. */
public final class PdvView extends BorderPane {
    private final PdvGateway gateway;
    private final VBox pagina = new VBox(20);
    private final Label status = texto("Dados fictícios em memória · sem cobrança real · dados reiniciam ao fechar", "status");
    private final Map<String, Button> menu = new LinkedHashMap<>();
    private String tela = "Frente de caixa";

    public PdvView(PdvGateway gateway) {
        this.gateway = gateway;
        pagina.getStyleClass().add("page");
        setLeft(criarMenu());
        setCenter(pagina);
        setBottom(status);
        status.setMaxWidth(Double.MAX_VALUE);
        mostrar(tela);
    }

    private VBox criarMenu() {
        VBox lateral = new VBox(10);
        lateral.getStyleClass().add("sidebar");
        lateral.getChildren().addAll(texto("SENAC / PDV", "brand"), texto("VENDAS & CARRINHO · JAVAFX", "brand-sub"));
        Region espaco = new Region();
        espaco.setMinHeight(22);
        lateral.getChildren().add(espaco);
        String[] nomes = {"Frente de caixa", "Produtos e estoque", "Clientes", "Histórico de vendas", "Guia da apresentação"};
        for (int i = 0; i < nomes.length; i++) {
            String nome = nomes[i];
            Button botao = botao(nome, "menu-" + i, () -> mostrar(nome), "");
            botao.setMaxWidth(Double.MAX_VALUE);
            menu.put(nome, botao);
            lateral.getChildren().add(botao);
        }
        Region flex = new Region();
        VBox.setVgrow(flex, Priority.ALWAYS);
        lateral.getChildren().addAll(flex, texto("AV2 · POO", "brand-sub"), texto("Prof. Carlos Bruno", "brand-sub"), texto("MODO DEMONSTRAÇÃO", "brand-sub"));
        return lateral;
    }

    void mostrar(String nome) {
        tela = nome;
        pagina.getChildren().clear();
        menu.forEach((chave, botao) -> {
            botao.getStyleClass().remove("active");
            if (chave.equals(nome)) botao.getStyleClass().add("active");
        });
        VBox titulo = new VBox(5, texto("OPERAÇÃO / " + nome.toUpperCase(), "eyebrow"), texto(nome, "heading"));
        Region flex = new Region();
        HBox.setHgrow(flex, Priority.ALWAYS);
        HBox cabecalho = new HBox(12, titulo, flex, texto("●  JavaFX · demonstração local", "badge"));
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        pagina.getChildren().add(cabecalho);
        Node corpo = switch (nome) {
            case "Frente de caixa" -> frenteCaixa();
            case "Produtos e estoque" -> produtos();
            case "Clientes" -> clientes();
            case "Histórico de vendas" -> historico();
            default -> guia();
        };
        pagina.getChildren().add(corpo);
        VBox.setVgrow(corpo, Priority.ALWAYS);
    }

    private Node frenteCaixa() {
        TableView<Produto> catalogo = tabelaProdutos();
        catalogo.setId("catalogo");
        TextField busca = campo("", "Nome ou código do produto", "busca-produto");
        ComboBox<String> tipo = combo(List.of("Todos os tipos", "Físico", "Digital"), "filtro-tipo");
        Runnable filtrar = () -> catalogo.setItems(FXCollections.observableArrayList(gateway.produtos().stream()
                .filter(p -> (p.codigo() + " " + p.nome()).toLowerCase().contains(busca.getText().toLowerCase()))
                .filter(p -> tipo.getValue().equals("Todos os tipos") || p.tipo().equals(tipo.getValue())).toList()));
        busca.textProperty().addListener((o, antes, agora) -> filtrar.run());
        tipo.valueProperty().addListener((o, antes, agora) -> filtrar.run());
        TextField codigo = campo("", "Ex.: P001", "codigo-venda");
        codigo.setPrefWidth(100);
        Spinner<Integer> quantidade = new Spinner<>(1, 99999, 1);
        quantidade.setId("quantidade-venda");
        quantidade.setPrefWidth(90);
        catalogo.getSelectionModel().selectedItemProperty().addListener((o, antes, agora) -> {
            if (agora != null) codigo.setText(agora.codigo());
        });
        Button adicionar = botao("+ Adicionar", "adicionar", () -> executar(() -> {
            gateway.adicionar(codigo.getText().trim(), quantidade.getValue());
            mostrar(tela);
            status.setText("Produto adicionado ao carrinho.");
        }), "primary");
        codigo.setOnAction(e -> adicionar.fire());
        VBox produtos = card("01 / Selecione os produtos", linha(busca, tipo), catalogo,
                linha(texto("Código", "form-label"), codigo, texto("Qtd.", "form-label"), quantidade, adicionar));
        VBox.setVgrow(catalogo, Priority.ALWAYS);
        TableView<Item> carrinho = tabela();
        carrinho.setId("carrinho");
        coluna(carrinho, "Código", i -> i.produto().codigo(), 75);
        coluna(carrinho, "Produto", i -> i.produto().nome(), 260);
        coluna(carrinho, "Qtd.", Item::quantidade, 55);
        coluna(carrinho, "Unitário", i -> brl(i.produto().preco()), 90);
        coluna(carrinho, "Subtotal", i -> brl(i.produto().preco().multiply(BigDecimal.valueOf(i.quantidade()))), 100);
        carrinho.setItems(FXCollections.observableArrayList(gateway.itens()));
        carrinho.setPlaceholder(texto("Seu carrinho está vazio. Selecione um produto acima.", "muted"));
        VBox itens = card("02 / Carrinho de compras", carrinho, fluxo(
                botao("Alterar quantidade", "alterar-qtd", () -> executar(() -> alterarQuantidade(selecionar(carrinho))), ""),
                botao("Remover item", "remover", () -> executar(() -> {
                    gateway.remover(selecionar(carrinho).produto().codigo());
                    mostrar(tela);
                }), ""),
                botao("Cancelar venda", "cancelar-venda", () -> {
                    if (!gateway.itens().isEmpty() && perguntar("Cancelar venda?", "Os itens serão removidos do carrinho. O estoque não será alterado.")) {
                        gateway.limpar();
                        mostrar(tela);
                    }
                }, "danger")));
        itens.setPrefHeight(285);
        itens.setMinHeight(265);
        carrinho.setMinHeight(145);
        VBox.setVgrow(carrinho, Priority.ALWAYS);
        VBox esquerda = new VBox(16, produtos, itens);
        VBox.setVgrow(produtos, Priority.ALWAYS);
        esquerda.setMinWidth(0);
        Resumo r = gateway.resumo("Dinheiro");
        Button pagar = botao("Ir para pagamento →", "checkout", () -> executar(() -> {
            if (gateway.itens().isEmpty()) throw new IllegalArgumentException("Adicione um produto ao carrinho antes de pagar.");
            pagamento();
        }), "primary");
        pagar.setMaxWidth(Double.MAX_VALUE);
        Label total = texto(brl(r.total()), "total");
        total.setId("total-parcial");
        VBox resumo = card("Resumo da venda",
                texto(gateway.itens().stream().mapToInt(Item::quantidade).sum() + " unidade(s) no carrinho", "muted"),
                valores("Produtos", brl(r.subtotal())), valores("Frete único", brl(r.frete())), new Separator(),
                texto("TOTAL PARCIAL", "eyebrow"), total,
                texto("PIX: −5% sobre os produtos", "subtitle"),
                texto("Crédito: +3% no total com frete", "subtitle"),
                texto("Dinheiro: sem taxa, com troco", "subtitle"), pagar,
                texto("Estoque baixado somente após a confirmação do pagamento.", "subtitle"));
        resumo.setPrefWidth(275);
        resumo.setMinWidth(260);
        HBox corpo = new HBox(18, esquerda, resumo);
        HBox.setHgrow(esquerda, Priority.ALWAYS);
        return corpo;
    }

    private void alterarQuantidade(Item item) {
        Dialog<ButtonType> dialog = dialogo("Alterar quantidade", "alterar-dialog");
        TextField qtd = campo(String.valueOf(item.quantidade()), "Quantidade inteira", "nova-quantidade");
        Label erro = texto("", "error");
        dialog.getDialogPane().setContent(card(item.produto().nome(), qtd, erro));
        salvar(dialog, "Aplicar", () -> {
            gateway.alterarQuantidade(item.produto().codigo(), Integer.parseInt(qtd.getText().trim()));
            mostrar(tela);
        }, erro);
        dialog.show();
    }

    private Node produtos() {
        TableView<Produto> tabela = tabelaProdutos();
        tabela.setId("produtos");
        coluna(tabela, "Detalhes", p -> p.tipo().equals("Físico") ? p.peso() + " · " + p.dimensoes() : p.tamanho(), 160);
        TextField busca = campo("", "Buscar no catálogo", "busca-cadastro");
        busca.textProperty().addListener((o, a, b) -> tabela.setItems(FXCollections.observableArrayList(gateway.produtos().stream()
                .filter(p -> (p.codigo() + " " + p.nome() + " " + p.tipo()).toLowerCase().contains(b.toLowerCase())).toList())));
        VBox p = card("Catálogo e disponibilidade", texto("Cadastre produtos físicos ou digitais e acompanhe o estoque disponível.", "subtitle"),
                tabela, fluxo(busca,
                botao("+ Novo produto", "novo-produto", () -> editarProduto(null), "primary"),
                botao("Editar / ajustar estoque", "editar-produto", () -> executar(() -> editarProduto(selecionar(tabela))), "")));
        VBox.setVgrow(tabela, Priority.ALWAYS);
        return p;
    }

    private void editarProduto(Produto original) {
        Dialog<ButtonType> d = dialogo(original == null ? "Novo produto" : "Editar produto", "produto-dialog");
        TextField codigo = campo(original == null ? "" : original.codigo(), "P004 ou D003", "produto-codigo");
        codigo.setDisable(original != null);
        TextField nome = campo(original == null ? "" : original.nome(), "Nome do produto", "produto-nome");
        ComboBox<String> tipo = combo(List.of("Físico", "Digital"), "produto-tipo");
        if (original != null) tipo.setValue(original.tipo());
        TextField preco = campo(original == null ? "" : original.preco().toPlainString(), "Ex.: 49,90", "produto-preco");
        TextField estoque = campo(original == null ? "0" : String.valueOf(original.estoque()), "Quantidade inteira", "produto-estoque");
        TextField peso = campo(original == null ? "" : original.peso(), "Ex.: 0,65 kg", "produto-peso");
        TextField dimensoes = campo(original == null ? "" : original.dimensoes(), "Ex.: 44 x 14 x 3 cm", "produto-dimensoes");
        TextField link = campo(original == null ? "" : original.link(), "https://...", "produto-link");
        TextField tamanho = campo(original == null ? "" : original.tamanho(), "Ex.: 12 MB", "produto-tamanho");
        Runnable atualizar = () -> {
            boolean fisico = tipo.getValue().equals("Físico");
            peso.setDisable(!fisico);
            dimensoes.setDisable(!fisico);
            link.setDisable(fisico);
            tamanho.setDisable(fisico);
        };
        tipo.valueProperty().addListener((o, a, b) -> atualizar.run());
        atualizar.run();
        GridPane form = formulario();
        par(form, 0, "Código *", codigo, "Nome *", nome);
        par(form, 1, "Tipo *", tipo, "Preço (R$) *", preco);
        par(form, 2, "Estoque *", estoque, "Peso físico *", peso);
        par(form, 3, "Dimensões físicas *", dimensoes, "Link digital *", link);
        par(form, 4, "Tamanho do arquivo *", tamanho, "", new Label());
        Label erro = texto("", "error");
        d.getDialogPane().setContent(card("Dados do produto", texto("* Obrigatório conforme o tipo selecionado. Código é único.", "subtitle"), form, erro));
        salvar(d, "Salvar produto", () -> {
            if (original == null && gateway.produtos().stream().anyMatch(p -> p.codigo().equals(codigo.getText().trim()))) {
                throw new IllegalArgumentException("Já existe um produto com esse código.");
            }
            gateway.salvarProduto(new Produto(codigo.getText().trim(), nome.getText().trim(), tipo.getValue(), decimal(preco.getText()),
                    Integer.parseInt(estoque.getText().trim()), peso.getText().trim(), dimensoes.getText().trim(), link.getText().trim(), tamanho.getText().trim()));
            mostrar(tela);
            status.setText("Produto salvo na demonstração.");
        }, erro);
        d.show();
    }

    private Node clientes() {
        TableView<Cliente> tabela = tabela();
        tabela.setId("clientes");
        coluna(tabela, "Nome", Cliente::nome, 170);
        coluna(tabela, "Identificação", Cliente::documento, 190);
        coluna(tabela, "E-mail", Cliente::email, 170);
        coluna(tabela, "Telefone", Cliente::telefone, 145);
        coluna(tabela, "Endereço", Cliente::endereco, 260);
        tabela.setItems(FXCollections.observableArrayList(gateway.clientes()));
        TextField busca = campo("", "Nome ou identificação", "busca-cliente");
        busca.textProperty().addListener((o, a, b) -> tabela.setItems(FXCollections.observableArrayList(gateway.clientes().stream()
                .filter(c -> (c.nome() + " " + c.documento() + " " + c.email()).toLowerCase().contains(b.toLowerCase())).toList())));
        VBox p = card("Pessoas que compram com você", texto("Use dados fictícios na apresentação. Endereço é necessário somente para entrega física.", "subtitle"),
                tabela, fluxo(busca, botao("+ Novo cliente", "novo-cliente", () -> editarCliente(null), "primary"),
                botao("Editar cliente", "editar-cliente", () -> executar(() -> editarCliente(selecionar(tabela))), "")));
        VBox.setVgrow(tabela, Priority.ALWAYS);
        return p;
    }

    private void editarCliente(Cliente original) {
        Dialog<ButtonType> d = dialogo(original == null ? "Novo cliente" : "Editar cliente", "cliente-dialog");
        TextField nome = campo(original == null ? "" : original.nome(), "Nome completo", "cliente-nome");
        TextField documento = campo(original == null ? "" : original.documento(), "Identificação do cliente", "cliente-documento");
        documento.setDisable(original != null);
        TextField email = campo(original == null ? "" : original.email(), "nome@example.com", "cliente-email");
        TextField telefone = campo(original == null ? "" : original.telefone(), "(92) 99999-0000", "cliente-telefone");
        TextField endereco = campo(original == null ? "" : original.endereco(), "Rua, número, bairro, cidade e UF", "cliente-endereco");
        GridPane form = formulario();
        par(form, 0, "Nome *", nome, "Identificação *", documento);
        par(form, 1, "E-mail", email, "Telefone", telefone);
        par(form, 2, "Endereço de entrega", endereco, "", new Label());
        Label erro = texto("", "error");
        d.getDialogPane().setContent(card("Cadastro de cliente", texto("* Campos obrigatórios. Nenhum dado é enviado pela demonstração.", "subtitle"), form, erro));
        salvar(d, "Salvar cliente", () -> {
            if (original == null && gateway.clientes().stream().anyMatch(c -> c.documento().equals(documento.getText().trim()))) {
                throw new IllegalArgumentException("Já existe um cliente com essa identificação.");
            }
            gateway.salvarCliente(new Cliente(nome.getText().trim(), documento.getText().trim(), email.getText().trim(), telefone.getText().trim(), endereco.getText().trim()));
            mostrar(tela);
            status.setText("Cliente salvo na demonstração.");
        }, erro);
        d.show();
    }

    private void pagamento() {
        Dialog<ButtonType> d = dialogo("Finalizar venda", "pagamento-dialog");
        ComboBox<Cliente> cliente = combo(gateway.clientes(), "pagamento-cliente");
        TextField endereco = campo("", "Endereço de entrega", "pagamento-endereco");
        boolean fisico = gateway.itens().stream().anyMatch(i -> i.produto().tipo().equals("Físico"));
        endereco.setDisable(!fisico);
        Runnable preencherEndereco = () -> endereco.setText(cliente.getValue() == null ? "" : cliente.getValue().endereco());
        preencherEndereco.run();
        cliente.valueProperty().addListener((o, a, b) -> preencherEndereco.run());
        ComboBox<String> metodo = combo(List.of("PIX", "Dinheiro", "Cartão de crédito"), "pagamento-metodo");
        TextField recebido = campo("", "Ex.: 200,00", "pagamento-recebido");
        Label subtotal = texto("", "amount"), frete = texto("", "amount"), ajuste = texto("", "amount");
        Label total = texto("", "total"), troco = texto("", "subtitle");
        Label erro = texto("", "error");
        erro.setId("pagamento-erro");
        total.setId("pagamento-total");
        troco.setId("pagamento-troco");
        Runnable atualizar = () -> {
            erro.setText("");
            Resumo r = gateway.resumo(metodo.getValue());
            subtotal.setText(brl(r.subtotal()));
            frete.setText(brl(r.frete()));
            ajuste.setText(brl(r.ajuste()));
            total.setText(brl(r.total()));
            recebido.setDisable(!metodo.getValue().equals("Dinheiro"));
            if (metodo.getValue().equals("Dinheiro")) {
                try {
                    BigDecimal diferenca = decimal(recebido.getText()).subtract(r.total());
                    troco.setText(diferenca.signum() < 0 ? "Valor insuficiente. Faltam " + brl(diferenca.negate()) : "Troco: " + brl(diferenca));
                } catch (NumberFormatException ex) {
                    troco.setText("Informe o valor recebido para calcular o troco.");
                }
            } else {
                troco.setText("Confirmação manual de pagamento demonstrativo.");
            }
        };
        metodo.valueProperty().addListener((o, a, b) -> atualizar.run());
        recebido.textProperty().addListener((o, a, b) -> atualizar.run());
        atualizar.run();
        GridPane form = formulario();
        par(form, 0, "Cliente *", cliente, fisico ? "Endereço de entrega *" : "Digital · sem endereço obrigatório", endereco);
        par(form, 1, "Forma de pagamento *", metodo, "Valor recebido em dinheiro (R$)", recebido);
        endereco.textProperty().addListener((o, a, b) -> erro.setText(""));
        cliente.valueProperty().addListener((o, a, b) -> erro.setText(""));
        CheckBox confirmado = new CheckBox("Confirmo o recebimento demonstrativo e a baixa do estoque.");
        confirmado.setId("confirmacao-pagamento");
        confirmado.selectedProperty().addListener((o, a, b) -> erro.setText(""));
        VBox corpo = card("03 / Pagamento e entrega", form, new Separator(),
                linha(new VBox(5, texto("PRODUTOS", "eyebrow"), subtotal),
                        new VBox(5, texto("FRETE ÚNICO", "eyebrow"), frete),
                        new VBox(5, texto("DESCONTO / TAXA", "eyebrow"), ajuste)),
                texto("TOTAL A PAGAR", "eyebrow"), total, troco,
                texto("PIX: −5% nos produtos · Crédito: +3% no total com frete", "subtitle"),
                texto("Nenhuma cobrança é processada. O comprovante não tem valor fiscal.", "subtitle"), confirmado, erro);
        d.getDialogPane().setContent(corpo);
        ButtonType confirmar = new ButtonType("Confirmar pagamento", ButtonBar.ButtonData.OK_DONE);
        ButtonType voltar = new ButtonType("Voltar ao carrinho", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().setAll(voltar, confirmar);
        Button botao = (Button) d.getDialogPane().lookupButton(confirmar);
        botao.setId("concluir-pagamento");
        botao.getStyleClass().add("primary");
        botao.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            try {
                if (!confirmado.isSelected()) throw new IllegalArgumentException("Marque a confirmação de recebimento antes de concluir.");
                BigDecimal valor = metodo.getValue().equals("Dinheiro") ? decimal(recebido.getText()) : null;
                Venda venda = gateway.confirmar(cliente.getValue(), endereco.getText().trim(), metodo.getValue(), valor);
                d.close();
                mostrar("Frente de caixa");
                status.setText("Venda #" + venda.numero() + " concluída. Estoque atualizado.");
                comprovante(venda);
            } catch (Exception ex) {
                erro.setText(mensagem(ex));
                d.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
        d.show();
    }

    private Node historico() {
        TableView<Venda> tabela = tabela();
        tabela.setId("historico");
        coluna(tabela, "Venda", v -> "#" + v.numero(), 90);
        coluna(tabela, "Data", Venda::data, 150);
        coluna(tabela, "Cliente", Venda::cliente, 250);
        coluna(tabela, "Pagamento", Venda::metodo, 170);
        coluna(tabela, "Total", v -> brl(v.total()), 120);
        tabela.setItems(FXCollections.observableArrayList(gateway.vendas()));
        tabela.setPlaceholder(texto("Nenhuma venda concluída nesta sessão.", "muted"));
        VBox p = card("Vendas desta sessão", texto("O histórico é demonstrativo e será apagado quando o programa encerrar.", "subtitle"), tabela,
                fluxo(texto(gateway.vendas().size() + " venda(s) concluída(s)", "subtitle"),
                        botao("Abrir comprovante", "abrir-comprovante", () -> executar(() -> comprovante(selecionar(tabela))), "primary")));
        VBox.setVgrow(tabela, Priority.ALWAYS);
        return p;
    }

    private void comprovante(Venda venda) {
        Dialog<ButtonType> d = dialogo("Venda #" + venda.numero() + " · comprovante demonstrativo", "comprovante-dialog");
        TextArea textoCupom = new TextArea(venda.cupom());
        textoCupom.setId("texto-comprovante");
        textoCupom.setEditable(false);
        textoCupom.getStyleClass().add("receipt");
        textoCupom.setPrefRowCount(20);
        textoCupom.setPrefWidth(650);
        Label aviso = texto("", "subtitle");
        Button salvar = botao("Salvar TXT", "salvar-cupom", () -> executar(() -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar comprovante demonstrativo");
            chooser.setInitialFileName("comprovante-demo-" + venda.numero() + ".txt");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto UTF-8", "*.txt"));
            java.io.File arquivo = chooser.showSaveDialog(d.getDialogPane().getScene().getWindow());
            if (arquivo != null) {
                Files.writeString(arquivo.toPath(), venda.cupom(), StandardCharsets.UTF_8);
                aviso.setText("Comprovante salvo em " + arquivo.getAbsolutePath());
            }
        }), "");
        Button imprimir = botao("Imprimir", "imprimir-cupom", () -> executar(() -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) throw new IllegalArgumentException("Nenhuma impressora disponível neste computador.");
            if (job.showPrintDialog(d.getDialogPane().getScene().getWindow())) {
                Label papel = new Label(venda.cupom());
                papel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
                double largura = job.getJobSettings().getPageLayout().getPrintableWidth();
                papel.setWrapText(true);
                papel.setPrefWidth(largura);
                ScrollPane paginaImpressa = new ScrollPane(papel);
                new javafx.scene.Scene(paginaImpressa);
                paginaImpressa.applyCss();
                papel.autosize();
                double altura = job.getJobSettings().getPageLayout().getPrintableHeight();
                double escala = Math.min(1, altura / Math.max(1, papel.getLayoutBounds().getHeight()));
                papel.getTransforms().add(new javafx.scene.transform.Scale(escala, escala));
                if (job.printPage(papel)) job.endJob();
                else { job.cancelJob(); throw new IllegalArgumentException("A impressora não concluiu o trabalho."); }
            }
        }), "");
        d.getDialogPane().setContent(card("Venda concluída · estoque atualizado", texto("SEM VALOR FISCAL · SEM COBRANÇA REAL", "eyebrow"), textoCupom, fluxo(salvar, imprimir), aviso));
        d.getDialogPane().getButtonTypes().setAll(new ButtonType("Nova venda / fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        d.show();
    }

    private Node guia() {
        VBox roteiro = card("Roteiro para o professor Bruno",
                etapa("01", "O problema", "Vendas manuais geram erros de cálculo e perda do controle de estoque. Mostre como o PDV organiza esse processo."),
                etapa("02", "Cadastros", "Compare os campos de produto físico e digital. Mostre cadastro e edição de clientes e produtos."),
                etapa("03", "Carrinho e validação", "Adicione P001 e D001. Altere e remova itens. Tente P003 (estoque zero) e depois uma quantidade maior que o estoque de P001."),
                etapa("04", "Pagamento", "Compare PIX, dinheiro e cartão. Mostre o frete único, o desconto, a taxa e o troco. Voltar ao carrinho não altera o estoque."),
                etapa("05", "Venda concluída", "Confirme o recebimento, abra o comprovante e confira a baixa de estoque. Faça uma compra apenas digital: sem frete nem endereço obrigatório."),
                etapa("06", "Sua contribuição e integração", "PdvView é JavaFX; PdvGateway é o contrato; DemoGateway fornece a demonstração em memória. A herança de Produto e as estratégias de pagamento pertencem ao domínio final da equipe."),
                texto("Limites: sem banco, autenticação, cobrança real ou emissão fiscal. Os dados são apagados ao encerrar.", "subtitle"));
        ScrollPane scroll = new ScrollPane(roteiro);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private Node etapa(String numero, String titulo, String descricao) {
        Label detalhe = texto(descricao, "guide");
        return new VBox(5, texto(numero + " / " + titulo, "card-title"), detalhe);
    }

    private TableView<Produto> tabelaProdutos() {
        TableView<Produto> tabela = tabela();
        coluna(tabela, "Código", Produto::codigo, 75);
        coluna(tabela, "Produto", Produto::nome, 280);
        coluna(tabela, "Tipo", Produto::tipo, 75);
        coluna(tabela, "Preço", p -> brl(p.preco()), 95);
        TableColumn<Produto, Integer> estoque = coluna(tabela, "Estoque", Produto::estoque, 75);
        estoque.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean vazio) {
                super.updateItem(item, vazio);
                setText(vazio || item == null ? null : item == 0 ? "Esgotado" : item.toString());
                setStyle(!vazio && item != null && item == 0 ? "-fx-text-fill: #b13737; -fx-font-weight: bold;" : "");
            }
        });
        tabela.setItems(FXCollections.observableArrayList(gateway.produtos()));
        return tabela;
    }

    private static <T> TableView<T> tabela() {
        TableView<T> tabela = new TableView<>();
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPlaceholder(texto("Nenhum registro encontrado.", "muted"));
        tabela.setMinHeight(100);
        return tabela;
    }

    private static <T, V> TableColumn<T, V> coluna(TableView<T> tabela, String nome, Function<T, V> valor, double largura) {
        TableColumn<T, V> coluna = new TableColumn<>(nome);
        coluna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        coluna.setPrefWidth(largura);
        tabela.getColumns().add(coluna);
        return coluna;
    }

    private static <T> T selecionar(TableView<T> tabela) {
        T valor = tabela.getSelectionModel().getSelectedItem();
        if (valor == null) throw new IllegalArgumentException("Selecione uma linha da tabela antes de continuar.");
        return valor;
    }

    private Dialog<ButtonType> dialogo(String titulo, String id) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titulo);
        d.initOwner(getScene().getWindow());
        d.setResizable(true);
        d.getDialogPane().setId(id);
        d.getDialogPane().getStylesheets().add(PdvApplication.class.getResource("/pdv-av2.css").toExternalForm());
        return d;
    }

    private void salvar(Dialog<ButtonType> d, String titulo, Acao acao, Label erro) {
        ButtonType salvar = new ButtonType(titulo, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, salvar);
        ((Button) d.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Button b = (Button) d.getDialogPane().lookupButton(salvar);
        b.setId("salvar-formulario");
        b.getStyleClass().add("primary");
        b.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                acao.run();
            } catch (Exception ex) {
                erro.setText(mensagem(ex));
                d.getDialogPane().getScene().getWindow().sizeToScene();
                event.consume();
            }
        });
    }

    boolean perguntar(String titulo, String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensagem, ButtonType.YES, ButtonType.NO);
        alerta.initOwner(getScene().getWindow());
        alerta.setTitle(titulo);
        alerta.setHeaderText(titulo);
        alerta.getDialogPane().getStylesheets().add(PdvApplication.class.getResource("/pdv-av2.css").toExternalForm());
        ((Button) alerta.getDialogPane().lookupButton(ButtonType.YES)).setText("Sim");
        ((Button) alerta.getDialogPane().lookupButton(ButtonType.NO)).setText("Não");
        return alerta.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    @FunctionalInterface
    private interface Acao { void run() throws Exception; }

    private void executar(Acao acao) {
        try {
            acao.run();
        } catch (Exception ex) {
            Alert alerta = new Alert(Alert.AlertType.WARNING, mensagem(ex), ButtonType.OK);
            alerta.initOwner(getScene().getWindow());
            alerta.setTitle("Confira os dados");
            alerta.setHeaderText("Não foi possível concluir");
            alerta.getDialogPane().setId("alerta-validacao");
            alerta.getDialogPane().getStylesheets().add(PdvApplication.class.getResource("/pdv-av2.css").toExternalForm());
            alerta.show();
        }
    }

    private static String mensagem(Exception ex) {
        return ex instanceof NumberFormatException ? "Informe um número válido. Use vírgula ou ponto nos centavos e números inteiros no estoque." : ex.getMessage();
    }

    private static BigDecimal decimal(String valor) {
        return new BigDecimal(valor.trim().replace(",", ".")).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Label texto(String valor, String estilo) {
        Label l = new Label(valor);
        if (!estilo.isBlank()) l.getStyleClass().add(estilo);
        l.setWrapText(true);
        return l;
    }

    private static Button botao(String titulo, String id, Runnable acao, String estilo) {
        Button b = new Button(titulo);
        b.setId(id);
        if (!estilo.isBlank()) b.getStyleClass().add(estilo);
        b.setOnAction(e -> acao.run());
        return b;
    }

    private static TextField campo(String valor, String dica, String id) {
        TextField t = new TextField(valor);
        t.setPromptText(dica);
        t.setId(id);
        return t;
    }

    private static <T> ComboBox<T> combo(List<T> valores, String id) {
        ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(valores));
        c.setId(id);
        c.setMaxWidth(Double.MAX_VALUE);
        if (!valores.isEmpty()) c.getSelectionModel().selectFirst();
        return c;
    }

    private static HBox linha(Node... nodes) {
        HBox h = new HBox(12, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static FlowPane fluxo(Node... nodes) {
        FlowPane f = new FlowPane(10, 10, nodes);
        f.setAlignment(Pos.CENTER_LEFT);
        return f;
    }

    private static VBox card(String titulo, Node... nodes) {
        VBox v = new VBox(14);
        v.getStyleClass().add("card");
        v.getChildren().add(texto(titulo, "card-title"));
        v.getChildren().addAll(nodes);
        return v;
    }

    private static HBox valores(String descricao, String valor) {
        Region flex = new Region();
        HBox.setHgrow(flex, Priority.ALWAYS);
        return linha(texto(descricao, "muted"), flex, texto(valor, "amount"));
    }

    private static GridPane formulario() {
        GridPane g = new GridPane();
        g.setHgap(18);
        g.setVgap(14);
        g.setPrefWidth(680);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        g.getColumnConstraints().addAll(c1, c2);
        return g;
    }

    private static void par(GridPane grid, int linha, String label1, Node campo1, String label2, Node campo2) {
        grid.add(new VBox(6, texto(label1, "form-label"), campo1), 0, linha);
        grid.add(new VBox(6, texto(label2, "form-label"), campo2), 1, linha);
    }
}
