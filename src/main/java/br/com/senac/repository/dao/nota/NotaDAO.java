package br.com.senac.repository.dao.nota;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class NotaDAO {

    /**
     * Salva a Nota e atualiza em uma única transação atômica (ACID) os saldos dos lotes de origem consumidos
     * (FIFO ou Lote Específico), aplicando bloqueio pessimista (FOR UPDATE) para máxima confiabilidade concorrente.
     */
    public <N extends Nota> N salvarComSincronizacaoFIFO(N nota, List<Item<? extends Produto>> lotesOrigemAlterados) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (nota.getPessoa() != null && nota.getPessoa().getId() != null) {
                nota.setPessoa(em.find(br.com.senac.repository.modal.pessoa.Pessoa.class, nota.getPessoa().getId()));
            }
            if (nota.getVinculoFatorExterno() != null && nota.getVinculoFatorExterno().getId() != null) {
                nota.setVinculoFatorExterno(em.merge(nota.getVinculoFatorExterno()));
            }
            if (nota instanceof br.com.senac.repository.modal.nota.NotaDevolucao dev
                    && dev.getNotaPerdaGerada() != null) {
                if (dev.getNotaPerdaGerada().getPessoa() != null && dev.getNotaPerdaGerada().getPessoa().getId() != null) {
                    dev.getNotaPerdaGerada().setPessoa(
                            em.find(br.com.senac.repository.modal.pessoa.Pessoa.class, dev.getNotaPerdaGerada().getPessoa().getId()));
                }
                if (dev.getNotaPerdaGerada().getVinculoFatorExterno() != null) {
                    dev.getNotaPerdaGerada().setVinculoFatorExterno(nota.getVinculoFatorExterno());
                }
            }
            if (lotesOrigemAlterados != null) {
                for (Item<? extends Produto> lote : lotesOrigemAlterados) {
                    if (lote.getId() != null) {
                        // Garante bloqueio pessimista na linha do lote antes do merge para evitar condição de corrida
                        em.find(Item.class, lote.getId(), LockModeType.PESSIMISTIC_WRITE);
                        em.merge(lote);
                    }
                }
            }
            if (nota.getId() == null) {
                em.persist(nota);
            } else {
                nota = em.merge(nota);
            }
            inicializarGrafoNotaParaEndpoint(nota);
            tx.commit();
            return nota;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Busca um lote específico pelo ID validando se pertence a uma NotaCompra e se possui saldo disponível.
     */
    @SuppressWarnings("unchecked")
    public Item<? extends Produto> buscarLotePorId(Long idLote) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return (Item<? extends Produto>) em.createQuery(
                            "SELECT i FROM Item i JOIN FETCH i.produto p JOIN FETCH i.nota n LEFT JOIN FETCH n.pessoa " +
                            "WHERE i.id = :idLote AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra",
                            Item.class)
                    .setParameter("idLote", idLote)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Busca os lotes de entrada (NotaCompra) com saldo disponível > 0 e dentro da validade na ordem FIFO
     * (data de entrada mais antiga e validade mais próxima primeiro), usando o índice idx_item_fifo.
     */
    @SuppressWarnings("unchecked")
    public List<Item<? extends Produto>> buscarLotesDisponiveisFIFO(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return (List<Item<? extends Produto>>) (List<?>) em.createQuery(
                            "SELECT i FROM Item i JOIN FETCH i.produto p JOIN FETCH i.nota n LEFT JOIN FETCH n.pessoa " +
                            "WHERE p.id = :idProduto AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                            "AND i.quantidadeDisponivel > 0 " +
                            "AND (i.dataValidade IS NULL OR i.dataValidade >= :hoje) " +
                            "ORDER BY i.data ASC, i.dataValidade ASC, i.id ASC",
                            Item.class)
                    .setParameter("idProduto", idProduto)
                    .setParameter("hoje", LocalDate.now())
                    .getResultList();
        }
    }

    /**
     * Executa a Stored Procedure sp_consumir_estoque_fifo diretamente no banco MySQL
     * (com FOR UPDATE nativo no banco para operações de altíssimo volume).
     */
    public void executarProcedureBaixaFIFO(Long idNotaVenda, Long idProduto, BigDecimal quantidadeTotal, BigDecimal precoVenda) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            StoredProcedureQuery sp = em.createStoredProcedureQuery("sp_consumir_estoque_fifo")
                    .registerStoredProcedureParameter("p_id_nota_venda", Long.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_id_produto", Long.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_quantidade_total", BigDecimal.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_preco_venda", BigDecimal.class, ParameterMode.IN)
                    .setParameter("p_id_nota_venda", idNotaVenda)
                    .setParameter("p_id_produto", idProduto)
                    .setParameter("p_quantidade_total", quantidadeTotal)
                    .setParameter("p_preco_venda", precoVenda != null ? precoVenda : BigDecimal.ZERO);
            sp.execute();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Executa a Stored Procedure sp_consumir_lote_especifico diretamente no banco MySQL.
     */
    public void executarProcedureBaixaLoteEspecifico(Long idNotaVenda, Long idLoteDesejado, BigDecimal quantidade, BigDecimal precoVenda) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            StoredProcedureQuery sp = em.createStoredProcedureQuery("sp_consumir_lote_especifico")
                    .registerStoredProcedureParameter("p_id_nota_venda", Long.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_id_lote_desejado", Long.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_quantidade", BigDecimal.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("p_preco_venda", BigDecimal.class, ParameterMode.IN)
                    .setParameter("p_id_nota_venda", idNotaVenda)
                    .setParameter("p_id_lote_desejado", idLoteDesejado)
                    .setParameter("p_quantidade", quantidade)
                    .setParameter("p_preco_venda", precoVenda != null ? precoVenda : BigDecimal.ZERO);
            sp.execute();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Consulta o saldo total em estoque utilizando a View otimizada vw_saldo_consolidado_estoque.
     */
    public BigDecimal consultarSaldoTotalProduto(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            Object resultado = em.createNativeQuery(
                            "SELECT saldo_total_disponivel FROM vw_saldo_consolidado_estoque WHERE id_produto = :idProduto")
                    .setParameter("idProduto", idProduto)
                    .getSingleResult();
            if (resultado instanceof BigDecimal bd) {
                return bd;
            } else if (resultado instanceof Number num) {
                return BigDecimal.valueOf(num.doubleValue());
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Consulta a View vw_lotes_fifo_disponiveis para exibir a fila de lotes disponíveis ao operador.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> consultarViewFilaLotesFIFO(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createNativeQuery(
                            "SELECT id_lote, nome_produto, data_entrada_lote, data_validade, quantidade_disponivel, custo_unitario_lote " +
                            "FROM vw_lotes_fifo_disponiveis WHERE id_produto = :idProduto ORDER BY data_entrada_lote ASC, id_lote ASC")
                    .setParameter("idProduto", idProduto)
                    .getResultList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Item<? extends Produto>> listarItensProximosValidade(LocalDate dataLimite) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return (List<Item<? extends Produto>>) (List<?>) em.createQuery(
                            "SELECT i FROM Item i JOIN FETCH i.produto WHERE i.quantidadeDisponivel > 0 " +
                            "AND i.dataValidade IS NOT NULL AND i.dataValidade <= :limite ORDER BY i.dataValidade ASC",
                            Item.class)
                    .setParameter("limite", dataLimite)
                    .getResultList();
        }
    }

    /**
     * Busca as Notas de Perda pós-venda (geradas a partir de NotaDevolucao) relacionadas ao produto do fornecedor
     * que ocorreram dentro do período de validade e estão elegíveis para abatimento/reposição.
     */
    public List<NotaPerda> buscarPerdasPosVendaValidadePorFornecedorEProduto(Long idFornecedor, Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            String jpql = "SELECT DISTINCT np FROM NotaPerda np " +
                          "JOIN FETCH np.itens ip " +
                          "JOIN FETCH ip.produto p " +
                          "LEFT JOIN FETCH np.notaCompraOrigem nc " +
                          "WHERE np.notaDevolucao IS NOT NULL " +
                          "AND np.notaCompraAbatimento IS NULL " +
                          "AND (np.pessoa.id = :idForn OR (nc IS NOT NULL AND nc.pessoa.id = :idForn)) " +
                          "AND (:idProd IS NULL OR p.id = :idProd) " +
                          "ORDER BY np.dataGerada ASC";

            List<NotaPerda> candidatas = em.createQuery(jpql, NotaPerda.class)
                    .setParameter("idForn", idFornecedor)
                    .setParameter("idProd", idProduto)
                    .getResultList();

            return candidatas.stream()
                    .filter(NotaPerda::isPerdaDentroDaValidade)
                    .toList();
        }
    }

    /**
     * Consulta a View otimizada vw_perdas_por_fornecedor filtrando por fornecedor, produto (opcional)
     * e se deve retornar apenas perdas pós-venda dentro do período da validade.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> consultarViewPerdasPorFornecedor(
            Long idFornecedor,
            Long idProduto,
            boolean apenasPosVendaDentroValidade) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            StringBuilder sql = new StringBuilder(
                    "SELECT id_nota_perda, data_perda, nome_fornecedor, id_produto, nome_produto, " +
                    "quantidade_perdida, valor_custo_unitario, valor_total_custo_perda, data_validade, " +
                    "is_perda_pos_venda, dentro_periodo_validade, elegivel_abatimento_pos_venda " +
                    "FROM vw_perdas_por_fornecedor WHERE id_fornecedor = :idForn ");

            if (idProduto != null) {
                sql.append("AND id_produto = :idProd ");
            }
            if (apenasPosVendaDentroValidade) {
                sql.append("AND is_perda_pos_venda = 1 AND dentro_periodo_validade = 1 AND elegivel_abatimento_pos_venda = 1 ");
            }
            sql.append("ORDER BY data_perda DESC");

            var query = em.createNativeQuery(sql.toString()).setParameter("idForn", idFornecedor);
            if (idProduto != null) {
                query.setParameter("idProd", idProduto);
            }
            return query.getResultList();
        }
    }

    /**
     * Consulta a View contábil vw_apuracao_lucro_contabil para apuração de CMV, Receita Bruta,
     * Descontos, Receita Líquida, Lucro Bruto, Lucro Líquido e Margem % por venda.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> consultarViewApuracaoLucroContabil(Long idNotaVenda) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            String sql = "SELECT id_nota_venda, nome_produto, quantidade_vendida, custo_unitario_lote, " +
                         "preco_venda_unitario, cmv_custo_total_mercadoria, receita_liquida_item, " +
                         "lucro_bruto_item, lucro_liquido_item, margem_lucro_percentual " +
                         "FROM vw_apuracao_lucro_contabil " +
                         (idNotaVenda != null ? "WHERE id_nota_venda = :idNota " : "") +
                         "ORDER BY id_nota_venda DESC, id_item_venda ASC";
            var query = em.createNativeQuery(sql);
            if (idNotaVenda != null) {
                query.setParameter("idNota", idNotaVenda);
            }
            return query.getResultList();
        }
    }

    /**
     * Consulta a View vw_analise_sazonal_vendas consolidando clima, nível dos rios, eventos e faturamento.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> consultarViewAnaliseSazonalVendas() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.createNativeQuery(
                            "SELECT chave_snapshot, data_referencia, bloco_horario_6h, cidade, uf, " +
                            "dia_util, quinto_dia_util, temp_media, precipitacao_mm, vazao_m3s, " +
                            "total_eventos_ativos, total_notas_venda, faturamento_liquido_itens " +
                            "FROM vw_analise_sazonal_vendas ORDER BY data_referencia DESC, bloco_horario_6h DESC")
                    .getResultList();
        }
    }

    public Nota buscarNotaPorId(Long id) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            Nota nota = em.createQuery(
                            "SELECT DISTINCT n FROM Nota n LEFT JOIN FETCH n.itens LEFT JOIN FETCH n.pessoa WHERE n.id = :id",
                            Nota.class)
                    .setParameter("id", id)
                    .getSingleResult();
            inicializarGrafoNotaParaEndpoint(nota);
            return nota;
        } catch (Exception e) {
            return null;
        }
    }

    public <T extends Nota> List<T> listarNotasPorTipo(Class<T> classeTipoNota) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<T> lista = em.createQuery(
                            "SELECT DISTINCT n FROM " + classeTipoNota.getSimpleName() + " n " +
                            "LEFT JOIN FETCH n.itens LEFT JOIN FETCH n.pessoa ORDER BY n.dataGerada DESC",
                            classeTipoNota)
                    .getResultList();
            lista.forEach(this::inicializarGrafoNotaParaEndpoint);
            return lista;
        }
    }

    /**
     * Retorna todas as notas emitidas vinculadas a uma determinada pessoa (cliente ou fornecedor),
     * com itens, produtos e pagamentos inicializados.
     */
    public List<Nota> listarNotasPorPessoa(Long idPessoa) {
        if (idPessoa == null) {
            return List.of();
        }
        try (EntityManager em = JPAUtil.getEntityManager()) {
            List<Nota> lista = em.createQuery(
                            "SELECT DISTINCT n FROM Nota n " +
                            "LEFT JOIN FETCH n.itens " +
                            "LEFT JOIN FETCH n.pessoa " +
                            "WHERE n.pessoa.id = :idPessoa ORDER BY n.dataGerada DESC",
                            Nota.class)
                    .setParameter("idPessoa", idPessoa)
                    .getResultList();
            lista.forEach(this::inicializarGrafoNotaParaEndpoint);
            return lista;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Inicializa preventivamente as associações e coleções LAZY da Nota dentro da sessão JPA ativa,
     * evitando LazyInitializationException quando consumida por telas JavaFX, CLI Inline ou IA Gemini.
     */
    private void inicializarGrafoNotaParaEndpoint(Nota n) {
        if (n == null) return;
        if (n.getPessoa() != null && n.getPessoa().getRegistros() != null) {
            n.getPessoa().getRegistros().size();
        }
        if (n.getVinculoFatorExterno() != null) {
            n.getVinculoFatorExterno().getChaveSnapshot();
        }
        if (n.getItens() != null) {
            for (Item<? extends Produto> item : n.getItens()) {
                if (item.getProduto() != null) {
                    item.getProduto().getNome();
                }
                if (item.getItemOrigem() != null) {
                    item.getItemOrigem().getValor();
                    if (item.getItemOrigem().getItemOrigem() != null) {
                        item.getItemOrigem().getItemOrigem().getValor();
                    }
                }
            }
        }
        if (n.getPagamentos() != null) {
            for (br.com.senac.repository.modal.pagamento.NotaPagamento p : n.getPagamentos()) {
                if (p.getFormaPagamento() != null) {
                    p.getFormaPagamento().getCodigo();
                }
                if (p instanceof br.com.senac.repository.modal.pagamento.PagamentoParcelado pp
                        && pp.getParcelas() != null) {
                    pp.getParcelas().size();
                }
            }
        }
        if (n instanceof br.com.senac.repository.modal.nota.NotaVenda nv && nv.getDevolucoes() != null) {
            nv.getDevolucoes().size();
        }
        if (n instanceof br.com.senac.repository.modal.nota.NotaCompra nc && nc.getNotasPerdaAbatidas() != null) {
            nc.getNotasPerdaAbatidas().size();
        }
        if (n instanceof br.com.senac.repository.modal.nota.NotaDevolucao nd && nd.getNotaPerdaGerada() != null) {
            inicializarGrafoNotaParaEndpoint(nd.getNotaPerdaGerada());
        }
    }
}
