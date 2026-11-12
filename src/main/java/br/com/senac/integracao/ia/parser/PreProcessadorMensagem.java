package br.com.senac.integracao.ia.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pré-processador de mensagens do usuário para enriquecimento semântico antes da chamada da LLM.
 *
 * <p>Identifica entidades como valores monetários, datas relativas/absolutas, CPFs/CNPJs, siglas de
 * produtos e papéis de pessoas, gerando tags semânticas estruturadas. Isso reduz drasticamente a
 * ambiguidade e o consumo de tokens das APIs de IA, além de viabilizar a execução precisa de modelos
 * locais compactos (como Qwen 2.5 1.5B/3B).
 */
public class PreProcessadorMensagem {

    public enum TipoEntidade {
        VALOR_MONETARIO,
        DATA,
        CPF,
        CNPJ,
        SIGLA_PRODUTO,
        PRODUTO_NOMINADO,
        PESSOA_NOMINADA
    }

    public record EntidadeExtraida(
            TipoEntidade tipo,
            String textoOriginal,
            String valorNormalizado,
            int inicio,
            int fim
    ) {}

    public record ResultadoPreProcessamento(
            String mensagemOriginal,
            String mensagemAnotada,
            List<EntidadeExtraida> entidades,
            boolean ehComandoDsl
    ) {
        /**
         * Gera um bloco compacto de contexto semântico pré-extraído para guiar a LLM.
         */
        public String gerarContextoSemantico() {
            if (entidades == null || entidades.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("[Contexto Semântico Pré-processado pelo Sistema]:\n");
            for (EntidadeExtraida e : entidades) {
                sb.append(String.format(" - %s: \"%s\" (Normalizado: %s)%n",
                        e.tipo(), e.textoOriginal(), e.valorNormalizado()));
            }
            return sb.toString().trim();
        }
    }

    // Padrões de regex para extração semântica
    private static final Pattern PADRAO_VALOR = Pattern.compile("(?i)(?:R\\$\\s*(\\d{1,6}(?:[.,]\\d{1,2})?)|\\b(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*reais)\\b");
    private static final Pattern PADRAO_DATA_BR = Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{2,4})\\b");
    private static final Pattern PADRAO_DATA_ISO = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern PADRAO_DATA_RELATIVA = Pattern.compile("(?i)\\b(hoje|ontem|amanh[aã]|ant[eo]ntem)\\b|<\\s*(\\d+)|(\\d+)\\s*>");
    private static final Pattern PADRAO_CPF = Pattern.compile("\\b(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11})\\b");
    private static final Pattern PADRAO_CNPJ = Pattern.compile("\\b(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\d{14})\\b");
    private static final Pattern PADRAO_SIGLA = Pattern.compile(">([a-zA-Z0-9_]{2,8})\\b");
    private static final Pattern PADRAO_PRODUTO_ASPAS = Pattern.compile("(?i)(?:produto|kit|conjunto|item)\\s*[\":]?\\s*([^\"\\n,;]+?)(?=\\s+(?:foi|em|de|com|que|tem|na|no|para|um>|vv>|vc>|q>|vl>|val>|dim>)\\b|[,\";\\n]|$)");
    private static final Pattern PADRAO_PESSOA_ASPAS = Pattern.compile("(?i)(?:cliente|fornecedor|pessoa)\\s*[\":]?\\s*([^\"\\n,;]+?)(?=\\s+(?:foi|em|de|com|que|tem|na|no|para)\\b|[,\";\\n]|$)");

    /**
     * Analisa o texto e extrai entidades semânticas, anotando o texto com tags XML estruturadas.
     */
    public ResultadoPreProcessamento processar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return new ResultadoPreProcessamento("", "", List.of(), false);
        }

        String texto = mensagem.trim();
        boolean ehDsl = texto.startsWith("$$") || texto.startsWith("abrir>janela");

        List<EntidadeExtraida> entidades = new ArrayList<>();

        // 1. Extração de CPF e CNPJ
        Matcher mCnpj = PADRAO_CNPJ.matcher(texto);
        while (mCnpj.find()) {
            String raw = mCnpj.group(1);
            String limpo = raw.replaceAll("[^0-9]", "");
            if (limpo.length() == 14) {
                entidades.add(new EntidadeExtraida(TipoEntidade.CNPJ, raw, limpo, mCnpj.start(), mCnpj.end()));
            }
        }

        Matcher mCpf = PADRAO_CPF.matcher(texto);
        while (mCpf.find()) {
            String raw = mCpf.group(1);
            String limpo = raw.replaceAll("[^0-9]", "");
            if (limpo.length() == 11 && !ehContido(entidades, mCpf.start(), mCpf.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.CPF, raw, limpo, mCpf.start(), mCpf.end()));
            }
        }

        // 2. Extração de Datas (absolutas e relativas)
        Matcher mDataBr = PADRAO_DATA_BR.matcher(texto);
        while (mDataBr.find()) {
            String raw = mDataBr.group(1);
            if (!ehContido(entidades, mDataBr.start(), mDataBr.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.DATA, raw, normalizarDataBr(raw), mDataBr.start(), mDataBr.end()));
            }
        }

        Matcher mDataIso = PADRAO_DATA_ISO.matcher(texto);
        while (mDataIso.find()) {
            String raw = mDataIso.group(1);
            if (!ehContido(entidades, mDataIso.start(), mDataIso.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.DATA, raw, raw, mDataIso.start(), mDataIso.end()));
            }
        }

        Matcher mDataRel = PADRAO_DATA_RELATIVA.matcher(texto);
        while (mDataRel.find()) {
            String raw = mDataRel.group(0);
            if (!ehContido(entidades, mDataRel.start(), mDataRel.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.DATA, raw, normalizarDataRelativa(raw), mDataRel.start(), mDataRel.end()));
            }
        }

        // 3. Extração de Siglas de busca rápida (>oba, >ocv)
        Matcher mSigla = PADRAO_SIGLA.matcher(texto);
        while (mSigla.find()) {
            String raw = mSigla.group(0);
            String termo = mSigla.group(1);
            if (!ehContido(entidades, mSigla.start(), mSigla.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.SIGLA_PRODUTO, raw, termo.toLowerCase(Locale.ROOT), mSigla.start(), mSigla.end()));
            }
        }

        // 4. Produtos e Pessoas nominadas entre aspas ou após marcadores
        Matcher mProd = PADRAO_PRODUTO_ASPAS.matcher(texto);
        while (mProd.find()) {
            String termo = mProd.group(1).trim();
            if (!termo.isEmpty() && !ehContido(entidades, mProd.start(), mProd.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.PRODUTO_NOMINADO, mProd.group(0), termo, mProd.start(), mProd.end()));
            }
        }

        Matcher mPess = PADRAO_PESSOA_ASPAS.matcher(texto);
        while (mPess.find()) {
            String termo = mPess.group(1).trim();
            if (!termo.isEmpty() && !ehContido(entidades, mPess.start(), mPess.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.PESSOA_NOMINADA, mPess.group(0), termo, mPess.start(), mPess.end()));
            }
        }

        // 5. Valores Monetários (R$ 150,00 ou 150 reais)
        Matcher mVal = PADRAO_VALOR.matcher(texto);
        while (mVal.find()) {
            String num = mVal.group(1) != null ? mVal.group(1) : mVal.group(2);
            String raw = mVal.group(0);
            if (!ehContido(entidades, mVal.start(), mVal.end())) {
                String normalizado = normalizarValor(num);
                entidades.add(new EntidadeExtraida(TipoEntidade.VALOR_MONETARIO, raw, normalizado, mVal.start(), mVal.end()));
            }
        }

        // Ordena entidades por posição de início
        entidades.sort((a, b) -> Integer.compare(a.inicio(), b.inicio()));

        // Constrói a mensagem anotada com tags semânticas XML
        String anotada = gerarTextoAnotado(texto, entidades);

        return new ResultadoPreProcessamento(texto, anotada, Collections.unmodifiableList(entidades), ehDsl);
    }

    private static boolean ehContido(List<EntidadeExtraida> lista, int inicio, int fim) {
        for (EntidadeExtraida e : lista) {
            if ((inicio >= e.inicio() && inicio < e.fim()) || (fim > e.inicio() && fim <= e.fim())) {
                return true;
            }
        }
        return false;
    }

    private static String normalizarValor(String s) {
        if (s == null) return "0.00";
        try {
            String limpo = s.replace(".", "").replace(",", ".");
            return new BigDecimal(limpo).setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString();
        } catch (Exception e) {
            return s.trim();
        }
    }

    private static String normalizarDataBr(String s) {
        try {
            String[] p = s.split("/");
            int d = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            int a = Integer.parseInt(p[2]);
            if (a < 100) a += 2000;
            return LocalDate.of(a, m, d).toString();
        } catch (Exception e) {
            return s;
        }
    }

    private static String normalizarDataRelativa(String s) {
        String limpo = s.toLowerCase(Locale.ROOT).trim();
        LocalDate hoje = LocalDate.now();
        if ("hoje".equals(limpo)) return hoje.toString();
        if ("ontem".equals(limpo)) return hoje.minusDays(1).toString();
        if ("anteontem".equals(limpo) || "antontem".equals(limpo)) return hoje.minusDays(2).toString();
        if ("amanha".equals(limpo) || "amanhã".equals(limpo)) return hoje.plusDays(1).toString();
        if (limpo.startsWith("<")) {
            long dias = Long.parseLong(limpo.substring(1).trim());
            return hoje.minusDays(dias).toString();
        }
        if (limpo.endsWith(">")) {
            long dias = Long.parseLong(limpo.substring(0, limpo.length() - 1).trim());
            return hoje.plusDays(dias).toString();
        }
        return s;
    }

    private static String gerarTextoAnotado(String texto, List<EntidadeExtraida> entidades) {
        if (entidades.isEmpty()) return texto;

        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (EntidadeExtraida e : entidades) {
            if (e.inicio() < cursor) {
                continue;
            }
            if (e.inicio() > cursor) {
                sb.append(texto, cursor, e.inicio());
            }
            String tag = switch (e.tipo()) {
                case VALOR_MONETARIO -> "valor";
                case DATA -> "data";
                case CPF -> "cpf";
                case CNPJ -> "cnpj";
                case SIGLA_PRODUTO -> "sigla";
                case PRODUTO_NOMINADO -> "produto";
                case PESSOA_NOMINADA -> "pessoa";
            };
            sb.append("<").append(tag).append(" normalizado=\"").append(e.valorNormalizado()).append("\">")
              .append(e.textoOriginal())
              .append("</").append(tag).append(">");
            cursor = e.fim();
        }
        if (cursor < texto.length()) {
            sb.append(texto.substring(cursor));
        }
        return sb.toString();
    }
}
