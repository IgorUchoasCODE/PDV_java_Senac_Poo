package br.com.senac.ui.documentada;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import static br.com.senac.ui.documentada.ComponentesUI.*;

/** Não cria uma bandeja paralela: exibe e opera a BandejaOperacoesContexto do chat oficial. */
public final class BandejaUI extends BorderPane {
    private final GatewayDaSessao gateway;
    private final TextArea conteudo=new TextArea();
    private final Label status=new Label("Use o chat ou a DSL $$ para preparar as operações.");
    private final TextField operacao=new TextField("OP-1");
    private final TextField edicao=new TextField();
    private volatile boolean enviando;
    public BandejaUI(GatewayDaSessao gateway) {
        this.gateway=gateway;
        setId("bandeja-oficial");
        conteudo.setEditable(false);conteudo.setWrapText(true);conteudo.setId("conteudo-bandeja");
        operacao.setPromptText("OP-1");operacao.setPrefWidth(90);
        edicao.setPromptText("Ex.: $$ U= id> OP-1, vv> 25.90;");
        edicao.setId("editar-bandeja");
        FlowPane comandos=new FlowPane(10,10,
                botao("Atualizar", "bandeja-atualizar",this::atualizar),
                botao("Confirmar SIM", "bandeja-confirmar",()->{
                    if(confirmar(this,"Confirmar bandeja","Executar no banco as operações que o núcleo marcou como válidas?"))enviar("SIM");
                }),
                botao("Cancelar NÃO", "bandeja-cancelar",()->{
                    if(confirmar(this,"Cancelar bandeja","Descartar as operações pendentes desta sessão?"))enviar("NÃO");
                }),botao("Próxima", "bandeja-proxima",()->enviar("próxima")),
                operacao,botao("Executar OP", "bandeja-executar-op",()->comandoOp("executar ")),
                botao("Pular OP", "bandeja-pular-op",()->comandoOp("pular ")),
                botao("Retomar OP", "bandeja-retomar-op",()->comandoOp("retomar ")));
        VBox root=card("Bandeja de contexto · mesma sessão do chatbot",status,comandos,conteudo,
                texto("Edição pelo compilador oficial de comandos curtos","rotulo"),
                linha(edicao,botao("Enviar edição","bandeja-editar",()->{
                    if(!edicao.getText().trim().startsWith("$$")){status.setText("A edição deve começar por $$.");return;}
                    enviar(edicao.getText());
                })));
        VBox.setVgrow(conteudo,Priority.ALWAYS);HBox.setHgrow(edicao,Priority.ALWAYS);setCenter(root);
    }
    private void comandoOp(String prefixo) {
        if(!operacao.getText().trim().matches("(?i)OP-\\d+")){status.setText("Informe uma operação como OP-1.");return;}
        enviar(prefixo+operacao.getText().trim());
    }
    public void atualizar() {
        gateway.executarEmBackground(()->{
            String r=gateway.executarComandoTerminalChat("",":bandeja");
            Platform.runLater(()->{conteudo.setText(limpar(r));status.setText("Consulta da bandeja concluída.");});
        },e->Platform.runLater(()->status.setText(e.getMessage())));
    }
    public boolean enviando(){return enviando;}
    private void enviar(String comando) {
        enviando=true;
        status.setText("Processando no núcleo…");
        gateway.executarEmBackground(()->{
            String r=gateway.perguntarAoChat("",comando);
            Platform.runLater(()->{enviando=false;conteudo.setText(limpar(r));status.setText("Resposta do núcleo recebida.");});
        },e->Platform.runLater(()->{enviando=false;status.setText(e.getMessage());}));
    }
    private String limpar(String s) {return s==null?"Sem resposta.":s.replaceAll("\\x1B\\[[0-9;]*m","");}
}
