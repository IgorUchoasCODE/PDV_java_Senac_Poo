package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.interfaces.OperacaoComDesconto;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("VENDA")
public class NotaVenda extends Nota implements OperacaoComDesconto {

    @Column(name = "cupom_aplicado", length = 60)
    private String cupomAplicado;

    @OneToMany(mappedBy = "notaVenda", fetch = FetchType.LAZY)
    private List<NotaDevolucao> devolucoes = new ArrayList<>();

    public NotaVenda() {
        super();
    }

    public NotaVenda(Pessoa cliente, String descricao) {
        super(cliente, descricao);
    }

    public NotaVenda(Pessoa cliente, String descricao, LocalDateTime dataGerada) {
        super(cliente, descricao, dataGerada);
    }

    public boolean validarLimiteCliente() {
        if (getPessoa() == null || !getPessoa().isCliente()) {
            return false;
        }
        BigDecimal valorExigidoNoLimite = calcularValorExigidoLimiteCliente();
        if (valorExigidoNoLimite.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        // Cliente padrão não registrado (CPF 000.000.000-00) não pode comprar no crediário/limite da loja
        if (getPessoa() instanceof br.com.senac.repository.modal.pessoa.PessoaFisica pf
                && "000.000.000-00".equals(pf.getCpf())) {
            return false;
        }
        return getPessoa().getLimite() != null && getPessoa().getLimite().compareTo(valorExigidoNoLimite) >= 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Item<? extends Produto> registrarItemVenda(Item<? extends Produto> itemEstoqueOrigem, BigDecimal quantidade) {
        if (itemEstoqueOrigem == null) {
            throw new IllegalArgumentException("O lote de estoque de origem deve ser informado.");
        }
        if (!itemEstoqueOrigem.isDentroDaValidade( java.time.LocalDate.now())) {
            throw new IllegalStateException("O lote escolhido (ID " + itemEstoqueOrigem.getId() + ") está fora da validade.");
        }
        if (itemEstoqueOrigem.getQuantidadeDisponivel().compareTo(quantidade) < 0) {
            throw new IllegalStateException("Saldo insuficiente no lote escolhido (ID " + itemEstoqueOrigem.getId()
                    + "). Disponível: " + itemEstoqueOrigem.getQuantidadeDisponivel() + ", Solicitado: " + quantidade);
        }
        BigDecimal consumido = itemEstoqueOrigem.consumirSaldoFIFO(quantidade);
        Produto prod = itemEstoqueOrigem.getProduto();
        BigDecimal precoVenda = prod.getPrecoVenda() != null && prod.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0
                ? prod.getPrecoVenda()
                : itemEstoqueOrigem.getValor();

        Item itemVenda = new Item(prod, precoVenda, consumido, itemEstoqueOrigem.getDataValidade());
        itemVenda.setQuantidadeDisponivel(BigDecimal.ZERO);
        itemVenda.setItemOrigem(itemEstoqueOrigem);
        addItem(itemVenda);
        return itemVenda;
    }

    /**
     * Permite escolher explicitamente o Lote Desejável para a venda (validando saldo e validade do lote escolhido).
     */
    public Item<? extends Produto> registrarItemVendaPorLoteDesejado(Item<? extends Produto> loteDesejado, BigDecimal quantidade) {
        return registrarItemVenda(loteDesejado, quantidade);
    }

    /**
     * Algoritmo FIFO de baixa de estoque: consome em ordem cronológica os lotes mais antigos (FIFO/FEFO),
     * desmembrando automaticamente em múltiplos itens de venda caso um único lote não cubra a quantidade total.
     */
    public List<Item<? extends Produto>> registrarItensVendaFIFO(
            List<Item<? extends Produto>> lotesOrdenadosFIFO,
            BigDecimal quantidadeTotal) {

        BigDecimal restante = quantidadeTotal;
        List<Item<? extends Produto>> itensGerados = new ArrayList<>();

        for (Item<? extends Produto> lote : lotesOrdenadosFIFO) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (!lote.possuiSaldoDisponivel()) {
                continue;
            }
            BigDecimal qtdParaConsumirNoLote = lote.getQuantidadeDisponivel().min(restante);
            Item<? extends Produto> itemGerado = registrarItemVenda(lote, qtdParaConsumirNoLote);
            itensGerados.add(itemGerado);
            restante = restante.subtract(qtdParaConsumirNoLote);
        }

        if (restante.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Estoque insuficiente (FIFO). Faltam " + restante + " unidades.");
        }
        return itensGerados;
    }

    public void registrarDevolucao(NotaDevolucao notaDevolucao) {
        notaDevolucao.setNotaVenda(this);
        this.devolucoes.add(notaDevolucao);
    }

    public void finalizarVenda() {
        if (!validarNota()) {
            throw new IllegalStateException("Nota de venda inválida ou limite do cliente excedido.");
        }
        setDataRecebida(LocalDateTime.now());
    }

    @Override
    public BigDecimal getValorTotal() {
        BigDecimal somaItens = getItens().stream()
                .map(Item::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MoedaContabilBacen.arredondarMoeda(
                somaItens.subtract(getDescontoGlobal()).max(BigDecimal.ZERO)
        );
    }

    /**
     * Calcula o CMV (Custo Total das Mercadorias Vendidas) desta nota com base nos lotes FIFO/específicos consumidos.
     */
    public BigDecimal getCustoTotalMercadoriaVendida() {
        return MoedaContabilBacen.arredondarMoeda(
                getItens().stream()
                        .map(Item::getCustoTotalMercadoria)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    /**
     * Calcula o Lucro Bruto Contábil da Nota de Venda (Valor Bruto - CMV Total), arredondado pelo padrão BACEN.
     */
    public BigDecimal getLucroBrutoVenda() {
        return MoedaContabilBacen.arredondarMoeda(
                getValorBruto().subtract(getCustoTotalMercadoriaVendida())
        );
    }

    /**
     * Calcula o Lucro Líquido Contábil da Nota de Venda (Valor Total Líquido de Descontos - CMV Total - Taxas de Operadora).
     */
    public BigDecimal getLucroLiquidoVenda() {
        return MoedaContabilBacen.arredondarMoeda(
                getValorTotal().subtract(getCustoTotalMercadoriaVendida()).subtract(getCustoTaxasPagamento())
        );
    }

    /**
     * Soma todas as taxas/tarifas de operadora (MDR %, tarifa de boleto, etc.) parametrizadas na tabela FormaPagamento.
     */
    public BigDecimal getCustoTaxasPagamento() {
        return MoedaContabilBacen.arredondarMoeda(
                getPagamentos().stream()
                        .map(br.com.senac.repository.modal.pagamento.NotaPagamento::calcularCustoTaxaOperadora)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    /**
     * Calcula a Margem de Lucro Líquida (%) da Nota de Venda segundo arredondamento BACEN (HALF_EVEN).
     */
    public BigDecimal getMargemLucroPercentual() {
        BigDecimal cmv = getCustoTotalMercadoriaVendida();
        if (cmv.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return getLucroLiquidoVenda()
                .multiply(MoedaContabilBacen.CEM, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(cmv, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
    }

    @Override
    public void atualizar() {
        // Recalcula estado da venda
    }

    @Override
    public boolean validarNota() {
        boolean pagamentosValidos = getPagamentos().isEmpty()
                || getPagamentos().stream().allMatch(br.com.senac.repository.modal.pagamento.NotaPagamento::isPagamentoValido);
        return !getItens().isEmpty()
                && getItens().stream().allMatch(Item::isQuantidadeValida)
                && pagamentosValidos
                && (getPessoa() == null || validarLimiteCliente());
    }

    @Override
    public void aplicarDescontoNota(BigDecimal desconto) {
        setDescontoGlobal(desconto);
    }

    @Override
    public void aplicarDescontoProduto(Produto produto, BigDecimal desconto) {
        getItens().stream()
                .filter(i -> i.getProduto().equals(produto))
                .forEach(i -> i.aplicarDesconto(desconto));
    }

    @Override
    public void aplicarDescontoProdutos(List<Produto> produtos, BigDecimal desconto) {
        if (produtos == null) return;
        produtos.forEach(p -> aplicarDescontoProduto(p, desconto));
    }

    @Override
    public void aplicarCupomDesconto(String codigoCupom, BigDecimal valorDesconto) {
        this.cupomAplicado = codigoCupom;
        setDescontoGlobal(getDescontoGlobal().add(valorDesconto != null ? valorDesconto : BigDecimal.ZERO));
    }

    public String getCupomAplicado() {
        return cupomAplicado;
    }

    public List<NotaDevolucao> getDevolucoes() {
        return devolucoes;
    }
}
