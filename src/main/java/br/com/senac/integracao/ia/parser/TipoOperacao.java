package br.com.senac.integracao.ia.parser;

import java.util.Locale;

/**
 * Operações suportadas pela DSL {@code $$} e pela árvore sintática (AST).
 */
public enum TipoOperacao {
    C("Criar"),
    R("Consultar"),
    U("Atualizar"),
    D("Excluir"),
    ABRIR_JANELA("Abrir Janela"),
    CLOSE("Fechar / Cancelar"),
    SUB_ADICIONAR("Adicionar Sub-Item (++)"),
    SUB_REMOVER("Remover Sub-Item (--)");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean ehMutacao() {
        return this == C || this == U || this == D || this == SUB_ADICIONAR || this == SUB_REMOVER;
    }

    public boolean ehConsulta() {
        return this == R;
    }

    public static TipoOperacao fromString(String op) {
        if (op == null || op.isBlank()) return R;
        String limpo = op.trim().toUpperCase(Locale.ROOT);
        return switch (limpo) {
            case "C", "CREATE" -> C;
            case "R", "READ" -> R;
            case "U", "UPDATE" -> U;
            case "D", "DELETE" -> D;
            case "++" -> SUB_ADICIONAR;
            case "--" -> SUB_REMOVER;
            default -> throw new IllegalArgumentException("Operação CRUD desconhecida: " + op);
        };
    }
}
