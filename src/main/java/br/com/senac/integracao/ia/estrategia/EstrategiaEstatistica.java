package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.parser.NoInstrucao;

import java.time.LocalDate;
import java.util.*;

/**
 * Estratégia de compilação e consulta de Estatísticas de Demanda, Relatórios e Fatores Externos
 * (Clima, Nível dos Rios, Eventos Culturais, Calendário Comercial).
 */
public class EstrategiaEstatistica implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of(
            "ESTATISTICA", "RELATORIO", "FATOREXTERNO", "VINCULOFATOREXTERNONOTA"
    );

    private final br.com.senac.integracao.facade.ConsultaIntegracaoFacade integracaoFacade =
            new br.com.senac.integracao.facade.ConsultaIntegracaoFacade();

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String tp = ExtratorAtributos.obter(attrs, "tp", "").toUpperCase(Locale.ROOT);

        if ("RECALCULAR".equals(tp) || "FATORES".equals(tp)) {
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.ESTATISTICA_RECALCULAR_FATORES,
                    Map.of(),
                    "Recalcular correlações e significância estatística dos fatores externos");
            return String.format("[%s] Preparei o recálculo dos fatores externos e lift estatístico.", item.getIdItem());
        }

        ItemBandeja item = bandeja.adicionarOuAtualizar(
                RotaSistema.FATOR_EXTERNO_RESOLVER_PENDENCIAS,
                Map.of(),
                "Resolver fila de notas pendentes com fatores externos");
        return String.format("[%s] Preparei a resolução de fatores externos pendentes das notas.", item.getIdItem());
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        ItemBandeja item = bandeja.adicionarOuAtualizar(
                RotaSistema.ESTATISTICA_RECALCULAR_FATORES,
                Map.of(),
                "Recalcular métricas estatísticas e impacto de fatores externos");
        return String.format("[%s] Preparei o recálculo das estatísticas de fatores externos.", item.getIdItem());
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        return "Estatísticas são métricas analíticas e não suportam exclusão direta.";
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String tp = ExtratorAtributos.obter(attrs, "tp", "").toUpperCase(Locale.ROOT);
        Long id = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        Long idNotaRef = ExtratorAtributos.parseLongSafe(ExtratorAtributos.obter(attrs, "id_nota", ExtratorAtributos.obter(attrs, "nota", null)));

        if ("VINCULOFATOREXTERNONOTA".equals(entidade) || "RELATORIO".equals(entidade)) {
            Long idAlvo = id != null ? id : (idNotaRef != null ? idNotaRef : (ctx.foco() != null ? ctx.foco().getIdUltimaNota() : null));
            if (idAlvo != null) {
                var res = ctx.roteador().executarEndpoint(RotaSistema.ESTATISTICA_CAUSAS_EXTERNAS_NOTA, Map.of("idNota", idAlvo));
                return "Relatório de Fatores Externos da Nota #" + idAlvo + ":\n" + (res != null ? res.formatarSaidaInline() : "Nenhum fator associado.");
            }
            var rel = ctx.roteador().executarEndpoint(RotaSistema.FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS, Map.of());
            return "Relatório Integrado de Fatores Externos:\n" + (rel != null ? rel.formatarSaidaInline() : "Nenhum dado consolidado.");
        }

        if (tp.contains("DEMANDA") || (tp.contains("CLIENTE") && tp.contains("MES"))) {
            Long idCli = id != null ? id : (ctx.foco() != null ? ctx.foco().getIdUltimaPessoa() : 1L);
            var res = ctx.roteador().estatisticas().preverDemandaMensalCliente(idCli != null ? idCli : 1L, null, null);
            return "Previsão de Demanda Mensal (Cliente #" + idCli + "):\n" + (res != null ? res.toString() : "Sem histórico suficiente.");
        }

        if (tp.contains("PERFIL")) {
            Long idCli = id != null ? id : (ctx.foco() != null ? ctx.foco().getIdUltimaPessoa() : 1L);
            var res = ctx.roteador().estatisticas().analisarPerfilCliente(idCli != null ? idCli : 1L);
            return "Perfil de Consumo do Cliente #" + idCli + ":\n" + (res != null ? res.toString() : "Sem dados.");
        }

        if ("CLIMA".equals(tp)) {
            return integracaoFacade.consultarClima(LocalDate.now(), ExtratorAtributos.obter(attrs, "cidade", "Manaus"), ExtratorAtributos.obter(attrs, "uf", "AM"));
        }
        if ("RIO".equals(tp)) {
            return integracaoFacade.consultarNivelRio(LocalDate.now(), ExtratorAtributos.obter(attrs, "cidade", "Manaus"), ExtratorAtributos.obter(attrs, "uf", "AM"));
        }
        if ("EVENTO".equals(tp) || "EVENTOS".equals(tp)) {
            return integracaoFacade.consultarEventos(LocalDate.now(), ExtratorAtributos.obter(attrs, "cidade", "Manaus"), ExtratorAtributos.obter(attrs, "uf", "AM"));
        }
        if ("CALENDARIO".equals(tp)) {
            return integracaoFacade.consultarCalendario(LocalDate.now(), ExtratorAtributos.obter(attrs, "uf", "AM"));
        }

        if (tp.contains("IMPACTO") || tp.contains("RIO") || tp.contains("CLIMA") || "FATOREXTERNO".equals(entidade)) {
            Long idProd = id != null ? id : (ctx.foco() != null ? ctx.foco().getIdUltimoProduto() : null);
            var res = ctx.roteador().estatisticas().analisarImpactoExterno(idProd);
            return "Análise de Impacto Externo (Clima, Rios, Eventos):\n" + (res != null ? res.toString() : "Sem impacto registrado.");
        }

        if (tp.contains("RANKING") || tp.contains("FORNECEDOR")) {
            Long idProd = id != null ? id : (ctx.foco() != null ? ctx.foco().getIdUltimoProduto() : 1L);
            var res = ctx.roteador().estatisticas().rankingFornecedores(idProd != null ? idProd : 1L, null, null);
            return "Ranking de Fornecedores do Produto #" + idProd + ":\n" + (res != null ? res.toString() : "Sem histórico.");
        }

        if (tp.contains("PERDA")) {
            var res = ctx.roteador().estatisticas().diagnosticarPerdaPrematura(id, null);
            return "Diagnóstico de Motivos de Perda Prematura:\n" + (res != null ? res.toString() : "Nenhuma anomalia detectada.");
        }

        if (tp.contains("SAZONAL") || tp.contains("DESTAQUE")) {
            var res = ctx.roteador().estatisticas().destaquesSazonais(25);
            return "Destaques Sazonais por Evento/Clima:\n" + (res != null ? res.toString() : "Nenhum item em destaque.");
        }

        if (tp.contains("PICO") || tp.contains("EVENTO")) {
            var res = ctx.roteador().estatisticas().picosDiasEventos();
            return "Picos de Venda em Dias de Eventos e 5º Dia Útil:\n" + (res != null ? res.toString() : "Sem histórico de picos.");
        }

        if (tp.contains("PLANO") || tp.contains("COMPRA")) {
            var res = ctx.roteador().estatisticas().planoEstrategico(LocalDate.now(), 30);
            return "Plano Estratégico de Compras:\n" + (res != null ? res.toString() : "Sem dados.");
        }

        var geral = ctx.roteador().estatisticas().analisarImpactoExterno(id);
        return "Resumo de Estatísticas e Fatores Externos:\n" + (geral != null ? geral.toString() : "Dados atualizados.");
    }
}
