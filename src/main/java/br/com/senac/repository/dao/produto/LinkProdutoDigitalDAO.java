package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.produto.LinkProdutoDigital;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class LinkProdutoDigitalDAO {

    public void salvar(LinkProdutoDigital link) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(link);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public LinkProdutoDigital atualizar(LinkProdutoDigital link) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            LinkProdutoDigital atualizado = em.merge(link);
            tx.commit();
            return atualizado;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public LinkProdutoDigital buscarPorId(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(LinkProdutoDigital.class, id);
        }
    }

    public List<LinkProdutoDigital> listarPorProdutoDigital(Long produtoDigitalId) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT l FROM LinkProdutoDigital l WHERE l.produtoDigital.id = :produtoId ORDER BY l.id",
                            LinkProdutoDigital.class)
                    .setParameter("produtoId", produtoDigitalId)
                    .getResultList();
        }
    }

    public void deletar(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            LinkProdutoDigital link = em.find(LinkProdutoDigital.class, id);
            if (link != null) {
                em.remove(link);
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
