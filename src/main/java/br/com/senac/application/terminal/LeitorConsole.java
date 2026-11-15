package br.com.senac.application.terminal;

/**
 * Abstração (DIP/ISP) para leitura de entrada e detecção de largura no terminal.
 * Desacopla {@link br.com.senac.application.ChatBotTerminal} do modo TTY em tempo real ou fallback {@code Scanner}.
 */
public interface LeitorConsole extends AutoCloseable {

    boolean isTempoReal();

    int detectarLarguraAtual();

    String lerEntrada(String textoInicial);

    String lerLinhaSimples();

    boolean detectarCancelamentoNaoBloqueante();

    void descartarEntradaPendente();

    @Override
    void close();
}
