package br.com.senac.ui.documentada;

import br.com.senac.controller.*;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.*;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Adaptador da UI: chat e bandeja compartilham a mesma sessão oficial do núcleo. */
public final class GatewayDaSessao implements SistemaGateway {
    private final SistemaGateway origem;
    private final String sessao = "ui-documentada-" + UUID.randomUUID();
    private volatile Runnable aoConversar = () -> {};
    public GatewayDaSessao(SistemaGateway origem) { this.origem = origem; }
    public void aoConversar(Runnable listener) { aoConversar = listener; }
    public RespostaEndpoint<Object> executarRota(RotaSistema r, Map<String,Object> p) {
        return origem.executarRota(r, new LinkedHashMap<>(p == null ? Map.of() : p));
    }
    public String perguntarAoChat(String s, String m) { return perguntarAoChatComProgresso(s,m,null); }
    public String perguntarAoChatComProgresso(String s,String m,Consumer<String> progresso) {
        try { return origem.perguntarAoChatComProgresso(sessao,m,progresso); } finally { aoConversar.run(); }
    }
    public String perguntarPorVoz(String s,byte[] b,String t) {
        try { return origem.perguntarPorVoz(sessao,b,t); } finally { aoConversar.run(); }
    }
    public String perguntarComImagem(String s,byte[] b,String t,String p) {
        try { return origem.perguntarComImagem(sessao,b,t,p); } finally { aoConversar.run(); }
    }
    public String executarComandoTerminalChat(String s,String c) { return origem.executarComandoTerminalChat(sessao,c); }
    public String obterProvedorIaAtivo() { return origem.obterProvedorIaAtivo(); }
    public void executarEmBackground(Runnable r,Consumer<Throwable> e) { origem.executarEmBackground(r,e); }
    public TabelaCsv lerCsv(Path p,Character d) throws IOException { return origem.lerCsv(p,d); }
    public TabelaCsv lerCsvTexto(String s,Character d) throws IOException { return origem.lerCsvTexto(s,d); }
    public ResultadoImportacaoCsv importarCsvMapeado(String o,TabelaCsv t,TipoImportacaoCsv tipo,Map<String,String> m,boolean p) {
        return origem.importarCsvMapeado(o,t,tipo,m,p);
    }
    public Properties carregarPropriedades(String p) { return origem.carregarPropriedades(p); }
    public void salvarPropriedades(String p,Properties v,String c) throws IOException { origem.salvarPropriedades(p,v,c); }
    public void recarregarConfiguracoesIA() { origem.recarregarConfiguracoesIA(); }
}
