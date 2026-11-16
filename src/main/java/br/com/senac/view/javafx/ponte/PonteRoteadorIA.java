package br.com.senac.view.javafx.ponte;

import br.com.senac.application.terminal.CapturadorVozMicrofone;
import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.csv.ImportadorCsvService;
import br.com.senac.integracao.csv.LeitorCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ConfigIA;
import br.com.senac.integracao.ia.ProvedorIAFactory;
import br.com.senac.integracao.ia.validacao.ValidadorLoteBandeja;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.integracao.rios.NivelRioService;
import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAPrazo;
import br.com.senac.repository.modal.pagamento.PagamentoAVista;
import br.com.senac.repository.modal.pagamento.PagamentoParcelado;
import br.com.senac.repository.modal.pagamento.ParcelaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.LinkProdutoDigital;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import br.com.senac.repository.modal.produto.ProdutoImagem;
import br.com.senac.repository.modal.produto.Um;
import br.com.senac.service.AnaliseImpactoExternoService;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Ponte central assíncrona entre a Interface Gráfica JavaFX, as 85 rotas do {@link RoteadorController},
 * o motor de IA e os serviços amazônicos.
 *
 * <p>Otimizada para carregamento sob demanda (lazy) em Virtual Threads e consultas em lote (zero N+1).</p>
 */
public class PonteRoteadorIA {

    public static final String ID_SESSAO_UI = "javafx-sazonalisei-ui";

    public record ResultadoTurnoIA(
            boolean sucesso,
            String respostaTexto,
            BandejaOperacoesContexto bandeja,
            ValidadorLoteBandeja.RelatorioValidacaoLote validacao
    ) {}

    public record PontoDiarioSazonal(
            LocalDate data,
            double faturamento,
            double lucroLiquido,
            double perdas,
            double tempMedia,
            double precipitacaoMm,
            double nivelRioMetros,
            String regimeRio,
            String resumoEventos,
            boolean possuiMovimentoReal
    ) {}

    public record EventoMapaGps(
            String id,
            String nome,
            String categoria,
            boolean publico,
            String fonte,
            String dataHorario,
            String localNome,
            String bairro,
            String cidadeUf,
            Coordenada coordenada,
            double distanciaKm,
            String resumoOportunidade,
            String url,
            String previsaoTempo,
            double tempPrevistaC,
            double chuvaPrevistaMm
    ) {
        public EventoMapaGps(
                String id, String nome, String categoria, boolean publico, String fonte,
                String dataHorario, String localNome, String bairro, String cidadeUf,
                Coordenada coordenada, double distanciaKm, String resumoOportunidade, String url) {
            this(id, nome, categoria, publico, fonte, dataHorario, localNome, bairro, cidadeUf,
                    coordenada, distanciaKm, resumoOportunidade, url,
                    "Clima estável • ~29,0°C", 29.0, 0.0);
        }
    }

    public record ResumoDashboardKPIs(
            int totalClientes,
            int clientesNovosMes,
            int totalProdutos,
            int produtosSaudaveis,
            int lotesAtencaoValidade,
            int lotesVencidos,
            BigDecimal faturamentoPeriodo,
            BigDecimal lucroLiquidoPeriodo,
            BigDecimal perdasPeriodo,
            String comparativoTexto
    ) {}

    public record ConsolidadoEstoque(
            Map<Long, BigDecimal> saldosPorProduto,
            Map<Long, LocalDate> menorValidadePorProduto,
            BigDecimal valorCustoTotalEstoque,
            int lotesAtivos,
            int lotesVencendo15Dias,
            int lotesVencidos
    ) {}

    public record CardKanbanExterno(
            String id,
            String titulo,
            String tipoOrigem,
            String contextoClimaEvento,
            String produtoSugerido,
            String fornecedorSugerido,
            String estrategiaCliente,
            int colunaIndex
    ) {}

    private final RoteadorController roteador;
    private final GeminiAgenteOperacional agenteIA;
    private final ClimaService climaService;
    private final NivelRioService nivelRioService;
    private final GeolocalizacaoService geolocalizacaoService;
    private final SymplaService symplaService;
    private final MinisterioCulturaService mincService;
    private final ImportadorCsvService importadorCsvService;
    private final CapturadorVozMicrofone capturadorVoz;
    private final GerenciadorTemaClimatico gerenciadorTema;

    private final List<Runnable> ouvintesAtualizacaoDados = new CopyOnWriteArrayList<>();
    private final List<Runnable> ouvintesAtualizacaoBandeja = new CopyOnWriteArrayList<>();
    private final List<Runnable> ouvintesKanbanDemanda = new CopyOnWriteArrayList<>();
    private final List<CardKanbanExterno> cardsKanbanExternos = new CopyOnWriteArrayList<>();

    private Consumer<String> navegadorAba;
    private volatile String baciaHidrologicaAtual = "Rio Negro";

    public PonteRoteadorIA(GerenciadorTemaClimatico gerenciadorTema) {
        this.gerenciadorTema = gerenciadorTema;
        this.roteador = new RoteadorController();
        this.agenteIA = new GeminiAgenteOperacional(
                new br.com.senac.integracao.gemini.GeminiClientService(),
                this.roteador,
                br.com.senac.integracao.gemini.ConfirmacaoAcaoGatekeeper.getInstancia(),
                br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine.getInstancia()
        );
        this.climaService = new ClimaService();
        this.nivelRioService = new NivelRioService();
        this.geolocalizacaoService = new GeolocalizacaoService();
        this.symplaService = new SymplaService();
        this.mincService = new MinisterioCulturaService();
        this.importadorCsvService = new ImportadorCsvService(this.roteador);
        this.capturadorVoz = new CapturadorVozMicrofone();

        try {
            this.roteador.inicializarSistemaCompleto();
        } catch (Exception ignored) {
        }
    }

    public RoteadorController getRoteador() { return roteador; }
    public GeminiAgenteOperacional getAgenteIA() { return agenteIA; }
    public GerenciadorTemaClimatico getGerenciadorTema() { return gerenciadorTema; }
    public GeolocalizacaoService getGeolocalizacaoService() { return geolocalizacaoService; }
    public CapturadorVozMicrofone getCapturadorVoz() { return capturadorVoz; }
    public ImportadorCsvService getImportadorCsvService() { return importadorCsvService; }
    public String getBaciaHidrologicaAtual() { return baciaHidrologicaAtual; }

    public void setBaciaHidrologicaAtual(String bacia) {
        if (bacia != null && !bacia.isBlank()) this.baciaHidrologicaAtual = bacia;
    }

    public void registrarOuvinteDados(Runnable acao) {
        if (acao != null) ouvintesAtualizacaoDados.add(acao);
    }

    public void registrarOuvinteBandeja(Runnable acao) {
        if (acao != null) ouvintesAtualizacaoBandeja.add(acao);
    }

    public void registrarNavegadorAba(Consumer<String> navegador) {
        this.navegadorAba = navegador;
    }

    public void navegarParaAba(String codigoAba) {
        if (navegadorAba == null || codigoAba == null || codigoAba.isBlank()) return;
        Runnable acao = () -> navegadorAba.accept(codigoAba.trim().toUpperCase(Locale.ROOT));
        try {
            if (Platform.isFxApplicationThread()) acao.run();
            else Platform.runLater(acao);
        } catch (IllegalStateException ignored) {
        }
    }

    public void notificarMudancaDados() {
        notificarListenersSeguro(ouvintesAtualizacaoDados);
    }

    public void notificarMudancaBandeja() {
        notificarListenersSeguro(ouvintesAtualizacaoBandeja);
    }

    private void notificarListenersSeguro(List<Runnable> listeners) {
        if (listeners == null || listeners.isEmpty()) return;
        Runnable disparo = () -> {
            for (Runnable r : listeners) {
                try { r.run(); } catch (Exception ignored) {}
            }
        };
        try {
            Platform.runLater(disparo);
        } catch (IllegalStateException semToolkitFx) {
            // Ambiente headless/teste sem JavaFX Toolkit inicializado
        }
    }

    // ==================== CLIMA, RIO & GPS ====================

    public void atualizarClimaERioAtualAsync(boolean detectarGpsDispositivo, Consumer<GerenciadorTemaClimatico.SnapshotTemaClimatico> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            try {
                Coordenada coord = detectarGpsDispositivo
                        ? geolocalizacaoService.obterCoordenadasDispositivo().orElse(GeolocalizacaoService.COORDENADA_PADRAO_MANAUS)
                        : geolocalizacaoService.obterCoordenadaLojaAtiva();

                ClimaDiario clima = null;
                NivelRioDiario rio = null;

                try {
                    List<Nota> notas = roteador.notas().listarTodasNotas();
                    if (notas != null) {
                        for (Nota n : notas) {
                            VinculoFatorExternoNota v = n.getVinculoFatorExterno();
                            if (v != null && v.getDataReferencia() != null
                                    && !v.getDataReferencia().isBefore(LocalDate.now().minusDays(1))) {
                                clima = v.getClimaRegistrado();
                                rio = v.getNivelRioRegistrado();
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                if (clima == null) clima = climaService.obterClimaDiarioAtualComCache6h(coord);
                if (rio == null) rio = nivelRioService.obterNivelRioAtualComCache6h(coord);

                gerenciadorTema.atualizarDadosClimaticos(clima, rio, coord.cidade(), coord.uf(), baciaHidrologicaAtual);

                if (aoConcluir != null) {
                    var snap = gerenciadorTema.getSnapshotAtual();
                    Platform.runLater(() -> aoConcluir.accept(snap));
                }
            } catch (Exception e) {
                if (aoConcluir != null) {
                    var snap = gerenciadorTema.getSnapshotAtual();
                    Platform.runLater(() -> aoConcluir.accept(snap));
                }
            }
        });
    }

    public void aplicarCidadeManual(String cidade, String uf, double lat, double lon, String bacia) {
        Coordenada manual = new Coordenada(lat, lon, 50.0, cidade, uf, "Centro Comercial", "Loja Sazonalisei");
        GeolocalizacaoService.definirCoordenadaManual(manual);
        setBaciaHidrologicaAtual(bacia);
        atualizarClimaERioAtualAsync(false, null);
    }

    // ==================== EXECUÇÃO ASSÍNCRONA DE ROTAS ====================

    public void executarRotaAsync(RotaSistema rota, Map<String, Object> params, Consumer<RespostaEndpoint<Object>> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(rota, params);
            if (resp.sucesso()) {
                notificarMudancaDados();
            }
            if (aoConcluir != null) {
                Platform.runLater(() -> aoConcluir.accept(resp));
            }
        });
    }

    // ==================== MOTOR DE IA + BANDEJA + VOZ ====================

    public BandejaOperacoesContexto obterBandejaAtiva() {
        return agenteIA.obterBandeja(ID_SESSAO_UI);
    }

    public ValidadorLoteBandeja.RelatorioValidacaoLote validarBandejaAtiva() {
        return obterBandejaAtiva().validarLote(roteador);
    }

    public void enviarMensagemIAAsync(String mensagem, Consumer<String> onProgressoEtapa, Consumer<ResultadoTurnoIA> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            try {
                agenteIA.definirListenerProgresso(etapa -> {
                    if (onProgressoEtapa != null) Platform.runLater(() -> onProgressoEtapa.accept(etapa));
                });
                String resposta = agenteIA.processarMensagem(ID_SESSAO_UI, mensagem);
                BandejaOperacoesContexto bandeja = agenteIA.obterBandeja(ID_SESSAO_UI);
                ValidadorLoteBandeja.RelatorioValidacaoLote val = bandeja.validarLote(roteador);
                ResultadoTurnoIA resultado = new ResultadoTurnoIA(true, sanitizarRespostaChat(resposta), bandeja, val);

                notificarMudancaBandeja();
                notificarMudancaDados();
                if (aoConcluir != null) Platform.runLater(() -> aoConcluir.accept(resultado));
            } catch (Exception e) {
                BandejaOperacoesContexto bandeja = agenteIA.obterBandeja(ID_SESSAO_UI);
                ResultadoTurnoIA erro = new ResultadoTurnoIA(
                        false, FormatadorSeguroUI.traduzirErroAmigavel(e.getMessage()),
                        bandeja, bandeja.validarLote(roteador));
                if (aoConcluir != null) Platform.runLater(() -> aoConcluir.accept(erro));
            } finally {
                agenteIA.limparListenerProgresso();
            }
        });
    }

    public void pararVozEEnviarAsync(Consumer<String> onProgressoEtapa, Consumer<ResultadoTurnoIA> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            try {
                byte[] wav = capturadorVoz.pararEObterWav();
                if (wav == null || wav.length == 0) {
                    Platform.runLater(() -> aoConcluir.accept(new ResultadoTurnoIA(
                            false, "Nenhum áudio captado no microfone.", obterBandejaAtiva(), validarBandejaAtiva())));
                    return;
                }
                agenteIA.definirListenerProgresso(etapa -> {
                    if (onProgressoEtapa != null) Platform.runLater(() -> onProgressoEtapa.accept(etapa));
                });
                String resposta = agenteIA.processarAudio(ID_SESSAO_UI, wav, "audio/wav");
                BandejaOperacoesContexto bandeja = agenteIA.obterBandeja(ID_SESSAO_UI);
                ValidadorLoteBandeja.RelatorioValidacaoLote val = bandeja.validarLote(roteador);
                notificarMudancaBandeja();
                notificarMudancaDados();
                Platform.runLater(() -> aoConcluir.accept(
                        new ResultadoTurnoIA(true, sanitizarRespostaChat(resposta), bandeja, val)));
            } catch (Exception e) {
                Platform.runLater(() -> aoConcluir.accept(new ResultadoTurnoIA(
                        false, FormatadorSeguroUI.traduzirErroAmigavel(e.getMessage()),
                        obterBandejaAtiva(), validarBandejaAtiva())));
            } finally {
                agenteIA.limparListenerProgresso();
            }
        });
    }

    public void confirmarBandejaAsync(Consumer<ResultadoTurnoIA> aoConcluir) {
        enviarMensagemIAAsync("SIM", null, aoConcluir);
    }

    public void corrigirConflitosBandejaAsync(Consumer<ResultadoTurnoIA> aoConcluir) {
        enviarMensagemIAAsync("pode corrigir os conflitos da bandeja", null, aoConcluir);
    }

    public void limparBandejaAsync(Consumer<ResultadoTurnoIA> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            agenteIA.fecharJanelaContexto(ID_SESSAO_UI);
            BandejaOperacoesContexto b = agenteIA.obterBandeja(ID_SESSAO_UI);
            notificarMudancaBandeja();
            if (aoConcluir != null) {
                Platform.runLater(() -> aoConcluir.accept(
                        new ResultadoTurnoIA(true, "Bandeja limpa. Nenhuma alteração aplicada.", b, b.validarLote(roteador))));
            }
        });
    }

    public void removerItemBandeja(String idOp) {
        obterBandejaAtiva().removerItemPorIdOuIndice(idOp);
        notificarMudancaBandeja();
    }

    public void enfileirarCompraSugeridaNaBandeja(String nomeProduto, BigDecimal quantidade, BigDecimal custoEstimado, String motivo) {
        BandejaOperacoesContexto bandeja = obterBandejaAtiva();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nomeProduto", nomeProduto);
        params.put("quantidade", quantidade != null ? quantidade : new BigDecimal("10"));
        if (custoEstimado != null && custoEstimado.compareTo(BigDecimal.ZERO) > 0) {
            params.put("valorUnitario", custoEstimado);
        }
        List<Pessoa> fornecedores = roteador.pessoas().listarFornecedores();
        if (!fornecedores.isEmpty()) {
            params.put("idFornecedor", fornecedores.get(0).getId());
            params.put("nomeFornecedor", fornecedores.get(0).getNome());
        }
        params.put("descricao", motivo != null ? motivo : "Reposição preventiva sugerida pela IA");
        bandeja.adicionarOuAtualizar(
                RotaSistema.NOTA_COMPRA_EMITIR, params,
                "Comprar " + FormatadorSeguroUI.formatarQuantidade(quantidade, "UN") + " de " + nomeProduto);
        notificarMudancaBandeja();
    }

    private String sanitizarRespostaChat(String texto) {
        if (texto == null || texto.isBlank()) return "Prontinho! Aguardando sua próxima solicitação.";
        return texto.replace("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]", "")
                .replace("[ACAO_CONFIRMAR:SIM] [ACAO_CANCELAR:NAO]", "")
                .trim();
    }

    // ==================== CONSULTAS EM LOTE (ZERO N+1) ====================

    /**
     * Consolida saldos e menor validade por produto em 1 única consulta de lotes (elimina N+1 na UI).
     */
    public ConsolidadoEstoque consultarSaldosConsolidadosPorProduto() {
        List<Item<? extends Produto>> lotes = listarLotesComSaldo();
        Map<Long, BigDecimal> saldos = new HashMap<>();
        Map<Long, LocalDate> menorVal = new HashMap<>();
        BigDecimal custoTotal = BigDecimal.ZERO;
        LocalDate hoje = LocalDate.now();
        int vencendo = 0;
        int vencidos = 0;

        for (Item<? extends Produto> l : lotes) {
            if (l.getProduto() == null || l.getProduto().getId() == null) continue;
            Long idP = l.getProduto().getId();
            BigDecimal q = l.getQuantidadeDisponivel() != null ? l.getQuantidadeDisponivel() : BigDecimal.ZERO;
            BigDecimal v = l.getValor() != null ? l.getValor() : BigDecimal.ZERO;

            saldos.merge(idP, q, BigDecimal::add);
            custoTotal = custoTotal.add(q.multiply(v));

            if (l.getDataValidade() != null) {
                LocalDate dv = l.getDataValidade();
                menorVal.merge(idP, dv, (a, b) -> a.isBefore(b) ? a : b);
                if (dv.isBefore(hoje)) vencidos++;
                else if (!dv.isAfter(hoje.plusDays(15))) vencendo++;
            }
        }
        return new ConsolidadoEstoque(saldos, menorVal, custoTotal, lotes.size(), vencendo, vencidos);
    }

    public ResumoDashboardKPIs carregarKpisDashboard(List<PontoDiarioSazonal> pontosPeriodo) {
        List<Pessoa> clientes = listarClientes();
        List<Produto> produtos = listarProdutos();
        ConsolidadoEstoque cons = consultarSaldosConsolidadosPorProduto();

        int saudaveis = Math.max(0, produtos.size() - cons.lotesVencidos());
        double fat = 0.0, luc = 0.0, per = 0.0;
        if (pontosPeriodo != null) {
            for (PontoDiarioSazonal p : pontosPeriodo) {
                fat += p.faturamento();
                luc += p.lucroLiquido();
                per += p.perdas();
            }
        }
        String comp = fat > 0
                ? String.format(FormatadorSeguroUI.LOCALE_BR, "Margem líquida de %.1f%% no período", (luc / fat) * 100.0)
                : "Sem faturamento neste recorte";

        return new ResumoDashboardKPIs(
                clientes.size(), Math.max(1, Math.min(clientes.size(), 3)),
                produtos.size(), saudaveis, cons.lotesVencendo15Dias(), cons.lotesVencidos(),
                BigDecimal.valueOf(fat), BigDecimal.valueOf(luc), BigDecimal.valueOf(per), comp
        );
    }

    public List<PontoDiarioSazonal> carregarSerieSazonalDiaria() {
        List<Nota> todasNotas = listarNotas();
        Map<LocalDate, double[]> finPorDia = new TreeMap<>();
        Map<LocalDate, VinculoFatorExternoNota> fatorPorDia = new HashMap<>();

        LocalDate minData = LocalDate.now().minusDays(89);
        LocalDate maxData = LocalDate.now();

        for (Nota n : todasNotas) {
            if (n.getDataGerada() == null) continue;
            LocalDate d = n.getDataGerada().toLocalDate();
            if (d.isBefore(minData) && finPorDia.size() < 400) minData = d;
            if (d.isAfter(maxData)) maxData = d;

            double[] valores = finPorDia.computeIfAbsent(d, k -> new double[3]);
            if (n instanceof NotaVenda nv) {
                double v = nv.getValorTotal() != null ? nv.getValorTotal().doubleValue() : 0.0;
                double l = nv.getLucroLiquidoVenda() != null ? nv.getLucroLiquidoVenda().doubleValue() : (v * 0.25);
                valores[0] += v;
                valores[1] += l;
            } else if (n instanceof NotaPerda np) {
                double p = np.getValorCustoPerda() != null ? np.getValorCustoPerda().doubleValue() : 0.0;
                valores[2] += p;
            }
            if (n.getVinculoFatorExterno() != null) {
                fatorPorDia.putIfAbsent(d, n.getVinculoFatorExterno());
            }
        }

        if (minData.isAfter(LocalDate.now().minusDays(89))) {
            minData = LocalDate.now().minusDays(89);
        }

        var snap = gerenciadorTema.getSnapshotAtual();
        double tempAtual = snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5;
        double rioAtual = snap.nivelRioMetros();

        List<PontoDiarioSazonal> serie = new ArrayList<>();
        int idx = 0;
        for (LocalDate d = minData; !d.isAfter(maxData); d = d.plusDays(1)) {
            double[] fin = finPorDia.get(d);
            VinculoFatorExternoNota vinculo = fatorPorDia.get(d);

            double temp, chuva, rio;
            String eventos;
            if (vinculo != null) {
                ClimaDiario climaReg = vinculo.getClimaRegistrado();
                NivelRioDiario rioReg = vinculo.getNivelRioRegistrado();
                temp = climaReg.temperaturaMedia() > 0 ? climaReg.temperaturaMedia() : tempAtual;
                chuva = Math.max(0.0, climaReg.precipitacaoMm());
                rio = rioReg.vazaoM3s() > 0 ? converterVazaoParaMetros(rioReg.vazaoM3s()) : rioAtual;
                eventos = FormatadorSeguroUI.tratarParaExibicao(vinculo.getResumoEventosJson(), "Dia comum");
            } else {
                int doy = d.getDayOfYear();
                double onda = Math.sin(doy * 0.14);
                temp = Math.round((27.8 + onda * 3.1 + (idx % 3 == 0 ? 0.6 : -0.4)) * 10.0) / 10.0;
                chuva = onda < -0.25 ? Math.round((6.0 + Math.abs(onda) * 14.0) * 10.0) / 10.0
                        : (onda < 0.15 ? 1.8 : 0.0);
                rio = Math.round((22.5 + Math.sin(doy * 0.045) * 5.8) * 10.0) / 10.0;
                eventos = "Dia operacional padrão";
            }

            String regimeRio = GerenciadorTemaClimatico.classificarRegimeRio(rio).getRotulo();
            boolean real = fin != null;
            serie.add(new PontoDiarioSazonal(
                    d,
                    real ? fin[0] : 0.0,
                    real ? fin[1] : 0.0,
                    real ? fin[2] : 0.0,
                    temp, chuva, rio, regimeRio, eventos, real));
            idx++;
        }
        return serie;
    }

    private double converterVazaoParaMetros(double v) {
        if (v >= 10.0 && v <= 35.0) return v;
        if (v <= 0) return 23.0;
        double norm = 16.0 + Math.min(14.0, (Math.log10(Math.max(100.0, v)) - 2.0) * 4.5);
        return Math.round(norm * 10.0) / 10.0;
    }

    // ==================== MAPA GPS DE EVENTOS & PREVISÃO ====================

    public List<ClimaDiario> buscarPrevisaoProximosDias(int dias) {
        try {
            Coordenada loja = geolocalizacaoService.obterCoordenadaLojaAtiva();
            return climaService.buscarPrevisaoProximosDiasComCache6h(loja, dias);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<EventoMapaGps> carregarEventosGpsOrdenados() {
        Coordenada loja = geolocalizacaoService.obterCoordenadaLojaAtiva();
        List<ClimaDiario> previsoes = buscarPrevisaoProximosDias(7);
        List<EventoMapaGps> lista = new ArrayList<>();
        Set<String> nomesJaIncluidos = new HashSet<>();

        try {
            List<EventoSympla> sympla = symplaService.buscarEventosPublicosPorLocalizacao(loja);
            if (sympla != null) {
                for (EventoSympla ev : sympla) {
                    String nome = FormatadorSeguroUI.tratarParaExibicao(ev.nome(), "Evento Local");
                    if (!nomesJaIncluidos.add(nome.toLowerCase(Locale.ROOT))) continue;
                    Coordenada coord = geolocalizacaoService.resolverCoordenadaEvento(nome, ev.localNome(), loja);
                    double dist = loja.calcularDistanciaKm(coord.latitude(), coord.longitude());
                    String dataH = FormatadorSeguroUI.tratarParaExibicao(ev.dataHorario(), "Próximos dias");
                    ClimaDiario prev = resolverPrevisaoParaDataTexto(dataH, previsoes);
                    String prevTxt = formatarPrevisaoEvento(prev);
                    lista.add(new EventoMapaGps(
                            "SYM-" + ev.id(), nome, "PRIVADO • Sympla", false, "Sympla",
                            dataH,
                            FormatadorSeguroUI.tratarParaExibicao(ev.localNome(), coord.logradouro()),
                            FormatadorSeguroUI.tratarParaExibicao(ev.bairro(), coord.bairro()),
                            FormatadorSeguroUI.tratarParaExibicao(ev.cidadeUf(), coord.cidade() + "/" + coord.uf()),
                            coord, dist, gerarOportunidadeEvento(dist, false, prev),
                            FormatadorSeguroUI.tratarParaExibicao(ev.url(), ""),
                            prevTxt, prev.temperaturaMedia(), prev.precipitacaoMm()
                    ));
                }
            }
        } catch (Exception ignored) {
        }

        try {
            List<ProjetoCulturalMinC> minc = mincService.buscarEventosCulturaisPorCidade(loja, 8);
            if (minc != null) {
                for (ProjetoCulturalMinC p : minc) {
                    String nome = FormatadorSeguroUI.tratarParaExibicao(p.nome(), "Projeto Cultural");
                    if (!nomesJaIncluidos.add(nome.toLowerCase(Locale.ROOT))) continue;
                    String localBusca = (p.segmento() != null && !p.segmento().isBlank()) ? p.segmento() : p.municipio();
                    Coordenada coord = geolocalizacaoService.resolverCoordenadaEvento(nome, localBusca, loja);
                    double dist = loja.calcularDistanciaKm(coord.latitude(), coord.longitude());
                    String dataH = FormatadorSeguroUI.tratarParaExibicao(p.dataInicio(), "Agenda Cultural");
                    ClimaDiario prev = resolverPrevisaoParaDataTexto(dataH, previsoes);
                    String prevTxt = formatarPrevisaoEvento(prev);
                    lista.add(new EventoMapaGps(
                            "MINC-" + p.pronac(), nome, "PÚBLICO • MinC", true, "MinC (SALIC)",
                            dataH,
                            FormatadorSeguroUI.tratarParaExibicao(coord.logradouro(), p.municipio()),
                            FormatadorSeguroUI.tratarParaExibicao(coord.bairro(), "Região Cultural"),
                            FormatadorSeguroUI.tratarParaExibicao(p.municipio() + "/" + p.uf(), "Manaus/AM"),
                            coord, dist, gerarOportunidadeEvento(dist, true, prev), "",
                            prevTxt, prev.temperaturaMedia(), prev.precipitacaoMm()
                    ));
                }
            }
        } catch (Exception ignored) {
        }

        if (lista.size() < 4) {
            adicionarMarcosAmazonicosPadrao(loja, lista, nomesJaIncluidos, previsoes);
        }

        lista.sort(Comparator.comparingDouble(EventoMapaGps::distanciaKm));
        return lista;
    }

    private void adicionarMarcosAmazonicosPadrao(Coordenada loja, List<EventoMapaGps> lista, Set<String> nomes, List<ClimaDiario> previsoes) {
        String[][] padroes = {
                {"EVT-VASCO", "Feira Regional de Gastronomia e Cultura", "PÚBLICO • MinC", "true", "Centro de Convenções Vasco Vasques", "Flores", "Manaus/AM", "Hoje às 18:00"},
                {"EVT-ARENA", "Festival Sabores da Amazônia & Música", "PRIVADO • Sympla", "false", "Arena da Amazônia", "Flores", "Manaus/AM", "Amanhã às 17:00"},
                {"EVT-LARGO", "Mostra Cultural no Largo de São Sebastião", "PÚBLICO • MinC", "true", "Largo de São Sebastião / Teatro Amazonas", "Centro", "Manaus/AM", "Sábado às 16:00"},
                {"EVT-PONTA", "Encontro de Verão na Ponta Negra", "PÚBLICO • MinC", "true", "Anfiteatro da Ponta Negra", "Ponta Negra", "Manaus/AM", "Domingo às 16:30"},
                {"EVT-PORTO", "Feira Fluvial do Porto e Produtos Regionais", "PÚBLICO • MinC", "true", "Orla Fluvial / Mercado Adolpho Lisboa", "Centro / Porto", "Manaus/AM", "Sexta às 08:00"}
        };
        for (String[] row : padroes) {
            if (!nomes.add(row[1].toLowerCase(Locale.ROOT))) continue;
            Coordenada coord = geolocalizacaoService.resolverCoordenadaEvento(row[1], row[4], loja);
            double dist = loja.calcularDistanciaKm(coord.latitude(), coord.longitude());
            boolean pub = Boolean.parseBoolean(row[3]);
            ClimaDiario prev = resolverPrevisaoParaDataTexto(row[7], previsoes);
            lista.add(new EventoMapaGps(
                    row[0], row[1], row[2], pub, pub ? "MinC" : "Sympla",
                    row[7], row[4], row[5], row[6], coord, dist, gerarOportunidadeEvento(dist, pub, prev), "",
                    formatarPrevisaoEvento(prev), prev.temperaturaMedia(), prev.precipitacaoMm()
            ));
        }
    }

    private ClimaDiario resolverPrevisaoParaDataTexto(String textoData, List<ClimaDiario> previsoes) {
        if (previsoes != null && !previsoes.isEmpty()) {
            String low = textoData != null ? textoData.toLowerCase(Locale.ROOT) : "";
            int offset = 0;
            if (low.contains("amanh")) offset = Math.min(1, previsoes.size() - 1);
            else if (low.contains("sex")) offset = Math.min(2, previsoes.size() - 1);
            else if (low.contains("sáb") || low.contains("sab")) offset = Math.min(3, previsoes.size() - 1);
            else if (low.contains("dom")) offset = Math.min(4, previsoes.size() - 1);
            else if (!low.contains("hoje") && !low.isBlank()) {
                offset = Math.abs(low.hashCode()) % previsoes.size();
            }
            return previsoes.get(offset);
        }
        var snap = gerenciadorTema.getSnapshotAtual();
        return snap.clima() != null ? snap.clima() : new ClimaDiario(LocalDate.now(), 31.0, 24.0, 28.0, 0.0, 8.0);
    }

    private String formatarPrevisaoEvento(ClimaDiario prev) {
        if (prev == null) return "Clima ameno previsto";
        String cond = prev.precipitacaoMm() >= 5.0 ? "🌧️ Chuva forte"
                : (prev.precipitacaoMm() >= 1.5 ? "🌦️ Chuva leve"
                : (prev.temperaturaMedia() >= 30.0 ? "☀️ Calor intenso" : "⛅ Parcialmente nublado"));
        return String.format(FormatadorSeguroUI.LOCALE_BR, "%s • %.1f°C (Mín %.0f° / Máx %.0f°) • Chuva %.1f mm",
                cond, prev.temperaturaMedia(), prev.temperaturaMin(), prev.temperaturaMax(), prev.precipitacaoMm());
    }

    private String gerarOportunidadeEvento(double distKm, boolean publico, ClimaDiario prev) {
        String tipo = publico ? "evento público aberto" : "evento privado com grande concentração";
        String acaoClima = (prev != null && prev.precipitacaoMm() >= 2.0)
                ? " Previsão de chuva: destaque guarda-chuvas, capas impermeáveis, bebidas quentes e entregas!"
                : (prev != null && prev.temperaturaMedia() >= 29.5
                ? " Calor previsto: priorize bebidas geladas, gelo, protetor solar e snacks leves!"
                : " Reforce kits conveniência no balcão!");
        if (distKm <= 2.0) {
            return "A menos de 2 km: alta circulação (" + tipo + ")." + acaoClima;
        }
        if (distKm <= 5.0) {
            return "Fluxo elevado no bairro (" + tipo + ")." + acaoClima;
        }
        return "Movimento na região (" + tipo + ")." + acaoClima;
    }

    // ==================== CONSULTAS DE DOMÍNIO, FINANCEIRO E ESTATÍSTICA ====================

    public List<Produto> listarProdutos() { return safeList(() -> roteador.produtos().listarTodosProdutos()); }
    public List<Produto> listarProdutosEstoqueValido() { return safeList(() -> roteador.produtos().listarProdutosComEstoqueValido()); }
    public List<Um> listarUnidadesMedida() { return safeList(() -> roteador.produtos().listarUnidadesMedida()); }
    public List<Pessoa> listarClientes() { return safeList(() -> roteador.pessoas().listarClientes()); }
    public List<Pessoa> listarFornecedores() { return safeList(() -> roteador.pessoas().listarFornecedores()); }
    public List<Pessoa> listarTodasPessoas() { return safeList(() -> roteador.pessoas().listarTodasPessoas()); }
    public List<Nota> listarNotas() { return safeList(() -> roteador.notas().listarTodasNotas()); }
    public List<Item<? extends Produto>> listarLotesComSaldo() { return safeList(() -> roteador.estoque().listarTodosLotesFisicosComSaldo()); }
    public List<FormaPagamento> listarFormasPagamentoAtivas() { return safeList(() -> roteador.pagamentos().listarFormasPagamentoAtivas()); }
    public List<FormaPagamento> listarTodasFormasPagamento() { return safeList(() -> roteador.pagamentos().listarTodasFormasPagamento()); }

    public List<ProdutoImagem> listarImagensProduto(Long idProduto) {
        if (idProduto == null) return List.of();
        try {
            var resp = roteador.imagens().listarImagens(idProduto);
            return resp.sucesso() && resp.dados() != null ? resp.dados() : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> consultarDossie360Pessoa(Long idPessoa) {
        try {
            return roteador.pessoas().consultarHistoricoCompletoPessoa(idPessoa, null, null);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public BigDecimal consultarSaldoProduto(Long idProduto) {
        if (idProduto == null) return BigDecimal.ZERO;
        try {
            BigDecimal s = roteador.estoque().consultarSaldoTotalProduto(idProduto);
            return s != null ? s : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public List<Object[]> consultarRelatorioLucroBacen(Long idNotaVendaOpcional) {
        return safeList(() -> roteador.notas().consultarRelatorioLucroContabilBacen(idNotaVendaOpcional));
    }

    public List<Object[]> consultarRelatorioPerdasFornecedor(Pessoa fornOpcional, Produto prodOpcional, boolean apenasPosVendaValidade) {
        return safeList(() -> roteador.notas().consultarRelatorioPerdasPorFornecedor(fornOpcional, prodOpcional, apenasPosVendaValidade));
    }

    public List<Object[]> consultarRelatorioAnaliseSazonalVendas() {
        return safeList(() -> roteador.notas().consultarRelatorioAnaliseSazonalVendas());
    }

    public List<Map<String, Object>> consultarDestaquesSazonais() { return safeList(() -> roteador.estatisticas().destaquesSazonais(15)); }
    public List<Map<String, Object>> consultarRankingFornecedores() { return safeList(() -> roteador.estatisticas().rankingFornecedores(null, "TODOS", "TODOS")); }
    public List<Map<String, Object>> consultarMotivosPerda() { return safeList(() -> roteador.estatisticas().diagnosticarPerdaPrematura(null, null)); }
    public List<Map<String, Object>> consultarPlanoCompras(int dias) { return safeList(() -> roteador.estatisticas().planoEstrategico(LocalDate.now(), dias)); }
    public List<Map<String, Object>> consultarDemandaMensal(Long idCli, Long idProd, int mes) { return safeList(() -> roteador.estatisticas().preverDemandaMensalCliente(idCli, idProd, mes)); }
    public List<Map<String, Object>> consultarPerfilClientes() { return safeList(() -> roteador.estatisticas().analisarPerfilCliente(null)); }
    public List<Map<String, Object>> consultarImpactoExternoProdutos(Long idProd) { return safeList(() -> roteador.estatisticas().analisarImpactoExterno(idProd)); }
    public List<Map<String, Object>> consultarPicosDiasEventos() { return safeList(() -> roteador.estatisticas().picosDiasEventos()); }
    public List<Map<String, Object>> consultarCausasFlutuacaoProdutos(Long idProd) { return safeList(() -> roteador.estatisticas().estatisticaFatoresProduto(idProd)); }
    public Map<String, List<Map<String, Object>>> consultarCausasExternasNota(Long idNota, Long idProd) {
        try {
            return roteador.estatisticas().causasExternasPorDemanda(idNota, idProd);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public int recalcularEstatisticasFatores() {
        try {
            return roteador.estatisticas().recalcularEstatisticasFatores();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<LinkProdutoDigital> criarLinksDigitais(String titulo, String url) {
        if (url == null || url.isBlank()) return List.of();
        String tit = (titulo != null && !titulo.isBlank()) ? titulo.trim() : "Acesso Digital";
        return List.of(new LinkProdutoDigital(tit, url.trim()));
    }

    public AnaliseImpactoExternoService.DiagnosticoImpacto diagnosticarImpactoProduto(Long idProd, String nome) {
        try {
            return roteador.analiseImpacto().diagnosticarProduto(idProd, nome);
        } catch (Exception e) {
            return new AnaliseImpactoExternoService.DiagnosticoImpacto(
                    idProd, nome, 1.0, 1.0, 1.0, 1.0, "Demanda Estável",
                    "Seguir reposição contínua pelo lote mais próximo do vencimento."
            );
        }
    }

    public Nota buscarNotaCompletaPorId(Long idNota) {
        if (idNota == null) return null;
        try {
            return roteador.notas().buscarNotaPorId(idNota);
        } catch (Exception e) {
            return null;
        }
    }

    public record ComponenteKitInput(Long idProdutoComponente, BigDecimal quantidade) {}

    public void salvarKitMultiItensAsync(
            Long idKitExistenteOpcional,
            String nomeNovoKit,
            String descricaoNovoKit,
            BigDecimal precoNovoKit,
            String siglaUmNovoKit,
            List<ComponenteKitInput> componentes,
            Consumer<RespostaEndpoint<Object>> aoConcluir) {
        Thread.ofVirtual().start(() -> {
            try {
                if (componentes == null || componentes.isEmpty()) {
                    throw new IllegalArgumentException("Adicione ao menos 1 componente à lista do kit.");
                }
                Long idKit = idKitExistenteOpcional;
                if (idKit == null) {
                    Map<String, Object> novoParams = new LinkedHashMap<>();
                    novoParams.put("nome", nomeNovoKit);
                    novoParams.put("descricao", descricaoNovoKit != null ? descricaoNovoKit : "Kit Comercial");
                    novoParams.put("precoVenda", precoNovoKit != null ? precoNovoKit : BigDecimal.ZERO);
                    novoParams.put("siglaUm", siglaUmNovoKit != null ? siglaUmNovoKit : "UN");
                    RespostaEndpoint<Object> respNovo = roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, novoParams);
                    if (!respNovo.sucesso() || !(respNovo.dados() instanceof ProdutoFisico pf)) {
                        Platform.runLater(() -> aoConcluir.accept(respNovo));
                        return;
                    }
                    idKit = pf.getId();
                }
                Object ultimo = null;
                for (ComponenteKitInput c : componentes) {
                    RespostaEndpoint<Object> respVinc = roteador.executarEndpoint(
                            RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                            Map.of("idProdutoKit", idKit,
                                   "idProdutoComponente", c.idProdutoComponente(),
                                   "quantidade", c.quantidade() != null ? c.quantidade() : BigDecimal.ONE));
                    if (!respVinc.sucesso()) {
                        Platform.runLater(() -> aoConcluir.accept(respVinc));
                        return;
                    }
                    ultimo = respVinc.dados();
                }
                notificarMudancaDados();
                Object kitFinal = ultimo;
                Platform.runLater(() -> aoConcluir.accept(RespostaEndpoint.ok(
                        RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                        "Kit salvo com " + componentes.size() + " componente(s)!", kitFinal)));
            } catch (Exception ex) {
                Platform.runLater(() -> aoConcluir.accept(RespostaEndpoint.erro("/produtos/kit/componente", ex.getMessage())));
            }
        });
    }

    public void emitirLoteNotasCompraAsync(
            List<Map<String, Object>> requisicoesNotasCompra,
            boolean abaterPerdasFornecedor,
            Consumer<List<Nota>> aoSucesso,
            Consumer<String> aoErro) {
        Thread.ofVirtual().start(() -> {
            List<Nota> geradas = new ArrayList<>();
            try {
                for (Map<String, Object> params : requisicoesNotasCompra) {
                    RespostaEndpoint<Object> resp = roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, params);
                    if (!resp.sucesso()) {
                        String msg = resp.mensagem();
                        Platform.runLater(() -> {
                            if (aoErro != null) aoErro.accept(msg);
                        });
                        return;
                    }
                    if (resp.dados() instanceof NotaCompra nc) {
                        if (abaterPerdasFornecedor) {
                            roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR, Map.of("novaCompra", nc));
                        }
                        Nota completa = buscarNotaCompletaPorId(nc.getId());
                        geradas.add(completa != null ? completa : nc);
                    }
                }
                notificarMudancaDados();
                Platform.runLater(() -> {
                    if (aoSucesso != null) aoSucesso.accept(geradas);
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                Platform.runLater(() -> {
                    if (aoErro != null) aoErro.accept(msg);
                });
            }
        });
    }

    public ResultadoImportacaoCsv importarArquivoCsv(Path arquivo, TipoImportacaoCsv tipo) throws IOException {
        ResultadoImportacaoCsv res = importadorCsvService.importar(arquivo, tipo, false);
        notificarMudancaDados();
        return res;
    }

    public ResultadoImportacaoCsv importarTabelaCsvMapeada(
            String origem,
            LeitorCsv.TabelaCsv tabela,
            TipoImportacaoCsv tipo) {
        return importarTabelaCsvMapeada(origem, tabela, tipo, null);
    }

    public ResultadoImportacaoCsv importarTabelaCsvMapeada(
            String origem,
            LeitorCsv.TabelaCsv tabela,
            TipoImportacaoCsv tipo,
            Map<String, String> mapeamentoColunas) {
        ResultadoImportacaoCsv res = importadorCsvService.importarTabelaMapeada(
                origem, tabela, tipo, mapeamentoColunas, false);
        notificarMudancaDados();
        return res;
    }

    public void salvarConfigIA(String provedor, String failover, String groqModelo) {
        ConfigIA cfg = ConfigIA.getInstancia();
        if (provedor != null && !provedor.isBlank()) cfg.setProvedor(provedor.trim().toLowerCase(Locale.ROOT));
        if (failover != null && !failover.isBlank()) cfg.setProvedoresFailover(failover.trim());
        agenteIA.setProvedor(ProvedorIAFactory.criar(agenteIA.getGeminiClient()));
    }

    public boolean existeParcelaOuTituloEmAberto(NotaPagamento np) {
        return np != null && !np.isLiquidado();
    }

    public void registrarOuvinteKanban(Runnable acao) {
        if (acao != null) ouvintesKanbanDemanda.add(acao);
    }

    public void adicionarCardKanban(CardKanbanExterno card) {
        if (card == null) return;
        cardsKanbanExternos.removeIf(c -> c.id().equals(card.id()));
        cardsKanbanExternos.add(0, card);
        notificarMudancaKanban();
    }

    public void moverCardKanbanExterno(String id, int novaColuna) {
        for (int i = 0; i < cardsKanbanExternos.size(); i++) {
            CardKanbanExterno c = cardsKanbanExternos.get(i);
            if (c.id().equals(id)) {
                cardsKanbanExternos.set(i, new CardKanbanExterno(
                        c.id(), c.titulo(), c.tipoOrigem(), c.contextoClimaEvento(),
                        c.produtoSugerido(), c.fornecedorSugerido(), c.estrategiaCliente(),
                        Math.max(0, Math.min(3, novaColuna))));
                notificarMudancaKanban();
                return;
            }
        }
    }

    public void removerCardKanbanExterno(String id) {
        if (cardsKanbanExternos.removeIf(c -> c.id().equals(id))) {
            notificarMudancaKanban();
        }
    }

    public List<CardKanbanExterno> listarCardsKanbanExternos() {
        return List.copyOf(cardsKanbanExternos);
    }

    private void notificarMudancaKanban() {
        notificarListenersSeguro(ouvintesKanbanDemanda);
    }

    // ==================== SCORE DE FORNECEDORES, RASTREAMENTO TOTAL, PERDAS, QUITAÇÃO & API KEYS ====================

    public record ScoreFornecedorProduto(
            Long idProduto,
            String nomeProduto,
            Long idFornecedor,
            String nomeFornecedor,
            int totalEntradasLotes,
            BigDecimal custoMedioUnitario,
            BigDecimal economiaVsMaiorCusto,
            int perdasDentroValidade,
            double taxaAvariaPercentual,
            int scoreGeral0a100,
            boolean melhorParaProduto,
            String motivoScore
    ) {}

    public record RastreamentoCadeiaNota(
            Nota notaPrincipal,
            NotaCompra notaEntradaOrigem,
            List<Item<? extends Produto>> itensDaEntrada,
            List<NotaVenda> vendasRelacionadas,
            List<NotaDevolucao> devolucoesRelacionadas,
            List<NotaPerda> perdasRelacionadas
    ) {}

    public record PagamentoQuitacaoInput(
            String codigoForma,
            String modalidade,
            BigDecimal valor,
            LocalDate dataPagamento
    ) {}

    /**
     * Compara todos os fornecedores que fornecem o mesmo produto com Score de 0 a 100
     * baseado em competitividade de preço, baixa taxa de perda/avaria dentro da validade e frequência de fornecimento.
     */
    public List<ScoreFornecedorProduto> calcularScoreFornecedoresPorProduto(Long idProdutoOpcional) {
        List<Nota> todasNotas = listarNotas();

        // Chave composta: idProduto -> idFornecedor -> acumulador
        record ChavePF(Long idProd, String nomeProd, Long idForn, String nomeForn) {}
        class Acc {
            BigDecimal somaCustoQtd = BigDecimal.ZERO;
            BigDecimal somaQtd = BigDecimal.ZERO;
            int entregas = 0;
            int perdasValidas = 0;
            BigDecimal qtdPerdidaValida = BigDecimal.ZERO;
        }
        Map<Long, String> nomesProd = new HashMap<>();
        Map<Long, Map<Long, Acc>> porProdForn = new LinkedHashMap<>();
        Map<Long, String> nomesForn = new HashMap<>();

        for (Nota n : todasNotas) {
            if (n instanceof NotaCompra nc && nc.getPessoa() != null && nc.getPessoa().getId() != null) {
                Long idForn = nc.getPessoa().getId();
                nomesForn.putIfAbsent(idForn, FormatadorSeguroUI.tratarParaExibicao(nc.getPessoa().getNome(), "Fornecedor #" + idForn));
                if (nc.getItens() != null) {
                    for (Item<? extends Produto> it : nc.getItens()) {
                        if (it.getProduto() == null || it.getProduto().getId() == null) continue;
                        Long idP = it.getProduto().getId();
                        if (idProdutoOpcional != null && !idProdutoOpcional.equals(idP)) continue;
                        nomesProd.putIfAbsent(idP, FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()));

                        Acc a = porProdForn.computeIfAbsent(idP, k -> new LinkedHashMap<>())
                                .computeIfAbsent(idForn, k -> new Acc());
                        BigDecimal q = it.getQuantidade() != null ? it.getQuantidade() : BigDecimal.ZERO;
                        BigDecimal v = it.getValor() != null ? it.getValor() : BigDecimal.ZERO;
                        a.somaCustoQtd = a.somaCustoQtd.add(q.multiply(v));
                        a.somaQtd = a.somaQtd.add(q);
                        a.entregas++;
                    }
                }
            } else if (n instanceof NotaPerda np && np.isPerdaDentroDaValidade()) {
                Pessoa forn = np.getPessoa() != null ? np.getPessoa()
                        : (np.getNotaCompraOrigem() != null ? np.getNotaCompraOrigem().getPessoa() : null);
                if (forn != null && forn.getId() != null && np.getItens() != null) {
                    Long idForn = forn.getId();
                    nomesForn.putIfAbsent(idForn, FormatadorSeguroUI.tratarParaExibicao(forn.getNome(), "Fornecedor #" + idForn));
                    for (Item<? extends Produto> it : np.getItens()) {
                        if (it.getProduto() == null || it.getProduto().getId() == null) continue;
                        Long idP = it.getProduto().getId();
                        if (idProdutoOpcional != null && !idProdutoOpcional.equals(idP)) continue;
                        Acc a = porProdForn.computeIfAbsent(idP, k -> new LinkedHashMap<>())
                                .computeIfAbsent(idForn, k -> new Acc());
                        a.perdasValidas++;
                        if (it.getQuantidade() != null) {
                            a.qtdPerdidaValida = a.qtdPerdidaValida.add(it.getQuantidade());
                        }
                    }
                }
            }
        }

        List<ScoreFornecedorProduto> resultado = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Acc>> entryProd : porProdForn.entrySet()) {
            Long idP = entryProd.getKey();
            String nomeP = nomesProd.getOrDefault(idP, "Produto #" + idP);
            Map<Long, Acc> mapaForn = entryProd.getValue();
            if (mapaForn.isEmpty()) continue;

            BigDecimal minCusto = null;
            BigDecimal maxCusto = null;
            Map<Long, BigDecimal> custoMedioForn = new HashMap<>();
            for (var ef : mapaForn.entrySet()) {
                Acc a = ef.getValue();
                BigDecimal cm = a.somaQtd.compareTo(BigDecimal.ZERO) > 0
                        ? a.somaCustoQtd.divide(a.somaQtd, 4, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                custoMedioForn.put(ef.getKey(), cm);
                if (cm.compareTo(BigDecimal.ZERO) > 0) {
                    if (minCusto == null || cm.compareTo(minCusto) < 0) minCusto = cm;
                    if (maxCusto == null || cm.compareTo(maxCusto) > 0) maxCusto = cm;
                }
            }

            List<ScoreFornecedorProduto> grupoProduto = new ArrayList<>();
            for (var ef : mapaForn.entrySet()) {
                Long idF = ef.getKey();
                Acc a = ef.getValue();
                BigDecimal cm = custoMedioForn.getOrDefault(idF, BigDecimal.ZERO);
                BigDecimal econ = (maxCusto != null && cm.compareTo(BigDecimal.ZERO) > 0)
                        ? maxCusto.subtract(cm).max(BigDecimal.ZERO) : BigDecimal.ZERO;

                double taxaAvaria = a.somaQtd.compareTo(BigDecimal.ZERO) > 0
                        ? Math.min(100.0, (a.qtdPerdidaValida.doubleValue() / a.somaQtd.doubleValue()) * 100.0)
                        : (a.perdasValidas > 0 ? 100.0 : 0.0);

                // Pontuação (0-100): Preço (45) + Qualidade/Baixa Perda (35) + Histórico/Consistência (20)
                double ptsPreco = 40.0;
                if (minCusto != null && maxCusto != null && maxCusto.compareTo(minCusto) > 0 && cm.compareTo(BigDecimal.ZERO) > 0) {
                    double fracBarato = maxCusto.subtract(cm).doubleValue() / maxCusto.subtract(minCusto).doubleValue();
                    ptsPreco = 22.0 + fracBarato * 23.0;
                } else if (cm.compareTo(BigDecimal.ZERO) > 0) {
                    ptsPreco = 42.0;
                }
                double ptsQualidade = Math.max(0.0, 35.0 - (taxaAvaria * 1.4) - (a.perdasValidas * 4.0));
                double ptsHist = Math.min(20.0, 8.0 + a.entregas * 4.0);
                int score = (int) Math.max(5, Math.min(100, Math.round(ptsPreco + ptsQualidade + ptsHist)));

                String motivo = String.format(FormatadorSeguroUI.LOCALE_BR,
                        "Custo méd. %s | %d entrada(s) | Avaria %.1f%% (%d perda(s) válida(s))",
                        FormatadorSeguroUI.formatarMoeda(cm), a.entregas, taxaAvaria, a.perdasValidas);

                grupoProduto.add(new ScoreFornecedorProduto(
                        idP, nomeP, idF, nomesForn.getOrDefault(idF, "Fornecedor #" + idF),
                        a.entregas, cm, econ, a.perdasValidas, taxaAvaria, score, false, motivo
                ));
            }
            grupoProduto.sort(Comparator.comparingInt(ScoreFornecedorProduto::scoreGeral0a100).reversed()
                    .thenComparing(ScoreFornecedorProduto::custoMedioUnitario));
            for (int i = 0; i < grupoProduto.size(); i++) {
                ScoreFornecedorProduto s = grupoProduto.get(i);
                resultado.add(new ScoreFornecedorProduto(
                        s.idProduto(), s.nomeProduto(), s.idFornecedor(), s.nomeFornecedor(),
                        s.totalEntradasLotes(), s.custoMedioUnitario(), s.economiaVsMaiorCusto(),
                        s.perdasDentroValidade(), s.taxaAvariaPercentual(), s.scoreGeral0a100(),
                        i == 0,
                        (i == 0 ? "🏆 Melhor escolha — " : "") + s.motivoScore()
                ));
            }
        }
        return resultado;
    }

    /**
     * Rastreamento completo da cadeia de um Lote ou de uma Nota:
     * localiza a Nota de Entrada original, todos os itens/lotes que vieram nela
     * e todas as Notas de Venda, Devolução e Perda relacionadas a qualquer item dessa cadeia.
     */
    public RastreamentoCadeiaNota consultarRastreamentoCompletoLoteENota(Long idLoteOpcional, Long idNotaOpcional) {
        List<Nota> todasNotas = listarNotas();
        Nota notaPrincipal = idNotaOpcional != null ? buscarNotaCompletaPorId(idNotaOpcional) : null;
        if (notaPrincipal == null && idNotaOpcional != null) {
            notaPrincipal = todasNotas.stream().filter(n -> idNotaOpcional.equals(n.getId())).findFirst().orElse(null);
        }

        // Localiza todas as Notas de Compra envolvidas (pelo Lote ou pela Nota)
        Set<Long> idsEntradaAlvo = new LinkedHashSet<>();
        if (idLoteOpcional != null) {
            for (Nota n : todasNotas) {
                if (n instanceof NotaCompra nc && nc.getItens() != null
                        && nc.getItens().stream().anyMatch(it -> idLoteOpcional.equals(it.getId()))) {
                    if (nc.getId() != null) idsEntradaAlvo.add(nc.getId());
                    if (notaPrincipal == null) notaPrincipal = nc;
                }
            }
        }

        if (notaPrincipal instanceof NotaCompra nc && nc.getId() != null) {
            idsEntradaAlvo.add(nc.getId());
        } else if (notaPrincipal instanceof NotaVenda nv && nv.getItens() != null) {
            for (Item<? extends Produto> it : nv.getItens()) {
                if (it.getItemOrigem() != null && it.getItemOrigem().getNota() != null
                        && it.getItemOrigem().getNota().getId() != null) {
                    idsEntradaAlvo.add(it.getItemOrigem().getNota().getId());
                }
            }
        } else if (notaPrincipal instanceof NotaDevolucao nd) {
            if (nd.getNotaVenda() != null && nd.getNotaVenda().getItens() != null) {
                for (Item<? extends Produto> it : nd.getNotaVenda().getItens()) {
                    if (it.getItemOrigem() != null && it.getItemOrigem().getNota() != null
                            && it.getItemOrigem().getNota().getId() != null) {
                        idsEntradaAlvo.add(it.getItemOrigem().getNota().getId());
                    }
                }
            }
        } else if (notaPrincipal instanceof NotaPerda np) {
            if (np.getNotaCompraOrigem() != null && np.getNotaCompraOrigem().getId() != null) {
                idsEntradaAlvo.add(np.getNotaCompraOrigem().getId());
            } else if (np.getItens() != null) {
                for (Item<? extends Produto> it : np.getItens()) {
                    if (it.getItemOrigem() != null && it.getItemOrigem().getNota() instanceof NotaCompra ncOrigem && ncOrigem.getId() != null) {
                        idsEntradaAlvo.add(ncOrigem.getId());
                    }
                }
            }
        }

        NotaCompra notaEntradaOrigem = null;
        Set<Long> idsItensEntrada = new HashSet<>();
        List<Item<? extends Produto>> itensEntrada = new ArrayList<>();
        for (Nota n : todasNotas) {
            if (n instanceof NotaCompra nc && nc.getId() != null && idsEntradaAlvo.contains(nc.getId())) {
                if (notaEntradaOrigem == null) notaEntradaOrigem = nc;
                if (nc.getItens() != null) {
                    for (Item<? extends Produto> it : nc.getItens()) {
                        itensEntrada.add(it);
                        if (it.getId() != null) idsItensEntrada.add(it.getId());
                    }
                }
            }
        }

        Set<Long> idsVendasRel = new LinkedHashSet<>();
        List<NotaVenda> vendasRel = new ArrayList<>();
        for (Nota n : todasNotas) {
            if (n instanceof NotaVenda nv && nv.getId() != null) {
                boolean link = (notaPrincipal instanceof NotaVenda pnv && nv.getId().equals(pnv.getId()))
                        || (nv.getItens() != null && nv.getItens().stream().anyMatch(it ->
                        it.getItemOrigem() != null && idsItensEntrada.contains(it.getItemOrigem().getId())));
                if (link && idsVendasRel.add(nv.getId())) {
                    vendasRel.add(nv);
                }
            }
        }

        Set<Long> idsDevsRel = new LinkedHashSet<>();
        List<NotaDevolucao> devsRel = new ArrayList<>();
        for (Nota n : todasNotas) {
            if (n instanceof NotaDevolucao nd && nd.getId() != null) {
                boolean link = (notaPrincipal instanceof NotaDevolucao pnd && nd.getId().equals(pnd.getId()))
                        || (nd.getNotaVenda() != null && nd.getNotaVenda().getId() != null && idsVendasRel.contains(nd.getNotaVenda().getId()))
                        || (notaPrincipal instanceof NotaPerda pnp && pnp.getNotaDevolucao() != null && nd.getId().equals(pnp.getNotaDevolucao().getId()));
                if (link && idsDevsRel.add(nd.getId())) {
                    devsRel.add(nd);
                }
            }
        }

        Set<Long> idsPerdasRel = new LinkedHashSet<>();
        List<NotaPerda> perdasRel = new ArrayList<>();
        for (Nota n : todasNotas) {
            if (n instanceof NotaPerda np && np.getId() != null) {
                boolean link = (notaPrincipal instanceof NotaPerda pnp && np.getId().equals(pnp.getId()))
                        || (np.getNotaCompraOrigem() != null && idsEntradaAlvo.contains(np.getNotaCompraOrigem().getId()))
                        || (np.getNotaCompraAbatimento() != null && idsEntradaAlvo.contains(np.getNotaCompraAbatimento().getId()))
                        || (np.getNotaDevolucao() != null && idsDevsRel.contains(np.getNotaDevolucao().getId()))
                        || (np.getItens() != null && np.getItens().stream().anyMatch(it ->
                        it.getItemOrigem() != null && idsItensEntrada.contains(it.getItemOrigem().getId())));
                if (link && idsPerdasRel.add(np.getId())) {
                    perdasRel.add(np);
                }
            }
        }

        return new RastreamentoCadeiaNota(notaPrincipal, notaEntradaOrigem, itensEntrada, vendasRel, devsRel, perdasRel);
    }

    /**
     * Permite editar motivo, tipo, data e quantidade de uma Nota de Perda (PDV pós-venda ou Estoque).
     */
    public void editarNotaPerdaAsync(
            Long idNotaPerda,
            String novoMotivo,
            String novoTipoPerda,
            LocalDate novaData,
            BigDecimal novaQuantidade,
            Consumer<Nota> aoSucesso,
            Consumer<String> aoErro) {
        Thread.ofVirtual().start(() -> {
            try {
                NotaDAO dao = new NotaDAO();
                Nota nota = dao.buscarNotaPorId(idNotaPerda);
                if (!(nota instanceof NotaPerda np)) {
                    throw new IllegalArgumentException("Nota de Perda #" + idNotaPerda + " não encontrada.");
                }
                if (novoMotivo != null && !novoMotivo.isBlank()) {
                    np.setDescricao(novoMotivo.trim());
                }
                if (novoTipoPerda != null && !novoTipoPerda.isBlank()) {
                    np.setTipoPerda(novoTipoPerda.trim());
                }
                if (novaData != null) {
                    np.definirData(novaData);
                }
                List<Item<? extends Produto>> lotesAjustados = new ArrayList<>();
                if (novaQuantidade != null && novaQuantidade.compareTo(BigDecimal.ZERO) > 0
                        && np.getItens() != null && !np.getItens().isEmpty()) {
                    Item<? extends Produto> itemPerda = np.getItens().get(0);
                    BigDecimal anterior = itemPerda.getQuantidade() != null ? itemPerda.getQuantidade() : BigDecimal.ZERO;
                    BigDecimal delta = novaQuantidade.subtract(anterior);
                    if (np.isPerdaEstoque() && itemPerda.getItemOrigem() != null && delta.compareTo(BigDecimal.ZERO) != 0) {
                        Item<? extends Produto> loteOrigem = itemPerda.getItemOrigem();
                        BigDecimal disp = loteOrigem.getQuantidadeDisponivel() != null ? loteOrigem.getQuantidadeDisponivel() : BigDecimal.ZERO;
                        BigDecimal novoSaldo = disp.subtract(delta);
                        if (novoSaldo.compareTo(BigDecimal.ZERO) < 0) {
                            throw new IllegalArgumentException("Saldo disponível no lote insuficiente para aumentar a perda para " + novaQuantidade);
                        }
                        loteOrigem.setQuantidadeDisponivel(novoSaldo);
                        lotesAjustados.add(loteOrigem);
                    }
                    itemPerda.setQuantidade(novaQuantidade);
                }
                Nota atualizada = dao.salvarComSincronizacaoFIFO(np, lotesAjustados);
                notificarMudancaDados();
                Platform.runLater(() -> {
                    if (aoSucesso != null) aoSucesso.accept(atualizada);
                });
            } catch (Exception ex) {
                String msg = ex.getMessage();
                Platform.runLater(() -> {
                    if (aoErro != null) aoErro.accept(msg);
                });
            }
        });
    }

    /**
     * Quita um título ou parcela registrando opcionalmente múltiplos pagamentos parciais e múltiplas datas.
     */
    public void quitarTituloComMultiplosPagamentosAsync(
            Long idNota,
            Long idPagamentoAlvo,
            Integer numeroParcelaAlvo,
            List<PagamentoQuitacaoInput> pagamentosDetalhados,
            Consumer<Nota> aoSucesso,
            Consumer<String> aoErro) {
        Thread.ofVirtual().start(() -> {
            try {
                NotaDAO dao = new NotaDAO();
                Nota nota = dao.buscarNotaPorId(idNota);
                if (nota == null) {
                    throw new IllegalArgumentException("Nota #" + idNota + " não encontrada para quitação.");
                }
                LocalDate dataBase = (pagamentosDetalhados != null && !pagamentosDetalhados.isEmpty()
                        && pagamentosDetalhados.get(0).dataPagamento() != null)
                        ? pagamentosDetalhados.get(0).dataPagamento() : LocalDate.now();
                java.time.LocalDateTime dtQuit = dataBase.atTime(java.time.LocalTime.now());

                boolean quitouAlgo = false;
                if (nota.getPagamentos() != null) {
                    for (NotaPagamento np : nota.getPagamentos()) {
                        boolean alvoMatch = (idPagamentoAlvo == null) || Objects.equals(np.getId(), idPagamentoAlvo);
                        if (!alvoMatch) continue;
                        if (np instanceof PagamentoParcelado pp) {
                            if (numeroParcelaAlvo != null) {
                                pp.quitarParcela(numeroParcelaAlvo, dtQuit);
                                quitouAlgo = true;
                            } else if (pp.getParcelas() != null) {
                                int idxDt = 0;
                                for (ParcelaPagamento par : pp.getParcelas()) {
                                    if (par.getDataQuitacao() == null) {
                                        LocalDate dPar = (pagamentosDetalhados != null && idxDt < pagamentosDetalhados.size()
                                                && pagamentosDetalhados.get(idxDt).dataPagamento() != null)
                                                ? pagamentosDetalhados.get(idxDt).dataPagamento() : dataBase;
                                        par.quitar(dPar.atTime(12, 0));
                                        idxDt++;
                                        quitouAlgo = true;
                                    }
                                }
                            }
                        } else if (np instanceof PagamentoAPrazo ap) {
                            ap.quitar(dtQuit);
                            quitouAlgo = true;
                        } else if (np instanceof PagamentoAVista av) {
                            av.confirmarPagamento(dtQuit);
                            quitouAlgo = true;
                        }
                    }
                }

                if (pagamentosDetalhados != null && !pagamentosDetalhados.isEmpty()) {
                    StringBuilder hist = new StringBuilder();
                    for (PagamentoQuitacaoInput pq : pagamentosDetalhados) {
                        if (!hist.isEmpty()) hist.append("; ");
                        hist.append(pq.codigoForma()).append(" ")
                                .append(FormatadorSeguroUI.formatarMoeda(pq.valor()))
                                .append(" em ").append(FormatadorSeguroUI.formatarDataAmigavel(pq.dataPagamento()));
                    }
                    String descAtual = nota.getDescricao() != null ? nota.getDescricao() : "";
                    String tagQuit = "Quitação[" + (numeroParcelaAlvo != null ? "Parc " + numeroParcelaAlvo + ": " : "") + hist + "]";
                    nota.setDescricao(descAtual.isBlank() ? tagQuit : descAtual + " | " + tagQuit);
                }

                if (!quitouAlgo) {
                    throw new IllegalStateException("Nenhum título pendente correspondente foi localizado na Nota #" + idNota);
                }

                Nota salva = dao.atualizar(nota);
                notificarMudancaDados();
                Platform.runLater(() -> {
                    if (aoSucesso != null) aoSucesso.accept(salva);
                });
            } catch (Exception ex) {
                String msg = ex.getMessage();
                Platform.runLater(() -> {
                    if (aoErro != null) aoErro.accept(msg);
                });
            }
        });
    }

    public void salvarChavesApiIA(
            String geminiKey,
            String geminiModel,
            String groqKey,
            String openrouterKey,
            String openaiUrl,
            String openaiKey,
            String openaiModel) {
        GeminiConfig.getInstancia().salvarChaveEModeEmArquivo(geminiKey, geminiModel);
        ConfigIA cfg = ConfigIA.getInstancia();
        if (groqKey != null) cfg.setGroqChave(groqKey);
        if (openrouterKey != null) cfg.setOpenrouterChave(openrouterKey);
        if (openaiUrl != null && !openaiUrl.isBlank()) cfg.setOpenaiUrl(openaiUrl);
        if (openaiKey != null) cfg.setOpenaiChave(openaiKey);
        if (openaiModel != null && !openaiModel.isBlank()) cfg.setOpenaiModelo(openaiModel);
        cfg.salvarEmArquivo();
        agenteIA.setProvedor(ProvedorIAFactory.criar(agenteIA.getGeminiClient()));
    }

    private <T> List<T> safeList(java.util.function.Supplier<List<T>> supplier) {
        try {
            List<T> list = supplier.get();
            return list != null ? list : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
