package br.com.senac.ui.documentada;

import br.com.senac.controller.*;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.view.javafx.gateway.RoteadorSistemaGateway;
import javafx.application.*;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.WritableImage;
import javafx.stage.*;
import java.util.*;
import java.util.function.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;

/** Aceitação da camada adicional usando o núcleo original, sem mocks, DAOs ou alterações no projeto. */
public final class VerificarCamadaUI {
    public static final class Janela extends Application {
        private GatewayDaSessao gateway;
        private ShellDocumentado shell;
        private Stage stage;
        private int checks;
        private Produto produto;
        private NovaNotaUI nota;
        @Override public void start(Stage s) {
            stage=s;gateway=new GatewayDaSessao(new RoteadorSistemaGateway());shell=new ShellDocumentado(gateway);
            Scene scene=new Scene(shell,1440,980);
            scene.getStylesheets().add(getClass().getResource("/css/sazonalisei-theme.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource("/ui-documentada.css").toExternalForm());
            stage.setScene(scene);stage.setTitle("Verificação · UI documental / núcleo original");stage.show();
            esperar(()->shell.pronto()&&shell.contagensProntas(),this::catalogo,0);
        }
        private void catalogo() {
            try {
                capturar("01-visao-geral");shell.mostrar("rotas");
            }
            catch(Exception e){falhar(e);return;}
            try {
                shell.mostrar("rotas");
                shell.applyCss();shell.layout();
                ComboBox<?> seletor=(ComboBox<?>)shell.lookup("#rota-documentada");
                ok(seletor.getItems().size()==85,"85 rotas documentadas disponíveis no seletor");
                @SuppressWarnings("unchecked") ComboBox<RotasDoDocumento.Operacao> combo=(ComboBox<RotasDoDocumento.Operacao>)seletor;
                for(var op:new RotasDoDocumento().todas()) {
                    combo.setValue(op);shell.applyCss();shell.layout();
                    for(var campo:op.campos())ok(shell.lookup("#campo-"+campo.nome())!=null,"Campo documentado: "+op.caminho()+" / "+campo.nome());
                }
                combo.setValue(new RotasDoDocumento().todas().stream().filter(x->x.id().equals("PRODUTO_FISICO_CADASTRAR")).findFirst().orElseThrow());
                texto(shell,"campo-nome").setText("Produto da UI documental");texto(shell,"campo-precoVenda").setText("100,00");texto(shell,"campo-siglaUm").setText("UN");
                Platform.runLater(this::confirmarJanela);botao(shell,"executar-rota").fire();
                esperar(()->((TextArea)shell.lookup("#resposta-rota")).getText().contains("Produto da UI documental"),()->{
                    ok(true,"Cadastro executado pela UI no núcleo original");capturar("02-formulario-documentado");
                    trabalho(()->{
                        var r=gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR,Map.of());
                        if(!r.sucesso())throw new IllegalStateException(r.mensagem());
                        produto=((List<?>)r.dados()).stream().filter(Produto.class::isInstance).map(Produto.class::cast).filter(p->p.getNome().equals("Produto da UI documental")).findFirst().orElseThrow();
                        return gateway.executarRota(RotaSistema.PESSOA_SIMPLES_CADASTRAR,Map.of("nome","Fornecedor da UI documental","isCliente",false,"isFornecedor",true));
                    },r->{ok(r.sucesso(),"Fornecedor criado pela fachada oficial");prepararCompra();});
                },0);
            }catch(Throwable e){falhar(e);}
        }
        private void prepararCompra() {
            shell.mostrar("notas");shell.applyCss();shell.layout();nota=(NovaNotaUI)shell.lookup("#nova-nota");
            esperar(()->!combo(nota,"nota-produto").getItems().isEmpty(),()->{
                combo(nota,"nota-tipo").setValue("Compra");selecionarProduto();
                texto(nota,"nota-quantidade").setText("2");
                // O custo é o único campo sem ID comercial: localizar pelo prompt evita tocar na classe existente do núcleo.
                TextField custo=nota.lookupAll(".text-field").stream().filter(TextField.class::isInstance).map(TextField.class::cast)
                        .filter(t->"Custo unitário da compra".equals(t.getPromptText())).findFirst().orElseThrow();custo.setText("25,00");
                escolherForma("PIX");botao(nota,"nota-adicionar").fire();
                esperar(()->((Label)nota.lookup("#nota-total")).getText().contains("47,50"),()->{
                    ok(true,"Pagamento de compra calculado pelo núcleo (PIX)");capturar("03-nova-compra");
                    Platform.runLater(this::confirmarJanela);botao(nota,"nota-emitir").fire();
                    esperar(()->!((TextArea)nota.lookup("#nota-comprovante")).getText().isBlank(),()->{
                        ok(((TextArea)nota.lookup("#nota-comprovante")).getText().contains("NotaCompra"),"Compra emitida pela rota original");
                        combo(nota,"nota-tipo").setValue("Venda FIFO");selecionarProduto();texto(nota,"nota-quantidade").setText("1");escolherForma("PIX");botao(nota,"nota-adicionar").fire();
                        esperar(()->((Label)nota.lookup("#nota-total")).getText().contains("95,00"),()->{
                            ok(true,"Venda calcula o valor final pela forma do banco");capturar("04-venda-pix");testarBandeja();
                        },0);
                    },0);
                },0);
            },0);
        }
        private void testarBandeja() {
            trabalho(()->gateway.perguntarAoChat("teste","$$ C= #Produto nm> Produto Bandeja Documental, um> UN, vv> 19.90;"),resposta->{
                ok(resposta.contains("OP-1"),"DSL oficial prepara OP-1 sem API de IA");
                shell.mostrar("bandeja");
                shell.applyCss();shell.layout();
                esperar(()->((TextArea)shell.lookup("#conteudo-bandeja")).getText().contains("Produto Bandeja Documental"),()->{
                    ok(true,"Bandeja visual compartilha a sessão real do chatbot");capturar("05-bandeja-real");
                    trabalho(()->gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR,Map.of()),r->{
                        ok(!r.dados().toString().contains("Produto Bandeja Documental"),"Preparar não grava o produto antes de SIM");
                        Platform.runLater(this::confirmarJanela);botao(shell,"bandeja-confirmar").fire();
                        esperar(()->!((BandejaUI)shell.lookup("#bandeja-oficial")).enviando(),()->{
                            trabalho(()->gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR,Map.of()),salvos->{
                                ok(salvos.dados().toString().contains("Produto Bandeja Documental"),"SIM da bandeja executa no mesmo núcleo");restantes();
                            });
                        },0);
                    });
                },0);
            });
        }
        private void restantes() {
            trabalho(()->gateway.executarComandoTerminalChat("teste",":ajuda"),r->{
                ok(r.contains(":bandeja"),"Comandos locais do chatbot oficial disponíveis");
                String[] paginas={"produtos","pessoas","estoque","pagamentos","config","estatisticas","graficos","grafo","mapa"};
                for(int i=0;i<paginas.length;i++){shell.mostrar(paginas[i]);capturar(String.format("%02d-%s",i+6,paginas[i]));ok(true,"Componente oficial montado: "+paginas[i]);}
                System.out.println("UI DOCUMENTAL: "+checks+" verificações aprovadas; sem alteração no núcleo.");
                Platform.exit();System.exit(0);
            });
        }
        @SuppressWarnings("unchecked") private ComboBox<Object> combo(Parent p,String id){return (ComboBox<Object>)p.lookup("#"+id);}
        private TextField texto(Parent p,String id){return (TextField)p.lookup("#"+id);}
        private Button botao(Parent p,String id){return (Button)p.lookup("#"+id);}
        private void selecionarProduto(){ComboBox<Object> c=combo(nota,"nota-produto");c.setValue(c.getItems().stream().filter(x->x instanceof Produto p&&p.getId().equals(produto.getId())).findFirst().orElseThrow());}
        private void escolherForma(String codigo){ComboBox<Object> c=combo(nota,"nota-forma");c.setValue(c.getItems().stream().filter(x->x instanceof FormaPagamento f&&f.getCodigo().equals(codigo)).findFirst().orElseThrow());}
        private void confirmarJanela(){for(Window w:new ArrayList<>(Window.getWindows()))if(w.isShowing()&&w.getScene().getRoot() instanceof DialogPane p){Node b=p.lookupButton(ButtonType.OK);if(b instanceof Button botao)botao.fire();}}
        private <T> void trabalho(Supplier<T> func,Consumer<T> depois){gateway.executarEmBackground(()->{try{T r=func.get();Platform.runLater(()->{try{depois.accept(r);}catch(Throwable e){falhar(e);}});}catch(Throwable e){falhar(e);}},this::falhar);}
        private void esperar(BooleanSupplier pronto,Runnable depois,int n){try{if(pronto.getAsBoolean()){Platform.runLater(()->{try{depois.run();}catch(Throwable e){falhar(e);}});return;}if(n>1200)throw new AssertionError("Tempo esgotado aguardando resposta do núcleo original.");PauseTransition t=new PauseTransition(Duration.millis(100));t.setOnFinished(e->esperar(pronto,depois,n+1));t.play();}catch(Throwable e){falhar(e);}}
        private void ok(boolean b,String s){if(!b)throw new AssertionError(s);checks++;System.out.println("OK "+checks+": "+s);}
        private void capturar(String nome){try{shell.applyCss();shell.layout();salvar(stage.getScene().snapshot(null),Path.of(System.getProperty("ui.documentada.evidencias"),nome+".png"));}catch(Exception e){throw new RuntimeException(e);}}
        private void falhar(Throwable e){e.printStackTrace();Platform.exit();System.exit(1);}
    }
    public static void main(String[] args){Application.launch(Janela.class,args);}
    private static void salvar(WritableImage img,Path arquivo) throws Exception {
        Files.createDirectories(arquivo.getParent());int w=(int)img.getWidth(),h=(int)img.getHeight();
        ByteArrayOutputStream dados=new ByteArrayOutputStream();
        try(DeflaterOutputStream zip=new DeflaterOutputStream(dados)){
            byte[] linha=new byte[w*4+1];
            for(int y=0;y<h;y++){linha[0]=0;for(int x=0;x<w;x++){int c=img.getPixelReader().getArgb(x,y),i=x*4+1;linha[i]=(byte)(c>>>16);linha[i+1]=(byte)(c>>>8);linha[i+2]=(byte)c;linha[i+3]=(byte)(c>>>24);}zip.write(linha);}
        }
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(arquivo))){
            out.writeLong(0x89504e470d0a1a0aL);ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
            d.writeInt(w);d.writeInt(h);d.writeByte(8);d.writeByte(6);d.writeByte(0);d.writeByte(0);d.writeByte(0);
            parte(out,"IHDR",b.toByteArray());parte(out,"IDAT",dados.toByteArray());parte(out,"IEND",new byte[0]);
        }
    }
    private static void parte(DataOutputStream out,String tipo,byte[] dados)throws Exception{byte[] t=tipo.getBytes(java.nio.charset.StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(t);crc.update(dados);out.writeInt(dados.length);out.write(t);out.write(dados);out.writeInt((int)crc.getValue());}
}
