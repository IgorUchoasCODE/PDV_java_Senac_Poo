package br.com.senac.apresentacao;

import javafx.animation.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.*;

/** Decorador aditivo: cobre telas e modais criados sob demanda, sem editar suas classes. */
public final class ApresentacaoJavaFX {
    private static final String MARCA="sazonalisei.formatacao.v25";
    private final Timeline varredura=new Timeline(new KeyFrame(Duration.millis(350),e->atualizar()));
    public void iniciar(){varredura.setCycleCount(Animation.INDEFINITE);varredura.play();atualizar();}
    public void parar(){varredura.stop();}
    public void atualizar(){for(Window w:List.copyOf(Window.getWindows()))if(w.isShowing()&&w.getScene()!=null)aplicar(w.getScene().getRoot());}
    public void aplicar(Node n) {
        if(n.getProperties().putIfAbsent(MARCA,true)==null) {
            if(n instanceof TableCell<?,?> cell)observar(cell);
            else if(n instanceof ListCell<?> cell)observar(cell);
            else if(n instanceof Label label)observar(label);
            // Campos editáveis e conversores das telas continuam sob controle do núcleo.
            // Os ListCells das seleções recebem apenas uma representação textual legível.
        }
        if(n instanceof Parent p)for(Node filho:List.copyOf(p.getChildrenUnmodifiable()))aplicar(filho);
    }
    private void observar(Labeled controle) {
        boolean[] atualizando={false};
        Runnable formatar=()->{
            if(atualizando[0]||controle.textProperty().isBound())return;
            String antes=controle.getText();
            if(antes==null)return;
            String depois;
            if(controle instanceof TableCell<?,?> c) {
                if(c.isEditing()||c.isEmpty()||c.getGraphic()!=null)return;
                String coluna=c.getTableColumn()==null?"":c.getTableColumn().getText();
                Object item=c.getItem();
                // Preserva representações especializadas que a tela já calculou.
                depois=item!=null&&!(item instanceof CharSequence)&&antes.equals(String.valueOf(item))?
                        ValoresVisuais.apresentar(item,coluna):ValoresVisuais.texto(antes,coluna);
            }else if(controle instanceof ListCell<?> c) {
                if(c.isEditing()||c.isEmpty()||c.getGraphic()!=null)return;
                Object item=c.getItem();
                depois=item!=null&&antes.equals(String.valueOf(item))?ValoresVisuais.apresentar(item,""):ValoresVisuais.texto(antes,"");
            }else depois=ValoresVisuais.texto(antes,"");
            if(!Objects.equals(antes,depois))try{atualizando[0]=true;controle.setText(depois);}finally{atualizando[0]=false;}
        };
        controle.textProperty().addListener((o,a,b)->formatar.run());
        if(controle instanceof Cell<?> c){c.itemProperty().addListener((o,a,b)->formatar.run());c.emptyProperty().addListener((o,a,b)->formatar.run());}
        formatar.run();
    }
}
