package br.com.senac.integracao.calendario;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public record DiaNaoUtil(
        LocalDate data,
        String nome,
        String categoria, // FERIADO NACIONAL, FERIADO ESTADUAL, FERIADO MUNICIPAL, PONTO FACULTATIVO, FIM DE SEMANA
        String abrangencia
) implements Comparable<DiaNaoUtil> {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter FORMATADOR_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String diaDaSemana() {
        return data.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
    }

    @Override
    public int compareTo(DiaNaoUtil outro) {
        return this.data.compareTo(outro.data);
    }

    @Override
    public String toString() {
        return String.format("%s (%-13s) | %-20s | %s [%s]",
                data.format(FORMATADOR_BR),
                diaDaSemana(),
                categoria,
                nome,
                abrangencia);
    }
}
