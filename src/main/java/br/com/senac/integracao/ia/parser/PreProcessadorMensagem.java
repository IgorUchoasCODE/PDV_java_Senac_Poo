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
            boolean ehComandoDsl,
            List<String> rotasSugeridas
    ) {
        public ResultadoPreProcessamento(
                String mensagemOriginal,
                String mensagemAnotada,
                List<EntidadeExtraida> entidades,
                boolean ehComandoDsl
        ) {
            this(mensagemOriginal, mensagemAnotada, entidades, ehDslOrFalse(ehComandoDsl), List.of());
        }

        private static boolean ehDslOrFalse(boolean v) {
            return v;
        }

        public boolean temContexto() {
            return (entidades != null && !entidades.isEmpty())
                    || (rotasSugeridas != null && !rotasSugeridas.isEmpty());
        }

        /**
         * Gera um bloco compacto de contexto semântico pré-extraído para guiar a LLM.
         */
        public String gerarContextoSemantico() {
            if (!temContexto()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("[Contexto Semântico Pré-processado pelo Sistema]:\n");
            if (entidades != null) {
                for (EntidadeExtraida e : entidades) {
                    sb.append(String.format(" - %s: \"%s\" (Normalizado: %s)%n",
                            e.tipo(), e.textoOriginal(), e.valorNormalizado()));
                }
            }
            if (rotasSugeridas != null && !rotasSugeridas.isEmpty()) {
                sb.append(" - ROTAS_PRIORITARIAS (mesmo contexto semântico): ")
                  .append(String.join(", ", rotasSugeridas))
                  .append("\n");
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
    private static final Pattern PADRAO_PRODUTO_ASPAS = Pattern.compile(
            "(?i)\\b(?:produto(?:\\s+(?:f[ií]sico|digital))?|kit|conjunto|item)\\b(?:\\s+chamado)?\\s*[\":]?\\s*([^\"\\n,;()]+?)(?=\\s+(?:foi|em|de|com|que|tem|tenha|contenha|pode|na|no|para|como|onde|comprando|vendendo|pagando|custando|e\\s+(?:por|colocar|transformar|mudar|atualizar|adicionar|criar)|por\\s+ele|um>|vv>|vc>|q>|vl>|val>|dim>)\\b|[,\"();\\n]|$)");
    private static final Pattern PADRAO_PESSOA_ASPAS = Pattern.compile(
            "(?i)\\b(?:cliente|fornecedor|pessoa)\\b\\s*[\":]?\\s*([^\"\\n,;()]+?)(?=\\s+(?:foi|em|de|com|que|tem|na|no|para|como|onde|comprando|vendendo|pagando)\\b|[,\"();\\n]|$)");

    /**
     * Analisa o texto e extrai entidades semânticas, anotando o texto com tags XML estruturadas.
     */
    public ResultadoPreProcessamento processar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return new ResultadoPreProcessamento("", "", List.of(), false);
        }

        String texto = mensagem.trim().replaceAll("[ \\t]{2,}", " ");
        boolean ehDsl = texto.startsWith("$$") || texto.startsWith("abrir>janela");
        if (!ehDsl) {
            texto = expandirAbreviacoesSemanticas(texto);
        }

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
            if (termo.length() > 1 && !ehContido(entidades, mProd.start(), mProd.end())) {
                entidades.add(new EntidadeExtraida(TipoEntidade.PRODUTO_NOMINADO, mProd.group(0), termo, mProd.start(), mProd.end()));
            }
        }

        Matcher mPess = PADRAO_PESSOA_ASPAS.matcher(texto);
        while (mPess.find()) {
            String termo = mPess.group(1).trim();
            if (termo.length() > 1 && !ehContido(entidades, mPess.start(), mPess.end())) {
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
        List<String> rotasSugeridas = inferirRotasPrioritarias(texto, entidades, ehDsl);

        return new ResultadoPreProcessamento(texto, anotada, Collections.unmodifiableList(entidades), ehDsl, rotasSugeridas);
    }

    /**
     * Mapeia intenções e entidades detectadas para grupos de rotas do mesmo contexto semântico,
     * permitindo que a IA priorize as ferramentas corretas sem varrer as 85 rotas do catálogo.
     */
    private static List<String> inferirRotasPrioritarias(String texto, List<EntidadeExtraida> entidades, boolean ehDsl) {
        if (ehDsl || texto == null || texto.isBlank()) {
            return List.of();
        }
        String lower = texto.toLowerCase(Locale.ROOT);
        java.util.LinkedHashSet<String> rotas = new java.util.LinkedHashSet<>();

        boolean temProdutoEnt = entidades.stream().anyMatch(e ->
                e.tipo() == TipoEntidade.PRODUTO_NOMINADO || e.tipo() == TipoEntidade.SIGLA_PRODUTO);
        boolean temPessoaEnt = entidades.stream().anyMatch(e ->
                e.tipo() == TipoEntidade.PESSOA_NOMINADA || e.tipo() == TipoEntidade.CPF || e.tipo() == TipoEntidade.CNPJ);

        if (lower.matches("(?s).*\\b(conjunto|conjuntos|kit|kits|composto|componente|componentes|cartela|cartelas|cont[eê]m|contenha|composi[cç][aã]o)\\b.*")) {
            rotas.addAll(List.of(
                    "produto_catalogo_buscar",
                    "produto_fisico_cadastrar",
                    "produto_kit_adicionar_componente",
                    "produto_catalogo_atualizar"
            ));
        }

        if (lower.matches("(?s).*\\b(unidade\\s+de\\s+medida|unidades\\s+de\\s+medida|um|sigla|fator|fracion\\w*)\\b.*")) {
            rotas.addAll(List.of(
                    "produto_unidade_listar",
                    "produto_unidade_cadastrar",
                    "produto_unidade_atualizar"
            ));
        }

        if (temProdutoEnt || lower.matches("(?s).*\\b(produto|produtos|item|itens|cat[aá]logo|pre[cç]o|dimens[aã]o|dimens[oõ]es|peso)\\b.*")) {
            rotas.addAll(List.of(
                    "produto_catalogo_buscar",
                    "produto_catalogo_listar",
                    "produto_fisico_cadastrar",
                    "produto_catalogo_atualizar",
                    "produto_catalogo_atualizar_preco"
            ));
        }

        if (temPessoaEnt || lower.matches("(?s).*\\b(cliente|clientes|fornecedor|fornecedores|pessoa|pessoas|cpf|cnpj|limite)\\b.*")) {
            rotas.addAll(List.of(
                    "pessoa_buscar",
                    "pessoa_listar_clientes",
                    "pessoa_listar_fornecedores",
                    "pessoa_simples_cadastrar",
                    "pessoa_cliente_pf_cadastrar",
                    "pessoa_cliente_pj_cadastrar",
                    "pessoa_fornecedor_pf_cadastrar",
                    "pessoa_fornecedor_pj_cadastrar"
            ));
        }

        if (lower.matches("(?s).*\\b(nota|notas|compra|comprar|venda|vender|emiti\\w*|fatura|devolu[cç][aã]o)\\b.*")) {
            rotas.addAll(List.of(
                    "nota_listar_padrao",
                    "nota_buscar_unica",
                    "nota_compra_emitir",
                    "nota_venda_emitir_fifo",
                    "nota_venda_emitir_balcao_cliente_padrao",
                    "nota_devolucao_registrar"
            ));
        }

        if (lower.matches("(?s).*\\b(estoque|lote|lotes|validade|vencid\\w*|perda|fifo|saldo)\\b.*")) {
            rotas.addAll(List.of(
                    "estoque_saldo_consolidado",
                    "estoque_lotes_validos_listar",
                    "estoque_fila_lotes_fifo",
                    "estoque_lotes_proximos_validade",
                    "estoque_baixar_perda_direta"
            ));
        }

        if (lower.matches("(?s).*\\b(pagamento|pagamentos|pagar|parcela|parcelas|quitar|forma\\s+de\\s+pagamento|pix|cart[aã]o|boleto|dinheiro)\\b.*")) {
            rotas.addAll(List.of(
                    "pagamento_forma_listar_ativas",
                    "pagamento_criar_a_vista",
                    "pagamento_criar_a_prazo",
                    "pagamento_criar_parcelado",
                    "pagamento_quitar_titulo_ou_parcela"
            ));
        }

        if (lower.matches("(?s).*\\b(lucro|bacen|relat[oó]rio|sazonal\\w*|demanda|ranking|previs[aã]o|clima|temperatura|graus?|rio|rios|evento|eventos|dist[aâ]ncia|impact\\w*|estrag\\w*|motivo\\w*|correla\\w*|chance\\w*|padr[aã]o|padr[oõ]es)\\b.*")) {
            rotas.addAll(List.of(
                    "busca_padrao_ia",
                    "estatistica_motivos_perda_prematura",
                    "estatistica_impacto_externo_produtos",
                    "estatistica_destaques_sazonais",
                    "estatistica_picos_dias_eventos",
                    "fator_externo_relatorio_integrado_notas",
                    "relatorio_analise_sazonal_vendas",
                    "relatorio_lucro_contabil_bacen",
                    "estatistica_plano_estrategico_compras",
                    "estatistica_ranking_fornecedores"
            ));
        }

        if (lower.matches("(?s).*\\b(soma\\w*|total|quantos|quantas|m[eé]dia|maior|menor|entre|acima|abaixo|exceto|sem|vencid\\w*|devolv\\w*|resumo|filtr\\w*)\\b.*")) {
            rotas.add("busca_padrao_ia");
        }

        return rotas.isEmpty() ? List.of() : Collections.unmodifiableList(new ArrayList<>(rotas));
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

    /**
     * Expande abreviações comuns de PDV no texto livre para aumentar a clareza semântica
     * sem alterar comandos DSL ou nomes próprios.
     */
    private static String expandirAbreviacoesSemanticas(String texto) {
        String normalizado = texto
                .replaceAll("(?i)^(?:voc[eê]|ei|ol[aá]|sistema)[,:\\s]+", "")
                .replaceAll("(?i)\\besque\\b", "estoque")
                .replaceAll("(?i)\\bp/\\s*", "para ")
                .replaceAll("(?i)^vend\\b", "vender")
                .replaceAll("(?i)(\\d+)\\s*cxs?\\b", "$1 caixa")
                .replaceAll("(?i)(\\d+)\\s*und?s?\\b", "$1 unidade");
        return normalizado.replaceAll("[ \\t]{2,}", " ").trim();
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
