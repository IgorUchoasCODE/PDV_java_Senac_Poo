package br.com.senac.ui.documentada;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import java.util.function.*;
import java.text.NumberFormat;
import java.math.BigDecimal;
import java.util.Locale;

final class ComponentesUI {
    private ComponentesUI() {}
    static Label texto(String s,String classe) { Label l=new Label(s);l.getStyleClass().add(classe);l.setWrapText(true);return l; }
    static Button botao(String s,String id,Runnable acao) { Button b=new Button(s);b.setId(id);b.setOnAction(e->acao.run());return b; }
    static VBox card(String nome,Node... filhos) { VBox v=new VBox(12,texto(nome,"titulo-card"));v.getStyleClass().add("cartao");v.getChildren().addAll(filhos);return v; }
    static ScrollPane rolagem(Node n) { ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);return s; }
    static HBox linha(Node... nodes) { HBox h=new HBox(10,nodes);h.setAlignment(Pos.CENTER_LEFT);return h; }
    static boolean confirmar(Node dono,String titulo,String mensagem) {
        Alert a=new Alert(Alert.AlertType.CONFIRMATION,mensagem,ButtonType.OK,ButtonType.CANCEL);
        a.setTitle(titulo);a.setHeaderText(titulo);
        if(dono.getScene()!=null)a.initOwner(dono.getScene().getWindow());
        return a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
    static String moeda(BigDecimal n) {return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(n==null?BigDecimal.ZERO:n);}
}
