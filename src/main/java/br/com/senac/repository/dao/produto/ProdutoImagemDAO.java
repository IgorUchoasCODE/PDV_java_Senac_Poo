package br.com.senac.repository.dao.produto;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.produto.ProdutoImagem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

/**
 * DAO direto para persistência e recuperação de imagens de produtos.
 */
public class ProdutoImagemDAO implements PadraoDAO<ProdutoImagem, Long> {

    @Override
    public ProdutoImagem salvar(ProdutoImagem imagem) {
        if (imagem != null && imagem.getId() != null && isBloqueadoParaBandejaIA(imagem.getId())) {
            throw new IllegalStateException("Imagem #" + imagem.getId() + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ProdutoImagem salva = em.merge(imagem);
            tx.commit();
            return salva;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public ProdutoImagem atualizar(ProdutoImagem imagem) {
        return salvar(imagem);
    }

    @Override
    public ProdutoImagem buscarPorId(Long id) {
        if (id == null) return null;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.find(ProdutoImagem.class, id);
        }
    }

    @Override
    public List<ProdutoImagem> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT pi FROM ProdutoImagem pi ORDER BY pi.id", ProdutoImagem.class)
                    .getResultList();
        }
    }

    public List<ProdutoImagem> listarPorProduto(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT pi FROM ProdutoImagem pi WHERE pi.produto.id = :idProduto ORDER BY pi.isPrincipal DESC, pi.ordem ASC, pi.id ASC",
                            ProdutoImagem.class)
                    .setParameter("idProduto", idProduto)
                    .getResultList();
        }
    }

    /**
     * Retorna diretamente apenas a lista de URLs/caminhos de todas as imagens vinculadas ao produto.
     * Ideal para carrosséis, galerias e envio direto para interfaces gráficas ou IA.
     */
    public List<String> listarUrlsPorProduto(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT pi.caminhoArquivo FROM ProdutoImagem pi WHERE pi.produto.id = :idProduto ORDER BY pi.isPrincipal DESC, pi.ordem ASC, pi.id ASC",
                            String.class)
                    .setParameter("idProduto", idProduto)
                    .getResultList();
        }
    }

    public ProdutoImagem buscarPrincipal(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<ProdutoImagem> lista = em.createQuery(
                            "SELECT pi FROM ProdutoImagem pi WHERE pi.produto.id = :idProduto AND pi.isPrincipal = true",
                            ProdutoImagem.class)
                    .setParameter("idProduto", idProduto)
                    .setMaxResults(1)
                    .getResultList();
            if (!lista.isEmpty()) {
                return lista.get(0);
            }

            // Fallback: se nenhuma estiver explicitamente como principal, pega a primeira por ordem
            List<ProdutoImagem> fallback = em.createQuery(
                            "SELECT pi FROM ProdutoImagem pi WHERE pi.produto.id = :idProduto ORDER BY pi.ordem ASC, pi.id ASC",
                            ProdutoImagem.class)
                    .setParameter("idProduto", idProduto)
                    .setMaxResults(1)
                    .getResultList();
            return fallback.isEmpty() ? null : fallback.get(0);
        }
    }

    public void definirComoPrincipal(Long idProduto, Long idImagem) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            // Desmarca todas as outras imagens do produto
            em.createQuery("UPDATE ProdutoImagem pi SET pi.isPrincipal = false WHERE pi.produto.id = :idProduto")
                    .setParameter("idProduto", idProduto)
                    .executeUpdate();

            // Marca a selecionada como principal
            em.createQuery("UPDATE ProdutoImagem pi SET pi.isPrincipal = true WHERE pi.id = :idImagem AND pi.produto.id = :idProduto")
                    .setParameter("idImagem", idImagem)
                    .setParameter("idProduto", idProduto)
                    .executeUpdate();

            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public boolean remover(Long idImagem) {
        if (idImagem == null) return false;
        if (isBloqueadoParaBandejaIA(idImagem)) {
            throw new IllegalStateException("Imagem #" + idImagem + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ProdutoImagem pi = em.find(ProdutoImagem.class, idImagem);
            if (pi != null) {
                em.remove(pi);
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

    @Override
    public boolean deletar(Long idImagem) {
        return remover(idImagem);
    }

    public long contarPorProduto(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT COUNT(pi) FROM ProdutoImagem pi WHERE pi.produto.id = :idProduto",
                            Long.class)
                    .setParameter("idProduto", idProduto)
                    .getSingleResult();
        }
    }
}
