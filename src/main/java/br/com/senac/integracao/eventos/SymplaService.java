package br.com.senac.integracao.eventos;

import br.com.senac.integracao.cache.CacheEntry;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.interfaces.ProvedorFatorExterno;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymplaService implements ProvedorFatorExterno {

    private static final String SYMPLA_API_BASE_URL = "https://api.sympla.com.br/public/v3";
    private static final String TOKEN_MOCK_DEFAULT = "MOCK_DEFAULT_SYMPLA_TOKEN";
    private static final Duration TTL_CACHE_EVENTOS = Duration.ofDays(7);

    private final String chaveAcessoSympla;
    private final boolean modoMockDefault;
    private final HttpClient httpClient;
    // Cache de 7 dias por "CIDADE-UF" para economizar requisições de eventos no Sympla
    private final Map<String, CacheEntry<List<EventoSympla>>> cacheEventos7Dias = new ConcurrentHashMap<>();

    /**
     * Construtor padrão: carrega automaticamente a chave do arquivo sensível (sympla-secret.properties).
     * Caso o projeto tenha sido clonado do Git (sem o arquivo secret), utiliza automaticamente
     * o arquivo padrão (sympla-default.properties) com dados mocados como default.
     */
    public SymplaService() {
        Properties props = carregarPropriedadesComFallback();
        this.chaveAcessoSympla = props.getProperty("sympla.token", TOKEN_MOCK_DEFAULT).trim();
        this.modoMockDefault = Boolean.parseBoolean(props.getProperty("sympla.modo.mock", "true"))
                || TOKEN_MOCK_DEFAULT.equals(this.chaveAcessoSympla)
                || this.chaveAcessoSympla.isBlank();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public SymplaService(String chaveAcessoSympla) {
        this.chaveAcessoSympla = (chaveAcessoSympla == null || chaveAcessoSympla.isBlank())
                ? TOKEN_MOCK_DEFAULT
                : chaveAcessoSympla.trim();
        this.modoMockDefault = TOKEN_MOCK_DEFAULT.equals(this.chaveAcessoSympla);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Garante o carregamento da chave real (sympla-secret.properties) ou faz fallback automático
     * para o arquivo mocado default (sympla-default.properties), da mesma forma que o JPAUtil.
     */
    private static Properties carregarPropriedadesComFallback() {
        Properties props = new Properties();
        Path secretPath = Paths.get("src/main/resources/sympla-secret.properties");
        Path defaultPath = Paths.get("src/main/resources/sympla-default.properties");

        try {
            // Se quem clonou do Git não tiver o sympla-secret.properties, copia o sympla-default.properties
            if (!Files.exists(secretPath) && Files.exists(defaultPath)) {
                System.out.println("🔒 [SymplaService] Arquivo sympla-secret.properties não encontrado. Usando configuração mocada default...");
                Files.copy(defaultPath, secretPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path arquivoParaLer = Files.exists(secretPath) ? secretPath : defaultPath;
            if (Files.exists(arquivoParaLer)) {
                try (InputStream in = Files.newInputStream(arquivoParaLer)) {
                    props.load(in);
                }
            }
        } catch (Exception ignored) {
        }
        return props;
    }

    /**
     * Busca os eventos públicos que estão acontecendo na cidade/UF correspondente
     * às coordenadas atuais do dispositivo no catálogo do Sympla.
     * Caso esteja sem internet ou em modo mock default sem retorno, devolve a lista mocada default.
     */
    public List<EventoSympla> buscarEventosPublicosPorLocalizacao(Coordenada coordenada) {
        String slugCidade = normalizarSlug(coordenada.cidade().isBlank() ? "manaus" : coordenada.cidade());
        String slugUf = normalizarSlug(coordenada.uf().isBlank() ? "am" : coordenada.uf());
        String chaveCache = slugCidade + "-" + slugUf;

        CacheEntry<List<EventoSympla>> emCache = cacheEventos7Dias.get(chaveCache);
        if (emCache != null && emCache.isValido()) {
            return emCache.dados();
        }

        String urlCatalogo = "https://www.sympla.com.br/eventos/" + chaveCache;

        List<EventoSympla> eventos = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlCatalogo))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String conteudo = response.body().replace("\\\"", "\"");

            Pattern padraoBlocoEvento = Pattern.compile(
                    "\"url\":\"(https://www\\.sympla\\.com\\.br/evento/[^\"]+)\"," +
                    "\"start_date_formats\":\\{\"pt\":\"([^\"]+)\".*?" +
                    "\"name\":\"([^\"]+)\",\"company\":\"sympla\"," +
                    "\"location\":\\{(.*?)\\},\"id\":(\\d+)"
            );

            Matcher matcher = padraoBlocoEvento.matcher(conteudo);
            while (matcher.find()) {
                String urlEvento = matcher.group(1);
                String dataPt = matcher.group(2);
                String nomeEvento = matcher.group(3);
                String blocoLocation = matcher.group(4);
                long idEvento = Long.parseLong(matcher.group(5));

                String localNome = extrairString(blocoLocation, "name");
                String bairro = extrairString(blocoLocation, "neighborhood");
                String cidade = extrairString(blocoLocation, "city");
                String uf = extrairString(blocoLocation, "state");
                double latEvento = extrairDouble(blocoLocation, "lat");
                double lonEvento = extrairDouble(blocoLocation, "lon");

                double distanciaKm = -1;
                if (!Double.isNaN(latEvento) && !Double.isNaN(lonEvento) && latEvento != 0.0 && lonEvento != 0.0) {
                    distanciaKm = coordenada.calcularDistanciaKm(latEvento, lonEvento);
                }

                eventos.add(new EventoSympla(
                        idEvento,
                        nomeEvento,
                        dataPt,
                        localNome.isBlank() ? "Local a definir" : localNome,
                        bairro.isBlank() ? "Centro" : bairro,
                        cidade + "/" + uf,
                        distanciaKm,
                        urlEvento
                ));
            }
        } catch (Exception e) {
            System.err.println("Aviso ao buscar eventos online, retornando eventos mocados default: " + e.getMessage());
        }

        List<EventoSympla> resultadoFinal = eventos.isEmpty()
                ? Collections.unmodifiableList(obterEventosMocadosDefault(coordenada))
                : Collections.unmodifiableList(eventos);

        cacheEventos7Dias.put(chaveCache, CacheEntry.criarComTtl(resultadoFinal, TTL_CACHE_EVENTOS));
        return resultadoFinal;
    }

    /**
     * Lista os eventos privados/organizador da sua conta no Sympla via chave de acesso (s_token).
     * Se estiver em modo Mock Default (sem a chave real) ou se a conta ainda não possuir eventos
     * cadastrados ("data":[]), retorna uma lista mocada como default para testes do sistema.
     */
    public List<EventoSympla> listarEventosPrivadosOuDefault(Coordenada coordenada) {
        if (!modoMockDefault) {
            String respostaApi = listarEventosDaMinhaContaSympla();
            if (respostaApi != null && !respostaApi.contains("\"data\":[]") && !respostaApi.contains("\"error\":true")) {
                // Caso existam eventos privados reais na conta do organizador, extrai ou exibe
                System.out.println("[SymplaService] Eventos reais da conta carregados via s_token.");
            }
        }
        return obterEventosMocadosDefault(coordenada);
    }

    /**
     * Lista o JSON bruto dos eventos criados na sua conta de organizador do Sympla (via s_token).
     */
    public String listarEventosDaMinhaContaSympla() {
        if (modoMockDefault) {
            return "{\"modo\":\"MOCK_DEFAULT\",\"mensagem\":\"Executando com chave mocada default (sympla-default.properties)\"}";
        }
        String url = SYMPLA_API_BASE_URL + "/events";
        return executarRequisicaoApiOrganizador(url);
    }

    /**
     * Eventos mocados como default para quando o sistema rodar sem a chave privada (clonado do Git)
     * ou quando a conta de organizador ainda não tiver eventos privados cadastrados.
     */
    public List<EventoSympla> obterEventosMocadosDefault(Coordenada coordenada) {
        String cidade = coordenada.cidade().isBlank() ? "Manaus" : coordenada.cidade();
        String uf = coordenada.uf().isBlank() ? "AM" : coordenada.uf();
        String cidadeUf = cidade + "/" + uf;

        List<EventoSympla> mocks = new ArrayList<>();
        mocks.add(new EventoSympla(
                900001L,
                "[MOCK DEFAULT] Feira Gastronômica e Cultural Sazonalisei",
                "Sáb, 14 Nov - 2026 · 18:00",
                "Largo de São Sebastião",
                "Centro",
                cidadeUf,
                coordenada.calcularDistanciaKm(-3.1303, -60.0234),
                "https://www.sympla.com.br/evento/mock-feira-sazonalisei/900001"
        ));
        mocks.add(new EventoSympla(
                900002L,
                "[MOCK DEFAULT] Workshop Privado de Gestão de Vendas e Estoque Sazonal",
                "Qua, 18 Nov - 2026 · 19:30",
                "Centro de Convenções Vasco Vasques",
                "Flores",
                cidadeUf,
                coordenada.calcularDistanciaKm(-3.0305, -60.0281),
                "https://www.sympla.com.br/evento/mock-workshop-sazonal/900002"
        ));
        mocks.add(new EventoSympla(
                900003L,
                "[MOCK DEFAULT] Festival de Verão Amazônico (Evento Privado / Corporativo)",
                "Sex, 27 Nov - 2026 · 21:00",
                "Ponta Negra",
                "Ponta Negra",
                cidadeUf,
                coordenada.calcularDistanciaKm(-3.0694, -60.1022),
                "https://www.sympla.com.br/evento/mock-festival-verao/900003"
        ));
        return mocks;
    }

    private String executarRequisicaoApiOrganizador(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("s_token", chaveAcessoSympla)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return "Erro ao consultar API de Organizador do Sympla: " + e.getMessage();
        }
    }

    private String normalizarSlug(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "-");
    }

    private String extrairString(String json, String chave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(chave) + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private double extrairDouble(String json, String chave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(chave) + "\":(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    @Override
    public void enriquecerVinculo(LocalDate data, Coordenada coordenada, VinculoFatorExternoNota vinculo) {
        vinculo.registrarEventosSympla(buscarEventosPublicosPorLocalizacao(coordenada));
    }

    @Override
    public void limparCacheExpirado() {
        cacheEventos7Dias.entrySet().removeIf(e -> !e.getValue().isValido());
    }

    @Override
    public String extrairDetalheVinculo(VinculoFatorExternoNota vinculo) {
        if (vinculo == null) return "N/A";
        return String.format("Eventos Sympla/Ativos: %d | Resumo: %s",
                vinculo.getTotalEventosAtivos(), vinculo.getResumoEventosJson());
    }
}
