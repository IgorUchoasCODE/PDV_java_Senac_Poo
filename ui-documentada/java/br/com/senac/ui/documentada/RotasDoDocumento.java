package br.com.senac.ui.documentada;

import br.com.senac.controller.RotaSistema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;

/** Catálogo extraído do HTML fornecido, sem depender do registry interno de IA. */
public final class RotasDoDocumento {
    public record Campo(String nome,String tipo) {}
    public record Operacao(String id,String metodo,String caminho,String descricao,String parametros,List<Campo> campos) {
        public RotaSistema rota() {return Arrays.stream(RotaSistema.values()).filter(r->r.getCaminhoRota().equals(caminho)).findFirst().orElseThrow(()->new IllegalArgumentException("Rota documentada não encontrada neste núcleo: "+caminho));}
        public String modulo() {return rota().getModulo();}
        public String toString() {return caminho;}
        public boolean restrita() {return caminho.equals("/sistema/reset-restrito");}
        public boolean altera() {return !metodo.equals("GET") && !id.startsWith("PAGAMENTO_CRIAR_");}
    }
    private final List<Operacao> operacoes;
    public RotasDoDocumento() {
        try(var stream=getClass().getResourceAsStream("/rotas-documentadas.json")) {
            operacoes=new ObjectMapper().readValue(stream,new TypeReference<List<Operacao>>(){});
            for(Operacao o:operacoes)o.rota();
            if(operacoes.size()!=85)throw new IllegalStateException("O documento não contém as 85 rotas esperadas.");
        }catch(Exception e){throw new IllegalStateException("Falha no catálogo documental: "+e.getMessage(),e);}
    }
    public List<Operacao> todas(){return List.copyOf(operacoes);}
}
