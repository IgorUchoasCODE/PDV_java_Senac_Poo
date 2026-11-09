package br.com.senac.application;

import br.com.senac.integracao.calendario.CalendarioService;
import br.com.senac.integracao.calendario.DiaNaoUtil;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.integracao.rios.NivelRioService;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        GeolocalizacaoService geoService = new GeolocalizacaoService();
        Optional<Coordenada> coordenadaOpt = geoService.obterCoordenadasDispositivo();

        if (coordenadaOpt.isPresent()) {
            Coordenada coord = coordenadaOpt.get();
            System.out.println("--- 1. Geolocalização (Windows Location API) ---");
            System.out.println(coord);

            ClimaService climaService = new ClimaService();
            System.out.println("\n--- 2. API Climática (Atual + Lote Mensal para Datas Passadas) ---");
            System.out.println("Clima Hoje: " + climaService.buscarClimaAtual(coord));

            LocalDate dataPassada = LocalDate.now().minusMonths(1);
            List<ClimaDiario> climaMesPassado = climaService.buscarClimaDoMesSeDataPassada(dataPassada, coord);
            System.out.println("Clima Mês Passado (" + dataPassada.getMonthValue() + "/" + dataPassada.getYear()
                    + " - carregado em 1 única chamada, total de " + climaMesPassado.size() + " dias):");
            climaMesPassado.stream().limit(5).forEach(System.out::println);

            NivelRioService rioService = new NivelRioService();
            System.out.println("\n--- 3. API Nível dos Rios (Atual + Lote Mensal para Datas Passadas) ---");
            System.out.println("Nível Rio Hoje: " + rioService.consultarNivelRioPorCoordenada(coord));

            List<NivelRioDiario> riosMesPassado = rioService.consultarNivelRioDoMesSeDataPassada(dataPassada, coord);
            System.out.println("Nível dos Rios Mês Passado (" + dataPassada.getMonthValue() + "/" + dataPassada.getYear()
                    + " - carregado em 1 única chamada, total de " + riosMesPassado.size() + " dias):");
            riosMesPassado.stream().limit(5).forEach(System.out::println);

            int anoAtual = LocalDate.now().getYear();
            int mesAtual = LocalDate.now().getMonthValue();
            CalendarioService calendarioService = new CalendarioService();

            System.out.println("\n--- 4. Validação de Dia Útil (Hoje) e 5º Dia Útil (" + coord.cidade() + "/" + coord.uf() + ") ---");
            boolean hojeUtil = calendarioService.isHojeDiaUtil(coord);
            System.out.println("Hoje é dia útil? " + (hojeUtil ? "SIM" : "NÃO"));
            System.out.println("Detalhe de hoje: " + calendarioService.obterResumoDiaUtil(LocalDate.now(), coord));
            System.out.println("5º Dia Útil do mês (Comercial/Bancário - Seg a Sex): " + calendarioService.obterQuintoDiaUtilMesAtual(coord));
            System.out.println("5º Dia Útil do mês (Regra CLT - inclui Sábado):      " + calendarioService.obterQuintoDiaUtilCLT(anoAtual, mesAtual, coord));

            System.out.println("\n--- 5. Feriados Nacionais, Estaduais, Municipais e Pontos Facultativos (" + anoAtual + " - " + coord.cidade() + "/" + coord.uf() + ") ---");
            List<DiaNaoUtil> feriadosAno = calendarioService.listarFeriadosEPontosFacultativos(anoAtual, coord);
            for (DiaNaoUtil d : feriadosAno) {
                System.out.println(d);
            }

            System.out.println("\n--- 5. Dias Não Úteis do Mês Atual (" + String.format("%02d/%d", mesAtual, anoAtual) + " - " + coord.cidade() + "/" + coord.uf() + ") ---");
            List<DiaNaoUtil> diasNaoUteisMes = calendarioService.listarDiasNaoUteisDoMes(anoAtual, mesAtual, coord);
            for (DiaNaoUtil d : diasNaoUteisMes) {
                System.out.println(d);
            }

            MinisterioCulturaService minCService = new MinisterioCulturaService();
            System.out.println("\n--- 6. Eventos e Projetos Públicos - Ministério da Cultura (SALIC) em " + coord.cidade() + "/" + coord.uf() + " ---");
            List<ProjetoCulturalMinC> projetosMinC = minCService.buscarEventosCulturaisPorCidade(coord, 10);
            for (ProjetoCulturalMinC p : projetosMinC) {
                System.out.println(p);
                System.out.println("------------------------------------------------------------");
            }

            // Carrega automaticamente o sympla-secret.properties (ignorado no Git) ou usa o mock default (sympla-default.properties)
            SymplaService symplaService = new SymplaService();

            System.out.println("\n--- 7. Eventos Públicos no Sympla em " + coord.cidade() + "/" + coord.uf() + " ---");
            List<EventoSympla> eventosPublicos = symplaService.buscarEventosPublicosPorLocalizacao(coord);
            System.out.println("Total de eventos encontrados na região: " + eventosPublicos.size() + "\n");
            eventosPublicos.stream().limit(5).forEach(evento -> {
                System.out.println(evento);
                System.out.println("------------------------------------------------------------");
            });

            System.out.println("\n--- 8. Eventos Privados / Organizador Sympla (Com Fallback Mocado Default) ---");
            List<EventoSympla> eventosPrivadosOuDefault = symplaService.listarEventosPrivadosOuDefault(coord);
            for (EventoSympla ev : eventosPrivadosOuDefault) {
                System.out.println(ev);
                System.out.println("------------------------------------------------------------");
            }
        }
    }


    public static void iniciar() {
        try {
            ProdutoDAO pDao = new ProdutoDAO();
            UmDAO uDao = new UmDAO();

            // Se o banco (embarcado H2 ou MySQL) estiver vazio, cria os registros iniciais de teste
            if (pDao.listarTodos().isEmpty()) {
                Um unidade = uDao.buscarPorSigla("UN");
                if (unidade == null) {
                    unidade = new Um("UN", "Unidade", "Unidade padrão inteira", BigDecimal.ONE);
                    uDao.salvar(unidade);
                }

                ProdutoFisico mouse = new ProdutoFisico("Mouse Gamer Sem Fio", unidade);
                mouse.setDescricao("Mouse óptico 16000 DPI com receptor USB");
                mouse.setPrecoVenda(new BigDecimal("199.90"));
                pDao.salvar(mouse);

                ProdutoDigital curso = new ProdutoDigital("Curso Completo de Java e JPA", unidade);
                curso.setDescricao("Acesso vitalício às aulas e materiais em PDF");
                curso.setPrecoVenda(new BigDecimal("297.00"));
                curso.adicionarLink(new LinkProdutoDigital("Plataforma de Aulas", "https://senac.com.br/aulas/java-jpa"));
                curso.adicionarLink(new LinkProdutoDigital("Apostila em PDF", "https://senac.com.br/materiais/apostila-jpa.pdf"));
                pDao.salvar(curso);

                ProdutoFisico teclado = new ProdutoFisico("Teclado Mecânico RGB", unidade);
                teclado.setDescricao("Teclado mecânico switch blue ABNT2");
                teclado.setPrecoVenda(new BigDecimal("289.90"));
                pDao.salvar(teclado);

                ProdutoFisico kitGamer = new ProdutoFisico("Kit Gamer Completo (Teclado + 2 Mouses)", unidade);
                kitGamer.setDescricao("Combo promocional contendo teclado mecânico e mouses");
                kitGamer.setPrecoVenda(new BigDecimal("599.90"));
                kitGamer.adicionarItemConjunto(teclado, new BigDecimal("1.000000"));
                kitGamer.adicionarItemConjunto(mouse, new BigDecimal("2.000000"));
                pDao.salvar(kitGamer);
            }

            System.out.println(pDao.buscarPorId(2L));
            System.out.println(pDao.buscarPorId(4L));
        } finally {
            JPAUtil.close();
        }
    }
}
