package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoDigital;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.time.LocalDate;
import java.util.List;

public class ProdutoDAO implements PadraoDAO<Produto, Long> {

    @Override
    public Produto salvar(Produto produto) {
        if (produto != null && produto.getId() != null && isBloqueadoParaBandejaIA(produto.getId())) {
            throw new IllegalStateException("Produto #" + produto.getId() + " está bloqueado na Bandeja de Contexto da IA.");
        }
        return executarComFallbackReacao(() -> {
            EntityManager em = JPAUtil.getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                if (produto.getItensConjunto() != null && !produto.getItensConjunto().isEmpty()) {
                    produto.getItensConjunto().forEach(ic -> {
                        if (ic.getItem() != null && ic.getItem().getId() != null) {
                            ic.setItem(em.getReference(Produto.class, ic.getItem().getId()));
                        }
                    });
                }
                if (produto.getId() == null) {
                    em.persist(produto);
                } else {
                    em.merge(produto);
                }
                tx.commit();
                return produto;
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        }, e -> {
            // Fallback reativo: se já existir um produto com mesmo nome único, atualiza ou retorna o existente
            if (produto != null && produto.getNome() != null) {
                Produto existente = buscarPorNomeExato(produto.getNome());
                if (existente != null) {
                    return existente;
                }
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        });
    }

    @Override
    public Produto atualizar(Produto produto) {
        if (produto != null && produto.getId() != null && isBloqueadoParaBandejaIA(produto.getId())) {
            throw new IllegalStateException("Produto #" + produto.getId() + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Produto atualizado = em.merge(produto);
            tx.commit();
            return atualizado;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Produto buscarPorId(Long id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<Produto> lista = em.createQuery(
                            "SELECT p FROM Produto p LEFT JOIN FETCH p.um WHERE p.id = :id",
                            Produto.class)
                    .setParameter("id", id)
                    .setMaxResults(1)
                    .getResultList();
            return lista.isEmpty() ? em.find(Produto.class, id) : lista.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public Produto buscarPorNomeExato(String nome) {
        if (nome == null || nome.isBlank()) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<Produto> exatos = em.createQuery(
                            "SELECT p FROM Produto p LEFT JOIN FETCH p.um WHERE LOWER(TRIM(p.nome)) = LOWER(TRIM(:nome)) ORDER BY p.id",
                            Produto.class)
                    .setParameter("nome", nome.trim())
                    .setMaxResults(1)
                    .getResultList();
            return exatos.isEmpty() ? null : exatos.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Busca um único produto pelo nome (priorizando match exato no índice idx_produto_nome e, em fallback reativo,
     * busca por palavras parciais ou iniciais como "ov branc tip a" ou "o b a").
     */
    public Produto buscarPorNome(String nome) {
        if (nome == null || nome.isBlank()) return null;
        return buscarUnicoComFallback(
                () -> buscarPorNomeExato(nome),
                () -> {
                    List<Produto> parciais = buscarPorTermo(nome);
                    return parciais.isEmpty() ? null : parciais.get(0);
                }
        );
    }

    /**
     * Busca uma informação única de produto que possua estoque válido (quantidade disponível > 0 e dentro da validade, ou produto digital),
     * suportando palavras parciais e iniciais (ex: "ov branc tip a" ou "o b a").
     */
    public Produto buscarUnicoComEstoqueValido(Long idOuNull, String nomeOuTermo) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            if (idOuNull != null) {
                List<Produto> porId = em.createQuery(
                                "SELECT DISTINCT p FROM Produto p LEFT JOIN FETCH p.um " +
                                "WHERE p.id = :id AND (TYPE(p) = ProdutoDigital OR EXISTS (" +
                                "SELECT 1 FROM Item i JOIN i.nota n WHERE i.produto = p AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                                "AND i.quantidadeDisponivel > 0 AND (i.dataValidade IS NULL OR i.dataValidade >= :hoje)))",
                                Produto.class)
                        .setParameter("id", idOuNull)
                        .setParameter("hoje", LocalDate.now())
                        .setMaxResults(1)
                        .getResultList();
                if (!porId.isEmpty()) return porId.get(0);
            }
            if (nomeOuTermo != null && !nomeOuTermo.isBlank()) {
                List<Produto> validos = listarProdutosComEstoqueValido();
                List<Produto> filtrados = BuscaPalavrasParciaisUtil.filtrarEOrdenar(validos, Produto::getNome, nomeOuTermo);
                if (!filtrados.isEmpty()) {
                    return filtrados.get(0);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retorna todos os produtos com estoque válido (lotes de compra com saldo disponível > 0 e dentro da validade, ou produtos digitais).
     */
    public List<Produto> listarProdutosComEstoqueValido() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT p FROM Produto p LEFT JOIN FETCH p.um " +
                            "WHERE TYPE(p) = ProdutoDigital OR EXISTS (" +
                            "SELECT 1 FROM Item i JOIN i.nota n WHERE i.produto = p AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                            "AND i.quantidadeDisponivel > 0 AND (i.dataValidade IS NULL OR i.dataValidade >= :hoje)) " +
                            "ORDER BY p.id",
                            Produto.class)
                    .setParameter("hoje", LocalDate.now())
                    .getResultList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Busca produtos suportando nome completo, substring, palavras parciais ("ov branc tip a")
     * ou iniciais ("o b a").
     */
    public List<Produto> buscarPorTermo(String termo) {
        if (termo == null || termo.isBlank()) return List.of();
        String t = termo.trim();
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<Produto> diretos = em.createQuery(
                    "SELECT p FROM Produto p LEFT JOIN FETCH p.um WHERE LOWER(p.nome) LIKE LOWER(:termo) ORDER BY p.id",
                    Produto.class)
                    .setParameter("termo", "%" + t + "%")
                    .getResultList();
            List<Produto> todos = listarTodos();
            List<Produto> parciais = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todos, Produto::getNome, t);
            if (!parciais.isEmpty()) {
                return parciais;
            }
            return diretos;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Busca um Produto específico por ID ou por palavras parciais/iniciais ("ov branc tip a", "o b a").
     * Se encontrar exatamente 1 produto específico (ou match exato), retorna o próprio {@link Produto}.
     * Caso não encontre um único específico mas encontre múltiplos candidatos compatíveis, retorna
     * a {@link List} de produtos para o usuário escolher.
     */
    public Object buscarEspecificoOuListarParaEscolha(Long idOuNull, String nomeOuTermo, boolean apenasEstoqueValido) {
        if (idOuNull != null) {
            return apenasEstoqueValido
                    ? buscarUnicoComEstoqueValido(idOuNull, null)
                    : buscarPorId(idOuNull);
        }
        if (nomeOuTermo == null || nomeOuTermo.isBlank()) {
            return apenasEstoqueValido ? listarProdutosComEstoqueValido() : listarTodos();
        }
        List<Produto> base = apenasEstoqueValido ? listarProdutosComEstoqueValido() : listarTodos();
        for (Produto p : base) {
            if (BuscaPalavrasParciaisUtil.isMatchExato(p.getNome(), nomeOuTermo)) {
                return p;
            }
        }
        List<Produto> candidatos = BuscaPalavrasParciaisUtil.filtrarEOrdenar(base, Produto::getNome, nomeOuTermo);
        if (candidatos.isEmpty()) {
            return null;
        }
        if (candidatos.size() == 1) {
            return candidatos.get(0);
        }
        return candidatos;
    }

    public ProdutoDigital buscarDigitalComLinks(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT pd FROM ProdutoDigital pd LEFT JOIN FETCH pd.links WHERE pd.id = :id",
                            ProdutoDigital.class)
                    .setParameter("id", id)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Produto buscarComItensConjunto(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT p FROM Produto p LEFT JOIN FETCH p.itensConjunto ic LEFT JOIN FETCH ic.item WHERE p.id = :id",
                            Produto.class)
                    .setParameter("id", id)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<Produto> listarConjuntos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT p FROM Produto p LEFT JOIN FETCH p.itensConjunto ic LEFT JOIN FETCH ic.item LEFT JOIN FETCH p.um WHERE p.isConjunto = true ORDER BY p.id",
                            Produto.class)
                    .getResultList();
        }
    }

    @Override
    public List<Produto> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT p FROM Produto p LEFT JOIN FETCH p.um ORDER BY p.id", Produto.class)
                    .getResultList();
        }
    }

    public List<ProdutoDigital> listarDigitais() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT pd FROM ProdutoDigital pd LEFT JOIN FETCH pd.links LEFT JOIN FETCH pd.um ORDER BY pd.id",
                            ProdutoDigital.class)
                    .getResultList();
        }
    }

    public List<ProdutoFisico> listarFisicos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT pf FROM ProdutoFisico pf LEFT JOIN FETCH pf.um ORDER BY pf.id",
                            ProdutoFisico.class)
                    .getResultList();
        }
    }

    @Override
    public boolean deletar(Long id) {
        if (id != null && isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Produto #" + id + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Produto produto = em.find(Produto.class, id);
            if (produto != null) {
                em.remove(produto);
                tx.commit();
                return true;
            }
            tx.commit();
            return false;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
