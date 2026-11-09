package br.com.senac.integracao.calendario;

import br.com.senac.integracao.geolocalizacao.Coordenada;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CalendarioService {

    private final HttpClient httpClient;

    public CalendarioService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Lista todos os Feriados (Nacionais, Estaduais e Municipais) e Pontos Facultativos
     * do ano com base na cidade/UF da coordenada informada.
     */
    public List<DiaNaoUtil> listarFeriadosEPontosFacultativos(int ano, Coordenada coordenada) {
        Map<LocalDate, DiaNaoUtil> mapaDatas = new LinkedHashMap<>();
        String cidade = coordenada.cidade().isBlank() ? "Manaus" : coordenada.cidade().trim();
        String uf = coordenada.uf().isBlank() ? "AM" : coordenada.uf().trim().toUpperCase(Locale.ROOT);

        // 1. Consulta a BrasilAPI para obter os feriados nacionais e datas móveis (Carnaval, Páscoa, Corpus Christi)
        LocalDate tercaCarnaval = null;
        try {
            String url = "https://brasilapi.com.br/api/feriados/v1/" + ano;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            Pattern padraoItem = Pattern.compile("\\{\"date\":\"(\\d{4}-\\d{2}-\\d{2})\",\"name\":\"([^\"]+)\"");
            Matcher matcher = padraoItem.matcher(resp.body());

            while (matcher.find()) {
                LocalDate data = LocalDate.parse(matcher.group(1));
                String nome = matcher.group(2);

                // Pela Portaria do MGI, Carnaval e Corpus Christi são Pontos Facultativos Nacionais
                if (nome.toLowerCase(Locale.ROOT).contains("carnaval")) {
                    mapaDatas.put(data, new DiaNaoUtil(data, nome, "PONTO FACULTATIVO", "Nacional"));
                    if (data.getDayOfWeek() == DayOfWeek.TUESDAY) {
                        tercaCarnaval = data;
                    }
                } else if (nome.toLowerCase(Locale.ROOT).contains("corpus christi")) {
                    mapaDatas.put(data, new DiaNaoUtil(data, nome, "PONTO FACULTATIVO", "Nacional / Municipal"));
                } else {
                    mapaDatas.put(data, new DiaNaoUtil(data, nome, "FERIADO NACIONAL", "Nacional"));
                }
            }
        } catch (Exception e) {
            System.err.println("Aviso ao consultar BrasilAPI: " + e.getMessage());
        }

        // 2. Pontos Facultativos Nacionais complementares (Quarta-feira de Cinzas, Dia do Servidor, Vésperas)
        if (tercaCarnaval != null) {
            LocalDate quartaCinzas = tercaCarnaval.plusDays(1);
            mapaDatas.putIfAbsent(quartaCinzas, new DiaNaoUtil(
                    quartaCinzas, "Quarta-Feira de Cinzas (até 14h)", "PONTO FACULTATIVO", "Nacional"));
        }
        adicionarSeAusente(mapaDatas, LocalDate.of(ano, 10, 28),
                "Dia do Servidor Público", "PONTO FACULTATIVO", "Nacional / Estadual / Municipal");
        adicionarSeAusente(mapaDatas, LocalDate.of(ano, 12, 24),
                "Véspera de Natal (após 14h)", "PONTO FACULTATIVO", "Nacional");
        adicionarSeAusente(mapaDatas, LocalDate.of(ano, 12, 31),
                "Véspera de Ano Novo (após 14h)", "PONTO FACULTATIVO", "Nacional");

        // 3. Feriados Estaduais e Municipais baseados na UF e Cidade da Coordenada
        adicionarFeriadosLocais(mapaDatas, ano, cidade, uf);

        List<DiaNaoUtil> resultado = new ArrayList<>(mapaDatas.values());
        Collections.sort(resultado);
        return resultado;
    }

    /**
     * Lista todos os Dias Não Úteis de um determinado mês/ano (incluindo Sábados, Domingos,
     * Feriados Nacionais, Estaduais, Municipais e Pontos Facultativos) para a cidade/UF.
     */
    public List<DiaNaoUtil> listarDiasNaoUteisDoMes(int ano, int mes, Coordenada coordenada) {
        List<DiaNaoUtil> feriadosAno = listarFeriadosEPontosFacultativos(ano, coordenada);
        Map<LocalDate, DiaNaoUtil> mapaMes = new LinkedHashMap<>();

        for (DiaNaoUtil f : feriadosAno) {
            if (f.data().getMonthValue() == mes) {
                mapaMes.put(f.data(), f);
            }
        }

        YearMonth anoMes = YearMonth.of(ano, mes);
        for (int dia = 1; dia <= anoMes.lengthOfMonth(); dia++) {
            LocalDate data = anoMes.atDay(dia);
            DayOfWeek dow = data.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                String nomeFimSemana = (dow == DayOfWeek.SATURDAY) ? "Sábado (Fim de Semana)" : "Domingo (Fim de Semana)";
                mapaMes.putIfAbsent(data, new DiaNaoUtil(data, nomeFimSemana, "DIA NÃO ÚTIL", "Fim de Semana"));
            }
        }

        List<DiaNaoUtil> resultado = new ArrayList<>(mapaMes.values());
        Collections.sort(resultado);
        return resultado;
    }

    /**
     * Valida se a data de hoje (LocalDate.now()) é um dia útil na cidade/UF da coordenada.
     */
    public boolean isHojeDiaUtil(Coordenada coordenada) {
        return isDiaUtil(LocalDate.now(), coordenada);
    }

    /**
     * Valida se uma data específica é dia útil (Segunda a Sexta, exceto Feriados e Pontos Facultativos)
     * na cidade/UF da coordenada.
     */
    public boolean isDiaUtil(LocalDate data, Coordenada coordenada) {
        DayOfWeek dow = data.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        List<DiaNaoUtil> feriadosAno = listarFeriadosEPontosFacultativos(data.getYear(), coordenada);
        for (DiaNaoUtil d : feriadosAno) {
            if (d.data().equals(data)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retorna uma descrição detalhada informando se uma data (ex: hoje) é dia útil ou o motivo de não ser.
     */
    public String obterResumoDiaUtil(LocalDate data, Coordenada coordenada) {
        List<DiaNaoUtil> diasNaoUteisMes = listarDiasNaoUteisDoMes(data.getYear(), data.getMonthValue(), coordenada);
        for (DiaNaoUtil d : diasNaoUteisMes) {
            if (d.data().equals(data)) {
                return String.format("NÃO É DIA ÚTIL -> %s", d);
            }
        }
        return String.format("É DIA ÚTIL -> %02d/%02d/%d (%s)",
                data.getDayOfMonth(),
                data.getMonthValue(),
                data.getYear(),
                data.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.of("pt", "BR")));
    }

    /**
     * Retorna o 5º dia útil do mês atual considerando o calendário comercial/bancário
     * (Segunda a Sexta, excluindo Sábados, Domingos e Feriados Nacionais/Estaduais/Municipais).
     */
    public LocalDate obterQuintoDiaUtilMesAtual(Coordenada coordenada) {
        LocalDate hoje = LocalDate.now();
        return obterQuintoDiaUtil(hoje.getYear(), hoje.getMonthValue(), coordenada);
    }

    /**
     * Retorna o 5º dia útil de um mês/ano específico (Segunda a Sexta, sem Sábados, Domingos e Feriados).
     */
    public LocalDate obterQuintoDiaUtil(int ano, int mes, Coordenada coordenada) {
        return obterEnesimoDiaUtil(ano, mes, 5, false, coordenada);
    }

    /**
     * Retorna o 5º dia útil pela regra trabalhista (CLT - pagamento de salários),
     * onde o Sábado é contabilizado como dia útil caso não seja feriado.
     */
    public LocalDate obterQuintoDiaUtilCLT(int ano, int mes, Coordenada coordenada) {
        return obterEnesimoDiaUtil(ano, mes, 5, true, coordenada);
    }

    /**
     * Calcula o N-ésimo dia útil de qualquer mês/ano considerando os feriados da cidade/UF.
     */
    public LocalDate obterEnesimoDiaUtil(int ano, int mes, int n, boolean incluirSabado, Coordenada coordenada) {
        List<DiaNaoUtil> feriadosAno = listarFeriadosEPontosFacultativos(ano, coordenada);
        Map<LocalDate, DiaNaoUtil> mapaFeriadosOficiais = new LinkedHashMap<>();
        for (DiaNaoUtil f : feriadosAno) {
            if (f.categoria().startsWith("FERIADO")) {
                mapaFeriadosOficiais.put(f.data(), f);
            }
        }

        YearMonth anoMes = YearMonth.of(ano, mes);
        int contadorDiasUteis = 0;

        for (int dia = 1; dia <= anoMes.lengthOfMonth(); dia++) {
            LocalDate data = anoMes.atDay(dia);
            DayOfWeek dow = data.getDayOfWeek();

            boolean isDomingo = (dow == DayOfWeek.SUNDAY);
            boolean isSabado = (dow == DayOfWeek.SATURDAY);
            boolean isFeriado = mapaFeriadosOficiais.containsKey(data);

            if (!isDomingo && !isFeriado && (incluirSabado || !isSabado)) {
                contadorDiasUteis++;
                if (contadorDiasUteis == n) {
                    return data;
                }
            }
        }
        return anoMes.atEndOfMonth();
    }

    private void adicionarFeriadosLocais(Map<LocalDate, DiaNaoUtil> mapa, int ano, String cidade, String uf) {
        String locEstadual = "Estadual - " + uf;
        String locMunicipal = "Municipal - " + cidade + "/" + uf;

        if ("AM".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 9, 5),
                    "Elevação do Amazonas à Categoria de Província", "FERIADO ESTADUAL", locEstadual);
            adicionar(mapa, LocalDate.of(ano, 12, 8),
                    "Dia de Nossa Senhora da Conceição (Padroeira)", "FERIADO ESTADUAL", locEstadual);
        } else if ("SP".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 7, 9),
                    "Revolução Constitucionalista de 1932", "FERIADO ESTADUAL", locEstadual);
        } else if ("RJ".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 4, 23),
                    "Dia de São Jorge", "FERIADO ESTADUAL", locEstadual);
        } else if ("PA".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 8, 15),
                    "Adesão do Grão-Pará à Independência", "FERIADO ESTADUAL", locEstadual);
        } else if ("RR".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 10, 5),
                    "Criação do Estado de Roraima", "FERIADO ESTADUAL", locEstadual);
        } else if ("RO".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 1, 4),
                    "Instalação do Estado de Rondônia", "FERIADO ESTADUAL", locEstadual);
        } else if ("AC".equalsIgnoreCase(uf)) {
            adicionar(mapa, LocalDate.of(ano, 6, 15),
                    "Aniversário do Estado do Acre", "FERIADO ESTADUAL", locEstadual);
        }

        if ("Manaus".equalsIgnoreCase(cidade)) {
            adicionar(mapa, LocalDate.of(ano, 10, 24),
                    "Aniversário de Manaus", "FERIADO MUNICIPAL", locMunicipal);
            adicionar(mapa, LocalDate.of(ano, 12, 8),
                    "Dia de Nossa Senhora da Conceição (Padroeira de Manaus)", "FERIADO MUNICIPAL", locMunicipal);
        }
    }

    private void adicionar(Map<LocalDate, DiaNaoUtil> mapa, LocalDate data, String nome, String categoria, String abrangencia) {
        mapa.put(data, new DiaNaoUtil(data, nome, categoria, abrangencia));
    }

    private void adicionarSeAusente(Map<LocalDate, DiaNaoUtil> mapa, LocalDate data, String nome, String categoria, String abrangencia) {
        mapa.putIfAbsent(data, new DiaNaoUtil(data, nome, categoria, abrangencia));
    }
}
