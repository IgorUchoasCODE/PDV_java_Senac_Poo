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
 * Script dedicado de integração dos dados reais de {@code docs/testesDadosReais18M.csv},
 * utilizando {@link LeitorCsv} como ferramenta de leitura e {@link RoteadorController} para todo tratamento lógico:
 * <ul>
 *   <li>Executa {@link InicializadorSistemaService} antes da carga.</li>
 *   <li>Cadastra fornecedores, cartelas (validade 15 dias) e conjuntos Caixa (12 cartelas/cx).</li>
 *   <li>Entradas: converte quantidade para caixas (quantidade / 12) com valor unitário = valor da caixa de custo,
 *       agrupando itens da mesma data (e fornecedor) em uma única NotaCompra a prazo.</li>
 *   <li>Saídas: classifica em Perda (valor total zerado e lucro negativo) ou Venda (em DINHEIRO, por cartela,
 *       inclusive vendas sem lucro / lucro negativo com valor total > 0).</li>
 *   <li>Desmembra caixas em cartelas em ordem FIFO sob demanda quando necessário para venda ou perda.</li>
 *   <li>Mantém fila FIFO das notas de compra em aberto, quitando-as conforme o caixa em dinheiro acumula saldo.</li>
 * </ul>
 */
public class IntegrarDadosReais18M {

    private static final int DURABILIDADE_CARTELA_DIAS = 15;
    private static final BigDecimal CARTELAS_POR_CAIXA = new BigDecimal("12");

    private enum TipoMovimento {
        ENTRADA,
        VENDA,
        PERDA
    }

    private record RegistroMovimentoCsv(
            int linhaOriginal,
            LocalDate data,
            String fornecedorOuSaida,
            String produto,
            BigDecimal quantidade,
            BigDecimal valorUnitarioOuCaixa,
            BigDecimal valorTotal,
            BigDecimal lucro,
            TipoMovimento tipo
    ) {}

    public record ResumoIntegracao18M(
            int totalLinhasLidas,
            int fornecedoresCadastrados,
            int produtosCartelaCadastrados,
            int kitsCaixaCadastrados,
            int notasCompraEmitidas,
            int notasVendaEmitidas,
            int baixasPerdaExecutadas,
            int notasCompraQuitadasNaFila,
            int notasCompraEmAberto,
            BigDecimal saldoCaixaDinheiroRestante,
            int avisosOuFalhas
    ) {
        public String formatar() {
            return String.format(
                    "=== RESUMO INTEGRAÇÃO 18M ===%n" +
                    "Linhas lidas: %d%n" +
                    "Fornecedores cadastrados: %d%n" +
                    "Cartelas cadastradas: %d | Caixas (kits 12 un) cadastradas: %d%n" +
                    "Notas de Compra emitidas (em Caixas): %d%n" +
                    "Notas de Venda (DINHEIRO, em Cartelas) emitidas: %d%n" +
                    "Baixas de Perda executadas: %d%n" +
                    "Fila de Pagamento -> Quitadas: %d | Em aberto: %d | Saldo caixa restante: R$ %s%n" +
                    "Avisos/Falhas pontuais: %d",
                    totalLinhasLidas, fornecedoresCadastrados, produtosCartelaCadastrados, kitsCaixaCadastrados,
                    notasCompraEmitidas, notasVendaEmitidas, baixasPerdaExecutadas,
                    notasCompraQuitadasNaFila, notasCompraEmAberto,
                    MoedaContabilBacen.arredondarMoeda(saldoCaixaDinheiroRestante),
                    avisosOuFalhas
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Path csvPath = args != null && args.length > 0
                ? Paths.get(args[0])
                : Paths.get("docs", "testesDadosReais18M.csv");

        boolean resetarBanco = args == null || args.length < 2 || Boolean.parseBoolean(args[1]);

        IntegrarDadosReais18M script = new IntegrarDadosReais18M();
        ResumoIntegracao18M resumo = script.executarIntegracao(csvPath, resetarBanco);
        System.out.println("\n" + resumo.formatar());
    }

    public ResumoIntegracao18M executarIntegracao(Path csvPath, boolean resetarBancoAntes) throws IOException {
        if (csvPath == null || !Files.exists(csvPath)) {
            throw new IllegalArgumentException("Arquivo CSV não encontrado: " + csvPath);
        }

        // 1. InicializadorSistemaService primeiro
        InicializadorSistemaService inicializador = new InicializadorSistemaService();
        if (resetarBancoAntes) {
            System.out.println("[1/5] Resetando banco para dados fundamentais via InicializadorSistemaService...");
            inicializador.resetarBancoParaDadosFundamentais(InicializadorSistemaService.TOKEN_CONFIRMACAO_RESET);
        } else {
            System.out.println("[1/5] Inicializando dados fundamentais via InicializadorSistemaService...");
            inicializador.executarScriptInicializacao();
        }

        // 2. Leitura via ferramenta LeitorCsv e ordenação cronológica
        System.out.println("[2/5] Lendo " + csvPath + " com a ferramenta LeitorCsv...");
        List<RegistroMovimentoCsv> movimentos = lerEOrdenarMovimentos(csvPath);

        RoteadorController roteador = new RoteadorController();
        RespostaEndpoint<Object> respCliPadrao = roteador.executarEndpoint(RotaSistema.PESSOA_CLIENTE_PADRAO_OBTER, Map.of());
        Pessoa clientePadrao = respCliPadrao.dados() instanceof Pessoa p ? p : null;

        // 3. Cadastro de Fornecedores, Cartelas e Conjuntos Caixa (12 cartelas)
        System.out.println("[3/5] Cadastrando fornecedores, cartelas e conjuntos Caixa (12 cartelas/cx)...");
        Map<String, Pessoa> fornecedoresPorNome = cadastrarFornecedores(roteador, movimentos);
        Map<String, Produto> cartelasPorNome = new LinkedHashMap<>();
        Map<String, Produto> caixasPorNomeCartela = new LinkedHashMap<>();
        int kitsCriados = cadastrarProdutosCartelaECaixa(roteador, movimentos, cartelasPorNome, caixasPorNomeCartela);

        // 4. Processamento cronológico via rotas do RoteadorController
        System.out.println("[4/5] Processando " + movimentos.size() + " movimentos via rotas dos controllers...");
        Queue<NotaCompra> filaNotasEmAberto = new ArrayDeque<>();
        BigDecimal saldoCaixaDinheiro = BigDecimal.ZERO;
        int notasCompraEmitidas = 0;
        int notasVendaEmitidas = 0;
        int baixasPerda = 0;
        int notasQuitadas = 0;
        int avisos = 0;

        Map<LocalDate, List<RegistroMovimentoCsv>> porData = new LinkedHashMap<>();
        for (RegistroMovimentoCsv m : movimentos) {
            porData.computeIfAbsent(m.data(), k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<LocalDate, List<RegistroMovimentoCsv>> diaEntry : porData.entrySet()) {
            LocalDate dataDia = diaEntry.getKey();
            List<RegistroMovimentoCsv> doDia = diaEntry.getValue();

            Map<String, List<RegistroMovimentoCsv>> entradasPorFornecedor = new LinkedHashMap<>();
            List<RegistroMovimentoCsv> saidasDoDia = new ArrayList<>();

            for (RegistroMovimentoCsv mov : doDia) {
                if (mov.tipo() == TipoMovimento.ENTRADA) {
                    entradasPorFornecedor
                            .computeIfAbsent(mov.fornecedorOuSaida(), k -> new ArrayList<>())
                            .add(mov);
                } else {
                    saidasDoDia.add(mov);
                }
            }

            // 4a. Entradas na mesma data -> mesma NotaCompra (em Caixas: qtd / 12, valor = custo da caixa)
            for (Map.Entry<String, List<RegistroMovimentoCsv>> entForn : entradasPorFornecedor.entrySet()) {
                Pessoa fornecedor = fornecedoresPorNome.get(entForn.getKey());
                NotaCompra nc = emitirNotaCompraEmCaixasAgrupada(
                        roteador, fornecedor, dataDia, entForn.getValue(), caixasPorNomeCartela, cartelasPorNome
                );
                if (nc != null) {
                    notasCompraEmitidas++;
                    filaNotasEmAberto.offer(nc);
                } else {
                    avisos++;
                }
            }

            // 4b. Saídas do dia (Vendas em dinheiro por cartela e Perdas)
            for (RegistroMovimentoCsv saida : saidasDoDia) {
                Produto cartela = cartelasPorNome.get(saida.produto());
                Produto caixa = caixasPorNomeCartela.get(saida.produto());
                if (cartela == null || caixa == null) {
                    avisos++;
                    continue;
                }

                // Garante saldo de cartelas desmembrando caixas (1 cx = 12 cartelas) em FIFO se necessário
                garantirSaldoCartelasDesmembrandoCaixas(cartela, caixa, saida.quantidade(), dataDia);

                if (saida.tipo() == TipoMovimento.PERDA) {
                    if (baixarPerdaProduto(roteador, cartela, saida.quantidade(), dataDia)) {
                        baixasPerda++;
                    } else {
                        avisos++;
                    }
                } else {
                    BigDecimal precoUnitarioVenda = calcularPrecoUnitarioVenda(saida, cartela);
                    atualizarPrecoSeNecessario(roteador, cartela, precoUnitarioVenda);

                    LocalDateTime dtVenda = dataDia.atTime(14, 0);
                    BigDecimal brutoVenda = MoedaContabilBacen.arredondarMoeda(precoUnitarioVenda.multiply(saida.quantidade()));
                    BigDecimal totalVenda = saida.valorTotal().compareTo(BigDecimal.ZERO) > 0
                            ? saida.valorTotal()
                            : brutoVenda;
                    BigDecimal descontoCupom = brutoVenda.compareTo(totalVenda) > 0
                            ? brutoVenda.subtract(totalVenda)
                            : BigDecimal.ZERO;
                    if (totalVenda.compareTo(brutoVenda) > 0) {
                        totalVenda = brutoVenda;
                    }

                    Map<String, Object> pagDinheiro = Map.of(
                            "codigoForma", "DINHEIRO",
                            "valor", totalVenda.toPlainString()
                    );
                    Map<String, Object> paramsVenda = new LinkedHashMap<>();
                    if (clientePadrao != null) {
                        paramsVenda.put("idCliente", clientePadrao.getId());
                    }
                    paramsVenda.put("descricao", "Venda balcão em dinheiro (" + dataDia + ")");
                    paramsVenda.put("itens", List.of(Map.of(
                            "idProduto", cartela.getId(),
                            "quantidade", saida.quantidade().toPlainString()
                    )));
                    paramsVenda.put("pagamentos", List.of(pagDinheiro));
                    if (descontoCupom.compareTo(BigDecimal.ZERO) > 0) {
                        paramsVenda.put("cupom", "DESCONTO_BALCAO");
                        paramsVenda.put("descontoCupom", descontoCupom.toPlainString());
                    }
                    paramsVenda.put("dataGerada", dtVenda);

                    RespostaEndpoint<Object> respVenda = roteador.executarEndpoint(
                            clientePadrao != null ? RotaSistema.NOTA_VENDA_EMITIR_FIFO : RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO,
                            paramsVenda
                    );

                    if (respVenda.sucesso() && respVenda.dados() instanceof NotaVenda nv) {
                        notasVendaEmitidas++;
                        BigDecimal recebido = nv.getValorTotal() != null ? nv.getValorTotal() : totalVenda;
                        saldoCaixaDinheiro = saldoCaixaDinheiro.add(recebido);

                        // Fila FIFO de quitação das notas de compra em aberto
                        while (!filaNotasEmAberto.isEmpty()) {
                            NotaCompra proxima = filaNotasEmAberto.peek();
                            BigDecimal valorNota = proxima.getValorTotal() != null ? proxima.getValorTotal() : BigDecimal.ZERO;
                            if (saldoCaixaDinheiro.compareTo(valorNota) < 0) {
                                break;
                            }
                            filaNotasEmAberto.poll();
                            if (quitarNotaCompra(roteador, proxima, dataDia.atTime(18, 0))) {
                                saldoCaixaDinheiro = saldoCaixaDinheiro.subtract(valorNota);
                                notasQuitadas++;
                            } else {
                                avisos++;
                            }
                        }
                    } else {
                        avisos++;
                        System.err.printf("  [AVISO] Linha %d (%s | %s | qtd=%s): %s%n",
                                saida.linhaOriginal(), dataDia, saida.produto(), saida.quantidade(), respVenda.mensagem());
                    }
                }
            }
        }

        System.out.println("[5/5] Integração concluída.");
        return new ResumoIntegracao18M(
                movimentos.size(),
                fornecedoresPorNome.size(),
                cartelasPorNome.size(),
                kitsCriados,
                notasCompraEmitidas,
                notasVendaEmitidas,
                baixasPerda,
                notasQuitadas,
                filaNotasEmAberto.size(),
                saldoCaixaDinheiro,
                avisos
        );
    }

    private List<RegistroMovimentoCsv> lerEOrdenarMovimentos(Path csvPath) throws IOException {
        LeitorCsv leitor = new LeitorCsv();
        TabelaCsv tabela = leitor.lerArquivo(csvPath, ';');
        List<Map<String, String>> linhas = tabela.linhas();
        if (linhas.isEmpty()) {
            return Collections.emptyList();
        }

        // O CSV está em ordem decrescente (mais recente no topo, 01/01/2025 no final).
        // Lemos de baixo para cima para inferir corretamente a virada de ano (2025 -> 2026).
        List<RegistroMovimentoCsv> movimentos = new ArrayList<>();
        int anoAtual = 2025;
        int mesAnterior = 1;

        for (int idx = linhas.size() - 1; idx >= 0; idx--) {
            Map<String, String> row = linhas.get(idx);
            String colFornecedor = row.getOrDefault("fornecedor", "").trim();
            String colProduto = row.getOrDefault("produto", "").trim();
            String colQtd = row.getOrDefault("quantidade", "").trim();
            String colValor = row.getOrDefault("valor", "").trim();
            String colValorTotal = row.getOrDefault("valor total", "").trim();
            String colData = row.getOrDefault("data", "").trim();
            String colLucro = row.getOrDefault("lucro", "").trim();

            if (colFornecedor.isEmpty() || colProduto.isEmpty() || colData.isEmpty()) {
                continue;
            }

            String[] dm = colData.split("/");
            if (dm.length < 2) {
                continue;
            }
            int dia = Integer.parseInt(dm[0].trim());
            int mes = Integer.parseInt(dm[1].trim());
            if (mes < mesAnterior) {
                anoAtual++;
            }
            mesAnterior = mes;
            LocalDate data = LocalDate.of(anoAtual, mes, dia);

            BigDecimal quantidade = parsearNumeroBr(colQtd, BigDecimal.ZERO);
            if (quantidade.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal valor = parsearNumeroBr(colValor, BigDecimal.ZERO);
            BigDecimal valorTotal = parsearNumeroBr(colValorTotal, BigDecimal.ZERO);
            BigDecimal lucro = parsearNumeroBr(colLucro, BigDecimal.ZERO);

            TipoMovimento tipo;
            String fornNorm = colFornecedor.toLowerCase(Locale.ROOT);
            if (fornNorm.equals("saída") || fornNorm.equals("saida")) {
                if (valorTotal.compareTo(BigDecimal.ZERO) == 0 && lucro.compareTo(BigDecimal.ZERO) < 0) {
                    tipo = TipoMovimento.PERDA;
                } else {
                    tipo = TipoMovimento.VENDA;
                }
            } else {
                tipo = TipoMovimento.ENTRADA;
            }

            movimentos.add(new RegistroMovimentoCsv(
                    idx + 2, data, colFornecedor, colProduto, quantidade, valor, valorTotal, lucro, tipo
            ));
        }

        movimentos.sort(Comparator
                .comparing(RegistroMovimentoCsv::data)
                .thenComparingInt(m -> m.tipo() == TipoMovimento.ENTRADA ? 0 : 1)
                .thenComparingInt(RegistroMovimentoCsv::linhaOriginal));
        return movimentos;
    }

    private Map<String, Pessoa> cadastrarFornecedores(RoteadorController roteador, List<RegistroMovimentoCsv> movimentos) {
        Set<String> nomes = new LinkedHashSet<>();
        for (RegistroMovimentoCsv m : movimentos) {
            if (m.tipo() == TipoMovimento.ENTRADA) {
                nomes.add(m.fornecedorOuSaida());
            }
        }
        Map<String, Pessoa> mapa = new LinkedHashMap<>();
        for (String nome : nomes) {
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                    RotaSistema.PESSOA_SIMPLES_CADASTRAR,
                    Map.of("nome", nome, "papel", "FORNECEDOR")
            );
            if (resp.sucesso() && resp.dados() instanceof Pessoa p) {
                mapa.put(nome, p);
            }
        }
        return mapa;
    }

    private int cadastrarProdutosCartelaECaixa(
            RoteadorController roteador,
            List<RegistroMovimentoCsv> movimentos,
            Map<String, Produto> cartelasPorNome,
            Map<String, Produto> caixasPorNomeCartela
    ) {
        Set<String> produtos = new LinkedHashSet<>();
        for (RegistroMovimentoCsv m : movimentos) {
            produtos.add(m.produto());
        }

        int kits = 0;
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

                String nomeCaixa = "CAIXA " + nomeCartela;
                RespostaEndpoint<Object> respCaixa = roteador.executarEndpoint(
                        RotaSistema.PRODUTO_FISICO_CADASTRAR,
                        Map.of(
                                "nome", nomeCaixa,
                                "descricao", "Conjunto Caixa com 12 cartelas de " + nomeCartela,
                                "precoVenda", "200.00",
                                "siglaUm", "CX"
                        )
                );
                if (respCaixa.sucesso() && respCaixa.dados() instanceof Produto caixa) {
                    caixasPorNomeCartela.put(nomeCartela, caixa);
                    RespostaEndpoint<Object> respKit = roteador.executarEndpoint(
                            RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE,
                            Map.of(
                                    "idProdutoKit", caixa.getId(),
                                    "idProdutoComponente", cartela.getId(),
                                    "quantidade", "12.000000"
                            )
                    );
                    if (respKit.sucesso()) {
                        kits++;
                    }
                }
            }
        }
        return kits;
    }

    private NotaCompra emitirNotaCompraEmCaixasAgrupada(
            RoteadorController roteador,
            Pessoa fornecedor,
            LocalDate dataEntrada,
            List<RegistroMovimentoCsv> entradas,
            Map<String, Produto> caixasPorNomeCartela,
            Map<String, Produto> cartelasPorNome
    ) {
        if (fornecedor == null || entradas.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal totalEstimado = BigDecimal.ZERO;

        for (RegistroMovimentoCsv e : entradas) {
            Produto caixa = caixasPorNomeCartela.get(e.produto());
            Produto cartela = cartelasPorNome.get(e.produto());
            if (caixa == null) {
                continue;
            }
            // Quantidade da entrada é dividida por 12 para representar Caixas inteiras (ex.: 120 / 12 = 10 CX)
            BigDecimal[] divRem = e.quantidade().divideAndRemainder(CARTELAS_POR_CAIXA);
            BigDecimal qtdCaixasInteiras = divRem[0];
            BigDecimal qtdCartelasAvulsas = divRem[1];

            // Valor unitário na entrada é o valor da caixa de custo
            BigDecimal custoCaixa = e.valorUnitarioOuCaixa().compareTo(BigDecimal.ZERO) > 0
                    ? e.valorUnitarioOuCaixa()
                    : (e.quantidade().compareTo(BigDecimal.ZERO) > 0
                        ? e.valorTotal().divide(e.quantidade(), 4, RoundingMode.HALF_EVEN).multiply(CARTELAS_POR_CAIXA)
                        : BigDecimal.ZERO);

            if (qtdCaixasInteiras.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> itemCaixa = new LinkedHashMap<>();
                itemCaixa.put("idProduto", caixa.getId());
                itemCaixa.put("quantidade", qtdCaixasInteiras.toPlainString());
                itemCaixa.put("valorUnitario", custoCaixa.toPlainString());
                itemCaixa.put("dataValidade", dataEntrada.plusDays(DURABILIDADE_CARTELA_DIAS));
                itens.add(itemCaixa);
            }
            if (qtdCartelasAvulsas.compareTo(BigDecimal.ZERO) > 0 && cartela != null) {
                BigDecimal custoCartela = custoCaixa.divide(CARTELAS_POR_CAIXA, 4, RoundingMode.HALF_EVEN);
                Map<String, Object> itemCartela = new LinkedHashMap<>();
                itemCartela.put("idProduto", cartela.getId());
                itemCartela.put("quantidade", qtdCartelasAvulsas.toPlainString());
                itemCartela.put("valorUnitario", custoCartela.toPlainString());
                itemCartela.put("dataValidade", dataEntrada.plusDays(DURABILIDADE_CARTELA_DIAS));
                itens.add(itemCartela);
            }
        }

        if (itens.isEmpty()) {
            return null;
        }

        LocalDateTime dtCompra = dataEntrada.atTime(8, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idFornecedor", fornecedor.getId());
        params.put("descricao", "Entrada agrupada em Caixas (12 cartelas/cx) - " + fornecedor.getNome() + " em " + dataEntrada);
        params.put("itens", itens);
        params.put("dataGerada", dtCompra);

        RespostaEndpoint<Object> resp = roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, params);
        if (resp.sucesso() && resp.dados() instanceof NotaCompra nc) {
            return nc;
        }
        System.err.printf("  [AVISO] Falha ao emitir NotaCompra (%s | %s): %s%n",
                fornecedor.getNome(), dataEntrada, resp.mensagem());
        return null;
    }

    /**
     * Se não houver saldo direto suficiente de cartelas na data, desmembra lotes de Caixa (1 cx = 12 cartelas)
     * em ordem FIFO para disponibilizar cartelas avulsas com o custo proporcional (custoCaixa / 12) e mesma validade.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void garantirSaldoCartelasDesmembrandoCaixas(
            Produto cartela,
            Produto caixa,
            BigDecimal quantidadeNecessaria,
            LocalDate dataReferencia
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
                            "AND i.quantidadeDisponivel > 0 " +
                            "ORDER BY i.data ASC, i.id ASC",
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
                    if (faltante.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    BigDecimal dispCaixa = loteCaixa.getQuantidadeDisponivel();
                    if (dispCaixa == null || dispCaixa.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal caixasInteirasNecessarias = faltante.divide(CARTELAS_POR_CAIXA, 0, RoundingMode.CEILING);
                    BigDecimal caixasAbrir = dispCaixa.min(caixasInteirasNecessarias);
                    BigDecimal cartelasGeradas = caixasAbrir.multiply(CARTELAS_POR_CAIXA).setScale(6, RoundingMode.HALF_EVEN);

                    loteCaixa.setQuantidadeDisponivel(dispCaixa.subtract(caixasAbrir));
                    em.merge(loteCaixa);

                    BigDecimal custoCartela = loteCaixa.getValor() != null
                            ? loteCaixa.getValor().divide(CARTELAS_POR_CAIXA, 4, RoundingMode.HALF_EVEN)
                            : BigDecimal.ZERO;

                    LocalDate validadeCartela = loteCaixa.getDataValidade() != null
                            ? loteCaixa.getDataValidade()
                            : dataReferencia.plusDays(DURABILIDADE_CARTELA_DIAS);
                    Item itemCartela = new Item(cartela, custoCartela, cartelasGeradas, validadeCartela);
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
            RoteadorController roteador,
            Produto cartela,
            BigDecimal quantidadePerda,
            LocalDate dataPerda
    ) {
        BigDecimal restante = quantidadePerda;
        List<Item<? extends Produto>> lotes = buscarLotesComSaldo(cartela.getId());
        if (lotes.isEmpty()) {
            return false;
        }
        boolean baixouAlgo = false;
        for (Item<? extends Produto> lote : lotes) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal disponivel = lote.getQuantidadeDisponivel();
            if (disponivel == null || disponivel.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal baixar = disponivel.min(restante);
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                    Map.of(
                            "idLote", lote.getId(),
                            "quantidade", baixar.toPlainString(),
                            "motivo", "Perda registrada em " + dataPerda + " (valor total zerado e lucro negativo)",
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
                            "AND i.quantidadeDisponivel > 0 " +
                            "ORDER BY i.data ASC, i.id ASC",
                            Item.class)
                    .setParameter("idProd", idProduto)
                    .getResultList();
        }
    }

    private BigDecimal calcularPrecoUnitarioVenda(RegistroMovimentoCsv saida, Produto cartela) {
        if (saida.valorUnitarioOuCaixa().compareTo(BigDecimal.ZERO) > 0) {
            return saida.valorUnitarioOuCaixa();
        }
        if (saida.valorTotal().compareTo(BigDecimal.ZERO) > 0 && saida.quantidade().compareTo(BigDecimal.ZERO) > 0) {
            return saida.valorTotal().divide(saida.quantidade(), 2, RoundingMode.HALF_EVEN);
        }
        return cartela.getPrecoVenda() != null ? cartela.getPrecoVenda() : new BigDecimal("18.00");
    }

    private void atualizarPrecoSeNecessario(RoteadorController roteador, Produto cartela, BigDecimal novoPreco) {
        if (novoPreco == null || novoPreco.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (cartela.getPrecoVenda() != null && cartela.getPrecoVenda().compareTo(novoPreco) == 0) {
            return;
        }
        RespostaEndpoint<Object> resp = roteador.executarEndpoint(
                RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
                Map.of("idProduto", cartela.getId(), "novoPreco", novoPreco.toPlainString())
        );
        if (resp.sucesso()) {
            cartela.setPrecoVenda(novoPreco);
        }
    }

    private boolean quitarNotaCompra(RoteadorController roteador, NotaCompra nc, LocalDateTime dataQuitacao) {
        if (nc.getPagamentos() == null || nc.getPagamentos().isEmpty()) {
            return false;
        }
        boolean tudoQuitado = true;
        for (NotaPagamento np : nc.getPagamentos()) {
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

    private BigDecimal parsearNumeroBr(String bruto, BigDecimal padrao) {
        if (bruto == null || bruto.isBlank()) {
            return padrao;
        }
        String limpo = bruto.replace("R$", "").replace(" ", "").trim();
        if (limpo.isEmpty() || limpo.equals("-")) {
            return padrao;
        }
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
