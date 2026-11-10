package br.com.senac.integracao.ia;

import java.util.List;
import java.util.Map;

/**
 * Contrato único para qualquer IA que queira operar o sistema (Gemini API, Ollama, LM Studio, OpenAI, etc.).
 * A IA recebe o pedido do usuário e devolve texto e/ou chamadas de ferramenta (rotas do RoteadorController).
 * Quem executa, valida e pede confirmação é sempre o GeminiAgenteOperacional - a IA nunca toca no banco.
 *
 * Para plugar uma IA nova basta implementar esta interface e registrá-la em {@link ProvedorIAFactory}.
 */
public interface ProvedorIA {

    /** Identificador curto (ex: "gemini", "openai"). */
    String getId();

    /** Descrição amigável exibida na tela. */
    String getDescricao();

    /** Se aceita áudio/imagem junto com o pedido. */
    boolean suportaMidia();

    /** Retorna null se o provedor está pronto para uso, ou uma mensagem explicando o que falta configurar. */
    String verificarPronto();

    /** Envia o pedido e devolve a interpretação da IA. */
    RespostaIA conversar(PedidoIA pedido) throws Exception;

    record PedidoIA(String mensagem, byte[] anexo, String mimeType) {
    }

    /** Uma rota que a IA quer executar. {@code rota} aceita o nome do enum ou o nome da função. */
    record ChamadaFerramenta(String rota, Map<String, Object> parametros) {
    }

    record RespostaIA(String texto, List<ChamadaFerramenta> chamadas) {
        public boolean temChamadas() {
            return chamadas != null && !chamadas.isEmpty();
        }
    }
}
