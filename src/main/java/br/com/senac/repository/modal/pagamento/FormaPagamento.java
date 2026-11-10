package br.com.senac.repository.modal.pagamento;

import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Entidade persistida na tabela 'forma_pagamento' que centraliza e permite customizar
 * dinamicamente no banco de dados todas as taxas, tarifas, descontos, limites de parcelas,
 * prazos de compensação e encargos moratórios (juros/multa) conforme arredondamento BACEN (HALF_EVEN).
 */
@Entity
@Table(name = "forma_pagamento")
public class FormaPagamento implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 35, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nome", length = 80, nullable = false, unique = true)
    private String nome;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Column(name = "permite_a_vista", nullable = false)
    private boolean permiteAVista = true;

    @Column(name = "permite_a_prazo", nullable = false)
    private boolean permiteAPrazo = false;

    @Column(name = "permite_parcelado", nullable = false)
    private boolean permiteParcelado = false;

    @Column(name = "max_parcelas", nullable = false)
    private int maxParcelas = 1;

    @Column(name = "max_parcelas_sem_juros", nullable = false)
    private int maxParcelasSemJuros = 1;

    @Column(name = "dias_intervalo_parcelas", nullable = false)
    private int diasIntervaloParcelasPadrao = 30;

    @Column(name = "dias_compensacao", nullable = false)
    private int diasCompensacaoPadrao = 0;

    @Column(name = "taxa_operadora_percentual", precision = 10, scale = 4, nullable = false)
    private BigDecimal taxaOperadoraPercentual = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "tarifa_fixa_operadora", precision = 20, scale = 4, nullable = false)
    private BigDecimal tarifaFixaOperadora = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "percentual_desconto_a_vista", precision = 10, scale = 4, nullable = false)
    private BigDecimal percentualDescontoAVistaPadrao = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "taxa_juros_mensal_padrao", precision = 10, scale = 4, nullable = false)
    private BigDecimal taxaJurosMensalPadrao = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "taxa_juros_diario_atraso", precision = 10, scale = 4, nullable = false)
    private BigDecimal taxaJurosDiarioAtrasoPadrao = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "percentual_multa_atraso", precision = 10, scale = 4, nullable = false)
    private BigDecimal percentualMultaAtrasoPadrao = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "valor_minimo_parcela", precision = 20, scale = 4, nullable = false)
    private BigDecimal valorMinimoParcela = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "exige_limite_cliente", nullable = false)
    private boolean exigeLimiteCliente = false;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public FormaPagamento() {
    }

    public FormaPagamento(
            String codigo,
            String nome,
            String descricao,
            boolean permiteAVista,
            boolean permiteAPrazo,
            boolean permiteParcelado,
            int maxParcelas,
            int maxParcelasSemJuros,
            int diasCompensacaoPadrao,
            BigDecimal taxaOperadoraPercentual,
            BigDecimal tarifaFixaOperadora,
            BigDecimal percentualDescontoAVistaPadrao,
            BigDecimal taxaJurosMensalPadrao,
            BigDecimal taxaJurosDiarioAtrasoPadrao,
            BigDecimal percentualMultaAtrasoPadrao,
            BigDecimal valorMinimoParcela,
            boolean exigeLimiteCliente) {
        this.codigo = codigo;
        this.nome = nome;
        this.descricao = descricao;
        this.permiteAVista = permiteAVista;
        this.permiteAPrazo = permiteAPrazo;
        this.permiteParcelado = permiteParcelado;
        this.maxParcelas = Math.max(1, maxParcelas);
        this.maxParcelasSemJuros = Math.max(1, maxParcelasSemJuros);
        this.diasCompensacaoPadrao = Math.max(0, diasCompensacaoPadrao);
        this.taxaOperadoraPercentual = MoedaContabilBacen.arredondarContabil(taxaOperadoraPercentual);
        this.tarifaFixaOperadora = MoedaContabilBacen.arredondarContabil(tarifaFixaOperadora);
        this.percentualDescontoAVistaPadrao = MoedaContabilBacen.arredondarContabil(percentualDescontoAVistaPadrao);
        this.taxaJurosMensalPadrao = MoedaContabilBacen.arredondarContabil(taxaJurosMensalPadrao);
        this.taxaJurosDiarioAtrasoPadrao = MoedaContabilBacen.arredondarContabil(taxaJurosDiarioAtrasoPadrao);
        this.percentualMultaAtrasoPadrao = MoedaContabilBacen.arredondarContabil(percentualMultaAtrasoPadrao);
        this.valorMinimoParcela = MoedaContabilBacen.arredondarContabil(valorMinimoParcela);
        this.exigeLimiteCliente = exigeLimiteCliente;
        this.ativo = true;
    }

    /**
     * Calcula o custo financeiro da operadora/maquininha/banco (MDR % + tarifa fixa) sobre a transação,
     * utilizando arredondamento estatístico do BACEN (RoundingMode.HALF_EVEN).
     */
    public BigDecimal calcularCustoTaxaOperadora(BigDecimal valorTransacao) {
        if (valorTransacao == null || valorTransacao.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        BigDecimal percentual = valorTransacao
                .multiply(taxaOperadoraPercentual, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        return MoedaContabilBacen.arredondarMoeda(percentual.add(tarifaFixaOperadora));
    }

    /**
     * Calcula o desconto padrão para pagamento à vista parametrizado nesta FormaPagamento.
     */
    public BigDecimal calcularDescontoAVistaPadrao(BigDecimal valorBase) {
        if (valorBase == null || percentualDescontoAVistaPadrao.compareTo(BigDecimal.ZERO) <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        return MoedaContabilBacen.arredondarMoeda(
                valorBase.multiply(percentualDescontoAVistaPadrao, MoedaContabilBacen.CONTEXTO_BACEN)
                        .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN)
        );
    }

    /**
     * Calcula encargos de mora (multa + juros diários) conforme regras parametrizadas na tabela.
     */
    public BigDecimal calcularEncargosAtraso(BigDecimal valorBase, long diasAtraso, BigDecimal taxaDiariaCustom, BigDecimal multaCustom) {
        if (valorBase == null || diasAtraso <= 0) {
            return MoedaContabilBacen.ZERO_MOEDA;
        }
        BigDecimal taxaDia = taxaDiariaCustom != null ? taxaDiariaCustom : taxaJurosDiarioAtrasoPadrao;
        BigDecimal percMulta = multaCustom != null ? multaCustom : percentualMultaAtrasoPadrao;

        BigDecimal multa = valorBase
                .multiply(percMulta, MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        BigDecimal juros = valorBase
                .multiply(taxaDia, MoedaContabilBacen.CONTEXTO_BACEN)
                .multiply(BigDecimal.valueOf(diasAtraso), MoedaContabilBacen.CONTEXTO_BACEN)
                .divide(MoedaContabilBacen.CEM, MoedaContabilBacen.ESCALA_CONTABIL_INTERNA, MoedaContabilBacen.ARREDONDAMENTO_BACEN);
        return MoedaContabilBacen.arredondarMoeda(multa.add(juros));
    }

    /**
     * Obtém a taxa de juros mensal aplicável para a quantidade de parcelas (respeitando maxParcelasSemJuros).
     */
    public BigDecimal obterTaxaJurosMensalParaParcelas(int quantidadeParcelas) {
        if (quantidadeParcelas <= maxParcelasSemJuros) {
            return MoedaContabilBacen.ZERO_CONTABIL;
        }
        return taxaJurosMensalPadrao;
    }

    public boolean validarParcelamento(int quantidadeParcelas, BigDecimal valorTotal) {
        if (!ativo || !permiteParcelado || quantidadeParcelas < 2 || quantidadeParcelas > maxParcelas) {
            return false;
        }
        if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal valorParcela = valorTotal.divide(
                BigDecimal.valueOf(quantidadeParcelas),
                MoedaContabilBacen.ESCALA_MOEDA_FINAL,
                MoedaContabilBacen.ARREDONDAMENTO_BACEN
        );
        return valorParcela.compareTo(valorMinimoParcela) >= 0;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public boolean isPermiteAVista() { return permiteAVista; }
    public void setPermiteAVista(boolean permiteAVista) { this.permiteAVista = permiteAVista; }
    public boolean isPermiteAPrazo() { return permiteAPrazo; }
    public void setPermiteAPrazo(boolean permiteAPrazo) { this.permiteAPrazo = permiteAPrazo; }
    public boolean isPermiteParcelado() { return permiteParcelado; }
    public void setPermiteParcelado(boolean permiteParcelado) { this.permiteParcelado = permiteParcelado; }
    public int getMaxParcelas() { return maxParcelas; }
    public void setMaxParcelas(int maxParcelas) { this.maxParcelas = maxParcelas; }
    public int getMaxParcelasSemJuros() { return maxParcelasSemJuros; }
    public void setMaxParcelasSemJuros(int maxParcelasSemJuros) { this.maxParcelasSemJuros = maxParcelasSemJuros; }
    public int getDiasIntervaloParcelasPadrao() { return diasIntervaloParcelasPadrao; }
    public void setDiasIntervaloParcelasPadrao(int diasIntervaloParcelasPadrao) { this.diasIntervaloParcelasPadrao = diasIntervaloParcelasPadrao; }
    public int getDiasCompensacaoPadrao() { return diasCompensacaoPadrao; }
    public void setDiasCompensacaoPadrao(int diasCompensacaoPadrao) { this.diasCompensacaoPadrao = diasCompensacaoPadrao; }
    public BigDecimal getTaxaOperadoraPercentual() { return taxaOperadoraPercentual; }
    public void setTaxaOperadoraPercentual(BigDecimal taxaOperadoraPercentual) { this.taxaOperadoraPercentual = MoedaContabilBacen.arredondarContabil(taxaOperadoraPercentual); }
    public BigDecimal getTarifaFixaOperadora() { return tarifaFixaOperadora; }
    public void setTarifaFixaOperadora(BigDecimal tarifaFixaOperadora) { this.tarifaFixaOperadora = MoedaContabilBacen.arredondarContabil(tarifaFixaOperadora); }
    public BigDecimal getPercentualDescontoAVistaPadrao() { return percentualDescontoAVistaPadrao; }
    public void setPercentualDescontoAVistaPadrao(BigDecimal percentualDescontoAVistaPadrao) { this.percentualDescontoAVistaPadrao = MoedaContabilBacen.arredondarContabil(percentualDescontoAVistaPadrao); }
    public BigDecimal getTaxaJurosMensalPadrao() { return taxaJurosMensalPadrao; }
    public void setTaxaJurosMensalPadrao(BigDecimal taxaJurosMensalPadrao) { this.taxaJurosMensalPadrao = MoedaContabilBacen.arredondarContabil(taxaJurosMensalPadrao); }
    public BigDecimal getTaxaJurosDiarioAtrasoPadrao() { return taxaJurosDiarioAtrasoPadrao; }
    public void setTaxaJurosDiarioAtrasoPadrao(BigDecimal taxaJurosDiarioAtrasoPadrao) { this.taxaJurosDiarioAtrasoPadrao = MoedaContabilBacen.arredondarContabil(taxaJurosDiarioAtrasoPadrao); }
    public BigDecimal getPercentualMultaAtrasoPadrao() { return percentualMultaAtrasoPadrao; }
    public void setPercentualMultaAtrasoPadrao(BigDecimal percentualMultaAtrasoPadrao) { this.percentualMultaAtrasoPadrao = MoedaContabilBacen.arredondarContabil(percentualMultaAtrasoPadrao); }
    public BigDecimal getValorMinimoParcela() { return valorMinimoParcela; }
    public void setValorMinimoParcela(BigDecimal valorMinimoParcela) { this.valorMinimoParcela = MoedaContabilBacen.arredondarContabil(valorMinimoParcela); }
    public boolean isExigeLimiteCliente() { return exigeLimiteCliente; }
    public void setExigeLimiteCliente(boolean exigeLimiteCliente) { this.exigeLimiteCliente = exigeLimiteCliente; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public String toString() {
        return String.format("FormaPagamento{id=%d, codigo='%s', nome='%s', taxaOp=%s%%, descVista=%s%%, maxParc=%dx}",
                id, codigo, nome, taxaOperadoraPercentual, percentualDescontoAVistaPadrao, maxParcelas);
    }
}
