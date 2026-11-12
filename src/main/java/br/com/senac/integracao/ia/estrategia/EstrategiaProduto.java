package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ContextoIABuilder;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.RetornoChatBotUI;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.*;

/**
 * Estratégia de manipulação e consulta de Produtos e Unidades de Medida (UM).
 */
public class EstrategiaProduto implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of("PRODUTO", "PRODUTOFISICO", "PRODUTODIGITAL", "UM");

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String nome = ExtratorAtributos.obter(attrs, "nm", "");

        // Se o comando possui sub-instruções de componentes ou declara ser conjunto/kit, delega para EstrategiaConjunto
        boolean ehDeclaradoConjunto = no.temSubInstrucoes()
                || "true".equalsIgnoreCase(attrs.get("conjunto"))
                || "true".equalsIgnoreCase(attrs.get("kit"))
                || no.getTextoOriginal().toLowerCase(Locale.ROOT).contains("conjunto")
                || no.getTextoOriginal().toLowerCase(Locale.ROOT).contains("kit");

        if (ehDeclaradoConjunto && !"UM".equals(entidade)) {
            return new EstrategiaConjunto().compilarCriacao(no, bandeja, ctx);
        }

        if ("UM".equals(entidade)) {
            String sigla = ExtratorAtributos.obter(attrs, "nm", ExtratorAtributos.obter(attrs, "um", "UN")).toUpperCase(Locale.ROOT);
            String descUm = ExtratorAtributos.obter(attrs, "desc", sigla);
            String base = ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "1.000000"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sigla", sigla);
            params.put("nome", descUm);
            params.put("descricao", descUm);
            params.put("base", base);
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
                    params,
                    "Cadastrar unidade de medida " + sigla + " (" + descUm + ")");
            return String.format("[%s] Preparei o cadastro da unidade de medida %s (%s).",
                    item.getIdItem(), sigla, descUm);
        }

        if (nome.isBlank()) {
            return ContextoIABuilder.formatarPropriedade("ia.erro.campo.obrigatorio",
                    "Campo obrigatorio '%s' nao informado para #%s (ex: %s).", "#" + entidade, "nm", "nm> Nome do Produto");
        }
        for (String chaveNum : List.of("vv", "val", "vl")) {
            if (attrs.containsKey(chaveNum) && !validarNumeroReal(attrs.get(chaveNum))) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.valor.numerico",
                        "O valor '%s' informado no campo '%s' e invalido.", attrs.get(chaveNum), chaveNum);
            }
        }
        boolean digital = "PRODUTODIGITAL".equals(entidade);
        String um = digital ? "UN" : ExtratorAtributos.obter(attrs, "um", "UN").toUpperCase(Locale.ROOT);
        if (!ctx.roteador().produtos().unidadeExiste(um)) {
            return String.format("A unidade de medida %s não está cadastrada. Use uma existente (ex.: UN, KG, CX) "
                    + "ou cadastre antes com: $$ C= #Um nm> %s, desc> Descrição;", um, um);
        }

        Produto existente = ctx.roteador().produtos().buscarPorChaveUnica(nome, um, false);
        if (existente != null) {
            BigDecimal saldo = ctx.roteador().estoque().consultarSaldoTotalProduto(existente.getId());
            String umSigla = existente.getUm() != null ? existente.getUm().getSigla() : "UN";
            return String.format("O produto %s (#%d) já está cadastrado na unidade %s.\n"
                            + "  Estoque disponível: %s %s, Preço atual: R$ %s\n"
                            + "  Para alterar o preço, envie: $$ U= #Produto id> %d, vv> NOVO_VALOR;\n"
                            + "  Para cadastrar o mesmo nome em outra unidade, informe: um> CX (por exemplo).",
                    ResolvedorProduto.limparNomeExibicao(existente.getNome(), umSigla), existente.getId(), umSigla,
                    saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0",
                    umSigla,
                    FormatadorMoeda.moeda(existente.getPrecoVenda()), existente.getId());
        }

        String precoVenda = ExtratorAtributos.obter(attrs, "vv", ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00")));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("nome", nome);
        params.put("descricao", ExtratorAtributos.obter(attrs, "desc", "Cadastrado via assistente"));
        params.put("precoVenda", precoVenda);

        RotaSistema rotaAlvo = digital ? RotaSistema.PRODUTO_DIGITAL_CADASTRAR : RotaSistema.PRODUTO_FISICO_CADASTRAR;
        if (rotaAlvo == RotaSistema.PRODUTO_FISICO_CADASTRAR) {
            params.put("siglaUm", um);
            ExtratorAtributos.aplicarDimensoes(attrs, params);
        }

        String desc = String.format("Cadastrar produto '%s' (%s) por R$ %s", nome, um, precoVenda);
        ItemBandeja item = bandeja.adicionarOuAtualizar(rotaAlvo, params, desc);
        return String.format("[%s] Preparei o cadastro do produto %s (Unidade: %s, Preço de venda: R$ %s).",
                item.getIdItem(), nome, um, precoVenda);
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        if ("UM".equals(entidade)) {
            String sigla = ExtratorAtributos.obter(attrs, "nm", "UN").toUpperCase(Locale.ROOT);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sigla", sigla);
            params.put("nome", ExtratorAtributos.obter(attrs, "desc", sigla));
            params.put("descricao", ExtratorAtributos.obter(attrs, "desc", sigla));
            if (attrs.containsKey("val")) params.put("base", attrs.get("val"));
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_UNIDADE_ATUALIZAR, params,
                    "Atualizar unidade de medida " + sigla);
            return String.format("[%s] Atualizar a unidade de medida %s.", item.getIdItem(), sigla);
        }

        Produto alvo = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), id, termo);
        if (alvo != null) {
            String umSigla = alvo.getUm() != null ? alvo.getUm().getSigla() : "UN";
            String nomeAlvoLimpo = ResolvedorProduto.limparNomeExibicao(alvo.getNome(), umSigla);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "ProdutoDAO", alvo.getId(), nomeAlvoLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }
        }

        StringBuilder resumoDiff = new StringBuilder();
        if (attrs.containsKey("vv") || attrs.containsKey("val") || attrs.containsKey("vl")) {
            String novoPreco = ExtratorAtributos.obter(attrs, "vv", ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00")));
            Map<String, Object> paramsPreco = new LinkedHashMap<>();
            if (alvo != null) {
                paramsPreco.put("idProduto", alvo.getId());
            } else {
                paramsPreco.put("nomeProduto", termo);
            }
            paramsPreco.put("novoPreco", novoPreco);
            String descItem = alvo != null
                    ? String.format("Atualizar preço do produto '%s' (#%d) para R$ %s",
                    ResolvedorProduto.limparNomeExibicao(alvo.getNome(), alvo.getUm() != null ? alvo.getUm().getSigla() : null), alvo.getId(), novoPreco)
                    : String.format("Atualizar preço do produto '%s' para R$ %s", termo, novoPreco);

            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, paramsPreco, descItem);
            resumoDiff.append(String.format("[%s] %s.\n", item.getIdItem(), descItem));
        }

        if (attrs.containsKey("dim") || attrs.containsKey("pb") || attrs.containsKey("pl")) {
            Map<String, Object> paramsDim = new LinkedHashMap<>();
            if (alvo != null) paramsDim.put("idProduto", alvo.getId());
            ExtratorAtributos.aplicarDimensoes(attrs, paramsDim);
            String descDim = alvo != null
                    ? String.format("Atualizar dimensões do produto físico '%s' (#%d)", alvo.getNome(), alvo.getId())
                    : "Atualizar dimensões do produto físico";
            ItemBandeja itemDim = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_FISICO_ATUALIZAR_DIMENSOES, paramsDim, descDim);
            resumoDiff.append(String.format("[%s] %s.\n", itemDim.getIdItem(), descDim));
        }

        if (attrs.containsKey("novo_nm")) {
            String novoNome = attrs.get("novo_nm");
            Map<String, Object> paramsNm = new LinkedHashMap<>();
            if (alvo != null) {
                paramsNm.put("idProduto", alvo.getId());
                paramsNm.put("nomeAtual", alvo.getNome());
            } else {
                paramsNm.put("nomeAtual", termo);
            }
            paramsNm.put("novoNome", novoNome);
            String descNm = String.format("Alterar nome do produto para '%s'", novoNome);
            ItemBandeja itemNm = bandeja.adicionarOuAtualizar(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR, paramsNm, descNm);
            resumoDiff.append(String.format("[%s] %s.\n", itemNm.getIdItem(), descNm));
        }

        return resumoDiff.toString().trim();
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        Produto alvo = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), id, termo);
        if (alvo == null) {
            return "Não encontrei o produto '" + (id != null ? "#" + id : termo) + "' no catálogo.";
        }

        BigDecimal saldo = ctx.roteador().estoque().consultarSaldoTotalProduto(alvo.getId());
        boolean temEstoque = saldo != null && saldo.compareTo(BigDecimal.ZERO) > 0;
        List<NoInstrucao> perdas = no.getSubInstrucoesPorEntidade("NOTAPERDA");
        boolean solicitouAbatimentoPerda = !perdas.isEmpty() || "isento".equalsIgnoreCase(ExtratorAtributos.obter(attrs, "resp", ""));
        String umSigla = alvo.getUm() != null ? alvo.getUm().getSigla() : "UN";
        String nomeAlvoLimpo = ResolvedorProduto.limparNomeExibicao(alvo.getNome(), umSigla);

        String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                "ProdutoDAO", alvo.getId(), nomeAlvoLimpo, bandeja != null ? bandeja.getIdSessao() : null);
        if (erroTrancado != null) {
            return erroTrancado;
        }

        if (temEstoque && !solicitouAbatimentoPerda) {
            return String.format("Exclusão bloqueada preventivamente:\n"
                            + "  O produto #%d - %s ainda possui %s %s em estoque.\n"
                            + "  Só é permitido excluir produtos com estoque zerado.\n"
                            + "  Para dar baixa no estoque como perda (isentando o fornecedor) e excluir em seguida, envie:\n"
                            + "  $$ D= #Produto id> %d, -- #NotaPerda[ q> %s, resp> isento, mot> Baixa para exclusao ];",
                    alvo.getId(), nomeAlvoLimpo,
                    saldo.stripTrailingZeros().toPlainString(),
                    umSigla,
                    alvo.getId(), saldo.stripTrailingZeros().toPlainString());
        }

        StringBuilder sb = new StringBuilder();
        if (temEstoque) {
            List<Item<? extends Produto>> lotes = ctx.roteador().estoque().listarLotesDisponiveisFIFO(alvo.getId());
            for (Item<? extends Produto> lote : lotes) {
                if (lote.getQuantidadeDisponivel() != null && lote.getQuantidadeDisponivel().compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> paramsPerda = new LinkedHashMap<>();
                    paramsPerda.put("idLote", lote.getId());
                    paramsPerda.put("quantidade", lote.getQuantidadeDisponivel().toPlainString());
                    paramsPerda.put("motivo", "Abatimento total para exclusão de produto [Fornecedor Isento de Ressarcimento]");

                    ItemBandeja itemPerda = bandeja.adicionarOuAtualizar(
                            RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA,
                            paramsPerda,
                            String.format("Baixar Nota de Perda (Lote #%d, Qtd: %s) - Fornecedor Isento de Ressarcimento",
                                    lote.getId(), lote.getQuantidadeDisponivel().stripTrailingZeros().toPlainString())
                    );
                    sb.append(String.format("[%s] Abater %s %s do lote #%d via Nota de Perda (Fornecedor Isento de Ressarcimento).\n",
                            itemPerda.getIdItem(), lote.getQuantidadeDisponivel().stripTrailingZeros().toPlainString(),
                            umSigla, lote.getId()));
                }
            }
        }

        Map<String, Object> paramsDel = Map.of("idProduto", alvo.getId());
        ItemBandeja item = bandeja.adicionarOuAtualizar(
                RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
                paramsDel,
                String.format("Excluir produto '%s' (#%d)", nomeAlvoLimpo, alvo.getId())
        );
        sb.append(String.format("[%s] Excluir o produto #%d - %s do catálogo.", item.getIdItem(), alvo.getId(), nomeAlvoLimpo));
        return sb.toString().trim();
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        List<String> alvosCascata = no.getCascata();
        String termo = ExtratorAtributos.obter(attrs, "nm", ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", "")));
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        Long idNotaRef = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id_nota", ExtratorAtributos.obter(attrs, "nota", null)));
        String ctxFoco = ExtratorAtributos.obter(attrs, "contexto_foco", "");

        boolean semFiltroExplicito = (id == null && termo.isBlank());
        boolean pedirDaNota = "da_nota".equals(ctxFoco)
                || idNotaRef != null
                || (semFiltroExplicito && ctx.foco() != null && ctx.foco().getIdUltimaNota() != null);

        if (pedirDaNota) {
            Long idNotaAlvo = idNotaRef != null ? idNotaRef : (ctx.foco() != null ? ctx.foco().getIdUltimaNota() : null);
            if (idNotaAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                        "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
            }
            var notaFoco = ctx.roteador().notas().buscarNotaPorId(idNotaAlvo);
            if (notaFoco == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota #%s não encontrada.", idNotaAlvo);
            }
            RetornoChatBotUI.RetornoProdutosDaNota recProds = RetornoChatBotUI.construirRecordProdutosDaNota(notaFoco);
            if (ctx.foco() != null) {
                ctx.foco().registrarRecord(recProds);
            }
            return recProds.formatarTexto();
        }

        if (!alvosCascata.isEmpty() && (id != null || !termo.isBlank())) {
            Produto p = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), id, termo);
            if (p == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.produto.nao.encontrado",
                        "Não encontrei nenhum produto correspondente a '%s'.", id != null ? "#" + id : termo);
            }
            if (ctx.foco() != null) {
                ctx.foco().registrarProdutoEmFoco(p);
            }
            boolean incEst = cascataContem(alvosCascata, "ESTOQUE", "LOTE") || alvosCascata.isEmpty();
            boolean incForn = cascataContem(alvosCascata, "FORNECEDOR", "PESSOA") || alvosCascata.isEmpty();
            boolean incNotas = cascataContem(alvosCascata, "NOTA") || alvosCascata.isEmpty();
            RetornoChatBotUI.RetornoProdutoCascata recProd = RetornoChatBotUI.construirRecordProdutoCascata(
                    p, ctx.roteador(), incEst, incForn, incNotas);
            if (ctx.foco() != null) {
                ctx.foco().registrarRecord(recProd);
            }
            return recProd.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId());
        }

        List<Produto> todos = ctx.roteador().produtos().listarTodosProdutos();
        if (id != null) {
            Produto p = ctx.roteador().produtos().buscarPorId(id);
            if (p == null) return "Não encontrei nenhum produto com o código #" + id + ".";
            if (ctx.foco() != null) {
                ctx.foco().registrarProdutoEmFoco(p);
            }
            return ResolvedorProduto.formatarDetalhesCompletosProduto(p, ctx.roteador())
                    + sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId());
        }

        if (!termo.isBlank()) {
            if (termo.trim().matches("^#?\\d+$")) {
                try {
                    Long idParsed = Long.parseLong(termo.trim().replace("#", ""));
                    Produto p = ctx.roteador().produtos().buscarPorId(idParsed);
                    if (p != null) {
                        if (ctx.foco() != null) {
                            ctx.foco().registrarProdutoEmFoco(p);
                        }
                        return ResolvedorProduto.formatarDetalhesCompletosProduto(p, ctx.roteador())
                                + sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId());
                    }
                } catch (Exception ignored) {}
            }
            List<Produto> filtrados = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todos, Produto::getNome, termo);
            if (filtrados.isEmpty()) {
                return "Não encontrei nenhum produto correspondente a '" + termo + "'.";
            }
            if (filtrados.size() == 1 || (BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(termo) && !ResolvedorProduto.temEmpateDeSigla(filtrados, Produto::getNome, termo))) {
                Produto p = filtrados.get(0);
                if (ctx.foco() != null) {
                    ctx.foco().registrarProdutoEmFoco(p);
                }
                return ResolvedorProduto.formatarDetalhesCompletosProduto(p, ctx.roteador())
                        + sufixoAvisoRegistroTrancadoLeitura("ProdutoDAO", p.getId());
            }
            StringBuilder sb = new StringBuilder("Produtos encontrados para '" + termo + "':\n");
            for (Produto p : filtrados.stream().limit(10).toList()) {
                BigDecimal saldo = ctx.roteador().estoque().consultarSaldoTotalProduto(p.getId());
                String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                String tipo = p.isDigital() ? "Digital" : "Físico";
                String kit = p.isConjunto() ? "Kit/Conjunto" : "Simples";
                sb.append(String.format("  • #%d - %s (%s): R$ %s [%s | %s] (Estoque: %s)\n",
                        p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), umSigla),
                        umSigla, FormatadorMoeda.moeda(p.getPrecoVenda()),
                        tipo, kit,
                        saldo != null ? saldo.stripTrailingZeros().toPlainString() : "0"));
            }
            return sb.toString().trim();
        }

        List<Produto> unicos = ResolvedorProduto.desduplicarPorNome(todos);
        StringBuilder sb = new StringBuilder("Produtos no catálogo (" + unicos.size() + " itens):\n");
        for (Produto p : unicos.stream().limit(15).toList()) {
            String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
            sb.append(String.format("  • #%d - %s (%s) — R$ %s\n",
                    p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), umSigla),
                    umSigla, FormatadorMoeda.moeda(p.getPrecoVenda())));
        }
        if (unicos.size() > 15) {
            sb.append(String.format("  ... e mais %d produto(s). Use 'nm> ...' para refinar.", unicos.size() - 15));
        }
        return sb.toString().trim();
    }

    private static boolean cascataContem(List<String> alvos, String... prefixos) {
        if (alvos == null || alvos.isEmpty()) return false;
        for (String a : alvos) {
            if (a == null) continue;
            String u = a.trim().toUpperCase(Locale.ROOT);
            for (String p : prefixos) {
                if (u.startsWith(p)) return true;
            }
        }
        return false;
    }

    private static boolean validarNumeroReal(String valor) {
        if (valor == null || valor.isBlank()) return false;
        String limpo = valor.replace("R$", "").replace(" ", "").replace(",", ".").trim();
        try {
            new BigDecimal(limpo);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String sufixoAvisoRegistroTrancadoLeitura(String prefixoDao, Long id) {
        if (id == null) return "";
        PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo()) {
            return String.format("\n  [Status Concorrência: Registro trancado para alteração de estado pela sessão '%s' — Consulta liberada]",
                    info.idSessaoBandeja());
        }
        return "";
    }

    private static String verificarBloqueioMutacaoOutraSessao(String prefixoDao, Long id, String nomeExibicao, String idSessaoAtual) {
        if (id == null) return null;
        PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo() && (idSessaoAtual == null || !info.idSessaoBandeja().equals(idSessaoAtual))) {
            return ContextoIABuilder.formatarPropriedade(
                    "ia.erro.registro.trancado",
                    "Operacao bloqueada: o registro #%s (%s) esta trancado pela sessao '%s' e nao pode ter seu estado alterado agora (apenas consultas R= sao permitidas).",
                    id, nomeExibicao, info.idSessaoBandeja());
        }
        return null;
    }
}
