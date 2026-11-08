package br.com.senac.repository.modal.produto;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("1")
public class ProdutoDigital extends Produto {

    @OneToMany(mappedBy = "produtoDigital", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    private List<LinkProdutoDigital> links = new ArrayList<>();

    public ProdutoDigital() {
        super();
    }

    public ProdutoDigital(Long id, String nome, Um um) {
        super(id, nome, um);
    }

    public ProdutoDigital(String nome, Um um) {
        super(nome, um);
    }

    @Override
    public boolean isDigital() {
        return true;
    }

    public void adicionarLink(LinkProdutoDigital link) {
        links.add(link);
        link.setProdutoDigital(this);
    }

    public void removerLink(LinkProdutoDigital link) {
        links.remove(link);
        link.setProdutoDigital(null);
    }

    public List<LinkProdutoDigital> getLinks() {
        return links;
    }

    public void setLinks(List<LinkProdutoDigital> links) {
        this.links = links;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProdutoDigital{")
                .append("id=").append(getId())
                .append(", nome='").append(getNome()).append('\'')
                .append(", descricao='").append(getDescricao()).append('\'')
                .append(", precoVenda=").append(getPrecoVenda())
                .append(", isDigital=").append(isDigital())
                .append(", isConjunto=").append(isConjunto())
                .append(", um=").append(getUm() != null ? getUm().getSigla() : "null")
                .append(", links=").append(links);
        if (isConjunto() && getItensConjunto() != null) {
            sb.append(", itensConjunto=").append(getItensConjunto());
        }
        sb.append("}");
        return sb.toString();
    }
}
