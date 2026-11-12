package br.com.senac.integracao.ia.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser recursivo e validador atômico de sintaxe para a DSL {@code $$}.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validação atômica em memória antes de qualquer execução ou enfileiramento na bandeja.</li>
 *   <li>Suporte a aninhamento arbitrário de colchetes {@code [ ... ]} para kits/conjuntos e notas compostas.</li>
 *   <li>Identificação precisa de erros de sintaxe (linha, coluna, trecho com indicador {@code ^} e sugestão amigável).</li>
 *   <li>Compatibilidade total com a sintaxe flat existente ({@code C=}, {@code R=}, {@code U=}, {@code D=},
 *       {@code >> #Cascata}, {@code abrir>janela>...}, {@code close::}, etc.).</li>
 * </ul>
 */
public class ParserInstrucao {

    public static final Set<String> ENTIDADES_VALIDAS = Set.of(
            "PRODUTO", "PRODUTOFISICO", "PRODUTODIGITAL", "CONJUNTOPRODUTO", "COMPONENTE", "UM",
            "ESTOQUE", "ITEM", "PESSOA", "PESSOAFISICA", "PESSOAJURIDICA", "CLIENTE", "CLIENTES",
            "FORNECEDOR", "FORNECEDORES", "REGISTRO", "CONTATO", "CONTATOS",
            "FORMAPAGAMENTO", "PAGAMENTO", "PAGAMENTOS", "PAGAMENTOAVISTA", "PAGAMENTOAPRAZO",
            "PAGAMENTOPARCELADO", "NOTAPAGAMENTO", "PARCELAPAGAMENTO",
            "NOTA", "NOTAS", "NOTACOMPRA", "NOTAVENDA", "NOTADEVOLUCAO", "NOTAPERDA",
            "VINCULOFATOREXTERNONOTA", "FATOREXTERNO", "ESTATISTICA", "RELATORIO"
    );

    private static final Pattern PADRAO_CLOSE_ID = Pattern.compile("(?i)^close\\s*<\\s*([^>]*)\\s*>$");
    private static final Pattern PADRAO_OP_CRUD = Pattern.compile("(?i)^([A-Za-z0-9_]+)\\s*=");

    /**
     * Valida e compila atomicamente todo o comando {@code $$} em uma lista de nós AST.
     *
     * @param comandoBruto texto recebido do usuário (com ou sem prefixo {@code $$})
     * @return lista de nós raiz de instrução
     * @throws ErroSintaxe se houver qualquer erro de sintaxe no texto
     */
    public List<NoInstrucao> parsear(String comandoBruto) throws ErroSintaxe {
        if (comandoBruto == null || comandoBruto.isBlank()) {
            return Collections.emptyList();
        }

        String texto = comandoBruto.trim();
        int deslocamentoInicial = 0;
        if (texto.startsWith("$$")) {
            deslocamentoInicial = comandoBruto.indexOf("$$") + 2;
            texto = texto.substring(2).trim();
        }

        if (texto.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Validação global de balanceamento de colchetes e aspas
        validarBalanceamentoGlobal(comandoBruto, texto, deslocamentoInicial);

        // 2. Separa instruções do topo dividindo por ';' fora de colchetes
        List<TrechoTexto> instrucoesTopo = dividirPorDelimitador(texto, ';', deslocamentoInicial);

        // 3. Converte cada trecho em um nó AST (validação atômica)
        List<NoInstrucao> nos = new ArrayList<>();
        for (TrechoTexto trecho : instrucoesTopo) {
            String s = trecho.conteudo().trim();
            if (s.isEmpty()) continue;
            nos.add(parsearInstrucaoUnica(s, trecho.inicio(), comandoBruto));
        }

        return nos;
    }

    private void validarBalanceamentoGlobal(String comandoBruto, String texto, int deslocamento) {
        Deque<Integer> pilhaColchetes = new ArrayDeque<>();
        boolean emAspas = false;
        int posAspas = -1;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            int posReal = deslocamento + i;

            if (c == '"' && (i == 0 || texto.charAt(i - 1) != '\\')) {
                emAspas = !emAspas;
                if (emAspas) {
                    posAspas = posReal;
                }
            }

            if (!emAspas) {
                if (c == '[') {
                    pilhaColchetes.push(posReal);
                } else if (c == ']') {
                    if (pilhaColchetes.isEmpty()) {
                        throw ErroSintaxe.criar(
                                comandoBruto, posReal,
                                "Colchete de fechamento ']' encontrado sem colchete de abertura '[' correspondente.",
                                "Remova o ']' sobressalente ou adicione o '[' de abertura no início do bloco."
                        );
                    }
                    pilhaColchetes.pop();
                }
            }
        }

        if (emAspas) {
            throw ErroSintaxe.criar(
                    comandoBruto, posAspas,
                    "Aspas abertas não foram fechadas antes do término da instrução.",
                    "Feche as aspas com '\"' para delimitar o valor textual."
            );
        }

        if (!pilhaColchetes.isEmpty()) {
            int posAbertura = pilhaColchetes.pop();
            throw ErroSintaxe.criar(
                    comandoBruto, posAbertura,
                    "Colchete de abertura '[' não foi fechado.",
                    "Feche o bloco com ']' correspondente antes do ponto-e-vírgula ou fim da linha."
            );
        }
    }

    private NoInstrucao parsearInstrucaoUnica(String textoInstrucao, int posInicio, String textoCompleto) {
        String s = textoInstrucao.trim();

        // Comando especial: abrir>janela>...
        if (s.toLowerCase(Locale.ROOT).startsWith("abrir>janela")) {
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("comando", s);
            return new NoInstrucao(TipoOperacao.ABRIR_JANELA, "JANELA", attrs, List.of(), List.of(),
                    posInicio, posInicio + s.length(), s);
        }

        // Comando especial: close::
        if ("close::".equalsIgnoreCase(s) || s.toLowerCase(Locale.ROOT).startsWith("close::")) {
            return new NoInstrucao(TipoOperacao.CLOSE, "SESSAO", Map.of("acao", "fechar_sessao"),
                    List.of(), List.of(), posInicio, posInicio + s.length(), s);
        }

        // Comando especial: close<OP-X>
        Matcher mClose = PADRAO_CLOSE_ID.matcher(s);
        if (mClose.find()) {
            String alvo = mClose.group(1).trim();
            return new NoInstrucao(TipoOperacao.CLOSE, "ITEM", Map.of("id", alvo),
                    List.of(), List.of(), posInicio, posInicio + s.length(), s);
        }

        // Extrai alvos de cascata '>> #Entidade'
        List<String> cascata = new ArrayList<>();
        String semCascata = s;
        if (s.contains(">>")) {
            String[] partesCascata = s.split(">>");
            semCascata = partesCascata[0].trim();
            for (int c = 1; c < partesCascata.length; c++) {
                String alvoC = partesCascata[c].trim().replaceFirst("^#", "").replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT);
                if (!alvoC.isEmpty()) {
                    cascata.add(alvoC);
                }
            }
        }

        // Extrai sub-blocos aninhados recursivamente
        ResultadoExtracaoSubBlocos extracao = extrairSubBlocosRecursivo(semCascata, posInicio, textoCompleto);
        String cabecalho = extracao.textoRestante().trim();

        // Identifica operação (C=, R=, U=, D=) ou sub-modificador (++, --)
        TipoOperacao operacao = TipoOperacao.R;
        if (cabecalho.startsWith("++")) {
            operacao = TipoOperacao.SUB_ADICIONAR;
            cabecalho = cabecalho.substring(2).trim();
        } else if (cabecalho.startsWith("--")) {
            operacao = TipoOperacao.SUB_REMOVER;
            cabecalho = cabecalho.substring(2).trim();
        } else {
            Matcher mOp = PADRAO_OP_CRUD.matcher(cabecalho);
            if (mOp.find()) {
                String opStr = mOp.group(1).toUpperCase(Locale.ROOT);
                if (!Set.of("C", "R", "U", "D").contains(opStr)) {
                    throw ErroSintaxe.criar(
                            textoCompleto, posInicio + mOp.start(),
                            "Operação '" + opStr + "' não reconhecida. Use C=, R=, U=, D= ou abrir>janela>...",
                            "Utilize C= (cadastrar), R= (consultar), U= (atualizar) ou D= (excluir)."
                    );
                }
                operacao = TipoOperacao.fromString(opStr);
                cabecalho = cabecalho.substring(mOp.end()).trim();
            }
        }

        // Identifica Entidade (#Entidade), tolerando vírgula após (#Produto,)
        String entidade = "PRODUTO";
        Matcher mEnt = Pattern.compile("#([A-Za-z0-9_]+)\\s*,?").matcher(cabecalho);
        if (mEnt.find()) {
            entidade = mEnt.group(1).toUpperCase(Locale.ROOT);
            cabecalho = (cabecalho.substring(0, mEnt.start()) + " " + cabecalho.substring(mEnt.end())).trim();
            if (!ENTIDADES_VALIDAS.contains(entidade)) {
                throw ErroSintaxe.criar(
                        textoCompleto, posInicio + mEnt.start(),
                        "Entidade '#" + entidade + "' não é reconhecida no sistema. Entidades suportadas: #Produto, #Nota, #Pessoa, #Cliente, #Fornecedor, etc.",
                        "Verifique a ortografia do nome da entidade após o caractere '#'."
                );
            }
        } else if (operacao == TipoOperacao.SUB_ADICIONAR || operacao == TipoOperacao.SUB_REMOVER) {
            // Em sub-nós posicional (ex: ++ dinheiro, 1000), a entidade é inferida pelo contexto
            entidade = inferirEntidadeSubNo(cabecalho);
        }

        // Extrai atributos chave> valor
        Map<String, String> atributos = extrairAtributos(cabecalho);

        // Marcadores de contexto
        String cabLower = cabecalho.toLowerCase(Locale.ROOT);
        if (cabLower.contains("da_nota") || cabLower.contains("dessa_nota") || cabLower.contains("nota_atual")) {
            atributos.put("contexto_foco", "da_nota");
        } else if (cabLower.contains("da_pessoa") || cabLower.contains("dessa_pessoa") || cabLower.contains("pessoa_atual")) {
            atributos.put("contexto_foco", "da_pessoa");
        }

        if (cabecalho.isEmpty() && extracao.subNos().size() == 1) {
            return extracao.subNos().get(0);
        }

        return new NoInstrucao(operacao, entidade, atributos, extracao.subNos(), cascata,
                posInicio, posInicio + textoInstrucao.length(), textoInstrucao);
    }

    private static String inferirEntidadeSubNo(String cabecalho) {
        String s = cabecalho.toLowerCase(Locale.ROOT);
        if (s.contains("dinheiro") || s.contains("pix") || s.contains("cartao") || s.contains("boleto") || s.contains("debito") || s.contains("credito")) {
            return "PAGAMENTO";
        }
        return "ITEM";
    }

    /**
     * Extrai blocos entre colchetes {@code [ ... ]} recursivamente, suportando aninhamento profundo.
     */
    private ResultadoExtracaoSubBlocos extrairSubBlocosRecursivo(String texto, int offsetPai, String textoCompleto) {
        List<NoInstrucao> subNos = new ArrayList<>();
        StringBuilder textoRestante = new StringBuilder();

        int i = 0;
        while (i < texto.length()) {
            char c = texto.charAt(i);

            if (c == '[') {
                int inicioColchete = i;
                int nivel = 1;
                int j = i + 1;
                while (j < texto.length() && nivel > 0) {
                    char cj = texto.charAt(j);
                    if (cj == '[') nivel++;
                    else if (cj == ']') nivel--;
                    j++;
                }

                if (nivel > 0) {
                    throw ErroSintaxe.criar(textoCompleto, offsetPai + inicioColchete,
                            "Colchete '[' não foi devidamente fechado.",
                            "Adicione ']' no término do bloco.");
                }

                int fimColchete = j - 1; // índice do ']'
                String conteudoInterno = texto.substring(inicioColchete + 1, fimColchete).trim();

                // Analisa o prefixo antes do '[' no textoRestante (ex.: ++ #Componente ou ++ #Produto)
                String prefixoAcumulado = textoRestante.toString();
                Matcher mSubHeader = Pattern.compile("(\\+\\+|--)\\s*(?:#([A-Za-z0-9_]+))?\\s*$").matcher(prefixoAcumulado);

                if (mSubHeader.find()) {
                    String opMod = mSubHeader.group(1);
                    String entMod = mSubHeader.group(2);
                    // Remove o prefixo do textoRestante
                    textoRestante.setLength(mSubHeader.start());

                    TipoOperacao opSub = "++".equals(opMod) ? TipoOperacao.SUB_ADICIONAR : TipoOperacao.SUB_REMOVER;
                    String entSub = (entMod != null && !entMod.isBlank()) ? entMod.toUpperCase(Locale.ROOT) : "";

                    // Divide os itens internos por ';' fora de colchetes internos
                    List<TrechoTexto> itensInternos = dividirPorDelimitador(conteudoInterno, ';', offsetPai + inicioColchete + 1);
                    for (TrechoTexto item : itensInternos) {
                        String sItem = item.conteudo().trim();
                        if (sItem.isEmpty()) continue;

                        NoInstrucao noFilho = parsearInstrucaoUnica(sItem, item.inicio(), textoCompleto);
                        // Se o filho não especificou entidade ou operação explícita, herda as do bloco pai
                        String entFinal = (!noFilho.getEntidade().equals("PRODUTO") && !noFilho.getEntidade().equals("ITEM"))
                                ? noFilho.getEntidade() : (!entSub.isEmpty() ? entSub : noFilho.getEntidade());
                        TipoOperacao opFinal = noFilho.getOperacao() == TipoOperacao.R ? opSub : noFilho.getOperacao();
                        noFilho = new NoInstrucao(opFinal, entFinal, noFilho.getAtributos(),
                                noFilho.getSubInstrucoes(), noFilho.getCascata(), noFilho.getPosicaoInicio(),
                                noFilho.getPosicaoFim(), noFilho.getTextoOriginal());
                        subNos.add(noFilho);
                    }
                } else {
                    // Bloco de agrupamento geral: [ ++ #Item[...]; ++ #Pagamento[...] ]
                    List<TrechoTexto> itensInternos = dividirPorDelimitador(conteudoInterno, ';', offsetPai + inicioColchete + 1);
                    for (TrechoTexto item : itensInternos) {
                        String sItem = item.conteudo().trim();
                        if (sItem.isEmpty()) continue;
                        subNos.add(parsearInstrucaoUnica(sItem, item.inicio(), textoCompleto));
                    }
                }

                i = j;
            } else {
                textoRestante.append(c);
                i++;
            }
        }

        return new ResultadoExtracaoSubBlocos(textoRestante.toString(), subNos);
    }

    private static Map<String, String> extrairAtributos(String segmento) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (segmento == null || segmento.isBlank()) return mapa;

        String[] tokens = segmento.split(",");
        int pos = 0;
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
                continue;
            }

            if (t.equalsIgnoreCase("conjunto") || t.equalsIgnoreCase("kit")) {
                mapa.put("conjunto", "true");
                continue;
            }

            // Tratamento de argumentos posicionais (ex.: ++ dinheiro, 1000, dt> <3)
            if (!t.contains(">")) {
                if (pos == 0 && !mapa.containsKey("nm")) {
                    mapa.put("nm", t);
                    pos++;
                } else if (pos <= 1 && !mapa.containsKey("val") && !mapa.containsKey("vl") && t.matches("^[0-9]+([.,][0-9]+)?$")) {
                    mapa.put("val", t.replace(",", "."));
                    pos = 2;
                } else if (!mapa.containsKey("dt")) {
                    mapa.put("dt", t);
                }
            }
        }
        return mapa;
    }

    private static List<TrechoTexto> dividirPorDelimitador(String texto, char delimitador, int offsetGlobal) {
        List<TrechoTexto> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        int nivelColchetes = 0;
        boolean emAspas = false;
        int inicioTrecho = offsetGlobal;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"' && (i == 0 || texto.charAt(i - 1) != '\\')) {
                emAspas = !emAspas;
            }

            if (!emAspas) {
                if (c == '[') nivelColchetes++;
                else if (c == ']' && nivelColchetes > 0) nivelColchetes--;
            }

            if (c == delimitador && nivelColchetes == 0 && !emAspas) {
                partes.add(new TrechoTexto(atual.toString(), inicioTrecho, offsetGlobal + i));
                atual.setLength(0);
                inicioTrecho = offsetGlobal + i + 1;
            } else {
                atual.append(c);
            }
        }

        if (!atual.isEmpty()) {
            partes.add(new TrechoTexto(atual.toString(), inicioTrecho, offsetGlobal + texto.length()));
        }

        return partes;
    }

    private record TrechoTexto(String conteudo, int inicio, int fim) {}
    private record ResultadoExtracaoSubBlocos(String textoRestante, List<NoInstrucao> subNos) {}
}
