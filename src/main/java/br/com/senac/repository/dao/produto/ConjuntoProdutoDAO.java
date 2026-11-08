package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.ConjuntoProdutoId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class ConjuntoProdutoDAO {

    public void salvar(ConjuntoProduto conjuntoProduto) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.merge(conjuntoProduto);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public ConjuntoProduto atualizar(ConjuntoProduto conjuntoProduto) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ConjuntoProduto atualizado = em.merge(conjuntoProduto);
            tx.commit();
            return atualizado;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public ConjuntoProduto buscarPorId(Long idProduto, Long idItem) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(ConjuntoProduto.class, new ConjuntoProdutoId(idProduto, idItem));
        }
    }

    public List<ConjuntoProduto> listarPorProduto(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT cp FROM ConjuntoProduto cp JOIN FETCH cp.item i LEFT JOIN FETCH i.um WHERE cp.produto.id = :idProduto",
                            ConjuntoProduto.class)
                    .setParameter("idProduto", idProduto)
                    .getResultList();
        }
    }

    public void deletar(Long idProduto, Long idItem) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ConjuntoProduto cp = em.find(ConjuntoProduto.class, new ConjuntoProdutoId(idProduto, idItem));
            if (cp != null) {
                em.remove(cp);
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
