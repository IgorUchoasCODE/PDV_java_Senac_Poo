package br.com.senac.view.javafx.componentes;

import br.com.senac.view.javafx.componentes.FormatadorBalaoChatUI.MensagemTratadaUI;
import br.com.senac.view.javafx.componentes.PersonagemIaAvatarContainer.Estado;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Painel interativo de Chat IA utilizando os mesmos mecanismos do {@code ChatBotTerminal}:
 * listener de progresso em tempo real, comandos {@code :bandeja}, {@code :limpar}, {@code :ajuda},
 * {@code :persona}, {@code :rotas}, entrada de áudio/voz, imagem e personagem animado da IA ativa.
 */
public class PainelChatPesquisaContainer extends VBox {

    @FunctionalInterface
    public interface ListenerEstadoPersonagem {
        void aoAtualizar(String provedor, Estado estado, int trocas, String mensagemStatus);
    }

    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SistemaGateway gateway;
    private final String idSessao = "javafx-ui-" + UUID.randomUUID().toString().substring(0, 8);
    private final CapturadorVozMicrofone capturadorVoz = new CapturadorVozMicrofone();

    private final PersonagemIaAvatarContainer avatarHeader = new PersonagemIaAvatarContainer(58);
    private final Label lblProvedorAtivo = new Label("IA Ativa: Gemini");
    private final Label lblProgresso = new Label("Pronto • Digite :ajuda para comandos do terminal");
    private final VBox listaMensagens = new VBox(10);
    private final ScrollPane scrollMensagens;
    private final TextField txtEntrada = new TextField();
    private final Button btnEnviar = new Button("Enviar");
    private final Button btnVoz = new Button("🎤 Voz");
    private final Button btnImagem = new Button("📷");
    private final Label lblStatusVoz = new Label();

    private int contadorTrocas = 0;
    private ListenerEstadoPersonagem listenerExterno;

    public PainelChatPesquisaContainer(SistemaGateway gateway) {
        this.gateway = gateway;
        getStyleClass().add("chat-panel");
        setSpacing(10);
        setPadding(new Insets(12));
        setPrefWidth(440);
        setMinWidth(360);

        // Cabeçalho com personagem animado da IA
        lblProvedorAtivo.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        lblProgresso.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        lblProgresso.setWrapText(true);
        VBox infoHeader = new VBox(3, lblProvedorAtivo, lblProgresso);
        infoHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoHeader, Priority.ALWAYS);

        HBox cabecalhoPersonagem = new HBox(10, avatarHeader, infoHeader);
        cabecalhoPersonagem.setAlignment(Pos.CENTER_LEFT);
        cabecalhoPersonagem.getStyleClass().add("card-container");

        FlowPane chips = new FlowPane(6, 6);
        chips.getChildren().addAll(
                criarChip("Produtos", "produtos"),
                criarChip("Quando comprar?", "Analise os produtos e me diga quando comprar e quando vender"),
                criarChip(":bandeja", ":bandeja"),
                criarChip(":limpar", ":limpar"),
                criarChip(":ajuda", ":ajuda")
        );

        listaMensagens.setPadding(new Insets(6));
        scrollMensagens = new ScrollPane(listaMensagens);
        scrollMensagens.setFitToWidth(true);
        scrollMensagens.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollMensagens, Priority.ALWAYS);

        lblStatusVoz.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #F87171;");
        lblStatusVoz.setManaged(false);
        lblStatusVoz.setVisible(false);

        txtEntrada.setPromptText("Pergunte à IA ou use :bandeja, :limpar, :persona, $$...");
        txtEntrada.setOnAction(e -> enviarMensagem());
        HBox.setHgrow(txtEntrada, Priority.ALWAYS);

        btnEnviar.getStyleClass().add("btn-primary");
        btnEnviar.setOnAction(e -> enviarMensagem());

        btnVoz.getStyleClass().add("btn-voice");
        btnVoz.setOnAction(e -> alternarGravacaoVoz());

        btnImagem.getStyleClass().add("btn-secondary");
        btnImagem.setOnAction(e -> anexarImagem());

        HBox barraEntrada = new HBox(6, txtEntrada, btnImagem, btnVoz, btnEnviar);
        barraEntrada.setAlignment(Pos.CENTER);

        getChildren().addAll(cabecalhoPersonagem, chips, scrollMensagens, lblStatusVoz, barraEntrada);

        atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.OCIOSO, "Pronto • Digite :ajuda para comandos");
        adicionarBolha("Assistente", "Olá! Uso o mesmo motor do ChatBotTerminal (progresso em tempo real, :bandeja, :limpar, :persona, voz e imagem).", false);
    }

    public void definirListenerEstadoPersonagem(ListenerEstadoPersonagem listener) {
        this.listenerExterno = listener;
        atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.OCIOSO, lblProgresso.getText());
    }

    private Button criarChip(String rotulo, String comando) {
        Button chip = new Button(rotulo);
        chip.getStyleClass().add("btn-secondary");
        chip.setStyle("-fx-font-size: 11px; -fx-padding: 4 9 4 9;");
        chip.setOnAction(e -> {
            txtEntrada.setText(comando);
            enviarMensagem();
        });
        return chip;
    }

    public void pesquisarExternamente(String consulta) {
        if (consulta == null || consulta.isBlank()) return;
        txtEntrada.setText(consulta);
        enviarMensagem();
    }

    private void enviarMensagem() {
        String texto = txtEntrada.getText();
        if (texto == null || texto.isBlank()) return;
        txtEntrada.clear();

        contadorTrocas++;
        adicionarBolha("Você", texto.trim(), true);

        if (texto.trim().startsWith(":")) {
            String respCmd = gateway.executarComandoTerminalChat(idSessao, texto.trim());
            if (respCmd != null) {
                adicionarBolha("Terminal IA", respCmd, false);
                atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.OCIOSO, "Comando executado • Troca #" + contadorTrocas);
                return;
            }
        }

        definirControlesBloqueados(true);
        atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.PENSANDO, "Processando...");

        gateway.executarEmBackground(() -> {
            String resposta = gateway.perguntarAoChatComProgresso(idSessao, texto.trim(), etapa ->
                    Platform.runLater(() -> atualizarEstadoPersonagem(
                            gateway.obterProvedorIaAtivo(), Estado.PENSANDO, "⏳ " + etapa
                    ))
            );
            String prov = gateway.obterProvedorIaAtivo();
            Platform.runLater(() -> {
                definirControlesBloqueados(false);
                adicionarBolha("Assistente (" + prov.toUpperCase() + ")", resposta, false);
                atualizarEstadoPersonagem(prov, Estado.RESPONDENDO, "Respondeu • Troca #" + contadorTrocas);
            });
        }, erro -> Platform.runLater(() -> {
            definirControlesBloqueados(false);
            adicionarBolha("Sistema", "Erro ao consultar IA: " + erro.getMessage(), false);
            atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.OCIOSO, "Erro na consulta");
        }));
    }

    private void anexarImagem() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Selecionar Imagem para Análise da IA");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File arq = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (arq == null) return;

        String prompt = txtEntrada.getText() == null || txtEntrada.getText().isBlank()
                ? "Analise esta imagem no contexto do PDV/estoque."
                : txtEntrada.getText().trim();
        txtEntrada.clear();
        contadorTrocas++;
        adicionarBolha("Você (Imagem)", "[" + arq.getName() + "] " + prompt, true);
        definirControlesBloqueados(true);
        atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.PENSANDO, "Analisando imagem...");

        gateway.executarEmBackground(() -> {
            try {
                byte[] bytes = Files.readAllBytes(arq.toPath());
                String mime = arq.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                String resp = gateway.perguntarComImagem(idSessao, bytes, mime, prompt);
                String prov = gateway.obterProvedorIaAtivo();
                Platform.runLater(() -> {
                    definirControlesBloqueados(false);
                    adicionarBolha("Assistente (" + prov.toUpperCase() + ")", resp, false);
                    atualizarEstadoPersonagem(prov, Estado.RESPONDENDO, "Imagem analisada • Troca #" + contadorTrocas);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    definirControlesBloqueados(false);
                    adicionarBolha("Sistema", "Falha ao ler imagem: " + ex.getMessage(), false);
                });
            }
        }, err -> Platform.runLater(() -> definirControlesBloqueados(false)));
    }

    private void alternarGravacaoVoz() {
        if (!capturadorVoz.isGravando()) {
            try {
                capturadorVoz.iniciarGravacao();
                btnVoz.setText("⏹ Parar");
                btnVoz.getStyleClass().setAll("btn-voice-recording");
                lblStatusVoz.setText("Gravando áudio do microfone... clique em Parar para enviar.");
                lblStatusVoz.setManaged(true);
                lblStatusVoz.setVisible(true);
                atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.OUVINDO, "Ouvindo microfone...");
            } catch (Exception ex) {
                adicionarBolha("Sistema", "Microfone indisponível: " + ex.getMessage(), false);
            }
        } else {
            btnVoz.setText("🎤 Voz");
            btnVoz.getStyleClass().setAll("btn-voice");
            lblStatusVoz.setManaged(false);
            lblStatusVoz.setVisible(false);

            byte[] audio;
            try {
                audio = capturadorVoz.pararEObterWav();
            } catch (Exception ex) {
                adicionarBolha("Sistema", "Erro ao capturar áudio WAV: " + ex.getMessage(), false);
                return;
            }
            if (audio.length == 0) return;

            contadorTrocas++;
            adicionarBolha("Você (Áudio)", "🎤 Comando de voz enviado (" + (audio.length / 1024) + " KB)", true);
            definirControlesBloqueados(true);
            atualizarEstadoPersonagem(gateway.obterProvedorIaAtivo(), Estado.PENSANDO, "Processando áudio...");

            gateway.executarEmBackground(() -> {
                String resposta = gateway.perguntarPorVoz(idSessao, audio, "audio/wav");
                String prov = gateway.obterProvedorIaAtivo();
                Platform.runLater(() -> {
                    definirControlesBloqueados(false);
                    adicionarBolha("Assistente (" + prov.toUpperCase() + ")", resposta, false);
                    atualizarEstadoPersonagem(prov, Estado.RESPONDENDO, "Respondeu áudio • Troca #" + contadorTrocas);
                });
            }, erro -> Platform.runLater(() -> {
                definirControlesBloqueados(false);
                adicionarBolha("Sistema", "Erro ao processar áudio: " + erro.getMessage(), false);
            }));
        }
    }

    private void atualizarEstadoPersonagem(String provedor, Estado estado, String statusTexto) {
        String prov = provedor == null || provedor.isBlank() ? "gemini" : provedor;
        avatarHeader.atualizarContexto(prov, estado, contadorTrocas);
        lblProvedorAtivo.setText("IA Ativa: " + prov.toUpperCase() + " (Troca #" + contadorTrocas + ")");
        lblProgresso.setText(statusTexto);
        if (listenerExterno != null) {
            listenerExterno.aoAtualizar(prov, estado, contadorTrocas, statusTexto);
        }
    }

    private void definirControlesBloqueados(boolean bloqueado) {
        btnEnviar.setDisable(bloqueado);
        btnVoz.setDisable(bloqueado);
        btnImagem.setDisable(bloqueado);
    }

    private void adicionarBolha(String autor, String textoBruto, boolean usuario) {
        MensagemTratadaUI tratada = FormatadorBalaoChatUI.tratarParaBalao(textoBruto, usuario);

        VBox bolha = new VBox(6);
        bolha.getStyleClass().add(usuario ? "bubble-user" : "bubble-ai");
        bolha.maxWidthProperty().bind(scrollMensagens.widthProperty().subtract(36));

        Label lblAutor = new Label(autor + " • " + LocalTime.now().format(HORA_FMT));
        lblAutor.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + (usuario ? "#E0F2FE" : "#38BDF8") + ";");

        VBox conteudoRico = FormatadorBalaoChatUI.renderizarConteudoRico(tratada.textoLimpo(), usuario);
        bolha.getChildren().addAll(lblAutor, conteudoRico);

        if (tratada.requerConfirmacaoBandeja()) {
            HBox acoes = new HBox(8);
            Button btnConfirmar = new Button("Confirmar");
            btnConfirmar.getStyleClass().add("btn-primary");
            btnConfirmar.setOnAction(e -> {
                txtEntrada.setText("confirmar");
                enviarMensagem();
            });

            Button btnCancelar = new Button("Cancelar");
            btnCancelar.getStyleClass().add("btn-secondary");
            btnCancelar.setOnAction(e -> {
                txtEntrada.setText("cancelar");
                enviarMensagem();
            });
            acoes.getChildren().addAll(btnConfirmar, btnCancelar);
            bolha.getChildren().add(acoes);
        }

        HBox linha = new HBox(bolha);
        linha.setAlignment(usuario ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        listaMensagens.getChildren().add(linha);

        Platform.runLater(() -> scrollMensagens.setVvalue(1.0));
    }
}
