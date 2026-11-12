package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.ItemBandeja;

import java.util.Map;

/**
 * Estratégia de conferência de uma instrução da bandeja (C=, U=, D=).
 *
 * <p>Cada implementação cuida de um grupo de rotas. A conferência é feita por <b>consultas</b>
 * ao banco (as mesmas que o {@code R=} usa), em dois momentos:
 * <ol>
 *   <li>{@link #antes}: o alvo existe? os dados fazem sentido? Se reprovar, a instrução não executa.</li>
 *   <li>{@link #depois}: o banco ficou como a instrução prometia?</li>
 * </ol>
 *
 * <p>O {@code estadoAntes} devolvido por {@link #capturarEstadoAntes} é repassado ao {@link #depois},
 * permitindo comparar "como estava" com "como ficou".
 */
public interface ValidadorExecucao {

    /** Informa se este validador cuida da rota. */
    boolean aplicaA(RotaSistema rota);

    /** Foto do registro antes de executar (pode ser vazia). */
    default Map<String, Object> capturarEstadoAntes(ItemBandeja item, RoteadorController roteador) {
        return Map.of();
    }

    /** Conferência antes de executar. */
    default ResultadoValidacao antes(ItemBandeja item, RoteadorController roteador) {
        return ResultadoValidacao.naoSeAplica();
    }

    /** Conferência depois de executar. */
    default ResultadoValidacao depois(ItemBandeja item, Object resultado,
                                      Map<String, Object> estadoAntes, RoteadorController roteador) {
        return ResultadoValidacao.naoSeAplica();
    }
}
