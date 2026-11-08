package br.com.senac.repository.modal.produto;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("0")
public class ProdutoFisico extends Produto {

    public ProdutoFisico() {
        super();
    }

    public ProdutoFisico(Long id, String nome, Um um) {
        super(id, nome, um);
    }

    public ProdutoFisico(String nome, Um um) {
        super(nome, um);
    }

    @Override
    public boolean isDigital() {
        return false;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
