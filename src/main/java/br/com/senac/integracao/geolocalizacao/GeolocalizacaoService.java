package br.com.senac.integracao.geolocalizacao;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeolocalizacaoService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Extrai as coordenadas geográficas reais do dispositivo via Windows Location API
     * e identifica automaticamente a cidade, UF, bairro e rua via Reverse Geocoding (OpenStreetMap).
     */
    public Optional<Coordenada> obterCoordenadasDispositivo() {
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
                    return Optional.of(enriquecerComEndereco(lat, lon, precisao));
                }
            }
        } catch (Exception e) {
            System.err.println("[Geolocalização] Aviso no sensor Windows: " + e.getMessage());
        }

        return obterFallbackPorIp();
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
}
