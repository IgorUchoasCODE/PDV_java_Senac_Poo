package br.com.senac.integracao.ia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra e persiste em arquivo o rastreamento completo de cada turno da IA/Agente:
 * <ol>
 *   <li>Pré-processamento (entidades/atalhos locais)</li>
 *   <li>Conteúdo enviado para a IA</li>
 *   <li>Pós-processamento (tool calls, resposta bruta e bandeja)</li>
 *   <li>Retorno final entregue ao usuário</li>
 * </ol>
 * Quando ativado, grava em disco e disponibiliza a exibição automática ao finalizar a
 * {@link BandejaOperacoesContexto} ou ao encerrar a aplicação.
 */
public class LogFluxoIAContexto {

    private static final DateTimeFormatter FMT_DATA_ARQ = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public record RegistroTurno(
            LocalDateTime timestamp,
            String entradaUsuario,
            String preProcessamento,
            String enviadoParaIA,
            String posProcessamento,
            String retornoUsuario
    ) {}

    private volatile boolean ativo = false;
    private final Path diretorioLogs;
    private final Map<String, List<RegistroTurno>> registrosPorSessao = new ConcurrentHashMap<>();
    private final Set<String> sessoesComBandejaFinalizada = ConcurrentHashMap.newKeySet();

    public LogFluxoIAContexto() {
        this(Paths.get("logs"));
    }

    public LogFluxoIAContexto(Path diretorioLogs) {
        this.diretorioLogs = diretorioLogs != null ? diretorioLogs : Paths.get("logs");
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Path getArquivoLogAtual() {
        String nome = "fluxo-ia-" + LocalDate.now().format(FMT_DATA_ARQ) + ".log";
        return diretorioLogs.resolve(nome).toAbsolutePath().normalize();
    }

    /**
     * Registra um turno completo em memória (para exibição após finalização da bandeja ou saída)
     * e persiste imediatamente no arquivo de log diário quando ativo.
     */
    public void registrarTurno(
            String idSessao,
            String entradaUsuario,
            String preProcessamento,
            String enviadoParaIA,
            String posProcessamento,
            String retornoUsuario) {
        if (!ativo || idSessao == null || idSessao.isBlank()) {
            return;
        }
        RegistroTurno reg = new RegistroTurno(
                LocalDateTime.now(),
                valorOuVazio(entradaUsuario),
                valorOuVazio(preProcessamento),
                valorOuVazio(enviadoParaIA),
                valorOuVazio(posProcessamento),
                valorOuVazio(retornoUsuario)
        );
        registrosPorSessao.computeIfAbsent(idSessao, k -> Collections.synchronizedList(new ArrayList<>())).add(reg);
        gravarEmArquivo(idSessao, reg);
    }

    /**
     * Sinaliza que a {@link BandejaOperacoesContexto} da sessão foi finalizada/fechada.
     */
    public void marcarBandejaFinalizada(String idSessao) {
        if (!ativo || idSessao == null || idSessao.isBlank()) {
            return;
        }
        List<RegistroTurno> lista = registrosPorSessao.get(idSessao);
        if (lista != null && !lista.isEmpty()) {
            sessoesComBandejaFinalizada.add(idSessao);
        }
    }

    /**
     * Se a bandeja da sessão tiver sido finalizada e houver registros acumulados,
     * retorna o relatório formatado e limpa o buffer em memória dessa sessão.
     */
    public String consumirRelatorioSeBandejaFinalizada(String idSessao) {
        if (!ativo || idSessao == null || !sessoesComBandejaFinalizada.remove(idSessao)) {
            return null;
        }
        return consumirRelatorioSessao(idSessao, "FINALIZAÇÃO DA BANDEJA DE CONTEXTO");
    }

    /**
     * Retorna e limpa os registros acumulados da sessão ao sair da aplicação.
     */
    public String consumirRelatorioAoSair(String idSessao) {
        if (!ativo || idSessao == null) {
            return null;
        }
        sessoesComBandejaFinalizada.remove(idSessao);
        return consumirRelatorioSessao(idSessao, "ENCERRAMENTO DA APLICAÇÃO");
    }

    /**
     * Formata os registros atualmente em memória para a sessão sem removê-los.
     */
    public String visualizarRelatorioAtual(String idSessao) {
        if (idSessao == null) return null;
        List<RegistroTurno> lista = registrosPorSessao.get(idSessao);
        if (lista == null || lista.isEmpty()) {
            return null;
        }
        synchronized (lista) {
            return formatarLista(idSessao, new ArrayList<>(lista), "VISUALIZAÇÃO SOB DEMANDA");
        }
    }

    private String consumirRelatorioSessao(String idSessao, String gatilho) {
        List<RegistroTurno> lista = registrosPorSessao.remove(idSessao);
        if (lista == null || lista.isEmpty()) {
            return null;
        }
        List<RegistroTurno> copia;
        synchronized (lista) {
            copia = new ArrayList<>(lista);
        }
        return formatarLista(idSessao, copia, gatilho);
    }

    private String formatarLista(String idSessao, List<RegistroTurno> registros, String gatilho) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("📋 LOG DO FLUXO IA (").append(gatilho).append(")\n");
        sb.append("Sessão: ").append(idSessao).append(" | Turnos: ").append(registros.size()).append("\n");
        sb.append("Arquivo salvo: ").append(getArquivoLogAtual()).append("\n");
        sb.append("================================================================================\n");
        for (int i = 0; i < registros.size(); i++) {
            RegistroTurno r = registros.get(i);
            sb.append(formatarTurno(i + 1, r));
        }
        return sb.toString().trim();
    }

    private String formatarTurno(int numero, RegistroTurno r) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- [Turno #").append(numero).append(" | ").append(r.timestamp().format(FMT_HORA)).append("] ---\n");
        sb.append("Entrada do usuário:\n  ").append(identar(r.entradaUsuario())).append("\n");
        sb.append("1) Pré-processamento:\n  ").append(identar(r.preProcessamento())).append("\n");
        sb.append("2) Enviado para a IA:\n  ").append(identar(r.enviadoParaIA())).append("\n");
        sb.append("3) Pós-processamento:\n  ").append(identar(r.posProcessamento())).append("\n");
        sb.append("4) Retorno para o usuário:\n  ").append(identar(r.retornoUsuario())).append("\n");
        return sb.toString();
    }

    private void gravarEmArquivo(String idSessao, RegistroTurno r) {
        try {
            Files.createDirectories(diretorioLogs);
            Path arquivo = getArquivoLogAtual();
            String bloco = "[Sessão: " + idSessao + "]" + formatarTurno(
                    registrosPorSessao.getOrDefault(idSessao, List.of()).size(), r) + "\n";
            Files.writeString(
                    arquivo,
                    bloco,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
        }
    }

    private static String identar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "(vazio)";
        }
        return texto.trim().replace("\n", "\n  ");
    }

    private static String valorOuVazio(String s) {
        return (s == null || s.isBlank()) ? "(vazio)" : s.trim();
    }
}
