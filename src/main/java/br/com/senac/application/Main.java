package br.com.senac.application;

import br.com.senac.view.javafx.SazonaliseiGuiLauncher;

/**
 * Ponto de entrada principal (Main) do sistema Sazonalisei.
 * Inicia a interface gráfica minimalista cartoon amazônica por padrão, ou o terminal interativo
 * quando chamado com {@code --cli} / {@code --terminal}.
 */
public class Main {

    public static void main(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("--cli".equalsIgnoreCase(arg) || "--terminal".equalsIgnoreCase(arg)) {
                    ChatBotTerminal.main(args);
                    return;
                }
            }
        }
        SazonaliseiGuiLauncher.main(args);
    }
}
