package br.com.senac.repository.modal.produto;

import jakarta.persistence.*;
import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entidade que mapeia as imagens associadas a um Produto.
 * Suporta múltiplos arquivos por produto, formatos modernos (PNG, JPG, JPEG, WEBP, GIF, SVG, BMP),
 * ordenação para galeria/carrossel e marcação de imagem principal para exibição no JavaFX.
 */
@Entity
@Table(
        name = "produto_imagem",
        indexes = {
                @Index(name = "idx_produto_imagem_prod", columnList = "produto_id"),
                @Index(name = "idx_produto_imagem_principal", columnList = "produto_id, is_principal")
        }
)
public class ProdutoImagem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "caminho_arquivo", length = 500, nullable = false)
    private String caminhoArquivo;

    @Column(name = "nome_arquivo", length = 255, nullable = false, unique = true)
    private String nomeArquivo;

    @Column(name = "nome_original", length = 255)
    private String nomeOriginal;

    @Column(name = "formato", length = 15, nullable = false)
    private String formato;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "is_principal", columnDefinition = "TINYINT(1) DEFAULT 0", nullable = false)
    private boolean isPrincipal = false;

    @Column(name = "ordem", nullable = false)
    private int ordem = 0;

    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    public ProdutoImagem() {
    }

    public ProdutoImagem(
            Produto produto,
            String caminhoArquivo,
            String nomeArquivo,
            String nomeOriginal,
            String formato,
            Long tamanhoBytes,
            boolean isPrincipal,
            int ordem) {
        this.produto = produto;
        this.caminhoArquivo = caminhoArquivo;
        this.nomeArquivo = nomeArquivo;
        this.nomeOriginal = nomeOriginal;
        this.formato = formato != null ? formato.toUpperCase() : "PNG";
        this.tamanhoBytes = tamanhoBytes;
        this.isPrincipal = isPrincipal;
        this.ordem = ordem;
        this.dataCadastro = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
    }

    public String getCaminhoArquivo() {
        return caminhoArquivo;
    }

    public void setCaminhoArquivo(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public void setNomeOriginal(String nomeOriginal) {
        this.nomeOriginal = nomeOriginal;
    }

    public String getFormato() {
        return formato;
    }

    public void setFormato(String formato) {
        this.formato = formato != null ? formato.toUpperCase() : null;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public boolean isPrincipal() {
        return isPrincipal;
    }

    public void setPrincipal(boolean principal) {
        isPrincipal = principal;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    /**
     * Retorna o objeto java.io.File físico apontando para o arquivo no disco.
     */
    public File getArquivoFisico() {
        if (caminhoArquivo == null || caminhoArquivo.isBlank()) {
            return null;
        }
        return new File(caminhoArquivo);
    }

    /**
     * Retorna a URI em formato file:// ou caminho seguro compatível diretamente com o javafx.scene.image.Image.
     */
    public String getUriJavaFx() {
        File file = getArquivoFisico();
        if (file != null && file.exists()) {
            return file.toURI().toString();
        }
        return null;
    }

    @Override
    public String toString() {
        return "ProdutoImagem{" +
                "id=" + id +
                ", produtoId=" + (produto != null ? produto.getId() : null) +
                ", nomeArquivo='" + nomeArquivo + '\'' +
                ", formato='" + formato + '\'' +
                ", tamanhoBytes=" + tamanhoBytes +
                ", isPrincipal=" + isPrincipal +
                ", ordem=" + ordem +
                '}';
    }
}
