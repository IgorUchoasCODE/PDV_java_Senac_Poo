package br.com.senac.interfaces;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Padronização Contábil e Financeira conforme normas do Banco Central do Brasil (BACEN)
 * e ABNT NBR 5891 (Arredondamento Estatístico / Bancário: RoundingMode.HALF_EVEN).
 */
public final class MoedaContabilBacen {

    /**
     * Regra oficial ABNT NBR 5891 / BACEN (Half-Even / Arredondamento Bancário):
     * Evita viés cumulativo em grandes volumes de lançamentos contábeis e parcelas.
     */
    public static final RoundingMode ARREDONDAMENTO_BACEN = RoundingMode.HALF_EVEN;

    public static final int ESCALA_MOEDA_FINAL = 2;       // Moeda final (R$ 0,00)
    public static final int ESCALA_CONTABIL_INTERNA = 4;  // Custo/preço unitário e taxas (0,0000)
    public static final int ESCALA_QUANTIDADE = 6;        // Quantidades e pesagens (0,000000)

    public static final MathContext CONTEXTO_BACEN = new MathContext(20, ARREDONDAMENTO_BACEN);

    public static final BigDecimal ZERO_MOEDA = BigDecimal.ZERO.setScale(ESCALA_MOEDA_FINAL, ARREDONDAMENTO_BACEN);
    public static final BigDecimal ZERO_CONTABIL = BigDecimal.ZERO.setScale(ESCALA_CONTABIL_INTERNA, ARREDONDAMENTO_BACEN);
    public static final BigDecimal ZERO_QUANTIDADE = BigDecimal.ZERO.setScale(ESCALA_QUANTIDADE, ARREDONDAMENTO_BACEN);
    public static final BigDecimal CEM = new BigDecimal("100.0000");

    private MoedaContabilBacen() {
    }

    public static BigDecimal arredondarMoeda(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_MOEDA_FINAL, ARREDONDAMENTO_BACEN);
    }

    public static BigDecimal arredondarContabil(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA_CONTABIL_INTERNA, ARREDONDAMENTO_BACEN);
    }

    public static BigDecimal arredondarQuantidade(BigDecimal quantidade) {
        return (quantidade == null ? BigDecimal.ZERO : quantidade).setScale(ESCALA_QUANTIDADE, ARREDONDAMENTO_BACEN);
    }

    public static BigDecimal arredondarPercentual(BigDecimal percentual) {
        return (percentual == null ? BigDecimal.ZERO : percentual).setScale(ESCALA_MOEDA_FINAL, ARREDONDAMENTO_BACEN);
    }
}
