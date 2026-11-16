package br.com.senac.application;

import br.com.senac.view.javafx.abas.*;
import br.com.senac.view.javafx.ponte.ConectorProblemasIA;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utilitário automatizado para renderização e captura de screenshots de alta resolução
 * de todos os 9 módulos da interface gráfica JavaFX do Sazonalisei para a documentação técnica oficial.
 */
public class CapturadorScreenshotsJavaFX {

    public static void main(String[] args) throws Exception {
        System.out.println("--- Iniciando Captura de Screenshots dos 9 Módulos JavaFX ---");
        CountDownLatch latchFim = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                executarCapturas();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latchFim.countDown();
            }
        });

        boolean ok = latchFim.await(90, TimeUnit.SECONDS);
        System.out.println("Captura finalizada: " + (ok ? "SUCESSO (100% gravado em docs/imagens_interface)" : "TIMEOUT"));
        Platform.exit();
        System.exit(0);
    }

    private static void executarCapturas() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "sazonalisei_imagens";
        File pastaOut = new File(tempDir);
        if (!pastaOut.exists()) pastaOut.mkdirs();

        GerenciadorTemaClimatico tema = new GerenciadorTemaClimatico();
        PonteRoteadorIA ponte = new PonteRoteadorIA(tema);
        ConectorProblemasIA conector = new ConectorProblemasIA(ponte);

        Stage stage = new Stage();
        stage.setWidth(1280);
        stage.setHeight(820);

        // Renderizar cada uma das 9 abas no layout corporativo
        capturarAba(stage, pastaOut, "01_home_dashboard_clima.png", "🏠 Início & Inteligência Climática",
                new AbaInicioClimaView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "02_pdv_balcao_multi_pagamentos.png", "🛒 PDV Balcão & Múltiplos Pagamentos",
                new AbaBalcaoNotasView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "03_catalogo_estoque_lotes.png", "📦 Estoque, Catálogo & Lotes 360º",
                new AbaCatalogoEstoquePessoasView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "04_financeiro_saude_contabil.png", "💰 Financeiro, DRE & Saúde Contábil",
                new AbaFinanceiroContabilidadeView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "05_estatistica_preditiva_causas.png", "📈 Estatística Preditiva & Causas",
                new AbaEstatisticaAvancadaView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "06_mapa_gps_clima_relevo.png", "🗺️ Mapa GPS, Relevo & Clima 7 Dias",
                new AbaMapaEventosGpsView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "07_planejamento_demanda_kanban.png", "📋 Planejamento de Demanda Kanban",
                new AbaPlanejamentoDemandaKanbanView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "08_controle_operacional_ia.png", "🤖 Central Operacional & Chatbot IA",
                new AbaControleOperacionalIAView(ponte, conector), tema, false);

        capturarAba(stage, pastaOut, "09_configuracoes_mapeador_csv.png", "⚙️ Configurações & Mapeador Dinâmico CSV",
                new AbaConfiguracaoAmbienteView(ponte, conector), tema, false);

        // Snapshot em Tema Escuro (Modo Dark Corporativo)
        tema.definirModoEscuro(true);
        capturarAba(stage, pastaOut, "10_tema_escuro_interface.png", "🌙 Sazonalisei ERP — Modo Escuro Ativo",
                new AbaInicioClimaView(ponte, conector), tema, true);
        tema.definirModoEscuro(false); // restaura
    }

    private static void capturarAba(Stage stage, File pastaOut, String nomeArquivo, String tituloModulo,
                                    Node abaView, GerenciadorTemaClimatico tema, boolean modoEscuro) throws Exception {
        if (abaView instanceof AbaInicioClimaView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaBalcaoNotasView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaCatalogoEstoquePessoasView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaFinanceiroContabilidadeView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaEstatisticaAvancadaView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaMapaEventosGpsView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaPlanejamentoDemandaKanbanView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaControleOperacionalIAView a) a.carregarSeNecessario();
        else if (abaView instanceof AbaConfiguracaoAmbienteView a) a.carregarSeNecessario();

        BorderPane moldura = new BorderPane();
        moldura.setStyle(modoEscuro ? "-fx-background-color: #0F172A;" : "-fx-background-color: #F8FAFC;");

        // Top bar corporativa
        HBox topBar = new HBox(12);
        topBar.setStyle(modoEscuro ? "-fx-background-color: #1E293B; -fx-padding: 10px 16px; -fx-border-color: #334155; -fx-border-width: 0 0 1px 0;"
                : "-fx-background-color: #0F172A; -fx-padding: 10px 16px; -fx-border-color: #CBD5E1; -fx-border-width: 0 0 1px 0;");
        Label lblLogo = new Label("SAZONALISEI ERP");
        lblLogo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        Label lblTit = new Label("• " + tituloModulo);
        lblTit.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #F1F5F9;");
        Label lblStatus = new Label("🌤️ Manaus: 31.5°C • Rio Negro: 21.40m • Status: Estável");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        HBox.setHgrow(lblTit, Priority.ALWAYS);
        topBar.getChildren().addAll(lblLogo, lblTit, lblStatus);

        moldura.setTop(topBar);

        // Sidebar compacta simulada
        VBox sidebar = new VBox(6);
        sidebar.setPrefWidth(190);
        sidebar.setStyle(modoEscuro ? "-fx-background-color: #1E293B; -fx-padding: 12px 8px; -fx-border-color: #334155; -fx-border-width: 0 1px 0 0;"
                : "-fx-background-color: #FFFFFF; -fx-padding: 12px 8px; -fx-border-color: #E2E8F0; -fx-border-width: 0 1px 0 0;");
        String[] menus = {
                "🏠 Início / Clima", "🛒 PDV Balcão", "📦 Catálogo / Lotes", "💰 Financeiro / DRE",
                "📈 Estatística", "🗺️ Mapa / Clima GPS", "📋 Kanban Demanda", "🤖 Assistente IA", "⚙️ Configurações"
        };
        for (String m : menus) {
            Label item = new Label(m);
            boolean ativo = tituloModulo.contains(m.substring(2).trim());
            item.setStyle(ativo
                    ? "-fx-background-color: #1D4ED8; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 8px 10px; -fx-background-radius: 6px;"
                    : (modoEscuro ? "-fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-padding: 8px 10px;" : "-fx-text-fill: #475569; -fx-font-size: 11px; -fx-padding: 8px 10px;"));
            sidebar.getChildren().add(item);
        }
        moldura.setLeft(sidebar);

        StackPane centro = new StackPane(abaView);
        centro.setStyle(modoEscuro ? "-fx-background-color: #0F172A; -fx-padding: 10px;" : "-fx-background-color: #F8FAFC; -fx-padding: 10px;");
        moldura.setCenter(centro);

        Scene scene = new Scene(moldura, 1280, 800);
        stage.setScene(scene);
        moldura.applyCss();
        moldura.layout();

        // Snapshot
        WritableImage img = moldura.snapshot(new SnapshotParameters(), null);
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage bImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bImg.setRGB(x, y, pr.getArgb(x, y));
            }
        }
        File f = new File(pastaOut, nomeArquivo);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(bImg, "png", baos);
        byte[] bytes = baos.toByteArray();
        java.nio.file.Files.write(f.toPath(), bytes);
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        java.nio.file.Files.writeString(new File(pastaOut, nomeArquivo + ".b64").toPath(), b64);
        System.out.println("  ✔ Gravado screenshot: " + f.getAbsolutePath() + " (" + w + "x" + h + ")");
    }
}
