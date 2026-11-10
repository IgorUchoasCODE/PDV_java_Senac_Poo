package br.com.senac.repository.modal.produto;

import br.com.senac.interfaces.MoedaContabilBacen;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Entity
@DiscriminatorValue("0")
public class ProdutoFisico extends Produto {

    private static final BigDecimal CM3_POR_METRO_CUBICO = new BigDecimal("1000000");
    private static final BigDecimal FATOR_CUBAGEM_RODOVIARIO_PADRAO = new BigDecimal("300.0000");

    @Column(name = "altura_cm", precision = 12, scale = 4)
    private BigDecimal alturaCm = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "largura_cm", precision = 12, scale = 4)
    private BigDecimal larguraCm = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "comprimento_cm", precision = 12, scale = 4)
    private BigDecimal comprimentoCm = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "peso_bruto_kg", precision = 12, scale = 4)
    private BigDecimal pesoBrutoKg = MoedaContabilBacen.ZERO_CONTABIL;

    @Column(name = "peso_liquido_kg", precision = 12, scale = 4)
    private BigDecimal pesoLiquidoKg = MoedaContabilBacen.ZERO_CONTABIL;

    public ProdutoFisico() {
        super();
    }

    public ProdutoFisico(Long id, String nome, Um um) {
        super(id, nome, um);
    }

    public ProdutoFisico(String nome, Um um) {
        super(nome, um);
    }

    public ProdutoFisico(
            String nome,
            Um um,
            BigDecimal alturaCm,
            BigDecimal larguraCm,
            BigDecimal comprimentoCm,
            BigDecimal pesoBrutoKg,
            BigDecimal pesoLiquidoKg) {
        super(nome, um);
        definirDimensoes(alturaCm, larguraCm, comprimentoCm, pesoBrutoKg, pesoLiquidoKg);
    }

    @Override
    public boolean isDigital() {
        return false;
    }

    /**
     * Define todas as dimensões físicas (em cm) e pesos (em kg) do produto aplicando arredondamento BACEN.
     */
    public void definirDimensoes(
            BigDecimal alturaCm,
            BigDecimal larguraCm,
            BigDecimal comprimentoCm,
            BigDecimal pesoBrutoKg,
            BigDecimal pesoLiquidoKg) {
        this.alturaCm = MoedaContabilBacen.arredondarContabil(alturaCm);
        this.larguraCm = MoedaContabilBacen.arredondarContabil(larguraCm);
        this.comprimentoCm = MoedaContabilBacen.arredondarContabil(comprimentoCm);
        this.pesoBrutoKg = MoedaContabilBacen.arredondarContabil(pesoBrutoKg);
        this.pesoLiquidoKg = MoedaContabilBacen.arredondarContabil(pesoLiquidoKg);
    }

    /**
     * Calcula a área da base (Largura x Comprimento) em cm².
     */
    public BigDecimal calcularAreaBaseCm2() {
        return MoedaContabilBacen.arredondarContabil(
                getLarguraCm().multiply(getComprimentoCm(), MoedaContabilBacen.CONTEXTO_BACEN)
        );
    }

    /**
     * Calcula o volume físico em centímetros cúbicos (cm³ = Altura x Largura x Comprimento).
     * Se for um Kit/Conjunto sem dimensões de embalagem própria definidas, soma o volume dos componentes físicos.
     */
    public BigDecimal calcularVolumeCm3() {
        BigDecimal volumeProprio = MoedaContabilBacen.arredondarContabil(
                getAlturaCm()
                        .multiply(getLarguraCm(), MoedaContabilBacen.CONTEXTO_BACEN)
                        .multiply(getComprimentoCm(), MoedaContabilBacen.CONTEXTO_BACEN)
        );
        if (volumeProprio.compareTo(BigDecimal.ZERO) > 0 || !isConjunto() || getItensConjunto() == null) {
            return volumeProprio;
        }
        BigDecimal volumeComponentes = BigDecimal.ZERO;
        for (ConjuntoProduto cp : getItensConjunto()) {
            if (cp.getItem() instanceof ProdutoFisico pf) {
                BigDecimal volComp = pf.calcularVolumeCm3().multiply(cp.getQuantidade(), MoedaContabilBacen.CONTEXTO_BACEN);
                volumeComponentes = volumeComponentes.add(volComp);
            }
        }
        return MoedaContabilBacen.arredondarContabil(volumeComponentes);
    }

    /**
     * Calcula o volume físico em metros cúbicos (m³ = cm³ / 1.000.000) na escala de 6 casas decimais.
     */
    public BigDecimal calcularVolumeMetrosCubicos() {
        return calcularVolumeCm3().divide(
                CM3_POR_METRO_CUBICO,
                MoedaContabilBacen.ESCALA_QUANTIDADE,
                MoedaContabilBacen.ARREDONDAMENTO_BACEN
        );
    }

    /**
     * Calcula o peso bruto total (kg). Se for um Kit/Conjunto sem peso próprio informado,
     * soma o peso bruto proporcional de cada componente físico.
     */
    public BigDecimal calcularPesoBrutoTotalKg() {
        if (getPesoBrutoKg().compareTo(BigDecimal.ZERO) > 0 || !isConjunto() || getItensConjunto() == null) {
            return getPesoBrutoKg();
        }
        BigDecimal pesoComponentes = BigDecimal.ZERO;
        for (ConjuntoProduto cp : getItensConjunto()) {
            if (cp.getItem() instanceof ProdutoFisico pf) {
                BigDecimal pesoComp = pf.calcularPesoBrutoTotalKg().multiply(cp.getQuantidade(), MoedaContabilBacen.CONTEXTO_BACEN);
                pesoComponentes = pesoComponentes.add(pesoComp);
            }
        }
        return MoedaContabilBacen.arredondarContabil(pesoComponentes);
    }

    /**
     * Calcula o peso cubado logístico (Volume em m³ x Fator de Cubagem em kg/m³).
     * Caso o fator não seja informado, utiliza o padrão rodoviário ANTT (300 kg/m³).
     */
    public BigDecimal calcularPesoCubadoKg(BigDecimal fatorCubagemKgPorM3) {
        BigDecimal fator = (fatorCubagemKgPorM3 != null && fatorCubagemKgPorM3.compareTo(BigDecimal.ZERO) > 0)
                ? fatorCubagemKgPorM3
                : FATOR_CUBAGEM_RODOVIARIO_PADRAO;
        return MoedaContabilBacen.arredondarContabil(
                calcularVolumeMetrosCubicos().multiply(fator, MoedaContabilBacen.CONTEXTO_BACEN)
        );
    }

    /**
     * Retorna o peso taxável para frete (o maior entre o Peso Bruto Total e o Peso Cubado Rodoviário).
     */
    public BigDecimal calcularPesoTaxavelFreteKg() {
        BigDecimal bruto = calcularPesoBrutoTotalKg();
        BigDecimal cubado = calcularPesoCubadoKg(FATOR_CUBAGEM_RODOVIARIO_PADRAO);
        return bruto.max(cubado);
    }

    /**
     * Valida se as dimensões e pesos cadastrados são consistentes (não negativos e peso bruto >= peso líquido).
     */
    public boolean possuiDimensoesValidas() {
        return getAlturaCm().compareTo(BigDecimal.ZERO) >= 0
                && getLarguraCm().compareTo(BigDecimal.ZERO) >= 0
                && getComprimentoCm().compareTo(BigDecimal.ZERO) >= 0
                && getPesoBrutoKg().compareTo(BigDecimal.ZERO) >= 0
                && getPesoLiquidoKg().compareTo(BigDecimal.ZERO) >= 0
                && getPesoBrutoKg().compareTo(getPesoLiquidoKg()) >= 0;
    }

    /**
     * Retorna uma representação textual formatada das dimensões (AxLxC em cm, Volume e Peso).
     */
    public String formatarResumoDimensoes() {
        return String.format("%s x %s x %s cm | Vol: %s cm³ (%s m³) | Peso Bruto: %s kg | Peso Líq: %s kg",
                getAlturaCm(), getLarguraCm(), getComprimentoCm(),
                calcularVolumeCm3(), calcularVolumeMetrosCubicos(),
                calcularPesoBrutoTotalKg(), getPesoLiquidoKg());
    }

    public BigDecimal getAlturaCm() {
        return alturaCm != null ? alturaCm : MoedaContabilBacen.ZERO_CONTABIL;
    }

    public void setAlturaCm(BigDecimal alturaCm) {
        this.alturaCm = MoedaContabilBacen.arredondarContabil(alturaCm);
    }

    public BigDecimal getLarguraCm() {
        return larguraCm != null ? larguraCm : MoedaContabilBacen.ZERO_CONTABIL;
    }

    public void setLarguraCm(BigDecimal larguraCm) {
        this.larguraCm = MoedaContabilBacen.arredondarContabil(larguraCm);
    }

    public BigDecimal getComprimentoCm() {
        return comprimentoCm != null ? comprimentoCm : MoedaContabilBacen.ZERO_CONTABIL;
    }

    public void setComprimentoCm(BigDecimal comprimentoCm) {
        this.comprimentoCm = MoedaContabilBacen.arredondarContabil(comprimentoCm);
    }

    public BigDecimal getPesoBrutoKg() {
        return pesoBrutoKg != null ? pesoBrutoKg : MoedaContabilBacen.ZERO_CONTABIL;
    }

    public void setPesoBrutoKg(BigDecimal pesoBrutoKg) {
        this.pesoBrutoKg = MoedaContabilBacen.arredondarContabil(pesoBrutoKg);
    }

    public BigDecimal getPesoLiquidoKg() {
        return pesoLiquidoKg != null ? pesoLiquidoKg : MoedaContabilBacen.ZERO_CONTABIL;
    }

    public void setPesoLiquidoKg(BigDecimal pesoLiquidoKg) {
        this.pesoLiquidoKg = MoedaContabilBacen.arredondarContabil(pesoLiquidoKg);
    }

    @Override
    public String toString() {
        return super.toString() + " [Dimensões: " + formatarResumoDimensoes() + "]";
    }
}
