package br.com.senac.service;

import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.ProdutoImagemDAO;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoImagem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Serviço responsável pela gestão do ciclo de vida das imagens de produtos:
 * - Validação de extensões permitidas (PNG, JPG, JPEG, WEBP, GIF, SVG, BMP)
 * - Geração de nomes de arquivos 100% únicos (UUID + Timestamp)
 * - Armazenamento físico em disco (storage/imagens/produtos/)
 * - Persistência de metadados e URLs/caminhos no banco via ProdutoImagemDAO
 * - Suporte a múltiplos arquivos, ordenação, imagem principal e integração com JavaFX
 */
public class ImagemProdutoService {

    public static final Set<String> FORMATOS_PERMITIDOS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("PNG", "JPG", "JPEG", "WEBP", "GIF", "SVG", "BMP"))
    );

    private static final String DIRETORIO_PADRAO = "storage" + File.separator + "imagens" + File.separator + "produtos";

    private final ProdutoDAO produtoDAO;
    private final ProdutoImagemDAO produtoImagemDAO;
    private final Path diretorioArmazenamento;

    public ImagemProdutoService() {
        this(new ProdutoDAO(), new ProdutoImagemDAO(), Paths.get(DIRETORIO_PADRAO));
    }

    public ImagemProdutoService(ProdutoDAO produtoDAO, ProdutoImagemDAO produtoImagemDAO, Path diretorioArmazenamento) {
        this.produtoDAO = produtoDAO;
        this.produtoImagemDAO = produtoImagemDAO;
        this.diretorioArmazenamento = diretorioArmazenamento;
        inicializarDiretorio();
    }

    private void inicializarDiretorio() {
        try {
            if (!Files.exists(diretorioArmazenamento)) {
                Files.createDirectories(diretorioArmazenamento);
            }
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível inicializar o diretório de imagens: " + diretorioArmazenamento, e);
        }
    }

    /**
     * Valida e extrai a extensão do arquivo garantindo que está na lista de formatos permitidos.
     */
    public String extrairEValidarFormato(String nomeArquivo) {
        if (nomeArquivo == null || !nomeArquivo.contains(".")) {
            throw new IllegalArgumentException("Nome de arquivo inválido ou sem extensão: " + nomeArquivo);
        }
        String extensao = nomeArquivo.substring(nomeArquivo.lastIndexOf('.') + 1).trim().toUpperCase();
        if (!FORMATOS_PERMITIDOS.contains(extensao)) {
            throw new IllegalArgumentException(
                    "Formato '" + extensao + "' não suportado. Formatos aceitos: " + FORMATOS_PERMITIDOS
            );
        }
        return extensao;
    }

    /**
     * Salva uma imagem a partir de um array de bytes com nome único e registra no banco.
     */
    public ProdutoImagem salvarImagem(Long idProduto, String nomeOriginal, byte[] bytes, boolean isPrincipal, Integer ordem) {
        if (idProduto == null) {
            throw new IllegalArgumentException("O ID do produto é obrigatório.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("O conteúdo binário da imagem não pode estar vazio.");
        }

        Produto produto = produtoDAO.buscarPorId(idProduto);
        if (produto == null) {
            throw new IllegalArgumentException("Produto não encontrado com ID: " + idProduto);
        }

        String formato = extrairEValidarFormato(nomeOriginal);
        String extensaoLimpa = formato.equalsIgnoreCase("JPEG") ? "jpg" : formato.toLowerCase();

        // Geração de nome único e imutável para evitar colisões
        String nomeArquivoUnico = String.format("prod_%d_%s_%d.%s",
                idProduto,
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis(),
                extensaoLimpa
        );

        Path caminhoDestino = diretorioArmazenamento.resolve(nomeArquivoUnico);
        try {
            Files.write(caminhoDestino, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar arquivo de imagem no disco: " + caminhoDestino, e);
        }

        long totalExistente = produtoImagemDAO.contarPorProduto(idProduto);
        // Se for a primeira imagem, ela se torna automaticamente a principal
        boolean definirComoPrincipal = isPrincipal || totalExistente == 0;
        int ordemDefinida = ordem != null ? ordem : (int) totalExistente;

        // Armazena o caminho normalizado em formato relativo com barras universais
        String caminhoRelativoNormalizado = caminhoDestino.toString().replace('\\', '/');

        ProdutoImagem pi = new ProdutoImagem(
                produto,
                caminhoRelativoNormalizado,
                nomeArquivoUnico,
                nomeOriginal,
                formato,
                (long) bytes.length,
                definirComoPrincipal,
                ordemDefinida
        );

        if (definirComoPrincipal && totalExistente > 0) {
            // Se já existem outras e esta foi marcada como principal, desmarca as anteriores
            produtoImagemDAO.salvar(pi);
            produtoImagemDAO.definirComoPrincipal(idProduto, pi.getId());
            pi.setPrincipal(true);
            return pi;
        }

        return produtoImagemDAO.salvar(pi);
    }

    /**
     * Salva imagem a partir de um arquivo físico no disco (ex.: selecionado no FileChooser do JavaFX).
     */
    public ProdutoImagem salvarImagemArquivo(Long idProduto, File arquivoOrigem, boolean isPrincipal) {
        if (arquivoOrigem == null || !arquivoOrigem.exists() || !arquivoOrigem.isFile()) {
            throw new IllegalArgumentException("Arquivo de origem inválido ou inexistente: " + arquivoOrigem);
        }
        try {
            byte[] bytes = Files.readAllBytes(arquivoOrigem.toPath());
            return salvarImagem(idProduto, arquivoOrigem.getName(), bytes, isPrincipal, null);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo de imagem: " + arquivoOrigem.getAbsolutePath(), e);
        }
    }

    /**
     * Salva imagem enviada em formato Base64 (ideal para chamadas REST, endpoints remotos ou IA).
     */
    public ProdutoImagem salvarImagemBase64(Long idProduto, String nomeOriginal, String base64Payload, boolean isPrincipal) {
        if (base64Payload == null || base64Payload.isBlank()) {
            throw new IllegalArgumentException("Payload Base64 da imagem não pode ser nulo ou vazio.");
        }
        String payloadLimpo = base64Payload;
        // Remove cabeçalho data:image/...;base64, se presente
        int virgula = payloadLimpo.indexOf(',');
        if (virgula >= 0 && payloadLimpo.substring(0, virgula).contains("base64")) {
            payloadLimpo = payloadLimpo.substring(virgula + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(payloadLimpo.trim());
        return salvarImagem(idProduto, nomeOriginal, bytes, isPrincipal, null);
    }

    /**
     * Retorna todas as imagens cadastradas para o produto ordenadas por principal e ordem.
     */
    public List<ProdutoImagem> listarImagens(Long idProduto) {
        return produtoImagemDAO.listarPorProduto(idProduto);
    }

    /**
     * Retorna apenas a lista de URLs/caminhos das imagens vinculadas ao produto.
     */
    public List<String> listarUrlsImagens(Long idProduto) {
        return produtoImagemDAO.listarUrlsPorProduto(idProduto);
    }

    /**
     * Retorna a imagem principal do produto para exibição rápida no catálogo.
     */
    public ProdutoImagem obterImagemPrincipal(Long idProduto) {
        return produtoImagemDAO.buscarPrincipal(idProduto);
    }

    /**
     * Define uma imagem específica como principal do produto.
     */
    public void definirImagemPrincipal(Long idProduto, Long idImagem) {
        ProdutoImagem pi = produtoImagemDAO.buscarPorId(idImagem);
        if (pi == null || !pi.getProduto().getId().equals(idProduto)) {
            throw new IllegalArgumentException("Imagem ID " + idImagem + " não pertence ao produto ID " + idProduto);
        }
        produtoImagemDAO.definirComoPrincipal(idProduto, idImagem);
    }

    /**
     * Remove a imagem do banco e exclui o arquivo físico no disco.
     */
    public boolean removerImagem(Long idImagem, boolean removerArquivoFisico) {
        ProdutoImagem pi = produtoImagemDAO.buscarPorId(idImagem);
        if (pi == null) {
            return false;
        }

        String caminho = pi.getCaminhoArquivo();
        Long idProduto = pi.getProduto().getId();
        boolean eraPrincipal = pi.isPrincipal();

        boolean removido = produtoImagemDAO.remover(idImagem);

        if (removido && removerArquivoFisico && caminho != null) {
            try {
                Path path = Paths.get(caminho);
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // Loga e segue, o registro no banco já foi removido
                System.err.println("Aviso: Não foi possível deletar o arquivo físico da imagem: " + caminho);
            }
        }

        // Se a imagem deletada era a principal, elege automaticamente a próxima disponível
        if (removido && eraPrincipal) {
            List<ProdutoImagem> restantes = produtoImagemDAO.listarPorProduto(idProduto);
            if (!restantes.isEmpty()) {
                produtoImagemDAO.definirComoPrincipal(idProduto, restantes.get(0).getId());
            }
        }

        return removido;
    }

    /**
     * Retorna o arquivo físico de uma imagem existente.
     */
    public File obterArquivoFisico(Long idImagem) {
        ProdutoImagem pi = produtoImagemDAO.buscarPorId(idImagem);
        if (pi != null) {
            return pi.getArquivoFisico();
        }
        return null;
    }

    public Path getDiretorioArmazenamento() {
        return diretorioArmazenamento;
    }
}
