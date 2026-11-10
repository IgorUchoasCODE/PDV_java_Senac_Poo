package br.com.senac.controller;

import br.com.senac.repository.dao.nota.EstatisticaDAO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller de inteligência estatística/estratégica. O processamento ocorre nas Stored Procedures do MySQL.
 */
public class EstatisticaController {

    private final EstatisticaDAO dao = new EstatisticaDAO();

    public List<Map<String, Object>> preverDemandaMensalCliente(Long idCliente, Long idProduto, Integer mes) {
        return dao.demandaMensalCliente(idCliente, idProduto, mes);
    }

    public List<Map<String, Object>> analisarPerfilCliente(Long idCliente) {
        return dao.perfilConsumoCliente(idCliente);
    }

    public List<Map<String, Object>> analisarImpactoExterno(Long idProduto) {
        return dao.impactoExternoProdutos(idProduto);
    }

    public List<Map<String, Object>> rankingFornecedores(Long idProduto, String clima, String regimeRio) {
        return dao.rankingFornecedores(idProduto, clima, regimeRio);
    }

    public List<Map<String, Object>> diagnosticarPerdaPrematura(Long idProduto, Long idFornecedor) {
        return dao.motivosPerdaPrematura(idProduto, idFornecedor);
    }

    public List<Map<String, Object>> destaquesSazonais(int limite) {
        return dao.destaquesSazonais(limite);
    }

    public List<Map<String, Object>> picosDiasEventos() {
        return dao.picosDiasEventos();
    }

    public Map<String, List<Map<String, Object>>> causasExternasPorDemanda(Long idNota, Long idProduto) {
        return dao.causasExternasPorDemanda(idNota, idProduto);
    }

    public int recalcularEstatisticasFatores() {
        return dao.recalcularEstatisticasFatores();
    }

    public List<Map<String, Object>> estatisticaFatoresProduto(Long idProduto) {
        return dao.estatisticaFatoresProduto(idProduto);
    }

    public List<Map<String, Object>> eventosPersistidos(int limite) {
        return dao.eventosPersistidos(limite);
    }

    public List<Map<String, Object>> planoEstrategico(LocalDate dataReferencia, int horizonteDias) {
        return dao.planoEstrategico(dataReferencia, horizonteDias);
    }
}
