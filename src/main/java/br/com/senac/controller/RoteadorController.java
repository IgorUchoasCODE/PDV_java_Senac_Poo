package br.com.senac.controller;

import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.LinkProdutoDigital;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.service.EstoqueManage;
import br.com.senac.service.InicializadorSistemaService;
import br.com.senac.service.ManageNotas;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fachada e Roteador Central de Endpoints que atende simultaneamente:
 * 1. Interface Gráfica JavaFX (via controllers tipados ou 'executarEndpoint')
 * 2. Interface Inline / CLI (via 'executarComandoInline')
 * 3. Integração com IA Gemini / Function Calling (via 'exportarDeclaracoesFerramentasGeminiJson' e 'executarEndpoint')
 */
public class RoteadorController {

    private final InicializadorSistemaService inicializadorService;
    private final EstoqueManage estoqueManage;
    private final ManageNotas manageNotas;

    private final ProdutoController produtoController;
    private final PessoaController pessoaController;
    private final PagamentoController pagamentoController;
    private final EstoqueController estoqueController;
    private final NotaController notaController;
    private final EstatisticaController estatisticaController = new EstatisticaController();
    private final ImagemProdutoController imagemProdutoController = new ImagemProdutoController();
    private final Map<String, RotaSistema> mapaRotasPorCaminho = new LinkedHashMap<>();
    private final Map<String, RotaSistema> mapaRotasPorToolGemini = new LinkedHashMap<>();

    public RoteadorController() {
        this.inicializadorService = new InicializadorSistemaService();
        this.estoqueManage = new EstoqueManage();
        this.manageNotas = new ManageNotas(estoqueManage, new GeolocalizacaoService());

        this.produtoController = new ProdutoController(estoqueManage);
        this.pessoaController = new PessoaController();
        this.pagamentoController = new PagamentoController(manageNotas);
        this.estoqueController = new EstoqueController(estoqueManage, manageNotas);
        this.notaController = new NotaController(manageNotas);

        for (RotaSistema rota : RotaSistema.values()) {
            mapaRotasPorCaminho.put(rota.getCaminhoRota(), rota);
            mapaRotasPorToolGemini.put(rota.getNomeFuncaoGemini(), rota);
            mapaRotasPorToolGemini.put(rota.name(), rota);
        }
    }

    public void inicializarSistemaCompleto() {
        inicializadorService.executarScriptInicializacao();
    }

    public Map<String, RotaSistema> listarMapaRotasDisponiveis() {
        return Collections.unmodifiableMap(mapaRotasPorCaminho);
    }

    public List<RotaSistema> listarRotasPorModulo(String modulo) {
        return Arrays.stream(RotaSistema.values())
                .filter(r -> r.getModulo().equalsIgnoreCase(modulo))
                .collect(Collectors.toList());
    }

    public RotaSistema resolverRota(String caminhoOuNomeFuncaoGemini) {
        if (caminhoOuNomeFuncaoGemini == null) return null;
        String chave = caminhoOuNomeFuncaoGemini.trim();
        RotaSistema rota = mapaRotasPorCaminho.get(chave);
        if (rota != null) return rota;
        rota = mapaRotasPorToolGemini.get(chave);
        if (rota != null) return rota;
        return mapaRotasPorToolGemini.get(chave.toLowerCase(Locale.ROOT));
    }

    /**
     * Executa qualquer uma das 42 rotas a partir do caminho da rota ("/produtos/...") ou do nome
     * da função Gemini ("produto_fisico_cadastrar"), retornando um RespostaEndpoint padronizado.
     */
    public RespostaEndpoint<Object> executarEndpoint(String caminhoOuToolName, Map<String, Object> params) {
        RotaSistema rota = resolverRota(caminhoOuNomeSafe(caminhoOuToolName));
        if (rota == null) {
            return RespostaEndpoint.erro(caminhoOuToolName, "Rota ou função não encontrada: " + caminhoOuToolName);
        }
        return executarEndpoint(rota, params);
    }

    /**
     * Executa uma rota na modalidade Inline/CLI e devolve a linha formatada pronta para exibição no terminal.
     */
    public String executarComandoInline(String caminhoOuToolName, Map<String, Object> params) {
        return executarEndpoint(caminhoOuToolName, params).formatarSaidaInline();
    }

    /**
     * Despachante central de todas as 42 rotas para JavaFX, CLI Inline e Function Calling da IA Gemini.
     */
    @SuppressWarnings("unchecked")
    public RespostaEndpoint<Object> executarEndpoint(RotaSistema rota, Map<String, Object> parametros) {
        Map<String, Object> p = parametros != null ? parametros : Collections.emptyMap();
        try {
            Object resultado = switch (rota) {
                case SISTEMA_INICIALIZAR_DADOS_PADRAO -> {
                    inicializarSistemaCompleto();
                    yield "Dados padrão (Um, FormaPagamento e Cliente Padrão) inicializados com sucesso.";
                }

                // 2. PRODUTO
                case PRODUTO_UNIDADE_LISTAR -> produtoController.listarUnidadesMedida();
                case PRODUTO_UNIDADE_CADASTRAR -> produtoController.cadastrarUnidadeMedida(
                        str(p, "sigla", "UN"),
                        str(p, "nome", "Unidade"),
                        str(p, "descricao", "Unidade padrão"),
                        bd(p, "base", "1.000000")
                );
                case PRODUTO_UNIDADE_ATUALIZAR -> produtoController.atualizarUnidadeMedida(
                        str(p, "sigla", null),
                        str(p, "nome", null),
                        str(p, "descricao", null),
                        p.containsKey("base") ? bd(p, "base", "1.000000") : null
                );
                case PRODUTO_UNIDADE_EXCLUIR -> produtoController.excluirUnidadeMedida(str(p, "sigla", ""));
                case PRODUTO_FISICO_CADASTRAR -> produtoController.cadastrarProdutoFisico(
                        str(p, "nome", null),
                        str(p, "descricao", ""),
                        bd(p, "precoVenda", "0.00"),
                        str(p, "siglaUm", "UN"),
                        bd(p, "alturaCm", "0.0000"),
                        bd(p, "larguraCm", "0.0000"),
                        bd(p, "comprimentoCm", "0.0000"),
                        bd(p, "pesoBrutoKg", "0.0000"),
                        bd(p, "pesoLiquidoKg", "0.0000")
                );
                case PRODUTO_FISICO_ATUALIZAR_DIMENSOES -> produtoController.atualizarDimensoesProdutoFisico(
                        lng(p, "idProduto"),
                        bd(p, "alturaCm", "0.0000"),
                        bd(p, "larguraCm", "0.0000"),
                        bd(p, "comprimentoCm", "0.0000"),
                        bd(p, "pesoBrutoKg", "0.0000"),
                        bd(p, "pesoLiquidoKg", "0.0000")
                );
                case PRODUTO_DIGITAL_CADASTRAR -> produtoController.cadastrarProdutoDigital(
                        str(p, "nome", null),
                        str(p, "descricao", ""),
                        bd(p, "precoVenda", "0.00"),
                        (List<LinkProdutoDigital>) p.getOrDefault("links", Collections.emptyList())
                );
                case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                    produtoController.adicionarComponenteAoKit(
                            lng(p, "idProdutoKit"),
                            lng(p, "idProdutoComponente"),
                            bd(p, "quantidade", "1.000000")
                    );
                    yield produtoController.buscarPorId(lng(p, "idProdutoKit"));
                }
                case PRODUTO_CATALOGO_LISTAR -> produtoController.listarTodosProdutos();
                case PRODUTO_CATALOGO_BUSCAR -> {
                    if (p.containsKey("id")) {
                        yield produtoController.buscarPorId(lng(p, "id"));
                    }
                    String termo = str(p, "nome", str(p, "termo", ""));
                    var encontrados = produtoController.buscarPorTermo(termo);
                    if (encontrados.size() == 1) {
                        yield encontrados.get(0);
                    }
                    if (!encontrados.isEmpty()) {
                        yield encontrados;
                    }
                    yield produtoController.buscarPorNome(termo);
                }
                case PRODUTO_CATALOGO_ATUALIZAR -> {
                    Long id = lng(p, "idProduto");
                    String nomeAtual = str(p, "nomeAtual", null);
                    String novoNome = str(p, "novoNome", null);
                    if (novoNome == null) {
                        if (id != null || nomeAtual != null) {
                            novoNome = str(p, "nome", null);
                        } else {
                            nomeAtual = str(p, "nome", null);
                        }
                    }
                    yield produtoController.atualizarProdutoPorIdOuNome(
                            id,
                            nomeAtual,
                            novoNome,
                            str(p, "descricao", null)
                    );
                }
                case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> produtoController.atualizarPrecoVendaPorIdOuNome(
                        lng(p, "idProduto"),
                        str(p, "nomeProduto", str(p, "nome", null)),
                        bd(p, "novoPreco", "0.00")
                );
                case PRODUTO_CATALOGO_EXCLUIR -> produtoController.excluirProdutoPorIdOuNome(
                        lng(p, "idProduto"),
                        str(p, "nomeProduto", str(p, "nome", null))
                );
                case PRODUTO_IMAGEM_ADICIONAR -> {
                    if (p.containsKey("base64")) {
                        yield imagemProdutoController.adicionarImagemBase64(
                                lng(p, "idProduto"),
                                str(p, "nomeOriginal", "imagem.png"),
                                str(p, "base64", ""),
                                bool(p, "isPrincipal", false)
                        ).dados();
                    } else if (p.containsKey("caminhoArquivo")) {
                        yield imagemProdutoController.adicionarImagemArquivo(
                                lng(p, "idProduto"),
                                new File(str(p, "caminhoArquivo", "")),
                                bool(p, "isPrincipal", false)
                        ).dados();
                    }
                    throw new IllegalArgumentException("Parâmetro 'base64' ou 'caminhoArquivo' é obrigatório para adicionar imagem.");
                }
                case PRODUTO_IMAGEM_LISTAR -> imagemProdutoController.listarImagens(lng(p, "idProduto")).dados();
                case PRODUTO_IMAGEM_PRINCIPAL_OBTER -> imagemProdutoController.obterImagemPrincipal(lng(p, "idProduto")).dados();
                case PRODUTO_IMAGEM_DEFINIR_PRINCIPAL -> imagemProdutoController.definirImagemPrincipal(
                        lng(p, "idProduto"),
                        lng(p, "idImagem")
                ).dados();
                case PRODUTO_IMAGEM_REMOVER -> imagemProdutoController.removerImagem(
                        lng(p, "idImagem"),
                        bool(p, "removerArquivoFisico", true)
                ).dados();

                // 3. PESSOA
                case PESSOA_CLIENTE_PADRAO_OBTER -> pessoaController.obterClientePadraoNaoRegistrado();
                case PESSOA_SIMPLES_CADASTRAR -> {
                    String papel = str(p, "papel", "").toUpperCase(Locale.ROOT);
                    boolean isCliente = bool(p, "isCliente", papel.contains("CLIENTE") || papel.contains("AMBOS"));
                    boolean isFornecedor = bool(p, "isFornecedor", papel.contains("FORNECEDOR") || papel.contains("AMBOS") || (!papel.contains("CLIENTE")));
                    yield pessoaController.cadastrarPessoaSimples(
                            str(p, "nome", str(p, "razaoSocial", null)),
                            isCliente,
                            isFornecedor,
                            str(p, "documento", str(p, "cnpj", str(p, "cpf", null))),
                            p.containsKey("limite") ? bd(p, "limite", "0.00") : BigDecimal.ZERO
                    );
                }
                case PESSOA_CLIENTE_PF_CADASTRAR -> pessoaController.cadastrarClientePessoaFisica(
                        str(p, "nome", null),
                        str(p, "cpf", null),
                        bd(p, "limite", "0.00")
                );
                case PESSOA_CLIENTE_PJ_CADASTRAR -> pessoaController.cadastrarClientePessoaJuridica(
                        str(p, "razaoSocial", str(p, "nome", null)),
                        str(p, "cnpj", null),
                        bd(p, "limite", "0.00")
                );
                case PESSOA_CLIENTE_ATUALIZAR_LIMITE -> pessoaController.atualizarLimiteCreditoCliente(
                        lng(p, "idPessoa"),
                        bd(p, "novoLimite", "0.00")
                );
                case PESSOA_DADOS_ATUALIZAR -> pessoaController.atualizarDadosCadastrais(
                        lng(p, "idPessoa"),
                        str(p, "nome", str(p, "razaoSocial", null))
                );
                case PESSOA_FORNECEDOR_PJ_CADASTRAR -> pessoaController.cadastrarFornecedorPessoaJuridica(
                        str(p, "razaoSocial", str(p, "nome", null)),
                        str(p, "cnpj", null)
                );
                case PESSOA_FORNECEDOR_PF_CADASTRAR -> pessoaController.cadastrarFornecedorPessoaFisica(
                        str(p, "nome", null),
                        str(p, "cpf", null)
                );
                case PESSOA_REGISTRO_ADICIONAR -> pessoaController.adicionarDocumentoRegistro(
                        lng(p, "idPessoa"),
                        str(p, "tipoDocumento", "RG"),
                        str(p, "numeroRegistro", "")
                );
                case PESSOA_LISTAR_CLIENTES -> pessoaController.listarClientes();
                case PESSOA_LISTAR_FORNECEDORES -> pessoaController.listarFornecedores();
                case PESSOA_BUSCAR -> pessoaController.buscarPessoasPorTermo(
                        str(p, "termo", str(p, "nome", str(p, "documento", ""))),
                        str(p, "papel", null)
                );
                case PESSOA_HISTORICO_COMPLETO -> pessoaController.consultarHistoricoCompletoPessoa(
                        lng(p, "idPessoa"),
                        str(p, "termo", str(p, "nome", str(p, "documento", null))),
                        str(p, "papel", null)
                );
                case PESSOA_CLIENTE_EXCLUIR -> pessoaController.excluirPessoa(lng(p, "idPessoa"));

                // 4. PAGAMENTO
                case PAGAMENTO_FORMA_LISTAR_ATIVAS -> pagamentoController.listarFormasPagamentoAtivas();
                case PAGAMENTO_FORMA_CADASTRAR -> pagamentoController.cadastrarNovaFormaPagamento(
                        str(p, "codigo", null),
                        str(p, "nome", null),
                        str(p, "descricao", null),
                        bool(p, "permiteAVista", true),
                        bool(p, "permiteAPrazo", false),
                        bool(p, "permiteParcelado", false),
                        inteiro(p, "maxParcelas", 1),
                        inteiro(p, "maxParcelasSemJuros", 1),
                        p.containsKey("taxaOperadoraPercentual") ? bd(p, "taxaOperadoraPercentual", "0.0000") : BigDecimal.ZERO,
                        p.containsKey("tarifaFixaOperadora") ? bd(p, "tarifaFixaOperadora", "0.0000") : BigDecimal.ZERO,
                        p.containsKey("descontoAVistaPercentual") ? bd(p, "descontoAVistaPercentual", "0.0000") : BigDecimal.ZERO,
                        p.containsKey("taxaJurosMensal") ? bd(p, "taxaJurosMensal", "0.0000") : BigDecimal.ZERO
                );
                case PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS -> pagamentoController.atualizarTaxasERegrasFormaPagamento(
                        str(p, "codigo", "PIX"),
                        bd(p, "taxaOperadoraPercentual", "0.0000"),
                        bd(p, "tarifaFixaOperadora", "0.0000"),
                        bd(p, "descontoAVistaPercentual", "0.0000"),
                        inteiro(p, "maxParcelas", 1),
                        inteiro(p, "maxParcelasSemJuros", 1),
                        bd(p, "taxaJurosMensal", "0.0000")
                );
                case PAGAMENTO_FORMA_ALTERAR_STATUS -> pagamentoController.alterarStatusFormaPagamento(
                        str(p, "codigo", "PIX"),
                        bool(p, "ativo", true)
                );
                case PAGAMENTO_FORMA_EXCLUIR -> pagamentoController.excluirFormaPagamento(str(p, "codigo", null));
                case PAGAMENTO_CRIAR_A_VISTA -> pagamentoController.criarPagamentoAVista(
                        str(p, "codigoForma", "PIX"),
                        bd(p, "valor", "0.00"),
                        p.containsKey("valorRecebido") ? bd(p, "valorRecebido", "0.00") : null,
                        p.containsKey("descontoCustomizado") ? bd(p, "descontoCustomizado", "0.00") : null
                );
                case PAGAMENTO_CRIAR_A_PRAZO -> pagamentoController.criarPagamentoAPrazo(
                        str(p, "codigoForma", "BOLETO"),
                        bd(p, "valor", "0.00"),
                        p.get("dataVencimento") instanceof LocalDate ld ? ld : LocalDate.now().plusDays(30)
                );
                case PAGAMENTO_CRIAR_PARCELADO -> pagamentoController.criarPagamentoParcelado(
                        str(p, "codigoForma", "CARTAO_CREDITO"),
                        bd(p, "valor", "0.00"),
                        inteiro(p, "quantidadeParcelas", 2)
                );
                case PAGAMENTO_QUITAR_TITULO_OU_PARCELA -> {
                    NotaPagamento np = (NotaPagamento) p.get("pagamento");
                    LocalDateTime dt = p.get("dataQuitacao") instanceof LocalDateTime ldt ? ldt : LocalDateTime.now();
                    if (np instanceof PagamentoParcelado pp && p.containsKey("numeroParcela")) {
                        pagamentoController.quitarParcelaEspecifica(pp, inteiro(p, "numeroParcela", 1), dt);
                    } else if (np != null) {
                        pagamentoController.quitarPagamento(np, dt);
                    }
                    yield np;
                }

                // 5. ESTOQUE
                case ESTOQUE_SALDO_CONSOLIDADO -> estoqueController.consultarSaldoTotalProduto(lng(p, "idProduto"));
                case ESTOQUE_FILA_LOTES_FIFO -> estoqueController.listarLotesDisponiveisFIFO(lng(p, "idProduto"));
                case ESTOQUE_BUSCAR_LOTE_ESPECIFICO -> estoqueController.buscarLoteEspecificoPorId(
                        lng(p, "idLote") != null ? lng(p, "idLote") : lng(p, "idItemLote")
                );
                case ESTOQUE_LOTES_PROXIMOS_VALIDADE -> estoqueController.consultarLotesProximosAoVencimento(
                        p.get("dataLimite") instanceof LocalDate ld ? ld : LocalDate.now().plusMonths(3)
                );
                case ESTOQUE_BAIXAR_PERDA_DIRETA -> estoqueController.baixarPerdaEmLoteEstoque(
                        lng(p, "idLote") != null ? lng(p, "idLote") : lng(p, "idItemLote"),
                        bd(p, "quantidade", str(p, "quantidadePerda", "1.000000")),
                        str(p, "motivo", str(p, "motivoPerda", "Perda operacional de estoque"))
                );

                // 6. NOTA
                case NOTA_COMPRA_EMITIR -> {
                    NotaCompra nc = resolverOuConstruirNotaCompra(p);
                    List<NotaPagamento> pags = extrairPagamentos(p);
                    yield notaController.emitirNotaCompra(nc, pags);
                }
                case NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR -> {
                    NotaCompra nc = p.get("novaCompra") instanceof NotaCompra n ? n : null;
                    if (nc == null && (p.containsKey("idNovaCompra") || p.containsKey("idNotaCompra") || p.containsKey("idNota"))) {
                        Long id = p.containsKey("idNovaCompra") ? lng(p, "idNovaCompra") : (p.containsKey("idNotaCompra") ? lng(p, "idNotaCompra") : lng(p, "idNota"));
                        Nota n = notaController.buscarNotaPorId(id);
                        if (n instanceof NotaCompra compra) nc = compra;
                    }
                    if (nc == null && (p.containsKey("idFornecedor") || p.containsKey("fornecedor"))) {
                        nc = resolverOuConstruirNotaCompra(p);
                    }
                    if (nc == null) {
                        throw new IllegalArgumentException("Nova nota de compra não informada ou não encontrada para abatimento de perdas.");
                    }
                    Produto prod = p.get("produto") instanceof Produto pr ? pr : null;
                    if (prod == null && p.containsKey("idProduto")) {
                        prod = produtoController.buscarPorId(lng(p, "idProduto"));
                    }
                    yield notaController.abaterPerdasPosVendaEmNovaCompra(nc, prod);
                }
                case NOTA_COMPRA_SUGERIR_PRECO_VENDA -> {
                    NotaCompra nc = p.get("notaCompra") instanceof NotaCompra n ? n : null;
                    if (nc == null && (p.containsKey("idNotaCompra") || p.containsKey("idNota"))) {
                        Long id = p.containsKey("idNotaCompra") ? lng(p, "idNotaCompra") : lng(p, "idNota");
                        Nota n = notaController.buscarNotaPorId(id);
                        if (n instanceof NotaCompra compra) nc = compra;
                    }
                    if (nc == null && (p.containsKey("idFornecedor") || p.containsKey("fornecedor"))) {
                        nc = resolverOuConstruirNotaCompra(p);
                    }
                    if (nc == null) {
                        throw new IllegalArgumentException("Nota de compra não informada ou não encontrada para sugestão de preços.");
                    }
                    yield notaController.sugerirPrecosVendaDaCompra(nc, bd(p, "margemDesejada", "0.3000"));
                }
                case NOTA_VENDA_EMITIR_FIFO -> {
                    Pessoa cliente = resolverCliente(p);
                    String descricao = str(p, "descricao", "Venda FIFO");
                    Map<Produto, BigDecimal> itens = resolverItensVenda(p);
                    List<NotaPagamento> pags = extrairPagamentos(p);
                    String cupom = str(p, "cupom", null);
                    BigDecimal desconto = p.containsKey("descontoCupom") ? bd(p, "descontoCupom", "0.00")
                            : (p.containsKey("descontoGlobal") ? bd(p, "descontoGlobal", "0.00") : BigDecimal.ZERO);
                    yield notaController.emitirVendaFIFO(cliente, descricao, itens, pags, cupom, desconto);
                }
                case NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> {
                    Map<Produto, BigDecimal> itens = resolverItensVenda(p);
                    List<NotaPagamento> pags = extrairPagamentos(p);
                    yield notaController.emitirVendaBalcaoClienteNaoRegistradoFIFO(itens, pags);
                }
                case NOTA_VENDA_EMITIR_LOTE_DESEJAVEL -> {
                    Pessoa cliente = resolverCliente(p);
                    String descricao = str(p, "descricao", "Venda Lote Desejável");
                    Map<Long, BigDecimal> lotes = resolverLotesDesejados(p);
                    List<NotaPagamento> pags = extrairPagamentos(p);
                    yield notaController.emitirVendaPorLoteDesejado(cliente, descricao, lotes, pags);
                }
                case NOTA_VENDA_PROCEDURE_MYSQL_FIFO -> {
                    notaController.executarBaixaViaProcedureMySqlFIFO(
                            lng(p, "idNotaVenda"),
                            lng(p, "idProduto"),
                            bd(p, "quantidade", "1.000000"),
                            bd(p, "precoVenda", "0.0000")
                    );
                    yield notaController.buscarNotaPorId(lng(p, "idNotaVenda"));
                }
                case NOTA_VENDA_PROCEDURE_MYSQL_LOTE -> {
                    notaController.executarBaixaViaProcedureMySqlLoteEspecifico(
                            lng(p, "idNotaVenda"),
                            lng(p, "idLoteDesejado") != null ? lng(p, "idLoteDesejado") : lng(p, "idItemLoteDesejavel"),
                            bd(p, "quantidade", "1.000000"),
                            bd(p, "precoVenda", "0.0000")
                    );
                    yield notaController.buscarNotaPorId(lng(p, "idNotaVenda"));
                }
                case NOTA_DEVOLUCAO_REGISTRAR -> {
                    NotaVenda nv = p.get("notaVenda") instanceof NotaVenda v ? v : null;
                    if (nv == null && (p.containsKey("idNotaVenda") || p.containsKey("idNota"))) {
                        Long idNota = p.containsKey("idNotaVenda") ? lng(p, "idNotaVenda") : lng(p, "idNota");
                        Nota n = notaController.buscarNotaPorId(idNota);
                        if (n instanceof NotaVenda v) nv = v;
                    }
                    if (nv == null) {
                        throw new IllegalArgumentException("Nota de venda original não informada ou não encontrada para a devolução.");
                    }

                    Map<Item<? extends Produto>, BigDecimal> itensDevolvidos = new LinkedHashMap<>();
                    if (p.get("itensDevolvidos") instanceof Map<?, ?> m) {
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() instanceof Item<?> item) {
                                BigDecimal qtd = e.getValue() instanceof BigDecimal b ? b : new BigDecimal(e.getValue().toString());
                                itensDevolvidos.put((Item<? extends Produto>) item, qtd);
                            }
                        }
                    }
                    if (itensDevolvidos.isEmpty() && (p.containsKey("idItemVenda") || p.containsKey("idProduto") || p.containsKey("idItem"))) {
                        Long idItem = p.containsKey("idItemVenda") ? lng(p, "idItemVenda") : lng(p, "idItem");
                        Long idProd = lng(p, "idProduto");
                        BigDecimal qtdDev = bd(p, "quantidadeDevolvida", str(p, "quantidade", "1.000000"));

                        Item<? extends Produto> itemAlvo = null;
                        if (nv.getItens() != null) {
                            for (Item<? extends Produto> it : nv.getItens()) {
                                if (idItem != null && Objects.equals(it.getId(), idItem)) {
                                    itemAlvo = it;
                                    break;
                                }
                                if (idProd != null && it.getProduto() != null && Objects.equals(it.getProduto().getId(), idProd)) {
                                    itemAlvo = it;
                                    break;
                                }
                            }
                        }
                        if (itemAlvo == null && nv.getItens() != null && !nv.getItens().isEmpty()) {
                            itemAlvo = nv.getItens().get(0);
                        }
                        if (itemAlvo != null) {
                            itensDevolvidos.put(itemAlvo, qtdDev);
                        }
                    }
                    if (itensDevolvidos.isEmpty()) {
                        throw new IllegalArgumentException("Nenhum item válido informado para devolução.");
                    }
                    yield notaController.registrarDevolucaoVenda(
                            nv,
                            itensDevolvidos,
                            str(p, "motivo", "Devolução de mercadoria"),
                            bool(p, "geraPerdaFornecedor", false)
                    );
                }

                // 7. RELATORIO
                case RELATORIO_LUCRO_CONTABIL_BACEN -> notaController.consultarRelatorioLucroContabilBacen(
                        p.containsKey("idNotaVenda") ? lng(p, "idNotaVenda") : null
                );
                case RELATORIO_PERDAS_POR_FORNECEDOR -> notaController.consultarRelatorioPerdasPorFornecedor(
                        (Pessoa) p.get("fornecedor"),
                        (Produto) p.get("produto"),
                        bool(p, "apenasPosVendaDentroValidade", true)
                );
                case RELATORIO_ANALISE_SAZONAL_VENDAS -> notaController.consultarRelatorioAnaliseSazonalVendas();

                // 8. ESTATISTICA (processamento nas Stored Procedures do MySQL)
                case ESTATISTICA_DEMANDA_MENSAL_CLIENTE -> estatisticaController.preverDemandaMensalCliente(
                        p.containsKey("idCliente") ? lng(p, "idCliente") : null,
                        p.containsKey("idProduto") ? lng(p, "idProduto") : null,
                        inteiro(p, "mes", 0));
                case ESTATISTICA_PERFIL_CLIENTE -> estatisticaController.analisarPerfilCliente(
                        p.containsKey("idCliente") ? lng(p, "idCliente") : null);
                case ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS -> estatisticaController.analisarImpactoExterno(
                        p.containsKey("idProduto") ? lng(p, "idProduto") : null);
                case ESTATISTICA_RANKING_FORNECEDORES -> estatisticaController.rankingFornecedores(
                        p.containsKey("idProduto") ? lng(p, "idProduto") : null,
                        str(p, "clima", "TODOS"), str(p, "regimeRio", "TODOS"));
                case ESTATISTICA_MOTIVOS_PERDA_PREMATURA -> estatisticaController.diagnosticarPerdaPrematura(
                        p.containsKey("idProduto") ? lng(p, "idProduto") : null,
                        p.containsKey("idFornecedor") ? lng(p, "idFornecedor") : null);
                case ESTATISTICA_DESTAQUES_SAZONAIS -> estatisticaController.destaquesSazonais(inteiro(p, "limite", 25));
                case ESTATISTICA_PICOS_DIAS_EVENTOS -> estatisticaController.picosDiasEventos();
                case ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS -> estatisticaController.planoEstrategico(
                        p.get("dataReferencia") instanceof LocalDate ld ? ld : LocalDate.now(),
                        inteiro(p, "horizonteDias", 30));
                case ESTATISTICA_CAUSAS_EXTERNAS_NOTA -> estatisticaController.causasExternasPorDemanda(
                        lng(p, "idNota"), p.containsKey("idProduto") ? lng(p, "idProduto") : null);
                case ESTATISTICA_RECALCULAR_FATORES -> estatisticaController.recalcularEstatisticasFatores();
                case ESTATISTICA_FATORES_PRODUTO -> estatisticaController.estatisticaFatoresProduto(
                        p.containsKey("idProduto") ? lng(p, "idProduto") : null);
                case FATOR_EXTERNO_EVENTOS_LISTAR -> estatisticaController.eventosPersistidos(inteiro(p, "limite", 100));
                case FATOR_EXTERNO_RESOLVER_PENDENCIAS -> manageNotas.resolverFatoresExternosPendentes();
            };

            return RespostaEndpoint.ok(rota, rota.getDescricaoOperacao(), resultado);
        } catch (Exception e) {
            return RespostaEndpoint.erro(rota.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Exporta todas as 42 rotas como Function Declarations no formato JSON Schema oficial da API Gemini
     * (tools -> functionDeclarations), permitindo que um agente Gemini invoque qualquer rota diretamente.
     */
    public String exportarDeclaracoesFerramentasGeminiJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"tools\": [\n    {\n      \"functionDeclarations\": [\n");
        RotaSistema[] rotas = RotaSistema.values();
        for (int i = 0; i < rotas.length; i++) {
            RotaSistema r = rotas[i];
            sb.append("        {\n")
              .append("          \"name\": \"").append(r.getNomeFuncaoGemini()).append("\",\n")
              .append("          \"description\": \"[").append(r.getModulo()).append(" | ").append(r.getCaminhoRota())
              .append("] ").append(r.getDescricaoOperacao().replace("\"", "\\\"")).append("\",\n")
              .append("          \"parameters\": {\n")
              .append("            \"type\": \"OBJECT\",\n")
              .append("            \"properties\": {\n")
              .append("              \"rota\": { \"type\": \"STRING\", \"description\": \"").append(r.getCaminhoRota()).append("\" }\n")
              .append("            }\n")
              .append("          }\n")
              .append("        }").append(i < rotas.length - 1 ? ",\n" : "\n");
        }
        sb.append("      ]\n    }\n  ]\n}");
        return sb.toString();
    }

    private String caminhoOuNomeSafe(String s) {
        return s != null ? s.trim() : "";
    }

    private String str(Map<String, Object> p, String chave, String padrao) {
        Object v = p.get(chave);
        return v != null ? v.toString() : padrao;
    }

    private BigDecimal bd(Map<String, Object> p, String chave, String padrao) {
        Object v = p.get(chave);
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v != null && !v.toString().isBlank()) return new BigDecimal(v.toString());
        return new BigDecimal(padrao);
    }

    private Long lng(Map<String, Object> p, String chave) {
        Object v = p.get(chave);
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        if (v != null) return Long.parseLong(v.toString());
        return null;
    }

    private int inteiro(Map<String, Object> p, String chave, int padrao) {
        Object v = p.get(chave);
        if (v instanceof Number n) return n.intValue();
        if (v != null) return Integer.parseInt(v.toString());
        return padrao;
    }

    private boolean bool(Map<String, Object> p, String chave, boolean padrao) {
        Object v = p.get(chave);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return padrao;
    }

    // ==================== AUXILIARES DE RESOLUÇÃO DE PARÂMETROS DE NOTAS ====================

    @SuppressWarnings("unchecked")
    private NotaCompra resolverOuConstruirNotaCompra(Map<String, Object> p) {
        if (p.get("notaCompra") instanceof NotaCompra nc) {
            return nc;
        }
        if (p.containsKey("idNotaCompra") || p.containsKey("idNota")) {
            Long id = p.containsKey("idNotaCompra") ? lng(p, "idNotaCompra") : lng(p, "idNota");
            Nota n = notaController.buscarNotaPorId(id);
            if (n instanceof NotaCompra nc) return nc;
        }

        Pessoa fornecedor = resolverFornecedor(p);
        if (fornecedor == null) {
            throw new IllegalArgumentException("Fornecedor não identificado ou não encontrado para emissão da Nota de Compra.");
        }

        String descricao = str(p, "descricao", "Compra de estoque");
        NotaCompra nota = new NotaCompra(fornecedor, descricao);

        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }

        if (itensObj instanceof List<?> list) {
            for (Object el : list) {
                if (el instanceof Item<?> it) {
                    nota.addItem((Item<? extends Produto>) it);
                } else if (el instanceof Map<?, ?> itemMap) {
                    Map<String, Object> map = (Map<String, Object>) itemMap;
                    Produto prod = resolverProduto(map);
                    if (prod == null) {
                        throw new IllegalArgumentException("Produto não encontrado no item da compra: " + map);
                    }
                    BigDecimal qtd = bd(map, "quantidade", "1.000000");
                    BigDecimal valor = resolverValorItem(map, prod);
                    LocalDate validade = resolverValidadeItem(map);
                    Item<Produto> item = new Item<>(prod, valor, qtd, validade);
                    if (map.containsKey("desconto")) {
                        item.aplicarDesconto(bd(map, "desconto", "0.0000"));
                    }
                    nota.addItem(item);
                }
            }
        } else if (itensObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Produto prod = null;
                if (entry.getKey() instanceof Produto pr) prod = pr;
                else if (entry.getKey() instanceof Number num) prod = produtoController.buscarPorId(num.longValue());
                else if (entry.getKey() != null) prod = produtoController.buscarPorNome(entry.getKey().toString());

                if (prod == null) {
                    throw new IllegalArgumentException("Produto não encontrado no item da compra: " + entry.getKey());
                }

                BigDecimal qtd = BigDecimal.ONE;
                BigDecimal valor = prod.getPrecoVenda() != null ? prod.getPrecoVenda() : BigDecimal.ZERO;
                LocalDate validade = null;

                if (entry.getValue() instanceof BigDecimal b) {
                    qtd = b;
                } else if (entry.getValue() instanceof Number num) {
                    qtd = BigDecimal.valueOf(num.doubleValue());
                } else if (entry.getValue() instanceof Map<?, ?> vm) {
                    Map<String, Object> valMap = (Map<String, Object>) vm;
                    qtd = bd(valMap, "quantidade", "1.000000");
                    valor = resolverValorItem(valMap, prod);
                    validade = resolverValidadeItem(valMap);
                }
                Item<Produto> item = new Item<>(prod, valor, qtd, validade);
                nota.addItem(item);
            }
        }

        if (nota.getItens().isEmpty()) {
            throw new IllegalArgumentException("A Nota de Compra deve conter pelo menos um item válido.");
        }
        return nota;
    }

    private Pessoa resolverFornecedor(Map<String, Object> p) {
        if (p.get("fornecedor") instanceof Pessoa pes) return pes;
        Long id = lng(p, "idFornecedor");
        if (id == null) id = lng(p, "idPessoa");
        if (id == null && p.get("fornecedor") instanceof Number n) id = n.longValue();
        if (id != null) {
            Pessoa pes = pessoaController.buscarPorId(id);
            if (pes != null) {
                if (!pes.isFornecedor()) pes.definirComoFornecedor();
                return pes;
            }
        }
        String termo = str(p, "nomeFornecedor", null);
        if (termo == null && p.get("fornecedor") instanceof String s) termo = s;
        if (termo != null && !termo.isBlank()) {
            try {
                Long parsed = Long.parseLong(termo.trim());
                Pessoa pes = pessoaController.buscarPorId(parsed);
                if (pes != null) {
                    if (!pes.isFornecedor()) pes.definirComoFornecedor();
                    return pes;
                }
            } catch (Exception ignored) {}
            List<Pessoa> fornecedores = pessoaController.listarFornecedores();
            for (Pessoa f : fornecedores) {
                if (f.getNome().equalsIgnoreCase(termo.trim())) return f;
            }
            for (Pessoa f : fornecedores) {
                if (f.getNome().toLowerCase(Locale.ROOT).contains(termo.toLowerCase(Locale.ROOT))) return f;
            }
        }
        List<Pessoa> fornecedores = pessoaController.listarFornecedores();
        if (!fornecedores.isEmpty()) {
            return fornecedores.get(0);
        }
        return null;
    }

    private Pessoa resolverCliente(Map<String, Object> p) {
        if (p.get("cliente") instanceof Pessoa pes) return pes;
        Long id = lng(p, "idCliente");
        if (id == null) id = lng(p, "idPessoa");
        if (id == null && p.get("cliente") instanceof Number n) id = n.longValue();
        if (id != null) {
            Pessoa pes = pessoaController.buscarPorId(id);
            if (pes != null) return pes;
        }
        String termo = str(p, "nomeCliente", null);
        if (termo == null && p.get("cliente") instanceof String s) termo = s;
        if (termo != null && !termo.isBlank()) {
            try {
                Long parsed = Long.parseLong(termo.trim());
                Pessoa pes = pessoaController.buscarPorId(parsed);
                if (pes != null) return pes;
            } catch (Exception ignored) {}
            List<Pessoa> clientes = pessoaController.listarClientes();
            for (Pessoa c : clientes) {
                if (c.getNome().equalsIgnoreCase(termo.trim())) return c;
            }
            for (Pessoa c : clientes) {
                if (c.getNome().toLowerCase(Locale.ROOT).contains(termo.toLowerCase(Locale.ROOT))) return c;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<Produto, BigDecimal> resolverItensVenda(Map<String, Object> p) {
        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }

        Map<Produto, BigDecimal> res = new LinkedHashMap<>();

        if (itensObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Produto prod = null;
                if (entry.getKey() instanceof Produto pr) prod = pr;
                else if (entry.getKey() instanceof Number num) prod = produtoController.buscarPorId(num.longValue());
                else if (entry.getKey() != null) {
                    try {
                        prod = produtoController.buscarPorId(Long.parseLong(entry.getKey().toString().trim()));
                    } catch (Exception ignored) {
                        prod = produtoController.buscarPorNome(entry.getKey().toString());
                    }
                }
                if (prod == null) {
                    throw new IllegalArgumentException("Produto não encontrado para venda: " + entry.getKey());
                }
                BigDecimal qtd = BigDecimal.ONE;
                if (entry.getValue() instanceof BigDecimal b) qtd = b;
                else if (entry.getValue() instanceof Number num) qtd = BigDecimal.valueOf(num.doubleValue());
                else if (entry.getValue() != null && !entry.getValue().toString().isBlank()) qtd = new BigDecimal(entry.getValue().toString());
                res.put(prod, res.getOrDefault(prod, BigDecimal.ZERO).add(qtd));
            }
        } else if (itensObj instanceof List<?> list) {
            for (Object el : list) {
                if (el instanceof Map<?, ?> itemMap) {
                    Map<String, Object> map = (Map<String, Object>) itemMap;
                    Produto prod = resolverProduto(map);
                    if (prod == null) {
                        throw new IllegalArgumentException("Produto não encontrado no item de venda: " + map);
                    }
                    BigDecimal qtd = bd(map, "quantidade", "1.000000");
                    res.put(prod, res.getOrDefault(prod, BigDecimal.ZERO).add(qtd));
                }
            }
        }

        if (res.isEmpty()) {
            throw new IllegalArgumentException("Itens da venda não informados ou vazios.");
        }
        return res;
    }

    private Map<Long, BigDecimal> resolverLotesDesejados(Map<String, Object> p) {
        if (p.get("lotesEQuantidades") instanceof Map<?, ?> m) {
            Map<Long, BigDecimal> res = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Long idLote = null;
                if (e.getKey() instanceof Number num) idLote = num.longValue();
                else if (e.getKey() != null) idLote = Long.parseLong(e.getKey().toString().trim());

                BigDecimal qtd = BigDecimal.ONE;
                if (e.getValue() instanceof BigDecimal b) qtd = b;
                else if (e.getValue() instanceof Number num) qtd = BigDecimal.valueOf(num.doubleValue());
                else if (e.getValue() != null) qtd = new BigDecimal(e.getValue().toString());

                if (idLote != null) res.put(idLote, qtd);
            }
            if (!res.isEmpty()) return res;
        }
        Long idLote = lng(p, "idItemLoteDesejavel");
        if (idLote == null) idLote = lng(p, "idLote");
        if (idLote != null) {
            BigDecimal qtd = bd(p, "quantidade", "1.000000");
            return Collections.singletonMap(idLote, qtd);
        }
        throw new IllegalArgumentException("Lotes desejados e quantidades não informados para a venda.");
    }

    private Produto resolverProduto(Map<String, Object> map) {
        if (map.get("produto") instanceof Produto pr) return pr;
        Long id = lng(map, "idProduto");
        if (id == null) id = lng(map, "id");
        if (id != null) {
            Produto p = produtoController.buscarPorId(id);
            if (p != null) return p;
        }
        String nome = str(map, "nomeProduto", null);
        if (nome == null) nome = str(map, "nome", null);
        if (nome != null && !nome.isBlank()) {
            Produto p = produtoController.buscarPorNome(nome);
            if (p != null) return p;
            List<Produto> porTermo = produtoController.buscarPorTermo(nome);
            if (!porTermo.isEmpty()) return porTermo.get(0);
        }
        return null;
    }

    private BigDecimal resolverValorItem(Map<String, Object> map, Produto prod) {
        if (map.containsKey("valorUnitario")) return bd(map, "valorUnitario", "0.0000");
        if (map.containsKey("valor")) return bd(map, "valor", "0.0000");
        if (map.containsKey("precoUnitario")) return bd(map, "precoUnitario", "0.0000");
        if (map.containsKey("preco")) return bd(map, "preco", "0.0000");
        if (map.containsKey("custo")) return bd(map, "custo", "0.0000");
        if (prod != null && prod.getPrecoVenda() != null) return prod.getPrecoVenda();
        return BigDecimal.ZERO;
    }

    private LocalDate resolverValidadeItem(Map<String, Object> map) {
        Object v = map.get("dataValidade");
        if (v == null) v = map.get("validade");
        if (v instanceof LocalDate ld) return ld;
        if (v != null && !v.toString().isBlank()) {
            try {
                return LocalDate.parse(v.toString().trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<NotaPagamento> extrairPagamentos(Map<String, Object> p) {
        Object pags = p.get("pagamentos");
        if (pags instanceof List<?> list) {
            List<NotaPagamento> res = new ArrayList<>();
            for (Object el : list) {
                if (el instanceof NotaPagamento np) {
                    res.add(np);
                }
            }
            return res;
        }
        return Collections.emptyList();
    }

    public ProdutoController produtos() {
        return produtoController;
    }

    public PessoaController pessoas() {
        return pessoaController;
    }

    public PagamentoController pagamentos() {
        return pagamentoController;
    }

    public EstoqueController estoque() {
        return estoqueController;
    }

    public NotaController notas() {
        return notaController;
    }

    public EstatisticaController estatisticas() {
        return estatisticaController;
    }

    public ImagemProdutoController imagens() {
        return imagemProdutoController;
    }

    public EstoqueManage getEstoqueManage() {
        return estoqueManage;
    }

    public ManageNotas getManageNotas() {
        return manageNotas;
    }
}
