package br.com.senac.repository.modal.nota;

import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.interfaces.OperacaoComDesconto;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@DiscriminatorValue("COMPRA")
public class NotaCompra extends Nota implements OperacaoComDesconto {

    @Column(name = "cupom_aplicado", length = 60)
    private String cupomAplicado;

    @OneToMany(mappedBy = "notaCompraAbatimento", fetch = FetchType.LAZY)
    private List<NotaPerda> notasPerdaAbatidas = new ArrayList<>();

    public NotaCompra() {
        super();
    }

    public NotaCompra(Pessoa fornecedor, String descricao) {
        super(fornecedor, descricao);
    }

    public NotaCompra(Pessoa fornecedor, String descricao, java.time.LocalDateTime dataGerada) {
        super(fornecedor, descricao, dataGerada);
    }

    public void adicionarNotaPerda(NotaPerda notaPerda) {
        if (validarNotaPerdaPosVendaFornecedor(notaPerda)) {
            notaPerda.setNotaCompraAbatimento(this);
            this.notasPerdaAbatidas.add(notaPerda);
        }
    }

    public boolean validarNotaPerdaPosVendaFornecedor(NotaPerda notaPerda) {
        return notaPerda != null
                && notaPerda.isPerdaDentroDaValidade()
                && getPessoa() != null
                && notaPerda.pertenceAoFornecedor(getPessoa());
    }

    public BigDecimal extrairValorBrutoPerda(NotaPerda notaPerda) {
        return notaPerda != null ? notaPerda.getValorBrutoPerda() : BigDecimal.ZERO;
    }

    public void adicionarItensPerdidosReposicao(NotaPerda notaPerda) {
        if (validarNotaPerdaPosVendaFornecedor(notaPerda)) {
            for (Item<? extends Produto> itemReposto : notaPerda.gerarItensReposicaoMesmoValor()) {
                addItem(itemReposto);
            }
        }
    }

    public Map<Produto, BigDecimal> sugerirPrecoVendaItens(BigDecimal margemLucro) {
        Map<Produto, BigDecimal> sugestoes = new LinkedHashMap<>();
        BigDecimal fator = BigDecimal.ONE.add(margemLucro != null ? margemLucro : new BigDecimal("0.3000"));
        for (Item<? extends Produto> item : getItens()) {
            BigDecimal precoSugerido = MoedaContabilBacen.arredondarMoeda(
                    item.getValor().multiply(fator, MoedaContabilBacen.CONTEXTO_BACEN)
            );
            sugestoes.put(item.getProduto(), precoSugerido);
        }
        return sugestoes;
    }

    public List<Item<? extends Produto>> liberarItensParaVenda() {
        for (Item<? extends Produto> item : getItens()) {
            if (item.getQuantidadeDisponivel() == null || item.getQuantidadeDisponivel().compareTo(BigDecimal.ZERO) == 0) {
                item.setQuantidadeDisponivel(item.getQuantidade());
            }
        }
        return getItens();
    }

    @Override
    public BigDecimal getValorTotal() {
        BigDecimal somaItens = getItens().stream()
                .map(Item::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal abatimentoPerdas = notasPerdaAbatidas.stream()
                .map(NotaPerda::getValorCustoPerda)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return MoedaContabilBacen.arredondarMoeda(
                somaItens.subtract(getDescontoGlobal()).subtract(abatimentoPerdas).max(BigDecimal.ZERO)
        );
    }

    @Override
    public void atualizar() {
        liberarItensParaVenda();
    }

    @Override
    public boolean validarNota() {
        return getPessoa() != null && getPessoa().isFornecedor() && !getItens().isEmpty()
                && getItens().stream().allMatch(Item::isQuantidadeValida);
    }

    @Override
    public void aplicarDescontoNota(BigDecimal desconto) {
        setDescontoGlobal(desconto);
    }

    @Override
    public void aplicarDescontoProduto(Produto produto, BigDecimal desconto) {
        getItens().stream()
                .filter(i -> i.getProduto().equals(produto))
                .forEach(i -> i.aplicarDesconto(desconto));
    }

    @Override
    public void aplicarDescontoProdutos(List<Produto> produtos, BigDecimal desconto) {
        if (produtos == null) return;
        produtos.forEach(p -> aplicarDescontoProduto(p, desconto));
    }

    @Override
    public void aplicarCupomDesconto(String codigoCupom, BigDecimal valorDesconto) {
        this.cupomAplicado = codigoCupom;
        setDescontoGlobal(getDescontoGlobal().add(valorDesconto != null ? valorDesconto : BigDecimal.ZERO));
    }

    public String getCupomAplicado() {
        return cupomAplicado;
    }

    public List<NotaPerda> getNotasPerdaAbatidas() {
        return notasPerdaAbatidas;
    }
}
