package br.com.senac.service;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.nota.NotaDAO;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EstoqueManage {

    private final ProdutoDAO produtoDAO = new ProdutoDAO();
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
        List<Item<? extends Produto>> lotesDisponiveis = buscarLotesDisponiveisFIFO(produto);

        BigDecimal saldoDireto = lotesDisponiveis.stream()
                .map(Item::getQuantidadeDisponivel)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (saldoDireto.compareTo(quantidadeRequisitada) >= 0) {
            List<Item<? extends Produto>> itensVenda = notaVenda.registrarItensVendaFIFO(lotesDisponiveis, quantidadeRequisitada);
            for (Item<? extends Produto> iv : itensVenda) {
                if (iv.getItemOrigem() != null) {
                    lotesAlterados.add(iv.getItemOrigem());
                }
            }
            return lotesAlterados;
        }

        // Produtos digitais (cursos, e-books, licenças sob demanda) podem ser vendidos sem lote físico prévio
        if (produto.isDigital() && lotesDisponiveis.isEmpty()) {
            Item itemDigital = new Item(produto, produto.getPrecoVenda(), quantidadeRequisitada, null);
            itemDigital.setQuantidadeDisponivel(MoedaContabilBacen.ZERO_QUANTIDADE);
            notaVenda.addItem(itemDigital);
            return lotesAlterados;
        }

        // Se for um Kit/Conjunto, desmembra nos itens componentes aplicando FIFO em cada componente
        if (produto.isConjunto() && produto.getItensConjunto() != null && !produto.getItensConjunto().isEmpty()) {
            BigDecimal somaPrecoComponentes = BigDecimal.ZERO;
            List<Item<? extends Produto>> todosItensGeradosDoKit = new ArrayList<>();

            for (ConjuntoProduto componente : produto.getItensConjunto()) {
                BigDecimal qtdNecessariaComponente = componente.getQuantidade().multiply(quantidadeRequisitada);
                List<Item<? extends Produto>> lotesComp = buscarLotesDisponiveisFIFO(componente.getItem());
                List<Item<? extends Produto>> itensVendaComp = notaVenda.registrarItensVendaFIFO(lotesComp, qtdNecessariaComponente);
                for (Item<? extends Produto> iv : itensVendaComp) {
                    somaPrecoComponentes = somaPrecoComponentes.add(iv.getSubtotalBruto());
                    todosItensGeradosDoKit.add(iv);
                    if (iv.getItemOrigem() != null) {
                        lotesAlterados.add(iv.getItemOrigem());
                    }
                }
            }

            // Preserva o preço promocional do Kit rateando proporcionalmente (Padrão BACEN + resíduo) entre os lotes FIFO gerados
            if (produto.getPrecoVenda() != null && produto.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal precoTotalKit = produto.calcularValorProporcional(produto.getPrecoVenda(), quantidadeRequisitada);
                BigDecimal diferencaKit = MoedaContabilBacen.arredondarMoeda(somaPrecoComponentes.subtract(precoTotalKit));
                if (diferencaKit.compareTo(BigDecimal.ZERO) > 0 && !todosItensGeradosDoKit.isEmpty()) {
                    BigDecimal acumuladoRateado = BigDecimal.ZERO;
                    for (int i = 0; i < todosItensGeradosDoKit.size(); i++) {
                        Item<? extends Produto> itemKit = todosItensGeradosDoKit.get(i);
                        BigDecimal descontoRateado;
                        if (i == todosItensGeradosDoKit.size() - 1) {
                            descontoRateado = diferencaKit.subtract(acumuladoRateado);
                        } else {
                            descontoRateado = itemKit.getSubtotalBruto()
                                    .multiply(diferencaKit, MoedaContabilBacen.CONTEXTO_BACEN)
                                    .divide(somaPrecoComponentes, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
                            acumuladoRateado = acumuladoRateado.add(descontoRateado);
                        }
                        itemKit.aplicarDesconto(MoedaContabilBacen.arredondarMoeda(itemKit.getDesconto().add(descontoRateado)));
                    }
                }
            }
            return lotesAlterados;
        }

        throw new IllegalStateException("Saldo insuficiente em estoque (FIFO) para o produto: "
                + produto.getNome() + " (Disponível: " + saldoDireto + ", Solicitado: " + quantidadeRequisitada + ")");
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
