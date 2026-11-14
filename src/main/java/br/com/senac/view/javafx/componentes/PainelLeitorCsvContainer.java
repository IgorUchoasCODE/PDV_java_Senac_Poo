package br.com.senac.view.javafx.componentes;

import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Container reutilizável de Leitura CSV com pré-visualização e configuração
 * interativa de mapeamento de colunas para importação pelas rotas oficiais.
 */
public class PainelLeitorCsvContainer extends VBox {

    private static final Map<TipoImportacaoCsv, List<String>> COLUNAS_DESTINO = Map.of(
            TipoImportacaoCsv.UNIDADES_MEDIDA, List.of("IGNORAR", "sigla", "nome", "descricao", "base"),
            TipoImportacaoCsv.FORMAS_PAGAMENTO, List.of("IGNORAR", "codigo", "nome", "descricao", "permiteAVista", "permiteAPrazo", "permiteParcelado", "maxParcelas", "taxaOperadoraPercentual", "descontoAVistaPercentual"),
            TipoImportacaoCsv.PESSOAS, List.of("IGNORAR", "nome", "razaoSocial", "papel", "cpf", "cnpj", "documento", "limite"),
            TipoImportacaoCsv.PRODUTOS_FISICOS, List.of("IGNORAR", "nome", "descricao", "precoVenda", "siglaUm", "alturaCm", "larguraCm", "comprimentoCm", "pesoBrutoKg", "pesoLiquidoKg"),
            TipoImportacaoCsv.PRODUTOS_DIGITAIS, List.of("IGNORAR", "nome", "descricao", "precoVenda", "tituloLink", "urlLink"),
            TipoImportacaoCsv.NOTAS_COMPRA, List.of("IGNORAR", "idFornecedor", "nomeFornecedor", "idProduto", "nomeProduto", "quantidade", "custo", "dataValidade", "dataGerada", "codigoForma", "descricao"),
            TipoImportacaoCsv.NOTAS_VENDA, List.of("IGNORAR", "idCliente", "nomeCliente", "idProduto", "nomeProduto", "quantidade", "dataGerada", "codigoForma", "parcelas", "descontoCupom", "cupom", "descricao")
    );

    private final SistemaGateway gateway;
    private final ComboBox<TipoImportacaoCsv> cmbTipo = new ComboBox<>();
    private final ComboBox<String> cmbDelimitador = new ComboBox<>();
    private final Label lblArquivo = new Label("Nenhum arquivo selecionado (use 'Carregar CSV' ou 'Exemplo').");
    private final GridPane gradeMapeamento = new GridPane();
    private final TableView<Map<String, String>> tabelaPreview = new TableView<>();
    private final TextArea txtResultado = new TextArea();
    private final Map<String, ComboBox<String>> combosMapeamento = new LinkedHashMap<>();

    private TabelaCsv tabelaAtual;
    private String origemAtual = "csv-ui";

    public PainelLeitorCsvContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        getStyleClass().add("card-friendly");
        setSpacing(12);

        Label lblTitulo = new Label("Leitor CSV & Configuração de Colunas");
        lblTitulo.getStyleClass().add("section-title");

        cmbTipo.setItems(FXCollections.observableArrayList(TipoImportacaoCsv.values()));
        cmbTipo.setValue(TipoImportacaoCsv.PRODUTOS_FISICOS);
        cmbTipo.setOnAction(e -> reconstruirGradeMapeamento());

        cmbDelimitador.setItems(FXCollections.observableArrayList("Auto", ";", ",", "\\t"));
        cmbDelimitador.setValue("Auto");

        Button btnAbrir = new Button("Carregar CSV");
        btnAbrir.getStyleClass().add("btn-secondary");
        btnAbrir.setOnAction(e -> selecionarArquivo());

        Button btnExemplo = new Button("Carregar Exemplo");
        btnExemplo.getStyleClass().add("btn-secondary");
        btnExemplo.setOnAction(e -> carregarExemplo());

        Button btnImportar = new Button("Importar com Mapeamento");
        btnImportar.getStyleClass().add("btn-primary");
        btnImportar.setOnAction(e -> executarImportacao());

        HBox barraControles = new HBox(10,
                new Label("Destino:"), cmbTipo,
                new Label("Separador:"), cmbDelimitador,
                btnAbrir, btnExemplo, btnImportar
        );
        barraControles.setAlignment(Pos.CENTER_LEFT);

        lblArquivo.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        gradeMapeamento.setHgap(12);
        gradeMapeamento.setVgap(8);
        gradeMapeamento.setPadding(new Insets(8));
        gradeMapeamento.setStyle("-fx-background-color: #0D1117; -fx-background-radius: 8; -fx-border-color: #2B3340; -fx-border-radius: 8;");

        tabelaPreview.setPrefHeight(180);
        VBox.setVgrow(tabelaPreview, Priority.SOMETIMES);

        txtResultado.setEditable(false);
        txtResultado.setPrefRowCount(4);
        txtResultado.setPromptText("O resumo da importação CSV aparecerá aqui...");

        getChildren().addAll(
                lblTitulo,
                barraControles,
                lblArquivo,
                new Label("Mapeamento de Colunas (CSV → Campo do Sistema):"),
                gradeMapeamento,
                new Label("Pré-visualização dos Dados:"),
                tabelaPreview,
                txtResultado
        );
    }

    private Character obterDelimitadorSelecionado() {
        String sel = cmbDelimitador.getValue();
        if (";".equals(sel)) return ';';
        if (",".equals(sel)) return ',';
        if ("\\t".equals(sel)) return '\t';
        return null;
    }

    private void selecionarArquivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Documento CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv", "*.txt"));
        File arquivo = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (arquivo == null) {
            return;
        }
        try {
            Path path = arquivo.toPath();
            tabelaAtual = gateway.lerCsv(path, obterDelimitadorSelecionado());
            origemAtual = arquivo.getName();
            lblArquivo.setText("Arquivo: " + arquivo.getAbsolutePath() + " (" + tabelaAtual.linhas().size() + " linhas | delimitador '" + tabelaAtual.delimitadorDetectado() + "')");
            atualizarPreviewEMapeamento();
        } catch (Exception ex) {
            txtResultado.setText("Erro ao ler arquivo CSV: " + ex.getMessage());
        }
    }

    private void carregarExemplo() {
        String exemplo = switch (cmbTipo.getValue()) {
            case PESSOAS -> "nome_completo;tipo_pessoa;cpf_cnpj;credito\nMaria Oliveira;CLIENTE;111.222.333-44;2500.00\nGranja Sol Nascente;FORNECEDOR;99.888.777/0001-66;15000.00";
            default -> "produto_nome;detalhes;preco;unidade\nOvo Caipira Bandeja 30;Bandeja fresca;24.90;UN\nMel Silvestre 500g;Pote vidro;32.00;UN";
        };
        try {
            tabelaAtual = gateway.lerCsvTexto(exemplo, null);
            origemAtual = "exemplo-csv";
            lblArquivo.setText("Exemplo carregado (" + tabelaAtual.linhas().size() + " linhas | delimitador '" + tabelaAtual.delimitadorDetectado() + "')");
            atualizarPreviewEMapeamento();
        } catch (Exception ex) {
            txtResultado.setText("Erro ao carregar exemplo: " + ex.getMessage());
        }
    }

    private void atualizarPreviewEMapeamento() {
        tabelaPreview.getColumns().clear();
        tabelaPreview.getItems().clear();
        if (tabelaAtual == null) {
            return;
        }
        for (String col : tabelaAtual.cabecalhos()) {
            TableColumn<Map<String, String>, String> tc = new TableColumn<>(col);
            tc.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getOrDefault(col, "")));
            tc.setPrefWidth(140);
            tabelaPreview.getColumns().add(tc);
        }
        int limite = Math.min(20, tabelaAtual.linhas().size());
        tabelaPreview.setItems(FXCollections.observableArrayList(tabelaAtual.linhas().subList(0, limite)));
        reconstruirGradeMapeamento();
    }

    private void reconstruirGradeMapeamento() {
        gradeMapeamento.getChildren().clear();
        combosMapeamento.clear();
        if (tabelaAtual == null || tabelaAtual.cabecalhos().isEmpty()) {
            gradeMapeamento.add(new Label("Carregue um arquivo CSV para configurar as colunas."), 0, 0);
            return;
        }
        List<String> destinos = COLUNAS_DESTINO.getOrDefault(cmbTipo.getValue(), List.of("IGNORAR"));
        int row = 0;
        int colIdx = 0;
        for (String cabecalho : tabelaAtual.cabecalhos()) {
            ComboBox<String> cmb = new ComboBox<>(FXCollections.observableArrayList(destinos));
            cmb.setValue(sugerirColunaDestino(cabecalho, destinos));
            combosMapeamento.put(cabecalho, cmb);

            HBox par = new HBox(6, new Label(cabecalho + " →"), cmb);
            par.setAlignment(Pos.CENTER_LEFT);
            gradeMapeamento.add(par, colIdx, row);
            colIdx++;
            if (colIdx >= 2) {
                colIdx = 0;
                row++;
            }
        }
    }

    private String sugerirColunaDestino(String cabecalho, List<String> destinos) {
        String norm = cabecalho.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        for (String dest : destinos) {
            String dNorm = dest.toLowerCase(Locale.ROOT);
            if (!"ignorar".equals(dNorm) && (norm.equals(dNorm) || norm.contains(dNorm) || dNorm.contains(norm))) {
                return dest;
            }
        }
        if (norm.contains("preco") || norm.contains("valor")) return destinos.contains("precoVenda") ? "precoVenda" : destinos.get(0);
        if (norm.contains("un") || norm.contains("sigla")) return destinos.contains("siglaUm") ? "siglaUm" : destinos.get(0);
        if (norm.contains("desc") || norm.contains("detalhe")) return destinos.contains("descricao") ? "descricao" : destinos.get(0);
        if (norm.contains("nome") || norm.contains("produto")) return destinos.contains("nome") ? "nome" : destinos.get(0);
        return "IGNORAR";
    }

    private void executarImportacao() {
        if (tabelaAtual == null || tabelaAtual.isVazia()) {
            txtResultado.setText("Carregue um documento CSV antes de importar.");
            return;
        }
        Map<String, String> mapeamento = new LinkedHashMap<>();
        combosMapeamento.forEach((orig, cmb) -> mapeamento.put(orig, cmb.getValue()));
        TipoImportacaoCsv tipo = cmbTipo.getValue();
        txtResultado.setText("Importando registros pelas rotas oficiais...");

        gateway.executarEmBackground(() -> {
            ResultadoImportacaoCsv res = gateway.importarCsvMapeado(origemAtual, tabelaAtual, tipo, mapeamento, false);
            Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Importação concluída — Total: ").append(res.totalLinhas())
                  .append(" | Sucessos: ").append(res.sucessos())
                  .append(" | Falhas: ").append(res.falhas()).append("\n");
                res.erros().forEach(e -> sb.append("  Linha ").append(e.numeroLinha()).append(": ").append(e.mensagem()).append("\n"));
                txtResultado.setText(sb.toString());
            });
        }, err -> Platform.runLater(() -> txtResultado.setText("Erro na importação CSV: " + err.getMessage())));
    }
}
