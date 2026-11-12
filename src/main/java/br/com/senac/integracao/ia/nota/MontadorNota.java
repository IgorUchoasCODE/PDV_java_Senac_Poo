package br.com.senac.integracao.ia.nota;

import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Builder para montagem unificada de Notas Fiscais (Produtos + Pagamentos integrados).
 */
public class MontadorNota {

    public enum TipoNota {
        COMPRA, VENDA_FIFO, VENDA_BALCAO, VENDA_LOTE, DEVOLUCAO, PERDA
    }

    public record ItemNota(
            Long idProduto,
            String nomeProduto,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            boolean ressarcimentoFornecedor
    ) {
        public BigDecimal subtotal() {
            if (quantidade == null || valorUnitario == null) return BigDecimal.ZERO;
            return quantidade.multiply(valorUnitario);
        }
    }

    public record PagamentoNota(
            String codigoForma,
            BigDecimal valor,
            LocalDate dataVencimento,
            int parcelas
    ) {}

    private TipoNota tipo = TipoNota.VENDA_FIFO;
    private Long idPessoa;
    private String nomePessoa;
    private LocalDate dataNota = LocalDate.now();
    private String motivo;
    private Long idNotaReferenciada;
    private boolean geraPerdaFornecedor = false;
    private final List<ItemNota> itens = new ArrayList<>();
    private final List<PagamentoNota> pagamentos = new ArrayList<>();

    public MontadorNota tipo(TipoNota tipo) {
        if (tipo != null) this.tipo = tipo;
        return this;
    }

    public MontadorNota pessoa(Long id, String nome) {
        this.idPessoa = id;
        this.nomePessoa = nome;
        return this;
    }

    public MontadorNota pessoa(Pessoa p) {
        if (p != null) {
            this.idPessoa = p.getId();
            this.nomePessoa = p.getNome();
        }
        return this;
    }

    public MontadorNota dataNota(LocalDate data) {
        if (data != null) this.dataNota = data;
        return this;
    }

    public MontadorNota motivo(String motivo) {
        this.motivo = motivo;
        return this;
    }

    public MontadorNota notaReferenciada(Long idNota) {
        this.idNotaReferenciada = idNota;
        return this;
    }

    public MontadorNota geraPerdaFornecedor(boolean gera) {
        this.geraPerdaFornecedor = gera;
        return this;
    }

    public MontadorNota adicionarItem(Long idProduto, String nome, BigDecimal qtd, BigDecimal valorUnitario, boolean ressarcimento) {
        itens.add(new ItemNota(idProduto, nome, qtd != null ? qtd : BigDecimal.ONE,
                valorUnitario != null ? valorUnitario : BigDecimal.ZERO, ressarcimento));
        return this;
    }

    public MontadorNota adicionarItem(Produto p, BigDecimal qtd, BigDecimal valorUnitario) {
        if (p != null) {
            adicionarItem(p.getId(), p.getNome(), qtd,
                    valorUnitario != null ? valorUnitario : p.getPrecoVenda(), false);
        }
        return this;
    }

    public MontadorNota adicionarPagamento(String codigoForma, BigDecimal valor, LocalDate data, int parcelas) {
        pagamentos.add(new PagamentoNota(
                codigoForma != null ? codigoForma.toUpperCase(Locale.ROOT) : "PIX",
                valor != null ? valor : BigDecimal.ZERO,
                data != null ? data : LocalDate.now(),
                Math.max(1, parcelas)
        ));
        return this;
    }

    public BigDecimal calcularTotalItens() {
        return itens.stream()
                .filter(i -> !i.ressarcimentoFornecedor())
                .map(ItemNota::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calcularTotalPagamentos() {
        return pagamentos.stream()
                .map(PagamentoNota::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public TipoNota getTipo() { return tipo; }
    public Long getIdPessoa() { return idPessoa; }
    public String getNomePessoa() { return nomePessoa; }
    public LocalDate getDataNota() { return dataNota; }
    public String getMotivo() { return motivo; }
    public Long getIdNotaReferenciada() { return idNotaReferenciada; }
    public boolean isGeraPerdaFornecedor() { return geraPerdaFornecedor; }
    public List<ItemNota> getItens() { return Collections.unmodifiableList(itens); }
    public List<PagamentoNota> getPagamentos() { return Collections.unmodifiableList(pagamentos); }
}
