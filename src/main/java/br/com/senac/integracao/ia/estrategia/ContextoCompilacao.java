package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.RetornoChatBotUI;

/**
 * Contexto compartilhado com os serviços e estado de sessão necessários para a compilação e consulta.
 */
public record ContextoCompilacao(
        RoteadorController roteador,
        RetornoChatBotUI.FocoBuscaSessao foco
) {}
