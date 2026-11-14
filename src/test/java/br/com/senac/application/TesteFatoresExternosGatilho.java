package br.com.senac.application;

import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.JPAUtil;
import jakarta.persistence.EntityManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Valida: gatilhos (atribuição sem requisição + fila), eventos persistidos com cache no banco,
 * estatística de fatores (lift/Welch) e causas externas por demanda. Dados de teste têm chave 'TESTE|' e são removidos.
 */
public class TesteFatoresExternosGatilho {

    static int ok = 0, falhas = 0;

    static void check(String nome, boolean cond, String detalhe) {
        if (cond) ok++; else falhas++;
        System.out.printf("[%s] %s %s%n", cond ? "OK " : "ERRO", nome, detalhe);
    }

    static long scalar(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    static void exec(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement()) { st.executeUpdate(sql); }
    }

    public static void main(String[] args) throws Exception {
        RoteadorController r = new RoteadorController();
        try (EntityManager em = JPAUtil.getEntityManager()) {
            em.unwrap(org.hibernate.Session.class).doWork(c -> {
                try { rodar(c, r); } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        System.out.println("RESULTADO: " + ok + " ok / " + falhas + " falhas");
        JPAUtil.close();
    }

    static void rodar(Connection c, RoteadorController r) throws Exception {
        c.setAutoCommit(true);
        limpar(c);

        check("tabelas e gatilhos instalados",
                scalar(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='sazonalisei' AND table_name IN ('evento_externo','vinculo_evento','fila_fator_externo_pendente','fator_externo_estatistica')") == 4
                        && scalar(c, "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='sazonalisei' AND trigger_name LIKE 'trg_nota_%'") == 2, "");

        long idProd = scalar(c, "SELECT MIN(id) FROM produtos WHERE is_digital = 0");
        LocalDate hoje = LocalDate.now();

        // 6 snapshots sintéticos no bloco 2 (12h-18h): 3 com calor+evento e 3 amenos
        double[] qtds = {10, 12, 11, 4, 5, 4.5};
        for (int i = 0; i < 6; i++) {
            LocalDate d = hoje.minusDays(40 + i);
            boolean calor = i < 3;
            exec(c, String.format(java.util.Locale.US,
                    "INSERT INTO vinculo_fator_externo_nota (chave_snapshot,data_referencia,bloco_horario_6h,cidade,uf,dia_util,quinto_dia_util,temp_media,precipitacao_mm,vazao_m3s,vazao_media_m3s,total_eventos_ativos,resumo_eventos_json) " +
                            "VALUES ('TESTE|%s|B2','%s',2,'Manaus','AM',b'1',b'0',%s,0,%s,10,%d,'[]')",
                    d, d, calor ? "32.5" : "27.0", calor ? "7.0" : "10.0", calor ? 2 : 0));
            // nota SEM vinculo: o gatilho BEFORE INSERT precisa atribuir o snapshot existente
            exec(c, "INSERT INTO nota (tipo_nota, data_gerada, descricao) VALUES ('VENDA','" + d + " 13:30:00','TESTE gatilho')");
            long idNota = scalar(c, "SELECT MAX(id) FROM nota WHERE descricao='TESTE gatilho'");
            exec(c, String.format(java.util.Locale.US,
                    "INSERT INTO item (id_nota,id_produto,valor,desconto,quantidade,quantidade_disponivel,data) VALUES (%d,%d,10,0,%s,0,'%s 13:30:00')", idNota, idProd, qtds[i], d));
        }
        exec(c, "INSERT INTO nota (tipo_nota, data_gerada, descricao) VALUES ('VENDA','" + hoje.minusDays(40) + " 15:00:00','TESTE gatilho')");
        check("gatilho BEFORE INSERT atribuiu snapshot às 6 notas sem requisição externa",
                scalar(c, "SELECT COUNT(*) FROM nota n JOIN vinculo_fator_externo_nota v ON v.id=n.id_vinculo_fator_externo WHERE n.descricao='TESTE gatilho' AND v.chave_snapshot LIKE 'TESTE|%'") == 7, "(6 + 1 extra na mesma janela)");

        // nota em janela sem snapshot -> fila
        LocalDate semSnap = hoje.minusDays(200);
        exec(c, "INSERT INTO nota (tipo_nota, data_gerada, descricao) VALUES ('VENDA','" + semSnap + " 07:10:00','TESTE fila')");
        check("nota sem snapshot foi para a fila (1 janela, sem requisição por nota)",
                scalar(c, "SELECT COUNT(*) FROM fila_fator_externo_pendente f JOIN nota n ON n.id=f.id_nota WHERE n.descricao='TESTE fila'") == 1, "");
        exec(c, "DELETE FROM nota WHERE descricao='TESTE fila'");

        int linhas = r.estatisticas().recalcularEstatisticasFatores();
        check("recalculo estatístico no banco gerou linhas", linhas > 0, "linhas=" + linhas);
        List<Map<String, Object>> fat = r.estatisticas().estatisticaFatoresProduto(idProd);
        Map<String, Object> calor = fat.stream().filter(m -> "CALOR".equals(m.get("tipo_fator"))).findFirst().orElse(null);
        check("lift de CALOR positivo e significativo (média 11 x demais)",
                calor != null && ((Number) calor.get("lift_pct")).doubleValue() > 50 && !"INSUFICIENTE".equals(calor.get("significancia")),
                calor != null ? "lift=" + calor.get("lift_pct") + "% z=" + calor.get("z_welch") + " sig=" + calor.get("significancia") + " n=" + calor.get("n_com") + "/" + calor.get("n_sem") : "sem linha");
        Map<String, Object> evt = fat.stream().filter(m -> "EVENTO".equals(m.get("tipo_fator"))).findFirst().orElse(null);
        check("fator EVENTO calculado", evt != null && ((Number) evt.get("n_com")).intValue() >= 3, evt != null ? "lift=" + evt.get("lift_pct") : "");

        long idNotaTeste = scalar(c, "SELECT MIN(n.id) FROM nota n JOIN vinculo_fator_externo_nota v ON v.id=n.id_vinculo_fator_externo WHERE n.descricao='TESTE gatilho' AND v.chave_snapshot LIKE 'TESTE|%'");
        var causas = r.estatisticas().causasExternasPorDemanda(idNotaTeste, null);
        check("causas por demanda: fatores ativos retornados", !causas.get("fatores_ativos_e_efeito_estatistico").isEmpty(),
                "tipos=" + causas.get("fatores_ativos_e_efeito_estatistico").stream().map(m -> m.get("tipo_fator") + ":" + m.get("leitura")).toList());
        check("causas por demanda: notas da mesma janela externa citadas", causas.get("notas_relacionadas").stream().anyMatch(m -> "MESMA_JANELA_EXTERNA".equals(m.get("relacao"))), "n=" + causas.get("notas_relacionadas").size());

        // Eventos reais persistidos + cache no banco (não repete requisição)
        var mn = r.getManageNotas();
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        long antes = scalar(c, "SELECT COUNT(*) FROM evento_externo");
        long t0 = System.currentTimeMillis();
        var v1 = mn.obterOuCriarVinculoFatorExterno(base.withHour(((LocalDateTime.now().getHour() / 6 + 1) % 4) * 6));
        long t1 = System.currentTimeMillis();
        long eventos = scalar(c, "SELECT COUNT(*) FROM evento_externo");
        long ligados = scalar(c, "SELECT COUNT(*) FROM vinculo_evento WHERE id_vinculo=" + v1.getId());
        check("eventos reais gravados em evento_externo e ligados ao snapshot (N:N)", eventos > 0 && ligados > 0,
                "eventos=" + eventos + " (antes " + antes + ") ligados=" + ligados + " em " + (t1 - t0) + "ms");
        var ant = scalar(c, "SELECT COUNT(*) FROM evento_externo WHERE coletado_em >= NOW() - INTERVAL 7 DAY");
        var v2 = mn.obterOuCriarVinculoFatorExterno(base.minusDays(1).withHour(13));
        long ligados2 = scalar(c, "SELECT COUNT(*) FROM vinculo_evento WHERE id_vinculo=" + v2.getId());
        check("segundo snapshot reutiliza eventos do cache persistente", ligados2 == ligados && scalar(c, "SELECT COUNT(*) FROM evento_externo WHERE coletado_em >= NOW() - INTERVAL 7 DAY") == ant,
                "ligados=" + ligados2);
        check("1 fator externo cita várias notas / eventos", scalar(c, "SELECT MAX(c) FROM (SELECT COUNT(*) c FROM vinculo_evento GROUP BY id_vinculo) x") >= 1, "");

        limpar(c);
        r.estatisticas().recalcularEstatisticasFatores();
    }

    static void limpar(Connection c) throws Exception {
        exec(c, "DELETE i FROM item i JOIN nota n ON n.id=i.id_nota WHERE n.descricao IN ('TESTE gatilho','TESTE fila')");
        exec(c, "DELETE FROM nota WHERE descricao IN ('TESTE gatilho','TESTE fila')");
        exec(c, "DELETE FROM vinculo_fator_externo_nota WHERE chave_snapshot LIKE 'TESTE|%'");
    }
}
