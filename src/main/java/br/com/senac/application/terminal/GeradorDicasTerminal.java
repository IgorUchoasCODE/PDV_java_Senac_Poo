package br.com.senac.application.terminal;

import br.com.senac.application.terminal.FonteEntidadesTerminal.PessoaResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.ProdutoResumo;
import br.com.senac.integracao.ia.parser.ParserInstrucao;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsabilidade única (SRP): gerar dicas contextuais e sugestões {@code TAB}
 * com base em entidades reais carregadas da {@link FonteEntidadesTerminal} e nos metadados oficiais do projeto.
 */
public final class GeradorDicasTerminal {

    /** Comandos CLI essenciais do próprio terminal. */
    public static final List<String> COMANDOS_ESSENCIAIS = List.of(
            ":ajuda", ":voz", ":transcrever", ":audio", ":imagem",
            ":analise", ":bandeja", ":simular", ":fechar", ":logs",
            ":rotas", ":persona", ":manual", ":limpar", ":sair"
    );

    /** Atributos DSL mínimos por família de entidade (não há tabela de atributos no banco). */
    private static final Map<String, List<String>> ATRIBUTOS_DSL_ESSENCIAIS = Map.ofEntries(
            Map.entry("PRODUTO", List.of("nm>", "vl>", "um>", "desc>", "id>", "sigla>")),
            Map.entry("PRODUTOFISICO", List.of("nm>", "vl>", "um>", "alt>", "larg>", "comp>", "pb>", "pl>")),
            Map.entry("CONJUNTOPRODUTO", List.of("nm>", "vl>", "um>", "[ ++ #Componente[ nm> , q> ] ]")),
            Map.entry("UM", List.of("sigla>", "nm>", "desc>", "base>")),
            Map.entry("PESSOA", List.of("nm>", "cpf>", "cnpj>", "rg>", "papel>", "limite>", "id>")),
            Map.entry("CLIENTE", List.of("nm>", "cpf>", "cnpj>", "limite>", "id>")),
            Map.entry("FORNECEDOR", List.of("nm>", "cpf>", "cnpj>", "rg>", "id>")),
            Map.entry("NOTA", List.of("tipo>", "pessoa>", "itens> [...]", "pg>", "id>")),
            Map.entry("NOTAVENDA", List.of("pessoa>", "itens> [...]", "pg>")),
            Map.entry("NOTACOMPRA", List.of("pessoa>", "itens> [...]", "pg>")),
            Map.entry("NOTAPERDA", List.of("itens> [...]", "motivo>")),
            Map.entry("NOTADEVOLUCAO", List.of("nota>", "itens> [...]", "motivo>")),
            Map.entry("ESTOQUE", List.of("prod>", "q>", "validade>", "id>")),
            Map.entry("PAGAMENTO", List.of("nota>", "forma>", "vl>", "parcelas>"))
    );

    private static final Pattern PADRAO_ENTIDADE_DSL = Pattern.compile("#[a-zA-Z0-9_]+");

    public record DicaResultado(String dicaLinha, String complementoTab, int inicioSubstituicaoTab) {}

    public DicaResultado gerarParaComando(String texto, int cursor) {
        int ini = 0;
        while (ini < texto.length() && Character.isWhitespace(texto.charAt(ini))) ini++;
        int fim = texto.indexOf(' ', ini);
        if (fim < 0) fim = texto.length();

        String cmdDigitado = texto.substring(ini, Math.min(cursor, fim)).toLowerCase(Locale.ROOT);
        List<String> matches = COMANDOS_ESSENCIAIS.stream().filter(c -> c.startsWith(cmdDigitado)).toList();

        if (cursor <= fim && !matches.isEmpty()) {
            String primeiro = matches.get(0);
            String tab = !primeiro.equals(cmdDigitado)
                    ? primeiro + (precisaArgumento(primeiro) ? " " : "")
                    : null;
            String dica = "💡 Comandos: " + String.join(" | ", matches)
                    + (tab != null ? "  [TAB → " + tab.trim() + "]" : "");
            return new DicaResultado(dica, tab, ini);
        }

        String cmdFull = texto.substring(ini, fim).toLowerCase(Locale.ROOT);
        String dica = switch (cmdFull) {
            case ":voz", ":mic", ":gravar" -> "💡 :voz — grava áudio do microfone e envia direto para a IA.";
            case ":transcrever" -> "💡 :transcrever [arquivo] — transcreve e permite enviar (S), editar (E) ou descartar (N).";
            case ":audio" -> "💡 :audio <caminho> — envia .wav, .mp3, .ogg, .m4a ou .webm para execução.";
            case ":imagem" -> "💡 :imagem <caminho> — analisa documento/foto (.png, .jpg, .webp).";
            case ":analise", ":inspecao" -> "💡 :analise [on|off] — alterna a identificação automática de entidades e DSL $$.";
            case ":logs" -> "💡 :logs [on|off|ver] — controla e exibe o log detalhado do fluxo.";
            case ":persona" -> "💡 :persona <tom> — define a personalidade de resposta da IA.";
            default -> "💡 Pressione ENTER para executar " + cmdFull + " ou :ajuda para listar comandos.";
        };
        return new DicaResultado(dica, null, ini);
    }

    public DicaResultado gerarParaDsl(String texto, int cursor, FonteEntidadesTerminal fonte) {
        PalavraAtual palavra = extrairPalavra(texto, cursor);
        String antesCursor = texto.substring(0, Math.max(0, Math.min(cursor, texto.length())));
        String ultimaEntidade = extrairUltimaEntidade(antesCursor);

        String tab = null;
        int inicioTab = palavra.inicio();
        String dica;

        if (palavra.texto().startsWith("#")) {
            String pref = palavra.texto().substring(1).toUpperCase(Locale.ROOT);
            List<String> validas = ParserInstrucao.ENTIDADES_VALIDAS.stream()
                    .filter(e -> e.startsWith(pref))
                    .sorted()
                    .limit(6)
                    .toList();
            if (!validas.isEmpty()) {
                String sugestao = "#" + formatarNomeEntidadeDsl(validas.get(0));
                tab = sugestao + ", ";
                dica = "💡 Entidades DSL: " + validas.stream().map(e -> "#" + formatarNomeEntidadeDsl(e)).toList()
                        + "  [TAB → " + sugestao + "]";
            } else {
                dica = "💡 Informe uma entidade válida após '#' (ex.: #PRODUTO, #FORNECEDOR, #NOTA).";
            }
        } else if (ultimaEntidade != null) {
            List<String> attrs = ATRIBUTOS_DSL_ESSENCIAIS.getOrDefault(
                    ultimaEntidade.toUpperCase(Locale.ROOT), List.of("nm>", "id>", "vl>"));
            String pref = palavra.texto().toLowerCase(Locale.ROOT);
            if (!pref.isEmpty()) {
                for (String a : attrs) {
                    if (a.toLowerCase(Locale.ROOT).startsWith(pref) && !a.equalsIgnoreCase(pref)) {
                        tab = a + " ";
                        break;
                    }
                }
            } else {
                tab = attrs.get(0) + " ";
            }
            // Se estiver após nm> ou prod> ou pessoa>, oferece autocompletar real do catálogo!
            String sugestaoReal = sugerirValorRealNaDsl(antesCursor, palavra, fonte);
            if (sugestaoReal != null) {
                tab = sugestaoReal;
                dica = "💡 Atributos #" + ultimaEntidade + ": " + String.join(" ", attrs)
                        + "  [TAB → \"" + sugestaoReal + "\"]";
            } else {
                dica = "💡 Atributos #" + ultimaEntidade + ": " + String.join(" ", attrs)
                        + (tab != null ? "  [TAB → " + tab.trim() + "]" : "");
            }
        } else {
            int idxDolar = texto.indexOf("$$");
            String apos = idxDolar >= 0 ? texto.substring(idxDolar + 2).trim() : texto.trim();
            if (apos.isEmpty() || apos.equalsIgnoreCase("c") || apos.equalsIgnoreCase("r")
                    || apos.equalsIgnoreCase("u") || apos.equalsIgnoreCase("d")) {
                tab = apos.isEmpty() ? "R= " : apos.toUpperCase(Locale.ROOT) + "= ";
                inicioTab = apos.isEmpty() ? cursor : palavra.inicio();
                dica = "💡 Operações DSL $$: C= | R= | U= | D= | abrir>janela> | close::  [TAB → " + tab.trim() + "]";
            } else {
                tab = "#Produto, ";
                inicioTab = cursor;
                dica = "💡 Informe a entidade com # (ex.: #Produto, #Fornecedor, #Nota)  [TAB → #Produto]";
            }
        }

        int abertos = contarColchetesAbertos(texto);
        if (abertos > 0) {
            dica = dica + "  [" + abertos + " '[' aberto(s)]";
        }
        return new DicaResultado(dica, tab, inicioTab);
    }

    public DicaResultado gerarParaTextoNormal(
            String texto,
            int cursor,
            Set<String> chavesIdentificadas,
            PreProcessadorMensagem.ResultadoPreProcessamento preProc,
            FonteEntidadesTerminal fonte) {
        PalavraAtual palavra = extrairPalavra(texto, cursor);
        SugestaoReal sugestao = sugerirEntidadeReal(palavra.texto(), fonte);

        if (sugestao != null) {
            return new DicaResultado(
                    "💡 TAB completa " + sugestao.rotulo() + ": \"" + sugestao.valor() + "\"",
                    sugestao.valor(),
                    palavra.inicio()
            );
        }

        boolean temVenda = chavesIdentificadas.contains("ACAO:VENDA");
        boolean temCompra = chavesIdentificadas.contains("ACAO:COMPRA");
        boolean temCadastro = chavesIdentificadas.contains("ACAO:CADASTRO");
        boolean temProd = chavesIdentificadas.stream().anyMatch(k -> k.startsWith("PROD#") || k.startsWith("PRODUTO_NOMINADO"));
        boolean temPess = chavesIdentificadas.stream().anyMatch(k -> k.startsWith("PESS#") || k.startsWith("PESSOA_NOMINADA") || k.startsWith("REG:"));
        boolean temNum = chavesIdentificadas.stream().anyMatch(k -> k.startsWith("NUM:") || k.startsWith("VALOR"));

        String dica;
        if (temVenda && (!temProd || !temNum)) {
            dica = "💡 Dica [Venda]: informe produto, quantidade e cliente (ou CPF/ID).";
        } else if (temCompra && (!temProd || !temPess)) {
            dica = "💡 Dica [Compra]: informe fornecedor (nome, CNPJ ou ID), produto, quantidade e valor.";
        } else if (temCadastro && !temNum) {
            dica = "💡 Dica [Cadastro]: informe nome, valor/limite e unidade ou documento (CPF/CNPJ).";
        } else if (preProc.rotasSugeridas() != null && !preProc.rotasSugeridas().isEmpty()) {
            dica = "💡 Rotas relacionadas: " + String.join(", ", preProc.rotasSugeridas().stream().limit(3).toList());
        } else if (BuscaPalavrasParciaisUtil.isFormatoSiglaExplicita(texto.trim())) {
            dica = "💡 Atalho de sigla detectado — ENTER consulta diretamente no catálogo.";
        } else {
            dica = "💡 Dica: digite nome de produto/fornecedor, ID, CPF/CNPJ, >sigla ou $$ para DSL.";
        }
        return new DicaResultado(dica, null, palavra.inicio());
    }

    private record SugestaoReal(String valor, String rotulo) {}

    private SugestaoReal sugerirEntidadeReal(String termo, FonteEntidadesTerminal fonte) {
        if (fonte == null) return null;
        String norm = BuscaPalavrasParciaisUtil.normalizar(termo);
        if (norm.length() < 2) return null;

        for (ProdutoResumo p : fonte.listarProdutos()) {
            if (!p.nomeNormalizado().equals(norm) && p.nomeNormalizado().startsWith(norm)) {
                return new SugestaoReal(p.nome(), "Produto #" + p.id());
            }
        }
        for (PessoaResumo pe : fonte.listarPessoas()) {
            if (!pe.nomeNormalizado().equals(norm) && pe.nomeNormalizado().startsWith(norm)) {
                return new SugestaoReal(pe.nome(), pe.papelTexto() + " #" + pe.id());
            }
        }
        return null;
    }

    private String sugerirValorRealNaDsl(String antesCursor, PalavraAtual palavra, FonteEntidadesTerminal fonte) {
        if (fonte == null || palavra.texto().isBlank()) return null;
        String lower = antesCursor.toLowerCase(Locale.ROOT);
        if (lower.endsWith("nm> " + palavra.texto().toLowerCase(Locale.ROOT))
                || lower.endsWith("prod> " + palavra.texto().toLowerCase(Locale.ROOT))
                || lower.endsWith("pessoa> " + palavra.texto().toLowerCase(Locale.ROOT))) {
            SugestaoReal s = sugerirEntidadeReal(palavra.texto(), fonte);
            return s != null ? s.valor() : null;
        }
        return null;
    }

    private static boolean precisaArgumento(String cmd) {
        return cmd.equals(":audio") || cmd.equals(":imagem") || cmd.equals(":persona")
                || cmd.equals(":logs") || cmd.equals(":analise");
    }

    private static String extrairUltimaEntidade(String texto) {
        Matcher m = PADRAO_ENTIDADE_DSL.matcher(texto);
        String ultima = null;
        while (m.find()) {
            ultima = m.group().substring(1);
        }
        return ultima;
    }

    private static String formatarNomeEntidadeDsl(String upper) {
        if (upper.equals("UM")) return "UM";
        return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
    }

    private record PalavraAtual(String texto, int inicio, int fim) {}

    private static PalavraAtual extrairPalavra(String texto, int cursor) {
        int pos = Math.max(0, Math.min(cursor, texto.length()));
        int ini = pos;
        while (ini > 0 && !Character.isWhitespace(texto.charAt(ini - 1)) && texto.charAt(ini - 1) != ';') {
            ini--;
        }
        return new PalavraAtual(texto.substring(ini, pos), ini, pos);
    }

    private static int contarColchetesAbertos(String texto) {
        int saldo = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '[') saldo++;
            else if (c == ']') saldo--;
        }
        return saldo;
    }
}
