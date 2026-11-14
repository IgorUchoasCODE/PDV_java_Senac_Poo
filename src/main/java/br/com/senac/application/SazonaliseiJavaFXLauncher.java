package br.com.senac.application;

import javafx.application.Application;

/**
 * Classe lançadora (sem estender {@link Application}) para compatibilidade direta
 * com classpath padrão no JDK 21 sem exigir --module-path manual.
 */
public class SazonaliseiJavaFXLauncher {

    public static void main(String[] args) {
        Application.launch(SazonaliseiApp.class, args);
    }
}
