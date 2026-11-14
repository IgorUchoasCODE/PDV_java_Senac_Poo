package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.gemini.GeminiAgenteOperacional;
import br.com.senac.integracao.ia.DecisaoConfirmacao;
import br.com.senac.integracao.ia.InterpretadorRespostaIA;
import br.com.senac.integracao.ia.ManualInstrucoesIA;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;

import java.util.List;
import java.util.Map;

/**
 * Testes offline (sem API e sem alterar dados) da camada de integração com qualquer IA:
 * manual autoexplicativo, interpretador de respostas, decisão de confirmação e ponte sem API.
 */
public class TesteIntegracaoIA {

    private static int total = 0;
    private static int ok = 0;

    public static void main(String[] args) {
        try {
            // 1. Manual autoexplicativo cobre 100% das rotas
            String manual = ManualInstrucoesIA.gerar();
            for (RotaSistema r : RotaSistema.values()) {
                afirmar(manual.contains("**" + r.name() + "**"), "Manual documenta a rota " + r.name());
            }
            afirmar(manual.contains("\"acoes\""), "Manual define o formato de resposta (acoes)");
            afirmar(manual.contains("NUNCA invente IDs"), "Manual traz regras semânticas");
            afirmar(manual.contains("OBRIGATÓRIO"), "Manual marca parâmetros obrigatórios");
            afirmar(manual.contains("\"rota\":\"PRODUTO_CATALOGO_EXCLUIR\",\"parametros\":{\"idProduto\":1}"), "Manual gera exemplo de chamada automaticamente");

            // 2. Interpretador tolerante
            RespostaIA a = InterpretadorRespostaIA.interpretar(
                    "Claro! Aqui está:\n```json\n{\"mensagem\":\"Vou excluir\",\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_EXCLUIR\",\"parametros\":{\"idProduto\":86}}]}\n```\nAlgo mais?");
            afirmar(a.temChamadas() && a.chamadas().size() == 1, "Interpretador extrai JSON de bloco markdown com texto em volta");
            afirmar("PRODUTO_CATALOGO_EXCLUIR".equals(a.chamadas().get(0).rota()), "Rota extraída corretamente");
            afirmar(((Number) a.chamadas().get(0).parametros().get("idProduto")).longValue() == 86L, "Parâmetro idProduto extraído");
            afirmar("Vou excluir".equals(a.texto()), "Mensagem ao usuário extraída");

            RespostaIA b = InterpretadorRespostaIA.interpretar("[{\"tool\":\"PRODUTO_CATALOGO_LISTAR\",\"args\":{}}]");
            afirmar(b.chamadas().size() == 1 && "PRODUTO_CATALOGO_LISTAR".equals(b.chamadas().get(0).rota()), "Aceita lista na raiz e nomes alternativos (tool/args)");

            RespostaIA c = InterpretadorRespostaIA.interpretar("Olá! Como posso ajudar?");
            afirmar(!c.temChamadas() && c.texto().startsWith("Olá"), "Texto sem JSON vira resposta simples");

            boolean falhou = false;
            try {
                InterpretadorRespostaIA.interpretar("{\"acoes\": [ {rota: ");
            } catch (IllegalArgumentException e) {
                falhou = true;
            }
            afirmar(falhou, "JSON quebrado gera erro claro (não executa nada)");

            // 3. Decisão de confirmação: negação sempre vence
            afirmar(DecisaoConfirmacao.interpretar("sim") == DecisaoConfirmacao.SIM, "'sim' => SIM");
            afirmar(DecisaoConfirmacao.interpretar("Confirmo, pode apagar") == DecisaoConfirmacao.SIM, "'Confirmo, pode apagar' => SIM");
            afirmar(DecisaoConfirmacao.interpretar("NÃO") == DecisaoConfirmacao.NAO, "'NÃO' => NAO");
            afirmar(DecisaoConfirmacao.interpretar("não, pode cancelar") == DecisaoConfirmacao.NAO, "'não, pode cancelar' => NAO (antes executava!)");
            afirmar(DecisaoConfirmacao.interpretar("não está ok") == DecisaoConfirmacao.NAO, "'não está ok' => NAO");
            afirmar(DecisaoConfirmacao.interpretar("talvez depois") == DecisaoConfirmacao.INDEFINIDA, "'talvez depois' => INDEFINIDA (não executa)");

            // 4. Validação de parâmetros obrigatórios
            afirmar(ManualInstrucoesIA.obrigatoriosFaltando(RotaSistema.PRODUTO_CATALOGO_EXCLUIR, Map.of()).contains("idProduto"), "Detecta idProduto faltando na exclusão");
            afirmar(ManualInstrucoesIA.obrigatoriosFaltando(RotaSistema.PRODUTO_CATALOGO_EXCLUIR, Map.of("idProduto", 86)).isEmpty(), "Chamada completa passa na validação");

            // 5. Ponte sem API de ponta a ponta (sem executar exclusão real)
            GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
            String s = "teste-ponte";
            String pergunta = agente.processarRespostaExterna(s,
                    "{\"mensagem\":\"ok\",\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_EXCLUIR\",\"parametros\":{\"idProduto\":987654}}]}");
            afirmar(pergunta.contains("Confirmação Necessária"), "Ação destrutiva da IA externa exige confirmação");

            String bloqueio = agente.processarRespostaExterna(s, "{\"acoes\":[]}");
            afirmar(bloqueio.contains("confirmação pendente"), "Novas ações são bloqueadas enquanto há confirmação pendente");

            String indef = agente.processarRespostaExterna(s, "talvez");
            afirmar(indef.contains("Aguardando sua decisão"), "Resposta ambígua não executa nada");

            String cancel = agente.processarRespostaExterna(s, "não, pode cancelar");
            afirmar(cancel.contains("cancelada"), "'não, pode cancelar' cancela a operação");

            String faltando = agente.processarRespostaExterna(s, "{\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_EXCLUIR\",\"parametros\":{}}]}");
            afirmar(faltando.contains("faltam os dados"), "Parâmetro obrigatório ausente é recusado com mensagem clara");

            String inexistente = agente.processarRespostaExterna(s, "{\"acoes\":[{\"rota\":\"ROTA_QUE_NAO_EXISTE\",\"parametros\":{}}]}");
            afirmar(inexistente.contains("não existe"), "Rota inventada pela IA é recusada");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DA CAMADA DE IA APROVADOS!%n", ok, total);
            System.out.println("================================================================================");
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void afirmar(boolean cond, String msg) {
        total++;
        if (cond) {
            ok++;
            if (total <= 3 || !msg.startsWith("Manual documenta")) System.out.println(" [OK] " + msg);
        } else {
            System.err.println(" [FALHA] " + msg);
            throw new AssertionError(msg);
        }
    }
}
