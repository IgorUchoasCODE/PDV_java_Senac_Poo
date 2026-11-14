package br.com.senac.view.javafx.componentes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trata e adapta as respostas do agente/terminal para exibição limpa e formatada (Markdown -> JavaFX)
 * em balões na interface gráfica JavaFX (remove códigos ANSI, renderiza títulos, negrito, itálico,
 * listas e tabelas Markdown em componentes nativos).
 */
public final class FormatadorBalaoChatUI {

    private static final Pattern PADRAO_ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern BORDA_ASCII = Pattern.compile("(?m)^[=\\-]{8,}\\s*$");
    private static final Pattern QUEBRAS_MULTIPLAS = Pattern.compile("\\n{3,}");
    private static final Pattern TOKEN_INLINE = Pattern.compile("(\\*\\*[^*]+\\*\\*|\\*[^*\\n]+\\*|`[^`]+`)");
    private static final Pattern LINHA_ITEM_CATALOGO = Pattern.compile("^#(\\d+)\\s*-\\s*(.+?)\\s+[—-]\\s+(.+?)\\s*\\|\\s*(.+)$");

    public enum TipoBalao {
        USUARIO("chat-bubble-user"),
        PADRAO("chat-bubble-bot"),
        SUCESSO("chat-bubble-success"),
        ERRO("chat-bubble-error"),
        BANDEJA("chat-bubble-bandeja");

        private final String classeCss;

        TipoBalao(String classeCss) {
            this.classeCss = classeCss;
        }

        public String getClasseCss() {
            return classeCss;
        }
    }

    public record MensagemTratadaUI(String textoLimpo, TipoBalao tipo, boolean requerConfirmacaoBandeja) {
    }

    private FormatadorBalaoChatUI() {
    }

    public static MensagemTratadaUI tratarParaBalao(String textoBruto, boolean ehUsuario) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return new MensagemTratadaUI("...", ehUsuario ? TipoBalao.USUARIO : TipoBalao.PADRAO, false);
        }

        String limpo = PADRAO_ANSI.matcher(textoBruto).replaceAll("");
        limpo = limpo.replace("\uFE0F", "");
        limpo = BORDA_ASCII.matcher(limpo).replaceAll("");
        limpo = QUEBRAS_MULTIPLAS.matcher(limpo).replaceAll("\n\n").trim();

        if (ehUsuario) {
            return new MensagemTratadaUI(limpo, TipoBalao.USUARIO, false);
        }

        String upper = limpo.toUpperCase();
        boolean temBandeja = upper.contains("BANDEJA DE CONTEXTO ATIVA")
                || upper.contains("CONFIRMA A EXECUÇÃO")
                || upper.contains("DIGITE SIM PARA SALVAR");

        if (temBandeja) {
            return new MensagemTratadaUI(limpo, TipoBalao.BANDEJA, true);
        }

        if (limpo.contains("❌") || upper.startsWith("ERRO") || upper.contains("NÃO FOI POSSÍVEL")
                || upper.contains("FALHA AO PROCESSAR") || upper.contains("BLOQUEADA")) {
            return new MensagemTratadaUI(limpo, TipoBalao.ERRO, false);
        }

        if (limpo.contains("✔") || upper.contains("BANDEJA DE OPERAÇÕES EXECUTADA COM SUCESSO")
                || upper.contains("[OK]")) {
            return new MensagemTratadaUI(limpo, TipoBalao.SUCESSO, false);
        }

        return new MensagemTratadaUI(limpo, TipoBalao.PADRAO, false);
    }

    /**
     * Renderiza texto Markdown (títulos #, negrito **, itálico *, código `, listas e tabelas | col |)
     * em nós JavaFX nativos e bem alinhados dentro do balão de chat.
     */
    public static VBox renderizarConteudoRico(String texto, boolean ehUsuario) {
        VBox container = new VBox(5);
        String[] linhas = texto.replace("\r\n", "\n").split("\n", -1);
        Color corTexto = ehUsuario ? Color.WHITE : Color.web("#E2E8F0");

        int i = 0;
        while (i < linhas.length) {
            String linha = linhas[i];
            String trimmed = linha.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            // Tabela Markdown: coleta linhas consecutivas que começam com |
            if (trimmed.startsWith("|") && trimmed.contains("|")) {
                List<String> linhasTabela = new ArrayList<>();
                while (i < linhas.length && linhas[i].trim().startsWith("|")) {
                    linhasTabela.add(linhas[i].trim());
                    i++;
                }
                container.getChildren().add(criarTabelaMarkdown(linhasTabela, corTexto));
                continue;
            }

            // Títulos (#, ##, ###)
            if (trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                int nivel = trimmed.startsWith("### ") ? 3 : (trimmed.startsWith("## ") ? 2 : 1);
                String tituloTexto = trimmed.replaceFirst("^#{1,3}\\s+", "");
                Label lblTitulo = new Label(removerMarcadoresSimples(tituloTexto));
                lblTitulo.setWrapText(true);
                lblTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: " + (nivel == 1 ? 15 : (nivel == 2 ? 14 : 13))
                        + "px; -fx-text-fill: #38BDF8; -fx-padding: 4 0 2 0;");
                container.getChildren().add(lblTitulo);
                i++;
                continue;
            }

            // Linha estruturada de item/produto (#ID - Nome — Preço | Estoque/Alerta)
            Matcher mItem = LINHA_ITEM_CATALOGO.matcher(trimmed);
            if (mItem.matches()) {
                container.getChildren().add(criarCardItemMapeado(
                        mItem.group(1), mItem.group(2), mItem.group(3), mItem.group(4)));
                i++;
                continue;
            }

            // Lista com marcadores (- ou *)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                String itemTexto = "• " + trimmed.substring(2).trim();
                TextFlow tf = criarTextFlowComInline(itemTexto, corTexto, 12.5);
                tf.setPadding(new Insets(0, 0, 0, 8));
                container.getChildren().add(tf);
                i++;
                continue;
            }

            container.getChildren().add(criarTextFlowComInline(trimmed, corTexto, 12.5));
            i++;
        }

        return container;
    }

    private static TextFlow criarTextFlowComInline(String texto, Color corBase, double tamanhoFonte) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2.0);
        Matcher m = TOKEN_INLINE.matcher(texto);
        int pos = 0;

        while (m.find()) {
            if (m.start() > pos) {
                Text normal = new Text(texto.substring(pos, m.start()));
                normal.setFill(corBase);
                normal.setFont(Font.font("Segoe UI", FontWeight.NORMAL, tamanhoFonte));
                flow.getChildren().add(normal);
            }
            String token = m.group(1);
            if (token.startsWith("**") && token.endsWith("**")) {
                Text negrito = new Text(token.substring(2, token.length() - 2));
                negrito.setFill(Color.web("#F8FAFC"));
                negrito.setFont(Font.font("Segoe UI", FontWeight.BOLD, tamanhoFonte));
                flow.getChildren().add(negrito);
            } else if (token.startsWith("*") && token.endsWith("*")) {
                Text italico = new Text(token.substring(1, token.length() - 1));
                italico.setFill(Color.web("#BAE6FD"));
                italico.setFont(Font.font("Segoe UI", FontPosture.ITALIC, tamanhoFonte));
                flow.getChildren().add(italico);
            } else if (token.startsWith("`") && token.endsWith("`")) {
                Text codigo = new Text(token.substring(1, token.length() - 1));
                codigo.setFill(Color.web("#2DD4BF"));
                codigo.setFont(Font.font("Consolas", FontWeight.BOLD, tamanhoFonte - 0.5));
                flow.getChildren().add(codigo);
            }
            pos = m.end();
        }

        if (pos < texto.length()) {
            Text resto = new Text(texto.substring(pos));
            resto.setFill(corBase);
            resto.setFont(Font.font("Segoe UI", FontWeight.NORMAL, tamanhoFonte));
            flow.getChildren().add(resto);
        }

        return flow;
    }

    private static GridPane criarTabelaMarkdown(List<String> linhasTabela, Color corTexto) {
        GridPane grid = new GridPane();
        grid.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6;");
        grid.setPadding(new Insets(4));
        grid.setHgap(1);
        grid.setVgap(1);

        int rowIdx = 0;
        int maxCols = 0;
        for (String linha : linhasTabela) {
            // Ignora linha separadora | :--- | :---: |
            if (linha.matches("^\\|[\\s:\\-|]+\\|?$")) {
                continue;
            }
            String semBordas = linha.replaceAll("^\\|", "").replaceAll("\\|$", "");
            String[] celulas = semBordas.split("\\|", -1);
            maxCols = Math.max(maxCols, celulas.length);

            boolean cabecalho = (rowIdx == 0);
            for (int c = 0; c < celulas.length; c++) {
                String conteudoCelula = celulas[c].trim();
                TextFlow tf = criarTextFlowComInline(conteudoCelula, cabecalho ? Color.web("#38BDF8") : corTexto, 11.5);
                VBox cellBox = new VBox(tf);
                cellBox.setPadding(new Insets(5, 7, 5, 7));
                cellBox.setStyle("-fx-background-color: " + (cabecalho ? "#111827" : (rowIdx % 2 == 0 ? "#131D31" : "#0F172A")) + ";");
                grid.add(cellBox, c, rowIdx);
            }
            rowIdx++;
        }

        for (int c = 0; c < maxCols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setPercentWidth(100.0 / Math.max(maxCols, 1));
            grid.getColumnConstraints().add(cc);
        }
        return grid;
    }

    private static VBox criarCardItemMapeado(String id, String nome, String valor, String detalhe) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(7, 10, 7, 10));
        card.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label lblId = new Label("#" + id);
        lblId.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #38BDF8; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");

        Label lblNome = new Label(nome.trim());
        lblNome.setWrapText(true);
        lblNome.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-font-size: 12px;");
        HBox.setHgrow(lblNome, Priority.ALWAYS);

        Label lblValor = new Label(valor.trim());
        lblValor.setStyle("-fx-text-fill: #2DD4BF; -fx-font-weight: bold; -fx-font-size: 12px;");

        HBox topo = new HBox(8, lblId, lblNome, lblValor);
        topo.setAlignment(Pos.CENTER_LEFT);

        String detLimpo = detalhe.trim();
        boolean alerta = detLimpo.toLowerCase().contains("vencido") || detLimpo.contains("⚠");
        boolean semEstoque = detLimpo.toLowerCase().contains("sem estoque");

        Label lblDet = new Label(detLimpo);
        lblDet.setWrapText(true);
        String corDet = alerta ? "#F87171" : (semEstoque ? "#F59E0B" : "#94A3B8");
        lblDet.setStyle("-fx-text-fill: " + corDet + "; -fx-font-size: 11.5px;");

        FlowPane rodape = new FlowPane(6, 4, lblDet);
        card.getChildren().addAll(topo, rodape);
        return card;
    }

    private static String removerMarcadoresSimples(String s) {
        return s.replace("**", "").replace("`", "");
    }
}
