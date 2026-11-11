package br.com.senac.integracao.mcp;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.ConcorrenciaSeguraEngine;
import br.com.senac.integracao.gemini.ConfirmacaoAcaoGatekeeper;
import br.com.senac.integracao.gemini.GeminiToolRegistry;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servidor MCP (Model Context Protocol) oficial do Sistema de Vendas Sazonalisei.
 * Comunica-se via JSON-RPC 2.0 sobre Standard I/O (stdio), permitindo que QUALQUER cliente MCP
 * (Claude Desktop, Antigravity, Cursor, Windsurf, extensões ou agentes locais) acesse:
 * 
 * 1. RESOURCES (Contexto Vivo Dinâmico):
 *    - sazonalisei://regras/bacen (Regras contábeis e financeiras)
 *    - sazonalisei://catalogo/resumo (Lista atual de produtos do banco de dados)
 *    - sazonalisei://clientes/resumo (Lista atual de clientes e limites de crédito)
 *    - sazonalisei://rotas/manual (Manual semântico e dicionário de termos)
 * 
 * 2. TOOLS (Ferramentas Executáveis):
 *    - 63 ferramentas mapeadas diretamente para as rotas do RoteadorController
 *    - Concorrência segura via Virtual Threads (Java 21)
 *    - Governança Human-in-the-Loop para operações destrutivas
 * 
 * 3. PROMPTS (Modelos de Contexto e Atendimento):
 *    - consulta_catalogo, atualizar_preco, auditoria_estoque
 */
public class McpServidorSazonalisei {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "sazonalisei-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoteadorController roteador = new RoteadorController();
    private final ConcorrenciaSeguraEngine concorrencia = ConcorrenciaSeguraEngine.getInstancia();
    private final ConfirmacaoAcaoGatekeeper gatekeeper = ConfirmacaoAcaoGatekeeper.getInstancia();

    public static void main(String[] args) {
        // Redireciona logs internos para stderr para não poluir o stdout (exclusivo do JSON-RPC)
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        new McpServidorSazonalisei().executarLoopStdio();
    }

    public void executarLoopStdio() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                try {
                    JsonNode requisicao = objectMapper.readTree(linha);
                    tratarMensagemJsonRpc(requisicao);
                } catch (Exception e) {
                    System.err.println("[MCP ERROR] Falha ao processar mensagem JSON-RPC: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[MCP FATAL] Erro no loop de stdio: " + e.getMessage());
        }
    }

    public void tratarMensagemJsonRpc(JsonNode req) {
        String method = req.path("method").asText("");
        JsonNode idNode = req.get("id");

        // Se for notificação (sem ID)
        if (idNode == null || idNode.isNull()) {
            if ("notifications/initialized".equals(method)) {
                System.err.println("[MCP] Cliente MCP inicializado com sucesso.");
            }
            return;
        }

        Object id = idNode.isNumber() ? idNode.asLong() : idNode.asText();

        try {
            switch (method) {
                case "initialize" -> responderInitialize(id);
                case "ping" -> responderOk(id, Collections.emptyMap());
                case "tools/list" -> responderToolsList(id);
                case "tools/call" -> responderToolsCall(id, req.path("params"));
                case "resources/list" -> responderResourcesList(id);
                case "resources/read" -> responderResourcesRead(id, req.path("params"));
                case "prompts/list" -> responderPromptsList(id);
                case "prompts/get" -> responderPromptsGet(id, req.path("params"));
                default -> responderErro(id, -32601, "Método não encontrado: " + method);
            }
        } catch (Exception e) {
            responderErro(id, -32603, "Erro interno: " + e.getMessage());
        }
    }

    // ==================== 1. HANDSHAKE (INITIALIZE) ====================

    private void responderInitialize(Object id) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        capabilities.put("prompts", Map.of("listChanged", false));

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        result.put("instructions", 
                "Servidor MCP do Sistema Sazonalisei. Fornece ferramentas do ERP (catálogo, estoque, pessoas, pagamentos e notas) " +
                "e recursos com dados ao vivo do banco de dados para enriquecer o contexto de raciocínio da IA.");

        responderOk(id, result);
    }

    // ==================== 2. TOOLS ====================

    private void responderToolsList(Object id) {
        List<Map<String, Object>> toolsMcp = new ArrayList<>();

        for (Map<String, Object> toolOrig : GeminiToolRegistry.gerarDeclaracoesTodasTools()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("name", toolOrig.get("name"));
            tm.put("description", toolOrig.get("description"));

            // Converte tipos do JSON Schema para padrão minúsculo exigido pelo MCP
            Object params = converterTiposSchemaMcp(toolOrig.get("parameters"));
            tm.put("inputSchema", params != null ? params : Map.of("type", "object", "properties", Collections.emptyMap()));

            toolsMcp.add(tm);
        }

        responderOk(id, Map.of("tools", toolsMcp));
    }

    private void responderToolsCall(Object id, JsonNode paramsNode) {
        String toolName = paramsNode.path("name").asText("");
        JsonNode argsNode = paramsNode.path("arguments");

        Map<String, Object> args = new LinkedHashMap<>();
        if (argsNode.isObject()) {
            args = objectMapper.convertValue(argsNode, new TypeReference<LinkedHashMap<String, Object>>() {});
        }

        RotaSistema rota = roteador.resolverRota(toolName);
        if (rota == null) {
            responderOk(id, Map.of(
                    "isError", true,
                    "content", List.of(Map.of("type", "text", "text", "❌ Ferramenta desconhecida: " + toolName))
            ));
            return;
        }

        GeminiToolRegistry.NivelRiscoOperacao risco = GeminiToolRegistry.classificarRisco(rota);

        // Se for ação destrutiva, gera aviso de confirmação
        if (risco == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO) {
            String resumo = rota.getDescricaoOperacao() + " com parâmetros: " + args;
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.criarTicket(rota, args, resumo);

            String texto = String.format(
                    "⚠️ [ATENÇÃO - AÇÃO DESTRUTIVA / ALTO RISCO]\n" +
                    "A operação '%s' possui impacto permanente.\n" +
                    "Ticket de Confirmação gerado: %s\n" +
                    "Para executar fisicamente, chame a confirmação no sistema ou confirme via chat.",
                    rota.getCaminhoRota(), ticket.ticketId()
            );

            responderOk(id, Map.of(
                    "isError", false,
                    "content", List.of(Map.of("type", "text", "text", texto))
            ));
            return;
        }

        // Executa com segurança transacional via Virtual Threads
        try {
            Map<String, Object> finalArgs = args;
            RespostaEndpoint<Object> ret = concorrencia.submeterTarefa(
                    () -> roteador.executarEndpoint(rota, finalArgs),
                    extrairChaveRecurso(args)
            ).get();

            String textoRetorno = ret.sucesso()
                    ? "✅ [" + rota.getCaminhoRota() + "] Sucesso:\n" + formatarRetorno(ret.dados())
                    : "❌ [" + rota.getCaminhoRota() + "] Falha: " + ret.mensagem();

            responderOk(id, Map.of(
                    "isError", !ret.sucesso(),
                    "content", List.of(Map.of("type", "text", "text", textoRetorno))
            ));
        } catch (Exception e) {
            responderOk(id, Map.of(
                    "isError", true,
                    "content", List.of(Map.of("type", "text", "text", "❌ Erro interno na execução: " + e.getMessage()))
            ));
        }
    }

    // ==================== 3. RESOURCES (CONTEXTO VIVO) ====================

    private void responderResourcesList(Object id) {
        List<Map<String, Object>> resources = List.of(
                Map.of(
                        "uri", "sazonalisei://regras/bacen",
                        "name", "Regras Contábeis e Margens BACEN",
                        "description", "Diretrizes de arredondamento bancário (HALF_EVEN, 2 casas), cálculo de margens e juros conforme o Banco Central do Brasil.",
                        "mimeType", "text/markdown"
                ),
                Map.of(
                        "uri", "sazonalisei://catalogo/resumo",
                        "name", "Catálogo Atual de Produtos",
                        "description", "Listagem consolidada em tempo real com todos os produtos cadastrados, tipos, unidades e preços de venda.",
                        "mimeType", "text/markdown"
                ),
                Map.of(
                        "uri", "sazonalisei://clientes/resumo",
                        "name", "Clientes Cadastrados e Limites",
                        "description", "Listagem consolidada de pessoas físicas e jurídicas cadastradas como clientes e seus limites de crédito.",
                        "mimeType", "text/markdown"
                ),
                Map.of(
                        "uri", "sazonalisei://rotas/manual",
                        "name", "Manual Semântico das 63 Rotas",
                        "description", "Manual autoexplicativo gerado dinamicamente com dicionário de intenções e parâmetros de cada rota do sistema.",
                        "mimeType", "text/markdown"
                )
        );

        responderOk(id, Map.of("resources", resources));
    }

    private void responderResourcesRead(Object id, JsonNode paramsNode) {
        String uri = paramsNode.path("uri").asText("");
        String conteudo = "";

        switch (uri) {
            case "sazonalisei://regras/bacen" -> conteudo = """
                    # DIRETRIZES CONTÁBEIS E FINANCEIRAS BACEN (SAZONALISEI)
                    1. Arredondamento Bancário Obrigatório: Todas as operações de moeda utilizam RoundingMode.HALF_EVEN com 2 casas decimais.
                    2. Quantidades e Estoque: Quantidades suportam até 6 casas decimais para itens fracionados (kg, litros, metros).
                    3. FIFO (First-In, First-Out): Baixas em estoque consomem prioritariamente os lotes mais antigos para apuração correta do CPV.
                    4. Limite de Crédito: Vendas a prazo exigem validação prévia do limite disponível do cliente.
                    """;

            case "sazonalisei://catalogo/resumo" -> {
                try {
                    List<Produto> lista = roteador.produtos().listarTodosProdutos();
                    StringBuilder sb = new StringBuilder("# CATÁLOGO DE PRODUTOS ATUAL (" + lista.size() + " itens cadastrados)\n\n");
                    for (Produto p : lista) {
                        sb.append(String.format("• **#%d** - %s | R$ %s (%s) | Tipo: %s\n",
                                p.getId(), p.getNome(),
                                p.getPrecoVenda() != null ? p.getPrecoVenda() : "0.00",
                                p.getUm() != null ? p.getUm().getSigla() : "UN",
                                p.getClass().getSimpleName()));
                    }
                    conteudo = sb.toString();
                } catch (Exception e) {
                    conteudo = "Erro ao carregar catálogo: " + e.getMessage();
                }
            }

            case "sazonalisei://clientes/resumo" -> {
                try {
                    List<Pessoa> clientes = roteador.pessoas().listarClientes();
                    StringBuilder sb = new StringBuilder("# CLIENTES CADASTRADOS (" + clientes.size() + " clientes)\n\n");
                    for (Pessoa c : clientes) {
                        sb.append(String.format("• **#%d** - %s | Limite: R$ %s\n",
                                c.getId(), c.getNome(),
                                c.getLimite() != null ? c.getLimite() : "0.00"));
                    }
                    conteudo = sb.toString();
                } catch (Exception e) {
                    conteudo = "Erro ao carregar clientes: " + e.getMessage();
                }
            }

            case "sazonalisei://rotas/manual" -> conteudo = ManualInstrucoesIA.gerar();

            default -> {
                responderErro(id, -32602, "URI de recurso desconhecida: " + uri);
                return;
            }
        }

        Map<String, Object> resContent = new LinkedHashMap<>();
        resContent.put("uri", uri);
        resContent.put("mimeType", "text/markdown");
        resContent.put("text", conteudo);

        responderOk(id, Map.of("contents", List.of(resContent)));
    }

    // ==================== 4. PROMPTS ====================

    private void responderPromptsList(Object id) {
        List<Map<String, Object>> prompts = List.of(
                Map.of(
                        "name", "consulta_catalogo",
                        "description", "Prompt para buscar produtos e tirar dúvidas sobre especificações, preços e disponibilidade.",
                        "arguments", List.of(Map.of("name", "termoBusca", "description", "Nome ou ID do produto", "required", true))
                ),
                Map.of(
                        "name", "atualizar_preco",
                        "description", "Prompt para orientar a alteração do preço de venda de um item com conferência prévia.",
                        "arguments", List.of(
                                Map.of("name", "idProduto", "description", "ID do produto no catálogo", "required", true),
                                Map.of("name", "novoPreco", "description", "Novo valor em R$", "required", true)
                        )
                ),
                Map.of(
                        "name", "auditoria_estoque",
                        "description", "Prompt para auditar saldo de estoque consolidado e lotes em vencimento.",
                        "arguments", List.of(Map.of("name", "idProduto", "description", "ID do produto", "required", false))
                )
        );

        responderOk(id, Map.of("prompts", prompts));
    }

    private void responderPromptsGet(Object id, JsonNode paramsNode) {
        String name = paramsNode.path("name").asText("");
        JsonNode args = paramsNode.path("arguments");

        String texto = switch (name) {
            case "consulta_catalogo" -> "Consulte o catálogo de produtos buscando por '" + args.path("termoBusca").asText("") + "'. " +
                    "Apresente os resultados com ID, Nome, Preço de Venda e Unidade.";
            case "atualizar_preco" -> "Verifique o produto #" + args.path("idProduto").asText("") + " e altere seu preço de venda para R$ " +
                    args.path("novoPreco").asText("") + " utilizando a ferramenta PRODUTO_CATALOGO_ATUALIZAR_PRECO.";
            case "auditoria_estoque" -> "Realize a conferência do estoque do produto #" + args.path("idProduto").asText("") +
                    ", consultando o saldo consolidado e os lotes disponíveis.";
            default -> "Execute as tarefas operacionais de acordo com as diretrizes do sistema.";
        };

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", Map.of("type", "text", "text", texto)
        );

        responderOk(id, Map.of(
                "description", "Prompt " + name,
                "messages", List.of(message)
        ));
    }

    // ==================== AUXILIARES JSON-RPC ====================

    private void responderOk(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        enviarLinhaJson(resp);
    }

    private void responderErro(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", err);
        enviarLinhaJson(resp);
    }

    private synchronized void enviarLinhaJson(Map<String, Object> mapa) {
        try {
            String json = objectMapper.writeValueAsString(mapa);
            System.out.println(json);
            System.out.flush();
        } catch (Exception e) {
            System.err.println("[MCP ERROR] Falha ao enviar resposta JSON: " + e.getMessage());
        }
    }

    private Object converterTiposSchemaMcp(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if ("type".equals(k) && e.getValue() instanceof String s) {
                    r.put(k, s.toLowerCase());
                } else {
                    r.put(k, converterTiposSchemaMcp(e.getValue()));
                }
            }
            return r;
        }
        if (o instanceof List<?> l) {
            List<Object> r = new ArrayList<>();
            for (Object x : l) r.add(converterTiposSchemaMcp(x));
            return r;
        }
        return o;
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

    private String formatarRetorno(Object dados) {
        if (dados == null) return "Nenhum registro encontrado.";
        if (dados instanceof Produto p) {
            return String.format("Produto #%d: '%s' | Preço: R$ %s | Unidade: %s",
                    p.getId(), p.getNome(),
                    p.getPrecoVenda() != null ? p.getPrecoVenda() : "0.00",
                    p.getUm() != null ? p.getUm().getSigla() : "UN");
        }
        if (dados instanceof Pessoa pes) {
            return String.format("Pessoa #%d: '%s' | Cliente: %s | Fornecedor: %s | Limite: R$ %s",
                    pes.getId(), pes.getNome(), pes.isCliente(), pes.isFornecedor(),
                    pes.getLimite() != null ? pes.getLimite() : "0.00");
        }
        return dados.toString();
    }
}
