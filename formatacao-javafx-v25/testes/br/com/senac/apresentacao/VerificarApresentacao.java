package br.com.senac.apresentacao;

import br.com.senac.controller.*;
import br.com.senac.view.javafx.abas.*;
import br.com.senac.view.javafx.ponte.*;
import br.com.senac.view.javafx.tema.*;
import javafx.application.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import javafx.scene.image.WritableImage;

public final class VerificarApresentacao {
    private static int checks;
    private static void ok(boolean b,String mensagem){if(!b)throw new AssertionError(mensagem);System.out.println("OK "+(++checks)+": "+mensagem);}
    public static final class Janela extends Application {
        private final ApresentacaoJavaFX adapter=new ApresentacaoJavaFX();
        @Override public void start(Stage stage) {
            Thread.ofVirtual().start(()->{
                try {
                    ok(ValoresVisuais.apresentar(new BigDecimal("1234.50"),"Preço venda").contains("1.234,50"),"Moeda brasileira");
                    ok(ValoresVisuais.apresentar(new BigDecimal("2.50000"),"Quantidade").equals("2,5"),"Quantidade decimal");
                    ok(ValoresVisuais.apresentar(true,"").equals("Sim"),"Booleano positivo");
                    ok(ValoresVisuais.apresentar(false,"").equals("Não"),"Booleano negativo");
                    ok(ValoresVisuais.apresentar(null,"").equals("Não informado"),"Ausência de dados");
                    ok(ValoresVisuais.apresentar(LocalDate.of(2026,11,16),"").equals("16/11/2026"),"Data tipada");
                    ok(ValoresVisuais.apresentar(java.sql.Date.valueOf("2026-11-16"),"").equals("16/11/2026"),"Data SQL do controller");
                    ok(ValoresVisuais.apresentar(java.sql.Timestamp.valueOf("2026-11-16 09:30:00"),"").equals("16/11/2026 09:30"),"Data e hora SQL do controller");
                    ok(ValoresVisuais.texto("2026-11-16T09:30:21.500","Data").equals("16/11/2026 09:30"),"Data recebida como texto");
                    ok(ValoresVisuais.texto("2026-19-40","Data").equals("2026-19-40"),"Data inválida preservada");
                    ok(ValoresVisuais.texto("000123","").equals("000123"),"Código com zeros preservado");
                    ok(ValoresVisuais.texto("12345678901","CPF").equals("12345678901"),"Documento preservado");
                    ok(ValoresVisuais.texto("R$ 1.234,50","Preço").equals("R$ 1.234,50"),"Moeda já formatada preservada");
                    ok(ValoresVisuais.texto("12,5%","Margem %").equals("12,5%"),"Percentual já formatado preservado");
                    ok(ValoresVisuais.apresentar(new BigDecimal("12.5"),"Margem %").equals("12,5%"),"Percentual explícito");
                    ok(ValoresVisuais.texto("2026-11-16 (Hoje)","Data").equals("16/11/2026 (Hoje)"),"Contexto de data preservado");
                    ok(ValoresVisuais.apresentar(new BigDecimal("2.345"),"Valor").contains("2,34"),"Exibição monetária half-even");
                    ok(ValoresVisuais.apresentar(Map.of("totalRegistros",12),"").contains("12")&&!ValoresVisuais.apresentar(Map.of("totalRegistros",12),"").contains("R$"),"Contagem não confundida com moeda");
                    ok(ValoresVisuais.texto("{\"precoVenda\":1234.50,\"data\":\"2026-11-16\"}","").contains("1.234,50"),"Retorno JSON convertido em campos legíveis");
                    ok(!ValoresVisuais.apresentar(Map.of("apiKey","segredo","nome","Produto"),"").contains("segredo"),"Credenciais omitidas em dados estruturados");
                    Map<String,Object> ciclo=new HashMap<>();ciclo.put("relacao",ciclo);
                    ok(ValoresVisuais.apresentar(ciclo,"").contains("Referência relacionada"),"Mapa circular não bloqueia exibição");
                    var tema=new GerenciadorTemaClimatico();var ponte=new PonteRoteadorIA(tema);
                    var r=ponte.getRoteador().executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR,Map.of("nome","Produto da validação visual","precoVenda",new BigDecimal("1234.50"),"siglaUm","UN"));
                    ok(r.sucesso()&&r.dados()!=null,"Cadastro pelo controller real em H2 isolado");
                    Object produto=r.dados();
                    ok(ValoresVisuais.apresentar(produto,"").equals("Produto da validação visual"),"Entidade do controller sem toString técnico");
                    Platform.runLater(()->{try{validarTela(stage,ponte,produto);}catch(Throwable e){falhar(e);}});
                }catch(Throwable e){falhar(e);}
            });
        }
        @SuppressWarnings("unchecked") private void validarTela(Stage stage,PonteRoteadorIA ponte,Object produto)throws Exception {
            TableView<Object[]> tabela=new TableView<>();
            String[] nomes={"Produto","Preço venda","Quantidade","Data","Ativo"};
            for(int i=0;i<nomes.length;i++){int pos=i;TableColumn<Object[],Object> c=new TableColumn<>(nomes[i]);c.setCellValueFactory(v->new SimpleObjectProperty<>(v.getValue()[pos]));c.setPrefWidth(i==0?320:170);tabela.getColumns().add(c);}
            tabela.getItems().add(new Object[]{produto,new BigDecimal("1234.50"),new BigDecimal("2.5"),LocalDate.of(2026,11,16),true});
            TextField entrada=new TextField("2026-11-16");entrada.setPromptText("Entrada do usuário: permanece intacta");
            VBox raiz=new VBox(12,new Label("Validação da apresentação · produto retornado pelo controller real"),new Label("Os demais valores da linha são casos de teste de formatação."),tabela,entrada);
            raiz.setStyle("-fx-padding:24;-fx-background-color:#f8fafc;-fx-font-size:14px;");
            stage.setScene(new Scene(raiz,1200,700));stage.show();raiz.applyCss();raiz.layout();adapter.aplicar(raiz);
            Set<String> textos=new HashSet<>();for(Node n:tabela.lookupAll(".table-cell"))if(n instanceof TableCell<?,?> c&&c.getText()!=null)textos.add(c.getText());
            ok(textos.contains("Produto da validação visual"),"Célula renderiza nome do produto");
            ok(textos.stream().anyMatch(t->t.contains("1.234,50")),"Célula renderiza moeda");
            ok(textos.contains("2,5"),"Célula renderiza quantidade");
            ok(textos.contains("16/11/2026"),"Célula renderiza data");
            ok(textos.contains("Sim"),"Célula renderiza booleano");
            ok(entrada.getText().equals("2026-11-16"),"Campo de entrada não alterado");
            ok(tabela.getItems().getFirst()[1].equals(new BigDecimal("1234.50")),"Valor original permanece BigDecimal");
            Label dinamico=new Label("2026-11-16");raiz.getChildren().add(dinamico);adapter.aplicar(raiz);
            dinamico.setText("2026-11-17T10:45:00");ok(dinamico.getText().equals("17/11/2026 10:45"),"Atualização assíncrona de texto continua formatada");
            ListView<Object> lista=new ListView<>();lista.getItems().add(produto);raiz.getChildren().add(lista);raiz.applyCss();raiz.layout();adapter.aplicar(raiz);
            ok(lista.lookupAll(".list-cell").stream().filter(n->n instanceof ListCell<?>).map(n->((ListCell<?>)n).getText()).anyMatch("Produto da validação visual"::equals),"Seleção mostra nome sem representação técnica");
            raiz.getChildren().removeAll(dinamico,lista);
            salvar(stage.getScene().snapshot(null),Path.of(System.getProperty("ui.documentada.evidencias"),"01-formatacao-controller.png"));
            var conector=new ConectorProblemasIA(ponte);
            List<Node> modulos=List.of(new AbaInicioClimaView(ponte,conector),new AbaBalcaoNotasView(ponte,conector),new AbaCatalogoEstoquePessoasView(ponte,conector),new AbaFinanceiroContabilidadeView(ponte,conector),new AbaEstatisticaAvancadaView(ponte,conector),new AbaMapaEventosGpsView(ponte,conector),new AbaPlanejamentoDemandaKanbanView(ponte,conector),new AbaControleOperacionalIAView(ponte,conector),new AbaConfiguracaoAmbienteView(ponte,conector));
            int i=0;for(Node modulo:modulos){raiz.getChildren().setAll(modulo);VBox.setVgrow(modulo,Priority.ALWAYS);raiz.applyCss();raiz.layout();adapter.aplicar(raiz);ok(modulo.getScene()!=null,"Módulo oficial montado: "+modulo.getClass().getSimpleName());salvar(stage.getScene().snapshot(null),Path.of(System.getProperty("ui.documentada.evidencias"),String.format("%02d-modulo.png",++i+1)));}
            System.out.println("APRESENTAÇÃO V2.5: "+checks+" verificações aprovadas.");
            Platform.exit();System.exit(0);
        }
    }
    private static void falhar(Throwable e){e.printStackTrace();Platform.exit();System.exit(1);}
    public static void main(String[] args){Application.launch(Janela.class,args);}
    private static void salvar(WritableImage img,Path arquivo)throws Exception {
        Files.createDirectories(arquivo.getParent());int w=(int)img.getWidth(),h=(int)img.getHeight();ByteArrayOutputStream dados=new ByteArrayOutputStream();
        try(DeflaterOutputStream zip=new DeflaterOutputStream(dados)){byte[] linha=new byte[w*4+1];for(int y=0;y<h;y++){linha[0]=0;for(int x=0;x<w;x++){int c=img.getPixelReader().getArgb(x,y),i=x*4+1;linha[i]=(byte)(c>>>16);linha[i+1]=(byte)(c>>>8);linha[i+2]=(byte)c;linha[i+3]=(byte)(c>>>24);}zip.write(linha);}}
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(arquivo))){out.writeLong(0x89504e470d0a1a0aL);ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(w);d.writeInt(h);d.writeByte(8);d.writeByte(6);d.writeByte(0);d.writeByte(0);d.writeByte(0);parte(out,"IHDR",b.toByteArray());parte(out,"IDAT",dados.toByteArray());parte(out,"IEND",new byte[0]);}
    }
    private static void parte(DataOutputStream out,String tipo,byte[] dados)throws Exception{byte[] t=tipo.getBytes(java.nio.charset.StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(t);crc.update(dados);out.writeInt(dados.length);out.write(t);out.write(dados);out.writeInt((int)crc.getValue());}
}
