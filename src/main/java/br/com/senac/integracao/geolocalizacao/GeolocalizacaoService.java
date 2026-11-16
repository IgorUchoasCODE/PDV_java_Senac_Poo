package br.com.senac.integracao.geolocalizacao;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeolocalizacaoService {

    private static final Duration TTL_CACHE_COORDENADA = Duration.ofHours(6);
    public static final Coordenada COORDENADA_PADRAO_MANAUS = new Coordenada(
            -3.0950, -60.0250, 25.0, "Manaus", "AM", "Chapada", "Av. Djalma Batista");

    private static final Map<String, Coordenada> CACHE_LOCAIS_EVENTOS = new ConcurrentHashMap<>();
    private static volatile Coordenada coordenadaManualOverride;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private volatile Coordenada cacheCoordenadaAtual;
    private volatile Instant expiraCacheCoordenada = Instant.MIN;

    /**
     * Extrai as coordenadas geográficas reais do dispositivo via Windows Location API
     * e identifica automaticamente a cidade, UF, bairro e rua via Reverse Geocoding (OpenStreetMap).
     * Mantém cache em memória por 6 horas para evitar chamadas repetidas de processo/rede a cada nota emitida.
     */
    public Optional<Coordenada> obterCoordenadasDispositivo() {
        if (cacheCoordenadaAtual != null && Instant.now().isBefore(expiraCacheCoordenada)) {
            return Optional.of(cacheCoordenadaAtual);
        }
        String scriptPowerShell =
                "Add-Type -AssemblyName System.Device; " +
                "$watcher = New-Object System.Device.Location.GeoCoordinateWatcher([System.Device.Location.GeoPositionAccuracy]::High); " +
                "$watcher.Start(); " +
                "$count = 0; " +
                "while ((($watcher.Status -ne 'Ready') -or $watcher.Position.Location.IsUnknown) -and ($watcher.Permission -ne 'Denied') -and ($count -lt 80)) { " +
                "    Start-Sleep -Milliseconds 100; " +
                "    $count++; " +
                "} " +
                "if ($watcher.Permission -eq 'Denied') { " +
                "    Write-Output 'DENIED'; " +
                "} elseif ($watcher.Position.Location.IsUnknown) { " +
                "    Write-Output 'UNKNOWN'; " +
                "} else { " +
                "    $loc = $watcher.Position.Location; " +
                "    $ci = [System.Globalization.CultureInfo]::InvariantCulture; " +
                "    Write-Output (($loc.Latitude.ToString($ci), $loc.Longitude.ToString($ci), $loc.HorizontalAccuracy.ToString($ci)) -join '|'); " +
                "} " +
                "$watcher.Stop();";

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", scriptPowerShell);
            pb.redirectErrorStream(true);
            Process processo = pb.start();

            try (BufferedReader leitor = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                String resultado = leitor.readLine();
                processo.waitFor();

                if (resultado != null && !"UNKNOWN".equals(resultado.trim()) && !"DENIED".equals(resultado.trim())) {
                    String[] dados = resultado.trim().split("\\|");
                    double lat = Double.parseDouble(dados[0]);
                    double lon = Double.parseDouble(dados[1]);
                    double precisao = Double.parseDouble(dados[2]);
                    Coordenada coord = enriquecerComEndereco(lat, lon, precisao);
                    this.cacheCoordenadaAtual = coord;
                    this.expiraCacheCoordenada = Instant.now().plus(TTL_CACHE_COORDENADA);
                    return Optional.of(coord);
                }
            }
        } catch (Exception e) {
            System.err.println("[Geolocalização] Aviso no sensor Windows: " + e.getMessage());
        }

        Optional<Coordenada> fallback = obterFallbackPorIp();
        fallback.ifPresent(c -> {
            this.cacheCoordenadaAtual = c;
            this.expiraCacheCoordenada = Instant.now().plus(TTL_CACHE_COORDENADA);
        });
        return fallback;
    }

    private Optional<Coordenada> obterFallbackPorIp() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/line/?fields=status,lat,lon,city,region"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String[] linhas = resp.body().split("\\R");
            if (linhas.length >= 5 && "success".equals(linhas[0].trim())) {
                double lat = Double.parseDouble(linhas[1].trim());
                double lon = Double.parseDouble(linhas[2].trim());
                return Optional.of(enriquecerComEndereco(lat, lon, 1000.0));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Coordenada enriquecerComEndereco(double lat, double lon, double precisao) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f", lat, lon);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "SazonaliseiPDV/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String json = resp.body();

            String cidade = extrairCampoJson(json, "city");
            if (cidade.isBlank()) {
                cidade = extrairCampoJson(json, "town");
            }
            String isoUf = extrairCampoJson(json, "ISO3166-2-lvl4"); // Ex: BR-AM
            String uf = isoUf.contains("-") ? isoUf.substring(isoUf.indexOf('-') + 1) : "AM";
            String bairro = extrairCampoJson(json, "suburb");
            String rua = extrairCampoJson(json, "road");

            return new Coordenada(lat, lon, precisao,
                    cidade.isBlank() ? "Manaus" : cidade,
                    uf.isBlank() ? "AM" : uf,
                    bairro,
                    rua);
        } catch (Exception e) {
            return new Coordenada(lat, lon, precisao, "Manaus", "AM", "", "");
        }
    }

    private String extrairCampoJson(String json, String campo) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Permite fixar manualmente a coordenada da loja na aba de Configuração de Ambiente.
     */
    public static void definirCoordenadaManual(Coordenada novaCoordenada) {
        coordenadaManualOverride = novaCoordenada;
    }

    /**
     * Retorna a coordenada ativa da loja (manual -> GPS/IP do dispositivo -> padrão Manaus/AM).
     */
    public Coordenada obterCoordenadaLojaAtiva() {
        if (coordenadaManualOverride != null) {
            return coordenadaManualOverride;
        }
        return obterCoordenadasDispositivo().orElse(COORDENADA_PADRAO_MANAUS);
    }

    /**
     * Resolve a coordenada geográfica real de um evento próximo a partir do seu local/endereço/nome,
     * combinando marcos regionais amazônicos, Nominatim OpenStreetMap (com cache) e fallback determinístico.
     */
    public Coordenada resolverCoordenadaEvento(String nomeEvento, String localEvento, Coordenada referenciaLoja) {
        Coordenada base = referenciaLoja != null ? referenciaLoja : COORDENADA_PADRAO_MANAUS;
        String textoBusca = ((localEvento != null ? localEvento : "") + " " + (nomeEvento != null ? nomeEvento : "")).trim();
        if (textoBusca.isEmpty()) {
            return base;
        }
        String chave = textoBusca.toLowerCase(Locale.ROOT);
        Coordenada emCache = CACHE_LOCAIS_EVENTOS.get(chave);
        if (emCache != null) {
            return emCache;
        }

        // 1. Marcos conhecidos de Manaus e região amazônica para resposta instantânea e precisa
        Coordenada marco = buscarMarcoConhecidoManaus(chave);
        if (marco != null) {
            CACHE_LOCAIS_EVENTOS.put(chave, marco);
            return marco;
        }

        // 2. Geocodificação via Nominatim OpenStreetMap quando houver endereço/local explícito
        if (localEvento != null && !localEvento.isBlank() && !localEvento.equalsIgnoreCase("Online")) {
            try {
                String query = localEvento + ", " + (base.cidade().isBlank() ? "Manaus, AM" : base.cidade() + ", " + base.uf());
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "SazonaliseiPDV/1.0")
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                String latStr = extrairCampoJson(body, "lat");
                String lonStr = extrairCampoJson(body, "lon");
                if (!latStr.isBlank() && !lonStr.isBlank()) {
                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);
                    Coordenada encontrada = new Coordenada(lat, lon, 30.0, base.cidade(), base.uf(), localEvento, nomeEvento != null ? nomeEvento : "");
                    CACHE_LOCAIS_EVENTOS.put(chave, encontrada);
                    return encontrada;
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback determinístico no entorno da loja (raio 0.9 km a 7.5 km) para eventos sem endereço postal
        int hash = Math.abs(chave.hashCode());
        double angulo = Math.toRadians(hash % 360);
        double raioGraus = 0.008 + ((hash % 60) * 0.0009); // ~0.9km a ~6.8km
        double lat = base.latitude() + Math.sin(angulo) * raioGraus;
        double lon = base.longitude() + Math.cos(angulo) * raioGraus;
        Coordenada estimada = new Coordenada(lat, lon, 100.0, base.cidade(), base.uf(),
                localEvento != null && !localEvento.isBlank() ? localEvento : "Região Metropolitana",
                nomeEvento != null ? nomeEvento : "Evento Local");
        CACHE_LOCAIS_EVENTOS.put(chave, estimada);
        return estimada;
    }

    private Coordenada buscarMarcoConhecidoManaus(String textoLower) {
        if (textoLower.contains("arena da amazonia") || textoLower.contains("arena da amazônia") || textoLower.contains("flores")) {
            return new Coordenada(-3.0831, -60.0281, 15.0, "Manaus", "AM", "Flores", "Arena da Amazônia");
        }
        if (textoLower.contains("vasco vasques") || textoLower.contains("centro de convencoes") || textoLower.contains("centro de convenções")) {
            return new Coordenada(-3.0845, -60.0265, 15.0, "Manaus", "AM", "Flores", "Centro de Convenções Vasco Vasques");
        }
        if (textoLower.contains("teatro amazonas") || textoLower.contains("largo") || textoLower.contains("sao sebastiao") || textoLower.contains("são sebastião") || textoLower.contains("centro historico") || textoLower.contains("centro histórico")) {
            return new Coordenada(-3.1303, -60.0234, 15.0, "Manaus", "AM", "Centro", "Largo de São Sebastião / Teatro Amazonas");
        }
        if (textoLower.contains("ponta negra")) {
            return new Coordenada(-3.0672, -60.1011, 20.0, "Manaus", "AM", "Ponta Negra", "Anfiteatro da Ponta Negra");
        }
        if (textoLower.contains("studio 5") || textoLower.contains("distrito") || textoLower.contains("japiim")) {
            return new Coordenada(-3.1189, -59.9839, 20.0, "Manaus", "AM", "Distrito Industrial", "Studio 5 Centro de Convenções");
        }
        if (textoLower.contains("adrianopolis") || textoLower.contains("adrianópolis") || textoLower.contains("manauara")) {
            return new Coordenada(-3.1039, -60.0125, 20.0, "Manaus", "AM", "Adrianópolis", "Setor Adrianópolis");
        }
        if (textoLower.contains("vieiralves") || textoLower.contains("nossa senhora das gracas") || textoLower.contains("nossa senhora das graças")) {
            return new Coordenada(-3.1058, -60.0228, 20.0, "Manaus", "AM", "Nossa Sra. das Graças", "Eixo Vieiralves");
        }
        if (textoLower.contains("parque dez") || textoLower.contains("parque 10") || textoLower.contains("CSU")) {
            return new Coordenada(-3.0805, -60.0082, 20.0, "Manaus", "AM", "Parque 10 de Novembro", "Parque Dez");
        }
        if (textoLower.contains("ufam") || textoLower.contains("coroado") || textoLower.contains("inpa") || textoLower.contains("aleixo")) {
            return new Coordenada(-3.0908, -59.9644, 20.0, "Manaus", "AM", "Coroado / Aleixo", "Campus Universitário / Bosque");
        }
        if (textoLower.contains("cidade nova") || textoLower.contains("sumauma") || textoLower.contains("sumaúma") || textoLower.contains("zona norte")) {
            return new Coordenada(-3.0338, -59.9815, 25.0, "Manaus", "AM", "Cidade Nova", "Eixo Cidade Nova");
        }
        if (textoLower.contains("porto") || textoLower.contains("feira da manaus moderna") || textoLower.contains("adolpho lisboa") || textoLower.contains("mercado")) {
            return new Coordenada(-3.1394, -60.0239, 20.0, "Manaus", "AM", "Centro / Porto", "Orla Fluvial do Rio Negro");
        }
        if (textoLower.contains("chapada") || textoLower.contains("djalma batista") || textoLower.contains("senac")) {
            return new Coordenada(-3.0950, -60.0250, 15.0, "Manaus", "AM", "Chapada", "Av. Djalma Batista");
        }
        return null;
    }
}
