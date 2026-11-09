package br.com.senac.integracao.eventos;

public record EventoSympla(
        long id,
        String nome,
        String dataHorario,
        String localNome,
        String bairro,
        String cidadeUf,
        double distanciaKm,
        String url
) {
    @Override
    public String toString() {
        String dist = distanciaKm >= 0 ? String.format(" (a %.1f km de você)", distanciaKm) : "";
        return String.format("[%d] %s\n    Data:  %s\n    Local: %s - %s (%s)%s\n    Link:  %s",
                id, nome, dataHorario, localNome, bairro, cidadeUf, dist, url);
    }
}
