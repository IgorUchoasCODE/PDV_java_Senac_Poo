package br.com.senac.repository.dao;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrato padronizado para todos os DAOs da camada de persistência:
 * 1. Operações CRUD completas (salvar, atualizar, buscarPorId, listarTodos, deletar)
 * 2. Fallback automático para reação e recuperação segura em caso de falhas transacionais ou de consulta
 * 3. Bloqueio de informações sensíveis/em transição para a Bandeja de Contexto da IA (evitando colisão ou vazamento concorrente)
 */
public interface PadraoDAO<T, ID> {

    /**
     * Registra os recursos/entidades atualmente bloqueados para manipulação pela Bandeja de Contexto da IA.
     */
    Map<String, BloqueioBandejaInfo> BLOQUEIOS_BANDEJA_IA = new ConcurrentHashMap<>();
    Set<String> CAMPOS_BLOQUEADOS_IA = ConcurrentHashMap.newKeySet();

    record BloqueioBandejaInfo(String chaveRecurso, String idSessaoBandeja, String motivo, LocalDateTime expiraEm) {
        public boolean isAtivo() {
            return LocalDateTime.now().isBefore(expiraEm);
        }
    }

    // ==================== 1. CRUD PADRONIZADO ====================

    T salvar(T entidade);

    T atualizar(T entidade);

    T buscarPorId(ID id);

    List<T> listarTodos();

    boolean deletar(ID id);

    // ==================== 2. FALLBACK PARA REAÇÃO ====================

    /**
     * Executa uma operação no banco com fallback reativo seguro caso ocorra falha de conexão, lock ou estado inválido.
     */
    default <R> R executarComFallbackReacao(java.util.concurrent.Callable<R> operacaoPrincipal, java.util.function.Function<Exception, R> reacaoFallback) {
        try {
            return operacaoPrincipal.call();
        } catch (Exception e) {
            return reacaoFallback.apply(e);
        }
    }

    default T buscarUnicoComFallback(java.util.concurrent.Callable<T> consultaUnica, java.util.function.Supplier<T> fallbackReativo) {
        try {
            T resultado = consultaUnica.call();
            return resultado != null ? resultado : fallbackReativo.get();
        } catch (Exception e) {
            return fallbackReativo.get();
        }
    }

    // ==================== 3. BLOQUEIO DE INFORMAÇÕES PARA BANDEJA DE IA ====================

    default String construirChaveBloqueioBandeja(ID id) {
        return getClass().getSimpleName() + ":" + id;
    }

    /**
     * Bloqueia temporariamente um registro/informação enquanto estiver retido na Bandeja de Contexto da IA,
     * impedindo mutações concorrentes conflitantes antes da confirmação Human-in-the-Loop.
     */
    default void bloquearParaBandejaIA(ID id, String idSessaoBandeja, String motivo) {
        if (id == null) return;
        limparBloqueiosExpiradosBandeja();
        String chave = construirChaveBloqueioBandeja(id);
        BLOQUEIOS_BANDEJA_IA.put(chave, new BloqueioBandejaInfo(
                chave,
                idSessaoBandeja != null ? idSessaoBandeja : "default",
                motivo != null ? motivo : "Retido na Bandeja de Contexto da IA",
                LocalDateTime.now().plus(Duration.ofMinutes(10))
        ));
    }

    default void desbloquearDaBandejaIA(ID id) {
        if (id == null) return;
        BLOQUEIOS_BANDEJA_IA.remove(construirChaveBloqueioBandeja(id));
    }

    default boolean isBloqueadoParaBandejaIA(ID id) {
        if (id == null) return false;
        limparBloqueiosExpiradosBandeja();
        BloqueioBandejaInfo info = BLOQUEIOS_BANDEJA_IA.get(construirChaveBloqueioBandeja(id));
        return info != null && info.isAtivo();
    }

    default void validarNaoBloqueadoPorOutraSessaoIA(ID id, String idSessaoAtual) {
        if (id == null) return;
        limparBloqueiosExpiradosBandeja();
        BloqueioBandejaInfo info = BLOQUEIOS_BANDEJA_IA.get(construirChaveBloqueioBandeja(id));
        if (info != null && info.isAtivo() && (idSessaoAtual == null || !info.idSessaoBandeja().equals(idSessaoAtual))) {
            throw new IllegalStateException("Informação/registro bloqueado pela Bandeja de Contexto da IA (Sessão: "
                    + info.idSessaoBandeja() + " | Motivo: " + info.motivo() + ").");
        }
    }

    static void limparBloqueiosExpiradosBandeja() {
        BLOQUEIOS_BANDEJA_IA.entrySet().removeIf(e -> !e.getValue().isAtivo());
    }

    static void desbloquearTodosDaSessao(String idSessaoBandeja) {
        if (idSessaoBandeja == null) return;
        BLOQUEIOS_BANDEJA_IA.entrySet().removeIf(e -> idSessaoBandeja.equals(e.getValue().idSessaoBandeja()));
    }

    // ==================== 4. BUSCA PADRÃO ESTRUTURADA PARA LEITURA DA IA ====================

    /**
     * Busca padrão de informação única pelo ID formatada especificamente para leitura da IA,
     * incluindo fallback reativo e status de bloqueio na Bandeja de Contexto.
     */
    default Map<String, Object> buscarPadraoLeituraIA(ID id) {
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("dao", getClass().getSimpleName());
        envelope.put("idBuscado", id);
        envelope.put("bloqueadoBandejaIA", isBloqueadoParaBandejaIA(id));
        T encontrado = buscarUnicoComFallback(() -> buscarPorId(id), () -> null);
        envelope.put("encontrado", encontrado != null);
        envelope.put("dados", encontrado);
        envelope.put("resumoLeituraIA", encontrado != null ? encontrado.toString() : "Nenhum registro único encontrado para ID=" + id);
        return envelope;
    }

    /**
     * Listagem padrão de registros do DAO formatada para leitura da IA com fallback reativo seguro.
     */
    default Map<String, Object> listarPadraoLeituraIA() {
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("dao", getClass().getSimpleName());
        List<T> lista = executarComFallbackReacao(this::listarTodos, e -> java.util.Collections.emptyList());
        envelope.put("quantidade", lista != null ? lista.size() : 0);
        envelope.put("dados", lista);
        envelope.put("resumoLeituraIA", lista != null && !lista.isEmpty()
                ? lista.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" | "))
                : "Nenhum registro cadastrado em " + getClass().getSimpleName());
        return envelope;
    }
}
