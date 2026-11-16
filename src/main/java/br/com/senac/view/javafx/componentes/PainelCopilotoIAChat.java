package br.com.senac.view.javafx.componentes;

import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.validacao.ValidadorLoteBandeja;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * Janela Popover Flutuante do Assistente IA + Microfone + Bandeja de Contexto.
 * Ancorada no canto inferior direito sem ocupar área fixa dos painéis corporativos.
 */
public class PainelCopilotoIAChat extends VBox {

    private final PonteRoteadorIA ponte;

    private final Label lblTitulo;
    private final Button btnMinimizar;
    private final VBox boxProgresso;
    private final Label lblEtapaProgresso;
    private final ProgressBar barraProgresso;

    private final VBox listaMensagens;
    private final ScrollPane scrollChat;

    private final VBox boxBandejaContainer;
    private final Label lblTituloBandeja;
    private final Label lblAvisoValidacao;
    private final VBox listaCardsBandeja;
    private final Button btnConfirmarTudo;
    private final Button btnAjustar;
    private final Button btnLimparBandeja;

    private final TextArea txtEntrada;
    private final Button btnVoz;
    private final Button btnEnviar;

    private boolean gravandoVoz = false;
    private boolean processando = false;
    private Runnable aoMinimizar;
    private GerenciadorTemaClimatico.SnapshotTemaClimatico ultimoTema;

    public PainelCopilotoIAChat(PonteRoteadorIA ponte) {
        this.ponte = ponte;

        setSpacing(8);
        setPadding(new Insets(0));
        setPrefWidth(390);
        setMaxWidth(390);
        setPrefHeight(510);
        setMaxHeight(520);
        setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #94A3B8; -fx-border-width: 1px; "
                + "-fx-background-radius: 10px; -fx-border-radius: 10px; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(15, 23, 42, 0.22), 18, 0, 0, 6);");

        // 1. Cabeçalho Executivo do Balão Flutuante
        this.lblTitulo = FormatadorSeguroUI.criarLabel("💬 Assistente Operacional IA", 13, true, "#FFFFFF");
        this.btnMinimizar = FormatadorSeguroUI.criarBotaoCartoon("— Minimizar", "#1E293B", "#475569", "#F8FAFC");
        this.btnMinimizar.setOnAction(e -> {
            if (aoMinimizar != null) aoMinimizar.run();
        });

        HBox cabecalho = new HBox(10, lblTitulo, btnMinimizar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        cabecalho.setPadding(new Insets(8, 12, 8, 12));
        cabecalho.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 10px 10px 0 0;");
        HBox.setHgrow(lblTitulo, Priority.ALWAYS);

        // 2. Barra de progresso
        this.lblEtapaProgresso = FormatadorSeguroUI.criarLabel("Processando...", 12, true, "#1D4ED8");
        this.barraProgresso = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        this.barraProgresso.setMaxWidth(Double.MAX_VALUE);
        this.barraProgresso.setPrefHeight(8);
        this.boxProgresso = new VBox(4, lblEtapaProgresso, barraProgresso);
        this.boxProgresso.setPadding(new Insets(6, 10, 6, 10));
        this.boxProgresso.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
        this.boxProgresso.setVisible(false);
        this.boxProgresso.setManaged(false);

        // 3. Histórico do Chat
        this.listaMensagens = new VBox(8);
        this.listaMensagens.setPadding(new Insets(8));
        this.listaMensagens.setMinHeight(Region.USE_PREF_SIZE);

        this.scrollChat = FormatadorSeguroUI.envolverScrollSuave(listaMensagens);
        this.scrollChat.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollChat, Priority.ALWAYS);

        // 4. Bandeja de Contexto Compacta
        this.lblTituloBandeja = FormatadorSeguroUI.criarLabel("Bandeja de Operações (0)", 12, true, "#0F172A");
        this.lblAvisoValidacao = FormatadorSeguroUI.criarLabel("", 11, true, "#92400E");
        this.lblAvisoValidacao.setVisible(false);
        this.lblAvisoValidacao.setManaged(false);

        this.listaCardsBandeja = new VBox(6);
        this.listaCardsBandeja.setMinHeight(Region.USE_PREF_SIZE);

        ScrollPane scrollBandeja = FormatadorSeguroUI.envolverScrollSuave(listaCardsBandeja);
        scrollBandeja.setMaxHeight(150);

        this.btnConfirmarTudo = FormatadorSeguroUI.criarBotaoCartoon("✔ Confirmar", "#047857", "#065F46", "#FFFFFF");
        this.btnAjustar = FormatadorSeguroUI.criarBotaoCartoon("✏️ Ajustar", "#FEF3C7", "#F59E0B", "#78350F");
        this.btnLimparBandeja = FormatadorSeguroUI.criarBotaoCartoon("🗑️ Limpar", "#FEE2E2", "#EF4444", "#991B1B");

        btnConfirmarTudo.setOnAction(e -> executarConfirmacaoBandeja());
        btnAjustar.setOnAction(e -> executarAjusteBandeja());
        btnLimparBandeja.setOnAction(e -> executarLimpezaBandeja());

        FlowPane acoesBandeja = new FlowPane(6, 6, btnConfirmarTudo, btnAjustar, btnLimparBandeja);
        this.boxBandejaContainer = new VBox(6, lblTituloBandeja, lblAvisoValidacao, scrollBandeja, acoesBandeja);
        this.boxBandejaContainer.setPadding(new Insets(8));
        this.boxBandejaContainer.setMinHeight(Region.USE_PREF_SIZE);
        this.boxBandejaContainer.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFBEB", "#FCD34D"));
        this.boxBandejaContainer.setVisible(false);
        this.boxBandejaContainer.setManaged(false);

        // 5. Entrada Rápida + Voz
        this.txtEntrada = FormatadorSeguroUI.criarAreaTextoCartoon(
                "Pergunte ou digite comando (ex.: >oba, $$ C= #Produto...)...", "#CBD5E1", "#0F172A");
        this.txtEntrada.setPrefRowCount(2);
        this.txtEntrada.setMaxHeight(60);

        this.btnVoz = FormatadorSeguroUI.criarBotaoCartoon("🎤 Voz", "#F1F5F9", "#CBD5E1", "#334155");
        this.btnEnviar = FormatadorSeguroUI.criarBotaoCartoon("Enviar", "#0F172A", "#0F172A", "#FFFFFF");

        btnVoz.setOnAction(e -> alternarGravacaoVoz());
        btnEnviar.setOnAction(e -> enviarMensagemDigitada());

        HBox botoesEnvio = new HBox(6, btnVoz, btnEnviar);
        botoesEnvio.setAlignment(Pos.CENTER_RIGHT);

        VBox corpoChat = new VBox(8, boxProgresso, scrollChat, boxBandejaContainer, txtEntrada, botoesEnvio);
        corpoChat.setPadding(new Insets(8, 10, 10, 10));
        VBox.setVgrow(corpoChat, Priority.ALWAYS);

        getChildren().addAll(cabecalho, corpoChat);

        ponte.registrarOuvinteBandeja(this::atualizarBandejaVisual);
        adicionarBalaoIA("Assistente conectado. Posso consultar notas, analisar perdas/flutuações ou preparar cadastros na bandeja.");
        atualizarBandejaVisual();
    }

    public void setAoMinimizar(Runnable aoMinimizar) {
        this.aoMinimizar = aoMinimizar;
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        this.ultimoTema = snap;
        if (snap == null) return;
        FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnEnviar, snap.estado().getAccentMain(), snap.estado().getAccentMain(), "#FFFFFF", 12);
    }

    public void enviarPromptContextual(String tituloContexto, String promptParaIA) {
        if (promptParaIA == null || promptParaIA.isBlank() || processando) return;
        String rotulo = (tituloContexto != null && !tituloContexto.isBlank())
                ? "📌 " + tituloContexto
                : promptParaIA;
        adicionarBalaoUsuario(rotulo);
        executarEnvioIA(promptParaIA);
    }

    private void enviarMensagemDigitada() {
        if (processando) return;
        String texto = txtEntrada.getText() != null ? txtEntrada.getText().trim() : "";
        if (texto.isEmpty()) return;
        txtEntrada.clear();
        adicionarBalaoUsuario(texto);
        executarEnvioIA(texto);
    }

    private void executarEnvioIA(String texto) {
        definirEstadoProcessando(true, "Analisando...");
        ponte.enviarMensagemIAAsync(
                texto,
                this::atualizarProgressoEtapa,
                resultado -> {
                    definirEstadoProcessando(false, null);
                    adicionarBalaoIA(resultado.respostaTexto());
                    atualizarBandejaVisual();
                }
        );
    }

    private void alternarGravacaoVoz() {
        if (processando) return;
        if (!gravandoVoz) {
            try {
                if (!ponte.getCapturadorVoz().isMicrofoneDisponivel()) {
                    adicionarBalaoIA("Microfone indisponível neste dispositivo.");
                    return;
                }
                ponte.getCapturadorVoz().iniciarGravacao();
                gravandoVoz = true;
                FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVoz, "#FEE2E2", "#EF4444", "#991B1B", 12);
                btnVoz.setText("⏹️ Parar");
                definirEstadoProgressoSomente("🎤 Gravando áudio...");
            } catch (Exception ex) {
                adicionarBalaoIA("Falha ao iniciar microfone: " + FormatadorSeguroUI.traduzirErroAmigavel(ex.getMessage()));
            }
        } else {
            gravandoVoz = false;
            FormatadorSeguroUI.aplicarEstiloBotaoCartoon(btnVoz, "#F1F5F9", "#CBD5E1", "#334155", 12);
            btnVoz.setText("🎤 Voz");
            adicionarBalaoUsuario("🎤 [Comando de voz]");
            definirEstadoProcessando(true, "Transcrevendo áudio...");
            ponte.pararVozEEnviarAsync(
                    this::atualizarProgressoEtapa,
                    resultado -> {
                        definirEstadoProcessando(false, null);
                        adicionarBalaoIA(resultado.respostaTexto());
                        atualizarBandejaVisual();
                    }
            );
        }
    }

    private void executarConfirmacaoBandeja() {
        if (processando) return;
        adicionarBalaoUsuario("✔ Confirmar lote");
        definirEstadoProcessando(true, "Executando operações...");
        ponte.confirmarBandejaAsync(res -> {
            definirEstadoProcessando(false, null);
            adicionarBalaoIA(res.respostaTexto());
            atualizarBandejaVisual();
        });
    }

    private void executarAjusteBandeja() {
        if (processando) return;
        var val = ponte.validarBandejaAtiva();
        if (!val.todoValido()) {
            adicionarBalaoUsuario("✏️ Corrigir conflitos");
            definirEstadoProcessando(true, "Corrigindo lote...");
            ponte.corrigirConflitosBandejaAsync(res -> {
                definirEstadoProcessando(false, null);
                adicionarBalaoIA(res.respostaTexto());
                atualizarBandejaVisual();
            });
        } else {
            txtEntrada.requestFocus();
            txtEntrada.setText("Alterar na bandeja: ");
            txtEntrada.positionCaret(txtEntrada.getText().length());
        }
    }

    private void executarLimpezaBandeja() {
        if (processando) return;
        ponte.limparBandejaAsync(res -> {
            adicionarBalaoIA(res.respostaTexto());
            atualizarBandejaVisual();
        });
    }

    private void definirEstadoProgressoSomente(String texto) {
        lblEtapaProgresso.setText(texto);
        barraProgresso.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        boxProgresso.setVisible(true);
        boxProgresso.setManaged(true);
    }

    private void definirEstadoProcessando(boolean ativo, String textoInicial) {
        this.processando = ativo;
        btnEnviar.setDisable(ativo);
        btnConfirmarTudo.setDisable(ativo);
        btnAjustar.setDisable(ativo);
        boxProgresso.setVisible(ativo);
        boxProgresso.setManaged(ativo);
        if (ativo && textoInicial != null) {
            lblEtapaProgresso.setText(textoInicial);
            barraProgresso.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
    }

    private void atualizarProgressoEtapa(String etapa) {
        if (etapa == null || etapa.isBlank()) return;
        lblEtapaProgresso.setText(etapa);
        var m = java.util.regex.Pattern.compile("Etapa\\s+(\\d+)/(\\d+)").matcher(etapa);
        if (m.find()) {
            double atual = Double.parseDouble(m.group(1));
            double total = Math.max(1.0, Double.parseDouble(m.group(2)));
            barraProgresso.setProgress(atual / total);
        }
    }

    public void atualizarBandejaVisual() {
        BandejaOperacoesContexto bandeja = ponte.obterBandejaAtiva();
        List<ItemBandeja> itens = bandeja.getItens();
        if (itens.isEmpty()) {
            boxBandejaContainer.setVisible(false);
            boxBandejaContainer.setManaged(false);
            return;
        }

        ValidadorLoteBandeja.RelatorioValidacaoLote relatorio = ponte.validarBandejaAtiva();
        lblTituloBandeja.setText("Bandeja (" + itens.size() + " operação(ões))");

        if (!relatorio.todoValido()) {
            lblAvisoValidacao.setText("⚠️ " + relatorio.totalConflitos() + " conflito(s) pendente(s) — clique em Ajustar.");
        } else {
            lblAvisoValidacao.setText("✔ Validado. Clique em Confirmar para salvar.");
        }
        lblAvisoValidacao.setVisible(true);
        lblAvisoValidacao.setManaged(true);

        listaCardsBandeja.getChildren().clear();
        for (ItemBandeja item : itens) {
            String conflito = relatorio.primeiroConflito(item.getIdItem());
            boolean temAlerta = item.isPendente() || conflito != null || item.getStatus() == ItemBandeja.StatusItem.FALHA_EXECUCAO;

            Label lblOp = FormatadorSeguroUI.criarLabel(
                    item.getIdItem() + " • " + FormatadorSeguroUI.traduzirRotaAmigavel(item.getRota()),
                    12, true, "#0F172A");
            Label lblDesc = FormatadorSeguroUI.criarLabel(
                    FormatadorSeguroUI.tratarParaExibicao(item.getDescricaoAmigavel(), "Operação"),
                    11, false, "#334155");

            Button btnRem = FormatadorSeguroUI.criarBotaoCartoon("✕", "#FEE2E2", "#FCA5A5", "#991B1B");
            btnRem.setOnAction(e -> ponte.removerItemBandeja(item.getIdItem()));

            HBox topo = new HBox(6, lblOp, btnRem);
            topo.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lblOp, Priority.ALWAYS);

            VBox cardOp = new VBox(3, topo, lblDesc);
            cardOp.setPadding(new Insets(6, 8, 6, 8));
            cardOp.setMinHeight(Region.USE_PREF_SIZE);
            cardOp.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno(
                    temAlerta ? "#FEF2F2" : "#FFFFFF",
                    temAlerta ? "#FCA5A5" : "#CBD5E1"));

            if (conflito != null) {
                cardOp.getChildren().add(FormatadorSeguroUI.criarLabel("⚠️ " + conflito, 11, true, "#991B1B"));
            } else if (!item.getParametros().isEmpty()) {
                cardOp.getChildren().add(FormatadorSeguroUI.criarLabel(resumirParametros(item.getParametros()), 11, false, "#64748B"));
            }
            listaCardsBandeja.getChildren().add(cardOp);
        }

        boxBandejaContainer.setVisible(true);
        boxBandejaContainer.setManaged(true);
    }

    private String resumirParametros(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (var e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().toString().isBlank()) continue;
            if (c > 0) sb.append(" • ");
            sb.append(e.getKey()).append(": ").append(FormatadorSeguroUI.tratarParaExibicao(e.getValue(), ""));
            c++;
            if (c >= 3) break;
        }
        return sb.toString();
    }

    private void adicionarBalaoUsuario(String texto) {
        Label lbl = FormatadorSeguroUI.criarLabel(texto, 12, false, "#FFFFFF");
        VBox balao = new VBox(lbl);
        balao.setPadding(new Insets(7, 10, 7, 10));
        balao.setMaxWidth(290);
        balao.setMinHeight(Region.USE_PREF_SIZE);
        String cor = ultimoTema != null ? ultimoTema.estado().getAccentMain() : "#1D4ED8";
        balao.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 8px 8px 2px 8px;", cor));
        HBox wrapper = new HBox(balao);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        listaMensagens.getChildren().add(wrapper);
        rolarParaFinalChat();
    }

    private void adicionarBalaoIA(String texto) {
        Label lbl = FormatadorSeguroUI.criarLabel(FormatadorSeguroUI.tratarParaExibicao(texto, ""), 12, false, "#0F172A");
        VBox balao = new VBox(lbl);
        balao.setPadding(new Insets(7, 10, 7, 10));
        balao.setMaxWidth(315);
        balao.setMinHeight(Region.USE_PREF_SIZE);
        balao.setStyle("-fx-background-color: #F1F5F9; -fx-border-color: #E2E8F0; -fx-border-width: 1px; -fx-background-radius: 8px 8px 8px 2px; -fx-border-radius: 8px 8px 8px 2px;");
        HBox wrapper = new HBox(balao);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        listaMensagens.getChildren().add(wrapper);
        rolarParaFinalChat();
    }

    private void rolarParaFinalChat() {
        Platform.runLater(() -> scrollChat.setVvalue(1.0));
    }
}
