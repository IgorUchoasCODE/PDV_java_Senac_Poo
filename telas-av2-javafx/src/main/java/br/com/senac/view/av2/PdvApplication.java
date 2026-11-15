package br.com.senac.view.av2;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Ponto de entrada JavaFX. A aplicação existente da equipe permanece intacta. */
public final class PdvApplication extends Application {
    @Override
    public void start(Stage stage) {
        PdvView view = new PdvView(new DemoGateway());
        Scene scene = new Scene(view, 1380, 880);
        scene.getStylesheets().add(PdvApplication.class.getResource("/pdv-av2.css").toExternalForm());
        stage.setTitle("Senac PDV | JavaFX | Telas AV2");
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(760);
        stage.setOnCloseRequest(event -> {
            if (!view.perguntar("Encerrar demonstração?", "Os cadastros e as vendas desta sessão serão apagados.")) {
                event.consume();
            }
        });
        stage.show();
        if (getParameters().getRaw().contains("--verificar-ui")) {
            javafx.application.Platform.runLater(() -> VerificacaoInterface.executar(view, stage));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
