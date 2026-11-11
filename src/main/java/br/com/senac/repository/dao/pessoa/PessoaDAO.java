package br.com.senac.repository.dao.pessoa;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.pessoa.Registro;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

public class PessoaDAO implements PadraoDAO<Pessoa, Long> {

    public static final String CPF_CLIENTE_PADRAO = "000.000.000-00";
    public static final String NOME_CLIENTE_PADRAO = "CONSUMIDOR FINAL (NÃO REGISTRADO)";

    @Override
    public Pessoa salvar(Pessoa pessoa) {
        if (pessoa != null && pessoa.getId() != null && isBloqueadoParaBandejaIA(pessoa.getId())) {
            throw new IllegalStateException("Pessoa #" + pessoa.getId() + " está bloqueada na Bandeja de Contexto da IA.");
        }
        return executarComFallbackReacao(() -> {
            EntityManager em = JPAUtil.getEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                Pessoa gerenciada = pessoa;
                if (gerenciada.getId() == null) {
                    em.persist(gerenciada);
                } else {
                    gerenciada = em.merge(gerenciada);
                }
                if (gerenciada.getRegistros() != null) {
                    gerenciada.getRegistros().size();
                }
                tx.commit();
                return gerenciada;
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            } finally {
                em.close();
            }
        }, e -> {
            // Fallback reativo: se houve colisão de CPF/CNPJ único, recupera o cadastro existente
            if (pessoa instanceof PessoaFisica pf && pf.getCpf() != null && !pf.getCpf().isBlank()) {
                PessoaFisica existente = buscarPorCpf(pf.getCpf());
                if (existente != null) return existente;
            } else if (pessoa instanceof PessoaJuridica pj && pj.getCnpj() != null && !pj.getCnpj().isBlank()) {
                PessoaJuridica existente = buscarPorCnpj(pj.getCnpj());
                if (existente != null) return existente;
            }
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        });
    }

    @Override
    public Pessoa atualizar(Pessoa pessoa) {
        return salvar(pessoa);
    }

    @Override
    public Pessoa buscarPorId(Long id) {
        if (id == null) return null;
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

    /**
     * Busca uma única pessoa por identificador, CPF/CNPJ, Registro ou Nome (com fallback reativo em cascata).
     */
    public Pessoa buscarUnicaPessoa(Long id, String termoOuNome) {
        if (id != null) {
            Pessoa porId = buscarPorId(id);
            if (porId != null) return porId;
        }
        if (termoOuNome == null || termoOuNome.isBlank()) {
            return null;
        }
        String t = termoOuNome.trim();
        return buscarUnicoComFallback(
                () -> {
                    PessoaFisica pf = buscarPorCpf(t);
                    if (pf != null) return pf;
                    PessoaJuridica pj = buscarPorCnpj(t);
                    if (pj != null) return pj;
                    Pessoa porReg = buscarPorRegistroUnico(t);
                    if (porReg != null) return porReg;
                    try (EntityManager em = JPAUtil.getEntityManager()) {
                        List<Pessoa> exatas = em.createQuery(
                                        "SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE LOWER(TRIM(p.nome)) = LOWER(:nome) ORDER BY p.id",
                                        Pessoa.class)
                                .setParameter("nome", t)
                                .setMaxResults(1)
                                .getResultList();
                        return exatas.isEmpty() ? null : exatas.get(0);
                    }
                },
                () -> {
                    List<Pessoa> lista = buscarPorTermo(t, null);
                    return lista.isEmpty() ? null : lista.get(0);
                }
        );
    }

    /**
     * Busca pessoa pelo número único de Registro auxiliar (RG, IE, IM, CNH) ou CPF/CNPJ.
     */
    public Pessoa buscarPorRegistroUnico(String valorRegistro) {
        return buscarPorRegistroUnico(null, valorRegistro);
    }

    public Pessoa buscarPorRegistroUnico(String tipoOpcional, String valorRegistro) {
        if (valorRegistro == null || valorRegistro.isBlank()) return null;
        String val = valorRegistro.trim();
        return buscarUnicoComFallback(
                () -> {
                    try (EntityManager em = JPAUtil.getEntityManager()) {
                        String jpql = "SELECT r FROM Registro r JOIN FETCH r.pessoa p LEFT JOIN FETCH p.registros WHERE LOWER(TRIM(r.registro)) = LOWER(:reg)";
                        if (tipoOpcional != null && !tipoOpcional.isBlank()) {
                            jpql += " AND LOWER(TRIM(r.tipo)) = LOWER(:tipo)";
                        }
                        var query = em.createQuery(jpql, Registro.class)
                                .setParameter("reg", val)
                                .setMaxResults(1);
                        if (tipoOpcional != null && !tipoOpcional.isBlank()) {
                            query.setParameter("tipo", tipoOpcional.trim());
                        }
                        List<Registro> regs = query.getResultList();
                        if (!regs.isEmpty()) {
                            return regs.get(0).getPessoa();
                        }
                        return null;
                    }
                },
                () -> {
                    PessoaFisica pf = buscarPorCpf(val);
                    if (pf != null) return pf;
                    return buscarPorCnpj(val);
                }
        );
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

    @Override
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
     * Busca clientes ou fornecedores por termo: parte do nome (usando idx_pessoa_nome),
     * palavras parciais ("rob alm"), iniciais ("r a s"), CPF, CNPJ ou Registro.
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
            List<Pessoa> diretos = List.of();
            if (!ids.isEmpty()) {
                List<Long> longIds = ids.stream().map(Number::longValue).toList();
                diretos = em.createQuery(
                                "SELECT DISTINCT p FROM Pessoa p LEFT JOIN FETCH p.registros WHERE p.id IN (:ids) ORDER BY p.nome",
                                Pessoa.class)
                        .setParameter("ids", longIds)
                        .getResultList();
            }

            List<Pessoa> basePapel;
            if ("CLIENTE".equalsIgnoreCase(papel)) {
                basePapel = listarClientes();
            } else if ("FORNECEDOR".equalsIgnoreCase(papel)) {
                basePapel = listarFornecedores();
            } else {
                basePapel = listarTodos();
            }
            List<Pessoa> parciais = BuscaPalavrasParciaisUtil.filtrarEOrdenar(basePapel, Pessoa::getNome, t);
            if (!parciais.isEmpty()) {
                if (!diretos.isEmpty()) {
                    java.util.LinkedHashSet<Pessoa> uniao = new java.util.LinkedHashSet<>(parciais);
                    uniao.addAll(diretos);
                    return new java.util.ArrayList<>(uniao);
                }
                return parciais;
            }
            return diretos;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Busca um Cliente ou Fornecedor específico por ID, Documento/Registro único ou por palavras parciais/iniciais.
     * Se encontrar exatamente 1 pessoa específica (ou match único), retorna a própria {@link Pessoa}.
     * Caso existam múltiplas pessoas compatíveis (homônimos ou múltiplos matches de palavras parciais/iniciais),
     * retorna a {@link List} de pessoas para o usuário escolher.
     */
    public Object buscarEspecificaOuListarParaEscolha(Long idOuNull, String termoOuNome, String papelOpcional) {
        if (idOuNull != null) {
            return buscarPorId(idOuNull);
        }
        if (termoOuNome == null || termoOuNome.isBlank()) {
            if ("CLIENTE".equalsIgnoreCase(papelOpcional)) return listarClientes();
            if ("FORNECEDOR".equalsIgnoreCase(papelOpcional)) return listarFornecedores();
            return listarTodos();
        }
        String t = termoOuNome.trim();
        Pessoa porReg = buscarPorRegistroUnico(t);
        if (porReg != null) {
            return porReg;
        }
        List<Pessoa> candidatos = buscarPorTermo(t, papelOpcional);
        if (candidatos.isEmpty()) {
            return null;
        }
        if (candidatos.size() == 1) {
            return candidatos.get(0);
        }
        List<Pessoa> exatos = candidatos.stream()
                .filter(p -> BuscaPalavrasParciaisUtil.isMatchExato(p.getNome(), t))
                .toList();
        if (exatos.size() == 1) {
            return exatos.get(0);
        }
        return candidatos;
    }

    @Override
    public boolean deletar(Long id) {
        if (id != null && isBloqueadoParaBandejaIA(id)) {
            throw new IllegalStateException("Pessoa #" + id + " está bloqueada na Bandeja de Contexto da IA.");
        }
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Pessoa p = em.find(Pessoa.class, id);
            if (p != null) {
                em.remove(p);
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
