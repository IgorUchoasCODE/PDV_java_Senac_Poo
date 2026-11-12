package br.com.senac.integracao.ia.validacao;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Leitura segura dos parâmetros de uma instrução (valores podem chegar como texto ou número).
 * Ferramenta reutilizável pelos validadores e, futuramente, pelas estratégias de instrução.
 */
public final class LeitorParametros {

    private LeitorParametros() {
    }

    public static Long longOuNulo(Map<String, Object> params, String... chaves) {
        for (String chave : chaves) {
            Object v = params.get(chave);
            if (v == null) continue;
            if (v instanceof Number n) return n.longValue();
            try {
                return Long.valueOf(v.toString().trim().replaceAll("[^0-9\\-]", ""));
            } catch (NumberFormatException ignored) {
                // tenta a próxima chave
            }
        }
        return null;
    }

    public static BigDecimal decimalOuNulo(Map<String, Object> params, String... chaves) {
        for (String chave : chaves) {
            Object v = params.get(chave);
            if (v == null) continue;
            if (v instanceof BigDecimal b) return b;
            if (v instanceof Number n) return new BigDecimal(n.toString());
            try {
                return new BigDecimal(v.toString().trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                // tenta a próxima chave
            }
        }
        return null;
    }

    public static String textoOuNulo(Map<String, Object> params, String... chaves) {
        for (String chave : chaves) {
            Object v = params.get(chave);
            if (v != null && !v.toString().isBlank()) return v.toString().trim();
        }
        return null;
    }
}
