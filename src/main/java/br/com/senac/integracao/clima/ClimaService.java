package br.com.senac.integracao.clima;

import br.com.senac.integracao.geolocalizacao.Coordenada;

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

public class ClimaService {

    private final HttpClient httpClient;
    // Cache em memória por "ANO-MES|LAT|LON" para evitar chamadas repetidas ao processar grandes lotes passados
    private final Map<String, Map<LocalDate, ClimaDiario>> cacheMensal = new ConcurrentHashMap<>();

    public ClimaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Consulta o clima em tempo real (caso a consulta seja para o momento atual / hoje).
     */
    public String buscarClimaAtual(Coordenada coordenada) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m&timezone=auto",
                coordenada.latitude(), coordenada.longitude());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return "Erro ao consultar API Climática: " + e.getMessage();
        }
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
}
