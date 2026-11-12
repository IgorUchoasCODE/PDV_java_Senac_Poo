package br.com.senac.integracao.ia.ferramentas;

import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.produto.ConjuntoProdutoDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Utilitário para localização, desduplicação e limpeza de exibição de produtos por ID, nome, termos parciais e siglas.
 */
public final class ResolvedorProduto {

    private ResolvedorProduto() {}

    public static Produto localizarProdutoUnico(RoteadorController roteador, Long id, String termo) {
        if (id != null) {
            return roteador.produtos().buscarPorId(id);
        }
        if (termo == null || termo.isBlank()) return null;
        String t = termo.trim();
        if (t.matches("^#?\\d+$")) {
            try {
                Produto porId = roteador.produtos().buscarPorId(Long.parseLong(t.replace("#", "")));
                if (porId != null) return porId;
            } catch (Exception ignored) {}
        }
        Produto exato = roteador.produtos().buscarPorNome(termo);
        if (exato != null) return exato;
        List<Produto> candidatos = BuscaPalavrasParciaisUtil.filtrarEOrdenar(
                roteador.produtos().listarTodosProdutos(), Produto::getNome, termo);
        return candidatos.isEmpty() ? null : candidatos.get(0);
    }

    public static <T> boolean temEmpateDeSigla(List<T> filtrados, Function<T, String> extrator, String termo) {
        if (filtrados.size() < 2) return false;
        int s1 = BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(extrator.apply(filtrados.get(0)), termo);
        int s2 = BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(extrator.apply(filtrados.get(1)), termo);
        return s1 == s2 && s1 < 1000;
    }

    public static String limparNomeExibicao(String nome, String siglaUm) {
        if (nome == null) return "";
        String limpo = nome
                .replaceAll("\\s+#[0-9]{3,}\\b", "")
                .replaceAll("\\s+[0-9]{10,}\\b", "")
                .trim();
        if (siglaUm != null && !siglaUm.isBlank()) {
            limpo = limpo.replaceAll("(?i)\\s*\\(" + Pattern.quote(siglaUm.trim()) + "\\)\\s*$", "").trim();
        }
        limpo = limpo.replaceAll("(?i)\\s*\\((UN|KG|LT|MT|CX|PC)\\)\\s*$", "").trim();
        return limpo.isEmpty() ? nome.trim() : limpo;
    }

    public static List<Produto> filtrarProdutosExibicaoLimpa(List<Produto> produtos) {
        if (produtos == null) return Collections.emptyList();
        Map<String, Produto> canonicos = new LinkedHashMap<>();
        for (Produto p : produtos) {
            String nomeLimpo = limparNomeExibicao(p.getNome(), p.getUm() != null ? p.getUm().getSigla() : null);
            String chave = (p.getUm() != null ? p.getUm().getSigla() + ":" : "") + nomeLimpo.toLowerCase(Locale.ROOT);
            if (!canonicos.containsKey(chave)) {
                canonicos.put(chave, p);
            }
        }
        return new ArrayList<>(canonicos.values());
    }

    public static List<Produto> desduplicarPorNome(List<Produto> lista) {
        Map<String, Produto> unicasPorNome = new LinkedHashMap<>();
        for (Produto p : lista) {
            String chaveCanonica = BuscaPalavrasParciaisUtil.normalizar(limparNomeExibicao(p.getNome(), null));
            if (!chaveCanonica.isBlank()) {
                unicasPorNome.putIfAbsent(chaveCanonica, p);
            }
        }
        return new ArrayList<>(unicasPorNome.values());
    }

    /**
     * Formata de maneira rica, humana e completa todas as informações da entidade Produto:
     * identificação, unidade, preço de venda, tipo (Físico com dimensões/pesos ou Digital com links),
     * indicador de Conjunto/Kit com detalhamento de todos os componentes vinculados e estoque disponível.
     */
    public static String formatarDetalhesCompletosProduto(Produto p, RoteadorController roteador) {
        if (p == null) return "Nenhum produto selecionado.";
        String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
        String nomeLimpo = limparNomeExibicao(p.getNome(), umSigla);
        String preco = p.getPrecoVenda() != null
                ? p.getPrecoVenda().setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
                : "0.00";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#%d - %s (%s) — Preço de venda: R$ %s%n",
                p.getId(), nomeLimpo, umSigla, preco));

        // 1. Tipo do Produto (Digital vs Físico)
        if (p.isDigital()) {
            sb.append("• Tipo: Produto Digital\n");
            List<LinkProdutoDigital> links = null;
            if (p instanceof ProdutoDigital pd) {
                links = pd.getLinks();
            }
            if (links == null || links.isEmpty()) {
                sb.append("  Links de download: Nenhum link de download cadastrado.\n");
            } else {
                sb.append(String.format("  Links de download (%d):\n", links.size()));
                for (LinkProdutoDigital link : links) {
                    sb.append(String.format("    - Link #%d: %s (%s)\n",
                            link.getId(), link.getTitulo() != null ? link.getTitulo() : "Download", link.getUrl()));
                }
            }
        } else if (p instanceof ProdutoFisico pf) {
            sb.append("• Tipo: Produto Físico\n");
            if (pf.possuiDimensoesValidas()) {
                sb.append(String.format("  Dimensões: %scm x %scm x %scm (Volume: %scm³)\n",
                        pf.getAlturaCm(), pf.getLarguraCm(), pf.getComprimentoCm(), pf.calcularVolumeCm3()));
                sb.append(String.format("  Pesos: Peso Bruto: %skg, Peso Líquido: %skg\n",
                        pf.getPesoBrutoKg(), pf.getPesoLiquidoKg()));
            } else {
                sb.append("  Dimensões e pesos: Dimensões padrão (0.0000).\n");
            }
        }

        // 2. Indicador de Kit / Conjunto
        if (p.isConjunto()) {
            sb.append("• Conjunto/Kit: SIM (Produto composto por múltiplos itens)\n");
            List<ConjuntoProduto> componentes = p.getItensConjunto();
            if ((componentes == null || componentes.isEmpty()) && p.getId() != null) {
                try {
                    componentes = new ConjuntoProdutoDAO().listarPorProduto(p.getId());
                } catch (Exception ignored) {}
            }
            if (componentes == null || componentes.isEmpty()) {
                sb.append("  Componentes do Kit: Nenhum componente vinculado até o momento.\n");
            } else {
                sb.append(String.format("  Componentes do Kit (%d ite%s):\n",
                        componentes.size(), componentes.size() == 1 ? "m" : "ns"));
                for (ConjuntoProduto cp : componentes) {
                    Produto itemComp = cp.getItem();
                    String itemUm = itemComp != null && itemComp.getUm() != null ? itemComp.getUm().getSigla() : "UN";
                    String itemNome = itemComp != null ? limparNomeExibicao(itemComp.getNome(), itemUm) : "Item #" + cp.getId().getIdItem();
                    String itemTipo = (itemComp != null && itemComp.isDigital()) ? "Digital" : "Físico";
                    sb.append(String.format("    - #%d %s — Quantidade: %s %s (%s)\n",
                            itemComp != null ? itemComp.getId() : cp.getId().getIdItem(),
                            itemNome,
                            cp.getQuantidade() != null ? cp.getQuantidade().stripTrailingZeros().toPlainString() : "1",
                            itemUm, itemTipo));
                }
            }
        } else {
            sb.append("• Conjunto/Kit: NÃO (Produto simples)\n");
        }

        // 3. Saldo em Estoque e Lotes FIFO
        if (roteador != null && p.getId() != null) {
            try {
                BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                sb.append(String.format("• Estoque disponível: %s %s\n",
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0", umSigla));

                List<Item<? extends Produto>> lotes = roteador.estoque().listarLotesDisponiveisFIFO(p.getId());
                if (lotes != null && !lotes.isEmpty()) {
                    sb.append(String.format("  Lotes FIFO em estoque (%d):\n", lotes.size()));
                    for (Item<?> l : lotes) {
                        sb.append(String.format("    - Lote #%d — Saldo: %s %s, Custo Unit.: R$ %s, Validade: %s\n",
                                l.getId(),
                                l.getQuantidadeDisponivel() != null ? l.getQuantidadeDisponivel().stripTrailingZeros().toPlainString() : "0",
                                umSigla,
                                l.getValor() != null ? l.getValor().setScale(2, java.math.RoundingMode.HALF_EVEN) : "0.00",
                                l.getDataValidade() != null ? l.getDataValidade() : "Indeterminada"));
                    }
                }
            } catch (Exception ignored) {}
        }

        return sb.toString().trim();
    }
}
