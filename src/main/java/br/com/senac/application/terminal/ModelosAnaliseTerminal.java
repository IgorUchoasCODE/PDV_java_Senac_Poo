package br.com.senac.application.terminal;

import java.util.List;

/**
 * Modelos imutáveis compartilhados entre formatador, analisadores e gerador de dicas do terminal.
 */
public final class ModelosAnaliseTerminal {

    private ModelosAnaliseTerminal() {
    }

    public static final String RESET = "\u001B[0m";
    public static final String CIANO = "\u001B[36m";
    public static final String VERDE = "\u001B[32m";
    public static final String VERDE_CLARO = "\u001B[92m";
    public static final String AMARELO = "\u001B[33m";
    public static final String VERMELHO = "\u001B[31m";
    public static final String AZUL = "\u001B[34m";
    public static final String ROXO = "\u001B[35m";
    public static final String CINZA = "\u001B[90m";
    public static final String NEGRITO = "\u001B[1m";

    public record TrechoDestacado(
            int inicio,
            int fim,
            String corAnsi,
            String categoria,
            String resumoItem
    ) {}

    public record LinhaVisual(int inicioTexto, int fimTexto, String texto) {}

    public record LayoutVisual(List<LinhaVisual> linhas, int linhaCursor, int colunaCursor) {}

    public record ResultadoAnaliseTerminal(
            boolean ehDsl,
            boolean ehComandoTerminal,
            List<TrechoDestacado> trechos,
            List<String> entidadesIdentificadas,
            String resumoIdentificacao,
            String dicaLinha,
            String complementoTab,
            int inicioSubstituicaoTab
    ) {}
}
