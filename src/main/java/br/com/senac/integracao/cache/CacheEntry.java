package br.com.senac.integracao.cache;

import java.time.Duration;
import java.time.Instant;

/**
 * Record genérico e imutável para controle de Cache em memória com TTL (Time-To-Live),
 * economizando requisições de API (ex: 6 horas para Clima/Rios e 7 dias para Eventos).
 */
public record CacheEntry<T>(
        T dados,
        Instant expiraEm
) {
    public static <T> CacheEntry<T> criarComTtl(T dados, Duration ttl) {
        return new CacheEntry<>(dados, Instant.now().plus(ttl));
    }

    public boolean isValido() {
        return dados != null && Instant.now().isBefore(expiraEm);
    }
}
