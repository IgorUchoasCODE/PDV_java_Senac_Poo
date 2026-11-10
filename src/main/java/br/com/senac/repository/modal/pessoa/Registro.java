package br.com.senac.repository.modal.pessoa;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "registro")
public class Registro implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pessoa", nullable = false)
    private Pessoa pessoa;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "registro", nullable = false, length = 250)
    private String registro;

    public Registro() {
    }

    public Registro(Pessoa pessoa, String tipo, String registro) {
        this.pessoa = pessoa;
        this.tipo = tipo;
        this.registro = registro;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public void setPessoa(Pessoa pessoa) {
        this.pessoa = pessoa;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getRegistro() {
        return registro;
    }

    public void setRegistro(String registro) {
        this.registro = registro;
    }

    @Override
    public String toString() {
        return String.format("Registro{id=%d, tipo='%s', valor='%s'}", id, tipo, registro);
    }
}
