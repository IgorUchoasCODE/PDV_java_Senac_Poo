package br.com.senac.repository.dao.nota;

import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import jakarta.persistence.EntityManager;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistência dos eventos reais (Sympla/MinC) e da fila de notas sem fator externo.
 * O banco funciona como cache persistente: dentro do TTL nenhuma requisição externa é refeita,
 * mesmo após reiniciar o sistema.
 */
public class FatorExternoPersistenciaDAO {

    public static final int TTL_EVENTOS_DIAS = 7;

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("fev", 2), Map.entry("mar", 3), Map.entry("abr", 4),
            Map.entry("mai", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("ago", 8),
            Map.entry("set", 9), Map.entry("out", 10), Map.entry("nov", 11), Map.entry("dez", 12));

    /** Existe coleta de eventos da cidade/UF dentro do TTL? (evita nova requisição externa) */
    public boolean existeColetaRecente(String cidade, String uf, int dias) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM evento_externo WHERE UPPER(cidade) = UPPER(?) AND UPPER(uf) = UPPER(?) " +
                        "AND coletado_em >= NOW() - INTERVAL ? DAY LIMIT 1")) {
                    ps.setString(1, cidade);
                    ps.setString(2, uf);
                    ps.setInt(3, dias);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            });
        }
    }

    /** Carrega do banco os eventos vigentes (coletados dentro do TTL) e os registra no vinculo, sem requisição externa. */
    public void carregarEventosRecentes(String cidade, String uf, int dias, VinculoFatorExternoNota vinculo) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                List<EventoSympla> sympla = new ArrayList<>();
                List<ProjetoCulturalMinC> minc = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT fonte, id_externo, nome, local_nome, bairro, cidade, uf, data_texto, data_inicio, data_fim, " +
                        "distancia_km, url, segmento, situacao, proponente, resumo FROM evento_externo " +
                        "WHERE UPPER(cidade) = UPPER(?) AND UPPER(uf) = UPPER(?) AND coletado_em >= NOW() - INTERVAL ? DAY")) {
                    ps.setString(1, cidade);
                    ps.setString(2, uf);
                    ps.setInt(3, dias);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if ("SYMPLA".equals(rs.getString("fonte"))) {
                                sympla.add(new EventoSympla(Long.parseLong(rs.getString("id_externo")), rs.getString("nome"),
                                        nz(rs.getString("data_texto")), nz(rs.getString("local_nome")), nz(rs.getString("bairro")),
                                        rs.getString("cidade") + "/" + rs.getString("uf"), rs.getDouble("distancia_km"), nz(rs.getString("url"))));
                            } else {
                                Date di = rs.getDate("data_inicio");
                                Date df = rs.getDate("data_fim");
                                minc.add(new ProjetoCulturalMinC(rs.getString("id_externo"), rs.getString("nome"), nz(rs.getString("segmento")),
                                        di != null ? di.toString() : "", df != null ? df.toString() : "", nz(rs.getString("situacao")),
                                        rs.getString("cidade"), rs.getString("uf"), nz(rs.getString("proponente")), nz(rs.getString("resumo"))));
                            }
                        }
                    }
                }
                vinculo.registrarEventosSympla(sympla);
                vinculo.registrarEventosMinC(minc);
            });
        }
    }

    /** Grava (upsert) os eventos e vincula todos ao snapshot (N:N). Retorna quantos vínculos foram criados. */
    public int salvarEVincular(Long idVinculo, List<EventoSympla> sympla, List<ProjetoCulturalMinC> minc) {
        if (idVinculo == null) return 0;
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                int vinculos = 0;
                String upsert = "INSERT INTO evento_externo (fonte, id_externo, nome, local_nome, bairro, cidade, uf, data_inicio, data_fim, " +
                        "data_texto, distancia_km, url, segmento, situacao, proponente, resumo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE nome = VALUES(nome), distancia_km = VALUES(distancia_km), coletado_em = NOW(), id = LAST_INSERT_ID(id)";
                try (PreparedStatement ps = conn.prepareStatement(upsert, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement lig = conn.prepareStatement("INSERT IGNORE INTO vinculo_evento (id_vinculo, id_evento) VALUES (?, ?)")) {
                    for (EventoSympla e : sympla) {
                        String[] cu = dividirCidadeUf(e.cidadeUf());
                        LocalDate ini = interpretarDataTexto(e.dataHorario());
                        preencher(ps, "SYMPLA", String.valueOf(e.id()), e.nome(), e.localNome(), e.bairro(), cu[0], cu[1], ini, ini,
                                e.dataHorario(), e.distanciaKm() >= 0 ? e.distanciaKm() : null, e.url(), null, null, null, null);
                        vinculos += ligar(ps, lig, idVinculo);
                    }
                    for (ProjetoCulturalMinC p : minc) {
                        preencher(ps, "MINC", p.pronac(), p.nome(), null, null, p.municipio(), p.uf(), interpretarDataTexto(p.dataInicio()),
                                interpretarDataTexto(p.dataTermino()), p.dataInicio() + " a " + p.dataTermino(), null, null,
                                p.segmento(), p.situacao(), p.proponente(), p.resumo());
                        vinculos += ligar(ps, lig, idVinculo);
                    }
                }
                return vinculos;
            });
        }
    }

    /** Liga o snapshot aos eventos vigentes da cidade/UF já gravados (reaproveitamento do cache persistente). */
    public int vincularEventosRecentes(Long idVinculo, String cidade, String uf, int dias) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO vinculo_evento (id_vinculo, id_evento) SELECT ?, id FROM evento_externo " +
                        "WHERE UPPER(cidade) = UPPER(?) AND UPPER(uf) = UPPER(?) AND coletado_em >= NOW() - INTERVAL ? DAY")) {
                    ps.setLong(1, idVinculo);
                    ps.setString(2, cidade);
                    ps.setString(3, uf);
                    ps.setInt(4, dias);
                    return ps.executeUpdate();
                }
            });
        }
    }

    /** Janelas (data + bloco de 6h) com notas aguardando fator externo. */
    public List<int[]> listarJanelasPendentes() {
        List<int[]> r = new ArrayList<>();
        try (EntityManager em = JPAUtil.getEntityManager()) {
            em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT DISTINCT data_referencia, bloco_horario_6h FROM fila_fator_externo_pendente ORDER BY 1, 2")) {
                    while (rs.next()) {
                        LocalDate d = rs.getDate(1).toLocalDate();
                        r.add(new int[]{(int) d.toEpochDay(), rs.getInt(2)});
                    }
                }
            });
        }
        return r;
    }

    /** Aplica o snapshot às notas pendentes da janela e remove da fila. */
    public int resolverJanela(LocalDate data, int bloco, Long idVinculo) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return em.unwrap(org.hibernate.Session.class).doReturningWork(conn -> {
                int n;
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE nota n JOIN fila_fator_externo_pendente f ON f.id_nota = n.id SET n.id_vinculo_fator_externo = ? " +
                        "WHERE f.data_referencia = ? AND f.bloco_horario_6h = ?")) {
                    up.setLong(1, idVinculo);
                    up.setDate(2, Date.valueOf(data));
                    up.setInt(3, bloco);
                    n = up.executeUpdate();
                }
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM fila_fator_externo_pendente WHERE data_referencia = ? AND bloco_horario_6h = ?")) {
                    del.setDate(1, Date.valueOf(data));
                    del.setInt(2, bloco);
                    del.executeUpdate();
                }
                return n;
            });
        }
    }

    // ----------------------------------------------------------------------------------------------------

    private int ligar(PreparedStatement ps, PreparedStatement lig, Long idVinculo) throws java.sql.SQLException {
        ps.executeUpdate();
        long idEvento;
        try (ResultSet k = ps.getGeneratedKeys()) {
            if (!k.next()) return 0;
            idEvento = k.getLong(1);
        }
        lig.setLong(1, idVinculo);
        lig.setLong(2, idEvento);
        return lig.executeUpdate();
    }

    private void preencher(PreparedStatement ps, String fonte, String idExterno, String nome, String local, String bairro, String cidade,
                           String uf, LocalDate ini, LocalDate fim, String dataTexto, Double dist, String url,
                           String segmento, String situacao, String proponente, String resumo) throws java.sql.SQLException {
        ps.setString(1, fonte);
        ps.setString(2, corta(idExterno, 60));
        ps.setString(3, corta(nome, 300));
        ps.setString(4, corta(local, 200));
        ps.setString(5, corta(bairro, 100));
        ps.setString(6, corta(cidade == null || cidade.isBlank() ? "Manaus" : cidade, 80));
        ps.setString(7, corta(uf == null || uf.isBlank() ? "AM" : uf, 5));
        ps.setDate(8, ini != null ? Date.valueOf(ini) : null);
        ps.setDate(9, fim != null ? Date.valueOf(fim) : null);
        ps.setString(10, corta(dataTexto, 120));
        ps.setObject(11, dist);
        ps.setString(12, corta(url, 500));
        ps.setString(13, corta(segmento, 120));
        ps.setString(14, corta(situacao, 80));
        ps.setString(15, corta(proponente, 200));
        ps.setString(16, corta(resumo, 600));
    }

    private static String corta(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String[] dividirCidadeUf(String cidadeUf) {
        if (cidadeUf == null || !cidadeUf.contains("/")) return new String[]{"Manaus", "AM"};
        int i = cidadeUf.lastIndexOf('/');
        return new String[]{cidadeUf.substring(0, i).trim(), cidadeUf.substring(i + 1).trim()};
    }

    /** Interpreta "2026-11-14", "14/11/2026", "Sáb, 14 Nov - 2026 · 18:00" ou "14/NOV" (ano atual). */
    static LocalDate interpretarDataTexto(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        try {
            return LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
        }
        Matcher m = Pattern.compile("(\\d{1,2})\\s*[/ ]\\s*([A-Za-zçÇ]{3})[A-Za-z]*(?:\\s*-?\\s*(\\d{4}))?").matcher(t);
        if (m.find()) {
            Integer mes = MESES.get(m.group(2).toLowerCase(Locale.ROOT));
            if (mes != null) {
                int ano = m.group(3) != null ? Integer.parseInt(m.group(3)) : LocalDate.now().getYear();
                try {
                    return LocalDate.of(ano, mes, Integer.parseInt(m.group(1)));
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
