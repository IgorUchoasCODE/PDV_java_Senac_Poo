package br.com.senac.repository.modal.produto;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "link_produto_digital")
public class LinkProdutoDigital implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String titulo;

    @Column(length = 2800, nullable = false)
    private String url;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_digital_id", nullable = false)
    private ProdutoDigital produtoDigital;

    public LinkProdutoDigital() {}

    public LinkProdutoDigital(String titulo, String url) {
        this.titulo = titulo;
        this.url = url;
    }

    public LinkProdutoDigital(String titulo, String url, ProdutoDigital produtoDigital) {
        this.titulo = titulo;
        this.url = url;
        this.produtoDigital = produtoDigital;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public ProdutoDigital getProdutoDigital() { return produtoDigital; }
    public void setProdutoDigital(ProdutoDigital produtoDigital) { this.produtoDigital = produtoDigital; }

    @Override
    public String toString() {
        return String.format("LinkProdutoDigital{id=%d, titulo='%s', url='%s'}", id, titulo, url);
    }
}
