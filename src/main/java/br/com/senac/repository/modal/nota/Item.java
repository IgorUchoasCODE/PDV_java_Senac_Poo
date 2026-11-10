package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "item",
        indexes = {
                @Index(name = "idx_item_fifo", columnList = "id_produto, quantidade_disponivel, data, data_validade")
        }
)
public class Item<T extends Produto> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota", nullable = false)
    private Nota nota;

    @ManyToOne(targetEntity = Produto.class, optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_produto", nullable = false)
    private T produto;

    @ManyToOne(targetEntity = Item.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_origem")
    private Item<T> itemOrigem;

    @Column(name = "valor", precision = 20, scale = 4, nullable = false)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "desconto", precision = 20, scale = 4, nullable = false)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "quantidade", precision = 20, scale = 6, nullable = false)
    private BigDecimal quantidade = BigDecimal.ZERO;

    /**
     * Saldo disponível no lote (alimentado na NotaCompra e abatido em O(1) pelo algoritmo FIFO na NotaVenda/NotaPerda).
     */
    @Column(name = "quantidade_disponivel", precision = 20, scale = 6, nullable = false)
    private BigDecimal quantidadeDisponivel = BigDecimal.ZERO;

    @Column(name = "data", nullable = false)
    private LocalDateTime data = LocalDateTime.now();

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    public Item() {
    }

    public Item(T produto, BigDecimal valorUnitario, BigDecimal quantidade, LocalDate dataValidade) {
        this.produto = produto;
        this.valor = MoedaContabilBacen.arredondarContabil(valorUnitario);
        this.quantidade = MoedaContabilBacen.arredondarQuantidade(quantidade);
        this.quantidadeDisponivel = this.quantidade;
        this.dataValidade = dataValidade;
        this.data = LocalDateTime.now();
    }

    public BigDecimal getSubtotalBruto() {
        if (produto != null) {
            return produto.calcularValorProporcional(valor, quantidade);
        }
        return MoedaContabilBacen.arredondarMoeda(valor.multiply(quantidade, MoedaContabilBacen.CONTEXTO_BACEN));
    }

    public BigDecimal getSubtotal() {
        return MoedaContabilBacen.arredondarMoeda(
                getSubtotalBruto().subtract(desconto != null ? desconto : BigDecimal.ZERO)
                        .max(BigDecimal.ZERO)
        );
    }

    /**
     * Retorna o custo unitário contábil do lote de origem (NotaCompra) para apuração exata do CMV/Lucro.
     * Caso seja um item de NotaVenda sem lote físico de compra (ex.: ProdutoDigital sob demanda), o CMV é zero.
     */
    public BigDecimal getCustoUnitarioOrigem() {
        if (itemOrigem != null && itemOrigem.getValor() != null) {
            return MoedaContabilBacen.arredondarContabil(itemOrigem.getValor());
        }
        if (nota instanceof NotaVenda) {
            return MoedaContabilBacen.ZERO_CONTABIL;
        }
        return MoedaContabilBacen.arredondarContabil(valor);
    }

    /**
     * Retorna o CMV (Custo da Mercadoria Vendida) proporcional à quantidade deste item, arredondado pelo padrão BACEN.
     */
    public BigDecimal getCustoTotalMercadoria() {
        BigDecimal custoUnit = getCustoUnitarioOrigem();
        if (produto != null) {
            return produto.calcularValorProporcional(custoUnit, quantidade);
        }
        return MoedaContabilBacen.arredondarMoeda(custoUnit.multiply(quantidade, MoedaContabilBacen.CONTEXTO_BACEN));
    }

    /**
     * Calcula o Lucro Bruto contábil deste item (Subtotal Bruto de Venda - CMV do Lote Origem).
     */
    public BigDecimal getLucroBrutoItem() {
        return MoedaContabilBacen.arredondarMoeda(getSubtotalBruto().subtract(getCustoTotalMercadoria()));
    }

    /**
     * Calcula o Lucro Líquido contábil deste item (Subtotal Líquido com Desconto - CMV do Lote Origem).
     */
    public BigDecimal getLucroLiquidoItem() {
        return MoedaContabilBacen.arredondarMoeda(getSubtotal().subtract(getCustoTotalMercadoria()));
    }

    /**
     * Calcula a Margem de Lucro Líquida (%) sobre o custo do lote de origem segundo arredondamento BACEN (HALF_EVEN).
     */
    public BigDecimal getMargemLucroPercentual() {
        BigDecimal cmv = getCustoTotalMercadoria();
        if (cmv.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return getLucroLiquidoItem()
                .multiply(MoedaContabilBacen.CEM, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(cmv, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
    }

    public void aplicarDesconto(BigDecimal valorDesconto) {
        if (valorDesconto != null && valorDesconto.compareTo(BigDecimal.ZERO) >= 0) {
            this.desconto = MoedaContabilBacen.arredondarContabil(valorDesconto);
        }
    }

    /**
     * Consome até o limite disponível neste lote (FIFO) e retorna a quantidade efetivamente abatida.
     */
    public BigDecimal consumirSaldoFIFO(BigDecimal qtdRequisitada) {
        if (qtdRequisitada == null || qtdRequisitada.compareTo(BigDecimal.ZERO) <= 0 || !possuiSaldoDisponivel()) {
            return BigDecimal.ZERO;
        }
        BigDecimal consumido = quantidadeDisponivel.min(qtdRequisitada);
        this.quantidadeDisponivel = this.quantidadeDisponivel.subtract(consumido);
        return consumido;
    }

    /**
     * Restaura o saldo ao lote original (em caso de NotaDevolucao dentro da validade).
     */
    public void restaurarSaldoLote(BigDecimal qtdEstornada) {
        if (qtdEstornada != null && qtdEstornada.compareTo(BigDecimal.ZERO) > 0) {
            this.quantidadeDisponivel = this.quantidadeDisponivel.add(qtdEstornada).min(this.quantidade);
        }
    }

    public boolean possuiSaldoDisponivel() {
        return quantidadeDisponivel != null && quantidadeDisponivel.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isDentroDaValidade(LocalDate dataReferencia) {
        if (dataValidade == null) {
            return true;
        }
        LocalDate ref = dataReferencia != null ? dataReferencia : LocalDate.now();
        return !ref.isAfter(dataValidade);
    }

    public boolean isQuantidadeValida() {
        return produto != null && produto.isQuantidadeValida(quantidade);
    }

    public Long getId() {
        return id;
    }

    public Nota getNota() {
        return nota;
    }

    public void setNota(Nota nota) {
        this.nota = nota;
        if (nota != null && !(nota instanceof NotaCompra)) {
            this.quantidadeDisponivel = MoedaContabilBacen.ZERO_QUANTIDADE;
        }
    }

    public T getProduto() {
        return produto;
    }

    public void setProduto(T produto) {
        this.produto = produto;
    }

    public Item<T> getItemOrigem() {
        return itemOrigem;
    }

    public void setItemOrigem(Item<T> itemOrigem) {
        this.itemOrigem = itemOrigem;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getQuantidadeDisponivel() {
        return quantidadeDisponivel;
    }

    public void setQuantidadeDisponivel(BigDecimal quantidadeDisponivel) {
        this.quantidadeDisponivel = quantidadeDisponivel;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }

    public LocalDate getDataValidade() {
        return dataValidade;
    }

    public void setDataValidade(LocalDate dataValidade) {
        this.dataValidade = dataValidade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item<?> outro)) return false;
        return id != null && id.equals(outro.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return String.format("Item{id=%d, produto='%s', qtd=%s, dispFIFO=%s, valorUnit=%s, validade=%s}",
                id, produto != null ? produto.getNome() : "null", quantidade, quantidadeDisponivel, valor, dataValidade);
    }
}
