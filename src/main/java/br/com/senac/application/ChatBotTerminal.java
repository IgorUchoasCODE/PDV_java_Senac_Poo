package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.ia.ConfigIA;
import br.com.senac.integracao.ia.FormatadorSaidaIA;
import br.com.senac.integracao.ia.ManualInstrucoesIA;

import java.io.File;
import java.nio.file.Files;
import java.util.Scanner;

/**
 * Interface Interativa do ChatBot Operacional via Terminal CLI.
 * Permite interagir em tempo real com a IA (texto, áudio local, imagem),
 * visualizando o entendimento semântico, rota mapeada, regras de negócio aplicadas
 * e confirmação em duas etapas (Human-in-the-Loop) diretamente no console.
 */
public class ChatBotTerminal {

    private static final String RESET = "\u001B[0m";
    private static final String CIANO = "\u001B[36m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String AZUL = "\u001B[34m";
    private static final String ROXO = "\u001B[35m";
    private static final String NEGRITO = "\u001B[1m";

    static {
        silenciarLogsTecnicos();
    }

    public static void main(String[] args) {
        silenciarLogsTecnicos();

        // Inicializa o agente operacional e componentes
        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        String sessaoTerminal = "terminal-user-" + System.currentTimeMillis();

        exibirBanner(agente);

        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        while (true) {
            System.out.print("\n" + NEGRITO + VERDE + "Você > " + RESET);
            if (!scanner.hasNextLine()) break;

            String entrada = scanner.nextLine().trim();
            if (entrada.isEmpty()) continue;

            // Comandos especiais do terminal
            if (entrada.equalsIgnoreCase(":sair") || entrada.equalsIgnoreCase(":exit") || entrada.equalsIgnoreCase(":q")) {
                exibirLogsAoSair(agente, sessaoTerminal);
                System.out.println(CIANO + "\nEncerrando ChatBot Terminal do Sazonalisei. Até logo!\n" + RESET);
                break;
            }

            if (entrada.equalsIgnoreCase(":ajuda") || entrada.equalsIgnoreCase(":help") || entrada.equalsIgnoreCase(":h")) {
                exibirAjuda();
                continue;
            }

            if (entrada.equalsIgnoreCase(":limpar") || entrada.equalsIgnoreCase(":cls") || entrada.equalsIgnoreCase(":clear")) {
                agente.fecharJanelaContexto(sessaoTerminal);
                exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
                limparTela();
                exibirBanner(agente);
                System.out.println(VERDE + "✔ Janela de contexto e bandeja foram reiniciadas limpas." + RESET);
                continue;
            }

            if (entrada.equalsIgnoreCase(":bandeja") || entrada.equalsIgnoreCase(":tray")) {
                System.out.println("\n" + CIANO + agente.obterBandeja(sessaoTerminal).formatarVisualizacao() + RESET);
                continue;
            }

            if (entrada.equalsIgnoreCase(":fechar")) {
                agente.fecharJanelaContexto(sessaoTerminal);
                System.out.println(VERDE + "✔ Janela de contexto encerrada com sucesso. Sessão limpa e pronta!" + RESET);
                exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
                continue;
            }

            if (entrada.equalsIgnoreCase(":logs") || entrada.toLowerCase().startsWith(":logs ")) {
                tratarComandoLogs(agente, sessaoTerminal, entrada);
                continue;
            }

            if (entrada.equalsIgnoreCase(":rotas")) {
                exibirCatalogoRotas();
                continue;
            }

            if (entrada.equalsIgnoreCase(":manual")) {
                System.out.println("\n" + CIANO + ManualInstrucoesIA.gerar() + RESET);
                continue;
            }

            if (entrada.toLowerCase().startsWith(":persona")) {
                String novaPersona = entrada.substring(entrada.indexOf(' ') + 1).trim();
                GeminiConfig.getInstancia().salvarPersonalidade(novaPersona, "");
                System.out.println(VERDE + "✔ Personalidade atualizada com sucesso para: " + novaPersona + RESET);
                continue;
            }

            if (entrada.toLowerCase().startsWith(":audio ")) {
                processarComandoAudio(agente, sessaoTerminal, entrada.substring(7).trim());
                continue;
            }

            if (entrada.toLowerCase().startsWith(":imagem ")) {
                processarComandoImagem(agente, sessaoTerminal, entrada.substring(8).trim());
                continue;
            }

            // Envia o comando para o Agente Operacional com indicador de progresso e cancelamento seguro
            String resposta = executarComIndicadorProgresso(agente, sessaoTerminal, entrada, scanner);
            exibirRespostaFormatada(resposta);
            exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
        }

        exibirLogsAoSair(agente, sessaoTerminal);
        scanner.close();
    }

    private static String executarComIndicadorProgresso(
            GeminiAgenteOperacional agente,
            String sessaoTerminal,
            String entrada,
            Scanner scanner) {
        java.util.concurrent.atomic.AtomicReference<String> etapaAtual =
                new java.util.concurrent.atomic.AtomicReference<>("Processando");
        java.util.concurrent.atomic.AtomicReference<String> resultado =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean concluido =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread worker = Thread.startVirtualThread(() -> {
            agente.definirListenerProgresso(etapa -> etapaAtual.set(etapa.replace("...", "").trim()));
            try {
                resultado.set(agente.processarMensagem(sessaoTerminal, entrada));
            } catch (Exception e) {
                resultado.set("⚠️ Não foi possível concluir a operação: " + e.getMessage());
            } finally {
                agente.limparListenerProgresso();
                concluido.set(true);
            }
        });

        String[] pontos = {".", "..", "...", "....", "....."};
        int tick = 0;
        boolean cancelado = false;
        while (!concluido.get()) {
            String linhaStatus = String.format("\r%s  ⏳ %s%s (X + Enter para cancelar)%s",
                    CIANO, etapaAtual.get(), pontos[tick % pontos.length], RESET);
            System.out.print(linhaStatus + "   ");
            System.out.flush();
            tick++;

            try {
                if (System.in.available() > 0 && scanner.hasNextLine()) {
                    String cmd = scanner.nextLine().trim();
                    if (cmd.equalsIgnoreCase("x") || cmd.equalsIgnoreCase("c")
                            || cmd.equalsIgnoreCase("cancelar")) {
                        worker.interrupt();
                        cancelado = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Limpa a linha de status animada
        System.out.print("\r" + " ".repeat(72) + "\r");
        System.out.flush();

        // Descarta eventuais teclas digitadas por engano durante o bloqueio de escrita
        try {
            while (System.in.available() > 0 && scanner.hasNextLine()) {
                scanner.nextLine();
            }
        } catch (Exception ignored) {}

        if (cancelado) {
            return "⚠️ Operação cancelada pelo usuário. Nenhuma alteração pendente foi corrompida.";
        }
        return resultado.get() != null ? resultado.get() : "";
    }

    private static void processarComandoAudio(GeminiAgenteOperacional agente, String idSessao, String caminhoArquivo) {
        File file = new File(caminhoArquivo);
        if (!file.exists()) {
            System.out.println(VERMELHO + "❌ Arquivo de áudio não encontrado: " + caminhoArquivo + RESET);
            return;
        }

        try {
            System.out.println(AZUL + "🎧 Lendo e transcrevendo áudio: " + file.getName() + "..." + RESET);
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = file.getName().endsWith(".mp3") ? "audio/mp3" :
                          file.getName().endsWith(".wav") ? "audio/wav" : "audio/webm";

            String resposta = agente.processarAudio(idSessao, bytes, mime);
            exibirRespostaFormatada(resposta);
        } catch (Exception e) {
            System.out.println(VERMELHO + "❌ Erro ao carregar áudio: " + e.getMessage() + RESET);
        }
    }

    private static void processarComandoImagem(GeminiAgenteOperacional agente, String idSessao, String caminhoArquivo) {
        File file = new File(caminhoArquivo);
        if (!file.exists()) {
            System.out.println(VERMELHO + "❌ Arquivo de imagem não encontrado: " + caminhoArquivo + RESET);
            return;
        }

        try {
            System.out.println(AZUL + "📷 Analisando imagem/documento: " + file.getName() + "..." + RESET);
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = file.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

            String resposta = agente.processarImagem(idSessao, bytes, mime, "Analise este documento ou produto e execute no sistema.");
            exibirRespostaFormatada(resposta);
        } catch (Exception e) {
            System.out.println(VERMELHO + "❌ Erro ao processar imagem: " + e.getMessage() + RESET);
        }
    }

    public static String limparPoluicaoVisual(String resposta) {
        if (resposta == null) return "";
        return resposta
                .replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "")
                .replace("**", "")
                .replace("`", "")
                .replaceAll("(?m)^[╭╰─═]{3,}.*$", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static void exibirRespostaFormatada(String resposta) {
        String limpa = limparPoluicaoVisual(resposta);
        if (!limpa.isEmpty()) {
            System.out.println("\n" + FormatadorSaidaIA.formatarParaTerminalAnsi(limpa));
        }
    }

    private static void exibirBanner(GeminiAgenteOperacional agente) {
        String prov = ConfigIA.getInstancia().getProvedor();
        String modelo = GeminiConfig.getInstancia().getModel();

        System.out.println(CIANO + "================================================================================" + RESET);
        System.out.println(NEGRITO + CIANO + "   ⚡ SAZONALISEI - CHATBOT OPERACIONAL INTELIGENTE (TERMINAL CLI)" + RESET);
        System.out.println(CIANO + "================================================================================" + RESET);
        System.out.println(" • Provedor de IA: " + VERDE + prov.toUpperCase() + RESET + " (Modelo: " + modelo + ")");
        System.out.println(" • Regras Ativas:  " + AMARELO + "Concorrência Segura (Virtual Threads Java 21) | Padrão BACEN" + RESET);
        System.out.println(" • Governança:     " + VERMELHO + "Human-in-the-Loop (Confirmação obrigatória para ações destrutivas)" + RESET);
        System.out.println(" • Rotas Oficiais: " + CIANO + RotaSistema.values().length + " rotas do RoteadorController integradas" + RESET);
        System.out.println(CIANO + "--------------------------------------------------------------------------------" + RESET);
        System.out.println(" Digite em linguagem natural (ex: 'qual o preço do produto 86?', 'listar produtos')");
        System.out.println(" Comandos: " + NEGRITO + ":ajuda" + RESET + " | " + NEGRITO + ":bandeja" + RESET + " | " + NEGRITO + ":fechar" + RESET + " | " + NEGRITO + ":logs" + RESET + " | " + NEGRITO + ":rotas" + RESET + " | " + NEGRITO + ":persona <texto>" + RESET + " | " + NEGRITO + ":sair" + RESET);
        System.out.println(CIANO + "================================================================================" + RESET);
    }

    private static void exibirAjuda() {
        System.out.println("\n" + NEGRITO + CIANO + "📖 COMANDOS DISPONÍVEIS NO TERMINAL:" + RESET);
        System.out.println("  • " + VERDE + "<texto livre>" + RESET + "       : Envia comando em linguagem natural para a IA.");
        System.out.println("  • " + VERDE + ":bandeja" + RESET + "            : Exibe o status atual da Bandeja de Contexto (instruções validadas e pendentes).");
        System.out.println("  • " + VERDE + ":fechar" + RESET + "             : Encerra e limpa a janela de contexto atual (descarta pendências e limpa memória).");
        System.out.println("  • " + VERDE + ":logs [on|off|ver]" + RESET + "  : Ativa/desativa o salvamento de logs do fluxo (pré, IA, pós e retorno) e exibe ao finalizar a bandeja ou sair.");
        System.out.println("  • " + VERDE + ":rotas" + RESET + "              : Exibe o catálogo completo de rotas oficiais mapeadas.");
        System.out.println("  • " + VERDE + ":audio <arquivo>" + RESET + "     : Envia um arquivo de áudio (.wav, .mp3, .webm) para transcrição e ação.");
        System.out.println("  • " + VERDE + ":imagem <arquivo>" + RESET + "    : Envia foto ou scan (.png, .jpg) para análise multimodal.");
        System.out.println("  • " + VERDE + ":persona <texto>" + RESET + "    : Injeta nova personalidade e tom de voz na IA em tempo real.");
        System.out.println("  • " + VERDE + ":manual" + RESET + "             : Exibe o manual autoexplicativo gerado dinamicamente.");
        System.out.println("  • " + VERDE + ":limpar" + RESET + "             : Limpa o console e reseta a janela de contexto.");
        System.out.println("  • " + VERDE + ":sair" + RESET + "               : Encerra o ChatBot.");
        System.out.println("\n" + NEGRITO + "EXEMPLOS DE USO:" + RESET);
        System.out.println("  > " + CIANO + "cadastrar produto Leite um=UN" + RESET + " (adiciona à bandeja para confirmação)");
        System.out.println("  > " + CIANO + "altere o preço do produto 86 para 120 reais" + RESET);
        System.out.println("  > " + CIANO + "apague o produto 86" + RESET + " (o sistema pedirá confirmação antes)");
        System.out.println("  > " + CIANO + "sim" + RESET + " (executa todas as instruções validadas da bandeja e fecha o contexto)");
        System.out.println("  > " + CIANO + "nao" + RESET + " (descarta as instruções da bandeja e fecha o contexto)");
        System.out.println("  > " + CIANO + "listar todos os clientes cadastrados" + RESET);
        System.out.println("  > " + CIANO + "verifique o saldo consolidado do produto 86" + RESET);
    }

    private static void exibirCatalogoRotas() {
        System.out.println("\n" + NEGRITO + CIANO + "📋 CATÁLOGO DAS ROTAS DE CONTROLE (ROTEADORCONTROLLER):" + RESET);
        for (RotaSistema r : RotaSistema.values()) {
            System.out.printf(" • %-35s [%-10s] %s%n", r.name(), r.getModulo(), r.getDescricaoOperacao());
        }
    }

    private static void limparTela() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception ignored) {}
    }

    private static void tratarComandoLogs(GeminiAgenteOperacional agente, String sessaoTerminal, String entrada) {
        String arg = entrada.length() > 5 ? entrada.substring(5).trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (arg.equals("ver") || arg.equals("mostrar")) {
            String relatorio = agente.getLogFluxo().visualizarRelatorioAtual(sessaoTerminal);
            if (relatorio == null) {
                System.out.println(AMARELO + "ℹ️ Nenhum log registrado na sessão atual (status: "
                        + (agente.isSalvamentoLogsAtivo() ? "ATIVO" : "DESATIVADO") + ")." + RESET);
            } else {
                System.out.println("\n" + CIANO + relatorio + RESET);
            }
            return;
        }
        boolean novoEstado;
        if (arg.equals("on") || arg.equals("ativar") || arg.equals("1") || arg.equals("sim")) {
            novoEstado = true;
        } else if (arg.equals("off") || arg.equals("desativar") || arg.equals("0") || arg.equals("nao") || arg.equals("não")) {
            novoEstado = false;
        } else {
            novoEstado = !agente.isSalvamentoLogsAtivo();
        }
        agente.ativarSalvamentoLogs(novoEstado);
        if (novoEstado) {
            System.out.println(VERDE + "✔ Salvamento de logs do fluxo IA ATIVADO." + RESET);
            System.out.println(CIANO + "  • Arquivo: " + agente.getLogFluxo().getArquivoLogAtual() + RESET);
            System.out.println(CIANO + "  • Exibição automática ao finalizar a Bandeja de Contexto ou ao sair (:sair)." + RESET);
        } else {
            System.out.println(AMARELO + "⏸️ Salvamento de logs do fluxo IA DESATIVADO." + RESET);
        }
    }

    private static void exibirLogsSeBandejaFinalizada(GeminiAgenteOperacional agente, String sessaoTerminal) {
        String relatorio = agente.consumirLogsFluxoSeBandejaFinalizada(sessaoTerminal);
        if (relatorio != null) {
            System.out.println("\n" + CIANO + relatorio + RESET);
        }
    }

    private static void exibirLogsAoSair(GeminiAgenteOperacional agente, String sessaoTerminal) {
        String relatorio = agente.consumirLogsFluxoAoSair(sessaoTerminal);
        if (relatorio != null) {
            System.out.println("\n" + CIANO + relatorio + RESET);
        }
    }

    public static void silenciarLogsTecnicos() {
        try {
            java.util.logging.LogManager.getLogManager().reset();
            java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.hibernate").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("com.zaxxer.hikari").setLevel(java.util.logging.Level.OFF);
            System.setProperty("org.jboss.logging.provider", "jdk");
            java.io.PrintStream errOriginal = System.err;
            System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
                private final StringBuilder buffer = new StringBuilder();
                @Override
                public void write(int b) {
                    if (b == '\n') {
                        String linha = buffer.toString();
                        buffer.setLength(0);
                        if (!linha.contains("SLF4J") && !linha.contains("HHH0") && !linha.contains("org.hibernate")) {
                            errOriginal.println(linha);
                        }
                    } else if (b != '\r') {
                        buffer.append((char) b);
                    }
                }
            }, true, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
