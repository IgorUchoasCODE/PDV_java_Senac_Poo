package br.com.senac.repository.modal.produto;


import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produtos")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "is_digital", discriminatorType = DiscriminatorType.INTEGER, columnDefinition = "TINYINT(1)")
public abstract class Produto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120, unique = true)
    private String nome;

    @Column(name = "descricao", length = 1200, nullable = true)
    private String descricao;

    @Column(name = "v_venda", precision = 20, scale = 4, nullable = true)
    private BigDecimal precoVenda = BigDecimal.ZERO;

    @Column(name = "is_conjunto", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isConjunto = false;

    @ManyToOne
    @JoinColumn(name = "um_id")
    private Um um;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    private List<ConjuntoProduto> itensConjunto = new ArrayList<>();

    protected Produto() {
    }

    public Produto(Long id, String nome, Um um) {
        this.id = id;
        this.nome = nome;
        this.um = um;
    }

    public Produto(String nome, Um um) {
        this.nome = nome;
        this.um = um;
    }

    public abstract boolean isDigital();

    public void adicionarItemConjunto(Produto item, BigDecimal quantidade) {
        ConjuntoProduto conjuntoProduto = new ConjuntoProduto(this, item, quantidade);
        this.itensConjunto.add(conjuntoProduto);
        this.isConjunto = true;
    }

    public void adicionarItemConjunto(ConjuntoProduto itemConjunto) {
        itemConjunto.setProduto(this);
        this.itensConjunto.add(itemConjunto);
        this.isConjunto = true;
    }

    public void removerItemConjunto(ConjuntoProduto itemConjunto) {
        this.itensConjunto.remove(itemConjunto);
        itemConjunto.setProduto(null);
        this.isConjunto = !this.itensConjunto.isEmpty();
    }

    public boolean isConjunto() {
        return isConjunto;
    }

    public void setConjunto(boolean conjunto) {
        this.isConjunto = conjunto;
    }

    public List<ConjuntoProduto> getItensConjunto() {
        return itensConjunto;
    }

    public void setItensConjunto(List<ConjuntoProduto> itensConjunto) {
        this.itensConjunto = itensConjunto;
        this.isConjunto = itensConjunto != null && !itensConjunto.isEmpty();
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }
    public Um getUm() {
        return um;
    }
    public void setUm(Um um) {
        this.um = um;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("{")
                .append("id=").append(id)
                .append(", nome='").append(nome).append('\'')
                .append(", descricao='").append(descricao).append('\'')
                .append(", precoVenda=").append(precoVenda)
                .append(", isDigital=").append(isDigital())
                .append(", isConjunto=").append(isConjunto)
                .append(", um=").append(um != null ? um.getSigla() : "null");
        if (isConjunto && itensConjunto != null) {
            sb.append(", itensConjunto=").append(itensConjunto);
        }
        sb.append("}");
        return sb.toString();
    }
}
