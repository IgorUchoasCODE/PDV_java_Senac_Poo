package br.com.senac.application;

import br.com.senac.integracao.mcp.McpServidorSazonalisei;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Suíte de testes automatizados do Servidor MCP (Model Context Protocol):
 * 1. Handshake 'initialize' e negociação de capacidades
 * 2. Catálogo completo de 63 ferramentas em 'tools/list'
 * 3. Execução de rota via 'tools/call'
 * 4. Catálogo de contexto dinâmico em 'resources/list'
 * 5. Leitura de recursos em 'resources/read' (regras BACEN e catálogo ao vivo)
 * 6. Modelos de contexto em 'prompts/list' e 'prompts/get'
 */
public class TesteMcpServidor {

    private static int total = 0;
    private static int ok = 0;
    private static final ObjectMapper OM = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(" INICIANDO TESTE DO SERVIDOR MCP (MODEL CONTEXT PROTOCOL)");
        System.out.println("================================================================================");

        try {
            McpServidorSazonalisei servidor = new McpServidorSazonalisei();

            // 1. Teste de Handshake (initialize)
            JsonNode respInit = enviarMensagem(servidor, """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": { "name": "teste-mcp-client", "version": "1.0.0" }
                      }
                    }
                    """);
            afirmar(respInit.path("result").path("serverInfo").path("name").asText().equals("sazonalisei-mcp-server"), "Handshake 'initialize' respondeu com nome do servidor.");
            afirmar(respInit.path("result").path("capabilities").has("tools"), "Capacidade 'tools' habilitada.");
            afirmar(respInit.path("result").path("capabilities").has("resources"), "Capacidade 'resources' habilitada.");
            afirmar(respInit.path("result").path("capabilities").has("prompts"), "Capacidade 'prompts' habilitada.");

            // 2. Teste de Listagem de Ferramentas (tools/list)
            JsonNode respTools = enviarMensagem(servidor, """
                    { "jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {} }
                    """);
            JsonNode toolsArr = respTools.path("result").path("tools");
            afirmar(toolsArr.isArray() && toolsArr.size() == 63, "tools/list retornou exatamente as 63 rotas mapeadas (Total: " + toolsArr.size() + ")");

            boolean achouBuscar = false;
            boolean achouExcluir = false;
            for (JsonNode t : toolsArr) {
                String n = t.path("name").asText();
                if ("produto_catalogo_buscar".equalsIgnoreCase(n)) achouBuscar = true;
                if ("produto_catalogo_excluir".equalsIgnoreCase(n)) achouExcluir = true;
                afirmar("object".equals(t.path("inputSchema").path("type").asText()), "Tool '" + n + "' possui schema JSON válido no padrão MCP.");
            }
            afirmar(achouBuscar, "Ferramenta produto_catalogo_buscar está presente.");
            afirmar(achouExcluir, "Ferramenta produto_catalogo_excluir está presente.");

            // 3. Teste de Chamada de Ferramenta (tools/call)
            JsonNode respCall = enviarMensagem(servidor, """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "method": "tools/call",
                      "params": {
                        "name": "produto_catalogo_buscar",
                        "arguments": { "id": 1 }
                      }
                    }
                    """);
            afirmar(!respCall.path("result").path("isError").asBoolean(true), "tools/call executou sem erro de canal.");
            afirmar(respCall.path("result").path("content").isArray(), "tools/call retornou array de conteúdo textual.");

            // 4. Teste de Recursos de Contexto (resources/list)
            JsonNode respRes = enviarMensagem(servidor, """
                    { "jsonrpc": "2.0", "id": 4, "method": "resources/list", "params": {} }
                    """);
            JsonNode resArr = respRes.path("result").path("resources");
            afirmar(resArr.isArray() && resArr.size() >= 4, "resources/list retornou recursos de contexto dinâmico.");

            // 5. Teste de Leitura de Recursos (resources/read)
            JsonNode respReadBacen = enviarMensagem(servidor, """
                    {
                      "jsonrpc": "2.0",
                      "id": 5,
                      "method": "resources/read",
                      "params": { "uri": "sazonalisei://regras/bacen" }
                    }
                    """);
            String txtBacen = respReadBacen.path("result").path("contents").get(0).path("text").asText();
            afirmar(txtBacen.contains("HALF_EVEN"), "resources/read para sazonalisei://regras/bacen forneceu diretrizes contábeis.");

            JsonNode respReadCatalogo = enviarMensagem(servidor, """
                    {
                      "jsonrpc": "2.0",
                      "id": 6,
                      "method": "resources/read",
                      "params": { "uri": "sazonalisei://catalogo/resumo" }
                    }
                    """);
            String txtCatalogo = respReadCatalogo.path("result").path("contents").get(0).path("text").asText();
            afirmar(txtCatalogo.contains("CATÁLOGO DE PRODUTOS"), "resources/read para sazonalisei://catalogo/resumo forneceu dados vivos do banco.");

            // 6. Teste de Prompts (prompts/list e prompts/get)
            JsonNode respPrompts = enviarMensagem(servidor, """
                    { "jsonrpc": "2.0", "id": 7, "method": "prompts/list", "params": {} }
                    """);
            JsonNode promptsArr = respPrompts.path("result").path("prompts");
            afirmar(promptsArr.isArray() && promptsArr.size() >= 3, "prompts/list retornou modelos de contexto prontos.");

            JsonNode respPromptGet = enviarMensagem(servidor, """
                    {
                      "jsonrpc": "2.0",
                      "id": 8,
                      "method": "prompts/get",
                      "params": { "name": "consulta_catalogo", "arguments": { "termoBusca": "Monitor" } }
                    }
                    """);
            String promptMsg = respPromptGet.path("result").path("messages").get(0).path("content").path("text").asText();
            afirmar(promptMsg.contains("Monitor"), "prompts/get injetou argumentos de busca no prompt estruturado.");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DO SERVIDOR MCP APROVADOS!%n", ok, total);
            System.out.println("================================================================================");

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static JsonNode enviarMensagem(McpServidorSazonalisei servidor, String json) throws Exception {
        PrintStream stdoutOriginal = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));

        try {
            JsonNode req = OM.readTree(json);
            servidor.tratarMensagemJsonRpc(req);
            String output = baos.toString(StandardCharsets.UTF_8).trim();
            return OM.readTree(output);
        } finally {
            System.setOut(stdoutOriginal);
        }
    }

    private static void afirmar(boolean cond, String msg) {
        total++;
        if (cond) {
            ok++;
            System.out.println(" [OK] " + msg);
        } else {
            System.err.println(" [FALHA] " + msg);
            throw new AssertionError(msg);
        }
    }
}
