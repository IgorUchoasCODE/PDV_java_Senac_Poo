package br.com.senac.repository.modal.produto;

import br.com.senac.interfaces.Mensuravel;
import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;



@Entity
@Table(name = "unidade_medida")
public class Um implements Serializable, Mensuravel {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 4, nullable = false, unique = true)
    private String sigla;

    @Column(length = 50, nullable = false, unique = true)
    private String nome;


    @Column(length = 500, nullable = false)
    private String descricao;


    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal base;

    public Um() {}

    public Um(String sigla, String nome, String descricao, BigDecimal base) {
        this.sigla = sigla;
        this.nome = nome;
        this.descricao = descricao;
        this.base = base;
    }

    public boolean isQuantidadeValida(BigDecimal quantidade) {
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (quantidade.compareTo(this.base) < 0) {
            return false;
        }


        BigDecimal resto = quantidade.remainder(this.base);
        return resto.compareTo(BigDecimal.ZERO) == 0;
    }


    public BigDecimal calcularValorProporcional(BigDecimal valorCustoUnitario, BigDecimal quantidade) {
        if (!isQuantidadeValida(quantidade)) {
            throw new IllegalArgumentException(
                    String.format("Quantidade %s é inválida para a unidade %s (Mínimo/Fator: %s)",
                            quantidade, this.sigla, this.base)
            );
        }
        return valorCustoUnitario
                .multiply(quantidade, MoedaContabilBacen.CONTEXTO_BACEN)
                .setScale(MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
    }

    public Long getId() { return this.id; }

    public String getSigla() { return this.sigla; }

    public void setSigla(String sigla) { this.sigla = sigla; }

    public String getNome() { return nome; }

    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }

    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getBase() { return base; }

    public void setBase(BigDecimal base) { this.base = base; }

    @Override
    public String toString() {
        return String.format("Um{id=%d, sigla='%s', nome='%s', base=%s}", id, sigla, nome, base);
    }
}
