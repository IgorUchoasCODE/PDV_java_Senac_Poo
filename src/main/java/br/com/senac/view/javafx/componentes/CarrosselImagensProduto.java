package br.com.senac.view.javafx.componentes;

import br.com.senac.controller.RotaSistema;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoImagem;
import br.com.senac.view.javafx.ponte.PonteRoteadorIA;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrossel corporativo de imagens do produto com carregamento estritamente sob demanda (lazy)
 * e cache em memória para não travar a navegação no PDV, Estoque, Cadastro e Estatística.
 */
public class CarrosselImagensProduto extends VBox {

    private static final Map<Long, List<ProdutoImagem>> CACHE_IMAGENS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<ProdutoImagem>> eldest) {
                    return size() > 60;
                }
            });

    private final PonteRoteadorIA ponte;
    private final boolean permitirEdicao;
    private final double larguraImagem;
    private final double alturaImagem;

    private final Label lblTitulo = FormatadorSeguroUI.criarLabel("📸 Galeria do Produto", 12, true, "#0F172A");
    private final Label lblContador = FormatadorSeguroUI.criarBadgeCartoon("0 / 0", "#F1F5F9", "#CBD5E1", "#475569");
    private final ImageView imageView = new ImageView();
    private final Label lblPlaceholder = FormatadorSeguroUI.criarLabel("Selecione um produto para visualizar fotos sob demanda.", 11, false, "#64748B");
    private final Label lblLegendaFoto = FormatadorSeguroUI.criarLabel("", 11, false, "#475569");

    private final Button btnAnterior = FormatadorSeguroUI.criarBotaoCartoon("◀", "#FFFFFF", "#CBD5E1", "#0F172A");
    private final Button btnProximo = FormatadorSeguroUI.criarBotaoCartoon("▶", "#FFFFFF", "#CBD5E1", "#0F172A");
    private final Button btnAmpliar = FormatadorSeguroUI.criarBotaoCartoon("🔍 Ampliar", "#EFF6FF", "#93C5FD", "#1E40AF");

    private final CheckBox chkPrincipalUpload = new CheckBox("Principal");
    private final Button btnAnexar = FormatadorSeguroUI.criarBotaoCartoon("+ Anexar Foto", "#047857", "#065F46", "#FFFFFF");
    private final Button btnSalvarFoto = FormatadorSeguroUI.criarBotaoCartoon("💾 Salvar Foto(s)", "#0284C7", "#0369A1", "#FFFFFF");
    private final Button btnDefinirPrincipal = FormatadorSeguroUI.criarBotaoCartoon("★ Principal", "#EFF6FF", "#93C5FD", "#1E40AF");
    private final Button btnRemover = FormatadorSeguroUI.criarBotaoCartoon("🗑️ Remover", "#FEE2E2", "#FCA5A5", "#991B1B");
    private final Label lblStatusFoto = FormatadorSeguroUI.criarLabel("", 11, true, "#047857");

    private record ImagemPendente(File arquivo, boolean principal) {}

    private Long idProdutoAtual;
    private String nomeProdutoAtual;
    private List<ProdutoImagem> imagensAtuais = new ArrayList<>();
    private final List<ImagemPendente> imagensPendentes = new ArrayList<>();
    private int indiceAtual = 0;

    public CarrosselImagensProduto(PonteRoteadorIA ponte, boolean permitirEdicao, double larguraImagem, double alturaImagem) {
        this.ponte = ponte;
        this.permitirEdicao = permitirEdicao;
        this.larguraImagem = Math.max(120, larguraImagem);
        this.alturaImagem = Math.max(85, alturaImagem);

        setSpacing(6);
        setPadding(new Insets(8, 10, 8, 10));
        setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#CBD5E1"));

        HBox.setHgrow(lblTitulo, Priority.ALWAYS);
        HBox topo = new HBox(8, lblTitulo, lblContador);
        topo.setAlignment(Pos.CENTER_LEFT);

        imageView.setFitWidth(this.larguraImagem);
        imageView.setFitHeight(this.alturaImagem);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setStyle("-fx-cursor: hand;");
        imageView.setOnMouseClicked(e -> abrirLightbox());

        StackPane visor = new StackPane(lblPlaceholder, imageView);
        visor.setMinHeight(this.alturaImagem + 10);
        visor.setPrefHeight(this.alturaImagem + 14);
        visor.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 1px; "
                + "-fx-background-radius: 6px; -fx-border-radius: 6px;");

        btnAnterior.setOnAction(e -> navegar(-1));
        btnProximo.setOnAction(e -> navegar(1));
        btnAmpliar.setOnAction(e -> abrirLightbox());

        HBox nav = new HBox(6, btnAnterior, lblLegendaFoto, btnProximo, btnAmpliar);
        nav.setAlignment(Pos.CENTER);
        HBox.setHgrow(lblLegendaFoto, Priority.ALWAYS);
        lblLegendaFoto.setAlignment(Pos.CENTER);

        getChildren().addAll(topo, visor, nav);

        if (permitirEdicao) {
            chkPrincipalUpload.setStyle("-fx-font-size: 11px;");
            btnAnexar.setOnAction(e -> anexarNovaImagem());
            btnSalvarFoto.setOnAction(e -> salvarFotosExplicitamente());
            btnDefinirPrincipal.setOnAction(e -> definirFotoAtualComoPrincipal());
            btnRemover.setOnAction(e -> removerFotoAtual());

            HBox barraEdicao = new HBox(6, btnAnexar, chkPrincipalUpload, btnSalvarFoto, btnDefinirPrincipal, btnRemover);
            barraEdicao.setAlignment(Pos.CENTER_LEFT);
            lblStatusFoto.setWrapText(true);
            getChildren().addAll(barraEdicao, lblStatusFoto);
        }

        atualizarEstadoControles();
    }

    public static void invalidarCache(Long idProduto) {
        if (idProduto != null) CACHE_IMAGENS.remove(idProduto);
    }

    public void limpar() {
        this.idProdutoAtual = null;
        this.nomeProdutoAtual = null;
        this.imagensAtuais = new ArrayList<>();
        this.imagensPendentes.clear();
        this.indiceAtual = 0;
        this.lblTitulo.setText("📸 Galeria do Produto");
        this.lblPlaceholder.setText("Selecione um produto para visualizar ou anexe fotos para novo cadastro.");
        this.lblStatusFoto.setText("");
        this.imageView.setImage(null);
        atualizarEstadoControles();
    }

    /**
     * Carrega as imagens deste produto estritamente sob demanda (usando cache em memória se já carregado).
     */
    public void carregarProdutoSobDemanda(Produto produto) {
        if (produto == null || produto.getId() == null) {
            limpar();
            return;
        }
        carregarProdutoSobDemanda(produto.getId(), FormatadorSeguroUI.limparNomeProduto(produto.getNome()), false);
    }

    public void carregarProdutoSobDemanda(Long idProduto, String nomeProduto, boolean forcarRecarga) {
        if (idProduto == null) {
            limpar();
            return;
        }
        if (!forcarRecarga && idProduto.equals(this.idProdutoAtual) && CACHE_IMAGENS.containsKey(idProduto)) {
            this.imagensAtuais = new ArrayList<>(CACHE_IMAGENS.get(idProduto));
            exibirIndiceAtual();
            return;
        }

        this.idProdutoAtual = idProduto;
        this.nomeProdutoAtual = FormatadorSeguroUI.tratarParaExibicao(nomeProduto, "Produto #" + idProduto);
        this.lblTitulo.setText("📸 Fotos: " + this.nomeProdutoAtual);

        List<ProdutoImagem> emCache = !forcarRecarga ? CACHE_IMAGENS.get(idProduto) : null;
        if (emCache != null) {
            this.imagensAtuais = new ArrayList<>(emCache);
            this.indiceAtual = 0;
            exibirIndiceAtual();
            return;
        }

        this.imagensAtuais = new ArrayList<>();
        this.indiceAtual = 0;
        this.imageView.setImage(null);
        this.lblPlaceholder.setText("Carregando fotos sob demanda...");
        this.lblPlaceholder.setVisible(true);
        atualizarEstadoControles();

        final Long idSolicitado = idProduto;
        Thread.ofVirtual().start(() -> {
            List<ProdutoImagem> imgs = ponte != null ? ponte.listarImagensProduto(idSolicitado) : List.of();
            CACHE_IMAGENS.put(idSolicitado, imgs);
            Platform.runLater(() -> {
                if (!idSolicitado.equals(this.idProdutoAtual)) return;
                this.imagensAtuais = new ArrayList<>(imgs);
                this.indiceAtual = 0;
                exibirIndiceAtual();
            });
        });
    }

    private void navegar(int delta) {
        if (imagensAtuais.isEmpty()) return;
        indiceAtual = (indiceAtual + delta + imagensAtuais.size()) % imagensAtuais.size();
        exibirIndiceAtual();
    }

    private void exibirIndiceAtual() {
        int totalPendentes = imagensPendentes.size();
        if (idProdutoAtual == null && totalPendentes > 0) {
            if (indiceAtual < 0 || indiceAtual >= totalPendentes) indiceAtual = 0;
            ImagemPendente pend = imagensPendentes.get(indiceAtual);
            try {
                Image fxImg = new Image(pend.arquivo().toURI().toString(), larguraImagem, alturaImagem, true, true);
                imageView.setImage(!fxImg.isError() ? fxImg : null);
                lblPlaceholder.setVisible(fxImg.isError());
            } catch (Exception ignored) {
                imageView.setImage(null);
                lblPlaceholder.setVisible(true);
            }
            lblLegendaFoto.setText((pend.principal() ? "★ " : "") + "[Na fila p/ salvar] " + pend.arquivo().getName());
            atualizarEstadoControles();
            return;
        }
        if (imagensAtuais.isEmpty()) {
            imageView.setImage(null);
            lblPlaceholder.setText(idProdutoAtual == null
                    ? "Anexe fotos para o novo produto ou selecione um item na tabela."
                    : "Nenhuma imagem cadastrada para este produto.");
            lblPlaceholder.setVisible(true);
            lblLegendaFoto.setText("Sem imagens");
            atualizarEstadoControles();
            return;
        }

        if (indiceAtual < 0 || indiceAtual >= imagensAtuais.size()) indiceAtual = 0;
        ProdutoImagem img = imagensAtuais.get(indiceAtual);
        Image fxImg = null;
        try {
            if (img.getCaminhoArquivo() != null) {
                File f = new File(img.getCaminhoArquivo());
                if (f.exists()) {
                    fxImg = new Image(f.toURI().toString(), larguraImagem, alturaImagem, true, true);
                }
            }
        } catch (Exception ignored) {
        }

        if (fxImg != null && !fxImg.isError()) {
            imageView.setImage(fxImg);
            lblPlaceholder.setVisible(false);
        } else {
            imageView.setImage(null);
            lblPlaceholder.setText("Arquivo de imagem indisponível no disco.");
            lblPlaceholder.setVisible(true);
        }

        String nomeArq = FormatadorSeguroUI.tratarParaExibicao(img.getNomeOriginal(), img.getNomeArquivo());
        lblLegendaFoto.setText((img.isPrincipal() ? "★ Principal • " : "") + nomeArq);
        atualizarEstadoControles();
    }

    private void atualizarEstadoControles() {
        int total = idProdutoAtual == null ? imagensPendentes.size() : imagensAtuais.size();
        lblContador.setText(total > 0 ? (indiceAtual + 1) + " / " + total : "0 / 0");
        boolean temMultiplas = total > 1;
        btnAnterior.setDisable(!temMultiplas);
        btnProximo.setDisable(!temMultiplas);
        btnAmpliar.setDisable(total == 0);
        if (permitirEdicao) {
            btnAnexar.setDisable(ponte == null);
            btnSalvarFoto.setDisable(total == 0 && imagensPendentes.isEmpty());
            btnDefinirPrincipal.setDisable(idProdutoAtual == null || imagensAtuais.isEmpty()
                    || (indiceAtual >= 0 && indiceAtual < imagensAtuais.size() && imagensAtuais.get(indiceAtual).isPrincipal()));
            btnRemover.setDisable(total == 0);
        }
    }

    private void abrirLightbox() {
        if (idProdutoAtual == null && !imagensPendentes.isEmpty()) {
            ImagemPendente pend = imagensPendentes.get(Math.min(indiceAtual, imagensPendentes.size() - 1));
            exibirJanelaLightbox(pend.arquivo(), (pend.principal() ? "★ " : "") + pend.arquivo().getName());
            return;
        }
        if (imagensAtuais.isEmpty()) return;
        ProdutoImagem atual = imagensAtuais.get(indiceAtual);
        if (atual.getCaminhoArquivo() == null) return;
        File f = new File(atual.getCaminhoArquivo());
        if (!f.exists()) return;
        exibirJanelaLightbox(f, (atual.isPrincipal() ? "★ " : "")
                + FormatadorSeguroUI.tratarParaExibicao(atual.getNomeOriginal(), atual.getNomeArquivo()));
    }

    private void exibirJanelaLightbox(File f, String legenda) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (getScene() != null && getScene().getWindow() != null) {
            modal.initOwner(getScene().getWindow());
        }
        modal.setTitle("Visualização Ampliada — " + (nomeProdutoAtual != null ? nomeProdutoAtual : "Produto"));

        ImageView fullView = new ImageView(new Image(f.toURI().toString(), 720, 520, true, true));
        fullView.setPreserveRatio(true);
        Button btnFechar = FormatadorSeguroUI.criarBotaoCartoon("Fechar", "#0F172A", "#0F172A", "#FFFFFF");
        btnFechar.setOnAction(e -> modal.close());

        VBox box = new VBox(10,
                FormatadorSeguroUI.criarLabel(legenda, 13, true, "#0F172A"),
                fullView,
                btnFechar);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #FFFFFF;");
        modal.setScene(new Scene(box));
        modal.showAndWait();
    }

    private void anexarNovaImagem() {
        if (ponte == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Selecionar Imagem do Produto");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
        File f = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (f == null) return;

        if (idProdutoAtual == null) {
            boolean principal = chkPrincipalUpload.isSelected() || imagensPendentes.isEmpty();
            imagensPendentes.add(new ImagemPendente(f, principal));
            indiceAtual = imagensPendentes.size() - 1;
            lblStatusFoto.setText("📌 Foto adicionada à fila (" + imagensPendentes.size() + "). Será salva junto ao cadastrar o produto!");
            lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0284C7;");
            exibirIndiceAtual();
            return;
        }

        lblStatusFoto.setText("Salvando foto no banco do produto...");
        lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1D4ED8;");
        ponte.executarRotaAsync(RotaSistema.PRODUTO_IMAGEM_ADICIONAR,
                Map.of("idProduto", idProdutoAtual,
                        "caminhoArquivo", f.getAbsolutePath(),
                        "isPrincipal", chkPrincipalUpload.isSelected() || imagensAtuais.isEmpty()),
                resp -> {
                    invalidarCache(idProdutoAtual);
                    if (resp.sucesso()) {
                        lblStatusFoto.setText("✔ Foto '" + f.getName() + "' salva com sucesso no catálogo!");
                        lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                    } else {
                        lblStatusFoto.setText("⚠️ Erro ao salvar foto: " + FormatadorSeguroUI.traduzirErroAmigavel(resp.mensagem()));
                        lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
                    }
                    carregarProdutoSobDemanda(idProdutoAtual, nomeProdutoAtual, true);
                });
    }

    private void salvarFotosExplicitamente() {
        if (idProdutoAtual == null) {
            lblStatusFoto.setText("📌 Clique em 'Cadastrar Produto' abaixo — " + imagensPendentes.size() + " foto(s) na fila serão gravadas automaticamente.");
            lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0284C7;");
            return;
        }
        if (!imagensPendentes.isEmpty()) {
            salvarImagensPendentesNoProduto(idProdutoAtual);
        } else if (!imagensAtuais.isEmpty()) {
            invalidarCache(idProdutoAtual);
            carregarProdutoSobDemanda(idProdutoAtual, nomeProdutoAtual, true);
            lblStatusFoto.setText("✔ Galeria de fotos (" + imagensAtuais.size() + ") sincronizada e salva no produto #" + idProdutoAtual + ".");
            lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #047857;");
        }
    }

    /**
     * Grava todas as fotos anexadas antes do cadastro assim que o Produto receber um ID persistido.
     */
    public void salvarImagensPendentesNoProduto(Long idNovoProduto) {
        if (idNovoProduto == null || ponte == null || imagensPendentes.isEmpty()) return;
        List<ImagemPendente> copia = new ArrayList<>(imagensPendentes);
        imagensPendentes.clear();
        Thread.ofVirtual().start(() -> {
            for (ImagemPendente p : copia) {
                ponte.getRoteador().executarEndpoint(RotaSistema.PRODUTO_IMAGEM_ADICIONAR,
                        Map.of("idProduto", idNovoProduto,
                                "caminhoArquivo", p.arquivo().getAbsolutePath(),
                                "isPrincipal", p.principal()));
            }
            invalidarCache(idNovoProduto);
            Platform.runLater(() -> {
                lblStatusFoto.setText("✔ " + copia.size() + " foto(s) gravada(s) no produto #" + idNovoProduto + ".");
                lblStatusFoto.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #047857;");
                carregarProdutoSobDemanda(idNovoProduto, nomeProdutoAtual, true);
            });
        });
    }

    private void definirFotoAtualComoPrincipal() {
        if (idProdutoAtual == null || imagensAtuais.isEmpty() || ponte == null) return;
        ProdutoImagem img = imagensAtuais.get(indiceAtual);
        ponte.executarRotaAsync(RotaSistema.PRODUTO_IMAGEM_DEFINIR_PRINCIPAL,
                Map.of("idProduto", idProdutoAtual, "idImagem", img.getId()),
                resp -> {
                    invalidarCache(idProdutoAtual);
                    carregarProdutoSobDemanda(idProdutoAtual, nomeProdutoAtual, true);
                });
    }

    private void removerFotoAtual() {
        if (idProdutoAtual == null && !imagensPendentes.isEmpty()) {
            imagensPendentes.remove(Math.min(indiceAtual, imagensPendentes.size() - 1));
            indiceAtual = Math.max(0, indiceAtual - 1);
            exibirIndiceAtual();
            return;
        }
        if (idProdutoAtual == null || imagensAtuais.isEmpty() || ponte == null) return;
        ProdutoImagem img = imagensAtuais.get(indiceAtual);
        DialogoCorporativoModal.confirmarAcaoManual(
                this,
                "Remover Imagem do Produto",
                "Deseja remover permanentemente esta foto do catálogo?",
                Map.of("Produto", nomeProdutoAtual != null ? nomeProdutoAtual : "#" + idProdutoAtual,
                       "Arquivo", FormatadorSeguroUI.tratarParaExibicao(img.getNomeOriginal(), img.getNomeArquivo())),
                () -> ponte.executarRotaAsync(RotaSistema.PRODUTO_IMAGEM_REMOVER,
                        Map.of("idImagem", img.getId(), "removerArquivoFisico", true),
                        resp -> {
                            invalidarCache(idProdutoAtual);
                            carregarProdutoSobDemanda(idProdutoAtual, nomeProdutoAtual, true);
                        }));
    }
}
