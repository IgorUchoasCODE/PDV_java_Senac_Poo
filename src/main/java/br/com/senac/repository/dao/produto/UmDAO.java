package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.produto.Um;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;

public class UmDAO implements PadraoDAO<Um, Long> {

    @Override
    public Um salvar(Um um) {
        if (um != null && um.getId() != null && isBloqueadoParaBandejaIA(um.getId())) {
            throw new IllegalStateException("Unidade de medida #" + um.getId() + " está bloqueada na Bandeja de Contexto da IA.");
        }
        return executarComFallbackReacao(() -> {
            EntityManager em = JPAUtil.getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                if (um.getId() == null) {
                    em.persist(um);
                } else {
                    em.merge(um);
                }
                tx.commit();
                return um;
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        }, e -> {
            if (um != null && um.getSigla() != null) {
                Um existente = buscarPorSigla(um.getSigla());
                if (existente != null) return existente;
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        });
    }

    @Override
    public Um atualizar(Um um) {
        if (um != null && um.getId() != null && isBloqueadoParaBandejaIA(um.getId())) {
            throw new IllegalStateException("Unidade de medida #" + um.getId() + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Um atualizado = em.merge(um);
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
    public Um buscarPorId(Long id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(Um.class, id);
        }
    }

    public Um buscarPorSigla(String sigla) {
        if (sigla == null || sigla.isBlank()) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT u FROM Um u WHERE UPPER(TRIM(u.sigla)) = UPPER(TRIM(:sigla))", Um.class)
                    .setParameter("sigla", sigla.trim())
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public List<Um> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT u FROM Um u ORDER BY u.nome", Um.class)
                    .getResultList();
        }
    }

    @Override
    public boolean deletar(Long id) {
        if (id == null) return false;
        if (isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Unidade de medida #" + id + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Um um = em.find(Um.class, id);
            if (um != null) {
                em.remove(um);
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
