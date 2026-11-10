package br.com.senac.interfaces;

import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;

import java.time.LocalDate;

public interface ProvedorFatorExterno {
    void enriquecerVinculo(LocalDate data, Coordenada coordenada, VinculoFatorExternoNota vinculo);

    default void limparCacheExpirado() {
        // Implementação opcional nos serviços que possuem cache com TTL
    }
}
