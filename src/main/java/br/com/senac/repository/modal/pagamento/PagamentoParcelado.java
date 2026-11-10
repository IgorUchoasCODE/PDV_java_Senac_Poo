package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("2")
public class PagamentoParcelado extends NotaPagamento {

    @Column(name = "quantidade_parcelas")
    private int quantidadeParcelas = 2;

    @Column(name = "taxa_juros_mensal", precision = 10, scale = 4)
    private BigDecimal taxaJurosMensal = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "intervalo_dias")
    private int intervaloDias = 30;

    @Column(name = "primeiro_vencimento")
    private LocalDate primeiroVencimento;

    @OneToMany(mappedBy = "pagamentoParcelado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ParcelaPagamento> parcelas = new ArrayList<>();

    public PagamentoParcelado() {
        super();
    }

    public PagamentoParcelado(BigDecimal valor, int quantidadeParcelas, BigDecimal taxaJurosMensal, int intervaloDias, LocalDate primeiroVencimento) {
        this(null, valor, quantidadeParcelas, taxaJurosMensal, intervaloDias, primeiroVencimento);
    }

    /**
     * Construtor integrado com a tabela FormaPagamento: puxa automaticamente o limite de parcelas sem juros,
     * taxa de juros mensal, intervalo de dias entre parcelas e valida valor mínimo da parcela.
     */
    public PagamentoParcelado(FormaPagamento formaPagamento, BigDecimal valor, int quantidadeParcelas) {
        this(
                formaPagamento,
                valor,
                quantidadeParcelas,
                formaPagamento != null ? formaPagamento.obterTaxaJurosMensalParaParcelas(quantidadeParcelas) : BigDecimal.ZERO,
                formaPagamento != null ? formaPagamento.getDiasIntervaloParcelasPadrao() : 30,
                LocalDate.now().plusDays(formaPagamento != null ? formaPagamento.getDiasIntervaloParcelasPadrao() : 30)
        );
    }

    public PagamentoParcelado(FormaPagamento formaPagamento, BigDecimal valor, int quantidadeParcelas, BigDecimal taxaJurosMensal, int intervaloDias, LocalDate primeiroVencimento) {
        super(formaPagamento, valor);
        this.quantidadeParcelas = Math.max(2, quantidadeParcelas);
        this.taxaJurosMensal = MoedaContabilBacen.arredondarContabil(taxaJurosMensal);
        this.intervaloDias = intervaloDias > 0 ? intervaloDias : 30;
        this.primeiroVencimento = primeiroVencimento != null ? primeiroVencimento : LocalDate.now().plusDays(this.intervaloDias);
        gerarParcelas();
    }

    /**
     * Gera as parcelas segundo a norma do BACEN (RoundingMode.HALF_EVEN) e aloca eventual resíduo
     * de centavo na 1ª parcela para que a soma contábil seja 100% exata.
     */
    public List<ParcelaPagamento> gerarParcelas() {
        this.parcelas.clear();
        BigDecimal qtdBD = BigDecimal.valueOf(quantidadeParcelas);
        BigDecimal valorTotalNominal = MoedaContabilBacen.arredondarMoeda(getValor());
        BigDecimal valorBase = getValorParcelaBase();
        BigDecimal residuoNominal = valorTotalNominal.subtract(valorBase.multiply(qtdBD));

        BigDecimal totalJuros = calcularEncargos();
        BigDecimal jurosPorParcela = totalJuros.divide(qtdBD, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        BigDecimal residuoJuros = totalJuros.subtract(jurosPorParcela.multiply(qtdBD));

        for (int i = 1; i <= quantidadeParcelas; i++) {
            LocalDate vencimento = primeiroVencimento.plusDays((long) (i - 1) * intervaloDias);
            BigDecimal nominalParcela = (i == 1) ? valorBase.add(residuoNominal) : valorBase;
            BigDecimal jurosParcela = (i == 1) ? jurosPorParcela.add(residuoJuros) : jurosPorParcela;
            ParcelaPagamento parcela = new ParcelaPagamento(this, i, nominalParcela, jurosParcela, vencimento);
            adicionarParcela(parcela);
        }
        return this.parcelas;
    }

    public void adicionarParcela(ParcelaPagamento parcela) {
        parcela.setPagamentoParcelado(this);
        this.parcelas.add(parcela);
    }

    public void removerParcela(ParcelaPagamento parcela) {
        this.parcelas.remove(parcela);
        parcela.setPagamentoParcelado(null);
    }

    public BigDecimal getValorParcelaBase() {
        return getValor().divide(
                BigDecimal.valueOf(quantidadeParcelas),
                MoedaContabilBacen.ESCALA_MOEDA_FINAL,
                MoedaContabilBacen.ARREDONDAMENTO_BACEN
        );
    }

    public BigDecimal getTotalQuitadoParcelas() {
        return MoedaContabilBacen.arredondarMoeda(
                parcelas.stream()
                        .filter(p -> p.getDataQuitacao() != null)
                        .map(ParcelaPagamento::getValorTotalParcela)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    public BigDecimal getSaldoDevedorParcelas() {
        return MoedaContabilBacen.arredondarMoeda(
                parcelas.stream()
                        .filter(p -> p.getDataQuitacao() == null)
                        .map(ParcelaPagamento::getValorTotalParcela)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    public void quitarParcela(int numeroParcela, LocalDateTime dataPagamento) {
        parcelas.stream()
                .filter(p -> p.getNumeroParcela() == numeroParcela)
                .findFirst()
                .ifPresent(p -> p.quitar(dataPagamento));
    }

    @Override
    public BigDecimal calcularEncargos() {
        if (taxaJurosMensal == null || taxaJurosMensal.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return getValor()
                .multiply(taxaJurosMensal, MoedaContabilBacen.CONTEXTO_BACEN)
                .multiply(BigDecimal.valueOf(quantidadeParcelas), MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_MOEDA_FINAL, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
    }

    @Override
    public BigDecimal calcularValorFinal() {
        return MoedaContabilBacen.arredondarMoeda(getValor().add(calcularEncargos()));
    }

    @Override
    public boolean isPagamentoValido() {
        if (getFormaPagamento() != null && !getFormaPagamento().validarParcelamento(quantidadeParcelas, getValor())) {
            return false;
        }
        return getValor() != null && getValor().compareTo(BigDecimal.ZERO) > 0 && quantidadeParcelas >= 2;
    }

    @Override
    public boolean isLiquidado() {
        return !parcelas.isEmpty() && parcelas.stream().allMatch(p -> p.getDataQuitacao() != null);
    }

    public List<ParcelaPagamento> getParcelas() {
        return parcelas;
    }
}
