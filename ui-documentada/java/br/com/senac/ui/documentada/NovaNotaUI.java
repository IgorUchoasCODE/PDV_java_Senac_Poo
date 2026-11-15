package br.com.senac.ui.documentada;

import br.com.senac.controller.*;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pagamento.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static br.com.senac.ui.documentada.ComponentesUI.*;

/** Monta os objetos públicos esperados pelas rotas; os cálculos de pagamento continuam no núcleo. */
public final class NovaNotaUI extends SplitPane {
    private final GatewayDaSessao gateway;
    private final ComboBox<String> tipo=new ComboBox<>(FXCollections.observableArrayList("Venda FIFO","Venda balcão","Compra","Venda por lote"));
    private final ComboBox<Produto> produto=new ComboBox<>();
    private final ComboBox<Pessoa> pessoa=new ComboBox<>();
    private final ComboBox<FormaPagamento> forma=new ComboBox<>();
    private final ComboBox<String> modalidade=new ComboBox<>(FXCollections.observableArrayList("À vista","A prazo","Parcelado"));
    private final TextField quantidade=new TextField("1"),valor=new TextField(),recebido=new TextField(),parcelas=new TextField("2"),lote=new TextField();
    private final DatePicker validade=new DatePicker(),vencimento=new DatePicker(LocalDate.now().plusDays(30));
    private final TableView<Linha> itens=new TableView<>();
    private final Label total=new Label("R$ 0,00"),previa=new Label(),status=new Label("Carregue o catálogo para montar uma nota.");
    private final TextArea comprovante=new TextArea();
    private final Button emitir;
    private List<Pessoa> pessoas=List.of();
    private NotaPagamento pagamento;
    private long revisao,revisaoItens;
    private boolean bloqueada,trocando;
    public record Linha(Produto produto,BigDecimal quantidade,BigDecimal valor,LocalDate validade,Long lote){}
    public NovaNotaUI(GatewayDaSessao gateway) {
        this.gateway=gateway;setId("nova-nota");setDividerPositions(.62);
        tipo.setValue("Venda FIFO");modalidade.setValue("À vista");
        nome(produto,p->"#"+p.getId()+" · "+p.getNome());nome(pessoa,p->"#"+p.getId()+" · "+p.getNome());nome(forma,p->p.getCodigo()+" · "+p.getNome());
        tipo.setId("nota-tipo");produto.setId("nota-produto");forma.setId("nota-forma");quantidade.setId("nota-quantidade");recebido.setId("nota-recebido");
        valor.setPromptText("Custo unitário da compra");lote.setPromptText("ID do lote para venda dirigida");
        GridPane campos=new GridPane();campos.setHgap(12);campos.setVgap(12);
        for(int i=0;i<2;i++){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(50);campos.getColumnConstraints().add(c);}
        campo(campos,0,0,"Operação",tipo);campo(campos,1,0,"Cliente / fornecedor",pessoa);
        campo(campos,0,1,"Produto",produto);campo(campos,1,1,"Quantidade",quantidade);
        campo(campos,0,2,"Custo unitário (compra)",valor);campo(campos,1,2,"Validade (compra)",validade);
        campo(campos,0,3,"Lote específico",lote);
        Button adicionar=botao("Adicionar item","nota-adicionar",this::adicionar);campos.add(adicionar,1,3);
        coluna("Produto",l->l.produto().getNome(),220);coluna("Quantidade",Linha::quantidade,90);coluna("Unitário",l->moeda(l.valor()),100);coluna("Lote",Linha::lote,80);
        itens.setPrefHeight(200);itens.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);itens.setId("nota-itens");
        itens.setPlaceholder(new Label("Nenhum item adicionado."));
        Button remover=botao("Remover item","nota-remover",()->{if(!bloqueada){revisaoItens++;itens.getItems().remove(itens.getSelectionModel().getSelectedItem());calcular();}});
        Button limpar=botao("Limpar rascunho","nota-limpar",()->{if(!bloqueada){revisaoItens++;itens.getItems().clear();calcular();}});
        comprovante.setEditable(false);comprovante.setWrapText(true);comprovante.setPrefRowCount(7);comprovante.setId("nota-comprovante");
        getItems().add(rolagem(card("Montar nota",status,campos,itens,linha(remover,limpar),texto("Resposta / comprovante operacional","rotulo"),comprovante)));
        emitir=botao("Revisar e emitir nota","nota-emitir",this::emitir);emitir.getStyleClass().add("primario");
        total.setId("nota-total");total.getStyleClass().add("valor-total");previa.setWrapText(true);previa.setId("nota-previa");
        recebido.setPromptText("Obrigatório apenas em dinheiro");
        getItems().add(rolagem(card("Pagamento pelo núcleo",texto("Valor final","rotulo"),total,texto("Forma cadastrada","rotulo"),forma,
                texto("Modalidade","rotulo"),modalidade,texto("Valor recebido","rotulo"),recebido,
                texto("Parcelas","rotulo"),parcelas,texto("Vencimento","rotulo"),vencimento,previa,emitir,
                botao("Recarregar cadastros","nota-recarregar",this::carregar))));
        tipo.valueProperty().addListener((o,a,b)->{
            if(trocando)return;if(!itens.getItems().isEmpty()){trocando=true;tipo.setValue(a);trocando=false;status.setText("Limpe o rascunho antes de trocar a operação.");return;}
            revisaoItens++;filtrarPessoas();calcular();
        });
        forma.valueProperty().addListener((o,a,b)->calcular());modalidade.valueProperty().addListener((o,a,b)->calcular());
        recebido.textProperty().addListener((o,a,b)->calcular());parcelas.textProperty().addListener((o,a,b)->calcular());vencimento.valueProperty().addListener((o,a,b)->calcular());
        carregar();
    }
    public void carregar() {
        gateway.executarEmBackground(()->{
            try{
                var ps=lista(gateway.executarRota(RotaSistema.PRODUTO_CATALOGO_LISTAR,Map.of()),Produto.class);
                var pe=lista(gateway.executarRota(RotaSistema.PESSOA_BUSCAR,Map.of("papel","TODOS")),Pessoa.class);
                var fp=lista(gateway.executarRota(RotaSistema.PAGAMENTO_FORMA_LISTAR_ATIVAS,Map.of()),FormaPagamento.class);
                Platform.runLater(()->{produto.setItems(FXCollections.observableArrayList(ps));pessoas=pe;filtrarPessoas();forma.setItems(FXCollections.observableArrayList(fp));forma.getSelectionModel().selectFirst();status.setText("Cadastros obtidos pelas rotas oficiais.");});
            }catch(Exception e){falha(e);}
        },this::falha);
    }
    private boolean compra(){return tipo.getValue().equals("Compra");}
    private void filtrarPessoas(){pessoa.setItems(FXCollections.observableArrayList(pessoas.stream().filter(p->compra()?p.isFornecedor():p.isCliente()).toList()));pessoa.getSelectionModel().selectFirst();}
    private BigDecimal nominal(){return itens.getItems().stream().map(l->l.quantidade().multiply(l.valor())).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void adicionar() {
        try{
            if(bloqueada)return;Produto p=produto.getValue();if(p==null)throw new IllegalArgumentException("Selecione um produto.");
            BigDecimal q=decimal(quantidade.getText());if(q.signum()<=0)throw new IllegalArgumentException("Quantidade deve ser positiva.");
            BigDecimal preco=compra()?decimal(valor.getText()):p.getPrecoVenda();
            Long idLote=tipo.getValue().equals("Venda por lote")?Long.parseLong(lote.getText().trim()):null;
            Linha l=new Linha(p,q,preco,validade.getValue(),idLote);
            if(compra()){itens.getItems().add(l);calcular();return;}
            String operacao=tipo.getValue();long pedido=++revisaoItens;
            BigDecimal solicitada=itens.getItems().stream().filter(x->idLote==null?x.produto().getId().equals(p.getId()):Objects.equals(x.lote(),idLote)).map(Linha::quantidade).reduce(q,BigDecimal::add);
            status.setText("Consultando disponibilidade…");
            gateway.executarEmBackground(()->{
                try{
                    if(idLote!=null){
                        Item<?> lote=(Item<?>)RequisicaoDaTela.dados(gateway.executarRota(RotaSistema.ESTOQUE_BUSCAR_LOTE_ESPECIFICO,Map.of("idLote",idLote)));
                        if(!lote.getProduto().getId().equals(p.getId())||lote.getQuantidadeDisponivel().compareTo(solicitada)<0||lote.getDataValidade()!=null&&lote.getDataValidade().isBefore(LocalDate.now()))throw new IllegalArgumentException("Lote incompatível, vencido ou sem saldo suficiente.");
                    }else{
                        BigDecimal saldo=(BigDecimal)RequisicaoDaTela.dados(gateway.executarRota(RotaSistema.ESTOQUE_SALDO_CONSOLIDADO,Map.of("idProduto",p.getId())));
                        if(saldo.compareTo(solicitada)<0)throw new IllegalArgumentException("Estoque insuficiente. Disponível: "+saldo);
                    }
                    Platform.runLater(()->{if(pedido!=revisaoItens||!operacao.equals(tipo.getValue()))return;itens.getItems().add(l);status.setText("Item adicionado com saldo consultado.");calcular();});
                }catch(Exception e){falha(e);}
            },this::falha);
        }catch(Exception e){falha(e);}
    }
    private void calcular() {
        long versao=++revisao;pagamento=null;emitir.setDisable(true);BigDecimal n=nominal();total.setText(moeda(n));
        boolean dinheiro=forma.getValue()!=null&&forma.getValue().getCodigo().equals("DINHEIRO")&&modalidade.getValue().equals("À vista");
        recebido.setDisable(!dinheiro);valor.setDisable(!compra());validade.setDisable(!compra());lote.setDisable(!tipo.getValue().equals("Venda por lote"));
        parcelas.setDisable(!modalidade.getValue().equals("Parcelado"));vencimento.setDisable(!modalidade.getValue().equals("A prazo"));
        if(n.signum()<=0||forma.getValue()==null){previa.setText("Adicione os itens e selecione uma forma de pagamento.");return;}
        Map<String,Object> p=new LinkedHashMap<>();p.put("valor",n);p.put("codigoForma",forma.getValue().getCodigo());RotaSistema rota;
        try{
            if(modalidade.getValue().equals("A prazo")){rota=RotaSistema.PAGAMENTO_CRIAR_A_PRAZO;if(vencimento.getValue()==null)throw new IllegalArgumentException("Informe o vencimento.");p.put("dataVencimento",vencimento.getValue());}
            else if(modalidade.getValue().equals("Parcelado")){rota=RotaSistema.PAGAMENTO_CRIAR_PARCELADO;p.put("quantidadeParcelas",Integer.parseInt(parcelas.getText()));}
            else{rota=RotaSistema.PAGAMENTO_CRIAR_A_VISTA;if(dinheiro){if(recebido.getText().isBlank())throw new IllegalArgumentException("Informe o valor recebido.");p.put("valorRecebido",decimal(recebido.getText()));}}
        }catch(Exception e){previa.setText(e.getMessage());return;}
        gateway.executarEmBackground(()->{
            try{
                NotaPagamento np=(NotaPagamento)RequisicaoDaTela.dados(gateway.executarRota(rota,p));
                Platform.runLater(()->{if(versao!=revisao)return;pagamento=np;total.setText(moeda(np.calcularValorFinal()));previa.setText(ResultadoLegivel.texto(np));emitir.setDisable(!np.isPagamentoValido()||bloqueada);});
            }catch(Exception e){Platform.runLater(()->{if(versao==revisao)previa.setText(e.getMessage());});}
        },this::falha);
    }
    private void emitir() {
        if(bloqueada||pagamento==null||itens.getItems().isEmpty())return;
        if(pessoa.getValue()==null&&!tipo.getValue().equals("Venda balcão")){status.setText("Selecione cliente/fornecedor.");return;}
        if(!confirmar(this,"Revisar nota","Emitir a nota com os itens e o pagamento apresentados? O núcleo validará e gravará no banco."))return;
        bloqueada=true;setDisable(true);emitir.setDisable(true);
        List<Linha> linhas=List.copyOf(itens.getItems());NotaPagamento pagamentoEscolhido=pagamento;Pessoa pessoaEscolhida=pessoa.getValue();String tipoEscolhido=tipo.getValue();
        gateway.executarEmBackground(()->{
            try{
                Map<String,Object> p=new LinkedHashMap<>();p.put("pagamentos",List.of(pagamentoEscolhido));RotaSistema rota;
                if(tipoEscolhido.equals("Compra")){
                    NotaCompra nota=new NotaCompra(pessoaEscolhida,"Compra pela interface documentada",LocalDateTime.now());
                    for(Linha l:linhas)nota.addItem(new Item<Produto>(l.produto(),l.valor(),l.quantidade(),l.validade()));
                    p.put("notaCompra",nota);rota=RotaSistema.NOTA_COMPRA_EMITIR;
                }else if(tipoEscolhido.equals("Venda por lote")){
                    Map<Long,BigDecimal> lotes=new LinkedHashMap<>();for(Linha l:linhas)lotes.merge(l.lote(),l.quantidade(),BigDecimal::add);
                    p.put("lotesDesejados",lotes);p.put("cliente",pessoaEscolhida);rota=RotaSistema.NOTA_VENDA_EMITIR_LOTE_DESEJAVEL;
                }else{
                    Map<Produto,BigDecimal> produtos=new LinkedHashMap<>();for(Linha l:linhas)produtos.merge(l.produto(),l.quantidade(),BigDecimal::add);
                    p.put("itens",produtos);if(pessoaEscolhida!=null)p.put("cliente",pessoaEscolhida);
                    rota=tipoEscolhido.equals("Venda balcão")?RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO:RotaSistema.NOTA_VENDA_EMITIR_FIFO;
                }
                var r=gateway.executarRota(rota,p);String texto=ResultadoLegivel.texto(r.dados());
                Platform.runLater(()->{bloqueada=false;setDisable(false);comprovante.setText(r.mensagem()+"\n\n"+texto);status.setText(r.sucesso()?"Nota respondida pelo núcleo.":"O núcleo recusou a emissão.");if(r.sucesso())itens.getItems().clear();calcular();});
            }catch(Exception e){Platform.runLater(()->{bloqueada=false;setDisable(false);calcular();});falha(e);}
        },e->{Platform.runLater(()->{bloqueada=false;setDisable(false);calcular();});falha(e);});
    }
    private void falha(Throwable e){Platform.runLater(()->status.setText("Confira: "+e.getMessage()));}
    private static BigDecimal decimal(String s){return new BigDecimal(s.trim().replace(',','.'));}
    private static <T> void nome(ComboBox<T> c,Function<T,String> f){c.setMaxWidth(Double.MAX_VALUE);c.setConverter(new StringConverter<>(){public String toString(T t){return t==null?"":f.apply(t);}public T fromString(String s){return null;}});}
    private static void campo(GridPane g,int x,int y,String s,javafx.scene.Node n){g.add(new VBox(4,texto(s,"rotulo"),n),x,y);}
    private <T,V> void coluna(String s,Function<Linha,V> f,double w){TableColumn<Linha,V> c=new TableColumn<>(s);c.setPrefWidth(w);c.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(f.apply(x.getValue())));itens.getColumns().add(c);}
    private static <T> List<T> lista(RespostaEndpoint<Object> r,Class<T> c){if(!r.sucesso())throw new IllegalArgumentException(r.mensagem());return r.dados() instanceof List<?> l?l.stream().filter(c::isInstance).map(c::cast).toList():List.of();}
}
