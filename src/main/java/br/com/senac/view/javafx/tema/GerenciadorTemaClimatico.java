package br.com.senac.view.javafx.tema;

import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.rios.NivelRioDiario;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gerenciador de Tema Corporativo Dinâmico guiado pelo Clima Amazônico e Regime dos Rios.
 * Mantém identidade empresarial sóbria, limpa e compacta (fonte padrão 13px, piso 11px).
 */
public class GerenciadorTemaClimatico {

    public enum EstadoClimaticoAmazonico {
        SOL_EQUATORIAL(
                "☀️", "Sol Equatorial (Verão Amazônico)",
                "#F8FAFC", "#FFFFFF", "#FFFBEB", "#F59E0B", "#B45309", "#047857", "#0F172A", "#475569"),
        TORO_CHUVA(
                "🌧️", "Toró / Chuva Amazônica (Inverno)",
                "#F1F5F9", "#FFFFFF", "#EFF6FF", "#93C5FD", "#1D4ED8", "#0F766E", "#0F172A", "#475569"),
        BRISA_FLORESTA(
                "⛅", "Brisa da Floresta (Clima Ameno)",
                "#F4F7F5", "#FFFFFF", "#ECFDF5", "#CBD5E1", "#047857", "#1D4ED8", "#0F172A", "#475569"),
        FRIAGEM_AMAZONICA(
                "🌬️", "Friagem Amazônica",
                "#F5F3FF", "#FFFFFF", "#EDE9FE", "#C4B5FD", "#5B21B6", "#1D4ED8", "#0F172A", "#475569");

        private final String icone;
        private final String titulo;
        private final String bgApp;
        private final String bgCard;
        private final String bgSoft;
        private final String borderCard;
        private final String accentMain;
        private final String accentSec;
        private final String textPrimary;
        private final String textSecondary;

        EstadoClimaticoAmazonico(String icone, String titulo,
                                 String bgApp, String bgCard, String bgSoft,
                                 String borderCard, String accentMain, String accentSec,
                                 String textPrimary, String textSecondary) {
            this.icone = icone;
            this.titulo = titulo;
            this.bgApp = bgApp;
            this.bgCard = bgCard;
            this.bgSoft = bgSoft;
            this.borderCard = borderCard;
            this.accentMain = accentMain;
            this.accentSec = accentSec;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
        }

        public String getIcone() { return icone; }
        public String getTitulo() { return titulo; }
        public String getBgApp() { return bgApp; }
        public String getBgCard() { return bgCard; }
        public String getBgSoft() { return bgSoft; }
        public String getBorderCard() { return borderCard; }
        public String getAccentMain() { return accentMain; }
        public String getAccentSec() { return accentSec; }
        public String getTextPrimary() { return textPrimary; }
        public String getTextSecondary() { return textSecondary; }
    }

    public enum RegimeHidrologicoRio {
        CHEIA("🌊", "Rio em Cheia", "#DBEAFE", "#93C5FD", "#1E40AF"),
        NORMAL("🛶", "Nível Normal", "#E0F2FE", "#7DD3FC", "#0369A1"),
        VAZANTE_SECA("🍂", "Rio em Vazante / Seca", "#FEF3C7", "#FCD34D", "#92400E");

        private final String icone;
        private final String rotulo;
        private final String bgBadge;
        private final String borderBadge;
        private final String textBadge;

        RegimeHidrologicoRio(String icone, String rotulo, String bgBadge, String borderBadge, String textBadge) {
            this.icone = icone;
            this.rotulo = rotulo;
            this.bgBadge = bgBadge;
            this.borderBadge = borderBadge;
            this.textBadge = textBadge;
        }

        public String getIcone() { return icone; }
        public String getRotulo() { return rotulo; }
        public String getBgBadge() { return bgBadge; }
        public String getBorderBadge() { return borderBadge; }
        public String getTextBadge() { return textBadge; }

        public String getOrientacaoOperacional() {
            return switch (this) {
                case CHEIA -> "Navegação fluvial facilitada para regiões rurais/ribeirinhas; redobrar atenção com armazenagem contra umidade.";
                case VAZANTE_SECA -> "Risco de frete fluvial mais lento e encarecimento logístico; antecipar compras de itens essenciais com fornecedores.";
                case NORMAL -> "Fluxo fluvial regular para entregas e reposições de catálogo.";
            };
        }
    }

    public record SnapshotTemaClimatico(
            EstadoClimaticoAmazonico estado,
            RegimeHidrologicoRio regimeRio,
            ClimaDiario clima,
            double nivelRioMetros,
            String cidade,
            String uf,
            String bacia,
            boolean modoDinamico,
            int fonteBasePx,
            boolean modoEscuro,
            String corBgPersonalizadaHex
    ) {
        public SnapshotTemaClimatico(
                EstadoClimaticoAmazonico estado,
                RegimeHidrologicoRio regimeRio,
                ClimaDiario clima,
                double nivelRioMetros,
                String cidade,
                String uf,
                String bacia,
                boolean modoDinamico,
                int fonteBasePx) {
            this(estado, regimeRio, clima, nivelRioMetros, cidade, uf, bacia,
                    modoDinamico, fonteBasePx, false, null);
        }

        public String resolverBgApp() {
            if (corBgPersonalizadaHex != null && !corBgPersonalizadaHex.isBlank()) {
                return corBgPersonalizadaHex;
            }
            return modoEscuro ? "#0B1120" : estado.getBgApp();
        }

        public String resolverBgCard() {
            return modoEscuro ? "#1E293B" : estado.getBgCard();
        }

        public String resolverBgSoft() {
            return modoEscuro ? "#172033" : estado.getBgSoft();
        }

        public String resolverBorderCard() {
            return modoEscuro ? "#334155" : estado.getBorderCard();
        }

        public String resolverTextPrimary() {
            if (corBgPersonalizadaHex != null && isHexEscuro(corBgPersonalizadaHex)) {
                return "#F8FAFC";
            }
            return modoEscuro ? "#F8FAFC" : estado.getTextPrimary();
        }

        public String resolverTextSecondary() {
            if (corBgPersonalizadaHex != null && isHexEscuro(corBgPersonalizadaHex)) {
                return "#CBD5E1";
            }
            return modoEscuro ? "#CBD5E1" : estado.getTextSecondary();
        }

        private static boolean isHexEscuro(String hex) {
            try {
                String h = hex.replace("#", "").trim();
                if (h.length() != 6) return false;
                int r = Integer.parseInt(h.substring(0, 2), 16);
                int g = Integer.parseInt(h.substring(2, 4), 16);
                int b = Integer.parseInt(h.substring(4, 6), 16);
                double lum = (0.299 * r + 0.587 * g + 0.114 * b);
                return lum < 128.0;
            } catch (Exception e) {
                return false;
            }
        }

        public String descreverClimaCurto() {
            double temp = clima != null ? clima.temperaturaMedia() : 28.0;
            double chuva = clima != null ? clima.precipitacaoMm() : 0.0;
            String local = (cidade != null && !cidade.isBlank()) ? cidade : "Manaus";
            if (chuva >= 5.0) {
                return String.format(FormatadorSeguroUI.LOCALE_BR,
                        "%s %.1f°C • Chuva (%.0f mm) • %s",
                        estado.getIcone(), temp, chuva, local);
            }
            return String.format(FormatadorSeguroUI.LOCALE_BR,
                    "%s %.1f°C • %s • %s",
                    estado.getIcone(), temp, estado.getTitulo(), local);
        }

        public String descreverRioCurto() {
            return String.format(FormatadorSeguroUI.LOCALE_BR,
                    "%s %s (%.1f m • %s)",
                    regimeRio.getIcone(), regimeRio.getRotulo(), nivelRioMetros,
                    bacia != null && !bacia.isBlank() ? bacia : "Rio Negro");
        }
    }

    private final List<Consumer<SnapshotTemaClimatico>> ouvintes = new CopyOnWriteArrayList<>();
    private volatile SnapshotTemaClimatico snapshotAtual;
    private volatile boolean modoDinamico = true;
    private volatile EstadoClimaticoAmazonico estadoFixo = EstadoClimaticoAmazonico.BRISA_FLORESTA;
    private volatile boolean modoEscuro = false;
    private volatile String corBgPersonalizadaHex = null;

    public GerenciadorTemaClimatico() {
        ClimaDiario padrao = new ClimaDiario(LocalDate.now(), 32.0, 24.5, 29.5, 0.0, 9.0);
        this.snapshotAtual = new SnapshotTemaClimatico(
                EstadoClimaticoAmazonico.SOL_EQUATORIAL,
                RegimeHidrologicoRio.NORMAL,
                padrao,
                23.4,
                "Manaus",
                "AM",
                "Rio Negro",
                true,
                FormatadorSeguroUI.getFonteBasePx(),
                false,
                null
        );
    }

    public SnapshotTemaClimatico getSnapshotAtual() {
        return snapshotAtual;
    }

    public boolean isModoEscuro() {
        return modoEscuro;
    }

    public String getCorBgPersonalizadaHex() {
        return corBgPersonalizadaHex;
    }

    public void registrarOuvinte(Consumer<SnapshotTemaClimatico> ouvinte) {
        if (ouvinte != null) ouvintes.add(ouvinte);
    }

    public void removerOuvinte(Consumer<SnapshotTemaClimatico> ouvinte) {
        ouvintes.remove(ouvinte);
    }

    public synchronized void atualizarDadosClimaticos(
            ClimaDiario clima,
            NivelRioDiario nivelRio,
            String cidade,
            String uf,
            String bacia) {
        ClimaDiario c = clima != null ? clima : snapshotAtual.clima();
        EstadoClimaticoAmazonico novoEstado = modoDinamico
                ? classificarClima(c.temperaturaMedia(), c.precipitacaoMm())
                : estadoFixo;

        double cotaMetros = calcularCotaMetros(nivelRio);
        RegimeHidrologicoRio novoRegime = classificarRegimeRio(cotaMetros);

        this.snapshotAtual = new SnapshotTemaClimatico(
                novoEstado, novoRegime, c, cotaMetros,
                (cidade != null && !cidade.isBlank()) ? cidade : snapshotAtual.cidade(),
                (uf != null && !uf.isBlank()) ? uf : snapshotAtual.uf(),
                (bacia != null && !bacia.isBlank()) ? bacia : snapshotAtual.bacia(),
                modoDinamico,
                FormatadorSeguroUI.getFonteBasePx(),
                modoEscuro,
                corBgPersonalizadaHex
        );
        notificar();
    }

    public synchronized void definirModoDinamico(boolean dinamico, EstadoClimaticoAmazonico fixoOpcional) {
        this.modoDinamico = dinamico;
        if (fixoOpcional != null) this.estadoFixo = fixoOpcional;
        EstadoClimaticoAmazonico novoEstado = modoDinamico
                ? classificarClima(snapshotAtual.clima().temperaturaMedia(), snapshotAtual.clima().precipitacaoMm())
                : estadoFixo;
        this.snapshotAtual = new SnapshotTemaClimatico(
                novoEstado, snapshotAtual.regimeRio(), snapshotAtual.clima(),
                snapshotAtual.nivelRioMetros(), snapshotAtual.cidade(), snapshotAtual.uf(),
                snapshotAtual.bacia(), modoDinamico, FormatadorSeguroUI.getFonteBasePx(),
                modoEscuro, corBgPersonalizadaHex
        );
        notificar();
    }

    public synchronized void definirModoEscuro(boolean escuro) {
        this.modoEscuro = escuro;
        this.snapshotAtual = new SnapshotTemaClimatico(
                snapshotAtual.estado(), snapshotAtual.regimeRio(), snapshotAtual.clima(),
                snapshotAtual.nivelRioMetros(), snapshotAtual.cidade(), snapshotAtual.uf(),
                snapshotAtual.bacia(), snapshotAtual.modoDinamico(), FormatadorSeguroUI.getFonteBasePx(),
                modoEscuro, corBgPersonalizadaHex
        );
        notificar();
    }

    public synchronized void definirCorBackgroundPersonalizada(String hexOpcional) {
        this.corBgPersonalizadaHex = (hexOpcional != null && !hexOpcional.isBlank()) ? hexOpcional.trim() : null;
        this.snapshotAtual = new SnapshotTemaClimatico(
                snapshotAtual.estado(), snapshotAtual.regimeRio(), snapshotAtual.clima(),
                snapshotAtual.nivelRioMetros(), snapshotAtual.cidade(), snapshotAtual.uf(),
                snapshotAtual.bacia(), snapshotAtual.modoDinamico(), FormatadorSeguroUI.getFonteBasePx(),
                modoEscuro, corBgPersonalizadaHex
        );
        notificar();
    }

    public synchronized void definirTamanhoFonteBase(int novoTamanhoPx) {
        FormatadorSeguroUI.setFonteBasePx(novoTamanhoPx);
        this.snapshotAtual = new SnapshotTemaClimatico(
                snapshotAtual.estado(), snapshotAtual.regimeRio(), snapshotAtual.clima(),
                snapshotAtual.nivelRioMetros(), snapshotAtual.cidade(), snapshotAtual.uf(),
                snapshotAtual.bacia(), snapshotAtual.modoDinamico(), FormatadorSeguroUI.getFonteBasePx(),
                modoEscuro, corBgPersonalizadaHex
        );
        notificar();
    }

    public static EstadoClimaticoAmazonico classificarClima(double tempMedia, double precipMm) {
        if (precipMm >= 5.0) return EstadoClimaticoAmazonico.TORO_CHUVA;
        if (tempMedia < 24.0) return EstadoClimaticoAmazonico.FRIAGEM_AMAZONICA;
        if (tempMedia >= 29.0 && precipMm < 2.0) return EstadoClimaticoAmazonico.SOL_EQUATORIAL;
        return EstadoClimaticoAmazonico.BRISA_FLORESTA;
    }

    public static RegimeHidrologicoRio classificarRegimeRio(double cotaMetros) {
        if (cotaMetros >= 27.0) return RegimeHidrologicoRio.CHEIA;
        if (cotaMetros <= 19.0) return RegimeHidrologicoRio.VAZANTE_SECA;
        return RegimeHidrologicoRio.NORMAL;
    }

    private static double calcularCotaMetros(NivelRioDiario nivel) {
        if (nivel == null || nivel.vazaoM3s() <= 0.0) return 23.4;
        double v = nivel.vazaoM3s();
        if (v >= 10.0 && v <= 35.0) return v;
        double normalizada = 16.0 + Math.min(14.0, (Math.log10(Math.max(100.0, v)) - 2.0) * 4.5);
        return Math.round(normalizada * 10.0) / 10.0;
    }

    private void notificar() {
        SnapshotTemaClimatico snap = this.snapshotAtual;
        for (Consumer<SnapshotTemaClimatico> c : ouvintes) {
            try {
                c.accept(snap);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * CSS global corporativo compacto para JavaFX (TableView, TabPane, Inputs e Cards limpos).
     */
    public String gerarCssGlobalDataUri() {
        EstadoClimaticoAmazonico e = snapshotAtual.estado();
        boolean dark = snapshotAtual.modoEscuro();
        String bgApp = snapshotAtual.resolverBgApp();
        String bgCard = snapshotAtual.resolverBgCard();
        String txtPrimary = snapshotAtual.resolverTextPrimary();
        String txtSecondary = snapshotAtual.resolverTextSecondary();
        String tabBg = dark ? "#0F172A" : "#E2E8F0";
        String tabSelected = dark ? "#1E293B" : "#FFFFFF";
        String borderCol = snapshotAtual.resolverBorderCard();
        String tblHeaderBg = dark ? "#0F172A" : "#F1F5F9";
        String tblHeaderTxt = dark ? "#E2E8F0" : "#1E293B";
        String tblRowEven = dark ? "#1E293B" : "#FFFFFF";
        String tblRowOdd = dark ? "#172033" : "#F8FAFC";
        String accent = dark ? "#60A5FA" : e.getAccentMain();

        int base = FormatadorSeguroUI.getFonteBasePx();
        int menor = Math.max(FormatadorSeguroUI.PISO_FONTE_PX, base - 1);

        String css = String.format(Locale.US, """
                .root {
                    -fx-font-family: "Segoe UI", "System";
                    -fx-font-size: %dpx;
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                }
                .label {
                    -fx-font-size: %dpx;
                    -fx-text-fill: %s;
                }
                .button, .toggle-button {
                    -fx-font-size: %dpx;
                    -fx-background-radius: 6px;
                    -fx-border-radius: 6px;
                    -fx-border-width: 1px;
                }
                .text-field, .text-area, .combo-box, .date-picker {
                    -fx-font-size: %dpx;
                    -fx-background-radius: 6px;
                    -fx-border-radius: 6px;
                }
                .tab-pane .tab-header-area .tab-header-background {
                    -fx-background-color: %s;
                    -fx-background-radius: 6px;
                }
                .tab-pane .tab {
                    -fx-background-color: transparent;
                    -fx-background-radius: 6px;
                    -fx-padding: 5px 12px;
                }
                .tab-pane .tab:selected {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-border-width: 1px;
                    -fx-border-radius: 6px;
                }
                .tab-pane .tab .tab-label {
                    -fx-font-size: %dpx;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                }
                .tab-pane .tab:selected .tab-label {
                    -fx-text-fill: %s;
                }
                .table-view {
                    -fx-background-color: %s;
                    -fx-border-color: %s;
                    -fx-border-width: 1px;
                    -fx-background-radius: 6px;
                    -fx-border-radius: 6px;
                    -fx-font-size: %dpx;
                }
                .table-view .column-header-background {
                    -fx-background-color: %s;
                }
                .table-view .column-header .label {
                    -fx-font-size: %dpx;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                }
                .table-row-cell {
                    -fx-cell-size: 32px;
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                }
                .table-row-cell:odd {
                    -fx-background-color: %s;
                }
                .table-row-cell:selected {
                    -fx-background-color: #1D4ED8;
                    -fx-text-fill: #FFFFFF;
                }
                .table-row-cell .text {
                    -fx-fill: %s;
                }
                .tooltip {
                    -fx-font-size: %dpx;
                    -fx-background-color: #0F172A;
                    -fx-text-fill: #FFFFFF;
                    -fx-background-radius: 6px;
                    -fx-padding: 6px 10px;
                }
                .scroll-pane {
                    -fx-background-color: transparent;
                    -fx-background: transparent;
                }
                .scroll-pane > .viewport {
                    -fx-background-color: transparent;
                }
                .scroll-bar .thumb {
                    -fx-background-color: #94A3B8;
                    -fx-background-radius: 6px;
                }
                .check-box, .radio-button {
                    -fx-font-size: %dpx;
                    -fx-text-fill: %s;
                }
                """,
                base, bgApp, txtPrimary,
                base, txtPrimary,
                menor,
                menor,
                tabBg,
                tabSelected, borderCol,
                menor, txtSecondary,
                accent,
                bgCard, borderCol, menor,
                tblHeaderBg,
                menor, tblHeaderTxt,
                tblRowEven, txtPrimary,
                tblRowOdd,
                txtPrimary,
                menor,
                menor, txtPrimary
        );
        return "data:text/css," + URLEncoder.encode(css, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
