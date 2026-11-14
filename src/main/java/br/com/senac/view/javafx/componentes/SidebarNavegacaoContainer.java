package br.com.senac.view.javafx.componentes;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sidebar de navegação principal (menu lateral esquerdo), substituindo abas horizontais lotadas
 * por uma navegação limpa, séria e categorizada.
 */
public class SidebarNavegacaoContainer extends ScrollPane {

    private final VBox containerBotoes = new VBox(4);
    private final Map<String, Button> botoesPorId = new LinkedHashMap<>();
    private final Consumer<String> aoSelecionar;

    public SidebarNavegacaoContainer(Consumer<String> aoSelecionar) {
        this.aoSelecionar = aoSelecionar;
        setFitToWidth(true);
        setPrefWidth(232);
        setMinWidth(220);
        setMaxWidth(240);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("sidebar-container");

        containerBotoes.setPadding(new Insets(4, 2, 12, 2));
        VBox.setVgrow(containerBotoes, Priority.ALWAYS);
        setContent(containerBotoes);
    }

    public void adicionarSecao(String tituloSecao) {
        Label lbl = new Label(tituloSecao);
        lbl.getStyleClass().add("sidebar-section-label");
        containerBotoes.getChildren().add(lbl);
    }

    public void adicionarItem(String id, String rotulo) {
        Button btn = new Button(rotulo);
        btn.getStyleClass().add("sidebar-btn");
        btn.setOnAction(e -> {
            selecionar(id);
            if (aoSelecionar != null) {
                aoSelecionar.accept(id);
            }
        });
        botoesPorId.put(id, btn);
        containerBotoes.getChildren().add(btn);
    }

    public void selecionar(String idAtivo) {
        for (Map.Entry<String, Button> entry : botoesPorId.entrySet()) {
            Button b = entry.getValue();
            b.getStyleClass().remove("sidebar-btn-active");
            if (entry.getKey().equals(idAtivo)) {
                b.getStyleClass().add("sidebar-btn-active");
            }
        }
    }
}
