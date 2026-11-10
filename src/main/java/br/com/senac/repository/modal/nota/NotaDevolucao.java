package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("DEVOLUCAO")
public class NotaDevolucao extends Nota {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota_venda")
    private NotaVenda notaVenda;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @OneToOne(mappedBy = "notaDevolucao", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NotaPerda notaPerdaGerada;

    public NotaDevolucao() {
        super();
    }

    public NotaDevolucao(NotaVenda notaVenda, String motivo) {
        super(notaVenda != null ? notaVenda.getPessoa() : null, "Devolução: " + motivo);
        this.notaVenda = notaVenda;
        this.motivo = motivo;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Item<? extends Produto> adicionarItemDevolvido(Item<? extends Produto> itemVendaOrigem, BigDecimal quantidade) {
        if (itemVendaOrigem == null || quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade devolvida inválida para o item de venda.");
        }
        BigDecimal jaDevolvido = calcularQuantidadeJaDevolvida(itemVendaOrigem);
        BigDecimal saldoDevolvivel = itemVendaOrigem.getQuantidade().subtract(jaDevolvido);
        if (quantidade.compareTo(saldoDevolvivel) > 0) {
            throw new IllegalArgumentException("Quantidade devolvida (" + quantidade
                    + ") excede o saldo devolvível do item de venda (" + saldoDevolvivel + ").");
        }
        Item itemDev = new Item(
                itemVendaOrigem.getProduto(),
                itemVendaOrigem.getValor(),
                quantidade,
                itemVendaOrigem.getDataValidade()
        );
        // Rateia proporcionalmente o desconto do item de venda (promoção de item ou rateio de Kit) segundo o BACEN
        if (itemVendaOrigem.getDesconto() != null
                && itemVendaOrigem.getDesconto().compareTo(BigDecimal.ZERO) > 0
                && itemVendaOrigem.getQuantidade().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal descontoItemProporcional = itemVendaOrigem.getDesconto()
                    .multiply(quantidade, MoedaContabilBacen.CONTEXTO_BACEN)
                    .divide(itemVendaOrigem.getQuantidade(), MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
            itemDev.aplicarDesconto(descontoItemProporcional);
        }
        itemDev.setQuantidadeDisponivel(BigDecimal.ZERO);
        itemDev.setItemOrigem(itemVendaOrigem);
        addItem(itemDev);
        recalcularRateioCupomGlobalDaVenda();
        return itemDev;
    }

    /**
     * Calcula e aplica proporcionalmente a fração do Desconto Global / Cupom da NotaVenda original
     * sobre os itens devolvidos, garantindo que o estorno nunca ultrapasse o valor líquido efetivamente pago.
     */
    public void recalcularRateioCupomGlobalDaVenda() {
        if (notaVenda == null || notaVenda.getDescontoGlobal() == null
                || notaVenda.getDescontoGlobal().compareTo(BigDecimal.ZERO) <= 0) {
            setDescontoGlobal(MoedaContabilBacen.ZERO_MOEDA);
            return;
        }
        BigDecimal somaSubtotaisVendaOriginal = notaVenda.getItens().stream()
                .map(Item::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaSubtotaisVendaOriginal.compareTo(BigDecimal.ZERO) <= 0) {
            setDescontoGlobal(MoedaContabilBacen.ZERO_MOEDA);
            return;
        }
        BigDecimal somaSubtotaisDevolucao = getItens().stream()
                .map(Item::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cupomProporcional = notaVenda.getDescontoGlobal()
                .multiply(somaSubtotaisDevolucao, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(somaSubtotaisVendaOriginal, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        setDescontoGlobal(cupomProporcional);
    }

    /**
     * Calcula quanto daquele item específico de venda já foi devolvido nesta nota ou em devoluções anteriores.
     */
    public BigDecimal calcularQuantidadeJaDevolvida(Item<? extends Produto> itemVendaOrigem) {
        BigDecimal nestaDevolucao = getItens().stream()
                .filter(i -> itemVendaOrigem.equals(i.getItemOrigem()))
                .map(Item::getQuantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal emDevolucoesAnteriores = BigDecimal.ZERO;
        if (notaVenda != null && notaVenda.getDevolucoes() != null) {
            for (NotaDevolucao dev : notaVenda.getDevolucoes()) {
                if (dev != this && dev.getItens() != null) {
                    BigDecimal soma = dev.getItens().stream()
                            .filter(i -> itemVendaOrigem.equals(i.getItemOrigem()))
                            .map(Item::getQuantidade)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    emDevolucoesAnteriores = emDevolucoesAnteriores.add(soma);
                }
            }
        }
        return nestaDevolucao.add(emDevolucoesAnteriores);
    }

    public boolean validarItensComNotaVenda() {
        if (notaVenda == null || getItens().isEmpty()) {
            return false;
        }
        return getItens().stream().allMatch(i ->
                i.getItemOrigem() != null && notaVenda.getItens().contains(i.getItemOrigem()));
    }

    public boolean isDevolucaoDentroValidade() {
        return getItens().stream().allMatch(i -> i.isDentroDaValidade(LocalDate.now()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public NotaPerda gerarNotaPerdaPosVenda(String tipoPerda, String descricaoPerda) {
        NotaPerda perda = new NotaPerda(tipoPerda, descricaoPerda, this);
        for (Item<? extends Produto> itemDev : getItens()) {
            Item<? extends Produto> loteCompraOrigem = itemDev.getItemOrigem() != null
                    ? itemDev.getItemOrigem().getItemOrigem()
                    : null;
            BigDecimal custoOriginal = loteCompraOrigem != null ? loteCompraOrigem.getValor() : itemDev.getValor();

            Item itemPerda = new Item(itemDev.getProduto(), custoOriginal, itemDev.getQuantidade(), itemDev.getDataValidade());
            itemPerda.setQuantidadeDisponivel(BigDecimal.ZERO);
            itemPerda.setItemOrigem(loteCompraOrigem != null ? loteCompraOrigem : itemDev);
            perda.addItem(itemPerda);

            if (loteCompraOrigem != null && loteCompraOrigem.getNota() instanceof NotaCompra nc) {
                perda.setNotaCompraOrigem(nc);
                perda.setPessoa(nc.getPessoa());
            }
        }
        this.notaPerdaGerada = perda;
        return perda;
    }

    public BigDecimal calcularValorEstornoCliente() {
        return getValorTotal();
    }

    /**
     * Retorna a soma dos subtotais dos itens devolvidos (já deduzidos os descontos de item/Kit, antes do cupom global).
     */
    public BigDecimal getSubtotalItensComDescontoKit() {
        return MoedaContabilBacen.arredondarMoeda(
                getItens().stream()
                        .map(Item::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @Override
    public BigDecimal getValorTotal() {
        return MoedaContabilBacen.arredondarMoeda(
                getSubtotalItensComDescontoKit()
                        .subtract(getDescontoGlobal())
                        .max(BigDecimal.ZERO)
        );
    }

    @Override
    public void atualizar() {
    }

    @Override
    public boolean validarNota() {
        return validarItensComNotaVenda();
    }

    public NotaVenda getNotaVenda() {
        return notaVenda;
    }

    public void setNotaVenda(NotaVenda notaVenda) {
        this.notaVenda = notaVenda;
    }

    public String getMotivo() {
        return motivo;
    }

    public NotaPerda getNotaPerdaGerada() {
        return notaPerdaGerada;
    }
}
