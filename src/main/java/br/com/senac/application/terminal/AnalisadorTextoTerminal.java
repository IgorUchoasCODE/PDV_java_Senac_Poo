package br.com.senac.application.terminal;

import br.com.senac.application.terminal.FonteEntidadesTerminal.FormaPagamentoResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.NotaResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.PessoaResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.ProdutoResumo;
import br.com.senac.application.terminal.GeradorDicasTerminal.DicaResultado;
import br.com.senac.application.terminal.IdentificadorEntidadeTerminal.ContextoIdentificacao;
import br.com.senac.application.terminal.IdentificadorEntidadeTerminal.TokenTexto;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.ResultadoAnaliseTerminal;
import br.com.senac.integracao.ia.CompiladorInstrucoesCurtaService;
import br.com.senac.integracao.ia.parser.ErroSintaxe;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.integracao.ia.parser.ParserInstrucao;
import br.com.senac.integracao.ia.parser.PreProcessadorMensagem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static br.com.senac.application.terminal.ModelosAnaliseTerminal.*;

/**
 * Orquestrador SOLID de análise léxica/semântica em tempo real para o terminal CLI.
 * Compõe identificadores especializados (OCP) alimentados por {@link FonteEntidadesTerminal} (DIP).
 */
public class AnalisadorTextoTerminal {

    private static final ParserInstrucao PARSER_INSTRUCAO = new ParserInstrucao();
    private static final PreProcessadorMensagem PRE_PROCESSADOR = new PreProcessadorMensagem();

    private final FonteEntidadesTerminal fonte;
    private final GeradorDicasTerminal geradorDicas;
    private final List<IdentificadorEntidadeTerminal> identificadoresTextoNormal;

    public AnalisadorTextoTerminal(FonteEntidadesTerminal fonte) {
        this(fonte, new GeradorDicasTerminal(), List.of(
                new IdentificadorRegistroDocumento(),
                new IdentificadorIdEntidade(),
                new IdentificadorCatalogoReal(),
                new IdentificadorPadroesEssenciais()
        ));
    }

    public AnalisadorTextoTerminal(
            FonteEntidadesTerminal fonte,
            GeradorDicasTerminal geradorDicas,
            List<IdentificadorEntidadeTerminal> identificadoresTextoNormal) {
        this.fonte = fonte;
        this.geradorDicas = geradorDicas;
        this.identificadoresTextoNormal = identificadoresTextoNormal;
    }

    public ResultadoAnaliseTerminal analisar(String texto, int cursor) {
        String safe = texto != null ? texto : "";
        String trim = safe.trim();
        int posCursor = Math.max(0, Math.min(cursor, safe.length()));

        if (trim.isEmpty()) {
            return new ResultadoAnaliseTerminal(
                    false, false, List.of(), List.of(), "",
                    "💡 Dica: digite nome de produto/fornecedor, ID, CPF/CNPJ, >sigla, $$ para DSL ou :ajuda.",
                    null, 0
            );
        }

        if (trim.startsWith(":")) {
            return analisarComando(safe, posCursor);
        }

        if (CompiladorInstrucoesCurtaService.ehComandoEncurtado(trim)) {
            return analisarDsl(safe, posCursor);
        }

        return analisarTextoNormal(safe, posCursor);
    }

    private ResultadoAnaliseTerminal analisarComando(String safe, int posCursor) {
        ContextoIdentificacao ctx = new ContextoIdentificacao(safe, fonte);
        int ini = 0;
        while (ini < safe.length() && Character.isWhitespace(safe.charAt(ini))) ini++;
        int fim = safe.indexOf(' ', ini);
        if (fim < 0) fim = safe.length();
        ctx.adicionar(ini, fim, NEGRITO + VERDE, "COMANDO", "CMD:" + safe.substring(ini, fim), null);

        DicaResultado dica = geradorDicas.gerarParaComando(safe, posCursor);
        return new ResultadoAnaliseTerminal(
                false, true, ctx.trechos(), List.of(), "",
                dica.dicaLinha(), dica.complementoTab(), dica.inicioSubstituicaoTab()
        );
    }

    private ResultadoAnaliseTerminal analisarDsl(String safe, int posCursor) {
        ContextoIdentificacao ctx = new ContextoIdentificacao(safe, fonte);
        int idxDolar = safe.indexOf("$$");
        if (idxDolar >= 0) {
            ctx.adicionar(idxDolar, idxDolar + 2, NEGRITO + ROXO, "DSL", "DSL:$$", null);
        }
        aplicarRegex(ctx, Pattern.compile("(?i)(?:\\b[CRUD]=|\\+\\+|--|abrir>janela>[^\\s;]*|close::)"), NEGRITO + AMARELO, "OP");
        aplicarRegex(ctx, Pattern.compile("#[a-zA-Z0-9_]+"), NEGRITO + CIANO, "ENTIDADE");
        aplicarRegex(ctx, Pattern.compile("(?i)\\b[a-zA-Z_]+>"), VERDE, "ATTR");

        // Também resolve IDs e registros reais citados dentro da instrução $$
        new IdentificadorRegistroDocumento().identificar(ctx);
        new IdentificadorIdEntidade().identificar(ctx);

        String resumo = "";
        try {
            List<NoInstrucao> nos = PARSER_INSTRUCAO.parsear(safe);
            if (!nos.isEmpty()) {
                List<String> partes = new ArrayList<>();
                for (NoInstrucao no : nos) {
                    partes.add(no.getOperacao().name() + "= #" + no.getEntidade()
                            + (no.getAtributos().isEmpty() ? "" : " " + no.getAtributos().keySet()));
                }
                resumo = "⚡ [DSL $$ Válida] " + String.join(" ; ", partes);
                if (!ctx.entidadesResumo().isEmpty()) {
                    resumo = resumo + " | " + String.join(" | ", ctx.entidadesResumo());
                }
            }
        } catch (ErroSintaxe e) {
            resumo = "⚠️ [DSL $$] " + e.getMessage();
        }

        DicaResultado dica = geradorDicas.gerarParaDsl(safe, posCursor, fonte);
        return new ResultadoAnaliseTerminal(
                true, false, ctx.trechos(), ctx.entidadesResumo(), resumo,
                dica.dicaLinha(), dica.complementoTab(), dica.inicioSubstituicaoTab()
        );
    }

    private ResultadoAnaliseTerminal analisarTextoNormal(String safe, int posCursor) {
        ContextoIdentificacao ctx = new ContextoIdentificacao(safe, fonte);
        for (IdentificadorEntidadeTerminal identificador : identificadoresTextoNormal) {
            identificador.identificar(ctx);
        }

        PreProcessadorMensagem.ResultadoPreProcessamento preProc = PRE_PROCESSADOR.processar(safe);
        for (PreProcessadorMensagem.EntidadeExtraida e : preProc.entidades()) {
            String cor = switch (e.tipo()) {
                case PRODUTO_NOMINADO, SIGLA_PRODUTO -> NEGRITO + CIANO;
                case PESSOA_NOMINADA, CPF, CNPJ -> NEGRITO + AZUL;
                case VALOR_MONETARIO -> VERDE_CLARO;
                case DATA -> AMARELO;
            };
            ctx.adicionar(e.inicio(), e.fim(), cor, e.tipo().name(),
                    e.tipo().name() + ":" + e.valorNormalizado(),
                    e.tipo().name() + "=\"" + e.valorNormalizado() + "\"");
        }

        DicaResultado dica = geradorDicas.gerarParaTextoNormal(safe, posCursor, ctx.chavesUnicas(), preProc, fonte);
        String resumo = ctx.entidadesResumo().isEmpty()
                ? ""
                : "🔎 Identificado: " + String.join(" | ", ctx.entidadesResumo());

        return new ResultadoAnaliseTerminal(
                false, false, ctx.trechos(), ctx.entidadesResumo(), resumo,
                dica.dicaLinha(), dica.complementoTab(), dica.inicioSubstituicaoTab()
        );
    }

    private static void aplicarRegex(ContextoIdentificacao ctx, Pattern padrao, String cor, String cat) {
        Matcher m = padrao.matcher(ctx.texto());
        while (m.find()) {
            ctx.adicionar(m.start(), m.end(), cor, cat, null, null);
        }
    }

    // ==================== ESTRATÉGIAS DE IDENTIFICAÇÃO (SRP / OCP) ====================

    /** Identifica registros (CPF, CNPJ, RG/chaves explícitas) e resolve o titular real no banco. */
    static final class IdentificadorRegistroDocumento implements IdentificadorEntidadeTerminal {
        private static final Pattern PADRAO_CPF_CNPJ = Pattern.compile(
                "\\b(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{14}|\\d{11})\\b"
        );
        private static final Pattern PADRAO_REGISTRO_EXPLICITO = Pattern.compile(
                "(?i)\\b(?:registro|rg)\\b\\s*(?:>|:\\s*|\\s+)\\s*([A-Za-z0-9._/@-]{4,60})"
        );

        @Override
        public void identificar(ContextoIdentificacao ctx) {
            Matcher mDoc = PADRAO_CPF_CNPJ.matcher(ctx.texto());
            while (mDoc.find()) {
                String raw = mDoc.group(1);
                String digitos = raw.replaceAll("[^0-9]", "");
                String tipoDoc = digitos.length() == 14 ? "CNPJ" : "CPF";
                Optional<PessoaResumo> titular = ctx.fonte() != null
                        ? ctx.fonte().buscarPessoaPorRegistro(raw)
                        : Optional.empty();
                String resumo = titular.isPresent()
                        ? titular.get().papelTexto() + "=\"" + titular.get().nome() + "\" (#" + titular.get().id() + ", " + tipoDoc + " " + raw + ")"
                        : "Registro(" + tipoDoc + ")=" + raw;
                ctx.adicionar(mDoc.start(), mDoc.end(), NEGRITO + AZUL, "REGISTRO", "REG:" + digitos, resumo);
            }

            Matcher mReg = PADRAO_REGISTRO_EXPLICITO.matcher(ctx.texto());
            while (mReg.find()) {
                String valor = mReg.group(1);
                Optional<PessoaResumo> titular = ctx.fonte() != null
                        ? ctx.fonte().buscarPessoaPorRegistro(valor)
                        : Optional.empty();
                String resumo = titular.isPresent()
                        ? titular.get().papelTexto() + "=\"" + titular.get().nome() + "\" (#" + titular.get().id() + ", Reg " + valor + ")"
                        : "Registro=" + valor;
                ctx.adicionar(mReg.start(), mReg.end(), NEGRITO + AZUL, "REGISTRO", "REG:" + valor.toLowerCase(Locale.ROOT), resumo);
            }
        }
    }

    /** Identifica IDs explícitos de entidades (ex.: "produto 12", "fornecedor #5", "nota 8", "#12", "id> 12"). */
    static final class IdentificadorIdEntidade implements IdentificadorEntidadeTerminal {
        private static final Pattern PADRAO_ENTIDADE_ID = Pattern.compile(
                "(?i)\\b(produto|fornecedor|cliente|pessoa|nota|estoque)\\b\\s*(?:#|id\\s*>?|n[úu]mero)?\\s*(\\d{1,9})\\b"
        );
        private static final Pattern PADRAO_HASH_ID = Pattern.compile("#(\\d{1,9})\\b");
        private static final Pattern PADRAO_ID_DSL = Pattern.compile("(?i)\\bid\\s*>\\s*(\\d{1,9})\\b");

        @Override
        public void identificar(ContextoIdentificacao ctx) {
            Matcher mEnt = PADRAO_ENTIDADE_ID.matcher(ctx.texto());
            while (mEnt.find()) {
                String tipo = mEnt.group(1).toLowerCase(Locale.ROOT);
                long id = Long.parseLong(mEnt.group(2));
                String resumo = resolverResumoPorTipoEId(ctx.fonte(), tipo, id);
                ctx.adicionar(mEnt.start(), mEnt.end(), NEGRITO + CIANO, "ID_ENTIDADE",
                        "ID:" + tipo + "#" + id, resumo);
            }

            Matcher mHash = PADRAO_HASH_ID.matcher(ctx.texto());
            while (mHash.find()) {
                long id = Long.parseLong(mHash.group(1));
                String resumo = resolverIdGenerico(ctx.fonte(), id);
                ctx.adicionar(mHash.start(), mHash.end(), NEGRITO + CIANO, "ID_ENTIDADE",
                        "ID:#" + id, resumo);
            }

            Matcher mIdDsl = PADRAO_ID_DSL.matcher(ctx.texto());
            while (mIdDsl.find()) {
                long id = Long.parseLong(mIdDsl.group(1));
                String resumo = resolverIdGenerico(ctx.fonte(), id);
                ctx.adicionar(mIdDsl.start(), mIdDsl.end(), NEGRITO + CIANO, "ID_ENTIDADE",
                        "ID:DSL#" + id, resumo);
            }
        }

        private static String resolverResumoPorTipoEId(FonteEntidadesTerminal fonte, String tipo, long id) {
            if (fonte == null) return tipo.toUpperCase(Locale.ROOT) + " #" + id;
            return switch (tipo) {
                case "produto" -> fonte.buscarProdutoPorId(id)
                        .map(p -> "Produto=#" + id + " \"" + p.nome() + "\"" + (p.preco() != null ? " (R$ " + p.preco() + ")" : ""))
                        .orElse("Produto=#" + id);
                case "fornecedor", "cliente", "pessoa" -> fonte.buscarPessoaPorId(id)
                        .map(p -> p.papelTexto() + "=#" + id + " \"" + p.nome() + "\"")
                        .orElse(tipo.substring(0, 1).toUpperCase(Locale.ROOT) + tipo.substring(1) + "=#" + id);
                case "nota" -> fonte.buscarNotaPorId(id)
                        .map(n -> n.tipoNota() + "=#" + id + (n.valorBruto() != null ? " (R$ " + n.valorBruto() + ")" : ""))
                        .orElse("Nota=#" + id);
                default -> tipo.toUpperCase(Locale.ROOT) + "=#" + id;
            };
        }

        private static String resolverIdGenerico(FonteEntidadesTerminal fonte, long id) {
            if (fonte != null) {
                Optional<ProdutoResumo> prod = fonte.buscarProdutoPorId(id);
                if (prod.isPresent()) return "ID #" + id + " (Produto: \"" + prod.get().nome() + "\")";
                Optional<PessoaResumo> pess = fonte.buscarPessoaPorId(id);
                if (pess.isPresent()) return "ID #" + id + " (" + pess.get().papelTexto() + ": \"" + pess.get().nome() + "\")";
                Optional<NotaResumo> nota = fonte.buscarNotaPorId(id);
                if (nota.isPresent()) return "ID #" + id + " (" + nota.get().tipoNota() + ")";
            }
            return "ID=#" + id;
        }
    }

    /** Identifica nomes reais de Produtos, Fornecedores, Clientes e Formas de Pagamento no texto normal. */
    static final class IdentificadorCatalogoReal implements IdentificadorEntidadeTerminal {
        private static final Set<String> STOPWORDS_ESSENCIAIS = Set.of(
                "a", "ao", "as", "com", "como", "da", "das", "de", "do", "dos",
                "e", "em", "eu", "me", "na", "no", "o", "os", "ou", "para",
                "por", "pra", "pro", "qual", "que", "quero", "se", "sem", "um", "uma"
        );

        @Override
        public void identificar(ContextoIdentificacao ctx) {
            if (ctx.fonte() == null) return;
            List<TokenTexto> tokens = ctx.tokens();
            List<ProdutoResumo> produtos = ctx.fonte().listarProdutos();
            List<PessoaResumo> pessoas = ctx.fonte().listarPessoas();
            List<FormaPagamentoResumo> formas = ctx.fonte().listarFormasPagamento();

            for (int i = 0; i < tokens.size(); i++) {
                TokenTexto t0 = tokens.get(i);
                if (t0.normalizado().length() < 2
                        || STOPWORDS_ESSENCIAIS.contains(t0.normalizado())
                        || IdentificadorPadroesEssenciais.ehVerboOuDominio(t0.normalizado())) {
                    continue;
                }

                // 1. Produtos reais (janela de até 4 palavras)
                boolean casou = false;
                for (int jan = Math.min(4, tokens.size() - i); jan >= 1; jan--) {
                    int ini = tokens.get(i).inicio();
                    int fim = tokens.get(i + jan - 1).fim();
                    if (ctx.sobrepoe(ini, fim)) continue;
                    String trecho = juntar(tokens, i, jan);
                    if (trecho.length() < 3) continue;

                    ProdutoResumo prod = buscarProduto(produtos, trecho);
                    if (prod != null) {
                        String preco = prod.preco() != null ? " · R$ " + prod.preco() : "";
                        ctx.adicionar(ini, fim, NEGRITO + CIANO, "PRODUTO",
                                "PROD#" + prod.id(), "Produto=\"" + prod.nome() + "\" (#" + prod.id() + preco + ")");
                        i += (jan - 1);
                        casou = true;
                        break;
                    }
                }
                if (casou) continue;

                // 2. Fornecedores e Clientes reais (janela de até 3 palavras)
                for (int jan = Math.min(3, tokens.size() - i); jan >= 1; jan--) {
                    int ini = tokens.get(i).inicio();
                    int fim = tokens.get(i + jan - 1).fim();
                    if (ctx.sobrepoe(ini, fim)) continue;
                    String trecho = juntar(tokens, i, jan);
                    if (trecho.length() < 3) continue;

                    PessoaResumo pess = buscarPessoa(pessoas, trecho);
                    if (pess != null) {
                        String cor = pess.fornecedor() && !pess.cliente() ? (NEGRITO + AMARELO) : (NEGRITO + AZUL);
                        ctx.adicionar(ini, fim, cor, pess.papelTexto().toUpperCase(Locale.ROOT),
                                "PESS#" + pess.id(), pess.papelTexto() + "=\"" + pess.nome() + "\" (#" + pess.id() + ")");
                        i += (jan - 1);
                        casou = true;
                        break;
                    }
                }
                if (casou) continue;

                // 3. Formas de pagamento cadastradas
                for (FormaPagamentoResumo fp : formas) {
                    if (t0.normalizado().equalsIgnoreCase(fp.codigo())
                            || (t0.normalizado().length() >= 3 && fp.nomeNormalizado().equals(t0.normalizado()))) {
                        ctx.adicionar(t0.inicio(), t0.fim(), VERDE, "PAGAMENTO",
                                "FP:" + fp.codigo(), "Pagamento=\"" + fp.nome() + "\"");
                        break;
                    }
                }
            }
        }

        private static ProdutoResumo buscarProduto(List<ProdutoResumo> lista, String termoNorm) {
            ProdutoResumo melhor = null;
            int melhorScore = 0;
            for (ProdutoResumo p : lista) {
                if (p.nomeNormalizado().equals(termoNorm)) return p;
                if (termoNorm.length() >= 3 && p.nomeNormalizado().contains(termoNorm)) {
                    int score = p.nomeNormalizado().startsWith(termoNorm) ? 900 : 800;
                    if (score > melhorScore) {
                        melhorScore = score;
                        melhor = p;
                    }
                }
            }
            return melhor;
        }

        private static PessoaResumo buscarPessoa(List<PessoaResumo> lista, String termoNorm) {
            for (PessoaResumo p : lista) {
                if (p.nomeNormalizado().equals(termoNorm)) return p;
                for (String parte : p.nomeNormalizado().split("\\s+")) {
                    if (parte.equals(termoNorm)) return p;
                }
                if (termoNorm.length() >= 4 && p.nomeNormalizado().startsWith(termoNorm)) return p;
            }
            return null;
        }

        private static String juntar(List<TokenTexto> tokens, int ini, int qtd) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < qtd; k++) {
                if (k > 0) sb.append(' ');
                sb.append(tokens.get(ini + k).normalizado());
            }
            return sb.toString();
        }
    }

    /** Identifica padrões essenciais que não vêm de tabela: valores, datas, siglas, UM, números e ações. */
    static final class IdentificadorPadroesEssenciais implements IdentificadorEntidadeTerminal {
        private static final Pattern PADRAO_VALOR = Pattern.compile("(?i)(?:R\\$\\s*\\d+(?:[.,]\\d{1,2})?|\\b\\d+(?:[.,]\\d{1,2})?\\s*reais\\b)");
        private static final Pattern PADRAO_DATA = Pattern.compile("(?i)\\b(?:\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|hoje|ontem|amanh[aã])\\b");
        private static final Pattern PADRAO_SIGLA = Pattern.compile("(?:>[a-zA-Z0-9_]{2,8}|\\b[a-zA-Z](?:\\.[a-zA-Z]){1,5}\\b)");
        private static final Pattern PADRAO_NUMERO = Pattern.compile("\\b\\d+(?:[.,]\\d{1,3})?\\b");

        private static final Map<String, String> VERBOS_ACAO = Map.ofEntries(
                Map.entry("cadastrar", "CADASTRO"), Map.entry("criar", "CADASTRO"),
                Map.entry("vender", "VENDA"), Map.entry("venda", "VENDA"),
                Map.entry("comprar", "COMPRA"), Map.entry("compra", "COMPRA"),
                Map.entry("emitir", "EMITIR_NOTA"), Map.entry("devolver", "DEVOLUCAO"),
                Map.entry("perda", "PERDA"), Map.entry("baixar", "BAIXA_ESTOQUE"),
                Map.entry("listar", "LISTAGEM"), Map.entry("buscar", "BUSCA"),
                Map.entry("procurar", "BUSCA"), Map.entry("consultar", "CONSULTA"),
                Map.entry("alterar", "ATUALIZACAO"), Map.entry("atualizar", "ATUALIZACAO"),
                Map.entry("excluir", "EXCLUSAO"), Map.entry("apagar", "EXCLUSAO"),
                Map.entry("pagar", "PAGAMENTO"), Map.entry("quitar", "QUITACAO")
        );

        private static final Set<String> TERMOS_DOMINIO = Set.of(
                "produto", "produtos", "kit", "conjunto", "cliente", "clientes",
                "fornecedor", "fornecedores", "pessoa", "nota", "notas",
                "estoque", "lote", "pagamento", "parcela", "lucro", "relatorio"
        );

        static boolean ehVerboOuDominio(String norm) {
            return VERBOS_ACAO.containsKey(norm) || TERMOS_DOMINIO.contains(norm);
        }

        @Override
        public void identificar(ContextoIdentificacao ctx) {
            Matcher mVal = PADRAO_VALOR.matcher(ctx.texto());
            while (mVal.find()) {
                ctx.adicionar(mVal.start(), mVal.end(), VERDE_CLARO, "VALOR", "VALOR:" + mVal.group(), "Valor=" + mVal.group());
            }
            Matcher mData = PADRAO_DATA.matcher(ctx.texto());
            while (mData.find()) {
                ctx.adicionar(mData.start(), mData.end(), AMARELO, "DATA", "DATA:" + mData.group(), "Data=" + mData.group());
            }
            Matcher mSigla = PADRAO_SIGLA.matcher(ctx.texto());
            while (mSigla.find()) {
                ctx.adicionar(mSigla.start(), mSigla.end(), NEGRITO + CIANO, "SIGLA", "SIGLA:" + mSigla.group(), "Sigla=" + mSigla.group());
            }

            List<String> ums = ctx.fonte() != null ? ctx.fonte().listarSiglasUm() : List.of();
            for (TokenTexto t : ctx.tokens()) {
                if (ctx.sobrepoe(t.inicio(), t.fim())) continue;
                String norm = t.normalizado();
                if (VERBOS_ACAO.containsKey(norm)) {
                    String acao = VERBOS_ACAO.get(norm);
                    ctx.adicionar(t.inicio(), t.fim(), NEGRITO + AMARELO, "ACAO", "ACAO:" + acao, "Ação=" + acao);
                } else if (TERMOS_DOMINIO.contains(norm)) {
                    ctx.adicionar(t.inicio(), t.fim(), ROXO, "DOMINIO", "DOM:" + norm, "Entidade=" + norm.toUpperCase(Locale.ROOT));
                } else if (ums.contains(norm.toUpperCase(Locale.ROOT))) {
                    ctx.adicionar(t.inicio(), t.fim(), VERDE, "UM", "UM:" + norm, "UM=" + norm.toUpperCase(Locale.ROOT));
                } else if (PADRAO_NUMERO.matcher(t.original()).matches()) {
                    ctx.adicionar(t.inicio(), t.fim(), VERDE_CLARO, "NUM", "NUM:" + t.original(), "Num=" + t.original());
                }
            }
        }
    }
}
