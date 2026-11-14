package br.com.senac.application;

import br.com.senac.integracao.csv.ImportadorCsvService;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv;
import br.com.senac.integracao.csv.TipoImportacaoCsv;
import br.com.senac.service.InicializadorSistemaService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Console interativo de terminal para confirmar a limpeza dos dados do banco
 * (preservando os dados fundamentais) e escolher a integração a executar.
 */
public class ConsoleResetEIntegracao {

    private static final String RESET = "\u001B[0m";
    private static final String CIANO = "\u001B[36m";
    private static final String VERDE = "\u001B[32m";
    private static final String AMARELO = "\u001B[33m";
    private static final String VERMELHO = "\u001B[31m";
    private static final String CINZA = "\u001B[90m";
    private static final String NEGRITO = "\u001B[1m";
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void main(String[] args) {
        habilitarLogsDetalhados();

        System.out.println(CIANO + "================================================================================" + RESET);
        System.out.println(NEGRITO + CIANO + "   ⚙️  SAZONALISEI — CONSOLE DE LIMPEZA E INTEGRAÇÃO DE DADOS" + RESET);
        System.out.println(CIANO + "================================================================================" + RESET);
        logInfo("Console iniciado. Logs técnicos (Hibernate, HikariCP e etapas) habilitados.");

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.println("\n" + NEGRITO + AMARELO + "MENU PRINCIPAL:" + RESET);
            System.out.println("  [1] Apagar dados (mantém apenas os dados fundamentais)");
            System.out.println("  [2] Apagar dados e integrar (escolher integração ou sair)");
            System.out.println("  [0] Sair");
            System.out.print(NEGRITO + "\nEscolha uma opção [0-2]: " + RESET);

            String opcao = scanner.nextLine().trim();
            logInfo("Opção selecionada no menu principal: '" + opcao + "'");

            switch (opcao) {
                case "1" -> confirmarEApagarDados(scanner);
                case "2" -> {
                    if (confirmarEApagarDados(scanner)) {
                        escolherEExecutarIntegracao(scanner);
                    }
                }
                case "0", "" -> logInfo("Encerrando console a pedido do usuário.");
                default -> System.out.println(VERMELHO + "❌ Opção inválida: " + opcao + RESET);
            }
        } catch (Exception e) {
            logErro("Falha não tratada durante a execução do console: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void habilitarLogsDetalhados() {
        Logger.getLogger("").setLevel(Level.INFO);
        Logger.getLogger("org.hibernate").setLevel(Level.INFO);
        Logger.getLogger("com.zaxxer.hikari").setLevel(Level.INFO);
        Logger.getLogger("br.com.senac").setLevel(Level.INFO);
    }

    private static void logInfo(String mensagem) {
        System.out.println(CINZA + "[LOG " + LocalTime.now().format(FMT_HORA) + "] " + RESET + mensagem);
    }

    private static void logErro(String mensagem) {
        System.err.println(VERMELHO + "[LOG " + LocalTime.now().format(FMT_HORA) + "][ERRO] " + mensagem + RESET);
    }

    private static boolean confirmarEApagarDados(Scanner scanner) {
        System.out.println(VERMELHO + "\n⚠️  ATENÇÃO: Esta ação apagará os registros do banco e manterá apenas os dados fundamentais!" + RESET);
        System.out.print("Digite " + NEGRITO + "CONFIRMAR" + RESET + " para prosseguir (ou qualquer outra coisa para sair): ");
        String confirmacao = scanner.nextLine().trim();

        if (!"CONFIRMAR".equalsIgnoreCase(confirmacao)) {
            logInfo("Reset cancelado pelo usuário (entrada='" + confirmacao + "'). Nenhuma alteração foi feita no banco.");
            return false;
        }

        long inicio = System.currentTimeMillis();
        logInfo("Iniciando InicializadorSistemaService.resetarBancoParaDadosFundamentais...");
        InicializadorSistemaService inicializador = new InicializadorSistemaService();
        inicializador.resetarBancoParaDadosFundamentais(InicializadorSistemaService.TOKEN_CONFIRMACAO_RESET);
        long duracaoMs = System.currentTimeMillis() - inicio;
        logInfo(VERDE + "Reset concluído em " + duracaoMs + " ms. Banco pronto apenas com os dados fundamentais." + RESET);
        return true;
    }

    private static void escolherEExecutarIntegracao(Scanner scanner) throws Exception {
        System.out.println("\n" + NEGRITO + CIANO + "ESCOLHA A INTEGRAÇÃO:" + RESET);
        System.out.println("  [1] IntegrarDadosReais18M (arquivo padrão: docs/testesDadosReais18M.csv)");
        System.out.println("  [2] IntegrarDadosReais18M (informar caminho de CSV personalizado)");
        System.out.println("  [3] ImportadorCsvService genérico (escolher tipo de importação + arquivo CSV)");
        System.out.println("  [0] Sair");
        System.out.print(NEGRITO + "\nEscolha uma opção [0-3]: " + RESET);

        String opcao = scanner.nextLine().trim();
        logInfo("Opção selecionada no menu de integração: '" + opcao + "'");

        switch (opcao) {
            case "1" -> rodarIntegracao18M(Paths.get("docs", "testesDadosReais18M.csv"));
            case "2" -> {
                System.out.print("Informe o caminho do arquivo CSV: ");
                String caminho = scanner.nextLine().trim().replace("\"", "");
                rodarIntegracao18M(Paths.get(caminho));
            }
            case "3" -> rodarImportadorCsvGenerico(scanner);
            case "0", "" -> logInfo("Encerrando console sem executar integração.");
            default -> System.out.println(VERMELHO + "❌ Opção inválida: " + opcao + RESET);
        }
    }

    private static void rodarIntegracao18M(Path csvPath) throws Exception {
        Path absoluto = csvPath.toAbsolutePath().normalize();
        logInfo("Verificando arquivo CSV para IntegrarDadosReais18M: " + absoluto);
        if (!Files.exists(absoluto)) {
            logErro("Arquivo CSV não encontrado: " + absoluto);
            return;
        }
        long tamanhoBytes = Files.size(absoluto);
        long linhas;
        try (var stream = Files.lines(absoluto, StandardCharsets.UTF_8)) {
            linhas = stream.count();
        }
        logInfo(String.format("Arquivo localizado (%d bytes, %d linhas). Executando IntegrarDadosReais18M...", tamanhoBytes, linhas));

        long inicio = System.currentTimeMillis();
        IntegrarDadosReais18M script = new IntegrarDadosReais18M();
        IntegrarDadosReais18M.ResumoIntegracao18M resumo = script.executarIntegracao(absoluto, false);
        long duracaoMs = System.currentTimeMillis() - inicio;

        logInfo("IntegrarDadosReais18M finalizado em " + duracaoMs + " ms.");
        System.out.println(VERDE + "\n" + resumo.formatar() + RESET);
    }

    private static void rodarImportadorCsvGenerico(Scanner scanner) throws Exception {
        TipoImportacaoCsv[] tipos = TipoImportacaoCsv.values();
        System.out.println("\n" + NEGRITO + "Tipos de importação disponíveis:" + RESET);
        for (int i = 0; i < tipos.length; i++) {
            System.out.printf("  [%d] %-20s — %s%n", i + 1, tipos[i].name(), tipos[i].getDescricaoColunas());
        }
        System.out.print(NEGRITO + "\nEscolha o tipo [1-" + tipos.length + "]: " + RESET);
        int idx;
        try {
            idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
        } catch (NumberFormatException e) {
            logErro("Número inválido informado para tipo de importação.");
            return;
        }
        if (idx < 0 || idx >= tipos.length) {
            logErro("Opção fora do intervalo: " + (idx + 1));
            return;
        }

        System.out.print("Informe o caminho do arquivo CSV: ");
        Path csvPath = Paths.get(scanner.nextLine().trim().replace("\"", "")).toAbsolutePath().normalize();
        logInfo("Verificando arquivo CSV para ImportadorCsvService (" + tipos[idx].name() + "): " + csvPath);
        if (!Files.exists(csvPath)) {
            logErro("Arquivo CSV não encontrado: " + csvPath);
            return;
        }

        long tamanhoBytes = Files.size(csvPath);
        long linhas;
        try (var stream = Files.lines(csvPath, StandardCharsets.UTF_8)) {
            linhas = stream.count();
        }
        logInfo(String.format("Arquivo localizado (%d bytes, %d linhas). Executando ImportadorCsvService (%s)...",
                tamanhoBytes, linhas, tipos[idx].name()));

        long inicio = System.currentTimeMillis();
        ImportadorCsvService importador = new ImportadorCsvService();
        ResultadoImportacaoCsv resultado = importador.importar(csvPath, tipos[idx]);
        long duracaoMs = System.currentTimeMillis() - inicio;

        logInfo("ImportadorCsvService finalizado em " + duracaoMs + " ms.");
        System.out.println(VERDE + "\n" + resultado.formatarResumo() + RESET);
        if (!resultado.semErros()) {
            logErro("Foram registrados " + resultado.erros().size() + " erro(s) durante a importação:");
            resultado.erros().forEach(err -> logErro("  " + err));
        }
    }
}
