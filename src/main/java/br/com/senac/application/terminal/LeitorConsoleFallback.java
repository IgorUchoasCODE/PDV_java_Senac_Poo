package br.com.senac.application.terminal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static br.com.senac.application.terminal.ModelosAnaliseTerminal.*;

/**
 * Implementação de {@link LeitorConsole} baseada em {@link Scanner} para execução direta em IDEs,
 * pipes ou ambientes sem console TTY interativo.
 */
public class LeitorConsoleFallback implements LeitorConsole {

    private static final int LARGURA_PADRAO = 100;
    private static final int LARGURA_MIN = 48;
    private static final int LARGURA_MAX = 220;
    private static final long CACHE_MS = 2000L;
    private static final Pattern PADRAO_COLUNAS_WIN = Pattern.compile("(?i)(?:Colunas|Columns)\\s*:\\s*(\\d+)");

    private final Scanner scanner;
    private volatile int larguraCache = LARGURA_PADRAO;
    private volatile long ultimaLeituraMs = 0L;

    public LeitorConsoleFallback(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public boolean isTempoReal() {
        return false;
    }

    @Override
    public int detectarLarguraAtual() {
        long agora = System.currentTimeMillis();
        if (ultimaLeituraMs > 0 && (agora - ultimaLeituraMs) < CACHE_MS) {
            return larguraCache;
        }
        int detectada = lerLarguraSistema();
        larguraCache = Math.max(LARGURA_MIN, Math.min(detectada, LARGURA_MAX));
        ultimaLeituraMs = agora;
        return larguraCache;
    }

    @Override
    public String lerEntrada(String textoInicial) {
        if (textoInicial != null && !textoInicial.isBlank()) {
            System.out.println(CINZA + "  Texto atual: " + RESET + textoInicial);
            System.out.print(CINZA + "  Edite (digite o novo texto ou ENTER para manter): " + RESET);
            System.out.flush();
            if (!scanner.hasNextLine()) return null;
            String novo = scanner.nextLine().replace("\uFEFF", "").trim();
            return novo.isEmpty() ? textoInicial.trim() : novo;
        }

        System.out.print("\n" + NEGRITO + VERDE + FormatadorEntradaTerminal.PROMPT_INICIAL + RESET);
        System.out.flush();
        if (!scanner.hasNextLine()) {
            return null;
        }

        String primeiraLinha = scanner.nextLine().replace("\uFEFF", "");
        String aparada = primeiraLinha.trim();
        if (aparada.isEmpty()) {
            return "";
        }

        if (aparada.startsWith("\"\"\"")) {
            return lerBlocoMultilinha(primeiraLinha);
        }

        if (!aparada.startsWith(":") && (aparada.endsWith("\\") || saldoColchetesAberto(aparada) > 0)) {
            return lerContinuacaoAutomatica(primeiraLinha);
        }

        return aparada;
    }

    @Override
    public String lerLinhaSimples() {
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    @Override
    public boolean detectarCancelamentoNaoBloqueante() {
        try {
            if (System.in.available() > 0 && scanner.hasNextLine()) {
                String cmd = scanner.nextLine().trim();
                return cmd.equalsIgnoreCase("x")
                        || cmd.equalsIgnoreCase("c")
                        || cmd.equalsIgnoreCase("cancelar");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void descartarEntradaPendente() {
        try {
            while (System.in.available() > 0 && scanner.hasNextLine()) {
                scanner.nextLine();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        // Scanner gerenciado pelo chamador
    }

    private String lerBlocoMultilinha(String primeiraLinha) {
        StringBuilder sb = new StringBuilder();
        String semAbertura = primeiraLinha.trim().substring(3).trim();
        if (semAbertura.endsWith("\"\"\"") && semAbertura.length() >= 3) {
            return semAbertura.substring(0, semAbertura.length() - 3).trim();
        }
        if (!semAbertura.isEmpty()) {
            sb.append(semAbertura);
        }

        System.out.println(CINZA + "  (Modo multilinha ativo: digite \"\"\" para finalizar)" + RESET);
        while (true) {
            System.out.print(CINZA + "  ... > " + RESET);
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            String linha = scanner.nextLine();
            String trim = linha.trim();
            if (trim.equals("\"\"\"")) break;
            if (trim.endsWith("\"\"\"")) {
                String trecho = trim.substring(0, trim.length() - 3).trim();
                if (!trecho.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(trecho);
                }
                break;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(linha);
        }
        return sb.toString().trim();
    }

    private String lerContinuacaoAutomatica(String primeiraLinha) {
        StringBuilder sb = new StringBuilder();
        String atual = primeiraLinha;
        int saldoColchetes = 0;

        while (true) {
            String trim = atual.trim();
            boolean continuaPorBarra = trim.endsWith("\\");
            String trecho = continuaPorBarra ? trim.substring(0, trim.length() - 1).trim() : trim;

            if (!trecho.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(trecho);
            }

            saldoColchetes += saldoColchetesAberto(trecho);
            if (!continuaPorBarra && saldoColchetes <= 0) break;

            System.out.print(CINZA + "  ... > " + RESET);
            System.out.flush();
            if (!scanner.hasNextLine()) break;
            atual = scanner.nextLine();
            String atualTrim = atual.trim();
            if (atualTrim.equalsIgnoreCase(":cancelar")) return "";
            if (atualTrim.isEmpty() && !continuaPorBarra) break;
        }
        return sb.toString().trim();
    }

    private static int saldoColchetesAberto(String texto) {
        int saldo = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') saldo++;
            else if (c == ']') saldo--;
        }
        return saldo;
    }

    private static int lerLarguraSistema() {
        Integer prop = parsearPositivo(System.getProperty("terminal.width"));
        if (prop != null) return prop;
        Integer env = parsearPositivo(System.getenv("COLUMNS"));
        if (env != null) return env;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            Integer win = lerLarguraWindowsModeCon();
            if (win != null) return win;
        } else {
            Integer unix = lerLarguraUnixTput();
            if (unix != null) return unix;
        }
        return LARGURA_PADRAO;
    }

    private static Integer lerLarguraWindowsModeCon() {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "mode", "con").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = br.readLine()) != null) {
                    Matcher m = PADRAO_COLUNAS_WIN.matcher(linha);
                    if (m.find()) return parsearPositivo(m.group(1));
                }
            }
            p.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Integer lerLarguraUnixTput() {
        try {
            Process p = new ProcessBuilder("sh", "-c", "tput cols 2>/dev/null || stty size 2>/dev/null | awk '{print $2}'")
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                Integer val = parsearPositivo(br.readLine());
                if (val != null) return val;
            }
            p.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Integer parsearPositivo(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
