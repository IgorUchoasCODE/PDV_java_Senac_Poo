package br.com.senac.repository.modal.produto;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "conjunto_produto")
public class ConjuntoProduto implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private ConjuntoProdutoId id = new ConjuntoProdutoId();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("idProduto")
    @JoinColumn(name = "id_produto", nullable = false)
    private Produto produto;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @MapsId("idItem")
    @JoinColumn(name = "id_item", nullable = false)
    private Produto item;

    @Column(name = "quantidade", precision = 20, scale = 6, nullable = false)
    private BigDecimal quantidade;

    public ConjuntoProduto() {
    }

    public ConjuntoProduto(Produto item, BigDecimal quantidade) {
        this.item = item;
        this.quantidade = quantidade;
        if (item != null && item.getId() != null) {
            this.id.setIdItem(item.getId());
        }
    }

    public ConjuntoProduto(Produto produto, Produto item, BigDecimal quantidade) {
        this.produto = produto;
        this.item = item;
        this.quantidade = quantidade;
        this.id = new ConjuntoProdutoId(
                produto != null ? produto.getId() : null,
                item != null ? item.getId() : null
        );
    }

    public ConjuntoProdutoId getId() {
        return id;
    }

    public void setId(ConjuntoProdutoId id) {
        this.id = id;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
        if (produto != null && produto.getId() != null) {
            this.id.setIdProduto(produto.getId());
        }
    }

    public Produto getItem() {
        return item;
    }

    public void setItem(Produto item) {
        this.item = item;
        if (item != null && item.getId() != null) {
            this.id.setIdItem(item.getId());
        }
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    @Override
    public String toString() {
        return String.format("ConjuntoProduto{quantidade=%s, item=%s}", quantidade, item);
    }
}
