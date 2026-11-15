package br.com.senac.application.terminal;

import br.com.senac.application.terminal.ModelosAnaliseTerminal.LayoutVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.LinhaVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.ResultadoAnaliseTerminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static br.com.senac.application.terminal.ModelosAnaliseTerminal.*;

/**
 * Responsabilidade única (SRP): coordenar a edição interativa no terminal em tempo real
 * (quebra por largura, coloração de entidades, dicas ao vivo, histórico e autocompletar {@code TAB}).
 */
public class EditorTerminalInterativo {

    public enum TipoTecla {
        CHAR, BACKSPACE, DELETE, ENTER, TAB, LEFT, RIGHT, UP, DOWN, HOME, END, ESC, WINDOW_ONLY
    }

    public record EventoTecla(
            TipoTecla tipo,
            char caractere,
            boolean ctrl,
            boolean shift,
            boolean emRajada,
            int larguraJanela) {
        public EventoTecla(TipoTecla tipo, char caractere, boolean ctrl, int larguraJanela) {
            this(tipo, caractere, ctrl, false, false, larguraJanela);
        }
    }

    private final AnalisadorTextoTerminal analisador;
    private final List<String> historico = new ArrayList<>();

    public EditorTerminalInterativo(AnalisadorTextoTerminal analisador) {
        this.analisador = analisador;
    }

    public String editar(
            String textoInicial,
            int larguraInicial,
            Supplier<EventoTecla> proximoEvento) {
        StringBuilder buffer = new StringBuilder(textoInicial != null ? textoInicial : "");
        int cursor = buffer.length();
        int indiceHistorico = historico.size();
        String rascunhoAtual = "";
        int largura = larguraInicial;
        int linhaCursorAnterior = desenhar(buffer.toString(), cursor, largura, 0, true);

        while (true) {
            EventoTecla ev = proximoEvento.get();
            if (ev == null) {
                return null;
            }
            if (ev.larguraJanela() > 0) {
                largura = Math.max(48, Math.min(ev.larguraJanela(), 220));
            }

            switch (ev.tipo()) {
                case ENTER -> {
                    String atual = buffer.toString();
                    String trim = atual.trim();
                    boolean continua = ev.emRajada()
                            || (ev.shift() && !ev.ctrl())
                            || trim.endsWith("\\")
                            || estaEmBlocoAspasTriplasAberto(trim)
                            || (!trim.startsWith(":") && contarColchetesAbertos(trim) > 0 && !linhaAtualEhVazia(atual, cursor));
                    if (continua && (ev.emRajada() || !ev.ctrl())) {
                        buffer.insert(cursor, '\n');
                        cursor++;
                        break;
                    }
                    String resultado = normalizarSubmissaoMultilinha(atual);
                    finalizarDesenho(resultado, largura, linhaCursorAnterior);
                    if (!resultado.isEmpty()) {
                        historico.add(resultado);
                    }
                    return resultado;
                }
                case CHAR -> {
                    char ch = ev.caractere() == '\r' ? '\n' : ev.caractere();
                    buffer.insert(cursor, ch);
                    cursor++;
                }
                case BACKSPACE -> {
                    if (cursor > 0) {
                        buffer.deleteCharAt(cursor - 1);
                        cursor--;
                    }
                }
                case DELETE -> {
                    if (cursor < buffer.length()) {
                        buffer.deleteCharAt(cursor);
                    }
                }
                case LEFT -> {
                    if (cursor > 0) cursor--;
                }
                case RIGHT -> {
                    if (cursor < buffer.length()) cursor++;
                }
                case HOME -> cursor = 0;
                case END -> cursor = buffer.length();
                case UP -> {
                    if (!historico.isEmpty() && indiceHistorico > 0) {
                        if (indiceHistorico == historico.size()) {
                            rascunhoAtual = buffer.toString();
                        }
                        indiceHistorico--;
                        buffer.setLength(0);
                        buffer.append(historico.get(indiceHistorico));
                        cursor = buffer.length();
                    }
                }
                case DOWN -> {
                    if (indiceHistorico < historico.size()) {
                        indiceHistorico++;
                        buffer.setLength(0);
                        buffer.append(indiceHistorico == historico.size() ? rascunhoAtual : historico.get(indiceHistorico));
                        cursor = buffer.length();
                    }
                }
                case TAB -> {
                    ResultadoAnaliseTerminal analise = analisador.analisar(buffer.toString(), cursor);
                    if (analise.complementoTab() != null) {
                        int ini = Math.max(0, Math.min(analise.inicioSubstituicaoTab(), cursor));
                        buffer.replace(ini, cursor, analise.complementoTab());
                        cursor = ini + analise.complementoTab().length();
                    }
                }
                case ESC -> {
                    buffer.setLength(0);
                    cursor = 0;
                }
                case WINDOW_ONLY -> {
                }
            }

            if (!ev.emRajada()) {
                linhaCursorAnterior = desenhar(buffer.toString(), cursor, largura, linhaCursorAnterior, false);
            }
        }
    }

    private int desenhar(
            String texto,
            int cursor,
            int larguraTerminal,
            int linhaCursorAnterior,
            boolean primeiraVez) {
        ResultadoAnaliseTerminal analise = analisador.analisar(texto, cursor);
        LayoutVisual layout = FormatadorEntradaTerminal.calcularLayoutVisual(texto, cursor, larguraTerminal);

        StringBuilder out = new StringBuilder();
        if (primeiraVez) {
            out.append('\n');
        } else {
            if (linhaCursorAnterior > 0) {
                out.append("\u001B[").append(linhaCursorAnterior).append('A');
            }
            out.append("\r\u001B[J");
        }

        List<LinhaVisual> linhas = layout.linhas();
        for (int i = 0; i < linhas.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(i == 0
                    ? (NEGRITO + VERDE + FormatadorEntradaTerminal.PROMPT_INICIAL + RESET)
                    : FormatadorEntradaTerminal.PROMPT_CONT);
            LinhaVisual lv = linhas.get(i);
            out.append(FormatadorEntradaTerminal.colorirTrechoPreservandoLargura(
                    texto, lv.inicioTexto(), lv.fimTexto(), analise.trechos()));
        }

        int linhasImpressas = linhas.size();
        int maxHint = Math.max(30, larguraTerminal - 3);

        if (!analise.resumoIdentificacao().isBlank()) {
            out.append('\n').append(ROXO).append(truncar("  " + analise.resumoIdentificacao(), maxHint)).append(RESET);
            linhasImpressas++;
        }
        if (!analise.dicaLinha().isBlank()) {
            out.append('\n').append(CINZA).append(truncar("  " + analise.dicaLinha(), maxHint)).append(RESET);
            linhasImpressas++;
        }

        int subir = (linhasImpressas - 1) - layout.linhaCursor();
        if (subir > 0) {
            out.append("\u001B[").append(subir).append('A');
        }
        out.append('\r');
        if (layout.colunaCursor() > 0) {
            out.append("\u001B[").append(layout.colunaCursor()).append('C');
        }

        System.out.print(out);
        System.out.flush();
        return layout.linhaCursor();
    }

    private void finalizarDesenho(String texto, int larguraTerminal, int linhaCursorAnterior) {
        ResultadoAnaliseTerminal analise = analisador.analisar(texto, texto.length());
        LayoutVisual layout = FormatadorEntradaTerminal.calcularLayoutVisual(texto, texto.length(), larguraTerminal);

        StringBuilder out = new StringBuilder();
        if (linhaCursorAnterior > 0) {
            out.append("\u001B[").append(linhaCursorAnterior).append('A');
        }
        out.append("\r\u001B[J");

        List<LinhaVisual> linhas = layout.linhas();
        for (int i = 0; i < linhas.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(i == 0
                    ? (NEGRITO + VERDE + FormatadorEntradaTerminal.PROMPT_INICIAL + RESET)
                    : FormatadorEntradaTerminal.PROMPT_CONT);
            LinhaVisual lv = linhas.get(i);
            out.append(FormatadorEntradaTerminal.colorirTrechoPreservandoLargura(
                    texto, lv.inicioTexto(), lv.fimTexto(), analise.trechos()));
        }
        out.append('\n');
        System.out.print(out);
        System.out.flush();
    }

    private static int contarColchetesAbertos(String texto) {
        int saldo = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') saldo++;
            else if (c == ']') saldo--;
        }
        return saldo;
    }

    private static boolean estaEmBlocoAspasTriplasAberto(String trim) {
        if (!trim.startsWith("\"\"\"")) return false;
        if (trim.length() <= 3) return true;
        return !trim.endsWith("\"\"\"");
    }

    private static boolean linhaAtualEhVazia(String texto, int cursor) {
        int ini = texto.lastIndexOf('\n', Math.max(0, cursor - 1));
        return texto.substring(ini < 0 ? 0 : ini + 1, cursor).trim().isEmpty();
    }

    private static String normalizarSubmissaoMultilinha(String texto) {
        String trim = texto != null ? texto.trim() : "";
        if (trim.startsWith("\"\"\"") && trim.endsWith("\"\"\"") && trim.length() >= 6) {
            trim = trim.substring(3, trim.length() - 3).trim();
        }
        return trim.replaceAll("[ \\t]*\\\\\\r?\\n", "\n").trim();
    }

    private static String truncar(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        if (max <= 3) return s.substring(0, Math.max(0, max));
        return s.substring(0, max - 3) + "...";
    }
}
