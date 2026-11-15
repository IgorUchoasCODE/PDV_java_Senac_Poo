package br.com.senac.application.terminal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Abstração (DIP) da fonte de entidades reais do sistema para o terminal CLI.
 * Permite identificar produtos, fornecedores, clientes, IDs, registros e formas de pagamento
 * sem acoplar os analisadores diretamente à camada de persistência.
 */
public interface FonteEntidadesTerminal {

    record ProdutoResumo(Long id, String nome, String nomeNormalizado, BigDecimal preco, String um, boolean conjunto) {}

    record PessoaResumo(Long id, String nome, String nomeNormalizado, boolean cliente, boolean fornecedor, String documento) {
        public String papelTexto() {
            if (cliente && fornecedor) return "Cliente/Fornecedor";
            if (fornecedor) return "Fornecedor";
            if (cliente) return "Cliente";
            return "Pessoa";
        }
    }

    record NotaResumo(Long id, String tipoNota, BigDecimal valorBruto) {}

    record FormaPagamentoResumo(String codigo, String nome, String nomeNormalizado) {}

    void recarregar();

    List<ProdutoResumo> listarProdutos();

    List<PessoaResumo> listarPessoas();

    List<NotaResumo> listarNotas();

    List<String> listarSiglasUm();

    List<FormaPagamentoResumo> listarFormasPagamento();

    Optional<ProdutoResumo> buscarProdutoPorId(Long id);

    Optional<PessoaResumo> buscarPessoaPorId(Long id);

    Optional<NotaResumo> buscarNotaPorId(Long id);

    Optional<PessoaResumo> buscarPessoaPorRegistro(String valorRegistro);
}
