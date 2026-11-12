package br.com.senac.integracao.ia.bandeja;

import java.util.Map;

/**
 * Resultado de executar (ou tentar executar) uma instrução da bandeja.
 *
 * @param resultado   o que aconteceu
 * @param idItem      código da instrução (ex.: OP-2), ou {@code null} se não houve alvo
 * @param descricao   descrição amigável da instrução
 * @param conferenciaAntes  texto da conferência feita antes de executar (pode ser vazio)
 * @param conferenciaDepois texto da conferência feita depois de executar (pode ser vazio)
 * @param mensagem    detalhe extra (erro, motivo do bloqueio)
 * @param dados       objeto devolvido pela rota (quando executou)
 * @param parametros  parâmetros usados na execução
 * @param restantes   quantas instruções ainda estão em aberto na bandeja
 */
public record PassoExecutado(
        Resultado resultado,
        String idItem,
        String descricao,
        String conferenciaAntes,
        String conferenciaDepois,
        String mensagem,
        Object dados,
        Map<String, Object> parametros,
        int restantes) {

    public enum Resultado {
        EXECUTADO,
        EXECUTADO_COM_ALERTA,
        NAO_EXECUTADO_CONFERENCIA,
        FALHA,
        FALTAM_DADOS,
        JA_EXECUTADA,
        NAO_ENCONTRADA,
        NADA_A_EXECUTAR
    }

    /** {@code true} se a instrução foi gravada (com ou sem alerta na conferência final). */
    public boolean executou() {
        return resultado == Resultado.EXECUTADO || resultado == Resultado.EXECUTADO_COM_ALERTA;
    }

    /** Texto curto e simples, pensado para quem não conhece o sistema. */
    public String formatarTexto() {
        StringBuilder sb = new StringBuilder();
        switch (resultado) {
            case EXECUTADO -> sb.append("Pronto! ").append(idItem).append(" foi executada: ").append(descricao).append(".");
            case EXECUTADO_COM_ALERTA -> sb.append("Executei ").append(idItem).append(" (").append(descricao)
                    .append("), mas a conferência final encontrou uma diferença. Vale revisar.");
            case NAO_EXECUTADO_CONFERENCIA -> sb.append("Não executei ").append(idItem).append(" (").append(descricao)
                    .append("). Antes de salvar, a conferência encontrou um problema.");
            case FALHA -> sb.append("Não consegui executar ").append(idItem).append(" (").append(descricao).append(").");
            case FALTAM_DADOS -> sb.append(idItem).append(" ainda não pode ser executada, faltam dados.");
            case JA_EXECUTADA -> sb.append(idItem).append(" já foi executada antes.");
            case NAO_ENCONTRADA -> sb.append("Não encontrei essa instrução na bandeja.");
            case NADA_A_EXECUTAR -> sb.append("Não há nenhuma instrução pronta para executar agora.");
        }
        if (conferenciaAntes != null && !conferenciaAntes.isBlank()) {
            sb.append("\n  Conferi antes: ").append(conferenciaAntes);
        }
        if (conferenciaDepois != null && !conferenciaDepois.isBlank()) {
            sb.append("\n  Conferi depois: ").append(conferenciaDepois);
        }
        if (mensagem != null && !mensagem.isBlank()) {
            sb.append("\n  Detalhe: ").append(mensagem);
        }
        return sb.toString();
    }
}
