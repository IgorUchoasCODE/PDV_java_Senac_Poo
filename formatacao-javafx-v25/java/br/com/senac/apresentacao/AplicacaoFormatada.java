package br.com.senac.apresentacao;

import br.com.senac.view.javafx.SazonaliseiGuiApp;
import javafx.stage.Stage;

/** Mantém os nove módulos e seus fluxos originais da versão 2.5. */
public final class AplicacaoFormatada extends SazonaliseiGuiApp {
    private final ApresentacaoJavaFX apresentacao=new ApresentacaoJavaFX();
    @Override public void start(Stage stage){super.start(stage);apresentacao.iniciar();}
    @Override public void stop(){apresentacao.parar();}
}
