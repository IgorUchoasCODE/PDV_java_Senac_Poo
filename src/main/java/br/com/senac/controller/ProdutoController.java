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

        String sigla = siglaUm != null && !siglaUm.isBlank() ? siglaUm.trim() : "UN";
        Um um = umDAO.buscarPorSigla(sigla);
        if (um == null) {
            throw new IllegalArgumentException("A unidade de medida \"" + sigla + "\" não está cadastrada. "
                    + "Cadastre a unidade antes ou use uma existente (ex.: UN, KG, CX).");
        }
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

    public Produto buscarUnicoComEstoqueValido(Long id, String nomeOuTermo) {
        return produtoDAO.buscarUnicoComEstoqueValido(id, nomeOuTermo);
    }

    public List<Produto> listarProdutosComEstoqueValido() {
        return produtoDAO.listarProdutosComEstoqueValido();
    }

    // ==================== ROTAS DE MANUTENÇÃO (EXCLUSÃO E PREÇO) ====================

    public List<Produto> buscarPorTermo(String termo) {
        return produtoDAO.buscarPorTermo(termo);
    }

    /**
     * Busca um produto específico por ID ou palavras parciais/iniciais ("ov branc tip a" ou "o b a").
     * Retorna o {@link Produto} único se encontrado especificamente, ou uma {@link List} de candidatos
     * caso existam múltiplos produtos compatíveis para o usuário escolher.
     */
    public Object buscarEspecificoOuListarParaEscolha(Long id, String nomeOuTermo, boolean apenasEstoqueValido) {
        return produtoDAO.buscarEspecificoOuListarParaEscolha(id, nomeOuTermo, apenasEstoqueValido);
    }

    public boolean excluirProduto(Long idProduto) {
        return excluirProdutoPorIdOuNome(idProduto, null);
    }

    public boolean excluirProdutoPorIdOuNome(Long idProduto, String nomeProduto) {
        Produto p = resolverPorIdOuNome(idProduto, nomeProduto);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado para exclusão (ID: " + idProduto + ", Nome: " + nomeProduto + ").");
        }
        produtoDAO.deletar(p.getId());
        return true;
    }

    public Produto atualizarPrecoVenda(Long idProduto, BigDecimal novoPreco) {
        return atualizarPrecoVendaPorIdOuNome(idProduto, null, novoPreco);
    }

    public Produto atualizarPrecoVendaPorIdOuNome(Long idProduto, String nomeProduto, BigDecimal novoPreco) {
        Produto p = resolverPorIdOuNome(idProduto, nomeProduto);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado para atualização de preço.");
        }
        p.setPrecoVenda(novoPreco);
        return produtoDAO.atualizar(p);
    }

    public Produto atualizarProduto(Long idProduto, String novoNome, String novaDescricao) {
        return atualizarProdutoPorIdOuNome(idProduto, null, novoNome, novaDescricao);
    }

    public Produto atualizarProdutoPorIdOuNome(Long idProduto, String nomeAtual, String novoNome, String novaDescricao) {
        Produto p = resolverPorIdOuNome(idProduto, nomeAtual);
        if (p == null) {
            throw new IllegalArgumentException("Produto não encontrado para atualização (ID: " + idProduto + ", Nome: " + nomeAtual + ").");
        }
        if (novoNome != null && !novoNome.isBlank()) {
            p.setNome(novoNome.trim());
        }
        if (novaDescricao != null) {
            p.setDescricao(novaDescricao.trim());
        }
        return produtoDAO.atualizar(p);
    }

    // ==================== CHAVE ÚNICA (NOME + UNIDADE + CONJUNTO) ====================

    /** Busca EXATA pela chave natural do produto. Nunca devolve um produto de nome parecido. */
    public Produto buscarPorChaveUnica(String nome, String siglaUm, boolean conjunto) {
        return produtoDAO.buscarPorChaveUnica(nome, siglaUm, conjunto);
    }

    /** Todos os produtos com exatamente este nome (um por unidade/tipo). */
    public List<Produto> listarPorNomeExato(String nome) {
        return produtoDAO.listarPorNomeExato(nome);
    }

    public boolean unidadeExiste(String sigla) {
        return sigla != null && umDAO.buscarPorSigla(sigla.trim()) != null;
    }

    /**
     * Localiza o produto por código ou por nome. Se o nome existir em mais de uma unidade, não adivinha:
     * pede o código com a lista das opções.
     */
    private Produto resolverPorIdOuNome(Long id, String nome) {
        Produto p = id != null ? produtoDAO.buscarPorId(id) : null;
        if (p != null || nome == null || nome.isBlank()) {
            return p;
        }
        List<Produto> exatos = produtoDAO.listarPorNomeExato(nome);
        if (exatos.size() > 1) {
            StringBuilder sb = new StringBuilder("Há mais de um produto chamado \"" + nome.trim() + "\". Informe o código:");
            for (Produto o : exatos) {
                sb.append(" #").append(o.getId()).append(" (")
                        .append(o.getUm() != null ? o.getUm().getSigla() : "sem unidade")
                        .append(o.isConjunto() ? ", conjunto" : "").append(");");
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return exatos.size() == 1 ? exatos.get(0) : produtoDAO.buscarPorNome(nome);
    }
}
