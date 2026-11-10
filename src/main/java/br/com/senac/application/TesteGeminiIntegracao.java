package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.gemini.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Suíte de Testes Automatizados para a Integração com a IA Gemini:
 * 1. Validação de Credenciais e Configuração em Cascata
 * 2. Geração Completa dos Schemas das 60 Tools do RotaSistema
 * 3. Classificação de Risco e Segurança (READ, WRITE, DESTRUTIVO)
 * 4. Validação da Concorrência Segura em Virtual Threads (Java 21+)
 * 5. Ciclo de Confirmação em 2 Etapas (Human-in-the-Loop)
 */
public class TesteGeminiIntegracao {

    private static int testesExecutados = 0;
    private static int testesAprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(" INICIANDO TESTE DO SUBSISTEMA DE INTEGRAÇÃO COM IA GEMINI");
        System.out.println("================================================================================");

        try {
            // 1. Teste de Configuração e Modelo Padrão
            GeminiConfig config = GeminiConfig.getInstancia();
            afirmar(GeminiConfig.DEFAULT_MODEL.equals(config.getModel()), "Modelo padrão é gemini-3.8-flash: " + config.getModel());
            afirmar(config.getTemperature() == 0.2, "Temperatura padrão de precisão é 0.2");
            afirmar(config.getMaxOutputTokens() == 4096, "Limite de saída é 4096 tokens");

            // 2. Teste do Gerador de Tools (63 Rotas)
            List<Map<String, Object>> tools = GeminiToolRegistry.gerarDeclaracoesTodasTools();
            afirmar(tools.size() == 63, "Exatamente 63 ferramentas mapeadas para a IA Gemini (Total: " + tools.size() + ")");

            for (Map<String, Object> tool : tools) {
                String name = (String) tool.get("name");
                String desc = (String) tool.get("description");
                Map<?, ?> params = (Map<?, ?>) tool.get("parameters");

                afirmar(name != null && !name.isBlank(), "Ferramenta possui nome válido: " + name);
                afirmar(desc != null && !desc.isBlank(), "Ferramenta possui descrição enriquecida: " + name);
                afirmar(params != null && "OBJECT".equals(params.get("type")), "Ferramenta possui schema de parâmetros JSON Schema: " + name);

                Map<?, ?> properties = (Map<?, ?>) params.get("properties");
                if (properties != null) {
                    for (Map.Entry<?, ?> entry : properties.entrySet()) {
                        Map<?, ?> pMap = (Map<?, ?>) entry.getValue();
                        if ("ARRAY".equals(pMap.get("type"))) {
                            afirmar(pMap.containsKey("items"), "Propriedade ARRAY '" + entry.getKey() + "' na tool '" + name + "' possui campo 'items' obrigatório");
                        }
                    }
                }
            }

            // 3. Teste de Classificação de Risco
            GeminiToolRegistry.NivelRiscoOperacao riscoRemover = GeminiToolRegistry.classificarRisco(RotaSistema.PRODUTO_IMAGEM_REMOVER);
            afirmar(riscoRemover == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO, "PRODUTO_IMAGEM_REMOVER classificado como DESTRUTIVO");

            GeminiToolRegistry.NivelRiscoOperacao riscoExcluirProduto = GeminiToolRegistry.classificarRisco(RotaSistema.PRODUTO_CATALOGO_EXCLUIR);
            afirmar(riscoExcluirProduto == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO, "PRODUTO_CATALOGO_EXCLUIR classificado como DESTRUTIVO (Human-in-the-Loop)");

            GeminiToolRegistry.NivelRiscoOperacao riscoExcluirPessoa = GeminiToolRegistry.classificarRisco(RotaSistema.PESSOA_CLIENTE_EXCLUIR);
            afirmar(riscoExcluirPessoa == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO, "PESSOA_CLIENTE_EXCLUIR classificado como DESTRUTIVO (Human-in-the-Loop)");

            GeminiToolRegistry.NivelRiscoOperacao riscoAtualizarPreco = GeminiToolRegistry.classificarRisco(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO);
            afirmar(riscoAtualizarPreco == GeminiToolRegistry.NivelRiscoOperacao.WRITE_SEGURO, "PRODUTO_CATALOGO_ATUALIZAR_PRECO classificado como WRITE_SEGURO");

            GeminiToolRegistry.NivelRiscoOperacao riscoBaixar = GeminiToolRegistry.classificarRisco(RotaSistema.ESTOQUE_BAIXAR_PERDA_DIRETA);
            afirmar(riscoBaixar == GeminiToolRegistry.NivelRiscoOperacao.DESTRUTIVO, "ESTOQUE_BAIXAR_PERDA_DIRETA classificado como DESTRUTIVO");

            GeminiToolRegistry.NivelRiscoOperacao riscoCadastrar = GeminiToolRegistry.classificarRisco(RotaSistema.PRODUTO_FISICO_CADASTRAR);
            afirmar(riscoCadastrar == GeminiToolRegistry.NivelRiscoOperacao.WRITE_SEGURO, "PRODUTO_FISICO_CADASTRAR classificado como WRITE_SEGURO");

            GeminiToolRegistry.NivelRiscoOperacao riscoListar = GeminiToolRegistry.classificarRisco(RotaSistema.PRODUTO_CATALOGO_LISTAR);
            afirmar(riscoListar == GeminiToolRegistry.NivelRiscoOperacao.READ_ONLY, "PRODUTO_CATALOGO_LISTAR classificado como READ_ONLY");

            // 4. Teste de System Instruction
            Map<String, Object> sysInstr = GeminiToolRegistry.gerarSystemInstruction();
            afirmar(sysInstr.containsKey("parts"), "System Instruction gerado com estrutura válida para a API Gemini.");

            // 5. Teste de Concorrência Segura (Virtual Threads)
            ConcorrenciaSeguraEngine engine = ConcorrenciaSeguraEngine.getInstancia();
            AtomicBoolean usouVirtualThread = new AtomicBoolean(false);

            engine.submeterTarefa(() -> {
                usouVirtualThread.set(Thread.currentThread().isVirtual());
                return true;
            }, null).get();

            afirmar(usouVirtualThread.get(), "Tarefa concorrente executada com sucesso em Virtual Thread (Java 21 Loom).");

            // 6. Teste do Protocolo de Confirmação em 2 Etapas (Gatekeeper)
            ConfirmacaoAcaoGatekeeper gatekeeper = ConfirmacaoAcaoGatekeeper.getInstancia();
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket = gatekeeper.criarTicket(
                    RotaSistema.PRODUTO_IMAGEM_REMOVER,
                    Map.of("idImagem", 999L),
                    "Exclusão da imagem ID 999 do disco e banco"
            );

            afirmar(ticket != null && ticket.ticketId().startsWith("CONFIRM-"), "Ticket de confirmação gerado: " + ticket.ticketId());
            afirmar(!ticket.isExpirado(java.time.Duration.ofMinutes(1)), "Ticket está ativo e dentro da janela de validade.");

            // Cancelamento voluntário
            boolean cancelado = gatekeeper.cancelarTicket(ticket.ticketId());
            afirmar(cancelado, "Ticket cancelado com sucesso a pedido do usuário.");
            afirmar(gatekeeper.buscarTicket(ticket.ticketId()) == null, "Ticket cancelado foi devidamente descartado.");

            // 7. Teste de Diálogo com o Agente Operacional (Simulação de Confirmação)
            GeminiAgenteOperacional agente = new GeminiAgenteOperacional();
            String idSessao = "sessao-teste-usuario-1";

            // Cria novo ticket pendente simulando intenção destrutiva identificada pela IA
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticket2 = gatekeeper.criarTicket(
                    RotaSistema.PRODUTO_IMAGEM_REMOVER,
                    Map.of("idImagem", 1L),
                    "Deleção de imagem"
            );
            // Injeta manualmente na sessão
            agente.processarMensagem(idSessao, "quero apagar imagem");

            // Usuário responde "NÃO"
            // Cria ticket para teste de recusa
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticketRecusa = gatekeeper.criarTicket(
                    RotaSistema.PRODUTO_IMAGEM_REMOVER,
                    Map.of("idImagem", 1L),
                    "Teste de recusa"
            );
            // Simula resposta negativa
            gatekeeper.cancelarTicket(ticketRecusa.ticketId());
            afirmar(gatekeeper.buscarTicket(ticketRecusa.ticketId()) == null, "Simulação de recusa de exclusão validada com sucesso.");

            // 8. Teste de Ciclo Completo de Produto (Criar -> Atualizar Preço -> Excluir com Confirmação)
            RoteadorController roteador = new RoteadorController();
            var respCriar = roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Produto Teste 86",
                    "descricao", "Item para teste de ciclo de vida",
                    "precoVenda", "99.90",
                    "siglaUm", "UN",
                    "alturaCm", "10.00",
                    "larguraCm", "20.00",
                    "comprimentoCm", "30.00",
                    "pesoBrutoKg", "1.500",
                    "pesoLiquidoKg", "1.200"
            ));
            afirmar(respCriar.sucesso(), "Produto teste criado com sucesso no catálogo: " + respCriar.dados());
            br.com.senac.repository.modal.produto.Produto prodCriado = (br.com.senac.repository.modal.produto.Produto) respCriar.dados();
            Long idProdutoTeste = prodCriado.getId();

            // Atualização de Preço
            var respPreco = roteador.executarEndpoint(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, Map.of(
                    "idProduto", idProdutoTeste,
                    "novoPreco", "149.90"
            ));
            afirmar(respPreco.sucesso(), "Preço do produto atualizado com sucesso para R$ 149.90");

            // Exclusão com Confirmação Human-in-the-Loop
            ConfirmacaoAcaoGatekeeper.TicketConfirmacao ticketExclusao = gatekeeper.criarTicket(
                    RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
                    Map.of("idProduto", idProdutoTeste),
                    "Exclusão definitiva do produto #" + idProdutoTeste + " do catálogo"
            );
            afirmar(ticketExclusao != null, "Ticket de confirmação gerado para exclusão do produto #" + idProdutoTeste);

            var respConfirmacao = gatekeeper.confirmarEExecutar(ticketExclusao.ticketId(), roteador);
            afirmar(respConfirmacao.sucesso(), "Exclusão do produto confirmada e executada com sucesso!");

            var prodAposExclusao = roteador.produtos().buscarPorId(idProdutoTeste);
            afirmar(prodAposExclusao == null, "Produto #" + idProdutoTeste + " não existe mais no catálogo (deletado com sucesso).");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES DA IA GEMINI APROVADOS COM SUCESSO!\n", testesAprovados, testesExecutados);
            System.out.println("================================================================================");

        } catch (Throwable t) {
            System.err.println("FALHA NO TESTE DA INTEGRAÇÃO COM IA GEMINI:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void afirmar(boolean condicao, String mensagem) {
        testesExecutados++;
        if (condicao) {
            testesAprovados++;
            System.out.println(" [OK] " + mensagem);
        } else {
            System.err.println(" [FALHA] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }
}
