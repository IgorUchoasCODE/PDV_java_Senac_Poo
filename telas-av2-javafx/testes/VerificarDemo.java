package br.com.senac.view.av2;
import java.math.BigDecimal;
import static br.com.senac.view.av2.PdvGateway.*;

/** Casos de aceitação da demonstração, sem bibliotecas externas. */
public class VerificarDemo {
    private static int checks;
    static void ok(boolean v) { checks++;if(!v)throw new AssertionError("Falhou verificação "+checks); }
    static void falha(Runnable r) {boolean recusou=false;try{r.run();}catch(IllegalArgumentException e){recusou=true;}ok(recusou);}
    static void valor(BigDecimal v,String esperado){ok(v.compareTo(new BigDecimal(esperado))==0);}
    public static void main(String[] args) {
        DemoGateway d=new DemoGateway();
        falha(()->d.adicionar("P003",1));falha(()->d.adicionar("P001",13));falha(()->d.adicionar("P001",0));falha(()->d.adicionar("INVALIDO",1));
        d.adicionar("P001",1);falha(()->d.adicionar("P001",12));ok(d.itens().get(0).quantidade()==1);
        d.adicionar("D001",1);valor(d.resumo("PIX").total(),"185.81");valor(d.resumo("Dinheiro").total(),"194.80");valor(d.resumo("Cartão de crédito").total(),"200.64");
        d.alterarQuantidade("P001",2);valor(d.resumo("Dinheiro").frete(),"15.00");d.alterarQuantidade("P001",1);
        Cliente c=d.clientes().get(0);falha(()->d.confirmar(null,c.endereco(),"PIX",null));falha(()->d.confirmar(c,"","PIX",null));falha(()->d.confirmar(c,c.endereco(),"Dinheiro",new BigDecimal("1")));
        ok(d.produtos().stream().filter(p->p.codigo().equals("P001")).findFirst().orElseThrow().estoque()==12);ok(d.vendas().isEmpty());
        Venda v=d.confirmar(c,c.endereco(),"Dinheiro",new BigDecimal("200"));ok(v.cupom().contains("5,20"));ok(d.itens().isEmpty());ok(d.vendas().size()==1);ok(d.produtos().stream().filter(p->p.codigo().equals("P001")).findFirst().orElseThrow().estoque()==11);
        d.adicionar("D001",1);valor(d.resumo("PIX").frete(),"0.00");valor(d.resumo("PIX").total(),"47.40");d.confirmar(c,"","PIX",null);ok(d.vendas().size()==2);
        d.adicionar("P002",2);d.limpar();ok(d.produtos().stream().filter(p->p.codigo().equals("P002")).findFirst().orElseThrow().estoque()==8);
        Produto p=d.produtos().get(0);falha(()->d.salvarProduto(new Produto(p.codigo(),p.nome(),p.tipo(),p.preco(),-1,p.peso(),p.dimensoes(),p.link(),p.tamanho())));
        falha(()->d.confirmar(c,c.endereco(),"PIX",null));
        System.out.println(checks+" verificações aprovadas.");
    }
}
