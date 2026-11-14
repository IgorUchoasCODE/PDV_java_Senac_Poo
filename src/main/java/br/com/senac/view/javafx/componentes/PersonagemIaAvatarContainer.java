package br.com.senac.view.javafx.componentes;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Personagem animado que representa visualmente a IA que está respondendo no momento
 * (Gemini, Groq, OpenRouter, Cerebras, Mistral, Ollama/OpenAI) e evolui/muda conforme
 * as trocas da conversa e o estado atual (Ocioso, Pensando, Respondendo, Ouvindo).
 */
public class PersonagemIaAvatarContainer extends StackPane {

    public enum Estado {
        OCIOSO,
        PENSANDO,
        RESPONDENDO,
        OUVINDO
    }

    private final Canvas canvas;
    private String provedorAtual = "gemini";
    private Estado estadoAtual = Estado.OCIOSO;
    private int numeroTrocas = 0;
    private double tempo = 0;

    public PersonagemIaAvatarContainer(double tamanho) {
        this.canvas = new Canvas(tamanho, tamanho);
        getChildren().add(canvas);
        setPrefSize(tamanho, tamanho);
        setMaxSize(tamanho, tamanho);
        iniciarAnimacao();
    }

    public void atualizarContexto(String provedor, Estado estado, int trocas) {
        if (provedor != null && !provedor.isBlank()) {
            this.provedorAtual = provedor.toLowerCase(Locale.ROOT);
        }
        if (estado != null) {
            this.estadoAtual = estado;
        }
        this.numeroTrocas = Math.max(0, trocas);
    }

    public String getProvedorAtual() {
        return provedorAtual;
    }

    private void iniciarAnimacao() {
        AnimationTimer timer = new AnimationTimer() {
            private long ultimo = 0;

            @Override
            public void handle(long now) {
                if (ultimo == 0) {
                    ultimo = now;
                    return;
                }
                double dt = Math.min((now - ultimo) / 1_000_000_000.0, 0.05);
                ultimo = now;
                tempo += dt;
                desenhar();
            }
        };
        timer.start();
    }

    private void desenhar() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        Color corProvedor = corPorProvedor(provedorAtual);
        double cx = w / 2.0;
        double floatY = Math.sin(tempo * (estadoAtual == Estado.PENSANDO ? 4.5 : 2.0)) * (w * 0.02);
        double cy = h * 0.43 + floatY;
        double rCorpo = w * 0.29;

        // Anéis de evolução conforme o passar das trocas na conversa
        int aneis = Math.min(3, (numeroTrocas / 2) + 1);
        for (int i = 0; i < aneis; i++) {
            double rAnel = rCorpo + 4 + i * 3.5;
            double alpha = (estadoAtual == Estado.PENSANDO ? 0.55 : 0.25) * (1.0 - i * 0.22);
            gc.setStroke(Color.color(corProvedor.getRed(), corProvedor.getGreen(), corProvedor.getBlue(), alpha));
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - rAnel, cy - rAnel, rAnel * 2, rAnel * 2);
        }

        // Cabeça / corpo do personagem
        gc.setFill(Color.web("#1E293B"));
        gc.fillOval(cx - rCorpo, cy - rCorpo, rCorpo * 2, rCorpo * 2);
        gc.setStroke(corProvedor);
        gc.setLineWidth(2.4);
        gc.strokeOval(cx - rCorpo, cy - rCorpo, rCorpo * 2, rCorpo * 2);

        // Antena / crista indicadora de provedor
        double topoY = cy - rCorpo;
        gc.setStroke(corProvedor);
        gc.setLineWidth(2.0);
        gc.strokeLine(cx, topoY, cx, topoY - rCorpo * 0.24);
        gc.setFill(corProvedor);
        gc.fillOval(cx - 3.5, topoY - rCorpo * 0.24 - 3.5, 7, 7);

        // Visor facial
        double vw = rCorpo * 1.25;
        double vh = rCorpo * 0.68;
        gc.setFill(Color.web("#0F172A"));
        gc.fillRoundRect(cx - vw / 2, cy - vh * 0.55, vw, vh, 10, 10);

        // Olhos expressivos (piscam ou animam conforme estado)
        boolean piscando = estadoAtual == Estado.OCIOSO && (tempo % 4.2) > 4.05;
        double olhoOffset = vw * 0.23;
        double olhoY = cy - vh * 0.22;
        gc.setFill(estadoAtual == Estado.OUVINDO ? Color.web("#2DD4BF") : corProvedor);

        if (piscando) {
            gc.fillRect(cx - olhoOffset - 4, olhoY, 8, 2);
            gc.fillRect(cx + olhoOffset - 4, olhoY, 8, 2);
        } else if (estadoAtual == Estado.PENSANDO) {
            double shift = Math.sin(tempo * 6.0) * 3.0;
            gc.fillOval(cx - olhoOffset - 3.5 + shift, olhoY - 3.5, 7, 7);
            gc.fillOval(cx + olhoOffset - 3.5 + shift, olhoY - 3.5, 7, 7);
        } else {
            gc.fillOval(cx - olhoOffset - 3.5, olhoY - 3.5, 7, 7);
            gc.fillOval(cx + olhoOffset - 3.5, olhoY - 3.5, 7, 7);
        }

        // Boca / onda de voz
        double bocaY = cy + vh * 0.22;
        if (estadoAtual == Estado.RESPONDENDO || estadoAtual == Estado.OUVINDO) {
            double amp = 2.0 + Math.abs(Math.sin(tempo * 11.0)) * 4.5;
            gc.setFill(corProvedor);
            gc.fillRoundRect(cx - vw * 0.18, bocaY - amp / 2, vw * 0.36, amp, 4, 4);
        } else {
            gc.setStroke(corProvedor);
            gc.setLineWidth(1.8);
            gc.strokeLine(cx - vw * 0.15, bocaY, cx + vw * 0.15, bocaY);
        }

        // Sigla discreta do provedor na base
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, Math.max(8, w * 0.11)));
        gc.setFill(Color.web("#E2E8F0"));
        String sigla = siglaProvedor(provedorAtual);
        gc.fillText(sigla, cx - (sigla.length() * w * 0.032), cy + rCorpo + 11);
    }

    private Color corPorProvedor(String prov) {
        if (prov.contains("groq")) return Color.web("#F59E0B");
        if (prov.contains("openrouter")) return Color.web("#818CF8");
        if (prov.contains("cerebras")) return Color.web("#10B981");
        if (prov.contains("mistral")) return Color.web("#FB7185");
        if (prov.contains("openai") || prov.contains("ollama")) return Color.web("#2DD4BF");
        return Color.web("#38BDF8"); // Gemini padrão
    }

    private String siglaProvedor(String prov) {
        if (prov.contains("groq")) return "GROQ";
        if (prov.contains("openrouter")) return "ROUTER";
        if (prov.contains("cerebras")) return "CEREB";
        if (prov.contains("mistral")) return "MISTRAL";
        if (prov.contains("openai") || prov.contains("ollama")) return "LOCAL";
        return "GEMINI";
    }
}
