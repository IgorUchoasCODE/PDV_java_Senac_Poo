package br.com.senac.view.javafx.componentes;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Container superior de navegação e status da aplicação JavaFX.
 */
public class CabecalhoNavegacaoContainer extends HBox {

    private final Label lblStatus;
    private final Button btnToggleChat;

    public CabecalhoNavegacaoContainer(Runnable aoAtualizarDados, Runnable aoAlternarChat) {
        getStyleClass().add("header-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(14);

        Label lblTitulo = new Label("Sazonalisei • Inteligência Comercial");
        lblTitulo.getStyleClass().add("header-title");

        Label lblSub = new Label("Estatísticas Sazonais, Grafo Obsidian, Chat de Pesquisa & Ambiente CSV");
        lblSub.getStyleClass().add("header-subtitle");

        VBox boxTitulos = new VBox(2, lblTitulo, lblSub);

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        lblStatus = new Label("Pronto • 85 Rotas Ativas");
        lblStatus.getStyleClass().add("status-pill");

        Button btnAtualizar = new Button("Atualizar Painéis");
        btnAtualizar.getStyleClass().add("btn-secondary");
        btnAtualizar.setOnAction(e -> {
            if (aoAtualizarDados != null) {
                aoAtualizarDados.run();
            }
        });

        btnToggleChat = new Button("Ocultar Chat");
        btnToggleChat.getStyleClass().add("btn-secondary");
        btnToggleChat.setOnAction(e -> {
            if (aoAlternarChat != null) {
                aoAlternarChat.run();
            }
        });

        getChildren().addAll(boxTitulos, espacador, lblStatus, btnAtualizar, btnToggleChat);
    }

    public void definirStatus(String status) {
        lblStatus.setText(status);
    }

    public void atualizarTextoToggleChat(boolean chatVisivel) {
        btnToggleChat.setText(chatVisivel ? "Ocultar Chat" : "Abrir Chat IA");
    }
}
