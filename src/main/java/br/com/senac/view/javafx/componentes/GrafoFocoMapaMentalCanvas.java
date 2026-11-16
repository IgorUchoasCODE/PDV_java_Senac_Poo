package br.com.senac.view.javafx.componentes;

import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAPrazo;
import br.com.senac.repository.modal.pagamento.PagamentoAVista;
import br.com.senac.repository.modal.pagamento.PagamentoParcelado;
import br.com.senac.repository.modal.pagamento.ParcelaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Diagrama Analítico Corporativo de Relações, Causas e Ciclo de Vida Financeiro.
 * Suporta expansão procedural em múltiplos níveis (clique duplo ou botão Expandir),
 * histórico de navegação ('Voltar'), Ciclo de Vida de Notas & Pagamentos, e modo Tela Cheia com zoom e arraste.
 */
public class GrafoFocoMapaMentalCanvas extends VBox {

    public enum ModoGrafo {
        CAUSA_EFEITO_SAZONAL("🌦️ Causa e Efeito"),
        CICLO_PAGAMENTOS("💳 Ciclo de Notas & Pagamentos"),
        ARVORE_KIT_EMBALAGENS("📦 Estrutura de Kits"),
        RAIO_X_360("🔍 Raio-X 360º"),
        RASTREIO_PERDAS("⚠️ Fluxo de Perdas");

        private final String titulo;

        ModoGrafo(String titulo) {
            this.titulo = titulo;
        }

        public String getTitulo() { return titulo; }
    }

    private record NoCaixa(
            String categoria,
            String titulo,
            String metrica,
            String rotuloAresta,
            String headerHex,
            String borderHex,
            String detalheCompleto,
            String promptIA,
            String tipoEntidade,
            Long idEntidade,
            double cx,
            double cy
    ) {}

    private record SnapshotNivel(NoCaixa central, List<NoCaixa> perifericos) {}

    private static final double BOX_W = 172;
    private static final double BOX_H = 68;

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;
    private final boolean emTelaCheia;

    private ModoGrafo modoAtual = ModoGrafo.CAUSA_EFEITO_SAZONAL;
    private final ToggleGroup grupoModo = new ToggleGroup();
    private final List<ToggleButton> botoesModo = new ArrayList<>();

    private final ComboBox<String> cbCenarioSazonal = FormatadorSeguroUI.criarComboBoxCartoon("#CBD5E1", "#0F172A");
    private final TextField txtBuscaRaioX = FormatadorSeguroUI.criarCampoTextoCartoon(
            "Buscar Produto, Cliente, Fornecedor ou #Nota...", "#CBD5E1", "#0F172A");
    private final Button btnBuscarRaioX = FormatadorSeguroUI.criarBotaoCartoon("Inspecionar", "#0F172A", "#0F172A", "#FFFFFF");
    private final HBox barraContextoModo = new HBox(8);

    private final Button btnVoltarNivel = FormatadorSeguroUI.criarBotaoCartoon("⬅ Voltar Nível", "#F1F5F9", "#CBD5E1", "#334155");
    private final Button btnExpandirNo = FormatadorSeguroUI.criarBotaoCartoon("➕ Expandir Nó", "#EFF6FF", "#93C5FD", "#1E40AF");
    private final Button btnTelaCheia = FormatadorSeguroUI.criarBotaoCartoon("⛶ Tela Cheia", "#0F172A", "#0F172A", "#FFFFFF");
    private final Label lblBreadcrumb = FormatadorSeguroUI.criarBadgeCartoon("Nível Principal", "#F8FAFC", "#CBD5E1", "#475569");

    private final Canvas canvas = new Canvas(760, 350);
    private final StackPane containerCanvas = new StackPane(canvas);

    private final VBox cardDetalheNo;
    private final Label lblDetalheTitulo;
    private final Label lblDetalheTexto;
    private final Button btnAbrirCardNo;
    private final Button btnDetalheIA;

    // Cache em memória dos dados carregados sob demanda
    private List<Produto> produtosCache = List.of();
    private List<Pessoa> pessoasCache = List.of();
    private List<Nota> notasCache = List.of();
    private PonteRoteadorIA.ConsolidadoEstoque consCache;
    private List<Map<String, Object>> destaquesCache = List.of();

    private final Deque<SnapshotNivel> pilhaNiveis = new ArrayDeque<>();
    private NoCaixa noCentral;
    private final List<NoCaixa> nosPerifericos = new ArrayList<>();
    private int indiceSelecionado = -1;
    private double escalaZoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private double dragInicioX;
    private double dragInicioY;
    private boolean arrastouMouse = false;
    private GerenciadorTemaClimatico.SnapshotTemaClimatico ultimoTema;

    public GrafoFocoMapaMentalCanvas(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this(ponte, conectorIA, false);
    }

    private GrafoFocoMapaMentalCanvas(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA, boolean emTelaCheia) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;
        this.emTelaCheia = emTelaCheia;

        setSpacing(10);
        setPadding(new Insets(12, 14, 12, 14));
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxWidth(Double.MAX_VALUE);
        setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));

        Label titulo = FormatadorSeguroUI.criarTituloSecao("🕸️", "Diagrama Analítico Interativo & Expansão Procedural", "#0F172A");
        Label subtitulo = FormatadorSeguroUI.criarSubtitulo(
                "Clique em um bloco para inspecionar ou clique duas vezes / use '➕ Expandir Nó' para navegar em profundidade.", "#64748B");

        VBox boxTit = new VBox(2, titulo, subtitulo);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        btnVoltarNivel.setDisable(true);
        btnVoltarNivel.setOnAction(e -> voltarNivelAnterior());
        btnExpandirNo.setOnAction(e -> expandirProceduralmente(obterNoSelecionadoOuCentral()));
        btnTelaCheia.setOnAction(e -> abrirJanelaTelaCheia());
        btnTelaCheia.setVisible(!emTelaCheia);
        btnTelaCheia.setManaged(!emTelaCheia);

        Button btnResetZoom = FormatadorSeguroUI.criarBotaoCartoon("100%", "#FFFFFF", "#CBD5E1", "#475569");
        btnResetZoom.setOnAction(e -> {
            escalaZoom = 1.0;
            panX = 0;
            panY = 0;
            desenharDiagrama();
        });

        HBox topo = new HBox(8, boxTit, lblBreadcrumb, btnVoltarNivel, btnExpandirNo, btnResetZoom, btnTelaCheia);
        topo.setAlignment(Pos.CENTER_LEFT);

        FlowPane pilulasModo = new FlowPane(6, 6);
        for (ModoGrafo m : ModoGrafo.values()) {
            ToggleButton tb = new ToggleButton(m.getTitulo());
            tb.setToggleGroup(grupoModo);
            if (m == ModoGrafo.CAUSA_EFEITO_SAZONAL) tb.setSelected(true);
            tb.setOnAction(e -> {
                modoAtual = m;
                pilhaNiveis.clear();
                atualizarEstilosModos();
                reconstruirBarraModo();
                recarregarNosAsync();
            });
            botoesModo.add(tb);
            pilulasModo.getChildren().add(tb);
        }

        cbCenarioSazonal.getItems().addAll(
                "Chuva Forte + Cheia do Rio",
                "Sol Equatorial (> 30°C)",
                "Evento Público Próximo",
                "Vazante / Seca dos Rios"
        );
        cbCenarioSazonal.getSelectionModel().selectFirst();
        cbCenarioSazonal.setOnAction(e -> recarregarNosAsync());

        btnBuscarRaioX.setOnAction(e -> recarregarNosAsync());
        txtBuscaRaioX.setOnAction(e -> recarregarNosAsync());
        HBox.setHgrow(txtBuscaRaioX, Priority.ALWAYS);

        barraContextoModo.setAlignment(Pos.CENTER_LEFT);
        barraContextoModo.setMinHeight(Region.USE_PREF_SIZE);

        containerCanvas.setMinHeight(emTelaCheia ? 460 : 340);
        containerCanvas.setPrefHeight(emTelaCheia ? 560 : 380);
        if (emTelaCheia) {
            VBox.setVgrow(containerCanvas, Priority.ALWAYS);
        }
        containerCanvas.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-border-color: #CBD5E1; -fx-border-width: 1px; -fx-cursor: hand;");

        containerCanvas.widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(Math.max(420, n.doubleValue() - 4));
            desenharDiagrama();
        });
        containerCanvas.heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(Math.max(320, n.doubleValue() - 4));
            desenharDiagrama();
        });

        configurarInteracaoCanvas();

        this.lblDetalheTitulo = FormatadorSeguroUI.criarLabel("Inspeção do Nó Selecionado", 13, true, "#0F172A");
        this.lblDetalheTexto = FormatadorSeguroUI.criarLabel("Clique em qualquer bloco no diagrama para ver os detalhes e expandir conexões.", 12, false, "#334155");
        this.btnAbrirCardNo = FormatadorSeguroUI.criarBotaoCartoon("🔍 Ver Card Completo", "#FFFFFF", "#CBD5E1", "#0F172A");
        this.btnAbrirCardNo.setOnAction(e -> abrirModalCardDoNo(obterNoSelecionadoOuCentral()));
        this.btnDetalheIA = FormatadorSeguroUI.criarBotaoIA("💬 Analisar com IA", () -> {
            NoCaixa alvo = obterNoSelecionadoOuCentral();
            if (alvo != null && conectorIA != null) {
                conectorIA.perguntarAoCopiloto(this, alvo.titulo(), alvo.detalheCompleto(), alvo.promptIA());
            }
        });

        HBox topoDet = new HBox(8, lblDetalheTitulo, btnAbrirCardNo, btnDetalheIA);
        topoDet.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(lblDetalheTitulo, Priority.ALWAYS);

        this.cardDetalheNo = new VBox(4, topoDet, lblDetalheTexto);
        this.cardDetalheNo.setPadding(new Insets(10, 12, 10, 12));
        this.cardDetalheNo.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));

        getChildren().addAll(topo, pilulasModo, barraContextoModo, containerCanvas, cardDetalheNo);
        atualizarEstilosModos();
        reconstruirBarraModo();
    }

    private void configurarInteracaoCanvas() {
        canvas.setOnMousePressed(e -> {
            dragInicioX = e.getX();
            dragInicioY = e.getY();
            arrastouMouse = false;
        });
        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - dragInicioX;
            double dy = e.getY() - dragInicioY;
            if (Math.hypot(dx, dy) > 3) {
                arrastouMouse = true;
                panX += dx;
                panY += dy;
                dragInicioX = e.getX();
                dragInicioY = e.getY();
                desenharDiagrama();
            }
        });
        canvas.setOnMouseClicked(e -> {
            if (!arrastouMouse) {
                tratarCliqueCanvas(e.getX(), e.getY(), e.getClickCount() >= 2);
            }
        });
        canvas.setOnScroll(e -> {
            e.consume();
            double fator = e.getDeltaY() > 0 ? 1.10 : 0.91;
            escalaZoom = Math.max(0.60, Math.min(1.85, escalaZoom * fator));
            desenharDiagrama();
        });
    }

    private void abrirJanelaTelaCheia() {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) {
            modal.initOwner(getScene().getWindow());
        }
        modal.setTitle("Diagrama Analítico em Tela Cheia — Sazonalisei ERP (Pressione ESC para sair)");
        modal.setWidth(1240);
        modal.setHeight(800);
        modal.setMaximized(true);

        GrafoFocoMapaMentalCanvas grafoFull = new GrafoFocoMapaMentalCanvas(ponte, conectorIA, true);
        grafoFull.modoAtual = this.modoAtual;
        grafoFull.produtosCache = this.produtosCache;
        grafoFull.pessoasCache = this.pessoasCache;
        grafoFull.notasCache = this.notasCache;
        grafoFull.consCache = this.consCache;
        grafoFull.destaquesCache = this.destaquesCache;
        grafoFull.noCentral = this.noCentral;
        grafoFull.nosPerifericos.addAll(this.nosPerifericos);
        grafoFull.atualizarEstilosModos();
        grafoFull.reconstruirBarraModo();
        grafoFull.atualizarEstadoNavegacao();
        grafoFull.atualizarDetalheSelecionado(grafoFull.obterNoSelecionadoOuCentral());

        // Faixa fixa superior com instrução explícita e botão vermelho de saída
        Label lblInstrucao = FormatadorSeguroUI.criarLabel(
                "🕸️ MODO TELA CHEIA ATIVO  •  Pressione [ESC] no teclado ou clique no botão vermelho ao lado para voltar  •  Scroll = Zoom  •  Arrastar = Mover",
                12, true, "#F8FAFC");
        lblInstrucao.setWrapText(true);
        HBox.setHgrow(lblInstrucao, Priority.ALWAYS);

        Button btnSairTelaCheia = FormatadorSeguroUI.criarBotaoCartoon("✕ SAIR DA TELA CHEIA (ESC)", "#DC2626", "#991B1B", "#FFFFFF");
        btnSairTelaCheia.setOnAction(e -> modal.close());

        HBox barraSair = new HBox(12, lblInstrucao, btnSairTelaCheia);
        barraSair.setAlignment(Pos.CENTER_LEFT);
        barraSair.setPadding(new Insets(10, 16, 10, 16));
        barraSair.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-width: 0 0 2px 0;");

        VBox wrapper = new VBox(barraSair, grafoFull);
        VBox.setVgrow(grafoFull, Priority.ALWAYS);

        Scene sceneFull = new Scene(wrapper);
        sceneFull.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                modal.close();
                ev.consume();
            }
        });

        modal.setScene(sceneFull);
        modal.show();
        Platform.runLater(grafoFull::desenharDiagrama);
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        this.ultimoTema = snap;
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgCard() + ";");
        }
        atualizarEstilosModos();
        desenharDiagrama();
    }

    private void atualizarEstilosModos() {
        var accent = ultimoTema != null ? ultimoTema.estado().getAccentMain() : "#1D4ED8";
        for (ToggleButton tb : botoesModo) {
            FormatadorSeguroUI.aplicarEstiloToggleCartoon(
                    tb, tb.isSelected(),
                    "#F8FAFC", "#CBD5E1", "#334155",
                    accent, accent, "#FFFFFF");
        }
    }

    private void reconstruirBarraModo() {
        barraContextoModo.getChildren().clear();
        if (modoAtual == ModoGrafo.CAUSA_EFEITO_SAZONAL) {
            cbCenarioSazonal.setPrefWidth(280);
            barraContextoModo.getChildren().addAll(
                    FormatadorSeguroUI.criarLabel("Cenário Analisado:", 12, true, "#0F172A"),
                    cbCenarioSazonal);
        } else if (modoAtual == ModoGrafo.CICLO_PAGAMENTOS) {
            barraContextoModo.getChildren().add(FormatadorSeguroUI.criarLabel(
                    "Mapeando Notas Emitidas → Modalidades de Pagamento → Parcelas → Taxas & Saldo Contábil (Duplo clique expande a carteira)",
                    11, true, "#1E40AF"));
        } else {
            txtBuscaRaioX.setPromptText(switch (modoAtual) {
                case ARVORE_KIT_EMBALAGENS -> "Filtrar kit ou produto...";
                case RAIO_X_360 -> "Buscar Produto, Cliente ou Fornecedor...";
                default -> "Filtrar perda ou devolução...";
            });
            barraContextoModo.getChildren().addAll(txtBuscaRaioX, btnBuscarRaioX);
        }
    }

    public void recarregarNos() {
        recarregarNosAsync();
    }

    public void recarregarNosAsync() {
        Thread.ofVirtual().start(() -> {
            List<Produto> produtos = ponte.listarProdutos();
            List<Pessoa> pessoas = ponte.listarTodasPessoas();
            List<Nota> notas = ponte.listarNotas();
            PonteRoteadorIA.ConsolidadoEstoque cons = ponte.consultarSaldosConsolidadosPorProduto();
            List<Map<String, Object>> destaques = ponte.consultarDestaquesSazonais();

            Platform.runLater(() -> {
                this.produtosCache = produtos;
                this.pessoasCache = pessoas;
                this.notasCache = notas;
                this.consCache = cons;
                this.destaquesCache = destaques;
                this.pilhaNiveis.clear();
                this.panX = 0;
                this.panY = 0;

                nosPerifericos.clear();
                indiceSelecionado = -1;
                switch (modoAtual) {
                    case CAUSA_EFEITO_SAZONAL -> montarModoCausaEfeito(produtos, destaques);
                    case CICLO_PAGAMENTOS -> montarModoCicloPagamentos(notas);
                    case ARVORE_KIT_EMBALAGENS -> montarModoArvoreKit(produtos, cons);
                    case RAIO_X_360 -> montarModoRaioX360(produtos, pessoas, notas, cons);
                    case RASTREIO_PERDAS -> montarModoRastreioPerdas(notas);
                }
                limitarPerifericos(6);
                atualizarEstadoNavegacao();
                atualizarDetalheSelecionado(obterNoSelecionadoOuCentral());
                desenharDiagrama();
            });
        });
    }

    private void limitarPerifericos(int max) {
        while (nosPerifericos.size() > max) {
            nosPerifericos.remove(nosPerifericos.size() - 1);
        }
    }

    private void montarModoCausaEfeito(List<Produto> produtos, List<Map<String, Object>> destaques) {
        String cenario = cbCenarioSazonal.getValue() != null ? cbCenarioSazonal.getValue() : "Chuva Forte + Cheia do Rio";
        noCentral = new NoCaixa(
                "CENÁRIO SAZONAL", encurtar(cenario, 22), "Fator Externo Ativo", "",
                "#1E3A8A", "#3B82F6",
                "Cenário analisado: " + cenario + ". Clique duas vezes em qualquer produto ao redor para expandir suas causas, lotes e compradores.",
                "Para o cenário '" + cenario + "', quais produtos devo reforçar ou proteger no estoque?",
                "CENARIO", null, 0, 0);

        if (!destaques.isEmpty()) {
            for (int i = 0; i < Math.min(6, destaques.size()); i++) {
                var m = destaques.get(i);
                String nome = FormatadorSeguroUI.tratarParaExibicao(
                        m.getOrDefault("nome_produto", m.getOrDefault("produto", "Produto")), "Produto");
                String imp = FormatadorSeguroUI.tratarParaExibicao(m.getOrDefault("impacto", "+18%"), "+18%");
                boolean risco = imp.contains("-");
                Long idP = buscarIdProdutoPorNome(nome);
                nosPerifericos.add(new NoCaixa(
                        risco ? "RISCO" : "DEMANDA", encurtar(nome, 22), imp, risco ? "Risco" : "Alta",
                        risco ? "#991B1B" : "#047857", risco ? "#EF4444" : "#10B981",
                        "Produto: " + nome + " | Efeito no cenário '" + cenario + "': " + imp,
                        "Qual estratégia devo adotar para '" + nome + "' durante '" + cenario + "'?",
                        "PRODUTO", idP, 0, 0));
            }
        } else {
            for (int i = 0; i < Math.min(6, produtos.size()); i++) {
                Produto p = produtos.get(i);
                var diag = ponte.diagnosticarImpactoProduto(p.getId(), p.getNome());
                double lift = cenario.contains("Chuva") ? diag.liftClimaChuvoso()
                        : (cenario.contains("Evento") ? diag.liftEventosCulturais() : diag.liftRioSeca());
                boolean risco = lift < 0.95;
                String delta = String.format(FormatadorSeguroUI.LOCALE_BR, "Variação %.2fx", lift);
                nosPerifericos.add(new NoCaixa(
                        risco ? "ATENÇÃO" : "PRODUTO", encurtar(p.getNome(), 22),
                        FormatadorSeguroUI.formatarMoeda(p.getPrecoVenda()), delta,
                        risco ? "#B45309" : "#047857", risco ? "#F59E0B" : "#10B981",
                        p.getNome() + " — " + diag.recomendacaoEstoque(),
                        "O que fazer com '" + p.getNome() + "' neste cenário '" + cenario + "'?",
                        "PRODUTO", p.getId(), 0, 0));
            }
        }
    }

    private void montarModoCicloPagamentos(List<Nota> notas) {
        int qtdNotasPg = 0, qtdVista = 0, qtdPrazo = 0, qtdParc = 0, qtdQuitado = 0, qtdAberto = 0, qtdVencido = 0;
        BigDecimal totalVista = BigDecimal.ZERO, totalPrazo = BigDecimal.ZERO, totalParc = BigDecimal.ZERO;
        BigDecimal totalTaxas = BigDecimal.ZERO, totalReceber = BigDecimal.ZERO, totalPagar = BigDecimal.ZERO;
        LocalDate hoje = LocalDate.now();

        for (Nota n : notas) {
            if (n.getPagamentos() == null) continue;
            boolean entrada = (n instanceof NotaCompra);
            for (NotaPagamento np : n.getPagamentos()) {
                qtdNotasPg++;
                BigDecimal vf = np.calcularValorFinal();
                totalTaxas = totalTaxas.add(np.calcularCustoTaxaOperadora());
                boolean liq = np.isLiquidado();
                if (liq) qtdQuitado++;
                else {
                    qtdAberto++;
                    if (entrada) totalPagar = totalPagar.add(vf);
                    else totalReceber = totalReceber.add(vf);
                }
                if (np instanceof PagamentoAVista) {
                    qtdVista++;
                    totalVista = totalVista.add(vf);
                } else if (np instanceof PagamentoAPrazo ap) {
                    qtdPrazo++;
                    totalPrazo = totalPrazo.add(vf);
                    if (!liq && ap.isVencido(hoje)) qtdVencido++;
                } else if (np instanceof PagamentoParcelado pp) {
                    qtdParc++;
                    totalParc = totalParc.add(vf);
                    if (!liq && pp.getParcelas() != null && pp.getParcelas().stream().anyMatch(par -> par.isVencido(hoje))) {
                        qtdVencido++;
                    }
                }
            }
        }

        BigDecimal totalGeral = totalVista.add(totalPrazo).add(totalParc);
        noCentral = new NoCaixa(
                "CICLO PAGAMENTOS",
                qtdNotasPg + " Títulos / Notas",
                FormatadorSeguroUI.formatarMoeda(totalGeral),
                "", "#0F172A", "#334155",
                String.format("Ciclo Financeiro completo: %d notas de pagamento (%d liquidadas, %d em aberto, %d em atraso). Taxas de operadora: %s.",
                        qtdNotasPg, qtdQuitado, qtdAberto, qtdVencido, FormatadorSeguroUI.formatarMoeda(totalTaxas)),
                "Analise o ciclo de vida das minhas notas de pagamento: liquidez à vista, prazos, inadimplência e taxas.",
                "CICLO_GERAL", null, 0, 0);

        nosPerifericos.add(new NoCaixa("À VISTA", qtdVista + " liquidações", FormatadorSeguroUI.formatarMoeda(totalVista),
                "Caixa Imediato", "#047857", "#10B981",
                "Pagamentos à vista (Dinheiro, PIX, Débito) com entrada imediata em caixa.",
                "Avalie minha proporção de vendas à vista (" + FormatadorSeguroUI.formatarMoeda(totalVista) + ").",
                "MOD_VISTA", null, 0, 0));

        nosPerifericos.add(new NoCaixa("A PRAZO", qtdPrazo + " títulos", FormatadorSeguroUI.formatarMoeda(totalPrazo),
                "Venc. Único", "#1D4ED8", "#3B82F6",
                "Pagamentos a prazo com controle de vencimento único e juros moratórios.",
                "Como gerenciar meus títulos a prazo (" + qtdPrazo + ")?",
                "MOD_PRAZO", null, 0, 0));

        nosPerifericos.add(new NoCaixa("PARCELADO", qtdParc + " carnês/cartões", FormatadorSeguroUI.formatarMoeda(totalParc),
                "Parcelas", "#6D28D9", "#8B5CF6",
                "Pagamentos parcelados gerenciados parcela a parcela até quitação total.",
                "Analise meus parcelamentos em carteira (" + FormatadorSeguroUI.formatarMoeda(totalParc) + ").",
                "MOD_PARCELADO", null, 0, 0));

        nosPerifericos.add(new NoCaixa("A RECEBER (ABERTO)", "Clientes pendentes", FormatadorSeguroUI.formatarMoeda(totalReceber),
                qtdVencido > 0 ? qtdVencido + " vencidos" : "Em dia",
                qtdVencido > 0 ? "#B91C1C" : "#D97706", qtdVencido > 0 ? "#EF4444" : "#F59E0B",
                "Saldo em aberto a receber de clientes cadastrados (" + qtdVencido + " títulos vencidos).",
                "Estratégia para reduzir recebíveis em atraso (" + FormatadorSeguroUI.formatarMoeda(totalReceber) + ").",
                "CART_RECEBER", null, 0, 0));

        nosPerifericos.add(new NoCaixa("A PAGAR (FORN.)", "Compromissos compra", FormatadorSeguroUI.formatarMoeda(totalPagar),
                "Saídas", "#0369A1", "#0EA5E9",
                "Compromissos financeiros em aberto de Notas de Entrada com fornecedores.",
                "Como equilibrar o fluxo entre o que tenho a receber e a pagar?",
                "CART_PAGAR", null, 0, 0));

        nosPerifericos.add(new NoCaixa("TAXAS OPERADORA", "Custo financeiro", FormatadorSeguroUI.formatarMoeda(totalTaxas),
                "MDR / Tarifa", "#475569", "#94A3B8",
                "Custos acumulados com operadoras de cartão e boletos descontados das receitas.",
                "Minhas taxas financeiras somam " + FormatadorSeguroUI.formatarMoeda(totalTaxas) + ". Como otimizar formas de pagamento?",
                "TAXAS", null, 0, 0));
    }

    private void montarModoArvoreKit(List<Produto> produtos, PonteRoteadorIA.ConsolidadoEstoque cons) {
        String filtro = txtBuscaRaioX.getText() != null ? txtBuscaRaioX.getText().trim() : "";
        List<Produto> filtrados = filtro.isBlank()
                ? produtos
                : BuscaPalavrasParciaisUtil.filtrarEOrdenar(produtos, Produto::getNome, filtro);

        Produto alvo = filtrados.stream()
                .filter(p -> p.isConjunto() && p.getItensConjunto() != null && !p.getItensConjunto().isEmpty())
                .findFirst().orElse(filtrados.isEmpty() ? null : filtrados.get(0));

        if (alvo == null) {
            noCentral = new NoCaixa("CATÁLOGO", "Nenhum produto", "Cadastre em Estoque", "",
                    "#475569", "#94A3B8", "Nenhum produto cadastrado ainda.", "Ajude a cadastrar um produto ou kit.",
                    "GERAL", null, 0, 0);
            return;
        }
        final Long idAlvo = alvo.getId();
        final String nomeAlvo = alvo.getNome();
        BigDecimal saldo = cons != null ? cons.saldosPorProduto().getOrDefault(idAlvo, BigDecimal.ZERO) : BigDecimal.ZERO;
        String um = alvo.getUm() != null ? alvo.getUm().getSigla() : "UN";

        noCentral = new NoCaixa(
                alvo.isConjunto() ? "KIT / COMBO" : "PRODUTO",
                encurtar(nomeAlvo, 22),
                FormatadorSeguroUI.formatarQuantidade(saldo, um) + " • " + FormatadorSeguroUI.formatarMoeda(alvo.getPrecoVenda()),
                "", "#047857", "#10B981",
                nomeAlvo + " • Unidade: " + um + " • Saldo: " + FormatadorSeguroUI.formatarQuantidade(saldo, um),
                "Analise a composição e margem de '" + nomeAlvo + "'.",
                "PRODUTO", idAlvo, 0, 0);

        if (alvo.isConjunto() && alvo.getItensConjunto() != null && !alvo.getItensConjunto().isEmpty()) {
            for (ConjuntoProduto cp : alvo.getItensConjunto()) {
                if (cp.getItem() == null) continue;
                String uComp = cp.getItem().getUm() != null ? cp.getItem().getUm().getSigla() : "UN";
                String qTxt = FormatadorSeguroUI.formatarQuantidade(cp.getQuantidade(), uComp);
                nosPerifericos.add(new NoCaixa(
                        "COMPONENTE", encurtar(cp.getItem().getNome(), 22),
                        FormatadorSeguroUI.formatarMoeda(cp.getItem().getPrecoVenda()), "Qtd: " + qTxt,
                        "#1D4ED8", "#3B82F6",
                        "Componente: " + cp.getItem().getNome() + " • Proporção por kit: " + qTxt,
                        "Verifique o estoque de '" + cp.getItem().getNome() + "' para o kit '" + nomeAlvo + "'.",
                        "PRODUTO", cp.getItem().getId(), 0, 0));
                if (nosPerifericos.size() >= 6) break;
            }
        } else {
            String[] pal = nomeAlvo.split("\\s+");
            String chave = pal.length > 0 ? pal[0].toLowerCase(Locale.ROOT) : "";
            for (Produto outro : produtos) {
                if (Objects.equals(outro.getId(), idAlvo)) continue;
                if (outro.getNome().toLowerCase(Locale.ROOT).contains(chave)
                        || (outro.getItensConjunto() != null && outro.getItensConjunto().stream()
                        .anyMatch(c -> c.getItem() != null && Objects.equals(c.getItem().getId(), idAlvo)))) {
                    String uO = outro.getUm() != null ? outro.getUm().getSigla() : "UN";
                    BigDecimal sO = cons != null ? cons.saldosPorProduto().getOrDefault(outro.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;
                    nosPerifericos.add(new NoCaixa(
                            outro.isConjunto() ? "KIT" : "RELACIONADO",
                            encurtar(outro.getNome(), 22),
                            FormatadorSeguroUI.formatarQuantidade(sO, uO), uO,
                            "#B45309", "#F59E0B",
                            outro.getNome() + " (" + uO + ") • Preço: " + FormatadorSeguroUI.formatarMoeda(outro.getPrecoVenda()),
                            "Explique a equivalência entre '" + nomeAlvo + "' e '" + outro.getNome() + "'.",
                            "PRODUTO", outro.getId(), 0, 0));
                    if (nosPerifericos.size() >= 6) break;
                }
            }
        }
    }

    private void montarModoRaioX360(
            List<Produto> produtos, List<Pessoa> pessoas, List<Nota> notas, PonteRoteadorIA.ConsolidadoEstoque cons) {
        String termo = txtBuscaRaioX.getText() != null ? txtBuscaRaioX.getText().trim() : "";
        if (termo.startsWith("#")) {
            try {
                Long idN = Long.parseLong(termo.substring(1).trim());
                Nota n = notas.stream().filter(nt -> idN.equals(nt.getId())).findFirst().orElse(null);
                if (n != null) {
                    expandirNotaComoCentro(n);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        Produto prodAlvo = null;
        Pessoa pesAlvo = null;
        if (!termo.isBlank()) {
            var pm = BuscaPalavrasParciaisUtil.filtrarEOrdenar(produtos, Produto::getNome, termo);
            var pesm = BuscaPalavrasParciaisUtil.filtrarEOrdenar(pessoas, Pessoa::getNome, termo);
            if (!pm.isEmpty()) prodAlvo = pm.get(0);
            else if (!pesm.isEmpty()) pesAlvo = pesm.get(0);
        } else if (!produtos.isEmpty()) {
            prodAlvo = produtos.get(0);
        } else if (!pessoas.isEmpty()) {
            pesAlvo = pessoas.get(0);
        }

        if (prodAlvo != null) {
            expandirProdutoComoCentro(prodAlvo);
        } else if (pesAlvo != null) {
            expandirPessoaComoCentro(pesAlvo);
        } else {
            noCentral = new NoCaixa("BUSCA 360º", "Sem registros", "Cadastre ou pesquise", "",
                    "#475569", "#94A3B8", "Digite um termo acima.", "Listar produtos e pessoas.",
                    "GERAL", null, 0, 0);
        }
    }

    private void montarModoRastreioPerdas(List<Nota> notas) {
        NotaPerda perda = notas.stream().filter(n -> n instanceof NotaPerda).map(n -> (NotaPerda) n).findFirst().orElse(null);
        NotaDevolucao dev = notas.stream().filter(n -> n instanceof NotaDevolucao).map(n -> (NotaDevolucao) n).findFirst().orElse(null);
        if (perda != null) {
            String prod = (perda.getItens() != null && !perda.getItens().isEmpty() && perda.getItens().get(0).getProduto() != null)
                    ? perda.getItens().get(0).getProduto().getNome() : "Lote";
            Long idProd = (perda.getItens() != null && !perda.getItens().isEmpty() && perda.getItens().get(0).getProduto() != null)
                    ? perda.getItens().get(0).getProduto().getId() : null;
            String forn = perda.getPessoa() != null ? perda.getPessoa().getNome() : "Estoque Interno";
            Long idForn = perda.getPessoa() != null ? perda.getPessoa().getId() : null;
            boolean ress = perda.isPerdaDentroDaValidade();
            String val = FormatadorSeguroUI.formatarMoeda(perda.getValorCustoPerda());

            noCentral = new NoCaixa("NOTA DE PERDA #" + perda.getId(), encurtar(prod, 22), val, "",
                    "#991B1B", "#EF4444",
                    "Perda #" + perda.getId() + " (" + prod + "): " + FormatadorSeguroUI.tratarParaExibicao(perda.getDescricao(), "Avaria"),
                    "Analise a perda #" + perda.getId() + " e sugira plano de ação.",
                    "NOTA", perda.getId(), 0, 0);
            nosPerifericos.add(new NoCaixa("ORIGEM", encurtar(forn, 22), ress ? "Dentro da validade" : "Vencido", "Fornecedor",
                    "#1D4ED8", "#3B82F6", "Fornecedor: " + forn, "Avalie histórico de perdas de '" + forn + "'.",
                    "PESSOA", idForn, 0, 0));
            nosPerifericos.add(new NoCaixa("PRODUTO AFETADO", encurtar(prod, 22), val, "Item",
                    "#B45309", "#F59E0B", "Produto envolvido na perda: " + prod, "Como reduzir perdas de '" + prod + "'?",
                    "PRODUTO", idProd, 0, 0));
            nosPerifericos.add(new NoCaixa("AÇÃO CONTÁBIL", ress ? "Cobrar Abatimento" : "Ajustar Compra", val, ress ? "Crédito" : "Interna",
                    "#047857", "#10B981",
                    ress ? "Elegível para abater " + val + " na próxima compra." : "Perda interna — reduza o lote de compra.",
                    "Prepare abatimento ou ajuste de compra para '" + prod + "'.",
                    "NOTA", perda.getId(), 0, 0));
        } else if (dev != null) {
            noCentral = new NoCaixa("DEVOLUÇÃO #" + dev.getId(),
                    encurtar(dev.getPessoa() != null ? dev.getPessoa().getNome() : "Cliente", 22),
                    FormatadorSeguroUI.formatarMoeda(dev.getValorTotal()), "",
                    "#C2410C", "#F97316",
                    "Motivo: " + FormatadorSeguroUI.tratarParaExibicao(dev.getMotivo(), "Devolução"),
                    "Como reduzir devoluções como a nota #" + dev.getId() + "?",
                    "NOTA", dev.getId(), 0, 0);
        } else {
            noCentral = new NoCaixa("CONTROLE DE ESTOQUE", "Zero Perdas", "Operação Saudável", "",
                    "#047857", "#10B981",
                    "Nenhuma nota de perda ou devolução registrada até o momento.",
                    "Quais lotes merecem atenção preventiva hoje?",
                    "GERAL", null, 0, 0);
        }
    }

    // ==================== EXPANSÃO PROCEDURAL DE NÓS ====================

    private void expandirProceduralmente(NoCaixa alvo) {
        if (alvo == null || alvo.tipoEntidade() == null) return;
        if (noCentral != null && alvo == noCentral && pilhaNiveis.isEmpty()) {
            // Se selecionou o centro no nível raiz, expande o primeiro filho ou detalha
            if (!nosPerifericos.isEmpty()) alvo = nosPerifericos.get(0);
            else return;
        }

        pilhaNiveis.push(new SnapshotNivel(noCentral, new ArrayList<>(nosPerifericos)));
        nosPerifericos.clear();
        indiceSelecionado = -1;

        switch (alvo.tipoEntidade()) {
            case "PRODUTO" -> {
                Long idP = alvo.idEntidade();
                Produto p = produtosCache.stream().filter(pr -> Objects.equals(pr.getId(), idP)).findFirst().orElse(null);
                if (p != null) expandirProdutoComoCentro(p);
                else { voltarNivelAnterior(); return; }
            }
            case "PESSOA" -> {
                Long idPes = alvo.idEntidade();
                Pessoa pes = pessoasCache.stream().filter(ps -> Objects.equals(ps.getId(), idPes)).findFirst().orElse(null);
                if (pes != null) expandirPessoaComoCentro(pes);
                else { voltarNivelAnterior(); return; }
            }
            case "NOTA" -> {
                Long idN = alvo.idEntidade();
                Nota nota = notasCache.stream().filter(nt -> Objects.equals(nt.getId(), idN)).findFirst().orElse(null);
                if (nota != null) expandirNotaComoCentro(nota);
                else { voltarNivelAnterior(); return; }
            }
            case "MOD_VISTA", "MOD_PRAZO", "MOD_PARCELADO", "CART_RECEBER", "CART_PAGAR" -> expandirCarteiraPagamentos(alvo);
            default -> {
                pilhaNiveis.pop();
                abrirModalCardDoNo(alvo);
                return;
            }
        }

        limitarPerifericos(6);
        atualizarEstadoNavegacao();
        atualizarDetalheSelecionado(obterNoSelecionadoOuCentral());
        desenharDiagrama();
    }

    private void expandirProdutoComoCentro(Produto prodAlvo) {
        final Long idP = prodAlvo.getId();
        List<Pessoa> forns = new ArrayList<>();
        List<Pessoa> clis = new ArrayList<>();
        List<Nota> notasDoProd = new ArrayList<>();
        for (Nota n : notasCache) {
            boolean tem = n.getItens() != null && n.getItens().stream()
                    .anyMatch(it -> it.getProduto() != null && Objects.equals(it.getProduto().getId(), idP));
            if (!tem) continue;
            notasDoProd.add(n);
            if (n instanceof NotaCompra && n.getPessoa() != null && forns.stream().noneMatch(f -> f.getId().equals(n.getPessoa().getId()))) {
                forns.add(n.getPessoa());
            }
            if (n instanceof NotaVenda && n.getPessoa() != null && clis.stream().noneMatch(c -> c.getId().equals(n.getPessoa().getId()))) {
                clis.add(n.getPessoa());
            }
        }
        String um = prodAlvo.getUm() != null ? prodAlvo.getUm().getSigla() : "UN";
        BigDecimal saldo = consCache != null ? consCache.saldosPorProduto().getOrDefault(idP, BigDecimal.ZERO) : BigDecimal.ZERO;
        LocalDate val = consCache != null ? consCache.menorValidadePorProduto().get(idP) : null;
        var diag = ponte.diagnosticarImpactoProduto(idP, prodAlvo.getNome());

        noCentral = new NoCaixa("PRODUTO", encurtar(prodAlvo.getNome(), 22),
                FormatadorSeguroUI.formatarMoeda(prodAlvo.getPrecoVenda()) + " • " + FormatadorSeguroUI.formatarQuantidade(saldo, um),
                "", "#0F172A", "#334155",
                "Raio-X do Produto '" + prodAlvo.getNome() + "' • Saldo em Estoque: " + FormatadorSeguroUI.formatarQuantidade(saldo, um)
                        + " • Fator dominante: " + diag.fatorMaisImpactante(),
                "Analise causas de comportamento, estoque e fornecedores de '" + prodAlvo.getNome() + "'.",
                "PRODUTO", idP, 0, 0);

        nosPerifericos.add(new NoCaixa("ESTOQUE & VALIDADE", FormatadorSeguroUI.formatarQuantidade(saldo, um),
                "Val.: " + FormatadorSeguroUI.formatarDataAmigavel(val), "Lotes",
                "#047857", "#10B981",
                "Saldo consolidado: " + FormatadorSeguroUI.formatarQuantidade(saldo, um) + " • Próx. vencimento: " + FormatadorSeguroUI.formatarDataAmigavel(val),
                "Avalie o giro e vencimento de '" + prodAlvo.getNome() + "'.",
                "GERAL", null, 0, 0));

        if (!forns.isEmpty()) {
            Pessoa f = forns.get(0);
            nosPerifericos.add(new NoCaixa("FORNECEDOR", encurtar(f.getNome(), 22),
                    forns.size() + " origem(ns)", "Entrada", "#1D4ED8", "#3B82F6",
                    "Principal fornecedor: " + f.getNome() + ". Duplo clique para abrir o fornecedor.",
                    "Avalie desempenho e custo do fornecedor '" + f.getNome() + "'.",
                    "PESSOA", f.getId(), 0, 0));
        }

        if (!clis.isEmpty()) {
            Pessoa c = clis.get(0);
            nosPerifericos.add(new NoCaixa("COMPRADORES", encurtar(c.getNome(), 22),
                    clis.size() + " cliente(s)", "Demanda", "#6D28D9", "#8B5CF6",
                    "Cliente destaque: " + c.getNome() + ". Duplo clique para expandir perfil.",
                    "Analise perfil dos clientes de '" + prodAlvo.getNome() + "'.",
                    "PESSOA", c.getId(), 0, 0));
        }

        nosPerifericos.add(new NoCaixa("CAUSA EXTERNA", encurtar(diag.fatorMaisImpactante(), 22),
                String.format(FormatadorSeguroUI.LOCALE_BR, "Chuva %.2fx | Ev %.2fx", diag.liftClimaChuvoso(), diag.liftEventosCulturais()),
                "Sazonal", "#B45309", "#F59E0B",
                diag.recomendacaoEstoque(),
                "Explique as causas de comportamento sazonal de '" + prodAlvo.getNome() + "'.",
                "CAUSA", idP, 0, 0));

        for (int i = 0; i < Math.min(2, notasDoProd.size()); i++) {
            Nota n = notasDoProd.get(i);
            nosPerifericos.add(new NoCaixa("NOTA #" + n.getId(),
                    encurtar(n instanceof NotaCompra ? "Entrada" : (n instanceof NotaVenda ? "Venda" : "Movimentação"), 22),
                    FormatadorSeguroUI.formatarMoeda(n.getValorTotal()), "Ciclo",
                    "#0369A1", "#0EA5E9",
                    "Nota #" + n.getId() + " em " + FormatadorSeguroUI.formatarDataHoraAmigavel(n.getDataGerada())
                            + " (Duplo clique para ver itens e ciclo de pagamento)",
                    "Explique a Nota #" + n.getId() + " do produto '" + prodAlvo.getNome() + "'.",
                    "NOTA", n.getId(), 0, 0));
        }
    }

    private void expandirPessoaComoCentro(Pessoa pes) {
        noCentral = new NoCaixa(pes.isFornecedor() ? "FORNECEDOR" : "CLIENTE",
                encurtar(pes.getNome(), 22),
                "Limite " + FormatadorSeguroUI.formatarMoeda(pes.getLimite()),
                "", "#1E3A8A", "#3B82F6",
                "Dossiê relacional de " + pes.getNome() + " • Clique nas notas ao redor para expandir seus ciclos financeiros.",
                "Faça uma análise completa do relacionamento com " + pes.getNome() + ".",
                "PESSOA", pes.getId(), 0, 0);

        for (Nota n : notasCache) {
            if (n.getPessoa() != null && Objects.equals(n.getPessoa().getId(), pes.getId())) {
                String tipo = n instanceof NotaCompra ? "ENTRADA #" + n.getId()
                        : (n instanceof NotaVenda ? "VENDA #" + n.getId() : "NOTA #" + n.getId());
                nosPerifericos.add(new NoCaixa(tipo,
                        FormatadorSeguroUI.formatarDataAmigavel(n.getDataGerada() != null ? n.getDataGerada().toLocalDate() : null),
                        FormatadorSeguroUI.formatarMoeda(n.getValorTotal()),
                        n instanceof NotaCompra ? "Compra" : "Venda",
                        n instanceof NotaPerda ? "#B91C1C" : "#047857",
                        n instanceof NotaPerda ? "#EF4444" : "#10B981",
                        FormatadorSeguroUI.tratarParaExibicao(n.getDescricao(), tipo) + " • Duplo clique para ver pagamentos e itens.",
                        "Detalhe a Nota #" + n.getId() + " de " + pes.getNome(),
                        "NOTA", n.getId(), 0, 0));
                if (nosPerifericos.size() >= 6) break;
            }
        }
    }

    private void expandirNotaComoCentro(Nota nota) {
        Nota completa = nota.getId() != null ? ponte.buscarNotaCompletaPorId(nota.getId()) : nota;
        if (completa == null) completa = nota;

        noCentral = new NoCaixa("NOTA #" + completa.getId(),
                encurtar(completa.getPessoa() != null ? completa.getPessoa().getNome() : "Operação Balcão", 22),
                FormatadorSeguroUI.formatarMoeda(completa.getValorTotal()),
                "", "#0F172A", "#334155",
                "Ciclo completo da Nota #" + completa.getId() + " emitida em "
                        + FormatadorSeguroUI.formatarDataHoraAmigavel(completa.getDataGerada()),
                "Analise itens, pagamentos e lucratividade da Nota #" + completa.getId(),
                "NOTA", completa.getId(), 0, 0);

        if (completa.getPessoa() != null) {
            Pessoa p = completa.getPessoa();
            nosPerifericos.add(new NoCaixa("ENTIDADE", encurtar(p.getNome(), 22),
                    p.isFornecedor() ? "Fornecedor" : "Cliente", "Cadastro",
                    "#1D4ED8", "#3B82F6",
                    "Pessoa vinculada: " + p.getNome() + " (Duplo clique expande histórico)",
                    "Resuma histórico de " + p.getNome(), "PESSOA", p.getId(), 0, 0));
        }

        if (completa.getPagamentos() != null) {
            for (NotaPagamento np : completa.getPagamentos()) {
                String forma = np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "Pagamento";
                String mod = (np instanceof PagamentoParcelado pp) ? "Parcelado " + pp.getParcelas().size() + "x"
                        : (np instanceof PagamentoAPrazo ? "A Prazo" : "À Vista");
                boolean liq = np.isLiquidado();
                nosPerifericos.add(new NoCaixa("PAGAMENTO", encurtar(forma + " • " + mod, 22),
                        FormatadorSeguroUI.formatarMoeda(np.calcularValorFinal()),
                        liq ? "Quitado" : "Aberto",
                        liq ? "#047857" : "#D97706", liq ? "#10B981" : "#F59E0B",
                        "Nota de Pagamento (" + forma + " — " + mod + ") • Status: " + (liq ? "Liquidada" : "Em Aberto")
                                + " • Taxa operadora: " + FormatadorSeguroUI.formatarMoeda(np.calcularCustoTaxaOperadora()),
                        "Detalhe o pagamento " + forma + " da Nota #" + completa.getId(),
                        "GERAL", null, 0, 0));
            }
        }

        if (completa.getItens() != null) {
            for (Item<? extends Produto> it : completa.getItens()) {
                if (nosPerifericos.size() >= 6) break;
                if (it.getProduto() == null) continue;
                nosPerifericos.add(new NoCaixa("ITEM", encurtar(it.getProduto().getNome(), 22),
                        FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), "") + " • " + FormatadorSeguroUI.formatarMoeda(it.getSubtotal()),
                        "Mercadoria", "#6D28D9", "#8B5CF6",
                        it.getProduto().getNome() + " • Duplo clique para expandir o produto",
                        "Analise o item '" + it.getProduto().getNome() + "' na Nota #" + completa.getId(),
                        "PRODUTO", it.getProduto().getId(), 0, 0));
            }
        }
    }

    private void expandirCarteiraPagamentos(NoCaixa categoria) {
        noCentral = new NoCaixa(categoria.categoria(), categoria.titulo(), categoria.metrica(), "",
                categoria.headerHex(), categoria.borderHex(),
                "Detalhamento das Notas vinculadas a: " + categoria.categoria(),
                categoria.promptIA(), "GERAL", null, 0, 0);

        for (Nota n : notasCache) {
            if (n.getPagamentos() == null) continue;
            boolean match = false;
            for (NotaPagamento np : n.getPagamentos()) {
                if ("MOD_VISTA".equals(categoria.tipoEntidade()) && np instanceof PagamentoAVista) match = true;
                else if ("MOD_PRAZO".equals(categoria.tipoEntidade()) && np instanceof PagamentoAPrazo) match = true;
                else if ("MOD_PARCELADO".equals(categoria.tipoEntidade()) && np instanceof PagamentoParcelado) match = true;
                else if ("CART_RECEBER".equals(categoria.tipoEntidade()) && !(n instanceof NotaCompra) && !np.isLiquidado()) match = true;
                else if ("CART_PAGAR".equals(categoria.tipoEntidade()) && (n instanceof NotaCompra) && !np.isLiquidado()) match = true;
            }
            if (match) {
                String pNome = n.getPessoa() != null ? n.getPessoa().getNome() : "Consumidor Final";
                nosPerifericos.add(new NoCaixa("NOTA #" + n.getId(), encurtar(pNome, 22),
                        FormatadorSeguroUI.formatarMoeda(n.getValorTotal()), "Ver Ciclo",
                        "#1D4ED8", "#3B82F6",
                        "Nota #" + n.getId() + " (" + pNome + ") • Duplo clique expande pagamentos e parcelas",
                        "Verifique status da Nota #" + n.getId(),
                        "NOTA", n.getId(), 0, 0));
                if (nosPerifericos.size() >= 6) break;
            }
        }
    }

    private void voltarNivelAnterior() {
        if (pilhaNiveis.isEmpty()) return;
        SnapshotNivel prev = pilhaNiveis.pop();
        this.noCentral = prev.central();
        this.nosPerifericos.clear();
        this.nosPerifericos.addAll(prev.perifericos());
        this.indiceSelecionado = -1;
        this.panX = 0;
        this.panY = 0;
        atualizarEstadoNavegacao();
        atualizarDetalheSelecionado(noCentral);
        desenharDiagrama();
    }

    private void atualizarEstadoNavegacao() {
        int nivel = pilhaNiveis.size() + 1;
        lblBreadcrumb.setText("Nível " + nivel + (noCentral != null ? " • " + encurtar(noCentral.titulo(), 18) : ""));
        btnVoltarNivel.setDisable(pilhaNiveis.isEmpty());
    }

    private void abrirModalCardDoNo(NoCaixa alvo) {
        if (alvo == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Categoria", alvo.categoria());
        meta.put("Título", alvo.titulo());
        meta.put("Métrica Principal", alvo.metrica());
        meta.put("Relação", FormatadorSeguroUI.tratarParaExibicao(alvo.rotuloAresta(), "Nó Central"));
        meta.put("Resumo Analítico", alvo.detalheCompleto());

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "🕸️", alvo.titulo(), alvo.categoria(), alvo.headerHex(),
                meta, null, null,
                () -> {
                    if (conectorIA != null) {
                        conectorIA.perguntarAoCopiloto(this, alvo.titulo(), alvo.detalheCompleto(), alvo.promptIA());
                    }
                });
    }

    private Long buscarIdProdutoPorNome(String nome) {
        if (nome == null) return null;
        String low = nome.toLowerCase(Locale.ROOT);
        for (Produto p : produtosCache) {
            if (p.getNome() != null && p.getNome().toLowerCase(Locale.ROOT).contains(low)) {
                return p.getId();
            }
        }
        return null;
    }

    private NoCaixa obterNoSelecionadoOuCentral() {
        if (indiceSelecionado >= 0 && indiceSelecionado < nosPerifericos.size()) {
            return nosPerifericos.get(indiceSelecionado);
        }
        return noCentral;
    }

    private void atualizarDetalheSelecionado(NoCaixa no) {
        if (no == null) return;
        lblDetalheTitulo.setText("[" + no.categoria() + "] " + no.titulo() + " — " + no.metrica());
        lblDetalheTexto.setText(no.detalheCompleto());
        btnExpandirNo.setDisable(no.tipoEntidade() == null || "GERAL".equals(no.tipoEntidade()) || "CAUSA".equals(no.tipoEntidade()));
    }

    private void tratarCliqueCanvas(double mx, double my, boolean duploClique) {
        for (int i = 0; i < nosPerifericos.size(); i++) {
            NoCaixa no = nosPerifericos.get(i);
            if (dentroCaixa(mx, my, no)) {
                indiceSelecionado = i;
                atualizarDetalheSelecionado(no);
                desenharDiagrama();
                if (duploClique) {
                    expandirProceduralmente(no);
                }
                return;
            }
        }
        if (noCentral != null && dentroCaixa(mx, my, noCentral)) {
            indiceSelecionado = -1;
            atualizarDetalheSelecionado(noCentral);
            desenharDiagrama();
            if (duploClique) {
                abrirModalCardDoNo(noCentral);
            }
        }
    }

    private boolean dentroCaixa(double mx, double my, NoCaixa no) {
        double halfW = (BOX_W * escalaZoom) / 2.0;
        double halfH = (BOX_H * escalaZoom) / 2.0;
        return mx >= no.cx() - halfW && mx <= no.cx() + halfW
                && my >= no.cy() - halfH && my <= no.cy() + halfH;
    }

    private void desenharDiagrama() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 40 || h <= 40 || noCentral == null) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        g.setFill(Color.web("#F8FAFC"));
        g.fillRect(0, 0, w, h);
        g.setStroke(Color.web("#E2E8F0"));
        g.setLineWidth(0.8);
        for (double gx = 24; gx < w; gx += 32) g.strokeLine(gx, 0, gx, h);
        for (double gy = 24; gy < h; gy += 32) g.strokeLine(0, gy, w, gy);

        double cx = (w / 2.0) + panX;
        double cy = (h / 2.0) + panY;
        double rx = Math.min(w * 0.35, emTelaCheia ? 360 : 250) * escalaZoom;
        double ry = Math.min(h * 0.35, emTelaCheia ? 210 : 122) * escalaZoom;

        noCentral = new NoCaixa(noCentral.categoria(), noCentral.titulo(), noCentral.metrica(), "",
                noCentral.headerHex(), noCentral.borderHex(), noCentral.detalheCompleto(), noCentral.promptIA(),
                noCentral.tipoEntidade(), noCentral.idEntidade(), cx, cy);

        int count = nosPerifericos.size();
        for (int i = 0; i < count; i++) {
            double ang = -Math.PI / 2.0 + (2.0 * Math.PI * i / Math.max(1, count));
            double nx = cx + Math.cos(ang) * rx;
            double ny = cy + Math.sin(ang) * ry;
            NoCaixa o = nosPerifericos.get(i);
            nosPerifericos.set(i, new NoCaixa(o.categoria(), o.titulo(), o.metrica(), o.rotuloAresta(),
                    o.headerHex(), o.borderHex(), o.detalheCompleto(), o.promptIA(),
                    o.tipoEntidade(), o.idEntidade(), nx, ny));
        }

        for (int i = 0; i < count; i++) {
            NoCaixa p = nosPerifericos.get(i);
            boolean sel = (i == indiceSelecionado);
            g.setStroke(sel ? Color.web(p.borderHex()) : Color.web("#94A3B8"));
            g.setLineWidth(sel ? 2.4 : 1.5);
            g.strokeLine(cx, cy, p.cx(), p.cy());

            if (p.rotuloAresta() != null && !p.rotuloAresta().isBlank()) {
                double mx = (cx + p.cx()) / 2.0;
                double my = (cy + p.cy()) / 2.0;
                g.setFill(Color.WHITE);
                g.fillRoundRect(mx - 32, my - 9, 64, 18, 4, 4);
                g.setStroke(Color.web("#CBD5E1"));
                g.setLineWidth(1.0);
                g.strokeRoundRect(mx - 32, my - 9, 64, 18, 4, 4);
                g.setFill(Color.web("#334155"));
                g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
                g.setTextAlign(TextAlignment.CENTER);
                g.fillText(encurtar(p.rotuloAresta(), 12), mx, my + 3.5);
            }
        }

        desenharCaixa(g, noCentral, indiceSelecionado == -1);
        for (int i = 0; i < count; i++) {
            desenharCaixa(g, nosPerifericos.get(i), i == indiceSelecionado);
        }
    }

    private void desenharCaixa(GraphicsContext g, NoCaixa no, boolean selecionada) {
        double bw = BOX_W * Math.min(1.18, Math.max(0.85, escalaZoom));
        double bh = BOX_H * Math.min(1.18, Math.max(0.85, escalaZoom));
        double x = no.cx() - bw / 2.0;
        double y = no.cy() - bh / 2.0;

        g.setFill(Color.rgb(15, 23, 42, 0.09));
        g.fillRoundRect(x + 2, y + 3, bw, bh, 6, 6);

        g.setFill(Color.WHITE);
        g.fillRoundRect(x, y, bw, bh, 6, 6);

        g.setFill(Color.web(no.headerHex()));
        g.fillRoundRect(x, y, bw, 20, 6, 6);
        g.fillRect(x, y + 12, bw, 8);

        g.setStroke(Color.web(no.borderHex()));
        g.setLineWidth(selecionada ? 2.6 : 1.0);
        g.strokeRoundRect(x, y, bw, bh, 6, 6);

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        g.fillText(encurtar(no.categoria(), 24), no.cx(), y + 14);

        g.setFill(Color.web("#0F172A"));
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        g.fillText(encurtar(no.titulo(), 22), no.cx(), y + 38);

        g.setFill(Color.web("#475569"));
        g.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        g.fillText(encurtar(no.metrica(), 24), no.cx(), y + 55);
    }

    private String encurtar(String s, int max) {
        if (s == null) return "";
        String l = s.replaceAll("\\s+", " ").trim();
        return l.length() <= max ? l : l.substring(0, max - 1).trim();
    }
}
