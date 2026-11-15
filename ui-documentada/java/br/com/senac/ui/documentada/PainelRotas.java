package br.com.senac.ui.documentada;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.util.*;
import java.util.function.Supplier;
import static br.com.senac.ui.documentada.ComponentesUI.*;

/** Um formulário por operação documentada, integrado apenas pelo SistemaGateway. */
public final class PainelRotas extends BorderPane {
    private final GatewayDaSessao gateway;
    private final RotasDoDocumento catalogo;
    private final ComboBox<RotasDoDocumento.Operacao> seletor=new ComboBox<>();
    private final VBox formulario=new VBox(12);
    private final Map<String,Supplier<String>> valores=new LinkedHashMap<>();
    private final TextArea extras=new TextArea();
    private final TextArea resposta=new TextArea();
    private final Label status=new Label("Selecione uma operação para consultar seu contrato.");
    private final Button executar;
    private final Set<String> modulos;
    public PainelRotas(GatewayDaSessao gateway,RotasDoDocumento catalogo,String... modulos) {
        this.gateway=gateway;this.catalogo=catalogo;this.modulos=Set.of(modulos);
        TextField busca=new TextField();busca.setPromptText("Buscar operação por nome, caminho ou descrição");busca.setId("buscar-rota");
        seletor.setId("rota-documentada");seletor.setMaxWidth(Double.MAX_VALUE);
        busca.textProperty().addListener((o,a,b)->filtrar(b));
        seletor.valueProperty().addListener((o,a,b)->montar(b));
        extras.setPromptText("{\"parametro\": \"valor\"}");extras.setPrefRowCount(4);extras.setId("parametros-avancados");
        TitledPane avancado=new TitledPane("Parâmetros estruturados avançados (JSON)",extras);avancado.setExpanded(false);
        executar=botao("Executar operação", "executar-rota",this::executar);
        executar.getStyleClass().add("primario");
        resposta.setEditable(false);resposta.setWrapText(true);resposta.setId("resposta-rota");
        ScrollPane scroll=rolagem(formulario);VBox.setVgrow(scroll,Priority.ALWAYS);
        VBox entrada=new VBox(12,busca,seletor,scroll,avancado,linha(executar,botao("Limpar campos","limpar-rota",()->montar(seletor.getValue()))));
        VBox saida=card("Retorno do sistema",status,resposta,
                botao("Salvar resultado TXT","salvar-resultado",this::salvarResultado));
        VBox.setVgrow(resposta,Priority.ALWAYS);
        SplitPane split=new SplitPane(entrada,saida);split.setDividerPositions(.53);setCenter(split);
        filtrar("");
    }
    public ComboBox<RotasDoDocumento.Operacao> seletor(){return seletor;}
    public void selecionar(String caminho){catalogo.todas().stream().filter(x->x.caminho().equals(caminho)).findFirst().ifPresent(seletor::setValue);}
    private void filtrar(String termo) {
        seletor.setItems(FXCollections.observableArrayList(catalogo.todas().stream()
                .filter(x->modulos.isEmpty()||modulos.contains(x.modulo()))
                .filter(x->(x.caminho()+" "+x.descricao()).toLowerCase().contains(termo.toLowerCase())).toList()));
        if(!seletor.getItems().isEmpty())seletor.getSelectionModel().selectFirst();else montar(null);
    }
    private void montar(RotasDoDocumento.Operacao op) {
        formulario.getChildren().clear();valores.clear();extras.clear();
        if(op==null){formulario.getChildren().add(new Label("Nenhuma operação encontrada."));return;}
        formulario.getChildren().addAll(texto(op.descricao(),"titulo-card"),texto(op.metodo()+" · "+op.caminho(),"sobrelinha"),
                texto("Contrato do documento: "+op.parametros(),"ajuda"));
        if(op.restrita())formulario.getChildren().add(texto("Operação restrita de limpeza. O núcleo exige seu próprio token de confirmação.","aviso"));
        if(op.id().contains("PROCEDURE_MYSQL")||op.modulo().equals("ESTATISTICA"))
            formulario.getChildren().add(texto("A disponibilidade depende dos recursos de banco implementados no núcleo. O H2 não oferece todas as procedures MySQL.","ajuda"));
        for(var campo:op.campos()) {
            Node controle;
            String ajuda=campo.tipo();
            if(campo.tipo().equals("Boolean")) {
                ComboBox<String> c=new ComboBox<>(FXCollections.observableArrayList("","true","false"));c.setValue("");
                controle=c;valores.put(campo.nome(),()->c.getValue()==null?"":c.getValue());
            }else if(campo.tipo().equals("List")||campo.tipo().equals("Map")) {
                TextArea a=new TextArea();a.setPrefRowCount(3);
                a.setPromptText(campo.tipo().equals("List")?"[{…}]":"{…}");controle=a;valores.put(campo.nome(),a::getText);
            }else {
                TextInputControl t=campo.nome().equals("tokenConfirmacao")?new PasswordField():new TextField();
                t.setPromptText(switch(campo.tipo()) {
                    case "NotaCompra","NotaVenda","Pessoa","Produto" -> "ID do registro existente";
                    case "NotaPagamento" -> "ID_DA_NOTA:ID_DO_PAGAMENTO";
                    case "LocalDate" -> "AAAA-MM-DD";
                    case "BigDecimal" -> "Ex.: 12,50";
                    default -> "Deixe vazio para utilizar o padrão do núcleo";
                });
                if(campo.tipo().startsWith("Nota")||campo.tipo().equals("Pessoa")||campo.tipo().equals("Produto"))
                    ajuda+=" · A referência será consultada pela rota oficial antes da execução.";
                controle=t;valores.put(campo.nome(),t::getText);
            }
            controle.setId("campo-"+campo.nome());
            VBox f=new VBox(4,texto(campo.nome(),"rotulo"),controle,texto(ajuda,"ajuda"));
            if(campo.nome().equals("caminhoArquivo"))f.getChildren().add(botao("Selecionar arquivo","arquivo-rota",()->{
                FileChooser c=new FileChooser();var arquivo=c.showOpenDialog(getScene().getWindow());
                if(arquivo!=null)((TextInputControl)controle).setText(arquivo.getAbsolutePath());
            }));
            formulario.getChildren().add(f);
        }
        if(op.campos().isEmpty())formulario.getChildren().add(texto("Nenhum parâmetro a preencher.","ajuda"));
        if(op.id().equals("NOTA_COMPRA_EMITIR"))formulario.getChildren().add(texto("Para montar uma nova compra com vários itens, use a aba Nova nota. Aqui uma referência de nota existente deve ser usada somente quando o fluxo do núcleo a exigir.","ajuda"));
    }
    private void executar() {
        var op=seletor.getValue();if(op==null)return;
        try {
            Map<String,String> entrada=new LinkedHashMap<>();valores.forEach((k,v)->entrada.put(k,v.get()));
            Map<String,Object> dados=RequisicaoDaTela.ler(op,entrada,extras.getText());
            if(op.altera()&&!confirmar(this,op.restrita()?"Operação restrita":"Revisar execução",op.descricao()+"\n\nExecutar com os dados preenchidos?"))return;
            executar.setDisable(true);status.setText("Processando pela fachada oficial…");
            gateway.executarEmBackground(()->{
                try {
                    var r=RequisicaoDaTela.executar(gateway,op,dados);
                    String texto=r.mensagem()+"\n\n"+ResultadoLegivel.texto(r.dados());
                    Platform.runLater(()->{resposta.setText(texto);status.setText(r.sucesso()?"Operação respondida pelo núcleo.":"Operação recusada pelo núcleo.");executar.setDisable(false);});
                }catch(Throwable e){erro(e);}
            },this::erro);
        }catch(Exception e){status.setText(e.getMessage());}
    }
    private void erro(Throwable e){Platform.runLater(()->{status.setText("Falha: "+e.getMessage());executar.setDisable(false);});}
    private void salvarResultado() {
        FileChooser c=new FileChooser();c.setInitialFileName("resultado-operacao.txt");var f=c.showSaveDialog(getScene().getWindow());
        String conteudo=resposta.getText();
        if(f!=null)gateway.executarEmBackground(()->{
            try{java.nio.file.Files.writeString(f.toPath(),conteudo,java.nio.charset.StandardCharsets.UTF_8);}
            catch(Exception e){erro(e);}
        },this::erro);
    }
}
