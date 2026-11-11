package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.produto.LinkProdutoDigital;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class LinkProdutoDigitalDAO implements PadraoDAO<LinkProdutoDigital, Long> {

    @Override
    public LinkProdutoDigital salvar(LinkProdutoDigital link) {
        if (link != null && link.getId() != null && isBloqueadoParaBandejaIA(link.getId())) {
            throw new IllegalStateException("Link digital #" + link.getId() + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (link.getId() == null) {
                em.persist(link);
            } else {
                em.merge(link);
            }
            tx.commit();
            return link;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public LinkProdutoDigital atualizar(LinkProdutoDigital link) {
        if (link != null && link.getId() != null && isBloqueadoParaBandejaIA(link.getId())) {
            throw new IllegalStateException("Link digital #" + link.getId() + " está bloqueado na Bandeja de Contexto da IA.");
        }
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

    @Override
    public LinkProdutoDigital buscarPorId(Long id) {
        if (id == null) return null;
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

    @Override
    public List<LinkProdutoDigital> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT l FROM LinkProdutoDigital l ORDER BY l.id", LinkProdutoDigital.class)
                    .getResultList();
        }
    }

    @Override
    public boolean deletar(Long id) {
        if (id == null) return false;
        if (isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Link digital #" + id + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            LinkProdutoDigital link = em.find(LinkProdutoDigital.class, id);
            if (link != null) {
                em.remove(link);
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
