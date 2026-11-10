package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@DiscriminatorValue("0")
public class PagamentoAVista extends NotaPagamento {

    @Column(name = "desconto_a_vista", precision = 20, scale = 4)
    private BigDecimal descontoAVista = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "valor_recebido", precision = 20, scale = 4)
    private BigDecimal valorRecebido = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "data_confirmacao")
    private LocalDateTime dataConfirmacao;

    public PagamentoAVista() {
        super();
    }

    public PagamentoAVista(BigDecimal valor, BigDecimal valorRecebido, BigDecimal descontoAVista) {
        this(null, valor, valorRecebido, descontoAVista);
    }

    /**
     * Construtor integrado com a tabela FormaPagamento: aplica automaticamente o desconto à vista
     * parametrizado na FormaPagamento caso descontoAVista seja null.
     */
    public PagamentoAVista(FormaPagamento formaPagamento, BigDecimal valor, BigDecimal valorRecebido, BigDecimal descontoAVista) {
        super(formaPagamento, valor);
        BigDecimal descPadrao = (descontoAVista != null)
                ? descontoAVista
                : (formaPagamento != null ? formaPagamento.calcularDescontoAVistaPadrao(valor) : BigDecimal.ZERO);
        this.descontoAVista = MoedaContabilBacen.arredondarContabil(descPadrao);
        this.valorRecebido = MoedaContabilBacen.arredondarContabil(valorRecebido != null ? valorRecebido : calcularValorFinal());
        this.dataConfirmacao = LocalDateTime.now();
    }

    public PagamentoAVista(FormaPagamento formaPagamento, BigDecimal valor) {
        this(formaPagamento, valor, null, null);
    }

    public BigDecimal calcularTroco() {
        BigDecimal liquido = calcularValorFinal();
        if (valorRecebido == null || valorRecebido.compareTo(liquido) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return MoedaContabilBacen.arredondarMoeda(valorRecebido.subtract(liquido));
    }

    public void confirmarPagamento() {
        this.dataConfirmacao = LocalDateTime.now();
    }

    @Override
    public BigDecimal calcularValorFinal() {
        BigDecimal desc = descontoAVista != null ? descontoAVista : BigDecimal.ZERO;
        return MoedaContabilBacen.arredondarMoeda(getValor().subtract(desc).max(BigDecimal.ZERO));
    }

    @Override
    public BigDecimal calcularEncargos() {
        return MoedaContabilBacen.ZERO_MOEDA;
    }

    @Override
    public boolean isPagamentoValido() {
        boolean modalidadePermitida = getFormaPagamento() == null || (getFormaPagamento().isAtivo() && getFormaPagamento().isPermiteAVista());
        return modalidadePermitida
                && getValor() != null && getValor().compareTo(BigDecimal.ZERO) > 0
                && valorRecebido != null && valorRecebido.compareTo(calcularValorFinal()) >= 0;
    }

    @Override
    public boolean isLiquidado() {
        return dataConfirmacao != null;
    }

    public BigDecimal getDescontoAVista() {
        return descontoAVista;
    }

    public void setDescontoAVista(BigDecimal descontoAVista) {
        this.descontoAVista = MoedaContabilBacen.arredondarContabil(descontoAVista);
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public void setValorRecebido(BigDecimal valorRecebido) {
        this.valorRecebido = MoedaContabilBacen.arredondarContabil(valorRecebido);
    }

    public LocalDateTime getDataConfirmacao() {
        return dataConfirmacao;
    }
}
