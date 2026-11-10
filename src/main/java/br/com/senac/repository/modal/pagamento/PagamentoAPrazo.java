package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.interfaces.TituloVencivel;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@DiscriminatorValue("1")
public class PagamentoAPrazo extends NotaPagamento implements TituloVencivel {

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_quitacao")
    private LocalDateTime dataQuitacao;

    @Column(name = "taxa_juros_diario", precision = 10, scale = 4)
    private BigDecimal taxaJurosDiario = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "percentual_multa", precision = 10, scale = 4)
    private BigDecimal percentualMulta = MoedaContabilBacen.ZERO_CONTABIL;

    public PagamentoAPrazo() {
        super();
    }

    public PagamentoAPrazo(BigDecimal valor, LocalDate dataVencimento, BigDecimal taxaJurosDiario, BigDecimal percentualMulta) {
        this(null, valor, dataVencimento, taxaJurosDiario, percentualMulta);
    }

    /**
     * Construtor integrado com a tabela FormaPagamento: puxa automaticamente a taxa de juros diário e multa
     * de atraso parametrizadas na FormaPagamento.
     */
    public PagamentoAPrazo(FormaPagamento formaPagamento, BigDecimal valor, LocalDate dataVencimento) {
        this(
                formaPagamento,
                valor,
                dataVencimento,
                formaPagamento != null ? formaPagamento.getTaxaJurosDiarioAtrasoPadrao() : BigDecimal.ZERO,
                formaPagamento != null ? formaPagamento.getPercentualMultaAtrasoPadrao() : BigDecimal.ZERO
        );
    }

    public PagamentoAPrazo(FormaPagamento formaPagamento, BigDecimal valor, LocalDate dataVencimento, BigDecimal taxaJurosDiario, BigDecimal percentualMulta) {
        super(formaPagamento, valor);
        this.dataVencimento = dataVencimento;
        this.taxaJurosDiario = MoedaContabilBacen.arredondarContabil(taxaJurosDiario);
        this.percentualMulta = MoedaContabilBacen.arredondarContabil(percentualMulta);
    }

    @Override
    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    @Override
    public boolean isVencido(LocalDate dataReferencia) {
        if (isLiquidado() || dataVencimento == null) {
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

    @Override
    public BigDecimal calcularEncargos() {
        long dias = getDiasAtraso(LocalDate.now());
        if (dias <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        if (getFormaPagamento() != null) {
            return getFormaPagamento().calcularEncargosAtraso(getValor(), dias, taxaJurosDiario, percentualMulta);
        }
        BigDecimal multa = getValor()
                .multiply(percentualMulta, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        BigDecimal juros = getValor()
                .multiply(taxaJurosDiario, MoedaContabilBacen.CONTEXTO_BACEN)
                .multiply(BigDecimal.valueOf(dias), MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        return MoedaContabilBacen.arredondarMoeda(multa.add(juros));
    }

    @Override
    public BigDecimal calcularValorFinal() {
        return MoedaContabilBacen.arredondarMoeda(getValor().add(calcularEncargos()));
    }

    @Override
    public boolean isPagamentoValido() {
        boolean modalidadePermitida = getFormaPagamento() == null || (getFormaPagamento().isAtivo() && getFormaPagamento().isPermiteAPrazo());
        return modalidadePermitida && getValor() != null && getValor().compareTo(BigDecimal.ZERO) > 0 && dataVencimento != null;
    }

    @Override
    public boolean isLiquidado() {
        return dataQuitacao != null;
    }

    public LocalDateTime getDataQuitacao() {
        return dataQuitacao;
    }
}
