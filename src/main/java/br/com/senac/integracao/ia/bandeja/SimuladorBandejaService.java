package br.com.senac.integracao.ia.bandeja;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.CompiladorInstrucoesCurtaService;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Simulador financeiro e de estoque para itens em espera na {@link BandejaOperacoesContexto}.
 *
 * <p>Projeta em memória (sem alterar o banco de dados) o efeito das operações pendentes/validadas
 * combinadas com o catálogo, lotes FIFO e composições atuais do sistema, respeitando o padrão
 * contábil BACEN ({@link MoedaContabilBacen#ARREDONDAMENTO_BACEN}).
 */
public final class SimuladorBandejaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private SimuladorBandejaService() {
    }

    private static final class ComponenteSimulado {
        ProdutoSimulado produto;
        BigDecimal quantidade;

        ComponenteSimulado(ProdutoSimulado produto, BigDecimal quantidade) {
            this.produto = produto;
            this.quantidade = quantidade;
        }
    }

    private static final class ProdutoSimulado {
        String chave;
        Long idBanco;
        String opOrigem;
        String nome;
        String siglaUm = "UN";
        BigDecimal precoVendaAtual;
        BigDecimal precoVendaProjetado;
        BigDecimal custoUnitarioDb;
        BigDecimal estoqueAtual = BigDecimal.ZERO;
        BigDecimal deltaEstoqueBandeja = BigDecimal.ZERO;
        BigDecimal qtdCompraBandeja = BigDecimal.ZERO;
        BigDecimal totalCompraBandeja = BigDecimal.ZERO;
        BigDecimal qtdVendaBandeja = BigDecimal.ZERO;
        BigDecimal totalVendaBandeja = BigDecimal.ZERO;
        boolean ehKit;
        final List<ComponenteSimulado> componentes = new ArrayList<>();

        BigDecimal getCustoUnitarioCompraBandeja() {
            if (qtdCompraBandeja.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return totalCompraBandeja.divide(qtdCompraBandeja, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        }
    }

    private static final class EstadoSimulacao {
        final Map<String, ProdutoSimulado> porChave = new LinkedHashMap<>();
        final Map<Long, ProdutoSimulado> porIdBanco = new HashMap<>();
        final Map<String, ProdutoSimulado> porOp = new HashMap<>();
        final Map<String, ProdutoSimulado> porNomeNormalizado = new HashMap<>();
        BigDecimal totalComprasBandeja = BigDecimal.ZERO;
        BigDecimal totalVendasBandeja = BigDecimal.ZERO;
        BigDecimal totalDescontoVendasBandeja = BigDecimal.ZERO;
        BigDecimal totalPerdasBandeja = BigDecimal.ZERO;
        final List<String> notasObservacoes = new ArrayList<>();
    }

    /**
     * Gera um bloco estruturado e preciso para alimentar a IA com custos, margens,
     * descontos de kits, pontos de equilíbrio e impacto em estoque antes da confirmação.
     */
    public static String gerarContextoSimulacaoParaIA(BandejaOperacoesContexto bandeja, RoteadorController roteador) {
        if (bandeja == null || !bandeja.temItens()) {
            return "";
        }
        EstadoSimulacao estado = simular(bandeja, roteador);
        if (estado.porChave.isEmpty()
                && estado.totalComprasBandeja.compareTo(BigDecimal.ZERO) == 0
                && estado.totalVendasBandeja.compareTo(BigDecimal.ZERO) == 0
                && estado.totalPerdasBandeja.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder("[Simulação Financeira e de Estoque da Bandeja (Padrão BACEN - Antes de Confirmar)]:\n");
        appendResumoProdutos(sb, estado, true);
        appendResumoOperacoes(sb, estado);
        return sb.toString().trim();
    }

    /**
     * Gera um relatório legível e direto ao operador humano para perguntas ou comando direto de simulação.
     */
    public static String gerarRelatorioSimulacaoUsuario(BandejaOperacoesContexto bandeja, RoteadorController roteador) {
        if (bandeja == null || !bandeja.temItens()) {
            return "Não há instruções na bandeja para simular.";
        }
        EstadoSimulacao estado = simular(bandeja, roteador);
        if (estado.porChave.isEmpty()
                && estado.totalComprasBandeja.compareTo(BigDecimal.ZERO) == 0
                && estado.totalVendasBandeja.compareTo(BigDecimal.ZERO) == 0
                && estado.totalPerdasBandeja.compareTo(BigDecimal.ZERO) == 0) {
            return "As instruções atuais da bandeja não envolvem movimentação financeira ou de estoque simulável.";
        }

        StringBuilder sb = new StringBuilder("SIMULAÇÃO FINANCEIRA E DE ESTOQUE DA BANDEJA (ANTES DE SALVAR):\n");
        appendResumoProdutos(sb, estado, false);
        appendResumoOperacoes(sb, estado);
        return sb.toString().trim();
    }

    private static void appendResumoProdutos(StringBuilder sb, EstadoSimulacao estado, boolean modoIA) {
        for (ProdutoSimulado p : estado.porChave.values()) {
            BigDecimal custoEfetivo = calcularCustoEfetivo(p, new HashSet<>());
            BigDecimal precoVenda = p.precoVendaProjetado != null ? p.precoVendaProjetado : p.precoVendaAtual;
            BigDecimal estoqueProjetado = p.estoqueAtual.add(p.deltaEstoqueBandeja);

            sb.append(" • ").append(p.nome);
            if (p.idBanco != null) {
                sb.append(" (#").append(p.idBanco).append(")");
            } else if (p.opOrigem != null) {
                sb.append(" (").append(p.opOrigem).append(")");
            }
            sb.append(" [").append(p.siglaUm).append("]:\n");

            if (precoVenda != null) {
                sb.append("   - Preço de venda projetado: R$ ").append(fmtMoeda(precoVenda));
                if (p.precoVendaAtual != null && p.precoVendaAtual.compareTo(precoVenda) != 0) {
                    sb.append(" (antes: R$ ").append(fmtMoeda(p.precoVendaAtual)).append(")");
                }
                sb.append("\n");
            }

            if (custoEfetivo != null) {
                String origemCusto = p.ehKit ? "soma dos componentes"
                        : (p.getCustoUnitarioCompraBandeja() != null ? "compra na bandeja" : "lotes atuais em estoque");
                sb.append("   - Custo unitário estimado (").append(origemCusto).append("): R$ ")
                  .append(fmtMoeda(custoEfetivo)).append("\n");
            }

            if (custoEfetivo != null && precoVenda != null && precoVenda.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lucroUnit = precoVenda.subtract(custoEfetivo);
                BigDecimal margemPct = lucroUnit.multiply(CEM)
                        .divide(precoVenda, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                sb.append("   - Lucro unitário projetado: R$ ").append(fmtMoeda(lucroUnit))
                  .append(" (margem sobre venda: ").append(fmtMoeda(margemPct)).append("%");
                if (custoEfetivo.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal markupPct = lucroUnit.multiply(CEM)
                            .divide(custoEfetivo, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                    sb.append(", markup sobre custo: ").append(fmtMoeda(markupPct)).append("%");
                }
                sb.append(")\n");
            }

            if (p.ehKit && !p.componentes.isEmpty()) {
                BigDecimal precoAvulso = BigDecimal.ZERO;
                boolean todosComponentesComPreco = true;
                StringBuilder compDesc = new StringBuilder();
                for (ComponenteSimulado comp : p.componentes) {
                    if (compDesc.length() > 0) compDesc.append(", ");
                    compDesc.append(fmtQtd(comp.quantidade)).append("x ").append(comp.produto.nome);
                    BigDecimal pvComp = comp.produto.precoVendaProjetado != null
                            ? comp.produto.precoVendaProjetado
                            : comp.produto.precoVendaAtual;
                    if (pvComp != null && pvComp.compareTo(BigDecimal.ZERO) > 0) {
                        precoAvulso = precoAvulso.add(pvComp.multiply(comp.quantidade, MoedaContabilBacen.CONTEXTO_BACEN));
                    } else {
                        todosComponentesComPreco = false;
                    }
                }
                sb.append("   - Composição do kit: ").append(compDesc).append("\n");
                if (todosComponentesComPreco && precoVenda != null && precoAvulso.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal desconto = precoAvulso.subtract(precoVenda);
                    BigDecimal pctDesc = desconto.multiply(CEM)
                            .divide(precoAvulso, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                    sb.append("   - Comparativo avulso: soma dos itens avulsos = R$ ").append(fmtMoeda(precoAvulso))
                      .append(" | ").append(desconto.compareTo(BigDecimal.ZERO) >= 0 ? "Desconto do combo" : "Acréscimo do combo")
                      .append(": R$ ").append(fmtMoeda(desconto.abs())).append(" (").append(fmtMoeda(pctDesc.abs())).append("%)\n");
                }
            }

            if (p.qtdCompraBandeja.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("   - Entrada de compra na bandeja: +").append(fmtQtd(p.qtdCompraBandeja))
                  .append(" ").append(p.siglaUm).append(" | Investimento: R$ ").append(fmtMoeda(p.totalCompraBandeja)).append("\n");
                if (precoVenda != null && precoVenda.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal receitaAvulsa = precoVenda.multiply(p.qtdCompraBandeja, MoedaContabilBacen.CONTEXTO_BACEN);
                    BigDecimal lucroAvulsoTotal = receitaAvulsa.subtract(p.totalCompraBandeja);
                    sb.append("   - Potencial vendendo todo o lote avulso: Receita R$ ").append(fmtMoeda(receitaAvulsa))
                      .append(" | Lucro bruto R$ ").append(fmtMoeda(lucroAvulsoTotal)).append("\n");
                }
                appendCenariosKitsParaComponente(sb, estado, p);
            }

            if (p.deltaEstoqueBandeja.compareTo(BigDecimal.ZERO) != 0 || p.idBanco != null) {
                sb.append("   - Estoque: atual ").append(fmtQtd(p.estoqueAtual)).append(" ").append(p.siglaUm);
                if (p.deltaEstoqueBandeja.compareTo(BigDecimal.ZERO) != 0) {
                    sb.append(" -> projetado após confirmar: ").append(fmtQtd(estoqueProjetado)).append(" ").append(p.siglaUm);
                }
                sb.append("\n");
            }

            if (modoIA && p.qtdVendaBandeja.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("   - Saída em venda na bandeja: ").append(fmtQtd(p.qtdVendaBandeja))
                  .append(" ").append(p.siglaUm).append(" (R$ ").append(fmtMoeda(p.totalVendaBandeja)).append(")\n");
            }
        }
    }

    private static void appendCenariosKitsParaComponente(StringBuilder sb, EstadoSimulacao estado, ProdutoSimulado componente) {
        for (ProdutoSimulado kit : estado.porChave.values()) {
            if (!kit.ehKit || kit.componentes.isEmpty()) continue;
            for (ComponenteSimulado cs : kit.componentes) {
                if (cs.produto == componente && cs.quantidade.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal precoKit = kit.precoVendaProjetado != null ? kit.precoVendaProjetado : kit.precoVendaAtual;
                    BigDecimal custoKit = calcularCustoEfetivo(kit, new HashSet<>());
                    BigDecimal kitsPossiveis = componente.qtdCompraBandeja.divideToIntegralValue(cs.quantidade);
                    if (kitsPossiveis.compareTo(BigDecimal.ZERO) > 0 && precoKit != null && precoKit.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal receitaKits = precoKit.multiply(kitsPossiveis, MoedaContabilBacen.CONTEXTO_BACEN);
                        BigDecimal custoTotalKits = custoKit != null
                                ? custoKit.multiply(kitsPossiveis, MoedaContabilBacen.CONTEXTO_BACEN)
                                : componente.totalCompraBandeja;
                        BigDecimal lucroKits = receitaKits.subtract(custoTotalKits);
                        BigDecimal lucroPorKit = custoKit != null ? precoKit.subtract(custoKit) : null;
                        sb.append("   - Potencial vendendo via '").append(kit.nome).append("' (")
                          .append(fmtQtd(kitsPossiveis)).append(" kits possíveis com a compra): Receita R$ ")
                          .append(fmtMoeda(receitaKits)).append(" | Lucro bruto total R$ ").append(fmtMoeda(lucroKits));
                        if (lucroPorKit != null) {
                            sb.append(" (R$ ").append(fmtMoeda(lucroPorKit)).append(" por kit)");
                        }
                        sb.append("\n");

                        // Ponto de equilíbrio (payback do investimento da compra)
                        if (componente.totalCompraBandeja.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal paybackKits = componente.totalCompraBandeja
                                    .divide(precoKit, 0, RoundingMode.CEILING);
                            sb.append("   - Ponto de equilíbrio (para pagar os R$ ").append(fmtMoeda(componente.totalCompraBandeja))
                              .append(" da compra): ").append(fmtQtd(paybackKits)).append(" kit(s)");
                            BigDecimal precoComp = componente.precoVendaProjetado != null
                                    ? componente.precoVendaProjetado : componente.precoVendaAtual;
                            if (precoComp != null && precoComp.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal paybackAvulso = componente.totalCompraBandeja
                                        .divide(precoComp, 0, RoundingMode.CEILING);
                                sb.append(" ou ").append(fmtQtd(paybackAvulso)).append(" unidade(s) avulsa(s)");
                            }
                            sb.append("\n");
                        }
                    }
                }
            }
        }
    }

    private static void appendResumoOperacoes(StringBuilder sb, EstadoSimulacao estado) {
        if (estado.totalComprasBandeja.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" • Total previsto em compras na bandeja: R$ ").append(fmtMoeda(estado.totalComprasBandeja)).append("\n");
        }
        if (estado.totalVendasBandeja.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal receitaLiquida = estado.totalVendasBandeja.subtract(estado.totalDescontoVendasBandeja).max(BigDecimal.ZERO);
            BigDecimal cmvEstimado = BigDecimal.ZERO;
            boolean cmvCompleto = true;
            for (ProdutoSimulado p : estado.porChave.values()) {
                if (p.qtdVendaBandeja.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal c = calcularCustoEfetivo(p, new HashSet<>());
                    if (c != null) {
                        cmvEstimado = cmvEstimado.add(c.multiply(p.qtdVendaBandeja, MoedaContabilBacen.CONTEXTO_BACEN));
                    } else {
                        cmvCompleto = false;
                    }
                }
            }
            sb.append(" • Total previsto em vendas na bandeja: R$ ").append(fmtMoeda(receitaLiquida));
            if (cmvCompleto && cmvEstimado.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lucroVenda = receitaLiquida.subtract(cmvEstimado);
                sb.append(" | CMV estimado: R$ ").append(fmtMoeda(cmvEstimado))
                  .append(" | Lucro bruto da venda: R$ ").append(fmtMoeda(lucroVenda));
            }
            sb.append("\n");
        }
        if (estado.totalPerdasBandeja.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" • Custo previsto de perdas em estoque: R$ ").append(fmtMoeda(estado.totalPerdasBandeja)).append("\n");
        }
        for (String obs : estado.notasObservacoes) {
            sb.append(" • Obs.: ").append(obs).append("\n");
        }
    }

    private static EstadoSimulacao simular(BandejaOperacoesContexto bandeja, RoteadorController roteador) {
        EstadoSimulacao estado = new EstadoSimulacao();
        for (ItemBandeja item : bandeja.getItens()) {
            if (item.getStatus() == ItemBandeja.StatusItem.EXECUTADO_SUCESSO) {
                continue;
            }
            processarItemBandeja(item, estado, roteador);
        }
        return estado;
    }

    private static void processarItemBandeja(ItemBandeja item, EstadoSimulacao estado, RoteadorController roteador) {
        RotaSistema rota = item.getRota();
        if (rota == null) return;
        Map<String, Object> p = item.getParametros();

        switch (rota) {
            case PRODUTO_FISICO_CADASTRAR, PRODUTO_DIGITAL_CADASTRAR -> {
                String nome = str(p, "nome", item.getIdItem());
                String um = str(p, "siglaUm", "UN").toUpperCase(Locale.ROOT);
                BigDecimal preco = parseBd(p.get("precoVenda"));
                ProdutoSimulado ps = obterOuCriarProdutoPorOp(estado, item.getIdItem(), nome, um);
                if (preco != null) {
                    ps.precoVendaProjetado = preco;
                }
            }
            case PRODUTO_CATALOGO_ATUALIZAR -> {
                ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, p.get("idProduto"),
                        str(p, "nomeAtual", str(p, "nome", null)));
                if (ps != null) {
                    String novoNome = str(p, "novoNome", str(p, "nome", null));
                    if (novoNome != null && !novoNome.isBlank()) {
                        ps.nome = novoNome;
                        estado.porNomeNormalizado.put(normalizarChave(novoNome), ps);
                    }
                }
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, p.get("idProduto"),
                        str(p, "nomeProduto", str(p, "nome", null)));
                BigDecimal novoPreco = parseBd(p.get("novoPreco"));
                if (ps != null && novoPreco != null) {
                    ps.precoVendaProjetado = novoPreco;
                }
            }
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                ProdutoSimulado kit = resolverProdutoSimulado(estado, roteador, p.get("idProdutoKit"), str(p, "nomeKit", null));
                ProdutoSimulado comp = resolverProdutoSimulado(estado, roteador, p.get("idProdutoComponente"), str(p, "nomeComponente", null));
                BigDecimal qtd = parseBd(p.get("quantidade"));
                if (qtd == null || qtd.compareTo(BigDecimal.ZERO) <= 0) {
                    qtd = BigDecimal.ONE;
                }
                if (kit != null && comp != null && kit != comp) {
                    kit.ehKit = true;
                    adicionarOuSomarComponente(kit, comp, qtd);
                }
            }
            case NOTA_COMPRA_EMITIR -> simularCompra(p, estado, roteador);
            case NOTA_VENDA_EMITIR_FIFO, NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> simularVenda(p, estado, roteador);
            case NOTA_VENDA_PROCEDURE_MYSQL_FIFO -> {
                ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, p.get("idProduto"), null);
                BigDecimal qtd = parseBdOrDefault(p.get("quantidade"), BigDecimal.ONE);
                BigDecimal preco = parseBd(p.get("precoVenda"));
                if (ps != null) {
                    BigDecimal pu = preco != null && preco.compareTo(BigDecimal.ZERO) > 0
                            ? preco : (ps.precoVendaProjetado != null ? ps.precoVendaProjetado : BigDecimal.ZERO);
                    BigDecimal sub = pu.multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN);
                    ps.qtdVendaBandeja = ps.qtdVendaBandeja.add(qtd);
                    ps.totalVendaBandeja = ps.totalVendaBandeja.add(sub);
                    ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.subtract(qtd);
                    estado.totalVendasBandeja = estado.totalVendasBandeja.add(sub);
                }
            }
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> simularPerda(p, estado, roteador);
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void simularCompra(Map<String, Object> p, EstadoSimulacao estado, RoteadorController roteador) {
        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }
        if (itensObj instanceof List<?> lista) {
            for (Object el : lista) {
                if (el instanceof Map<?, ?> m) {
                    Map<String, Object> im = (Map<String, Object>) m;
                    Object idRef = im.get("idProduto") != null ? im.get("idProduto") : im.get("id");
                    String nomeRef = str(im, "nomeProduto", str(im, "nome", null));
                    ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, idRef, nomeRef);
                    if (ps == null && nomeRef != null) {
                        ps = obterOuCriarProdutoPorNome(estado, nomeRef, "UN");
                    }
                    if (ps == null) continue;

                    BigDecimal qtd = parseBdOrDefault(im.get("quantidade"), BigDecimal.ONE);
                    BigDecimal unit = primeiroValorNaoNulo(im, "valorUnitario", "valor", "precoUnitario", "preco", "valorCusto");
                    if (unit == null) unit = BigDecimal.ZERO;
                    BigDecimal desc = parseBdOrDefault(im.get("desconto"), BigDecimal.ZERO);
                    BigDecimal totalItem = unit.multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN).subtract(desc).max(BigDecimal.ZERO);

                    ps.qtdCompraBandeja = ps.qtdCompraBandeja.add(qtd);
                    ps.totalCompraBandeja = ps.totalCompraBandeja.add(totalItem);
                    ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.add(qtd);
                    estado.totalComprasBandeja = estado.totalComprasBandeja.add(totalItem);
                }
            }
        } else if (itensObj instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, e.getKey(), String.valueOf(e.getKey()));
                if (ps == null) continue;
                BigDecimal qtd = BigDecimal.ONE;
                BigDecimal unit = BigDecimal.ZERO;
                if (e.getValue() instanceof Map<?, ?> vm) {
                    Map<String, Object> valMap = (Map<String, Object>) vm;
                    qtd = parseBdOrDefault(valMap.get("quantidade"), BigDecimal.ONE);
                    BigDecimal u = primeiroValorNaoNulo(valMap, "valorUnitario", "valor", "precoUnitario", "preco", "valorCusto");
                    if (u != null) unit = u;
                } else {
                    qtd = parseBdOrDefault(e.getValue(), BigDecimal.ONE);
                }
                BigDecimal totalItem = unit.multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN);
                ps.qtdCompraBandeja = ps.qtdCompraBandeja.add(qtd);
                ps.totalCompraBandeja = ps.totalCompraBandeja.add(totalItem);
                ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.add(qtd);
                estado.totalComprasBandeja = estado.totalComprasBandeja.add(totalItem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void simularVenda(Map<String, Object> p, EstadoSimulacao estado, RoteadorController roteador) {
        BigDecimal desconto = parseBd(p.get("descontoCupom"));
        if (desconto == null) desconto = parseBd(p.get("descontoGlobal"));
        if (desconto != null && desconto.compareTo(BigDecimal.ZERO) > 0) {
            estado.totalDescontoVendasBandeja = estado.totalDescontoVendasBandeja.add(desconto);
        }

        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }
        if (itensObj instanceof List<?> lista) {
            for (Object el : lista) {
                if (el instanceof Map<?, ?> m) {
                    Map<String, Object> im = (Map<String, Object>) m;
                    Object idRef = im.get("idProduto") != null ? im.get("idProduto") : im.get("id");
                    String nomeRef = str(im, "nomeProduto", str(im, "nome", null));
                    ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, idRef, nomeRef);
                    if (ps == null) continue;

                    BigDecimal qtd = parseBdOrDefault(im.get("quantidade"), BigDecimal.ONE);
                    BigDecimal pv = ps.precoVendaProjetado != null ? ps.precoVendaProjetado
                            : (ps.precoVendaAtual != null ? ps.precoVendaAtual : BigDecimal.ZERO);
                    BigDecimal sub = pv.multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN);

                    ps.qtdVendaBandeja = ps.qtdVendaBandeja.add(qtd);
                    ps.totalVendaBandeja = ps.totalVendaBandeja.add(sub);
                    ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.subtract(qtd);
                    estado.totalVendasBandeja = estado.totalVendasBandeja.add(sub);
                }
            }
        } else if (itensObj instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                ProdutoSimulado ps = resolverProdutoSimulado(estado, roteador, e.getKey(), String.valueOf(e.getKey()));
                if (ps == null) continue;
                BigDecimal qtd = parseBdOrDefault(e.getValue(), BigDecimal.ONE);
                BigDecimal pv = ps.precoVendaProjetado != null ? ps.precoVendaProjetado
                        : (ps.precoVendaAtual != null ? ps.precoVendaAtual : BigDecimal.ZERO);
                BigDecimal sub = pv.multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN);
                ps.qtdVendaBandeja = ps.qtdVendaBandeja.add(qtd);
                ps.totalVendaBandeja = ps.totalVendaBandeja.add(sub);
                ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.subtract(qtd);
                estado.totalVendasBandeja = estado.totalVendasBandeja.add(sub);
            }
        }
    }

    private static void simularPerda(Map<String, Object> p, EstadoSimulacao estado, RoteadorController roteador) {
        if (roteador == null) return;
        Long idLote = parseLong(p.get("idLote") != null ? p.get("idLote") : p.get("idItemLote"));
        if (idLote == null) return;
        try {
            Item<? extends Produto> lote = roteador.estoque().buscarLoteEspecificoPorId(idLote);
            if (lote == null || lote.getProduto() == null) return;
            BigDecimal qtd = parseBdOrDefault(p.get("quantidade") != null ? p.get("quantidade") : p.get("quantidadePerda"), BigDecimal.ONE);
            ProdutoSimulado ps = registrarProdutoBanco(estado, roteador, lote.getProduto());
            ps.deltaEstoqueBandeja = ps.deltaEstoqueBandeja.subtract(qtd);
            if (lote.getValor() != null) {
                estado.totalPerdasBandeja = estado.totalPerdasBandeja.add(
                        lote.getValor().multiply(qtd, MoedaContabilBacen.CONTEXTO_BACEN));
            }
        } catch (Exception ignored) {
        }
    }

    private static BigDecimal calcularCustoEfetivo(ProdutoSimulado p, Set<String> visitados) {
        if (p == null || !visitados.add(p.chave)) {
            return null;
        }
        try {
            if (p.ehKit && !p.componentes.isEmpty()) {
                BigDecimal soma = BigDecimal.ZERO;
                boolean todosComCusto = true;
                for (ComponenteSimulado c : p.componentes) {
                    BigDecimal custoComp = calcularCustoEfetivo(c.produto, visitados);
                    if (custoComp == null) {
                        todosComCusto = false;
                        break;
                    }
                    soma = soma.add(custoComp.multiply(c.quantidade, MoedaContabilBacen.CONTEXTO_BACEN));
                }
                if (todosComCusto) {
                    return soma;
                }
            }
            BigDecimal custoCompra = p.getCustoUnitarioCompraBandeja();
            if (custoCompra != null && custoCompra.compareTo(BigDecimal.ZERO) > 0) {
                return custoCompra;
            }
            return p.custoUnitarioDb;
        } finally {
            visitados.remove(p.chave);
        }
    }

    private static ProdutoSimulado obterOuCriarProdutoPorOp(EstadoSimulacao estado, String opId, String nome, String um) {
        String opUpper = opId.trim().toUpperCase(Locale.ROOT);
        ProdutoSimulado existente = estado.porOp.get(opUpper);
        if (existente != null) {
            return existente;
        }
        ProdutoSimulado ps = new ProdutoSimulado();
        ps.chave = opUpper;
        ps.opOrigem = opUpper;
        ps.nome = CompiladorInstrucoesCurtaService.limparNomeExibicao(nome, um);
        ps.siglaUm = um;
        estado.porChave.put(ps.chave, ps);
        estado.porOp.put(opUpper, ps);
        estado.porNomeNormalizado.put(normalizarChave(ps.nome), ps);
        return ps;
    }

    private static ProdutoSimulado obterOuCriarProdutoPorNome(EstadoSimulacao estado, String nome, String um) {
        String norm = normalizarChave(nome);
        ProdutoSimulado existente = estado.porNomeNormalizado.get(norm);
        if (existente != null) {
            return existente;
        }
        ProdutoSimulado ps = new ProdutoSimulado();
        ps.chave = "NOME:" + norm;
        ps.nome = nome.trim();
        ps.siglaUm = um;
        estado.porChave.put(ps.chave, ps);
        estado.porNomeNormalizado.put(norm, ps);
        return ps;
    }

    private static ProdutoSimulado resolverProdutoSimulado(
            EstadoSimulacao estado, RoteadorController roteador, Object idObj, String nomeObj) {
        if (idObj != null) {
            String s = idObj.toString().trim();
            String sUpper = s.toUpperCase(Locale.ROOT);
            if (sUpper.startsWith("OP-")) {
                ProdutoSimulado op = estado.porOp.get(sUpper);
                if (op != null) return op;
            }
            Long id = parseLong(idObj);
            if (id != null) {
                ProdutoSimulado emCache = estado.porIdBanco.get(id);
                if (emCache != null) return emCache;
                if (roteador != null) {
                    try {
                        Produto prod = roteador.produtos().buscarPorId(id);
                        if (prod != null) {
                            return registrarProdutoBanco(estado, roteador, prod);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (nomeObj != null && !nomeObj.isBlank()) {
            String norm = normalizarChave(nomeObj);
            ProdutoSimulado emCache = estado.porNomeNormalizado.get(norm);
            if (emCache != null) return emCache;
            for (ProdutoSimulado ps : estado.porChave.values()) {
                if (normalizarChave(ps.nome).contains(norm) || norm.contains(normalizarChave(ps.nome))) {
                    return ps;
                }
            }
            if (roteador != null) {
                try {
                    Produto prod = roteador.produtos().buscarPorNome(nomeObj.trim());
                    if (prod == null) {
                        List<Produto> lista = roteador.produtos().buscarPorTermo(nomeObj.trim());
                        if (!lista.isEmpty()) prod = lista.get(0);
                    }
                    if (prod != null) {
                        return registrarProdutoBanco(estado, roteador, prod);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static ProdutoSimulado registrarProdutoBanco(EstadoSimulacao estado, RoteadorController roteador, Produto prod) {
        if (prod == null) return null;
        if (prod.getId() != null && estado.porIdBanco.containsKey(prod.getId())) {
            return estado.porIdBanco.get(prod.getId());
        }
        String um = prod.getUm() != null ? prod.getUm().getSigla() : "UN";
        String nomeLimpo = CompiladorInstrucoesCurtaService.limparNomeExibicao(prod.getNome(), um);
        String norm = normalizarChave(nomeLimpo);

        ProdutoSimulado ps = new ProdutoSimulado();
        ps.chave = prod.getId() != null ? "ID:" + prod.getId() : "NOME:" + norm;
        ps.idBanco = prod.getId();
        ps.nome = nomeLimpo;
        ps.siglaUm = um;
        ps.precoVendaAtual = prod.getPrecoVenda();
        ps.precoVendaProjetado = prod.getPrecoVenda();
        ps.ehKit = prod.isConjunto();
        estado.porChave.put(ps.chave, ps);
        if (ps.idBanco != null) {
            estado.porIdBanco.put(ps.idBanco, ps);
        }
        estado.porNomeNormalizado.put(norm, ps);

        if (roteador != null && prod.getId() != null) {
            try {
                BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(prod.getId());
                if (saldo != null) {
                    ps.estoqueAtual = saldo;
                }
            } catch (Exception ignored) {
            }
            try {
                List<Item<? extends Produto>> lotes = roteador.estoque().listarLotesFisicosProduto(prod.getId());
                if (lotes != null && !lotes.isEmpty()) {
                    BigDecimal somaValor = BigDecimal.ZERO;
                    BigDecimal somaQtd = BigDecimal.ZERO;
                    for (Item<? extends Produto> l : lotes) {
                        if (l.getValor() != null && l.getQuantidadeDisponivel() != null
                                && l.getQuantidadeDisponivel().compareTo(BigDecimal.ZERO) > 0) {
                            somaValor = somaValor.add(l.getValor().multiply(l.getQuantidadeDisponivel(), MoedaContabilBacen.CONTEXTO_BACEN));
                            somaQtd = somaQtd.add(l.getQuantidadeDisponivel());
                        }
                    }
                    if (somaQtd.compareTo(BigDecimal.ZERO) > 0) {
                        ps.custoUnitarioDb = somaValor.divide(somaQtd, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                    } else if (lotes.get(0).getValor() != null) {
                        ps.custoUnitarioDb = lotes.get(0).getValor();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (prod.isConjunto() && prod.getItensConjunto() != null) {
            for (ConjuntoProduto cp : prod.getItensConjunto()) {
                if (cp.getItem() != null && cp.getQuantidade() != null) {
                    ProdutoSimulado compSim = registrarProdutoBanco(estado, roteador, cp.getItem());
                    if (compSim != null) {
                        adicionarOuSomarComponente(ps, compSim, cp.getQuantidade());
                    }
                }
            }
        }
        return ps;
    }

    private static void adicionarOuSomarComponente(ProdutoSimulado kit, ProdutoSimulado comp, BigDecimal qtd) {
        for (ComponenteSimulado existente : kit.componentes) {
            if (existente.produto == comp) {
                existente.quantidade = existente.quantidade.add(qtd);
                return;
            }
        }
        kit.componentes.add(new ComponenteSimulado(comp, qtd));
    }

    private static BigDecimal primeiroValorNaoNulo(Map<String, Object> m, String... chaves) {
        for (String k : chaves) {
            if (m.containsKey(k)) {
                BigDecimal v = parseBd(m.get(k));
                if (v != null) return v;
            }
        }
        return null;
    }

    private static String str(Map<String, Object> m, String k, String padrao) {
        if (m == null || !m.containsKey(k) || m.get(k) == null) return padrao;
        String s = m.get(k).toString().trim();
        return s.isEmpty() ? padrao : s;
    }

    private static Long parseLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = o.toString().trim().replace("#", "");
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseBdOrDefault(Object o, BigDecimal padrao) {
        BigDecimal v = parseBd(o);
        return v != null ? v : padrao;
    }

    private static BigDecimal parseBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = o.toString().trim().replace("R$", "").replace(" ", "");
        if (s.isEmpty()) return null;
        if (s.contains(",") && s.contains(".")) {
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                s = s.replace(".", "").replace(",", ".");
            } else {
                s = s.replace(",", "");
            }
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizarChave(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String fmtMoeda(BigDecimal valor) {
        return MoedaContabilBacen.arredondarMoeda(valor).toPlainString();
    }

    private static String fmtQtd(BigDecimal qtd) {
        if (qtd == null) return "0";
        BigDecimal stripped = qtd.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : stripped.toPlainString();
    }
}
