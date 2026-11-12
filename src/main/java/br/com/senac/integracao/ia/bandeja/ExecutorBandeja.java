package br.com.senac.integracao.ia.bandeja;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.bandeja.PassoExecutado.Resultado;
import br.com.senac.integracao.ia.validacao.RegistroValidadores;
import br.com.senac.integracao.ia.validacao.ResultadoValidacao;
import br.com.senac.integracao.ia.validacao.ValidadorExecucao;

import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Executa as instruções da {@link BandejaOperacoesContexto} <b>uma por uma</b>, conferindo o banco
 * antes e depois de cada uma (via {@link ValidadorExecucao}) e permitindo percorrer a bandeja
 * (próxima, pular, retomar, ir para).
 *
 * <p>Somente instruções de escrita ({@code C=}, {@code U=}, {@code D=}) existem na bandeja.
 * As consultas ({@code R=}) nunca entram nela: são usadas aqui, por baixo, para conferir os dados.
 *
 * <p>Tudo roda em sequência e na thread do chamador.
 */
public class ExecutorBandeja {

    private final RoteadorController roteador;
    private final RegistroValidadores validadores;

    public ExecutorBandeja(RoteadorController roteador) {
        this(roteador, RegistroValidadores.padrao());
    }

    public ExecutorBandeja(RoteadorController roteador, RegistroValidadores validadores) {
        this.roteador = roteador;
        this.validadores = validadores;
    }

    // ==================== EXECUÇÃO ====================

    /** Executa a próxima instrução pronta, a partir do cursor, percorrendo a bandeja de forma circular. */
    public PassoExecutado executarProximo(BandejaOperacoesContexto bandeja) {
        ItemBandeja proximo = bandeja.proximaExecutavel();
        if (proximo == null) {
            return semAlvo(Resultado.NADA_A_EXECUTAR, bandeja);
        }
        bandeja.irPara(proximo.getIdItem());
        return executarItem(bandeja, proximo);
    }

    /** Executa uma instrução específica pelo código (OP-2) ou posição (2). */
    public PassoExecutado executarPorId(BandejaOperacoesContexto bandeja, String idOuIndice) {
        ItemBandeja item = bandeja.buscarItemPorIdOuIndice(idOuIndice);
        if (item == null) {
            return semAlvo(Resultado.NAO_ENCONTRADA, bandeja);
        }
        return executarItem(bandeja, item);
    }

    /**
     * Executa todas as instruções prontas, em ordem. Para na primeira que não puder ser executada
     * (uma instrução pode depender da anterior, ex.: criar produto e depois montar o conjunto).
     */
    public List<PassoExecutado> executarTodos(BandejaOperacoesContexto bandeja) {
        List<PassoExecutado> passos = new ArrayList<>();
        for (ItemBandeja item : new ArrayList<>(bandeja.getItensValidados())) {
            PassoExecutado passo = executarItem(bandeja, item);
            passos.add(passo);
            if (!passo.executou()) {
                break;
            }
        }
        return passos;
    }

    public PassoExecutado executarItem(BandejaOperacoesContexto bandeja, ItemBandeja item) {
        if (item.getStatus() == ItemBandeja.StatusItem.EXECUTADO_SUCESSO) {
            return passo(Resultado.JA_EXECUTADA, item, "", "", null, null, bandeja);
        }
        if (item.isPendente()) {
            return passo(Resultado.FALTAM_DADOS, item, "", "",
                    "Falta informar: " + item.getCamposFaltantes(), null, bandeja);
        }
        // Executar de forma explícita uma instrução pulada ou que falhou = tentar de novo.
        item.retomar();

        // Resolve referências a itens anteriores executados no mesmo lote (ex.: idProdutoKit = "OP-1")
        resolverReferenciasDinamicas(bandeja, item);

        Optional<ValidadorExecucao> validador = validadores.para(item.getRota());
        Map<String, Object> estadoAntes = validador
                .map(v -> v.capturarEstadoAntes(item, roteador)).orElse(Collections.emptyMap());

        ResultadoValidacao antes = validador
                .map(v -> v.antes(item, roteador)).orElse(ResultadoValidacao.naoSeAplica());
        if (!antes.ok()) {
            item.setMensagemErro(antes.mensagem());
            return passo(Resultado.NAO_EXECUTADO_CONFERENCIA, item, antes.mensagem(), "", null, null, bandeja);
        }

        bandeja.liberarBloqueioParaExecucao(item);
        try {
            RespostaEndpoint<Object> resp = roteador.executarEndpoint(item.getRota(), item.getParametros());
            if (!resp.sucesso()) {
                item.setMensagemErro(resp.mensagem());
                return passo(Resultado.FALHA, item, antes.mensagem(), "", resp.mensagem(), null, bandeja);
            }
            item.setResultadoExecucao(resp.dados());

            ResultadoValidacao depois = validador
                    .map(v -> v.depois(item, resp.dados(), estadoAntes, roteador))
                    .orElse(ResultadoValidacao.naoSeAplica());
            return passo(depois.ok() ? Resultado.EXECUTADO : Resultado.EXECUTADO_COM_ALERTA,
                    item, antes.mensagem(), depois.mensagem(), null, resp.dados(), bandeja);
        } catch (Exception e) {
            item.setMensagemErro(e.getMessage());
            return passo(Resultado.FALHA, item, antes.mensagem(), "", e.getMessage(), null, bandeja);
        } finally {
            bandeja.reaplicarBloqueiosEmAberto();
        }
    }

    // ==================== NAVEGAÇÃO ====================

    /** Deixa a instrução para depois. Ela continua na bandeja e pode ser retomada. */
    public ItemBandeja pular(BandejaOperacoesContexto bandeja, String idOuIndice) {
        ItemBandeja item = idOuIndice == null || idOuIndice.isBlank()
                ? bandeja.itemSobCursor() : bandeja.buscarItemPorIdOuIndice(idOuIndice);
        if (item != null) {
            item.pular();
            bandeja.moverProximo();
        }
        return item;
    }

    /** Volta uma instrução pulada (ou que falhou) para a fila. */
    public ItemBandeja retomar(BandejaOperacoesContexto bandeja, String idOuIndice) {
        ItemBandeja item = bandeja.buscarItemPorIdOuIndice(idOuIndice);
        if (item != null) {
            item.retomar();
            bandeja.irPara(item.getIdItem());
        }
        return item;
    }

    // ==================== APOIO ====================

    private PassoExecutado passo(Resultado r, ItemBandeja item, String antes, String depois,
                                 String mensagem, Object dados, BandejaOperacoesContexto bandeja) {
        return new PassoExecutado(r, item.getIdItem(), item.getDescricaoAmigavel(), antes, depois,
                mensagem, dados, item.getParametros(), bandeja.itensEmAberto().size());
    }

    private PassoExecutado semAlvo(Resultado r, BandejaOperacoesContexto bandeja) {
        return new PassoExecutado(r, null, null, "", "", null, null,
                Collections.emptyMap(), bandeja.itensEmAberto().size());
    }

    private void resolverReferenciasDinamicas(BandejaOperacoesContexto bandeja, ItemBandeja item) {
        Map<String, Object> p = item.getParametrosModificaveis();
        for (Map.Entry<String, Object> entry : new ArrayList<>(p.entrySet())) {
            Object valor = entry.getValue();
            if (valor instanceof String str && str.trim().toUpperCase(Locale.ROOT).startsWith("OP-")) {
                ItemBandeja ref = bandeja.buscarItemPorIdOuIndice(str.trim());
                if (ref != null && ref.getResultadoExecucao() != null) {
                    Long idResolvido = extrairIdResultado(ref.getResultadoExecucao());
                    if (idResolvido != null) {
                        entry.setValue(idResolvido);
                    }
                }
            }
        }
    }

    private static Long extrairIdResultado(Object res) {
        if (res instanceof Produto prod) return prod.getId();
        if (res instanceof Nota nota) return nota.getId();
        if (res instanceof Pessoa pes) return pes.getId();
        if (res != null) {
            try {
                var m = res.getClass().getMethod("getId");
                Object id = m.invoke(res);
                if (id instanceof Long l) return l;
                if (id instanceof Number n) return n.longValue();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
