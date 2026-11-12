package br.com.senac.integracao.ia.registro;

import br.com.senac.integracao.ia.estrategia.*;

import java.util.*;

/**
 * Registro central de estratégias da DSL de acordo com a entidade alvo (Open/Closed Principle).
 */
public class RegistroEstrategias {

    private final Map<String, EstrategiaInstrucao> mapa = new HashMap<>();

    public RegistroEstrategias(List<EstrategiaInstrucao> estrategias) {
        if (estrategias != null) {
            for (EstrategiaInstrucao e : estrategias) {
                for (String ent : e.entidadesSuportadas()) {
                    mapa.put(ent.toUpperCase(Locale.ROOT), e);
                }
            }
        }
    }

    public static RegistroEstrategias criarPadrao() {
        return new RegistroEstrategias(List.of(
                new EstrategiaProduto(),
                new EstrategiaConjunto(),
                new EstrategiaPessoa(),
                new EstrategiaEstoque(),
                new EstrategiaNota(),
                new EstrategiaPagamento(),
                new EstrategiaEstatistica()
        ));
    }

    public Optional<EstrategiaInstrucao> obter(String entidade) {
        if (entidade == null) return Optional.empty();
        return Optional.ofNullable(mapa.get(entidade.trim().toUpperCase(Locale.ROOT)));
    }

    public boolean suporta(String entidade) {
        if (entidade == null) return false;
        return mapa.containsKey(entidade.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> entidadesValidas() {
        return Collections.unmodifiableSet(mapa.keySet());
    }
}
