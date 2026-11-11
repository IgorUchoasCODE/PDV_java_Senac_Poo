package br.com.senac.application;

import br.com.senac.integracao.gemini.GeminiAgenteOperacional;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Novo Prompt Humanizado e Silencioso (Zero Poluição Visual e Zero Erros Técnicos).
 * Suporta:
 * - Linguagem natural livre (com failover automático entre múltiplas IAs)
 * - Busca direta por siglas fora de $$ (ex: "procura produto o.b.a" ou ">oba", "pessoa >isu")
 * - Comandos encurtados $$ com consultas paralelas em Virtual Threads e Bandeja de Espera para C=, U=, D=.
 */
public class ChatBotPromptLimpo {

    private static final String RESET = "\u001B[0m";
    private static final String VERDE = "\u001B[32m";
    private static final String CIANO = "\u001B[36m";
    private static final String NEGRITO = "\u001B[1m";

    public static void main(String[] args) {
        ChatBotTerminal.silenciarLogsTecnicos();
        // No modo limpo, suprime integralmente qualquer saída em System.err para zero erros técnicos na tela
        System.setErr(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        String sessao = "prompt-limpo-" + System.currentTimeMillis();

        // Aquece a conexão JPA em Virtual Thread em background sem exibir nada
        Thread.ofVirtual().start(() -> {
            try {
                new br.com.senac.controller.RoteadorController().produtos().listarUnidadesMedida();
            } catch (Exception ignored) {
            }
        });

        System.out.println(NEGRITO + CIANO + "✨ Assistente Sazonalisei pronto! (Digite sua pergunta, sigla como '>oba' ou comando '$$', ou ':sair')" + RESET);

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\n" + NEGRITO + VERDE + "Você > " + RESET);
                if (!scanner.hasNextLine()) break;

                String entrada = scanner.nextLine().trim();
                if (entrada.isEmpty()) continue;

                if (entrada.equalsIgnoreCase(":sair") || entrada.equalsIgnoreCase(":exit") || entrada.equalsIgnoreCase(":q")) {
                    System.out.println(CIANO + "Até logo!" + RESET);
                    break;
                }
                if (entrada.equalsIgnoreCase(":bandeja")) {
                    System.out.println("\n" + agente.obterBandeja(sessao).formatarVisualizacao());
                    continue;
                }
                if (entrada.equalsIgnoreCase(":fechar") || entrada.equalsIgnoreCase(":limpar")) {
                    agente.fecharJanelaContexto(sessao);
                    System.out.println("✔ Lista de espera e contexto limpos.");
                    continue;
                }

                try {
                    String resposta = agente.processarMensagem(sessao, entrada);
                    String limpa = ChatBotTerminal.limparPoluicaoVisual(resposta);
                    if (!limpa.isEmpty()) {
                        System.out.println("\n" + limpa);
                    }
                } catch (Throwable ignored) {
                    System.out.println("\nNão foi possível concluir essa ação agora. Verifique os dados e tente novamente.");
                }
            }
        }
    }
}
