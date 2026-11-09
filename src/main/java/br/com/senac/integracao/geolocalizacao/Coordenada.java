package br.com.senac.integracao.geolocalizacao;

public record Coordenada(
        double latitude,
        double longitude,
        double precisaoMetros,
        String cidade,
        String uf,
        String bairro,
        String logradouro
) {
    public Coordenada(double latitude, double longitude, double precisaoMetros) {
        this(latitude, longitude, precisaoMetros, "", "", "", "");
    }

    /**
     * Calcula a distância em quilômetros (fórmula de Haversine) entre esta coordenada e outro ponto.
     */
    public double calcularDistanciaKm(double outraLat, double outraLon) {
        final double RAIO_TERRA_KM = 6371.0;
        double dLat = Math.toRadians(outraLat - this.latitude);
        double dLon = Math.toRadians(outraLon - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(outraLat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAIO_TERRA_KM * c;
    }

    @Override
    public String toString() {
        String endereco = (!cidade.isBlank())
                ? String.format(" | Local: %s, %s - %s/%s", logradouro, bairro, cidade, uf)
                : "";
        return String.format("Latitude: %.6f | Longitude: %.6f | Precisão: ±%.0fm%s",
                latitude, longitude, precisaoMetros, endereco);
    }
}
