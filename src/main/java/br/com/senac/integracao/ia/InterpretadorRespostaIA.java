package br.com.senac.integracao.ia;

import br.com.senac.integracao.ia.ProvedorIA.ChamadaFerramenta;
import br.com.senac.integracao.ia.ProvedorIA.RespostaIA;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converte o texto devolvido por QUALQUER IA (Gemini do celular, ChatGPT, Claude, modelo local...)
 * no formato do manual ({"mensagem": "...", "acoes": [{"rota": "...", "parametros": {...}}]}).
 * É tolerante: aceita bloco ```json, texto antes/depois, lista na raiz e nomes de campo alternativos.
 * Se não houver JSON, trata a resposta como texto simples (a IA só conversou).
 */
public final class InterpretadorRespostaIA {

    private static final ObjectMapper OM = new ObjectMapper();

    private InterpretadorRespostaIA() {
    }

    public static RespostaIA interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("A resposta da IA está vazia. Cole o JSON que ela devolveu.");
        }
        String t = texto.trim();
        int ini = primeiroIndice(t);
        if (ini < 0) {
            return new RespostaIA(t, List.of());
        }
        char abre = t.charAt(ini);
        int fim = t.lastIndexOf(abre == '{' ? '}' : ']');
        if (fim <= ini) {
            throw new IllegalArgumentException("O JSON da IA está incompleto (faltou fechar '" + (abre == '{' ? '}' : ']') + "'). Peça para ela responder de novo.");
        }

        JsonNode raiz;
        try {
            raiz = OM.readTree(t.substring(ini, fim + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("O JSON da IA é inválido. Peça para ela responder somente com o JSON do manual.");
        }

        String mensagem = "";
        JsonNode acoes;
        if (raiz.isArray()) {
            acoes = raiz;
        } else {
            mensagem = primeiroTexto(raiz, "mensagem", "message", "resposta");
            acoes = primeiroNo(raiz, "acoes", "ações", "actions", "calls", "tool_calls");
        }

        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        if (acoes != null && acoes.isArray()) {
            for (JsonNode a : acoes) {
                String rota = primeiroTexto(a, "rota", "tool", "nome", "name", "funcao");
                if (rota.isBlank()) {
                    throw new IllegalArgumentException("Uma das ações não informa o campo \"rota\".");
                }
                JsonNode p = primeiroNo(a, "parametros", "parâmetros", "params", "args", "arguments");
                Map<String, Object> params = (p != null && p.isObject())
                        ? OM.convertValue(p, new TypeReference<LinkedHashMap<String, Object>>() {
                })
                        : new LinkedHashMap<>();
                chamadas.add(new ChamadaFerramenta(rota.trim(), params));
            }
        }
        return new RespostaIA(mensagem, chamadas);
    }

    private static int primeiroIndice(String t) {
        int a = t.indexOf('{');
        int b = t.indexOf('[');
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static String primeiroTexto(JsonNode n, String... campos) {
        JsonNode v = primeiroNo(n, campos);
        return v != null && !v.isNull() ? v.asText("") : "";
    }

    private static JsonNode primeiroNo(JsonNode n, String... campos) {
        for (String c : campos) {
            if (n.has(c) && !n.get(c).isNull()) return n.get(c);
        }
        return null;
    }
}
