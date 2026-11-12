package br.com.senac.integracao.ia;

import br.com.senac.controller.RotaSistema;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Representa uma instrução operacional na Bandeja de Contexto do usuário.
 * Armazena a rota, os parâmetros conhecidos, os campos faltantes e o estado de validação/execução.
 */
public class ItemBandeja {

    public enum StatusItem {
        PENDENTE_DADOS,
        VALIDADO,
        PULADO,
        EXECUTADO_SUCESSO,
        FALHA_EXECUCAO
    }

    private final String idItem;
    private final RotaSistema rota;
    private final Map<String, Object> parametros;
    private final LocalDateTime dataCriacao;
    private List<String> camposFaltantes;
    private StatusItem status;
    private String descricaoAmigavel;
    private Object resultadoExecucao;
    private String mensagemErro;

    public ItemBandeja(String idItem, RotaSistema rota, Map<String, Object> parametrosIniciais, List<String> camposFaltantes, String descricaoAmigavel) {
        this.idItem = idItem;
        this.rota = rota;
        this.parametros = new LinkedHashMap<>();
        if (parametrosIniciais != null) {
            this.parametros.putAll(parametrosIniciais);
        }
        this.dataCriacao = LocalDateTime.now();
        this.camposFaltantes = camposFaltantes != null ? new ArrayList<>(camposFaltantes) : new ArrayList<>();
        this.status = this.camposFaltantes.isEmpty() ? StatusItem.VALIDADO : StatusItem.PENDENTE_DADOS;
        this.descricaoAmigavel = descricaoAmigavel != null ? descricaoAmigavel : (rota != null ? rota.getDescricaoOperacao() : "Operação");
    }

    public String getIdItem() {
        return idItem;
    }

    public RotaSistema getRota() {
        return rota;
    }

    public Map<String, Object> getParametros() {
        return Collections.unmodifiableMap(parametros);
    }

    public Map<String, Object> getParametrosModificaveis() {
        return parametros;
    }

    public List<String> getCamposFaltantes() {
        return Collections.unmodifiableList(camposFaltantes);
    }

    public StatusItem getStatus() {
        return status;
    }

    public void setStatus(StatusItem status) {
        this.status = status;
    }

    public String getDescricaoAmigavel() {
        return descricaoAmigavel;
    }

    public void setDescricaoAmigavel(String descricaoAmigavel) {
        this.descricaoAmigavel = descricaoAmigavel;
    }

    public Object getResultadoExecucao() {
        return resultadoExecucao;
    }

    public void setResultadoExecucao(Object resultadoExecucao) {
        this.resultadoExecucao = resultadoExecucao;
        this.status = StatusItem.EXECUTADO_SUCESSO;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public void setMensagemErro(String mensagemErro) {
        this.mensagemErro = mensagemErro;
        this.status = StatusItem.FALHA_EXECUCAO;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public boolean isValido() {
        return status == StatusItem.VALIDADO;
    }

    public boolean isPendente() {
        return status == StatusItem.PENDENTE_DADOS;
    }

    public boolean isConcluido() {
        return status == StatusItem.EXECUTADO_SUCESSO || status == StatusItem.FALHA_EXECUCAO;
    }

    public boolean isPulado() {
        return status == StatusItem.PULADO;
    }

    /** Marca o item como pulado: ele fica na bandeja, mas não é executado até ser retomado. */
    public void pular() {
        if (status == StatusItem.VALIDADO) {
            status = StatusItem.PULADO;
        }
    }

    /** Volta um item pulado (ou que falhou) para a fila de execução, se não faltar nenhum dado. */
    public void retomar() {
        if ((status == StatusItem.PULADO || status == StatusItem.FALHA_EXECUCAO) && camposFaltantes.isEmpty()) {
            status = StatusItem.VALIDADO;
            mensagemErro = null;
        }
    }

    public void mesclarParametros(Map<String, Object> novosParams, List<String> novosFaltantes) {
        if (novosParams != null) {
            novosParams.forEach((k, v) -> {
                if (v != null && !(v instanceof String s && s.isBlank())) {
                    this.parametros.put(k, v);
                }
            });
        }
        this.camposFaltantes = novosFaltantes != null ? new ArrayList<>(novosFaltantes) : new ArrayList<>();
        this.status = this.camposFaltantes.isEmpty() ? StatusItem.VALIDADO : StatusItem.PENDENTE_DADOS;
    }
}
