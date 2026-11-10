package br.com.senac.application;

import br.com.senac.repository.dao.JPAUtil;
import jakarta.persistence.EntityManager;

/** Utilitário de diagnóstico: executa um SELECT/SHOW e imprime as linhas. Uso: ConsultaSql "SELECT ..." */
public class ConsultaSql {
    public static void main(String[] args) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                try (var st = conn.createStatement(); var rs = st.executeQuery(args[0])) {
                    var m = rs.getMetaData();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i <= m.getColumnCount(); i++) sb.append(m.getColumnLabel(i)).append('=').append(rs.getObject(i)).append(" | ");
                        System.out.println("ROW " + sb);
                    }
                }
            });
        }
        JPAUtil.close();
    }
}
