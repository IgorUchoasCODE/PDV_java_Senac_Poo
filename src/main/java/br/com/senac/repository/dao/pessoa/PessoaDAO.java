package br.com.senac.repository.dao.pessoa;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class PessoaDAO {

    public static final String CPF_CLIENTE_PADRAO = "000.000.000-00";
    public static final String NOME_CLIENTE_PADRAO = "CONSUMIDOR FINAL (NÃO REGISTRADO)";

    public Pessoa salvar(Pessoa pessoa) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (pessoa.getId() == null) {
                em.persist(pessoa);
            } else {
                pessoa = em.merge(pessoa);
            }
            if (pessoa.getRegistros() != null) {
                pessoa.getRegistros().size();
            }
            tx.commit();
            return pessoa;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public Pessoa buscarPorId(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE p.id = :id",
                            Pessoa.class)
                    .setParameter("id", id)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public PessoaFisica buscarPorCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return null;
        }
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT pf FROM PessoaFisica pf LEFT JOIN FETCH pf.registros WHERE pf.cpf = :cpf",
                            PessoaFisica.class)
                    .setParameter("cpf", cpf)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public PessoaJuridica buscarPorCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return null;
        }
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery(
                            "SELECT DISTINCT pj FROM PessoaJuridica pj LEFT JOIN FETCH pj.registros WHERE pj.cnpj = :cnpj",
                            PessoaJuridica.class)
                    .setParameter("cnpj", cnpj)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtém (ou cria caso ainda não exista) o Cliente Padrão que recebe todas as vendas
     * de consumidores não registrados/identificados no caixa.
     */
    public synchronized PessoaFisica obterOuCriarClientePadrao() {
        PessoaFisica padrao = buscarPorCpf(CPF_CLIENTE_PADRAO);
        if (padrao != null) {
            return padrao;
        }
        PessoaFisica novoPadrao = new PessoaFisica(NOME_CLIENTE_PADRAO, CPF_CLIENTE_PADRAO);
        novoPadrao.definirComoCliente(MoedaContabilBacen.ZERO_MOEDA);
        return (PessoaFisica) salvar(novoPadrao);
    }

    public boolean isClientePadrao(Pessoa pessoa) {
        if (pessoa instanceof PessoaFisica pf) {
            return CPF_CLIENTE_PADRAO.equals(pf.getCpf());
        }
        return false;
    }

    public List<Pessoa> listarTodos() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros ORDER BY p.id", Pessoa.class)
                    .getResultList();
        }
    }

    public List<Pessoa> listarClientes() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE p.isCliente = true ORDER BY p.nome", Pessoa.class)
                    .getResultList();
        }
    }

    public List<Pessoa> listarFornecedores() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE p.isFornecedor = true ORDER BY p.nome", Pessoa.class)
                    .getResultList();
        }
    }

    /**
     * Busca clientes ou fornecedores por termo: parte do nome, CPF ou CNPJ (com ou sem pontuação).
     */
    @SuppressWarnings("unchecked")
    public List<Pessoa> buscarPorTermo(String termo, String papel) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        String t = termo.trim();
        String digitos = t.replaceAll("\\D", "");

        try (EntityManager em = JPAUtil.getEntityManager()) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT p.id FROM pessoa p WHERE (");
            sql.append("LOWER(p.nome) LIKE LOWER(:likeTermo) ");
            if (!digitos.isEmpty()) {
                sql.append("OR REPLACE(REPLACE(REPLACE(p.cpf, '.', ''), '-', ''), '/', '') LIKE :docLike ");
                sql.append("OR REPLACE(REPLACE(REPLACE(p.cnpj, '.', ''), '-', ''), '/', '') LIKE :docLike ");
            }
            sql.append(") ");

            if ("CLIENTE".equalsIgnoreCase(papel)) {
                sql.append("AND p.is_cliente = 1 ");
            } else if ("FORNECEDOR".equalsIgnoreCase(papel)) {
                sql.append("AND p.is_fornecedor = 1 ");
            }
            sql.append("ORDER BY p.id LIMIT 50");

            var nativeQuery = em.createNativeQuery(sql.toString());
            nativeQuery.setParameter("likeTermo", "%" + t + "%");
            if (!digitos.isEmpty()) {
                nativeQuery.setParameter("docLike", "%" + digitos + "%");
            }

            List<Number> ids = (List<Number>) nativeQuery.getResultList();
            if (ids.isEmpty()) {
                return List.of();
            }

            List<Long> longIds = ids.stream().map(Number::longValue).toList();
            return em.createQuery(
                            "SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE p.id IN (:ids) ORDER BY p.nome",
                            Pessoa.class)
                    .setParameter("ids", longIds)
                    .getResultList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void deletar(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Pessoa p = em.find(Pessoa.class, id);
            if (p != null) {
                em.remove(p);
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
