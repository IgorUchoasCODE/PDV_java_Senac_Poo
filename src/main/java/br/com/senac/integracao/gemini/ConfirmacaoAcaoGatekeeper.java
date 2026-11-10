package br.com.senac.integracao.gemini;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gatekeeper responsável pelo protocolo de Confirmação em 2 Etapas (Human-in-the-Loop).
 * Retém temporariamente ações destrutivas ou de alto risco, exigindo confirmação explícita
 * do usuário antes de encaminhar a execução física ao RoteadorController.
 */
public class ConfirmacaoAcaoGatekeeper {

    private static final Duration TTL_TICKET = Duration.ofMinutes(5);
    private final Map<String, TicketConfirmacao> ticketsPendentes = new ConcurrentHashMap<>();

    private static ConfirmacaoAcaoGatekeeper instancia;

    public static synchronized ConfirmacaoAcaoGatekeeper getInstancia() {
        if (instancia == null) {
            instancia = new ConfirmacaoAcaoGatekeeper();
        }
        return instancia;
    }

    /**
     * Cria e registra um ticket de confirmação para uma operação pendente.
     */
    public TicketConfirmacao criarTicket(RotaSistema rota, Map<String, Object> parametros, String resumoImpacto) {
        limparExpirados();
        String ticketId = "CONFIRM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TicketConfirmacao ticket = new TicketConfirmacao(ticketId, rota, parametros, resumoImpacto, LocalDateTime.now());
        ticketsPendentes.put(ticketId, ticket);
        return ticket;
    }

    /**
     * Confirma e executa fisicamente a ação através do RoteadorController caso o ticket seja válido e não esteja expirado.
     */
    public RespostaEndpoint<Object> confirmarEExecutar(String ticketId, RoteadorController roteador) {
        TicketConfirmacao ticket = ticketsPendentes.remove(ticketId);
        if (ticket == null) {
            return RespostaEndpoint.erro("/confirmacao/executar", "Ticket de confirmação não encontrado ou já executado: " + ticketId);
        }

        if (ticket.isExpirado(TTL_TICKET)) {
            return RespostaEndpoint.erro(ticket.rota().getCaminhoRota(), "O ticket de confirmação expirou. Por segurança, repita a solicitação.");
        }

        // Executa com segurança na camada de controle oficial
        return roteador.executarEndpoint(ticket.rota(), ticket.parametros());
    }

    /**
     * Cancela e descarta um ticket pendente a pedido do usuário.
     */
    public boolean cancelarTicket(String ticketId) {
        return ticketsPendentes.remove(ticketId) != null;
    }

    public TicketConfirmacao buscarTicket(String ticketId) {
        return ticketsPendentes.get(ticketId);
    }

    private void limparExpirados() {
        ticketsPendentes.entrySet().removeIf(entry -> entry.getValue().isExpirado(TTL_TICKET));
    }

    public record TicketConfirmacao(
            String ticketId,
            RotaSistema rota,
            Map<String, Object> parametros,
            String resumoImpacto,
            LocalDateTime dataCriacao
    ) {
        public boolean isExpirado(Duration ttl) {
            return LocalDateTime.now().isAfter(dataCriacao.plus(ttl));
        }

        public String formatarMensagemParaUsuario() {
            return String.format(
                    "ATENÇÃO: A operação '%s' possui impacto permanente.\n" +
                    "Resumo: %s\n" +
                    "Você confirma a execução desta ação? (Código: %s)",
                    rota.getCaminhoRota(),
                    resumoImpacto,
                    ticketId
            );
        }
    }
}
