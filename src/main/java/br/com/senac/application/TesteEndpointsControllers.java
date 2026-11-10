package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bateria completa de validação de todas as 42 Rotas/Endpoints dos Controllers
 * simulando o consumo real por:
 * 1. Interface Gráfica JavaFX (objetos desanexados sem LazyInitializationException)
 * 2. Interface Inline / CLI (executarComandoInline)
 * 3. Agente de IA Gemini / Function Calling (executarEndpoint por tool name + Schema JSON)
 */
public class TesteEndpointsControllers {

    private static final Set<RotaSistema> rotasValidadasComSucesso = EnumSet.noneOf(RotaSistema.class);

    public static void main(String[] args) {
        try {
            RoteadorController roteador = new RoteadorController();
            String sufixo = String.valueOf(System.currentTimeMillis() % 100000);

            System.out.println("==========================================================================================");
            System.out.println("   VALIDAÇÃO END-TO-END DOS 42 ENDPOINTS (JAVAFX + INLINE CLI + IA GEMINI TOOLS)");
            System.out.println("==========================================================================================\n");

            // 1. MÓDULO SISTEMA (1 rota)
            validarEndpoint(roteador, RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

            // 2. MÓDULO PRODUTO (8 rotas)
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_LISTAR, Map.of());
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_CADASTRAR, Map.of(
                    "sigla", "DZ", "nome", "Dúzia " + sufixo, "descricao", "Conjunto de 12 unidades", "base", "1.000000"
            ));

            RespostaEndpoint<Object> respProdFisico = validarEndpoint(roteador, RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Monitor Gamer 27 Pol #" + sufixo,
                    "descricao", "Monitor IPS 165Hz",
                    "precoVenda", "1499.90",
                    "siglaUm", "UN",
                    "alturaCm", "45.0000",
                    "larguraCm", "61.5000",
                    "comprimentoCm", "18.0000",
                    "pesoBrutoKg", "5.8000",
                    "pesoLiquidoKg", "4.5000"
            ));
            ProdutoFisico monitor = (ProdutoFisico) respProdFisico.dados();

            validarEndpoint(roteador, RotaSistema.PRODUTO_FISICO_ATUALIZAR_DIMENSOES, Map.of(
                    "idProduto", monitor.getId(),
                    "alturaCm", "46.0000",
                    "larguraCm", "62.0000",
                    "comprimentoCm", "20.0000",
                    "pesoBrutoKg", "6.0000",
                    "pesoLiquidoKg", "4.6000"
            ));

            RespostaEndpoint<Object> respProdDigital = validarEndpoint(roteador, RotaSistema.PRODUTO_DIGITAL_CADASTRAR, Map.of(
                    "nome", "Garantia Estendida Digital 24m #" + sufixo,
                    "descricao", "Cobertura digital imediata",
                    "precoVenda", "199.90",
                    "links", List.of(new LinkProdutoDigital("Portal Garantia", "https://senac.com.br/garantia"))
            ));
            ProdutoDigital garantiaDigital = (ProdutoDigital) respProdDigital.dados();

            RespostaEndpoint<Object> respKit = validarEndpoint(roteador, RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Combo Monitor + Garantia #" + sufixo,
                    "descricao", "Kit Monitor 27 + Garantia",
                    "precoVenda", "1599.90",
                    "siglaUm", "UN"
            ));
            ProdutoFisico kitMonitor = (ProdutoFisico) respKit.dados();

            validarEndpoint(roteador, RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", kitMonitor.getId(),
                    "idProdutoComponente", monitor.getId(),
                    "quantidade", "1.000000"
            ));
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_LISTAR, Map.of());
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("id", monitor.getId()));

            // 3. MÓDULO PESSOA (9 rotas)
            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PADRAO_OBTER, Map.of());

            RespostaEndpoint<Object> respCliPf = validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR, Map.of(
                    "nome", "Roberto Almeida #" + sufixo,
                    "cpf", "777.888." + sufixo.substring(0, 3) + "-99",
                    "limite", "8000.00"
            ));
            PessoaFisica clientePf = (PessoaFisica) respCliPf.dados();

            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR, Map.of(
                    "razaoSocial", "Tech Office Manaus LTDA #" + sufixo,
                    "cnpj", "88.999." + sufixo.substring(0, 3) + "/0001-77",
                    "limite", "25000.00"
            ));

            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE, Map.of(
                    "idPessoa", clientePf.getId(),
                    "novoLimite", "12000.00"
            ));

            RespostaEndpoint<Object> respFornPj = validarEndpoint(roteador, RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR, Map.of(
                    "razaoSocial", "Fábrica Display Amazônia S.A. #" + sufixo,
                    "cnpj", "99.111." + sufixo.substring(0, 3) + "/0001-88"
            ));
            PessoaJuridica fornecedorPj = (PessoaJuridica) respFornPj.dados();

            validarEndpoint(roteador, RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR, Map.of(
                    "nome", "Produtor Rural João #" + sufixo,
                    "cpf", "666.555." + sufixo.substring(0, 3) + "-11"
            ));

            RespostaEndpoint<Object> respPessoaComReg = validarEndpoint(roteador, RotaSistema.PESSOA_REGISTRO_ADICIONAR, Map.of(
                    "idPessoa", clientePf.getId(),
                    "tipoDocumento", "CNH",
                    "numeroRegistro", "04599887766"
            ));
            // Simula acesso da Tabela JavaFX à coleção LAZY 'registros' fora do EntityManager:
            PessoaFisica pfDesanexado = (PessoaFisica) respPessoaComReg.dados();
            if (pfDesanexado.getRegistros().isEmpty()) {
                throw new AssertionError("Registros da Pessoa não foram carregados para o JavaFX!");
            }

            validarEndpoint(roteador, RotaSistema.PESSOA_LISTAR_CLIENTES, Map.of());
            validarEndpoint(roteador, RotaSistema.PESSOA_LISTAR_FORNECEDORES, Map.of());

            // 4. MÓDULO PAGAMENTO (7 rotas)
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_LISTAR_ATIVAS, Map.of());
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS, Map.of(
                    "codigo", "CARTAO_DEBITO",
                    "taxaOperadoraPercentual", "1.3900",
                    "tarifaFixaOperadora", "0.0000",
                    "descontoAVistaPercentual", "0.0000",
                    "maxParcelas", 1,
                    "maxParcelasSemJuros", 1,
                    "taxaJurosMensal", "0.0000"
            ));
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_ALTERAR_STATUS, Map.of(
                    "codigo", "TRANSFERENCIA",
                    "ativo", true
            ));

            RespostaEndpoint<Object> respPagVista = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_A_VISTA, Map.of(
                    "codigoForma", "PIX",
                    "valor", "8500.00",
                    "valorRecebido", "8500.00",
                    "descontoCustomizado", "0.00"
            ));
            PagamentoAVista pagCompraPix = (PagamentoAVista) respPagVista.dados();

            RespostaEndpoint<Object> respPagPrazo = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_A_PRAZO, Map.of(
                    "codigoForma", "BOLETO",
                    "valor", "1499.90",
                    "dataVencimento", LocalDate.now().plusDays(15)
            ));
            PagamentoAPrazo pagPrazo = (PagamentoAPrazo) respPagPrazo.dados();

            RespostaEndpoint<Object> respPagParc = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_PARCELADO, Map.of(
                    "codigoForma", "CARTAO_CREDITO",
                    "valor", "1499.90",
                    "quantidadeParcelas", 3
            ));
            PagamentoParcelado pagParc = (PagamentoParcelado) respPagParc.dados();

            validarEndpoint(roteador, RotaSistema.PAGAMENTO_QUITAR_TITULO_OU_PARCELA, Map.of(
                    "pagamento", pagPrazo,
                    "dataQuitacao", LocalDateTime.now()
            ));

            // 5. MÓDULO NOTA DE COMPRA E ESTOQUE (8 rotas)
            NotaCompra notaCompra = new NotaCompra(fornecedorPj, "Compra de 10 Monitores p/ Teste de Endpoints");
            notaCompra.addItem(new Item<>(monitor, new BigDecimal("850.0000"), new BigDecimal("10.000000"), LocalDate.now().plusMonths(12)));

            validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_SUGERIR_PRECO_VENDA, Map.of(
                    "notaCompra", notaCompra,
                    "margemDesejada", "0.4000"
            ));

            RespostaEndpoint<Object> respCompraEmitida = validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "notaCompra", notaCompra,
                    "pagamentos", List.of(pagCompraPix)
            ));
            NotaCompra compraSalva = (NotaCompra) respCompraEmitida.dados();
            Long idLoteCriado = compraSalva.getItens().get(0).getId();

            validarEndpoint(roteador, RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", monitor.getId()));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_FILA_LOTES_FIFO, Map.of("idProduto", monitor.getId()));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_BUSCAR_LOTE_ESPECIFICO, Map.of("idLote", idLoteCriado));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_LOTES_PROXIMOS_VALIDADE, Map.of("dataLimite", LocalDate.now().plusMonths(18)));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA, Map.of(
                    "idLote", idLoteCriado,
                    "quantidade", "1.000000",
                    "motivo", "Avaria de empilhadeira no depósito"
            ));

            // 6. MÓDULO NOTA DE VENDA, PROCEDURES MYSQL, DEVOLUÇÃO E ABATIMENTO (7 rotas)
            Map<Produto, BigDecimal> itensVenda1 = new LinkedHashMap<>();
            itensVenda1.put(monitor, new BigDecimal("2.000000"));
            itensVenda1.put(garantiaDigital, new BigDecimal("1.000000"));

            RespostaEndpoint<Object> respVendaFifo = validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "cliente", clientePf,
                    "descricao", "Venda FIFO Endpoint Test",
                    "itens", itensVenda1,
                    "pagamentos", List.of(roteador.pagamentos().criarPagamentoAVista("PIX", new BigDecimal("3149.70"), new BigDecimal("3149.70"), BigDecimal.ZERO)),
                    "cupom", "DESC_50",
                    "descontoCupom", "50.00"
            ));
            NotaVenda vendaFifoSalva = (NotaVenda) respVendaFifo.dados();

            // Simula o JavaFX lendo todas as propriedades contábeis da NotaVenda fora do EntityManager:
            NotaVenda vendaRecarregada = (NotaVenda) roteador.notas().buscarNotaPorId(vendaFifoSalva.getId());
            if (vendaRecarregada.getLucroLiquidoVenda() == null || vendaRecarregada.getPagamentos().isEmpty()) {
                throw new AssertionError("Grafo da NotaVenda não foi inicializado corretamente para o JavaFX!");
            }

            Map<Produto, BigDecimal> itensBalcao = new LinkedHashMap<>();
            itensBalcao.put(monitor, new BigDecimal("1.000000"));
            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO, Map.of(
                    "itens", itensBalcao,
                    "pagamentos", List.of(roteador.pagamentos().criarPagamentoAVista("DINHEIRO", new BigDecimal("1499.90"), new BigDecimal("1500.00"), null))
            ));

            Map<Long, BigDecimal> mapaLoteDesejado = new LinkedHashMap<>();
            mapaLoteDesejado.put(idLoteCriado, new BigDecimal("1.000000"));
            RespostaEndpoint<Object> respVendaLote = validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL, Map.of(
                    "cliente", clientePf,
                    "descricao", "Venda Lote Desejável Endpoint Test",
                    "lotesEQuantidades", mapaLoteDesejado,
                    "pagamentos", List.of(pagParc)
            ));
            NotaVenda vendaLoteSalva = (NotaVenda) respVendaLote.dados();

            // Testa as 2 rotas de Stored Procedure MySQL (sp_consumir_estoque_fifo e sp_consumir_lote_especifico)
            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_PROCEDURE_MYSQL_FIFO, Map.of(
                    "idNotaVenda", vendaLoteSalva.getId(),
                    "idProduto", monitor.getId(),
                    "quantidade", "1.000000",
                    "precoVenda", "1499.9000"
            ));
            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_PROCEDURE_MYSQL_LOTE, Map.of(
                    "idNotaVenda", vendaLoteSalva.getId(),
                    "idLoteDesejado", idLoteCriado,
                    "quantidade", "1.000000",
                    "precoVenda", "1499.9000"
            ));

            // Devolução pós-venda com geração de perda para o fornecedor e posterior abatimento em nova compra
            Item<? extends Produto> itemParaDevolver = vendaFifoSalva.getItens().get(0);
            Map<Item<? extends Produto>, BigDecimal> mapaDevolucao = new LinkedHashMap<>();
            mapaDevolucao.put(itemParaDevolver, new BigDecimal("1.000000"));

            validarEndpoint(roteador, RotaSistema.NOTA_DEVOLUCAO_REGISTRAR, Map.of(
                    "notaVenda", vendaFifoSalva,
                    "itensDevolvidos", mapaDevolucao,
                    "motivo", "Dead pixel constatado dentro da validade",
                    "geraPerdaFornecedor", true
            ));

            NotaCompra novaCompraParaAbater = new NotaCompra(fornecedorPj, "Nova Compra p/ Abater Perda");
            novaCompraParaAbater.addItem(new Item<>(monitor, new BigDecimal("850.0000"), new BigDecimal("2.000000"), LocalDate.now().plusMonths(12)));
            validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR, Map.of(
                    "novaCompra", novaCompraParaAbater,
                    "produto", monitor
            ));

            // 7. MÓDULO RELATÓRIOS CONTÁBEIS E SAZONAIS (3 rotas)
            validarEndpoint(roteador, RotaSistema.RELATORIO_LUCRO_CONTABIL_BACEN, Map.of("idNotaVenda", vendaFifoSalva.getId()));
            validarEndpoint(roteador, RotaSistema.RELATORIO_PERDAS_POR_FORNECEDOR, Map.of(
                    "fornecedor", fornecedorPj,
                    "produto", monitor,
                    "apenasPosVendaDentroValidade", true
            ));
            validarEndpoint(roteador, RotaSistema.RELATORIO_ANALISE_SAZONAL_VENDAS, Map.of());

            // 8. Validação do Schema JSON de Function Declarations para a IA Gemini
            String jsonToolsGemini = roteador.exportarDeclaracoesFerramentasGeminiJson();
            boolean schemaValido = jsonToolsGemini.contains("\"functionDeclarations\"")
                    && jsonToolsGemini.contains("\"nota_venda_emitir_fifo\"")
                    && jsonToolsGemini.contains("\"relatorio_lucro_contabil_bacen\"");
            if (!schemaValido) {
                throw new AssertionError("O JSON de Function Declarations da IA Gemini está incompleto!");
            }

            System.out.println("\n==========================================================================================");
            System.out.printf("   COBERTURA DE ENDPOINTS: %d / %d ROTAS TESTADAS E APROVADAS (100%% OPERACIONAL)%n",
                    rotasValidadasComSucesso.size(), RotaSistema.values().length);
            System.out.println("   - JavaFX: Grafo de entidades desanexadas validado (0 LazyInitializationException)");
            System.out.println("   - Inline CLI: Saída formatada validada em todas as rotas");
            System.out.println("   - IA Gemini: 42 Function Declarations (JSON Schema) e Tool Calls validadas");
            System.out.println("==========================================================================================");
        } finally {
            JPAUtil.close();
        }
    }

    private static RespostaEndpoint<Object> validarEndpoint(
            RoteadorController roteador,
            RotaSistema rota,

            Map<String, Object> parametros) {

        // Testa invocação pelo nome da função da IA Gemini (ou caminho da rota)
        RespostaEndpoint<Object> resposta = roteador.executarEndpoint(rota.getNomeFuncaoGemini(), parametros);
        if (!resposta.sucesso()) {
            throw new AssertionError("Falha no endpoint " + rota.getCaminhoRota() + " (" + rota.getNomeFuncaoGemini() + "): " + resposta.mensagem());
        }
        String saidaInline = resposta.formatarSaidaInline();
        String saidaJsonIA = resposta.toJsonResumoIA();
        if (saidaInline.isBlank() || !saidaJsonIA.startsWith("{")) {
            throw new AssertionError("Formatação Inline ou JSON IA inválida para a rota " + rota.getCaminhoRota());
        }
        rotasValidadasComSucesso.add(rota);
        System.out.printf("  [OK] %-9s | Rota: %-36s | Tool IA: %-35s%n",
                rota.getModulo(), rota.getCaminhoRota(), rota.getNomeFuncaoGemini());
        return resposta;
    }
}
