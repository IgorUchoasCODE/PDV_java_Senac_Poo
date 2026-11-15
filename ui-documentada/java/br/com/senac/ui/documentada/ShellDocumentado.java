package br.com.senac.ui.documentada;

import br.com.senac.controller.*;
import br.com.senac.view.javafx.abas.*;
import br.com.senac.view.javafx.abas.AbaCadastrosOperacoesContainer.Especialidade;
import br.com.senac.view.javafx.componentes.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import java.util.*;
import java.util.function.Supplier;
import static br.com.senac.ui.documentada.ComponentesUI.*;

/** Shell adicional: reutiliza telas oficiais sem editar SazonaliseiApp ou qualquer arquivo do núcleo. */
public final class ShellDocumentado extends StackPane {
    private final GatewayDaSessao gateway;
    private final RotasDoDocumento catalogo=new RotasDoDocumento();
    private final BorderPane layout=new BorderPane();
    private final VBox area=new VBox(16);
    private final Label titulo=texto("Visão geral","titulo-pagina"),status=texto("Conectando pela fachada oficial…","estado");
    private final Label quantidadeProdutos=texto("—","valor-total"),quantidadeClientes=texto("—","valor-total"),quantidadeNotas=texto("—","valor-total");
    private final Map<String,Supplier<Node>> fabricas=new LinkedHashMap<>();
    private final Map<String,Node> carregadas=new LinkedHashMap<>();
    private final Map<String,Button> botoes=new LinkedHashMap<>();
    private final AssistenteFlutuantePopupContainer assistente;
    private final BandejaUI bandeja;
    private volatile String pagina="inicio";
    private boolean pronto;
    private boolean contagensProntas;
    public ShellDocumentado(GatewayDaSessao gateway) {
        this.gateway=gateway;setId("shell-documentado");
        PainelChatPesquisaContainer chat=new PainelChatPesquisaContainer(gateway);
        String css=Objects.requireNonNull(getClass().getResource("/css/sazonalisei-theme.css")).toExternalForm();
        assistente=new AssistenteFlutuantePopupContainer(chat,css);
        bandeja=new BandejaUI(gateway);
        gateway.aoConversar(()->{if(pagina.equals("bandeja")&&!bandeja.enviando())bandeja.atualizar();});
        fabricas.put("inicio",this::inicio);
        fabricas.put("estatisticas",()->abas(new String[]{"Painel oficial","Relatórios / operações"},
                new AbaEstatisticaFinanceiroContainer(gateway,q->assistente.consultarEmJanela(q,css)),
                new PainelRotas(gateway,catalogo,"RELATORIO","ESTATISTICA","FATOR_EXTERNO")));
        fabricas.put("graficos",()->new AbaGraficosAnaliticosContainer(gateway));
        fabricas.put("grafo",()->new AbaGrafoObsidianContainer(gateway));
        fabricas.put("mapa",()->new AbaMapaEventosContainer(gateway,q->assistente.consultarEmJanela(q,css)));
        fabricas.put("estoque",()->abas(new String[]{"Estoque FIFO oficial","Consultas, perdas e lotes"},
                new AbaEstoqueFifoContainer(gateway),new PainelRotas(gateway,catalogo,"ESTOQUE")));
        fabricas.put("produtos",()->cadastros(Especialidade.PRODUTOS,"PRODUTO"));
        fabricas.put("pessoas",()->cadastros(Especialidade.PESSOAS,"PESSOA"));
        fabricas.put("notas",()->abas(new String[]{"Nova nota","Formulários oficiais","Todas as operações de notas"},
                new NovaNotaUI(gateway),new AbaCadastrosOperacoesContainer(gateway,()->status.setText("Alteração respondida pelo núcleo. Recarregue a tela consultada."),Especialidade.NOTAS),
                new PainelRotas(gateway,catalogo,"NOTA")));
        fabricas.put("pagamentos",()->cadastros(Especialidade.PAGAMENTOS,"PAGAMENTO"));
        fabricas.put("config",()->new AbaConfiguracaoAmbienteContainer(gateway));
        fabricas.put("bandeja",()->bandeja);
        fabricas.put("rotas",()->new PainelRotas(gateway,catalogo));
        VBox menu=menu();layout.setLeft(menu);
        Button comandos=botao("Comando rápido · Ctrl+Espaço","abrir-comando",this::comandoRapido);
        Button abrirChat=botao("Abrir chatbot","abrir-chat",()->assistente.abrirOuFocarJanelaInterativa(css));
        HBox topo=linha(texto("SAZONALISEI", "marca"),texto("Vendas, estoque FIFO e inteligência comercial","ajuda"));
        Region flex=new Region();HBox.setHgrow(flex,Priority.ALWAYS);topo.getChildren().addAll(flex,comandos,abrirChat);
        topo.getStyleClass().add("cabecalho");layout.setTop(topo);
        area.getStyleClass().add("conteudo");layout.setCenter(area);
        status.setMaxWidth(Double.MAX_VALUE);layout.setBottom(status);
        getChildren().addAll(layout,assistente);StackPane.setAlignment(assistente,Pos.BOTTOM_RIGHT);StackPane.setMargin(assistente,new Insets(0,18,42,0));
        mostrar("inicio");
        sceneProperty().addListener((o,a,b)->{if(b!=null){
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE,KeyCombination.CONTROL_DOWN),this::comandoRapido);
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F2),this::comandoRapido);
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F1),()->mostrar("inicio"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F3),()->mostrar("produtos"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F4),()->mostrar("pessoas"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F5),()->mostrar("notas"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F6),()->mostrar("pagamentos"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F7),()->mostrar("estoque"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F8),()->mostrar("estatisticas"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F9),()->mostrar("config"));
            b.getAccelerators().put(new KeyCodeCombination(KeyCode.F10),()->mostrar("bandeja"));
        }});
        inicializar();
    }
    public boolean pronto(){return pronto;}
    public boolean contagensProntas(){return contagensProntas;}
    public GatewayDaSessao gateway(){return gateway;}
    private void inicializar() {
        gateway.executarEmBackground(()->{
            var r=gateway.executarRota(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO,Map.of());
            Platform.runLater(()->{pronto=r.sucesso();status.setText(r.sucesso()?"Sistema conectado · dados fundamentais disponíveis":"Falha na inicialização: "+r.mensagem());if(pronto)atualizarIndicadores();});
        },e->Platform.runLater(()->status.setText("Falha ao inicializar: "+e.getMessage())));
    }
    private VBox menu() {
        VBox v=new VBox(7);v.getStyleClass().add("menu-lateral");v.setPrefWidth(215);
        item(v,"inicio","Visão geral");secao(v,"ANÁLISE & CLIMA");
        item(v,"estatisticas","Estatísticas & Financeiro");item(v,"graficos","Gráficos · Rios & Clima");item(v,"grafo","Grafo Analítico");item(v,"mapa","Mapa & Eventos Locais");
        secao(v,"OPERAÇÕES & ESTOQUE");item(v,"estoque","Estoque FIFO");item(v,"notas","Notas & Vendas");item(v,"pagamentos","Pagamentos");
        secao(v,"CADASTROS");item(v,"produtos","Produtos & Kits");item(v,"pessoas","Clientes & Fornecedores");
        secao(v,"SISTEMA");item(v,"bandeja","Bandeja de contexto");item(v,"rotas","Operações avançadas");item(v,"config","Ambiente, Chaves & CSV");
        Region flex=new Region();VBox.setVgrow(flex,Priority.ALWAYS);v.getChildren().addAll(flex,texto("AV2 · POO\nProf. Carlos Bruno", "rodape-menu"));
        return v;
    }
    private void item(VBox menu,String id,String nome){Button b=botao(nome,"menu-"+id,()->mostrar(id));b.setMaxWidth(Double.MAX_VALUE);botoes.put(id,b);menu.getChildren().add(b);}
    private void secao(VBox v,String s){v.getChildren().add(texto(s,"secao-menu"));}
    public void mostrar(String id) {
        if(!fabricas.containsKey(id))return;
        pagina=id;botoes.forEach((k,b)->{b.getStyleClass().remove("ativo");if(k.equals(id))b.getStyleClass().add("ativo");});
        titulo.setText(botoes.get(id).getText());Node tela=carregadas.computeIfAbsent(id,k->fabricas.get(k).get());
        area.getChildren().setAll(titulo,tela);VBox.setVgrow(tela,Priority.ALWAYS);
        if(id.equals("bandeja"))bandeja.atualizar();
        if(id.equals("inicio")&&pronto)atualizarIndicadores();
    }
    private Node cadastros(Especialidade e,String modulo){return abas(new String[]{"Formulários oficiais","Todas as operações"},
            new AbaCadastrosOperacoesContainer(gateway,()->status.setText("Operação respondida. Atualize a tabela para conferir o banco."),e),new PainelRotas(gateway,catalogo,modulo));}
    private TabPane abas(String[] titulos,Node... nos){TabPane p=new TabPane();p.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);for(int i=0;i<nos.length;i++)p.getTabs().add(new Tab(titulos[i],nos[i]));return p;}
    private Node inicio() {
        Label explicacao=texto("Acompanhe seus cadastros, prepare compras e vendas e consulte o estoque. Use o assistente para pesquisar e revisar operações antes de confirmar.","texto-introducao");
        HBox indicadores=linha(card("Produtos cadastrados",quantidadeProdutos),card("Clientes cadastrados",quantidadeClientes),card("Notas operacionais",quantidadeNotas));
        for(Node c:indicadores.getChildren())HBox.setHgrow(c,Priority.ALWAYS);
        FlowPane operacoes=new FlowPane(12,12,botao("Produtos & Kits","atalho-produtos",()->mostrar("produtos")),
                botao("Clientes & Fornecedores","atalho-pessoas",()->mostrar("pessoas")),botao("Preparar uma nota","atalho-notas",()->mostrar("notas")),
                botao("Consultar estoque","atalho-estoque",()->mostrar("estoque")),botao("Revisar bandeja","atalho-bandeja",()->mostrar("bandeja")));
        return rolagem(new VBox(18,explicacao,indicadores,card("Comece uma operação",operacoes),
                card("Sua operação em um só lugar",texto("Gerencie produtos e pessoas, acompanhe os lotes FIFO, consulte pagamentos e revise as instruções na bandeja do assistente.","ajuda")),
                card("Antes de apresentar",texto("Inicialize os dados fundamentais. A carga CSV e o reset utilizam os pontos de entrada oficiais e exigem confirmação no terminal. Recursos que exigem MySQL, rede ou credenciais dependem do ambiente configurado.","ajuda"),
                        botao("Inicializar dados fundamentais","inicializar-padrao",this::inicializar)),
                card("Atalhos",texto("F1: início · F2 / Ctrl+Espaço: comando rápido · F3: produtos · F4: pessoas · F5: notas · F6: pagamentos · F7: estoque · F8: estatísticas · F9: ambiente · F10: bandeja","ajuda"))));
    }
    private void atualizarIndicadores() {
        gateway.executarEmBackground(()->{
            try{
                long[] valores=new long[3];String[] entidades={"PRODUTO","CLIENTE","NOTA"};
                for(int i=0;i<entidades.length;i++){
                    var r=gateway.executarRota(RotaSistema.BUSCA_PADRAO_IA,Map.of("entidade",entidades[i],"modo","CONTAGEM"));
                    if(!r.sucesso()||!(r.dados() instanceof Map<?,?> m)||!(m.get("totalRegistros") instanceof Number n))throw new IllegalStateException("Contagem indisponível: "+r.mensagem());
                    valores[i]=n.longValue();
                }
                Platform.runLater(()->{quantidadeProdutos.setText(String.valueOf(valores[0]));quantidadeClientes.setText(String.valueOf(valores[1]));quantidadeNotas.setText(String.valueOf(valores[2]));contagensProntas=true;});
            }catch(Exception e){Platform.runLater(()->status.setText("Não foi possível atualizar os indicadores: "+e.getMessage()));}
        },e->Platform.runLater(()->status.setText(e.getMessage())));
    }
    private void comandoRapido() {
        TextInputDialog d=new TextInputDialog();d.initOwner(getScene().getWindow());d.setTitle("Comando rápido");d.setHeaderText("Navegue por abrir>janela>… ou envie um comando ao chatbot oficial.");
        d.setContentText("Comando:");d.showAndWait().ifPresent(c->{
            String texto=c.trim();String n=java.text.Normalizer.normalize(texto,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase();
            if(n.startsWith("abrir>janela>")) {
                String id=n.contains("produto")?"produtos":n.contains("pessoa")||n.contains("cliente")||n.contains("fornecedor")?"pessoas":
                        n.contains("nota")?"notas":n.contains("estoque")?"estoque":n.contains("pagamento")||n.contains("parcela")?"pagamentos":
                        n.contains("bandeja")?"bandeja":n.contains("estatistica")||n.contains("analise")||n.contains("compras")?"estatisticas":null;
                if(id!=null){mostrar(id);return;}
                status.setText("Comando de janela não reconhecido nesta camada de UI.");return;
            }
            String css=Objects.requireNonNull(getClass().getResource("/css/sazonalisei-theme.css")).toExternalForm();assistente.consultarEmJanela(texto,css);
        });
    }
}
