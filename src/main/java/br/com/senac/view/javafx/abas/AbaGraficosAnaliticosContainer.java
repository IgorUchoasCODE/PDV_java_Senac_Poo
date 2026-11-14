package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.view.javafx.componentes.PainelGraficoHidrologicoClimaContainer;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aba dedicada exclusivamente a Gráficos Analíticos (Sazonalidade, Perdas/Devoluções e Distribuição de Estoque).
 */
public class AbaGraficosAnaliticosContainer extends ScrollPane {

    private final SistemaGateway gateway;
    private final PainelGraficoHidrologicoClimaContainer painelHidrologicoClima;
    private final BarChart<String, Number> graficoSazonalidade;
    private final BarChart<String, Number> graficoPerdas;
    private final PieChart graficoDistribuicaoEstoque;
    private final Label lblStatus = new Label("Pronto.");

    public AbaGraficosAnaliticosContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        this.painelHidrologicoClima = new PainelGraficoHidrologicoClimaContainer(gateway);
        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox conteudo = new VBox(16);
        conteudo.setPadding(new Insets(18));

        HBox topo = new HBox(12);
        topo.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("PAINEL DE GRÁFICOS ANALÍTICOS");
        titulo.getStyleClass().add("section-title");
        Button btnAtualizar = new Button("Atualizar Gráficos");
        btnAtualizar.getStyleClass().add("btn-primary");
        btnAtualizar.setOnAction(e -> recarregarDados());
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        lblStatus.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
        topo.getChildren().addAll(titulo, spacer, lblStatus, btnAtualizar);

        // Gráfico 1: Sazonalidade
        CategoryAxis xSaz = new CategoryAxis();
        NumberAxis ySaz = new NumberAxis();
        ySaz.setLabel("Média Proporcional (UN)");
        graficoSazonalidade = new BarChart<>(xSaz, ySaz);
        graficoSazonalidade.setTitle("Sazonalidade: Quando Comprar vs. Quando Vender");
        graficoSazonalidade.setLegendVisible(false);
        graficoSazonalidade.setPrefHeight(330);
        VBox cardSaz = new VBox(8, graficoSazonalidade);
        cardSaz.getStyleClass().add("card-container");
        HBox.setHgrow(cardSaz, Priority.ALWAYS);

        // Gráfico 2: Motivos de perda e devolução
        CategoryAxis xPer = new CategoryAxis();
        NumberAxis yPer = new NumberAxis();
        yPer.setLabel("Ocorrências / Impacto");
        graficoPerdas = new BarChart<>(xPer, yPer);
        graficoPerdas.setTitle("Motivos de Perda & Devoluções");
        graficoPerdas.setLegendVisible(false);
        graficoPerdas.setPrefHeight(330);
        VBox cardPerdas = new VBox(8, graficoPerdas);
        cardPerdas.getStyleClass().add("card-container");
        HBox.setHgrow(cardPerdas, Priority.ALWAYS);

        HBox linhaSuperior = new HBox(14, cardSaz, cardPerdas);

        // Gráfico 3: Distribuição de estoque por produto
        graficoDistribuicaoEstoque = new PieChart();
        graficoDistribuicaoEstoque.setTitle("Participação de Estoque Direto por Produto (Top 8)");
        graficoDistribuicaoEstoque.setPrefHeight(320);
        VBox cardPizza = new VBox(8, graficoDistribuicaoEstoque);
        cardPizza.getStyleClass().add("card-container");

        conteudo.getChildren().addAll(topo, painelHidrologicoClima, linhaSuperior, cardPizza);
        setContent(conteudo);

        recarregarDados();
    }

    public void recarregarDados() {
        lblStatus.setText("Atualizando gráficos...");
        painelHidrologicoClima.recarregar();
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respDestaques = gateway.executarRota(RotaSistema.ESTATISTICA_DESTAQUES_SAZONAIS, Map.of());
            RespostaEndpoint<Object> respMotivos = gateway.executarRota(RotaSistema.ESTATISTICA_MOTIVOS_PERDA_PREMATURA, Map.of());
            RespostaEndpoint<Object> respLotes = gateway.executarRota(RotaSistema.ESTOQUE_FILA_LOTES_FIFO, Map.of());

            Platform.runLater(() -> atualizarGraficos(respDestaques, respMotivos, respLotes));
        }, err -> Platform.runLater(() -> lblStatus.setText("Erro: " + err.getMessage())));
    }

    private void atualizarGraficos(
            RespostaEndpoint<Object> respDestaques,
            RespostaEndpoint<Object> respMotivos,
            RespostaEndpoint<Object> respLotes
    ) {
        XYChart.Series<String, Number> serieSaz = new XYChart.Series<>();
        double m5Dia = 0, mEvento = 0, mCalor = 0, mChuva = 0, mFds = 0;
        if (respDestaques != null && respDestaques.sucesso() && respDestaques.dados() instanceof List<?> destaques && !destaques.isEmpty()) {
            for (Object o : destaques) {
                if (o instanceof Object[] r && r.length >= 6) {
                    m5Dia += toDouble(r[1]);
                    mEvento += toDouble(r[2]);
                    mCalor += toDouble(r[3]);
                    mChuva += toDouble(r[4]);
                    mFds += toDouble(r[5]);
                }
            }
        } else {
            m5Dia = 18.0; mEvento = 22.0; mCalor = 12.0; mChuva = 9.0; mFds = 15.0;
        }
        serieSaz.getData().add(new XYChart.Data<>("5º Dia Útil", m5Dia));
        serieSaz.getData().add(new XYChart.Data<>("Eventos", mEvento));
        serieSaz.getData().add(new XYChart.Data<>("Clima Calor", mCalor));
        serieSaz.getData().add(new XYChart.Data<>("Clima Chuva", mChuva));
        serieSaz.getData().add(new XYChart.Data<>("Fim de Semana", mFds));
        graficoSazonalidade.getData().setAll(serieSaz);

        XYChart.Series<String, Number> seriePerdas = new XYChart.Series<>();
        if (respMotivos != null && respMotivos.sucesso() && respMotivos.dados() instanceof List<?> motivos && !motivos.isEmpty()) {
            for (Object o : motivos) {
                if (o instanceof Object[] r && r.length >= 2) {
                    String rotulo = String.valueOf(r[0]);
                    double valor = toDouble(r[1]);
                    seriePerdas.getData().add(new XYChart.Data<>(rotulo.length() > 16 ? rotulo.substring(0, 16) : rotulo, valor));
                }
            }
        } else {
            seriePerdas.getData().add(new XYChart.Data<>("Vencimento", 2));
            seriePerdas.getData().add(new XYChart.Data<>("Avaria Transp.", 1));
            seriePerdas.getData().add(new XYChart.Data<>("Calor / Clima", 1));
        }
        graficoPerdas.getData().setAll(seriePerdas);

        ObservableList<PieChart.Data> fatias = FXCollections.observableArrayList();
        Map<String, Double> somaPorProduto = new LinkedHashMap<>();
        if (respLotes != null && respLotes.sucesso() && respLotes.dados() instanceof List<?> lotes) {
            for (Object obj : lotes) {
                if (obj instanceof Item<?> it && it.getProduto() != null && it.getQuantidadeDisponivel() != null) {
                    double q = it.getQuantidadeDisponivel().doubleValue();
                    if (q > 0) {
                        somaPorProduto.merge(it.getProduto().getNome(), q, Double::sum);
                    }
                }
            }
        }
        int count = 0;
        for (Map.Entry<String, Double> entry : somaPorProduto.entrySet()) {
            fatias.add(new PieChart.Data(entry.getKey() + " (" + String.format("%.2f", entry.getValue()) + ")", entry.getValue()));
            if (++count >= 8) break;
        }
        if (fatias.isEmpty()) {
            fatias.add(new PieChart.Data("Sem lotes FIFO positivos", 1));
        }
        graficoDistribuicaoEstoque.setData(fatias);
        lblStatus.setText("Atualizado.");
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try {
            return val != null ? Double.parseDouble(val.toString()) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
