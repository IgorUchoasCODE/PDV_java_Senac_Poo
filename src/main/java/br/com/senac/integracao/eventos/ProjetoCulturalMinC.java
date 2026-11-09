package br.com.senac.integracao.eventos;

public record ProjetoCulturalMinC(
        String pronac,
        String nome,
        String segmento,
        String dataInicio,
        String dataTermino,
        String situacao,
        String municipio,
        String uf,
        String proponente,
        String resumo
) {
    @Override
    public String toString() {
        String resumoCurto = (resumo != null && resumo.length() > 160)
                ? resumo.substring(0, 160) + "..."
                : resumo;
        return String.format("[PRONAC %s] %s\n    Segmento:   %s | Período: %s até %s\n    Local:      %s/%s | Proponente: %s\n    Situação:   %s\n    Resumo:     %s",
                pronac, nome, segmento, dataInicio, dataTermino, municipio, uf, proponente, situacao, resumoCurto);
    }
}
