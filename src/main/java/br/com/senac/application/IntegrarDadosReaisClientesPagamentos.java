package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.csv.LeitorCsv;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.service.InicializadorSistemaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Script dedicado para injeção do CSV real ({@code testesDadosReais18M--1.csv}) com suporte a:
 * <ul>
 *   <li><b>Entradas ({@code entrada})</b>: agrupadas por <b>Fornecedor + Data</b> em uma única {@link NotaCompra} (em Caixas de 12 cartelas).</li>
 *   <li><b>Vendas de Clientes Cadastrados</b>: agrupadas por <b>Cliente + Data</b> em uma única {@link NotaVenda} a prazo ({@code CREDIARIO}), quitada FIFO conforme chegam linhas de {@code pagamento} do cliente.</li>
 *   <li><b>Vendas do Cliente Padrão ({@code aleatório})</b>: <b>não</b> são agrupadas — cada linha gera uma nova {@link NotaVenda} individual já com seu {@link NotaPagamento} vinculado (usando a forma de pagamento do dia ou {@code DINHEIRO}).</li>
 * </ul>
 */
public class IntegrarDadosReaisClientesPagamentos {

    private static final int DURABILIDADE_CARTELA_DIAS = 15;
    private static final BigDecimal CARTELAS_POR_CAIXA = new BigDecimal("12");
    private static final Path CAMINHO_PADRAO_CSV = Paths.get(
            System.getProperty("user.home"), "OneDrive", "Documentos", "testesDadosReais18M--1.csv"
    );

    private enum TipoLinhaCsv {
        ENTRADA,
        VENDA,
        PERDA,
        PAGAMENTO
    }

    private record RegistroCsv(
            int linhaOriginal,
            LocalDate data,
            String modalidade,
            String pessoaNome,
            boolean clientePadrao,
            String produtoOuForma,
            BigDecimal quantidade,
            BigDecimal valorTotal,
            BigDecimal lucro,
            TipoLinhaCsv tipo
    ) {}

    public record ResumoInjecao(
            int totalLinhasProcessadas,
            int fornecedoresCadastrados,
            int clientesCadastrados,
            int produtosCadastrados,
            int notasCompraEmitidas,
            int notasVendaClientesAgrupadas,
            int notasVendaClientePadraoIndividuais,
            int baixasPerdaExecutadas,
            int pagamentosClientesQuitados,
            int notasCompraQuitadasNaFila,
            int ajustesNegativosIgnorados,
            int avisosOuFalhas,
            BigDecimal totalTaxasPagamentoDescontadas,
            BigDecimal saldoCaixaDinheiroRestante
    ) {
        public String formatar() {
            return String.format(
                    "=== RESUMO INJEÇÃO DE DADOS REAIS ===%n" +
                    "Linhas válidas processadas: %d (ajustes negativos ignorados: %d)%n" +
                    "Fornecedores: %d | Clientes cadastrados: %d | Produtos (cartelas): %d%n" +
                    "Notas de Compra emitidas (Fornecedor+Data): %d%n" +
                    "Notas de Venda (Clientes cadastrados, Cliente+Data): %d%n" +
                    "Notas de Venda (Cliente padrão, individuais c/ pagamento): %d%n" +
                    "Baixas de Perda executadas (valor=0, lucro<0, qtd>0): %d%n" +
                    "Pagamentos de clientes cadastrados quitados (FIFO): %d%n" +
                    "Taxas de pagamento descontadas (lucro < 0): R$ %s%n" +
                    "Notas de Compra quitadas na fila: %d | Saldo caixa dinheiro: R$ %s%n" +
                    "Avisos/Falhas pontuais: %d",
                    totalLinhasProcessadas, ajustesNegativosIgnorados,
                    fornecedoresCadastrados, clientesCadastrados, produtosCadastrados,
                    notasCompraEmitidas, notasVendaClientesAgrupadas, notasVendaClientePadraoIndividuais,
                    baixasPerdaExecutadas, pagamentosClientesQuitados,
                    MoedaContabilBacen.arredondarMoeda(totalTaxasPagamentoDescontadas),
                    notasCompraQuitadasNaFila,
                    MoedaContabilBacen.arredondarMoeda(saldoCaixaDinheiroRestante),
                    avisosOuFalhas
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Path csvPath = args != null && args.length > 0
                ? Paths.get(args[0])
                : CAMINHO_PADRAO_CSV;
        boolean resetarBanco = args == null || args.length < 2 || Boolean.parseBoolean(args[1]);

        IntegrarDadosReaisClientesPagamentos script = new IntegrarDadosReaisClientesPagamentos();
        ResumoInjecao resumo = script.executar(csvPath, resetarBanco);
        System.out.println("\n" + resumo.formatar());
    }

    public ResumoInjecao executar(Path csvPath, boolean resetarBancoAntes) throws IOException {
        if (csvPath == null || !Files.exists(csvPath)) {
            throw new IllegalArgumentException("Arquivo CSV não encontrado: " + csvPath);
        }

        InicializadorSistemaService inicializador = new InicializadorSistemaService();
        if (resetarBancoAntes) {
            System.out.println("[1/5] Resetando banco para dados fundamentais...");
            inicializador.resetarBancoParaDadosFundamentais(InicializadorSistemaService.TOKEN_CONFIRMACAO_RESET);
        } else {
            System.out.println("[1/5] Inicializando dados fundamentais...");
            inicializador.executarScriptInicializacao();
        }

        System.out.println("[2/5] Lendo e ordenando cronologicamente: " + csvPath);
        List<RegistroCsv> registros = new ArrayList<>();
        int ajustesNegativos = lerEOrdenarRegistros(csvPath, registros);

        RoteadorController roteador = new RoteadorController();
        RespostaEndpoint<Object> respPadrao = roteador.executarEndpoint(RotaSistema.PESSOA_CLIENTE_PADRAO_OBTER, Map.of());
        Pessoa clientePadrao = respPadrao.dados() instanceof Pessoa p ? p : null;

        System.out.println("[3/5] Cadastrando fornecedores, clientes e produtos...");
        Map<String, Pessoa> fornecedoresPorNome = cadastrarPessoas(roteador, registros, true);
        Map<String, Pessoa> clientesPorNome = cadastrarPessoas(roteador, registros, false);
        Map<String, Produto> cartelasPorNome = new LinkedHashMap<>();
        Map<String, Produto> caixasPorNomeCartela = new LinkedHashMap<>();
        cadastrarProdutosCartelaECaixa(roteador, registros, cartelasPorNome, caixasPorNomeCartela);

        System.out.println("[4/5] Processando " + registros.size() + " registros via RoteadorController...");
        Queue<NotaCompra> filaComprasEmAberto = new ArrayDeque<>();
        Map<String, Queue<NotaVenda>> filaVendasPorCliente = new LinkedHashMap<>();
        BigDecimal saldoCaixaDinheiro = BigDecimal.ZERO;
        BigDecimal totalTaxasPagamento = BigDecimal.ZERO;

        int notasCompraEmitidas = 0;
        int notasVendaClientes = 0;
        int notasVendaPadrao = 0;
        int baixasPerda = 0;
        int pagamentosClientesQuitados = 0;
        int notasCompraQuitadas = 0;
        int avisos = 0;

        Map<LocalDate, List<RegistroCsv>> porData = new LinkedHashMap<>();
        for (RegistroCsv r : registros) {
            porData.computeIfAbsent(r.data(), k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<LocalDate, List<RegistroCsv>> diaEntry : porData.entrySet()) {
            LocalDate dataDia = diaEntry.getKey();
            List<RegistroCsv> doDia = diaEntry.getValue();

            Map<String, List<RegistroCsv>> entradasPorFornecedor = new LinkedHashMap<>();
            Map<String, List<RegistroCsv>> vendasPorClienteCadastrado = new LinkedHashMap<>();
            List<RegistroCsv> vendasClientePadrao = new ArrayList<>();
            List<RegistroCsv> perdasDoDia = new ArrayList<>();
            Queue<RegistroCsv> pagamentosClientePadrao = new ArrayDeque<>();
            List<RegistroCsv> pagamentosClientesCadastrados = new ArrayList<>();

            for (RegistroCsv reg : doDia) {
                switch (reg.tipo()) {
                    case ENTRADA -> entradasPorFornecedor
                            .computeIfAbsent(reg.pessoaNome(), k -> new ArrayList<>())
                            .add(reg);
                    case PERDA -> perdasDoDia.add(reg);
                    case VENDA -> {
                        if (reg.clientePadrao()) {
                            vendasClientePadrao.add(reg);
                        } else {
                            vendasPorClienteCadastrado
                                    .computeIfAbsent(reg.pessoaNome(), k -> new ArrayList<>())
                                    .add(reg);
                        }
                    }
                    case PAGAMENTO -> {
                        if (reg.clientePadrao()) {
                            pagamentosClientePadrao.add(reg);
                        } else {
                            pagamentosClientesCadastrados.add(reg);
                        }
                    }
                }
            }

            // 4a. Entradas: mesmo fornecedor + mesma data = mesma NotaCompra
            for (Map.Entry<String, List<RegistroCsv>> entForn : entradasPorFornecedor.entrySet()) {
                Pessoa fornecedor = fornecedoresPorNome.get(entForn.getKey());
                NotaCompra nc = emitirNotaCompraAgrupada(
                        roteador, fornecedor, dataDia, entForn.getValue(), caixasPorNomeCartela, cartelasPorNome
                );
                if (nc != null) {
                    notasCompraEmitidas++;
                    filaComprasEmAberto.offer(nc);
                } else {
                    avisos++;
                }
            }

            // 4a-bis. Perdas (valor=0, lucro<0, qtd>0): baixa direta de estoque FIFO
            for (RegistroCsv perda : perdasDoDia) {
                Produto cartela = cartelasPorNome.get(perda.produtoOuForma());
                Produto caixa = caixasPorNomeCartela.get(perda.produtoOuForma());
                if (cartela == null || caixa == null) {
                    avisos++;
                    continue;
                }
                garantirSaldoCartelasDesmembrandoCaixas(cartela, caixa, perda.quantidade(), dataDia);
                if (baixarPerdaProduto(roteador, cartela, perda.quantidade(), dataDia)) {
                    baixasPerda++;
                } else {
                    avisos++;
                }
            }

            // 4b. Vendas para Clientes Cadastrados: mesmo cliente + mesma data = mesma NotaVenda (CREDIARIO a prazo)
            for (Map.Entry<String, List<RegistroCsv>> entCli : vendasPorClienteCadastrado.entrySet()) {
                Pessoa cliente = clientesPorNome.get(entCli.getKey());
                NotaVenda nv = emitirNotaVendaClienteAgrupada(
                        roteador, cliente, dataDia, entCli.getValue(), cartelasPorNome, caixasPorNomeCartela
                );
                if (nv != null) {
                    notasVendaClientes++;
                    filaVendasPorCliente
                            .computeIfAbsent(entCli.getKey(), k -> new ArrayDeque<>())
                            .offer(nv);
                } else {
                    avisos++;
                }
            }

            // 4c. Vendas para Cliente Padrão ("aleatório"): cada ocorrência = nova NotaVenda + pagamento vinculado
            for (RegistroCsv vendaPadrao : vendasClientePadrao) {
                RegistroCsv pagVinculado = retirarPagamentoCompativel(pagamentosClientePadrao, vendaPadrao.valorTotal());
                String codigoForma = pagVinculado != null
                        ? mapearCodigoFormaPagamento(pagVinculado.produtoOuForma())
                        : "DINHEIRO";
                BigDecimal taxaDesconto = extrairTaxaDesconto(pagVinculado);
                totalTaxasPagamento = totalTaxasPagamento.add(taxaDesconto);

                NotaVenda nv = emitirNotaVendaIndividualPadrao(
                        roteador, clientePadrao, dataDia, vendaPadrao, codigoForma, cartelasPorNome, caixasPorNomeCartela
                );
                if (nv != null) {
                    notasVendaPadrao++;
                    if ("DINHEIRO".equals(codigoForma)) {
                        BigDecimal bruto = nv.getValorTotal() != null ? nv.getValorTotal() : vendaPadrao.valorTotal();
                        saldoCaixaDinheiro = saldoCaixaDinheiro.add(bruto.subtract(taxaDesconto).max(BigDecimal.ZERO));
                    }
                } else {
                    avisos++;
                }
            }

            // 4d. Pagamentos de Clientes Cadastrados: quita notas em aberto do cliente (FIFO)
            for (RegistroCsv pagCli : pagamentosClientesCadastrados) {
                BigDecimal taxaDesconto = extrairTaxaDesconto(pagCli);
                totalTaxasPagamento = totalTaxasPagamento.add(taxaDesconto);
                Queue<NotaVenda> filaCli = filaVendasPorCliente.get(pagCli.pessoaNome());
                if (filaCli != null && !filaCli.isEmpty()) {
                    NotaVenda pendente = filaCli.poll();
                    if (quitarPagamentosDaNota(roteador, pendente.getPagamentos(), dataDia.atTime(17, 0))) {
                        pagamentosClientesQuitados++;
                    }
                }
                if ("DINHEIRO".equals(mapearCodigoFormaPagamento(pagCli.produtoOuForma()))) {
                    saldoCaixaDinheiro = saldoCaixaDinheiro.add(pagCli.valorTotal().subtract(taxaDesconto).max(BigDecimal.ZERO));
                }
            }

            // 4e. Quitação FIFO de Notas de Compra com saldo em dinheiro acumulado
            while (!filaComprasEmAberto.isEmpty()) {
                NotaCompra proxima = filaComprasEmAberto.peek();
                BigDecimal valorNota = proxima.getValorTotal() != null ? proxima.getValorTotal() : BigDecimal.ZERO;
                if (saldoCaixaDinheiro.compareTo(valorNota) < 0) {
                    break;
                }
                filaComprasEmAberto.poll();
                if (quitarPagamentosDaNota(roteador, proxima.getPagamentos(), dataDia.atTime(18, 0))) {
                    saldoCaixaDinheiro = saldoCaixaDinheiro.subtract(valorNota);
                    notasCompraQuitadas++;
                } else {
                    avisos++;
                }
            }
        }

        System.out.println("[5/5] Injeção concluída.");
        return new ResumoInjecao(
                registros.size(),
                fornecedoresPorNome.size(),
                clientesPorNome.size(),
                cartelasPorNome.size(),
                notasCompraEmitidas,
                notasVendaClientes,
                notasVendaPadrao,
                baixasPerda,
                pagamentosClientesQuitados,
                notasCompraQuitadas,
                ajustesNegativos,
                avisos,
                totalTaxasPagamento,
                saldoCaixaDinheiro
        );
    }

    private int lerEOrdenarRegistros(Path csvPath, List<RegistroCsv> destino) throws IOException {
        LeitorCsv leitor = new LeitorCsv();
        TabelaCsv tabela = leitor.lerArquivo(csvPath, ';');
        List<Map<String, String>> linhas = tabela.linhas();
        if (linhas.isEmpty()) {
            return 0;
        }

        // O CSV está em ordem decrescente (mais recente no topo, 1/1 no final).
        int anoAtual = 2025;
        int mesAnterior = 1;
        int ajustesNegativos = 0;

        for (int idx = linhas.size() - 1; idx >= 0; idx--) {
            Map<String, String> row = linhas.get(idx);
            String colModalidade = valorColuna(row, "atacado").toLowerCase(Locale.ROOT);
            String colCliente = valorColuna(row, "cliente");
            String colProduto = valorColuna(row, "produto");
            String colQtd = valorColuna(row, "quantidade");
            String colValorTotal = valorColuna(row, "valor total");
            String colLucro = valorColuna(row, "lucro");
            String colData = valorColuna(row, "data");

            if (colCliente.isEmpty() || colProduto.isEmpty() || colData.isEmpty()) {
                continue;
            }

            LocalDate data = parsearDataComViradaAno(colData, anoAtual, mesAnterior);
            if (data == null) {
                continue;
            }
            if (data.getMonthValue() < mesAnterior) {
                anoAtual++;
                data = data.withYear(anoAtual);
            }
            mesAnterior = data.getMonthValue();

            boolean isPadrao = ehClientePadrao(colCliente);
            BigDecimal valorTotal = parsearNumeroBr(colValorTotal, BigDecimal.ZERO).abs();
            BigDecimal lucro = parsearNumeroBr(colLucro, BigDecimal.ZERO);

            if ("pagamento".equalsIgnoreCase(colQtd)) {
                destino.add(new RegistroCsv(
                        idx + 2, data, colModalidade, colCliente, isPadrao,
                        colProduto, BigDecimal.ONE, valorTotal, lucro, TipoLinhaCsv.PAGAMENTO
                ));
                continue;
            }

            BigDecimal qtd = parsearNumeroBr(colQtd, BigDecimal.ZERO);
            if (qtd.compareTo(BigDecimal.ZERO) <= 0) {
                ajustesNegativos++;
                continue;
            }

            TipoLinhaCsv tipo;
            if ("entrada".equals(colModalidade)) {
                tipo = TipoLinhaCsv.ENTRADA;
            } else if (valorTotal.compareTo(BigDecimal.ZERO) == 0 && lucro.compareTo(BigDecimal.ZERO) < 0) {
                tipo = TipoLinhaCsv.PERDA;
            } else {
                tipo = TipoLinhaCsv.VENDA;
            }
            destino.add(new RegistroCsv(
                    idx + 2, data, colModalidade, colCliente, isPadrao,
                    colProduto, qtd, valorTotal, lucro, tipo
            ));
        }

        destino.sort(Comparator
                .comparing(RegistroCsv::data)
                .thenComparingInt(r -> switch (r.tipo()) {
                    case ENTRADA -> 0;
                    case PERDA -> 1;
                    case VENDA -> 2;
                    case PAGAMENTO -> 3;
                })
                .thenComparingInt(RegistroCsv::linhaOriginal));
        return ajustesNegativos;
    }

    private Map<String, Pessoa> cadastrarPessoas(RoteadorController roteador, List<RegistroCsv> registros, boolean fornecedores) {
        Set<String> nomes = new LinkedHashSet<>();
        for (RegistroCsv r : registros) {
            if (fornecedores && r.tipo() == TipoLinhaCsv.ENTRADA) {
                nomes.add(r.pessoaNome());
            } else if (!fornecedores && r.tipo() != TipoLinhaCsv.ENTRADA && !r.clientePadrao()) {
                nomes.add(r.pessoaNome());
            }
        }

        String papel = fornecedores ? "FORNECEDOR" : "CLIENTE";
        Map<String, Pessoa> mapa = new LinkedHashMap<>();
        for (String nome : nomes) {
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                    RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                    Map.of("nome", nome, "papel", papel)
            );
            if (resp.sucesso() && resp.dados() instanceof Pessoa p) {
                mapa.put(nome, p);
                if (!fornecedores) {
                    roteador.executarEndpoint(
                            RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE,
                            Map.of("idCliente", p.getId(), "novoLimite", "100000.00")
                    );
                }
            }
        }
        return mapa;
    }

    private void cadastrarProdutosCartelaECaixa(
            RoteadorController roteador,
            List<RegistroCsv> registros,
            Map<String, Produto> cartelasPorNome,
            Map<String, Produto> caixasPorNomeCartela
    ) {
        Set<String> produtos = new LinkedHashSet<>();
        for (RegistroCsv r : registros) {
            if (r.tipo() != TipoLinhaCsv.PAGAMENTO) {
                produtos.add(r.produtoOuForma());
            }
        }

        for (String nomeCartela : produtos) {
            RespostaEndpoint<Object> respCartela = roteador.executarEndpoint(
                    RotaSistema.PRODUTO_FISICO_CADASTRAR,
                    Map.of(
                            "nome", nomeCartela,
                            "descricao", "Cartela de ovos (" + nomeCartela + ") - durabilidade 15 dias",
                            "precoVenda", "18.00",
                            "siglaUm", "UN"
                    )
            );
            if (respCartela.sucesso() && respCartela.dados() instanceof Produto cartela) {
                cartelasPorNome.put(nomeCartela, cartela);
                RespostaEndpoint<Object> respCaixa = roteador.executarEndpoint(
                        RotaSistema.PRODUTO_FISICO_CADASTRAR,
                        Map.of(
                                "nome", "CAIXA " + nomeCartela,
                                "descricao", "Conjunto Caixa com 12 cartelas de " + nomeCartela,
                                "precoVenda", "200.00",
                                "siglaUm", "CX"
                        )
                );
                if (respCaixa.sucesso() && respCaixa.dados() instanceof Produto caixa) {
                    caixasPorNomeCartela.put(nomeCartela, caixa);
                    roteador.executarEndpoint(
                            RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                            Map.of(
                                    "idProdutoKit", caixa.getId(),
                                    "idProdutoComponente", cartela.getId(),
                                    "quantidade", "12.000000"
                            )
                    );
                }
            }
        }
    }

    private NotaCompra emitirNotaCompraAgrupada(
            RoteadorController roteador,
            Pessoa fornecedor,
            LocalDate dataEntrada,
            List<RegistroCsv> entradas,
            Map<String, Produto> caixasPorNomeCartela,
            Map<String, Produto> cartelasPorNome
    ) {
        if (fornecedor == null || entradas.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        for (RegistroCsv e : entradas) {
            Produto caixa = caixasPorNomeCartela.get(e.produtoOuForma());
            Produto cartela = cartelasPorNome.get(e.produtoOuForma());
            if (caixa == null || e.quantidade().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal custoCartela = e.valorTotal().divide(e.quantidade(), 4, RoundingMode.HALF_EVEN);
            BigDecimal custoCaixa = custoCartela.multiply(CARTELAS_POR_CAIXA).setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal[] divRem = e.quantidade().divideAndRemainder(CARTELAS_POR_CAIXA);

            if (divRem[0].compareTo(BigDecimal.ZERO) > 0) {
                itens.add(criarMapaItemCompra(caixa.getId(), divRem[0], custoCaixa, dataEntrada));
            }
            if (divRem[1].compareTo(BigDecimal.ZERO) > 0 && cartela != null) {
                itens.add(criarMapaItemCompra(cartela.getId(), divRem[1], custoCartela, dataEntrada));
            }
        }
        if (itens.isEmpty()) {
            return null;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idFornecedor", fornecedor.getId());
        params.put("descricao", "Entrada agrupada - " + fornecedor.getNome() + " em " + dataEntrada);
        params.put("itens", itens);
        params.put("dataGerada", dataEntrada.atTime(8, 0));

        RespostaEndpoint<Object> resp = roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, params);
        return resp.sucesso() && resp.dados() instanceof NotaCompra nc ? nc : null;
    }

    private NotaVenda emitirNotaVendaClienteAgrupada(
            RoteadorController roteador,
            Pessoa cliente,
            LocalDate dataDia,
            List<RegistroCsv> vendas,
            Map<String, Produto> cartelasPorNome,
            Map<String, Produto> caixasPorNomeCartela
    ) {
        if (cliente == null || vendas.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal totalNota = BigDecimal.ZERO;

        for (RegistroCsv v : vendas) {
            Produto cartela = cartelasPorNome.get(v.produtoOuForma());
            Produto caixa = caixasPorNomeCartela.get(v.produtoOuForma());
            if (cartela == null || caixa == null) {
                continue;
            }
            garantirSaldoCartelasDesmembrandoCaixas(cartela, caixa, v.quantidade(), dataDia);
            BigDecimal precoUnit = v.valorTotal().divide(v.quantidade(), 2, RoundingMode.HALF_EVEN);
            atualizarPrecoSeNecessario(roteador, cartela, precoUnit);

            itens.add(Map.of("idProduto", cartela.getId(), "quantidade", v.quantidade().toPlainString()));
            totalNota = totalNota.add(v.valorTotal());
        }
        if (itens.isEmpty()) {
            return null;
        }

        Map<String, Object> pagAPrazo = new LinkedHashMap<>();
        pagAPrazo.put("codigoForma", "CREDIARIO");
        pagAPrazo.put("modalidade", "A_PRAZO");
        pagAPrazo.put("valor", MoedaContabilBacen.arredondarMoeda(totalNota).toPlainString());
        pagAPrazo.put("dataValidade", dataDia.plusDays(30));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idCliente", cliente.getId());
        params.put("descricao", "Venda agrupada cliente cadastrado: " + cliente.getNome() + " (" + dataDia + ")");
        params.put("itens", itens);
        params.put("pagamentos", List.of(pagAPrazo));
        params.put("dataGerada", dataDia.atTime(14, 0));

        RespostaEndpoint<Object> resp = roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, params);
        return resp.sucesso() && resp.dados() instanceof NotaVenda nv ? nv : null;
    }

    private NotaVenda emitirNotaVendaIndividualPadrao(
            RoteadorController roteador,
            Pessoa clientePadrao,
            LocalDate dataDia,
            RegistroCsv venda,
            String codigoForma,
            Map<String, Produto> cartelasPorNome,
            Map<String, Produto> caixasPorNomeCartela
    ) {
        Produto cartela = cartelasPorNome.get(venda.produtoOuForma());
        Produto caixa = caixasPorNomeCartela.get(venda.produtoOuForma());
        if (cartela == null || caixa == null) {
            return null;
        }

        garantirSaldoCartelasDesmembrandoCaixas(cartela, caixa, venda.quantidade(), dataDia);
        BigDecimal precoUnit = venda.valorTotal().divide(venda.quantidade(), 2, RoundingMode.HALF_EVEN);
        atualizarPrecoSeNecessario(roteador, cartela, precoUnit);

        BigDecimal total = MoedaContabilBacen.arredondarMoeda(venda.valorTotal());
        Map<String, Object> params = new LinkedHashMap<>();
        if (clientePadrao != null) {
            params.put("idCliente", clientePadrao.getId());
        }
        params.put("descricao", "Venda individual cliente padrão (" + venda.modalidade() + " - " + dataDia + ")");
        params.put("itens", List.of(Map.of("idProduto", cartela.getId(), "quantidade", venda.quantidade().toPlainString())));
        params.put("pagamentos", List.of(Map.of("codigoForma", codigoForma, "valor", total.toPlainString())));
        params.put("dataGerada", dataDia.atTime(15, 0));

        RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                clientePadrao != null ? RotaSistema.NOTA_VENDA_EMITIR_FIFO : RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO,
                params
        );
        return resp.sucesso() && resp.dados() instanceof NotaVenda nv ? nv : null;
    }

    private Map<String, Object> criarMapaItemCompra(Long idProduto, BigDecimal qtd, BigDecimal custoUnit, LocalDate dataEntrada) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idProduto", idProduto);
        item.put("quantidade", qtd.toPlainString());
        item.put("valorUnitario", custoUnit.toPlainString());
        item.put("dataValidade", dataEntrada.plusDays(DURABILIDADE_CARTELA_DIAS));
        return item;
    }

    private RegistroCsv retirarPagamentoCompativel(Queue<RegistroCsv> filaPagamentos, BigDecimal valorVenda) {
        if (filaPagamentos == null || filaPagamentos.isEmpty()) {
            return null;
        }
        for (RegistroCsv p : filaPagamentos) {
            if (p.valorTotal().compareTo(valorVenda) == 0) {
                filaPagamentos.remove(p);
                return p;
            }
        }
        return filaPagamentos.poll();
    }

    private BigDecimal extrairTaxaDesconto(RegistroCsv pagamento) {
        if (pagamento == null || pagamento.lucro() == null) {
            return BigDecimal.ZERO;
        }
        return pagamento.lucro().compareTo(BigDecimal.ZERO) < 0
                ? pagamento.lucro().abs()
                : BigDecimal.ZERO;
    }

    private String mapearCodigoFormaPagamento(String textoForma) {
        if (textoForma == null) {
            return "DINHEIRO";
        }
        String norm = textoForma.trim().toLowerCase(Locale.ROOT);
        if (norm.contains("pix")) return "PIX";
        if (norm.contains("débito") || norm.contains("debito")) return "CARTAO_DEBITO";
        if (norm.contains("crédito") || norm.contains("credito")) return "CARTAO_CREDITO";
        return "DINHEIRO";
    }

    private boolean ehClientePadrao(String nomeCliente) {
        if (nomeCliente == null || nomeCliente.isBlank()) {
            return true;
        }
        String norm = nomeCliente.trim().toLowerCase(Locale.ROOT);
        return norm.equals("aleatório") || norm.equals("aleatorio");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void garantirSaldoCartelasDesmembrandoCaixas(
            Produto cartela, Produto caixa, BigDecimal quantidadeNecessaria, LocalDate dataReferencia
    ) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            BigDecimal saldoCartelas = em.createQuery(
                            "SELECT COALESCE(SUM(i.quantidadeDisponivel), 0) FROM Item i JOIN i.nota n " +
                            "WHERE i.produto.id = :idProd AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                            "AND i.quantidadeDisponivel > 0",
                            BigDecimal.class)
                    .setParameter("idProd", cartela.getId())
                    .getSingleResult();

            if (saldoCartelas != null && saldoCartelas.compareTo(quantidadeNecessaria) >= 0) {
                return;
            }
            BigDecimal faltante = quantidadeNecessaria.subtract(saldoCartelas != null ? saldoCartelas : BigDecimal.ZERO);

            List<Item> lotesCaixa = em.createQuery(
                            "SELECT i FROM Item i JOIN FETCH i.nota n " +
                            "WHERE i.produto.id = :idCaixa AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                            "AND i.quantidadeDisponivel > 0 ORDER BY i.data ASC, i.id ASC",
                            Item.class)
                    .setParameter("idCaixa", caixa.getId())
                    .getResultList();
            if (lotesCaixa.isEmpty()) {
                return;
            }

            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                for (Item loteCaixa : lotesCaixa) {
                    if (faltante.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal disp = loteCaixa.getQuantidadeDisponivel();
                    if (disp == null || disp.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal caixasAbrir = disp.min(faltante.divide(CARTELAS_POR_CAIXA, 0, RoundingMode.CEILING));
                    BigDecimal cartelasGeradas = caixasAbrir.multiply(CARTELAS_POR_CAIXA).setScale(6, RoundingMode.HALF_EVEN);
                    loteCaixa.setQuantidadeDisponivel(disp.subtract(caixasAbrir));
                    em.merge(loteCaixa);

                    BigDecimal custoCartela = loteCaixa.getValor() != null
                            ? loteCaixa.getValor().divide(CARTELAS_POR_CAIXA, 4, RoundingMode.HALF_EVEN)
                            : BigDecimal.ZERO;
                    LocalDate validade = loteCaixa.getDataValidade() != null
                            ? loteCaixa.getDataValidade()
                            : dataReferencia.plusDays(DURABILIDADE_CARTELA_DIAS);
                    Item itemCartela = new Item(cartela, custoCartela, cartelasGeradas, validade);
                    itemCartela.setData(loteCaixa.getData());
                    itemCartela.setNota(loteCaixa.getNota());
                    em.persist(itemCartela);

                    faltante = faltante.subtract(cartelasGeradas);
                }
                tx.commit();
            } catch (Exception ex) {
                if (tx.isActive()) tx.rollback();
                throw ex;
            }
        }
    }

    private boolean baixarPerdaProduto(
            RoteadorController roteador, Produto cartela, BigDecimal quantidadePerda, LocalDate dataPerda
    ) {
        BigDecimal restante = quantidadePerda;
        List<Item<? extends Produto>> lotes = buscarLotesComSaldo(cartela.getId());
        if (lotes.isEmpty()) {
            return false;
        }
        boolean baixouAlgo = false;
        for (Item<? extends Produto> lote : lotes) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal disponivel = lote.getQuantidadeDisponivel();
            if (disponivel == null || disponivel.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal baixar = disponivel.min(restante);
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                    Map.of(
                            "idLote", lote.getId(),
                            "quantidade", baixar.toPlainString(),
                            "motivo", "Perda registrada em " + dataPerda + " (valor=0, lucro<0, qtd>0)",
                            "dataGerada", dataPerda.atTime(16, 0)
                    )
            );
            if (resp.sucesso()) {
                baixouAlgo = true;
                restante = restante.subtract(baixar);
            }
        }
        return baixouAlgo && restante.compareTo(BigDecimal.ZERO) == 0;
    }

    @SuppressWarnings("unchecked")
    private List<Item<? extends Produto>> buscarLotesComSaldo(Long idProduto) {
        try (EntityManager em = JPAUtil.getEntityManager()) {
            return (List<Item<? extends Produto>>) (List<?>) em.createQuery(
                            "SELECT i FROM Item i JOIN i.nota n " +
                            "WHERE i.produto.id = :idProd AND TYPE(n) = br.com.senac.repository.modal.nota.NotaCompra " +
                            "AND i.quantidadeDisponivel > 0 ORDER BY i.data ASC, i.id ASC",
                            Item.class)
                    .setParameter("idProd", idProduto)
                    .getResultList();
        }
    }

    private void atualizarPrecoSeNecessario(RoteadorController roteador, Produto cartela, BigDecimal novoPreco) {
        if (novoPreco == null || novoPreco.compareTo(BigDecimal.ZERO) <= 0) return;
        if (cartela.getPrecoVenda() != null && cartela.getPrecoVenda().compareTo(novoPreco) == 0) return;

        RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                Map.of("idProduto", cartela.getId(), "novoPreco", novoPreco.toPlainString())
        );
        if (resp.sucesso()) {
            cartela.setPrecoVenda(novoPreco);
        }
    }

    private boolean quitarPagamentosDaNota(RoteadorController roteador, List<NotaPagamento> pagamentos, LocalDateTime dataQuitacao) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            return false;
        }
        boolean tudoQuitado = true;
        for (NotaPagamento np : pagamentos) {
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                    RotaSistema.PAGAMENTO_QUITAR_TITULO_OU_PARCELA,
                    Map.of("pagamento", np, "dataQuitacao", dataQuitacao)
            );
            if (!resp.sucesso()) {
                tudoQuitado = false;
            }
        }
        return tudoQuitado;
    }

    private String valorColuna(Map<String, String> row, String nomeEsperado) {
        if (row.containsKey(nomeEsperado)) {
            return row.get(nomeEsperado).trim();
        }
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(nomeEsperado)) {
                return e.getValue() != null ? e.getValue().trim() : "";
            }
        }
        return "";
    }

    private LocalDate parsearDataComViradaAno(String colData, int anoAtual, int mesAnterior) {
        String[] dm = colData.split("/");
        if (dm.length < 2) return null;
        try {
            int dia = Integer.parseInt(dm[0].trim());
            int mes = Integer.parseInt(dm[1].trim());
            return LocalDate.of(anoAtual, mes, dia);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parsearNumeroBr(String bruto, BigDecimal padrao) {
        if (bruto == null || bruto.isBlank()) return padrao;
        String limpo = bruto.replace("R$", "").replace(" ", "").trim();
        if (limpo.isEmpty() || limpo.equals("-")) return padrao;
        if (limpo.contains(",") && limpo.contains(".")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        } else if (limpo.contains(",")) {
            limpo = limpo.replace(",", ".");
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
