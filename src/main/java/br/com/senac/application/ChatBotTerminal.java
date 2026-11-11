package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.ia.ConfigIA;
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

    public static void main(String[] args) {
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
                System.out.println(CIANO + "\nEncerrando ChatBot Terminal do Sazonalisei. Até logo!\n" + RESET);
                break;
            }

            if (entrada.equalsIgnoreCase(":ajuda") || entrada.equalsIgnoreCase(":help") || entrada.equalsIgnoreCase(":h")) {
                exibirAjuda();
                continue;
            }

            if (entrada.equalsIgnoreCase(":limpar") || entrada.equalsIgnoreCase(":cls") || entrada.equalsIgnoreCase(":clear")) {
                agente.fecharJanelaContexto(sessaoTerminal);
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

            // Envia o comando em linguagem natural para o Agente Operacional
            System.out.println(AZUL + "⏳ Processando comando e analisando regras de controle..." + RESET);
            String resposta = agente.processarMensagem(sessaoTerminal, entrada);
            exibirRespostaFormatada(resposta);
        }

        scanner.close();
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

    private static void exibirRespostaFormatada(String resposta) {
        System.out.println("\n" + NEGRITO + CIANO + "╭────────────────────── RESPOSTA DO CHATBOT ──────────────────────" + RESET);

        // Remove tokens específicos de interface web se presentes
        String limpa = resposta
                .replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "")
                .trim();

        for (String linha : limpa.split("\n")) {
            if (linha.startsWith("🧠")) {
                System.out.println(ROXO + NEGRITO + linha + RESET);
            } else if (linha.startsWith("🎯")) {
                System.out.println(CIANO + NEGRITO + linha + RESET);
            } else if (linha.startsWith("📋")) {
                System.out.println(AZUL + linha + RESET);
            } else if (linha.startsWith("⚖️")) {
                System.out.println(AMARELO + linha + RESET);
            } else if (linha.startsWith("⚠️")) {
                System.out.println(VERMELHO + NEGRITO + linha + RESET);
            } else if (linha.startsWith("✅") || linha.startsWith("🗑️") || linha.startsWith("🏷️")) {
                System.out.println(VERDE + NEGRITO + linha + RESET);
            } else if (linha.startsWith("─")) {
                System.out.println(AMARELO + linha + RESET);
            } else {
                System.out.println(linha);
            }
        }

        System.out.println(CIANO + "╰─────────────────────────────────────────────────────────────────" + RESET);
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
        System.out.println(" Comandos: " + NEGRITO + ":ajuda" + RESET + " | " + NEGRITO + ":bandeja" + RESET + " | " + NEGRITO + ":fechar" + RESET + " | " + NEGRITO + ":rotas" + RESET + " | " + NEGRITO + ":persona <texto>" + RESET + " | " + NEGRITO + ":sair" + RESET);
        System.out.println(CIANO + "================================================================================" + RESET);
    }

    private static void exibirAjuda() {
        System.out.println("\n" + NEGRITO + CIANO + "📖 COMANDOS DISPONÍVEIS NO TERMINAL:" + RESET);
        System.out.println("  • " + VERDE + "<texto livre>" + RESET + "       : Envia comando em linguagem natural para a IA.");
        System.out.println("  • " + VERDE + ":bandeja" + RESET + "            : Exibe o status atual da Bandeja de Contexto (instruções validadas e pendentes).");
        System.out.println("  • " + VERDE + ":fechar" + RESET + "             : Encerra e limpa a janela de contexto atual (descarta pendências e limpa memória).");
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
}
