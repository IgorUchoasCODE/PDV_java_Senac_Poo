package br.com.senac.view.javafx.gateway;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Contrato de fachada (DIP/ISP - SOLID) que desacopla completamente os componentes
 * e containers JavaFX das camadas de rotas, DAOs, Hibernate e provedores de IA.
 */
public interface SistemaGateway {

    RespostaEndpoint<Object> executarRota(RotaSistema rota, Map<String, Object> parametros);

    String perguntarAoChat(String idSessao, String mensagem);

    String perguntarAoChatComProgresso(String idSessao, String mensagem, Consumer<String> aoProgresso);

    String perguntarPorVoz(String idSessao, byte[] audioBytes, String mimeType);

    String perguntarComImagem(String idSessao, byte[] imagemBytes, String mimeType, String prompt);

    String executarComandoTerminalChat(String idSessao, String comando);

    String obterProvedorIaAtivo();

    void executarEmBackground(Runnable tarefa, Consumer<Throwable> aoErro);

    TabelaCsv lerCsv(Path caminho, Character delimitador) throws IOException;

    TabelaCsv lerCsvTexto(String conteudo, Character delimitador) throws IOException;

    ResultadoImportacaoCsv importarCsvMapeado(
            String origem,
            TabelaCsv tabela,
            TipoImportacaoCsv tipo,
            Map<String, String> mapeamentoColunas,
            boolean pararNoPrimeiroErro
    );

    Properties carregarPropriedades(String caminhoArquivo);

    void salvarPropriedades(String caminhoArquivo, Properties propriedades, String comentario) throws IOException;

    void recarregarConfiguracoesIA();
}
