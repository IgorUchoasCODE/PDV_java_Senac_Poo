package br.com.senac.view.javafx.util;

import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de mapeamento visual e comandos expressivos por coluna CSV.
 * Suporta a sintaxe personalizada solicitada para varrer toda uma coluna e distribuir/transformar valores:
 * <pre>
 *   C= @col="Produto" -> id>, nm>
 *   C= @col="Preco"   -> val> * 1.15
 *   C= @col="Forn"    -> forn>
 *   C= @col="Qtd"     -> qtd>
 * </pre>
 */
public final class ProcessadorExpressaoColunaCsv {

    public record ResultadoProcessamentoCsv(
            TabelaCsv tabela,
            int linhasTransformadas,
            List<String> logsAplicacao
    ) {}

    private static final Pattern PADRAO_COMANDO = Pattern.compile(
            "C=\\s*@col\\s*=\\s*\"([^\"]+)\"\\s*->\\s*([^;\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PADRAO_ARITMETICA = Pattern.compile(
            "^([a-zA-Z_]+>)\\s*([*+\\-/])\\s*(-?\\d+(?:[.,]\\d+)?)$"
    );

    private static final Map<String, String> ALIASES_DESTINO = Map.ofEntries(
            Map.entry("id>", "idProduto"),
            Map.entry("idproduto>", "idProduto"),
            Map.entry("nm>", "nome"),
            Map.entry("nome>", "nome"),
            Map.entry("prod>", "nome"),
            Map.entry("produto>", "nome"),
            Map.entry("val>", "precoVenda"),
            Map.entry("preco>", "precoVenda"),
            Map.entry("valor>", "valorUnitario"),
            Map.entry("custo>", "valorUnitario"),
            Map.entry("forn>", "fornecedor"),
            Map.entry("fornecedor>", "fornecedor"),
            Map.entry("cli>", "cliente"),
            Map.entry("cliente>", "cliente"),
            Map.entry("qtd>", "quantidade"),
            Map.entry("quant>", "quantidade"),
            Map.entry("um>", "siglaUm"),
            Map.entry("desc>", "descricao"),
            Map.entry("validade>", "dataValidade"),
            Map.entry("doc>", "documento"),
            Map.entry("tel>", "telefone"),
            Map.entry("email>", "email"),
            Map.entry("papel>", "papel")
    );

    private ProcessadorExpressaoColunaCsv() {}

    /**
     * Lista os campos de destino mais usados nas rotas CSV do ERP para o ComboBox de mapeamento visual.
     */
    public static List<String> camposDestinoDisponiveis() {
        return List.of(
                "IGNORAR",
                "nome",
                "idProduto",
                "precoVenda",
                "valorUnitario",
                "quantidade",
                "siglaUm",
                "fornecedor",
                "cliente",
                "descricao",
                "dataValidade",
                "codigoForma",
                "parcelas",
                "documento",
                "papel",
                "telefone",
                "email",
                "logradouro",
                "cidade",
                "uf"
        );
    }

    /**
     * Sugere automaticamente um campo do ERP para um cabeçalho CSV bruto.
     */
    public static String sugerirCampoDestino(String cabecalhoCsv) {
        if (cabecalhoCsv == null) return "IGNORAR";
        String k = cabecalhoCsv.toLowerCase(Locale.ROOT).trim();
        if (k.contains("id_prod") || k.equals("id") || k.equals("codigo")) return "idProduto";
        if (k.contains("nome") || k.contains("prod") || k.contains("item")) return "nome";
        if (k.contains("preco") || k.contains("preço")) return "precoVenda";
        if (k.contains("custo") || k.contains("valor")) return "valorUnitario";
        if (k.contains("qtd") || k.contains("quant") || k.contains("estoque")) return "quantidade";
        if (k.contains("forn")) return "fornecedor";
        if (k.contains("cli")) return "cliente";
        if (k.contains("un") || k.contains("medida") || k.equals("um")) return "siglaUm";
        if (k.contains("desc")) return "descricao";
        if (k.contains("valid")) return "dataValidade";
        if (k.contains("cpf") || k.contains("cnpj") || k.contains("doc")) return "documento";
        if (k.contains("tel") || k.contains("cel") || k.contains("fone")) return "telefone";
        if (k.contains("mail")) return "email";
        if (k.contains("papel") || k.contains("tipo_pessoa")) return "papel";
        return cabecalhoCsv;
    }

    /**
     * Aplica o mapeamento visual de colunas seguido dos comandos em lote C= @col="..." -> ...
     */
    public static ResultadoProcessamentoCsv aplicar(
            TabelaCsv original,
            Map<String, String> mapeamentoVisual,
            String comandosExpressao) {
        if (original == null) {
            return new ResultadoProcessamentoCsv(new TabelaCsv(';', List.of(), List.of()), 0, List.of());
        }

        List<String> logs = new ArrayList<>();
        Set<String> headers = new LinkedHashSet<>();

        // 1. Copia aplicando o mapeamento visual coluna -> campoDestino
        List<Map<String, String>> linhas = new ArrayList<>();
        for (Map<String, String> row : original.linhas()) {
            Map<String, String> nova = new LinkedHashMap<>();
            for (String hOrig : original.cabecalhos()) {
                String dest = mapeamentoVisual != null ? mapeamentoVisual.getOrDefault(hOrig, hOrig) : hOrig;
                if (dest != null && !dest.isBlank() && !"IGNORAR".equalsIgnoreCase(dest.trim())) {
                    String campo = dest.trim();
                    headers.add(campo);
                    nova.put(campo, row.getOrDefault(hOrig, ""));
                }
            }
            // Preserva originais ainda não ignoradas caso comando @col aponte pelo nome original do cabeçalho
            linhas.add(nova);
        }

        int transformadas = 0;
        if (comandosExpressao != null && !comandosExpressao.isBlank()) {
            Matcher matcher = PADRAO_COMANDO.matcher(comandosExpressao);
            while (matcher.find()) {
                String nomeColOrig = matcher.group(1).trim();
                String exprDestino = matcher.group(2).trim();
                String[] alvosBrutos = exprDestino.split(",");

                int indiceColOrig = localizarIndiceHeader(original.cabecalhos(), nomeColOrig);
                String chaveRealOrig = indiceColOrig >= 0 ? original.cabecalhos().get(indiceColOrig) : nomeColOrig;

                for (int r = 0; r < original.linhas().size(); r++) {
                    String valBruto = obterValorPorNomeOuMapeado(original.linhas().get(r), linhas.get(r), chaveRealOrig, mapeamentoVisual);
                    if (valBruto == null) valBruto = "";

                    if (alvosBrutos.length > 1) {
                        // Divide célula composta (ex.: "101 - Água Mineral" ou "101;Água Mineral") em múltiplos campos
                        String[] partes = valBruto.split("\\s*(?:\\|\\||\\||;| - |\\/|:)\\s*", alvosBrutos.length);
                        if (partes.length < alvosBrutos.length && valBruto.contains(" ")) {
                            partes = valBruto.trim().split("\\s+", alvosBrutos.length);
                        }
                        for (int a = 0; a < alvosBrutos.length; a++) {
                            String tokenAlvo = alvosBrutos[a].trim();
                            String valorParte = a < partes.length ? partes[a].trim() : "";
                            if (a == 0 && tokenAlvo.toLowerCase(Locale.ROOT).startsWith("id")) {
                                valorParte = valorParte.replace("#", "").trim();
                            }
                            aplicarAtribuicaoAlvo(tokenAlvo, valorParte, linhas.get(r), headers);
                        }
                        transformadas++;
                    } else if (alvosBrutos.length == 1) {
                        String tokenAlvo = alvosBrutos[0].trim();
                        aplicarAtribuicaoAlvo(tokenAlvo, valBruto.trim(), linhas.get(r), headers);
                        transformadas++;
                    }
                }
                logs.add("Comando aplicado: @col=\"" + chaveRealOrig + "\" -> " + exprDestino
                        + " (" + original.linhas().size() + " linhas)");
            }
        }

        TabelaCsv tabelaFinal = new TabelaCsv(
                original.delimitadorDetectado(),
                new ArrayList<>(headers),
                linhas
        );
        return new ResultadoProcessamentoCsv(tabelaFinal, transformadas, logs);
    }

    private static void aplicarAtribuicaoAlvo(
            String tokenAlvo,
            String valorOrigem,
            Map<String, String> destinoRow,
            Set<String> headers) {
        Matcher mMat = PADRAO_ARITMETICA.matcher(tokenAlvo);
        if (mMat.matches()) {
            String alias = mMat.group(1).trim();
            String op = mMat.group(2);
            String fatorTxt = mMat.group(3).replace(",", ".");
            String campoReal = resolverCampo(alias);
            String valFinal = aplicarOperacaoNumerica(valorOrigem, op, fatorTxt);
            headers.add(campoReal);
            destinoRow.put(campoReal, valFinal);
            return;
        }
        String campoReal = resolverCampo(tokenAlvo);
        headers.add(campoReal);
        destinoRow.put(campoReal, valorOrigem);
    }

    private static String resolverCampo(String aliasOuNome) {
        if (aliasOuNome == null) return "campo";
        String key = aliasOuNome.trim().toLowerCase(Locale.ROOT);
        if (ALIASES_DESTINO.containsKey(key)) return ALIASES_DESTINO.get(key);
        if (key.endsWith(">")) return aliasOuNome.trim().substring(0, aliasOuNome.trim().length() - 1);
        return aliasOuNome.trim();
    }

    private static String aplicarOperacaoNumerica(String bruto, String op, String fatorTxt) {
        try {
            String numLimpo = bruto.replace("R$", "").replace(" ", "").trim();
            if (numLimpo.contains(",") && numLimpo.contains(".")) {
                numLimpo = numLimpo.replace(".", "").replace(",", ".");
            } else if (numLimpo.contains(",")) {
                numLimpo = numLimpo.replace(",", ".");
            }
            BigDecimal v = new BigDecimal(numLimpo);
            BigDecimal f = new BigDecimal(fatorTxt);
            BigDecimal res = switch (op) {
                case "*" -> v.multiply(f);
                case "/" -> f.compareTo(BigDecimal.ZERO) == 0 ? v : v.divide(f, 2, RoundingMode.HALF_UP);
                case "+" -> v.add(f);
                case "-" -> v.subtract(f);
                default -> v;
            };
            return res.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return bruto;
        }
    }

    private static int localizarIndiceHeader(List<String> headers, String alvo) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(alvo)) return i;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase(Locale.ROOT).contains(alvo.toLowerCase(Locale.ROOT))) return i;
        }
        return -1;
    }

    private static String obterValorPorNomeOuMapeado(
            Map<String, String> origRow,
            Map<String, String> mappedRow,
            String nomeOrig,
            Map<String, String> mapeamentoVisual) {
        if (origRow.containsKey(nomeOrig)) return origRow.get(nomeOrig);
        if (mapeamentoVisual != null && mapeamentoVisual.containsKey(nomeOrig)) {
            String mappedCol = mapeamentoVisual.get(nomeOrig);
            if (mappedRow.containsKey(mappedCol)) return mappedRow.get(mappedCol);
        }
        return mappedRow.getOrDefault(nomeOrig, "");
    }
}
