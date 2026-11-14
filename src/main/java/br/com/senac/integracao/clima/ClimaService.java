package br.com.senac.integracao.clima;

import br.com.senac.integracao.cache.CacheEntry;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.interfaces.ProvedorFatorExterno;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClimaService implements ProvedorFatorExterno {

    private static final Duration TTL_CACHE_CLIMA_ATUAL = Duration.ofHours(6);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    // Cache de 6 horas para o clima atual por coordenada ("LAT|LON") economizando requisições de API
    private final Map<String, CacheEntry<String>> cacheClimaAtual6Horas = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ClimaDiario>> cacheClimaDiarioAtual6Horas = new ConcurrentHashMap<>();
    // Cache em memória por "ANO-MES|LAT|LON" para evitar chamadas repetidas ao processar grandes lotes passados
    private final Map<String, Map<LocalDate, ClimaDiario>> cacheMensal = new ConcurrentHashMap<>();
    private volatile ClimaDiario ultimoClimaValido;

    public ClimaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    /**
     * Consulta o clima em tempo real com Cache de 6 Horas por região para economizar requisições na API.
     */
    public String buscarClimaAtual(Coordenada coordenada) {
        String chaveRegiao = String.format(Locale.US, "%.2f|%.2f", coordenada.latitude(), coordenada.longitude());
        CacheEntry<String> emCache = cacheClimaAtual6Horas.get(chaveRegiao);
        if (emCache != null && emCache.isValido()) {
            return emCache.dados();
        }

        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m&timezone=auto",
                coordenada.latitude(), coordenada.longitude());

        String corpo = executarGet(url);
        if (corpo != null && corpo.contains("\"current\"")) {
            cacheClimaAtual6Horas.put(chaveRegiao, CacheEntry.criarComTtl(corpo, TTL_CACHE_CLIMA_ATUAL));
            return corpo;
        }
        // Fallback secundário: wttr.in JSON
        String urlWttr = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1",
                coordenada.latitude(), coordenada.longitude());
        String corpoWttr = executarGet(urlWttr);
        if (corpoWttr != null && corpoWttr.contains("\"current_condition\"")) {
            cacheClimaAtual6Horas.put(chaveRegiao, CacheEntry.criarComTtl(corpoWttr, TTL_CACHE_CLIMA_ATUAL));
            return corpoWttr;
        }
        return "Erro ao consultar APIs Climáticas (Open-Meteo e wttr.in indisponíveis)";
    }

    /**
     * Retorna o record ClimaDiario consolidado de hoje utilizando o Cache de 6 Horas.
     */
    public ClimaDiario obterClimaDiarioAtualComCache6h(Coordenada coordenada) {
        String chaveRegiao = String.format(Locale.US, "%.2f|%.2f", coordenada.latitude(), coordenada.longitude());
        CacheEntry<ClimaDiario> emCache = cacheClimaDiarioAtual6Horas.get(chaveRegiao);
        if (emCache != null && emCache.isValido()) {
            return emCache.dados();
        }

        String json = buscarClimaAtual(coordenada);
        ClimaDiario registro;
        if (json.contains("\"current\"")) {
            double temp = extrairValorCurrent(json, "temperature_2m");
            double chuva = extrairValorCurrent(json, "precipitation");
            double vento = extrairValorCurrent(json, "wind_speed_10m");
            registro = new ClimaDiario(LocalDate.now(), temp, temp, temp, chuva, vento);
        } else if (json.contains("\"current_condition\"")) {
            double temp = extrairValorRegex(json, "\"temp_C\"\\s*:\\s*\"?(-?[0-9]+(?:\\.[0-9]+)?)\"?");
            double chuva = extrairValorRegex(json, "\"precipMM\"\\s*:\\s*\"?(-?[0-9]+(?:\\.[0-9]+)?)\"?");
            double vento = extrairValorRegex(json, "\"windspeedKmph\"\\s*:\\s*\"?(-?[0-9]+(?:\\.[0-9]+)?)\"?");
            registro = new ClimaDiario(LocalDate.now(), temp, temp, temp, chuva, vento);
        } else {
            registro = criarClimaFallback(LocalDate.now());
        }
        ultimoClimaValido = registro;
        cacheClimaDiarioAtual6Horas.put(chaveRegiao, CacheEntry.criarComTtl(registro, TTL_CACHE_CLIMA_ATUAL));
        return registro;
    }

    private String executarGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            System.err.println("[ClimaService] HTTP " + response.statusCode() + " ao consultar: " + url);
        } catch (Exception e) {
            System.err.println("[ClimaService] Falha de rede (" + e.getMessage() + ") ao consultar: " + url);
        }
        return null;
    }

    private double extrairValorRegex(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private ClimaDiario criarClimaFallback(LocalDate data) {
        if (ultimoClimaValido != null) {
            return new ClimaDiario(data, ultimoClimaValido.temperaturaMax(), ultimoClimaValido.temperaturaMin(),
                    ultimoClimaValido.temperaturaMedia(), ultimoClimaValido.precipitacaoMm(), ultimoClimaValido.ventoMaxKmh());
        }
        return new ClimaDiario(data, 30.0, 23.0, 26.5, 0.0, 10.0);
    }

    private double extrairValorCurrent(String json, String campo) {
        int idxCurrent = json.indexOf("\"current\":");
        if (idxCurrent == -1) return 0.0;
        Matcher m = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
                .matcher(json.substring(idxCurrent));
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    /**
     * Caso a data de consulta seja inferior a hoje (data passada), busca de uma só vez
     * todos os registros climáticos do mês inteiro e armazena em cache para economizar
     * chamadas de API durante a entrada de grandes lotes de registros passados.
     */
    public List<ClimaDiario> buscarClimaDoMesSeDataPassada(LocalDate dataConsulta, Coordenada coordenada) {
        LocalDate hoje = LocalDate.now();
        if (!dataConsulta.isBefore(hoje)) {
            System.out.println("[ClimaService] A data informada (" + dataConsulta + ") não é inferior a hoje. Use buscarClimaAtual().");
            return Collections.emptyList();
        }
        Map<LocalDate, ClimaDiario> mapaMes = carregarOuObterCacheDoMes(
                YearMonth.from(dataConsulta), coordenada);
        return new ArrayList<>(mapaMes.values());
    }

    /**
     * Consulta o clima de uma data específica reutilizando o cache mensal em lote
     * quando a data é passada (1 única requisição HTTP serve todos os dias daquele mês).
     */
    public Optional<ClimaDiario> buscarClimaPorData(LocalDate dataConsulta, Coordenada coordenada) {
        Map<LocalDate, ClimaDiario> mapaMes = carregarOuObterCacheDoMes(
                YearMonth.from(dataConsulta), coordenada);
        return Optional.ofNullable(mapaMes.get(dataConsulta));
    }

    private Map<LocalDate, ClimaDiario> carregarOuObterCacheDoMes(YearMonth anoMes, Coordenada coordenada) {
        String chaveCache = String.format(Locale.US, "%s|%.3f|%.3f",
                anoMes, coordenada.latitude(), coordenada.longitude());

        Map<LocalDate, ClimaDiario> existente = cacheMensal.get(chaveCache);
        if (existente != null && !existente.isEmpty()) {
            return existente;
        }
        Map<LocalDate, ClimaDiario> obtido = requisitarClimaMensalNaApi(anoMes, coordenada);
        if (!obtido.isEmpty()) {
            cacheMensal.put(chaveCache, obtido);
        }
        return obtido;
    }

    private Map<LocalDate, ClimaDiario> requisitarClimaMensalNaApi(YearMonth anoMes, Coordenada coordenada) {
        Map<LocalDate, ClimaDiario> resultadoMes = new LinkedHashMap<>();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = anoMes.atDay(1);
        LocalDate fimMes = anoMes.atEndOfMonth();

        // Se for o mês atual, limita a busca até ontem (dias passados consolidados)
        if (fimMes.isAfter(hoje.minusDays(1))) {
            fimMes = hoje.minusDays(1);
        }
        if (inicioMes.isAfter(fimMes)) {
            return resultadoMes;
        }

        // 1. Provedor primário: Open-Meteo (archive-api ou forecast)
        long diasAtras = ChronoUnit.DAYS.between(inicioMes, hoje);
        String baseEndpoint = (diasAtras > 60)
                ? "https://archive-api.open-meteo.com/v1/archive"
                : "https://api.open-meteo.com/v1/forecast";

        String urlOpenMeteo = String.format(Locale.US,
                "%s?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s&daily=temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum,wind_speed_10m_max&timezone=auto",
                baseEndpoint,
                coordenada.latitude(),
                coordenada.longitude(),
                inicioMes,
                fimMes);

        String json = executarGet(urlOpenMeteo);
        if (json != null && json.contains("\"daily\":")) {
            preencherDeOpenMeteo(json, resultadoMes);
        }

        // 2. Redundância secundária: NASA POWER API (histórico diário global)
        if (resultadoMes.isEmpty()) {
            System.err.println("[ClimaService] Open-Meteo sem dados para " + anoMes + ". Acionando fallback NASA POWER...");
            consultarNasaPowerMensal(inicioMes, fimMes, coordenada, resultadoMes);
        }

        if (!resultadoMes.isEmpty()) {
            resultadoMes.values().stream().findFirst().ifPresent(c -> ultimoClimaValido = c);
        }
        return resultadoMes;
    }

    private void preencherDeOpenMeteo(String json, Map<LocalDate, ClimaDiario> resultadoMes) {
        int idxDaily = json.indexOf("\"daily\":");
        if (idxDaily == -1) return;
        String blocoDaily = json.substring(idxDaily);
        String[] datas = extrairArrayString(blocoDaily, "time");
        double[] tempMax = extrairArrayDouble(blocoDaily, "temperature_2m_max");
        double[] tempMin = extrairArrayDouble(blocoDaily, "temperature_2m_min");
        double[] tempMean = extrairArrayDouble(blocoDaily, "temperature_2m_mean");
        double[] precip = extrairArrayDouble(blocoDaily, "precipitation_sum");
        double[] vento = extrairArrayDouble(blocoDaily, "wind_speed_10m_max");

        for (int i = 0; i < datas.length; i++) {
            try {
                LocalDate data = LocalDate.parse(datas[i].trim());
                resultadoMes.put(data, new ClimaDiario(
                        data,
                        obterValorSeguro(tempMax, i),
                        obterValorSeguro(tempMin, i),
                        obterValorSeguro(tempMean, i),
                        obterValorSeguro(precip, i),
                        obterValorSeguro(vento, i)
                ));
            } catch (Exception ignored) {
            }
        }
    }

    private void consultarNasaPowerMensal(LocalDate inicio, LocalDate fim, Coordenada coordenada, Map<LocalDate, ClimaDiario> destino) {
        String start = inicio.toString().replace("-", "");
        String end = fim.toString().replace("-", "");
        String urlNasa = String.format(Locale.US,
                "https://power.larc.nasa.gov/api/temporal/daily/point?parameters=T2M,T2M_MAX,T2M_MIN,PRECTOTCORR,WS10M&community=RE&longitude=%.4f&latitude=%.4f&start=%s&end=%s&format=JSON",
                coordenada.longitude(), coordenada.latitude(), start, end);
        String json = executarGet(urlNasa);
        if (json == null || !json.contains("\"parameter\"")) return;

        Map<String, Double> tMean = extrairMapaNasa(json, "T2M");
        Map<String, Double> tMax = extrairMapaNasa(json, "T2M_MAX");
        Map<String, Double> tMin = extrairMapaNasa(json, "T2M_MIN");
        Map<String, Double> precip = extrairMapaNasa(json, "PRECTOTCORR");
        Map<String, Double> vento = extrairMapaNasa(json, "WS10M");

        for (String yyyymmdd : tMean.keySet()) {
            try {
                LocalDate data = LocalDate.of(
                        Integer.parseInt(yyyymmdd.substring(0, 4)),
                        Integer.parseInt(yyyymmdd.substring(4, 6)),
                        Integer.parseInt(yyyymmdd.substring(6, 8)));
                double media = tMean.getOrDefault(yyyymmdd, 26.0);
                double max = tMax.getOrDefault(yyyymmdd, media);
                double min = tMin.getOrDefault(yyyymmdd, media);
                double chuva = Math.max(0.0, precip.getOrDefault(yyyymmdd, 0.0));
                double windKmh = Math.max(0.0, vento.getOrDefault(yyyymmdd, 0.0) * 3.6);
                if (media > -900) {
                    destino.put(data, new ClimaDiario(data, max, min, media, chuva, windKmh));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Map<String, Double> extrairMapaNasa(String json, String parametro) {
        Map<String, Double> mapa = new LinkedHashMap<>();
        Matcher bloco = Pattern.compile("\"" + Pattern.quote(parametro) + "\"\\s*:\\s*\\{([^}]*)\\}").matcher(json);
        if (!bloco.find()) return mapa;
        Matcher par = Pattern.compile("\"(\\d{8})\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(bloco.group(1));
        while (par.find()) {
            try {
                double val = Double.parseDouble(par.group(2));
                if (val > -900) {
                    mapa.put(par.group(1), val);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return mapa;
    }

    private String[] extrairArrayString(String json, String campo) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find() || m.group(1).isBlank()) {
            return new String[0];
        }
        return m.group(1).replace("\"", "").split("\\s*,\\s*");
    }

    private double[] extrairArrayDouble(String json, String campo) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find() || m.group(1).isBlank()) {
            return new double[0];
        }
        String[] partes = m.group(1).split("\\s*,\\s*");
        double[] valores = new double[partes.length];
        for (int i = 0; i < partes.length; i++) {
            try {
                valores[i] = ("null".equalsIgnoreCase(partes[i])) ? 0.0 : Double.parseDouble(partes[i]);
            } catch (NumberFormatException e) {
                valores[i] = 0.0;
            }
        }
        return valores;
    }

    private double obterValorSeguro(double[] arr, int idx) {
        return (idx >= 0 && idx < arr.length) ? arr[idx] : 0.0;
    }

    @Override
    public void enriquecerVinculo(LocalDate data, Coordenada coordenada, VinculoFatorExternoNota vinculo) {
        if (data.isBefore(LocalDate.now())) {
            ClimaDiario clima = buscarClimaPorData(data, coordenada).orElseGet(() -> criarClimaFallback(data));
            vinculo.setClimaRegistrado(clima);
        } else {
            vinculo.setClimaRegistrado(obterClimaDiarioAtualComCache6h(coordenada));
        }
    }

    @Override
    public void limparCacheExpirado() {
        cacheClimaAtual6Horas.entrySet().removeIf(e -> !e.getValue().isValido());
        cacheClimaDiarioAtual6Horas.entrySet().removeIf(e -> !e.getValue().isValido());
    }

    @Override
    public String extrairDetalheVinculo(VinculoFatorExternoNota vinculo) {
        if (vinculo == null || vinculo.getClimaRegistrado() == null) return "N/A";
        ClimaDiario c = vinculo.getClimaRegistrado();
        return String.format(Locale.US, "Temp Média: %.1f°C (Máx %.1f°C / Mín %.1f°C), Chuva: %.1f mm, Vento: %.1f km/h",
                c.temperaturaMedia(), c.temperaturaMax(), c.temperaturaMin(), c.precipitacaoMm(), c.ventoMaxKmh());
    }
}
