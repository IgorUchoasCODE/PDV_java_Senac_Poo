package br.com.senac.integracao.ia;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Interpreta a resposta do usuário a um pedido de confirmação.
 * Regra de segurança: qualquer negação vence ("não, pode cancelar" = NÃO). Na dúvida, NÃO executa.
 */
public enum DecisaoConfirmacao {
    SIM, NAO, INDEFINIDA;

    private static final Set<String> NEGATIVAS = Set.of(
            "NAO", "NUNCA", "JAMAIS", "CANCELA", "CANCELAR", "CANCELE", "DEIXA", "DEIXE",
            "PARE", "ESPERA", "ESPERE", "DESISTI", "DESISTO", "ERRADO");
    private static final Set<String> POSITIVAS = Set.of(
            "SIM", "CONFIRMO", "CONFIRMA", "CONFIRMAR", "CONFIRMADO", "OK", "PODE", "AUTORIZO",
            "ISSO", "EXATO", "POSITIVO", "CLARO", "SALVAR", "SALVA", "EXECUTA", "EXECUTAR", "CONCLUIR", "CONCLUA");

    private static final Set<String> CANCELAMENTOS_EXPLICITOS = Set.of(
            "CANCELA", "CANCELAR", "CANCELE", "DESISTI", "DESISTO");

    private static final java.util.regex.Pattern INTENCAO_EDICAO_OU_NOVA_ACAO = java.util.regex.Pattern.compile(
            ".*\\b(QUERO|MODIFI\\w*|MUD\\w*|ALTER\\w*|TROC\\w*|CORRIG\\w*|AJUST\\w*|ARRUM\\w*|CONSERT\\w*|REMOV\\w*|TIR\\w*|ADICION\\w*|COLOC\\w*|CRI[AE]\\w*|ATUALIZ\\w*|EDIT\\w*|SUBSTITU\\w*|REFA[CZ]\\w*|OP-?\\d+|EM VEZ|AO INVES)\\b.*",
            java.util.regex.Pattern.DOTALL);

    private static final java.util.regex.Pattern INTENCAO_PERGUNTA_OU_SIMULACAO = java.util.regex.Pattern.compile(
            ".*\\b(QUANTO|QUANTOS|QUANTAS|COMO|QUAL|QUAIS|ONDE|PORQUE|POR\\s+QUE|SERA|SE|CASO|AO|QUANDO|SIMUL\\w*|CALCULA\\w*|ESTIMA\\w*|PREVIS\\w*|LUCRO\\w*|LUCR\\w*|MARGEM\\w*|CUSTO\\w*|PREJUIZ\\w*|IMPACTO\\w*|EXPLIC\\w*|ENTEND\\w*|DUVID\\w*|PERGUNT\\w*|FICARIA|SERIA|IRIA|TERIA|DARIA|VALERIA|CUSTARIA|GANHARIA|PERDERIA|ACHA|ANTES|DEPOIS|AINDA|MAS|POREM|CONTUDO|ENTRETANTO|APENAS|SOMENTE)\\b.*",
            java.util.regex.Pattern.DOTALL);

    private static final java.util.regex.Pattern VARIANTE_SIM = java.util.regex.Pattern.compile("^S{1,2}I{1,2}M{1,3}$");

    public static DecisaoConfirmacao interpretar(String msg) {
        if (msg == null || msg.isBlank()) return INDEFINIDA;
        String n = Normalizer.normalize(msg, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        if ("N".equals(n)) return NAO;
        if ("S".equals(n)) return SIM;
        boolean ehPerguntaOuSimulacao = n.endsWith("?") || INTENCAO_PERGUNTA_OU_SIMULACAO.matcher(n).matches();
        boolean negativa = false;
        boolean cancelamentoExplicito = false;
        boolean positiva = false;
        String[] tokens = n.split("[^A-Z0-9-]+");
        int qtdPalavras = 0;
        for (String tok : tokens) {
            if (tok.isBlank()) continue;
            qtdPalavras++;
            if (CANCELAMENTOS_EXPLICITOS.contains(tok)) cancelamentoExplicito = true;
            if (NEGATIVAS.contains(tok)) negativa = true;
            if (POSITIVAS.contains(tok) || (qtdPalavras <= 2 && VARIANTE_SIM.matcher(tok).matches())) positiva = true;
        }
        if (negativa) {
            // Se o usuário disse "não, quero que modifique..." ou "não entendi...", não cancela a bandeja.
            if (!cancelamentoExplicito && (ehPerguntaOuSimulacao
                    || (qtdPalavras > 4 && INTENCAO_EDICAO_OU_NOVA_ACAO.matcher(n).matches()))) {
                return INDEFINIDA;
            }
            return NAO;
        }
        if (positiva && (ehPerguntaOuSimulacao || INTENCAO_EDICAO_OU_NOVA_ACAO.matcher(n).matches())) {
            return INDEFINIDA;
        }
        return positiva ? SIM : INDEFINIDA;
    }
}
