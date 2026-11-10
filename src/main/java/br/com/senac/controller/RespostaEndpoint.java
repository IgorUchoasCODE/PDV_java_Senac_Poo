package br.com.senac.controller;

import java.time.LocalDateTime;

/**
 * Envelope padronizado de resposta dos Endpoints dos Controllers para consumo simultâneo por:
 * 1. Interface Gráfica JavaFX (acesso tipado via 'dados' e 'sucesso')
 * 2. Interface Inline / CLI (exibição direta via 'formatarSaidaInline()')
 * 3. Integração com IA Gemini / Function Calling (serialização estruturada via 'toJsonResumoIA()')
 */
public record RespostaEndpoint<T>(
        boolean sucesso,
        String modulo,
        String caminhoRota,
        String nomeFuncaoGemini,
        String mensagem,
        T dados,
        LocalDateTime timestamp
) {

    public static <T> RespostaEndpoint<T> ok(RotaSistema rota, String mensagem, T dados) {
        return new RespostaEndpoint<>(
                true,
                rota.getModulo(),
                rota.getCaminhoRota(),
                rota.getNomeFuncaoGemini(),
                mensagem,
                dados,
                LocalDateTime.now()
        );
    }

    public static <T> RespostaEndpoint<T> erro(String caminhoRota, String mensagemErro) {
        return new RespostaEndpoint<>(
                false,
                "ERRO",
                caminhoRota,
                "erro_endpoint",
                mensagemErro,
                null,
                LocalDateTime.now()
        );
    }

    public String formatarSaidaInline() {
        return String.format("[%s] (%s) %s -> %s | Dados: %s",
                sucesso ? "SUCESSO" : "ERRO",
                modulo,
                caminhoRota,
                mensagem,
                dados != null ? dados.toString() : "null");
    }

    public String toJsonResumoIA() {
        String msgSegura = mensagem != null ? mensagem.replace("\"", "\\\"") : "";
        String dadosSeguros = dados != null ? dados.toString().replace("\"", "\\\"").replace("\n", " ") : "null";
        return String.format(
                "{\"sucesso\":%s,\"modulo\":\"%s\",\"rota\":\"%s\",\"tool\":\"%s\",\"mensagem\":\"%s\",\"dados\":\"%s\",\"timestamp\":\"%s\"}",
                sucesso, modulo, caminhoRota, nomeFuncaoGemini, msgSegura, dadosSeguros, timestamp);
    }
}
