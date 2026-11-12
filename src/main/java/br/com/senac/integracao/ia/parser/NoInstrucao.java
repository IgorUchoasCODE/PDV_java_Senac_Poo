package br.com.senac.integracao.ia.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Nó da Árvore de Sintaxe Abstrata (AST) para a DSL {@code $$}.
 *
 * <p>Suporta instruções simples e instruções compostas aninhadas em qualquer nível de profundidade
 * (ex.: {@code C= #ConjuntoProduto nm> Combo [ ++ #Componente[ nm> ItemA, q> 1 ]; ++ #Componente[ nm> ItemB, q> 2 ] ]}).
 */
public class NoInstrucao {

    private final TipoOperacao operacao;
    private final String entidade;
    private final Map<String, String> atributos;
    private final List<NoInstrucao> subInstrucoes;
    private final List<String> cascata;
    private final int posicaoInicio;
    private final int posicaoFim;
    private final String textoOriginal;

    public NoInstrucao(
            TipoOperacao operacao,
            String entidade,
            Map<String, String> atributos,
            List<NoInstrucao> subInstrucoes,
            List<String> cascata,
            int posicaoInicio,
            int posicaoFim,
            String textoOriginal) {

        this.operacao = Objects.requireNonNull(operacao, "operacao não pode ser nula");
        this.entidade = entidade != null ? entidade.trim().toUpperCase(Locale.ROOT) : "";
        this.atributos = atributos != null ? new LinkedHashMap<>(atributos) : new LinkedHashMap<>();
        this.subInstrucoes = subInstrucoes != null ? new ArrayList<>(subInstrucoes) : new ArrayList<>();
        this.cascata = cascata != null ? new ArrayList<>(cascata) : new ArrayList<>();
        this.posicaoInicio = posicaoInicio;
        this.posicaoFim = posicaoFim;
        this.textoOriginal = textoOriginal != null ? textoOriginal : "";
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public Map<String, String> getAtributos() {
        return Collections.unmodifiableMap(atributos);
    }

    public String get(String chave) {
        if (chave == null) return null;
        return atributos.get(chave.trim().toLowerCase(Locale.ROOT));
    }

    public String getOuPadrao(String chave, String padrao) {
        String v = get(chave);
        return (v != null && !v.isBlank()) ? v : padrao;
    }

    public boolean temAtributo(String chave) {
        if (chave == null) return false;
        return atributos.containsKey(chave.trim().toLowerCase(Locale.ROOT));
    }

    public List<NoInstrucao> getSubInstrucoes() {
        return Collections.unmodifiableList(subInstrucoes);
    }

    public boolean temSubInstrucoes() {
        return !subInstrucoes.isEmpty();
    }

    public List<NoInstrucao> getSubInstrucoesAdicionar() {
        return subInstrucoes.stream()
                .filter(n -> n.getOperacao() == TipoOperacao.SUB_ADICIONAR)
                .toList();
    }

    public List<NoInstrucao> getSubInstrucoesRemover() {
        return subInstrucoes.stream()
                .filter(n -> n.getOperacao() == TipoOperacao.SUB_REMOVER)
                .toList();
    }

    public List<NoInstrucao> getSubInstrucoesPorEntidade(String nomeEntidade) {
        if (nomeEntidade == null) return List.of();
        String alvo = nomeEntidade.trim().toUpperCase(Locale.ROOT);
        return subInstrucoes.stream()
                .filter(n -> alvo.equals(n.getEntidade()))
                .toList();
    }

    public List<String> getCascata() {
        return Collections.unmodifiableList(cascata);
    }

    public int getPosicaoInicio() {
        return posicaoInicio;
    }

    public int getPosicaoFim() {
        return posicaoFim;
    }

    public String getTextoOriginal() {
        return textoOriginal;
    }

    public boolean ehMutacao() {
        return operacao.ehMutacao();
    }

    public boolean ehConsulta() {
        return operacao.ehConsulta();
    }

    @Override
    public String toString() {
        return "NoInstrucao{" +
                "op=" + operacao +
                ", ent='" + entidade + '\'' +
                ", attrs=" + atributos +
                ", subs=" + subInstrucoes.size() +
                ", cascata=" + cascata +
                '}';
    }
}
