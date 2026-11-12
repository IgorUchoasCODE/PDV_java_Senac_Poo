package br.com.senac.integracao.ia.estrategia;

import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.parser.NoInstrucao;

import java.util.Set;

/**
 * Estratégia de compilação e consulta para um domínio de entidades específico da DSL {@code $$}.
 */
public interface EstrategiaInstrucao {

    /** Conjunto de nomes de entidade (em maiúsculas) suportados por esta estratégia. */
    Set<String> entidadesSuportadas();

    /** Compilação de instrução C= (Create). */
    String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx);

    /** Compilação de instrução U= (Update). */
    String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx);

    /** Compilação de instrução D= (Delete). */
    String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx);

    /** Processamento de consulta imediata R= (Read). */
    String processarConsulta(NoInstrucao no, ContextoCompilacao ctx);
}
