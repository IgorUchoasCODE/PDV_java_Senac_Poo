package br.com.senac.integracao.ia.estrategia;

import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Estratégia de consulta para níveis de Estoque e saldos de produtos.
 */
public class EstrategiaEstoque implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of("ESTOQUE");

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Para abastecer o estoque, emita uma Nota de Compra ($$ C= #NotaCompra ...).";
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Alterações de estoque devem ser feitas via Nota de Compra, Venda ou Perda.";
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Para baixar estoque, utilize uma Nota de Perda ($$ C= #NotaPerda ...).";
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        var attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", "")));
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        if (id != null || !termo.isBlank()) {
            Produto p = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), id, termo);
            if (p != null) {
                BigDecimal saldo = ctx.roteador().estoque().consultarSaldoTotalProduto(p.getId());
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                return String.format("%s (#%d) — Estoque disponível: %s %s, Preço de venda: R$ %s",
                        ResolvedorProduto.limparNomeExibicao(p.getNome(), umSigla), p.getId(),
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                        umSigla,
                        FormatadorMoeda.moeda(p.getPrecoVenda()));
            }
            return "Produto não encontrado para consulta de estoque.";
        }

        List<Produto> comEstoque = ctx.roteador().produtos().listarProdutosComEstoqueValido();
        StringBuilder sb = new StringBuilder(String.format("Produtos com estoque disponível (%d itens):\n", comEstoque.size()));
        for (Produto p : comEstoque.stream().limit(15).toList()) {
            BigDecimal saldo = ctx.roteador().estoque().consultarSaldoTotalProduto(p.getId());
            String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
            sb.append(String.format("  • #%d - %s (%s): %s em estoque (Preço: R$ %s)\n",
                    p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), umSigla),
                    umSigla, saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                    FormatadorMoeda.moeda(p.getPrecoVenda())));
        }
        return sb.toString().trim();
    }
}
