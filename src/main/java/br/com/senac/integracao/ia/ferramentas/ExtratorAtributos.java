package br.com.senac.integracao.ia.ferramentas;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário para extração de atributos, dimensões e normalização de códigos.
 */
public final class ExtratorAtributos {

    private ExtratorAtributos() {}

    public static String obter(Map<String, String> map, String chave, String padrao) {
        if (map == null || chave == null) return padrao;
        String v = map.get(chave.toLowerCase(Locale.ROOT));
        return (v != null && !v.isBlank()) ? v.trim() : padrao;
    }

    public static Map<String, String> extrairAtributos(String segmento) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (segmento == null || segmento.isBlank()) return mapa;

        String[] tokens = segmento.split(",");
        for (String tokenBruto : tokens) {
            String t = tokenBruto.trim();
            if (t.isEmpty()) continue;

            Matcher mPadrao = Pattern.compile("^([a-zA-Z0-9_]+)\\s*>\\s*(.+)$").matcher(t);
            if (mPadrao.find()) {
                mapa.put(mPadrao.group(1).toLowerCase(Locale.ROOT), mPadrao.group(2).trim());
                continue;
            }

            Matcher mInvertido = Pattern.compile("^>\\s*([a-zA-Z0-9_]+)\\s+(.+)$").matcher(t);
            if (mInvertido.find()) {
                mapa.put(mInvertido.group(1).toLowerCase(Locale.ROOT), mInvertido.group(2).trim());
            }
        }
        return mapa;
    }

    public static Map<String, String> extrairAtributosPagamentoPosicionalOuChaveado(String linha) {
        String limpa = linha.replaceFirst("^(\\+\\+|--)\\s*", "").trim();
        Map<String, String> attrs = extrairAtributos(limpa);
        String[] partes = limpa.split(",");
        int pos = 0;
        for (String p : partes) {
            String token = p.trim();
            if (token.isEmpty() || token.contains(">")) continue;
            if (pos == 0 && !attrs.containsKey("nm")) {
                attrs.put("nm", token);
                pos++;
            } else if (pos <= 1 && !attrs.containsKey("val") && !attrs.containsKey("vl") && token.matches("^[0-9]+([.,][0-9]+)?$")) {
                attrs.put("val", token.replace(",", "."));
                pos = 2;
            } else if (!attrs.containsKey("dt")) {
                attrs.put("dt", token);
            }
        }
        return attrs;
    }

    public static void aplicarDimensoes(Map<String, String> attrs, Map<String, Object> params) {
        String dim = obter(attrs, "dim", "10x10x10");
        String[] partes = dim.toLowerCase(Locale.ROOT).split("x");
        params.put("alturaCm", partes.length > 0 ? partes[0].trim() : "10.0");
        params.put("larguraCm", partes.length > 1 ? partes[1].trim() : "10.0");
        params.put("comprimentoCm", partes.length > 2 ? partes[2].trim() : "10.0");
        params.put("pesoBrutoKg", obter(attrs, "pb", "1.0"));
        params.put("pesoLiquidoKg", obter(attrs, "pl", "0.9"));
    }

    public static String normalizarCodigoForma(String raw) {
        if (raw == null || raw.isBlank()) return "PIX";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.contains("DINHEIRO")) return "DINHEIRO";
        if (s.contains("PIX")) return "PIX";
        if (s.contains("DEBITO")) return "CARTAO_DEBITO";
        if (s.contains("CREDITO") || s.contains("CARTAO")) return "CARTAO_CREDITO";
        if (s.contains("BOLETO") || s.contains("PRAZO") || s.contains("CREDIARIO")) return "BOLETO";
        if (s.contains("CHEQUE")) return "CHEQUE";
        return s.replace(" ", "_");
    }

    public static Long parseLongSafe(String s) {
        return FormatadorMoeda.parseLongSafe(s);
    }

    public static BigDecimal parseBigDecimalSafe(String s, BigDecimal padrao) {
        return FormatadorMoeda.parseBigDecimalSafe(s, padrao);
    }
}
