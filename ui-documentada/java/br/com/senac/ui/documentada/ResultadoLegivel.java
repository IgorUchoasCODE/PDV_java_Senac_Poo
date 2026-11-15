package br.com.senac.ui.documentada;

import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import java.lang.reflect.Array;
import java.util.*;
import static br.com.senac.ui.documentada.ComponentesUI.moeda;

final class ResultadoLegivel {
    private ResultadoLegivel(){}
    static String texto(Object o) {
        if(o==null)return "Nenhum dado retornado.";
        if(o instanceof Nota n) {
            StringBuilder b=new StringBuilder("NOTA OPERACIONAL · SEM AUTORIZAÇÃO NF-e\n");
            b.append(n.getClass().getSimpleName()).append(" #").append(n.getId()).append(" · ").append(n.getDataGerada())
                    .append("\nPessoa: ").append(n.getPessoa()==null?"—":n.getPessoa().getNome());
            for(Item<?> i:n.getItens())b.append("\nItem #").append(i.getId()).append(" · ").append(i.getProduto().getNome())
                    .append(" · ").append(i.getQuantidade()).append(" × ").append(moeda(i.getValor())).append(" = ").append(moeda(i.getSubtotal()));
            b.append("\nTotal da nota: ").append(moeda(n.getValorTotal())).append("\nPago: ").append(moeda(n.getTotalPago())).append("\nSaldo: ").append(moeda(n.getSaldoRestante()));
            for(var p:n.getPagamentos())b.append("\n\n").append(texto(p));return b.toString();
        }
        if(o instanceof NotaPagamento p) {
            String t=(p.getId()==null?"Prévia de pagamento":"Pagamento #"+p.getId())+" · "+(p.getFormaPagamento()==null?"":p.getFormaPagamento().getNome())+
                    "\nNominal: "+moeda(p.getValor())+" | Final: "+moeda(p.calcularValorFinal())+" | Custo de operadora: "+moeda(p.calcularCustoTaxaOperadora());
            if(p instanceof PagamentoAVista a)t+="\nRecebido: "+moeda(a.getValorRecebido())+" | Troco: "+moeda(a.calcularTroco());return t;
        }
        if(o instanceof Map<?,?> m){StringBuilder b=new StringBuilder();m.forEach((k,v)->b.append(k).append(": ").append(texto(v)).append("\n"));return b.toString();}
        if(o instanceof Collection<?> c)return c.isEmpty()?"Nenhum registro encontrado.":String.join("\n\n",c.stream().map(ResultadoLegivel::texto).toList());
        if(o.getClass().isArray()){List<String> a=new ArrayList<>();for(int i=0;i<Array.getLength(o);i++)a.add(texto(Array.get(o,i)));return String.join(" | ",a);}
        return o.toString();
    }
}
