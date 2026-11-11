package br.com.senac.integracao.ia;

import br.com.senac.integracao.gemini.GeminiConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builder de Contexto e Mensagens da IA (Princípio da Responsabilidade Única - SRP).
 * Centraliza a leitura de {@code config/ia-contexto.properties} (ou {@code classpath:/ia-contexto.properties}),
 * eliminando textos de contexto mocados no código das classes operacionais e traduzindo instruções {@code $$}
 * para linguagem natural compreensível tanto pelo usuário quanto por qualquer LLM.
 */
public final class ContextoIABuilder {

    private static final Properties PROPS = new Properties();
    private static volatile boolean carregado = false;

    private ContextoIABuilder() {
    }

    public static synchronized void carregarPropriedades() {
        PROPS.clear();
        try (InputStream cpIn = ContextoIABuilder.class.getResourceAsStream("/ia-contexto.properties")) {
            if (cpIn != null) {
                PROPS.load(new InputStreamReader(cpIn, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        File arqExterno = new File("config/ia-contexto.properties");
        if (arqExterno.exists()) {
            try (FileInputStream fIn = new FileInputStream(arqExterno)) {
                PROPS.load(new InputStreamReader(fIn, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        carregado = true;
    }

    public static String propriedade(String chave, String padrao) {
        if (!carregado) {
            carregarPropriedades();
        }
        String val = PROPS.getProperty(chave);
        return val != null && !val.isBlank() ? val.trim() : padrao;
    }

    /**
     * Lê um template parametrizado do arquivo {@code ia-contexto.properties} e formata com os argumentos.
     */
    public static String formatarPropriedade(String chave, String padrao, Object... args) {
        String template = propriedade(chave, padrao);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception e) {
            return template;
        }
    }

    /**
     * Constrói o objeto {@code system_instruction} a partir do arquivo de propriedades e das configurações do usuário.
     */
    public static Map<String, Object> construirSystemInstruction() {
        GeminiConfig cfg = GeminiConfig.getInstancia();
        StringBuilder sb = new StringBuilder();
        sb.append(propriedade("ia.contexto.papel",
                "Você é o Assistente Operacional do Sistema de Vendas Sazonalisei.")).append("\n\n");

        if (cfg.getPersonalidade() != null && !cfg.getPersonalidade().isBlank()) {
            sb.append("Personalidade e tom de voz: ").append(cfg.getPersonalidade().trim()).append("\n\n");
        }
        if (cfg.getInstrucoesCustomizadas() != null && !cfg.getInstrucoesCustomizadas().isBlank()) {
            sb.append("Instruções adicionais do usuário: ").append(cfg.getInstrucoesCustomizadas().trim()).append("\n\n");
        }

        sb.append("DIRETRIZES OPERACIONAIS:\n");
        sb.append(propriedade("ia.contexto.diretrizes", "")).append("\n\n");
        sb.append("Data atual do sistema: ").append(LocalDate.now()).append("\n");

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", sb.toString());
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("parts", Collections.singletonList(textPart));
        return sys;
    }

    /**
     * Traduz comandos encurtados {@code $$} (ex: {@code $$ C= #Produto, nm> Ovo, vl> 20, um> UN})
     * em uma frase fluida em português natural para que qualquer IA ou operador humano possa ler sem esforço.
     */
    public static String traduzirDslParaLinguagemNatural(String comandoDsl) {
        if (comandoDsl == null || comandoDsl.isBlank()) return "";
        String limpo = comandoDsl.trim();
        if (limpo.startsWith("$$")) {
            limpo = limpo.substring(2).trim();
        }

        String[] instrucoes = limpo.split(";");
        List<String> frases = new ArrayList<>();

        for (String inst : instrucoes) {
            String s = inst.trim();
            if (s.isEmpty()) continue;

            if (s.toLowerCase(Locale.ROOT).startsWith("abrir>janela") || s.toLowerCase(Locale.ROOT).startsWith("abrir > janela")) {
                frases.add("Abrir a janela gráfica JavaFX para '" + s.replace(">", " -> ") + "'");
                continue;
            }

            String verbo = "Consultar";
            Matcher mOp = Pattern.compile("(?i)^([CRUD])\\s*=").matcher(s);
            if (mOp.find()) {
                verbo = switch (mOp.group(1).toUpperCase(Locale.ROOT)) {
                    case "C" -> "Criar/Cadastrar";
                    case "R" -> "Buscar/Listar";
                    case "U" -> "Atualizar/Modificar";
                    case "D" -> "Excluir/Remover";
                    default -> "Processar";
                };
                s = s.substring(mOp.end()).trim();
            }

            String entidade = "registro";
            Matcher mEnt = Pattern.compile("#([A-Za-z0-9_]+)\\s*,?").matcher(s);
            if (mEnt.find()) {
                entidade = humanizarNomeEntidade(mEnt.group(1));
                s = (s.substring(0, mEnt.start()) + " " + s.substring(mEnt.end())).trim();
            }

            List<String> detalhes = new ArrayList<>();
            Matcher mAttr = Pattern.compile("(?:>\\s*([a-zA-Z0-9_]+)\\s+([^,;\\]]+)|([a-zA-Z0-9_]+)\\s*>\\s*([^,;\\]]+))").matcher(s);
            while (mAttr.find()) {
                String k = (mAttr.group(1) != null ? mAttr.group(1) : mAttr.group(3)).toLowerCase(Locale.ROOT);
                String v = (mAttr.group(2) != null ? mAttr.group(2) : mAttr.group(4)).trim();
                String rotulo = switch (k) {
                    case "nm" -> "nome '" + v + "'";
                    case "novo_nm" -> "novo nome '" + v + "'";
                    case "vl", "val", "vv" -> "preço de venda R$ " + v;
                    case "vc" -> "valor de custo R$ " + v;
                    case "um" -> "unidade de medida " + v.toUpperCase(Locale.ROOT);
                    case "q" -> "quantidade " + v;
                    case "dt" -> "data " + v;
                    case "id" -> "ID " + v;
                    case "tp" -> "tipo " + v;
                    case "doc", "reg" -> "documento " + v;
                    case "resp" -> ("isento".equalsIgnoreCase(v) ? "isentando o fornecedor de ressarcimento" : "com responsabilidade do fornecedor");
                    default -> k + " = " + v;
                };
                detalhes.add(rotulo);
            }

            if (detalhes.isEmpty()) {
                frases.add(verbo + " " + entidade);
            } else {
                frases.add(verbo + " " + entidade + " com " + String.join(", ", detalhes));
            }
        }

        return String.join("; e ", frases) + ".";
    }

    private static String humanizarNomeEntidade(String raw) {
        if (raw == null) return "registro";
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "PRODUTO", "PRODUTOFISICO" -> "produto";
            case "PRODUTODIGITAL" -> "produto digital";
            case "CONJUNTOPRODUTO" -> "kit de produtos";
            case "UM" -> "unidade de medida";
            case "PESSOA" -> "pessoa";
            case "PESSOAFISICA" -> "pessoa física";
            case "PESSOAJURIDICA" -> "pessoa jurídica";
            case "NOTACOMPRA" -> "nota de compra";
            case "NOTAVENDA" -> "nota de venda";
            case "NOTADEVOLUCAO" -> "nota de devolução";
            case "NOTAPERDA" -> "nota de perda";
            case "FORMAPAGAMENTO" -> "forma de pagamento";
            case "NOTAPAGAMENTO", "PAGAMENTOAVISTA", "PAGAMENTOAPRAZO", "PAGAMENTOPARCELADO" -> "pagamento";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }
}
