package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ContextoIABuilder;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.RetornoChatBotUI;
import br.com.senac.integracao.ia.ferramentas.ConversorData;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pagamento.FormaPagamento;

import java.time.LocalDate;
import java.util.*;

/**
 * Estratégia de manipulação e consulta de Formas de Pagamento, Pagamentos e Quitações.
 */
public class EstrategiaPagamento implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of(
            "FORMAPAGAMENTO", "PAGAMENTO", "PAGAMENTOS", "PAGAMENTOAVISTA",
            "PAGAMENTOAPRAZO", "PAGAMENTOPARCELADO", "NOTAPAGAMENTO", "PARCELAPAGAMENTO"
    );

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String nome = ExtratorAtributos.obter(attrs, "nm", "");

        if ("FORMAPAGAMENTO".equals(entidade)) {
            String cod = ExtratorAtributos.obter(attrs, "cod", nome.toUpperCase(Locale.ROOT).replace(" ", "_"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("codigo", cod);
            params.put("nome", nome.isBlank() ? cod : nome);
            params.put("descricao", ExtratorAtributos.obter(attrs, "desc", "Forma de pagamento " + cod));
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PAGAMENTO_FORMA_CADASTRAR, params,
                    "Cadastrar forma de pagamento " + params.get("nome"));
            return String.format("[%s] Preparei o cadastro da forma de pagamento %s.",
                    item.getIdItem(), params.get("nome"));
        }

        if (entidade.startsWith("PAGAMENTO")) {
            String codigoForma = ExtratorAtributos.normalizarCodigoForma(nome);
            String valor = ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00"));
            LocalDate dt = ConversorData.resolverDataRelativa(ExtratorAtributos.obter(attrs, "dt", "hoje"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("codigoForma", codigoForma);
            params.put("valor", valor);
            RotaSistema rota = RotaSistema.PAGAMENTO_CRIAR_A_VISTA;
            if ("PAGAMENTOAPRAZO".equals(entidade)) {
                rota = RotaSistema.PAGAMENTO_CRIAR_A_PRAZO;
                params.put("dataVencimento", dt);
            } else if ("PAGAMENTOPARCELADO".equals(entidade)) {
                rota = RotaSistema.PAGAMENTO_CRIAR_PARCELADO;
                params.put("quantidadeParcelas", Integer.parseInt(ExtratorAtributos.obter(attrs, "q", "2").replaceAll("[^0-9]", "")));
            }
            ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params,
                    "Registrar pagamento em " + codigoForma + " de R$ " + valor + " (" + dt + ")");
            return String.format("[%s] Preparei o pagamento em %s no valor de R$ %s (data: %s).",
                    item.getIdItem(), codigoForma, valor, dt);
        }

        return "Instrução de pagamento adicionada à lista de espera.";
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();

        if ("NOTAPAGAMENTO".equals(entidade) || "PARCELAPAGAMENTO".equals(entidade)) {
            String notasAlvo = ExtratorAtributos.obter(attrs, "notas",
                    ExtratorAtributos.obter(attrs, "id_pag", ExtratorAtributos.obter(attrs, "id", "1")));
            List<String> baixas = new ArrayList<>();
            for (NoInstrucao sub : no.getSubInstrucoes()) {
                if ("BAIXA".equalsIgnoreCase(sub.getEntidade()) || "PAGAMENTO".equalsIgnoreCase(sub.getEntidade())) {
                    baixas.add(sub.getAtributos().toString());
                }
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("notasReferenciadas", notasAlvo);
            params.put("baixas", baixas);
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PAGAMENTO_QUITAR_TITULO_OU_PARCELA, params,
                    "Dar baixa/quitar títulos " + notasAlvo);
            return String.format("[%s] Dar baixa/quitação nas notas %s.", item.getIdItem(), notasAlvo);
        }

        return "Atualização de pagamento adicionada à lista de espera.";
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");

        if ("FORMAPAGAMENTO".equals(entidade)) {
            String cod = ExtratorAtributos.obter(attrs, "cod", termo).toUpperCase(Locale.ROOT);
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PAGAMENTO_FORMA_EXCLUIR,
                    Map.of("codigo", cod),
                    "Excluir forma de pagamento " + cod);
            return String.format("[%s] Excluir a forma de pagamento %s.", item.getIdItem(), cod);
        }

        return "Exclusão de pagamento adicionada à lista de espera.";
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        RetornoChatBotUI.FocoBuscaSessao foco = ctx.foco();

        if ("FORMAPAGAMENTO".equals(entidade)) {
            String termo = ExtratorAtributos.obter(attrs, "cod", ExtratorAtributos.obter(attrs, "nm", ""));
            if (!termo.isBlank()) {
                FormaPagamento fp = ctx.roteador().pagamentos().buscarFormaPorCodigo(termo);
                if (fp != null) {
                    return br.com.senac.integracao.ia.ferramentas.ResolvedorPagamento.formatarDetalhesCompletosFormaPagamento(fp);
                }
            }
            List<FormaPagamento> lista = ctx.roteador().pagamentos().listarFormasPagamentoAtivas();
            return br.com.senac.integracao.ia.ferramentas.ResolvedorPagamento.formatarListaFormasPagamento(lista);
        }

        if (entidade.startsWith("PAGAMENTO")) {
            Long id = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
            Long idNotaRef = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_nota", ExtratorAtributos.obter(attrs, "nota", null)));
            Long idNotaAlvo = idNotaRef != null ? idNotaRef : (id != null ? id : (foco != null ? foco.getIdUltimaNota() : null));
            if (idNotaAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                        "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
            }
            Nota notaFoco = ctx.roteador().notas().buscarNotaPorId(idNotaAlvo);
            if (notaFoco == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota #%s não encontrada.", idNotaAlvo);
            }
            RetornoChatBotUI.RetornoPagamentosDaNota recPags = RetornoChatBotUI.construirRecordPagamentosDaNota(notaFoco, ctx.roteador());
            if (foco != null) {
                foco.registrarRecord(recPags);
            }
            return recPags.formatarTexto();
        }

        return "Consulta de pagamento concluída.";
    }
}
