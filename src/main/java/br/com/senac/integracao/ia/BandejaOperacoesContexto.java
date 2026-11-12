package br.com.senac.integracao.ia;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.GeminiToolRegistry;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

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
        registrarBloqueioRecursoNaBandeja(rota, novo.getParametros());
        return novo;
    }

    /**
     * Bloqueia preventivamente a informação/recurso (Produto, Pessoa, Nota, Lote, etc.) enquanto estiver na Bandeja de Contexto da IA,
     * impedindo mutações concorrentes conflitantes e protegendo a integridade dos dados.
     */
    private void registrarBloqueioRecursoNaBandeja(RotaSistema rota, Map<String, Object> params) {
        String chave = chaveBloqueio(rota, params);
        if (chave != null) {
            PadraoDAO.BLOQUEIOS_BANDEJA_IA.put(chave, new PadraoDAO.BloqueioBandejaInfo(
                    chave,
                    idSessao,
                    "Retido na Bandeja de Contexto da IA (" + rota.name() + ")",
                    LocalDateTime.now().plusMinutes(10)
            ));
        }
    }

    /** Monta a chave do recurso (ex.: {@code ProdutoDAO:10}) ou {@code null} se a instrução não aponta para um registro. */
    private String chaveBloqueio(RotaSistema rota, Map<String, Object> params) {
        if (rota == null || params == null || params.isEmpty()) return null;
        Object idAlvo = params.get("idProduto");
        String prefixo = "ProdutoDAO";
        if (idAlvo == null && params.containsKey("idPessoa")) {
            idAlvo = params.get("idPessoa");
            prefixo = "PessoaDAO";
        } else if (idAlvo == null && params.containsKey("idNota")) {
            idAlvo = params.get("idNota");
            prefixo = "NotaDAO";
        } else if (idAlvo == null && params.containsKey("idImagem")) {
            idAlvo = params.get("idImagem");
            prefixo = "ProdutoImagemDAO";
        }
        return idAlvo != null ? prefixo + ":" + idAlvo : null;
    }

    /**
     * Solta a trava do registro deste item para que a execução dele não seja barrada pela própria bandeja.
     * Usado na execução passo a passo; as travas dos itens que seguem abertos são reaplicadas depois
     * por {@link #reaplicarBloqueiosEmAberto()}.
     */
    public synchronized void liberarBloqueioParaExecucao(ItemBandeja item) {
        if (item == null) return;
        String chave = chaveBloqueio(item.getRota(), item.getParametros());
        if (chave != null) {
            PadraoDAO.BLOQUEIOS_BANDEJA_IA.remove(chave);
        }
    }

    /** Recoloca a trava de todos os itens que ainda não foram concluídos. */
    public synchronized void reaplicarBloqueiosEmAberto() {
        for (ItemBandeja it : itens) {
            if (!it.isConcluido()) {
                registrarBloqueioRecursoNaBandeja(it.getRota(), it.getParametros());
            }
        }
    }

    // ==================== NAVEGAÇÃO (cursor) ====================

    private volatile int cursor = 0;

    /** Posição atual (0-based) do cursor de navegação. */
    public int getCursor() {
        return Math.max(0, Math.min(cursor, Math.max(0, itens.size() - 1)));
    }

    /** Item apontado pelo cursor, ou {@code null} se a bandeja estiver vazia. */
    public synchronized ItemBandeja itemSobCursor() {
        return itens.isEmpty() ? null : itens.get(getCursor());
    }

    /** Move o cursor para a instrução seguinte (circular) e a devolve. */
    public synchronized ItemBandeja moverProximo() {
        if (itens.isEmpty()) return null;
        cursor = (getCursor() + 1) % itens.size();
        return itens.get(cursor);
    }

    /** Move o cursor para a instrução anterior (circular) e a devolve. */
    public synchronized ItemBandeja moverAnterior() {
        if (itens.isEmpty()) return null;
        cursor = (getCursor() - 1 + itens.size()) % itens.size();
        return itens.get(cursor);
    }

    /** Posiciona o cursor em uma instrução pelo ID (OP-2) ou índice (2). Devolve o item ou {@code null}. */
    public synchronized ItemBandeja irPara(String idOuIndice) {
        ItemBandeja alvo = buscarItemPorIdOuIndice(idOuIndice);
        if (alvo != null) {
            cursor = itens.indexOf(alvo);
        }
        return alvo;
    }

    /**
     * Próxima instrução pronta para executar, partindo do cursor e percorrendo a bandeja
     * de forma circular (ignora pendentes, puladas e já executadas).
     */
    public synchronized ItemBandeja proximaExecutavel() {
        int n = itens.size();
        for (int i = 0; i < n; i++) {
            ItemBandeja it = itens.get((getCursor() + i) % n);
            if (it.isValido()) {
                return it;
            }
        }
        return null;
    }

    /**
     * Instruções que ainda dependem de ação do usuário: prontas, com dados faltando, puladas
     * ou que falharam (podem ser corrigidas e executadas de novo).
     */
    public synchronized List<ItemBandeja> itensEmAberto() {
        return itens.stream()
                .filter(i -> i.isValido() || i.isPendente() || i.isPulado()
                        || i.getStatus() == ItemBandeja.StatusItem.FALHA_EXECUCAO)
                .toList();
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
        PadraoDAO.desbloquearTodosDaSessao(idSessao);
        List<ItemBandeja> validados = getItensValidados();
        int sucessos = 0;
        int falhas = 0;
        List<RelatorioExecucaoBandeja.ItemExecutado> detalhes = new ArrayList<>();
        Map<String, Long> idsCriadosPorItem = new LinkedHashMap<>();

        for (ItemBandeja item : validados) {
            try {
                // Resolve referências a itens anteriores executados no mesmo lote (ex.: idProdutoKit = "OP-1")
                resolverReferenciasDinamicas(item, idsCriadosPorItem);

                RespostaEndpoint<Object> resp = roteador.executarEndpoint(item.getRota(), item.getParametros());
                if (resp.sucesso()) {
                    sucessos++;
                    item.setResultadoExecucao(resp.dados());
                    Long idGerado = extrairIdResultado(resp.dados());
                    if (idGerado != null) {
                        idsCriadosPorItem.put(item.getIdItem().toUpperCase(Locale.ROOT), idGerado);
                    }
                    detalhes.add(new RelatorioExecucaoBandeja.ItemExecutado(
                            item.getIdItem(),
                            item.getRota().name(),
                            item.getDescricaoAmigavel(),
                            true,
                            resp.mensagem() != null ? resp.mensagem() : "Executado com sucesso.",
                            resp.dados(),
                            item.getParametros()
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
                            null,
                            item.getParametros()
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
                        null,
                        item.getParametros()
                ));
            }
        }

        return new RelatorioExecucaoBandeja(validados.size(), sucessos, falhas, detalhes);
    }

    /**
     * Fecha e limpa a janela de contexto.
     */
    public synchronized void fecharJanela() {
        PadraoDAO.desbloquearTodosDaSessao(idSessao);
        itens.clear();
        janelaAberta = false;
    }

    /**
     * Busca uma instrução em memória de execução pelo ID (ex: "OP-1") ou pelo índice numérico 1-based (ex: "1").
     * Se idOuIndice for vazio/nulo, retorna a última instrução adicionada.
     */
    public synchronized ItemBandeja buscarItemPorIdOuIndice(String idOuIndice) {
        if (itens.isEmpty()) return null;
        if (idOuIndice == null || idOuIndice.isBlank()) {
            return itens.get(itens.size() - 1);
        }
        String chave = idOuIndice.trim().toUpperCase(Locale.ROOT);
        for (ItemBandeja item : itens) {
            if (item.getIdItem().equalsIgnoreCase(chave)) {
                return item;
            }
        }
        String apenasDigitos = chave.replace("OP-", "").replace("#", "").trim();
        try {
            int idx = Integer.parseInt(apenasDigitos);
            if (idx >= 1 && idx <= itens.size()) {
                return itens.get(idx - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /**
     * Remove uma instrução específica da bandeja pelo ID (ex: "OP-1" via close<OP-1>)
     * ou remove a última instrução caso nenhum ID seja especificado (close<>).
     */
    public synchronized ItemBandeja removerItemPorIdOuIndice(String idOuIndice) {
        ItemBandeja alvo = buscarItemPorIdOuIndice(idOuIndice);
        if (alvo != null) {
            itens.remove(alvo);
        }
        return alvo;
    }

    /**
     * Verifica se há um produto agendado para criação nesta mesma bandeja em memória de execução,
     * permitindo que instruções seguintes no mesmo bloco $$ referenciem o produto antes do commit no banco.
     */
    public synchronized boolean possuiProdutoEmEstagio(String nomeProduto) {
        if (nomeProduto == null || nomeProduto.isBlank()) return false;
        String alvo = nomeProduto.trim();
        for (ItemBandeja item : itens) {
            if (item.getRota() == RotaSistema.PRODUTO_FISICO_CADASTRAR
                    || item.getRota() == RotaSistema.PRODUTO_DIGITAL_CADASTRAR) {
                Object n = item.getParametros().get("nome");
                if (n != null && (n.toString().equalsIgnoreCase(alvo)
                        || br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(n.toString(), alvo) >= 400)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifica se há uma pessoa (cliente/fornecedor) agendada para criação nesta mesma bandeja em memória.
     */
    public synchronized boolean possuiPessoaEmEstagio(String nomePessoa) {
        if (nomePessoa == null || nomePessoa.isBlank()) return false;
        String alvo = nomePessoa.trim();
        for (ItemBandeja item : itens) {
            if (item.getRota() == RotaSistema.PESSOA_SIMPLES_CADASTRAR
                    || item.getRota() == RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR
                    || item.getRota() == RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR
                    || item.getRota() == RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR
                    || item.getRota() == RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR) {
                Object n = item.getParametros().get("nome");
                if (n == null) n = item.getParametros().get("razaoSocial");
                if (n != null && (n.toString().equalsIgnoreCase(alvo)
                        || br.com.senac.repository.dao.BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(n.toString(), alvo) >= 400)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Formata visualmente o conteúdo da bandeja para exibição no console/UI sem poluição de markdown.
     */
    public String formatarVisualizacao() {
        if (itens.isEmpty()) {
            return "Bandeja de Contexto vazia (nenhuma instrução pendente).";
        }

        StringBuilder sb = new StringBuilder();
        int total = itens.size();
        long validados = itens.stream().filter(ItemBandeja::isValido).count();
        long pendentes = itens.stream().filter(ItemBandeja::isPendente).count();

        sb.append(String.format("BANDEJA DE CONTEXTO ATIVA (%d instrução%s: %d validada%s, %d pendente%s):\n",
                total, total > 1 ? "ões" : "",
                validados, validados > 1 ? "s" : "",
                pendentes, pendentes > 1 ? "s" : ""));

        for (int i = 0; i < itens.size(); i++) {
            ItemBandeja item = itens.get(i);
            String statusTxt = switch (item.getStatus()) {
                case VALIDADO -> "PRONTA";
                case PENDENTE_DADOS -> "FALTAM DADOS";
                case PULADO -> "PULADA";
                case EXECUTADO_SUCESSO -> "EXECUTADA";
                case FALHA_EXECUCAO -> "ERRO";
            };

            sb.append(String.format("  %d. [%s - %s] %s (%s)\n",
                    (i + 1), item.getIdItem(), statusTxt, item.getDescricaoAmigavel(), item.getRota().name()));

            if (item.isPendente() && !item.getCamposFaltantes().isEmpty()) {
                sb.append("     Faltam informar: ").append(item.getCamposFaltantes()).append("\n");
            }
        }

        if (pendentes > 0) {
            sb.append("\nInforme os dados que faltam acima para concluir.");
        } else if (validados > 0) {
            sb.append("\nVocê pode executar uma por vez: diga \"próxima\" ou \"executar OP-1\". ")
              .append("Para deixar uma para depois: \"pular OP-1\".\n")
              .append("Confirma a execução de todas? Digite SIM para salvar ou NÃO para cancelar.\n")
              .append("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]");
        }

        return sb.toString().trim();
    }

    private void resolverReferenciasDinamicas(ItemBandeja item, Map<String, Long> idsCriados) {
        if (idsCriados == null || idsCriados.isEmpty()) return;
        Map<String, Object> p = item.getParametrosModificaveis();
        for (Map.Entry<String, Object> entry : new ArrayList<>(p.entrySet())) {
            Object valor = entry.getValue();
            if (valor instanceof String str) {
                String chave = str.trim().toUpperCase(Locale.ROOT);
                if (idsCriados.containsKey(chave)) {
                    entry.setValue(idsCriados.get(chave));
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
