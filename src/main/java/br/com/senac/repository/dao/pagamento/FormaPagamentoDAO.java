package br.com.senac.repository.dao.pagamento;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;

public class FormaPagamentoDAO implements PadraoDAO<FormaPagamento, Long> {

    @Override
    public FormaPagamento salvar(FormaPagamento formaPagamento) {
        if (formaPagamento != null && formaPagamento.getId() != null && isBloqueadoParaBandejaIA(formaPagamento.getId())) {
            throw new IllegalStateException("Forma de pagamento #" + formaPagamento.getId() + " está bloqueada na Bandeja de Contexto da IA.");
        }
        return executarComFallbackReacao(() -> {
            EntityManager em = JPAUtil.getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                FormaPagamento salva = formaPagamento;
                if (salva.getId() == null) {
                    em.persist(salva);
                } else {
                    salva = em.merge(salva);
                }
                tx.commit();
                return salva;
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        }, e -> {
            if (formaPagamento != null && formaPagamento.getCodigo() != null) {
                FormaPagamento existente = buscarPorCodigo(formaPagamento.getCodigo());
                if (existente != null) return existente;
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        });
    }

    @Override
    public FormaPagamento atualizar(FormaPagamento formaPagamento) {
        return salvar(formaPagamento);
    }

    @Override
    public FormaPagamento buscarPorId(Long id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(FormaPagamento.class, id);
        }
    }

    public FormaPagamento buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT fp FROM FormaPagamento fp WHERE UPPER(TRIM(fp.codigo)) = UPPER(TRIM(:codigo))", FormaPagamento.class)
                    .setParameter("codigo", codigo.trim())
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

    @Override
    public List<FormaPagamento> listarTodos() {
        return listarTodas();
    }

    @Override
    public boolean deletar(Long id) {
        if (id == null) return false;
        if (isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Forma de pagamento #" + id + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            FormaPagamento fp = em.find(FormaPagamento.class, id);
            if (fp != null) {
                em.remove(fp);
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

    public boolean deletarPorCodigo(String codigo) {
        FormaPagamento fp = buscarPorCodigo(codigo);
        if (fp != null) {
            return deletar(fp.getId());
        }
        return false;
    }
}
