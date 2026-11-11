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
        sb.append(ContextoIABuilder.propriedade("ia.manual.titulo",
                "# MANUAL DO SISTEMA DE VENDAS SAZONALISEI - INSTRUÇÕES PARA IA")).append("\n\n");

        sb.append("## 1. Seu papel\n");
        sb.append(ContextoIABuilder.propriedade("ia.contexto.papel",
                "Você é o assistente operacional do Sistema de Vendas Sazonalisei.")).append("\n");
        GeminiConfig cfg = GeminiConfig.getInstancia();
        if (cfg.getPersonalidade() != null && !cfg.getPersonalidade().isBlank()) {
            sb.append("\nPersonalidade e tom da mensagem ao usuário: ").append(cfg.getPersonalidade().trim()).append("\n");
        }
        if (cfg.getInstrucoesCustomizadas() != null && !cfg.getInstrucoesCustomizadas().isBlank()) {
            sb.append("Instruções adicionais do usuário: ").append(cfg.getInstrucoesCustomizadas().trim()).append("\n");
        }
        sb.append("Data de hoje: ").append(LocalDate.now()).append(".\n\n");

        sb.append("## 2. Formato OBRIGATÓRIO da resposta\n");
        sb.append(ContextoIABuilder.propriedade("ia.manual.formato.json",
                "Responda SOMENTE com um JSON contendo mensagem e acoes.")).append("\n\n");
        sb.append("```json\n{\n  \"mensagem\": \"o que você entendeu e vai fazer, em português curto\",\n")
          .append("  \"acoes\": [\n    { \"rota\": \"NOME_DA_ROTA\", \"parametros\": { \"campo\": \"valor\" } }\n  ]\n}\n```\n\n");

        sb.append("## 3. Regras semânticas e Leitura de Comandos $$\n");
        sb.append(ContextoIABuilder.propriedade("ia.contexto.diretrizes", "")).append("\n\n");

        sb.append("## 4. Catálogo de rotas\n");
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
            if (ex.isEmpty() && props.containsKey("idProduto")) {
                ex.put("idProduto", 1);
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
        if (r == null) return faltando;

        for (String campo : obrigatorios(r)) {
            Object v = params != null ? params.get(campo) : null;
            if (v == null || (v instanceof String s && s.isBlank())) faltando.add(campo);
        }

        // Validações semânticas adicionais para rotas que aceitam identificação por ID ou Nome:
        if (r == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR) {
            boolean temIdentificador = (params != null && params.get("idProduto") != null) ||
                    (params != null && params.get("nomeAtual") != null && !params.get("nomeAtual").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("idProduto");
            }
            boolean temDadoAtualizacao = (params != null && params.get("novoNome") != null && !params.get("novoNome").toString().isBlank()) ||
                    (params != null && params.get("nome") != null && !params.get("nome").toString().isBlank()) ||
                    (params != null && params.get("descricao") != null && !params.get("descricao").toString().isBlank());
            if (!temDadoAtualizacao) {
                faltando.add("novoNome");
            }
        } else if (r == RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO) {
            boolean temIdentificador = (params != null && params.get("idProduto") != null) ||
                    (params != null && params.get("nomeProduto") != null && !params.get("nomeProduto").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("idProduto");
            }
        } else if (r == RotaSistema.PRODUTO_CATALOGO_EXCLUIR) {
            boolean temIdentificador = (params != null && params.get("idProduto") != null) ||
                    (params != null && params.get("nomeProduto") != null && !params.get("nomeProduto").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("idProduto");
            }
        } else if (r == RotaSistema.PRODUTO_BUSCAR_UNICO || r == RotaSistema.PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO) {
            boolean temIdentificador = (params != null && (params.get("id") != null || params.get("idProduto") != null)) ||
                    (params != null && params.get("nome") != null && !params.get("nome").toString().isBlank()) ||
                    (params != null && params.get("termo") != null && !params.get("termo").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("nome");
            }
        } else if (r == RotaSistema.PESSOA_BUSCAR || r == RotaSistema.PESSOA_BUSCAR_UNICA) {
            boolean temIdentificador = (params != null && (params.get("idPessoa") != null || params.get("id") != null)) ||
                    (params != null && params.get("termo") != null && !params.get("termo").toString().isBlank()) ||
                    (params != null && params.get("nome") != null && !params.get("nome").toString().isBlank()) ||
                    (params != null && params.get("documento") != null && !params.get("documento").toString().isBlank()) ||
                    (params != null && params.get("registro") != null && !params.get("registro").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("termo");
            }
        } else if (r == RotaSistema.PESSOA_REGISTRO_BUSCAR_UNICO) {
            boolean temIdentificador = (params != null && params.get("numeroRegistro") != null && !params.get("numeroRegistro").toString().isBlank()) ||
                    (params != null && params.get("documento") != null && !params.get("documento").toString().isBlank()) ||
                    (params != null && params.get("registro") != null && !params.get("registro").toString().isBlank()) ||
                    (params != null && params.get("termo") != null && !params.get("termo").toString().isBlank());
            if (!temIdentificador) {
                faltando.add("numeroRegistro");
            }
        } else if (r == RotaSistema.NOTA_BUSCAR_UNICA) {
            boolean temIdentificador = params != null && (params.get("idNota") != null || params.get("id") != null);
            if (!temIdentificador) {
                faltando.add("idNota");
            }
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
