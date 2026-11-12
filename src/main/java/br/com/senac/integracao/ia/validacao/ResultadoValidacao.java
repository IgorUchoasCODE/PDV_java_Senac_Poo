package br.com.senac.integracao.ia.validacao;

/**
 * Resultado de uma conferência feita antes ou depois de executar uma instrução da bandeja.
 *
 * @param ok       {@code true} se a conferência passou
 * @param mensagem texto curto, em linguagem simples, para mostrar ao usuário
 */
public record ResultadoValidacao(boolean ok, String mensagem) {

    public static ResultadoValidacao aprovado(String mensagem) {
        return new ResultadoValidacao(true, mensagem);
    }

    public static ResultadoValidacao reprovado(String mensagem) {
        return new ResultadoValidacao(false, mensagem);
    }

    /** Conferência que não se aplica à instrução (não gera texto nem bloqueia). */
    public static ResultadoValidacao naoSeAplica() {
        return new ResultadoValidacao(true, "");
    }

    public boolean temMensagem() {
        return mensagem != null && !mensagem.isBlank();
    }
}
