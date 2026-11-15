package br.com.senac.view.av2;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Aceitação executada na thread JavaFX, exercitando os mesmos controles da apresentação. */
final class VerificacaoInterface {
    private static int verificacoes;
    private VerificacaoInterface() {}

    static void executar(PdvView view, Stage stage) {
        try {
            capturar(stage.getScene(), "01-frente-caixa-vazia");
            text(view, "busca-produto").setText("java");
            ok(tabela(view, "catalogo").getItems().size() == 1, "Busca por nome");
            text(view, "busca-produto").clear();
            escolha(view, "filtro-tipo", "Digital");
            ok(tabela(view, "catalogo").getItems().size() == 2, "Filtro de digitais");
            escolha(view, "filtro-tipo", "Todos os tipos");
            adicionar(view, "P003");
            Parent alerta = dialogo("alerta-validacao");
            ok(alerta != null, "Alerta de estoque zero");
            capturar(alerta.getScene(), "02-estoque-insuficiente");
            fechar(alerta);
            adicionar(view, "P001");
            adicionar(view, "D001");
            ok(tabela(view, "carrinho").getItems().size() == 2, "Inclusão de físico e digital");
            ok(label(view, "total-parcial").getText().contains("194,80"), "Subtotal misto");
            capturar(stage.getScene(), "03-frente-caixa-carrinho");
            tabela(view, "carrinho").getSelectionModel().selectFirst();
            fire(view, "alterar-qtd");
            Parent alterar = dialogo("alterar-dialog");
            text(alterar, "nova-quantidade").setText("13");
            fire(alterar, "salvar-formulario");
            ok(alterar.getScene().getWindow().isShowing(), "Quantidade acima do estoque bloqueada");
            text(alterar, "nova-quantidade").setText("2");
            fire(alterar, "salvar-formulario");
            ok(label(view, "total-parcial").getText().contains("324,70"), "Alteração de quantidade");
            tabela(view, "carrinho").getSelectionModel().selectFirst();
            fire(view, "alterar-qtd");
            alterar = dialogo("alterar-dialog");
            text(alterar, "nova-quantidade").setText("1");
            fire(alterar, "salvar-formulario");
            tabela(view, "carrinho").getSelectionModel().select(1);
            fire(view, "remover");
            ok(tabela(view, "carrinho").getItems().size() == 1, "Remoção de item");
            adicionar(view, "D001");

            fire(view, "menu-1");
            capturar(stage.getScene(), "04-produtos-estoque");
            fire(view, "novo-produto");
            Parent produto = dialogo("produto-dialog");
            capturar(produto.getScene(), "05-cadastro-produto-fisico");
            fire(produto, "salvar-formulario");
            ok(produto.getScene().getWindow().isShowing(), "Formulário vazio permanece aberto");
            text(produto, "produto-codigo").setText("T001");
            text(produto, "produto-nome").setText("Produto de teste da interface");
            text(produto, "produto-preco").setText("10,00");
            text(produto, "produto-estoque").setText("3");
            text(produto, "produto-peso").setText("1 kg");
            text(produto, "produto-dimensoes").setText("10 x 10 x 10 cm");
            fire(produto, "salvar-formulario");
            ok(tabela(view, "produtos").getItems().size() == 6, "Cadastro físico salvo");
            fire(view, "novo-produto");
            produto = dialogo("produto-dialog");
            escolha(produto, "produto-tipo", "Digital");
            ok(text(produto, "produto-peso").isDisabled(), "Peso desabilitado para digital");
            ok(!text(produto, "produto-link").isDisabled(), "Link habilitado para digital");
            capturar(produto.getScene(), "06-cadastro-produto-digital");
            fechar(produto);

            fire(view, "menu-2");
            capturar(stage.getScene(), "07-clientes");
            fire(view, "novo-cliente");
            Parent cliente = dialogo("cliente-dialog");
            capturar(cliente.getScene(), "08-cadastro-cliente");
            text(cliente, "cliente-nome").setText("Cliente de teste da interface");
            text(cliente, "cliente-documento").setText("TESTE-UI");
            text(cliente, "cliente-email").setText("invalido");
            fire(cliente, "salvar-formulario");
            ok(cliente.getScene().getWindow().isShowing(), "E-mail inválido bloqueado");
            text(cliente, "cliente-email").setText("teste@example.com");
            fire(cliente, "salvar-formulario");
            ok(tabela(view, "clientes").getItems().size() == 2, "Cadastro de cliente salvo");
            tabela(view, "clientes").getSelectionModel().select(1);
            fire(view, "editar-cliente");
            cliente = dialogo("cliente-dialog");
            text(cliente, "cliente-telefone").setText("(92) 99999-0001");
            fire(cliente, "salvar-formulario");
            ok(((PdvGateway.Cliente)tabela(view, "clientes").getItems().get(1)).telefone().contains("0001"), "Edição de cliente");

            fire(view, "menu-0");
            fire(view, "checkout");
            Parent pagamento = dialogo("pagamento-dialog");
            ok(label(pagamento, "pagamento-total").getText().contains("185,81"), "Total PIX");
            capturar(pagamento.getScene(), "09-pagamento-pix");
            fire(pagamento, "concluir-pagamento");
            ok(label(pagamento, "pagamento-erro").getText().contains("Marque"), "Recebimento requer confirmação");
            escolha(pagamento, "pagamento-metodo", "Dinheiro");
            text(pagamento, "pagamento-recebido").setText("200");
            ok(label(pagamento, "pagamento-troco").getText().contains("5,20"), "Troco em dinheiro");
            capturar(pagamento.getScene(), "10-pagamento-dinheiro");
            text(pagamento, "pagamento-recebido").setText("1");
            ((CheckBox)pagamento.lookup("#confirmacao-pagamento")).setSelected(true);
            fire(pagamento, "concluir-pagamento");
            ok(label(pagamento, "pagamento-erro").getText().contains("menor"), "Dinheiro insuficiente bloqueia venda");
            escolha(pagamento, "pagamento-metodo", "Cartão de crédito");
            ok(label(pagamento, "pagamento-total").getText().contains("200,64"), "Taxa do cartão");
            capturar(pagamento.getScene(), "11-pagamento-cartao");
            fechar(pagamento);
            ok(((PdvGateway.Produto)tabela(view, "catalogo").getItems().get(0)).estoque() == 12, "Voltar não baixa estoque");
            fire(view, "checkout");
            pagamento = dialogo("pagamento-dialog");
            ((CheckBox)pagamento.lookup("#confirmacao-pagamento")).setSelected(true);
            fire(pagamento, "concluir-pagamento");
            Parent recibo = dialogo("comprovante-dialog");
            ok(textarea(recibo, "texto-comprovante").getText().contains("185,81"), "Comprovante com total");
            capturar(recibo.getScene(), "12-comprovante");
            fechar(recibo);
            ok(tabela(view, "carrinho").getItems().isEmpty(), "Carrinho limpo após conclusão");
            ok(tabela(view, "catalogo").getItems().stream().map(p -> (PdvGateway.Produto)p).anyMatch(p -> p.codigo().equals("P001") && p.estoque() == 11), "Baixa de estoque");
            adicionar(view, "D001");
            fire(view, "checkout");
            pagamento = dialogo("pagamento-dialog");
            ok(text(pagamento, "pagamento-endereco").isDisabled(), "Digital não exige endereço");
            ok(label(pagamento, "pagamento-total").getText().contains("47,40"), "Digital sem frete");
            capturar(pagamento.getScene(), "13-compra-digital");
            fechar(pagamento);
            Platform.runLater(() -> {
                for (Window w : new ArrayList<>(Window.getWindows())) {
                    if (w.getScene() != null && w.getScene().getRoot() instanceof DialogPane pane && w.isShowing()) {
                        Node sim = pane.lookupButton(ButtonType.YES);
                        if (sim instanceof Button b) b.fire();
                    }
                }
            });
            fire(view, "cancelar-venda");
            ok(tabela(view, "carrinho").getItems().isEmpty(), "Cancelamento confirmado limpa carrinho");
            fire(view, "checkout");
            ok(dialogo("alerta-validacao") != null, "Checkout de carrinho vazio bloqueado");
            fechar(dialogo("alerta-validacao"));
            fire(view, "menu-3");
            ok(tabela(view, "historico").getItems().size() == 1, "Histórico da sessão");
            capturar(stage.getScene(), "14-historico");
            tabela(view, "historico").getSelectionModel().selectFirst();
            fire(view, "abrir-comprovante");
            ok(dialogo("comprovante-dialog") != null, "Reabrir comprovante");
            fechar(dialogo("comprovante-dialog"));
            fire(view, "menu-4");
            capturar(stage.getScene(), "15-guia-apresentacao");
            System.out.println("JAVAFX: " + verificacoes + " verificações de interface aprovadas; 15 capturas geradas.");
            Platform.exit();
        } catch (Throwable ex) {
            ex.printStackTrace();
            Platform.exit();
            System.exit(1);
        }
    }

    private static void adicionar(Parent root, String codigo) {
        text(root, "codigo-venda").setText(codigo);
        fire(root, "adicionar");
    }

    private static void ok(boolean passou, String descricao) {
        verificacoes++;
        if (!passou) throw new AssertionError(descricao);
        System.out.println("OK " + verificacoes + " · " + descricao);
    }

    private static void fire(Parent root, String id) { ((Button)root.lookup("#" + id)).fire(); }
    private static TextField text(Parent root, String id) { return (TextField)root.lookup("#" + id); }
    private static TextArea textarea(Parent root, String id) { return (TextArea)root.lookup("#" + id); }
    private static Label label(Parent root, String id) { return (Label)root.lookup("#" + id); }
    private static TableView<?> tabela(Parent root, String id) { return (TableView<?>)root.lookup("#" + id); }
    @SuppressWarnings("unchecked")
    private static void escolha(Parent root, String id, String valor) { ((ComboBox<String>)root.lookup("#" + id)).setValue(valor); }
    private static void fechar(Parent root) { root.getScene().getWindow().hide(); }

    private static Parent dialogo(String id) {
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (w.isShowing() && w.getScene() != null) {
                Node n = w.getScene().lookup("#" + id);
                if (n instanceof Parent p) return p;
            }
        }
        throw new AssertionError("Janela não encontrada: " + id);
    }

    private static void capturar(Scene scene, String nome) throws Exception {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage img = scene.snapshot(null);
        Path pasta = Path.of("capturas");
        Files.createDirectories(pasta);
        gravarPng(img, pasta.resolve(nome + ".png"));
    }

    /** PNG via pixels JavaFX e zlib; sem Swing, AWT ou biblioteca de imagem externa. */
    private static void gravarPng(WritableImage img, Path arquivo) throws Exception {
        int largura = (int)img.getWidth(), altura = (int)img.getHeight();
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (DeflaterOutputStream zip = new DeflaterOutputStream(pixels)) {
            for (int y = 0; y < altura; y++) {
                zip.write(0);
                for (int x = 0; x < largura; x++) {
                    int rgba = img.getPixelReader().getArgb(x, y);
                    zip.write((rgba >> 16) & 255);
                    zip.write((rgba >> 8) & 255);
                    zip.write(rgba & 255);
                    zip.write((rgba >>> 24) & 255);
                }
            }
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(arquivo))) {
            out.writeLong(0x89504e470d0a1a0aL);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(header);
            h.writeInt(largura); h.writeInt(altura); h.writeByte(8); h.writeByte(6);
            h.writeByte(0); h.writeByte(0); h.writeByte(0);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", pixels.toByteArray());
            chunk(out, "IEND", new byte[0]);
        }
    }

    private static void chunk(DataOutputStream out, String tipo, byte[] dados) throws Exception {
        byte[] nome = tipo.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeInt(dados.length); out.write(nome); out.write(dados);
        CRC32 crc = new CRC32(); crc.update(nome); crc.update(dados);
        out.writeInt((int)crc.getValue());
    }
}
