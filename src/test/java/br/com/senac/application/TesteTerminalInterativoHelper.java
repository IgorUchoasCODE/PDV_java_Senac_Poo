package br.com.senac.application;

import br.com.senac.application.terminal.AnalisadorTextoTerminal;
import br.com.senac.application.terminal.CatalogoControladoresTerminal;
import br.com.senac.application.terminal.EditorTerminalInterativo;
import br.com.senac.application.terminal.EditorTerminalInterativo.EventoTecla;
import br.com.senac.application.terminal.EditorTerminalInterativo.TipoTecla;
import br.com.senac.application.terminal.FonteEntidadesTerminal;
import br.com.senac.application.terminal.FonteEntidadesTerminal.FormaPagamentoResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.NotaResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.PessoaResumo;
import br.com.senac.application.terminal.FonteEntidadesTerminal.ProdutoResumo;
import br.com.senac.application.terminal.FormatadorEntradaTerminal;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.LayoutVisual;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.ResultadoAnaliseTerminal;
import br.com.senac.application.terminal.ModelosAnaliseTerminal.TrechoDestacado;
import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.JPAUtil;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Validação da arquitetura SOLID do terminal interativo:
 * quebra automática por largura, identificação automática de fornecedor/produto/ID/registro/DSL,
 * dicas contextuais ao digitar, autocompletar TAB e integração com CatalogoControladoresTerminal.
 */
public class TesteTerminalInterativoHelper {

    public static void main(String[] args) {
        try {
            testarQuebraAutomaticaLarguraECursor();
            testarIdentificacaoEntidadesTextoNormal();
            testarIdentificacaoEDicasDsl();
            testarEditorSimuladoComTabEBackSpace();
            testarIntegracaoCatalogoControladores();
            System.out.println("✅ PASSOU: TesteTerminalInterativoHelper validado com sucesso.");
        } finally {
            JPAUtil.close();
        }
    }

    private static void testarQuebraAutomaticaLarguraECursor() {
        String frase = "comprar 10 unidades do produto Cafe Especial com fornecedor Alfa Distribuidora";
        LayoutVisual estreito = FormatadorEntradaTerminal.calcularLayoutVisual(frase, frase.length(), 48);
        LayoutVisual largo = FormatadorEntradaTerminal.calcularLayoutVisual(frase, frase.length(), 140);

        assertTrue(estreito.linhas().size() > 1, "Terminal estreito deve quebrar linha automaticamente");
        assertEquals(1, largo.linhas().size(), "Terminal largo deve manter em 1 linha");

        int larguraUtilEstreita = Math.max(20, 48 - FormatadorEntradaTerminal.LARGURA_PREFIXO - 2);
        for (int i = 0; i < estreito.linhas().size(); i++) {
            assertTrue(estreito.linhas().get(i).texto().length() <= larguraUtilEstreita,
                    "Linha quebrada não deve exceder a largura útil do terminal");
        }

        // Cursor no meio da frase deve mapear para linha/coluna consistentes
        int posMeio = 35;
        LayoutVisual cursorMeio = FormatadorEntradaTerminal.calcularLayoutVisual(frase, posMeio, 48);
        var linhaCursor = cursorMeio.linhas().get(cursorMeio.linhaCursor());
        int offsetNaLinha = cursorMeio.colunaCursor() - FormatadorEntradaTerminal.LARGURA_PREFIXO;
        assertEquals(posMeio, linhaCursor.inicioTexto() + offsetNaLinha,
                "Posição visual do cursor deve corresponder ao índice real no texto");

        // Coloração ANSI preserva exatamente o texto visível
        String colorido = FormatadorEntradaTerminal.colorirTrechoPreservandoLargura(
                "Cafe Especial",
                0,
                13,
                List.of(new TrechoDestacado(0, 13, "\u001B[33m", "PRODUTO", "PRD#1 Cafe Especial"))
        );
        String semAnsi = colorido.replaceAll("\u001B\\[[0-9;]*m", "");
        assertEquals("Cafe Especial", semAnsi, "Coloração ANSI não deve alterar o texto visível");
    }

    private static void testarIdentificacaoEntidadesTextoNormal() {
        AnalisadorTextoTerminal analisador = new AnalisadorTextoTerminal(criarFonteSimulada());

        // Fornecedor + Produto + ID + Registro em texto natural
        String texto = "comprar Cafe Especial do fornecedor Alfa Distribuidora id 15 registro 99887766 cpf 12345678901";
        ResultadoAnaliseTerminal res = analisador.analisar(texto, texto.length());

        assertTrue(!res.ehDsl(), "Texto natural não deve ser tratado como DSL $$");
        assertTrue(res.resumoIdentificacao().contains("Fornecedor"),
                "Deve identificar fornecedor no texto normal: " + res.resumoIdentificacao());
        assertTrue(res.resumoIdentificacao().contains("Cafe Especial"),
                "Deve identificar produto no texto normal: " + res.resumoIdentificacao());
        assertTrue(res.resumoIdentificacao().contains("99887766") || res.resumoIdentificacao().contains("RG"),
                "Deve identificar registro único no texto normal: " + res.resumoIdentificacao());
        assertTrue(res.resumoIdentificacao().contains("CPF"),
                "Deve identificar CPF válido/formato em texto normal: " + res.resumoIdentificacao());
        assertTrue(res.dicaLinha() != null && !res.dicaLinha().isBlank(),
                "Deve gerar dica contextual ao identificar entidades em texto normal");

        // Prefixo parcial de produto/fornecedor gera sugestão TAB
        String digitando = "vender cafe";
        ResultadoAnaliseTerminal resTab = analisador.analisar(digitando, digitando.length());
        assertTrue("Cafe Especial".equalsIgnoreCase(resTab.complementoTab()),
                "Deveria sugerir TAB para completar nome de produto real, obteve: " + resTab.complementoTab());
        assertTrue(resTab.dicaLinha().contains("TAB"),
                "Dica deve mencionar TAB quando houver complemento");

        // Contexto com #id explícito
        String porId = "consultar fornecedor #20";
        ResultadoAnaliseTerminal resId = analisador.analisar(porId, porId.length());
        assertTrue(resId.resumoIdentificacao().contains("FORN#20") || resId.resumoIdentificacao().contains("Alfa"),
                "Deveria resolver ID de fornecedor via FonteEntidadesTerminal: " + resId.resumoIdentificacao());
    }

    private static void testarIdentificacaoEDicasDsl() {
        AnalisadorTextoTerminal analisador = new AnalisadorTextoTerminal(criarFonteSimulada());

        String dslValida = "$$ #Produto[ nm>\"Cafe Especial\", vl>19.90, um>\"KG\" ]";
        ResultadoAnaliseTerminal resOk = analisador.analisar(dslValida, dslValida.length());
        assertTrue(resOk.ehDsl(), "Comando iniciado com $$ deve ser reconhecido como DSL");
        assertTrue(resOk.resumoIdentificacao().contains("DSL $$") && resOk.resumoIdentificacao().contains("#PRODUTO"),
                "DSL válida deve exibir resumo estruturado: " + resOk.resumoIdentificacao());

        String dslDigitandoEntidade = "$$ #Fornec";
        ResultadoAnaliseTerminal resTabEntidade = analisador.analisar(dslDigitandoEntidade, dslDigitandoEntidade.length());
        assertTrue(resTabEntidade.complementoTab() != null && resTabEntidade.complementoTab().startsWith("#Fornecedor"),
                "Deve sugerir TAB #Fornecedor[...] na DSL, obteve: " + resTabEntidade.complementoTab());
    }

    private static void testarEditorSimuladoComTabEBackSpace() {
        AnalisadorTextoTerminal analisador = new AnalisadorTextoTerminal(criarFonteSimulada());
        EditorTerminalInterativo editor = new EditorTerminalInterativo(analisador);

        Deque<EventoTecla> fila = new ArrayDeque<>();
        for (char c : ":trans".toCharArray()) {
            fila.add(new EventoTecla(TipoTecla.CHAR, c, false, 80));
        }
        fila.add(new EventoTecla(TipoTecla.TAB, '\0', false, 80));
        fila.add(new EventoTecla(TipoTecla.CHAR, 'x', false, 80));
        fila.add(new EventoTecla(TipoTecla.BACKSPACE, '\0', false, 80));
        for (char c : " voz.wav".toCharArray()) {
            fila.add(new EventoTecla(TipoTecla.CHAR, c, false, 80));
        }
        fila.add(new EventoTecla(TipoTecla.ENTER, '\0', false, 80));

        String resultado = editor.editar("", 80, fila::poll);
        assertEquals(":transcrever voz.wav", resultado,
                "Editor deve aplicar TAB, BACKSPACE e ENTER corretamente em modo interativo");
    }

    private static void testarIntegracaoCatalogoControladores() {
        RoteadorController roteador = new RoteadorController();
        CatalogoControladoresTerminal catalogo = new CatalogoControladoresTerminal(roteador);
        catalogo.recarregar();

        // Unidades padrão sempre são semeadas em inicializarDadosPadrao ou em listagens válidas
        assertTrue(catalogo.listarSiglasUm() != null, "Lista de siglas UM não deve ser nula");
        assertTrue(catalogo.listarFormasPagamento() != null, "Lista de formas de pagamento não deve ser nula");
    }

    private static FonteEntidadesTerminal criarFonteSimulada() {
        ProdutoResumo prodCafe = new ProdutoResumo(
                15L, "Cafe Especial", BuscaPalavrasParciaisUtil.normalizar("Cafe Especial"),
                new BigDecimal("24.50"), "KG", false);
        PessoaResumo fornAlfa = new PessoaResumo(
                20L, "Alfa Distribuidora", BuscaPalavrasParciaisUtil.normalizar("Alfa Distribuidora"),
                false, true, "12345678000195");
        PessoaResumo cliMaria = new PessoaResumo(
                21L, "Maria Souza", BuscaPalavrasParciaisUtil.normalizar("Maria Souza"),
                true, false, "12345678901");
        NotaResumo nota100 = new NotaResumo(100L, "COMPRA", new BigDecimal("500.00"));
        FormaPagamentoResumo pix = new FormaPagamentoResumo("PIX", "PIX Instantaneo", "pix instantaneo");

        return new FonteEntidadesTerminal() {
            @Override
            public void recarregar() {
            }

            @Override
            public List<ProdutoResumo> listarProdutos() {
                return List.of(prodCafe);
            }

            @Override
            public List<PessoaResumo> listarPessoas() {
                return List.of(fornAlfa, cliMaria);
            }

            @Override
            public List<NotaResumo> listarNotas() {
                return List.of(nota100);
            }

            @Override
            public List<String> listarSiglasUm() {
                return List.of("UN", "KG", "CX");
            }

            @Override
            public List<FormaPagamentoResumo> listarFormasPagamento() {
                return List.of(pix);
            }

            @Override
            public Optional<ProdutoResumo> buscarProdutoPorId(Long id) {
                return Long.valueOf(15L).equals(id) ? Optional.of(prodCafe) : Optional.empty();
            }

            @Override
            public Optional<PessoaResumo> buscarPessoaPorId(Long id) {
                if (Long.valueOf(20L).equals(id)) return Optional.of(fornAlfa);
                if (Long.valueOf(21L).equals(id)) return Optional.of(cliMaria);
                return Optional.empty();
            }

            @Override
            public Optional<NotaResumo> buscarNotaPorId(Long id) {
                return Long.valueOf(100L).equals(id) ? Optional.of(nota100) : Optional.empty();
            }

            @Override
            public Optional<PessoaResumo> buscarPessoaPorRegistro(String valorRegistro) {
                if ("99887766".equals(valorRegistro) || "12345678000195".equals(valorRegistro)) {
                    return Optional.of(fornAlfa);
                }
                if ("12345678901".equals(valorRegistro)) {
                    return Optional.of(cliMaria);
                }
                return Optional.empty();
            }
        };
    }

    private static void assertTrue(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError("Falha de asserção: " + mensagem);
        }
    }

    private static void assertEquals(Object esperado, Object atual, String mensagem) {
        if (!java.util.Objects.equals(esperado, atual)) {
            throw new AssertionError(mensagem + " | esperado=" + esperado + " | atual=" + atual);
        }
    }
}
