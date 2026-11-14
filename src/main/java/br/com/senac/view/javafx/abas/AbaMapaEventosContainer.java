package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Aba especializada: Mapa de Localização da Loja e Eventos Locais (Sympla / MinC / Sazonais),
 * permitindo visualizar o raio de impacto dos eventos em torno das coordenadas do usuário.
 */
public class AbaMapaEventosContainer extends BorderPane {

    private static class EventoMapeado {
        final String nome;
        final String origem;
        final String data;
        final double lat;
        final double lon;
        final double distanciaKm;

        EventoMapeado(String nome, String origem, String data, double lat, double lon, double distanciaKm) {
            this.nome = nome;
            this.origem = origem;
            this.data = data;
            this.lat = lat;
            this.lon = lon;
            this.distanciaKm = distanciaKm;
        }
    }

    private final SistemaGateway gateway;
    private final Consumer<String> aoPerguntarChat;

    private final TextField txtLat = new TextField("-15.6014");
    private final TextField txtLon = new TextField("-56.0979");
    private final TextField txtRaioKm = new TextField("25");
    private final Label lblStatus = new Label("Localização central configurada.");

    private final Canvas canvas = new Canvas(680, 520);
    private final TableView<EventoMapeado> tabelaEventos = new TableView<>();
    private final List<EventoMapeado> eventos = new ArrayList<>();
    private EventoMapeado eventoSelecionado;

    public AbaMapaEventosContainer(SistemaGateway gateway, Consumer<String> aoPerguntarChat) {
        this.gateway = gateway;
        this.aoPerguntarChat = aoPerguntarChat;
        setPadding(new Insets(14));
        montarLayout();
        recarregarEventos();
    }

    private void montarLayout() {
        HBox barra = new HBox(10);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 12, 0));

        txtLat.setPrefWidth(100);
        txtLon.setPrefWidth(100);
        txtRaioKm.setPrefWidth(65);

        Button btnAplicar = new Button("Centralizar Localização");
        btnAplicar.getStyleClass().add("btn-secondary");
        btnAplicar.setOnAction(e -> recarregarEventos());

        Button btnAtualizar = new Button("Atualizar Eventos");
        btnAtualizar.getStyleClass().add("btn-primary");
        btnAtualizar.setOnAction(e -> recarregarEventos());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        lblStatus.setStyle("-fx-text-fill: #38BDF8; -fx-font-size: 12px;");

        barra.getChildren().addAll(
                new Label("Minha Lat:"), txtLat,
                new Label("Lon:"), txtLon,
                new Label("Raio (km):"), txtRaioKm,
                btnAplicar, btnAtualizar, spacer, lblStatus
        );
        setTop(barra);

        Pane holder = new Pane(canvas);
        holder.setStyle("-fx-background-color: #0B1220; -fx-border-color: #334155; -fx-border-radius: 10; -fx-background-radius: 10;");
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> desenharMapa());
        canvas.heightProperty().addListener((o, a, b) -> desenharMapa());
        setCenter(holder);

        VBox painelDireito = new VBox(10);
        painelDireito.setPrefWidth(360);
        painelDireito.setPadding(new Insets(14));
        painelDireito.getStyleClass().add("card-container");
        BorderPane.setMargin(painelDireito, new Insets(0, 0, 0, 12));

        Label lblLista = new Label("EVENTOS PRÓXIMOS À MINHA LOCALIZAÇÃO");
        lblLista.getStyleClass().add("section-title");

        configurarTabela();
        VBox.setVgrow(tabelaEventos, Priority.ALWAYS);

        Button btnAnalisarIa = new Button("Planejar Estoque para Eventos no Chat IA");
        btnAnalisarIa.getStyleClass().add("btn-primary");
        btnAnalisarIa.setMaxWidth(Double.MAX_VALUE);
        btnAnalisarIa.setOnAction(e -> {
            if (aoPerguntarChat != null) {
                aoPerguntarChat.accept("Considerando os eventos locais mapeados na minha região, quais produtos devo reforçar no estoque e quando comprar?");
            }
        });

        painelDireito.getChildren().addAll(lblLista, tabelaEventos, btnAnalisarIa);
        setRight(painelDireito);
    }

    private void configurarTabela() {
        TableColumn<EventoMapeado, String> cNome = new TableColumn<>("Evento");
        cNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nome));
        cNome.setPrefWidth(165);

        TableColumn<EventoMapeado, String> cOrig = new TableColumn<>("Fonte");
        cOrig.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().origem));
        cOrig.setPrefWidth(70);

        TableColumn<EventoMapeado, String> cDist = new TableColumn<>("Dist.");
        cDist.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f km", c.getValue().distanciaKm)));
        cDist.setPrefWidth(70);

        tabelaEventos.getColumns().setAll(List.of(cNome, cOrig, cDist));
        tabelaEventos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabelaEventos.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            eventoSelecionado = n;
            desenharMapa();
        });
    }

    public void recarregarEventos() {
        double latBase = parseDouble(txtLat.getText(), -15.6014);
        double lonBase = parseDouble(txtLon.getText(), -56.0979);
        double raioKm = Math.max(2.0, parseDouble(txtRaioKm.getText(), 25.0));
        Coordenada minhaPos = new Coordenada(latBase, lonBase, 50.0);

        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> resp = gateway.executarRota(RotaSistema.FATOR_EXTERNO_EVENTOS_LISTAR, Map.of("limite", 60));
            Platform.runLater(() -> preencherEventos(resp, minhaPos, raioKm));
        }, err -> Platform.runLater(() -> lblStatus.setText("Erro ao carregar eventos: " + err.getMessage())));
    }

    private void preencherEventos(RespostaEndpoint<Object> resp, Coordenada minhaPos, double raioKm) {
        eventos.clear();
        if (resp != null && resp.sucesso() && resp.dados() instanceof List<?> lista) {
            int idx = 0;
            for (Object obj : lista) {
                if (obj instanceof Map<?, ?> m) {
                    String nome = extrairTexto(m, "nome", "titulo", "Evento Local #" + (idx + 1));
                    String origem = extrairTexto(m, "fonte", "origem", "Local");
                    String data = extrairTexto(m, "dataInicio", "data", "-");
                    double eLat = parseDouble(m.get("latitude"), Double.NaN);
                    double eLon = parseDouble(m.get("longitude"), Double.NaN);
                    if (Double.isNaN(eLat) || Double.isNaN(eLon)) {
                        double ang = (idx * 2.39996);
                        double frac = 0.25 + ((idx % 5) * 0.14);
                        eLat = minhaPos.latitude() + Math.sin(ang) * (raioKm / 111.0) * frac;
                        eLon = minhaPos.longitude() + Math.cos(ang) * (raioKm / 111.0) * frac;
                    }
                    double dist = minhaPos.calcularDistanciaKm(eLat, eLon);
                    eventos.add(new EventoMapeado(nome, origem, data, eLat, eLon, dist));
                    idx++;
                }
            }
        }

        ObservableList<EventoMapeado> obs = FXCollections.observableArrayList(eventos);
        tabelaEventos.setItems(obs);
        lblStatus.setText(eventos.size() + " evento(s) mapeado(s) no raio de " + (int) raioKm + " km.");
        desenharMapa();
    }

    private void desenharMapa() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 20 || h <= 20) return;

        double latBase = parseDouble(txtLat.getText(), -15.6014);
        double lonBase = parseDouble(txtLon.getText(), -56.0979);
        double raioKm = Math.max(2.0, parseDouble(txtRaioKm.getText(), 25.0));

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0B1220"));
        gc.fillRect(0, 0, w, h);

        double cx = w / 2.0;
        double cy = h / 2.0;
        double maxR = Math.min(w, h) * 0.42;

        // Referência de curso do rio no mapa (faixa suave azul-rio)
        gc.setStroke(Color.rgb(2, 132, 199, 0.35));
        gc.setLineWidth(14);
        gc.strokeLine(0, cy + maxR * 0.45, w, cy - maxR * 0.25);
        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        gc.setFill(Color.web("#38BDF8"));
        gc.fillText("Leito do Rio (Referência Hidrológica)", 18, cy + maxR * 0.45 - 12);

        // Anéis de distância
        for (int i = 1; i <= 4; i++) {
            double r = maxR * (i / 4.0);
            gc.setStroke(Color.web("#1E293B"));
            gc.setLineWidth(1.2);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            gc.setFill(Color.web("#64748B"));
            gc.fillText(String.format("%.0f km", raioKm * (i / 4.0)), cx + 4, cy - r + 12);
        }

        // Eixos cardeais
        gc.setStroke(Color.web("#1E293B"));
        gc.strokeLine(cx, 16, cx, h - 16);
        gc.strokeLine(16, cy, w - 16, cy);

        // Minha localização (centro)
        gc.setFill(Color.web("#0D9488"));
        gc.fillOval(cx - 7, cy - 7, 14, 14);
        gc.setStroke(Color.web("#F8FAFC"));
        gc.setLineWidth(2);
        gc.strokeOval(cx - 7, cy - 7, 14, 14);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.setFill(Color.web("#2DD4BF"));
        gc.fillText("Minha Localização", cx + 12, cy + 4);

        // Eventos plotados
        gc.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        for (EventoMapeado ev : eventos) {
            double dLatKm = (ev.lat - latBase) * 111.0;
            double dLonKm = (ev.lon - lonBase) * 111.0 * Math.cos(Math.toRadians(latBase));
            double px = cx + (dLonKm / raioKm) * maxR;
            double py = cy - (dLatKm / raioKm) * maxR;

            boolean sel = ev == eventoSelecionado;
            gc.setFill(sel ? Color.web("#F59E0B") : Color.web("#38BDF8"));
            double rPt = sel ? 7.5 : 5.5;
            gc.fillOval(px - rPt, py - rPt, rPt * 2, rPt * 2);

            if (sel) {
                gc.setStroke(Color.web("#F8FAFC"));
                gc.setLineWidth(1.5);
                gc.strokeOval(px - rPt - 3, py - rPt - 3, (rPt + 3) * 2, (rPt + 3) * 2);
            }

            gc.setFill(Color.web("#E2E8F0"));
            String label = ev.nome.length() > 20 ? ev.nome.substring(0, 18) + ".." : ev.nome;
            gc.fillText(label, px + rPt + 5, py + 4);
        }
    }

    private String extrairTexto(Map<?, ?> m, String chave1, String chave2, String padrao) {
        Object v1 = m.get(chave1);
        if (v1 != null && !v1.toString().isBlank()) return v1.toString();
        Object v2 = m.get(chave2);
        if (v2 != null && !v2.toString().isBlank()) return v2.toString();
        return padrao;
    }

    private double parseDouble(Object val, double padrao) {
        if (val instanceof Number n) return n.doubleValue();
        try {
            return val != null ? Double.parseDouble(val.toString().replace(",", ".").trim()) : padrao;
        } catch (Exception e) {
            return padrao;
        }
    }
}
