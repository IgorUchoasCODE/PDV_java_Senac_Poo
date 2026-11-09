package br.com.senac.integracao.clima;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record ClimaDiario(
        LocalDate data,
        double temperaturaMax,
        double temperaturaMin,
        double temperaturaMedia,
        double precipitacaoMm,
        double ventoMaxKmh
) {
    private static final DateTimeFormatter FORMATADOR_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String toString() {
        return String.format("%s | Temp. Média: %5.1f°C (Mín: %4.1f°C / Máx: %4.1f°C) | Chuva: %5.1f mm | Vento Máx: %4.1f km/h",
                data.format(FORMATADOR_BR),
                temperaturaMedia,
                temperaturaMin,
                temperaturaMax,
                precipitacaoMm,
                ventoMaxKmh);
    }
}
