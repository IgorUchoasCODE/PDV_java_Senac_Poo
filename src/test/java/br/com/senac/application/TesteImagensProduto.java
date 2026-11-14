package br.com.senac.application;

import br.com.senac.controller.ImagemProdutoController;
import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import br.com.senac.repository.modal.produto.ProdutoImagem;
import br.com.senac.repository.modal.produto.Um;
import br.com.senac.service.ImagemProdutoService;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Validação rigorosa do subsistema de Imagens de Produtos:
 * 1. Formatos permitidos (PNG, JPG, JPEG, WEBP, GIF, SVG, BMP)
 * 2. Bloqueio de extensões inválidas (.exe, .pdf, .txt)
 * 3. Nomes únicos e proteção contra sobreescrita
 * 4. Persistência e retorno direto via DAO
 * 5. Eleição automática de imagem principal e troca atômica
 * 6. Exclusão física do arquivo em disco sincronizada com o banco
 * 7. Execução via RoteadorController (Endpoints / Tools Gemini)
 */
public class TesteImagensProduto {

    private static int testesExecutados = 0;
    private static int testesAprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println(" INICIANDO TESTE DO SUBSISTEMA DE CONTROLE DE IMAGENS DE PRODUTOS");
        System.out.println("================================================================================");

        try {
            RoteadorController roteador = new RoteadorController();
            ImagemProdutoController controller = roteador.imagens();
            ImagemProdutoService service = controller.getImagemProdutoService();
            ProdutoDAO produtoDAO = new ProdutoDAO();
            UmDAO umDAO = new UmDAO();

            // 1. Garante produto de teste no banco
            Um um = umDAO.buscarPorSigla("UN");
            if (um == null) {
                um = new Um("UN", "Unidade", "Unidade padrão", BigDecimal.ONE);
                umDAO.salvar(um);
            }

            String nomeProduto = "Smartphone Teste Foto " + System.currentTimeMillis();
            ProdutoFisico produto = new ProdutoFisico(nomeProduto, um);
            produtoDAO.salvar(produto);
            Long idProduto = produto.getId();
            afirmar(idProduto != null, "Produto de teste cadastrado com ID válido.");

            // 2. Teste de formatos aceitos (PNG, JPG, WEBP, GIF, SVG, BMP)
            String[] formatosAceitos = {"foto1.png", "foto2.jpg", "foto3.jpeg", "foto4.webp", "foto5.gif", "foto6.svg", "foto7.bmp"};
            for (String f : formatosAceitos) {
                String ext = service.extrairEValidarFormato(f);
                afirmar(ext != null && !ext.isEmpty(), "Formato aceito com sucesso: " + f);
            }

            // 3. Teste de rejeição de formatos proibidos
            boolean rejeitouExe = false;
            try {
                service.extrairEValidarFormato("malware.exe");
            } catch (IllegalArgumentException e) {
                rejeitouExe = true;
            }
            afirmar(rejeitouExe, "Extensão não suportada .exe bloqueada com sucesso.");

            boolean rejeitouPdf = false;
            try {
                service.extrairEValidarFormato("manual.pdf");
            } catch (IllegalArgumentException e) {
                rejeitouPdf = true;
            }
            afirmar(rejeitouPdf, "Extensão .pdf bloqueada na galeria de imagens.");

            // 4. Inserção da 1ª imagem (PNG via bytes simulados)
            byte[] bytesPng = "PNG_FAKE_DATA_HEADER_BYTES".getBytes(StandardCharsets.UTF_8);
            RespostaEndpoint<ProdutoImagem> resp1 = controller.adicionarImagemBytes(idProduto, "foto_frontal.png", bytesPng, false, 0);
            afirmar(resp1.sucesso(), "1ª imagem cadastrada via controller.");
            ProdutoImagem img1 = resp1.dados();
            afirmar(img1.isPrincipal(), "A primeira imagem do produto foi automaticamente definida como PRINCIPAL.");
            afirmar(img1.getNomeArquivo().startsWith("prod_" + idProduto), "Nome único gerado contém o prefixo prod_{id}: " + img1.getNomeArquivo());
            afirmar(img1.getArquivoFisico() != null && img1.getArquivoFisico().exists(), "Arquivo físico foi gravado no disco: " + img1.getCaminhoArquivo());

            // 5. Inserção da 2ª imagem (WEBP via Base64)
            byte[] bytesWebp = "RIFF_WEBP_FAKE_BYTES_TEST".getBytes(StandardCharsets.UTF_8);
            String base64Webp = Base64.getEncoder().encodeToString(bytesWebp);
            RespostaEndpoint<ProdutoImagem> resp2 = controller.adicionarImagemBase64(idProduto, "foto_traseira.webp", base64Webp, false);
            afirmar(resp2.sucesso(), "2ª imagem adicionada via Base64.");
            ProdutoImagem img2 = resp2.dados();
            afirmar(!img2.isPrincipal(), "2ª imagem NÃO substitui a principal se isPrincipal for false.");
            afirmar(!img1.getNomeArquivo().equals(img2.getNomeArquivo()), "Nomes de arquivo são estritamente únicos entre imagens.");

            // 6. Inserção da 3ª imagem (JPG via arquivo temporário File)
            File tempFile = File.createTempFile("teste_upload_", ".jpg");
            Files.write(tempFile.toPath(), "JPEG_RAW_BYTES_TEST".getBytes(StandardCharsets.UTF_8));
            tempFile.deleteOnExit();

            RespostaEndpoint<ProdutoImagem> resp3 = controller.adicionarImagemArquivo(idProduto, tempFile, false);
            afirmar(resp3.sucesso(), "3ª imagem adicionada a partir de arquivo File do JavaFX.");
            ProdutoImagem img3 = resp3.dados();

            // 7. Listagem de imagens do produto
            RespostaEndpoint<List<ProdutoImagem>> listagem = controller.listarImagens(idProduto);
            afirmar(listagem.sucesso() && listagem.dados().size() == 3, "Listagem retornou exatamente as 3 imagens cadastradas.");

            // 8. Teste de compatibilidade de URL para JavaFX
            String uriJavaFx = img1.getUriJavaFx();
            afirmar(uriJavaFx != null && uriJavaFx.startsWith("file:/"), "URI gerada para o JavaFX é válida: " + uriJavaFx);

            // 9. Alteração de imagem principal (img2 agora é a principal)
            RespostaEndpoint<String> respTroca = controller.definirImagemPrincipal(idProduto, img2.getId());
            afirmar(respTroca.sucesso(), "Imagem ID " + img2.getId() + " promovida a principal.");

            RespostaEndpoint<ProdutoImagem> respPrincipal = controller.obterImagemPrincipal(idProduto);
            afirmar(respPrincipal.dados().getId().equals(img2.getId()), "Imagem principal consultada no banco é de fato a img2.");

            // 10. Teste de execução via RoteadorController (simulando chamada por IA Gemini ou CLI)
            RespostaEndpoint<Object> resTool = roteador.executarEndpoint(RotaSistema.PRODUTO_IMAGEM_LISTAR, Map.of("idProduto", idProduto));
            afirmar(resTool.sucesso() && resTool.dados() instanceof List && ((List<?>) resTool.dados()).size() == 3, "Endpoint /produtos/imagens/listar despachado pelo RoteadorController.");

            // 11. Exclusão física de imagem (img3)
            File arq3 = img3.getArquivoFisico();
            afirmar(arq3.exists(), "Arquivo físico da img3 existia antes da deleção.");
            RespostaEndpoint<Boolean> respDel = controller.removerImagem(img3.getId(), true);
            afirmar(respDel.sucesso() && Boolean.TRUE.equals(respDel.dados()), "Deleção da imagem 3 retornou sucesso.");
            afirmar(!arq3.exists(), "Arquivo físico da img3 foi devidamente excluído do disco.");

            // 12. Cleanup das imagens 1 e 2
            controller.removerImagem(img1.getId(), true);
            controller.removerImagem(img2.getId(), true);
            afirmar(controller.listarImagens(idProduto).dados().isEmpty(), "Todas as imagens de teste foram limpas do banco e disco.");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO: %d / %d TESTES APROVADOS COM SUCESSO!\n", testesAprovados, testesExecutados);
            System.out.println("================================================================================");

        } catch (Throwable t) {
            System.err.println("FALHA CRÍTICA NO TESTE DE IMAGENS:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void afirmar(boolean condicao, String mensagem) {
        testesExecutados++;
        if (condicao) {
            testesAprovados++;
            System.out.println(" [OK] " + mensagem);
        } else {
            System.err.println(" [FALHA] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }
}
