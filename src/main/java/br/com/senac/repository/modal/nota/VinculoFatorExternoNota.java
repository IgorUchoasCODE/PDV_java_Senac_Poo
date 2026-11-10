package br.com.senac.repository.modal.nota;

import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot compartilhado (N:1 com Nota) dos fatores externos (Calendário, Clima 6h, Rios 6h e Eventos 7d).
 * Em vez de gravar 1 registro duplicado por cada Nota emitida no mesmo período, todas as Notas dentro da
 * mesma janela de 6h e região apontam para o mesmo registro, reduzindo em >99% as gravações no banco.
 */
@Entity
@Table(
        name = "vinculo_fator_externo_nota",
        indexes = {
                @Index(name = "uk_snapshot_fator_externo", columnList = "chave_snapshot", unique = true),
                @Index(name = "idx_fator_data_cidade", columnList = "data_referencia, cidade, uf")
        }
)
public class VinculoFatorExternoNota implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_snapshot", nullable = false, unique = true, length = 120)
    private String chaveSnapshot;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Column(name = "bloco_horario_6h", nullable = false)
    private int blocoHorario6h;

    // Campos desnormalizados do record Coordenada (0 JOINs extras)
    @Column(name = "latitude")
    private double latitude;

    @Column(name = "longitude")
    private double longitude;

    @Column(name = "precisao_metros")
    private double precisaoMetros;

    @Column(name = "cidade", length = 80)
    private String cidade = "Manaus";

    @Column(name = "uf", length = 5)
    private String uf = "AM";

    @Column(name = "bairro", length = 100)
    private String bairro = "";

    @Column(name = "logradouro", length = 180)
    private String logradouro = "";

    // Campos de Calendário
    @Column(name = "dia_util", nullable = false)
    private boolean diaUtil;

    @Column(name = "quinto_dia_util", nullable = false)
    private boolean quintoDiaUtil;

    @Column(name = "resumo_calendario", length = 255)
    private String resumoCalendario;

    // Campos desnormalizados do record ClimaDiario
    @Column(name = "temp_max")
    private double temperaturaMax;

    @Column(name = "temp_min")
    private double temperaturaMin;

    @Column(name = "temp_media")
    private double temperaturaMedia;

    @Column(name = "precipitacao_mm")
    private double precipitacaoMm;

    @Column(name = "vento_max_kmh")
    private double ventoMaxKmh;

    // Campos desnormalizados do record NivelRioDiario
    @Column(name = "vazao_m3s")
    private double vazaoM3s;

    @Column(name = "vazao_media_m3s")
    private double vazaoMediaM3s;

    @Column(name = "vazao_max_m3s")
    private double vazaoMaxM3s;

    @Column(name = "vazao_min_m3s")
    private double vazaoMinM3s;

    // Resumo compacto de Eventos (MinC + Sympla) para evitar tabelas filhas massivas
    @Column(name = "total_eventos_ativos", nullable = false)
    private int totalEventosAtivos = 0;

    @Column(name = "resumo_eventos_json", length = 2000)
    private String resumoEventosJson = "[]";

    @Transient
    private List<ProjetoCulturalMinC> eventosMinC = new ArrayList<>();

    @Transient
    private List<EventoSympla> eventosSympla = new ArrayList<>();

    public VinculoFatorExternoNota() {
    }

    public VinculoFatorExternoNota(String chaveSnapshot, LocalDate dataReferencia, int blocoHorario6h, Coordenada coordenada) {
        this.chaveSnapshot = chaveSnapshot;
        this.dataReferencia = dataReferencia;
        this.blocoHorario6h = blocoHorario6h;
        setCoordenada(coordenada);
    }

    public Coordenada getCoordenada() {
        return new Coordenada(latitude, longitude, precisaoMetros, cidade, uf, bairro, logradouro);
    }

    public void setCoordenada(Coordenada coordenada) {
        if (coordenada != null) {
            this.latitude = coordenada.latitude();
            this.longitude = coordenada.longitude();
            this.precisaoMetros = coordenada.precisaoMetros();
            this.cidade = coordenada.cidade();
            this.uf = coordenada.uf();
            this.bairro = coordenada.bairro();
            this.logradouro = coordenada.logradouro();
        }
    }

    public ClimaDiario getClimaRegistrado() {
        return new ClimaDiario(dataReferencia, temperaturaMax, temperaturaMin, temperaturaMedia, precipitacaoMm, ventoMaxKmh);
    }

    public void setClimaRegistrado(ClimaDiario clima) {
        if (clima != null) {
            this.temperaturaMax = clima.temperaturaMax();
            this.temperaturaMin = clima.temperaturaMin();
            this.temperaturaMedia = clima.temperaturaMedia();
            this.precipitacaoMm = clima.precipitacaoMm();
            this.ventoMaxKmh = clima.ventoMaxKmh();
        }
    }

    public NivelRioDiario getNivelRioRegistrado() {
        return new NivelRioDiario(dataReferencia, vazaoM3s, vazaoMediaM3s, vazaoMaxM3s, vazaoMinM3s);
    }

    public void setNivelRioRegistrado(NivelRioDiario nivelRio) {
        if (nivelRio != null) {
            this.vazaoM3s = nivelRio.vazaoM3s();
            this.vazaoMediaM3s = nivelRio.vazaoMediaM3s();
            this.vazaoMaxM3s = nivelRio.vazaoMaxM3s();
            this.vazaoMinM3s = nivelRio.vazaoMinM3s();
        }
    }

    public void registrarEventosMinC(List<ProjetoCulturalMinC> projetos) {
        this.eventosMinC = projetos != null ? new ArrayList<>(projetos) : new ArrayList<>();
        atualizarResumoEventosCompacto();
    }

    public void registrarEventosSympla(List<EventoSympla> eventos) {
        this.eventosSympla = eventos != null ? new ArrayList<>(eventos) : new ArrayList<>();
        atualizarResumoEventosCompacto();
    }

    private void atualizarResumoEventosCompacto() {
        this.totalEventosAtivos = (eventosMinC != null ? eventosMinC.size() : 0)
                + (eventosSympla != null ? eventosSympla.size() : 0);

        StringBuilder sb = new StringBuilder("[");
        if (eventosSympla != null) {
            for (int i = 0; i < Math.min(3, eventosSympla.size()); i++) {
                if (sb.length() > 1) sb.append(", ");
                sb.append("\"Sympla: ").append(eventosSympla.get(i).nome().replace("\"", "'")).append("\"");
            }
        }
        if (eventosMinC != null) {
            for (int i = 0; i < Math.min(3, eventosMinC.size()); i++) {
                if (sb.length() > 1) sb.append(", ");
                sb.append("\"MinC: ").append(eventosMinC.get(i).nome().replace("\"", "'")).append("\"");
            }
        }
        sb.append("]");
        this.resumoEventosJson = sb.length() > 2000 ? sb.substring(0, 1997) + "...]" : sb.toString();
    }

    public boolean possuiEventosAtivosNaRegiao() {
        return totalEventosAtivos > 0;
    }

    public BigDecimal calcularIndiceImpactoDemanda() {
        BigDecimal indice = BigDecimal.ONE;
        if (quintoDiaUtil) {
            indice = indice.add(new BigDecimal("0.35"));
        } else if (!diaUtil) {
            indice = indice.add(new BigDecimal("0.20"));
        }
        if (temperaturaMedia >= 31.0) {
            indice = indice.add(new BigDecimal("0.15"));
        }
        if (precipitacaoMm > 15.0) {
            indice = indice.subtract(new BigDecimal("0.10"));
        }
        if (possuiEventosAtivosNaRegiao()) {
            indice = indice.add(new BigDecimal("0.25"));
        }
        return MoedaContabilBacen.arredondarMoeda(indice);
    }

    public String determinarPadraoConsumo() {
        StringBuilder padrao = new StringBuilder();
        padrao.append(diaUtil ? "DIA_UTIL" : "FERIADO_OU_FIM_DE_SEMANA");
        if (quintoDiaUtil) {
            padrao.append(" | PICO_5_DIA_UTIL");
        }
        if (precipitacaoMm > 10.0) {
            padrao.append(" | CHUVOSO_DELIVERY_ALTO");
        } else if (temperaturaMedia >= 30.0) {
            padrao.append(" | CALOR_ALTA_DEMANDA_BEBIDAS");
        } else {
            padrao.append(" | CLIMA_ESTAVEL");
        }
        if (possuiEventosAtivosNaRegiao()) {
            padrao.append(" | EVENTOS_ATIVOS(").append(totalEventosAtivos).append(")");
        }
        return padrao.toString();
    }

    public Long getId() {
        return id;
    }

    public String getChaveSnapshot() {
        return chaveSnapshot;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public int getBlocoHorario6h() {
        return blocoHorario6h;
    }

    public boolean isDiaUtil() {
        return diaUtil;
    }

    public void setDiaUtil(boolean diaUtil) {
        this.diaUtil = diaUtil;
    }

    public boolean isQuintoDiaUtil() {
        return quintoDiaUtil;
    }

    public void setQuintoDiaUtil(boolean quintoDiaUtil) {
        this.quintoDiaUtil = quintoDiaUtil;
    }

    public String getResumoCalendario() {
        return resumoCalendario;
    }

    public void setResumoCalendario(String resumoCalendario) {
        this.resumoCalendario = resumoCalendario;
    }

    public int getTotalEventosAtivos() {
        return totalEventosAtivos;
    }

    public String getResumoEventosJson() {
        return resumoEventosJson;
    }

    public List<ProjetoCulturalMinC> getEventosMinC() {
        return eventosMinC;
    }

    public List<EventoSympla> getEventosSympla() {
        return eventosSympla;
    }

    @Override
    public String toString() {
        return String.format("VinculoFatorExternoNota{id=%d, snapshot='%s', padrao='%s', impacto=%s}",
                id, chaveSnapshot, determinarPadraoConsumo(), calcularIndiceImpactoDemanda());
    }
}
