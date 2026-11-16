package br.com.senac.view.javafx.tema;

import br.com.senac.controller.RotaSistema;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * Utilitário central de formatação brasileira, sanitização de dados e criação
 * de controles corporativos compactos para a interface JavaFX.
 *
 * <p>Mantém tipografia executiva enxuta (padrão 12px–13px, piso 11px),
 * quebra automática de linha ({@code wrapText = true}) sem estouro de containers
 * e tradução amigável de rotas e mensagens técnicas.</p>
 */
public final class FormatadorSeguroUI {

    public static final int PISO_FONTE_PX = 11;
    public static final Locale LOCALE_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String FAMILIA_FONTE = "Segoe UI";

    private static volatile int fonteBasePx = 13;
    private static final Properties PROPRIEDADES_CONTEXTO = carregarPropriedadesContexto();

    private FormatadorSeguroUI() {
    }

    private static Properties carregarPropriedadesContexto() {
        Properties props = new Properties();
        File arquivo = new File("config/ia-contexto.properties");
        if (arquivo.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(arquivo), StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (Exception ignored) {
            }
        }
        return props;
    }

    public static int getFonteBasePx() {
        return Math.max(12, Math.min(15, fonteBasePx));
    }

    public static void setFonteBasePx(int novoTamanho) {
        fonteBasePx = Math.max(12, Math.min(15, novoTamanho));
    }

    public static int garantirPisoFonte(int tamanhoDesejado) {
        int delta = getFonteBasePx() - 13;
        return Math.max(PISO_FONTE_PX, Math.min(24, tamanhoDesejado + delta));
    }

    public static Font fonteCartoon(int tamanhoPx, boolean negrito) {
        int finalPx = garantirPisoFonte(tamanhoPx);
        return Font.font(FAMILIA_FONTE, negrito ? FontWeight.BOLD : FontWeight.NORMAL, finalPx);
    }

    // ==================== CRIAÇÃO SEGURA DE COMPONENTES CORPORATIVOS ====================

    public static Label criarLabel(String texto, int tamanhoPx, boolean negrito, String corHex) {
        int tamanhoCompacto = Math.max(PISO_FONTE_PX, Math.min(20, tamanhoPx <= 15 ? 12 : (tamanhoPx >= 20 ? 15 : 13)));
        if (tamanhoPx == 11 || tamanhoPx == 12 || tamanhoPx == 13 || tamanhoPx == 14) {
            tamanhoCompacto = tamanhoPx;
        }
        Label label = new Label(tratarParaExibicao(texto, ""));
        configurarLabelCorporativo(label, tamanhoCompacto, negrito, corHex);
        return label;
    }

    public static void configurarLabelSeguro(Label label, int tamanhoPx, boolean negrito, String corHex) {
        int tamanhoCompacto = tamanhoPx <= 15 ? 12 : (tamanhoPx >= 20 ? 15 : 13);
        if (tamanhoPx == 11 || tamanhoPx == 12 || tamanhoPx == 13 || tamanhoPx == 14) {
            tamanhoCompacto = tamanhoPx;
        }
        configurarLabelCorporativo(label, tamanhoCompacto, negrito, corHex);
    }

    private static void configurarLabelCorporativo(Label label, int tamanhoPx, boolean negrito, String corHex) {
        if (label == null) return;
        int finalPx = garantirPisoFonte(tamanhoPx);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setFont(Font.font(FAMILIA_FONTE, negrito ? FontWeight.BOLD : FontWeight.NORMAL, finalPx));
        String corSegura = (corHex != null && !corHex.isBlank()) ? corHex : "#0F172A";
        label.setStyle(String.format(Locale.US,
                "-fx-font-size: %dpx; -fx-font-weight: %s; -fx-text-fill: %s;",
                finalPx, negrito ? "bold" : "normal", corSegura));
    }

    public static Label criarTituloSecao(String icone, String texto, String corHex) {
        String combinado = (icone != null && !icone.isBlank() ? icone + " " : "") + tratarParaExibicao(texto, "");
        Label l = new Label(combinado);
        configurarLabelCorporativo(l, 15, true, corHex != null ? corHex : "#0F172A");
        return l;
    }

    public static Label criarSubtitulo(String texto, String corHex) {
        Label l = new Label(tratarParaExibicao(texto, ""));
        configurarLabelCorporativo(l, 12, false, corHex != null ? corHex : "#475569");
        return l;
    }

    public static Label criarValorKpi(String texto, String corHex) {
        Label l = new Label(tratarParaExibicao(texto, ""));
        int finalPx = garantirPisoFonte(19);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setFont(Font.font(FAMILIA_FONTE, FontWeight.BOLD, finalPx));
        l.setStyle(String.format(Locale.US,
                "-fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: %s;",
                finalPx, corHex != null ? corHex : "#0F172A"));
        return l;
    }

    public static Label criarBadgeCartoon(String texto, String bgHex, String borderHex, String textHex) {
        Label badge = new Label(tratarParaExibicao(texto, ""));
        badge.setMinHeight(Region.USE_PREF_SIZE);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        int px = garantirPisoFonte(11);
        badge.setStyle(String.format(Locale.US,
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 4px; -fx-border-radius: 4px; "
                        + "-fx-padding: 2px 8px; -fx-font-size: %dpx; -fx-font-weight: bold; -fx-text-fill: %s;",
                bgHex, borderHex, px, textHex));
        return badge;
    }

    public static Button criarBotaoCartoon(String texto, String bgHex, String borderHex, String textHex) {
        Button btn = new Button(tratarParaExibicao(texto, "Ação"));
        aplicarEstiloBotaoCartoon(btn, bgHex, borderHex, textHex, 12);
        return btn;
    }

    public static void aplicarEstiloBotaoCartoon(Button btn, String bgHex, String borderHex, String textHex, int tamanhoPx) {
        if (btn == null) return;
        int compacto = tamanhoPx >= 15 ? 12 : Math.max(PISO_FONTE_PX, tamanhoPx);
        int px = garantirPisoFonte(compacto);
        btn.setWrapText(true);
        btn.setMinHeight(30);
        btn.setFont(Font.font(FAMILIA_FONTE, FontWeight.SEMI_BOLD, px));
        String estiloBase = String.format(Locale.US,
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                        + "-fx-padding: 5px 11px; -fx-font-size: %dpx; -fx-font-weight: 600; "
                        + "-fx-text-fill: %s; -fx-cursor: hand;",
                bgHex, borderHex, px, textHex);
        btn.setStyle(estiloBase);
    }

    public static void aplicarEstiloToggleCartoon(ToggleButton btn, boolean selecionado,
                                                  String bgNormal, String borderNormal, String textNormal,
                                                  String bgAtivo, String borderAtivo, String textAtivo) {
        if (btn == null) return;
        int px = garantirPisoFonte(12);
        btn.setWrapText(true);
        btn.setMinHeight(32);
        btn.setFont(Font.font(FAMILIA_FONTE, selecionado ? FontWeight.BOLD : FontWeight.SEMI_BOLD, px));
        String bg = selecionado ? bgAtivo : bgNormal;
        String border = selecionado ? borderAtivo : borderNormal;
        String txt = selecionado ? textAtivo : textNormal;
        btn.setStyle(String.format(Locale.US,
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                        + "-fx-padding: 6px 12px; -fx-font-size: %dpx; -fx-font-weight: %s; "
                        + "-fx-text-fill: %s; -fx-cursor: hand;",
                bg, border, px, selecionado ? "bold" : "600", txt));
    }

    public static Button criarBotaoIA(String texto, Runnable acao) {
        String rotulo = (texto != null && !texto.isBlank()) ? texto : "💬 Analisar com IA";
        Button btn = criarBotaoCartoon(rotulo, "#EFF6FF", "#93C5FD", "#1E3A8A");
        if (acao != null) {
            btn.setOnAction(e -> acao.run());
        }
        return btn;
    }

    public static TextField criarCampoTextoCartoon(String prompt, String borderHex, String textHex) {
        TextField tf = new TextField();
        tf.setPromptText(tratarParaExibicao(prompt, ""));
        tf.setPrefHeight(32);
        tf.setMinHeight(30);
        int px = garantirPisoFonte(12);
        tf.setFont(Font.font(FAMILIA_FONTE, FontWeight.NORMAL, px));
        tf.setStyle(String.format(Locale.US,
                "-fx-background-color: #FFFFFF; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                        + "-fx-padding: 4px 10px; -fx-font-size: %dpx; -fx-text-fill: %s;",
                borderHex != null ? borderHex : "#CBD5E1", px, textHex != null ? textHex : "#0F172A"));
        return tf;
    }

    public static TextArea criarAreaTextoCartoon(String prompt, String borderHex, String textHex) {
        TextArea ta = new TextArea();
        ta.setPromptText(tratarParaExibicao(prompt, ""));
        ta.setWrapText(true);
        int px = garantirPisoFonte(12);
        ta.setFont(Font.font(FAMILIA_FONTE, FontWeight.NORMAL, px));
        ta.setStyle(String.format(Locale.US,
                "-fx-control-inner-background: #FFFFFF; -fx-background-color: #FFFFFF; "
                        + "-fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                        + "-fx-padding: 4px 8px; -fx-font-size: %dpx; -fx-text-fill: %s;",
                borderHex != null ? borderHex : "#CBD5E1", px, textHex != null ? textHex : "#0F172A"));
        return ta;
    }

    public static <T> ComboBox<T> criarComboBoxCartoon(String borderHex, String textHex) {
        ComboBox<T> cb = new ComboBox<>();
        cb.setPrefHeight(32);
        cb.setMinHeight(30);
        cb.setMaxWidth(Double.MAX_VALUE);
        int px = garantirPisoFonte(12);
        String b = borderHex != null ? borderHex : "#CBD5E1";
        String t = textHex != null ? textHex : "#0F172A";
        cb.setStyle(String.format(Locale.US,
                "-fx-background-color: #FFFFFF; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; "
                        + "-fx-padding: 2px 8px; -fx-font-size: %dpx; -fx-text-fill: %s;",
                b, px, t));
        cb.setButtonCell(criarCelulaComboSegura(px, t));
        cb.setCellFactory(listView -> criarCelulaComboSegura(px, t));
        return cb;
    }

    private static <T> ListCell<T> criarCelulaComboSegura(int px, String textHex) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(tratarParaExibicao(item.toString(), ""));
                    setWrapText(true);
                    setMinHeight(Region.USE_PREF_SIZE);
                    setStyle(String.format(Locale.US, "-fx-font-size: %dpx; -fx-text-fill: %s;", px, textHex));
                }
            }
        };
    }

    public static TabPane criarTabPaneCorporativo() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.setStyle("-fx-background-color: transparent;");
        return tp;
    }

    public static <T> void configurarTabelaCorporativa(TableView<T> tabela, double alturaPref) {
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabela.setPrefHeight(alturaPref);
        tabela.setMinHeight(180);
        tabela.setStyle(String.format(Locale.US,
                "-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px; -fx-font-size: %dpx;",
                garantirPisoFonte(12)));
    }

    public static ScrollPane envolverScrollSuave(Region conteudo) {
        ScrollPane scroll = new ScrollPane(conteudo);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        return scroll;
    }

    public static String estiloCardCartoon(String bgHex, String borderHex) {
        return String.format(Locale.US,
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 8px; -fx-border-radius: 8px; "
                        + "-fx-effect: dropshadow(three-pass-box, rgba(15, 23, 42, 0.05), 8, 0, 0, 2);",
                bgHex != null ? bgHex : "#FFFFFF",
                borderHex != null ? borderHex : "#E2E8F0");
    }

    public static void aplicarCardCartoon(Region region, String bgHex, String borderHex, Insets padding) {
        if (region == null) return;
        region.setStyle(estiloCardCartoon(bgHex, borderHex));
        if (padding != null && region instanceof javafx.scene.layout.Pane pane) {
            pane.setPadding(padding);
        }
        if (region instanceof Control c) {
            c.setMinHeight(Region.USE_PREF_SIZE);
        }
    }

    public static String estiloCartaoSuaveInterno(String bgHex, String borderHex) {
        return String.format(Locale.US,
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1px; "
                        + "-fx-background-radius: 6px; -fx-border-radius: 6px;",
                bgHex != null ? bgHex : "#F8FAFC",
                borderHex != null ? borderHex : "#CBD5E1");
    }

    public static void garantirMinimoAlturaNos(Node... nodes) {
        if (nodes == null) return;
        for (Node n : nodes) {
            if (n instanceof Region r) {
                r.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
    }

    // ==================== SANITIZAÇÃO E FORMATAÇÃO BRASILEIRA ====================

    public static String tratarParaExibicao(Object valor, String padraoSeVazio) {
        String fallback = padraoSeVazio != null ? padraoSeVazio : "Não informado";
        if (valor == null) {
            return fallback;
        }
        String texto = valor.toString().trim();
        if (texto.isEmpty()
                || "null".equalsIgnoreCase(texto)
                || "undefined".equalsIgnoreCase(texto)
                || "N/A".equalsIgnoreCase(texto)
                || "nan".equalsIgnoreCase(texto)) {
            return fallback;
        }
        if ((texto.startsWith("{") && texto.endsWith("}")) || (texto.startsWith("[") && texto.endsWith("]"))) {
            String limpo = texto.replaceAll("[{}\\[\\]\"]", " ").replaceAll("\\s+", " ").trim();
            return limpo.isEmpty() ? fallback : limpo;
        }
        if (texto.contains("Exception:") || texto.contains("\tat br.com.")) {
            return traduzirErroAmigavel(texto);
        }
        return texto;
    }

    public static String formatarMoeda(Number valor) {
        BigDecimal bd = BigDecimal.ZERO;
        if (valor instanceof BigDecimal b) {
            bd = b;
        } else if (valor != null) {
            bd = BigDecimal.valueOf(valor.doubleValue());
        }
        NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_BR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(bd.setScale(2, RoundingMode.HALF_EVEN));
    }

    public static String formatarDecimal(double valor, int casas) {
        NumberFormat nf = NumberFormat.getNumberInstance(LOCALE_BR);
        nf.setMinimumFractionDigits(casas);
        nf.setMaximumFractionDigits(casas);
        return nf.format(valor);
    }

    public static String formatarQuantidade(Number valor, String siglaUm) {
        BigDecimal bd = BigDecimal.ZERO;
        if (valor instanceof BigDecimal b) {
            bd = b;
        } else if (valor != null) {
            bd = BigDecimal.valueOf(valor.doubleValue());
        }
        BigDecimal semZeros = bd.stripTrailingZeros();
        if (semZeros.scale() < 0) {
            semZeros = semZeros.setScale(0, RoundingMode.UNNECESSARY);
        }
        String numBr = semZeros.toPlainString().replace('.', ',');
        String um = (siglaUm != null && !siglaUm.isBlank() && !"null".equalsIgnoreCase(siglaUm))
                ? " " + siglaUm.trim().toUpperCase(Locale.ROOT)
                : "";
        return numBr + um;
    }

    public static String formatarDataAmigavel(LocalDate data) {
        if (data == null) {
            return "Indeterminada";
        }
        LocalDate hoje = LocalDate.now();
        String base = data.format(DATA_BR);
        if (data.equals(hoje)) return base + " (Hoje)";
        if (data.equals(hoje.minusDays(1))) return base + " (Ontem)";
        if (data.equals(hoje.plusDays(1))) return base + " (Amanhã)";
        return base;
    }

    public static String formatarDataHoraAmigavel(LocalDateTime dataHora) {
        if (dataHora == null) {
            return "Sem registro";
        }
        LocalDate hoje = LocalDate.now();
        LocalDate d = dataHora.toLocalDate();
        if (d.equals(hoje)) return dataHora.format(DATA_HORA_BR) + " (Hoje)";
        if (d.equals(hoje.minusDays(1))) return dataHora.format(DATA_HORA_BR) + " (Ontem)";
        return dataHora.format(DATA_HORA_BR);
    }

    public static String formatarDistanciaKm(double km) {
        if (km < 0 || Double.isNaN(km)) {
            return "Não calculada";
        }
        int min = Math.max(2, (int) Math.round(km * 3.0));
        return String.format(LOCALE_BR, "%.1f km (~%d min)", km, min);
    }

    public static String traduzirRotaAmigavel(RotaSistema rota) {
        if (rota == null) return "Operação Comercial";
        return switch (rota) {
            case SISTEMA_INICIALIZAR_DADOS_PADRAO -> "Preparar dados iniciais da loja";
            case SISTEMA_RESETAR_BANCO_RESTRITO -> "Reiniciar base de demonstração";
            case BUSCA_PADRAO_IA -> "Consulta estruturada do sistema";
            case PRODUTO_UNIDADE_LISTAR -> "Listar unidades de medida";
            case PRODUTO_UNIDADE_CADASTRAR -> "Cadastrar unidade de medida";
            case PRODUTO_UNIDADE_ATUALIZAR -> "Atualizar unidade de medida";
            case PRODUTO_UNIDADE_EXCLUIR -> "Excluir unidade de medida";
            case PRODUTO_FISICO_CADASTRAR -> "Cadastrar produto físico";
            case PRODUTO_FISICO_ATUALIZAR_DIMENSOES -> "Atualizar dimensões e pesos do produto";
            case PRODUTO_DIGITAL_CADASTRAR -> "Cadastrar produto digital";
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> "Vincular componente a kit/conjunto";
            case PRODUTO_CATALOGO_LISTAR -> "Listar catálogo de produtos";
            case PRODUTO_CATALOGO_BUSCAR, PRODUTO_BUSCAR_UNICO -> "Buscar produto no catálogo";
            case PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO, PRODUTO_ESTOQUE_VALIDO_LISTAR -> "Listar produtos com estoque válido";
            case PRODUTO_CATALOGO_ATUALIZAR -> "Atualizar cadastro do produto";
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> "Atualizar preço de venda";
            case PRODUTO_CATALOGO_EXCLUIR -> "Excluir produto do catálogo";
            case PRODUTO_IMAGEM_ADICIONAR -> "Adicionar imagem ao produto";
            case PRODUTO_IMAGEM_LISTAR, PRODUTO_IMAGEM_PRINCIPAL_OBTER -> "Consultar imagens do produto";
            case PRODUTO_IMAGEM_DEFINIR_PRINCIPAL -> "Definir imagem principal";
            case PRODUTO_IMAGEM_REMOVER -> "Remover imagem do produto";
            case PESSOA_CLIENTE_PADRAO_OBTER -> "Consumidor Final de balcão";
            case PESSOA_SIMPLES_CADASTRAR -> "Cadastrar pessoa simples";
            case PESSOA_CLIENTE_PF_CADASTRAR -> "Cadastrar cliente Pessoa Física";
            case PESSOA_CLIENTE_PJ_CADASTRAR -> "Cadastrar cliente Pessoa Jurídica";
            case PESSOA_CLIENTE_ATUALIZAR_LIMITE -> "Atualizar limite de crédito";
            case PESSOA_DADOS_ATUALIZAR -> "Atualizar dados cadastrais";
            case PESSOA_FORNECEDOR_PJ_CADASTRAR -> "Cadastrar fornecedor Pessoa Jurídica";
            case PESSOA_FORNECEDOR_PF_CADASTRAR -> "Cadastrar fornecedor Pessoa Física";
            case PESSOA_REGISTRO_ADICIONAR -> "Adicionar contato ou documento";
            case PESSOA_LISTAR_CLIENTES -> "Listar clientes";
            case PESSOA_LISTAR_FORNECEDORES -> "Listar fornecedores";
            case PESSOA_BUSCAR, PESSOA_BUSCAR_UNICA, PESSOA_REGISTRO_BUSCAR_UNICO -> "Buscar cliente ou fornecedor";
            case PESSOA_HISTORICO_COMPLETO -> "Consultar histórico 360º";
            case PESSOA_CLIENTE_EXCLUIR -> "Excluir pessoa";
            case PAGAMENTO_FORMA_LISTAR_ATIVAS -> "Listar formas de pagamento";
            case PAGAMENTO_FORMA_CADASTRAR -> "Cadastrar forma de pagamento";
            case PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS -> "Configurar taxas e parcelamento";
            case PAGAMENTO_FORMA_ALTERAR_STATUS -> "Alterar status de forma de pagamento";
            case PAGAMENTO_FORMA_EXCLUIR -> "Excluir forma de pagamento";
            case PAGAMENTO_CRIAR_A_VISTA -> "Registrar pagamento à vista";
            case PAGAMENTO_CRIAR_A_PRAZO -> "Registrar pagamento a prazo";
            case PAGAMENTO_CRIAR_PARCELADO -> "Registrar pagamento parcelado";
            case PAGAMENTO_QUITAR_TITULO_OU_PARCELA -> "Quitar título ou parcela";
            case ESTOQUE_SALDO_CONSOLIDADO -> "Consultar saldo consolidado";
            case ESTOQUE_FILA_LOTES_FIFO, ESTOQUE_LOTES_VALIDOS_LISTAR, ESTOQUE_LOTE_VALIDO_BUSCAR_UNICO,
                 ESTOQUE_BUSCAR_LOTE_ESPECIFICO -> "Consultar lotes em estoque";
            case ESTOQUE_LOTES_PROXIMOS_VALIDADE -> "Lotes próximos do vencimento";
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> "Registrar perda em estoque";
            case NOTA_COMPRA_EMITIR -> "Emitir Nota de Entrada (Compra)";
            case NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR -> "Abater perdas com fornecedor";
            case NOTA_COMPRA_SUGERIR_PRECO_VENDA -> "Sugerir preço de venda";
            case NOTA_VENDA_EMITIR_FIFO -> "Emitir Nota de Venda";
            case NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> "Emitir venda rápida balcão";
            case NOTA_VENDA_EMITIR_LOTE_DESEJAVEL -> "Emitir venda por lote específico";
            case NOTA_VENDA_PROCEDURE_MYSQL_FIFO, NOTA_VENDA_PROCEDURE_MYSQL_LOTE -> "Baixar estoque automaticamente";
            case NOTA_DEVOLUCAO_REGISTRAR -> "Registrar devolução / perda pós-venda";
            case NOTA_BUSCAR_UNICA, NOTA_LISTAR_PADRAO -> "Consultar notas operacionais";
            case NOTA_DEFINIR_DATA -> "Atualizar data da nota";
            case RELATORIO_LUCRO_CONTABIL_BACEN -> "Relatório de lucro contábil BACEN";
            case RELATORIO_PERDAS_POR_FORNECEDOR -> "Relatório de perdas por fornecedor";
            case ESTATISTICA_DEMANDA_MENSAL_CLIENTE -> "Previsão de demanda mensal";
            case ESTATISTICA_PERFIL_CLIENTE -> "Perfil de consumo dos clientes";
            case ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS -> "Impacto climático e sazonal nos produtos";
            case ESTATISTICA_RANKING_FORNECEDORES -> "Ranking de fornecedores";
            case ESTATISTICA_MOTIVOS_PERDA_PREMATURA -> "Diagnóstico de perda prematura";
            case ESTATISTICA_DESTAQUES_SAZONAIS -> "Destaques sazonais";
            case ESTATISTICA_PICOS_DIAS_EVENTOS -> "Dias de pico e eventos";
            case ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS -> "Plano estratégico de compras";
            case ESTATISTICA_CAUSAS_EXTERNAS_NOTA -> "Causas externas da nota";
            case ESTATISTICA_RECALCULAR_FATORES, ESTATISTICA_FATORES_PRODUTO -> "Estatística de fatores externos";
            case FATOR_EXTERNO_EVENTOS_LISTAR -> "Eventos regionais persistidos";
            case FATOR_EXTERNO_RESOLVER_PENDENCIAS -> "Resolver fatores externos pendentes";
            case FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS, RELATORIO_ANALISE_SAZONAL_VENDAS -> "Relatório sazonal integrado";
        };
    }

    public static String traduzirErroAmigavel(String mensagemCrua) {
        if (mensagemCrua == null || mensagemCrua.isBlank()) {
            return PROPRIEDADES_CONTEXTO.getProperty(
                    "ia.msg.erro.generico",
                    "Não foi possível concluir a operação. Verifique os dados informados.");
        }
        String limpa = mensagemCrua.trim();
        String lower = limpa.toLowerCase(LOCALE_BR);
        if ((lower.contains("estoque") && lower.contains("positivo")) || lower.contains("possui saldo")) {
            return "Produto possui saldo em estoque. Dê baixa ou venda o saldo antes de excluir.";
        }
        if (lower.contains("estoque insuficiente") || lower.contains("saldo insuficiente")) {
            return "Saldo em estoque insuficiente para a quantidade solicitada.";
        }
        if (lower.contains("limite") && lower.contains("exced")) {
            return "O valor a prazo excede o limite de crédito cadastrado para o cliente.";
        }
        if (lower.contains("exception") || lower.contains("sql") || lower.contains("hibernate")) {
            return PROPRIEDADES_CONTEXTO.getProperty(
                    "ia.msg.erro.generico",
                    "Não foi possível persistir o registro. Confira os parâmetros ou consulte o Assistente IA.");
        }
        return limpa.replaceAll("^(?:java\\.lang\\.[A-Za-z]+Exception:\\s*)+", "").trim();
    }

    public static String limparNomeProduto(String nome) {
        if (nome == null) return "Produto";
        String limpo = nome.replaceAll("\\s+", " ").trim();
        return limpo.isEmpty() ? "Produto" : limpo;
    }

    /**
     * Renderiza um mapa chave-valor formatado em grade corporativa limpa (2 colunas),
     * substituindo concatenações em texto estilo terminal por cartões legíveis.
     */
    public static GridPane criarGradeMetadadosCards(java.util.Map<String, String> campos) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            c.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(c);
        }
        if (campos == null || campos.isEmpty()) {
            return grid;
        }
        int idx = 0;
        for (var entry : campos.entrySet()) {
            VBox cell = criarMiniCardInfo(entry.getKey(), entry.getValue(), "#F8FAFC", "#E2E8F0", "#0F172A");
            grid.add(cell, idx % 2, idx / 2);
            idx++;
        }
        return grid;
    }

    public static VBox criarMiniCardInfo(String rotulo, String valor, String bgHex, String borderHex, String accentHex) {
        Label lRot = criarLabel(rotulo, 11, true, "#64748B");
        Label lVal = criarLabel(tratarParaExibicao(valor, "—"), 12, true, accentHex != null ? accentHex : "#0F172A");
        VBox box = new VBox(3, lRot, lVal);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setMinHeight(Region.USE_PREF_SIZE);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle(estiloCartaoSuaveInterno(bgHex, borderHex));
        return box;
    }

    public static BigDecimal parseDecimalSeguro(String texto, BigDecimal padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            String limpo = texto.trim().replace("R$", "").replace(" ", "");
            if (limpo.contains(",")) {
                limpo = limpo.replace(".", "").replace(",", ".");
            }
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return padrao;
        }
    }
}
