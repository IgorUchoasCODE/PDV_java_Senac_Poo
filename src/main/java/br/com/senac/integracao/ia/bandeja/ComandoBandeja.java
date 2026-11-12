package br.com.senac.integracao.ia.bandeja;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comando de navegação da bandeja escrito em linguagem simples ("próxima", "executar OP-2",
 * "pular 3"). É interpretado <b>antes</b> de chamar a IA, então não gasta requisição.
 *
 * <p>Um "executar" sozinho continua significando "confirmar tudo" (tratado por
 * {@code DecisaoConfirmacao}); aqui só entram frases que apontam uma instrução ou o passo seguinte.
 */
public record ComandoBandeja(Acao acao, String alvo) {

    public enum Acao {
        EXECUTAR_ITEM, EXECUTAR_PROXIMO, PULAR, RETOMAR, ANTERIOR, IR_PARA, VER
    }

    private static final String ALVO = "(?:item\\s+|instrucao\\s+|op\\s*-?\\s*)?(\\d+)";

    private static final Pattern EXECUTAR_ITEM =
            Pattern.compile("^(?:executar|executa|rodar|roda|salvar|salva|confirmar|confirma)\\s+(?:a\\s+|o\\s+)?" + ALVO + "$");
    private static final Pattern EXECUTAR_PROXIMO =
            Pattern.compile("^(?:(?:executar|executa|rodar|roda)\\s+)?(?:a\\s+|o\\s+)?(?:proximo|proxima|seguinte)(?:\\s+item|\\s+instrucao)?$");
    private static final Pattern PULAR =
            Pattern.compile("^(?:pular|pula|pule)(?:\\s+(?:a\\s+|o\\s+)?" + ALVO + ")?$");
    private static final Pattern RETOMAR =
            Pattern.compile("^(?:retomar|retoma|voltar a executar)\\s+(?:a\\s+|o\\s+)?" + ALVO + "$");
    private static final Pattern ANTERIOR =
            Pattern.compile("^(?:anterior|voltar|volta|item anterior|instrucao anterior)$");
    private static final Pattern IR_PARA =
            Pattern.compile("^(?:ir para|vai para|ver|mostrar|mostra|abrir)\\s+(?:a\\s+|o\\s+)?" + ALVO + "$");
    private static final Pattern VER =
            Pattern.compile("^(?:ver bandeja|mostrar bandeja|mostra bandeja|bandeja|o que falta|o que tem na bandeja)$");

    public static Optional<ComandoBandeja> interpretar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) return Optional.empty();
        String t = Normalizer.normalize(mensagem, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[.!?]+$", "")
                .replaceAll("\\s+", " ");

        Matcher m = EXECUTAR_ITEM.matcher(t);
        if (m.matches()) return Optional.of(new ComandoBandeja(Acao.EXECUTAR_ITEM, idOp(m.group(1))));
        if (EXECUTAR_PROXIMO.matcher(t).matches()) return Optional.of(new ComandoBandeja(Acao.EXECUTAR_PROXIMO, null));
        m = PULAR.matcher(t);
        if (m.matches()) return Optional.of(new ComandoBandeja(Acao.PULAR, idOp(m.group(1))));
        m = RETOMAR.matcher(t);
        if (m.matches()) return Optional.of(new ComandoBandeja(Acao.RETOMAR, idOp(m.group(1))));
        if (ANTERIOR.matcher(t).matches()) return Optional.of(new ComandoBandeja(Acao.ANTERIOR, null));
        m = IR_PARA.matcher(t);
        if (m.matches()) return Optional.of(new ComandoBandeja(Acao.IR_PARA, idOp(m.group(1))));
        if (VER.matcher(t).matches()) return Optional.of(new ComandoBandeja(Acao.VER, null));
        return Optional.empty();
    }

    private static String idOp(String numero) {
        return numero == null ? null : "OP-" + Integer.parseInt(numero);
    }
}
