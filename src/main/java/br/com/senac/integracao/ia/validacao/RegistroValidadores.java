package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;

import java.util.List;
import java.util.Optional;

/**
 * Registro dos validadores disponíveis. Para conferir uma rota nova, basta criar uma
 * implementação de {@link ValidadorExecucao} e acrescentá-la aqui (Open/Closed).
 */
public class RegistroValidadores {

    private final List<ValidadorExecucao> validadores;

    public RegistroValidadores(List<ValidadorExecucao> validadores) {
        this.validadores = List.copyOf(validadores);
    }

    public static RegistroValidadores padrao() {
        return new RegistroValidadores(List.of(new ValidadorProduto(), new ValidadorPessoa()));
    }

    /** Validador responsável pela rota, se existir. Rotas sem validador executam sem conferência extra. */
    public Optional<ValidadorExecucao> para(RotaSistema rota) {
        return validadores.stream().filter(v -> v.aplicaA(rota)).findFirst();
    }
}
