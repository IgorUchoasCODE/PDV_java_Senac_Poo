package br.com.senac.repository.dao.pagamento;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;

public class FormaPagamentoDAO {

    public FormaPagamento salvar(FormaPagamento formaPagamento) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (formaPagamento.getId() == null) {
                em.persist(formaPagamento);
            } else {
                formaPagamento = em.merge(formaPagamento);
            }
            tx.commit();
            return formaPagamento;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public FormaPagamento buscarPorCodigo(String codigo) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT fp FROM FormaPagamento fp WHERE fp.codigo = :codigo", FormaPagamento.class)
                    .setParameter("codigo", codigo)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<FormaPagamento> listarAtivas() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT fp FROM FormaPagamento fp WHERE fp.ativo = true ORDER BY fp.id", FormaPagamento.class)
                    .getResultList();
        }
    }

    public List<FormaPagamento> listarTodas() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT fp FROM FormaPagamento fp ORDER BY fp.id", FormaPagamento.class)
                    .getResultList();
        }
    }

    public void deletar(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            FormaPagamento fp = em.find(FormaPagamento.class, id);
            if (fp != null) {
                em.remove(fp);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public boolean deletarPorCodigo(String codigo) {
        FormaPagamento fp = buscarPorCodigo(codigo);
        if (fp != null) {
            deletar(fp.getId());
            return true;
        }
        return false;
    }
}
