package br.com.senac.application;

import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.view.javafx.abas.AbaCadastrosOperacoesContainer;
import br.com.senac.view.javafx.abas.AbaCadastrosOperacoesContainer.Especialidade;
import br.com.senac.view.javafx.abas.AbaConfiguracaoAmbienteContainer;
import br.com.senac.view.javafx.abas.AbaEstatisticaFinanceiroContainer;
import br.com.senac.view.javafx.abas.AbaEstoqueFifoContainer;
import br.com.senac.view.javafx.abas.AbaGraficosAnaliticosContainer;
import br.com.senac.view.javafx.abas.AbaGrafoObsidianContainer;
import br.com.senac.view.javafx.abas.AbaMapaEventosContainer;
import br.com.senac.view.javafx.componentes.AssistenteFlutuantePopupContainer;
import br.com.senac.view.javafx.componentes.CabecalhoNavegacaoContainer;
import br.com.senac.view.javafx.componentes.PainelChatPesquisaContainer;
import br.com.senac.view.javafx.componentes.SidebarNavegacaoContainer;
import br.com.senac.view.javafx.gateway.RoteadorSistemaGateway;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Aplicação principal JavaFX do Sazonalisei.
 * Navegação via Sidebar lateral esquerda, área central limpa em StackPane
 * e assistente IA flutuante/arrastável que abre em janela interativa.
 */
public class SazonaliseiApp extends Application {

    @Override
    public void start(Stage stage) {
        SistemaGateway gateway = new RoteadorSistemaGateway();
        PainelChatPesquisaContainer chatContainer = new PainelChatPesquisaContainer(gateway);

        var cssResource = getClass().getResource("/css/sazonalisei-theme.css");
        String cssUrl = cssResource != null ? cssResource.toExternalForm() : null;

        AssistenteFlutuantePopupContainer assistenteFlutuante = new AssistenteFlutuantePopupContainer(chatContainer, cssUrl);

        Consumer<String> enviarParaChat = consulta -> assistenteFlutuante.consultarEmJanela(consulta, cssUrl);

        AbaEstatisticaFinanceiroContainer abaEstatisticaFinanceiro = new AbaEstatisticaFinanceiroContainer(gateway, enviarParaChat);
        AbaGraficosAnaliticosContainer abaGraficos = new AbaGraficosAnaliticosContainer(gateway);
        AbaGrafoObsidianContainer abaGrafo = new AbaGrafoObsidianContainer(gateway);
        AbaMapaEventosContainer abaMapa = new AbaMapaEventosContainer(gateway, enviarParaChat);
        AbaEstoqueFifoContainer abaEstoque = new AbaEstoqueFifoContainer(gateway);

        Runnable[] recarregarTudoRef = new Runnable[1];
        AbaCadastrosOperacoesContainer abaProdutos = new AbaCadastrosOperacoesContainer(gateway, () -> recarregarTudoRef[0].run(), Especialidade.PRODUTOS);
        AbaCadastrosOperacoesContainer abaPessoas = new AbaCadastrosOperacoesContainer(gateway, () -> recarregarTudoRef[0].run(), Especialidade.PESSOAS);
        AbaCadastrosOperacoesContainer abaNotas = new AbaCadastrosOperacoesContainer(gateway, () -> recarregarTudoRef[0].run(), Especialidade.NOTAS);
        AbaCadastrosOperacoesContainer abaPagamentos = new AbaCadastrosOperacoesContainer(gateway, () -> recarregarTudoRef[0].run(), Especialidade.PAGAMENTOS);
        AbaConfiguracaoAmbienteContainer abaConfig = new AbaConfiguracaoAmbienteContainer(gateway);

        recarregarTudoRef[0] = () -> {
            abaEstatisticaFinanceiro.recarregarDados();
            abaGraficos.recarregarDados();
            abaGrafo.carregarDadosGrafo();
            abaMapa.recarregarEventos();
            abaEstoque.recarregarDados();
            abaProdutos.recarregarDados();
            abaPessoas.recarregarDados();
            abaNotas.recarregarDados();
            abaPagamentos.recarregarDados();
        };

        Map<String, Node> telas = new LinkedHashMap<>();
        telas.put("estatisticas", abaEstatisticaFinanceiro);
        telas.put("graficos", abaGraficos);
        telas.put("grafo", abaGrafo);
        telas.put("mapa", abaMapa);
        telas.put("estoque", abaEstoque);
        telas.put("produtos", abaProdutos);
        telas.put("pessoas", abaPessoas);
        telas.put("notas", abaNotas);
        telas.put("pagamentos", abaPagamentos);
        telas.put("config", abaConfig);

        StackPane areaCentral = new StackPane();
        areaCentral.getChildren().addAll(telas.values());

        Consumer<String> alternarTela = id -> {
            for (Map.Entry<String, Node> e : telas.entrySet()) {
                boolean ativo = e.getKey().equals(id);
                e.getValue().setVisible(ativo);
                e.getValue().setManaged(ativo);
            }
        };

        SidebarNavegacaoContainer sidebar = new SidebarNavegacaoContainer(alternarTela);
        sidebar.adicionarSecao("ANÁLISE & CLIMA");
        sidebar.adicionarItem("estatisticas", "Estatísticas & Financeiro");
        sidebar.adicionarItem("graficos", "Gráficos (Rios & Clima)");
        sidebar.adicionarItem("grafo", "Grafo Analítico");
        sidebar.adicionarItem("mapa", "Mapa & Eventos Locais");

        sidebar.adicionarSecao("OPERAÇÕES & ESTOQUE");
        sidebar.adicionarItem("estoque", "Estoque FIFO");
        sidebar.adicionarItem("notas", "Notas & Vendas");
        sidebar.adicionarItem("pagamentos", "Pagamentos");

        sidebar.adicionarSecao("CADASTROS");
        sidebar.adicionarItem("produtos", "Produtos & Kits");
        sidebar.adicionarItem("pessoas", "Clientes & Fornecedores");

        sidebar.adicionarSecao("SISTEMA");
        sidebar.adicionarItem("config", "Ambiente, Chaves & CSV");

        sidebar.selecionar("estatisticas");
        alternarTela.accept("estatisticas");

        BorderPane layoutPrincipal = new BorderPane();
        CabecalhoNavegacaoContainer[] cabecalhoRef = new CabecalhoNavegacaoContainer[1];
        cabecalhoRef[0] = new CabecalhoNavegacaoContainer(
                () -> {
                    cabecalhoRef[0].definirStatus("Sincronizando dados...");
                    recarregarTudoRef[0].run();
                    cabecalhoRef[0].definirStatus("Pronto • 85 Rotas Ativas");
                },
                () -> assistenteFlutuante.abrirOuFocarJanelaInterativa(cssUrl)
        );
        cabecalhoRef[0].atualizarTextoToggleChat(false);

        layoutPrincipal.setTop(cabecalhoRef[0]);
        layoutPrincipal.setLeft(sidebar);
        layoutPrincipal.setCenter(areaCentral);

        StackPane rootCamadas = new StackPane(layoutPrincipal, assistenteFlutuante);
        StackPane.setAlignment(assistenteFlutuante, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(assistenteFlutuante, new Insets(0, 24, 24, 0));

        Rectangle2D tela = Screen.getPrimary().getVisualBounds();
        double largura = Math.max(980, Math.min(1440, tela.getWidth() * 0.94));
        double altura = Math.max(640, Math.min(900, tela.getHeight() * 0.92));

        Scene scene = new Scene(rootCamadas, largura, altura);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl);
        }

        stage.setMinWidth(960);
        stage.setMinHeight(620);

        stage.setTitle("Sazonalisei • Monitoramento Hidrológico, Estoque FIFO, Grafo Analítico & IA");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> JPAUtil.close());
        stage.show();
    }
}
