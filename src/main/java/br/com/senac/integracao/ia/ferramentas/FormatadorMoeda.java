package br.com.senac.integracao.ia.ferramentas;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilitário de formatação e parsing seguro de moedas e números para a DSL de IA.
 */
public final class FormatadorMoeda {

    private FormatadorMoeda() {}

    public static String moeda(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    public static BigDecimal parseBigDecimalSafe(String s, BigDecimal padrao) {
        if (s == null || s.isBlank()) return padrao;
        try {
            return new BigDecimal(s.trim().replace(",", "."));
        } catch (Exception e) {
            return padrao;
        }
    }

    public static Long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim().replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
