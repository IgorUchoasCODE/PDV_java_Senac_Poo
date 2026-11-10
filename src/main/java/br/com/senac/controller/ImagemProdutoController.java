package br.com.senac.controller;

import br.com.senac.repository.modal.produto.ProdutoImagem;
import br.com.senac.service.ImagemProdutoService;

import java.io.File;
import java.util.List;

/**
 * Controller responsável pela comunicação, gerenciamento e endpoints das imagens de produtos.
 * Faz a ponte direta entre a interface gráfica JavaFX / IA / API e o ImagemProdutoService.
 */
public class ImagemProdutoController {

    private final ImagemProdutoService imagemProdutoService;

    public ImagemProdutoController() {
        this(new ImagemProdutoService());
    }

    public ImagemProdutoController(ImagemProdutoService imagemProdutoService) {
        this.imagemProdutoService = imagemProdutoService;
    }

    /**
     * Adiciona uma imagem ao produto a partir de um arquivo local selecionado no JavaFX (FileChooser).
     */
    public RespostaEndpoint<ProdutoImagem> adicionarImagemArquivo(Long idProduto, File arquivo, boolean isPrincipal) {
        try {
            ProdutoImagem pi = imagemProdutoService.salvarImagemArquivo(idProduto, arquivo, isPrincipal);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_ADICIONAR, "Imagem física adicionada com sucesso ao produto.", pi);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_ADICIONAR.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Adiciona uma imagem ao produto a partir de um payload Base64 (ideal para chamadas REST ou IA Gemini).
     */
    public RespostaEndpoint<ProdutoImagem> adicionarImagemBase64(Long idProduto, String nomeOriginal, String base64Payload, boolean isPrincipal) {
        try {
            ProdutoImagem pi = imagemProdutoService.salvarImagemBase64(idProduto, nomeOriginal, base64Payload, isPrincipal);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_ADICIONAR, "Imagem Base64 gravada com sucesso no storage e banco.", pi);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_ADICIONAR.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Adiciona uma imagem a partir de bytes brutos.
     */
    public RespostaEndpoint<ProdutoImagem> adicionarImagemBytes(Long idProduto, String nomeOriginal, byte[] bytes, boolean isPrincipal, Integer ordem) {
        try {
            ProdutoImagem pi = imagemProdutoService.salvarImagem(idProduto, nomeOriginal, bytes, isPrincipal, ordem);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_ADICIONAR, "Imagem binária persistida com sucesso.", pi);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_ADICIONAR.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Lista todas as imagens cadastradas para o produto especificado.
     */
    public RespostaEndpoint<List<ProdutoImagem>> listarImagens(Long idProduto) {
        try {
            List<ProdutoImagem> lista = imagemProdutoService.listarImagens(idProduto);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_LISTAR, "Imagens do produto carregadas com sucesso.", lista);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_LISTAR.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Retorna diretamente apenas a lista de URLs/caminhos de todas as imagens vinculadas ao produto.
     */
    public RespostaEndpoint<List<String>> listarUrlsImagens(Long idProduto) {
        try {
            List<String> urls = imagemProdutoService.listarUrlsImagens(idProduto);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_LISTAR, "URLs das imagens carregadas com sucesso.", urls);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_LISTAR.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Retorna a imagem principal do produto para exibição na listagem do JavaFX.
     */
    public RespostaEndpoint<ProdutoImagem> obterImagemPrincipal(Long idProduto) {
        try {
            ProdutoImagem principal = imagemProdutoService.obterImagemPrincipal(idProduto);
            if (principal == null) {
                return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_PRINCIPAL_OBTER.getCaminhoRota(), "Nenhuma imagem cadastrada para este produto.");
            }
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_PRINCIPAL_OBTER, "Imagem principal recuperada com sucesso.", principal);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_PRINCIPAL_OBTER.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Define uma imagem existente como a principal do produto.
     */
    public RespostaEndpoint<String> definirImagemPrincipal(Long idProduto, Long idImagem) {
        try {
            imagemProdutoService.definirImagemPrincipal(idProduto, idImagem);
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_DEFINIR_PRINCIPAL, "Imagem definida como principal.", "Imagem ID " + idImagem + " definida como principal com sucesso.");
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_DEFINIR_PRINCIPAL.getCaminhoRota(), e.getMessage());
        }
    }

    /**
     * Remove uma imagem do produto, excluindo o registro no banco e o arquivo físico no disco.
     */
    public RespostaEndpoint<Boolean> removerImagem(Long idImagem, boolean removerArquivoFisico) {
        try {
            boolean removido = imagemProdutoService.removerImagem(idImagem, removerArquivoFisico);
            if (!removido) {
                return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_REMOVER.getCaminhoRota(), "Imagem ID " + idImagem + " não encontrada para remoção.");
            }
            return RespostaEndpoint.ok(RotaSistema.PRODUTO_IMAGEM_REMOVER, "Imagem removida com sucesso.", Boolean.TRUE);
        } catch (Exception e) {
            return RespostaEndpoint.erro(RotaSistema.PRODUTO_IMAGEM_REMOVER.getCaminhoRota(), e.getMessage());
        }
    }

    public ImagemProdutoService getImagemProdutoService() {
        return imagemProdutoService;
    }
}
