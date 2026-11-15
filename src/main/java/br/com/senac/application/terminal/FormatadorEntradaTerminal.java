package br.com.senac.application.terminal;

import br.com.senac.application.terminal.ModelosAnaliseTerminal.LayoutVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.LinhaVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.TrechoDestacado;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Responsabilidade única (SRP): cálculo de quebra automática de linha por palavra pela largura do terminal
 * e renderização ANSI preservando exatamente as posições visuais do cursor.
 */
public final class FormatadorEntradaTerminal {

    public static final String PROMPT_INICIAL = "Você > ";
    public static final String PROMPT_CONT = "       ";
    public static final int LARGURA_PREFIXO = PROMPT_INICIAL.length();

    private FormatadorEntradaTerminal() {
    }

    public static LayoutVisual calcularLayoutVisual(String texto, int cursor, int larguraTerminal) {
        String safe = texto != null ? texto : "";
        int posCursor = Math.max(0, Math.min(cursor, safe.length()));
        int larguraUtil = Math.max(20, larguraTerminal - LARGURA_PREFIXO - 2);

        List<LinhaVisual> linhas = new ArrayList<>();
        int idx = 0;
        int n = safe.length();

        while (idx <= n) {
            int proxQuebra = safe.indexOf('\n', idx);
            int fimParagrafo = proxQuebra >= 0 ? proxQuebra : n;

            if (idx == fimParagrafo) {
                linhas.add(new LinhaVisual(idx, idx, ""));
            } else {
                int inicioLinha = idx;
                while (inicioLinha < fimParagrafo) {
                    int limite = Math.min(fimParagrafo, inicioLinha + larguraUtil);
                    if (limite == fimParagrafo) {
                        linhas.add(new LinhaVisual(inicioLinha, fimParagrafo, safe.substring(inicioLinha, fimParagrafo)));
                        break;
                    }
                    int ultimoEspaco = -1;
                    for (int k = limite; k > inicioLinha; k--) {
                        char c = safe.charAt(k - 1);
                        if (c == ' ' || c == '\t') {
                            ultimoEspaco = k;
                            break;
                        }
                    }
                    if (ultimoEspaco > inicioLinha) {
                        linhas.add(new LinhaVisual(inicioLinha, ultimoEspaco, safe.substring(inicioLinha, ultimoEspaco)));
                        inicioLinha = ultimoEspaco;
                    } else {
                        linhas.add(new LinhaVisual(inicioLinha, limite, safe.substring(inicioLinha, limite)));
                        inicioLinha = limite;
                    }
                }
            }

            if (proxQuebra < 0) {
                break;
            }
            idx = proxQuebra + 1;
            if (idx == n) {
                linhas.add(new LinhaVisual(n, n, ""));
                break;
            }
        }

        if (linhas.isEmpty()) {
            linhas.add(new LinhaVisual(0, 0, ""));
        }

        int linhaCursor = linhas.size() - 1;
        int colCursor = LARGURA_PREFIXO + linhas.get(linhaCursor).texto().length();

        for (int i = 0; i < linhas.size(); i++) {
            LinhaVisual lv = linhas.get(i);
            boolean ultimo = (i == linhas.size() - 1);
            if (posCursor >= lv.inicioTexto() && (posCursor < lv.fimTexto() || (ultimo && posCursor <= lv.fimTexto()))) {
                linhaCursor = i;
                colCursor = LARGURA_PREFIXO + (posCursor - lv.inicioTexto());
                break;
            }
            if (posCursor == lv.fimTexto() && lv.fimTexto() < safe.length() && safe.charAt(lv.fimTexto()) == '\n') {
                linhaCursor = i;
                colCursor = LARGURA_PREFIXO + lv.texto().length();
                break;
            }
        }

        return new LayoutVisual(linhas, linhaCursor, colCursor);
    }

    public static String colorirTrechoPreservandoLargura(
            String textoCompleto,
            int inicioLinha,
            int fimLinha,
            List<TrechoDestacado> trechos) {
        if (inicioLinha >= fimLinha) {
            return "";
        }
        if (trechos == null || trechos.isEmpty()) {
            return textoCompleto.substring(inicioLinha, fimLinha);
        }

        List<TrechoDestacado> ordenados = new ArrayList<>(trechos);
        ordenados.sort(Comparator.comparingInt(TrechoDestacado::inicio));

        StringBuilder sb = new StringBuilder();
        int cursor = inicioLinha;
        for (TrechoDestacado t : ordenados) {
            if (t.fim() <= inicioLinha || t.inicio() >= fimLinha) {
                continue;
            }
            int ini = Math.max(inicioLinha, t.inicio());
            int fim = Math.min(fimLinha, t.fim());
            if (ini < cursor) {
                ini = cursor;
            }
            if (ini >= fim) {
                continue;
            }
            if (ini > cursor) {
                sb.append(textoCompleto, cursor, ini);
            }
            sb.append(t.corAnsi()).append(textoCompleto, ini, fim).append(ModelosAnaliseTerminal.RESET);
            cursor = fim;
        }
        if (cursor < fimLinha) {
            sb.append(textoCompleto, cursor, fimLinha);
        }
        return sb.toString();
    }
}
