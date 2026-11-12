package br.com.senac.integracao.ia;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Catálogo centralizado de mensagens do sistema e da IA (Zero Hardcoded Strings).
 * Carrega as chaves do arquivo {@code ia-contexto.properties} com encoding UTF-8.
 */
public final class CatalogoMensagens {

    private static final String ARQUIVO_PROPERTIES = "ia-contexto.properties";
    private static final Properties PROPS = new Properties();

    static {
        carregarPropriedades();
    }

    private CatalogoMensagens() {}

    private static synchronized void carregarPropriedades() {
        try (InputStream is = CatalogoMensagens.class.getClassLoader().getResourceAsStream(ARQUIVO_PROPERTIES)) {
            if (is != null) {
                try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    PROPS.load(isr);
                }
            }
        } catch (Exception ignored) {
            // Mantém fallback limpo caso arquivo não seja encontrado
        }
    }

    public static String obter(String chave) {
        return obter(chave, chave);
    }

    public static String obter(String chave, String padrao) {
        if (chave == null) return padrao;
        String val = PROPS.getProperty(chave);
        return (val != null && !val.isBlank()) ? val : padrao;
    }

    public static String formatar(String chave, String padrao, Object... args) {
        String template = obter(chave, padrao);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    // ==================== MENSAGENS ESPECÍFICAS DE CONJUNTO/KIT ====================

    public static String erroConjuntoSemComponentes(String nomeKit) {
        return formatar("ia.erro.conjunto.sem_componentes",
                "O conjunto '%s' precisa conter ao menos um componente com proporção definida.", nomeKit);
    }

    public static String erroConjuntoProporcaoInvalida(String qtd, String comp) {
        return formatar("ia.erro.conjunto.proporcao_invalida",
                "A quantidade '%s' do componente '%s' é inválida.", qtd, comp);
    }

    public static String erroConjuntoCiclo(String nome) {
        return formatar("ia.erro.conjunto.ciclo_recursivo",
                "Não é permitido adicionar o produto '%s' como componente dele mesmo.", nome);
    }

    public static String erroConjuntoComponenteNaoEncontrado(String comp, String kit) {
        return formatar("ia.erro.conjunto.componente_nao_encontrado",
                "Não encontrei o produto componente '%s' para inclusão no conjunto '%s'.", comp, kit);
    }

    public static String erroConjuntoDuplicado(String nome, String um) {
        return formatar("ia.erro.conjunto.duplicado",
                "Já existe um conjunto/kit cadastrado com o nome '%s' na unidade %s.", nome, um);
    }

    // ==================== MENSAGENS ESPECÍFICAS DE NOTA COMPLETA ====================

    public static String notaSucesso(String idItem, String tipo, String pessoa, int itens, String total, String totalPago) {
        return formatar("ia.nota.sucesso_preparacao",
                "[%s] Nota de %s para '%s' preparada (%d itens, Total: R$ %s, Pagamentos: R$ %s).",
                idItem, tipo, pessoa, itens, total, totalPago);
    }

    public static String erroNotaPagamentoInsuficiente(String pago, String total, String falta) {
        return formatar("ia.nota.pagamento_insuficiente",
                "O valor pago (R$ %s) é inferior ao total da nota (R$ %s). Faltam R$ %s.", pago, total, falta);
    }

    public static String notaTrocoCalculado(String troco) {
        return formatar("ia.nota.troco_calculado",
                "Pagamento em dinheiro com excedente: Troco a devolver = R$ %s.", troco);
    }

    public static String erroNotaTrocoApenasDinheiro(String pago, String total) {
        return formatar("ia.nota.troco_apenas_dinheiro",
                "O valor pago (R$ %s) excede o total da nota (R$ %s), mas troco só é permitido para DINHEIRO.", pago, total);
    }

    public static String erroNotaEstoqueInsuficiente(String prod, Long id, String pedido, String um, String disp) {
        return formatar("ia.nota.estoque_insuficiente",
                "Estoque insuficiente para '%s' (#%d): solicitado %s %s, disponível %s %s.", prod, id, pedido, um, disp, um);
    }

    public static String erroNotaLimiteCredito(String valor, String cliente, String limite) {
        return formatar("ia.nota.limite_credito_excedido",
                "Venda a prazo de R$ %s excede o limite de crédito do cliente '%s' (Limite: R$ %s).", valor, cliente, limite);
    }
}
