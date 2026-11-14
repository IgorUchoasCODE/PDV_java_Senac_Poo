package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.componentes.FormatadorDadosUI;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Grafo Analítico estilo Obsidian (fundo limpo com grade técnica e esferas discretas),
 * com filtros centrados em decisões de negócio:
 * - Padrões sazonais que mais vendem
 * - O que mais gera perdas/devoluções
 * - Fornecedor que mais fornece
 * - Melhor fornecedor (ranking / menor perda)
 */
public class AbaGrafoObsidianContainer extends BorderPane {

    public enum TipoNo {
        CLIENTE("Cliente", Color.web("#38BDF8")),
        FORNECEDOR("Fornecedor", Color.web("#2DD4BF")),
        FATOR_EXTERNO("Padrão Sazonal", Color.web("#F59E0B")),
        NOTA("Nota / Perda", Color.web("#F87171")),
        PRODUTO("Produto", Color.web("#A78BFA"));

        final String nome;
        final Color cor;

        TipoNo(String nome, Color cor) {
            this.nome = nome;
            this.cor = cor;
        }
    }

    public enum ModoFoco {
        VISAO_GERAL("Visão Geral"),
        SAZONAL_MAIS_VENDE("Padrões Sazonais que Mais Vendem"),
        MAIS_PERDE("O que Mais Perde / Devoluções"),
        FORNECEDOR_MAIS_FORNECE("Fornecedor que Mais Fornece"),
        MELHOR_FORNECEDOR("Melhor Fornecedor (Ranking)");

        final String rotulo;

        ModoFoco(String rotulo) {
            this.rotulo = rotulo;
        }

        @Override
        public String toString() {
            return rotulo;
        }
    }

    private static class NoGrafo {
        final String id;
        final String rotulo;
        final TipoNo tipo;
        final String detalhes;
        double x, y;
        double vx = 0, vy = 0;
        double massa = 1.0;
        double raio = 8.5;
        boolean centroFoco = false;

        NoGrafo(String id, String rotulo, TipoNo tipo, String detalhes, double x, double y) {
            this.id = id;
            this.rotulo = rotulo;
            this.tipo = tipo;
            this.detalhes = detalhes;
            this.x = x;
            this.y = y;
        }
    }

    private static class ArestaGrafo {
        final NoGrafo origem;
        final NoGrafo destino;
        final String relacao;

        ArestaGrafo(NoGrafo origem, NoGrafo destino, String relacao) {
            this.origem = origem;
            this.destino = destino;
            this.relacao = relacao;
        }
    }

    private final SistemaGateway gateway;
    private final Canvas canvas = new Canvas(820, 560);
    private final ComboBox<ModoFoco> cmbFoco = new ComboBox<>();
    private final TextField txtFiltro = new TextField();
    private final CheckBox chkFisicaAtiva = new CheckBox("Física Ativa");
    private final Label lblInspetorTitulo = new Label("Selecione um nó para inspecionar");
    private final TextArea txtInspetorDetalhes = new TextArea();

    private final List<NoGrafo> nos = new ArrayList<>();
    private final List<ArestaGrafo> arestas = new ArrayList<>();

    private NoGrafo noSelecionado;
    private NoGrafo noArrastado;
    private double offsetX = 0;
    private double offsetY = 0;
    private double zoom = 1.0;
    private double lastMouseX;
    private double lastMouseY;

    public AbaGrafoObsidianContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        setPadding(new Insets(14));
        montarLayout();
        configurarInteracoesCanvas();
        iniciarLoopFisica();
        carregarDadosGrafo();
    }

    private void montarLayout() {
        VBox topoContainer = new VBox(8);
        topoContainer.setPadding(new Insets(0, 0, 10, 0));

        HBox linhaFiltros = new HBox(10);
        linhaFiltros.setAlignment(Pos.CENTER_LEFT);

        cmbFoco.getItems().addAll(ModoFoco.values());
        cmbFoco.setValue(ModoFoco.VISAO_GERAL);
        cmbFoco.setPrefWidth(260);
        cmbFoco.setOnAction(e -> carregarDadosGrafo());

        txtFiltro.setPromptText("Filtrar por nome, fornecedor, produto ou padrão...");
        txtFiltro.setPrefWidth(260);
        txtFiltro.textProperty().addListener((obs, o, n) -> desenharGrafo());

        chkFisicaAtiva.setSelected(true);
        chkFisicaAtiva.setStyle("-fx-text-fill: #E2E8F0;");

        Button btnRecarregar = new Button("Atualizar Grafo");
        btnRecarregar.getStyleClass().add("btn-primary");
        btnRecarregar.setOnAction(e -> carregarDadosGrafo());

        Button btnCentralizar = new Button("Centralizar");
        btnCentralizar.getStyleClass().add("btn-secondary");
        btnCentralizar.setOnAction(e -> {
            offsetX = 0;
            offsetY = 0;
            zoom = 1.0;
            desenharGrafo();
        });

        linhaFiltros.getChildren().addAll(new Label("Foco Analítico:"), cmbFoco, txtFiltro, chkFisicaAtiva, btnRecarregar, btnCentralizar);

        HBox legenda = new HBox(14);
        legenda.setAlignment(Pos.CENTER_LEFT);
        for (TipoNo t : TipoNo.values()) {
            Label badge = new Label("● " + t.nome);
            badge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + toHex(t.cor) + ";");
            legenda.getChildren().add(badge);
        }

        topoContainer.getChildren().addAll(linhaFiltros, legenda);
        setTop(topoContainer);

        Pane canvasHolder = new Pane(canvas);
        canvasHolder.setStyle("-fx-background-color: #0B1220; -fx-border-color: #334155; -fx-border-radius: 10; -fx-background-radius: 10;");
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> desenharGrafo());
        canvas.heightProperty().addListener((o, a, b) -> desenharGrafo());
        setCenter(canvasHolder);

        VBox inspetor = new VBox(10);
        inspetor.setPrefWidth(295);
        inspetor.setPadding(new Insets(14));
        inspetor.getStyleClass().add("card-container");
        BorderPane.setMargin(inspetor, new Insets(0, 0, 0, 12));

        Label lblHeader = new Label("ANÁLISE DO NÓ SELECIONADO");
        lblHeader.getStyleClass().add("section-title");

        lblInspetorTitulo.setWrapText(true);
        lblInspetorTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #38BDF8;");

        txtInspetorDetalhes.setEditable(false);
        txtInspetorDetalhes.setWrapText(true);
        VBox.setVgrow(txtInspetorDetalhes, Priority.ALWAYS);
        txtInspetorDetalhes.setText("Escolha um foco analítico acima (ex: Padrões Sazonais que Mais Vendem ou Melhor Fornecedor) e clique nos nós para ver detalhes.");

        inspetor.getChildren().addAll(lblHeader, lblInspetorTitulo, txtInspetorDetalhes);
        setRight(inspetor);
    }

    private void iniciarLoopFisica() {
        AnimationTimer timer = new AnimationTimer() {
            private long ultimoNs = 0;

            @Override
            public void handle(long now) {
                if (ultimoNs == 0) {
                    ultimoNs = now;
                    return;
                }
                double dt = Math.min((now - ultimoNs) / 1_000_000_000.0, 0.035);
                ultimoNs = now;

                if (chkFisicaAtiva.isSelected() && !nos.isEmpty()) {
                    atualizarFisica(dt);
                    desenharGrafo();
                }
            }
        };
        timer.start();
    }

    private void atualizarFisica(double dt) {
        double w = Math.max(canvas.getWidth(), 800);
        double h = Math.max(canvas.getHeight(), 520);
        double cx = w / 2.0;
        double cy = h / 2.0;

        int n = nos.size();
        double kRepulsao = 9500.0;
        double kMola = 2.8;
        double comprimentoIdeal = 105.0;
        double kGravidade = 0.65;
        double amortecimento = Math.pow(0.78, dt * 60.0);

        for (int i = 0; i < n; i++) {
            NoGrafo a = nos.get(i);
            for (int j = i + 1; j < n; j++) {
                NoGrafo b = nos.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double distSq = Math.max(dx * dx + dy * dy, 20.0);
                double dist = Math.sqrt(distSq);
                double forca = kRepulsao / distSq;
                double fx = (dx / dist) * forca;
                double fy = (dy / dist) * forca;

                if (a != noArrastado && !a.centroFoco) {
                    a.vx -= (fx / a.massa) * dt * 60.0 * 0.07;
                    a.vy -= (fy / a.massa) * dt * 60.0 * 0.07;
                }
                if (b != noArrastado && !b.centroFoco) {
                    b.vx += (fx / b.massa) * dt * 60.0 * 0.07;
                    b.vy += (fy / b.massa) * dt * 60.0 * 0.07;
                }
            }
        }

        for (ArestaGrafo e : arestas) {
            double dx = e.destino.x - e.origem.x;
            double dy = e.destino.y - e.origem.y;
            double dist = Math.hypot(dx, dy);
            if (dist < 1.0) continue;

            double deslocamento = dist - comprimentoIdeal;
            double forca = kMola * deslocamento;
            double fx = (dx / dist) * forca;
            double fy = (dy / dist) * forca;

            if (e.origem != noArrastado && !e.origem.centroFoco) {
                e.origem.vx += (fx / e.origem.massa) * dt;
                e.origem.vy += (fy / e.origem.massa) * dt;
            }
            if (e.destino != noArrastado && !e.destino.centroFoco) {
                e.destino.vx -= (fx / e.destino.massa) * dt;
                e.destino.vy -= (fy / e.destino.massa) * dt;
            }
        }

        for (NoGrafo no : nos) {
            if (no == noArrastado || no.centroFoco) {
                no.vx = 0;
                no.vy = 0;
                continue;
            }
            no.vx += (cx - no.x) * kGravidade * dt;
            no.vy += (cy - no.y) * kGravidade * dt;
            no.vx *= amortecimento;
            no.vy *= amortecimento;
            no.x += no.vx * dt;
            no.y += no.vy * dt;
        }
    }

    private void configurarInteracoesCanvas() {
        canvas.setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            double wx = (e.getX() - offsetX) / zoom;
            double wy = (e.getY() - offsetY) / zoom;

            noArrastado = null;
            for (int i = nos.size() - 1; i >= 0; i--) {
                NoGrafo n = nos.get(i);
                if (Math.hypot(wx - n.x, wy - n.y) <= n.raio + 6) {
                    noArrastado = n;
                    noSelecionado = n;
                    atualizarInspetor(n);
                    break;
                }
            }
            desenharGrafo();
        });

        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (noArrastado != null) {
                noArrastado.x += dx / zoom;
                noArrastado.y += dy / zoom;
                noArrastado.vx = 0;
                noArrastado.vy = 0;
            } else {
                offsetX += dx;
                offsetY += dy;
            }
            desenharGrafo();
        });

        canvas.setOnMouseReleased(e -> noArrastado = null);

        canvas.setOnScroll(e -> {
            double fator = e.getDeltaY() > 0 ? 1.12 : 0.89;
            zoom = Math.max(0.4, Math.min(2.6, zoom * fator));
            desenharGrafo();
        });
    }

    public void carregarDadosGrafo() {
        ModoFoco foco = cmbFoco.getValue() != null ? cmbFoco.getValue() : ModoFoco.VISAO_GERAL;
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respNotas = gateway.executarRota(RotaSistema.NOTA_LISTAR_PADRAO, Map.of("limite", 60));
            RespostaEndpoint<Object> respDestaques = gateway.executarRota(RotaSistema.ESTATISTICA_DESTAQUES_SAZONAIS, Map.of());
            RespostaEndpoint<Object> respMotivos = gateway.executarRota(RotaSistema.ESTATISTICA_MOTIVOS_PERDA_PREMATURA, Map.of());
            RespostaEndpoint<Object> respRankingForn = gateway.executarRota(RotaSistema.ESTATISTICA_RANKING_FORNECEDORES, Map.of());
            RespostaEndpoint<Object> respFatores = gateway.executarRota(RotaSistema.FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS, Map.of());

            Platform.runLater(() -> construirGrafoFocado(foco, respNotas, respDestaques, respMotivos, respRankingForn, respFatores));
        }, erro -> Platform.runLater(() -> txtInspetorDetalhes.setText("Erro ao carregar grafo: " + erro.getMessage())));
    }

    private void construirGrafoFocado(
            ModoFoco foco,
            RespostaEndpoint<Object> respNotas,
            RespostaEndpoint<Object> respDestaques,
            RespostaEndpoint<Object> respMotivos,
            RespostaEndpoint<Object> respRankingForn,
            RespostaEndpoint<Object> respFatores
    ) {
        nos.clear();
        arestas.clear();
        noSelecionado = null;

        double cx = Math.max(canvas.getWidth(), 820) / 2.0;
        double cy = Math.max(canvas.getHeight(), 540) / 2.0;
        Map<String, NoGrafo> mapa = new LinkedHashMap<>();
        Random rnd = new Random(19L);

        if (foco == ModoFoco.SAZONAL_MAIS_VENDE) {
            NoGrafo centro = criarNo(mapa, "CENTRO_SAZ", "Padrões que Mais Vendem", TipoNo.FATOR_EXTERNO,
                    "Centraliza os padrões sazonais com maior giro de vendas.", cx, cy, 11, true);
            if (respDestaques != null && respDestaques.sucesso() && respDestaques.dados() instanceof List<?> lista && !lista.isEmpty()) {
                for (Object o : lista) {
                    if (o instanceof Object[] r && r.length >= 7) {
                        String prodNome = FormatadorDadosUI.texto(r[0], "Produto");
                        String pico = FormatadorDadosUI.texto(r[6], "Sazonal");
                        NoGrafo nPico = criarNo(mapa, "PICO_" + pico, pico, TipoNo.FATOR_EXTERNO,
                                "Padrão sazonal líder para " + prodNome, cx + off(rnd), cy + off(rnd), 9, false);
                        NoGrafo nProd = criarNo(mapa, "PROD_" + prodNome, truncar(prodNome, 18), TipoNo.PRODUTO,
                                "Produto: " + prodNome + "\nPico: " + pico, cx + off(rnd), cy + off(rnd), 8.5, false);
                        arestas.add(new ArestaGrafo(centro, nPico, "impulsiona"));
                        arestas.add(new ArestaGrafo(nPico, nProd, "mais vende"));
                    }
                }
            }
            conectarNotasPorTipo(mapa, respNotas, NotaVenda.class, centro, cx, cy, rnd, "venda");
        } else if (foco == ModoFoco.MAIS_PERDE) {
            NoGrafo centro = criarNo(mapa, "CENTRO_PERDA", "Foco: Perdas & Devoluções", TipoNo.NOTA,
                    "Centraliza os principais motivos e itens com perda/devolução.", cx, cy, 11, true);
            if (respMotivos != null && respMotivos.sucesso() && respMotivos.dados() instanceof List<?> motivos) {
                for (Object o : motivos) {
                    if (o instanceof Object[] r && r.length >= 2) {
                        String mot = FormatadorDadosUI.texto(r[0], "Motivo");
                        String qtdMot = FormatadorDadosUI.quantidade(r[1]);
                        NoGrafo nMot = criarNo(mapa, "MOT_" + mot, truncar(mot, 18), TipoNo.NOTA,
                                "Motivo de perda: " + mot + "\nOcorrências: " + qtdMot, cx + off(rnd), cy + off(rnd), 9, false);
                        arestas.add(new ArestaGrafo(centro, nMot, "causa " + qtdMot));
                    }
                }
            }
            conectarNotasPorTipo(mapa, respNotas, NotaPerda.class, centro, cx, cy, rnd, "perda");
            conectarNotasPorTipo(mapa, respNotas, NotaDevolucao.class, centro, cx, cy, rnd, "devolução");
        } else if (foco == ModoFoco.FORNECEDOR_MAIS_FORNECE || foco == ModoFoco.MELHOR_FORNECEDOR) {
            String tituloCentro = foco == ModoFoco.FORNECEDOR_MAIS_FORNECE
                    ? "Fornecedor que Mais Fornece"
                    : "Melhor Fornecedor (Menor Perda)";
            NoGrafo centro = criarNo(mapa, "CENTRO_FORN", tituloCentro, TipoNo.FORNECEDOR,
                    "Ranking de fornecedores baseado em volume de compras e índice de perdas.", cx, cy, 11, true);

            if (respRankingForn != null && respRankingForn.sucesso() && respRankingForn.dados() instanceof List<?> rank) {
                for (Object o : rank) {
                    if (o instanceof Object[] r && r.length >= 2) {
                        String forn = FormatadorDadosUI.texto(r[0], "Fornecedor");
                        NoGrafo nForn = criarNo(mapa, "FORN_" + forn, truncar(forn, 18), TipoNo.FORNECEDOR,
                                "Fornecedor: " + forn + "\nIndicador: " + FormatadorDadosUI.formatarRegistroArray(r),
                                cx + off(rnd), cy + off(rnd), 9.5, false);
                        arestas.add(new ArestaGrafo(centro, nForn, "ranking"));
                    }
                }
            }
            conectarNotasPorTipo(mapa, respNotas, NotaCompra.class, centro, cx, cy, rnd, "forneceu");
        } else {
            construirVisaoGeral(mapa, respNotas, respFatores, cx, cy, rnd);
        }

        if (nos.isEmpty()) {
            NoGrafo c = criarNo(mapa, "VAZIO", "Sem registros para este foco", TipoNo.FATOR_EXTERNO,
                    "Cadastre notas de compra, venda ou perda para visualizar conexões reais neste filtro.", cx, cy, 10, true);
            noSelecionado = c;
            atualizarInspetor(c);
        }
        desenharGrafo();
    }

    private void conectarNotasPorTipo(
            Map<String, NoGrafo> mapa,
            RespostaEndpoint<Object> respNotas,
            Class<? extends Nota> classeAlvo,
            NoGrafo centro,
            double cx, double cy, Random rnd, String verbo
    ) {
        if (respNotas == null || !respNotas.sucesso() || !(respNotas.dados() instanceof List<?> notas)) return;
        int count = 0;
        for (Object obj : notas) {
            if (classeAlvo.isInstance(obj)) {
                Nota nota = (Nota) obj;
                if (nota.getPessoa() != null) {
                    String pNome = nota.getPessoa().getNome() != null ? nota.getPessoa().getNome() : "Pessoa #" + nota.getPessoa().getId();
                    TipoNo tipoP = nota.getPessoa().isFornecedor() ? TipoNo.FORNECEDOR : TipoNo.CLIENTE;
                    NoGrafo nPes = criarNo(mapa, "PES_" + nota.getPessoa().getId(), truncar(pNome, 16), tipoP,
                            tipoP.nome + ": " + pNome + "\nNota #" + nota.getId() + " | R$ " + nota.getValorTotal(),
                            cx + off(rnd), cy + off(rnd), 8.5, false);
                    arestas.add(new ArestaGrafo(centro, nPes, verbo));
                    if (nota.getItens() != null) {
                        for (Item<? extends Produto> it : nota.getItens()) {
                            if (it.getProduto() != null) {
                                Produto p = it.getProduto();
                                NoGrafo nProd = criarNo(mapa, "PROD_" + p.getId(), truncar(p.getNome(), 16), TipoNo.PRODUTO,
                                        "Produto: " + p.getNome() + "\nPreço Venda: R$ " + p.getPrecoVenda(),
                                        cx + off(rnd), cy + off(rnd), 7.5, false);
                                arestas.add(new ArestaGrafo(nPes, nProd, verbo + " (" + it.getQuantidade() + ")"));
                            }
                        }
                    }
                }
                if (++count >= 18) break;
            }
        }
    }

    private void construirVisaoGeral(
            Map<String, NoGrafo> mapa,
            RespostaEndpoint<Object> respNotas,
            RespostaEndpoint<Object> respFatores,
            double cx, double cy, Random rnd
    ) {
        if (respFatores != null && respFatores.sucesso() && respFatores.dados() instanceof List<?> lista) {
            int i = 0;
            for (Object obj : lista) {
                if (obj instanceof Map<?, ?> m) {
                    String padrao = String.valueOf(m.get("padraoConsumo"));
                    NoGrafo noFator = criarNo(mapa, "FATOR_" + padrao, padrao.replace("_", " "), TipoNo.FATOR_EXTERNO,
                            "Padrão: " + padrao + "\nImpacto: " + m.get("indiceImpacto"),
                            cx + off(rnd), cy + off(rnd), 9.5, false);
                    NoGrafo noNota = criarNo(mapa, "NOTA_" + m.get("idNota"), "Nota #" + m.get("idNota"), TipoNo.NOTA,
                            "Calendário: " + m.get("resumoCalendario"),
                            cx + off(rnd), cy + off(rnd), 7.5, false);
                    arestas.add(new ArestaGrafo(noNota, noFator, "influência"));
                }
                if (++i >= 20) break;
            }
        }
        if (respNotas != null && respNotas.sucesso() && respNotas.dados() instanceof List<?> notas) {
            int count = 0;
            for (Object obj : notas) {
                if (obj instanceof Nota nota) {
                    NoGrafo noNota = criarNo(mapa, "NOTA_" + nota.getId(),
                            nota.getClass().getSimpleName().replace("Nota", "") + " #" + nota.getId(),
                            TipoNo.NOTA, "Total: R$ " + nota.getValorTotal() + "\nData: " + nota.getDataGerada(),
                            cx + off(rnd), cy + off(rnd), 7.5, false);
                    if (nota.getPessoa() != null) {
                        TipoNo tp = nota.getPessoa().isFornecedor() ? TipoNo.FORNECEDOR : TipoNo.CLIENTE;
                        NoGrafo noPes = criarNo(mapa, "PES_" + nota.getPessoa().getId(),
                                truncar(nota.getPessoa().getNome(), 16), tp,
                                tp.nome + ": " + nota.getPessoa().getNome(),
                                cx + off(rnd), cy + off(rnd), 8.5, false);
                        arestas.add(new ArestaGrafo(noPes, noNota, "emitiu"));
                    }
                    if (nota.getItens() != null) {
                        for (Item<? extends Produto> it : nota.getItens()) {
                            if (it.getProduto() != null) {
                                Produto p = it.getProduto();
                                NoGrafo noProd = criarNo(mapa, "PROD_" + p.getId(),
                                        truncar(p.getNome(), 16), TipoNo.PRODUTO,
                                        "Produto: " + p.getNome() + "\nPreço: R$ " + p.getPrecoVenda(),
                                        cx + off(rnd), cy + off(rnd), 8.0, false);
                                arestas.add(new ArestaGrafo(noNota, noProd, "item"));
                            }
                        }
                    }
                    if (++count >= 22) break;
                }
            }
        }
    }

    private NoGrafo criarNo(Map<String, NoGrafo> mapa, String id, String rotulo, TipoNo tipo, String detalhes, double x, double y, double raio, boolean centro) {
        return mapa.computeIfAbsent(id, k -> {
            NoGrafo no = new NoGrafo(id, rotulo, tipo, detalhes, x, y);
            no.raio = raio;
            no.centroFoco = centro;
            no.massa = centro ? 3.0 : 1.0;
            nos.add(no);
            return no;
        });
    }

    private double off(Random rnd) {
        return (rnd.nextDouble() - 0.5) * 240.0;
    }

    private void atualizarInspetor(NoGrafo no) {
        lblInspetorTitulo.setText("[" + no.tipo.nome + "] " + no.rotulo);
        StringBuilder sb = new StringBuilder(no.detalhes).append("\n\nConexões:");
        for (ArestaGrafo a : arestas) {
            if (a.origem == no) {
                sb.append("\n -> ").append(a.relacao).append(" -> ").append(a.destino.rotulo);
            } else if (a.destino == no) {
                sb.append("\n <- ").append(a.relacao).append(" <- ").append(a.origem.rotulo);
            }
        }
        txtInspetorDetalhes.setText(sb.toString());
    }

    private void desenharGrafo() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Fundo técnico limpo e sério (sem estrelas/espaço) com grade sutil
        gc.setFill(Color.web("#0B1220"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#162033"));
        gc.setLineWidth(1.0);
        for (double x = 0; x < w; x += 36) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = 0; y < h; y += 36) {
            gc.strokeLine(0, y, w, y);
        }

        String termo = txtFiltro.getText() == null ? "" : txtFiltro.getText().trim().toLowerCase(Locale.ROOT);
        Set<NoGrafo> destacados = new HashSet<>();
        if (!termo.isBlank()) {
            for (NoGrafo n : nos) {
                if (n.rotulo.toLowerCase(Locale.ROOT).contains(termo) || n.detalhes.toLowerCase(Locale.ROOT).contains(termo)) {
                    destacados.add(n);
                }
            }
        }

        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(zoom, zoom);

        for (ArestaGrafo a : arestas) {
            boolean foco = noSelecionado == a.origem || noSelecionado == a.destino
                    || destacados.contains(a.origem) || destacados.contains(a.destino);
            gc.setStroke(foco ? Color.rgb(56, 189, 248, 0.80) : Color.rgb(100, 116, 139, 0.30));
            gc.setLineWidth(foco ? 1.8 : 1.0);
            gc.strokeLine(a.origem.x, a.origem.y, a.destino.x, a.destino.y);
        }

        gc.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        for (NoGrafo n : nos) {
            boolean ativo = destacados.isEmpty() || destacados.contains(n) || n == noSelecionado;
            double alpha = ativo ? 1.0 : 0.20;
            Color c = n.tipo.cor;

            // Esfera pequena, nítida e discreta
            gc.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            gc.fillOval(n.x - n.raio, n.y - n.raio, n.raio * 2, n.raio * 2);

            if (n == noSelecionado || n.centroFoco) {
                gc.setStroke(Color.web("#F8FAFC"));
                gc.setLineWidth(1.6);
                gc.strokeOval(n.x - n.raio - 2.5, n.y - n.raio - 2.5, (n.raio + 2.5) * 2, (n.raio + 2.5) * 2);
            }

            gc.setFill(Color.color(0.94, 0.96, 0.99, alpha));
            gc.fillText(n.rotulo, n.x + n.raio + 5, n.y + 4);
        }

        gc.restore();
    }

    private String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
