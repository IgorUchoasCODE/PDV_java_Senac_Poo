package br.com.senac.view.javafx.componentes;

import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Painel corporativo compacto com cabeçalho executivo e gaveta expansível sob demanda.
 */
public class CardMacioExpansivel extends VBox {

    private final Label lblIcone;
    private final Label lblTitulo;
    private final Label lblSubtitulo;
    private final Label lblValorDestaque;
    private final Label lblBadge;
    private final Button btnIA;
    private final Button btnExpandir;
    private final VBox containerConteudo;
    private final HBox cabecalho;

    private boolean expandido;
    private String corBordaPersonalizada;

    public CardMacioExpansivel(
            String icone,
            String titulo,
            String subtitulo,
            String valorDestaque,
            boolean iniciarExpandido) {
        this.expandido = iniciarExpandido;

        setSpacing(8);
        setPadding(new Insets(10, 14, 10, 14));
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxWidth(Double.MAX_VALUE);

        this.lblIcone = new Label(icone != null ? icone : "▪");
        this.lblIcone.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        this.lblIcone.setStyle("-fx-font-size: 16px;");

        this.lblTitulo = FormatadorSeguroUI.criarLabel(titulo, 13, true, "#0F172A");
        this.lblSubtitulo = FormatadorSeguroUI.criarLabel(subtitulo, 12, false, "#475569");
        this.lblSubtitulo.setVisible(subtitulo != null && !subtitulo.isBlank());
        this.lblSubtitulo.setManaged(subtitulo != null && !subtitulo.isBlank());

        VBox boxTextos = new VBox(2, lblTitulo, lblSubtitulo);
        boxTextos.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(boxTextos, Priority.ALWAYS);

        this.lblValorDestaque = FormatadorSeguroUI.criarLabel(
                valorDestaque != null ? valorDestaque : "", 15, true, "#047857");
        this.lblValorDestaque.setMinWidth(Region.USE_PREF_SIZE);
        this.lblValorDestaque.setVisible(valorDestaque != null && !valorDestaque.isBlank());
        this.lblValorDestaque.setManaged(valorDestaque != null && !valorDestaque.isBlank());

        this.lblBadge = FormatadorSeguroUI.criarBadgeCartoon("", "#F1F5F9", "#CBD5E1", "#334155");
        this.lblBadge.setVisible(false);
        this.lblBadge.setManaged(false);

        this.btnIA = FormatadorSeguroUI.criarBotaoIA("💬 IA", null);
        this.btnIA.setVisible(false);
        this.btnIA.setManaged(false);

        this.btnExpandir = FormatadorSeguroUI.criarBotaoCartoon(
                iniciarExpandido ? "▲ Recolher" : "▼ Detalhes",
                "#F8FAFC", "#CBD5E1", "#334155");
        this.btnExpandir.setOnAction(e -> alternarExpansao());

        HBox acoesTopo = new HBox(8, lblBadge, lblValorDestaque, btnIA, btnExpandir);
        acoesTopo.setAlignment(Pos.CENTER_RIGHT);
        acoesTopo.setMinHeight(Region.USE_PREF_SIZE);

        this.cabecalho = new HBox(10, lblIcone, boxTextos, acoesTopo);
        this.cabecalho.setAlignment(Pos.CENTER_LEFT);
        this.cabecalho.setMinHeight(Region.USE_PREF_SIZE);
        this.cabecalho.setStyle("-fx-cursor: hand;");
        this.cabecalho.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) {
                alternarExpansao();
            }
        });

        this.containerConteudo = new VBox(8);
        this.containerConteudo.setPadding(new Insets(6, 2, 2, 2));
        this.containerConteudo.setMinHeight(Region.USE_PREF_SIZE);
        this.containerConteudo.setVisible(iniciarExpandido);
        this.containerConteudo.setManaged(iniciarExpandido);

        getChildren().addAll(cabecalho, containerConteudo);
        setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", "#CBD5E1"));
    }

    public void aplicarTema(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        var e = snap.estado();
        String border = corBordaPersonalizada != null ? corBordaPersonalizada : "#CBD5E1";
        setStyle(FormatadorSeguroUI.estiloCardCartoon(e.getBgCard(), border));
        FormatadorSeguroUI.configurarLabelSeguro(lblTitulo, 13, true, e.getTextPrimary());
        FormatadorSeguroUI.configurarLabelSeguro(lblSubtitulo, 12, false, e.getTextSecondary());
    }

    public void definirBordaDestaque(String borderHex) {
        this.corBordaPersonalizada = borderHex;
        setStyle(FormatadorSeguroUI.estiloCardCartoon("#FFFFFF", borderHex));
    }

    public void configurarBadge(String texto, String bgHex, String borderHex, String textHex) {
        if (texto == null || texto.isBlank()) {
            lblBadge.setVisible(false);
            lblBadge.setManaged(false);
            return;
        }
        lblBadge.setText(FormatadorSeguroUI.tratarParaExibicao(texto, ""));
        int px = FormatadorSeguroUI.garantirPisoFonte(11);
        lblBadge.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 4px; -fx-border-radius: 4px; "
                        + "-fx-padding: 2px 8px; -fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: %s;",
                bgHex, borderHex, px, textHex));
        lblBadge.setVisible(true);
        lblBadge.setManaged(true);
    }

    public void configurarBotaoIA(String rotulo, Runnable acao) {
        if (acao == null) {
            btnIA.setVisible(false);
            btnIA.setManaged(false);
            return;
        }
        btnIA.setText(rotulo != null && !rotulo.isBlank() ? rotulo : "💬 Analisar IA");
        btnIA.setOnAction(e -> acao.run());
        btnIA.setVisible(true);
        btnIA.setManaged(true);
    }

    public void definirValorDestaque(String valor, String corHex) {
        boolean tem = valor != null && !valor.isBlank();
        lblValorDestaque.setText(tem ? valor : "");
        if (corHex != null && !corHex.isBlank()) {
            int px = FormatadorSeguroUI.garantirPisoFonte(15);
            lblValorDestaque.setStyle(String.format(
                    "-fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: %s;", px, corHex));
        }
        lblValorDestaque.setVisible(tem);
        lblValorDestaque.setManaged(tem);
    }

    public void definirSubtitulo(String subtitulo) {
        boolean tem = subtitulo != null && !subtitulo.isBlank();
        lblSubtitulo.setText(tem ? FormatadorSeguroUI.tratarParaExibicao(subtitulo, "") : "");
        lblSubtitulo.setVisible(tem);
        lblSubtitulo.setManaged(tem);
    }

    public void definirConteudo(Node... nos) {
        containerConteudo.getChildren().setAll(nos);
    }

    public VBox getContainerConteudo() {
        return containerConteudo;
    }

    public void setExpandido(boolean expandir) {
        this.expandido = expandir;
        containerConteudo.setVisible(expandir);
        containerConteudo.setManaged(expandir);
        btnExpandir.setText(expandir ? "▲ Recolher" : "▼ Detalhes");
    }

    public void alternarExpansao() {
        setExpandido(!expandido);
    }

    public boolean isExpandido() {
        return expandido;
    }

    public void ocultarBotaoExpandir() {
        btnExpandir.setVisible(false);
        btnExpandir.setManaged(false);
    }
}
