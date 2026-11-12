package br.com.senac.integracao.ia.parser;

/**
 * Exceção lançada quando a validação atômica de sintaxe da DSL {@code $$} encontra um erro.
 *
 * <p>Contém posição exata (linha, coluna, índice), recorte do trecho com indicador visual {@code ^}
 * e sugestão amigável de correção em linguagem natural para que o usuário corrija sem precisar
 * de conhecimento técnico avançado.
 */
public class ErroSintaxe extends RuntimeException {

    private final int posicao;
    private final int linha;
    private final int coluna;
    private final String trecho;
    private final String sugestao;

    public ErroSintaxe(String mensagem, int posicao, int linha, int coluna, String trecho, String sugestao) {
        super(mensagem);
        this.posicao = posicao;
        this.linha = linha;
        this.coluna = coluna;
        this.trecho = trecho;
        this.sugestao = sugestao;
    }

    public static ErroSintaxe criar(String textoCompleto, int posicao, String mensagem, String sugestao) {
        if (textoCompleto == null) textoCompleto = "";
        int posAjustada = Math.max(0, Math.min(posicao, textoCompleto.length()));

        int linha = 1;
        int coluna = 1;
        int inicioLinha = 0;
        int fimLinha = textoCompleto.length();

        for (int i = 0; i < posAjustada; i++) {
            char c = textoCompleto.charAt(i);
            if (c == '\n') {
                linha++;
                coluna = 1;
                inicioLinha = i + 1;
            } else {
                coluna++;
            }
        }

        int proximoNl = textoCompleto.indexOf('\n', posAjustada);
        if (proximoNl != -1) {
            fimLinha = proximoNl;
        }

        String linhaTexto = textoCompleto.substring(inicioLinha, fimLinha);
        StringBuilder indicador = new StringBuilder();
        int espacosAteErro = Math.max(0, coluna - 1);
        indicador.append(" ".repeat(Math.min(espacosAteErro, 120))).append("^");

        String trechoFormatado = String.format("   %s%n   %s", linhaTexto, indicador);

        return new ErroSintaxe(mensagem, posAjustada, linha, coluna, trechoFormatado, sugestao);
    }

    public int getPosicao() {
        return posicao;
    }

    public int getLinha() {
        return linha;
    }

    public int getColuna() {
        return coluna;
    }

    public String getTrecho() {
        return trecho;
    }

    public String getSugestao() {
        return sugestao;
    }

    /**
     * Formata a mensagem completa de erro de forma amigável e legível.
     */
    public String formatarMensagemAmigavel() {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ Erro de sintaxe na linha ").append(linha)
          .append(", coluna ").append(coluna).append(":\n\n");
        sb.append(trecho).append("\n\n");
        sb.append("⚠️ Motivo: ").append(getMessage()).append("\n");
        if (sugestao != null && !sugestao.isBlank()) {
            sb.append("💡 Dica: ").append(sugestao).append("\n");
        }
        return sb.toString();
    }
}
