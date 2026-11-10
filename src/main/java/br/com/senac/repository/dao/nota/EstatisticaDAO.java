package br.com.senac.repository.dao.nota;

import br.com.senac.repository.dao.JPAUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acesso às Views/Stored Procedures estatísticas. Todo o cálculo (médias, desvio-padrão, scores, janelas de compra)
 * roda no MySQL; o Java apenas converte o ResultSet em linhas (Map coluna -> valor).
 * Cada chamada usa seu próprio EntityManager (seguro para Virtual Threads).
 */
public class EstatisticaDAO {

    public List<Map<String, Object>> demandaMensalCliente(Long idCliente, Long idProduto, Integer mesAlvo) {
        return chamar("sp_prever_demanda_mensal_cliente",
                new Object[]{idCliente, idProduto, mesAlvo != null ? mesAlvo : 0},
                new Class<?>[]{Long.class, Long.class, Integer.class},
                new String[]{"p_id_cliente", "p_id_produto", "p_mes_alvo"});
    }

    public List<Map<String, Object>> perfilConsumoCliente(Long idCliente) {
        return chamar("sp_analisar_perfil_consumo_cliente",
                new Object[]{idCliente}, new Class<?>[]{Long.class}, new String[]{"p_id_cliente"});
    }

    public List<Map<String, Object>> impactoExternoProdutos(Long idProduto) {
        return chamar("sp_analisar_impacto_externo_rios_clima_produtos",
                new Object[]{idProduto}, new Class<?>[]{Long.class}, new String[]{"p_id_produto"});
    }

    public List<Map<String, Object>> rankingFornecedores(Long idProduto, String clima, String regimeRio) {
        return chamar("sp_ranking_fornecedores_custo_qualidade",
                new Object[]{idProduto, clima != null ? clima : "TODOS", regimeRio != null ? regimeRio : "TODOS"},
                new Class<?>[]{Long.class, String.class, String.class},
                new String[]{"p_id_produto", "p_condicao_clima", "p_regime_rio"});
    }

    public List<Map<String, Object>> motivosPerdaPrematura(Long idProduto, Long idFornecedor) {
        return chamar("sp_diagnosticar_motivos_perda_prematura",
                new Object[]{idProduto, idFornecedor}, new Class<?>[]{Long.class, Long.class},
                new String[]{"p_id_produto", "p_id_fornecedor"});
    }

    public List<Map<String, Object>> destaquesSazonais(int limiteTop) {
        return chamar("sp_analisar_destaques_e_picos_sazonais",
                new Object[]{limiteTop}, new Class<?>[]{Integer.class}, new String[]{"p_limite_top"});
    }

    public List<Map<String, Object>> planoEstrategico(LocalDate dataReferencia, int horizonteDias) {
        return chamar("sp_plano_estrategico_compras_vendas_inteligente",
                new Object[]{java.sql.Date.valueOf(dataReferencia != null ? dataReferencia : LocalDate.now()), horizonteDias},
                new Class<?>[]{java.sql.Date.class, Integer.class},
                new String[]{"p_data_referencia", "p_horizonte_dias"});
    }

    public List<Map<String, Object>> picosDiasEventos() {
        return consultarView("SELECT * FROM vw_picos_dias_eventos_quinto_dia_util");
    }

    /** Causas externas por demanda: devolve os 4 resultados da procedure (fatores, eventos, notas e produtos relacionados). */
    public Map<String, List<Map<String, Object>>> causasExternasPorDemanda(Long idNota, Long idProduto) {
        String[] nomes = {"fatores_ativos_e_efeito_estatistico", "eventos_reais_do_snapshot", "notas_relacionadas", "produtos_relacionados"};
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                Map<String, List<Map<String, Object>>> saida = new LinkedHashMap<>();
                try (var cs = conn.prepareCall("{call sp_causas_externas_por_demanda(?,?)}")) {
                    cs.setObject(1, idNota);
                    cs.setObject(2, idProduto);
                    boolean tem = cs.execute();
                    int i = 0;
                    while (tem || cs.getUpdateCount() != -1) {
                        if (tem) {
                            try (var rs = cs.getResultSet()) {
                                saida.put(i < nomes.length ? nomes[i] : "resultado_" + i, lerResultSet(rs));
                            }
                            i++;
                        }
                        tem = cs.getMoreResults();
                    }
                }
                return saida;
            });
        }
    }

    public int recalcularEstatisticasFatores() {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                try (var cs = conn.prepareCall("{call sp_recalcular_estatisticas_fatores()}")) {
                    cs.execute();
                }
            });
        }
        return consultarView("SELECT COUNT(*) AS n FROM fator_externo_estatistica").stream()
                .findFirst().map(l -> ((Number) l.get("n")).intValue()).orElse(0);
    }

    public List<Map<String, Object>> estatisticaFatoresProduto(Long idProduto) {
        return consultarView("SELECT e.*, p.nome AS nome_produto FROM fator_externo_estatistica e JOIN produtos p ON p.id = e.id_produto" +
                (idProduto != null ? " WHERE e.id_produto = " + idProduto : "") + " ORDER BY ABS(COALESCE(e.z_welch,0)) DESC LIMIT 200");
    }

    public List<Map<String, Object>> eventosPersistidos(int limite) {
        return consultarView("SELECT ev.*, (SELECT COUNT(*) FROM vinculo_evento ve WHERE ve.id_evento = ev.id) AS snapshots_vinculados " +
                "FROM evento_externo ev ORDER BY ev.coletado_em DESC, ev.id DESC LIMIT " + Math.max(1, limite));
    }

    public List<Map<String, Object>> perfilEstatisticoView(Long idCliente) {
        return consultarView("SELECT * FROM vw_perfil_estatistico_cliente" + (idCliente != null ? " WHERE id_cliente = " + idCliente : ""));
    }

    public List<Map<String, Object>> scoreFornecedoresView(Long idProduto) {
        return consultarView("SELECT * FROM vw_score_fornecedor_produto" + (idProduto != null ? " WHERE id_produto = " + idProduto : ""));
    }

    // ---------------------------------------------------------------------------------------------------------

    private List<Map<String, Object>> consultarView(String sql) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return converter(em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
                    return lerResultSet(rs);
                }
            }));
        }
    }

    private List<Map<String, Object>> chamar(String procedure, Object[] valores, Class<?>[] tipos, String[] nomes) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                StringBuilder sql = new StringBuilder("{call ").append(procedure).append("(");
                for (int i = 0; i < nomes.length; i++) sql.append(i == 0 ? "?" : ",?");
                sql.append(")}");
                try (var cs = conn.prepareCall(sql.toString())) {
                    for (int i = 0; i < valores.length; i++) {
                        if (valores[i] == null) cs.setObject(i + 1, null);
                        else cs.setObject(i + 1, valores[i]);
                    }
                    boolean temResultado = cs.execute();
                    return temResultado ? lerResultSet(cs.getResultSet()) : new ArrayList<Map<String, Object>>();
                }
            });
        }
    }

    private List<Map<String, Object>> lerResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<Map<String, Object>> linhas = new ArrayList<>();
        var meta = rs.getMetaData();
        while (rs.next()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                linha.put(meta.getColumnLabel(c), rs.getObject(c));
            }
            linhas.add(linha);
        }
        return linhas;
    }

    private List<Map<String, Object>> converter(List<Map<String, Object>> l) {
        return l;
    }
}
