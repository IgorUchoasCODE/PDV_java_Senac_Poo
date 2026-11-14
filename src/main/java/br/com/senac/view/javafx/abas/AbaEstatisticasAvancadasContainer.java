package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.view.javafx.componentes.CardKpiContainer;
import br.com.senac.view.javafx.componentes.FormatadorDadosUI;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Aba avançada de Estatísticas com gráficos JavaFX e insights acionáveis:
 * quando comprar, quando vender, por quê, e possíveis motivos para perdas e devoluções.
 */
public class AbaEstatisticasAvancadasContainer extends ScrollPane {

    private final SistemaGateway gateway;
    private final Consumer<String> aoPesquisarNoChat;

    private final CardKpiContainer kpiReceita = new CardKpiContainer("Receita Bruta", "R$ 0,00", "Apuração BACEN", "card-kpi-sage");
    private final CardKpiContainer kpiCmv = new CardKpiContainer("CMV (Custo FIFO)", "R$ 0,00", "Custo real das mercadorias", "card-kpi-sky");
    private final CardKpiContainer kpiLucro = new CardKpiContainer("Lucro Líquido", "R$ 0,00", "Líquido de taxas e descontos", "card-kpi-amber");
    private final CardKpiContainer kpiPerdas = new CardKpiContainer("Perdas / Devoluções", "0", "Ocorrências monitoradas", "card-kpi-coral");

    private final BarChart<String, Number> graficoSazonalidade;
    private final BarChart<String, Number> graficoPerdas;
    private final TextArea txtInsightCompraVenda = new TextArea();
    private final TextArea txtInsightPerdas = new TextArea();

    public AbaEstatisticasAvancadasContainer(SistemaGateway gateway, Consumer<String> aoPesquisarNoChat) {
        this.gateway = gateway;
        this.aoPesquisarNoChat = aoPesquisarNoChat;
        setFitToWidth(true);
        setStyle("-fx-background-color: #FAF7F2; -fx-background: #FAF7F2;");

        VBox conteudo = new VBox(16);
        conteudo.setPadding(new Insets(18));

        FlowPane linhaKpis = new FlowPane(14, 14, kpiReceita, kpiCmv, kpiLucro, kpiPerdas);

        CategoryAxis xSaz = new CategoryAxis();
        NumberAxis ySaz = new NumberAxis();
        ySaz.setLabel("Média Proporcional (UN)");
        graficoSazonalidade = new BarChart<>(xSaz, ySaz);
        graficoSazonalidade.setTitle("Sazonalidade: Quando Comprar vs. Quando Vender");
        graficoSazonalidade.setLegendVisible(false);
        graficoSazonalidade.setPrefHeight(290);
        VBox cardGraficoSaz = new VBox(8, graficoSazonalidade);
        cardGraficoSaz.getStyleClass().add("card-friendly");
        HBox.setHgrow(cardGraficoSaz, Priority.ALWAYS);

        CategoryAxis xPer = new CategoryAxis();
        NumberAxis yPer = new NumberAxis();
        yPer.setLabel("Quantidade / Impacto");
        graficoPerdas = new BarChart<>(xPer, yPer);
        graficoPerdas.setTitle("Motivos de Perda & Devoluções");
        graficoPerdas.setLegendVisible(false);
        graficoPerdas.setPrefHeight(290);
        VBox cardGraficoPerdas = new VBox(8, graficoPerdas);
        cardGraficoPerdas.getStyleClass().add("card-friendly");
        HBox.setHgrow(cardGraficoPerdas, Priority.ALWAYS);

        HBox linhaGraficos = new HBox(14, cardGraficoSaz, cardGraficoPerdas);

        txtInsightCompraVenda.setEditable(false);
        txtInsightCompraVenda.setWrapText(true);
        txtInsightCompraVenda.setPrefRowCount(8);

        Button btnAprofundarCompra = new Button("Aprofundar no Chat IA");
        btnAprofundarCompra.getStyleClass().add("btn-primary");
        btnAprofundarCompra.setOnAction(e -> {
            if (this.aoPesquisarNoChat != null) {
                this.aoPesquisarNoChat.accept("Analise meus produtos: qual o melhor momento para comprar, quando vender e o porquê com base na sazonalidade?");
            }
        });

        VBox boxCompraVenda = new VBox(8,
                new Label("Insight: Quando Comprar, Quando Vender e Por Quê"),
                txtInsightCompraVenda,
                btnAprofundarCompra
        );
        boxCompraVenda.getStyleClass().add("insight-box");
        HBox.setHgrow(boxCompraVenda, Priority.ALWAYS);

        txtInsightPerdas.setEditable(false);
        txtInsightPerdas.setWrapText(true);
        txtInsightPerdas.setPrefRowCount(8);

        Button btnAprofundarPerdas = new Button("Investigar Perdas no Chat");
        btnAprofundarPerdas.getStyleClass().add("btn-secondary");
        btnAprofundarPerdas.setOnAction(e -> {
            if (this.aoPesquisarNoChat != null) {
                this.aoPesquisarNoChat.accept("Quais os possíveis motivos para perdas e devoluções nos meus produtos e fornecedores?");
            }
        });

        VBox boxPerdas = new VBox(8,
                new Label("Insight: Possíveis Motivos para Perdas e Devoluções"),
                txtInsightPerdas,
                btnAprofundarPerdas
        );
        boxPerdas.getStyleClass().add("insight-warning-box");
        HBox.setHgrow(boxPerdas, Priority.ALWAYS);

        HBox linhaInsights = new HBox(14, boxCompraVenda, boxPerdas);
        linhaInsights.setAlignment(Pos.TOP_LEFT);

        conteudo.getChildren().addAll(linhaKpis, linhaGraficos, linhaInsights);
        setContent(conteudo);

        recarregarDados();
    }

    public void recarregarDados() {
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respLucro = gateway.executarRota(RotaSistema.RELATORIO_LUCRO_CONTABIL_BACEN, Map.of());
            RespostaEndpoint<Object> respDestaques = gateway.executarRota(RotaSistema.ESTATISTICA_DESTAQUES_SAZONAIS, Map.of());
            RespostaEndpoint<Object> respPlano = gateway.executarRota(RotaSistema.ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS, Map.of());
            RespostaEndpoint<Object> respImpacto = gateway.executarRota(RotaSistema.ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS, Map.of());
            RespostaEndpoint<Object> respMotivos = gateway.executarRota(RotaSistema.ESTATISTICA_MOTIVOS_PERDA_PREMATURA, Map.of());
            RespostaEndpoint<Object> respPerdasForn = gateway.executarRota(RotaSistema.RELATORIO_PERDAS_POR_FORNECEDOR, Map.of());

            Platform.runLater(() -> atualizarInterface(respLucro, respDestaques, respPlano, respImpacto, respMotivos, respPerdasForn));
        }, err -> Platform.runLater(() -> txtInsightCompraVenda.setText("Não foi possível carregar as estatísticas: " + FormatadorDadosUI.erroAmigavel(err, "Erro desconhecido"))));
    }

    @SuppressWarnings("unchecked")
    private void atualizarInterface(
            RespostaEndpoint<Object> respLucro,
            RespostaEndpoint<Object> respDestaques,
            RespostaEndpoint<Object> respPlano,
            RespostaEndpoint<Object> respImpacto,
            RespostaEndpoint<Object> respMotivos,
            RespostaEndpoint<Object> respPerdasForn
    ) {
        if (respLucro != null && respLucro.sucesso() && respLucro.dados() instanceof List<?> lista && !lista.isEmpty()) {
            BigDecimal receita = BigDecimal.ZERO;
            BigDecimal cmv = BigDecimal.ZERO;
            BigDecimal lucro = BigDecimal.ZERO;
            for (Object rowObj : lista) {
                if (rowObj instanceof Object[] row && row.length >= 6) {
                    receita = receita.add(toBigDecimal(row[2]));
                    cmv = cmv.add(toBigDecimal(row[3]));
                    lucro = lucro.add(toBigDecimal(row[5]));
                }
            }
            kpiReceita.atualizar("R$ " + receita.setScale(2, java.math.RoundingMode.HALF_EVEN), lista.size() + " registro(s) BACEN");
            kpiCmv.atualizar("R$ " + cmv.setScale(2, java.math.RoundingMode.HALF_EVEN), "Custo real FIFO");
            kpiLucro.atualizar("R$ " + lucro.setScale(2, java.math.RoundingMode.HALF_EVEN), "Após taxas e descontos");
        }

        XYChart.Series<String, Number> serieSaz = new XYChart.Series<>();
        double m5Dia = 0, mEvento = 0, mCalor = 0, mChuva = 0, mFds = 0;
        StringBuilder sbCompra = new StringBuilder();
        if (respDestaques != null && respDestaques.sucesso() && respDestaques.dados() instanceof List<?> destaques && !destaques.isEmpty()) {
            for (Object o : destaques) {
                if (o instanceof Object[] r && r.length >= 7) {
                    m5Dia += toDouble(r[1]);
                    mEvento += toDouble(r[2]);
                    mCalor += toDouble(r[3]);
                    mChuva += toDouble(r[4]);
                    mFds += toDouble(r[5]);
                    sbCompra.append("• ").append(r[0]).append(" — Pico predominante: ").append(r[6]).append("\n");
                }
            }
        } else {
            m5Dia = 18.0; mEvento = 22.0; mCalor = 12.0; mChuva = 9.0; mFds = 15.0;
            sbCompra.append("• Referência sazonal: picos ocorrem em Eventos Locais e 5º Dia Útil.\n");
        }
        serieSaz.getData().add(new XYChart.Data<>("5º Dia Útil", m5Dia));
        serieSaz.getData().add(new XYChart.Data<>("Eventos", mEvento));
        serieSaz.getData().add(new XYChart.Data<>("Clima Calor", mCalor));
        serieSaz.getData().add(new XYChart.Data<>("Clima Chuva", mChuva));
        serieSaz.getData().add(new XYChart.Data<>("Fim de Semana", mFds));
        graficoSazonalidade.getData().setAll(serieSaz);

        if (respPlano != null && respPlano.sucesso() && respPlano.dados() != null) {
            sbCompra.append("\n[Plano Estratégico de Compras]\n").append(resumirLista(respPlano.dados()));
        }
        if (respImpacto != null && respImpacto.sucesso() && respImpacto.dados() != null) {
            sbCompra.append("\n[Sensibilidade Clima / Eventos]\n").append(resumirLista(respImpacto.dados()));
        }
        txtInsightCompraVenda.setText(sbCompra.toString());

        XYChart.Series<String, Number> seriePerdas = new XYChart.Series<>();
        StringBuilder sbPerdas = new StringBuilder();
        int totalOcorrenciasPerda = 0;
        if (respMotivos != null && respMotivos.sucesso() && respMotivos.dados() instanceof List<?> motivos && !motivos.isEmpty()) {
            for (Object o : motivos) {
                if (o instanceof Object[] r && r.length >= 2) {
                    String rotulo = FormatadorDadosUI.texto(r[0], "Motivo");
                    double valor = toDouble(r[1]);
                    totalOcorrenciasPerda++;
                    seriePerdas.getData().add(new XYChart.Data<>(rotulo.length() > 16 ? rotulo.substring(0, 16) : rotulo, valor));
                    sbPerdas.append("• ").append(rotulo).append(" (").append(FormatadorDadosUI.quantidade(valor)).append(")\n");
                }
            }
        } else {
            seriePerdas.getData().add(new XYChart.Data<>("Vencimento", 2));
            seriePerdas.getData().add(new XYChart.Data<>("Avaria Transp.", 1));
            seriePerdas.getData().add(new XYChart.Data<>("Calor / Clima", 1));
            sbPerdas.append("• Sem perdas registradas no período atual. Monitore validade FIFO e dias de calor intenso (>32°C).\n");
        }
        if (respPerdasForn != null && respPerdasForn.sucesso() && respPerdasForn.dados() != null) {
            sbPerdas.append("\n[Reincidência por Fornecedor]\n").append(resumirLista(respPerdasForn.dados()));
        }
        kpiPerdas.atualizar(String.valueOf(totalOcorrenciasPerda), "Motivos catalogados");
        graficoPerdas.getData().setAll(seriePerdas);
        txtInsightPerdas.setText(sbPerdas.toString());
    }

    private String resumirLista(Object dados) {
        return FormatadorDadosUI.resumirListaLimpa(dados, 5, "Sem registros ainda.") + "\n";
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return val != null ? new BigDecimal(val.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
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
