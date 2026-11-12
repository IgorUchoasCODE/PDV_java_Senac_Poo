package br.com.senac.integracao.facade;

import br.com.senac.integracao.calendario.CalendarioService;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.ia.CatalogoMensagens;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.integracao.rios.NivelRioService;

import java.time.LocalDate;
import java.util.*;

/**
 * Facade unificado para consulta simplificada e segura aos provedores de fatores externos
 * (Clima, Nível dos Rios, Eventos Culturais Sympla/MinC, Calendário Comercial e Geolocalização).
 */
public class ConsultaIntegracaoFacade {

    private final ClimaService climaService;
    private final NivelRioService nivelRioService;
    private final SymplaService symplaService;
    private final MinisterioCulturaService minCulturaService;
    private final CalendarioService calendarioService;
    private final GeolocalizacaoService geolocalizacaoService;

    public ConsultaIntegracaoFacade() {
        this(
                new ClimaService(),
                new NivelRioService(),
                new SymplaService(),
                new MinisterioCulturaService(),
                new CalendarioService(),
                new GeolocalizacaoService()
        );
    }

    public ConsultaIntegracaoFacade(
            ClimaService climaService,
            NivelRioService nivelRioService,
            SymplaService symplaService,
            MinisterioCulturaService minCulturaService,
            CalendarioService calendarioService,
            GeolocalizacaoService geolocalizacaoService) {
        this.climaService = climaService;
        this.nivelRioService = nivelRioService;
        this.symplaService = symplaService;
        this.minCulturaService = minCulturaService;
        this.calendarioService = calendarioService;
        this.geolocalizacaoService = geolocalizacaoService;
    }

    public Coordenada obterCoordenada(String cidade, String uf) {
        String c = (cidade != null && !cidade.isBlank()) ? cidade.trim() : "Manaus";
        String u = (uf != null && !uf.isBlank()) ? uf.trim().toUpperCase(Locale.ROOT) : "AM";
        return geolocalizacaoService.obterCoordenadasDispositivo()
                .orElse(new Coordenada(-3.1190, -60.0217, 10.0, c, u, "Centro", "Av. Eduardo Ribeiro"));
    }

    public String consultarClima(LocalDate data, String cidade, String uf) {
        LocalDate dt = data != null ? data : LocalDate.now();
        Coordenada coord = obterCoordenada(cidade, uf);
        try {
            Optional<ClimaDiario> climaOpt = climaService.buscarClimaPorData(dt, coord);
            if (climaOpt.isPresent()) {
                ClimaDiario c = climaOpt.get();
                return CatalogoMensagens.formatar("ia.fator.clima",
                        "Clima em %s/%s (%s): Temp. Média: %.1f°C (Mín: %.1f°C / Máx: %.1f°C), Chuva: %.1f mm",
                        coord.cidade(), coord.uf(), dt,
                        c.temperaturaMedia(), c.temperaturaMin(), c.temperaturaMax(), c.precipitacaoMm());
            }
        } catch (Exception ignored) {}
        return String.format("Clima em %s/%s (%s): Dados meteorológicos indisponíveis no momento.",
                coord.cidade(), coord.uf(), dt);
    }

    public String consultarNivelRio(LocalDate data, String cidade, String uf) {
        LocalDate dt = data != null ? data : LocalDate.now();
        Coordenada coord = obterCoordenada(cidade, uf);
        try {
            Optional<NivelRioDiario> rioOpt = nivelRioService.consultarNivelRioPorData(dt, coord);
            if (rioOpt.isPresent()) {
                NivelRioDiario r = rioOpt.get();
                return CatalogoMensagens.formatar("ia.fator.rio",
                        "Nível do Rio em %s (%s): Vazão atual: %.2f m³/s (Média: %.2f m³/s)",
                        coord.cidade(), dt, r.vazaoM3s(), r.vazaoMediaM3s());
            }
        } catch (Exception ignored) {}
        return String.format("Nível dos Rios na região de %s/%s (%s): Sem leitura telemétrica registrada.",
                coord.cidade(), coord.uf(), dt);
    }

    public String consultarEventos(LocalDate data, String cidade, String uf) {
        LocalDate dt = data != null ? data : LocalDate.now();
        Coordenada coord = obterCoordenada(cidade, uf);
        List<String> nomesEventos = new ArrayList<>();
        try {
            List<EventoSympla> sympla = symplaService.buscarEventosPublicosPorLocalizacao(coord);
            for (EventoSympla e : sympla) {
                nomesEventos.add(e.nome() + " (" + e.localNome() + ")");
            }
        } catch (Exception ignored) {}

        if (nomesEventos.isEmpty()) {
            return String.format("Eventos Culturais em %s/%s (%s): Nenhum grande evento cadastrado para a data.",
                    coord.cidade(), coord.uf(), dt);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(CatalogoMensagens.formatar("ia.fator.eventos",
                "Eventos Culturais em %s (%s): %d evento(s) ativo(s)",
                coord.cidade(), dt, nomesEventos.size())).append(":\n");
        for (String ev : nomesEventos) {
            sb.append("  • ").append(ev).append("\n");
        }
        return sb.toString().trim();
    }

    public String consultarCalendario(LocalDate data, String uf) {
        LocalDate dt = data != null ? data : LocalDate.now();
        Coordenada coord = obterCoordenada("Manaus", uf);
        boolean util = calendarioService.isDiaUtil(dt, coord);
        LocalDate quintoUtil = calendarioService.obterQuintoDiaUtil(dt.getYear(), dt.getMonthValue(), coord);
        boolean ehQuinto = dt.equals(quintoUtil);
        String resumo = calendarioService.obterResumoDiaUtil(dt, coord);

        return CatalogoMensagens.formatar("ia.fator.calendario",
                "Calendário em %s (%s): %s | 5º Dia Útil: %s",
                coord.cidade(), dt, resumo != null ? resumo : (util ? "DIA ÚTIL" : "DIA NÃO ÚTIL"),
                ehQuinto ? "SIM" : "NÃO");
    }

    public Map<String, String> consultarSnapshotIntegrado(LocalDate data, String cidade, String uf) {
        LocalDate dt = data != null ? data : LocalDate.now();
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("data", dt.toString());
        snapshot.put("cidade", (cidade != null && !cidade.isBlank()) ? cidade : "Manaus");
        snapshot.put("uf", (uf != null && !uf.isBlank()) ? uf : "AM");
        snapshot.put("calendario", consultarCalendario(dt, uf));
        snapshot.put("clima", consultarClima(dt, cidade, uf));
        snapshot.put("rio", consultarNivelRio(dt, cidade, uf));
        snapshot.put("eventos", consultarEventos(dt, cidade, uf));
        return snapshot;
    }
}
