package br.com.senac.repository.dao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Utilitário de busca inteligente por palavras parciais e iniciais
 * exclusivo para Produto e Pessoa (Cliente / Fornecedor).
 *
 * Exemplos suportados:
 * - "ov branc tip a" -> "Ovo Branco Tipo A"
 * - "o b a"          -> "Ovo Branco Tipo A"
 * - "o b t a"        -> "Ovo Branco Tipo A"
 * - "rob alm"        -> "Roberto Almeida Silva"
 */
public final class BuscaPalavrasParciaisUtil {

    private BuscaPalavrasParciaisUtil() {
    }

    /**
     * Normaliza texto removendo acentos, convertendo para minúsculas e padronizando espaços.
     */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Verifica se o nome do registro é um match exato (ignorando caixa e acentuação) com o termo buscado.
     */
    public static boolean isMatchExato(String nomeAlvo, String termoBusca) {
        String alvo = normalizar(nomeAlvo);
        String termo = normalizar(termoBusca);
        return !alvo.isEmpty() && alvo.equals(termo);
    }

    /**
     * Calcula a pontuação de correspondência entre o nome do alvo (Produto/Cliente/Fornecedor) e o termo buscado.
     * Retorna 0 caso não haja correspondência.
     *
     * Níveis de pontuação:
     * - 1000: Match exato completo
     * -  900: Prefixo contínuo do nome completo
     * -  800: Substring contínua do nome completo
     * -  750: Todas as palavras parciais/iniciais batem na ordem exata com todas as palavras do nome ("ov branc tip a" -> "Ovo Branco Tipo A")
     * -  650: Palavras parciais/iniciais batem em ordem (podendo pular palavras intermediárias, ex: "o b a" -> "Ovo Branco Tipo A")
     * -  550: Todas as palavras parciais/iniciais batem com prefixos de palavras do nome fora de ordem
     * -  450: Termos parciais (>= 2 letras) contidos dentro das palavras do nome
     * -  400: Sigla compacta de iniciais ("obta" ou "oba" -> "Ovo Branco Tipo A")
     */
    public static int calcularPontuacaoMatch(String nomeAlvo, String termoBusca) {
        String alvo = normalizar(nomeAlvo);
        String termo = normalizar(termoBusca);
        if (alvo.isEmpty() || termo.isEmpty()) {
            return 0;
        }

        if (alvo.equals(termo)) {
            return 1000;
        }
        if (alvo.startsWith(termo)) {
            return 900;
        }
        if (alvo.contains(termo)) {
            return 800;
        }

        String[] palavrasAlvo = alvo.split("\\s+");
        String[] tokensBusca = termo.split("\\s+");

        // 1. Correspondência ordenada de prefixos de palavras (ex: "ov branc tip a" ou "o b a" -> "ovo branco tipo a")
        if (correspondePrefixosEmOrdem(palavrasAlvo, tokensBusca)) {
            return tokensBusca.length == palavrasAlvo.length ? 750 : 650;
        }

        // 2. Correspondência de prefixos de palavras em qualquer ordem
        if (correspondePrefixosQualquerOrdem(palavrasAlvo, tokensBusca)) {
            return 550;
        }

        // 3. Correspondência parcial interna nas palavras (para tokens com >= 2 caracteres)
        if (correspondeSubstringsPalavras(palavrasAlvo, tokensBusca)) {
            return 450;
        }

        // 4. Sigla compacta de iniciais (ex: "obta", ">oba", ">otba" -> "Ovo Branco Tipo A")
        if (tokensBusca.length == 1 && termo.length() >= 2 && termo.length() <= palavrasAlvo.length) {
            if (correspondeIniciaisCompactasEmOrdem(palavrasAlvo, termo)) {
                return termoBusca != null && termoBusca.trim().startsWith(">") ? 700 : 400;
            }
            if (correspondeIniciaisCompactasQualquerOrdem(palavrasAlvo, termo)) {
                return termoBusca != null && termoBusca.trim().startsWith(">") ? 600 : 380;
            }
        }

        return 0;
    }

    /**
     * Identifica se o termo informado usa notação explícita de sigla do encurtador:
     * ex: ">isu", ">oba", ">otba", "i.s.u", "o.b.a" ou iniciais separadas por espaço ("i s u").
     */
    public static boolean isFormatoSiglaExplicita(String termoBusca) {
        if (termoBusca == null) return false;
        String t = termoBusca.trim();
        if (t.startsWith(">") && t.length() >= 2) return true;
        if (t.contains(".") && t.replace(".", "").trim().length() >= 2) return true;
        String[] partes = t.split("\\s+");
        if (partes.length >= 2) {
            boolean todasCurta = true;
            for (String p : partes) {
                if (p.length() > 2) {
                    todasCurta = false;
                    break;
                }
            }
            return todasCurta;
        }
        return false;
    }

    private static boolean correspondeIniciaisCompactasQualquerOrdem(String[] palavrasAlvo, String sigla) {
        boolean[] usadas = new boolean[palavrasAlvo.length];
        for (int i = 0; i < sigla.length(); i++) {
            char c = sigla.charAt(i);
            boolean achou = false;
            for (int j = 0; j < palavrasAlvo.length; j++) {
                if (!usadas[j] && !palavrasAlvo[j].isEmpty() && palavrasAlvo[j].charAt(0) == c) {
                    usadas[j] = true;
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                return false;
            }
        }
        return true;
    }

    private static boolean tokenCombinaPrefixoPalavra(String palavraAlvo, String tokenBusca) {
        if (palavraAlvo.startsWith(tokenBusca)) {
            return true;
        }
        // Tolerância singular/plural simples (ex: "ovos" -> "ovo")
        if (palavraAlvo.length() >= 3 && tokenBusca.length() == palavraAlvo.length() + 1
                && tokenBusca.endsWith("s") && tokenBusca.startsWith(palavraAlvo)) {
            return true;
        }
        return false;
    }

    private static boolean correspondePrefixosEmOrdem(String[] palavrasAlvo, String[] tokensBusca) {
        if (tokensBusca.length > palavrasAlvo.length) {
            return false;
        }
        int idxPalavra = 0;
        for (String token : tokensBusca) {
            boolean achou = false;
            while (idxPalavra < palavrasAlvo.length) {
                if (tokenCombinaPrefixoPalavra(palavrasAlvo[idxPalavra], token)) {
                    achou = true;
                    idxPalavra++;
                    break;
                }
                idxPalavra++;
            }
            if (!achou) {
                return false;
            }
        }
        return true;
    }

    private static boolean correspondePrefixosQualquerOrdem(String[] palavrasAlvo, String[] tokensBusca) {
        if (tokensBusca.length > palavrasAlvo.length) {
            return false;
        }
        boolean[] usadas = new boolean[palavrasAlvo.length];
        for (String token : tokensBusca) {
            boolean achou = false;
            for (int i = 0; i < palavrasAlvo.length; i++) {
                if (!usadas[i] && tokenCombinaPrefixoPalavra(palavrasAlvo[i], token)) {
                    usadas[i] = true;
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                return false;
            }
        }
        return true;
    }

    private static boolean correspondeSubstringsPalavras(String[] palavrasAlvo, String[] tokensBusca) {
        if (tokensBusca.length > palavrasAlvo.length) {
            return false;
        }
        boolean algumTokenLongo = false;
        boolean[] usadas = new boolean[palavrasAlvo.length];
        for (String token : tokensBusca) {
            if (token.length() >= 2) {
                algumTokenLongo = true;
            }
            boolean achou = false;
            for (int i = 0; i < palavrasAlvo.length; i++) {
                if (usadas[i]) continue;
                boolean match = token.length() == 1
                        ? palavrasAlvo[i].startsWith(token)
                        : (palavrasAlvo[i].contains(token) || tokenCombinaPrefixoPalavra(palavrasAlvo[i], token));
                if (match) {
                    usadas[i] = true;
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                return false;
            }
        }
        return algumTokenLongo;
    }

    private static boolean correspondeIniciaisCompactasEmOrdem(String[] palavrasAlvo, String sigla) {
        int idxPalavra = 0;
        for (int i = 0; i < sigla.length(); i++) {
            char c = sigla.charAt(i);
            boolean achou = false;
            while (idxPalavra < palavrasAlvo.length) {
                if (!palavrasAlvo[idxPalavra].isEmpty() && palavrasAlvo[idxPalavra].charAt(0) == c) {
                    achou = true;
                    idxPalavra++;
                    break;
                }
                idxPalavra++;
            }
            if (!achou) {
                return false;
            }
        }
        return true;
    }

    /**
     * Filtra e ordena uma coleção de itens (Produtos, Clientes ou Fornecedores) pela pontuação de correspondência
     * parcial/iniciais do nome.
     */
    public static <T> List<T> filtrarEOrdenar(List<T> candidatos, Function<T, String> extratorNome, String termoBusca) {
        if (candidatos == null || candidatos.isEmpty() || termoBusca == null || termoBusca.isBlank()) {
            return List.of();
        }
        record ItemPontuado<E>(E item, int pontuacao, int comprimentoNome) {}
        List<ItemPontuado<T>> aprovados = new ArrayList<>();

        for (T item : candidatos) {
            if (item == null) continue;
            String nome = extratorNome.apply(item);
            int score = calcularPontuacaoMatch(nome, termoBusca);
            if (score > 0) {
                aprovados.add(new ItemPontuado<>(item, score, nome != null ? nome.length() : Integer.MAX_VALUE));
            }
        }

        aprovados.sort(Comparator.<ItemPontuado<T>>comparingInt(ItemPontuado::pontuacao).reversed()
                .thenComparingInt(ItemPontuado::comprimentoNome));

        List<T> resultado = new ArrayList<>(aprovados.size());
        for (ItemPontuado<T> ip : aprovados) {
            resultado.add(ip.item());
        }
        return resultado;
    }
}
