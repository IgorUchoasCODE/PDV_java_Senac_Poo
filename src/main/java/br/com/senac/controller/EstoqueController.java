package br.com.senac.controller;

import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.service.EstoqueManage;
import br.com.senac.service.ManageNotas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller responsável pelas rotas de Controle de Estoque, Consulta de Fila de Lotes FIFO,
 * Escolha de Lote Específico, Alertas de Validade e Baixa de Perdas em Estoque.
 */
public class EstoqueController {

    private final EstoqueManage estoqueManage;
    private final ManageNotas manageNotas;
    private final NotaDAO notaDAO = new NotaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();

    public EstoqueController(EstoqueManage estoqueManage, ManageNotas manageNotas) {
        this.estoqueManage = estoqueManage;
        this.manageNotas = manageNotas;
    }

    /**
     * Rota que consulta o saldo total disponível de um produto usando a View otimizada vw_saldo_consolidado_estoque.
     */
    public BigDecimal consultarSaldoTotalProduto(Long idProduto) {
        return notaDAO.consultarSaldoTotalProduto(idProduto);
    }

    /**
     * Rota que retorna as entidades Item (lotes de compra) na ordem exata do algoritmo FIFO.
     */
    public List<Item<? extends Produto>> listarLotesDisponiveisFIFO(Long idProduto) {
        Produto p = produtoDAO.buscarPorId(idProduto);
        if (p == null) {
            return List.of();
        }
        return estoqueManage.buscarLotesDisponiveisFIFO(p);
    }

    /**
     * Rota que consulta diretamente a View vw_lotes_fifo_disponiveis para exibição rápida de lotes ao operador.
     */
    public List<Object[]> consultarViewFilaLotesFIFO(Long idProduto) {
        return notaDAO.consultarViewFilaLotesFIFO(idProduto);
    }

    /**
     * Rota que busca um lote específico pelo ID para conferência ou seleção manual de lote desejável.
     */
    public Item<? extends Produto> buscarLoteEspecificoPorId(Long idLote) {
        return notaDAO.buscarLotePorId(idLote);
    }

    public Item<? extends Produto> buscarPrimeiroLoteComEstoqueValido(Long idProduto) {
        return notaDAO.buscarPrimeiroLoteComEstoqueValido(idProduto);
    }

    public List<Item<? extends Produto>> listarTodosLotesComEstoqueValido() {
        return notaDAO.listarTodosLotesComEstoqueValido();
    }

    public List<Produto> listarTodosProdutosComEstoqueValido() {
        return produtoDAO.listarProdutosComEstoqueValido();
    }

    public Produto buscarUnicoProdutoComEstoqueValido(Long idProduto, String nomeOuTermo) {
        return produtoDAO.buscarUnicoComEstoqueValido(idProduto, nomeOuTermo);
    }

    /**
     * Rota que lista todos os lotes com saldo > 0 cuja validade vence até a data limite informada.
     */
    public List<Item<? extends Produto>> consultarLotesProximosAoVencimento(LocalDate dataLimite) {
        return estoqueManage.listarItensProximosValidade(dataLimite);
    }

    /**
     * Rota que registra e persiste uma baixa de perda física diretamente sobre um lote de estoque específico.
     */
    public NotaPerda baixarPerdaEmLoteEstoque(Long idLoteOrigem, BigDecimal quantidadePerdida, String motivo) {
        Item<? extends Produto> lote = notaDAO.buscarLotePorId(idLoteOrigem);
        if (lote == null) {
            throw new IllegalArgumentException("Lote de estoque não encontrado (ID " + idLoteOrigem + ").");
        }
        NotaPerda perda = estoqueManage.registrarPerdaEstoque(lote, quantidadePerdida, motivo);
        return manageNotas.emitirNotaPerda(perda);
    }
}
