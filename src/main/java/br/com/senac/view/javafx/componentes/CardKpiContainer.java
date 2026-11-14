package br.com.senac.view.javafx.componentes;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Container reutilizável de KPI em estilo cartão amigável.
 */
public class CardKpiContainer extends VBox {

    private final Label lblTitulo;
    private final Label lblValor;
    private final Label lblSubtitulo;

    public CardKpiContainer(String titulo, String valorInicial, String subtituloInicial, String classeCor) {
        getStyleClass().addAll("card-friendly", classeCor != null ? classeCor : "card-kpi-sage");
        setSpacing(6);
        setAlignment(Pos.TOP_LEFT);
        setMinWidth(200);
        setPrefWidth(240);

        lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("kpi-title");
        lblTitulo.setWrapText(true);

        lblValor = new Label(valorInicial);
        lblValor.getStyleClass().add("kpi-value");

        lblSubtitulo = new Label(subtituloInicial);
        lblSubtitulo.getStyleClass().add("kpi-subtitle");
        lblSubtitulo.setWrapText(true);

        getChildren().addAll(lblTitulo, lblValor, lblSubtitulo);
    }

    public void atualizar(String valor, String subtitulo) {
        lblValor.setText(valor != null ? valor : "—");
        if (subtitulo != null) {
            lblSubtitulo.setText(subtitulo);
        }
    }
}
