package br.com.senac.controller;

import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.service.ManageNotas;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controller responsável por todas as rotas de Notas Fiscais/Operacionais:
 * - NotaCompra (Entrada de Lotes FIFO, Abatimento de Perdas de Fornecedor, Reposição de Itens Perdidos, Sugestão de Preço)
 * - NotaVenda (Venda Automática via Algoritmo FIFO, Venda por Lote Desejável, Venda para Cliente Padrão Não Registrado, Descontos/Cupons)
 * - NotaDevolucao (Devolução com Reintegração ao Lote Original ou Geração de Perda Pós-Venda dentro da Validade)
 * - NotaPerda e Relatórios das Views Contábeis/Sazonais (Lucro BACEN, Perdas por Fornecedor e Análise Sazonal)
 */
public class NotaController {

    private final ManageNotas manageNotas;
    private final NotaDAO notaDAO = new NotaDAO();

    public NotaController(ManageNotas manageNotas) {
        this.manageNotas = manageNotas;
    }

    // ==================== ROTAS DE NOTA DE COMPRA (ENTRADA DE LOTES) ====================

    public NotaCompra emitirNotaCompra(NotaCompra notaCompra, List<NotaPagamento> pagamentos) {
        return manageNotas.emitirNotaCompra(notaCompra, pagamentos);
    }

    public BigDecimal abaterPerdasPosVendaEmNovaCompra(NotaCompra novaCompra, Produto filtroProdutoOpcional) {
        List<NotaPerda> perdasElegiveis = manageNotas.buscarPerdasPosVendaValidadePorFornecedor(
                novaCompra.getPessoa(), filtroProdutoOpcional);
        return manageNotas.abaterPerdasEmCompra(novaCompra, perdasElegiveis);
    }

    public void adicionarReposicaoSemCustoDePerdaNaCompra(NotaCompra novaCompra, NotaPerda perdaElegivel) {
        novaCompra.adicionarItensPerdidosReposicao(perdaElegivel);
        novaCompra.adicionarNotaPerda(perdaElegivel);
    }

    public Map<Produto, BigDecimal> sugerirPrecosVendaDaCompra(NotaCompra notaCompra, BigDecimal margemDesejada) {
        return notaCompra.sugerirPrecoVendaItens(margemDesejada);
    }

    // ==================== ROTAS DE NOTA DE VENDA (FIFO, LOTE DESEJÁVEL E CLIENTE PADRÃO) ====================

    /**
     * Rota de Venda via Algoritmo FIFO para cliente registrado ou para o Cliente Padrão (quando cliente == null).
     */
    public NotaVenda emitirVendaFIFO(
            Pessoa clienteOuNullParaPadrao,
            String descricaoVenda,
            Map<Produto, BigDecimal> produtosEQuantidades,
            List<NotaPagamento> pagamentos,
            String cupomDescontoOpcional,
            BigDecimal valorDescontoCupom) {

        NotaVenda venda = new NotaVenda(clienteOuNullParaPadrao, descricaoVenda);
        if (cupomDescontoOpcional != null && !cupomDescontoOpcional.isBlank()) {
            venda.aplicarCupomDesconto(cupomDescontoOpcional, valorDescontoCupom);
        }
        return manageNotas.emitirNotaVendaFIFO(venda, produtosEQuantidades, pagamentos);
    }

    /**
     * Rota de Venda Rápida de Balcão (sem cliente registrado -> vincula automaticamente ao Cliente Padrão).
     */
    public NotaVenda emitirVendaBalcaoClienteNaoRegistradoFIFO(
            Map<Produto, BigDecimal> produtosEQuantidades,
            List<NotaPagamento> pagamentos) {

        NotaVenda vendaBalcao = new NotaVenda(null, "Venda de Balcão - Consumidor Não Registrado");
        return manageNotas.emitirNotaVendaFIFO(vendaBalcao, produtosEQuantidades, pagamentos);
    }

    /**
     * Rota de Venda escolhendo manualmente o Lote Desejável (idLote -> quantidade) para cliente registrado ou Cliente Padrão.
     */
    public NotaVenda emitirVendaPorLoteDesejado(
            Pessoa clienteOuNullParaPadrao,
            String descricaoVenda,
            Map<Long, BigDecimal> lotesDesejadosEQuantidades,
            List<NotaPagamento> pagamentos) {

        NotaVenda venda = new NotaVenda(clienteOuNullParaPadrao, descricaoVenda);
        return manageNotas.emitirNotaVendaPorLoteDesejado(venda, lotesDesejadosEQuantidades, pagamentos);
    }

    /**
     * Rota de execução direta da Stored Procedure MySQL sp_consumir_estoque_fifo.
     */
    public void executarBaixaViaProcedureMySqlFIFO(Long idNotaVenda, Long idProduto, BigDecimal quantidade, BigDecimal precoVenda) {
        notaDAO.executarProcedureBaixaFIFO(idNotaVenda, idProduto, quantidade, precoVenda);
    }

    /**
     * Rota de execução direta da Stored Procedure MySQL sp_consumir_lote_especifico.
     */
    public void executarBaixaViaProcedureMySqlLoteEspecifico(Long idNotaVenda, Long idLoteDesejado, BigDecimal quantidade, BigDecimal precoVenda) {
        notaDAO.executarProcedureBaixaLoteEspecifico(idNotaVenda, idLoteDesejado, quantidade, precoVenda);
    }

    // ==================== ROTAS DE DEVOLUÇÃO E PERDA PÓS-VENDA ====================

    /**
     * Registra uma devolução de venda. Se o produto devolvido estiver com avaria/impróprio ou vencido,
     * gera automaticamente a NotaPerda pós-venda vinculada ao fornecedor do lote de compra original.
     */
    public NotaDevolucao registrarDevolucaoVenda(
            NotaVenda notaVendaOriginal,
            Map<Item<? extends Produto>, BigDecimal> itensDevolvidosEQuantidades,
            String motivoDevolucao,
            boolean produtoImproprioGeraPerdaFornecedor) {

        NotaDevolucao devolucao = new NotaDevolucao(notaVendaOriginal, motivoDevolucao);
        for (Map.Entry<Item<? extends Produto>, BigDecimal> entry : itensDevolvidosEQuantidades.entrySet()) {
            devolucao.adicionarItemDevolvido(entry.getKey(), entry.getValue());
        }
        notaVendaOriginal.registrarDevolucao(devolucao);

        if (produtoImproprioGeraPerdaFornecedor) {
            NotaPerda perdaPosVenda = devolucao.gerarNotaPerdaPosVenda("PERDA_POS_VENDA_VALIDADE", motivoDevolucao);
            manageNotas.vincularFatoresExternosNaNota(perdaPosVenda);
        }
        return manageNotas.emitirNotaDevolucao(devolucao);
    }

    public List<NotaPerda> buscarPerdasPosVendaValidadePorFornecedor(Pessoa fornecedor, Produto produtoOpcional) {
        return manageNotas.buscarPerdasPosVendaValidadePorFornecedor(fornecedor, produtoOpcional);
    }

    // ==================== ROTAS DE RELATÓRIOS CONTÁBEIS E SAZONAIS (VIEWS) ====================

    public List<Object[]> consultarRelatorioLucroContabilBacen(Long idNotaVendaOpcional) {
        return notaDAO.consultarViewApuracaoLucroContabil(idNotaVendaOpcional);
    }

    public List<Object[]> consultarRelatorioPerdasPorFornecedor(
            Pessoa fornecedor,
            Produto produtoOpcional,
            boolean apenasPosVendaDentroValidade) {
        return manageNotas.consultarRelatorioViewPerdasFornecedor(fornecedor, produtoOpcional, apenasPosVendaDentroValidade);
    }

    public List<Object[]> consultarRelatorioAnaliseSazonalVendas() {
        return notaDAO.consultarViewAnaliseSazonalVendas();
    }

    public Nota buscarNotaPorId(Long idNota) {
        return notaDAO.buscarNotaPorId(idNota);
    }

    public <T extends Nota> List<T> listarNotasPorTipo(Class<T> tipoNota) {
        return notaDAO.listarNotasPorTipo(tipoNota);
    }
}
