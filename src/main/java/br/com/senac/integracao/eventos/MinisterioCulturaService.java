package br.com.senac.integracao.eventos;

import br.com.senac.integracao.cache.CacheEntry;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.interfaces.ProvedorFatorExterno;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinisterioCulturaService implements ProvedorFatorExterno {

    private static final String SALIC_API_URL = "https://api.salic.cultura.gov.br/api/v1/projetos";
    private static final Duration TTL_CACHE_EVENTOS = Duration.ofDays(7);

    private final HttpClient httpClient;
    // Cache de 7 dias por "CIDADE|UF|LIMITE" para evitar consultas repetidas na API SALIC
    private final Map<String, CacheEntry<List<ProjetoCulturalMinC>>> cacheEventos7Dias = new ConcurrentHashMap<>();

    public MinisterioCulturaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Consulta os eventos e projetos culturais públicos na API oficial do Ministério da Cultura (SALIC)
     * com base na cidade e UF extraídas das coordenadas do dispositivo, mantendo Cache de 7 Dias.
     */
    public List<ProjetoCulturalMinC> buscarEventosCulturaisPorCidade(Coordenada coordenada, int limite) {
        String uf = coordenada.uf().isBlank() ? "AM" : coordenada.uf().trim().toUpperCase(Locale.ROOT);
        String municipio = coordenada.cidade().isBlank() ? "Manaus" : coordenada.cidade().trim();
        String chaveCache = municipio.toLowerCase(Locale.ROOT) + "|" + uf + "|" + limite;

        CacheEntry<List<ProjetoCulturalMinC>> emCache = cacheEventos7Dias.get(chaveCache);
        if (emCache != null && emCache.isValido()) {
            return emCache.dados();
        }

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
            if (!projetos.isEmpty()) {
                List<ProjetoCulturalMinC> imutavel = Collections.unmodifiableList(projetos);
                cacheEventos7Dias.put(chaveCache, CacheEntry.criarComTtl(imutavel, TTL_CACHE_EVENTOS));
                return imutavel;
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

    @Override
    public void enriquecerVinculo(LocalDate data, Coordenada coordenada, VinculoFatorExternoNota vinculo) {
        vinculo.registrarEventosMinC(buscarEventosCulturaisPorCidade(coordenada, 10));
    }

    @Override
    public void limparCacheExpirado() {
        cacheEventos7Dias.entrySet().removeIf(e -> !e.getValue().isValido());
    }
}
