package br.com.senac.interfaces;

import java.math.BigDecimal;

public interface RegraPagamento {
    BigDecimal calcularValorFinal();
    BigDecimal calcularEncargos();
    boolean isPagamentoValido();
    boolean isLiquidado();
}
