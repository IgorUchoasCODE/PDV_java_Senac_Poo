package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "nota",
        indexes = {
                @Index(name = "idx_nota_tipo_pessoa_data", columnList = "tipo_nota, id_pessoa, data_gerada"),
                @Index(name = "idx_nota_tipo_vinculo", columnList = "tipo_nota, id_vinculo_fator_externo")
        }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_nota", discriminatorType = DiscriminatorType.STRING, length = 15)
public abstract class Nota implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pessoa")
    private Pessoa pessoa;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "data_gerada", nullable = false)
    private LocalDateTime dataGerada = LocalDateTime.now();

    @Column(name = "data_recebida")
    private LocalDateTime dataRecebida;

    @Column(name = "desconto_global", precision = 20, scale = 4)
    private BigDecimal descontoGlobal = BigDecimal.ZERO;

    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<NotaPagamento> pagamentos = new ArrayList<>();

    @OneToMany(targetEntity = Item.class, mappedBy = "nota", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Item<? extends Produto>> itens = new ArrayList<>();

    /**
     * Relacionamento N:1 com VinculoFatorExternoNota: várias Notas na mesma janela de 6h e região
     * compartilham o mesmo registro sazonal no banco, economizando armazenamento e I/O.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vinculo_fator_externo")
    private VinculoFatorExternoNota vinculoFatorExterno;

    protected Nota() {
    }

    protected Nota(Pessoa pessoa, String descricao) {
        this(pessoa, descricao, LocalDateTime.now());
    }

    protected Nota(Pessoa pessoa, String descricao, LocalDateTime dataGerada) {
        this.pessoa = pessoa;
        this.descricao = descricao;
        this.dataGerada = dataGerada != null ? dataGerada : LocalDateTime.now();
    }

    public void definirData(LocalDateTime data) {
        if (data != null) {
            this.dataGerada = data;
        }
    }

    public void definirData(java.time.LocalDate data) {
        if (data != null) {
            this.dataGerada = data.atTime(java.time.LocalTime.now());
        }
    }

    public void addItem(Item<? extends Produto> item) {
        item.setNota(this);
        this.itens.add(item);
    }

    public void removeItem(Item<? extends Produto> item) {
        this.itens.remove(item);
        item.setNota(null);
    }

    public BigDecimal getValorBruto() {
        return MoedaContabilBacen.arredondarMoeda(
                itens.stream()
                        .map(Item::getSubtotalBruto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    public abstract BigDecimal getValorTotal();

    public abstract void atualizar();

    public abstract boolean validarNota();

    public void addPagamento(NotaPagamento np) {
        np.vincularNota(this);
        this.pagamentos.add(np);
    }

    public void removePagamento(NotaPagamento np) {
        this.pagamentos.remove(np);
        np.vincularNota(null);
    }

    public BigDecimal getTotalPago() {
        return MoedaContabilBacen.arredondarMoeda(
                pagamentos.stream()
                        .filter(NotaPagamento::isLiquidado)
                        .map(NotaPagamento::calcularValorFinal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    /**
     * Retorna o total nominal coberto pelos pagamentos registrados na Nota (considerando o valor de face
     * abatido da dívida, inclusive descontos concedidos no PagamentoAVista).
     */
    public BigDecimal getTotalNominalPagamentos() {
        return MoedaContabilBacen.arredondarMoeda(
                pagamentos.stream()
                        .map(NotaPagamento::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    /**
     * Calcula o saldo efetivamente em aberto que exige limite de crédito próprio do cliente na loja
     * (pagamentos não liquidados cuja FormaPagamento exige limite, somados a eventual saldo descoberto da Nota).
     */
    public BigDecimal calcularValorExigidoLimiteCliente() {
        BigDecimal descoberto = getValorTotal().subtract(getTotalNominalPagamentos()).max(BigDecimal.ZERO);
        BigDecimal exigidoPelosPagamentos = pagamentos.stream()
                .filter(p -> !p.isLiquidado())
                .filter(p -> p.getFormaPagamento() == null || p.getFormaPagamento().isExigeLimiteCliente())
                .map(NotaPagamento::calcularValorFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MoedaContabilBacen.arredondarMoeda(descoberto.add(exigidoPelosPagamentos));
    }

    public BigDecimal getSaldoRestante() {
        BigDecimal liquidadoOuCoberto = pagamentos.stream()
                .filter(NotaPagamento::isLiquidado)
                .map(p -> p.getValor().max(p.calcularValorFinal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MoedaContabilBacen.arredondarMoeda(
                getValorTotal().subtract(liquidadoOuCoberto).max(BigDecimal.ZERO)
        );
    }

    public Long getId() {
        return id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public void setPessoa(Pessoa pessoa) {
        this.pessoa = pessoa;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDateTime getDataGerada() {
        return dataGerada;
    }

    public void setDataGerada(LocalDateTime dataGerada) {
        this.dataGerada = dataGerada;
    }

    public LocalDateTime getDataRecebida() {
        return dataRecebida;
    }

    public void setDataRecebida(LocalDateTime dataRecebida) {
        this.dataRecebida = dataRecebida;
    }

    public BigDecimal getDescontoGlobal() {
        return descontoGlobal != null ? descontoGlobal : BigDecimal.ZERO;
    }

    public void setDescontoGlobal(BigDecimal descontoGlobal) {
        this.descontoGlobal = descontoGlobal != null ? descontoGlobal : BigDecimal.ZERO;
    }

    public List<NotaPagamento> getPagamentos() {
        return pagamentos;
    }

    public List<Item<? extends Produto>> getItens() {
        return itens;
    }

    public VinculoFatorExternoNota getVinculoFatorExterno() {
        return vinculoFatorExterno;
    }

    public void setVinculoFatorExterno(VinculoFatorExternoNota vinculoFatorExterno) {
        this.vinculoFatorExterno = vinculoFatorExterno;
    }

    @Override
    public String toString() {
        String nomePessoa = "N/A";
        try {
            if (pessoa != null && pessoa.getNome() != null) {
                nomePessoa = pessoa.getNome();
            }
        } catch (Exception ignored) {
        }
        BigDecimal total = BigDecimal.ZERO;
        try {
            total = getValorTotal();
        } catch (Exception ignored) {
        }
        return String.format("%s{id=%d, data=%s, pessoa='%s', descricao='%s', valorTotal=%s}",
                getClass().getSimpleName(), id, dataGerada, nomePessoa, descricao, total);
    }
}
