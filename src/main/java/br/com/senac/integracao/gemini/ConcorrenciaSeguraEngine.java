package br.com.senac.integracao.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Motor de execução SÍNCRONA com trava granular por recurso.
 *
 * <p>Antes executava em Virtual Threads, o que fazia consultas competirem com a bandeja de contexto
 * e quebrava a ordem das instruções. Agora toda tarefa roda na thread chamadora, na ordem em que
 * foi submetida, e o {@link CompletableFuture} devolvido já vem concluído (mantém a API anterior).
 *
 * <p>Regras de consistência:
 * <ol>
 *   <li>Consultas (sem chave de recurso): executam direto, sem trava.</li>
 *   <li>Operações de escrita com chave (ex.: {@code PROD_10}): serializadas pela trava daquele
 *       recurso, sem bloquear os demais.</li>
 * </ol>
 */
public class ConcorrenciaSeguraEngine {

    private static final long TIMEOUT_LOCK_SEGUNDOS = 5;

    private final ConcurrentHashMap<String, ReentrantLock> locksPorRecurso = new ConcurrentHashMap<>();

    private static ConcorrenciaSeguraEngine instancia;

    public static synchronized ConcorrenciaSeguraEngine getInstancia() {
        if (instancia == null) {
            instancia = new ConcorrenciaSeguraEngine();
        }
        return instancia;
    }

    public ConcorrenciaSeguraEngine() {
    }

    /**
     * Executa a tarefa na thread atual e devolve o resultado em um future já concluído
     * (normalmente ou com falha, no mesmo formato do contrato anterior).
     */
    public <T> CompletableFuture<T> submeterTarefa(Callable<T> tarefa, String chaveRecursoOpcional) {
        try {
            return CompletableFuture.completedFuture(executar(tarefa, chaveRecursoOpcional));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e instanceof CompletionException ? e : new CompletionException(e));
        }
    }

    /** Executa a tarefa e devolve o resultado direto, propagando a exceção original. */
    public <T> T executar(Callable<T> tarefa, String chaveRecursoOpcional) throws Exception {
        if (chaveRecursoOpcional == null || chaveRecursoOpcional.isBlank()) {
            return tarefa.call();
        }
        ReentrantLock lock = locksPorRecurso.computeIfAbsent(chaveRecursoOpcional, k -> new ReentrantLock());
        boolean adquiriu = false;
        try {
            adquiriu = lock.tryLock(TIMEOUT_LOCK_SEGUNDOS, TimeUnit.SECONDS);
            if (!adquiriu) {
                throw new TimeoutException("Tempo limite excedido aguardando liberação do recurso concorrente: "
                        + chaveRecursoOpcional);
            }
            return tarefa.call();
        } finally {
            if (adquiriu) {
                lock.unlock();
            }
        }
    }

    /** Executa um lote de tarefas em sequência, preservando a ordem. */
    public <T> List<T> executarLoteSequencial(List<Callable<T>> tarefas) throws Exception {
        List<T> resultados = new ArrayList<>(tarefas.size());
        for (Callable<T> t : tarefas) {
            resultados.add(executar(t, null));
        }
        return resultados;
    }

    /** Mantido por compatibilidade: agora é sequencial. */
    public <T> List<T> executarLoteParalelo(List<Callable<T>> tarefas) throws Exception {
        return executarLoteSequencial(tarefas);
    }
}
