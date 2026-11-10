package br.com.senac.repository.modal.pessoa;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PJ")
public class PessoaJuridica extends Pessoa {

    @Column(name = "cnpj", length = 18, unique = true)
    private String cnpj;

    public PessoaJuridica() {
        super();
    }

    public PessoaJuridica(String nome, String cnpj) {
        super(nome);
        this.cnpj = cnpj;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    @Override
    public String toString() {
        return String.format("PessoaJuridica{id=%d, nome='%s', cnpj='%s', cliente=%s, fornecedor=%s, limite=%s}",
                getId(), getNome(), cnpj, isCliente(), isFornecedor(), getLimite());
    }
}
