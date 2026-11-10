package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.interfaces.RegraPagamento;
import br.com.senac.repository.modal.nota.Nota;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nota_pagamento")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "modo_pagamento", discriminatorType = DiscriminatorType.INTEGER, columnDefinition = "TINYINT")
public abstract class NotaPagamento implements Serializable, RegraPagamento {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nota", nullable = false)
    private Nota nota;

    /**
     * Vínculo com a tabela customizável 'forma_pagamento' que centraliza taxas, tarifas,
     * limites de parcelamento, descontos e encargos moratórios.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_forma_pagamento")
    private FormaPagamento formaPagamento;

    @Column(name = "valor", precision = 20, scale = 4, nullable = false)
    private BigDecimal valor = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "data", nullable = false)
    private LocalDateTime data = LocalDateTime.now();

    protected NotaPagamento() {
    }

    protected NotaPagamento(BigDecimal valor) {
        this(null, valor);
    }

    protected NotaPagamento(FormaPagamento formaPagamento, BigDecimal valor) {
        this.formaPagamento = formaPagamento;
        this.valor = MoedaContabilBacen.arredondarContabil(valor);
        this.data = LocalDateTime.now();
    }

    public void vincularNota(Nota nota) {
        this.nota = nota;
    }

    /**
     * Calcula o custo de taxa/tarifa da operadora financeira parametrizada na tabela 'forma_pagamento'.
     */
    public BigDecimal calcularCustoTaxaOperadora() {
        if (formaPagamento == null) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return formaPagamento.calcularCustoTaxaOperadora(calcularValorFinal());
    }

    public Long getId() {
        return id;
    }

    public Nota getNota() {
        return nota;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = MoedaContabilBacen.arredondarContabil(valor);
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }
}
