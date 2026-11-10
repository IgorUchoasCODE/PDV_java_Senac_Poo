package br.com.senac.integracao.gemini;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Motor de Concorrência Segura baseado em Virtual Threads (Java 21+) com Barreira Anti-Colisão.
 * Permite que a IA execute dezenas de tarefas e leituras simultâneas sem sobrecarregar a JVM.
 * 
 * Regras de Consistência:
 * 1. Consultas e Tarefas de Leitura (READ): 100% paralelas e livres de bloqueio.
 * 2. Operações Mutativas em Recursos Diferentes: Paralelas sem colisão.
 * 3. Operações no Mesmo Recurso (Mesmo ID de produto, lote ou cliente): Fila serializada por chave granular.
 */
public class ConcorrenciaSeguraEngine {

    private final ExecutorService virtualExecutor;
    private final ConcurrentHashMap<String, ReentrantLock> locksPorRecurso = new ConcurrentHashMap<>();

    private static ConcorrenciaSeguraEngine instancia;

    public static synchronized ConcorrenciaSeguraEngine getInstancia() {
        if (instancia == null) {
            instancia = new ConcorrenciaSeguraEngine();
        }
        return instancia;
    }

    public ConcorrenciaSeguraEngine() {
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Executa uma tarefa em uma Virtual Thread.
     * Se uma chave de recurso for informada (ex: 'PROD_10'), bloqueia apenas aquele recurso específico
     * durante a escrita, permitindo que todas as outras entidades continuem processando em paralelo.
     */
    public <T> CompletableFuture<T> submeterTarefa(Callable<T> tarefa, String chaveRecursoOpcional) {
        return CompletableFuture.supplyAsync(() -> {
            if (chaveRecursoOpcional == null || chaveRecursoOpcional.isBlank()) {
                try {
                    return tarefa.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }

            ReentrantLock lock = locksPorRecurso.computeIfAbsent(chaveRecursoOpcional, k -> new ReentrantLock());
            boolean adquiriu = false;
            try {
                adquiriu = lock.tryLock(5, TimeUnit.SECONDS);
                if (!adquiriu) {
                    throw new TimeoutException("Tempo limite excedido aguardando liberação do recurso concorrente: " + chaveRecursoOpcional);
                }
                return tarefa.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (adquiriu) {
                    lock.unlock();
                }
            }
        }, virtualExecutor);
    }

    /**
     * Executa um lote de tarefas concorrentes em paralelo e aguarda a conclusão de todas.
     */
    public <T> List<T> executarLoteParalelo(List<Callable<T>> tarefas) throws InterruptedException, ExecutionException {
        List<CompletableFuture<T>> futures = tarefas.stream()
                .map(t -> submeterTarefa(t, null))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    public ExecutorService getVirtualExecutor() {
        return virtualExecutor;
    }
}
