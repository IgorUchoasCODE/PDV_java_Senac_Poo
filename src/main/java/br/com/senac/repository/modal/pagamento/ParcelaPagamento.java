package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.interfaces.TituloVencivel;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "parcela_pagamento")
public class ParcelaPagamento implements Serializable, TituloVencivel {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pagamento_parcelado", nullable = false)
    private PagamentoParcelado pagamentoParcelado;

    @Column(name = "numero_parcela", nullable = false)
    private int numeroParcela;

    @Column(name = "valor_nominal", precision = 20, scale = 4, nullable = false)
    private BigDecimal valorNominal = BigDecimal.ZERO;

    @Column(name = "acrescimo_juros", precision = 20, scale = 4, nullable = false)
    private BigDecimal acrescimoJuros = BigDecimal.ZERO;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_quitacao")
    private LocalDateTime dataQuitacao;

    public ParcelaPagamento() {
    }

    public ParcelaPagamento(PagamentoParcelado pagamentoParcelado, int numeroParcela, BigDecimal valorNominal, BigDecimal acrescimoJuros, LocalDate dataVencimento) {
        this.pagamentoParcelado = pagamentoParcelado;
        this.numeroParcela = numeroParcela;
        this.valorNominal = MoedaContabilBacen.arredondarContabil(valorNominal);
        this.acrescimoJuros = MoedaContabilBacen.arredondarContabil(acrescimoJuros);
        this.dataVencimento = dataVencimento;
    }

    public BigDecimal getValorTotalParcela() {
        return MoedaContabilBacen.arredondarMoeda(valorNominal.add(acrescimoJuros));
    }

    @Override
    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    @Override
    public boolean isVencido(LocalDate dataReferencia) {
        if (dataQuitacao != null || dataVencimento == null) {
            return false;
        }
        LocalDate ref = dataReferencia != null ? dataReferencia : LocalDate.now();
        return ref.isAfter(dataVencimento);
    }

    @Override
    public long getDiasAtraso(LocalDate dataReferencia) {
        LocalDate ref = dataQuitacao != null ? dataQuitacao.toLocalDate()
                : (dataReferencia != null ? dataReferencia : LocalDate.now());
        if (dataVencimento == null || !ref.isAfter(dataVencimento)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(dataVencimento, ref);
    }

    @Override
    public void quitar(LocalDateTime dataPagamento) {
        this.dataQuitacao = dataPagamento != null ? dataPagamento : LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PagamentoParcelado getPagamentoParcelado() {
        return pagamentoParcelado;
    }

    public void setPagamentoParcelado(PagamentoParcelado pagamentoParcelado) {
        this.pagamentoParcelado = pagamentoParcelado;
    }

    public int getNumeroParcela() {
        return numeroParcela;
    }

    public BigDecimal getValorNominal() {
        return valorNominal;
    }

    public BigDecimal getAcrescimoJuros() {
        return acrescimoJuros;
    }

    public LocalDateTime getDataQuitacao() {
        return dataQuitacao;
    }
}
