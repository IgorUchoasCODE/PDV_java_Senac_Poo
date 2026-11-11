package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bateria completa de validação de TODAS as 72 Rotas/Endpoints dos Controllers
 * cobrindo 100% do enum RotaSistema:
 * 1. Módulo Sistema (Inicialização)
 * 2. Módulo Produto (CRUD Completo, Dimensões, Digitais, Kits, Galeria de Imagens)
 * 3. Módulo Pessoa (Cliente Padrão, PF, PJ, Fornecedores, Registros, Histórico 360º, Exclusão)
 * 4. Módulo Pagamento (Formas, Regras MDR/Juros BACEN, Criação À Vista/Prazo/Parcelado, Quitação)
 * 5. Módulo Estoque & Lotes FIFO (Saldos, Filas, Buscas, Validade, Perdas)
 * 6. Módulo Notas Operacionais (Compra, Venda FIFO, Venda Lote, Balcão, Devolução, Abatimento)
 * 7. Módulo Relatórios Contábeis (CMV, Perdas, Análise Sazonal)
 * 8. Módulo Estatísticas, Eventos e Fatores Externos
 */
public class TesteEndpointsControllers {

    private static final Set<RotaSistema> rotasValidadasComSucesso = EnumSet.noneOf(RotaSistema.class);
    private static final Map<RotaSistema, String> falhas = new LinkedHashMap<>();

    public static void main(String[] args) {
        try {
            RoteadorController roteador = new RoteadorController();
            String sufixo = String.valueOf(System.currentTimeMillis() % 100000);

            System.out.println("==========================================================================================");
            System.out.printf("   VALIDAÇÃO COMPLETA END-TO-END DE TODAS AS %d ROTAS DO ROTA SISTEMA%n", RotaSistema.values().length);
            System.out.println("==========================================================================================\n");

            // ==================== 1. MÓDULO SISTEMA ====================
            validarEndpoint(roteador, RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

            // ==================== 2. MÓDULO PRODUTO (CRUD & RECURSOS) ====================
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_LISTAR, Map.of());
            String siglaUnidade = "D" + (sufixo.length() >= 2 ? sufixo.substring(0, 2) : "1");
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_CADASTRAR, Map.of(
                    "sigla", siglaUnidade, "nome", "Dúzia " + sufixo, "descricao", "12 unidades", "base", "1.000000"
            ));
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_ATUALIZAR, Map.of(
                    "sigla", siglaUnidade, "nome", "Dúzia Atualizada " + sufixo, "descricao", "12 un atualizada", "base", "1.000000"
            ));
            validarEndpoint(roteador, RotaSistema.PRODUTO_UNIDADE_EXCLUIR, Map.of("sigla", siglaUnidade));

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
            ProdutoFisico monitor = respProdFisico.dados() instanceof ProdutoFisico pf ? pf : null;
            Long idMonitor = monitor != null ? monitor.getId() : 1L;

            validarEndpoint(roteador, RotaSistema.PRODUTO_FISICO_ATUALIZAR_DIMENSOES, Map.of(
                    "idProduto", idMonitor,
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
            ProdutoDigital garantiaDigital = respProdDigital.dados() instanceof ProdutoDigital pd ? pd : null;

            RespostaEndpoint<Object> respKit = validarEndpoint(roteador, RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Combo Monitor + Garantia #" + sufixo,
                    "descricao", "Kit Monitor 27 + Garantia",
                    "precoVenda", "1599.90",
                    "siglaUm", "UN"
            ));
            ProdutoFisico kitMonitor = respKit.dados() instanceof ProdutoFisico km ? km : null;
            Long idKit = kitMonitor != null ? kitMonitor.getId() : idMonitor;

            validarEndpoint(roteador, RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", idKit,
                    "idProdutoComponente", idMonitor,
                    "quantidade", "1.000000"
            ));
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_LISTAR, Map.of());
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("id", idMonitor));
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("nome", "Monitor Gamer"));
            validarEndpoint(roteador, RotaSistema.PRODUTO_BUSCAR_UNICO, Map.of("nome", "Monitor Gamer"));
            validarEndpoint(roteador, RotaSistema.PRODUTO_ESTOQUE_VALIDO_LISTAR, Map.of());
            validarEndpoint(roteador, RotaSistema.PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO, Map.of("id", idMonitor));

            // Validação de busca por palavras parciais ("ov branc tip a") e iniciais ("o b a")
            roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Ovo Branco Tipo A",
                    "descricao", "Cartela de ovos brancos tipo A",
                    "precoVenda", "18.90",
                    "siglaUm", "UN"
            ));
            RespostaEndpoint<Object> respParcial1 = validarEndpoint(roteador, RotaSistema.PRODUTO_BUSCAR_UNICO, Map.of("nome", "ov branc tip a"));
            if (respParcial1.dados() == null) {
                throw new AssertionError("Busca parcial 'ov branc tip a' deveria encontrar 'Ovo Branco Tipo A'!");
            }
            RespostaEndpoint<Object> respIniciais1 = validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("nome", "o b a"));
            if (respIniciais1.dados() == null) {
                throw new AssertionError("Busca por iniciais 'o b a' deveria encontrar 'Ovo Branco Tipo A'!");
            }

            // Cadastra segundo produto compatível com "o b a" para validar retorno de lista para escolha do usuário
            roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Ovo Branco Tipo A Especial #" + sufixo,
                    "descricao", "Cartela especial",
                    "precoVenda", "22.90",
                    "siglaUm", "UN"
            ));
            RespostaEndpoint<Object> respListaEscolha = validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_BUSCAR, Map.of("nome", "o b a"));
            if (!(respListaEscolha.dados() instanceof List<?> listaOpcoes) || listaOpcoes.size() < 2) {
                throw new AssertionError("Busca ambígua 'o b a' deveria retornar lista de opções para escolha do usuário!");
            }

            // Atualização cadastral de produto (CRUD - nome/descrição)
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_ATUALIZAR, Map.of(
                    "idProduto", idMonitor,
                    "novoNome", "Monitor Gamer 27 Pol PRO #" + sufixo,
                    "descricao", "Monitor IPS 165Hz com HDR e G-Sync"
            ));

            // Atualização de preço de venda (CRUD - preço)
            validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, Map.of(
                    "idProduto", idMonitor,
                    "novoPreco", "1399.90"
            ));

            // Produto temporário para validar exclusão (CRUD - exclusão)
            RespostaEndpoint<Object> respProdTemp = roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Produto Temporario Para Excluir #" + sufixo,
                    "descricao", "Item descartável",
                    "precoVenda", "10.00",
                    "siglaUm", "UN"
            ));
            if (respProdTemp.dados() instanceof Produto pt) {
                validarEndpoint(roteador, RotaSistema.PRODUTO_CATALOGO_EXCLUIR, Map.of("idProduto", pt.getId()));
            }

            // Galeria de Imagens do Produto (5 rotas)
            String base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
            RespostaEndpoint<Object> respImg = validarEndpoint(roteador, RotaSistema.PRODUTO_IMAGEM_ADICIONAR, Map.of(
                    "idProduto", idMonitor,
                    "base64", base64Png,
                    "nomeOriginal", "monitor_teste_" + sufixo + ".png",
                    "isPrincipal", true
            ));
            validarEndpoint(roteador, RotaSistema.PRODUTO_IMAGEM_LISTAR, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.PRODUTO_IMAGEM_PRINCIPAL_OBTER, Map.of("idProduto", idMonitor));

            Long idImagemCriada = null;
            if (respImg.dados() instanceof ProdutoImagem pi) {
                idImagemCriada = pi.getId();
            } else if (respImg.dados() instanceof Map<?, ?> m && m.get("id") != null) {
                idImagemCriada = Long.valueOf(m.get("id").toString());
            }
            if (idImagemCriada != null) {
                validarEndpoint(roteador, RotaSistema.PRODUTO_IMAGEM_DEFINIR_PRINCIPAL, Map.of("idProduto", idMonitor, "idImagem", idImagemCriada));
                validarEndpoint(roteador, RotaSistema.PRODUTO_IMAGEM_REMOVER, Map.of("idProduto", idMonitor, "idImagem", idImagemCriada));
            }

            // ==================== 3. MÓDULO PESSOA (CLIENTES, FORNECEDORES, HISTÓRICO) ====================
            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PADRAO_OBTER, Map.of());

            // Cadastro Simples (sem documento obrigatório)
            RespostaEndpoint<Object> respCliSimples = validarEndpoint(roteador, RotaSistema.PESSOA_SIMPLES_CADASTRAR, Map.of(
                    "nome", "Cliente Balcão Rápido #" + sufixo,
                    "isCliente", true,
                    "isFornecedor", false
            ));
            Pessoa pessoaSimples = respCliSimples.dados() instanceof Pessoa ps ? ps : null;

            RespostaEndpoint<Object> respCliPf = validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR, Map.of(
                    "nome", "Roberto Almeida #" + sufixo,
                    "cpf", "777.888." + sufixo.substring(0, 3) + "-99",
                    "limite", "8000.00"
            ));
            PessoaFisica clientePf = respCliPf.dados() instanceof PessoaFisica pf ? pf : null;
            Long idCliPf = clientePf != null ? clientePf.getId() : (pessoaSimples != null ? pessoaSimples.getId() : 1L);

            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR, Map.of(
                    "razaoSocial", "Tech Office Manaus LTDA #" + sufixo,
                    "cnpj", "88.999." + sufixo.substring(0, 3) + "/0001-77",
                    "limite", "25000.00"
            ));

            validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE, Map.of(
                    "idPessoa", idCliPf,
                    "novoLimite", "12000.00"
            ));

            validarEndpoint(roteador, RotaSistema.PESSOA_DADOS_ATUALIZAR, Map.of(
                    "idPessoa", idCliPf,
                    "novoNome", "Roberto Almeida Silva #" + sufixo
            ));

            RespostaEndpoint<Object> respFornPj = validarEndpoint(roteador, RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR, Map.of(
                    "razaoSocial", "Fábrica Display Amazônia S.A. #" + sufixo,
                    "cnpj", "99.111." + sufixo.substring(0, 3) + "/0001-88"
            ));
            PessoaJuridica fornecedorPj = respFornPj.dados() instanceof PessoaJuridica fj ? fj : null;

            validarEndpoint(roteador, RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR, Map.of(
                    "nome", "Produtor Rural João #" + sufixo,
                    "cpf", "666.555." + sufixo.substring(0, 3) + "-11"
            ));

            validarEndpoint(roteador, RotaSistema.PESSOA_REGISTRO_ADICIONAR, Map.of(
                    "idPessoa", idCliPf,
                    "tipoDocumento", "CNH",
                    "numeroRegistro", "04599887766" + sufixo
            ));

            validarEndpoint(roteador, RotaSistema.PESSOA_LISTAR_CLIENTES, Map.of());
            validarEndpoint(roteador, RotaSistema.PESSOA_LISTAR_FORNECEDORES, Map.of());
            validarEndpoint(roteador, RotaSistema.PESSOA_BUSCAR, Map.of("termo", "Roberto"));
            validarEndpoint(roteador, RotaSistema.PESSOA_BUSCAR_UNICA, Map.of("idPessoa", idCliPf));
            validarEndpoint(roteador, RotaSistema.PESSOA_REGISTRO_BUSCAR_UNICO, Map.of("tipoDocumento", "CNH", "numeroRegistro", "04599887766" + sufixo));
            validarEndpoint(roteador, RotaSistema.PESSOA_HISTORICO_COMPLETO, Map.of("idPessoa", idCliPf));

            // Exclusão de pessoa temporária
            if (pessoaSimples != null) {
                validarEndpoint(roteador, RotaSistema.PESSOA_CLIENTE_EXCLUIR, Map.of("idPessoa", pessoaSimples.getId()));
            }

            // ==================== 4. MÓDULO PAGAMENTO (REGRAS CONTÁBEIS & TÍTULOS) ====================
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_LISTAR_ATIVAS, Map.of());
            Map<String, Object> paramsForma = new LinkedHashMap<>();
            paramsForma.put("codigo", "PAG_TESTE_" + sufixo);
            paramsForma.put("nome", "Forma Pagamento Teste " + sufixo);
            paramsForma.put("descricao", "Para teste de rota");
            paramsForma.put("aVista", true);
            paramsForma.put("aPrazo", false);
            paramsForma.put("parcelado", false);
            paramsForma.put("maxParcelas", 1);
            paramsForma.put("maxParcelasSemJuros", 1);
            paramsForma.put("diasCompensacao", 0);
            paramsForma.put("taxaOperadoraPercentual", "0.00");
            paramsForma.put("tarifaFixaOperadora", "0.00");
            paramsForma.put("descontoAVistaPercentual", "0.00");
            paramsForma.put("taxaJurosMensal", "0.00");
            paramsForma.put("taxaJurosDiarioMora", "0.00");
            paramsForma.put("multaAtrasoPercentual", "0.00");
            paramsForma.put("valorMinimoParcela", "0.00");
            paramsForma.put("exigeLimiteCredito", false);
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_CADASTRAR, paramsForma);
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
            validarEndpoint(roteador, RotaSistema.PAGAMENTO_FORMA_EXCLUIR, Map.of("codigo", "PAG_TESTE_" + sufixo));

            RespostaEndpoint<Object> respPagVista = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_A_VISTA, Map.of(
                    "codigoForma", "PIX",
                    "valor", "8500.00",
                    "valorRecebido", "8500.00",
                    "descontoCustomizado", "0.00"
            ));
            PagamentoAVista pagCompraPix = respPagVista.dados() instanceof PagamentoAVista pv ? pv : null;

            RespostaEndpoint<Object> respPagPrazo = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_A_PRAZO, Map.of(
                    "codigoForma", "BOLETO",
                    "valor", "1499.90",
                    "dataVencimento", LocalDate.now().plusDays(15)
            ));
            PagamentoAPrazo pagPrazo = respPagPrazo.dados() instanceof PagamentoAPrazo pp ? pp : null;

            RespostaEndpoint<Object> respPagParc = validarEndpoint(roteador, RotaSistema.PAGAMENTO_CRIAR_PARCELADO, Map.of(
                    "codigoForma", "CARTAO_CREDITO",
                    "valor", "1499.90",
                    "quantidadeParcelas", 3
            ));
            PagamentoParcelado pagParc = respPagParc.dados() instanceof PagamentoParcelado pparc ? pparc : null;

            validarEndpoint(roteador, RotaSistema.PAGAMENTO_QUITAR_TITULO_OU_PARCELA, Map.of(
                    "pagamento", pagPrazo,
                    "dataQuitacao", LocalDateTime.now()
            ));

            // ==================== 5. MÓDULO NOTA DE COMPRA E ESTOQUE ====================
            NotaCompra notaCompra = new NotaCompra(fornecedorPj, "Compra de 10 Monitores p/ Teste de Endpoints");
            if (monitor != null) {
                notaCompra.addItem(new Item<>(monitor, new BigDecimal("850.0000"), new BigDecimal("10.000000"), LocalDate.now().plusMonths(12)));
            }

            validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_SUGERIR_PRECO_VENDA, Map.of(
                    "notaCompra", notaCompra,
                    "margemDesejada", "0.4000"
            ));

            RespostaEndpoint<Object> respCompraEmitida = validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "notaCompra", notaCompra,
                    "pagamentos", List.of(pagCompraPix)
            ));
            NotaCompra compraSalva = respCompraEmitida.dados() instanceof NotaCompra nc ? nc : null;
            Long idLoteCriado = (compraSalva != null && !compraSalva.getItens().isEmpty()) ? compraSalva.getItens().get(0).getId() : 1L;

            validarEndpoint(roteador, RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_FILA_LOTES_FIFO, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_LOTES_VALIDOS_LISTAR, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_LOTE_VALIDO_BUSCAR_UNICO, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_BUSCAR_LOTE_ESPECIFICO, Map.of("idLote", idLoteCriado));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_LOTES_PROXIMOS_VALIDADE, Map.of("dataLimite", LocalDate.now().plusMonths(18)));
            validarEndpoint(roteador, RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA, Map.of(
                    "idLote", idLoteCriado,
                    "quantidade", "1.000000",
                    "motivo", "Avaria de empilhadeira no depósito"
            ));

            // ==================== 6. MÓDULO NOTA DE VENDA, PROCEDURES, DEVOLUÇÃO E ABATIMENTO ====================
            Map<Produto, BigDecimal> itensVenda1 = new LinkedHashMap<>();
            if (monitor != null) itensVenda1.put(monitor, new BigDecimal("2.000000"));
            if (garantiaDigital != null) itensVenda1.put(garantiaDigital, new BigDecimal("1.000000"));

            RespostaEndpoint<Object> respVendaFifo = validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "cliente", clientePf != null ? clientePf : roteador.pessoas().obterClientePadraoNaoRegistrado(),
                    "descricao", "Venda FIFO Endpoint Test",
                    "itens", itensVenda1,
                    "pagamentos", List.of(roteador.pagamentos().criarPagamentoAVista("PIX", new BigDecimal("3149.70"), new BigDecimal("3149.70"), BigDecimal.ZERO)),
                    "cupom", "DESC_50",
                    "descontoCupom", "50.00"
            ));
            NotaVenda vendaFifoSalva = respVendaFifo.dados() instanceof NotaVenda nv ? nv : null;

            Map<Produto, BigDecimal> itensBalcao = new LinkedHashMap<>();
            if (monitor != null) itensBalcao.put(monitor, new BigDecimal("1.000000"));
            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO, Map.of(
                    "itens", itensBalcao,
                    "pagamentos", List.of(roteador.pagamentos().criarPagamentoAVista("DINHEIRO", new BigDecimal("1499.90"), new BigDecimal("1500.00"), null))
            ));

            Map<Long, BigDecimal> mapaLoteDesejado = new LinkedHashMap<>();
            mapaLoteDesejado.put(idLoteCriado, new BigDecimal("1.000000"));
            RespostaEndpoint<Object> respVendaLote = validarEndpoint(roteador, RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL, Map.of(
                    "cliente", clientePf != null ? clientePf : roteador.pessoas().obterClientePadraoNaoRegistrado(),
                    "descricao", "Venda Lote Desejável Endpoint Test",
                    "lotesEQuantidades", mapaLoteDesejado,
                    "pagamentos", List.of(pagParc)
            ));
            NotaVenda vendaLoteSalva = respVendaLote.dados() instanceof NotaVenda nl ? nl : vendaFifoSalva;
            Long idVendaLote = vendaLoteSalva != null ? vendaLoteSalva.getId() : 1L;

            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_PROCEDURE_MYSQL_FIFO, Map.of(
                    "idNotaVenda", idVendaLote,
                    "idProduto", idMonitor,
                    "quantidade", "1.000000",
                    "precoVenda", "1499.9000"
            ));
            validarEndpoint(roteador, RotaSistema.NOTA_VENDA_PROCEDURE_MYSQL_LOTE, Map.of(
                    "idNotaVenda", idVendaLote,
                    "idLoteDesejado", idLoteCriado,
                    "quantidade", "1.000000",
                    "precoVenda", "1499.9000"
            ));

            if (vendaFifoSalva != null && !vendaFifoSalva.getItens().isEmpty()) {
                Item<? extends Produto> itemParaDevolver = vendaFifoSalva.getItens().get(0);
                Map<Item<? extends Produto>, BigDecimal> mapaDevolucao = new LinkedHashMap<>();
                mapaDevolucao.put(itemParaDevolver, new BigDecimal("1.000000"));

                validarEndpoint(roteador, RotaSistema.NOTA_DEVOLUCAO_REGISTRAR, Map.of(
                        "notaVenda", vendaFifoSalva,
                        "itensDevolvidos", mapaDevolucao,
                        "motivo", "Dead pixel constatado dentro da validade",
                        "geraPerdaFornecedor", true
                ));
            }

            NotaCompra novaCompraParaAbater = new NotaCompra(fornecedorPj, "Nova Compra p/ Abater Perda");
            if (monitor != null) {
                novaCompraParaAbater.addItem(new Item<>(monitor, new BigDecimal("850.0000"), new BigDecimal("2.000000"), LocalDate.now().plusMonths(12)));
                validarEndpoint(roteador, RotaSistema.NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR, Map.of(
                        "novaCompra", novaCompraParaAbater,
                        "produto", monitor
                ));
            }

            // ==================== 7. MÓDULO RELATÓRIOS CONTÁBEIS E SAZONAIS ====================
            Long idNotaVendaRelatorio = vendaFifoSalva != null ? vendaFifoSalva.getId() : 1L;
            validarEndpoint(roteador, RotaSistema.NOTA_BUSCAR_UNICA, Map.of("idNota", idNotaVendaRelatorio));
            validarEndpoint(roteador, RotaSistema.NOTA_LISTAR_PADRAO, Map.of("tipoNota", "VENDA"));
            validarEndpoint(roteador, RotaSistema.NOTA_DEFINIR_DATA, Map.of(
                    "idNota", idNotaVendaRelatorio,
                    "data", LocalDateTime.now().toString()
            ));
            validarEndpoint(roteador, RotaSistema.BUSCA_PADRAO_IA, Map.of(
                    "entidade", "PRODUTO_ESTOQUE_VALIDO",
                    "id", idMonitor,
                    "unico", true
            ));
            validarEndpoint(roteador, RotaSistema.RELATORIO_LUCRO_CONTABIL_BACEN, Map.of("idNotaVenda", idNotaVendaRelatorio));
            validarEndpoint(roteador, RotaSistema.RELATORIO_PERDAS_POR_FORNECEDOR, Map.of(
                    "fornecedor", fornecedorPj,
                    "produto", monitor,
                    "apenasPosVendaDentroValidade", true
            ));
            validarEndpoint(roteador, RotaSistema.RELATORIO_ANALISE_SAZONAL_VENDAS, Map.of());

            // ==================== 8. MÓDULO ESTATÍSTICAS E FATORES EXTERNOS ====================
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_DEMANDA_MENSAL_CLIENTE, Map.of(
                    "idCliente", idCliPf, "idProduto", idMonitor, "mes", 11
            ));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_PERFIL_CLIENTE, Map.of("idCliente", idCliPf));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_RANKING_FORNECEDORES, Map.of(
                    "idProduto", idMonitor, "clima", "CALOR", "regimeRio", "NORMAL"
            ));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_MOTIVOS_PERDA_PREMATURA, Map.of(
                    "idProduto", idMonitor, "idFornecedor", fornecedorPj != null ? fornecedorPj.getId() : 1L
            ));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_DESTAQUES_SAZONAIS, Map.of("limite", 10));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_PICOS_DIAS_EVENTOS, Map.of());
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS, Map.of(
                    "dataReferencia", LocalDate.now(), "horizonteDias", 30
            ));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_CAUSAS_EXTERNAS_NOTA, Map.of(
                    "idNota", idNotaVendaRelatorio, "idProduto", idMonitor
            ));
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_RECALCULAR_FATORES, Map.of());
            validarEndpoint(roteador, RotaSistema.ESTATISTICA_FATORES_PRODUTO, Map.of("idProduto", idMonitor));
            validarEndpoint(roteador, RotaSistema.FATOR_EXTERNO_EVENTOS_LISTAR, Map.of("limite", 10));
            validarEndpoint(roteador, RotaSistema.FATOR_EXTERNO_RESOLVER_PENDENCIAS, Map.of());
            validarEndpoint(roteador, RotaSistema.FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS, Map.of("idNota", idNotaVendaRelatorio));

            // ==================== 9. VALIDAÇÃO DO SCHEMA JSON DE FERRAMENTAS GEMINI ====================
            String jsonToolsGemini = roteador.exportarDeclaracoesFerramentasGeminiJson();
            boolean schemaValido = jsonToolsGemini.contains("\"functionDeclarations\"")
                    && jsonToolsGemini.contains("\"nota_venda_emitir_fifo\"")
                    && jsonToolsGemini.contains("\"relatorio_lucro_contabil_bacen\"");
            if (!schemaValido) {
                throw new AssertionError("O JSON de Function Declarations da IA Gemini está incompleto!");
            }

            // ==================== RELATÓRIO FINAL CONSOLIDADO ====================
            System.out.println("\n==========================================================================================");
            System.out.printf("   COBERTURA DE ROTAS: %d / %d ROTAS HOMOLOGADAS COM SUCESSO%n",
                    rotasValidadasComSucesso.size(), RotaSistema.values().length);
            if (!falhas.isEmpty()) {
                System.out.printf("   ⚠️ ATENÇÃO: %d ROTAS APRESENTARAM ERROS:%n", falhas.size());
                falhas.forEach((r, err) -> System.out.printf("     - [%s] %s -> %s%n", r.getModulo(), r.getCaminhoRota(), err));
            } else {
                System.out.println("   ✔ 100% DAS ROTAS FORAM EXECUTADAS SEM ERROS!");
            }
            System.out.println("==========================================================================================");

            if (!falhas.isEmpty()) {
                throw new AssertionError("Houve falhas em " + falhas.size() + " rotas do sistema.");
            }

        } finally {
            JPAUtil.close();
        }
    }

    private static RespostaEndpoint<Object> validarEndpoint(
            RoteadorController roteador,
            RotaSistema rota,
            Map<String, Object> parametros) {

        try {
            RespostaEndpoint<Object> resposta = roteador.executarEndpoint(rota.getNomeFuncaoGemini(), parametros);
            if (!resposta.sucesso()) {
                falhas.put(rota, resposta.mensagem());
                System.out.printf("  [FALHA] %-9s | Rota: %-36s | Erro: %s%n",
                        rota.getModulo(), rota.getCaminhoRota(), resposta.mensagem());
                return resposta;
            }
            rotasValidadasComSucesso.add(rota);
            System.out.printf("  [OK]    %-9s | Rota: %-36s | Tool IA: %-35s%n",
                    rota.getModulo(), rota.getCaminhoRota(), rota.getNomeFuncaoGemini());
            return resposta;
        } catch (Throwable t) {
            falhas.put(rota, t.getMessage());
            System.out.printf("  [FALHA] %-9s | Rota: %-36s | Exceção: %s%n",
                    rota.getModulo(), rota.getCaminhoRota(), t.getMessage());
            return RespostaEndpoint.erro("ERRO", "Exceção na rota: " + t.getMessage());
        }
    }
}
