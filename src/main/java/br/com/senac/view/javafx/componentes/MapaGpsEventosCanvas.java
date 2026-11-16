package br.com.senac.view.javafx.componentes;

import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA.EventoMapaGps;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mapa Topográfico & GPS Interativo de Eventos Regionais.
 * Recursos:
 * - Camada de Relevo Topográfico com sombreamento altimétrico, curvas de nível (20m–100m),
 *   calha hidrográfica com batimetria e escala dinâmica em km/m;
 * - Zoom contínuo e responsivo (Scroll do mouse focado no cursor, slider e botões + / – de Zoom 8 a 18);
 * - Tiles topográficos (OpenTopoMap) cacheados em disco quando online + base vetorial garantida offline;
 * - Botão '⛶ Expandir Mapa' para visualização em Tela Cheia.
 */
public class MapaGpsEventosCanvas extends StackPane {

    private static final int TILE_SIZE = 256;
    private static final int ZOOM_MIN = 8;
    private static final int ZOOM_MAX = 18;
    private static final Path DIR_CACHE_TILES = Paths.get("storage", "map_tiles_topo");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, Image> cacheMemoriaTiles = new ConcurrentHashMap<>();
    private final Set<String> tilesEmDownload = ConcurrentHashMap.newKeySet();

    private final boolean emTelaCheia;
    private final Canvas canvas = new Canvas(640, 340);
    private Coordenada coordLoja = new Coordenada(-3.0950, -60.0250, 25.0, "Manaus", "AM", "Chapada", "Av. Djalma Batista");
    private double centroLat = -3.0950;
    private double centroLon = -60.0250;
    private int zoom = 12;
    private boolean mostrarRelevoVetorial = true;
    private boolean tentarTilesOnline = true;

    private List<EventoMapaGps> eventosVisiveis = new ArrayList<>();
    private EventoMapaGps eventoSelecionado;
    private Consumer<EventoMapaGps> aoSelecionarEvento;

    private double dragInicioX;
    private double dragInicioY;
    private boolean arrastouMouse;
    private boolean atualizandoSlider = false;

    private final Slider sliderZoom = new Slider(ZOOM_MIN, ZOOM_MAX, zoom);
    private final Label lblModoAtual = FormatadorSeguroUI.criarBadgeCartoon(
            "Relevo + GPS • Zoom 12", "#FFFFFF", "#CBD5E1", "#0F172A");

    public MapaGpsEventosCanvas() {
        this(false);
    }

    private MapaGpsEventosCanvas(boolean emTelaCheia) {
        this.emTelaCheia = emTelaCheia;
        setMinHeight(emTelaCheia ? 560 : 320);
        setPrefHeight(emTelaCheia ? 720 : 350);
        setMaxHeight(emTelaCheia ? Double.MAX_VALUE : 420);
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: #E2E8F0; -fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-color: #CBD5E1; -fx-border-width: 1px;");

        Button btnZoomMais = FormatadorSeguroUI.criarBotaoCartoon("+", "#FFFFFF", "#CBD5E1", "#0F172A");
        Button btnZoomMenos = FormatadorSeguroUI.criarBotaoCartoon("–", "#FFFFFF", "#CBD5E1", "#0F172A");
        Button btnCentralizar = FormatadorSeguroUI.criarBotaoCartoon("📍 Loja", "#0F172A", "#0F172A", "#FFFFFF");
        Button btnRelevo = FormatadorSeguroUI.criarBotaoCartoon("🏔️ Relevo", "#ECFDF5", "#6EE7B7", "#065F46");
        Button btnTiles = FormatadorSeguroUI.criarBotaoCartoon("🌐 Mapa Online", "#FFFFFF", "#CBD5E1", "#0F172A");
        Button btnExpandir = FormatadorSeguroUI.criarBotaoCartoon("⛶ Expandir", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnExpandir.setVisible(!emTelaCheia);
        btnExpandir.setManaged(!emTelaCheia);

        sliderZoom.setPrefWidth(88);
        sliderZoom.setMajorTickUnit(1);
        sliderZoom.setMinorTickCount(0);
        sliderZoom.setSnapToTicks(true);
        sliderZoom.valueProperty().addListener((obs, o, n) -> {
            if (!atualizandoSlider) {
                int novoZ = (int) Math.round(n.doubleValue());
                if (novoZ != zoom) {
                    definirZoomCentrado(novoZ, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
                }
            }
        });

        btnZoomMais.setOnAction(e -> alterarZoom(1, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
        btnZoomMenos.setOnAction(e -> alterarZoom(-1, canvas.getWidth() / 2.0, canvas.getHeight() / 2.0));
        btnCentralizar.setOnAction(e -> centralizarNaLoja());
        btnRelevo.setOnAction(e -> {
            mostrarRelevoVetorial = !mostrarRelevoVetorial;
            FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnRelevo,
                    mostrarRelevoVetorial ? "#ECFDF5" : "#F1F5F9",
                    mostrarRelevoVetorial ? "#6EE7B7" : "#CBD5E1",
                    mostrarRelevoVetorial ? "#065F46" : "#475569", 12);
            atualizarBadge();
            desenhar();
        });
        btnTiles.setOnAction(e -> {
            tentarTilesOnline = !tentarTilesOnline;
            atualizarBadge();
            desenhar();
        });
        btnExpandir.setOnAction(e -> abrirTelaCheia());

        HBox controles = new HBox(5, btnZoomMenos, sliderZoom, btnZoomMais, btnCentralizar, btnRelevo, btnTiles, btnExpandir);
        controles.setAlignment(Pos.TOP_RIGHT);
        controles.setPadding(new Insets(8));
        controles.setPickOnBounds(false);

        StackPane.setAlignment(lblModoAtual, Pos.TOP_LEFT);
        StackPane.setMargin(lblModoAtual, new Insets(8));
        StackPane.setAlignment(controles, Pos.TOP_RIGHT);

        getChildren().addAll(canvas, lblModoAtual, controles);

        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(Math.max(320, n.doubleValue() - 4));
            desenhar();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(Math.max(260, n.doubleValue() - 4));
            desenhar();
        });

        configurarInteracaoMapa();
    }

    public void setAoSelecionarEvento(Consumer<EventoMapaGps> callback) {
        this.aoSelecionarEvento = callback;
    }

    public void definirDados(Coordenada loja, List<EventoMapaGps> eventos) {
        if (loja != null) {
            boolean mudou = (this.coordLoja == null || Math.abs(this.coordLoja.latitude() - loja.latitude()) > 0.0001);
            this.coordLoja = loja;
            if (mudou) {
                this.centroLat = loja.latitude();
                this.centroLon = loja.longitude();
            }
        }
        this.eventosVisiveis = eventos != null ? new ArrayList<>(eventos) : new ArrayList<>();
        if (eventoSelecionado != null) {
            eventoSelecionado = this.eventosVisiveis.stream()
                    .filter(ev -> ev.id().equals(eventoSelecionado.id()))
                    .findFirst().orElse(null);
        }
        atualizarBadge();
        desenhar();
    }

    public void focarEvento(EventoMapaGps evento) {
        this.eventoSelecionado = evento;
        if (evento != null && evento.coordenada() != null) {
            this.centroLat = (coordLoja.latitude() + evento.coordenada().latitude()) / 2.0;
            this.centroLon = (coordLoja.longitude() + evento.coordenada().longitude()) / 2.0;
            if (evento.distanciaKm() <= 1.5) definirZoomDirect(14);
            else if (evento.distanciaKm() <= 4.0) definirZoomDirect(13);
            else if (evento.distanciaKm() <= 9.0) definirZoomDirect(12);
            else definirZoomDirect(11);
        }
        atualizarBadge();
        desenhar();
    }

    public void centralizarNaLoja() {
        if (coordLoja != null) {
            this.centroLat = coordLoja.latitude();
            this.centroLon = coordLoja.longitude();
            definirZoomDirect(12);
        }
        atualizarBadge();
        desenhar();
    }

    private void definirZoomDirect(int z) {
        this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, z));
        atualizandoSlider = true;
        sliderZoom.setValue(this.zoom);
        atualizandoSlider = false;
    }

    private void alterarZoom(int delta, double anchorX, double anchorY) {
        definirZoomCentrado(zoom + delta, anchorX, anchorY);
    }

    private void definirZoomCentrado(int novoZoomDesejado, double anchorX, double anchorY) {
        int novoZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, novoZoomDesejado));
        if (novoZoom == zoom) return;

        // Mantém a coordenada geográfica sob o cursor fixa durante o zoom
        double worldOld = TILE_SIZE * Math.pow(2, zoom);
        double anchorPxOldX = lonParaPixelX(centroLon, worldOld) + (anchorX - canvas.getWidth() / 2.0);
        double anchorPxOldY = latParaPixelY(centroLat, worldOld) + (anchorY - canvas.getHeight() / 2.0);
        double anchorLon = pixelXParaLon(anchorPxOldX, worldOld);
        double anchorLat = pixelYParaLat(anchorPxOldY, worldOld);

        zoom = novoZoom;
        double worldNew = TILE_SIZE * Math.pow(2, zoom);
        double newCentroPxX = lonParaPixelX(anchorLon, worldNew) - (anchorX - canvas.getWidth() / 2.0);
        double newCentroPxY = latParaPixelY(anchorLat, worldNew) - (anchorY - canvas.getHeight() / 2.0);
        centroLon = pixelXParaLon(newCentroPxX, worldNew);
        centroLat = pixelYParaLat(newCentroPxY, worldNew);

        atualizandoSlider = true;
        sliderZoom.setValue(zoom);
        atualizandoSlider = false;

        atualizarBadge();
        desenhar();
    }

    private void atualizarBadge() {
        String modo = mostrarRelevoVetorial ? (tentarTilesOnline ? "Topográfico + GPS" : "Relevo Vetorial") : "Mapa Padrão";
        lblModoAtual.setText(modo + " • Zoom " + zoom + "x");
    }

    private void configurarInteracaoMapa() {
        canvas.setOnMousePressed(e -> {
            dragInicioX = e.getX();
            dragInicioY = e.getY();
            arrastouMouse = false;
        });
        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - dragInicioX;
            double dy = e.getY() - dragInicioY;
            if (Math.hypot(dx, dy) > 2) arrastouMouse = true;
            dragInicioX = e.getX();
            dragInicioY = e.getY();

            double worldSize = TILE_SIZE * Math.pow(2, zoom);
            double centroPxX = lonParaPixelX(centroLon, worldSize) - dx;
            double centroPxY = latParaPixelY(centroLat, worldSize) - dy;
            centroLon = pixelXParaLon(centroPxX, worldSize);
            centroLat = pixelYParaLat(centroPxY, worldSize);
            desenhar();
        });
        canvas.setOnMouseClicked(e -> {
            if (!arrastouMouse) tratarCliquePin(e.getX(), e.getY());
        });

        // Captura scroll tanto no StackPane quanto no Canvas e consome para o ScrollPane pai nunca roubar o zoom!
        this.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            e.consume();
            double delta = e.getDeltaY() != 0 ? e.getDeltaY() : e.getTextDeltaY();
            if (delta > 0) alterarZoom(1, e.getX(), e.getY());
            else if (delta < 0) alterarZoom(-1, e.getX(), e.getY());
        });
    }

    private void abrirTelaCheia() {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) {
            modal.initOwner(getScene().getWindow());
        }
        modal.setTitle("Mapa Topográfico & GPS de Eventos (Tela Cheia) — Sazonalisei ERP");
        modal.setWidth(1220);
        modal.setHeight(780);
        modal.setMaximized(true);

        MapaGpsEventosCanvas mapaFull = new MapaGpsEventosCanvas(true);
        mapaFull.coordLoja = this.coordLoja;
        mapaFull.centroLat = this.centroLat;
        mapaFull.centroLon = this.centroLon;
        mapaFull.mostrarRelevoVetorial = this.mostrarRelevoVetorial;
        mapaFull.tentarTilesOnline = this.tentarTilesOnline;
        mapaFull.eventosVisiveis = new ArrayList<>(this.eventosVisiveis);
        mapaFull.eventoSelecionado = this.eventoSelecionado;
        mapaFull.definirZoomDirect(this.zoom);
        mapaFull.setAoSelecionarEvento(ev -> {
            this.eventoSelecionado = ev;
            if (this.aoSelecionarEvento != null) this.aoSelecionarEvento.accept(ev);
        });

        modal.setScene(new Scene(mapaFull));
        modal.show();
        Platform.runLater(mapaFull::desenhar);
    }

    private void tratarCliquePin(double mx, double my) {
        for (EventoMapaGps ev : eventosVisiveis) {
            double[] xy = coordenadaParaCanvas(ev.coordenada());
            if (Math.hypot(mx - xy[0], my - xy[1]) <= 22) {
                this.eventoSelecionado = ev;
                desenhar();
                if (aoSelecionarEvento != null) aoSelecionarEvento.accept(ev);
                return;
            }
        }
    }

    private void desenhar() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 40 || h <= 40) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // 1. Tiles topográficos online primeiro se habilitados (para ficarem como camada base real)
        g.setFill(Color.web("#EFF6EF"));
        g.fillRect(0, 0, w, h);

        boolean temAlgumTile = false;
        if (tentarTilesOnline) {
            temAlgumTile = desenharTilesSobrepostos(g, w, h);
        }

        // 2. Camada de Relevo Topográfico Vetorial (garantida offline e complementa o relevo!)
        if (!temAlgumTile || mostrarRelevoVetorial) {
            desenharBaseTopograficaERelevo(g, w, h, temAlgumTile ? 0.38 : 1.0);
        }

        // 3. Anéis de distância reais ao redor da loja (2 km, 5 km, 15 km)
        desenharAneisDistancia(g);

        // 4. Linha tracejada Loja <-> Evento selecionado
        double[] xyLoja = coordenadaParaCanvas(coordLoja);
        if (eventoSelecionado != null && eventoSelecionado.coordenada() != null) {
            double[] xyEv = coordenadaParaCanvas(eventoSelecionado.coordenada());
            g.setStroke(Color.web("#B45309"));
            g.setLineWidth(2.6);
            g.setLineDashes(6, 5);
            g.strokeLine(xyLoja[0], xyLoja[1], xyEv[0], xyEv[1]);
            g.setLineDashes(null);

            double mx = (xyLoja[0] + xyEv[0]) / 2.0;
            double my = (xyLoja[1] + xyEv[1]) / 2.0;
            desenharEtiqueta(g, mx, my - 10,
                    String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f km", eventoSelecionado.distanciaKm()),
                    "#FFFBEB", "#D97706", "#78350F");
        }

        // 5. Pins dos eventos
        for (EventoMapaGps ev : eventosVisiveis) {
            double[] xy = coordenadaParaCanvas(ev.coordenada());
            if (xy[0] < -35 || xy[0] > w + 35 || xy[1] < -35 || xy[1] > h + 35) continue;
            boolean sel = eventoSelecionado != null && eventoSelecionado.id().equals(ev.id());
            desenharPinEvento(g, xy[0], xy[1], ev, sel);
        }

        // 6. Pin da Loja
        desenharPinLoja(g, xyLoja[0], xyLoja[1]);

        // 7. Barra de Escala Topográfica + Bússola (N)
        desenharEscalaEBussola(g, w, h);
    }

    private void desenharBaseTopograficaERelevo(GraphicsContext g, double w, double h, double opacidade) {
        g.save();
        g.setGlobalAlpha(opacidade);

        // Fundo hipsométrico (planície fluvial em creme/verde claro)
        if (opacidade > 0.8) {
            g.setFill(Color.web("#F3F6EE"));
            g.fillRect(0, 0, w, h);
        }

        // Platôs de relevo e curvas de nível dinâmicas ancoradas na loja + colinas amazônicas
        if (mostrarRelevoVetorial) {
            double baseLat = coordLoja != null ? coordLoja.latitude() : -3.0950;
            double baseLon = coordLoja != null ? coordLoja.longitude() : -60.0250;

            // Platô Norte/Flores (Elevação 80m -> 100m)
            desenharMorroCurvasNivel(g, baseLat + 0.028, baseLon + 0.005, 0.042, 0.026,
                    new String[]{"40m", "60m", "80m", "100m"}, "#E7E5D0", "#D6CEAA", "#C8B88A");

            // Platô Leste/Inpa (Elevação 60m -> 85m)
            desenharMorroCurvasNivel(g, baseLat - 0.005, baseLon + 0.044, 0.032, 0.024,
                    new String[]{"40m", "60m", "85m"}, "#E5EAD7", "#D1DFB7", "#BBD094");

            // Crista Oeste/Ponta Negra (Elevação 40m -> 75m)
            desenharMorroCurvasNivel(g, baseLat + 0.022, baseLon - 0.058, 0.036, 0.022,
                    new String[]{"30m", "55m", "75m"}, "#EAEAD6", "#D9D2AE", "#C7B882");

            // Áreas verdes / parques ambientais
            desenharManchaVerde(g, baseLat + 0.014, baseLon + 0.019, "Parque do Mindu");
            desenharManchaVerde(g, baseLat + 0.001, baseLon + 0.039, "Bosque INPA");
        }

        // Grade topográfica métrica suave
        g.setStroke(Color.rgb(148, 163, 184, 0.25));
        g.setLineWidth(0.7);
        for (double x = 0; x < w; x += 48) g.strokeLine(x, 0, x, h);
        for (double y = 0; y < h; y += 48) g.strokeLine(0, y, w, y);

        // Calha principal do Rio Negro com sombreamento batimétrico (várzea -> canal fundo)
        double[][] rioNegroExterno = {
                {-3.055, -60.175}, {-3.082, -60.115}, {-3.118, -60.075},
                {-3.146, -60.030}, {-3.150, -59.978}, {-3.142, -59.910},
                {-3.205, -59.910}, {-3.205, -60.175}
        };
        double[][] rioNegroFundo = {
                {-3.070, -60.175}, {-3.095, -60.115}, {-3.130, -60.072},
                {-3.155, -60.028}, {-3.160, -59.975}, {-3.152, -59.910},
                {-3.205, -59.910}, {-3.205, -60.175}
        };
        desenharPoligonoGeo(g, rioNegroExterno, "#BFDBFE", "#60A5FA", 1.5);
        desenharPoligonoGeo(g, rioNegroFundo, "#93C5FD", "#3B82F6", 1.2);

        // Igarapé do Mindu (baixada fluvial)
        desenharCursoIgarape(g, -3.050, -59.960, -3.085, -60.008, -3.115, -60.045);

        // Corredores viários principais
        desenharVia(g, -3.132, -60.024, -3.040, -60.025, true);
        desenharVia(g, -3.088, -60.028, -3.015, -59.995, true);
        desenharVia(g, -3.105, -60.028, -3.067, -60.105, false);
        desenharVia(g, -3.096, -60.025, -3.091, -59.964, false);
        desenharVia(g, -3.135, -60.024, -3.118, -59.978, false);

        // Marcos de bairros
        desenharMarcoBairro(g, -3.130, -60.023, "Centro / Porto");
        desenharMarcoBairro(g, -3.095, -60.025, "Chapada");
        desenharMarcoBairro(g, -3.083, -60.028, "Flores / Arena");
        desenharMarcoBairro(g, -3.104, -60.012, "Adrianópolis");
        desenharMarcoBairro(g, -3.067, -60.101, "Ponta Negra");
        desenharMarcoBairro(g, -3.080, -60.008, "Parque 10");
        desenharMarcoBairro(g, -3.119, -59.984, "Distrito Ind.");
        desenharMarcoBairro(g, -3.034, -59.982, "Cidade Nova");

        g.restore();
    }

    private void desenharMorroCurvasNivel(
            GraphicsContext g,
            double latCentro, double lonCentro,
            double raioLat, double raioLon,
            String[] cotas,
            String... coresCamadas) {

        double[] centroPx = coordenadaParaCanvas(new Coordenada(latCentro, lonCentro, 0));
        double[] bordaPx = coordenadaParaCanvas(new Coordenada(latCentro + raioLat, lonCentro + raioLon, 0));
        double rx = Math.abs(bordaPx[0] - centroPx[0]);
        double ry = Math.abs(bordaPx[1] - centroPx[1]);
        if (rx < 10 || ry < 10) return;

        for (int i = 0; i < cotas.length; i++) {
            double fator = 1.0 - (i * (0.72 / Math.max(1, cotas.length)));
            double wEl = rx * 2.0 * fator;
            double hEl = ry * 2.0 * fator;
            String cor = coresCamadas[Math.min(i, coresCamadas.length - 1)];

            g.setFill(Color.web(cor, 0.55));
            g.fillOval(centroPx[0] - wEl / 2.0, centroPx[1] - hEl / 2.0, wEl, hEl);

            g.setStroke(Color.web("#A16207", 0.55));
            g.setLineWidth(1.0);
            g.strokeOval(centroPx[0] - wEl / 2.0, centroPx[1] - hEl / 2.0, wEl, hEl);

            if (zoom >= 11 && wEl >= 42) {
                g.setFill(Color.web("#78350F"));
                g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 9));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(cotas[i], centroPx[0], centroPx[1] - hEl / 2.0 + 10);
            }
        }
    }

    private void desenharPoligonoGeo(GraphicsContext g, double[][] coords, String fillHex, String strokeHex, double lw) {
        g.beginPath();
        for (int i = 0; i < coords.length; i++) {
            double[] p = coordenadaParaCanvas(new Coordenada(coords[i][0], coords[i][1], 0));
            if (i == 0) g.moveTo(p[0], p[1]);
            else g.lineTo(p[0], p[1]);
        }
        g.closePath();
        g.setFill(Color.web(fillHex));
        g.fill();
        g.setStroke(Color.web(strokeHex));
        g.setLineWidth(lw);
        g.stroke();
    }

    private void desenharCursoIgarape(GraphicsContext g, double lat1, double lon1, double lat2, double lon2, double lat3, double lon3) {
        double[] p1 = coordenadaParaCanvas(new Coordenada(lat1, lon1, 0));
        double[] p2 = coordenadaParaCanvas(new Coordenada(lat2, lon2, 0));
        double[] p3 = coordenadaParaCanvas(new Coordenada(lat3, lon3, 0));
        g.setStroke(Color.web("#60A5FA", 0.7));
        g.setLineWidth(Math.max(2.0, (zoom - 9) * 0.9));
        g.beginPath();
        g.moveTo(p1[0], p1[1]);
        g.quadraticCurveTo(p2[0], p2[1], p3[0], p3[1]);
        g.stroke();
    }

    private void desenharManchaVerde(GraphicsContext g, double lat, double lon, String nome) {
        double[] c = coordenadaParaCanvas(new Coordenada(lat, lon, 0));
        double[] b = coordenadaParaCanvas(new Coordenada(lat + 0.008, lon + 0.010, 0));
        double rx = Math.max(12, Math.abs(b[0] - c[0]));
        double ry = Math.max(9, Math.abs(b[1] - c[1]));
        g.setFill(Color.rgb(34, 197, 94, 0.25));
        g.fillOval(c[0] - rx, c[1] - ry, rx * 2, ry * 2);
        g.setStroke(Color.rgb(22, 163, 74, 0.45));
        g.setLineWidth(1.0);
        g.strokeOval(c[0] - rx, c[1] - ry, rx * 2, ry * 2);
    }

    private void desenharVia(GraphicsContext g, double lat1, double lon1, double lat2, double lon2, boolean principal) {
        double[] p1 = coordenadaParaCanvas(new Coordenada(lat1, lon1, 0));
        double[] p2 = coordenadaParaCanvas(new Coordenada(lat2, lon2, 0));
        double scale = Math.max(1.0, (zoom - 9) * 0.55);
        g.setStroke(Color.WHITE);
        g.setLineWidth((principal ? 3.8 : 2.5) * scale);
        g.strokeLine(p1[0], p1[1], p2[0], p2[1]);
        g.setStroke(Color.web(principal ? "#94A3B8" : "#CBD5E1"));
        g.setLineWidth(1.0);
        g.strokeLine(p1[0], p1[1], p2[0], p2[1]);
    }

    private void desenharMarcoBairro(GraphicsContext g, double lat, double lon, String nome) {
        double[] xy = coordenadaParaCanvas(new Coordenada(lat, lon, 0));
        g.setFill(Color.web("#334155"));
        g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 10));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(nome, xy[0], xy[1] + 4);
    }

    private void desenharAneisDistancia(GraphicsContext g) {
        if (coordLoja == null) return;
        double[] xyLoja = coordenadaParaCanvas(coordLoja);
        for (double rKm : new double[]{2.0, 5.0, 15.0}) {
            double latBorda = coordLoja.latitude() + (rKm / 111.32);
            double[] xyBorda = coordenadaParaCanvas(new Coordenada(latBorda, coordLoja.longitude(), 0));
            double rPx = Math.abs(xyLoja[1] - xyBorda[1]);
            if (rPx < 16) continue;
            g.setStroke(Color.rgb(30, 64, 175, 0.35));
            g.setLineWidth(1.3);
            g.setLineDashes(5, 4);
            g.strokeOval(xyLoja[0] - rPx, xyLoja[1] - rPx, rPx * 2, rPx * 2);
            g.setLineDashes(null);

            g.setFill(Color.web("#1E3A8A"));
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.format("%.0f km", rKm), xyLoja[0], xyLoja[1] - rPx + 12);
        }
    }

    private boolean desenharTilesSobrepostos(GraphicsContext g, double w, double h) {
        boolean desenhou = false;
        int maxTiles = 1 << zoom;
        double worldSize = TILE_SIZE * (double) maxTiles;
        double centroPxX = lonParaPixelX(centroLon, worldSize);
        double centroPxY = latParaPixelY(centroLat, worldSize);
        double topoEsqX = centroPxX - w / 2.0;
        double topoEsqY = centroPxY - h / 2.0;

        int tileMinX = (int) Math.floor(topoEsqX / TILE_SIZE);
        int tileMaxX = (int) Math.floor((topoEsqX + w) / TILE_SIZE);
        int tileMinY = (int) Math.floor(topoEsqY / TILE_SIZE);
        int tileMaxY = (int) Math.floor((topoEsqY + h) / TILE_SIZE);

        for (int tx = tileMinX; tx <= tileMaxX; tx++) {
            for (int ty = tileMinY; ty <= tileMaxY; ty++) {
                if (ty < 0 || ty >= maxTiles) continue;
                int wrappedX = ((tx % maxTiles) + maxTiles) % maxTiles;
                double drawX = tx * TILE_SIZE - topoEsqX;
                double drawY = ty * TILE_SIZE - topoEsqY;

                Image tileImg = obterOuBaixarTile(zoom, wrappedX, ty);
                if (tileImg != null && !tileImg.isError()) {
                    g.drawImage(tileImg, drawX, drawY, TILE_SIZE, TILE_SIZE);
                    desenhou = true;
                }
            }
        }
        return desenhou;
    }

    private Image obterOuBaixarTile(int z, int x, int y) {
        String chave = z + "/" + x + "/" + y;
        Image emMemoria = cacheMemoriaTiles.get(chave);
        if (emMemoria != null) return emMemoria;

        Path arquivoLocal = DIR_CACHE_TILES.resolve(String.valueOf(z)).resolve(String.valueOf(x)).resolve(y + ".png");
        if (Files.exists(arquivoLocal)) {
            try {
                Image img = new Image(arquivoLocal.toUri().toString(), false);
                if (!img.isError()) {
                    cacheMemoriaTiles.put(chave, img);
                    return img;
                }
            } catch (Exception ignored) {
            }
        }
        if (tilesEmDownload.add(chave)) {
            Thread.ofVirtual().start(() -> {
                try {
                    String url = mostrarRelevoVetorial && z <= 17
                            ? String.format(Locale.US, "https://a.tile.opentopomap.org/%d/%d/%d.png", z, x, y)
                            : String.format(Locale.US, "https://basemaps.cartocdn.com/rastertiles/voyager/%d/%d/%d.png", z, x, y);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(4))
                            .header("User-Agent", "SazonaliseiERP/2.0")
                            .GET()
                            .build();
                    HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body().length > 100) {
                        Files.createDirectories(arquivoLocal.getParent());
                        Files.write(arquivoLocal, resp.body());
                        Image img = new Image(new ByteArrayInputStream(resp.body()));
                        if (!img.isError()) {
                            cacheMemoriaTiles.put(chave, img);
                            Platform.runLater(this::desenhar);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    tilesEmDownload.remove(chave);
                }
            });
        }
        return null;
    }

    private void desenharPinLoja(GraphicsContext g, double x, double y) {
        g.setFill(Color.web("#047857"));
        g.fillOval(x - 10, y - 10, 20, 20);
        g.setStroke(Color.WHITE);
        g.setLineWidth(2.2);
        g.strokeOval(x - 10, y - 10, 20, 20);
        desenharEtiqueta(g, x, y + 20, "SUA LOJA", "#047857", "#065F46", "#FFFFFF");
    }

    private void desenharPinEvento(GraphicsContext g, double x, double y, EventoMapaGps ev, boolean sel) {
        double r = sel ? 11 : 8;
        String bg = ev.chuvaPrevistaMm() >= 2.0 ? "#0369A1" : (ev.publico() ? "#047857" : "#1D4ED8");
        g.setFill(Color.web(bg));
        g.fillOval(x - r, y - r, r * 2, r * 2);
        g.setStroke(Color.web(sel ? "#F59E0B" : "#FFFFFF"));
        g.setLineWidth(sel ? 2.8 : 1.5);
        g.strokeOval(x - r, y - r, r * 2, r * 2);

        String rotulo = String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f km • %.0f°C", ev.distanciaKm(), ev.tempPrevistaC());
        if (sel) {
            String nm = ev.nome().length() > 18 ? ev.nome().substring(0, 17) : ev.nome();
            desenharEtiqueta(g, x, y - r - 14, nm + " (" + rotulo + ")", "#FFFFFF", bg, "#0F172A");
        } else {
            desenharEtiqueta(g, x, y + r + 13, rotulo, "#FFFFFF", bg, "#0F172A");
        }
    }

    private void desenharEscalaEBussola(GraphicsContext g, double w, double h) {
        // Barra de escala real aproximada em metros/km no Equador/Manaus
        double worldSize = TILE_SIZE * Math.pow(2, zoom);
        double metersPerPx = (40075016.686 * Math.cos(Math.toRadians(centroLat))) / worldSize;
        double barMeters = 1000.0;
        if (metersPerPx * 90 > 4000) barMeters = 5000.0;
        else if (metersPerPx * 90 < 400) barMeters = 200.0;
        double barPx = Math.max(35, Math.min(130, barMeters / Math.max(0.1, metersPerPx)));
        String lblEscala = barMeters >= 1000 ? String.format("%.0f km", barMeters / 1000.0) : String.format("%.0f m", barMeters);

        double bx = 14, by = h - 18;
        g.setFill(Color.rgb(255, 255, 255, 0.85));
        g.fillRoundRect(bx - 4, by - 16, barPx + 24, 22, 4, 4);
        g.setStroke(Color.web("#0F172A"));
        g.setLineWidth(2.0);
        g.strokeLine(bx, by, bx + barPx, by);
        g.strokeLine(bx, by - 4, bx, by);
        g.strokeLine(bx + barPx, by - 4, bx + barPx, by);
        g.setFill(Color.web("#0F172A"));
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(lblEscala + " • Scroll=Zoom", bx + 2, by - 6);
    }

    private void desenharEtiqueta(GraphicsContext g, double cx, double cy, String texto, String bg, String border, String fg) {
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        double w = Math.max(60, texto.length() * 6.0 + 12);
        double h = 19;
        g.setFill(Color.web(bg));
        g.fillRoundRect(cx - w / 2, cy - h / 2, w, h, 4, 4);
        g.setStroke(Color.web(border));
        g.setLineWidth(1.0);
        g.strokeRoundRect(cx - w / 2, cy - h / 2, w, h, 4, 4);
        g.setFill(Color.web(fg));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(texto, cx, cy + 3.5);
    }

    private double[] coordenadaParaCanvas(Coordenada c) {
        if (c == null) return new double[]{canvas.getWidth() / 2.0, canvas.getHeight() / 2.0};
        double worldSize = TILE_SIZE * Math.pow(2, zoom);
        double centroPxX = lonParaPixelX(centroLon, worldSize);
        double centroPxY = latParaPixelY(centroLat, worldSize);
        double pX = lonParaPixelX(c.longitude(), worldSize);
        double pY = latParaPixelY(c.latitude(), worldSize);
        return new double[]{
                (canvas.getWidth() / 2.0) + (pX - centroPxX),
                (canvas.getHeight() / 2.0) + (pY - centroPxY)
        };
    }

    private static double lonParaPixelX(double lon, double worldSize) {
        return ((lon + 180.0) / 360.0) * worldSize;
    }

    private static double latParaPixelY(double lat, double worldSize) {
        double sinLat = Math.sin(Math.toRadians(Math.max(-85.0, Math.min(85.0, lat))));
        double y = 0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI);
        return y * worldSize;
    }

    private static double pixelXParaLon(double px, double worldSize) {
        return (px / worldSize) * 360.0 - 180.0;
    }

    private static double pixelYParaLat(double py, double worldSize) {
        double n = Math.PI - (2.0 * Math.PI * py) / worldSize;
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }
}
