package br.com.senac.integracao.ia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.gemini.GeminiConfig;
import br.com.senac.integracao.gemini.GeminiToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

/**
 * Gera o MANUAL AUTOEXPLICATIVO que ensina qualquer IA a operar o sistema:
 * papel, formato de resposta, regras semânticas (linguagem natural -> rota), exemplos e o catálogo
 * completo de rotas com parâmetros, tipos, obrigatoriedade, risco e exemplo de chamada.
 * O catálogo é gerado direto de {@link RotaSistema} + {@link GeminiToolRegistry}: rota nova = manual atualizado.
 */
public final class ManualInstrucoesIA {

    private static final ObjectMapper OM = new ObjectMapper();
    private static Map<String, Map<String, Object>> cache;

    private ManualInstrucoesIA() {
    }

    public static String gerar() {
        StringBuilder sb = new StringBuilder();
        sb.append("# MANUAL DO SISTEMA DE VENDAS SAZONALISEI - INSTRUÇÕES PARA IA\n\n");

        sb.append("## 1. Seu papel\n");
        sb.append("Você é o assistente operacional do Sistema de Vendas Sazonalisei. O usuário fala em português natural ")
          .append("(texto ou voz transcrita). Você traduz o pedido em AÇÕES, cada uma apontando para uma ROTA do sistema ")
          .append("(catálogo na seção 5). Você NÃO executa nada: quem executa e valida é o sistema.\n");
        GeminiConfig cfg = GeminiConfig.getInstancia();
        if (cfg.getPersonalidade() != null && !cfg.getPersonalidade().isBlank()) {
            sb.append("\nPersonalidade e tom da mensagem ao usuário: ").append(cfg.getPersonalidade().trim()).append("\n");
        }
        if (cfg.getInstrucoesCustomizadas() != null && !cfg.getInstrucoesCustomizadas().isBlank()) {
            sb.append("Instruções adicionais do usuário: ").append(cfg.getInstrucoesCustomizadas().trim()).append("\n");
        }
        sb.append("Data de hoje: ").append(LocalDate.now()).append(" (use para 'hoje', 'amanhã', 'daqui 30 dias').\n\n");

        sb.append("## 2. Formato OBRIGATÓRIO da resposta\n");
        sb.append("Responda SOMENTE com um JSON (pode estar dentro de um bloco ```json), sem texto fora dele:\n\n");
        sb.append("```json\n{\n  \"mensagem\": \"o que você entendeu e vai fazer, em português curto\",\n")
          .append("  \"acoes\": [\n    { \"rota\": \"NOME_DA_ROTA\", \"parametros\": { \"campo\": \"valor\" } }\n  ]\n}\n```\n\n");
        sb.append("- `rota`: exatamente um nome do catálogo (seção 5).\n");
        sb.append("- `parametros`: apenas os campos listados na rota. Os marcados como obrigatórios nunca podem faltar.\n");
        sb.append("- Se for só conversa, ou se faltar informação: `\"acoes\": []` e pergunte em `mensagem`.\n\n");

        sb.append("## 3. Regras semânticas (como entender o usuário)\n");
        sb.append("1. NUNCA invente IDs. Se o usuário citar só o nome (\"apagar o cliente Igor\"), use primeiro uma rota de busca/listagem ")
          .append("(ex: PESSOA_LISTAR_CLIENTES, PRODUTO_CATALOGO_BUSCAR) e, em `mensagem`, diga o que vai procurar. ")
          .append("Quando o usuário voltar com o ID ou o resultado, aí sim use a rota de ação.\n");
        sb.append("2. Ações destrutivas (risco ALTO: excluir, remover, cancelar, baixar perda) devem ser enviadas normalmente. ")
          .append("O SISTEMA pergunta a confirmação ao usuário e só executa após \"sim\". Não peça confirmação no JSON.\n");
        sb.append("3. Formatos: dinheiro, preço, quantidade e limite = TEXTO com ponto decimal (\"49.90\", \"1.000000\"); ")
          .append("IDs e contagens = número; datas = \"AAAA-MM-DD\"; verdadeiro/falso = true/false; CPF/CNPJ com pontuação.\n");
        sb.append("4. Pedidos independentes na mesma frase => várias ações na lista. ")
          .append("Pedidos que dependem do resultado de outro => envie só o primeiro passo.\n");
        sb.append("5. Em dúvida entre ler e alterar, escolha LER (busca/listagem). Nunca altere dados sem pedido claro.\n");
        sb.append("6. Mantenha `mensagem` curta, clara e sem termos técnicos (nada de nomes de rota para o usuário).\n\n");

        sb.append("### Dicionário rápido (frase do usuário -> rota)\n");
        sb.append("| O usuário diz | Rota |\n|---|---|\n");
        sb.append("| \"quanto custa / me mostra o produto 86\" | PRODUTO_CATALOGO_BUSCAR |\n");
        sb.append("| \"liste os produtos\" | PRODUTO_CATALOGO_LISTAR |\n");
        sb.append("| \"mude o preço do produto 86 para 120\" | PRODUTO_CATALOGO_ATUALIZAR_PRECO |\n");
        sb.append("| \"apague / delete / remova o produto 86\" | PRODUTO_CATALOGO_EXCLUIR |\n");
        sb.append("| \"liste meus clientes\" | PESSOA_LISTAR_CLIENTES |\n");
        sb.append("| \"apague o cliente 12\" | PESSOA_CLIENTE_EXCLUIR |\n");
        sb.append("| \"quanto tenho em estoque do produto 5\" | ESTOQUE_SALDO_CONSOLIDADO |\n\n");

        sb.append("## 4. Exemplos completos\n");
        sb.append("Usuário: \"qual o preço do produto 86?\"\n```json\n")
          .append("{\"mensagem\":\"Vou consultar o produto 86.\",\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_BUSCAR\",\"parametros\":{\"id\":86}}]}\n```\n");
        sb.append("Usuário: \"muda o preço do 86 pra 120 reais\"\n```json\n")
          .append("{\"mensagem\":\"Vou alterar o preço do produto 86 para R$ 120,00.\",\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_ATUALIZAR_PRECO\",\"parametros\":{\"idProduto\":86,\"novoPreco\":\"120.00\"}}]}\n```\n");
        sb.append("Usuário: \"apaga o produto 86\"\n```json\n")
          .append("{\"mensagem\":\"Vou excluir o produto 86 (o sistema vai pedir sua confirmação).\",\"acoes\":[{\"rota\":\"PRODUTO_CATALOGO_EXCLUIR\",\"parametros\":{\"idProduto\":86}}]}\n```\n");
        sb.append("Usuário: \"apaga o cliente Igor\" (sem ID)\n```json\n")
          .append("{\"mensagem\":\"Vou listar os clientes para localizar o Igor. Depois me diga o ID.\",\"acoes\":[{\"rota\":\"PESSOA_LISTAR_CLIENTES\",\"parametros\":{}}]}\n```\n\n");

        sb.append("## 5. Catálogo de rotas\n");
        Map<String, List<RotaSistema>> porModulo = new LinkedHashMap<>();
        for (RotaSistema r : RotaSistema.values()) {
            porModulo.computeIfAbsent(String.valueOf(r.getModulo()), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<RotaSistema>> e : porModulo.entrySet()) {
            sb.append("\n### Módulo: ").append(e.getKey()).append("\n");
            for (RotaSistema r : e.getValue()) {
                appendRota(sb, r);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendRota(StringBuilder sb, RotaSistema r) {
        GeminiToolRegistry.NivelRiscoOperacao risco = GeminiToolRegistry.classificarRisco(r);
        String rotulo = switch (risco) {
            case DESTRUTIVO -> "risco ALTO - exige confirmação";
            case WRITE_SEGURO -> "altera dados";
            case READ_ONLY -> "somente leitura";
        };
        sb.append("\n- **").append(r.name()).append("** (").append(rotulo).append(") - ")
          .append(r.getDescricaoOperacao()).append("\n");

        Map<String, Object> decl = indice().get(r.getNomeFuncaoGemini());
        Map<String, Object> params = decl != null ? (Map<String, Object>) decl.get("parameters") : null;
        Map<String, Object> props = params != null ? (Map<String, Object>) params.get("properties") : null;
        List<String> obrig = obrigatorios(r);

        if (props == null || props.isEmpty()) {
            sb.append("  - parâmetros: nenhum\n");
        } else {
            for (Map.Entry<String, Object> p : props.entrySet()) {
                Map<String, Object> def = (Map<String, Object>) p.getValue();
                sb.append("  - `").append(p.getKey()).append("` (").append(def.get("type"))
                  .append(obrig.contains(p.getKey()) ? ", OBRIGATÓRIO" : ", opcional").append("): ")
                  .append(def.get("description")).append("\n");
            }
        }
        sb.append("  - exemplo: `").append(exemploChamada(r, props, obrig)).append("`\n");
    }

    @SuppressWarnings("unchecked")
    private static String exemploChamada(RotaSistema r, Map<String, Object> props, List<String> obrig) {
        Map<String, Object> ex = new LinkedHashMap<>();
        if (props != null) {
            for (String campo : obrig) {
                if (props.get(campo) instanceof Map<?, ?> def) {
                    ex.put(campo, valorExemplo((Map<String, Object>) def));
                }
            }
        }
        Map<String, Object> chamada = new LinkedHashMap<>();
        chamada.put("rota", r.name());
        chamada.put("parametros", ex);
        try {
            return OM.writeValueAsString(chamada);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static Object valorExemplo(Map<String, Object> def) {
        String tipo = String.valueOf(def.get("type"));
        switch (tipo) {
            case "INTEGER":
                return 1;
            case "BOOLEAN":
                return true;
            case "ARRAY":
                Object it = def.get("items");
                return it instanceof Map<?, ?> m ? List.of(exemploObjeto((Map<String, Object>) m)) : List.of();
            case "OBJECT":
                return exemploObjeto(def);
            default:
                String d = String.valueOf(def.get("description"));
                if (d.contains("YYYY-MM-DD") || d.toLowerCase().contains("data")) return "2026-12-31";
                if (d.contains("R$") || d.toLowerCase().contains("preço") || d.toLowerCase().contains("quantidade")) return "10.00";
                return "texto";
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> exemploObjeto(Map<String, Object> schema) {
        Map<String, Object> r = new LinkedHashMap<>();
        Object props = schema.get("properties");
        Object req = schema.get("required");
        if (props instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                if (req instanceof List<?> rl && !rl.contains(e.getKey())) continue;
                r.put(String.valueOf(e.getKey()), valorExemplo((Map<String, Object>) e.getValue()));
            }
        }
        return r;
    }

    /** Nomes dos parâmetros obrigatórios da rota, conforme o schema enviado às IAs. */
    @SuppressWarnings("unchecked")
    public static List<String> obrigatorios(RotaSistema r) {
        Map<String, Object> decl = indice().get(r.getNomeFuncaoGemini());
        if (decl == null) return List.of();
        Map<String, Object> params = (Map<String, Object>) decl.get("parameters");
        if (params == null || !(params.get("required") instanceof List<?> l)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : l) out.add(String.valueOf(o));
        return out;
    }

    /** Parâmetros obrigatórios ausentes (ou em branco) na chamada. Lista vazia = chamada completa. */
    public static List<String> obrigatoriosFaltando(RotaSistema r, Map<String, Object> params) {
        List<String> faltando = new ArrayList<>();
        for (String campo : obrigatorios(r)) {
            Object v = params != null ? params.get(campo) : null;
            if (v == null || (v instanceof String s && s.isBlank())) faltando.add(campo);
        }
        return faltando;
    }

    private static synchronized Map<String, Map<String, Object>> indice() {
        if (cache == null) {
            cache = new HashMap<>();
            for (Map<String, Object> f : GeminiToolRegistry.gerarDeclaracoesTodasTools()) {
                cache.put((String) f.get("name"), f);
            }
        }
        return cache;
    }
}
