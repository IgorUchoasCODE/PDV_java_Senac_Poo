package br.com.senac.integracao.ia;

import br.com.senac.controller.RotaSistema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ferramenta central de tratamento, contagem de palavras, quebra de linhas e formatação visual
 * das saídas da IA (Terminal CLI, Web/Mobile e futura interface gráfica JavaFX).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Impedir que dados brutos ({@code [{chave=valor, ...}]} ou {@code Map}/{@code List<Map>}) cheguem sem formatação.</li>
 *   <li>Contar palavras separadas por espaço e inserir quebras de linha ({@code \n}) configuráveis.</li>
 *   <li>Aplicar metadados de fonte, tamanho e cores sutis para entidades do domínio
 *       (Cliente, Fornecedor, Pessoa, Produto, Notas, Valores e Alertas).</li>
 * </ul>
 */
public final class FormatadorSaidaIA {

    public static final int PADRAO_PALAVRAS_POR_LINHA = 18;
    public static final int PADRAO_CARACTERES_POR_LINHA = 130;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_NEGRITO = "\u001B[1m";
    private static final String ANSI_VERDE = "\u001B[32m";
    private static final String ANSI_AMARELO = "\u001B[33m";
    private static final String ANSI_AZUL = "\u001B[34m";
    private static final String ANSI_CIANO = "\u001B[36m";
    private static final String ANSI_ROXO = "\u001B[35m";
    private static final String ANSI_VERMELHO = "\u001B[31m";
    private static final String ANSI_VERDE_CLARO = "\u001B[92m";
    private static final String ANSI_CINZA = "\u001B[90m";

    private static final DateTimeFormatter FMT_DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Pattern PADRAO_ANSI = Pattern.compile("\u001B\\[[;?0-9]*[a-zA-Z]");
    private static final Pattern PADRAO_LISTA_MAPAS_BRUTOS = Pattern.compile("\\[\\s*\\{[^{}\\[\\]]*=[^{}\\[\\]]*\\}(?:\\s*,\\s*\\{[^{}\\[\\]]*=[^{}\\[\\]]*\\})*\\s*\\]");
    private static final Pattern PADRAO_MAPA_BRUTO_UNICO = Pattern.compile("\\{[a-zA-Z0-9_]+=[^\\{\\}]+,\\s*[a-zA-Z0-9_]+=[^\\{\\}]+\\}");
    private static final Pattern PADRAO_SEPARADOR_CHAVE_VALOR = Pattern.compile(",\\s*(?=[a-zA-Z0-9_]+\\s*=)");

    private static final Pattern PADRAO_DESTAQUE = Pattern.compile(
            "(R\\$\\s*-?\\d+(?:[.,]\\d{1,4})?)"
                    + "|(\\b(?:Clientes?|Fornecedor(?:es)?|Pessoas?|Produtos?|Notas?(?:\\s+(?:Fiscais|Fiscal|de\\s+Perda|de\\s+Compra|de\\s+Venda|de\\s+Devolu[çc][aã]o))?)\\b(?:\\s*#\\d+)?)"
                    + "|(#\\d+)"
                    + "|(⚠️[^\\n]*|❌[^\\n]*|\\b(?:DEGRADACAO_[A-Z0-9_]+|INFILTRACAO_[A-Z0-9_]+|FADIGA_[A-Z0-9_]+|FALHA_[A-Z0-9_]+|PERECIVEL_[A-Z0-9_]+|ALTA_SENSIBILIDADE_[A-Z0-9_]+)\\b)"
    );

    private FormatadorSaidaIA() {
    }

    /**
     * Categorias visuais para destaque sutil de entidades e elementos na UI/Terminal.
     */
    public enum TipoEntidadeDestaque {
        TITULO("#38BDF8", "Segoe UI", 16, true, ANSI_NEGRITO + ANSI_CIANO),
        SUBTITULO("#94A3B8", "Segoe UI", 14, true, ANSI_NEGRITO + ANSI_CINZA),
        CLIENTE("#34D399", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_VERDE),
        FORNECEDOR("#FBBF24", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_AMARELO),
        PESSOA("#60A5FA", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_AZUL),
        PRODUTO("#22D3EE", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_CIANO),
        NOTA("#C084FC", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_ROXO),
        VALOR_MONETARIO("#4ADE80", "Consolas", 13, true, ANSI_VERDE_CLARO),
        IDENTIFICADOR("#A5B4FC", "Consolas", 12, true, ANSI_CIANO),
        ALERTA("#F87171", "Segoe UI", 13, true, ANSI_NEGRITO + ANSI_VERMELHO),
        TEXTO_COMUM("#E2E8F0", "Segoe UI", 13, false, "");

        private final String corHex;
        private final String fontePadrao;
        private final int tamanhoPadraoPt;
        private final boolean negritoPadrao;
        private final String codigoAnsi;

        TipoEntidadeDestaque(String corHex, String fontePadrao, int tamanhoPadraoPt, boolean negritoPadrao, String codigoAnsi) {
            this.corHex = corHex;
            this.fontePadrao = fontePadrao;
            this.tamanhoPadraoPt = tamanhoPadraoPt;
            this.negritoPadrao = negritoPadrao;
            this.codigoAnsi = codigoAnsi;
        }

        public String getCorHex() {
            return corHex;
        }

        public String getFontePadrao() {
            return fontePadrao;
        }

        public int getTamanhoPadraoPt() {
            return tamanhoPadraoPt;
        }

        public boolean isNegritoPadrao() {
            return negritoPadrao;
        }

        public String getCodigoAnsi() {
            return codigoAnsi;
        }
    }

    /**
     * Configuração reutilizável para quebra de linha por contador de palavras, fonte e tamanho.
     */
    public record ConfiguracaoFormatacaoSaida(
            int maxPalavrasPorLinha,
            int maxCaracteresPorLinha,
            String fontePrincipal,
            String fonteMonoespacada,
            int tamanhoTituloPt,
            int tamanhoCorpoPt,
            boolean aplicarCoresEntidades
    ) {
        public static ConfiguracaoFormatacaoSaida padrao() {
            return new ConfiguracaoFormatacaoSaida(
                    PADRAO_PALAVRAS_POR_LINHA,
                    PADRAO_CARACTERES_POR_LINHA,
                    "Segoe UI",
                    "Consolas",
                    16,
                    13,
                    true
            );
        }

        public static ConfiguracaoFormatacaoSaida comPalavrasPorLinha(int maxPalavrasPorLinha) {
            return new ConfiguracaoFormatacaoSaida(
                    Math.max(1, maxPalavrasPorLinha),
                    PADRAO_CARACTERES_POR_LINHA,
                    "Segoe UI",
                    "Consolas",
                    16,
                    13,
                    true
            );
        }
    }

    /**
     * Segmento tipado pronto para renderização em JavaFX {@code TextFlow} ou componentes ricos.
     */
    public record SegmentoTextoFormatado(
            String texto,
            TipoEntidadeDestaque tipo,
            String corHex,
            String fonteFamilia,
            int tamanhoFontePt,
            boolean negrito
    ) {
    }

    /**
     * Pacote completo de saída já tratada para IA, Terminal CLI, Web/Mobile e futura UI JavaFX.
     */
    public record RespostaFormatadaIA(
            String textoPuroFormatado,
            String textoAnsiColorido,
            String htmlFormatado,
            List<SegmentoTextoFormatado> segmentos,
            int totalPalavras,
            int totalLinhas
    ) {
    }

    // ==================== API PRINCIPAL ====================

    /**
     * Trata a saída completa da IA com configuração padrão:
     * elimina dados brutos, aplica contador de palavras para quebra de linha e gera versões
     * em texto puro, ANSI colorido, HTML estilizado e segmentos tipados para JavaFX.
     */
    public static RespostaFormatadaIA formatar(String textoBruto) {
        return formatar(textoBruto, ConfiguracaoFormatacaoSaida.padrao());
    }

    public static RespostaFormatadaIA formatar(String textoBruto, ConfiguracaoFormatacaoSaida config) {
        ConfiguracaoFormatacaoSaida cfg = config != null ? config : ConfiguracaoFormatacaoSaida.padrao();
        String semBrutos = sanitizarTextoContraDadosBrutos(textoBruto);
        String textoQuebrado = quebrarLinhasPorPalavras(semBrutos, cfg.maxPalavrasPorLinha(), cfg.maxCaracteresPorLinha());
        List<SegmentoTextoFormatado> segmentos = segmentarComDestaque(textoQuebrado, cfg);
        String ansi = renderizarAnsi(segmentos);
        String html = renderizarHtml(segmentos, cfg);
        int palavras = contarPalavras(textoQuebrado);
        int linhas = textoQuebrado.isBlank() ? 0 : textoQuebrado.split("\n", -1).length;
        return new RespostaFormatadaIA(textoQuebrado, ansi, html, segmentos, palavras, linhas);
    }

    /**
     * Retorna a mensagem da IA já limpa, sem dados brutos e com quebras de linha aplicadas.
     */
    public static String formatarTextoParaIA(String textoBruto) {
        return formatar(textoBruto).textoPuroFormatado();
    }

    public static String formatarTextoParaIA(String textoBruto, int maxPalavrasPorLinha) {
        return formatar(textoBruto, ConfiguracaoFormatacaoSaida.comPalavrasPorLinha(maxPalavrasPorLinha))
                .textoPuroFormatado();
    }

    /**
     * Retorna o texto já tratado e com cores ANSI sutis para Cliente, Fornecedor, Pessoa, Produto e Notas.
     */
    public static String formatarParaTerminalAnsi(String textoBruto) {
        return formatar(textoBruto).textoAnsiColorido();
    }

    public static String formatarParaTerminalAnsi(String textoBruto, int maxPalavrasPorLinha) {
        return formatar(textoBruto, ConfiguracaoFormatacaoSaida.comPalavrasPorLinha(maxPalavrasPorLinha))
                .textoAnsiColorido();
    }

    /**
     * Retorna HTML estilizado (fonte, tamanho e cores sutis por entidade) para WebView JavaFX ou Mobile Server.
     */
    public static String formatarParaHtml(String textoBruto) {
        return formatar(textoBruto).htmlFormatado();
    }

    // ==================== CONTADOR DE PALAVRAS E QUEBRA DE LINHA ====================

    /**
     * Conta o total de palavras separadas por espaço/whitespace, ignorando códigos ANSI.
     */
    public static int contarPalavras(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        String limpo = removerAnsi(texto).trim();
        if (limpo.isEmpty()) {
            return 0;
        }
        return limpo.split("\\s+").length;
    }

    /**
     * Insere quebras de linha ({@code \n}) respeitando um limite máximo de palavras por linha
     * (contadas pelos espaços), preservando quebras intencionais e recuos de listas.
     */
    public static String quebrarLinhasPorPalavras(String texto, int maxPalavrasPorLinha) {
        return quebrarLinhasPorPalavras(texto, maxPalavrasPorLinha, PADRAO_CARACTERES_POR_LINHA);
    }

    public static String quebrarLinhasPorPalavras(String texto, int maxPalavrasPorLinha, int maxCaracteresPorLinha) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        int limitePalavras = Math.max(1, maxPalavrasPorLinha);
        int limiteChars = maxCaracteresPorLinha > 0 ? maxCaracteresPorLinha : Integer.MAX_VALUE;

        String normalizado = texto.replace("\r\n", "\n").replace("\r", "\n");
        String[] linhasOriginais = normalizado.split("\n", -1);
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < linhasOriginais.length; i++) {
            String linha = linhasOriginais[i];
            if (i > 0) {
                resultado.append('\n');
            }
            if (linha.isBlank()) {
                continue;
            }
            resultado.append(quebrarLinhaUnica(linha, limitePalavras, limiteChars));
        }
        return resultado.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String quebrarLinhaUnica(String linha, int limitePalavras, int limiteChars) {
        String recuoInicial = extrairRecuoInicial(linha);
        String conteudo = linha.substring(recuoInicial.length()).trim();
        if (conteudo.isEmpty()) {
            return linha;
        }

        // Preserva linhas decorativas de separador e quadros pré-formatados (ex: Bandeja de Contexto)
        if (conteudo.matches("^[=\\-─═]{4,}$")
                || conteudo.startsWith("╭")
                || conteudo.startsWith("│")
                || conteudo.startsWith("╰")
                || conteudo.startsWith("├")
                || conteudo.startsWith("╔")
                || conteudo.startsWith("║")
                || conteudo.startsWith("╚")) {
            return linha;
        }

        String[] palavras = conteudo.split("\\s+");
        if (palavras.length <= limitePalavras && removerAnsi(linha).length() <= limiteChars) {
            return linha;
        }

        String recuoContinuacao = calcularRecuoContinuacao(recuoInicial, conteudo);
        StringBuilder sb = new StringBuilder(recuoInicial);
        int palavrasNaLinha = 0;
        int charsNaLinha = recuoInicial.length();

        String palavraAnterior = "";
        for (String palavra : palavras) {
            int tamVisivel = removerAnsi(palavra).length();
            boolean excedeuPalavras = palavrasNaLinha >= limitePalavras;
            boolean excedeuChars = palavrasNaLinha > 0 && (charsNaLinha + 1 + tamVisivel) > limiteChars;
            boolean naoSeparar = "R$".equals(palavraAnterior) || palavraAnterior.endsWith(":");

            if ((excedeuPalavras || excedeuChars) && !naoSeparar) {
                sb.append('\n').append(recuoContinuacao);
                palavrasNaLinha = 0;
                charsNaLinha = recuoContinuacao.length();
            } else if (palavrasNaLinha > 0) {
                sb.append(' ');
                charsNaLinha += 1;
            }

            sb.append(palavra);
            palavraAnterior = removerAnsi(palavra);
            palavrasNaLinha++;
            charsNaLinha += tamVisivel;
        }
        return sb.toString();
    }

    private static String extrairRecuoInicial(String linha) {
        int idx = 0;
        while (idx < linha.length() && Character.isWhitespace(linha.charAt(idx))) {
            idx++;
        }
        return linha.substring(0, idx);
    }

    private static String calcularRecuoContinuacao(String recuoInicial, String conteudo) {
        if (conteudo.startsWith("• ") || conteudo.startsWith("- ") || conteudo.startsWith("* ")) {
            return recuoInicial + "  ";
        }
        return recuoInicial;
    }

    // ==================== TRATAMENTO DE DADOS BRUTOS (MAP / LIST<MAP> / TEXTO) ====================

    /**
     * Formata estruturas Java brutas ({@code List<Map>}, {@code Map}, {@code Collection}, {@code Object[]})
     * para texto humano estruturado, evitando {@code String.valueOf(dados)}.
     */
    public static String formatarDadoEstruturado(RotaSistema rota, Object dados) {
        if (dados == null) {
            return "Nenhum registro encontrado.";
        }
        if (dados instanceof String s) {
            return sanitizarTextoContraDadosBrutos(s);
        }
        if (dados instanceof List<?> lista) {
            if (lista.isEmpty()) {
                return "Nenhum registro encontrado para os critérios informados.";
            }
            if (lista.get(0) instanceof Map<?, ?>) {
                List<Map<String, Object>> mapas = new ArrayList<>();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> m) {
                        mapas.add(converterMapaParaStringObject(m));
                    }
                }
                return formatarListaMapas(rota, mapas);
            }
            StringBuilder sb = new StringBuilder("Resultados encontrados (" + lista.size() + "):\n");
            int idx = 1;
            for (Object item : lista) {
                sb.append("  ").append(idx++).append(". ").append(formatarValorSimples(item)).append('\n');
            }
            return sb.toString().trim();
        }
        if (dados instanceof Map<?, ?> mapa) {
            return formatarMapaGenerico(rota, converterMapaParaStringObject(mapa));
        }
        if (dados instanceof Object[] arr) {
            return formatarDadoEstruturado(rota, Arrays.asList(arr));
        }
        if (dados instanceof Collection<?> col) {
            return formatarDadoEstruturado(rota, new ArrayList<>(col));
        }
        return sanitizarTextoContraDadosBrutos(String.valueOf(dados));
    }

    /**
     * Detecta e converte qualquer bloco bruto {@code [{chave=valor, ...}]} ou {@code {chave=valor, ...}}
     * presente em uma string de saída da IA para texto formatado.
     */
    public static String sanitizarTextoContraDadosBrutos(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String atual = texto;

        Matcher mLista = PADRAO_LISTA_MAPAS_BRUTOS.matcher(atual);
        StringBuffer sbLista = new StringBuffer();
        boolean alterouLista = false;
        while (mLista.find()) {
            alterouLista = true;
            List<Map<String, Object>> mapas = parsearListaMapasBrutos(mLista.group());
            String formatado = mapas.isEmpty() ? mLista.group() : formatarListaMapas(null, mapas);
            mLista.appendReplacement(sbLista, Matcher.quoteReplacement(formatado));
        }
        if (alterouLista) {
            mLista.appendTail(sbLista);
            atual = sbLista.toString();
        }

        Matcher mUnico = PADRAO_MAPA_BRUTO_UNICO.matcher(atual);
        StringBuffer sbUnico = new StringBuffer();
        boolean alterouUnico = false;
        while (mUnico.find()) {
            alterouUnico = true;
            Map<String, Object> mapa = parsearMapaBruto(mUnico.group());
            String formatado = mapa.isEmpty() ? mUnico.group() : formatarListaMapas(null, List.of(mapa));
            mUnico.appendReplacement(sbUnico, Matcher.quoteReplacement(formatado));
        }
        if (alterouUnico) {
            mUnico.appendTail(sbUnico);
            atual = sbUnico.toString();
        }

        return atual;
    }

    private static List<Map<String, Object>> parsearListaMapasBrutos(String bruto) {
        List<Map<String, Object>> lista = new ArrayList<>();
        int nivel = 0;
        int inicio = -1;
        for (int i = 0; i < bruto.length(); i++) {
            char c = bruto.charAt(i);
            if (c == '{') {
                if (nivel == 0) {
                    inicio = i;
                }
                nivel++;
            } else if (c == '}') {
                nivel--;
                if (nivel == 0 && inicio >= 0) {
                    Map<String, Object> mapa = parsearMapaBruto(bruto.substring(inicio, i + 1));
                    if (!mapa.isEmpty()) {
                        lista.add(mapa);
                    }
                    inicio = -1;
                }
            }
        }
        return lista;
    }

    private static Map<String, Object> parsearMapaBruto(String bloco) {
        String interno = bloco.trim();
        if (interno.startsWith("{")) interno = interno.substring(1);
        if (interno.endsWith("}")) interno = interno.substring(0, interno.length() - 1);
        if (interno.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        String[] pares = PADRAO_SEPARADOR_CHAVE_VALOR.split(interno);
        for (String par : pares) {
            int eq = par.indexOf('=');
            if (eq > 0) {
                String chave = par.substring(0, eq).trim();
                String valor = par.substring(eq + 1).trim();
                mapa.put(chave, valor);
            }
        }
        return mapa;
    }

    private static String formatarListaMapas(RotaSistema rota, List<Map<String, Object>> mapas) {
        if (mapas == null || mapas.isEmpty()) {
            return "Nenhum registro encontrado para os critérios informados.";
        }
        Map<String, Object> amostra = mapas.get(0);

        if (amostra.containsKey("id_nota_perda") || amostra.containsKey("prejuizo_contabil_bacen")) {
            return formatarListaPerdasPrematuras(mapas);
        }
        if (amostra.containsKey("custo_medio_geral") || amostra.containsKey("sobrecusto_logistico_rios_pct")
                || amostra.containsKey("diagnostico_impacto_externo")) {
            return formatarListaImpactoExterno(mapas);
        }

        String titulo = rota != null
                ? "📋 " + rota.getDescricaoOperacao() + " (" + mapas.size() + " registro" + (mapas.size() == 1 ? "" : "s") + "):"
                : "📋 Registros encontrados (" + mapas.size() + "):";
        StringBuilder sb = new StringBuilder(titulo).append('\n');

        int idx = 1;
        for (Map<String, Object> item : mapas) {
            sb.append("\nRegistro ").append(idx++).append(":\n");
            for (Map.Entry<String, Object> entry : item.entrySet()) {
                if (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank()) {
                    continue;
                }
                sb.append("  • ").append(formatarRotuloCampo(entry.getKey()))
                  .append(": ").append(formatarValorPorChave(entry.getKey(), entry.getValue())).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String formatarListaPerdasPrematuras(List<Map<String, Object>> mapas) {
        BigDecimal prejuizoTotal = BigDecimal.ZERO;
        for (Map<String, Object> m : mapas) {
            prejuizoTotal = prejuizoTotal.add(paraBigDecimal(m.get("prejuizo_contabil_bacen")));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📉 Diagnóstico de Perdas Prematuras (").append(mapas.size())
          .append(" ocorrência").append(mapas.size() == 1 ? "" : "s")
          .append(" — Prejuízo total: R$ ").append(formatarMoeda(prejuizoTotal)).append("):\n");

        for (Map<String, Object> m : mapas) {
            sb.append("\nNota de Perda #").append(textoOuTraco(m.get("id_nota_perda")))
              .append(" — Data: ").append(formatarData(m.get("data_perda"))).append('\n');

            sb.append("  • Produto: #").append(textoOuTraco(m.get("id_produto")))
              .append(" ").append(textoOuTraco(m.get("nome_produto")))
              .append(" | Fornecedor: #").append(textoOuTraco(m.get("id_fornecedor")))
              .append(" ").append(textoOuTraco(m.get("nome_fornecedor"))).append('\n');

            sb.append("  • Quantidade perdida: ").append(formatarNumeroLimpo(m.get("quantidade_perdida")))
              .append(" | Prejuízo BACEN: R$ ").append(formatarMoeda(m.get("prejuizo_contabil_bacen"))).append('\n');

            sb.append("  • Validade: ").append(formatarData(m.get("data_validade")))
              .append(" (").append(textoOuTraco(m.get("dias_antes_de_vencer"))).append(" dias antes de vencer)")
              .append(" | Permanência pós-compra: ").append(textoOuTraco(m.get("dias_permanencia_apos_compra"))).append(" dias\n");

            sb.append("  • Clima na exposição: ").append(formatarNumeroLimpo(m.get("temperatura_exposicao_c"))).append("°C")
              .append(" | Chuva: ").append(formatarNumeroLimpo(m.get("precipitacao_exposicao_mm"))).append(" mm")
              .append(" | Vazão do rio: ").append(formatarNumeroLimpo(m.get("vazao_rio_transporte_m3s"))).append(" m³/s\n");

            sb.append("  • Pesos das causas: Calor ").append(formatarNumeroLimpo(m.get("peso_causa_calor_termico_pct"))).append("%")
              .append(" | Umidade/Chuva ").append(formatarNumeroLimpo(m.get("peso_causa_umidade_chuva_pct"))).append("%")
              .append(" | Rio ").append(formatarNumeroLimpo(m.get("peso_causa_transporte_rio_pct"))).append("%")
              .append(" | Fornecedor ").append(formatarNumeroLimpo(m.get("peso_causa_qualidade_fornecedor_pct"))).append("%\n");

            if (m.get("motivo_principal_diagnosticado") != null) {
                sb.append("  • Motivo principal: ").append(m.get("motivo_principal_diagnosticado")).append('\n');
            }
            if (m.get("acao_preventiva_recomendada") != null) {
                sb.append("  • Ação preventiva: ").append(formatarFraseCodigo(String.valueOf(m.get("acao_preventiva_recomendada")))).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String formatarListaImpactoExterno(List<Map<String, Object>> mapas) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Análise de Impacto Externo e Logística (").append(mapas.size())
          .append(" produto").append(mapas.size() == 1 ? "" : "s").append("):\n");

        for (Map<String, Object> m : mapas) {
            sb.append("\nProduto #").append(textoOuTraco(m.get("id_produto")))
              .append(" — ").append(textoOuTraco(m.get("nome_produto"))).append('\n');

            sb.append("  • Custos médios: Geral R$ ").append(formatarMoeda(m.get("custo_medio_geral")))
              .append(" | Seca/Vazante R$ ").append(formatarMoeda(m.get("custo_seca_rios_vazante")))
              .append(" | Nível Normal R$ ").append(formatarMoeda(m.get("custo_nivel_normal_rios")))
              .append(" | Cheia R$ ").append(formatarMoeda(m.get("custo_cheia_rios"))).append('\n');

            sb.append("  • Sobrecusto logístico fluvial: ")
              .append(formatarNumeroLimpo(m.get("sobrecusto_logistico_rios_pct"))).append("%\n");

            sb.append("  • Giro médio: Geral ").append(formatarNumeroLimpo(m.get("giro_medio_geral")))
              .append(" | Calor >30°C ").append(formatarNumeroLimpo(m.get("giro_medio_calor_30c")))
              .append(" | Chuva forte ").append(formatarNumeroLimpo(m.get("giro_medio_chuva_forte")))
              .append(" | Eventos ").append(formatarNumeroLimpo(m.get("giro_medio_eventos_ativos")))
              .append(" | 5º dia útil ").append(formatarNumeroLimpo(m.get("giro_medio_quinto_dia_util"))).append('\n');

            sb.append("  • Perdas acumuladas: Total ").append(formatarNumeroLimpo(m.get("qtd_total_perdida")))
              .append(" | Por clima adverso ").append(formatarNumeroLimpo(m.get("qtd_perdida_clima_adverso"))).append('\n');

            if (m.get("diagnostico_impacto_externo") != null) {
                sb.append("  • Diagnóstico externo: ").append(m.get("diagnostico_impacto_externo")).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String formatarMapaGenerico(RotaSistema rota, Map<String, Object> mapa) {
        if (mapa.isEmpty()) {
            return "Nenhum dado retornado.";
        }
        String titulo = rota != null ? "📋 " + rota.getDescricaoOperacao() + ":" : "📋 Detalhes do Resultado:";
        StringBuilder sb = new StringBuilder(titulo).append('\n');
        for (Map.Entry<String, Object> entry : mapa.entrySet()) {
            String chave = entry.getKey();
            Object val = entry.getValue();
            if (val == null) continue;
            if (val instanceof List<?> subLista) {
                sb.append("\n").append(formatarRotuloCampo(chave)).append(":\n");
                sb.append(formatarDadoEstruturado(null, subLista)).append('\n');
            } else if (val instanceof Map<?, ?> subMapa) {
                sb.append("\n").append(formatarRotuloCampo(chave)).append(":\n");
                sb.append(formatarMapaGenerico(null, converterMapaParaStringObject(subMapa))).append('\n');
            } else {
                sb.append("  • ").append(formatarRotuloCampo(chave)).append(": ")
                  .append(formatarValorPorChave(chave, val)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static Map<String, Object> converterMapaParaStringObject(Map<?, ?> origem) {
        Map<String, Object> destino = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : origem.entrySet()) {
            destino.put(String.valueOf(e.getKey()), e.getValue());
        }
        return destino;
    }

    private static String formatarRotuloCampo(String chave) {
        if (chave == null || chave.isBlank()) return "";
        String espacado = chave
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        String[] partes = espacado.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            String lower = p.toLowerCase(Locale.ROOT);
            if (lower.equals("id")) {
                sb.append("ID");
            } else if (lower.equals("bacen")) {
                sb.append("BACEN");
            } else if (lower.equals("pct")) {
                sb.append("(%)");
            } else {
                sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return sb.toString();
    }

    private static String formatarValorPorChave(String chave, Object valor) {
        if (valor == null) return "-";
        String k = chave != null ? chave.toLowerCase(Locale.ROOT) : "";
        if (k.contains("prejuizo") || k.contains("custo") || k.contains("valor") || k.contains("preco")
                || k.contains("limite") || k.contains("saldo") || k.contains("receita") || k.contains("faturamento")) {
            if (!k.contains("quantidade") && !k.contains("qtd") && !k.contains("pct")) {
                return "R$ " + formatarMoeda(valor);
            }
        }
        if (k.endsWith("_pct") || k.contains("percentual")) {
            return formatarNumeroLimpo(valor) + "%";
        }
        if (k.contains("temperatura") || k.endsWith("_c")) {
            return formatarNumeroLimpo(valor) + "°C";
        }
        if (k.contains("precipitacao") || k.endsWith("_mm")) {
            return formatarNumeroLimpo(valor) + " mm";
        }
        if (k.contains("vazao") || k.endsWith("_m3s")) {
            return formatarNumeroLimpo(valor) + " m³/s";
        }
        if (k.contains("data")) {
            return formatarData(valor);
        }
        if (valor instanceof Number || String.valueOf(valor).matches("-?\\d+\\.\\d+")) {
            return formatarNumeroLimpo(valor);
        }
        return String.valueOf(valor);
    }

    private static String formatarValorSimples(Object valor) {
        if (valor == null) return "-";
        if (valor instanceof Map<?, ?> m) {
            return formatarMapaGenerico(null, converterMapaParaStringObject(m));
        }
        return String.valueOf(valor);
    }

    private static String formatarFraseCodigo(String texto) {
        if (texto == null || texto.isBlank()) return "";
        String t = texto.trim();
        if (t.equals(t.toUpperCase(Locale.ROOT)) && t.length() > 8) {
            String lower = t.toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
        return t;
    }

    private static String formatarData(Object val) {
        if (val == null) return "-";
        String s = String.valueOf(val).trim();
        try {
            if (s.length() >= 16 && s.contains("T")) {
                return LocalDateTime.parse(s.substring(0, 16)).format(FMT_DATA_HORA_BR);
            }
            if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                return LocalDate.parse(s).format(FMT_DATA_BR);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    private static BigDecimal paraBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(val).replace(",", ".").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatarMoeda(Object val) {
        BigDecimal bd = paraBigDecimal(val).setScale(2, RoundingMode.HALF_EVEN);
        return bd.toPlainString().replace('.', ',');
    }

    private static String formatarNumeroLimpo(Object val) {
        if (val == null) return "-";
        String s = String.valueOf(val).trim();
        try {
            BigDecimal bd = new BigDecimal(s.replace(",", ".")).stripTrailingZeros();
            if (bd.scale() < 0) {
                bd = bd.setScale(0, RoundingMode.UNNECESSARY);
            }
            return bd.toPlainString();
        } catch (Exception e) {
            return s;
        }
    }

    private static String textoOuTraco(Object val) {
        if (val == null) return "-";
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? "-" : s;
    }

    // ==================== SEGMENTAÇÃO DE FONTES, TAMANHOS E CORES ====================

    /**
     * Segmenta o texto já formatado em blocos com cor, família de fonte e tamanho
     * para renderização em JavaFX ({@code TextFlow}), HTML ou Terminal ANSI.
     */
    public static List<SegmentoTextoFormatado> segmentarComDestaque(String textoFormatado, ConfiguracaoFormatacaoSaida config) {
        if (textoFormatado == null || textoFormatado.isEmpty()) {
            return List.of();
        }
        ConfiguracaoFormatacaoSaida cfg = config != null ? config : ConfiguracaoFormatacaoSaida.padrao();
        List<SegmentoTextoFormatado> segmentos = new ArrayList<>();
        String[] linhas = textoFormatado.split("\n", -1);

        for (int i = 0; i < linhas.length; i++) {
            if (i > 0) {
                segmentos.add(criarSegmento("\n", TipoEntidadeDestaque.TEXTO_COMUM, cfg));
            }
            String linha = removerAnsi(linhas[i]);
            if (linha.isEmpty()) {
                continue;
            }
            if (ehLinhaTitulo(linha)) {
                segmentos.add(criarSegmento(linha, TipoEntidadeDestaque.TITULO, cfg));
                continue;
            }
            if (!cfg.aplicarCoresEntidades()) {
                segmentos.add(criarSegmento(linha, TipoEntidadeDestaque.TEXTO_COMUM, cfg));
                continue;
            }
            segmentarLinhaComEntidades(linha, cfg, segmentos);
        }
        return Collections.unmodifiableList(segmentos);
    }

    private static boolean ehLinhaTitulo(String linha) {
        String trim = linha.trim();
        return trim.startsWith("📉") || trim.startsWith("📊") || trim.startsWith("📋")
                || trim.startsWith("📦") || trim.startsWith("👤") || trim.startsWith("🧾")
                || trim.startsWith("💳") || trim.startsWith("===");
    }

    private static void segmentarLinhaComEntidades(
            String linha,
            ConfiguracaoFormatacaoSaida cfg,
            List<SegmentoTextoFormatado> destino) {

        Matcher m = PADRAO_DESTAQUE.matcher(linha);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) {
                destino.add(criarSegmento(linha.substring(pos, m.start()), TipoEntidadeDestaque.TEXTO_COMUM, cfg));
            }
            String trecho = m.group();
            TipoEntidadeDestaque tipo = classificarTrecho(trecho, m);
            destino.add(criarSegmento(trecho, tipo, cfg));
            pos = m.end();
        }
        if (pos < linha.length()) {
            destino.add(criarSegmento(linha.substring(pos), TipoEntidadeDestaque.TEXTO_COMUM, cfg));
        }
    }

    private static TipoEntidadeDestaque classificarTrecho(String trecho, Matcher m) {
        if (m.group(1) != null) {
            return TipoEntidadeDestaque.VALOR_MONETARIO;
        }
        if (m.group(3) != null) {
            return TipoEntidadeDestaque.IDENTIFICADOR;
        }
        if (m.group(4) != null) {
            return TipoEntidadeDestaque.ALERTA;
        }
        String norm = Normalizer.normalize(trecho, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
        if (norm.startsWith("cliente")) return TipoEntidadeDestaque.CLIENTE;
        if (norm.startsWith("fornecedor")) return TipoEntidadeDestaque.FORNECEDOR;
        if (norm.startsWith("pessoa")) return TipoEntidadeDestaque.PESSOA;
        if (norm.startsWith("produto")) return TipoEntidadeDestaque.PRODUTO;
        if (norm.startsWith("nota")) return TipoEntidadeDestaque.NOTA;
        return TipoEntidadeDestaque.TEXTO_COMUM;
    }

    private static SegmentoTextoFormatado criarSegmento(
            String texto,
            TipoEntidadeDestaque tipo,
            ConfiguracaoFormatacaoSaida cfg) {

        String fonte = (tipo == TipoEntidadeDestaque.VALOR_MONETARIO || tipo == TipoEntidadeDestaque.IDENTIFICADOR)
                ? cfg.fonteMonoespacada()
                : cfg.fontePrincipal();
        int tamanho = switch (tipo) {
            case TITULO -> cfg.tamanhoTituloPt();
            case SUBTITULO -> Math.max(cfg.tamanhoCorpoPt() + 1, cfg.tamanhoTituloPt() - 2);
            case IDENTIFICADOR -> Math.max(11, cfg.tamanhoCorpoPt() - 1);
            default -> cfg.tamanhoCorpoPt();
        };
        return new SegmentoTextoFormatado(
                texto,
                tipo,
                tipo.getCorHex(),
                fonte,
                tamanho,
                tipo.isNegritoPadrao()
        );
    }

    // ==================== RENDERIZADORES (ANSI E HTML) ====================

    private static String renderizarAnsi(List<SegmentoTextoFormatado> segmentos) {
        StringBuilder sb = new StringBuilder();
        for (SegmentoTextoFormatado seg : segmentos) {
            if (seg.tipo() == TipoEntidadeDestaque.TEXTO_COMUM || seg.tipo().getCodigoAnsi().isEmpty()) {
                sb.append(seg.texto());
            } else {
                sb.append(seg.tipo().getCodigoAnsi()).append(seg.texto()).append(ANSI_RESET);
            }
        }
        return sb.toString();
    }

    private static String renderizarHtml(List<SegmentoTextoFormatado> segmentos, ConfiguracaoFormatacaoSaida cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'").append(escaparHtml(cfg.fontePrincipal()))
          .append("', sans-serif; font-size:").append(cfg.tamanhoCorpoPt())
          .append("pt; line-height:1.45; white-space:pre-wrap;\">");

        for (SegmentoTextoFormatado seg : segmentos) {
            if ("\n".equals(seg.texto())) {
                sb.append("<br>");
                continue;
            }
            String conteudo = escaparHtml(seg.texto());
            if (seg.tipo() == TipoEntidadeDestaque.TEXTO_COMUM) {
                sb.append(conteudo);
            } else {
                sb.append("<span style=\"color:").append(seg.corHex())
                  .append("; font-family:'").append(escaparHtml(seg.fonteFamilia())).append("'")
                  .append("; font-size:").append(seg.tamanhoFontePt()).append("pt")
                  .append(seg.negrito() ? "; font-weight:600" : "")
                  .append(";\">").append(conteudo).append("</span>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String escaparHtml(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String removerAnsi(String texto) {
        if (texto == null) return "";
        return PADRAO_ANSI.matcher(texto).replaceAll("");
    }
}
