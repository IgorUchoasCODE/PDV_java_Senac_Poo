package br.com.senac.repository.modal.pessoa;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "pessoa",
        indexes = {
                @Index(name = "idx_pessoa_nome", columnList = "nome")
        }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_pessoa", discriminatorType = DiscriminatorType.STRING, length = 2)
public abstract class Pessoa implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 150, unique = false)
    private String nome;

    @Column(name = "limite", precision = 20, scale = 2)
    private BigDecimal limite = BigDecimal.ZERO;

    @Column(name = "is_cliente", nullable = false)
    private boolean isCliente = false;

    @Column(name = "is_fornecedor", nullable = false)
    private boolean isFornecedor = false;

    @OneToMany(mappedBy = "pessoa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Registro> registros = new ArrayList<>();

    protected Pessoa() {
    }

    protected Pessoa(String nome) {
        this.nome = nome;
    }

    public void definirComoCliente(BigDecimal limite) {
        this.isCliente = true;
        this.limite = limite != null ? limite : BigDecimal.ZERO;
    }

    public void definirComoFornecedor() {
        this.isFornecedor = true;
    }

    public void adicionarRegistro(String tipo, String valorRegistro) {
        Registro reg = new Registro(this, tipo, valorRegistro);
        this.registros.add(reg);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getLimite() {
        return limite;
    }

    public void setLimite(BigDecimal limite) {
        this.limite = limite;
    }

    public boolean isCliente() {
        return isCliente;
    }

    public void setCliente(boolean cliente) {
        isCliente = cliente;
    }

    public boolean isFornecedor() {
        return isFornecedor;
    }

    public void setFornecedor(boolean fornecedor) {
        isFornecedor = fornecedor;
    }

    public List<Registro> getRegistros() {
        return registros;
    }

    public void setRegistros(List<Registro> registros) {
        this.registros = registros;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pessoa outra)) return false;
        return id != null && id.equals(outra.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return String.format("%s{id=%d, nome='%s', cliente=%s, fornecedor=%s, limite=%s}",
                getClass().getSimpleName(), id, nome, isCliente, isFornecedor, limite);
    }
}
