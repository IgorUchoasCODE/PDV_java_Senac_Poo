package br.com.senac.repository.dao.nota;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;
import java.util.Optional;

public class VinculoFatorExternoDAO implements PadraoDAO<VinculoFatorExternoNota, Long> {

    public Optional<VinculoFatorExternoNota> buscarPorChaveSnapshot(String chaveSnapshot) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            VinculoFatorExternoNota vinculo = em.createQuery(
                            "SELECT v FROM VinculoFatorExternoNota v WHERE v.chaveSnapshot = :chave",
                            VinculoFatorExternoNota.class)
                    .setParameter("chave", chaveSnapshot)
                    .getSingleResult();
            return Optional.of(vinculo);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public VinculoFatorExternoNota salvarOuReutilizar(VinculoFatorExternoNota novoVinculo) {
        Optional<VinculoFatorExternoNota> existente = buscarPorChaveSnapshot(novoVinculo.getChaveSnapshot());
        if (existente.isPresent()) {
            return existente.get();
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(novoVinculo);
            tx.commit();
            return novoVinculo;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            return buscarPorChaveSnapshot(novoVinculo.getChaveSnapshot()).orElseThrow(() -> e);
        } finally {
            em.close();
        }
    }

    @Override
    public VinculoFatorExternoNota salvar(VinculoFatorExternoNota entidade) {
        return salvarOuReutilizar(entidade);
    }

    @Override
    public VinculoFatorExternoNota atualizar(VinculoFatorExternoNota entidade) {
        if (entidade != null && entidade.getId() != null && isBloqueadoParaBandejaIA(entidade.getId())) {
            throw new IllegalStateException("Vínculo #" + entidade.getId() + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            VinculoFatorExternoNota atualizado = em.merge(entidade);
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
    public VinculoFatorExternoNota buscarPorId(Long id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(VinculoFatorExternoNota.class, id);
        }
    }

    @Override
    public List<VinculoFatorExternoNota> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT v FROM VinculoFatorExternoNota v ORDER BY v.id DESC", VinculoFatorExternoNota.class)
                    .getResultList();
        }
    }

    @Override
    public boolean deletar(Long id) {
        if (id == null) return false;
        if (isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Vínculo #" + id + " está bloqueado na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            VinculoFatorExternoNota v = em.find(VinculoFatorExternoNota.class, id);
            if (v != null) {
                em.remove(v);
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
