package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoDigital;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;

public class ProdutoDAO {

    public void salvar(Produto produto) {
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
            em.persist(produto);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public Produto atualizar(Produto produto) {
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

    public Produto buscarPorId(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(Produto.class, id);
        }
    }

    public Produto buscarPorNome(String nome) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT p FROM Produto p WHERE p.nome = :nome", Produto.class)
                    .setParameter("nome", nome)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
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

    public void deletar(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Produto produto = em.find(Produto.class, id);
            if (produto != null) {
                em.remove(produto);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
