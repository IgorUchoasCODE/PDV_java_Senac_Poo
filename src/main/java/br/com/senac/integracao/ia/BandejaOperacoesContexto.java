package br.com.senac.integracao.ia;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.GeminiToolRegistry;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bandeja de Operações e Contexto do Usuário (Sessão).
 * Acumula instruções operacionais validadas ou com dados parciais ao longo do diálogo,
 * permitindo confirmação e execução conjunta em lote (batch) de todas as operações validadas,
 * fechando a janela de contexto limpa após a execução ou descarte.
 */
public class BandejaOperacoesContexto {

    private final String idSessao;
    private final List<ItemBandeja> itens = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequencial = new AtomicInteger(1);
    private final LocalDateTime dataAberturaJanela;
    private volatile boolean janelaAberta;

    public BandejaOperacoesContexto(String idSessao) {
        this.idSessao = idSessao != null ? idSessao : "default";
        this.dataAberturaJanela = LocalDateTime.now();
        this.janelaAberta = true;
    }

    public String getIdSessao() {
        return idSessao;
    }

    public LocalDateTime getDataAberturaJanela() {
        return dataAberturaJanela;
    }

    public boolean isJanelaAberta() {
        return janelaAberta;
    }

    public List<ItemBandeja> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public List<ItemBandeja> getItensValidados() {
        return itens.stream().filter(ItemBandeja::isValido).toList();
    }

    public List<ItemBandeja> getItensPendentes() {
        return itens.stream().filter(ItemBandeja::isPendente).toList();
    }

    public boolean temItens() {
        return !itens.isEmpty();
    }

    public boolean temItensValidados() {
        return itens.stream().anyMatch(ItemBandeja::isValido);
    }

    public boolean temItensPendentes() {
        return itens.stream().anyMatch(ItemBandeja::isPendente);
    }

    public int totalItens() {
        return itens.size();
    }

    /**
     * Adiciona uma nova instrução ou atualiza uma instrução pendente compatível na bandeja.
     */
    public synchronized ItemBandeja adicionarOuAtualizar(RotaSistema rota, Map<String, Object> params, String descricaoAmigavel) {
        // Procura se já existe um item pendente da mesma rota que possa ser complementado
        ItemBandeja pendenteCompativel = itens.stream()
                .filter(i -> i.isPendente() && i.getRota() == rota)
                .findFirst()
                .orElse(null);

        Map<String, Object> paramsMesclados = new LinkedHashMap<>();
        if (pendenteCompativel != null) {
            paramsMesclados.putAll(pendenteCompativel.getParametros());
        }
        if (params != null) {
            paramsMesclados.putAll(params);
        }

        List<String> faltantes = ManualInstrucoesIA.obrigatoriosFaltando(rota, paramsMesclados);

        if (pendenteCompativel != null) {
            pendenteCompativel.mesclarParametros(params, faltantes);
            if (descricaoAmigavel != null && !descricaoAmigavel.isBlank()) {
                pendenteCompativel.setDescricaoAmigavel(descricaoAmigavel);
            }
            return pendenteCompativel;
        }

        String id = "OP-" + sequencial.getAndIncrement();
        ItemBandeja novo = new ItemBandeja(id, rota, params, faltantes, descricaoAmigavel);
        itens.add(novo);
        return novo;
    }

    /**
     * Tenta aplicar novos dados em um item que esteja pendente de parâmetros.
     */
    public synchronized boolean complementarItemPendente(Map<String, Object> dadosNovos) {
        ItemBandeja pendente = itens.stream().filter(ItemBandeja::isPendente).findFirst().orElse(null);
        if (pendente == null || dadosNovos == null || dadosNovos.isEmpty()) {
            return false;
        }
        Map<String, Object> paramsMesclados = new LinkedHashMap<>(pendente.getParametros());
        paramsMesclados.putAll(dadosNovos);
        List<String> faltantes = ManualInstrucoesIA.obrigatoriosFaltando(pendente.getRota(), paramsMesclados);
        pendente.mesclarParametros(dadosNovos, faltantes);
        return true;
    }

    /**
     * Executa todas as operações com status VALIDADO na bandeja através do RoteadorController.
     */
    public synchronized RelatorioExecucaoBandeja executarTodasValidadas(RoteadorController roteador) {
        List<ItemBandeja> validados = getItensValidados();
        int sucessos = 0;
        int falhas = 0;
        List<RelatorioExecucaoBandeja.ItemExecutado> detalhes = new ArrayList<>();

        for (ItemBandeja item : validados) {
            try {
                RespostaEndpoint<Object> resp = roteador.executarEndpoint(item.getRota(), item.getParametros());
                if (resp.sucesso()) {
                    sucessos++;
                    item.setResultadoExecucao(resp.dados());
                    detalhes.add(new RelatorioExecucaoBandeja.ItemExecutado(
                            item.getIdItem(),
                            item.getRota().name(),
                            item.getDescricaoAmigavel(),
                            true,
                            resp.mensagem() != null ? resp.mensagem() : "Executado com sucesso.",
                            resp.dados()
                    ));
                } else {
                    falhas++;
                    item.setMensagemErro(resp.mensagem());
                    detalhes.add(new RelatorioExecucaoBandeja.ItemExecutado(
                            item.getIdItem(),
                            item.getRota().name(),
                            item.getDescricaoAmigavel(),
                            false,
                            resp.mensagem() != null ? resp.mensagem() : "Falha na execução.",
                            null
                    ));
                }
            } catch (Exception e) {
                falhas++;
                item.setMensagemErro(e.getMessage());
                detalhes.add(new RelatorioExecucaoBandeja.ItemExecutado(
                        item.getIdItem(),
                        item.getRota().name(),
                        item.getDescricaoAmigavel(),
                        false,
                        "Erro inesperado: " + e.getMessage(),
                        null
                ));
            }
        }

        return new RelatorioExecucaoBandeja(validados.size(), sucessos, falhas, detalhes);
    }

    /**
     * Fecha e limpa a janela de contexto.
     */
    public synchronized void fecharJanela() {
        itens.clear();
        janelaAberta = false;
    }

    /**
     * Formata visualmente o conteúdo da bandeja para exibição no console/UI.
     */
    public String formatarVisualizacao() {
        if (itens.isEmpty()) {
            return "📥 **Bandeja de Contexto:** Vazia (nenhuma instrução pendente).";
        }

        StringBuilder sb = new StringBuilder();
        int total = itens.size();
        long validados = itens.stream().filter(ItemBandeja::isValido).count();
        long pendentes = itens.stream().filter(ItemBandeja::isPendente).count();

        sb.append(String.format("📥 **BANDEJA DE CONTEXTO ATIVA** (%d instrução%s: %d validada%s, %d pendente%s):\n",
                total, total > 1 ? "ões" : "",
                validados, validados > 1 ? "s" : "",
                pendentes, pendentes > 1 ? "s" : ""));
        sb.append("──────────────────────────────────────────────────\n");

        for (int i = 0; i < itens.size(); i++) {
            ItemBandeja item = itens.get(i);
            String icone = switch (item.getStatus()) {
                case VALIDADO -> "✅ [VALIDADO]";
                case PENDENTE_DADOS -> "⏳ [PENDENTE DADOS]";
                case EXECUTADO_SUCESSO -> "✔ [EXECUTADO]";
                case FALHA_EXECUCAO -> "❌ [ERRO]";
            };

            sb.append(String.format("• **[%d] %s** %s\n", (i + 1), icone, item.getDescricaoAmigavel()));
            sb.append("    🎯 Rota: `").append(item.getRota().name()).append("`");
            if (!item.getParametros().isEmpty()) {
                sb.append(" | Parâmetros: ").append(item.getParametros());
            }
            sb.append("\n");

            if (item.isPendente() && !item.getCamposFaltantes().isEmpty()) {
                sb.append("    ⚠️ **Faltam preencher:** ").append(item.getCamposFaltantes()).append("\n");
            }
        }
        sb.append("──────────────────────────────────────────────────\n");

        boolean temDestrutivo = itens.stream().anyMatch(i -> GeminiToolRegistry.classificarRisco(i.getRota()) == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO);

        if (pendentes > 0) {
            sb.append("⚠️ **Ação necessária:** Existem instruções pendentes de dados. Por favor, forneça as informações faltantes acima para concluir a validação.\n");
        } else if (validados > 0) {
            if (temDestrutivo) {
                sb.append("⚠️ **Confirmação Necessária (Exclusão Física / Ação Destrutiva):**\n");
            }
            sb.append("👉 **Deseja confirmar e executar TODAS as ")
              .append(validados)
              .append(" instruções validadas da bandeja?**\n")
              .append("Responda **'SIM'** para executar todas ou **'NÃO'** para descartar a bandeja.\n\n")
              .append("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]");
        }

        return sb.toString().trim();
    }
}
