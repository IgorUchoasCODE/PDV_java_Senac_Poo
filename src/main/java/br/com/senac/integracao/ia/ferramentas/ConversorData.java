package br.com.senac.integracao.ia.ferramentas;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário de parsing e resolução de datas absolutas e relativas (hoje, ontem, <4, 3>).
 */
public final class ConversorData {

    private ConversorData() {}

    public static LocalDate resolverDataRelativa(String expr) {
        if (expr == null || expr.isBlank()) return LocalDate.now();
        String s = expr.trim().toLowerCase(Locale.ROOT);
        if ("hoje".equals(s)) return LocalDate.now();
        if ("ontem".equals(s)) return LocalDate.now().minusDays(1);
        if ("amanha".equals(s) || "amanhã".equals(s)) return LocalDate.now().plusDays(1);

        Matcher mAtras = Pattern.compile("^<\\s*([0-9]+)$").matcher(s);
        if (mAtras.find()) {
            return LocalDate.now().minusDays(Long.parseLong(mAtras.group(1)));
        }
        Matcher mFrente = Pattern.compile("^([0-9]+)\\s*>$").matcher(s);
        if (mFrente.find()) {
            return LocalDate.now().plusDays(Long.parseLong(mFrente.group(1)));
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    public static String descreverDataAmigavel(String exprBruta, LocalDate dataResolvida) {
        if (exprBruta == null || exprBruta.isBlank() || "hoje".equalsIgnoreCase(exprBruta.trim())) {
            return "hoje";
        }
        String s = exprBruta.trim().toLowerCase(Locale.ROOT);
        if ("ontem".equals(s)) return "ontem";
        if (s.startsWith("<")) return "há " + s.substring(1).trim() + " dias";
        if (s.endsWith(">")) return "daqui a " + s.substring(0, s.length() - 1).trim() + " dias";
        return dataResolvida.toString();
    }
}
