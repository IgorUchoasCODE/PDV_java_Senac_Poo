package br.com.senac.integracao.ia;

import java.util.Collections;
import java.util.List;

/**
 * Relatório consolidado com os resultados da execução em lote de uma Bandeja de Contexto.
 */
public record RelatorioExecucaoBandeja(
        int totalOperacoes,
        int sucessos,
        int falhas,
        List<ItemExecutado> detalhes
) {
    public RelatorioExecucaoBandeja {
        detalhes = detalhes != null ? Collections.unmodifiableList(detalhes) : Collections.emptyList();
    }

    public boolean todasComSucesso() {
        return totalOperacoes > 0 && falhas == 0;
    }

    public boolean temFalhas() {
        return falhas > 0;
    }

    public record ItemExecutado(
            String idItem,
            String rota,
            String descricao,
            boolean sucesso,
            String mensagem,
            Object dados,
            java.util.Map<String, Object> parametros
    ) {
        public ItemExecutado(String idItem, String rota, String descricao, boolean sucesso, String mensagem, Object dados) {
            this(idItem, rota, descricao, sucesso, mensagem, dados, Collections.emptyMap());
        }
    }
}
