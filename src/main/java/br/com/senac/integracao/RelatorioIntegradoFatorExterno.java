package br.com.senac.integracao;

import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.util.List;
import java.util.Map;

/**
 * Interface que padroniza e integra a geração de relatórios sazonais e operacionais
 * consolidando Calendário, Clima, Nível dos Rios e Eventos (Sympla / MinC) e citando
 * individualmente cada Nota vinculada.
 */
public interface RelatorioIntegradoFatorExterno {

    /**
     * Gera o relatório padronizado completo para uma única Nota, citando seus dados
     * (ID, tipo, data, pessoa, valor) e os fatores externos (Clima, Rios, Calendário e Eventos).
     */
    String gerarRelatorioNota(Nota nota);

    /**
     * Gera o relatório consolidado padronizado integrando todas as Notas informadas e
     * citando cada Nota junto aos seus respectivos indicadores de Rio, Clima, Calendário e Eventos.
     */
    String gerarRelatorioConsolidadoCitandoNotas(List<? extends Nota> notas);

    /**
     * Estrutura em Mapa os indicadores de Clima, Rios, Eventos e Calendário citando cada Nota.
     */
    List<Map<String, Object>> estruturarRelatorioIntegradoNotas(List<? extends Nota> notas);

    /**
     * Resume um vínculo de fator externo e cita todas as notas associadas a ele.
     */
    String formatarSnapshotComNotas(VinculoFatorExternoNota vinculo, List<? extends Nota> notasDoSnapshot);
}
