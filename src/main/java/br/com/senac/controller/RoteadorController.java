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
                    yield produtoController.buscarPorNome(str(p, "nome", ""));
                }
                case PRODUTO_CATALOGO_ATUALIZAR -> produtoController.atualizarProduto(
                        lng(p, "idProduto"),
                        str(p, "nome", null),
                        str(p, "descricao", null)
                );
                case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> produtoController.atualizarPrecoVenda(
                        lng(p, "idProduto"),
                        bd(p, "novoPreco", "0.00")
                );
                case PRODUTO_CATALOGO_EXCLUIR -> produtoController.excluirProduto(lng(p, "idProduto"));
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
                case ESTOQUE_BUSCAR_LOTE_ESPECIFICO -> estoqueController.buscarLoteEspecificoPorId(lng(p, "idLote"));
                case ESTOQUE_LOTES_PROXIMOS_VALIDADE -> estoqueController.consultarLotesProximosAoVencimento(
                        p.get("dataLimite") instanceof LocalDate ld ? ld : LocalDate.now().plusMonths(3)
                );
                case ESTOQUE_BAIXAR_PERDA_DIRETA -> estoqueController.baixarPerdaEmLoteEstoque(
                        lng(p, "idLote"),
                        bd(p, "quantidade", "1.000000"),
                        str(p, "motivo", "Perda operacional de estoque")
                );

                // 6. NOTA
                case NOTA_COMPRA_EMITIR -> notaController.emitirNotaCompra(
                        (NotaCompra) p.get("notaCompra"),
                        (List<NotaPagamento>) p.getOrDefault("pagamentos", Collections.emptyList())
                );
                case NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR -> notaController.abaterPerdasPosVendaEmNovaCompra(
                        (NotaCompra) p.get("novaCompra"),
                        (Produto) p.get("produto")
                );
                case NOTA_COMPRA_SUGERIR_PRECO_VENDA -> notaController.sugerirPrecosVendaDaCompra(
                        (NotaCompra) p.get("notaCompra"),
                        bd(p, "margemDesejada", "0.3000")
                );
                case NOTA_VENDA_EMITIR_FIFO -> notaController.emitirVendaFIFO(
                        (Pessoa) p.get("cliente"),
                        str(p, "descricao", "Venda FIFO"),
                        (Map<Produto, BigDecimal>) p.get("itens"),
                        (List<NotaPagamento>) p.getOrDefault("pagamentos", Collections.emptyList()),
                        str(p, "cupom", null),
                        p.containsKey("descontoCupom") ? bd(p, "descontoCupom", "0.00") : BigDecimal.ZERO
                );
                case NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> notaController.emitirVendaBalcaoClienteNaoRegistradoFIFO(
                        (Map<Produto, BigDecimal>) p.get("itens"),
                        (List<NotaPagamento>) p.getOrDefault("pagamentos", Collections.emptyList())
                );
                case NOTA_VENDA_EMITIR_LOTE_DESEJAVEL -> notaController.emitirVendaPorLoteDesejado(
                        (Pessoa) p.get("cliente"),
                        str(p, "descricao", "Venda Lote Desejável"),
                        (Map<Long, BigDecimal>) p.get("lotesEQuantidades"),
                        (List<NotaPagamento>) p.getOrDefault("pagamentos", Collections.emptyList())
                );
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
                            lng(p, "idLoteDesejado"),
                            bd(p, "quantidade", "1.000000"),
                            bd(p, "precoVenda", "0.0000")
                    );
                    yield notaController.buscarNotaPorId(lng(p, "idNotaVenda"));
                }
                case NOTA_DEVOLUCAO_REGISTRAR -> notaController.registrarDevolucaoVenda(
                        (NotaVenda) p.get("notaVenda"),
                        (Map<Item<? extends Produto>, BigDecimal>) p.get("itensDevolvidos"),
                        str(p, "motivo", "Devolução de mercadoria"),
                        bool(p, "geraPerdaFornecedor", false)
                );

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
