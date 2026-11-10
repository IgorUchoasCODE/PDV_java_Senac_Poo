package br.com.senac.interfaces;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface TituloVencivel {
    LocalDate getDataVencimento();
    boolean isVencido(LocalDate dataReferencia);
    long getDiasAtraso(LocalDate dataReferencia);
    void quitar(LocalDateTime dataPagamento);
}
