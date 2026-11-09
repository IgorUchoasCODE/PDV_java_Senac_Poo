package br.com.senac.integracao.eventos;

import br.com.senac.integracao.geolocalizacao.Coordenada;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinisterioCulturaService {

    private static final String SALIC_API_URL = "https://api.salic.cultura.gov.br/api/v1/projetos";

    private final HttpClient httpClient;

    public MinisterioCulturaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Consulta os eventos e projetos culturais públicos na API oficial do Ministério da Cultura (SALIC)
     * com base na cidade e UF extraídas das coordenadas do dispositivo.
     */
    public List<ProjetoCulturalMinC> buscarEventosCulturaisPorCidade(Coordenada coordenada, int limite) {
        String uf = coordenada.uf().isBlank() ? "AM" : coordenada.uf().trim();
        String municipio = coordenada.cidade().isBlank() ? "Manaus" : coordenada.cidade().trim();

        String url = String.format("%s?UF=%s&municipio=%s&limit=%d&format=json",
                SALIC_API_URL,
                URLEncoder.encode(uf, StandardCharsets.UTF_8),
                URLEncoder.encode(municipio, StandardCharsets.UTF_8),
                limite);

        List<ProjetoCulturalMinC> projetos = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            // Divide cada objeto de projeto retornado em _embedded.projetos
            String[] blocos = json.split("\\{\"PRONAC\":");
            for (int i = 1; i < blocos.length; i++) {
                String bloco = "{\"PRONAC\":" + blocos[i];
                String pronac = extrairCampo(bloco, "PRONAC");
                String nome = decodificarUnicode(extrairCampo(bloco, "nome"));
                String segmento = decodificarUnicode(extrairCampo(bloco, "segmento"));
                String dataInicio = extrairCampo(bloco, "data_inicio");
                String dataTermino = extrairCampo(bloco, "data_termino");
                String situacao = decodificarUnicode(extrairCampo(bloco, "situacao"));
                String mun = decodificarUnicode(extrairCampo(bloco, "municipio"));
                String siglaUf = extrairCampo(bloco, "UF");
                String proponente = decodificarUnicode(extrairCampo(bloco, "proponente"));
                String resumo = decodificarUnicode(extrairCampo(bloco, "resumo")).replaceAll("\\s+", " ").trim();

                projetos.add(new ProjetoCulturalMinC(
                        pronac, nome, segmento, dataInicio, dataTermino, situacao, mun, siglaUf, proponente, resumo
                ));
            }
        } catch (Exception e) {
            System.err.println("Erro ao consultar API do Ministério da Cultura (SALIC): " + e.getMessage());
        }
        return projetos;
    }

    private String extrairCampo(String json, String campo) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private String decodificarUnicode(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(texto);
        int ultimoIndice = 0;
        while (m.find()) {
            sb.append(texto, ultimoIndice, m.start());
            sb.append((char) Integer.parseInt(m.group(1), 16));
            ultimoIndice = m.end();
        }
        sb.append(texto.substring(ultimoIndice));
        return sb.toString().replace("\\/", "/").replace("\\\"", "\"");
    }
}
