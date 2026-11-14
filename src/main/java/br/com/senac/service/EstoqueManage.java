package br.com.senac.service;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.produto.ConjuntoProdutoDAO;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.Um;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EstoqueManage {

    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final ConjuntoProdutoDAO conjuntoProdutoDAO = new ConjuntoProdutoDAO();
    private final UmDAO umDAO = new UmDAO();
    private final NotaDAO notaDAO = new NotaDAO();

    public Produto cadastrarProduto(Produto produto) {
        produtoDAO.salvar(produto);
        return produto;
    }

    public void atualizarCatalogo(Produto produto) {
        produtoDAO.atualizar(produto);
    }

    public void configurarUnidadeMedida(Produto produto, Um um) {
        if (um.getId() == null) {
            umDAO.salvar(um);
        }
        produto.setUm(um);
        if (produto.getId() != null) {
            produtoDAO.atualizar(produto);
        }
    }

    public void montarConjunto(Produto produtoPai, Produto itemComponente, BigDecimal quantidade) {
        produtoPai.adicionarItemConjunto(itemComponente, quantidade);
        if (produtoPai.getId() != null) {
            produtoDAO.atualizar(produtoPai);
        }
    }

    public List<Item<? extends Produto>> registrarEntradaCompra(NotaCompra notaCompra) {
        return notaCompra.liberarItensParaVenda();
    }

    public List<Item<? extends Produto>> buscarLotesDisponiveisFIFO(Produto produto) {
        return notaDAO.buscarLotesDisponiveisFIFO(produto.getId());
    }

    /**
     * Reserva/seleciona os lotes necessários em ordem FIFO para atender a uma venda.
     */
    public List<Item<? extends Produto>> reservarItensParaVenda(Produto produto, BigDecimal quantidade) {
        NotaVenda vendaTemporaria = new NotaVenda();
        consumirEstoqueFIFO(vendaTemporaria, produto, quantidade);
        return vendaTemporaria.getItens();
    }

    /**
     * Permite ao operador/sistema escolher um Lote Desejável específico (pelo ID do lote)
     * para abater na NotaVenda, validando saldo disponível, validade e bloqueio concorrente.
     */
    public List<Item<? extends Produto>> consumirEstoquePorLoteDesejado(
            NotaVenda notaVenda,
            Long idLoteDesejado,
            BigDecimal quantidadeRequisitada) {

        Item<? extends Produto> loteEscolhido = notaDAO.buscarLotePorId(idLoteDesejado);
        if (loteEscolhido == null) {
            throw new IllegalArgumentException("Lote desejado (ID " + idLoteDesejado + ") não encontrado no estoque.");
        }
        notaVenda.registrarItemVendaPorLoteDesejado(loteEscolhido, quantidadeRequisitada);
        return Collections.singletonList(loteEscolhido);
    }

    /**
     * Baixa o estoque automaticamente utilizando o algoritmo FIFO (First-In, First-Out).
     * - Se o produto possuir lotes de entrada em estoque, consome na ordem cronológica FIFO.
     * - Se for um ProdutoDigital sem lote físico controlado, registra a venda direta da licença/acesso.
     * - Se for um Kit/Conjunto (isConjunto = true) sem lote pré-montado, desmembra nos itens componentes
     *   aplicando FIFO em cada componente e ajustando o desconto promocional do Kit.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Item<? extends Produto>> consumirEstoqueFIFO(
            NotaVenda notaVenda,
            Produto produto,
            BigDecimal quantidadeRequisitada) {

        List<Item<? extends Produto>> lotesAlterados = new ArrayList<>();
        List<Item<? extends Produto>> lotesDisponiveis = notaDAO.buscarTodosLotesComSaldoFIFO(produto.getId());

        BigDecimal saldoDireto = lotesDisponiveis.stream()
                .map(Item::getQuantidadeDisponivel)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal restante = quantidadeRequisitada;

        if (saldoDireto.compareTo(BigDecimal.ZERO) > 0) {
            // Regra de preservação de custo: se o produto (mesmo composto) já possui estoque próprio,
            // consome primeiro os lotes existentes mantendo seu custo original inalterado.
            BigDecimal qtdDoLoteDireto = saldoDireto.min(quantidadeRequisitada);
            List<Item<? extends Produto>> itensVenda = notaVenda.registrarItensVendaFIFO(lotesDisponiveis, qtdDoLoteDireto);
            for (Item<? extends Produto> iv : itensVenda) {
                if (iv.getItemOrigem() != null) {
                    lotesAlterados.add(iv.getItemOrigem());
                }
            }
            restante = restante.subtract(qtdDoLoteDireto);
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                return lotesAlterados;
            }
        }

        // Produtos digitais (cursos, e-books, licenças sob demanda) podem ser vendidos sem lote físico prévio
        if (produto.isDigital() && lotesDisponiveis.isEmpty()) {
            Item itemDigital = new Item(produto, produto.getPrecoVenda(), restante, null);
            itemDigital.setQuantidadeDisponivel(MoedaContabilBacen.ZERO_QUANTIDADE);
            notaVenda.addItem(itemDigital);
            return lotesAlterados;
        }

        // Se for um Kit/Conjunto sem estoque próprio suficiente:
        // Opção 1: tenta desmembrar nos componentes abaixo (caso haja estoque na folha/subnível).
        // Opção 2 (fallback): se não houver estoque abaixo, tenta abrir uma embalagem superior (ex.: Caixa -> Cartela).
        if (produto.isConjunto() && produto.getItensConjunto() != null && !produto.getItensConjunto().isEmpty()) {
            int idxInicioKit = notaVenda.getItens().size();
            try {
                for (ConjuntoProduto componente : produto.getItensConjunto()) {
                    BigDecimal qtdNecessariaComponente = componente.getQuantidade().multiply(restante);
                    lotesAlterados.addAll(consumirEstoqueFIFO(notaVenda, componente.getItem(), qtdNecessariaComponente));
                }

                List<Item<? extends Produto>> todosItensGeradosDoKit = new ArrayList<>();
                BigDecimal somaPrecoComponentes = BigDecimal.ZERO;
                for (int i = idxInicioKit; i < notaVenda.getItens().size(); i++) {
                    Item<? extends Produto> iv = notaVenda.getItens().get(i);
                    todosItensGeradosDoKit.add(iv);
                    // Usa o valor líquido atual (caso sub-kits já tenham aplicado rateio) como base proporcional
                    somaPrecoComponentes = somaPrecoComponentes.add(iv.getSubtotal());
                }

                // Preserva o preço do Kit rateando proporcionalmente (Padrão BACEN + resíduo) entre os lotes FIFO folha
                if (produto.getPrecoVenda() != null && produto.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0
                        && somaPrecoComponentes.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal precoTotalKit = produto.calcularValorProporcional(produto.getPrecoVenda(), restante);
                    BigDecimal diferencaKit = MoedaContabilBacen.arredondarMoeda(somaPrecoComponentes.subtract(precoTotalKit));
                    if (diferencaKit.compareTo(BigDecimal.ZERO) != 0 && !todosItensGeradosDoKit.isEmpty()) {
                        BigDecimal acumuladoRateado = BigDecimal.ZERO;
                        for (int i = 0; i < todosItensGeradosDoKit.size(); i++) {
                            Item<? extends Produto> itemKit = todosItensGeradosDoKit.get(i);
                            BigDecimal descontoRateado;
                            if (i == todosItensGeradosDoKit.size() - 1) {
                                descontoRateado = diferencaKit.subtract(acumuladoRateado);
                            } else {
                                descontoRateado = itemKit.getSubtotal()
                                        .multiply(diferencaKit, MoedaContabilBacen.CONTEXTO_BACEN)
                                        .divide(somaPrecoComponentes, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                                acumuladoRateado = acumuladoRateado.add(descontoRateado);
                            }
                            BigDecimal novoDesconto = MoedaContabilBacen.arredondarMoeda(itemKit.getDesconto().add(descontoRateado));
                            if (novoDesconto.compareTo(BigDecimal.ZERO) >= 0) {
                                itemKit.aplicarDesconto(novoDesconto);
                            } else {
                                // Kit com preço de venda superior à soma dos preços de tabela dos componentes
                                BigDecimal acrescimoTotalItem = novoDesconto.abs();
                                itemKit.aplicarDesconto(BigDecimal.ZERO);
                                if (itemKit.getQuantidade() != null && itemKit.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal acrescimoUnitario = acrescimoTotalItem.divide(
                                            itemKit.getQuantidade(), MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                                    itemKit.setValor(MoedaContabilBacen.arredondarContabil(itemKit.getValor().add(acrescimoUnitario)));
                                }
                            }
                        }
                    }
                }
                return lotesAlterados;
            } catch (IllegalStateException semEstoqueAbaixo) {
                while (notaVenda.getItens().size() > idxInicioKit) {
                    notaVenda.getItens().remove(notaVenda.getItens().size() - 1);
                }
                if (fracionarEmbalagemSuperiorFIFO(produto, restante, new HashSet<>())) {
                    List<Item<? extends Produto>> novosLotes = notaDAO.buscarTodosLotesComSaldoFIFO(produto.getId());
                    List<Item<? extends Produto>> itensVenda = notaVenda.registrarItensVendaFIFO(novosLotes, restante);
                    for (Item<? extends Produto> iv : itensVenda) {
                        if (iv.getItemOrigem() != null) {
                            lotesAlterados.add(iv.getItemOrigem());
                        }
                    }
                    return lotesAlterados;
                }
                throw semEstoqueAbaixo;
            }
        }

        // Opção 2 para produto simples/folha sem estoque direto: tenta abrir embalagem superior (ex.: Caixa -> Cartela -> Ovo)
        if (fracionarEmbalagemSuperiorFIFO(produto, restante, new HashSet<>())) {
            List<Item<? extends Produto>> novosLotes = notaDAO.buscarTodosLotesComSaldoFIFO(produto.getId());
            List<Item<? extends Produto>> itensVenda = notaVenda.registrarItensVendaFIFO(novosLotes, restante);
            for (Item<? extends Produto> iv : itensVenda) {
                if (iv.getItemOrigem() != null) {
                    lotesAlterados.add(iv.getItemOrigem());
                }
            }
            return lotesAlterados;
        }

        throw new IllegalStateException("Saldo insuficiente em estoque (FIFO) para o produto: "
                + produto.getNome() + " (Disponível: " + saldoDireto + ", Solicitado: " + quantidadeRequisitada + ")");
    }

    /**
     * Abre/fraciona embalagens de níveis superiores (ex.: Caixa -> 12 Cartelas -> 30 Ovos) em ordem FIFO,
     * baixando a embalagem pai e gerando lotes filhos com a mesma validade e custo unitário rateado pelo padrão BACEN.
     */
    public boolean fracionarEmbalagemSuperiorFIFO(Produto produtoAlvo, BigDecimal quantidadeNecessaria) {
        return fracionarEmbalagemSuperiorFIFO(produtoAlvo, quantidadeNecessaria, new HashSet<>());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean fracionarEmbalagemSuperiorFIFO(Produto produtoAlvo, BigDecimal quantidadeNecessaria, Set<Long> visitados) {
        if (produtoAlvo == null || produtoAlvo.getId() == null || quantidadeNecessaria == null
                || quantidadeNecessaria.compareTo(BigDecimal.ZERO) <= 0 || !visitados.add(produtoAlvo.getId())) {
            return false;
        }

        List<ConjuntoProduto> vinculosPai = conjuntoProdutoDAO.listarPaisPorItem(produtoAlvo.getId());
        for (ConjuntoProduto vinculo : vinculosPai) {
            if (vinculo.getQuantidade() == null || vinculo.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Produto pai = produtoDAO.buscarComItensConjunto(vinculo.getProduto().getId());
            if (pai == null) {
                continue;
            }
            BigDecimal qtdFilhoPorPai = vinculo.getQuantidade();
            BigDecimal qtdPaisParaAbrir = quantidadeNecessaria.divide(qtdFilhoPorPai, 0, RoundingMode.CEILING);

            List<Item<? extends Produto>> lotesPai = notaDAO.buscarTodosLotesComSaldoFIFO(pai.getId());
            BigDecimal saldoPai = lotesPai.stream()
                    .map(Item::getQuantidadeDisponivel)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (saldoPai.compareTo(qtdPaisParaAbrir) < 0) {
                BigDecimal faltaPai = qtdPaisParaAbrir.subtract(saldoPai);
                if (!fracionarEmbalagemSuperiorFIFO(pai, faltaPai, visitados)) {
                    continue;
                }
                lotesPai = notaDAO.buscarTodosLotesComSaldoFIFO(pai.getId());
                saldoPai = lotesPai.stream()
                        .map(Item::getQuantidadeDisponivel)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (saldoPai.compareTo(qtdPaisParaAbrir) < 0) {
                    continue;
                }
            }

            BigDecimal restanteAbrir = qtdPaisParaAbrir;
            for (Item<? extends Produto> lotePai : lotesPai) {
                if (restanteAbrir.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                if (!lotePai.possuiSaldoDisponivel()) {
                    continue;
                }
                BigDecimal qtdConsumidaLote = lotePai.getQuantidadeDisponivel().min(restanteAbrir);
                lotePai.setQuantidadeDisponivel(lotePai.getQuantidadeDisponivel().subtract(qtdConsumidaLote));
                restanteAbrir = restanteAbrir.subtract(qtdConsumidaLote);

                BigDecimal custoTotalLote = pai.calcularValorProporcional(
                        MoedaContabilBacen.arredondarContabil(lotePai.getValor()), qtdConsumidaLote);

                NotaCompra notaFracionamento = new NotaCompra(
                        lotePai.getNota() != null ? lotePai.getNota().getPessoa() : null,
                        "Fracionamento de embalagem: " + pai.getNome()
                );
                notaFracionamento.setDataRecebida(LocalDateTime.now());

                List<ConjuntoProduto> componentesPai = pai.getItensConjunto();
                if (componentesPai.size() == 1) {
                    ConjuntoProduto unico = componentesPai.get(0);
                    BigDecimal qtdGerada = qtdConsumidaLote.multiply(unico.getQuantidade());
                    BigDecimal custoUnitarioFilho = custoTotalLote.divide(
                            qtdGerada, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                    Item itemFilho = new Item(unico.getItem(), custoUnitarioFilho, qtdGerada, lotePai.getDataValidade());
                    itemFilho.setQuantidadeDisponivel(qtdGerada);
                    notaFracionamento.addItem(itemFilho);
                } else {
                    BigDecimal somaPeso = BigDecimal.ZERO;
                    for (ConjuntoProduto comp : componentesPai) {
                        BigDecimal precoComp = comp.getItem().getPrecoVenda() != null
                                && comp.getItem().getPrecoVenda().compareTo(BigDecimal.ZERO) > 0
                                ? comp.getItem().getPrecoVenda()
                                : BigDecimal.ONE;
                        somaPeso = somaPeso.add(precoComp.multiply(comp.getQuantidade()));
                    }
                    BigDecimal custoAlocadoAcumulado = BigDecimal.ZERO;
                    for (int i = 0; i < componentesPai.size(); i++) {
                        ConjuntoProduto comp = componentesPai.get(i);
                        BigDecimal qtdGerada = qtdConsumidaLote.multiply(comp.getQuantidade());
                        BigDecimal custoComponenteTotal;
                        if (i == componentesPai.size() - 1) {
                            custoComponenteTotal = custoTotalLote.subtract(custoAlocadoAcumulado);
                        } else {
                            BigDecimal precoComp = comp.getItem().getPrecoVenda() != null
                                    && comp.getItem().getPrecoVenda().compareTo(BigDecimal.ZERO) > 0
                                    ? comp.getItem().getPrecoVenda()
                                    : BigDecimal.ONE;
                            custoComponenteTotal = custoTotalLote
                                    .multiply(precoComp.multiply(comp.getQuantidade()), MoedaContabilBacen.CONTEXTO_BACEN)
                                    .divide(somaPeso, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                            custoAlocadoAcumulado = custoAlocadoAcumulado.add(custoComponenteTotal);
                        }
                        BigDecimal custoUnitarioFilho = custoComponenteTotal.divide(
                                qtdGerada, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                        Item itemFilho = new Item(comp.getItem(), custoUnitarioFilho, qtdGerada, lotePai.getDataValidade());
                        itemFilho.setQuantidadeDisponivel(qtdGerada);
                        notaFracionamento.addItem(itemFilho);
                    }
                }

                notaDAO.salvarComSincronizacaoFIFO(notaFracionamento, Collections.singletonList(lotePai));
            }
            return true;
        }
        return false;
    }

    /**
     * Retorna o saldo físico direto somado ao saldo equivalente disponível em embalagens superiores fechadas
     * (ex.: 5 Caixas x 12 Cartelas x 30 Ovos = 1.800 Ovos disponíveis para fracionamento).
     */
    public BigDecimal calcularSaldoEquivalenteTotal(Long idProduto) {
        return calcularSaldoEquivalenteTotal(idProduto, new HashSet<>());
    }

    private BigDecimal calcularSaldoEquivalenteTotal(Long idProduto, Set<Long> visitados) {
        if (idProduto == null || !visitados.add(idProduto)) {
            return MoedaContabilBacen.ZERO_QUANTIDADE;
        }
        BigDecimal saldo = notaDAO.consultarSaldoTotalProduto(idProduto);
        if (saldo == null) {
            saldo = MoedaContabilBacen.ZERO_QUANTIDADE;
        }
        for (ConjuntoProduto vinculo : conjuntoProdutoDAO.listarPaisPorItem(idProduto)) {
            if (vinculo.getProduto() != null && vinculo.getQuantidade() != null
                    && vinculo.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal saldoPai = calcularSaldoEquivalenteTotal(vinculo.getProduto().getId(), visitados);
                if (saldoPai.compareTo(BigDecimal.ZERO) > 0) {
                    saldo = saldo.add(saldoPai.multiply(vinculo.getQuantidade()));
                }
            }
        }
        return saldo;
    }

    /**
     * Calcula o custo contábil FIFO (CMV estimado) de um produto simples ou composto multinível
     * (ex.: Cesta Básica ou Caixa -> Cartela -> Ovos) sem alterar o saldo dos lotes:
     * - Se o produto (mesmo composto) já possuir estoque próprio, preserva o custo dos seus lotes existentes.
     * - Para a quantidade sem estoque próprio, soma recursivamente os custos FIFO de todos os itens
     *   dos níveis abaixo.
     */
    public BigDecimal calcularCustoCompostoFIFO(Produto produto, BigDecimal quantidadeRequisitada) {
        if (produto == null || quantidadeRequisitada == null || quantidadeRequisitada.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }

        List<Item<? extends Produto>> lotesDisponiveis = notaDAO.buscarTodosLotesComSaldoFIFO(produto.getId());
        BigDecimal restante = quantidadeRequisitada;
        BigDecimal custoTotal = BigDecimal.ZERO;

        for (Item<? extends Produto> lote : lotesDisponiveis) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (!lote.possuiSaldoDisponivel()) {
                continue;
            }
            BigDecimal qtdNoLote = lote.getQuantidadeDisponivel().min(restante);
            BigDecimal custoUnitarioLote = MoedaContabilBacen.arredondarContabil(lote.getValor());
            custoTotal = custoTotal.add(produto.calcularValorProporcional(custoUnitarioLote, qtdNoLote));
            restante = restante.subtract(qtdNoLote);
        }

        if (restante.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.arredondarMoeda(custoTotal);
        }

        if (produto.isConjunto() && produto.getItensConjunto() != null && !produto.getItensConjunto().isEmpty()) {
            try {
                BigDecimal custoSubItens = BigDecimal.ZERO;
                for (ConjuntoProduto componente : produto.getItensConjunto()) {
                    BigDecimal qtdComponente = componente.getQuantidade().multiply(restante);
                    custoSubItens = custoSubItens.add(calcularCustoCompostoFIFO(componente.getItem(), qtdComponente));
                }
                return MoedaContabilBacen.arredondarMoeda(custoTotal.add(custoSubItens));
            } catch (IllegalStateException semEstoqueAbaixo) {
                BigDecimal custoViaPai = tentarCalcularCustoViaEmbalagemSuperior(produto, restante);
                if (custoViaPai != null) {
                    return MoedaContabilBacen.arredondarMoeda(custoTotal.add(custoViaPai));
                }
                throw semEstoqueAbaixo;
            }
        }

        if (produto.isDigital() && lotesDisponiveis.isEmpty()) {
            return MoedaContabilBacen.arredondarMoeda(custoTotal);
        }

        BigDecimal custoViaPai = tentarCalcularCustoViaEmbalagemSuperior(produto, restante);
        if (custoViaPai != null) {
            return MoedaContabilBacen.arredondarMoeda(custoTotal.add(custoViaPai));
        }

        throw new IllegalStateException("Saldo insuficiente em estoque (FIFO) para calcular custo de: "
                + produto.getNome() + " (Solicitado: " + quantidadeRequisitada + ", Faltante: " + restante + ")");
    }

    private BigDecimal tentarCalcularCustoViaEmbalagemSuperior(Produto produtoAlvo, BigDecimal quantidadeFaltante) {
        if (produtoAlvo == null || produtoAlvo.getId() == null) {
            return null;
        }
        for (ConjuntoProduto vinculo : conjuntoProdutoDAO.listarPaisPorItem(produtoAlvo.getId())) {
            if (vinculo.getProduto() == null || vinculo.getQuantidade() == null
                    || vinculo.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Produto pai = produtoDAO.buscarComItensConjunto(vinculo.getProduto().getId());
            if (pai == null) {
                continue;
            }
            BigDecimal qtdPaisEquivalente = quantidadeFaltante.divide(
                    vinculo.getQuantidade(), MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
            try {
                BigDecimal custoUmPai = calcularCustoDiretoOuPaiFIFO(pai, BigDecimal.ONE, new HashSet<>());
                if (custoUmPai != null) {
                    return MoedaContabilBacen.arredondarMoeda(custoUmPai.multiply(qtdPaisEquivalente, MoedaContabilBacen.CONTEXTO_BACEN));
                }
            } catch (IllegalStateException ignored) {
                // Tenta próximo vínculo pai
            }
        }
        return null;
    }

    private BigDecimal calcularCustoDiretoOuPaiFIFO(Produto produto, BigDecimal quantidade, Set<Long> visitados) {
        if (produto == null || produto.getId() == null || !visitados.add(produto.getId())) {
            return null;
        }
        List<Item<? extends Produto>> lotes = notaDAO.buscarTodosLotesComSaldoFIFO(produto.getId());
        BigDecimal saldo = lotes.stream().map(Item::getQuantidadeDisponivel).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (saldo.compareTo(quantidade) >= 0) {
            BigDecimal rest = quantidade;
            BigDecimal total = BigDecimal.ZERO;
            for (Item<? extends Produto> lote : lotes) {
                if (rest.compareTo(BigDecimal.ZERO) <= 0) break;
                if (!lote.possuiSaldoDisponivel()) continue;
                BigDecimal q = lote.getQuantidadeDisponivel().min(rest);
                total = total.add(produto.calcularValorProporcional(MoedaContabilBacen.arredondarContabil(lote.getValor()), q));
                rest = rest.subtract(q);
            }
            return total;
        }
        for (ConjuntoProduto vinculo : conjuntoProdutoDAO.listarPaisPorItem(produto.getId())) {
            if (vinculo.getProduto() != null && vinculo.getQuantidade() != null
                    && vinculo.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal custoUmPai = calcularCustoDiretoOuPaiFIFO(vinculo.getProduto(), BigDecimal.ONE, visitados);
                if (custoUmPai != null) {
                    return custoUmPai.divide(vinculo.getQuantidade(), MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN)
                            .multiply(quantidade, MoedaContabilBacen.CONTEXTO_BACEN);
                }
            }
        }
        return null;
    }

    /**
     * Reintegra os itens devolvidos ao seu lote de compra original caso estejam dentro da validade
     * e NÃO sejam fruto de avaria/perda pós-venda (notaPerdaGerada == null).
     */
    public List<Item<? extends Produto>> reintegrarItensDevolucaoFIFO(NotaDevolucao notaDevolucao) {
        List<Item<? extends Produto>> lotesRestaurados = new ArrayList<>();
        if (!notaDevolucao.isDevolucaoDentroValidade() || notaDevolucao.getNotaPerdaGerada() != null) {
            return lotesRestaurados;
        }
        for (Item<? extends Produto> itemDev : notaDevolucao.getItens()) {
            Item<? extends Produto> itemVenda = itemDev.getItemOrigem();
            Item<? extends Produto> loteCompra = itemVenda != null ? itemVenda.getItemOrigem() : null;
            if (loteCompra != null) {
                loteCompra.restaurarSaldoLote(itemDev.getQuantidade());
                lotesRestaurados.add(loteCompra);
            }
        }
        return lotesRestaurados;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public NotaPerda registrarPerdaEstoque(Item<? extends Produto> itemOrigem, BigDecimal quantidade, String motivo) {
        BigDecimal abatido = itemOrigem.consumirSaldoFIFO(quantidade);
        if (abatido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Lote sem saldo disponível para baixa de perda.");
        }
        NotaPerda perda = new NotaPerda("PERDA_ESTOQUE", motivo, null);
        Item itemPerda = new Item(itemOrigem.getProduto(), itemOrigem.getValor(), abatido, itemOrigem.getDataValidade());
        itemPerda.setQuantidadeDisponivel(BigDecimal.ZERO);
        itemPerda.setItemOrigem(itemOrigem);
        perda.addItem(itemPerda);

        if (itemOrigem.getNota() instanceof NotaCompra nc) {
            perda.setNotaCompraOrigem(nc);
            perda.setPessoa(nc.getPessoa());
        }
        return perda;
    }

    public BigDecimal consultarSaldoEstoque(Produto produto) {
        return notaDAO.consultarSaldoTotalProduto(produto.getId());
    }

    public List<Item<? extends Produto>> listarItensProximosValidade(LocalDate dataReferencia) {
        return notaDAO.listarItensProximosValidade(dataReferencia);
    }
}
