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

    public static DecisaoConfirmacao interpretar(String msg) {
        if (msg == null || msg.isBlank()) return INDEFINIDA;
        String n = Normalizer.normalize(msg, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        boolean positiva = false;
        for (String tok : n.split("[^A-Z]+")) {
            if (NEGATIVAS.contains(tok)) return NAO;
            if (POSITIVAS.contains(tok)) positiva = true;
        }
        return positiva ? SIM : INDEFINIDA;
    }
}
