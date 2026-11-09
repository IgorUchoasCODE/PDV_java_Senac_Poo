package br.com.senac.integracao.rios;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record NivelRioDiario(
        LocalDate data,
        double vazaoM3s,
        double vazaoMediaM3s,
        double vazaoMaxM3s,
        double vazaoMinM3s
) {
    private static final DateTimeFormatter FORMATADOR_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String toString() {
        return String.format("%s | Vazão/Nível: %6.2f m³/s | Média: %6.2f m³/s (Mín: %5.2f / Máx: %5.2f m³/s)",
                data.format(FORMATADOR_BR),
                vazaoM3s,
                vazaoMediaM3s,
                vazaoMinM3s,
                vazaoMaxM3s);
    }
}
