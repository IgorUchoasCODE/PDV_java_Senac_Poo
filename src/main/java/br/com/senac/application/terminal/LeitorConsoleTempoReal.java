package br.com.senac.application.terminal;

import br.com.senac.application.terminal.EditorTerminalInterativo.EventoTecla;
import br.com.senac.application.terminal.EditorTerminalInterativo.TipoTecla;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Implementação de {@link LeitorConsole} para console TTY real (Windows PowerShell / Unix stty),
 * garantindo leitura tecla a tecla sem dependências externas.
 */
public class LeitorConsoleTempoReal implements LeitorConsole {

    private final Process processo;
    private final BufferedReader leitor;
    private final boolean modoUnixRaw;
    private final EditorTerminalInterativo editor;
    private final LeitorConsoleFallback fallbackLargura;
    private final Deque<EventoTecla> eventosPendentes = new ArrayDeque<>();
    private volatile boolean ativa;
    private volatile int ultimaLarguraConhecida = 0;
    private boolean ultimoFoiCr = false;

    private LeitorConsoleTempoReal(
            Process processo,
            BufferedReader leitor,
            boolean modoUnixRaw,
            EditorTerminalInterativo editor,
            LeitorConsoleFallback fallbackLargura) {
        this.processo = processo;
        this.leitor = leitor;
        this.modoUnixRaw = modoUnixRaw;
        this.editor = editor;
        this.fallbackLargura = fallbackLargura;
        this.ativa = true;
    }

    public static LeitorConsole abrirOuFallback(AnalisadorTextoTerminal analisador, LeitorConsoleFallback fallback) {
        if (System.console() == null) {
            return fallback;
        }
        EditorTerminalInterativo editor = new EditorTerminalInterativo(analisador);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        LeitorConsoleTempoReal tty = os.contains("win")
                ? abrirWindows(editor, fallback)
                : abrirUnix(editor, fallback);
        return tty != null ? tty : fallback;
    }

    private static LeitorConsoleTempoReal abrirWindows(EditorTerminalInterativo editor, LeitorConsoleFallback fallback) {
        String psScript = String.join("; ",
                "[Console]::InputEncoding = [System.Text.Encoding]::UTF8",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
                "if ([Console]::IsInputRedirected) { [Console]::Out.WriteLine('NO_TTY'); [Console]::Out.Flush(); exit 2 }",
                "while ($true) {",
                "  $k = [Console]::ReadKey($true)",
                "  $w = try { [Console]::WindowWidth } catch { 100 }",
                "  $ch = [int][char]$k.KeyChar",
                "  $key = [int]$k.Key",
                "  $mod = [int]$k.Modifiers",
                "  $avail = if ([Console]::KeyAvailable) { 1 } else { 0 }",
                "  [Console]::Out.WriteLine(\"K:$key`:$ch`:$mod`:$w`:$avail\")",
                "  [Console]::Out.Flush()",
                "}"
        );
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
            Thread.sleep(60);
            if (!proc.isAlive() || (br.ready() && "NO_TTY".equals(br.readLine()))) {
                proc.destroyForcibly();
                return null;
            }
            return new LeitorConsoleTempoReal(proc, br, false, editor, fallback);
        } catch (Exception e) {
            return null;
        }
    }

    private static LeitorConsoleTempoReal abrirUnix(EditorTerminalInterativo editor, LeitorConsoleFallback fallback) {
        try {
            new ProcessBuilder("sh", "-c", "stty -icanon -echo min 1 </dev/tty")
                    .inheritIO().start().waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            return new LeitorConsoleTempoReal(null, br, true, editor, fallback);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isTempoReal() {
        return ativa && (processo == null || processo.isAlive());
    }

    @Override
    public int detectarLarguraAtual() {
        if (ultimaLarguraConhecida >= 48) {
            return ultimaLarguraConhecida;
        }
        return fallbackLargura.detectarLarguraAtual();
    }

    @Override
    public String lerEntrada(String textoInicial) {
        if (!isTempoReal()) {
            return null;
        }
        return editor.editar(textoInicial, detectarLarguraAtual(), this::obterProximoEvento);
    }

    @Override
    public String lerLinhaSimples() {
        StringBuilder sb = new StringBuilder();
        while (isTempoReal()) {
            EventoTecla ev = obterProximoEvento();
            if (ev == null) return sb.toString().trim();
            switch (ev.tipo()) {
                case ENTER -> {
                    System.out.println();
                    return sb.toString().trim();
                }
                case BACKSPACE -> {
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                }
                case CHAR -> {
                    sb.append(ev.caractere());
                    System.out.print(ev.caractere());
                    System.out.flush();
                }
                case ESC -> {
                    System.out.println();
                    return "x";
                }
                default -> {
                }
            }
        }
        return sb.toString().trim();
    }

    @Override
    public boolean detectarCancelamentoNaoBloqueante() {
        try {
            while (leitor.ready()) {
                EventoTecla ev = lerProximaTecla();
                if (ev == null) return false;
                boolean ehEscOuX = ev.tipo() == TipoTecla.ESC
                        || (ev.tipo() == TipoTecla.CHAR && Character.toLowerCase(ev.caractere()) == 'x');
                if (ehEscOuX && !ev.emRajada()) {
                    eventosPendentes.clear();
                    return true;
                }
                if (ev.tipo() != TipoTecla.WINDOW_ONLY) {
                    eventosPendentes.addLast(ev);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void descartarEntradaPendente() {
        eventosPendentes.clear();
        try {
            while (leitor.ready()) {
                lerProximaTecla();
            }
        } catch (Exception ignored) {
        }
    }

    private EventoTecla obterProximoEvento() {
        if (!eventosPendentes.isEmpty()) {
            return eventosPendentes.pollFirst();
        }
        return lerProximaTecla();
    }

    private EventoTecla lerProximaTecla() {
        if (!isTempoReal()) return null;
        if (modoUnixRaw) return lerTeclaUnix();
        try {
            while (true) {
                String linha = leitor.readLine();
                if (linha == null || "NO_TTY".equals(linha)) {
                    ativa = false;
                    return null;
                }
                if (!linha.startsWith("K:")) continue;
                String[] p = linha.split(":");
                if (p.length < 5) continue;
                int key = Integer.parseInt(p[1]);
                int ch = Integer.parseInt(p[2]);
                int mod = Integer.parseInt(p[3]);
                int width = Integer.parseInt(p[4]);
                boolean avail = p.length >= 6 && "1".equals(p[5]);
                if (width > 0) {
                    ultimaLarguraConhecida = Math.max(48, Math.min(width, 220));
                }
                boolean ctrl = (mod & 4) != 0;
                boolean shift = (mod & 2) != 0;
                boolean emRajada = avail || leitor.ready();

                if (key == 13) {
                    ultimoFoiCr = true;
                    return new EventoTecla(TipoTecla.ENTER, '\0', ctrl, shift, emRajada, width);
                }
                if (ch == 10) {
                    if (!ultimoFoiCr) {
                        return new EventoTecla(TipoTecla.CHAR, '\n', ctrl, shift, emRajada, width);
                    }
                    ultimoFoiCr = false;
                    return new EventoTecla(TipoTecla.WINDOW_ONLY, '\0', ctrl, shift, emRajada, width);
                }
                ultimoFoiCr = false;

                return switch (key) {
                    case 8 -> new EventoTecla(TipoTecla.BACKSPACE, '\0', ctrl, shift, emRajada, width);
                    case 9 -> new EventoTecla(TipoTecla.TAB, '\0', ctrl, shift, emRajada, width);
                    case 27 -> new EventoTecla(TipoTecla.ESC, '\0', ctrl, shift, emRajada, width);
                    case 35 -> new EventoTecla(TipoTecla.END, '\0', ctrl, shift, emRajada, width);
                    case 36 -> new EventoTecla(TipoTecla.HOME, '\0', ctrl, shift, emRajada, width);
                    case 37 -> new EventoTecla(TipoTecla.LEFT, '\0', ctrl, shift, emRajada, width);
                    case 38 -> new EventoTecla(TipoTecla.UP, '\0', ctrl, shift, emRajada, width);
                    case 39 -> new EventoTecla(TipoTecla.RIGHT, '\0', ctrl, shift, emRajada, width);
                    case 40 -> new EventoTecla(TipoTecla.DOWN, '\0', ctrl, shift, emRajada, width);
                    case 46 -> new EventoTecla(TipoTecla.DELETE, '\0', ctrl, shift, emRajada, width);
                    default -> ch >= 32
                            ? new EventoTecla(TipoTecla.CHAR, (char) ch, ctrl, shift, emRajada, width)
                            : new EventoTecla(TipoTecla.WINDOW_ONLY, '\0', ctrl, shift, emRajada, width);
                };
            }
        } catch (Exception e) {
            ativa = false;
            return null;
        }
    }

    private EventoTecla lerTeclaUnix() {
        try {
            int c = leitor.read();
            if (c < 0) {
                ativa = false;
                return null;
            }
            boolean emRajada = leitor.ready();
            if (c == '\r') {
                ultimoFoiCr = true;
                return new EventoTecla(TipoTecla.ENTER, '\0', false, false, emRajada, 0);
            }
            if (c == '\n') {
                if (!ultimoFoiCr) {
                    return new EventoTecla(TipoTecla.ENTER, '\0', false, false, emRajada, 0);
                }
                ultimoFoiCr = false;
                return new EventoTecla(TipoTecla.WINDOW_ONLY, '\0', false, false, emRajada, 0);
            }
            ultimoFoiCr = false;
            if (c == 127 || c == 8) return new EventoTecla(TipoTecla.BACKSPACE, '\0', false, false, emRajada, 0);
            if (c == '\t') return new EventoTecla(TipoTecla.TAB, '\0', false, false, emRajada, 0);
            if (c == 27) {
                if (leitor.ready()) {
                    int c2 = leitor.read();
                    if (c2 == '[' && leitor.ready()) {
                        int c3 = leitor.read();
                        boolean rajadaSeq = leitor.ready();
                        return switch (c3) {
                            case 'A' -> new EventoTecla(TipoTecla.UP, '\0', false, false, rajadaSeq, 0);
                            case 'B' -> new EventoTecla(TipoTecla.DOWN, '\0', false, false, rajadaSeq, 0);
                            case 'C' -> new EventoTecla(TipoTecla.RIGHT, '\0', false, false, rajadaSeq, 0);
                            case 'D' -> new EventoTecla(TipoTecla.LEFT, '\0', false, false, rajadaSeq, 0);
                            case 'H' -> new EventoTecla(TipoTecla.HOME, '\0', false, false, rajadaSeq, 0);
                            case 'F' -> new EventoTecla(TipoTecla.END, '\0', false, false, rajadaSeq, 0);
                            default -> new EventoTecla(TipoTecla.WINDOW_ONLY, '\0', false, false, rajadaSeq, 0);
                        };
                    }
                }
                return new EventoTecla(TipoTecla.ESC, '\0', false, false, emRajada, 0);
            }
            if (c >= 32) return new EventoTecla(TipoTecla.CHAR, (char) c, false, false, emRajada, 0);
            return new EventoTecla(TipoTecla.WINDOW_ONLY, '\0', false, false, emRajada, 0);
        } catch (IOException e) {
            ativa = false;
            return null;
        }
    }

    @Override
    public void close() {
        ativa = false;
        if (processo != null) {
            processo.destroyForcibly();
        }
        if (modoUnixRaw) {
            try {
                new ProcessBuilder("sh", "-c", "stty sane </dev/tty").inheritIO().start().waitFor();
            } catch (Exception ignored) {
            }
        }
    }
}
