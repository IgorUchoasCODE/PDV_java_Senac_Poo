package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ContextoIABuilder;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.RetornoChatBotUI;
import br.com.senac.integracao.ia.ferramentas.ConversorData;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.integracao.ia.ferramentas.ResolvedorPessoa;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Estratégia de manipulação e consulta de Notas Fiscais (Compra, Venda, Devolução e Perda).
 */
public class EstrategiaNota implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of(
            "NOTA", "NOTAS", "NOTACOMPRA", "NOTAVENDA", "NOTADEVOLUCAO", "NOTAPERDA"
    );

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();

        Map<String, List<String>> subAdd = new LinkedHashMap<>();
        Map<String, List<String>> subRem = new LinkedHashMap<>();
        organizarSubInstrucoes(no, subAdd, subRem);

        return switch (entidade) {
            case "NOTACOMPRA" -> compilarNotaCompra(attrs, subAdd, subRem, bandeja, ctx);
            case "NOTAVENDA" -> compilarNotaVenda(attrs, subAdd, bandeja, ctx);
            case "NOTADEVOLUCAO" -> compilarNotaDevolucao(attrs, subAdd, bandeja, ctx);
            case "NOTAPERDA" -> compilarNotaPerda(attrs, subAdd, bandeja, ctx);
            default -> "Tipo de nota '" + entidade + "' não suportado para emissão direta.";
        };
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Notas fiscais são documentos fiscais imutáveis. Para correções, registre devolução (#NotaDevolucao) ou perda (#NotaPerda).";
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Notas fiscais emitidas não podem ser excluídas do histórico fiscal.";
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        Map<String, String> attrs = no.getAtributos();
        List<String> alvosCascata = no.getCascata();
        RetornoChatBotUI.FocoBuscaSessao foco = ctx.foco();

        String termo = ExtratorAtributos.obter(attrs, "nm", ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", "")));
        Long id = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        Long idNotaRef = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_nota", ExtratorAtributos.obter(attrs, "nota", null)));
        String ctxFoco = ExtratorAtributos.obter(attrs, "contexto_foco", "");

        Long idAlvo = id != null ? id : idNotaRef;
        if (idAlvo == null && termo.isBlank() && "da_nota".equals(ctxFoco) && foco != null && foco.getIdUltimaNota() != null) {
            idAlvo = foco.getIdUltimaNota();
        }

        Nota notaEncontrada = null;
        if (idAlvo != null) {
            notaEncontrada = ctx.roteador().notas().buscarNotaPorId(idAlvo);
            if (notaEncontrada == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota #%s não encontrada.", idAlvo);
            }
        } else if (!termo.isBlank()) {
            String tLow = termo.toLowerCase(Locale.ROOT);
            List<Nota> todasNotas = ctx.roteador().notas().listarTodasNotas();
            for (Nota cand : todasNotas) {
                String nmP = cand.getPessoa() != null && cand.getPessoa().getNome() != null
                        ? cand.getPessoa().getNome().toLowerCase(Locale.ROOT) : "";
                String descN = cand.getDescricao() != null ? cand.getDescricao().toLowerCase(Locale.ROOT) : "";
                if (nmP.contains(tLow) || descN.contains(tLow)) {
                    if (notaEncontrada == null || cand.getId() > notaEncontrada.getId()) {
                        notaEncontrada = cand;
                    }
                }
            }
            if (notaEncontrada == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota '%s' não encontrada.", termo);
            }
        } else if (foco != null && foco.getIdUltimaPessoa() != null && ("da_pessoa".equals(ctxFoco) || foco.getIdUltimaNota() == null)) {
            Long idPesFoco = foco.getIdUltimaPessoa();
            for (Nota cand : ctx.roteador().notas().listarTodasNotas()) {
                if (cand.getPessoa() != null && Objects.equals(cand.getPessoa().getId(), idPesFoco)) {
                    if (notaEncontrada == null || cand.getId() > notaEncontrada.getId()) {
                        notaEncontrada = cand;
                    }
                }
            }
        }

        if (notaEncontrada != null) {
            if (foco != null) {
                foco.registrarNotaEmFoco(notaEncontrada);
            }
            boolean incContatos = alvosCascata.isEmpty()
                    || cascataContem(alvosCascata, "CONTATO", "REGISTRO", "FORNECEDOR", "CLIENTE", "PESSOA");
            boolean incProds = alvosCascata.isEmpty() || cascataContem(alvosCascata, "PRODUTO", "ITEM");
            boolean incPags = alvosCascata.isEmpty() || cascataContem(alvosCascata, "PAGAMENTO");

            RetornoChatBotUI.RetornoNotaDetalhada recNota = RetornoChatBotUI.construirRecordNota(
                    notaEncontrada, ctx.roteador(), incContatos, incProds, incPags);
            if (foco != null) {
                foco.registrarRecord(recNota);
            }

            if (!alvosCascata.isEmpty()) {
                List<RetornoChatBotUI.RetornoFormatavel> encadeados = new ArrayList<>();
                encadeados.add(recNota);
                for (String alvo : alvosCascata) {
                    String u = alvo.toUpperCase(Locale.ROOT);
                    if ((u.startsWith("FORNECEDOR") || u.startsWith("CLIENTE") || u.startsWith("PESSOA"))
                            && notaEncontrada.getPessoa() != null) {
                        RetornoChatBotUI.RetornoPessoaDetalhada rp = RetornoChatBotUI.construirRecordPessoa(
                                notaEncontrada.getPessoa(), notaEncontrada.getId(), ctx.roteador(), true, false, false);
                        if (foco != null) foco.registrarRecord(rp);
                        encadeados.add(rp);
                    } else if ((u.startsWith("CONTATO") || u.startsWith("REGISTRO"))
                            && notaEncontrada.getPessoa() != null) {
                        RetornoChatBotUI.RetornoContatosPessoa rc = RetornoChatBotUI.construirRecordContatos(
                                notaEncontrada.getPessoa(), notaEncontrada.getId(), ctx.roteador());
                        if (foco != null) foco.registrarRecord(rc);
                        encadeados.add(rc);
                    } else if (u.startsWith("PRODUTO") || u.startsWith("ITEM")) {
                        RetornoChatBotUI.RetornoProdutosDaNota rpr = RetornoChatBotUI.construirRecordProdutosDaNota(notaEncontrada);
                        if (foco != null) foco.registrarRecord(rpr);
                        encadeados.add(rpr);
                    } else if (u.startsWith("PAGAMENTO")) {
                        RetornoChatBotUI.RetornoPagamentosDaNota rpg = RetornoChatBotUI.construirRecordPagamentosDaNota(
                                notaEncontrada, ctx.roteador());
                        if (foco != null) foco.registrarRecord(rpg);
                        encadeados.add(rpg);
                    }
                }
                RetornoChatBotUI.ListaRetornosSequenciais seq = new RetornoChatBotUI.ListaRetornosSequenciais(encadeados);
                return seq.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("NotaDAO", notaEncontrada.getId());
            }

            return recNota.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("NotaDAO", notaEncontrada.getId());
        }

        // Listagem geral
        if (id != null) {
            Nota n = ctx.roteador().notas().buscarNotaPorId(id);
            return n != null
                    ? String.format("Nota #%d — Valor Total: R$ %s", n.getId(), FormatadorMoeda.moeda(n.getValorTotal()))
                    : "Nota #" + id + " não encontrada.";
        }
        List<Nota> notas = ctx.roteador().notas().listarTodasNotas();
        StringBuilder sb = new StringBuilder(String.format("Notas Emitidas (%d):\n", notas.size()));
        for (Nota n : notas) {
            sb.append(String.format("  Nota #%d (%s) — Total: R$ %s\n",
                    n.getId(), n.getClass().getSimpleName(), FormatadorMoeda.moeda(n.getValorTotal())));
        }
        return sb.toString().trim();
    }

    private String compilarNotaCompra(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem,
            BandejaOperacoesContexto bandeja,
            ContextoCompilacao ctx) {

        String nomeFornecedor = ExtratorAtributos.obter(attrs, "forn", ExtratorAtributos.obter(attrs, "fornecedor", ExtratorAtributos.obter(attrs, "nm", "Fornecedor Padrão")));
        Long idForn = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_forn", ExtratorAtributos.obter(attrs, "id_fornecedor", ExtratorAtributos.obter(attrs, "id", null))));
        String dtTexto = ExtratorAtributos.obter(attrs, "dt", "hoje");
        LocalDate dataNota = ConversorData.resolverDataRelativa(dtTexto);

        Pessoa fornecedor = (bandeja != null && bandeja.possuiPessoaEmEstagio(nomeFornecedor))
                ? null
                : ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), idForn, nomeFornecedor);
        List<Map<String, Object>> itensCompra = new ArrayList<>();
        List<Map<String, Object>> pagamentosCompra = new ArrayList<>();
        BigDecimal subtotalAcumulado = BigDecimal.ZERO;

        StringBuilder resumo = new StringBuilder();
        resumo.append(String.format("Nota de Compra — Fornecedor: %s (Entrega: %s - %s)\n",
                fornecedor != null ? ResolvedorPessoa.limparNomeExibicao(fornecedor.getNome(), null) : nomeFornecedor,
                ConversorData.descreverDataAmigavel(dtTexto, dataNota), dataNota));

        List<String> linhasProdutos = subAdd.getOrDefault("PRODUTO", subAdd.getOrDefault("ITEM", List.of()));
        if (!linhasProdutos.isEmpty()) {
            resumo.append("  Itens da nota:\n");
            for (String linhaProd : linhasProdutos) {
                Map<String, String> pa = ExtratorAtributos.extrairAtributos(linhaProd.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String nmProd = ExtratorAtributos.obter(pa, "nm", ExtratorAtributos.obter(pa, "prod", ExtratorAtributos.obter(pa, "produto", "")));
                Long idProd = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(pa, "id", ExtratorAtributos.obter(pa, "id_prod", null)));
                Produto prodBanco = (idProd == null && bandeja != null && bandeja.possuiProdutoEmEstagio(nmProd))
                        ? null
                        : ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), idProd, nmProd);

                BigDecimal qtd = ExtratorAtributos.parseBigDecimalSafe(ExtratorAtributos.obter(pa, "q", "1"), BigDecimal.ONE);
                String strCusto = ExtratorAtributos.obter(pa, "vc", ExtratorAtributos.obter(pa, "val", ExtratorAtributos.obter(pa, "vl", "0.00")));
                BigDecimal valorUnitario = ExtratorAtributos.parseBigDecimalSafe(strCusto, BigDecimal.ZERO);
                BigDecimal subtotalItem = valorUnitario.multiply(qtd);
                subtotalAcumulado = subtotalAcumulado.add(subtotalItem);

                Map<String, Object> itemMap = new LinkedHashMap<>();
                if (prodBanco != null) {
                    itemMap.put("idProduto", prodBanco.getId());
                    itemMap.put("nomeProduto", prodBanco.getNome());
                } else {
                    itemMap.put("nomeProduto", nmProd);
                }
                itemMap.put("quantidade", qtd.toPlainString());
                itemMap.put("valorUnitario", valorUnitario.toPlainString());
                String valStr = ExtratorAtributos.obter(pa, "validade", ExtratorAtributos.obter(pa, "val_dt", ExtratorAtributos.obter(pa, "dt_val", null)));
                if (valStr != null && !valStr.isBlank()) {
                    itemMap.put("validade", ConversorData.resolverDataRelativa(valStr).toString());
                }
                itensCompra.add(itemMap);

                resumo.append(String.format("    %s — %s un x R$ %s (Subtotal acum.: R$ %s)\n",
                        prodBanco != null ? ResolvedorProduto.limparNomeExibicao(prodBanco.getNome(), null) : nmProd,
                        qtd.stripTrailingZeros().toPlainString(),
                        valorUnitario.toPlainString(),
                        subtotalAcumulado.toPlainString()));
            }
        }

        List<String> linhasRessarcimento = subAdd.getOrDefault("RESSARCIMENTO", List.of());
        if (!linhasRessarcimento.isEmpty()) {
            resumo.append("  Ressarcimento de perdas do fornecedor:\n");
            for (String linhaRes : linhasRessarcimento) {
                Map<String, String> ra = ExtratorAtributos.extrairAtributos(linhaRes.replaceFirst("^(\\+\\+|--)\\s*", ""));
                String prodPerdido = ExtratorAtributos.obter(ra, "perdido", ExtratorAtributos.obter(ra, "nm_perd", "Produto perdido"));
                String qPerd = ExtratorAtributos.obter(ra, "q_perd", "1");
                String vcPerd = ExtratorAtributos.obter(ra, "vc_perd", "0.00");
                String prodRecebido = ExtratorAtributos.obter(ra, "recebido", ExtratorAtributos.obter(ra, "nm", prodPerdido));
                BigDecimal qRec = ExtratorAtributos.parseBigDecimalSafe(ExtratorAtributos.obter(ra, "q_rec", ExtratorAtributos.obter(ra, "q", "1")), BigDecimal.ONE);
                BigDecimal vcRec = ExtratorAtributos.parseBigDecimalSafe(ExtratorAtributos.obter(ra, "vc_rec", ExtratorAtributos.obter(ra, "vc", vcPerd)), BigDecimal.ZERO);

                Produto prodRecBanco = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), null, prodRecebido);
                Map<String, Object> itemRessarcido = new LinkedHashMap<>();
                if (prodRecBanco != null) {
                    itemRessarcido.put("idProduto", prodRecBanco.getId());
                    itemRessarcido.put("nomeProduto", prodRecBanco.getNome());
                } else {
                    itemRessarcido.put("nomeProduto", prodRecebido);
                }
                itemRessarcido.put("quantidade", qRec.toPlainString());
                itemRessarcido.put("valorUnitario", vcRec.toPlainString());
                itemRessarcido.put("ressarcimentoFornecedor", true);
                itensCompra.add(itemRessarcido);

                resumo.append(String.format("    Troca/Ressarcimento: %s un de %s (R$ %s) compensado por %s de %s (R$ %s) [Marcado como ressarcido]\n",
                        qPerd, prodPerdido, vcPerd, qRec.stripTrailingZeros().toPlainString(), prodRecebido, vcRec.toPlainString()));
            }
        }

        List<String> linhasPagamento = subAdd.getOrDefault("PAGAMENTO", List.of());
        BigDecimal totalPago = BigDecimal.ZERO;
        if (linhasPagamento.isEmpty()) {
            // Pagamento de nota de compra: padrão contábil é A PRAZO de 30 dias sem parcelas
            LocalDate vencPadrao = dataNota.plusDays(30);
            Map<String, Object> pagMap = new LinkedHashMap<>();
            pagMap.put("codigoForma", "BOLETO");
            pagMap.put("valor", subtotalAcumulado.toPlainString());
            pagMap.put("data", vencPadrao.toString());
            pagMap.put("modalidade", "PRAZO");
            pagamentosCompra.add(pagMap);
            totalPago = subtotalAcumulado;
            resumo.append(String.format("  • Pagamento padrão: A PRAZO (vencimento em 30 dias: %s) — R$ %s (sem parcelas)\n",
                    vencPadrao, subtotalAcumulado.toPlainString()));
        } else {
            resumo.append("  Pagamentos programados:\n");
            for (String linhaPag : linhasPagamento) {
                Map<String, String> pga = ExtratorAtributos.extrairAtributosPagamentoPosicionalOuChaveado(linhaPag);
                String forma = ExtratorAtributos.normalizarCodigoForma(ExtratorAtributos.obter(pga, "nm", "BOLETO"));
                BigDecimal valPag = ExtratorAtributos.parseBigDecimalSafe(ExtratorAtributos.obter(pga, "val", ExtratorAtributos.obter(pga, "vl", "0.00")), BigDecimal.ZERO);
                String dtPagStr = ExtratorAtributos.obter(pga, "dt", "hoje");
                LocalDate dtPag = ConversorData.resolverDataRelativa(dtPagStr);
                totalPago = totalPago.add(valPag);

                Map<String, Object> pagMap = new LinkedHashMap<>();
                pagMap.put("codigoForma", forma);
                pagMap.put("valor", valPag.toPlainString());
                pagMap.put("data", dtPag.toString());
                pagamentosCompra.add(pagMap);

                resumo.append(String.format("    %s: R$ %s (%s - %s)\n",
                        forma, valPag.toPlainString(), ConversorData.descreverDataAmigavel(dtPagStr, dtPag), dtPag));
            }
        }

        Map<String, Object> paramsNota = new LinkedHashMap<>();
        if (fornecedor != null) {
            paramsNota.put("idFornecedor", fornecedor.getId());
        }
        paramsNota.put("nomeFornecedor", fornecedor != null ? fornecedor.getNome() : nomeFornecedor);
        paramsNota.put("dataNota", dataNota.toString());
        paramsNota.put("descricao", "Nota de Compra de " + (fornecedor != null ? fornecedor.getNome() : nomeFornecedor));
        paramsNota.put("itens", itensCompra);
        paramsNota.put("pagamentos", pagamentosCompra);

        String resumoCurto = String.format("Emitir Nota de Compra de '%s' (%d ite%s, Total: R$ %s, Pago: R$ %s)",
                fornecedor != null ? ResolvedorPessoa.limparNomeExibicao(fornecedor.getNome(), null) : nomeFornecedor,
                itensCompra.size(), itensCompra.size() == 1 ? "m" : "ns",
                subtotalAcumulado.toPlainString(),
                totalPago.toPlainString());

        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.NOTA_COMPRA_EMITIR, paramsNota, resumoCurto);
        resumo.append(String.format("[%s] Nota preparada para confirmação (Total dos produtos: R$ %s, Pagamentos: R$ %s).",
                item.getIdItem(), subtotalAcumulado.toPlainString(), totalPago.toPlainString()));
        return resumo.toString();
    }

    private String compilarNotaVenda(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja,
            ContextoCompilacao ctx) {

        String tp = ExtratorAtributos.obter(attrs, "tp", "FIFO").toUpperCase(Locale.ROOT);
        String nmCliente = ExtratorAtributos.obter(attrs, "cli", ExtratorAtributos.obter(attrs, "cliente", ExtratorAtributos.obter(attrs, "nm", "CONSUMIDOR FINAL")));
        Long idCli = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_cli", ExtratorAtributos.obter(attrs, "id_cliente", ExtratorAtributos.obter(attrs, "id", null))));
        String dtTexto = ExtratorAtributos.obter(attrs, "dt", "hoje");
        LocalDate dataNota = ConversorData.resolverDataRelativa(dtTexto);

        Pessoa cliente = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), idCli, nmCliente);
        List<Map<String, Object>> itensVenda = new ArrayList<>();
        List<Map<String, Object>> pagamentosVenda = new ArrayList<>();
        BigDecimal subtotalVenda = BigDecimal.ZERO;

        List<String> linhasProdutos = subAdd.getOrDefault("PRODUTO", subAdd.getOrDefault("ITEM", List.of()));
        for (String linhaProd : linhasProdutos) {
            Map<String, String> pa = ExtratorAtributos.extrairAtributos(linhaProd.replaceFirst("^(\\+\\+|--)\\s*", ""));
            String nmProd = ExtratorAtributos.obter(pa, "nm", ExtratorAtributos.obter(pa, "prod", ExtratorAtributos.obter(pa, "produto", "")));
            Long idProd = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(pa, "id", ExtratorAtributos.obter(pa, "id_prod", null)));
            Produto prod = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), idProd, nmProd);
            BigDecimal qtd = ExtratorAtributos.parseBigDecimalSafe(ExtratorAtributos.obter(pa, "q", "1"), BigDecimal.ONE);
            BigDecimal preco = ExtratorAtributos.parseBigDecimalSafe(
                    ExtratorAtributos.obter(pa, "vv", ExtratorAtributos.obter(pa, "val", prod != null && prod.getPrecoVenda() != null ? prod.getPrecoVenda().toPlainString() : "0.00")),
                    BigDecimal.ZERO);
            subtotalVenda = subtotalVenda.add(preco.multiply(qtd));

            Map<String, Object> itemMap = new LinkedHashMap<>();
            if (prod != null) {
                itemMap.put("idProduto", prod.getId());
                itemMap.put("nomeProduto", prod.getNome());
            } else {
                itemMap.put("nomeProduto", nmProd.isBlank() ? "Produto" : nmProd);
            }
            itemMap.put("quantidade", qtd.toPlainString());
            itemMap.put("valorUnitario", preco.toPlainString());
            itensVenda.add(itemMap);
        }

        List<String> linhasPag = subAdd.getOrDefault("PAGAMENTO", List.of());
        boolean pagSemiAutomatico = false;
        if (linhasPag.isEmpty()) {
            // Nota de venda: pagamento obrigatório, semi-automático em DINHEIRO para confirmar
            Map<String, Object> pagMap = new LinkedHashMap<>();
            pagMap.put("codigoForma", "DINHEIRO");
            pagMap.put("valor", subtotalVenda.toPlainString());
            pagMap.put("data", dataNota.toString());
            pagamentosVenda.add(pagMap);
            pagSemiAutomatico = true;
        } else {
            for (String linhaPag : linhasPag) {
                Map<String, String> pga = ExtratorAtributos.extrairAtributosPagamentoPosicionalOuChaveado(linhaPag);
                Map<String, Object> pagMap = new LinkedHashMap<>();
                pagMap.put("codigoForma", ExtratorAtributos.normalizarCodigoForma(ExtratorAtributos.obter(pga, "nm", "DINHEIRO")));
                pagMap.put("valor", ExtratorAtributos.obter(pga, "val", ExtratorAtributos.obter(pga, "vl", subtotalVenda.toPlainString())));
                pagMap.put("data", ConversorData.resolverDataRelativa(ExtratorAtributos.obter(pga, "dt", "hoje")).toString());
                pagamentosVenda.add(pagMap);
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (cliente != null) {
            params.put("idCliente", cliente.getId());
        }
        params.put("nomeCliente", cliente != null ? cliente.getNome() : nmCliente);
        params.put("dataNota", dataNota.toString());
        params.put("itens", itensVenda);
        params.put("pagamentos", pagamentosVenda);

        RotaSistema rota = "BALCAO".equals(tp)
                ? RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO
                : ("LOTE".equals(tp) ? RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL : RotaSistema.NOTA_VENDA_EMITIR_FIFO);

        String resumo = String.format("Emitir Nota de Venda para '%s' (%d itens, Total: R$ %s)",
                cliente != null ? ResolvedorPessoa.limparNomeExibicao(cliente.getNome(), null) : nmCliente, itensVenda.size(), subtotalVenda.toPlainString());
        if (pagSemiAutomatico) {
            resumo += String.format(" [Forma de Pagamento semi-automática: DINHEIRO — R$ %s (Pronto para confirmar)]", subtotalVenda.toPlainString());
        }
        ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params, resumo);
        return String.format("[%s] %s.", item.getIdItem(), resumo);
    }

    private String compilarNotaDevolucao(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja,
            ContextoCompilacao ctx) {

        Long idNotaVenda = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_venda", ExtratorAtributos.obter(attrs, "id", "1")));
        boolean geraPerdaFornecedor = "FORN".equalsIgnoreCase(ExtratorAtributos.obter(attrs, "resp", "isento"))
                || "FORNECEDOR".equalsIgnoreCase(ExtratorAtributos.obter(attrs, "resp", "isento"));
        String motivo = ExtratorAtributos.obter(attrs, "mot", "Devolução de mercadoria");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idNotaVenda", idNotaVenda);
        params.put("motivo", motivo);
        params.put("geraPerdaFornecedor", geraPerdaFornecedor);

        List<String> prods = subAdd.getOrDefault("PRODUTO", subAdd.getOrDefault("ITEM", List.of()));
        if (!prods.isEmpty()) {
            Map<String, String> pa = ExtratorAtributos.extrairAtributos(prods.get(0).replaceFirst("^(\\+\\+|--)\\s*", ""));
            Produto p = ResolvedorProduto.localizarProdutoUnico(ctx.roteador(), ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(pa, "id", null)), ExtratorAtributos.obter(pa, "nm", ""));
            if (p != null) params.put("idProduto", p.getId());
            params.put("quantidadeDevolvida", ExtratorAtributos.obter(pa, "q", "1"));
        }

        String desc = String.format("Registrar devolução da venda #%d (%s)",
                idNotaVenda, geraPerdaFornecedor ? "gera ressarcimento com fornecedor" : "reintegra ao estoque");
        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.NOTA_DEVOLUCAO_REGISTRAR, params, desc);
        return String.format("[%s] %s.", item.getIdItem(), desc);
    }

    private String compilarNotaPerda(
            Map<String, String> attrs,
            Map<String, List<String>> subAdd,
            BandejaOperacoesContexto bandeja,
            ContextoCompilacao ctx) {

        Long idLote = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        String qtd = ExtratorAtributos.obter(attrs, "q", "1");
        List<String> itens = subAdd.getOrDefault("ITEM", subAdd.getOrDefault("PRODUTO", List.of()));
        if (!itens.isEmpty()) {
            Map<String, String> ia = ExtratorAtributos.extrairAtributos(itens.get(0).replaceFirst("^(\\+\\+|--)\\s*", ""));
            if (idLote == null) idLote = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(ia, "id", "1"));
            qtd = ExtratorAtributos.obter(ia, "q", qtd);
        }
        if (idLote == null) idLote = 1L;

        LocalDate dataPerda = ConversorData.resolverDataRelativa(ExtratorAtributos.obter(attrs, "dt", "hoje"));
        br.com.senac.repository.modal.nota.Item<? extends Produto> lote = ctx.roteador().estoque().buscarLoteEspecificoPorId(idLote);

        String motivoBase = ExtratorAtributos.obter(attrs, "mot", "Perda operacional");
        LocalDate dataValidade = lote != null ? lote.getDataValidade() : null;
        boolean vencido = dataValidade != null && dataPerda.isAfter(dataValidade);
        long diasDif = dataValidade != null ? Math.abs(java.time.temporal.ChronoUnit.DAYS.between(dataPerda, dataValidade)) : 0;

        boolean isentoFornecedor;
        if (attrs.containsKey("resp")) {
            isentoFornecedor = !"FORN".equalsIgnoreCase(attrs.get("resp"));
        } else {
            isentoFornecedor = vencido;
        }

        String motivoFinal = isentoFornecedor
                ? motivoBase + (vencido ? " [Fornecedor Isento - Lote Vencido há " + diasDif + " dias]" : " [Fornecedor Isento de Ressarcimento]")
                : motivoBase + " [Dentro da validade (" + diasDif + " dias restantes) - Ressarcimento do Fornecedor]";

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("idLote", idLote);
        params.put("quantidade", qtd);
        params.put("motivo", motivoFinal);

        ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA, params,
                "Baixar perda no lote #" + idLote + " (qtd: " + qtd + " — " + (isentoFornecedor ? "Fornecedor Isento" : "Ressarcir Fornecedor") + ")");

        StringBuilder sb = new StringBuilder();
        sb.append("📋 PRÉ-RELATÓRIO DE PERDA OPERACIONAL — ANÁLISE DE VALIDADE:\n");
        if (lote != null) {
            String nomeProd = lote.getProduto() != null ? lote.getProduto().getNome() : "Produto";
            String umSigla = lote.getProduto() != null && lote.getProduto().getUm() != null ? lote.getProduto().getUm().getSigla() : "UN";
            BigDecimal qtdBd = ExtratorAtributos.parseBigDecimalSafe(qtd, BigDecimal.ONE);
            BigDecimal valorUnit = lote.getValor() != null ? lote.getValor() : BigDecimal.ZERO;
            BigDecimal subtotal = valorUnit.multiply(qtdBd);

            sb.append(String.format("  • Lote: #%d — %s (%s)\n", lote.getId(), nomeProd, umSigla));
            sb.append(String.format("  • Quantidade a Baixar: %s %s (Saldo disponível no lote: %s %s)\n",
                    qtdBd.stripTrailingZeros().toPlainString(), umSigla,
                    lote.getQuantidadeDisponivel() != null ? lote.getQuantidadeDisponivel().stripTrailingZeros().toPlainString() : "0", umSigla));
            sb.append(String.format("  • Custo Unitário: R$ %s | Impacto Financeiro Total: R$ %s\n",
                    valorUnit.toPlainString(), subtotal.toPlainString()));
            sb.append(String.format("  • Data da Perda: %s\n", dataPerda));
            sb.append(String.format("  • Data de Validade do Lote: %s\n", dataValidade != null ? dataValidade.toString() : "Indeterminada / Sem vencimento"));
            if (dataValidade != null) {
                if (vencido) {
                    sb.append(String.format("  • Diagnóstico: ⚠️ LOTE VENCIDO (Expirado há %d dia%s).\n", diasDif, diasDif == 1 ? "" : "s"));
                    sb.append("    Classificação: Perda por Vencimento / Perecimento.\n");
                    sb.append("    Responsabilidade: ESTOQUE INTERNO (Fornecedor Isento de Ressarcimento).\n");
                } else {
                    sb.append(String.format("  • Diagnóstico: ✔ LOTE DENTRO DA VALIDADE (Restam %d dia%s até o vencimento).\n", diasDif, diasDif == 1 ? "" : "s"));
                    sb.append("    Classificação: Perda Operacional / Avaria / Quebra.\n");
                    sb.append("    Responsabilidade: ELEGÍVEL PARA RESSARCIMENTO / ABATIMENTO COM O FORNECEDOR.\n");
                }
            } else {
                sb.append("  • Diagnóstico: Lote sem data de validade cadastrada (Perda operacional direta).\n");
                sb.append("    Responsabilidade: ESTOQUE INTERNO (Fornecedor Isento).\n");
            }
        } else {
            sb.append(String.format("  • Lote: #%d — Quantidade: %s\n", idLote, qtd));
            sb.append(String.format("  • Data da Perda: %s\n", dataPerda));
        }
        sb.append(String.format("  • Motivo Anotado: %s\n\n", motivoFinal));
        sb.append(String.format("[%s] Instrução preparada para confirmação (Digite SIM para confirmar a baixa ou NÃO para cancelar).", item.getIdItem()));
        return sb.toString();
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

    private static void organizarSubInstrucoes(
            NoInstrucao no,
            Map<String, List<String>> subAdd,
            Map<String, List<String>> subRem) {
        for (NoInstrucao sub : no.getSubInstrucoes()) {
            String ent = sub.getEntidade() != null ? sub.getEntidade().toUpperCase(Locale.ROOT) : "ITEM";
            StringBuilder raw = new StringBuilder();
            sub.getAtributos().forEach((k, v) -> {
                if (raw.length() > 0) raw.append(", ");
                raw.append(k).append("> ").append(v);
            });
            String linha = raw.toString();
            if (sub.getOperacao() == br.com.senac.integracao.ia.parser.TipoOperacao.SUB_REMOVER) {
                subRem.computeIfAbsent(ent, k -> new ArrayList<>()).add(linha);
            } else {
                subAdd.computeIfAbsent(ent, k -> new ArrayList<>()).add(linha);
            }
        }
    }
}
