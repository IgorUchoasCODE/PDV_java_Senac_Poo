package br.com.senac.repository.modal.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConjuntoProdutoId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_produto")
    private Long idProduto;

    @Column(name = "id_item")
    private Long idItem;

    public ConjuntoProdutoId() {
    }

    public ConjuntoProdutoId(Long idProduto, Long idItem) {
        this.idProduto = idProduto;
        this.idItem = idItem;
    }

    public Long getIdProduto() {
        return idProduto;
    }

    public void setIdProduto(Long idProduto) {
        this.idProduto = idProduto;
    }

    public Long getIdItem() {
        return idItem;
    }

    public void setIdItem(Long idItem) {
        this.idItem = idItem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConjuntoProdutoId that = (ConjuntoProdutoId) o;
        return Objects.equals(idProduto, that.idProduto) && Objects.equals(idItem, that.idItem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProduto, idItem);
    }
}
