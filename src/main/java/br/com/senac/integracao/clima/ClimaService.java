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

    private final HttpClient httpClient;
    // Cache de 6 horas para o clima atual por coordenada ("LAT|LON") economizando requisições de API
    private final Map<String, CacheEntry<String>> cacheClimaAtual6Horas = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<ClimaDiario>> cacheClimaDiarioAtual6Horas = new ConcurrentHashMap<>();
    // Cache em memória por "ANO-MES|LAT|LON" para evitar chamadas repetidas ao processar grandes lotes passados
    private final Map<String, Map<LocalDate, ClimaDiario>> cacheMensal = new ConcurrentHashMap<>();

    public ClimaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
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

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String corpo = response.body();
            cacheClimaAtual6Horas.put(chaveRegiao, CacheEntry.criarComTtl(corpo, TTL_CACHE_CLIMA_ATUAL));
            return corpo;
        } catch (Exception e) {
            return "Erro ao consultar API Climática: " + e.getMessage();
        }
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
        double temp = extrairValorCurrent(json, "temperature_2m");
        double chuva = extrairValorCurrent(json, "precipitation");
        double vento = extrairValorCurrent(json, "wind_speed_10m");

        ClimaDiario registro = new ClimaDiario(LocalDate.now(), temp, temp, temp, chuva, vento);
        cacheClimaDiarioAtual6Horas.put(chaveRegiao, CacheEntry.criarComTtl(registro, TTL_CACHE_CLIMA_ATUAL));
        return registro;
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

        return cacheMensal.computeIfAbsent(chaveCache, k -> requisitarClimaMensalNaApi(anoMes, coordenada));
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

        // Para meses com mais de 60 dias no passado, utiliza o endpoint histórico (archive-api)
        long diasAtras = ChronoUnit.DAYS.between(inicioMes, hoje);
        String baseEndpoint = (diasAtras > 60)
                ? "https://archive-api.open-meteo.com/v1/archive"
                : "https://api.open-meteo.com/v1/forecast";

        String url = String.format(Locale.US,
                "%s?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s&daily=temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum,wind_speed_10m_max&timezone=auto",
                baseEndpoint,
                coordenada.latitude(),
                coordenada.longitude(),
                inicioMes,
                fimMes);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            int idxDaily = json.indexOf("\"daily\":");
            if (idxDaily != -1) {
                String blocoDaily = json.substring(idxDaily);
                String[] datas = extrairArrayString(blocoDaily, "time");
                double[] tempMax = extrairArrayDouble(blocoDaily, "temperature_2m_max");
                double[] tempMin = extrairArrayDouble(blocoDaily, "temperature_2m_min");
                double[] tempMean = extrairArrayDouble(blocoDaily, "temperature_2m_mean");
                double[] precip = extrairArrayDouble(blocoDaily, "precipitation_sum");
                double[] vento = extrairArrayDouble(blocoDaily, "wind_speed_10m_max");

                for (int i = 0; i < datas.length; i++) {
                    LocalDate data = LocalDate.parse(datas[i]);
                    resultadoMes.put(data, new ClimaDiario(
                            data,
                            obterValorSeguro(tempMax, i),
                            obterValorSeguro(tempMin, i),
                            obterValorSeguro(tempMean, i),
                            obterValorSeguro(precip, i),
                            obterValorSeguro(vento, i)
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[ClimaService] Erro ao buscar lote mensal (" + anoMes + "): " + e.getMessage());
        }
        return resultadoMes;
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
            buscarClimaPorData(data, coordenada).ifPresent(vinculo::setClimaRegistrado);
        } else {
            vinculo.setClimaRegistrado(obterClimaDiarioAtualComCache6h(coordenada));
        }
    }

    @Override
    public void limparCacheExpirado() {
        cacheClimaAtual6Horas.entrySet().removeIf(e -> !e.getValue().isValido());
        cacheClimaDiarioAtual6Horas.entrySet().removeIf(e -> !e.getValue().isValido());
    }
}
