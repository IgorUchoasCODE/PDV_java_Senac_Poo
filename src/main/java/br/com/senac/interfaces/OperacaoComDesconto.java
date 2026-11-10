package br.com.senac.interfaces;

import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.List;

public interface OperacaoComDesconto {
    void aplicarDescontoNota(BigDecimal desconto);
    void aplicarDescontoProduto(Produto produto, BigDecimal desconto);
    void aplicarDescontoProdutos(List<Produto> produtos, BigDecimal desconto);
    void aplicarCupomDesconto(String codigoCupom, BigDecimal valorDesconto);
}
