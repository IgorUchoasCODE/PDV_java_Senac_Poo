package br.com.senac.integracao.ia;

import br.com.senac.integracao.gemini.GeminiClientService;
import br.com.senac.integracao.gemini.GeminiConfig;

import java.util.List;

/** Adaptador da API oficial do Gemini (texto, áudio e imagem). */
public class GeminiApiProvedor implements ProvedorIA {

    private final GeminiClientService client;

    public GeminiApiProvedor(GeminiClientService client) {
        this.client = client;
    }

    @Override
    public String getId() {
        return "gemini";
    }

    @Override
    public String getDescricao() {
        return "Gemini API (" + GeminiConfig.getInstancia().getModel() + ")";
    }

    @Override
    public boolean suportaMidia() {
        return true;
    }

    @Override
    public String verificarPronto() {
        return GeminiConfig.getInstancia().isConfigurado()
                ? null
                : "⚠️ A chave de API do Gemini ainda não foi informada. Configure GEMINI_API_KEY, config/gemini.properties, ou use o modo sem API (ia.provedor=manual).";
    }

    @Override
    public RespostaIA conversar(PedidoIA pedido) throws Exception {
        GeminiClientService.RespostaGemini r = pedido.anexo() == null
                ? client.conversarComTools(pedido.mensagem(), null)
                : client.conversarMultimodal(pedido.mensagem(), pedido.anexo(), pedido.mimeType());

        List<ChamadaFerramenta> chamadas = r.getChamadasTools().stream()
                .map(c -> new ChamadaFerramenta(c.nomeFuncao(), c.parametros()))
                .toList();
        return new RespostaIA(r.getTexto(), chamadas);
    }
}
