package br.com.senac.integracao.rios;

import br.com.senac.integracao.geolocalizacao.Coordenada;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
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

public class NivelRioService {

    private final HttpClient httpClient;
    // Cache em memória por "ANO-MES|LAT|LON" para economizar chamadas na entrada de grandes lotes de registros passados
    private final Map<String, Map<LocalDate, NivelRioDiario>> cacheMensal = new ConcurrentHashMap<>();

    public NivelRioService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Consulta os dados hidrológicos / vazão e nível dos rios atuais e previsão de 7 dias
     * a partir das coordenadas informadas.
     */
    public String consultarNivelRioPorCoordenada(Coordenada coordenada) {
        String url = String.format(Locale.US,
                "https://flood-api.open-meteo.com/v1/flood?latitude=%.6f&longitude=%.6f&daily=river_discharge&forecast_days=7",
                coordenada.latitude(), coordenada.longitude());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return "Erro ao consultar API de Nível dos Rios: " + e.getMessage();
        }
    }

    /**
     * Caso a data de consulta seja inferior a hoje (data passada), busca em uma única chamada
     * todos os níveis/vazões dos rios do mês inteiro e armazena em cache para economizar
     * requisições durante a importação de grandes volumes de registros passados.
     */
    public List<NivelRioDiario> consultarNivelRioDoMesSeDataPassada(LocalDate dataConsulta, Coordenada coordenada) {
        LocalDate hoje = LocalDate.now();
        if (!dataConsulta.isBefore(hoje)) {
            System.out.println("[NivelRioService] A data informada (" + dataConsulta + ") não é inferior a hoje. Use consultarNivelRioPorCoordenada().");
            return Collections.emptyList();
        }
        Map<LocalDate, NivelRioDiario> mapaMes = carregarOuObterCacheDoMes(
                YearMonth.from(dataConsulta), coordenada);
        return new ArrayList<>(mapaMes.values());
    }

    /**
     * Retorna o nível/vazão do rio de uma data específica aproveitando o lote mensal em cache
     * (1 única chamada HTTP atende todas as consultas de dias daquele mesmo mês).
     */
    public Optional<NivelRioDiario> consultarNivelRioPorData(LocalDate dataConsulta, Coordenada coordenada) {
        Map<LocalDate, NivelRioDiario> mapaMes = carregarOuObterCacheDoMes(
                YearMonth.from(dataConsulta), coordenada);
        return Optional.ofNullable(mapaMes.get(dataConsulta));
    }

    private Map<LocalDate, NivelRioDiario> carregarOuObterCacheDoMes(YearMonth anoMes, Coordenada coordenada) {
        String chaveCache = String.format(Locale.US, "%s|%.3f|%.3f",
                anoMes, coordenada.latitude(), coordenada.longitude());

        return cacheMensal.computeIfAbsent(chaveCache, k -> requisitarNivelRioMensalNaApi(anoMes, coordenada));
    }

    private Map<LocalDate, NivelRioDiario> requisitarNivelRioMensalNaApi(YearMonth anoMes, Coordenada coordenada) {
        Map<LocalDate, NivelRioDiario> resultadoMes = new LinkedHashMap<>();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = anoMes.atDay(1);
        LocalDate fimMes = anoMes.atEndOfMonth();

        // Se for o mês atual, limita a consulta histórica até ontem
        if (fimMes.isAfter(hoje.minusDays(1))) {
            fimMes = hoje.minusDays(1);
        }
        if (inicioMes.isAfter(fimMes)) {
            return resultadoMes;
        }

        String url = String.format(Locale.US,
                "https://flood-api.open-meteo.com/v1/flood?latitude=%.6f&longitude=%.6f&start_date=%s&end_date=%s&daily=river_discharge,river_discharge_mean,river_discharge_max,river_discharge_min",
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
                double[] vazao = extrairArrayDouble(blocoDaily, "river_discharge");
                double[] vazaoMedia = extrairArrayDouble(blocoDaily, "river_discharge_mean");
                double[] vazaoMax = extrairArrayDouble(blocoDaily, "river_discharge_max");
                double[] vazaoMin = extrairArrayDouble(blocoDaily, "river_discharge_min");

                for (int i = 0; i < datas.length; i++) {
                    LocalDate data = LocalDate.parse(datas[i]);
                    resultadoMes.put(data, new NivelRioDiario(
                            data,
                            obterValorSeguro(vazao, i),
                            obterValorSeguro(vazaoMedia, i),
                            obterValorSeguro(vazaoMax, i),
                            obterValorSeguro(vazaoMin, i)
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[NivelRioService] Erro ao buscar lote mensal (" + anoMes + "): " + e.getMessage());
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
