package br.com.senac.integracao.csv;

import br.com.senac.controller.RespostaEndpoint;

import java.util.Collections;
import java.util.List;

/**
 * Resultado consolidado da importação de um arquivo CSV através das rotas dos controllers.
 */
public record ResultadoImportacaoCsv(
        String origem,
        int totalLinhas,
        int sucessos,
        int falhas,
        List<ErroLinhaCsv> erros,
        List<RespostaEndpoint<Object>> respostas
) {

    public record ErroLinhaCsv(int numeroLinha, String rota, String mensagem) {
        @Override
        public String toString() {
            return String.format("Linha %d (%s): %s", numeroLinha, rota, mensagem);
        }
    }

    public ResultadoImportacaoCsv {
        erros = erros != null ? Collections.unmodifiableList(erros) : Collections.emptyList();
        respostas = respostas != null ? Collections.unmodifiableList(respostas) : Collections.emptyList();
    }

    public boolean semErros() {
        return falhas == 0;
    }

    public String formatarResumo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Importação CSV [%s] -> Total: %d | Sucessos: %d | Falhas: %d",
                origem, totalLinhas, sucessos, falhas
        ));
        if (!erros.isEmpty()) {
            sb.append("\nErros:");
            for (ErroLinhaCsv erro : erros) {
                sb.append("\n - ").append(erro);
            }
        }
        return sb.toString();
    }
}
