package br.com.senac.repository.modal.pessoa;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PF")
public class PessoaFisica extends Pessoa {

    @Column(name = "cpf", length = 14, unique = true)
    private String cpf;

    public PessoaFisica() {
        super();
    }

    public PessoaFisica(String nome, String cpf) {
        super(nome);
        this.cpf = cpf;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    @Override
    public String toString() {
        return String.format("PessoaFisica{id=%d, nome='%s', cpf='%s', cliente=%s, fornecedor=%s, limite=%s}",
                getId(), getNome(), cpf, isCliente(), isFornecedor(), getLimite());
    }
}
