package br.com.senac.interfaces;

import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.time.LocalDate;
import java.util.List;

public interface ProvedorFatorExterno {
    void enriquecerVinculo(LocalDate data, Coordenada coordenada, VinculoFatorExternoNota vinculo);

    default String getNomeProvedor() {
        return getClass().getSimpleName();
    }

    /**
     * Gera o resumo padronizado deste fator externo para uma Nota específica, citando a Nota (ID, Tipo, Data e Valor).
     */
    default String gerarRelatorioPorNota(Nota nota) {
        if (nota == null) {
            return "[" + getNomeProvedor() + "] Nota não informada.";
        }
        VinculoFatorExternoNota v = nota.getVinculoFatorExterno();
        String refNota = String.format("Nota #%s [%s | Data: %s | Valor: R$ %s]",
                nota.getId() != null ? nota.getId() : "N/A",
                nota.getClass().getSimpleName(),
                nota.getDataGerada() != null ? nota.getDataGerada().toLocalDate() : LocalDate.now(),
                nota.getValorTotal());
        if (v == null) {
            return refNota + " -> [" + getNomeProvedor() + "] Sem fator externo vinculado.";
        }
        return refNota + " -> [" + getNomeProvedor() + "] " + extrairDetalheVinculo(v);
    }

    /**
     * Gera um relatório integrado padronizado citando cada Nota da lista e correlacionando com os dados deste provedor.
     */
    default String gerarRelatorioIntegradoNotas(List<? extends Nota> notas) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RELATÓRIO INTEGRADO [").append(getNomeProvedor()).append("] ===\n");
        if (notas == null || notas.isEmpty()) {
            sb.append("Nenhuma nota informada para o relatório.\n");
            return sb.toString();
        }
        for (Nota n : notas) {
            sb.append("• ").append(gerarRelatorioPorNota(n)).append("\n");
        }
        return sb.toString();
    }

    default String extrairDetalheVinculo(VinculoFatorExternoNota vinculo) {
        return vinculo != null ? vinculo.determinarPadraoConsumo() : "N/A";
    }

    default void limparCacheExpirado() {
        // Implementação opcional nos serviços que possuem cache com TTL
    }
}
