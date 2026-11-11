package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.ConjuntoProdutoId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class ConjuntoProdutoDAO implements PadraoDAO<ConjuntoProduto, ConjuntoProdutoId> {

    @Override
    public ConjuntoProduto salvar(ConjuntoProduto conjuntoProduto) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ConjuntoProduto salvo = em.merge(conjuntoProduto);
            tx.commit();
            return salvo;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public ConjuntoProduto atualizar(ConjuntoProduto conjuntoProduto) {
        return salvar(conjuntoProduto);
    }

    @Override
    public ConjuntoProduto buscarPorId(ConjuntoProdutoId id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(ConjuntoProduto.class, id);
        }
    }

    public ConjuntoProduto buscarPorId(Long idProduto, Long idItem) {
        return buscarPorId(new ConjuntoProdutoId(idProduto, idItem));
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

    @Override
    public List<ConjuntoProduto> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT cp FROM ConjuntoProduto cp JOIN FETCH cp.item i LEFT JOIN FETCH i.um",
                            ConjuntoProduto.class)
                    .getResultList();
        }
    }

    @Override
    public boolean deletar(ConjuntoProdutoId id) {
        if (id == null) return false;
        if (isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Componente de conjunto " + id + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ConjuntoProduto cp = em.find(ConjuntoProduto.class, id);
            if (cp != null) {
                em.remove(cp);
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

    public void deletar(Long idProduto, Long idItem) {
        deletar(new ConjuntoProdutoId(idProduto, idItem));
    }
}
