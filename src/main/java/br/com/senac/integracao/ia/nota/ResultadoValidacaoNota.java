package br.com.senac.integracao.ia.nota;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Resultado da validação estrutural e fiscal de regras de negócio de uma Nota antes do enfileiramento na bandeja.
 */
public record ResultadoValidacaoNota(
        boolean valido,
        BigDecimal totalItens,
        BigDecimal totalPago,
        BigDecimal troco,
        List<String> erros,
        List<String> avisos
) {
    public static ResultadoValidacaoNota sucesso(BigDecimal totalItens, BigDecimal totalPago, BigDecimal troco, List<String> avisos) {
        return new ResultadoValidacaoNota(true, totalItens, totalPago, troco != null ? troco : BigDecimal.ZERO,
                Collections.emptyList(), avisos != null ? avisos : Collections.emptyList());
    }

    public static ResultadoValidacaoNota falha(BigDecimal totalItens, BigDecimal totalPago, List<String> erros) {
        return new ResultadoValidacaoNota(false, totalItens, totalPago, BigDecimal.ZERO,
                erros != null ? erros : Collections.emptyList(), Collections.emptyList());
    }
}
