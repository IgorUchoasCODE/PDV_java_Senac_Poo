package br.com.senac.service;

import br.com.senac.integracao.IntegradorRelatorioFatorExternoService;
import br.com.senac.integracao.RelatorioIntegradoFatorExterno;
import br.com.senac.integracao.calendario.CalendarioService;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.rios.NivelRioService;
import br.com.senac.interfaces.ProvedorFatorExterno;
import br.com.senac.interfaces.TituloVencivel;
import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.nota.VinculoFatorExternoDAO;
import br.com.senac.repository.dao.pessoa.PessoaDAO;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ManageNotas {

    private final EstoqueManage estoqueManage;
    private final GeolocalizacaoService geolocalizacaoService;
    private final List<ProvedorFatorExterno> provedoresExternos = new ArrayList<>();
    private final NotaDAO notaDAO = new NotaDAO();
    private final PessoaDAO pessoaDAO = new PessoaDAO();
    private final VinculoFatorExternoDAO vinculoDAO = new VinculoFatorExternoDAO();
    private final br.com.senac.repository.dao.nota.FatorExternoPersistenciaDAO persistenciaFatores = new br.com.senac.repository.dao.nota.FatorExternoPersistenciaDAO();

    // Cache em memória dos snapshots já salvos no banco (evita SELECT/INSERT repetido na mesma janela de 6h)
    private final Map<String, VinculoFatorExternoNota> cacheSnapshotBanco = new ConcurrentHashMap<>();

    public ManageNotas(EstoqueManage estoqueManage, GeolocalizacaoService geolocalizacaoService) {
        this.estoqueManage = estoqueManage;
        this.geolocalizacaoService = geolocalizacaoService;
        registrarProvedorExterno(new CalendarioService());
        registrarProvedorExterno(new ClimaService());
        registrarProvedorExterno(new NivelRioService());
        registrarProvedorExterno(new MinisterioCulturaService());
        registrarProvedorExterno(new SymplaService());
    }

    public void registrarProvedorExterno(ProvedorFatorExterno provedor) {
        this.provedoresExternos.add(provedor);
    }

    /**
     * Garante que toda NotaVenda sem cliente identificado receba automaticamente o Cliente Padrão
     * ("CONSUMIDOR FINAL (NÃO REGISTRADO)" - CPF 000.000.000-00).
     */
    public void garantirClientePadraoSeNaoRegistrado(NotaVenda notaVenda) {
        if (notaVenda != null && notaVenda.getPessoa() == null) {
            notaVenda.setPessoa(pessoaDAO.obterOuCriarClientePadrao());
        }
    }

    public NotaCompra emitirNotaCompra(NotaCompra notaCompra, List<NotaPagamento> pagamentos) {
        estoqueManage.registrarEntradaCompra(notaCompra);
        vincularPagamentosNaNota(notaCompra, pagamentos);
        vincularFatoresExternosNaNota(notaCompra);
        if (!notaCompra.validarNota()) {
            throw new IllegalStateException("Nota de compra inválida: verifique o fornecedor e os itens.");
        }
        notaCompra.setDataRecebida(LocalDateTime.now());
        return notaDAO.salvarComSincronizacaoFIFO(notaCompra, null);
    }

    /**
     * Emite uma NotaVenda cujos itens já foram vinculados (seja por FIFO ou por escolha manual de lote).
     * Se o cliente não foi informado, atribui automaticamente o Cliente Padrão.
     */
    public NotaVenda emitirNotaVenda(NotaVenda notaVenda, List<NotaPagamento> pagamentos) {
        garantirClientePadraoSeNaoRegistrado(notaVenda);
        List<Item<? extends Produto>> lotesOrigemAlterados = new ArrayList<>();
        for (Item<? extends Produto> itemVenda : notaVenda.getItens()) {
            if (itemVenda.getItemOrigem() != null) {
                lotesOrigemAlterados.add(itemVenda.getItemOrigem());
            }
        }
        vincularPagamentosNaNota(notaVenda, pagamentos);
        vincularFatoresExternosNaNota(notaVenda);
        notaVenda.finalizarVenda();
        return notaDAO.salvarComSincronizacaoFIFO(notaVenda, lotesOrigemAlterados);
    }

    /**
     * Emite uma NotaVenda permitindo escolher especificamente qual lote (idLoteDesejado) será abatido
     * para cada item, mantendo bloqueio transacional e validação de validade/saldo.
     * Se o cliente não foi informado, atribui automaticamente o Cliente Padrão.
     */
    public NotaVenda emitirNotaVendaPorLoteDesejado(
            NotaVenda notaVenda,
            Map<Long, BigDecimal> lotesDesejadosComQuantidade,
            List<NotaPagamento> pagamentos) {

        garantirClientePadraoSeNaoRegistrado(notaVenda);
        List<Item<? extends Produto>> lotesOrigemAlterados = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : lotesDesejadosComQuantidade.entrySet()) {
            List<Item<? extends Produto>> lotesConsumidos = estoqueManage.consumirEstoquePorLoteDesejado(
                    notaVenda, entry.getKey(), entry.getValue());
            lotesOrigemAlterados.addAll(lotesConsumidos);
        }

        vincularPagamentosNaNota(notaVenda, pagamentos);
        vincularFatoresExternosNaNota(notaVenda);
        notaVenda.finalizarVenda();
        return notaDAO.salvarComSincronizacaoFIFO(notaVenda, lotesOrigemAlterados);
    }

    /**
     * Emite uma NotaVenda consumindo os lotes mais antigos via algoritmo FIFO, vincula os pagamentos,
     * reutiliza o snapshot sazonal de 6h/7d e salva tudo em uma única transação no banco.
     * Se o cliente não foi informado, atribui automaticamente o Cliente Padrão.
     */
    public NotaVenda emitirNotaVendaFIFO(
            NotaVenda notaVenda,
            Map<Produto, BigDecimal> itensDesejados,
            List<NotaPagamento> pagamentos) {

        garantirClientePadraoSeNaoRegistrado(notaVenda);
        List<Item<? extends Produto>> lotesOrigemAlterados = new ArrayList<>();
        for (Map.Entry<Produto, BigDecimal> entry : itensDesejados.entrySet()) {
            List<Item<? extends Produto>> lotesConsumidos = estoqueManage.consumirEstoqueFIFO(
                    notaVenda, entry.getKey(), entry.getValue());
            lotesOrigemAlterados.addAll(lotesConsumidos);
        }

        vincularPagamentosNaNota(notaVenda, pagamentos);
        vincularFatoresExternosNaNota(notaVenda);
        notaVenda.finalizarVenda();

        return notaDAO.salvarComSincronizacaoFIFO(notaVenda, lotesOrigemAlterados);
    }

    public NotaDevolucao emitirNotaDevolucao(NotaDevolucao notaDevolucao) {
        if (!notaDevolucao.validarNota()) {
            throw new IllegalStateException("Nota de devolução inválida: os itens devem pertencer à Nota de Venda de origem.");
        }
        List<Item<? extends Produto>> lotesRestaurados = estoqueManage.reintegrarItensDevolucaoFIFO(notaDevolucao);
        vincularFatoresExternosNaNota(notaDevolucao);
        notaDevolucao.setDataRecebida(LocalDateTime.now());
        return notaDAO.salvarComSincronizacaoFIFO(notaDevolucao, lotesRestaurados);
    }

    public NotaPerda emitirNotaPerda(NotaPerda notaPerda) {
        if (!notaPerda.validarNota()) {
            throw new IllegalStateException("Nota de perda inválida: informe ao menos um item perdido.");
        }
        List<Item<? extends Produto>> lotesOrigem = new ArrayList<>();
        for (Item<? extends Produto> item : notaPerda.getItens()) {
            if (item.getItemOrigem() != null) {
                lotesOrigem.add(item.getItemOrigem());
            }
        }
        vincularFatoresExternosNaNota(notaPerda);
        notaPerda.setDataRecebida(LocalDateTime.now());
        return notaDAO.salvarComSincronizacaoFIFO(notaPerda, lotesOrigem);
    }

    public void vincularPagamentosNaNota(Nota nota, List<NotaPagamento> pagamentos) {
        if (pagamentos == null) return;
        for (NotaPagamento p : pagamentos) {
            nota.addPagamento(p);
        }
    }

    public void processarQuitacaoPagamento(NotaPagamento pagamento, LocalDateTime dataQuitacao) {
        if (pagamento instanceof TituloVencivel tv) {
            tv.quitar(dataQuitacao);
        }
    }

    public BigDecimal abaterPerdasEmCompra(NotaCompra novaCompra, List<NotaPerda> perdasElegiveis) {
        if (perdasElegiveis == null) return BigDecimal.ZERO;
        BigDecimal totalAbatido = BigDecimal.ZERO;
        for (NotaPerda perda : perdasElegiveis) {
            if (perda.isElegivelAbatimentoCompra(novaCompra)) {
                novaCompra.adicionarNotaPerda(perda);
                totalAbatido = totalAbatido.add(perda.getValorCustoPerda());
            }
        }
        return totalAbatido;
    }

    /**
     * Busca todas as perdas pós-venda dentro do período de validade relacionadas ao fornecedor e ao produto
     * (ou todos os produtos do fornecedor caso produto seja null).
     */
    public List<NotaPerda> buscarPerdasPosVendaValidadePorFornecedor(Pessoa fornecedor, Produto produto) {
        if (fornecedor == null || fornecedor.getId() == null) {
            return List.of();
        }
        Long idProd = produto != null ? produto.getId() : null;
        return notaDAO.buscarPerdasPosVendaValidadePorFornecedorEProduto(fornecedor.getId(), idProd);
    }

    /**
     * Consulta a View vw_perdas_por_fornecedor diretamente no banco para relatórios e conferência de abatimento.
     */
    public List<Object[]> consultarRelatorioViewPerdasFornecedor(
            Pessoa fornecedor,
            Produto produto,
            boolean apenasPosVendaDentroValidade) {
        if (fornecedor == null || fornecedor.getId() == null) {
            return List.of();
        }
        Long idProd = produto != null ? produto.getId() : null;
        return notaDAO.consultarViewPerdasPorFornecedor(fornecedor.getId(), idProd, apenasPosVendaDentroValidade);
    }

    /**
     * Reutiliza o mesmo VinculoFatorExternoNota para todas as notas geradas dentro da mesma janela de 6h e cidade/UF.
     * Evita gravar linhas duplicadas no banco a cada venda e aproveita o Cache de 6h (Clima/Rios) e 7 dias (Eventos).
     */
    public VinculoFatorExternoNota obterOuCriarVinculoFatorExterno(LocalDateTime dataHoraNota) {
        LocalDateTime ref = dataHoraNota != null ? dataHoraNota : LocalDateTime.now();
        LocalDate data = ref.toLocalDate();
        int bloco6h = ref.getHour() / 6; // 0 (00-05h), 1 (06-11h), 2 (12-17h), 3 (18-23h)

        Coordenada coord = geolocalizacaoService.obterCoordenadasDispositivo()
                .orElseGet(() -> new Coordenada(-3.1190, -60.0217, 100.0, "Manaus", "AM", "Centro", ""));

        String cidade = coord.cidade().isBlank() ? "Manaus" : coord.cidade().trim();
        String uf = coord.uf().isBlank() ? "AM" : coord.uf().trim().toUpperCase(Locale.ROOT);
        String chaveSnapshot = String.format("%s|%s-%s|B%d", data, cidade.toUpperCase(Locale.ROOT), uf, bloco6h);

        return cacheSnapshotBanco.computeIfAbsent(chaveSnapshot, chave ->
                vinculoDAO.buscarPorChaveSnapshot(chave).orElseGet(() -> {
                    VinculoFatorExternoNota novo = new VinculoFatorExternoNota(chave, data, bloco6h, coord);
                    // Cache persistente: eventos já gravados dentro do TTL são reaproveitados sem nova requisição externa
                    boolean eventosEmCache = false;
                    try {
                        eventosEmCache = persistenciaFatores.existeColetaRecente(cidade, uf, br.com.senac.repository.dao.nota.FatorExternoPersistenciaDAO.TTL_EVENTOS_DIAS);
                        if (eventosEmCache) {
                            persistenciaFatores.carregarEventosRecentes(cidade, uf, br.com.senac.repository.dao.nota.FatorExternoPersistenciaDAO.TTL_EVENTOS_DIAS, novo);
                        }
                    } catch (RuntimeException ignorado) {
                        eventosEmCache = false;
                    }
                    for (ProvedorFatorExterno provedor : provedoresExternos) {
                        boolean provedorDeEventos = provedor instanceof SymplaService
                                || provedor instanceof br.com.senac.integracao.eventos.MinisterioCulturaService;
                        if (eventosEmCache && provedorDeEventos) continue;
                        provedor.enriquecerVinculo(data, coord, novo);
                    }
                    VinculoFatorExternoNota salvo = vinculoDAO.salvarOuReutilizar(novo);
                    try {
                        if (eventosEmCache) {
                            persistenciaFatores.vincularEventosRecentes(salvo.getId(), cidade, uf, br.com.senac.repository.dao.nota.FatorExternoPersistenciaDAO.TTL_EVENTOS_DIAS);
                        } else {
                            persistenciaFatores.salvarEVincular(salvo.getId(), novo.getEventosSympla(), novo.getEventosMinC());
                        }
                    } catch (RuntimeException e) {
                        System.err.println("[ManageNotas] Aviso ao persistir eventos do snapshot: " + e.getMessage());
                    }
                    return salvo;
                })
        );
    }

    /**
     * Resolve as notas que o gatilho do banco deixou na fila por não existir snapshot para a janela:
     * faz UMA obtenção por janela (data + bloco de 6h) — nunca uma por nota — e aplica a todas as notas da janela.
     */
    public int resolverFatoresExternosPendentes() {
        int notasAtualizadas = 0;
        for (int[] janela : persistenciaFatores.listarJanelasPendentes()) {
            LocalDate data = LocalDate.ofEpochDay(janela[0]);
            int bloco = janela[1];
            VinculoFatorExternoNota vinculo = obterOuCriarVinculoFatorExterno(data.atTime(bloco * 6, 0));
            notasAtualizadas += persistenciaFatores.resolverJanela(data, bloco, vinculo.getId());
        }
        return notasAtualizadas;
    }

    public VinculoFatorExternoNota vincularFatoresExternosNaNota(Nota nota) {
        VinculoFatorExternoNota vinculo = obterOuCriarVinculoFatorExterno(nota.getDataGerada());
        nota.setVinculoFatorExterno(vinculo);
        return vinculo;
    }

    public String determinarPadraoConsumoPorNota(Nota nota) {
        if (nota.getVinculoFatorExterno() == null) {
            vincularFatoresExternosNaNota(nota);
        }
        return nota.getVinculoFatorExterno().determinarPadraoConsumo();
    }

    public Map<String, BigDecimal> analisarPadraoConsumoPeriodo(List<Nota> notas) {
        Map<String, BigDecimal> acumuladoPorPadrao = new LinkedHashMap<>();
        if (notas == null) return acumuladoPorPadrao;
        for (Nota n : notas) {
            String padrao = determinarPadraoConsumoPorNota(n);
            acumuladoPorPadrao.merge(padrao, n.getValorTotal(), BigDecimal::add);
        }
        return acumuladoPorPadrao;
    }

    /**
     * Retorna o integrador padronizado que consolida relatórios de Clima, Rios, Calendário e Eventos citando cada Nota.
     */
    public RelatorioIntegradoFatorExterno obterIntegradorRelatorioFatorExterno() {
        return new IntegradorRelatorioFatorExternoService(provedoresExternos);
    }

    public String gerarRelatorioIntegradoCitandoNotas(List<? extends Nota> notas) {
        return obterIntegradorRelatorioFatorExterno().gerarRelatorioConsolidadoCitandoNotas(notas);
    }

    public List<Map<String, Object>> estruturarRelatorioIntegradoCitandoNotas(List<? extends Nota> notas) {
        return obterIntegradorRelatorioFatorExterno().estruturarRelatorioIntegradoNotas(notas);
    }
}
