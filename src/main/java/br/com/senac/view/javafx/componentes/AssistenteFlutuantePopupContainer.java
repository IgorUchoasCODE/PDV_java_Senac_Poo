package br.com.senac.view.javafx.componentes;

import br.com.senac.view.javafx.componentes.PersonagemIaAvatarContainer.Estado;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Personagem flutuante ("popup" solto e arrastável) que representa a IA ativa.
 * O usuário pode arrastá-lo livremente pela tela e, ao clicar/consultar, abre a janela interativa do chat.
 */
public class AssistenteFlutuantePopupContainer extends HBox {

    private final PersonagemIaAvatarContainer avatar = new PersonagemIaAvatarContainer(56);
    private final Label lblTitulo = new Label("IA Gemini");
    private final Label lblStatus = new Label("Clique para consultar");
    private final PainelChatPesquisaContainer chatContainer;

    private Stage janelaInterativa;
    private double dragStartX;
    private double dragStartY;
    private double startTranslateX;
    private double startTranslateY;
    private boolean arrastou = false;

    public AssistenteFlutuantePopupContainer(PainelChatPesquisaContainer chatContainer, String cssUrl) {
        this.chatContainer = chatContainer;

        setSpacing(10);
        setPadding(new Insets(8, 14, 8, 10));
        setAlignment(Pos.CENTER_LEFT);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setCursor(Cursor.HAND);
        setStyle("-fx-background-color: rgba(17, 24, 39, 0.95);"
                + "-fx-border-color: #0284C7;"
                + "-fx-border-width: 1.5;"
                + "-fx-border-radius: 36;"
                + "-fx-background-radius: 36;"
                + "-fx-effect: dropshadow(gaussian, rgba(2, 132, 199, 0.35), 14, 0.2, 0, 4);");

        lblTitulo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        VBox textos = new VBox(2, lblTitulo, lblStatus);
        textos.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(avatar, textos);

        // Sincroniza personagem flutuante com a IA ativa, trocas e progresso do ChatBotTerminal
        chatContainer.definirListenerEstadoPersonagem((prov, estado, trocas, msg) -> {
            avatar.atualizarContexto(prov, estado, trocas);
            lblTitulo.setText("IA " + prov.toUpperCase() + " • #" + trocas);
            lblStatus.setText(truncar(msg, 28));
        });

        configurarArrasteEClique(cssUrl);
    }

    private void configurarArrasteEClique(String cssUrl) {
        setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            startTranslateX = getTranslateX();
            startTranslateY = getTranslateY();
            arrastou = false;
        });

        setOnMouseDragged(e -> {
            double dx = e.getSceneX() - dragStartX;
            double dy = e.getSceneY() - dragStartY;
            if (Math.hypot(dx, dy) > 4.0) {
                arrastou = true;
            }
            setTranslateX(startTranslateX + dx);
            setTranslateY(startTranslateY + dy);
        });

        setOnMouseReleased(e -> {
            if (!arrastou) {
                abrirOuFocarJanelaInterativa(cssUrl);
            }
        });
    }

    public void abrirOuFocarJanelaInterativa(String cssUrl) {
        if (janelaInterativa == null) {
            janelaInterativa = new Stage();
            janelaInterativa.setTitle("Assistente IA Interativo • Sazonalisei");
            if (getScene() != null && getScene().getWindow() != null) {
                janelaInterativa.initOwner(getScene().getWindow());
            }
            Rectangle2D tela = Screen.getPrimary().getVisualBounds();
            double largura = Math.max(460, Math.min(640, tela.getWidth() * 0.44));
            double altura = Math.max(540, Math.min(780, tela.getHeight() * 0.84));
            Scene cena = new Scene(chatContainer, largura, altura);
            if (cssUrl != null) {
                cena.getStylesheets().add(cssUrl);
            }
            janelaInterativa.setMinWidth(400);
            janelaInterativa.setMinHeight(480);
            janelaInterativa.setScene(cena);
            janelaInterativa.setOnHidden(ev -> avatar.atualizarContexto(avatar.getProvedorAtual(), Estado.OCIOSO, 0));
        }
        if (!janelaInterativa.isShowing()) {
            janelaInterativa.show();
        }
        janelaInterativa.toFront();
        janelaInterativa.requestFocus();
    }

    public void consultarEmJanela(String consulta, String cssUrl) {
        abrirOuFocarJanelaInterativa(cssUrl);
        chatContainer.pesquisarExternamente(consulta);
    }

    private String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
