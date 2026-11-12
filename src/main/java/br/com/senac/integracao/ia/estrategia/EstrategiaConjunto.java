package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.modal.produto.Produto;

import java.util.*;

/**
 * Estratégia para cadastro, manutenção e consulta de Kits e Produtos Compostos (Conjuntos).
 */
public class EstrategiaConjunto implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of("CONJUNTOPRODUTO", "COMPONENTE");

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        String nomeKit = ExtratorAtributos.obter(attrs, "nm", null);
        Long idKit = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        Object idKitParam = idKit;
        StringBuilder sb = new StringBuilder();

        // Se foi passado nm> e não tem id>, agenda primeiro a criação do produto Kit/Conjunto!
        if (idKit == null && nomeKit != null && !nomeKit.isBlank()) {
            String siglaUm = ExtratorAtributos.obter(attrs, "um", "UN");
            String preco = ExtratorAtributos.obter(attrs, "pr", ExtratorAtributos.obter(attrs, "vl", ExtratorAtributos.obter(attrs, "vv", "0.00")));
            Map<String, Object> paramsKit = new LinkedHashMap<>();
            paramsKit.put("nome", nomeKit);
            paramsKit.put("siglaUm", siglaUm);
            paramsKit.put("precoVenda", preco);
            ExtratorAtributos.aplicarDimensoes(attrs, paramsKit);
            ItemBandeja itemKit = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_FISICO_CADASTRAR, paramsKit,
                    "Cadastrar Produto Conjunto/Kit " + nomeKit + " (" + siglaUm + ")");
            idKitParam = itemKit.getIdItem();
            sb.append(String.format("[%s] Cadastrar Produto Conjunto %s (%s) - R$ %s.%n",
                    itemKit.getIdItem(), nomeKit, siglaUm, preco));
        } else if (idKit == null) {
            idKitParam = 1L;
        }

        // Obtém componentes dos sub-nós AST
        List<NoInstrucao> componentes = no.getSubInstrucoesPorEntidade("COMPONENTE");
        if (componentes.isEmpty()) {
            componentes = no.getSubInstrucoesPorEntidade("PRODUTO");
        }
        if (componentes.isEmpty()) {
            componentes = no.getSubInstrucoes();
        }

        for (NoInstrucao compNo : componentes) {
            String nmComp = compNo.getOuPadrao("nm", "");
            Long idCompParsed = FormatadorMoeda.parseLongSafe(compNo.get("id"));
            Produto comp = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), idCompParsed, nmComp);
            if (comp != null && nomeKit != null && comp.getNome().equalsIgnoreCase(nomeKit)) {
                return br.com.senac.integracao.ia.CatalogoMensagens.erroConjuntoCiclo(nomeKit);
            }
            Long idComp = comp != null ? comp.getId() : (idCompParsed != null ? idCompParsed : 1L);
            String qtd = compNo.getOuPadrao("q", "1");

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("idProdutoKit", idKitParam);
            params.put("idProdutoComponente", idComp);
            params.put("quantidade", qtd);

            String descComp = "Adicionar " + qtd + "x " + (comp != null ? comp.getNome() : "#" + idComp)
                    + " ao Kit " + (nomeKit != null ? nomeKit : "#" + idKitParam);
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, params, descComp);
            sb.append(String.format("[%s] Adicionar %s (quantidade: %s) ao Kit.\n",
                    item.getIdItem(), comp != null ? comp.getNome() : "#" + idComp, qtd));
        }

        return sb.toString().trim();
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        // Atualização de kit delega para adição de componentes ou atualização de preço do produto
        return compilarCriacao(no, bandeja, ctx);
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        Long idKit = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        if (idKit != null) {
            Map<String, Object> paramsDel = Map.of("idProduto", idKit);
            ItemBandeja itemDel = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
                    paramsDel,
                    "Excluir o produto conjunto/kit #" + idKit);
            return String.format("[%s] Excluir o kit #%d.", itemDel.getIdItem(), idKit);
        }
        return "Informe id> ID_KIT para exclusão do conjunto.";
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        Produto p = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), id, termo);
        if (p == null) {
            return "Conjunto/Kit não encontrado para os filtros informados.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Conjunto #%d - %s (%s):\n",
                p.getId(), p.getNome(), p.getUm() != null ? p.getUm().getSigla() : "UN"));
        if (!p.isConjunto()) {
            sb.append("  (Este produto ainda não possui componentes vinculados).\n");
        }
        return sb.toString().trim();
    }
}
