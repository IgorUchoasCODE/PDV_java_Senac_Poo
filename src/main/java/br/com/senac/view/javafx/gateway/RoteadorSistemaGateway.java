package br.com.senac.view.javafx.gateway;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.csv.ImportadorCsvService;
import br.com.senac.integracao.csv.LeitorCsv;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.ia.ConfigIA;
import br.com.senac.integracao.ia.FormatadorSaidaIA;
import br.com.senac.integracao.ia.ManualInstrucoesIA;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Implementação padrão de {@link SistemaGateway} utilizando {@link RoteadorController},
 * {@link GeminiAgenteOperacional}, {@link ImportadorCsvService} e Virtual Threads do Java 21.
 */
public class RoteadorSistemaGateway implements SistemaGateway {

    private final RoteadorController roteador;
    private final ImportadorCsvService importadorCsv;
    private final LeitorCsv leitorCsv;
    private final ExecutorService executorVirtual;
    private GeminiAgenteOperacional agenteChat;

    public RoteadorSistemaGateway() {
        this.roteador = new RoteadorController();
        this.importadorCsv = new ImportadorCsvService(this.roteador);
        this.leitorCsv = new LeitorCsv();
        this.executorVirtual = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public RespostaEndpoint<Object> executarRota(RotaSistema rota, Map<String, Object> parametros) {
        return roteador.executarEndpoint(rota, parametros != null ? parametros : Map.of());
    }

    @Override
    public synchronized String perguntarAoChat(String idSessao, String mensagem) {
        return perguntarAoChatComProgresso(idSessao, mensagem, null);
    }

    @Override
    public synchronized String perguntarAoChatComProgresso(String idSessao, String mensagem, Consumer<String> aoProgresso) {
        if (agenteChat == null) {
            agenteChat = new GeminiAgenteOperacional();
        }
        if (aoProgresso != null) {
            agenteChat.definirListenerProgresso(aoProgresso);
        }
        try {
            return agenteChat.processarMensagem(idSessao, mensagem);
        } finally {
            agenteChat.limparListenerProgresso();
        }
    }

    @Override
    public synchronized String perguntarPorVoz(String idSessao, byte[] audioBytes, String mimeType) {
        if (agenteChat == null) {
            agenteChat = new GeminiAgenteOperacional();
        }
        return agenteChat.processarAudio(idSessao, audioBytes, mimeType);
    }

    @Override
    public synchronized String perguntarComImagem(String idSessao, byte[] imagemBytes, String mimeType, String prompt) {
        if (agenteChat == null) {
            agenteChat = new GeminiAgenteOperacional();
        }
        return agenteChat.processarImagem(idSessao, imagemBytes, mimeType, prompt);
    }

    @Override
    public synchronized String executarComandoTerminalChat(String idSessao, String comando) {
        if (agenteChat == null) {
            agenteChat = new GeminiAgenteOperacional();
        }
        String cmd = comando == null ? "" : comando.trim();
        String lower = cmd.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals(":ajuda") || lower.equals(":help") || lower.equals(":h")) {
            return """
                    COMANDOS DO CHAT (mesmos do ChatBotTerminal):
                    • :bandeja — Visualiza operações pendentes na bandeja de contexto
                    • :limpar / :fechar — Limpa e reinicia a janela de contexto e bandeja
                    • :logs — Exibe logs de fluxo da IA na sessão
                    • :rotas — Lista as rotas disponíveis do sistema
                    • :manual — Exibe o manual operacional da IA
                    • :persona <nome> — Altera a personalidade da IA""";
        }
        if (lower.equals(":bandeja") || lower.equals(":tray")) {
            return FormatadorSaidaIA.formatarTextoParaIA(agenteChat.obterBandeja(idSessao).formatarVisualizacao());
        }
        if (lower.equals(":limpar") || lower.equals(":cls") || lower.equals(":clear") || lower.equals(":fechar")) {
            agenteChat.fecharJanelaContexto(idSessao);
            return FormatadorSaidaIA.formatarTextoParaIA("Janela de contexto e bandeja reiniciadas limpas.");
        }
        if (lower.startsWith(":logs")) {
            String logs = agenteChat.consumirLogsFluxoSeBandejaFinalizada(idSessao);
            if (logs == null || logs.isBlank()) {
                logs = agenteChat.consumirLogsFluxoAoSair(idSessao);
            }
            return FormatadorSaidaIA.formatarTextoParaIA(
                    (logs == null || logs.isBlank()) ? "Nenhum log de fluxo pendente nesta sessão." : logs);
        }
        if (lower.equals(":rotas")) {
            StringBuilder sb = new StringBuilder("CATÁLOGO DE ROTAS (" + RotaSistema.values().length + "):\n");
            for (RotaSistema r : RotaSistema.values()) {
                sb.append("• ").append(r.getCaminhoRota()).append(" — ").append(r.getDescricaoOperacao()).append("\n");
            }
            return FormatadorSaidaIA.formatarTextoParaIA(sb.toString());
        }
        if (lower.equals(":manual")) {
            return FormatadorSaidaIA.formatarTextoParaIA(ManualInstrucoesIA.gerar());
        }
        if (lower.startsWith(":persona ")) {
            String nova = cmd.substring(9).trim();
            GeminiConfig.getInstancia().salvarPersonalidade(nova, "");
            return FormatadorSaidaIA.formatarTextoParaIA("Personalidade atualizada para: " + nova);
        }
        return null;
    }

    @Override
    public String obterProvedorIaAtivo() {
        if (agenteChat != null && agenteChat.getProvedor() != null) {
            return agenteChat.getProvedor().getId();
        }
        return ConfigIA.getInstancia().getProvedor();
    }

    @Override
    public void executarEmBackground(Runnable tarefa, Consumer<Throwable> aoErro) {
        executorVirtual.submit(() -> {
            try {
                tarefa.run();
            } catch (Throwable t) {
                if (aoErro != null) {
                    aoErro.accept(t);
                }
            }
        });
    }

    @Override
    public TabelaCsv lerCsv(Path caminho, Character delimitador) throws IOException {
        return leitorCsv.lerArquivo(caminho, delimitador);
    }

    @Override
    public TabelaCsv lerCsvTexto(String conteudo, Character delimitador) throws IOException {
        return leitorCsv.lerTexto(conteudo, delimitador);
    }

    @Override
    public ResultadoImportacaoCsv importarCsvMapeado(
            String origem,
            TabelaCsv tabela,
            TipoImportacaoCsv tipo,
            Map<String, String> mapeamentoColunas,
            boolean pararNoPrimeiroErro
    ) {
        return importadorCsv.importarTabelaMapeada(origem, tabela, tipo, mapeamentoColunas, pararNoPrimeiroErro);
    }

    @Override
    public Properties carregarPropriedades(String caminhoArquivo) {
        Properties props = new Properties();
        File arquivo = new File(caminhoArquivo);
        if (arquivo.exists()) {
            try (FileInputStream in = new FileInputStream(arquivo)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    @Override
    public void salvarPropriedades(String caminhoArquivo, Properties propriedades, String comentario) throws IOException {
        File arquivo = new File(caminhoArquivo);
        File pasta = arquivo.getParentFile();
        if (pasta != null && !pasta.exists()) {
            pasta.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(arquivo)) {
            propriedades.store(out, comentario);
        }
    }

    @Override
    public void recarregarConfiguracoesIA() {
        ConfigIA.getInstancia().carregar();
        synchronized (this) {
            this.agenteChat = null;
        }
    }
}
