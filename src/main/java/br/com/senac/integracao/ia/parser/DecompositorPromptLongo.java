package br.com.senac.integracao.ia.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Analisa prompts extensos em linguagem natural e os decompõe automaticamente em sub-prompts
 * menores ordenados por dependência de execução:
 * <ol>
 *   <li>Unidades de medida e formas de pagamento</li>
 *   <li>Pessoas (fornecedores e clientes)</li>
 *   <li>Produtos simples (físicos e digitais)</li>
 *   <li>Kits / conjuntos e vínculos de componentes</li>
 *   <li>Notas fiscais e movimentações financeiras/estoque</li>
 * </ol>
 */
public final class DecompositorPromptLongo {

    public static final int TAMANHO_MINIMO_PROMPT_LONGO = 320;
    public static final int ETAPAS_MINIMAS_DECOMPOSICAO = 3;

    private static final Pattern CONECTOR_INICIO = Pattern.compile(
            "^(?:por\\s+fim|finalmente|e\\s+tamb[eé]m|tamb[eé]m|al[eé]m\\s+disso|depois|em\\s+seguida|agora)\\s*,?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CONECTOR_MEIO_CADASTRO = Pattern.compile(
            "\\s+e\\s+(?:tamb[eé]m\\s+)?(?=(?:o\\s+cliente|o\\s+fornecedor|cadastre|crie|registre|emita|lance|adicion|vincule))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public enum CamadaExecucao {
        BASE_ESTRUTURAL(1, "Unidades e Formas de Pagamento"),
        PESSOAS(2, "Fornecedores e Clientes"),
        PRODUTOS_SIMPLES(3, "Produtos Simples"),
        KITS_E_VINCULOS(4, "Kits e Composição"),
        NOTAS_E_MOVIMENTACOES(5, "Notas Fiscais e Movimentações");

        private final int ordem;
        private final String titulo;

        CamadaExecucao(int ordem, String titulo) {
            this.ordem = ordem;
            this.titulo = titulo;
        }

        public int ordem() {
            return ordem;
        }

        public String titulo() {
            return titulo;
        }
    }

    public record EtapaPrompt(int ordem, CamadaExecucao camada, String titulo, String prompt) {
    }

    public record ResultadoDecomposicao(boolean promptLongo, List<EtapaPrompt> etapas, String avisoUsuario) {
    }

    private DecompositorPromptLongo() {
    }

    public static boolean isPromptMuitoLongo(String mensagem) {
        if (mensagem == null) return false;
        String t = mensagem.trim();
        if (t.length() < TAMANHO_MINIMO_PROMPT_LONGO || t.startsWith("$$")) {
            return false;
        }
        return decompor(t).etapas().size() >= ETAPAS_MINIMAS_DECOMPOSICAO;
    }

    public static ResultadoDecomposicao decompor(String mensagem) {
        if (mensagem == null || mensagem.isBlank() || mensagem.trim().startsWith("$$")) {
            return new ResultadoDecomposicao(false, List.of(), "");
        }
        String texto = mensagem.trim().replaceAll("\\s+", " ");
        if (texto.length() < TAMANHO_MINIMO_PROMPT_LONGO) {
            return new ResultadoDecomposicao(false, List.of(), "");
        }

        List<String> clausulas = fatiarClausulas(texto);
        if (clausulas.size() < ETAPAS_MINIMAS_DECOMPOSICAO) {
            return new ResultadoDecomposicao(false, List.of(), "");
        }

        Map<CamadaExecucao, List<String>> agrupado = new LinkedHashMap<>();
        for (CamadaExecucao c : CamadaExecucao.values()) {
            agrupado.put(c, new ArrayList<>());
        }
        for (String clausula : clausulas) {
            String limpa = CONECTOR_INICIO.matcher(clausula.trim()).replaceFirst("").trim();
            if (limpa.isEmpty()) continue;
            String limpaLower = limpa.toLowerCase(Locale.ROOT);
            if ((limpaLower.matches("^\\d+\\s+produto.*") || limpaLower.startsWith("produto "))
                    && !limpaLower.startsWith("cadastre")) {
                limpa = "Cadastre " + limpa;
            }
            CamadaExecucao camada = classificarCamada(limpa);
            agrupado.get(camada).add(limpa);
        }

        List<EtapaPrompt> etapas = new ArrayList<>();
        int idx = 1;
        for (CamadaExecucao camada : CamadaExecucao.values()) {
            List<String> itensCamada = agrupado.get(camada);
            if (itensCamada == null || itensCamada.isEmpty()) continue;
            String joined = String.join(". ", itensCamada);
            if (!joined.endsWith(".")) joined = joined + ".";
            etapas.add(new EtapaPrompt(idx++, camada, camada.titulo(), joined));
        }

        boolean longo = etapas.size() >= ETAPAS_MINIMAS_DECOMPOSICAO;
        if (!longo) {
            return new ResultadoDecomposicao(false, List.of(), "");
        }

        String aviso = "Prompt extenso detectado (" + texto.length() + " caracteres). "
                + "Para evitar perda de contexto e reduzir falhas, o comando foi dividido automaticamente em "
                + etapas.size() + " sub-prompts ordenados por dependência e validados a cada etapa.";
        return new ResultadoDecomposicao(true, List.copyOf(etapas), aviso);
    }

    private static List<String> fatiarClausulas(String texto) {
        List<String> sentencas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        int parenteses = 0;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '(') parenteses++;
            else if (c == ')' && parenteses > 0) parenteses--;

            boolean separadorSentenca = false;
            if (parenteses == 0) {
                if (c == ';') {
                    separadorSentenca = true;
                } else if (c == '.') {
                    boolean ehDecimalOuDoc = (i > 0 && Character.isDigit(texto.charAt(i - 1)))
                            && (i + 1 < texto.length() && Character.isDigit(texto.charAt(i + 1)));
                    boolean proximoEhFimOuEspacoMaisLetra = (i + 1 >= texto.length())
                            || (Character.isWhitespace(texto.charAt(i + 1)));
                    if (!ehDecimalOuDoc && proximoEhFimOuEspacoMaisLetra) {
                        separadorSentenca = true;
                    }
                }
            }

            if (separadorSentenca) {
                String trecho = atual.toString().trim();
                if (!trecho.isEmpty()) {
                    dividirConectoresInternos(trecho, sentencas);
                }
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        String resto = atual.toString().trim();
        if (!resto.isEmpty()) {
            dividirConectoresInternos(resto, sentencas);
        }
        return sentencas;
    }

    private static void dividirConectoresInternos(String trecho, List<String> destino) {
        String[] partes = CONECTOR_MEIO_CADASTRO.split(trecho);
        if (partes.length <= 1) {
            destino.add(trecho);
            return;
        }
        for (String p : partes) {
            String sub = p.trim();
            if (sub.isEmpty()) continue;
            // Se começou com "o cliente ..." ou "o fornecedor ..." após split, prefixa "Cadastre "
            String lower = sub.toLowerCase(Locale.ROOT);
            if ((lower.startsWith("o cliente ") || lower.startsWith("o fornecedor ")
                    || lower.startsWith("a cliente ") || lower.startsWith("a fornecedora "))
                    && !lower.contains("cadastre")) {
                sub = "Cadastre " + sub;
            }
            destino.add(sub);
        }
    }

    private static CamadaExecucao classificarCamada(String clausula) {
        String lower = clausula.toLowerCase(Locale.ROOT);
        if (contemAlgum(lower, "nota fiscal", "nota de compra", "nota de venda", "emita uma nota", "emitir nota",
                "comprando ", "vendendo ", "baixa de perda", "devolu")) {
            return CamadaExecucao.NOTAS_E_MOVIMENTACOES;
        }
        if (contemAlgum(lower, "kit", "conjunto", "combo", "cesta", "composto por", "vincule ")) {
            return CamadaExecucao.KITS_E_VINCULOS;
        }
        if (contemAlgum(lower, "produto", "produtos f", "produto digital")) {
            return CamadaExecucao.PRODUTOS_SIMPLES;
        }
        if (contemAlgum(lower, "unidade de medida", "forma de pagamento", "formas de pagamento")) {
            return CamadaExecucao.BASE_ESTRUTURAL;
        }
        if (contemAlgum(lower, "fornecedor", "cliente", "cnpj", "cpf")) {
            return CamadaExecucao.PESSOAS;
        }
        return CamadaExecucao.PRODUTOS_SIMPLES;
    }

    private static boolean contemAlgum(String texto, String... termos) {
        for (String t : termos) {
            if (texto.contains(t)) return true;
        }
        return false;
    }
}
