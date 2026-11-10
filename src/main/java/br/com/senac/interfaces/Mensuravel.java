package br.com.senac.interfaces;

import java.math.BigDecimal;

public interface Mensuravel {
    boolean isQuantidadeValida(BigDecimal quantidade);
    BigDecimal calcularValorProporcional(BigDecimal valorUnitario, BigDecimal quantidade);
}
