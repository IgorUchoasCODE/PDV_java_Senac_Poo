package br.com.senac.ui.documentada;

import br.com.senac.view.javafx.gateway.RoteadorSistemaGateway;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class AplicacaoUI extends Application {
    @Override public void start(Stage stage) {
        ShellDocumentado shell=new ShellDocumentado(new GatewayDaSessao(new RoteadorSistemaGateway()));
        Scene cena=new Scene(shell,1440,980);
        cena.getStylesheets().add(getClass().getResource("/css/sazonalisei-theme.css").toExternalForm());
        cena.getStylesheets().add(getClass().getResource("/ui-documentada.css").toExternalForm());
        stage.setTitle("Sazonalisei · UI documental · SistemaGateway");stage.setMinWidth(1160);stage.setMinHeight(800);stage.setScene(cena);
        stage.setOnCloseRequest(e->{if(!ComponentesUI.confirmar(shell,"Encerrar interface","Encerrar esta sessão da interface?"))e.consume();else System.exit(0);});stage.show();
    }
}
