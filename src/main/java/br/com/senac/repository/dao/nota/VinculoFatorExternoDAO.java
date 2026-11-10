package br.com.senac.repository.dao.nota;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.Optional;

public class VinculoFatorExternoDAO {

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
}
