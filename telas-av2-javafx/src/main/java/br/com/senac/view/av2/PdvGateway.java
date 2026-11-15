package br.com.senac.view.av2;

import java.math.BigDecimal;
import java.util.List;

/** Contrato exclusivo das telas. A equipe pode implementar um adaptador para seus controllers. */
public interface PdvGateway {
    record Produto(String codigo, String nome, String tipo, BigDecimal preco, int estoque,
                   String peso, String dimensoes, String link, String tamanho) {
        @Override public String toString() { return codigo + " · " + nome; }
    }
    record Cliente(String nome, String documento, String email, String telefone, String endereco) {
        @Override public String toString() { return nome; }
    }
    record Item(Produto produto, int quantidade) {}
    record Resumo(BigDecimal subtotal, BigDecimal frete, BigDecimal ajuste, BigDecimal total) {}
    record Venda(String numero, String data, String cliente, String metodo, BigDecimal total, String cupom) {}
    List<Produto> produtos();
    List<Cliente> clientes();
    List<Item> itens();
    List<Venda> vendas();
    void salvarProduto(Produto produto);
    void salvarCliente(Cliente cliente);
    void adicionar(String codigo, int quantidade);
    void alterarQuantidade(String codigo, int quantidade);
    void remover(String codigo);
    void limpar();
    Resumo resumo(String metodo);
    Venda confirmar(Cliente cliente, String endereco, String metodo, BigDecimal recebido);
}
