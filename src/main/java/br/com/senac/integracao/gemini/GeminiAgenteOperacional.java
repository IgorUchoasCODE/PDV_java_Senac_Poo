package br.com.senac.integracao.gemini;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.InterpretadorRespostaIA;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.integracao.ia.ProvedorIA;
import br.com.senac.integracao.ia.ProvedorIA.ChamadaFerramenta;
import br.com.senac.integracao.ia.ProvedorIA.PedidoIA;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;
import br.com.senac.integracao.ia.ProvedorIAFactory;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Agente Operacional do Sistema Sazonalisei.
 * Orquestra: Usuário -> ProvedorIA (qualquer IA) -> validação -> confirmação (Human-in-the-Loop)
 * -> concorrência segura (Virtual Threads) -> RoteadorController.
 *
 * Funciona de dois jeitos:
 *  1. Com provedor de API (Gemini API, Ollama/OpenAI-compatível): o sistema chama a IA sozinho.
 *  2. Sem API (ia.provedor=manual): o usuário copia o manual para qualquer IA (ex: Gemini do celular),
 *     e cola a resposta em {@link #processarRespostaExterna(String, String)}.
 */
public class GeminiAgenteOperacional {

    private final GeminiClientService geminiClient;
    private final RoteadorController roteadorController;
    private final ConfirmacaoAcaoGatekeeper gatekeeper;
    private final ConcorrenciaSeguraEngine concorrenciaEngine;
    private volatile ProvedorIA provedor;

    // Fila de tickets aguardando confirmação, por sessão (vários pedidos destrutivos na mesma frase)
    private final Map<String, Deque<String>> ticketsPendentesPorSessao = new ConcurrentHashMap<>();

    public GeminiAgenteOperacional() {
        this(new GeminiClientService(), new RoteadorController(), ConfirmacaoAcaoGatekeeper.getInstancia(), ConcorrenciaSeguraEngine.getInstancia());
    }

    public GeminiAgenteOperacional(
            GeminiClientService geminiClient,
            RoteadorController roteadorController,
            ConfirmacaoAcaoGatekeeper gatekeeper,
            ConcorrenciaSeguraEngine concorrenciaEngine) {
        this.geminiClient = geminiClient;
        this.roteadorController = roteadorController;
        this.gatekeeper = gatekeeper;
        this.concorrenciaEngine = concorrenciaEngine;
        this.provedor = ProvedorIAFactory.criar(geminiClient);
    }

    // ==================== ENTRADAS ====================

    /** Mensagem de texto do usuário, interpretada pelo provedor de IA configurado. */
    public String processarMensagem(String idSessao, String mensagemUsuario) {
        String msg = mensagemUsuario != null ? mensagemUsuario.trim() : "";

        if (temConfirmacaoPendente(idSessao)) {
            return processarRespostaConfirmacao(idSessao, msg);
        }
        String bloqueio = validarProvedor(false);
        if (bloqueio != null) return bloqueio;

        try {
            return processarRespostaIA(idSessao, provedor.conversar(new PedidoIA(msg, null, null)));
        } catch (Exception e) {
            return "❌ Erro ao processar solicitação com a IA: " + e.getMessage();
        }
    }

    /** Áudio de voz capturado pelo microfone do celular. */
    public String processarAudio(String idSessao, byte[] audioBytes, String mimeType) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return bloqueio;

        try {
            if (temConfirmacaoPendente(idSessao)) {
                RespostaIA r = provedor.conversar(new PedidoIA(
                        "O usuário está respondendo a uma pergunta de confirmação. Apenas transcreva, em poucas palavras, " +
                        "o que ele disse no áudio. NÃO execute nenhuma ferramenta.",
                        audioBytes, mimeType));
                return processarRespostaConfirmacao(idSessao, r.texto() != null ? r.texto() : "");
            }
            return processarRespostaIA(idSessao, provedor.conversar(new PedidoIA(
                    "Ouça este comando de voz em português. Identifique a intenção e selecione a ferramenta apropriada " +
                    "(consulta, cadastro, atualização ou exclusão).",
                    audioBytes, mimeType)));
        } catch (Exception e) {
            return "❌ Falha ao processar comando de voz: " + e.getMessage();
        }
    }

    /** Foto/documento capturado pela câmera do celular. */
    public String processarImagem(String idSessao, byte[] imagemBytes, String mimeType, String prompt) {
        String bloqueio = validarProvedor(true);
        if (bloqueio != null) return bloqueio;

        try {
            String p = prompt != null && !prompt.isBlank()
                    ? prompt
                    : "Analise esta foto ou documento e execute a operação correspondente no sistema de vendas.";
            return processarRespostaIA(idSessao, provedor.conversar(new PedidoIA(p, imagemBytes, mimeType)));
        } catch (Exception e) {
            return "❌ Falha ao processar imagem: " + e.getMessage();
        }
    }

    /**
     * PONTE SEM API: recebe o texto que uma IA externa (ex: Gemini do celular) devolveu seguindo o manual
     * e executa as ações com as mesmas validações e confirmações do modo com API.
     * Se houver confirmação pendente, uma resposta curta ("sim"/"não") é tratada como decisão.
     */
    public String processarRespostaExterna(String idSessao, String textoDaIA) {
        String texto = textoDaIA != null ? textoDaIA.trim() : "";

        if (temConfirmacaoPendente(idSessao)) {
            if (texto.contains("{") || texto.contains("[")) {
                return "⚠️ Ainda há uma confirmação pendente. Responda **SIM** ou **NÃO** antes de enviar novas ações.\n\n"
                        + perguntaAtual(idSessao);
            }
            return processarRespostaConfirmacao(idSessao, texto);
        }
        try {
            return processarRespostaIA(idSessao, InterpretadorRespostaIA.interpretar(texto));
        } catch (IllegalArgumentException e) {
            return "⚠️ " + e.getMessage();
        }
    }

    // ==================== NÚCLEO ====================

    private String validarProvedor(boolean exigeMidia) {
        if (provedor == null) {
            return "ℹ️ Modo **sem API** ativo. Toque em 🧩 **Sem API**, copie o manual para a sua IA (ex: Gemini do celular) " +
                    "e cole aqui a resposta dela para eu executar.";
        }
        if (exigeMidia && !provedor.suportaMidia()) {
            return "⚠️ O provedor atual (" + provedor.getDescricao() + ") não aceita áudio/imagem. Digite o comando em texto.";
        }
        return provedor.verificarPronto();
    }

    private String processarRespostaIA(String idSessao, RespostaIA resp) {
        if (!resp.temChamadas()) {
            return resp.texto() != null && !resp.texto().isBlank()
                    ? resp.texto()
                    : "Comando interpretado, nenhuma operação necessária.";
        }

        StringBuilder sb = new StringBuilder();
        if (resp.texto() != null && !resp.texto().isBlank()) {
            sb.append(resp.texto()).append("\n\n");
        }
        for (ChamadaFerramenta chamada : resp.chamadas()) {
            sb.append(despacharChamadaTool(idSessao, chamada)).append("\n");
        }
        return sb.toString().trim();
    }

    private String despacharChamadaTool(String idSessao, ChamadaFerramenta chamada) {
        Map<String, Object> params = chamada.parametros() != null ? chamada.parametros() : new LinkedHashMap<>();

        RotaSistema rota = roteadorController.resolverRota(chamada.rota());
        if (rota == null) {
            return "⚠️ A IA pediu uma operação que não existe no sistema: " + chamada.rota();
        }

        List<String> faltando = ManualInstrucoesIA.obrigatoriosFaltando(rota, params);
        if (!faltando.isEmpty()) {
            return "⚠️ Não consegui executar \"" + rota.getDescricaoOperacao() + "\": faltam os dados " + faltando
                    + ". Informe e peça novamente.";
        }

        GeminiToolRegistry.NivelRiscoOperacao risco = GeminiToolRegistry.classificarRisco(rota);
        String entendimento = descreverEntendimento(rota, params);
        String regra = switch (risco) {
            case DESTRUTIVO -> "⚠️ Governança Human-in-the-Loop ativa: Requer confirmação explícita do operador antes da exclusão física.";
            case WRITE_SEGURO -> "🔒 Execução sob Demanda: Ação de gravação/cadastro que exige confirmação prévia do operador antes de persistir no banco.";
            case READ_ONLY -> "⚡ Consulta concorrente livre em Virtual Thread (sem bloqueio de recursos).";
        };

        // AÇÕES DE ESCRITA / MUTAÇÃO (WRITE_SEGURO e DESTRUTIVO):
        // Retém em ticket de confirmação para que a execução só ocorra após a conclusão/aprovação do usuário
        if (risco != GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY) {
            String alvo = obterDetalheAlvoAmigavel(rota, params);
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.criarTicket(rota, params, alvo);
            Deque<String> fila = ticketsPendentesPorSessao.computeIfAbsent(idSessao, k -> new ConcurrentLinkedDeque<>());
            fila.addLast(ticket.ticketId());

            if (fila.size() == 1) {
                return montarPergunta(ticket);
            }
            return "⏳ Também aguardando sua confirmação (depois da atual): **" + rota.getDescricaoOperacao() + "** - " + alvo;
        }

        // APENAS CONSULTAS (READ_ONLY): Virtual Threads imediatas sem bloqueio
        try {
            String chaveRecurso = extrairChaveRecurso(params);
            RespostaEndpoint<Object> resultado = concorrenciaEngine.submeterTarefa(
                    () -> roteadorController.executarEndpoint(rota, params),
                    chaveRecurso
            ).get();

            StringBuilder sb = new StringBuilder();
            sb.append("🧠 **O que entendi:** ").append(entendimento).append("\n");
            sb.append("🎯 **Rota Identificada:** `").append(rota.name()).append("` (Módulo: ").append(rota.getModulo()).append(")\n");
            sb.append("📋 **Parâmetros:** ").append(formatarParametrosResumidos(params)).append("\n");
            sb.append("⚖️ **Regra do Sistema:** ").append(regra).append("\n\n");
            sb.append("────────────────────────────────────────\n");

            if (resultado.sucesso()) {
                sb.append(formatarResultadoAmigavel(rota, params, resultado.dados()));
            } else {
                sb.append(String.format("⚠️ Falha ao executar [%s]: %s", rota.getDescricaoOperacao(), resultado.mensagem()));
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ Erro interno ao executar operação: " + e.getMessage();
        }
    }

    private String descreverEntendimento(RotaSistema rota, Map<String, Object> params) {
        if (rota == null) return "Operação não identificada";
        return switch (rota) {
            case PRODUTO_CATALOGO_BUSCAR -> {
                if (params.containsKey("id")) yield "Buscar produto pelo ID #" + params.get("id");
                if (params.containsKey("nome")) yield "Buscar produto com nome semelhante a '" + params.get("nome") + "'";
                yield "Consultar catálogo de produtos";
            }
            case PRODUTO_CATALOGO_LISTAR -> "Listar todos os produtos disponíveis no catálogo";
            case PRODUTO_CATALOGO_ATUALIZAR -> "Atualizar dados cadastrais do produto #" + params.get("idProduto");
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> "Atualizar o preço de venda do produto #" + params.get("idProduto") + " para R$ " + params.get("novoPreco");
            case PRODUTO_CATALOGO_EXCLUIR -> "Excluir permanentemente o produto #" + params.get("idProduto") + " do catálogo";
            case PRODUTO_FISICO_CADASTRAR -> "Cadastrar novo produto físico '" + params.get("nome") + "' com suas dimensões e peso";
            case PRODUTO_DIGITAL_CADASTRAR -> "Cadastrar novo produto digital '" + params.get("nome") + "'";
            case PRODUTO_UNIDADE_CADASTRAR -> "Cadastrar nova unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_UNIDADE_ATUALIZAR -> "Atualizar dados da unidade de medida '" + params.get("sigla") + "'";
            case PRODUTO_UNIDADE_EXCLUIR -> "Excluir unidade de medida '" + params.get("sigla") + "'";
            case PESSOA_SIMPLES_CADASTRAR -> {
                String nomeP = params.get("nome") != null ? params.get("nome").toString() : (params.get("razaoSocial") != null ? params.get("razaoSocial").toString() : "");
                String papelP = params.get("papel") != null ? params.get("papel").toString() : (Boolean.parseBoolean(String.valueOf(params.get("isCliente"))) ? "cliente" : "fornecedor");
                yield "Cadastrar " + papelP.toLowerCase(java.util.Locale.ROOT) + " '" + nomeP + "' (sem exigir CNPJ/CPF)";
            }
            case PESSOA_LISTAR_CLIENTES -> "Listar clientes cadastrados na base de dados";
            case PESSOA_LISTAR_FORNECEDORES -> "Listar fornecedores cadastrados na base de dados";
            case PESSOA_BUSCAR -> "Buscar clientes ou fornecedores pelo termo '" + params.get("termo") + "'";
            case PESSOA_HISTORICO_COMPLETO -> "Consultar histórico 360º completo (notas, produtos e pagamentos) da pessoa";
            case PESSOA_CLIENTE_EXCLUIR -> "Excluir cadastro do cliente/pessoa #" + params.get("idPessoa");
            case PESSOA_CLIENTE_PF_CADASTRAR -> "Cadastrar cliente pessoa física '" + params.get("nome") + "'";
            case PESSOA_CLIENTE_PJ_CADASTRAR -> "Cadastrar cliente pessoa jurídica '" + params.get("razaoSocial") + "'";
            case PESSOA_FORNECEDOR_PJ_CADASTRAR -> "Cadastrar fornecedor pessoa jurídica '" + (params.get("razaoSocial") != null ? params.get("razaoSocial") : params.get("nome")) + "'";
            case PESSOA_FORNECEDOR_PF_CADASTRAR -> "Cadastrar fornecedor pessoa física '" + params.get("nome") + "'";
            case PESSOA_DADOS_ATUALIZAR -> "Atualizar dados cadastrais da pessoa #" + params.get("idPessoa");
            case PAGAMENTO_FORMA_CADASTRAR -> "Cadastrar nova forma de pagamento '" + params.get("codigo") + "'";
            case PAGAMENTO_FORMA_EXCLUIR -> "Excluir forma de pagamento '" + params.get("codigo") + "'";
            case ESTOQUE_SALDO_CONSOLIDADO -> "Consultar saldo consolidado do produto #" + params.get("idProduto");
            case ESTOQUE_FILA_LOTES_FIFO -> "Consultar lotes em fila FIFO para o produto #" + params.get("idProduto");
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> "Baixar perda operacional direta no lote #" + params.get("idLote");
            case NOTA_VENDA_EMITIR_FIFO -> "Emitir nota fiscal de venda com consumo FIFO de estoque";
            case NOTA_COMPRA_EMITIR -> "Emitir nota fiscal de compra e dar entrada no estoque";
            default -> rota.getDescricaoOperacao();
        };
    }

    private String formatarParametrosResumidos(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "Nenhum parâmetro (leitura global)";
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k).append("=").append(v);
        });
        return sb.toString();
    }

    // ==================== CONFIRMAÇÃO ====================

    private boolean temConfirmacaoPendente(String idSessao) {
        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        return fila != null && !fila.isEmpty();
    }

    private String processarRespostaConfirmacao(String idSessao, String msgUsuario) {
        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        String ticketId = fila.peekFirst();

        switch (DecisaoConfirmacao.interpretar(msgUsuario)) {
            case SIM: {
                fila.pollFirst();
                ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.buscarTicket(ticketId);
                RotaSistema rota = ticket != null ? ticket.rota() : null;
                Map<String, Object> params = ticket != null ? ticket.parametros() : Collections.emptyMap();

                RespostaEndpoint<Object> resp = gatekeeper.confirmarEExecutar(ticketId, roteadorController);
                String resultado;
                if (!resp.sucesso()) {
                    resultado = "❌ Não foi possível executar: " + resp.mensagem();
                } else if (rota == RotaSistema.PRODUTO_CATALOGO_EXCLUIR) {
                    resultado = "🗑️ **Produto #" + params.get("idProduto") + " excluído com sucesso do catálogo!**";
                } else if (rota == RotaSistema.PESSOA_CLIENTE_EXCLUIR) {
                    resultado = "🗑️ **Cliente/Pessoa #" + params.get("idPessoa") + " excluído com sucesso do sistema!**";
                } else {
                    resultado = "✅ **Operação confirmada e executada!**\n" + formatarResultadoAmigavel(rota, params, resp.dados());
                }
                return resultado + proximaPergunta(idSessao);
            }
            case NAO: {
                fila.pollFirst();
                gatekeeper.cancelarTicket(ticketId);
                return "🛑 **Operação cancelada.** Nada foi alterado ou excluído." + proximaPergunta(idSessao);
            }
            default:
                return "⚠️ Aguardando sua decisão. Responda ou fale **'SIM'** para confirmar ou **'NÃO'** para cancelar.\n\n"
                        + perguntaAtual(idSessao);
        }
    }

    private String perguntaAtual(String idSessao) {
        Deque<String> fila = ticketsPendentesPorSessao.get(idSessao);
        while (fila != null && !fila.isEmpty()) {
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao t = gatekeeper.buscarTicket(fila.peekFirst());
            if (t != null) return montarPergunta(t);
            fila.pollFirst(); // ticket expirado
        }
        return "";
    }

    private String proximaPergunta(String idSessao) {
        String p = perguntaAtual(idSessao);
        return p.isEmpty() ? "" : "\n\n" + p;
    }

    private String montarPergunta(ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket) {
        RotaSistema rota = ticket.rota();
        String entendimento = descreverEntendimento(rota, ticket.parametros());
        boolean isDestrutivo = GeminiToolRegistry.classificarRisco(rota) == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO;

        StringBuilder sb = new StringBuilder();
        sb.append("🧠 **O que entendi:** ").append(entendimento).append("\n");
        sb.append("🎯 **Rota Identificada:** `").append(rota.name()).append("` (Módulo: ").append(rota.getModulo()).append(")\n");
        sb.append("📋 **Campos Pré-preenchidos:** ").append(formatarParametrosResumidos(ticket.parametros())).append("\n");

        if (isDestrutivo) {
            sb.append("⚖️ **Regra do Sistema:** ⚠️ Governança Human-in-the-Loop ativa (Ação Destrutiva Irreversível).\n\n");
            sb.append("────────────────────────────────────────\n");
            sb.append("⚠️ **Confirmação Necessária (Exclusão Física):**\n");
            sb.append("🎯 **Alvo:** ").append(ticket.resumoImpacto()).append("\n\n");
            sb.append("👉 **Você confirma a exclusão definitiva?**\n");
            sb.append("Responda ou fale **'SIM'** para excluir, ou **'NÃO'** para cancelar.\n\n");
        } else {
            sb.append("⚖️ **Regra do Sistema:** 🔒 Execução sob Demanda: Os dados só serão gravados após sua conclusão/aprovação.\n\n");
            sb.append("────────────────────────────────────────\n");
            sb.append("👉 **Deseja concluir e executar esta operação com estes dados?**\n");
            sb.append("Responda ou fale **'SIM'** para salvar/executar, ou **'NÃO'** para cancelar.\n\n");
        }
        sb.append("[BOTAO_CONFIRMAR:SIM] [BOTAO_CANCELAR:NAO]");
        return sb.toString();
    }

    // ==================== FORMATAÇÃO ====================

    /** Descreve o alvo da ação (ex: nome do produto) antes de pedir confirmação. */
    private String obterDetalheAlvoAmigavel(RotaSistema rota, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "Nenhum detalhe informado";

        if (rota == RotaSistema.PRODUTO_CATALOGO_EXCLUIR) {
            Long id = extrairIdLong(params, "idProduto");
            if (id != null) {
                try {
                    Produto p = roteadorController.produtos().buscarPorId(id);
                    if (p != null) {
                        return String.format("Produto #%d: '%s' (Preço Atual: R$ %s)",
                                p.getId(), p.getNome(),
                                p.getPrecoVenda() != null ? p.getPrecoVenda().toString() : "0.00");
                    }
                    return "Produto ID #" + id + " (não encontrado no catálogo)";
                } catch (Exception ignored) {
                }
                return "Produto ID #" + id;
            }
        }

        if (rota == RotaSistema.PESSOA_CLIENTE_EXCLUIR) {
            Long id = extrairIdLong(params, "idPessoa");
            if (id != null) {
                try {
                    Pessoa p = roteadorController.pessoas().buscarPorId(id);
                    if (p != null) {
                        return String.format("Cliente #%d: '%s'", p.getId(), p.getNome());
                    }
                    return "Cliente/Pessoa ID #" + id + " (não encontrado)";
                } catch (Exception ignored) {
                }
                return "Cliente/Pessoa ID #" + id;
            }
        }

        if (rota == RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA) {
            return String.format("Lote #%s | Quantidade: %s | Motivo: %s",
                    params.get("idLote"), params.get("quantidade"), params.get("motivo"));
        }

        return params.toString();
    }

    /** Converte retornos Java em texto limpo e compreensível. */
    private String formatarResultadoAmigavel(RotaSistema rota, Map<String, Object> params, Object dados) {
        if (rota == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO) {
            return String.format("🏷️ **Preço Atualizado com Sucesso!**\nO produto **#%s** agora possui o preço de venda de **R$ %s**.",
                    params.get("idProduto"), params.get("novoPreco"));
        }
        if (dados == null) {
            return "✅ Operação realizada com sucesso.";
        }

        if (dados instanceof Produto p) {
            StringBuilder sb = new StringBuilder();
            sb.append("📦 **Detalhes do Produto:**\n")
              .append("• **ID:** #").append(p.getId()).append("\n")
              .append("• **Nome:** ").append(p.getNome()).append("\n")
              .append("• **Preço de Venda:** R$ ").append(p.getPrecoVenda() != null ? p.getPrecoVenda() : "0.00").append("\n")
              .append("• **Unidade:** ").append(p.getUm() != null ? p.getUm().getSigla() : "UN").append("\n");
            if (p instanceof ProdutoFisico pf && pf.possuiDimensoesValidas()) {
                sb.append(String.format("• **Dimensões:** %scm (A) x %scm (L) x %scm (C) | Peso: %skg\n",
                        pf.getAlturaCm(), pf.getLarguraCm(), pf.getComprimentoCm(), pf.getPesoLiquidoKg()));
            }
            return sb.toString().trim();
        }

        if (dados instanceof List<?> lista) {
            if (lista.isEmpty()) {
                return "ℹ️ Nenhum registro encontrado para os critérios informados.";
            }
            if (lista.get(0) instanceof Produto) {
                StringBuilder sb = new StringBuilder("📋 **Produtos Encontrados (" + lista.size() + " itens):**\n");
                for (Object item : lista) {
                    if (item instanceof Produto p) {
                        sb.append(String.format("• **#%d** - %s | **R$ %s** (%s)\n",
                                p.getId(), p.getNome(),
                                p.getPrecoVenda() != null ? p.getPrecoVenda() : "0.00",
                                p.getUm() != null ? p.getUm().getSigla() : "UN"));
                    }
                }
                return sb.toString().trim();
            }
            if (lista.get(0) instanceof Pessoa) {
                StringBuilder sb = new StringBuilder("👥 **Clientes / Fornecedores Encontrados (" + lista.size() + " registros):**\n");
                for (Object item : lista) {
                    if (item instanceof Pessoa pes) {
                        sb.append(String.format("• **#%d** - %s | Limite: R$ %s\n",
                                pes.getId(), pes.getNome(),
                                pes.getLimite() != null ? pes.getLimite() : "0.00"));
                    }
                }
                return sb.toString().trim();
            }
        }

        if (dados instanceof Map<?, ?> map && map.containsKey("pessoa") && map.containsKey("notas")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pMap = (Map<String, Object>) map.get("pessoa");
            @SuppressWarnings("unchecked")
            Map<String, Object> resumo = (Map<String, Object>) map.get("resumoFinanceiro");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> notas = (List<Map<String, Object>>) map.get("notas");

            StringBuilder sb = new StringBuilder();
            sb.append("👤 **DOSSIÊ COMPLETO 360º DA PESSOA**\n")
              .append("• **ID:** #").append(pMap.get("id")).append("\n")
              .append("• **Nome/Razão Social:** ").append(pMap.get("nome")).append("\n")
              .append("• **Documento:** ").append(pMap.get("documento")).append(" (").append(pMap.get("tipoPessoa")).append(")\n")
              .append("• **Papel:** ").append(Boolean.TRUE.equals(pMap.get("isCliente")) ? "Cliente " : "")
              .append(Boolean.TRUE.equals(pMap.get("isFornecedor")) ? "Fornecedor" : "").append("\n")
              .append("• **Limite de Crédito:** R$ ").append(pMap.get("limiteCredito")).append("\n\n");

            if (resumo != null) {
                sb.append("📊 **Resumo Consolidado:**\n")
                  .append("• Total de Notas: ").append(resumo.get("totalNotasEmitidas")).append("\n")
                  .append("• Valor Total Movimentado: R$ ").append(resumo.get("valorTotalMovimentado")).append("\n")
                  .append("• Quantidade Total de Itens: ").append(resumo.get("quantidadeTotalItens")).append("\n")
                  .append("• Total de Pagamentos: R$ ").append(resumo.get("valorTotalPagamentosRegistrados")).append("\n\n");
            }

            if (notas == null || notas.isEmpty()) {
                sb.append("ℹ️ Nenhuma nota fiscal/operacional vinculada a esta pessoa até o momento.");
            } else {
                sb.append("🧾 **Histórico de Notas Vinculadas (").append(notas.size()).append("):**\n");
                for (Map<String, Object> n : notas) {
                    sb.append(String.format("▸ **Nota #%s** [%s] - %s | Total: **R$ %s**\n",
                            n.get("idNota"), n.get("tipoNota"), n.get("dataGerada"), n.get("valorTotal")));

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> itens = (List<Map<String, Object>>) n.get("itensProdutos");
                    if (itens != null && !itens.isEmpty()) {
                        sb.append("   📦 **Produtos:** ");
                        for (int i = 0; i < itens.size(); i++) {
                            Map<String, Object> it = itens.get(i);
                            if (i > 0) sb.append(", ");
                            sb.append(it.get("nomeProduto")).append(" (").append(it.get("quantidade")).append(" ").append(it.get("unidade"))
                              .append(" x R$ ").append(it.get("precoUnitario")).append(")");
                        }
                        sb.append("\n");
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> pags = (List<Map<String, Object>>) n.get("pagamentos");
                    if (pags != null && !pags.isEmpty()) {
                        sb.append("   💳 **Pagamentos:** ");
                        for (int j = 0; j < pags.size(); j++) {
                            Map<String, Object> pg = pags.get(j);
                            if (j > 0) sb.append(", ");
                            sb.append(pg.get("forma")).append(" (R$ ").append(pg.get("valorFinal")).append(" - ").append(pg.get("tipo")).append(")");
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString().trim();
        }

        if (dados instanceof Pessoa pes) {
            return String.format(
                    "👤 **Dados do Cliente / Pessoa:**\n• **ID:** #%d\n• **Nome / Razão Social:** %s\n• **Limite de Crédito:** R$ %s",
                    pes.getId(), pes.getNome(), pes.getLimite() != null ? pes.getLimite() : "0.00");
        }
        if (dados instanceof BigDecimal bd) {
            return "📊 **Saldo / Valor:** R$ " + bd.toPlainString();
        }
        if (dados instanceof Boolean b) {
            return b ? "✅ Operação concluída com sucesso." : "⚠️ A operação não teve efeito.";
        }
        return "✅ **Resultado [" + (rota != null ? rota.getDescricaoOperacao() : "OK") + "]:**\n" + dados;
    }

    private Long extrairIdLong(Map<String, Object> params, String chave) {
        Object val = params.get(chave);
        if (val instanceof Number n) return n.longValue();
        if (val != null) {
            try {
                return Long.parseLong(val.toString().trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String extrairChaveRecurso(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return null;
        if (params.containsKey("idProduto")) return "PROD_" + params.get("idProduto");
        if (params.containsKey("idPessoa")) return "PESSOA_" + params.get("idPessoa");
        if (params.containsKey("idCliente")) return "CLIENTE_" + params.get("idCliente");
        if (params.containsKey("idNota")) return "NOTA_" + params.get("idNota");
        if (params.containsKey("idItemLote")) return "LOTE_" + params.get("idItemLote");
        return null;
    }

    // ==================== ACESSORES ====================

    public ProvedorIA getProvedor() {
        return provedor;
    }

    /** Troca a IA em tempo de execução (null = modo sem API). */
    public void setProvedor(ProvedorIA provedor) {
        this.provedor = provedor;
    }

    public GeminiClientService getGeminiClient() {
        return geminiClient;
    }

    public RoteadorController getRoteadorController() {
        return roteadorController;
    }

    public ConfirmacaoAcaoGatekeeper getGatekeeper() {
        return gatekeeper;
    }
}
