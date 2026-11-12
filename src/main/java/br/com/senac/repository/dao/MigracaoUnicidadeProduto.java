package br.com.senac.repository.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Migração da unicidade de {@code produtos}: de "nome único" para "nome + unidade + conjunto único".
 *
 * <p>O {@code hbm2ddl=update} cria a constraint nova, mas nunca remove as antigas. Sem esta migração,
 * bancos já existentes continuariam barrando o mesmo nome em unidades diferentes. A rotina descobre
 * qualquer índice/constraint única que cubra <b>somente</b> a coluna {@code nome} e a remove.
 * É idempotente: se não houver nada para remover, não faz nada. Nunca impede a aplicação de iniciar.
 */
final class MigracaoUnicidadeProduto {

    private static final String TABELA = "produtos";

    private MigracaoUnicidadeProduto() {
    }

    static void executar(EntityManagerFactory emf) {
        try (EntityManager em = emf.createEntityManager()) {
            em.unwrap(Session.class).doWork(MigracaoUnicidadeProduto::migrar);
        } catch (Exception e) {
            System.out.println("[JPAUtil] Aviso: não foi possível revisar a unicidade de produtos: " + e.getMessage());
        }
    }

    private static void migrar(Connection conn) throws SQLException {
        boolean h2 = conn.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
        List<String> remover = h2 ? restricoesH2SoNome(conn) : indicesMySqlSoNome(conn);
        for (String nome : remover) {
            String sql = h2
                    ? "ALTER TABLE " + TABELA + " DROP CONSTRAINT \"" + nome + "\""
                    : "ALTER TABLE " + TABELA + " DROP INDEX `" + nome + "`";
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
                System.out.println("[JPAUtil] Unicidade antiga de produtos removida: " + nome
                        + " (agora vale nome + unidade + conjunto).");
            } catch (Exception e) {
                System.out.println("[JPAUtil] Aviso: não consegui remover " + nome + ": " + e.getMessage());
            }
        }
    }

    /** Índices únicos do MySQL cujo único campo é {@code nome}. */
    private static List<String> indicesMySqlSoNome(Connection conn) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        Map<String, List<String>> colunasPorIndice = new LinkedHashMap<>();
        for (String tabela : new String[]{TABELA, TABELA.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = md.getIndexInfo(conn.getCatalog(), conn.getSchema(), tabela, true, false)) {
                while (rs.next()) {
                    String indice = rs.getString("INDEX_NAME");
                    String coluna = rs.getString("COLUMN_NAME");
                    if (indice != null && coluna != null) {
                        colunasPorIndice.computeIfAbsent(indice, k -> new ArrayList<>()).add(coluna.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (!colunasPorIndice.isEmpty()) break;
        }
        return soNome(colunasPorIndice);
    }

    /** Constraints UNIQUE do H2 cujo único campo é {@code nome}. */
    private static List<String> restricoesH2SoNome(Connection conn) throws SQLException {
        String sql = "SELECT tc.constraint_name, kcu.column_name "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "  ON kcu.constraint_name = tc.constraint_name AND kcu.table_name = tc.table_name "
                + "WHERE LOWER(tc.table_name) = '" + TABELA + "' AND tc.constraint_type = 'UNIQUE' "
                + "ORDER BY tc.constraint_name, kcu.ordinal_position";
        Map<String, List<String>> colunasPorRestricao = new TreeMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                colunasPorRestricao.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(rs.getString(2).toLowerCase(Locale.ROOT));
            }
        }
        return soNome(colunasPorRestricao);
    }

    private static List<String> soNome(Map<String, List<String>> colunasPorNome) {
        List<String> resultado = new ArrayList<>();
        colunasPorNome.forEach((nome, colunas) -> {
            if (colunas.size() == 1 && "nome".equals(colunas.get(0))) {
                resultado.add(nome);
            }
        });
        return resultado;
    }
}
