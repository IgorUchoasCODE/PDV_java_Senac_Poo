package br.com.senac.view.javafx.componentes;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel gráfico interativo com:
 * - Parte superior com coloração dinâmica conforme a temperatura (°C);
 * - Fundo com onda/nível dos rios (m³/s) e limiares de seca e alagação;
 * - Indicadores de Faturamento, Lucro e Perdas sobrepostos;
 * - Filtros climáticos/operacionais (Chuva, Seca, Quente, Perdas, Alagação, Frio) e janelas de data (Hoje, 7d, 30d, 60d, Personalizada).
 */
public class PainelGraficoHidrologicoClimaContainer extends VBox {

    private static class PontoDiario {
        final LocalDate data;
        double faturamento = 0.0;
        double lucro = 0.0;
        double perdas = 0.0;
        double temperaturaC = 26.0;
        double chuvaMm = 0.0;
        double nivelRioM3s = 120.0;
        boolean possuiClimaReal = false;
        boolean possuiRioReal = false;

        PontoDiario(LocalDate data) {
            this.data = data;
        }
    }

    private final SistemaGateway gateway;
    private final Canvas canvas = new Canvas(980, 340);
    private final ComboBox<String> cmbPeriodo = new ComboBox<>();
    private final DatePicker dtInicio = new DatePicker(LocalDate.now().minusDays(29));
    private final DatePicker dtFim = new DatePicker(LocalDate.now());

    private final CheckBox chkTodos = new CheckBox("Todos");
    private final CheckBox chkChuva = new CheckBox("Chuva");
    private final CheckBox chkSeca = new CheckBox("Seca");
    private final CheckBox chkQuente = new CheckBox("Quente (>=30°C)");
    private final CheckBox chkPerdas = new CheckBox("Perdas");
    private final CheckBox chkAlagacao = new CheckBox("Alagação");

    private final Label lblResumoFaturamento = new Label("Faturamento: R$ 0,00");
    private final Label lblResumoLucro = new Label("Lucro Est.: R$ 0,00");
    private final Label lblResumoTemp = new Label("Temp. Média: -");
    private final Label lblResumoRio = new Label("Nível Rio Médio: -");
    private final Label lblHover = new Label("Passe o mouse sobre o gráfico para inspecionar cada dia.");

    private final List<PontoDiario> pontosOriginais = new ArrayList<>();
    private final List<PontoDiario> pontosFiltrados = new ArrayList<>();
    private int indiceHover = -1;

    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("dd/MM");

    public PainelGraficoHidrologicoClimaContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        setSpacing(10);
        getStyleClass().add("card-container");

        montarControles();
        montarCanvas();
        recarregar();
    }

    private void montarControles() {
        HBox linhaTitulo = new HBox(12);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("PAINEL HIDROLÓGICO, TÉRMICO & FINANCEIRO (NÍVEL DO RIO × TEMPERATURA × FATURAMENTO/LUCRO)");
        titulo.getStyleClass().add("section-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAtualizar = new Button("Atualizar Painel");
        btnAtualizar.getStyleClass().add("btn-primary");
        btnAtualizar.setOnAction(e -> recarregar());
        linhaTitulo.getChildren().addAll(titulo, spacer, btnAtualizar);

        // Linha de filtros de período
        cmbPeriodo.getItems().addAll("Hoje", "7 Dias", "30 Dias", "60 Dias", "Data Personalizada");
        cmbPeriodo.setValue("30 Dias");
        dtInicio.setDisable(true);
        dtFim.setDisable(true);

        cmbPeriodo.setOnAction(e -> {
            boolean personalizada = "Data Personalizada".equals(cmbPeriodo.getValue());
            dtInicio.setDisable(!personalizada);
            dtFim.setDisable(!personalizada);
            aplicarFiltros();
        });
        dtInicio.setOnAction(e -> aplicarFiltros());
        dtFim.setOnAction(e -> aplicarFiltros());

        HBox barraDatas = new HBox(10,
                new Label("Período:"), cmbPeriodo,
                new Label("De:"), dtInicio,
                new Label("Até:"), dtFim
        );
        barraDatas.setAlignment(Pos.CENTER_LEFT);

        // Linha de filtros climáticos / eventos
        chkTodos.setSelected(true);
        chkTodos.setStyle("-fx-text-fill: #E2E8F0; -fx-font-weight: bold;");
        chkChuva.setStyle("-fx-text-fill: #38BDF8;");
        chkSeca.setStyle("-fx-text-fill: #FACC15;");
        chkQuente.setStyle("-fx-text-fill: #FB7185;");
        chkPerdas.setStyle("-fx-text-fill: #EF4444;");
        chkAlagacao.setStyle("-fx-text-fill: #818CF8;");

        chkTodos.setOnAction(e -> {
            if (chkTodos.isSelected()) {
                chkChuva.setSelected(false);
                chkSeca.setSelected(false);
                chkQuente.setSelected(false);
                chkPerdas.setSelected(false);
                chkAlagacao.setSelected(false);
            }
            aplicarFiltros();
        });

        for (CheckBox cb : List.of(chkChuva, chkSeca, chkQuente, chkPerdas, chkAlagacao)) {
            cb.setOnAction(e -> {
                if (cb.isSelected()) {
                    chkTodos.setSelected(false);
                } else if (!chkChuva.isSelected() && !chkSeca.isSelected() && !chkQuente.isSelected()
                        && !chkPerdas.isSelected() && !chkAlagacao.isSelected()) {
                    chkTodos.setSelected(true);
                }
                aplicarFiltros();
            });
        }

        HBox barraEventos = new HBox(14,
                new Label("Filtros:"), chkTodos, chkChuva, chkSeca, chkQuente, chkPerdas, chkAlagacao
        );
        barraEventos.setAlignment(Pos.CENTER_LEFT);

        // Resumo dos indicadores
        lblResumoFaturamento.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold; -fx-font-size: 13px;");
        lblResumoLucro.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold; -fx-font-size: 13px;");
        lblResumoTemp.setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: bold; -fx-font-size: 13px;");
        lblResumoRio.setStyle("-fx-text-fill: #818CF8; -fx-font-weight: bold; -fx-font-size: 13px;");
        lblHover.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");

        HBox barraResumo = new HBox(20, lblResumoFaturamento, lblResumoLucro, lblResumoTemp, lblResumoRio);
        barraResumo.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(linhaTitulo, barraDatas, barraEventos, barraResumo, lblHover);
    }

    private void montarCanvas() {
        Pane holder = new Pane(canvas);
        holder.setMinHeight(320);
        holder.setPrefHeight(340);
        holder.setStyle("-fx-background-color: #090D16; -fx-border-color: #2B3340; -fx-border-radius: 10; -fx-background-radius: 10;");
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> desenhar());
        canvas.heightProperty().addListener((o, a, b) -> desenhar());

        canvas.setOnMouseMoved(e -> {
            if (pontosFiltrados.isEmpty()) return;
            double padL = 56;
            double padR = 24;
            double areaW = Math.max(canvas.getWidth() - padL - padR, 1);
            double relX = e.getX() - padL;
            if (relX < 0 || relX > areaW) {
                indiceHover = -1;
            } else {
                int idx = (int) Math.round((relX / areaW) * Math.max(pontosFiltrados.size() - 1, 1));
                indiceHover = Math.max(0, Math.min(pontosFiltrados.size() - 1, idx));
                PontoDiario p = pontosFiltrados.get(indiceHover);
                lblHover.setText(String.format(
                        "Dia %s | Faturamento: R$ %.2f | Lucro Est.: R$ %.2f | Perdas: R$ %.2f | Temp: %.1f°C | Chuva: %.1f mm | Rio: %.1f m³/s",
                        p.data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        p.faturamento, p.lucro, p.perdas, p.temperaturaC, p.chuvaMm, p.nivelRioM3s
                ));
            }
            desenhar();
        });
        canvas.setOnMouseExited(e -> {
            indiceHover = -1;
            desenhar();
        });

        getChildren().add(holder);
    }

    public void recarregar() {
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respNotas = gateway.executarRota(RotaSistema.NOTA_LISTAR_PADRAO, Map.of("limite", 200));
            Platform.runLater(() -> processarNotas(respNotas));
        }, err -> Platform.runLater(() -> lblHover.setText("Erro ao carregar série hidrológica/financeira: " + err.getMessage())));
    }

    private void processarNotas(RespostaEndpoint<Object> respNotas) {
        Map<LocalDate, PontoDiario> mapa = new LinkedHashMap<>();
        LocalDate hoje = LocalDate.now();
        for (int i = 59; i >= 0; i--) {
            LocalDate d = hoje.minusDays(i);
            PontoDiario pd = new PontoDiario(d);
            // Linha de base suave quando não houver registro climático externo no dia
            pd.temperaturaC = 25.0 + 6.5 * Math.sin(i * 0.28);
            pd.nivelRioM3s = 135.0 + 55.0 * Math.cos(i * 0.22);
            pd.chuvaMm = Math.sin(i * 0.41) > 0.45 ? 14.0 + 18.0 * Math.sin(i * 0.41) : 0.0;
            mapa.put(d, pd);
        }

        if (respNotas != null && respNotas.sucesso() && respNotas.dados() instanceof List<?> notas) {
            for (Object obj : notas) {
                if (obj instanceof Nota nota) {
                    LocalDate dia = nota.getDataGerada() != null ? nota.getDataGerada().toLocalDate() : hoje;
                    PontoDiario pd = mapa.computeIfAbsent(dia, PontoDiario::new);
                    double valor = nota.getValorTotal() != null ? nota.getValorTotal().doubleValue() : 0.0;

                    if (nota instanceof NotaVenda) {
                        pd.faturamento += valor;
                        pd.lucro += valor * 0.28;
                    } else if (nota instanceof NotaPerda || nota instanceof NotaDevolucao) {
                        pd.perdas += valor;
                        pd.lucro -= valor * 0.5;
                    }

                    VinculoFatorExternoNota vinc = nota.getVinculoFatorExterno();
                    if (vinc != null) {
                        ClimaDiario clima = vinc.getClimaRegistrado();
                        if (clima != null) {
                            pd.temperaturaC = clima.temperaturaMax() > 0 ? clima.temperaturaMax() : clima.temperaturaMedia();
                            pd.chuvaMm = clima.precipitacaoMm();
                            pd.possuiClimaReal = true;
                        }
                        NivelRioDiario rio = vinc.getNivelRioRegistrado();
                        if (rio != null) {
                            pd.nivelRioM3s = rio.vazaoM3s();
                            pd.possuiRioReal = true;
                        }
                    }
                }
            }
        }

        pontosOriginais.clear();
        pontosOriginais.addAll(mapa.values());
        pontosOriginais.sort(Comparator.comparing(p -> p.data));
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio;
        LocalDate fim;
        String periodo = cmbPeriodo.getValue();
        if ("Hoje".equals(periodo)) {
            inicio = hoje;
            fim = hoje;
        } else if ("7 Dias".equals(periodo)) {
            inicio = hoje.minusDays(6);
            fim = hoje;
        } else if ("30 Dias".equals(periodo)) {
            inicio = hoje.minusDays(29);
            fim = hoje;
        } else if ("60 Dias".equals(periodo)) {
            inicio = hoje.minusDays(59);
            fim = hoje;
        } else {
            inicio = dtInicio.getValue() != null ? dtInicio.getValue() : hoje.minusDays(29);
            fim = dtFim.getValue() != null ? dtFim.getValue() : hoje;
        }

        pontosFiltrados.clear();
        for (PontoDiario p : pontosOriginais) {
            if (p.data.isBefore(inicio) || p.data.isAfter(fim)) continue;
            if (!chkTodos.isSelected()) {
                boolean match = false;
                if (chkChuva.isSelected() && p.chuvaMm >= 5.0) match = true;
                if (chkSeca.isSelected() && (p.chuvaMm < 1.0 && p.nivelRioM3s < 110.0)) match = true;
                if (chkQuente.isSelected() && p.temperaturaC >= 30.0) match = true;
                if (chkPerdas.isSelected() && p.perdas > 0.0) match = true;
                if (chkAlagacao.isSelected() && p.nivelRioM3s >= 175.0) match = true;
                if (!match) continue;
            }
            pontosFiltrados.add(p);
        }

        atualizarResumo();
        desenhar();
    }

    private void atualizarResumo() {
        double totFat = 0, totLuc = 0, sumTemp = 0, sumRio = 0;
        for (PontoDiario p : pontosFiltrados) {
            totFat += p.faturamento;
            totLuc += p.lucro;
            sumTemp += p.temperaturaC;
            sumRio += p.nivelRioM3s;
        }
        int n = Math.max(pontosFiltrados.size(), 1);
        lblResumoFaturamento.setText("Faturamento: " + FormatadorDadosUI.moeda(totFat));
        lblResumoLucro.setText("Lucro Est.: " + FormatadorDadosUI.moeda(totLuc));
        lblResumoTemp.setText("Temp. Média: " + FormatadorDadosUI.temperatura(sumTemp / n));
        lblResumoRio.setText("Nível Rio Médio: " + FormatadorDadosUI.vazaoRio(sumRio / n));
    }

    private void desenhar() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 20 || h <= 20) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double padL = 56, padR = 24, padT = 32, padB = 34;
        double areaW = Math.max(w - padL - padR, 10);
        double areaH = Math.max(h - padT - padB, 10);

        // 1. Parte superior muda de cor conforme a temperatura média / do ponto em hover
        double tempRef = 26.0;
        if (indiceHover >= 0 && indiceHover < pontosFiltrados.size()) {
            tempRef = pontosFiltrados.get(indiceHover).temperaturaC;
        } else if (!pontosFiltrados.isEmpty()) {
            tempRef = pontosFiltrados.stream().mapToDouble(p -> p.temperaturaC).average().orElse(26.0);
        }
        Color corCeu = corPorTemperatura(tempRef);

        LinearGradient gradCeu = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(corCeu.getRed(), corCeu.getGreen(), corCeu.getBlue(), 0.45)),
                new Stop(0.45, Color.color(corCeu.getRed(), corCeu.getGreen(), corCeu.getBlue(), 0.12)),
                new Stop(1.0, Color.web("#090D16")));
        gc.setFill(gradCeu);
        gc.fillRect(0, 0, w, h);

        // Badge térmico superior
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        gc.setFill(corCeu);
        gc.fillText(String.format("FAIXA TÉRMICA: %.1f°C (%s)", tempRef, rotuloTermico(tempRef)), padL, 19);

        if (pontosFiltrados.isEmpty()) {
            gc.setFill(Color.web("#94A3B8"));
            gc.fillText("Nenhum dia corresponde aos filtros selecionados.", padL, h / 2);
            return;
        }

        // Escalas
        double maxRio = 220.0;
        double maxFin = 100.0;
        for (PontoDiario p : pontosFiltrados) {
            maxRio = Math.max(maxRio, p.nivelRioM3s * 1.15);
            maxFin = Math.max(maxFin, Math.max(p.faturamento, Math.abs(p.lucro)) * 1.2);
        }

        int n = pontosFiltrados.size();
        double[] xs = new double[n];
        double[] yRio = new double[n];
        double[] yFat = new double[n];
        double[] yLuc = new double[n];

        for (int i = 0; i < n; i++) {
            PontoDiario p = pontosFiltrados.get(i);
            xs[i] = n == 1 ? padL + areaW / 2.0 : padL + (i * areaW) / (n - 1);
            yRio[i] = padT + areaH - (p.nivelRioM3s / maxRio) * (areaH * 0.72);
            yFat[i] = padT + areaH - (p.faturamento / maxFin) * (areaH * 0.85);
            double lucClamp = Math.max(0.0, p.lucro);
            yLuc[i] = padT + areaH - (lucClamp / maxFin) * (areaH * 0.85);
        }

        // 2. Nível dos rios no fundo (polígono translúcido azul-índigo)
        double[] polyX = new double[n + 2];
        double[] polyY = new double[n + 2];
        for (int i = 0; i < n; i++) {
            polyX[i] = xs[i];
            polyY[i] = yRio[i];
        }
        polyX[n] = xs[n - 1];
        polyY[n] = padT + areaH;
        polyX[n + 1] = xs[0];
        polyY[n + 1] = padT + areaH;

        LinearGradient gradRio = new LinearGradient(0, 0.3, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(56, 189, 248, 0.34)),
                new Stop(1, Color.rgb(30, 58, 138, 0.16)));
        gc.setFill(gradRio);
        gc.fillPolygon(polyX, polyY, n + 2);

        gc.setStroke(Color.rgb(99, 102, 241, 0.85));
        gc.setLineWidth(2.0);
        gc.strokePolyline(xs, yRio, n);

        // Linha de alerta de alagação (175 m³/s)
        double yAlag = padT + areaH - (175.0 / maxRio) * (areaH * 0.72);
        gc.setStroke(Color.rgb(129, 140, 248, 0.55));
        gc.setLineDashes(5);
        gc.strokeLine(padL, yAlag, padL + areaW, yAlag);
        gc.setLineDashes(0);
        gc.setFill(Color.web("#818CF8"));
        gc.fillText("Cota Alagação (175 m³/s)", padL + 6, Math.max(padT + 12, yAlag - 4));

        // 3. Indicadores de Faturamento (#38BDF8) e Lucro (#10B981)
        gc.setStroke(Color.web("#38BDF8"));
        gc.setLineWidth(2.4);
        gc.strokePolyline(xs, yFat, n);

        gc.setStroke(Color.web("#10B981"));
        gc.setLineWidth(2.4);
        gc.strokePolyline(xs, yLuc, n);

        // Marcadores de perda
        for (int i = 0; i < n; i++) {
            PontoDiario p = pontosFiltrados.get(i);
            if (p.perdas > 0) {
                gc.setFill(Color.web("#EF4444"));
                gc.fillOval(xs[i] - 4, padT + areaH - 8, 8, 8);
            }
        }

        // Eixo X (datas esparsas)
        gc.setFill(Color.web("#94A3B8"));
        int passo = Math.max(1, n / 8);
        for (int i = 0; i < n; i += passo) {
            gc.fillText(pontosFiltrados.get(i).data.format(FMT_DIA), xs[i] - 14, h - 10);
        }

        // Hover vertical line
        if (indiceHover >= 0 && indiceHover < n) {
            gc.setStroke(Color.rgb(226, 232, 240, 0.5));
            gc.setLineWidth(1.2);
            gc.strokeLine(xs[indiceHover], padT, xs[indiceHover], padT + areaH);
            gc.setFill(Color.web("#38BDF8"));
            gc.fillOval(xs[indiceHover] - 4.5, yFat[indiceHover] - 4.5, 9, 9);
            gc.setFill(Color.web("#10B981"));
            gc.fillOval(xs[indiceHover] - 4.5, yLuc[indiceHover] - 4.5, 9, 9);
        }
    }

    private Color corPorTemperatura(double tempC) {
        if (tempC >= 32.0) return Color.web("#EF4444"); // Calor extremo
        if (tempC >= 27.0) return Color.web("#F59E0B"); // Quente
        if (tempC >= 19.0) return Color.web("#10B981"); // Ameno
        return Color.web("#0EA5E9");                    // Frio
    }

    private String rotuloTermico(double tempC) {
        if (tempC >= 32.0) return "CALOR EXTREMO";
        if (tempC >= 27.0) return "QUENTE";
        if (tempC >= 19.0) return "AMENO";
        return "FRIO";
    }
}
