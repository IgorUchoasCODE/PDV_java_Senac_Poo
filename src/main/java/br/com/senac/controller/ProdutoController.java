package br.com.senac.controller;

import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.produto.*;
import br.com.senac.service.EstoqueManage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller responsável pelas rotas de Catálogo de Produtos (Físicos com Dimensões, Digitais e Kits/Conjuntos)
 * e Unidades de Medida (Um).
 */
public class ProdutoController {

    private final EstoqueManage estoqueManage;
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final UmDAO umDAO = new UmDAO();

    public ProdutoController(EstoqueManage estoqueManage) {
        this.estoqueManage = estoqueManage;
    }

    // ==================== ROTAS DE UNIDADE DE MEDIDA (Um) ====================

    public Um cadastrarUnidadeMedida(String sigla, String nome, String descricao, BigDecimal baseFracionamento) {
        Um existente = umDAO.buscarPorSigla(sigla);
        if (existente != null) {
            return existente;
        }
        Um nova = new Um(sigla, nome, descricao, baseFracionamento);
        umDAO.salvar(nova);
        return nova;
    }

    public Um buscarUnidadePorSigla(String sigla) {
        return umDAO.buscarPorSigla(sigla);
    }

    public List<Um> listarUnidadesMedida() {
        return umDAO.listarTodos();
    }

    public Um atualizarUnidadeMedida(String sigla, String novoNome, String novaDescricao, BigDecimal novaBaseFracionamento) {
        Um um = umDAO.buscarPorSigla(sigla);
        if (um == null) {
            throw new IllegalArgumentException("Unidade de medida não encontrada para a sigla: " + sigla);
        }
        if (novoNome != null && !novoNome.isBlank()) {
            um.setNome(novoNome);
        }
        if (novaDescricao != null) {
            um.setDescricao(novaDescricao);
        }
        if (novaBaseFracionamento != null && novaBaseFracionamento.compareTo(BigDecimal.ZERO) > 0) {
            um.setBase(novaBaseFracionamento);
        }
        return umDAO.atualizar(um);
    }

    public boolean excluirUnidadeMedida(String sigla) {
        Um um = umDAO.buscarPorSigla(sigla);
        if (um == null) {
            throw new IllegalArgumentException("Unidade de medida não encontrada para a sigla: " + sigla);
        }
        umDAO.deletar(um.getId());
        return true;
    }

    // ==================== ROTAS DE PRODUTO FÍSICO (COM DIMENSÕES) ====================

    public ProdutoFisico cadastrarProdutoFisico(
            String nome,
            String descricao,
            BigDecimal precoVenda,
            String siglaUm,
            BigDecimal alturaCm,
            BigDecimal larguraCm,
            BigDecimal comprimentoCm,
            BigDecimal pesoBrutoKg,
            BigDecimal pesoLiquidoKg) {

        Um um = umDAO.buscarPorSigla(siglaUm != null ? siglaUm : "UN");
        ProdutoFisico pf = new ProdutoFisico(nome, um, alturaCm, larguraCm, comprimentoCm, pesoBrutoKg, pesoLiquidoKg);
        pf.setDescricao(descricao);
        pf.setPrecoVenda(precoVenda);
        if (!pf.possuiDimensoesValidas()) {
            throw new IllegalArgumentException("Dimensões ou pesos inválidos para o produto físico: " + nome);
        }
        estoqueManage.cadastrarProduto(pf);
        return pf;
    }

    public ProdutoFisico atualizarDimensoesProdutoFisico(
            Long idProduto,
            BigDecimal alturaCm,
            BigDecimal larguraCm,
            BigDecimal comprimentoCm,
            BigDecimal pesoBrutoKg,
            BigDecimal pesoLiquidoKg) {

        Produto p = produtoDAO.buscarPorId(idProduto);
        if (!(p instanceof ProdutoFisico pf)) {
            throw new IllegalArgumentException("O produto ID " + idProduto + " não é um ProdutoFisico.");
        }
        pf.definirDimensoes(alturaCm, larguraCm, comprimentoCm, pesoBrutoKg, pesoLiquidoKg);
        if (!pf.possuiDimensoesValidas()) {
            throw new IllegalArgumentException("As dimensões informadas são inválidas (peso bruto deve ser >= peso líquido).");
        }
        estoqueManage.atualizarCatalogo(pf);
        return pf;
    }

    // ==================== ROTAS DE PRODUTO DIGITAL ====================

    public ProdutoDigital cadastrarProdutoDigital(
            String nome,
            String descricao,
            BigDecimal precoVenda,
            List<LinkProdutoDigital> links) {

        Um um = umDAO.buscarPorSigla("UN");
        ProdutoDigital pd = new ProdutoDigital(nome, um);
        pd.setDescricao(descricao);
        pd.setPrecoVenda(precoVenda);
        if (links != null) {
            links.forEach(pd::adicionarLink);
        }
        estoqueManage.cadastrarProduto(pd);
        return pd;
    }

    // ==================== ROTAS DE KIT / CONJUNTO ====================

    public void adicionarComponenteAoKit(Long idProdutoKit, Long idProdutoComponente, BigDecimal quantidadeComponente) {
        Produto kit = produtoDAO.buscarPorId(idProdutoKit);
        Produto comp = produtoDAO.buscarPorId(idProdutoComponente);
        if (kit == null || comp == null) {
            throw new IllegalArgumentException("Kit ou componente não encontrado.");
        }
        estoqueManage.montarConjunto(kit, comp, quantidadeComponente);
    }

    // ==================== ROTAS DE CONSULTA DE CATÁLOGO ====================

    public Produto buscarPorId(Long id) {
        return produtoDAO.buscarPorId(id);
    }

    public Produto buscarPorNome(String nome) {
        return produtoDAO.buscarPorNome(nome);
    }

    public List<Produto> listarTodosProdutos() {
        return produtoDAO.listarTodos();
    }

    // ==================== ROTAS DE MANUTENÇÃO (EXCLUSÃO E PREÇO) ====================

    public boolean excluirProduto(Long idProduto) {
        Produto p = produtoDAO.buscarPorId(idProduto);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado com ID: " + idProduto);
        }
        produtoDAO.deletar(idProduto);
        return true;
    }

    public Produto atualizarPrecoVenda(Long idProduto, BigDecimal novoPreco) {
        Produto p = produtoDAO.buscarPorId(idProduto);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado com ID: " + idProduto);
        }
        p.setPrecoVenda(novoPreco);
        produtoDAO.salvar(p);
        return p;
    }

    public Produto atualizarProduto(Long idProduto, String novoNome, String novaDescricao) {
        Produto p = produtoDAO.buscarPorId(idProduto);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado com ID: " + idProduto);
        }
        if (novoNome != null && !novoNome.isBlank()) {
            p.setNome(novoNome);
        }
        if (novaDescricao != null) {
            p.setDescricao(novaDescricao);
        }
        return produtoDAO.atualizar(p);
    }
}
