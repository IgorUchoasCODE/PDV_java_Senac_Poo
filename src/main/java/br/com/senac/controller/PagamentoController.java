package br.com.senac.controller;

import br.com.senac.repository.dao.pagamento.FormaPagamentoDAO;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.service.ManageNotas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller responsável pelas rotas de Configuração de Formas de Pagamento (tabela 'forma_pagamento')
 * e geração/quitação de pagamentos À Vista, A Prazo e Parcelados.
 */
public class PagamentoController {

    private final FormaPagamentoDAO formaPagamentoDAO = new FormaPagamentoDAO();
    private final ManageNotas manageNotas;

    public PagamentoController(ManageNotas manageNotas) {
        this.manageNotas = manageNotas;
    }

    // ==================== ROTAS DE CUSTOMIZAÇÃO DA TABELA FORMA_PAGAMENTO ====================

    public FormaPagamento salvarOuCustomizarFormaPagamento(FormaPagamento formaPagamento) {
        return formaPagamentoDAO.salvar(formaPagamento);
    }

    public FormaPagamento cadastrarNovaFormaPagamento(
            String codigo,
            String nome,
            String descricao,
            boolean permiteAVista,
            boolean permiteAPrazo,
            boolean permiteParcelado,
            int maxParcelas,
            int maxParcelasSemJuros,
            BigDecimal taxaOperadoraPercentual,
            BigDecimal tarifaFixa,
            BigDecimal descontoAVistaPercentual,
            BigDecimal taxaJurosMensal) {

        FormaPagamento existente = formaPagamentoDAO.buscarPorCodigo(codigo);
        if (existente != null) {
            throw new IllegalArgumentException("Já existe uma forma de pagamento com o código: " + codigo);
        }
        FormaPagamento nova = new FormaPagamento(
                codigo,
                nome,
                descricao != null ? descricao : nome,
                permiteAVista,
                permiteAPrazo,
                permiteParcelado,
                maxParcelas,
                maxParcelasSemJuros,
                0,
                taxaOperadoraPercentual != null ? taxaOperadoraPercentual : BigDecimal.ZERO,
                tarifaFixa != null ? tarifaFixa : BigDecimal.ZERO,
                descontoAVistaPercentual != null ? descontoAVistaPercentual : BigDecimal.ZERO,
                taxaJurosMensal != null ? taxaJurosMensal : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false
        );
        return formaPagamentoDAO.salvar(nova);
    }

    public boolean excluirFormaPagamento(String codigo) {
        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigo);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento não encontrada para o código: " + codigo);
        }
        return formaPagamentoDAO.deletarPorCodigo(codigo);
    }

    public FormaPagamento atualizarTaxasERegrasFormaPagamento(
            String codigoForma,
            BigDecimal novaTaxaOperadoraPercentual,
            BigDecimal novaTarifaFixa,
            BigDecimal novoDescontoAVistaPercentual,
            int novoMaximoParcelas,
            int novoMaximoParcelasSemJuros,
            BigDecimal novaTaxaJurosMensalParcelamento) {

        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigoForma);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento não encontrada para o código: " + codigoForma);
        }
        fp.setTaxaOperadoraPercentual(novaTaxaOperadoraPercentual);
        fp.setTarifaFixaOperadora(novaTarifaFixa);
        fp.setPercentualDescontoAVistaPadrao(novoDescontoAVistaPercentual);
        fp.setMaxParcelas(novoMaximoParcelas);
        fp.setMaxParcelasSemJuros(novoMaximoParcelasSemJuros);
        fp.setTaxaJurosMensalPadrao(novaTaxaJurosMensalParcelamento);
        return formaPagamentoDAO.salvar(fp);
    }

    public FormaPagamento alterarStatusFormaPagamento(String codigoForma, boolean ativo) {
        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigoForma);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento não encontrada: " + codigoForma);
        }
        fp.setAtivo(ativo);
        return formaPagamentoDAO.salvar(fp);
    }

    public FormaPagamento buscarFormaPorCodigo(String codigo) {
        return formaPagamentoDAO.buscarPorCodigo(codigo);
    }

    public List<FormaPagamento> listarFormasPagamentoAtivas() {
        return formaPagamentoDAO.listarAtivas();
    }

    public List<FormaPagamento> listarTodasFormasPagamento() {
        return formaPagamentoDAO.listarTodas();
    }

    // ==================== ROTAS DE CRIAÇÃO E QUITAÇÃO DE TÍTULOS DE PAGAMENTO ====================

    public PagamentoAVista criarPagamentoAVista(String codigoFormaPagamento, BigDecimal valorNominal, BigDecimal valorRecebido, BigDecimal descontoCustomizado) {
        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigoFormaPagamento);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento '" + codigoFormaPagamento + "' não cadastrada.");
        }
        return new PagamentoAVista(fp, valorNominal, valorRecebido, descontoCustomizado);
    }

    public PagamentoAPrazo criarPagamentoAPrazo(String codigoFormaPagamento, BigDecimal valorNominal, LocalDate dataVencimento) {
        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigoFormaPagamento);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento '" + codigoFormaPagamento + "' não cadastrada.");
        }
        return new PagamentoAPrazo(fp, valorNominal, dataVencimento);
    }

    public PagamentoParcelado criarPagamentoParcelado(String codigoFormaPagamento, BigDecimal valorNominal, int quantidadeParcelas) {
        FormaPagamento fp = formaPagamentoDAO.buscarPorCodigo(codigoFormaPagamento);
        if (fp == null) {
            throw new IllegalArgumentException("Forma de pagamento '" + codigoFormaPagamento + "' não cadastrada.");
        }
        if (!fp.validarParcelamento(quantidadeParcelas, valorNominal)) {
            throw new IllegalArgumentException("A forma '" + fp.getNome() + "' não permite parcelar R$ "
                    + valorNominal + " em " + quantidadeParcelas + "x (Máx: " + fp.getMaxParcelas()
                    + "x | Parcela mín: R$ " + fp.getValorMinimoParcela() + ").");
        }
        return new PagamentoParcelado(fp, valorNominal, quantidadeParcelas);
    }

    public void quitarPagamento(NotaPagamento pagamento, LocalDateTime dataHoraQuitacao) {
        manageNotas.processarQuitacaoPagamento(pagamento, dataHoraQuitacao);
    }

    public void quitarParcelaEspecifica(PagamentoParcelado pagamentoParcelado, int numeroParcela, LocalDateTime dataHoraQuitacao) {
        pagamentoParcelado.quitarParcela(numeroParcela, dataHoraQuitacao);
    }
}
