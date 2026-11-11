package br.com.senac.integracao;

import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.interfaces.ProvedorFatorExterno;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementação padronizada que integra todos os provedores de fatores externos
 * (Calendário, Clima, Nível dos Rios, Eventos Sympla e MinC), emitindo relatórios
 * unificados que citam explicitamente cada Nota analisada.
 */
public class IntegradorRelatorioFatorExternoService implements RelatorioIntegradoFatorExterno {

    private final List<ProvedorFatorExterno> provedores;

    public IntegradorRelatorioFatorExternoService(List<ProvedorFatorExterno> provedores) {
        this.provedores = provedores != null ? new ArrayList<>(provedores) : new ArrayList<>();
    }

    @Override
    public String gerarRelatorioNota(Nota nota) {
        if (nota == null) {
            return "Nota inválida (nula).";
        }
        StringBuilder sb = new StringBuilder();
        String tipoNota = nota.getClass().getSimpleName();
        String pessoaNome = nota.getPessoa() != null ? nota.getPessoa().getNome() : "CONSUMIDOR PADRÃO / INTERNO";
        LocalDate dataNota = nota.getDataGerada() != null ? nota.getDataGerada().toLocalDate() : LocalDate.now();

        sb.append(String.format("🧾 Nota #%s [%s] | Data: %s | Pessoa: %s | Valor Total: R$ %s",
                nota.getId() != null ? nota.getId() : "PENDENTE",
                tipoNota,
                dataNota,
                pessoaNome,
                nota.getValorTotal()));

        VinculoFatorExternoNota v = nota.getVinculoFatorExterno();
        if (v != null) {
            ClimaDiario c = v.getClimaRegistrado();
            NivelRioDiario r = v.getNivelRioRegistrado();
            Coordenada coord = v.getCoordenada();
            sb.append(String.format(Locale.US,
                    "\n   ├─ 📍 Local/Snapshot: %s (%s/%s) | Padrão: %s | Impacto Demanda: %s",
                    v.getChaveSnapshot(), coord.cidade(), coord.uf(),
                    v.determinarPadraoConsumo(), v.calcularIndiceImpactoDemanda()));
            sb.append(String.format(Locale.US,
                    "\n   ├─ 📅 Calendário: %s | 5º Dia Útil: %s",
                    v.getResumoCalendario() != null ? v.getResumoCalendario() : (v.isDiaUtil() ? "DIA ÚTIL" : "DIA NÃO ÚTIL"),
                    v.isQuintoDiaUtil() ? "SIM" : "NÃO"));
            sb.append(String.format(Locale.US,
                    "\n   ├─ 🌦️ Clima: Temp Média %.1f°C (Máx %.1f°C / Mín %.1f°C) | Chuva %.1f mm | Vento %.1f km/h",
                    c.temperaturaMedia(), c.temperaturaMax(), c.temperaturaMin(), c.precipitacaoMm(), c.ventoMaxKmh()));
            sb.append(String.format(Locale.US,
                    "\n   ├─ 🌊 Rios: Vazão %.2f m³/s (Média %.2f / Máx %.2f / Mín %.2f m³/s)",
                    r.vazaoM3s(), r.vazaoMediaM3s(), r.vazaoMaxM3s(), r.vazaoMinM3s()));
            sb.append(String.format(
                    "\n   └─ 🎉 Eventos (%d ativos): %s",
                    v.getTotalEventosAtivos(), v.getResumoEventosJson()));
        } else {
            sb.append("\n   └─ ⚠️ Sem snapshot de fatores externos vinculado.");
        }
        return sb.toString();
    }

    @Override
    public String gerarRelatorioConsolidadoCitandoNotas(List<? extends Nota> notas) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("   RELATÓRIO INTEGRADO DE FATORES EXTERNOS (CLIMA, RIOS, CALENDÁRIO E EVENTOS)\n");
        sb.append("================================================================================\n");
        if (notas == null || notas.isEmpty()) {
            sb.append("Nenhuma nota encontrada para o período/filtro informado.\n");
            return sb.toString();
        }
        for (Nota n : notas) {
            sb.append(gerarRelatorioNota(n)).append("\n--------------------------------------------------------------------------------\n");
        }
        if (!provedores.isEmpty()) {
            sb.append(" Fontes Integradas: ");
            for (int i = 0; i < provedores.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(provedores.get(i).getNomeProvedor());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> estruturarRelatorioIntegradoNotas(List<? extends Nota> notas) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (notas == null) return resultado;

        for (Nota n : notas) {
            if (n == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("idNota", n.getId());
            item.put("tipoNota", n.getClass().getSimpleName());
            item.put("dataGerada", n.getDataGerada() != null ? n.getDataGerada().toString() : null);
            item.put("pessoa", n.getPessoa() != null ? n.getPessoa().getNome() : "CONSUMIDOR PADRÃO");
            item.put("valorTotal", n.getValorTotal());
            item.put("quantidadeItens", n.getItens() != null ? n.getItens().size() : 0);

            VinculoFatorExternoNota v = n.getVinculoFatorExterno();
            if (v != null) {
                item.put("chaveSnapshot", v.getChaveSnapshot());
                item.put("cidade", v.getCoordenada().cidade());
                item.put("uf", v.getCoordenada().uf());
                item.put("diaUtil", v.isDiaUtil());
                item.put("quintoDiaUtil", v.isQuintoDiaUtil());
                item.put("resumoCalendario", v.getResumoCalendario());
                item.put("temperaturaMedia", v.getClimaRegistrado().temperaturaMedia());
                item.put("precipitacaoMm", v.getClimaRegistrado().precipitacaoMm());
                item.put("vazaoRioM3s", v.getNivelRioRegistrado().vazaoM3s());
                item.put("totalEventosAtivos", v.getTotalEventosAtivos());
                item.put("eventosResumo", v.getResumoEventosJson());
                item.put("padraoConsumo", v.determinarPadraoConsumo());
                item.put("indiceImpactoDemanda", v.calcularIndiceImpactoDemanda());
            }
            item.put("relatorioTexto", gerarRelatorioNota(n));
            resultado.add(item);
        }
        return resultado;
    }

    @Override
    public String formatarSnapshotComNotas(VinculoFatorExternoNota vinculo, List<? extends Nota> notasDoSnapshot) {
        StringBuilder sb = new StringBuilder();
        if (vinculo != null) {
            sb.append("Snapshot: ").append(vinculo.getChaveSnapshot())
              .append(" | Padrão: ").append(vinculo.determinarPadraoConsumo())
              .append(" | Impacto: ").append(vinculo.calcularIndiceImpactoDemanda()).append("\n");
        }
        if (notasDoSnapshot != null && !notasDoSnapshot.isEmpty()) {
            sb.append("Notas vinculadas (").append(notasDoSnapshot.size()).append("):\n");
            for (Nota n : notasDoSnapshot) {
                sb.append("  -> ").append(gerarRelatorioNota(n)).append("\n");
            }
        } else {
            sb.append("Nenhuma nota vinculada a este snapshot.\n");
        }
        return sb.toString();
    }
}
