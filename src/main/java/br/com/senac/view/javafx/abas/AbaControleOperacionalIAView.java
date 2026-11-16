package br.com.senac.view.javafx.abas;

import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.validacao.ValidadorLoteBandeja;
import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aba 8: Central de Controle Operacional por IA.
 * Permite comandar todas as áreas do ERP (PDV, Preços, Devoluções, Estoque/Kits,
 * Notas de Entrada/Pagamentos, Estatística e Planejamento Kanban/Clima) por texto, voz ou playbooks,
 * com revisão e auditoria completa da Bandeja de Contexto em Lote.
 */
public class AbaControleOperacionalIAView extends VBox {

    private final PonteRoteadorIA ponte;
    private final ConectorProblemasIA conectorIA;

    private final Label lblResumoEstado = FormatadorSeguroUI.criarLabel("Central IA pronta", 12, true, "#334155");
    private final CheckBox chkConfirmarAntes = new CheckBox("Confirmar em popup antes de disparar comandos rápidos");
    private final TextArea txtComando = FormatadorSeguroUI.criarAreaTextoCartoon(
            "Ex.: Vender 2 guarda-chuvas e 1 água no PIX; ou: Cadastrar kit evento chuva; ou: Analisar perdas deste mês...",
            "#CBD5E1", "#0F172A");

    private final Button btnVoz = FormatadorSeguroUI.criarBotaoCartoon("🎤 Comando por Voz", "#F1F5F9", "#CBD5E1", "#334155");
    private final Button btnEnviar = FormatadorSeguroUI.criarBotaoCartoon("🚀 Executar no Copiloto IA", "#1D4ED8", "#1E40AF", "#FFFFFF");
    private final Button btnLimparChat = FormatadorSeguroUI.criarBotaoCartoon("Limpar Log", "#FFFFFF", "#CBD5E1", "#475569");

    private final VBox boxProgresso = new VBox(4);
    private final Label lblEtapa = FormatadorSeguroUI.criarLabel("", 12, true, "#1D4ED8");
    private final ProgressBar barraProgresso = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

    private final VBox boxLogRespostas = new VBox(8);
    private final ScrollPane scrollLog;

    // Painel da Bandeja completa
    private final Label lblTituloBandeja = FormatadorSeguroUI.criarLabel("Bandeja de Lote (0 itens)", 14, true, "#0F172A");
    private final Label lblValidacaoBandeja = FormatadorSeguroUI.criarLabel("Nenhuma pendência em fila.", 12, true, "#047857");
    private final VBox boxListaBandeja = new VBox(8);
    private final Button btnConfirmarLote = FormatadorSeguroUI.criarBotaoCartoon("✔ Confirmar e Gravar Lote", "#047857", "#065F46", "#FFFFFF");
    private final Button btnCorrigirLote = FormatadorSeguroUI.criarBotaoCartoon("✏️ Corrigir Conflitos", "#D97706", "#B45309", "#FFFFFF");
    private final Button btnLimparBandeja = FormatadorSeguroUI.criarBotaoCartoon("🗑️ Limpar Lote", "#FEF2F2", "#FECACA", "#991B1B");

    private volatile boolean gravandoVoz = false;
    private volatile boolean processando = false;
    private volatile boolean abaAtiva = false;

    public AbaControleOperacionalIAView(PonteRoteadorIA ponte, ConectorProblemasIA conectorIA) {
        this.ponte = ponte;
        this.conectorIA = conectorIA;

        setSpacing(10);
        setPadding(new Insets(14, 16, 14, 16));
        setStyle("-fx-background-color: #F8FAFC;");
        setMaxWidth(Double.MAX_VALUE);

        this.scrollLog = new ScrollPane(boxLogRespostas);
        scrollLog.setFitToWidth(true);
        scrollLog.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollLog.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollLog.setPrefHeight(280);
        scrollLog.setMinHeight(160);
        scrollLog.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        HBox topo = criarCabecalho();
        GridPane corpoPrincipal = criarCorpoDuasColunas();
        VBox.setVgrow(corpoPrincipal, Priority.ALWAYS);

        getChildren().addAll(topo, corpoPrincipal);

        ponte.registrarOuvinteBandeja(this::atualizarPainelBandeja);
        ponte.registrarOuvinteDados(() -> {
            if (abaAtiva) atualizarPainelBandeja();
        });

        adicionarCardLog("🤖 Central Unificada IA",
                "Aqui você comanda todo o Sazonalisei ERP por linguagem natural, voz ou atalhos por módulo, "
                        + "revisando cada operação na Bandeja antes de gravar no banco.", false);
        atualizarPainelBandeja();
    }

    private HBox criarCabecalho() {
        Label tit = FormatadorSeguroUI.criarTituloSecao("🤖", "Central de Controle Operacional por IA", "#0F172A");
        Label sub = FormatadorSeguroUI.criarSubtitulo(
                "Orquestração completa por texto, comando de voz e pré-validação em lote da Bandeja para todos os setores.", "#64748B");
        VBox boxTit = new VBox(2, tit, sub, lblResumoEstado);
        HBox.setHgrow(boxTit, Priority.ALWAYS);

        Button btnAtualizar = FormatadorSeguroUI.criarBotaoCartoon("↻ Auditar Bandeja", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnAtualizar.setOnAction(e -> atualizarPainelBandeja());

        HBox topo = new HBox(10, boxTit, btnAtualizar);
        topo.setAlignment(Pos.CENTER_LEFT);
        return topo;
    }

    private GridPane criarCorpoDuasColunas() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        ColumnConstraints colEsq = new ColumnConstraints();
        colEsq.setPercentWidth(65);
        colEsq.setHgrow(Priority.ALWAYS);
        ColumnConstraints colDir = new ColumnConstraints();
        colDir.setPercentWidth(35);
        colDir.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().addAll(colEsq, colDir);

        VBox painelComando = construirPainelComandoEPlaybooks();
        VBox painelBandeja = construirPainelBandejaCompleta();

        GridPane.setVgrow(painelComando, Priority.ALWAYS);
        GridPane.setVgrow(painelBandeja, Priority.ALWAYS);

        grid.add(painelComando, 0, 0);
        grid.add(painelBandeja, 1, 0);
        return grid;
    }

    private VBox construirPainelComandoEPlaybooks() {
        txtComando.setPrefRowCount(4);
        chkConfirmarAntes.setSelected(true);
        chkConfirmarAntes.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        barraProgresso.setMaxWidth(Double.MAX_VALUE);
        barraProgresso.setPrefHeight(8);
        boxProgresso.getChildren().setAll(lblEtapa, barraProgresso);
        boxProgresso.setVisible(false);
        boxProgresso.setManaged(false);
        boxProgresso.setPadding(new Insets(6, 10, 6, 10));
        boxProgresso.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));

        btnVoz.setOnAction(e -> alternarVoz());
        btnEnviar.setOnAction(e -> enviarComandoDigitado());
        btnLimparChat.setOnAction(e -> boxLogRespostas.getChildren().clear());

        FlowPane barraEnvio = new FlowPane(8, 6, btnVoz, btnEnviar, btnLimparChat, chkConfirmarAntes);
        barraEnvio.setAlignment(Pos.CENTER_LEFT);

        VBox cardConsole = new VBox(8,
                FormatadorSeguroUI.criarLabel("💬 Comando Livre ou por Voz (Prompt Copiloto IA)", 13, true, "#0F172A"),
                txtComando,
                barraEnvio,
                boxProgresso,
                FormatadorSeguroUI.criarLabel("📜 Histórico de Execução & Respostas IA (clique no card para expandir)", 12, true, "#334155"),
                scrollLog);
        cardConsole.setPadding(new Insets(12));
        cardConsole.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        VBox.setVgrow(cardConsole, Priority.ALWAYS);
        VBox.setVgrow(scrollLog, Priority.ALWAYS);

        // Faixa Executiva: Controle Direto Visual + Navegação em Todos os Setores pelo Hub IA
        ToggleButton tgDark = new ToggleButton("🌙 Tema Escuro");
        tgDark.setSelected(ponte.getGerenciadorTema().isModoEscuro());
        Runnable syncDarkBtn = () -> FormatadorSeguroUI.aplicarEstiloToggleCartoon(
                tgDark, tgDark.isSelected(), "#F8FAFC", "#CBD5E1", "#0F172A", "#0F172A", "#38BDF8", "#F8FAFC");
        syncDarkBtn.run();
        tgDark.setOnAction(e -> {
            ponte.getGerenciadorTema().definirModoEscuro(tgDark.isSelected());
            syncDarkBtn.run();
        });

        ColorPicker cpFundo = new ColorPicker(Color.web(ponte.getGerenciadorTema().getSnapshotAtual().resolverBgApp()));
        cpFundo.setPrefWidth(118);
        cpFundo.setOnAction(e -> {
            Color c = cpFundo.getValue();
            String hex = String.format("#%02X%02X%02X",
                    (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
            ponte.getGerenciadorTema().definirCorBackgroundPersonalizada(hex);
        });
        Button btnResetCor = FormatadorSeguroUI.criarBotaoCartoon("↺ Fundo Padrão", "#FFFFFF", "#CBD5E1", "#334155");
        btnResetCor.setOnAction(e -> ponte.getGerenciadorTema().definirCorBackgroundPersonalizada(null));

        FlowPane barraAparAndNav = new FlowPane(6, 6,
                FormatadorSeguroUI.criarLabel("🎨 Estilo:", 11, true, "#1E40AF"),
                tgDark, cpFundo, btnResetCor,
                criarBotaoAtalhoAba("🛒 PDV", "PDV", "#ECFDF5", "#047857"),
                criarBotaoAtalhoAba("📦 Estoque", "ESTOQUE", "#FFFBEB", "#92400E"),
                criarBotaoAtalhoAba("💰 Financeiro", "FINANCEIRO", "#EFF6FF", "#1E40AF"),
                criarBotaoAtalhoAba("📊 Estatística", "ESTATISTICA", "#F0F9FF", "#0369A1"),
                criarBotaoAtalhoAba("🗂️ Kanban", "KANBAN", "#EEF2FF", "#4338CA"),
                criarBotaoAtalhoAba("⚙️ API/CSV", "CONFIG", "#F8FAFC", "#0F172A"));
        barraAparAndNav.setAlignment(Pos.CENTER_LEFT);
        barraAparAndNav.setPadding(new Insets(8, 10, 8, 10));
        barraAparAndNav.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));

        // Atalhos Estratégicos responsivos sem estouro de container
        FlowPane flowAtalhos = new FlowPane(8, 8);
        flowAtalhos.setPadding(new Insets(6, 4, 6, 4));
        flowAtalhos.setMinWidth(0);

        record AtalhoSetor(String setor, String corAccent, String bgTag, String rotulo, String prompt) {}
        List<AtalhoSetor> todosAtalhos = List.of(
                new AtalhoSetor("PDV", "#047857", "#ECFDF5", "Registrar Venda Mista", "Prepare na bandeja uma venda no balcão consultando os produtos mais vendidos e pagamentos disponíveis."),
                new AtalhoSetor("PDV", "#047857", "#ECFDF5", "Edição Rápida de Preço", "Liste meus produtos atuais e ajude a ajustar o preço de venda dos itens com menor margem ou vencimento próximo."),
                new AtalhoSetor("PDV", "#047857", "#ECFDF5", "Devolução / Pós-Venda", "Consulte as últimas notas de venda e me guie para registrar uma devolução seguida de perda pós-venda mantendo o histórico."),
                new AtalhoSetor("ESTOQUE", "#B45309", "#FFFBEB", "Montar Kit Promocional", "Analise meu estoque e monte um Kit Comercial combinando produtos complementares na Bandeja."),
                new AtalhoSetor("ESTOQUE", "#B45309", "#FFFBEB", "Auditar Lotes & Validade", "Verifique quais lotes estão mais próximos do vencimento e sugira promoção ou queima controlada."),
                new AtalhoSetor("ESTOQUE", "#B45309", "#FFFBEB", "Comparar Fornecedores", "Analise meus fornecedores por custo e taxa de avaria na validade e recomende o melhor para cada produto."),
                new AtalhoSetor("FINANCEIRO", "#1D4ED8", "#EFF6FF", "Emitir Nota de Entrada", "Consulte fornecedores e itens com baixo saldo e prepare uma Nota de Compra com vencimento ajustado."),
                new AtalhoSetor("FINANCEIRO", "#1D4ED8", "#EFF6FF", "Auditar Títulos & Parcelas", "Audite todas as contas a receber/pagar e parcelas pendentes com sugestões de cobrança e quitação."),
                new AtalhoSetor("FINANCEIRO", "#1D4ED8", "#EFF6FF", "Abatimento de Perdas", "Liste perdas dentro da validade com direito a abatimento com fornecedor e prepare crédito na compra."),
                new AtalhoSetor("ESTATISTICA", "#4338CA", "#EEF2FF", "Causas de Flutuação", "Analise os produtos com maior variação nas vendas e explique se a causa veio de chuva, calor, rio ou eventos."),
                new AtalhoSetor("ESTATISTICA", "#4338CA", "#EEF2FF", "Novos Produtos (Kanban)", "Compare meu catálogo atual com previsão do tempo e eventos próximos: liste itens que NÃO tenho para cadastrar e os que JÁ tenho para reforçar."),
                new AtalhoSetor("ESTATISTICA", "#4338CA", "#EEF2FF", "Estratégia Hidrológica", "Considerando o nível atual do rio e o giro da semana, prepare um plano antecipado de compras na Bandeja.")
        );

        ToggleGroup grpSetor = new ToggleGroup();
        FlowPane barraSetores = new FlowPane(6, 4);
        String[] setores = {"TODOS", "PDV", "ESTOQUE", "FINANCEIRO", "ESTATISTICA"};
        String[] nomesSetor = {"✨ Todos", "🛒 PDV", "📦 Estoque", "💰 Financeiro", "📊 Clima & Kanban"};
        List<ToggleButton> tgs = new ArrayList<>();

        Runnable[] renderAtalhos = new Runnable[1];
        renderAtalhos[0] = () -> {
            String ativo = "TODOS";
            for (int idx = 0; idx < tgs.size(); idx++) {
                boolean sel = tgs.get(idx).isSelected();
                FormatadorSeguroUI.aplicarEstiloToggleCartoon(tgs.get(idx), sel,
                        "#F8FAFC", "#CBD5E1", "#334155", "#1D4ED8", "#1E40AF", "#FFFFFF");
                if (sel) ativo = setores[idx];
            }
            flowAtalhos.getChildren().clear();
            for (AtalhoSetor a : todosAtalhos) {
                if (!"TODOS".equals(ativo) && !ativo.equals(a.setor())) continue;
                Button btnExec = FormatadorSeguroUI.criarBotaoCartoon("▶ Executar", a.corAccent(), a.corAccent(), "#FFFFFF");
                btnExec.setOnAction(ev -> dispararPromptComOuSemPopup(a.rotulo(), a.prompt()));
                Button btnEd = FormatadorSeguroUI.criarBotaoCartoon("✏️ Editar", "#FFFFFF", "#CBD5E1", "#334155");
                btnEd.setOnAction(ev -> {
                    txtComando.setText(a.prompt());
                    txtComando.requestFocus();
                });

                Label lTag = FormatadorSeguroUI.criarBadgeCartoon(a.setor(), a.bgTag(), a.corAccent(), a.corAccent());
                Label lTit = FormatadorSeguroUI.criarLabel(a.rotulo(), 12, true, "#0F172A");
                lTit.setWrapText(true);
                lTit.setMinWidth(0);
                Label lDesc = FormatadorSeguroUI.criarLabel(a.prompt(), 11, false, "#475569");
                lDesc.setWrapText(true);
                lDesc.setMinWidth(0);

                FlowPane btns = new FlowPane(5, 4, btnExec, btnEd);
                VBox mini = new VBox(5, new FlowPane(5, 4, lTag, lTit), lDesc, btns);
                mini.setPrefWidth(215);
                mini.setMinWidth(185);
                mini.setMaxWidth(240);
                mini.setPadding(new Insets(9));
                mini.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFFFF", a.corAccent()));
                flowAtalhos.getChildren().add(mini);
            }
        };

        for (int i = 0; i < setores.length; i++) {
            ToggleButton tb = new ToggleButton(nomesSetor[i]);
            tb.setToggleGroup(grpSetor);
            if (i == 0) tb.setSelected(true);
            tb.setOnAction(e -> renderAtalhos[0].run());
            tgs.add(tb);
            barraSetores.getChildren().add(tb);
        }
        renderAtalhos[0].run();

        ScrollPane scrollAtalhos = new ScrollPane(flowAtalhos);
        scrollAtalhos.setFitToWidth(true);
        scrollAtalhos.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollAtalhos.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollAtalhos.setPrefHeight(135);
        scrollAtalhos.setMinHeight(100);
        scrollAtalhos.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        VBox cardPlaybooks = new VBox(8,
                FormatadorSeguroUI.criarLabel("⚡ Central Unificada de Comandos por Setor (Zero Estouro — Faça Tudo Aqui)", 13, true, "#0F172A"),
                barraSetores,
                scrollAtalhos);
        cardPlaybooks.setPadding(new Insets(12));
        cardPlaybooks.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#93C5FD"));

        VBox col = new VBox(10, barraAparAndNav, cardConsole, cardPlaybooks);
        VBox.setVgrow(cardConsole, Priority.ALWAYS);
        return col;
    }

    private Button criarBotaoAtalhoAba(String label, String codigoAba, String bgHex, String textHex) {
        Button b = FormatadorSeguroUI.criarBotaoCartoon(label, bgHex, textHex, textHex);
        b.setOnAction(e -> ponte.navegarParaAba(codigoAba));
        return b;
    }

    private VBox construirPainelBandejaCompleta() {
        ScrollPane scrollBandeja = new ScrollPane(boxListaBandeja);
        scrollBandeja.setFitToWidth(true);
        scrollBandeja.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollBandeja.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollBandeja.setPrefHeight(420);
        scrollBandeja.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: #E2E8F0; -fx-border-radius: 6px;");
        VBox.setVgrow(scrollBandeja, Priority.ALWAYS);

        btnConfirmarLote.setOnAction(e -> confirmarLoteComModal());
        btnCorrigirLote.setOnAction(e -> corrigirConflitosBandeja());
        btnLimparBandeja.setOnAction(e -> limparBandejaComModal());

        FlowPane rodapeBandeja = new FlowPane(8, 6, btnConfirmarLote, btnCorrigirLote, btnLimparBandeja);
        rodapeBandeja.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8,
                lblTituloBandeja,
                lblValidacaoBandeja,
                scrollBandeja,
                rodapeBandeja);
        box.setPadding(new Insets(12));
        box.setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
        box.setMinWidth(300);
        box.setPrefWidth(350);
        box.setMaxWidth(380);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    // ==================== CICLO DE VIDA & TEMA ====================

    public void setAtiva(boolean ativa) {
        this.abaAtiva = ativa;
        if (ativa) {
            atualizarPainelBandeja();
        }
    }

    public void carregarSeNecessario() {
        atualizarPainelBandeja();
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap != null) {
            setStyle("-fx-background-color: " + snap.resolverBgApp() + ";");
        }
    }

    // ==================== EXECUÇÃO IA & VOZ ====================

    private void dispararPromptComOuSemPopup(String titulo, String prompt) {
        if (processando) return;
        if (chkConfirmarAntes.isSelected()) {
            DialogoCorporativoModal.confirmarEnvioIA(
                    this,
                    titulo,
                    "Atalho selecionado na Central IA (" + titulo + ")",
                    prompt,
                    promptFinal -> {
                        adicionarCardLog("👤 " + titulo, promptFinal, true);
                        executarEnvioIA(promptFinal);
                    }
            );
        } else {
            adicionarCardLog("👤 " + titulo, prompt, true);
            executarEnvioIA(prompt);
        }
    }

    private void enviarComandoDigitado() {
        if (processando) return;
        String texto = txtComando.getText() != null ? txtComando.getText().trim() : "";
        if (texto.isEmpty()) return;
        txtComando.clear();
        adicionarCardLog("👤 Comando Operacional", texto, true);
        executarEnvioIA(texto);
    }

    private void executarEnvioIA(String prompt) {
        definirEstadoProcessando(true, "Analisando contexto e rotas do ERP...");
        ponte.enviarMensagemIAAsync(prompt, this::atualizarEtapa, resultado -> {
            definirEstadoProcessando(false, null);
            adicionarCardLog("🤖 Resposta do Copiloto IA", resultado.respostaTexto(), false);
            atualizarPainelBandeja();
        });
    }

    private void abrirModalVozAssistidaCentral() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("🎤 Ditado de Voz / Comando Rápido IA");
        if (getScene() != null && getScene().getWindow() != null) dlg.initOwner(getScene().getWindow());

        TextField txtD = FormatadorSeguroUI.criarCampoTextoCartoon("Use Win+H para ditar ou escreva o comando para a IA...", "#93C5FD", "#0F172A");
        Button btnRun = FormatadorSeguroUI.criarBotaoCartoon("🚀 Executar no Copiloto", "#1D4ED8", "#1E40AF", "#FFFFFF");
        Runnable go = () -> {
            String f = txtD.getText() != null ? txtD.getText().trim() : "";
            dlg.close();
            if (!f.isEmpty()) {
                adicionarCardLog("🎤 Ditado", f, true);
                executarEnvioIA(f);
            }
        };
        btnRun.setOnAction(e -> go.run());
        txtD.setOnAction(e -> go.run());
        Button btnC = FormatadorSeguroUI.criarBotaoCartoon("Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnC.setOnAction(e -> dlg.close());

        VBox b = new VBox(10,
                FormatadorSeguroUI.criarLabel("🎤 Microfone indisponível — Ditado Assistido (Win+H no Windows)", 13, true, "#0F172A"),
                txtD, new HBox(8, btnC, btnRun));
        b.setPadding(new Insets(16));
        b.setPrefWidth(520);
        b.setStyle("-fx-background-color: #FFFFFF;");
        dlg.setScene(new Scene(b));
        Platform.runLater(txtD::requestFocus);
        dlg.showAndWait();
    }

    private void alternarVoz() {
        if (processando) return;
        if (!gravandoVoz) {
            try {
                if (!ponte.getCapturadorVoz().isMicrofoneDisponivel()) {
                    abrirModalVozAssistidaCentral();
                    return;
                }
                ponte.getCapturadorVoz().iniciarGravacao();
                gravandoVoz = true;
                FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVoz, "#FEE2E2", "#EF4444", "#991B1B", 12);
                btnVoz.setText("⏹️ Parar & Enviar Voz");
                lblEtapa.setText("🎤 Gravando comando de voz...");
                barraProgresso.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                boxProgresso.setVisible(true);
                boxProgresso.setManaged(true);
            } catch (Exception ex) {
                adicionarCardLog("⚠️ Falha de Áudio", FormatadorSeguroUI.traduzirErroAmigavel(ex.getMessage()) + " — usando ditado assistido.", false);
                abrirModalVozAssistidaCentral();
            }
        } else {
            gravandoVoz = false;
            FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVoz, "#F1F5F9", "#CBD5E1", "#334155", 12);
            btnVoz.setText("🎤 Comando por Voz");
            adicionarCardLog("🎤 Áudio Capturado", "Comando enviado para transcrição e roteamento inteligente.", true);
            definirEstadoProcessando(true, "Transcrevendo áudio...");
            ponte.pararVozEEnviarAsync(this::atualizarEtapa, resultado -> {
                definirEstadoProcessando(false, null);
                adicionarCardLog("🤖 Resposta por Voz", resultado.respostaTexto(), false);
                atualizarPainelBandeja();
            });
        }
    }

    private void definirEstadoProcessando(boolean ativo, String textoInicial) {
        this.processando = ativo;
        btnEnviar.setDisable(ativo);
        btnConfirmarLote.setDisable(ativo);
        btnCorrigirLote.setDisable(ativo);
        boxProgresso.setVisible(ativo);
        boxProgresso.setManaged(ativo);
        if (ativo && textoInicial != null) {
            lblEtapa.setText(textoInicial);
            barraProgresso.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
    }

    private void atualizarEtapa(String etapa) {
        if (etapa == null || etapa.isBlank()) return;
        lblEtapa.setText(etapa);
        var m = java.util.regex.Pattern.compile("Etapa\\s+(\\d+)/(\\d+)").matcher(etapa);
        if (m.find()) {
            double atual = Double.parseDouble(m.group(1));
            double total = Math.max(1.0, Double.parseDouble(m.group(2)));
            barraProgresso.setProgress(atual / total);
        }
    }

    private void adicionarCardLog(String titulo, String conteudo, boolean usuario) {
        String bg = usuario ? "#EFF6FF" : "#FFFFFF";
        String bd = usuario ? "#93C5FD" : "#CBD5E1";
        String ac = usuario ? "#1E40AF" : "#0F172A";

        Label lTit = FormatadorSeguroUI.criarLabel(titulo, 12, true, ac);
        Label lMsg = FormatadorSeguroUI.criarLabel(FormatadorSeguroUI.tratarParaExibicao(conteudo, "-"), 12, false, "#334155");

        VBox card = new VBox(4, lTit, lMsg);
        card.setPadding(new Insets(9, 11, 9, 11));
        card.setCursor(Cursor.HAND);
        card.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(bg, bd));
        card.setOnMouseClicked(e -> {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("Origem", titulo);
            meta.put("Mensagem Completa", conteudo);
            DialogoCorporativoModal.exibirCardDetalhes(
                    this, usuario ? "👤" : "🤖", titulo, "Registro da Central IA", ac, meta, null, null, null);
        });

        boxLogRespostas.getChildren().add(card);
        Platform.runLater(() -> scrollLog.setVvalue(1.0));
    }

    // ==================== GESTOR DA BANDEJA EM LOTE ====================

    private void atualizarPainelBandeja() {
        BandejaOperacoesContexto bandeja = ponte.obterBandejaAtiva();
        List<ItemBandeja> itens = bandeja.getItens();
        ValidadorLoteBandeja.RelatorioValidacaoLote relatorio = ponte.validarBandejaAtiva();

        boolean micOk = ponte.getCapturadorVoz().isMicrofoneDisponivel();
        lblResumoEstado.setText(String.format(
                "📦 %d operação(ões) na Bandeja • ⚠️ %d conflito(s) • 🎤 Microfone: %s",
                itens.size(), relatorio.totalConflitos(), micOk ? "Pronto" : "Indisponível"));

        lblTituloBandeja.setText("📦 Bandeja de Operações em Lote (" + itens.size() + ")");
        if (itens.isEmpty()) {
            lblValidacaoBandeja.setText("Nenhuma operação pendente na Bandeja. Envie um comando à esquerda para montar seu lote.");
            lblValidacaoBandeja.setStyle("-fx-text-fill: #475569; -fx-font-weight: bold;");
        } else if (relatorio.todoValido()) {
            lblValidacaoBandeja.setText("✔ Pré-validação aprovada: todas as operações estão prontas para gravar!");
            lblValidacaoBandeja.setStyle("-fx-text-fill: #047857; -fx-font-weight: bold;");
        } else {
            lblValidacaoBandeja.setText("⚠️ " + relatorio.totalConflitos() + " conflito(s) detectado(s). Clique em Corrigir Conflitos ou revise abaixo.");
            lblValidacaoBandeja.setStyle("-fx-text-fill: #B45309; -fx-font-weight: bold;");
        }

        btnConfirmarLote.setDisable(itens.isEmpty() || processando);
        btnCorrigirLote.setDisable(itens.isEmpty() || processando);
        btnLimparBandeja.setDisable(itens.isEmpty() || processando);

        boxListaBandeja.getChildren().clear();
        for (ItemBandeja item : itens) {
            boxListaBandeja.getChildren().add(criarCardItemBandeja(item, relatorio));
        }
    }

    private VBox criarCardItemBandeja(ItemBandeja item, ValidadorLoteBandeja.RelatorioValidacaoLote relatorio) {
        String conflito = relatorio.primeiroConflito(item.getIdItem());
        boolean temProblema = (conflito != null && !conflito.isBlank()) || item.isPendente();

        String badgeBg = temProblema ? "#FEF3C7" : (item.isPulado() ? "#F1F5F9" : "#ECFDF5");
        String badgeBd = temProblema ? "#FCD34D" : (item.isPulado() ? "#CBD5E1" : "#6EE7B7");
        String badgeFg = temProblema ? "#92400E" : (item.isPulado() ? "#475569" : "#065F46");
        Label badge = FormatadorSeguroUI.criarBadgeCartoon(item.getStatus().name(), badgeBg, badgeBd, badgeFg);

        Label lblRota = FormatadorSeguroUI.criarLabel(
                FormatadorSeguroUI.traduzirRotaAmigavel(item.getRota()), 12, true, "#0F172A");
        Label lblDesc = FormatadorSeguroUI.criarLabel(
                FormatadorSeguroUI.tratarParaExibicao(item.getDescricaoAmigavel(), "-"), 12, false, "#334155");

        VBox card = new VBox(5, new FlowPane(6, 4, badge, lblRota), lblDesc);
        if (temProblema) {
            String msg = conflito != null ? conflito : "Campos pendentes: " + String.join(", ", item.getCamposFaltantes());
            card.getChildren().add(FormatadorSeguroUI.criarLabel("⚠️ " + msg, 11, true, "#B45309"));
        }

        Button btnDetalhes = FormatadorSeguroUI.criarBotaoCartoon("🔍 Ver", "#FFFFFF", "#CBD5E1", "#0F172A");
        btnDetalhes.setOnAction(e -> abrirDetalhesItemBandeja(item, conflito));

        Button btnPular = FormatadorSeguroUI.criarBotaoCartoon(
                item.isPulado() ? "▶ Retomar" : "⏸️ Pular", "#F8FAFC", "#CBD5E1", "#334155");
        btnPular.setOnAction(e -> {
            if (item.isPulado()) item.retomar();
            else item.pular();
            atualizarPainelBandeja();
        });

        Button btnRemover = FormatadorSeguroUI.criarBotaoCartoon("🗑️ Remover", "#FEF2F2", "#FECACA", "#991B1B");
        btnRemover.setOnAction(e -> ponte.removerItemBandeja(item.getIdItem()));

        HBox botoes = new HBox(6, btnDetalhes, btnPular, btnRemover);
        botoes.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(botoes);

        card.setPadding(new Insets(9, 10, 9, 10));
        card.setCursor(Cursor.HAND);
        card.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(
                temProblema ? "#FFFBEB" : "#F8FAFC",
                temProblema ? "#FCD34D" : "#E2E8F0"));
        card.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) abrirDetalhesItemBandeja(item, conflito);
        });
        return card;
    }

    private void abrirDetalhesItemBandeja(ItemBandeja item, String conflito) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Código Operação", item.getIdItem());
        meta.put("Ação Amigável", FormatadorSeguroUI.traduzirRotaAmigavel(item.getRota()));
        meta.put("Status na Bandeja", item.getStatus().name());
        meta.put("Descrição", FormatadorSeguroUI.tratarParaExibicao(item.getDescricaoAmigavel(), "-"));
        if (conflito != null) meta.put("Alerta de Validação", conflito);
        item.getParametros().forEach((k, v) -> meta.put("Parâmetro • " + k, FormatadorSeguroUI.tratarParaExibicao(v, "-")));

        DialogoCorporativoModal.exibirCardDetalhes(
                this, "📋", "Item da Bandeja: " + item.getIdItem(),
                item.getStatus().name(), "#0F172A", meta, null, null, null);
    }

    private void confirmarLoteComModal() {
        List<ItemBandeja> itens = ponte.obterBandejaAtiva().getItens();
        if (itens.isEmpty() || processando) return;
        Map<String, String> resumo = new LinkedHashMap<>();
        int idx = 1;
        for (ItemBandeja it : itens) {
            resumo.put("Operação #" + idx++, FormatadorSeguroUI.traduzirRotaAmigavel(it.getRota()) + " — " + it.getDescricaoAmigavel());
        }
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Confirmar Execução em Lote da Bandeja (" + itens.size() + " operação(ões))",
                "Todas as operações validadas da bandeja serão executadas em sequência no banco.",
                resumo,
                () -> {
                    adicionarCardLog("✔ Confirmar Lote", "Executando " + itens.size() + " operação(ões) da bandeja.", true);
                    definirEstadoProcessando(true, "Executando operações no ERP...");
                    ponte.confirmarBandejaAsync(res -> {
                        definirEstadoProcessando(false, null);
                        adicionarCardLog("🤖 Lote Concluído", res.respostaTexto(), false);
                        atualizarPainelBandeja();
                    });
                }
        );
    }

    private void corrigirConflitosBandeja() {
        if (processando) return;
        adicionarCardLog("✏️ Corrigir Conflitos", "Solicitando correção automática à IA.", true);
        definirEstadoProcessando(true, "Corrigindo conflitos da bandeja...");
        ponte.corrigirConflitosBandejaAsync(res -> {
            definirEstadoProcessando(false, null);
            adicionarCardLog("🤖 Bandeja Ajustada", res.respostaTexto(), false);
            atualizarPainelBandeja();
        });
    }

    private void limparBandejaComModal() {
        List<ItemBandeja> itens = ponte.obterBandejaAtiva().getItens();
        if (itens.isEmpty() || processando) return;
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Descartar Bandeja de Operações",
                "Tem certeza que deseja limpar todas as operações pendentes sem gravar no banco?",
                Map.of("Operações descartadas", String.valueOf(itens.size())),
                () -> ponte.limparBandejaAsync(res -> {
                    adicionarCardLog("🗑️ Bandeja Limpa", res.respostaTexto(), false);
                    atualizarPainelBandeja();
                })
        );
    }
}
