package br.com.senac.integracao.ia.ferramentas;

import br.com.senac.repository.modal.pagamento.FormaPagamento;

import java.util.List;

/**
 * Utilitário de formatação rica e humanizada para Formas de Pagamento e Quitações,
 * com quebras de linha adequadas (\n) e detalhamento de modalidades contábeis (BACEN).
 */
public class ResolvedorPagamento {

    public static String formatarDetalhesCompletosFormaPagamento(FormaPagamento fp) {
        if (fp == null) {
            return "Forma de pagamento não encontrada.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("💳 Forma de Pagamento: %s (%s)\n", fp.getNome(), fp.getCodigo()));
        if (fp.getDescricao() != null && !fp.getDescricao().isBlank()) {
            sb.append(String.format("  • Descrição: %s\n", fp.getDescricao()));
        }
        sb.append("  • Modalidades Permitidas:\n");
        if (fp.isPermiteAVista()) {
            sb.append(String.format("    - À Vista: SIM (Desconto padrão: %s%%)\n",
                    fp.getPercentualDescontoAVistaPadrao() != null ? fp.getPercentualDescontoAVistaPadrao() : "0.00"));
        } else {
            sb.append("    - À Vista: NÃO\n");
        }
        if (fp.isPermiteAPrazo()) {
            sb.append(String.format("    - A Prazo: SIM (Compensação: %d dia(s) | Intervalo padrão: %d dias)\n",
                    fp.getDiasCompensacaoPadrao(), fp.getDiasIntervaloParcelasPadrao()));
        } else {
            sb.append("    - A Prazo: NÃO\n");
        }
        if (fp.isPermiteParcelado()) {
            sb.append(String.format("    - Parcelado: SIM (Até %dx | Sem juros até: %dx | Juros mensal: %s%%)\n",
                    fp.getMaxParcelas(), fp.getMaxParcelasSemJuros(),
                    fp.getTaxaJurosMensalPadrao() != null ? fp.getTaxaJurosMensalPadrao() : "0.00"));
        } else {
            sb.append("    - Parcelado: NÃO\n");
        }
        sb.append(String.format("  • Tarifas da Operadora: Taxa: %s%% | Tarifa Fixa: R$ %s\n",
                fp.getTaxaOperadoraPercentual() != null ? fp.getTaxaOperadoraPercentual() : "0.00",
                fp.getTarifaFixaOperadora() != null ? fp.getTarifaFixaOperadora() : "0.00"));
        sb.append(String.format("  • Exige Limite de Crédito: %s\n", fp.isExigeLimiteCliente() ? "SIM" : "NÃO"));
        sb.append(String.format("  • Status no Sistema: %s", fp.isAtivo() ? "ATIVO" : "INATIVO"));
        return sb.toString();
    }

    public static String formatarListaFormasPagamento(List<FormaPagamento> lista) {
        if (lista == null || lista.isEmpty()) {
            return "Nenhuma forma de pagamento cadastrada ou ativa no momento.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Formas de Pagamento Cadastradas (%d ativas):\n", lista.size()));
        for (FormaPagamento fp : lista) {
            sb.append(String.format("\n• [%s] %s\n", fp.getCodigo(), fp.getNome()));
            String modalidades = (fp.isPermiteAVista() ? "À Vista " : "")
                    + (fp.isPermiteAPrazo() ? "A Prazo " : "")
                    + (fp.isPermiteParcelado() ? ("Parcelado(até " + fp.getMaxParcelas() + "x) ") : "");
            sb.append(String.format("  Modalidades: %s\n", modalidades.trim().replace(" ", " | ")));
            sb.append(String.format("  Taxa Operadora: %s%% | Tarifa Fixa: R$ %s | Exige Limite: %s\n",
                    fp.getTaxaOperadoraPercentual() != null ? fp.getTaxaOperadoraPercentual() : "0.00",
                    fp.getTarifaFixaOperadora() != null ? fp.getTarifaFixaOperadora() : "0.00",
                    fp.isExigeLimiteCliente() ? "SIM" : "NÃO"));
        }
        return sb.toString().trim();
    }
}
