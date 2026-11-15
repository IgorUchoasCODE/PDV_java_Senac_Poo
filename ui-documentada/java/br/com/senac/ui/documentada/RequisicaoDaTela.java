package br.com.senac.ui.documentada;

import br.com.senac.controller.*;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.*;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Adapta dados de apresentação às assinaturas existentes, sempre consultando pelo gateway. */
public final class RequisicaoDaTela {
    private static final ObjectMapper JSON=new ObjectMapper();
    private RequisicaoDaTela(){}
    public static Map<String,Object> ler(RotasDoDocumento.Operacao op,Map<String,String> campos,String extras) throws Exception {
        Map<String,Object> p=new LinkedHashMap<>();
        for(var campo:op.campos()) {
            String v=campos.getOrDefault(campo.nome(),"").trim();if(v.isEmpty())continue;
            try {
                Object valor=switch(campo.tipo()) {
                    case "Long" -> Long.parseLong(v);
                    case "Integer","int" -> Integer.parseInt(v);
                    case "BigDecimal" -> new BigDecimal(v.replace(',','.'));
                    case "Boolean","boolean" -> {
                        if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException("Escolha true ou false.");
                        yield Boolean.parseBoolean(v);
                    }
                    case "LocalDate" -> LocalDate.parse(v);
                    case "LocalDateTime" -> LocalDateTime.parse(v);
                    case "List","Map" -> JSON.readValue(v,Object.class);
                    default -> v;
                };
                p.put(campo.nome(),valor);
            }catch(Exception e){throw new IllegalArgumentException("Confira "+campo.nome()+": "+e.getMessage());}
        }
        if(!extras.isBlank()) {
            Object lido=JSON.readValue(extras,Object.class);
            if(!(lido instanceof Map<?,?> m))throw new IllegalArgumentException("Os parâmetros avançados devem ser um objeto JSON.");
            m.forEach((k,v)->p.put(k.toString(),v));
        }
        return p;
    }
    public static RespostaEndpoint<Object> executar(SistemaGateway g,RotasDoDocumento.Operacao op,Map<String,Object> origem) {
        Map<String,Object> p=new LinkedHashMap<>(origem);
        // Ajustes de nomes entre o HTML e a assinatura pública atual, sem alterar o núcleo.
        renomear(p,"tokenConfirmacao","confirmacao");
        if(op.id().equals("PESSOA_DADOS_ATUALIZAR"))renomear(p,"novoNome","nome");
        renomear(p,"lotesEQuantidades","lotesDesejados");
        for(String chave:List.of("notaCompra","novaCompra","notaVenda")) {
            if(p.get(chave) instanceof String s && !s.isBlank())
                p.put(chave,dados(g.executarRota(RotaSistema.NOTA_BUSCAR_UNICA,Map.of("idNota",Long.parseLong(s)))));
        }
        for(String chave:List.of("cliente","fornecedor")) {
            if(p.get(chave) instanceof String s&&!s.isBlank())
                p.put(chave,dados(g.executarRota(RotaSistema.PESSOA_BUSCAR_UNICA,Map.of("idPessoa",Long.parseLong(s)))));
        }
        if(p.get("produto") instanceof String s&&!s.isBlank())
            p.put("produto",dados(g.executarRota(RotaSistema.PRODUTO_BUSCAR_UNICO,Map.of("idProduto",Long.parseLong(s)))));
        if(p.get("pagamento") instanceof String s) {
            String[] ids=s.split(":");
            if(ids.length!=2)throw new IllegalArgumentException("Pagamento: informe ID_DA_NOTA:ID_DO_PAGAMENTO.");
            Nota n=(Nota)dados(g.executarRota(RotaSistema.NOTA_BUSCAR_UNICA,Map.of("idNota",Long.parseLong(ids[0]))));
            long id=Long.parseLong(ids[1]);
            p.put("pagamento",n.getPagamentos().stream().filter(x->Objects.equals(x.getId(),id)).findFirst().orElseThrow(()->new IllegalArgumentException("Pagamento não pertence à nota.")));
        }
        if(p.get("links") instanceof List<?> links) {
            p.put("links",links.stream().map(x->JSON.convertValue(x,LinkProdutoDigital.class)).toList());
        }
        if(p.get("pagamentos") instanceof List<?> pagamentos) {
            List<NotaPagamento> convertidos=new ArrayList<>();
            for(Object x:pagamentos) {
                if(x instanceof NotaPagamento np){convertidos.add(np);continue;}
                if(!(x instanceof Map<?,?> m))throw new IllegalArgumentException("Cada pagamento deve ser um objeto.");
                Map<String,Object> c=new LinkedHashMap<>();m.forEach((k,v)->c.put(k.toString(),v));
                String modalidade=String.valueOf(c.getOrDefault("modalidade","VISTA"));
                RotaSistema rota=modalidade.equalsIgnoreCase("PRAZO")?RotaSistema.PAGAMENTO_CRIAR_A_PRAZO:
                        modalidade.equalsIgnoreCase("PARCELADO")?RotaSistema.PAGAMENTO_CRIAR_PARCELADO:RotaSistema.PAGAMENTO_CRIAR_A_VISTA;
                if(c.get("dataVencimento") instanceof String s)c.put("dataVencimento",LocalDate.parse(s));
                convertidos.add((NotaPagamento)dados(g.executarRota(rota,c)));
            }
            p.put("pagamentos",convertidos);
        }
        if(p.get("itensDevolvidos") instanceof Map<?,?> mapa && p.get("notaVenda") instanceof Nota nota) {
            Map<Item<? extends Produto>,BigDecimal> itens=new LinkedHashMap<>();
            mapa.forEach((id,q)->{
                Item<? extends Produto> i=nota.getItens().stream().filter(x->Objects.equals(x.getId(),Long.valueOf(id.toString()))).findFirst().orElseThrow(()->new IllegalArgumentException("Item não encontrado na nota: "+id));
                itens.put(i,new BigDecimal(q.toString().replace(',','.')));
            });p.put("itensDevolvidos",itens);
        }
        if(p.get("itens") instanceof Map<?,?> mapa && op.id().startsWith("NOTA_VENDA")) {
            Map<Produto,BigDecimal> itens=new LinkedHashMap<>();
            mapa.forEach((id,q)->{
                Produto produto=id instanceof Produto pr?pr:(Produto)dados(g.executarRota(RotaSistema.PRODUTO_BUSCAR_UNICO,Map.of("idProduto",Long.parseLong(id.toString()))));
                itens.put(produto,new BigDecimal(q.toString().replace(',','.')));
            });p.put("itens",itens);
        }
        return g.executarRota(op.rota(),p);
    }
    private static void renomear(Map<String,Object> p,String de,String para){if(p.containsKey(de)&&!p.containsKey(para))p.put(para,p.remove(de));}
    static Object dados(RespostaEndpoint<Object> resposta){if(resposta==null||!resposta.sucesso()||resposta.dados()==null)throw new IllegalArgumentException(resposta==null?"Sem resposta do núcleo.":resposta.mensagem());return resposta.dados();}
}
