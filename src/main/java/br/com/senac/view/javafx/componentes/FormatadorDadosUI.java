package br.com.senac.view.javafx.componentes;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Padroniza e trata TODAS as saídas de dados exibidas na interface JavaFX
 * (moeda BRL, quantidades, datas, listas de arrays e textos), evitando 'null',
 * datas ISO cruas, códigos de variação (\uFE0F) e Arrays.toString().
 */
public final class FormatadorDadosUI {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatadorDadosUI() {
    }

    public static String texto(Object valor, String padrao) {
        if (valor == null) return padrao;
        String s = valor.toString().replace("\uFE0F", "").trim();
        return (s.isEmpty() || "null".equalsIgnoreCase(s)) ? padrao : s;
    }

    public static String moeda(Object valor) {
        BigDecimal bd = toBigDecimal(valor);
        NumberFormat nf = NumberFormat.getCurrencyInstance(PT_BR);
        return nf.format(bd);
    }

    public static String quantidade(Object valor) {
        if (valor == null) return "0";
        BigDecimal bd = toBigDecimal(valor).stripTrailingZeros();
        return bd.scale() < 0 ? bd.setScale(0).toPlainString() : bd.toPlainString().replace('.', ',');
    }

    public static String numero(double valor, int casas) {
        NumberFormat nf = NumberFormat.getNumberInstance(PT_BR);
        nf.setMinimumFractionDigits(casas);
        nf.setMaximumFractionDigits(casas);
        return nf.format(valor);
    }

    public static String temperatura(double valor) {
        return numero(valor, 1) + " °C";
    }

    public static String milimetros(double valor) {
        return numero(valor, 1) + " mm";
    }

    public static String vazaoRio(double valor) {
        return numero(valor, 1) + " m³/s";
    }

    public static String percentual(Object valor) {
        return numero(toBigDecimal(valor).doubleValue(), 1) + "%";
    }

    public static String erroAmigavel(Throwable erro, String padrao) {
        if (erro == null) return padrao;
        return texto(erro.getMessage(), padrao);
    }

    public static String data(Object valor, String padrao) {
        if (valor == null) return padrao;
        if (valor instanceof LocalDate ld) return ld.format(FMT_DATA);
        if (valor instanceof LocalDateTime ldt) return ldt.format(FMT_DATA);
        String s = texto(valor, "");
        if (s.isEmpty()) return padrao;
        try {
            if (s.length() >= 10 && s.charAt(4) == '-') {
                return LocalDate.parse(s.substring(0, 10)).format(FMT_DATA);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    public static String dataHora(Object valor, String padrao) {
        if (valor == null) return padrao;
        if (valor instanceof LocalDateTime ldt) return ldt.format(FMT_DATA_HORA);
        if (valor instanceof LocalDate ld) return ld.format(FMT_DATA);
        String s = texto(valor, "");
        if (s.isEmpty()) return padrao;
        try {
            if (s.length() >= 16 && s.charAt(4) == '-' && s.contains("T")) {
                return LocalDateTime.parse(s.substring(0, 16)).format(FMT_DATA_HORA);
            }
        } catch (Exception ignored) {
        }
        return data(s, padrao);
    }

    public static String formatarRegistroArray(Object item) {
        if (item == null) return "-";
        if (item instanceof java.util.Map<?, ?> mapa) {
            StringBuilder sb = new StringBuilder();
            for (var entry : mapa.entrySet()) {
                String chave = texto(entry.getKey(), "");
                if (chave.isEmpty() || chave.startsWith("id_")) continue;
                String val = formatarCelulaGenerica(entry.getValue());
                if (val.equals("-")) continue;
                String rotulo = chave.replace('_', ' ');
                if (sb.length() > 0) sb.append(" • ");
                sb.append(rotulo).append(": ").append(val);
            }
            return sb.length() == 0 ? "-" : sb.toString();
        }
        if (item instanceof Object[] arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; i++) {
                String parte = formatarCelulaGenerica(arr[i]);
                if (parte.equals("-")) continue;
                if (sb.length() > 0) sb.append("  •  ");
                sb.append(parte);
            }
            return sb.length() == 0 ? "-" : sb.toString();
        }
        return texto(item, "-");
    }

    public static String resumirListaLimpa(Object dados, int limite, String mensagemVazia) {
        if (dados instanceof List<?> lista) {
            if (lista.isEmpty()) return mensagemVazia;
            StringBuilder sb = new StringBuilder();
            int max = Math.min(limite, lista.size());
            for (int i = 0; i < max; i++) {
                sb.append("• ").append(formatarRegistroArray(lista.get(i))).append("\n");
            }
            return sb.toString().trim();
        }
        return texto(dados, mensagemVazia);
    }

    private static String formatarCelulaGenerica(Object val) {
        if (val == null) return "-";
        if (val instanceof BigDecimal || val instanceof Number) return quantidade(val);
        if (val instanceof LocalDate || val instanceof LocalDateTime) return dataHora(val, "-");
        String s = texto(val, "-");
        if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return data(s, s);
        }
        return s;
    }

    public static BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return val != null ? new BigDecimal(val.toString().replace(",", ".").trim()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
