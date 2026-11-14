package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.csv.ImportadorCsvService;
import br.com.senac.integracao.csv.LeitorCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.integracao.gemini.GeminiToolRegistry;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.pessoa.PessoaDAO;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;
import br.com.senac.service.InicializadorSistemaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Valida:
 * 1. Leitura CSV (RFC-4180, BOM UTF-8, delimitador ';' e ',').
 * 2. Importação de dados reais via rotas do RoteadorController (Pessoas, Produtos, Compras e Vendas).
 * 3. Funcionalidade restrita de reset do banco de dados (bloqueio sem token + execução isolada em H2 mantendo apenas dados fundamentais).
 */
public class TesteImportadorCsvEReset {

    private static int aprovados = 0;
    private static int falhas = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================================================");
        System.out.println("   TESTE DO IMPORTADOR CSV VIA CONTROLLERS & RESET RESTRITO DO BANCO");
        System.out.println("==========================================================================================\n");

        try {
            testarLeitorCsv();
            testarImportacaoCsvViaRotas();
            testarResetRestritoBloqueioEExecucaoIsolada();
            testarRotaResetOcultaParaGemini();
        } catch (Exception e) {
            falhas++;
            e.printStackTrace();
        }

        System.out.println("\n==========================================================================================");
        System.out.printf(" RESULTADO CSV & RESET: %d aprovados | %d falhas%n", aprovados, falhas);
        System.out.println("==========================================================================================");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    private static void testarLeitorCsv() throws Exception {
        LeitorCsv leitor = new LeitorCsv();
        String csvComBom = "\uFEFFnome;precoVenda;descricao\n" +
                "\"Café \"\"Especial\"\" 500g\";18,90;Torra média\n" +
                "Açúcar Mascavo;12,50;Orgânico\n";

        LeitorCsv.TabelaCsv tabela = leitor.lerTexto(csvComBom);
        validar(tabela.delimitadorDetectado() == ';', "LeitorCsv detectou delimitador ';'");
        validar(tabela.cabecalhos().size() == 3, "LeitorCsv leu 3 cabeçalhos");
        validar(tabela.linhas().size() == 2, "LeitorCsv leu 2 linhas");
        validar("Café \"Especial\" 500g".equals(tabela.linhas().get(0).get("nome")),
                "LeitorCsv preservou aspas escapadas");
    }

    private static void testarImportacaoCsvViaRotas() throws Exception {
        RoteadorController roteador = new RoteadorController();
        roteador.inicializarSistemaCompleto();
        ImportadorCsvService importador = new ImportadorCsvService(roteador);

        String sufixo = String.valueOf(System.currentTimeMillis() % 100000);
        String nomeFornecedor = "Fornecedor CSV " + sufixo;
        String nomeCliente = "Cliente CSV " + sufixo;
        String nomeProduto = "Produto CSV " + sufixo;
        String cpfCliente = gerarCpfSimples(sufixo);
        String cnpjForn = gerarCnpjSimples(sufixo);

        Path tempDir = Files.createTempDirectory("pdv_csv_teste_");
        try {
            // 1. Importar Pessoas (Fornecedor PJ + Cliente PF)
            Path csvPessoas = tempDir.resolve("pessoas.csv");
            Files.writeString(csvPessoas,
                    "nome;papel;documento;limite\n" +
                    nomeFornecedor + ";FORNECEDOR;" + cnpjForn + ";0,00\n" +
                    nomeCliente + ";CLIENTE;" + cpfCliente + ";500,00\n",
                    StandardCharsets.UTF_8);

            ResultadoImportacaoCsv resPessoas = importador.importar(csvPessoas, TipoImportacaoCsv.PESSOAS);
            validar(resPessoas.semErros() && resPessoas.sucessos() == 2,
                    "Importação CSV de Pessoas via rotas: " + resPessoas.formatarResumo());

            // 2. Importar Produto Físico
            Path csvProdutos = tempDir.resolve("produtos.csv");
            Files.writeString(csvProdutos,
                    "nome,descricao,precoVenda,siglaUm,alturaCm,larguraCm,comprimentoCm,pesoBrutoKg,pesoLiquidoKg\n" +
                    nomeProduto + ",Item importado via CSV,25.00,UN,10,10,10,0.5,0.45\n",
                    StandardCharsets.UTF_8);

            ResultadoImportacaoCsv resProdutos = importador.importar(csvProdutos, TipoImportacaoCsv.PRODUTOS_FISICOS);
            validar(resProdutos.semErros() && resProdutos.sucessos() == 1,
                    "Importação CSV de Produtos Físicos via rotas: " + resProdutos.formatarResumo());

            // 3. Importar Nota de Compra (gera lote FIFO)
            Path csvCompras = tempDir.resolve("compras.csv");
            Files.writeString(csvCompras,
                    "nomeFornecedor;nomeProduto;quantidade;valorUnitario;codigoForma;descricao\n" +
                    nomeFornecedor + ";" + nomeProduto + ";10;12,00;BOLETO;Compra CSV " + sufixo + "\n",
                    StandardCharsets.UTF_8);

            ResultadoImportacaoCsv resCompras = importador.importar(csvCompras, TipoImportacaoCsv.NOTAS_COMPRA);
            validar(resCompras.semErros() && resCompras.sucessos() == 1,
                    "Importação CSV de Notas de Compra via rotas: " + resCompras.formatarResumo());

            // 4. Importar Nota de Venda FIFO (para cliente e para balcão padrão)
            Path csvVendas = tempDir.resolve("vendas.csv");
            Files.writeString(csvVendas,
                    "nomeCliente;nomeProduto;quantidade;codigoForma;descricao\n" +
                    nomeCliente + ";" + nomeProduto + ";2;PIX;Venda CSV Cliente " + sufixo + "\n" +
                    "PADRAO;" + nomeProduto + ";1;DINHEIRO;Venda CSV Balcão " + sufixo + "\n",
                    StandardCharsets.UTF_8);

            ResultadoImportacaoCsv resVendas = importador.importar(csvVendas, TipoImportacaoCsv.NOTAS_VENDA);
            validar(resVendas.semErros() && resVendas.sucessos() == 2,
                    "Importação CSV de Notas de Venda via rotas: " + resVendas.formatarResumo());

            // 5. Conferir saldo restante (10 - 2 - 1 = 7) via rota de estoque
            Produto prod = roteador.produtos().buscarPorNome(nomeProduto);
            validar(prod != null, "Produto importado encontrado no catálogo");
            if (prod != null) {
                RespostaEndpoint<Object> respSaldo = roteador.executarEndpoint(
                        RotaSistema.ESTOQUE_SALDO_CONSOLIDADO,
                        Map.of("idProduto", prod.getId())
                );
                validar(respSaldo.sucesso(), "Consulta de saldo consolidado pós-CSV OK");
            }
        } finally {
            // Limpeza dos arquivos temporários
            try (var stream = Files.walk(tempDir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    private static void testarResetRestritoBloqueioEExecucaoIsolada() {
        RoteadorController roteador = new RoteadorController();
        RespostaEndpoint<Object> respBloqueada = roteador.executarEndpoint(
                RotaSistema.SISTEMA_RESETAR_BANCO_RESTRITO,
                Map.of("confirmacao", "SENHA_ERRADA")
        );
        validar(!respBloqueada.sucesso(), "Rota /sistema/reset-restrito bloqueou token inválido");

        InicializadorSistemaService initService = new InicializadorSistemaService();
        boolean bloqueouExcecao = false;
        try {
            initService.resetarBancoParaDadosFundamentais("ERRADO");
        } catch (SecurityException se) {
            bloqueouExcecao = true;
        }
        validar(bloqueouExcecao, "InicializadorSistemaService lançou SecurityException para token inválido");

        // Testa o reset completo em um banco H2 embarcado isolado (sem tocar no MySQL principal)
        try (EntityManagerFactory emfH2 = Persistence.createEntityManagerFactory("sazonalisei-embarcado");
             EntityManager emH2 = emfH2.createEntityManager()) {

            // Garante estado inicial limpo caso o H2 em disco tenha resquícios de execuções anteriores
            initService.resetarBancoParaDadosFundamentais(emH2, InicializadorSistemaService.TOKEN_CONFIRMACAO_RESET);

            emH2.getTransaction().begin();

            // Insere dados extras (produto + pessoa não padrão)
            PessoaFisica extra = new PessoaFisica("Cliente Temporário H2", "111.222.333-44");
            extra.definirComoCliente(java.math.BigDecimal.TEN);
            emH2.persist(extra);

            var um = emH2.createQuery("SELECT u FROM Um u WHERE u.sigla = 'UN'",
                    br.com.senac.repository.modal.produto.Um.class).getSingleResult();
            ProdutoFisico pf = new ProdutoFisico("Produto H2", um);
            pf.setDescricao("Desc");
            pf.setPrecoVenda(new java.math.BigDecimal("50.00"));
            emH2.persist(pf);
            emH2.getTransaction().commit();

            // Executa o reset restrito no H2 isolado
            initService.resetarBancoParaDadosFundamentais(emH2, InicializadorSistemaService.TOKEN_CONFIRMACAO_RESET);

            Long qtdProdutos = emH2.createQuery("SELECT COUNT(p) FROM Produto p", Long.class).getSingleResult();
            Long qtdPessoas = emH2.createQuery("SELECT COUNT(p) FROM Pessoa p", Long.class).getSingleResult();
            Long qtdUm = emH2.createQuery("SELECT COUNT(u) FROM Um u", Long.class).getSingleResult();
            Long qtdFp = emH2.createQuery("SELECT COUNT(f) FROM FormaPagamento f", Long.class).getSingleResult();
            Pessoa clientePadrao = emH2.createQuery("SELECT pf FROM PessoaFisica pf WHERE pf.cpf = :cpf", PessoaFisica.class)
                    .setParameter("cpf", PessoaDAO.CPF_CLIENTE_PADRAO)
                    .getSingleResult();

            validar(qtdProdutos == 0L, "Reset H2 removeu todos os produtos");
            validar(qtdPessoas == 1L && clientePadrao != null, "Reset H2 manteve apenas o Cliente Padrão");
            validar(qtdUm == 10L, "Reset H2 manteve as 10 Unidades de Medida fundamentais");
            validar(qtdFp == 8L, "Reset H2 manteve as 8 Formas de Pagamento fundamentais");
        }
    }

    private static void testarRotaResetOcultaParaGemini() {
        List<Map<String, Object>> tools = GeminiToolRegistry.gerarDeclaracoesTodasTools();
        boolean exposta = tools.stream().anyMatch(t ->
                RotaSistema.SISTEMA_RESETAR_BANCO_RESTRITO.getNomeFuncaoGemini().equals(t.get("name"))
        );
        validar(!exposta, "Rota restrita de reset não está exposta nas ferramentas da IA Gemini");
    }

    private static String gerarCpfSimples(String sufixo) {
        String base = String.format("%09d", Math.abs(sufixo.hashCode()) % 1000000000);
        return base.substring(0, 3) + "." + base.substring(3, 6) + "." + base.substring(6, 9) + "-00";
    }

    private static String gerarCnpjSimples(String sufixo) {
        String base = String.format("%08d", Math.abs(sufixo.hashCode()) % 100000000);
        return base.substring(0, 2) + "." + base.substring(2, 5) + "." + base.substring(5, 8) + "/0001-00";
    }

    private static void validar(boolean condicao, String descricao) {
        if (condicao) {
            aprovados++;
            System.out.println("  [OK] " + descricao);
        } else {
            falhas++;
            System.err.println("  [FALHA] " + descricao);
        }
    }
}
