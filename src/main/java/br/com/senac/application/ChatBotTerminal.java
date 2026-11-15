package br.com.senac.application;

import br.com.senac.application.terminal.AnalisadorTextoTerminal;
import br.com.senac.application.terminal.CatalogoControladoresTerminal;
import br.com.senac.application.terminal.FonteEntidadesTerminal;
import br.com.senac.application.terminal.FormatadorEntradaTerminal;
import br.com.senac.application.terminal.LeitorConsole;
import br.com.senac.application.terminal.LeitorConsoleFallback;
import br.com.senac.application.terminal.LeitorConsoleTempoReal;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.LayoutVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.LinhaVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.ResultadoAnaliseTerminal;
import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.ia.CompiladorInstrucoesCurtaService;
import br.com.senac.integracao.ia.ConfigIA;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.FormatadorSaidaIA;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.ProvedorIA.PedidoIA;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;
import br.com.senac.integracao.ia.bandeja.ComandoBandeja;
import br.com.senac.integracao.ia.bandeja.SimuladorBandejaService;
import br.com.senac.integracao.ia.parser.ErroSintaxe;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.integracao.ia.parser.ParserInstrucao;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.view.javafx.componentes.CapturadorVozMicrofone;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.senac.application.terminal.ModelosAnaliseTerminal.*;

/**
 * Interface Interativa do ChatBot Operacional via Terminal CLI para testes e usuários avançados.
 * Executável tanto diretamente pela classe ({@code main}) quanto via {@code .\iniciar_terminal.ps1}.
 */
public class ChatBotTerminal {

    private static final ParserInstrucao PARSER_INSTRUCAO = new ParserInstrucao();
    private static final PreProcessadorMensagem PRE_PROCESSADOR = new PreProcessadorMensagem();

    private static volatile boolean analiseAutomaticaAtiva = true;

    static {
        silenciarLogsTecnicos();
    }

    public static void main(String[] args) {
        silenciarLogsTecnicos();

        GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
        String sessaoTerminal = "terminal-user-" + System.currentTimeMillis();

        FonteEntidadesTerminal fonteEntidades = new CatalogoControladoresTerminal(agente.getRoteadorController());
        fonteEntidades.recarregar();
        AnalisadorTextoTerminal analisador = new AnalisadorTextoTerminal(fonteEntidades);

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            LeitorConsoleFallback fallback = new LeitorConsoleFallback(scanner);
            LeitorConsole leitorAtivo = LeitorConsoleTempoReal.abrirOuFallback(analisador, fallback);
            final LeitorConsole[] leitorRef = { leitorAtivo };

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                leitorRef[0].close();
                JPAUtil.close();
            }));

            exibirBanner(agente, leitorAtivo.detectarLarguraAtual(), leitorAtivo.isTempoReal());

            try {
                while (true) {
                    int larguraTerminal = leitorAtivo.detectarLarguraAtual();
                    String entrada = leitorAtivo.lerEntrada(null);
                    if (entrada == null && leitorAtivo != fallback) {
                        // Caso TTY não esteja disponível após abertura, cai para fallback Scanner
                        leitorAtivo = fallback;
                        leitorRef[0] = fallback;
                        entrada = leitorAtivo.lerEntrada(null);
                    }
                    if (entrada == null) {
                        break;
                    }
                    if (entrada.isEmpty()) {
                        continue;
                    }

                    if (ehComandoSair(entrada)) {
                        exibirLogsAoSair(agente, sessaoTerminal);
                        System.out.println(CIANO + "\nEncerrando ChatBot Terminal do Sazonalisei. Até logo!\n" + RESET);
                        break;
                    }

                    if (tratarComandoEspecial(agente, sessaoTerminal, entrada, leitorAtivo, analisador, fonteEntidades, larguraTerminal)) {
                        continue;
                    }

                    if (!leitorAtivo.isTempoReal()) {
                        exibirEntradaFormatadaFallback(entrada, analisador, larguraTerminal);
                    }
                    if (analiseAutomaticaAtiva) {
                        exibirIdentificacaoAutomatica(agente, sessaoTerminal, entrada, analisador, larguraTerminal);
                    }

                    final String entradaFinal = entrada;
                    String resposta = executarComIndicadorProgresso(
                            agente,
                            "Processando",
                            leitorAtivo,
                            larguraTerminal,
                            () -> agente.processarMensagem(sessaoTerminal, entradaFinal)
                    );
                    exibirRespostaFormatada(resposta, larguraTerminal);
                    exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
                    fonteEntidades.recarregar();
                }
            } finally {
                leitorAtivo.close();
                JPAUtil.close();
            }
        }
    }

    private static boolean ehComandoSair(String entrada) {
        String lower = entrada.toLowerCase(Locale.ROOT);
        return lower.equals(":sair")
                || lower.equals(":exit")
                || lower.equals(":q")
                || lower.equals("sair")
                || lower.equals("exit");
    }

    private static boolean tratarComandoEspecial(
            GeminiAgenteOperacional agente,
            String sessaoTerminal,
            String entrada,
            LeitorConsole leitor,
            AnalisadorTextoTerminal analisador,
            FonteEntidadesTerminal fonteEntidades,
            int larguraTerminal) {
        String lower = entrada.toLowerCase(Locale.ROOT);

        if (lower.equals(":ajuda") || lower.equals(":help") || lower.equals(":h")) {
            exibirAjuda(larguraTerminal);
            return true;
        }

        if (lower.equals(":limpar") || lower.equals(":cls") || lower.equals(":clear")) {
            agente.fecharJanelaContexto(sessaoTerminal);
            exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
            limparTela();
            exibirBanner(agente, leitor.detectarLarguraAtual(), leitor.isTempoReal());
            System.out.println(VERDE + "✔ Janela de contexto e bandeja foram reiniciadas limpas." + RESET);
            return true;
        }

        if (lower.equals(":bandeja") || lower.equals(":tray")) {
            exibirRespostaFormatada(agente.obterBandeja(sessaoTerminal).formatarVisualizacao(), larguraTerminal);
            return true;
        }

        if (lower.equals(":simular") || lower.equals(":sim")) {
            var bandejaAtual = agente.obterBandeja(sessaoTerminal);
            String sim = SimuladorBandejaService.gerarRelatorioSimulacaoUsuario(
                    bandejaAtual, agente.getRoteadorController());
            System.out.println("\n" + AMARELO + quebrarParaTerminal(sim, larguraTerminal) + RESET);
            return true;
        }

        if (lower.equals(":fechar")) {
            agente.fecharJanelaContexto(sessaoTerminal);
            System.out.println(VERDE + "✔ Janela de contexto encerrada com sucesso. Sessão limpa e pronta!" + RESET);
            exibirLogsSeBandejaFinalizada(agente, sessaoTerminal);
            return true;
        }

        if (lower.equals(":logs") || lower.startsWith(":logs ")) {
            tratarComandoLogs(agente, sessaoTerminal, entrada);
            return true;
        }

        if (lower.equals(":analise") || lower.startsWith(":analise ")
                || lower.equals(":inspecao") || lower.startsWith(":inspecao ")) {
            tratarComandoAnalise(entrada);
            return true;
        }

        if (lower.equals(":rotas")) {
            exibirCatalogoRotas();
            return true;
        }

        if (lower.equals(":manual")) {
            System.out.println("\n" + CIANO + quebrarParaTerminal(ManualInstrucoesIA.gerar(), larguraTerminal) + RESET);
            return true;
        }

        if (lower.equals(":persona") || lower.startsWith(":persona ")) {
            tratarComandoPersona(entrada);
            return true;
        }

        if (lower.equals(":voz") || lower.equals(":mic") || lower.equals(":gravar")) {
            processarAudioMicrofone(agente, sessaoTerminal, leitor, analisador, fonteEntidades, larguraTerminal, false);
            return true;
        }

        if (lower.equals(":transcrever") || lower.startsWith(":transcrever ")) {
            String arg = entrada.length() > 12 ? limparAspasCaminho(entrada.substring(12)) : "";
            if (arg.isEmpty()) {
                processarAudioMicrofone(agente, sessaoTerminal, leitor, analisador, fonteEntidades, larguraTerminal, true);
            } else {
                processarAudioArquivo(agente, sessaoTerminal, arg, leitor, analisador, fonteEntidades, larguraTerminal, true);
            }
            return true;
        }

        if (lower.equals(":audio") || lower.startsWith(":audio ")) {
            String caminho = entrada.length() > 6 ? limparAspasCaminho(entrada.substring(6)) : "";
            if (caminho.isEmpty()) {
                System.out.println(AMARELO + "ℹ️ Informe o arquivo de áudio (ex: :audio C:\\audios\\pedido.wav) ou use :voz / :transcrever." + RESET);
            } else {
                processarAudioArquivo(agente, sessaoTerminal, caminho, leitor, analisador, fonteEntidades, larguraTerminal, false);
            }
            return true;
        }

        if (lower.equals(":imagem") || lower.startsWith(":imagem ")) {
            String caminho = entrada.length() > 7 ? limparAspasCaminho(entrada.substring(7)) : "";
            if (caminho.isEmpty()) {
                System.out.println(AMARELO + "ℹ️ Informe o caminho da imagem (ex: :imagem C:\\fotos\\nota.png)." + RESET);
            } else {
                processarComandoImagem(agente, sessaoTerminal, caminho, leitor, larguraTerminal);
            }
            return true;
        }

        return false;
    }

    private static void exibirEntradaFormatadaFallback(
            String entrada,
            AnalisadorTextoTerminal analisador,
            int larguraTerminal) {
        ResultadoAnaliseTerminal analise = analisador.analisar(entrada, entrada.length());
        LayoutVisual layout = FormatadorEntradaTerminal.calcularLayoutVisual(entrada, entrada.length(), larguraTerminal);
        boolean precisaMostrar = layout.linhas().size() > 1
                || entrada.contains("\n")
                || !analise.trechos().isEmpty();
        if (!precisaMostrar) {
            return;
        }
        System.out.println(CINZA + "  📝 Entrada formatada (" + larguraTerminal + " cols):" + RESET);
        for (LinhaVisual lv : layout.linhas()) {
            String colorida = FormatadorEntradaTerminal.colorirTrechoPreservandoLargura(
                    entrada, lv.inicioTexto(), lv.fimTexto(), analise.trechos());
            System.out.println("     " + colorida);
        }
    }

    private static void exibirIdentificacaoAutomatica(
            GeminiAgenteOperacional agente,
            String sessaoTerminal,
            String entrada,
            AnalisadorTextoTerminal analisador,
            int larguraTerminal) {
        if (CompiladorInstrucoesCurtaService.ehComandoEncurtado(entrada)) {
            exibirInspecaoInstrucaoDsl(entrada, analisador, larguraTerminal);
            return;
        }

        var bandeja = agente.obterBandeja(sessaoTerminal);
        if (bandeja != null && bandeja.temItens()) {
            Optional<ComandoBandeja> cmdBandeja = ComandoBandeja.interpretar(entrada);
            if (cmdBandeja.isPresent()) {
                String alvo = cmdBandeja.get().alvo() != null ? " -> " + cmdBandeja.get().alvo() : "";
                System.out.println(ROXO + "  🔎 [Bandeja Local] " + RESET
                        + AMARELO + cmdBandeja.get().acao() + alvo + RESET);
                return;
            }
        }

        if (agente.temConfirmacaoPendente(sessaoTerminal)) {
            DecisaoConfirmacao decisao = DecisaoConfirmacao.interpretar(entrada);
            if (decisao != DecisaoConfirmacao.INDEFINIDA) {
                System.out.println(ROXO + "  🔎 [Confirmação Bandeja] " + RESET
                        + AMARELO + decisao.name() + RESET);
                return;
            }
        }

        ResultadoAnaliseTerminal analise = analisador.analisar(entrada, entrada.length());
        PreProcessadorMensagem.ResultadoPreProcessamento preProc = PRE_PROCESSADOR.processar(entrada);
        boolean siglaExplicita = BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(entrada);

        if (!analise.resumoIdentificacao().isBlank()) {
            for (String linha : quebrarParaTerminal(analise.resumoIdentificacao(), Math.max(40, larguraTerminal - 4)).split("\n")) {
                System.out.println(ROXO + "  " + linha + RESET);
            }
        }
        if (siglaExplicita) {
            System.out.println("  " + AMARELO + "⚡ [ATALHO_SIGLA]" + RESET + ": " + CIANO + entrada.trim() + RESET);
        }
        if (preProc.rotasSugeridas() != null && !preProc.rotasSugeridas().isEmpty()) {
            String rotas = "Rotas prioritárias: " + String.join(", ", preProc.rotasSugeridas());
            for (String linha : quebrarParaTerminal(rotas, Math.max(40, larguraTerminal - 7)).split("\n")) {
                System.out.println(CINZA + "     • " + linha + RESET);
            }
        }
        if (!analise.dicaLinha().isBlank()) {
            for (String linha : quebrarParaTerminal(analise.dicaLinha(), Math.max(40, larguraTerminal - 4)).split("\n")) {
                System.out.println(CINZA + "  " + linha + RESET);
            }
        }
    }

    private static void exibirInspecaoInstrucaoDsl(
            String entrada,
            AnalisadorTextoTerminal analisador,
            int larguraTerminal) {
        try {
            List<NoInstrucao> nos = PARSER_INSTRUCAO.parsear(entrada);
            if (nos.isEmpty()) {
                return;
            }
            System.out.println(CIANO + "  ⚡ [DSL $$ Válida] " + nos.size() + " instrução(ões) AST:" + RESET);
            for (int i = 0; i < nos.size(); i++) {
                NoInstrucao no = nos.get(i);
                StringBuilder resumo = new StringBuilder();
                resumo.append(i + 1).append(". ")
                        .append(no.getOperacao().name()).append("= #").append(no.getEntidade());
                if (!no.getAtributos().isEmpty()) {
                    resumo.append(" ").append(no.getAtributos());
                }
                if (no.temSubInstrucoes()) {
                    resumo.append(" [").append(no.getSubInstrucoes().size()).append(" sub-item(ns)]");
                }
                if (!no.getCascata().isEmpty()) {
                    resumo.append(" >> ").append(no.getCascata());
                }
                for (String linha : quebrarParaTerminal(resumo.toString(), Math.max(40, larguraTerminal - 7)).split("\n")) {
                    System.out.println("     " + VERDE + linha + RESET);
                }
            }
            ResultadoAnaliseTerminal analise = analisador.analisar(entrada, entrada.length());
            if (!analise.entidadesIdentificadas().isEmpty()) {
                System.out.println(ROXO + "     • Entidades resolvidas: "
                        + String.join(" | ", analise.entidadesIdentificadas()) + RESET);
            }
        } catch (ErroSintaxe e) {
            System.out.println(AMARELO + "  ⚠️ [DSL $$ Sintaxe] Erro detectado na pré-análise (detalhes abaixo)." + RESET);
        }
    }

    private static void tratarComandoAnalise(String entrada) {
        String arg = entrada.contains(" ")
                ? entrada.substring(entrada.indexOf(' ') + 1).trim().toLowerCase(Locale.ROOT)
                : "";
        if (arg.equals("on") || arg.equals("ativar") || arg.equals("1") || arg.equals("sim")) {
            analiseAutomaticaAtiva = true;
        } else if (arg.equals("off") || arg.equals("desativar") || arg.equals("0") || arg.equals("nao") || arg.equals("não")) {
            analiseAutomaticaAtiva = false;
        } else {
            analiseAutomaticaAtiva = !analiseAutomaticaAtiva;
        }
        System.out.println((analiseAutomaticaAtiva ? VERDE : AMARELO)
                + "🔎 Identificação automática de entidades, dicas e DSL $$: "
                + (analiseAutomaticaAtiva ? "ATIVADA" : "DESATIVADA") + RESET);
    }

    private static void tratarComandoPersona(String entrada) {
        int idx = entrada.indexOf(' ');
        String novaPersona = idx >= 0 ? entrada.substring(idx + 1).trim() : "";
        if (novaPersona.isEmpty()) {
            System.out.println(AMARELO + "ℹ️ Informe a personalidade desejada após o comando. Ex: :persona técnico objetivo" + RESET);
            return;
        }
        GeminiConfig.getInstancia().salvarPersonalidade(novaPersona, "");
        System.out.println(VERDE + "✔ Personalidade atualizada com sucesso para: " + novaPersona + RESET);
    }

    private static void processarAudioMicrofone(
            GeminiAgenteOperacional agente,
            String idSessao,
            LeitorConsole leitor,
            AnalisadorTextoTerminal analisador,
            FonteEntidadesTerminal fonteEntidades,
            int larguraTerminal,
            boolean apenasTranscreverPrimeiro) {
        CapturadorVozMicrofone capturador = new CapturadorVozMicrofone();
        if (!capturador.isMicrofoneDisponivel()) {
            System.out.println(AMARELO + "⚠️ Nenhum microfone compatível (16kHz PCM) foi detectado. "
                    + "Você pode enviar um arquivo usando :audio <caminho> ou :transcrever <caminho>." + RESET);
            return;
        }

        try {
            capturador.iniciarGravacao();
            System.out.println(AZUL + "\n🎙️ Gravando do microfone... Fale naturalmente." + RESET);
            System.out.print(CINZA + "   Pressione ENTER para parar e enviar (ou X + ENTER para cancelar): " + RESET);
            System.out.flush();

            String comando = leitor.lerLinhaSimples();
            byte[] audioWav = capturador.pararEObterWav();

            if (comando.equalsIgnoreCase("x") || comando.equalsIgnoreCase("cancelar")) {
                System.out.println(AMARELO + "⚠️ Gravação de áudio cancelada." + RESET);
                return;
            }
            if (audioWav.length == 0) {
                System.out.println(AMARELO + "⚠️ Nenhum som foi capturado. Tente falar mais próximo ao microfone." + RESET);
                return;
            }

            System.out.println(CINZA + "   🎧 Áudio capturado (" + Math.max(1, audioWav.length / 1024) + " KB)." + RESET);
            executarFluxoAudio(agente, idSessao, audioWav, "audio/wav", leitor, analisador, fonteEntidades, larguraTerminal, apenasTranscreverPrimeiro);
        } catch (Exception e) {
            System.out.println(VERMELHO + "❌ Não foi possível capturar áudio do microfone: " + e.getMessage() + RESET);
        }
    }

    private static void processarAudioArquivo(
            GeminiAgenteOperacional agente,
            String idSessao,
            String caminhoArquivo,
            LeitorConsole leitor,
            AnalisadorTextoTerminal analisador,
            FonteEntidadesTerminal fonteEntidades,
            int larguraTerminal,
            boolean apenasTranscreverPrimeiro) {
        File file = new File(caminhoArquivo);
        if (!file.exists() || !file.isFile()) {
            System.out.println(VERMELHO + "❌ Arquivo de áudio não encontrado: " + caminhoArquivo + RESET);
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = detectarMimeAudio(file.getName());
            System.out.println(AZUL + "🎧 Lendo áudio: " + file.getName() + " (" + Math.max(1, bytes.length / 1024) + " KB)..." + RESET);
            executarFluxoAudio(agente, idSessao, bytes, mime, leitor, analisador, fonteEntidades, larguraTerminal, apenasTranscreverPrimeiro);
        } catch (Exception e) {
            System.out.println(VERMELHO + "❌ Erro ao carregar áudio: " + e.getMessage() + RESET);
        }
    }

    private static void executarFluxoAudio(
            GeminiAgenteOperacional agente,
            String idSessao,
            byte[] audioBytes,
            String mimeType,
            LeitorConsole leitor,
            AnalisadorTextoTerminal analisador,
            FonteEntidadesTerminal fonteEntidades,
            int larguraTerminal,
            boolean apenasTranscreverPrimeiro) {
        if (!apenasTranscreverPrimeiro) {
            String resposta = executarComIndicadorProgresso(
                    agente,
                    "Transcrevendo e executando áudio",
                    leitor,
                    larguraTerminal,
                    () -> agente.processarAudio(idSessao, audioBytes, mimeType)
            );
            exibirRespostaFormatada(resposta, larguraTerminal);
            exibirLogsSeBandejaFinalizada(agente, idSessao);
            fonteEntidades.recarregar();
            return;
        }

        String transcricao = executarComIndicadorProgresso(
                agente,
                "Transcrevendo áudio",
                leitor,
                larguraTerminal,
                () -> transcreverAudioParaTexto(agente, audioBytes, mimeType)
        );
        if (transcricao == null || transcricao.isBlank()) {
            System.out.println(AMARELO + "⚠️ Não foi possível obter a transcrição desse áudio." + RESET);
            return;
        }
        if (transcricao.startsWith("⚠️")) {
            exibirRespostaFormatada(transcricao, larguraTerminal);
            return;
        }

        System.out.println("\n" + NEGRITO + CIANO + "📝 Transcrição do áudio:" + RESET);
        for (String linha : quebrarParaTerminal(transcricao, Math.max(40, larguraTerminal - 5)).split("\n")) {
            System.out.println("   " + linha);
        }
        if (analiseAutomaticaAtiva) {
            exibirIdentificacaoAutomatica(agente, idSessao, transcricao, analisador, larguraTerminal);
        }

        System.out.print("\n" + VERDE + "Deseja enviar (S), editar com dicas (E) ou cancelar (N)? [S/E/N]: " + RESET);
        System.out.flush();
        String resp = leitor.lerLinhaSimples().toLowerCase(Locale.ROOT);
        String textoFinal = null;
        if (resp.equals("s") || resp.equals("sim")) {
            textoFinal = transcricao;
        } else if (resp.equals("e") || resp.equals("editar")) {
            String editado = leitor.lerEntrada(transcricao);
            if (editado != null && !editado.isBlank()) {
                textoFinal = editado;
                if (analiseAutomaticaAtiva) {
                    exibirIdentificacaoAutomatica(agente, idSessao, textoFinal, analisador, larguraTerminal);
                }
            }
        }

        if (textoFinal == null || textoFinal.isBlank()) {
            System.out.println(CINZA + "   Transcrição descartada sem envio." + RESET);
            return;
        }

        final String enviarFinal = textoFinal;
        String execucao = executarComIndicadorProgresso(
                agente,
                "Processando transcrição",
                leitor,
                larguraTerminal,
                () -> agente.processarMensagem(idSessao, enviarFinal)
        );
        exibirRespostaFormatada(execucao, larguraTerminal);
        exibirLogsSeBandejaFinalizada(agente, idSessao);
        fonteEntidades.recarregar();
    }

    private static String transcreverAudioParaTexto(
            GeminiAgenteOperacional agente,
            byte[] audioBytes,
            String mimeType) throws Exception {
        ProvedorIA provedor = agente.getProvedor();
        if (provedor == null) {
            return "⚠️ Modo sem API ativo. Configure um provedor multimodal (como Gemini) para transcrever áudio.";
        }
        if (!provedor.suportaMidia()) {
            return "⚠️ Nenhum provedor com suporte a áudio/mídia está configurado no momento (configure Gemini para transcrição).";
        }
        String pronto = provedor.verificarPronto();
        if (pronto != null) {
            return pronto;
        }
        RespostaIA resp = provedor.conversar(new PedidoIA(
                "Transcreva fielmente em português o conteúdo falado neste áudio, mantendo números, produtos e valores exatos. "
                        + "Responda apenas com o texto transcrito, sem executar ferramentas.",
                audioBytes,
                mimeType
        ));
        return resp != null && resp.texto() != null ? resp.texto().trim() : "";
    }

    private static void processarComandoImagem(
            GeminiAgenteOperacional agente,
            String idSessao,
            String caminhoArquivo,
            LeitorConsole leitor,
            int larguraTerminal) {
        File file = new File(caminhoArquivo);
        if (!file.exists() || !file.isFile()) {
            System.out.println(VERMELHO + "❌ Arquivo de imagem não encontrado: " + caminhoArquivo + RESET);
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mime = detectarMimeImagem(file.getName());
            System.out.println(AZUL + "📷 Analisando imagem/documento: " + file.getName() + "..." + RESET);
            String resposta = executarComIndicadorProgresso(
                    agente,
                    "Analisando imagem",
                    leitor,
                    larguraTerminal,
                    () -> agente.processarImagem(idSessao, bytes, mime, "Analise este documento ou produto e execute no sistema.")
            );
            exibirRespostaFormatada(resposta, larguraTerminal);
            exibirLogsSeBandejaFinalizada(agente, idSessao);
        } catch (Exception e) {
            System.out.println(VERMELHO + "❌ Erro ao processar imagem: " + e.getMessage() + RESET);
        }
    }

    private static String executarComIndicadorProgresso(
            GeminiAgenteOperacional agente,
            String etapaInicial,
            LeitorConsole leitor,
            int larguraTerminal,
            Callable<String> tarefa) {
        AtomicReference<String> etapaAtual = new AtomicReference<>(etapaInicial);
        AtomicReference<String> resultado = new AtomicReference<>();
        AtomicBoolean concluido = new AtomicBoolean(false);

        Thread worker = Thread.startVirtualThread(() -> {
            agente.definirListenerProgresso(etapa -> {
                if (etapa != null && !etapa.isBlank()) {
                    etapaAtual.set(etapa.replace("...", "").trim());
                }
            });
            try {
                resultado.set(tarefa.call());
            } catch (Throwable e) {
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
            String linhaStatus = String.format("\r%s  ⏳ %s%s (X para cancelar)%s",
                    CIANO, etapaAtual.get(), pontos[tick % pontos.length], RESET);
            System.out.print(linhaStatus + "   ");
            System.out.flush();
            tick++;

            if (leitor.detectarCancelamentoNaoBloqueante()) {
                worker.interrupt();
                cancelado = true;
                break;
            }

            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.print("\r" + " ".repeat(Math.min(Math.max(larguraTerminal - 1, 60), 120)) + "\r");
        System.out.flush();

        leitor.descartarEntradaPendente();

        if (cancelado) {
            return "⚠️ Operação cancelada pelo usuário. Nenhuma alteração pendente foi corrompida.";
        }
        return resultado.get() != null ? resultado.get() : "";
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

    private static void exibirRespostaFormatada(String resposta, int larguraTerminal) {
        String limpa = limparPoluicaoVisual(resposta);
        if (!limpa.isEmpty()) {
            int maxChars = Math.max(60, larguraTerminal - 2);
            int maxPalavras = Math.max(FormatadorSaidaIA.PADRAO_PALAVRAS_POR_LINHA, maxChars / 5);
            var config = new FormatadorSaidaIA.ConfiguracaoFormatacaoSaida(
                    maxPalavras, maxChars, "Segoe UI", "Consolas", 16, 13, true);
            System.out.println("\n" + FormatadorSaidaIA.formatar(limpa, config).textoAnsiColorido());
        }
    }

    private static String quebrarParaTerminal(String texto, int larguraTerminal) {
        int maxChars = Math.max(40, larguraTerminal - 4);
        int maxPalavras = Math.max(12, maxChars / 4);
        return FormatadorSaidaIA.quebrarLinhasPorPalavras(texto, maxPalavras, maxChars);
    }

    private static void exibirBanner(GeminiAgenteOperacional agente, int larguraTerminal, boolean modoTempoReal) {
        ProvedorIA provedor = agente.getProvedor();
        String prov = provedor != null
                ? provedor.getId()
                : ConfigIA.getInstancia().getProvedor();
        String descricaoModelo = provedor != null
                ? provedor.getDescricao()
                : GeminiConfig.getInstancia().getModel();

        int larguraLinha = Math.max(60, Math.min(larguraTerminal - 2, 110));
        String sepDuplo = "=".repeat(larguraLinha);
        String sepSimples = "-".repeat(larguraLinha);

        System.out.println(CIANO + sepDuplo + RESET);
        System.out.println(NEGRITO + CIANO + "   ⚡ SAZONALISEI - CHATBOT OPERACIONAL INTELIGENTE (TERMINAL CLI)" + RESET);
        System.out.println(CIANO + sepDuplo + RESET);
        System.out.println(" • Provedor de IA: " + VERDE + prov.toUpperCase(Locale.ROOT) + RESET + " (" + descricaoModelo + ")");
        System.out.println(" • Regras Ativas:  " + AMARELO + "Virtual Threads Java 21 | Padrão BACEN | Human-in-the-Loop" + RESET);
        System.out.println(" • Terminal:       " + CIANO + RotaSistema.values().length + " rotas" + RESET
                + " | Largura: " + CIANO + larguraTerminal + " cols" + RESET
                + " | Digitação: " + (modoTempoReal ? VERDE + "TEMPO REAL (Quebra/Dicas/TAB)" : AMARELO + "ENTRADA POR LINHA (IDE/Pipe)") + RESET
                + " | Análise: " + (analiseAutomaticaAtiva ? VERDE + "ON" : AMARELO + "OFF") + RESET);
        System.out.println(CIANO + sepSimples + RESET);
        System.out.println(" • Entrada:  Texto natural (produtos, fornecedores, IDs, CPF/CNPJ/registros), siglas ("
                + CIANO + ">oba" + RESET + "), DSL " + CIANO + "$$" + RESET + ", " + CIANO + "TAB" + RESET + " completa");
        System.out.println(" • Comandos: " + NEGRITO + ":ajuda" + RESET + " | " + NEGRITO + ":voz" + RESET + " | "
                + NEGRITO + ":transcrever" + RESET + " | " + NEGRITO + ":audio" + RESET + " | " + NEGRITO + ":imagem" + RESET + " | "
                + NEGRITO + ":bandeja" + RESET + " | " + NEGRITO + ":simular" + RESET + " | " + NEGRITO + ":logs" + RESET + " | "
                + NEGRITO + ":analise" + RESET + " | " + NEGRITO + ":sair" + RESET);
        System.out.println(CIANO + sepDuplo + RESET);
    }

    private static void exibirAjuda(int larguraTerminal) {
        System.out.println("\n" + NEGRITO + CIANO + "📖 COMANDOS DISPONÍVEIS NO TERMINAL (AVANÇADO & TESTES):" + RESET);
        System.out.println("  • " + VERDE + "<texto livre>" + RESET + "       : Reconhece produtos, fornecedores, clientes, IDs (#12), registros (CPF/CNPJ) e DSL $$.");
        System.out.println("  • " + VERDE + "TAB / ↑↓ / Esc" + RESET + "      : Completa entidades/dicas, navega histórico ou limpa a linha atual.");
        System.out.println("  • " + VERDE + "\\ ou \"\"\" ou [...]" + RESET + " : Quebra automática pela largura (" + larguraTerminal + " cols) e blocos multilinha.");
        System.out.println("  • " + VERDE + ":voz" + RESET + "                : Grava áudio do microfone e executa diretamente na IA (:mic, :gravar).");
        System.out.println("  • " + VERDE + ":transcrever [arq]" + RESET + "  : Transcreve áudio (mic ou arquivo), permite editar (E) com dicas e confirmar (S/N).");
        System.out.println("  • " + VERDE + ":audio <arquivo>" + RESET + "    : Envia arquivo (.wav, .mp3, .webm, .ogg, .m4a, .aac, .flac) para ação direta.");
        System.out.println("  • " + VERDE + ":imagem <arquivo>" + RESET + "   : Envia foto ou scan (.png, .jpg, .webp, .gif) para análise multimodal.");
        System.out.println("  • " + VERDE + ":analise [on|off]" + RESET + "   : Ativa/desativa a inspeção local automática de entidades, dicas e AST $$.");
        System.out.println("  • " + VERDE + ":bandeja" + RESET + "            : Exibe o status atual da Bandeja de Contexto (instruções validadas e pendentes).");
        System.out.println("  • " + VERDE + ":simular" + RESET + "            : Simula custos, lucro, margem e impacto no estoque dos itens na bandeja.");
        System.out.println("  • " + VERDE + ":fechar" + RESET + "             : Encerra e limpa a janela de contexto atual (descarta pendências e limpa memória).");
        System.out.println("  • " + VERDE + ":logs [on|off|ver]" + RESET + "  : Ativa/desativa o salvamento de logs do fluxo (pré, IA, pós e retorno).");
        System.out.println("  • " + VERDE + ":rotas" + RESET + "              : Exibe o catálogo completo de rotas oficiais mapeadas.");
        System.out.println("  • " + VERDE + ":persona <texto>" + RESET + "    : Injeta nova personalidade e tom de voz na IA em tempo real.");
        System.out.println("  • " + VERDE + ":manual" + RESET + "             : Exibe o manual autoexplicativo gerado dinamicamente.");
        System.out.println("  • " + VERDE + ":limpar" + RESET + "             : Limpa o console e reseta a janela de contexto.");
        System.out.println("  • " + VERDE + ":sair" + RESET + "               : Encerra o ChatBot.");
    }

    private static void exibirCatalogoRotas() {
        System.out.println("\n" + NEGRITO + CIANO + "📋 CATÁLOGO DAS ROTAS DE CONTROLE (ROTEADORCONTROLLER):" + RESET);
        for (RotaSistema r : RotaSistema.values()) {
            System.out.printf(" • %-35s [%-12s] %s%n", r.name(), r.getModulo(), r.getDescricaoOperacao());
        }
    }

    private static void limparTela() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private static void tratarComandoLogs(GeminiAgenteOperacional agente, String sessaoTerminal, String entrada) {
        String arg = entrada.length() > 5 ? entrada.substring(5).trim().toLowerCase(Locale.ROOT) : "";
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

    private static String limparAspasCaminho(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static String detectarMimeAudio(String nomeArquivo) {
        String lower = nomeArquivo.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return "audio/mp3";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "audio/webm";
    }

    private static String detectarMimeImagem(String nomeArquivo) {
        String lower = nomeArquivo.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
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
            }, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
