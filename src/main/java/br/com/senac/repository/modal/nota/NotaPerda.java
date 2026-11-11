package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("PERDA")
public class NotaPerda extends Nota {

    @Column(name = "tipo_perda", length = 60)
    private String tipoPerda;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota_devolucao")
    private NotaDevolucao notaDevolucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota_compra_origem")
    private NotaCompra notaCompraOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota_compra_abatimento")
    private NotaCompra notaCompraAbatimento;

    public NotaPerda() {
        super();
    }

    public NotaPerda(String tipoPerda, String descricao, NotaDevolucao notaDevolucao) {
        super(null, descricao);
        this.tipoPerda = tipoPerda;
        this.notaDevolucao = notaDevolucao;
    }

    public NotaPerda(String tipoPerda, String descricao, NotaDevolucao notaDevolucao, java.time.LocalDateTime dataGerada) {
        super(null, descricao, dataGerada);
        this.tipoPerda = tipoPerda;
        this.notaDevolucao = notaDevolucao;
    }

    public boolean isPerdaPosVenda() {
        return notaDevolucao != null;
    }

    public boolean isPerdaEstoque() {
        return notaDevolucao == null;
    }

    public boolean isPerdaDentroDaValidade() {
        LocalDate ref = getDataGerada() != null ? getDataGerada().toLocalDate() : LocalDate.now();
        return getItens().stream().allMatch(i -> i.isDentroDaValidade(ref));
    }

    public boolean pertenceAoFornecedor(Pessoa fornecedor) {
        if (fornecedor == null) return false;
        if (getPessoa() != null && getPessoa().getId() != null && getPessoa().getId().equals(fornecedor.getId())) {
            return true;
        }
        return notaCompraOrigem != null
                && notaCompraOrigem.getPessoa() != null
                && notaCompraOrigem.getPessoa().getId() != null
                && notaCompraOrigem.getPessoa().getId().equals(fornecedor.getId());
    }

    public boolean isElegivelAbatimentoCompra(NotaCompra novaCompra) {
        return novaCompra != null
                && notaCompraAbatimento == null
                && isPerdaDentroDaValidade()
                && pertenceAoFornecedor(novaCompra.getPessoa());
    }

    public BigDecimal getValorBrutoPerda() {
        return getValorBruto();
    }

    public BigDecimal getValorCustoPerda() {
        return MoedaContabilBacen.arredondarMoeda(
                getItens().stream()
                        .map(Item::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Item<? extends Produto>> gerarItensReposicaoMesmoValor() {
        List<Item<? extends Produto>> reposicao = new ArrayList<>();
        for (Item<? extends Produto> itemPerdido : getItens()) {
            Item novoLote = new Item(
                    itemPerdido.getProduto(),
                    itemPerdido.getValor(),
                    itemPerdido.getQuantidade(),
                    LocalDate.now().plusMonths(6)
            );
            novoLote.aplicarDesconto(itemPerdido.getSubtotalBruto()); // Reposição sem custo adicional
            reposicao.add(novoLote);
        }
        return reposicao;
    }

    @Override
    public BigDecimal getValorTotal() {
        return getValorCustoPerda();
    }

    @Override
    public void atualizar() {
    }

    @Override
    public boolean validarNota() {
        return !getItens().isEmpty();
    }

    public String getTipoPerda() {
        return tipoPerda;
    }

    public NotaDevolucao getNotaDevolucao() {
        return notaDevolucao;
    }

    public NotaCompra getNotaCompraOrigem() {
        return notaCompraOrigem;
    }

    public void setNotaCompraOrigem(NotaCompra notaCompraOrigem) {
        this.notaCompraOrigem = notaCompraOrigem;
    }

    public NotaCompra getNotaCompraAbatimento() {
        return notaCompraAbatimento;
    }

    public void setNotaCompraAbatimento(NotaCompra notaCompraAbatimento) {
        this.notaCompraAbatimento = notaCompraAbatimento;
    }
}
