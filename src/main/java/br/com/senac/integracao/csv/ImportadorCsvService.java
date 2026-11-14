package br.com.senac.integracao.csv;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.csv.LeitorCsv.TabelaCsv;
import br.com.senac.integracao.csv.ResultadoImportacaoCsv.ErroLinhaCsv;
import br.com.senac.repository.modal.produto.LinkProdutoDigital;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Serviço de integração para carga de dados reais a partir de arquivos CSV.
 * Todos os registros lidos são normalizados e despachados pelas rotas oficiais do
 * {@link RoteadorController}, garantindo tratamento lógico, validações de domínio e regras BACEN/FIFO.
 */
public class ImportadorCsvService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_HORA_SEG_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Set<String> CAMPOS_DECIMAIS = Set.of(
            "base", "precovenda", "novopreco", "alturacm", "larguracm", "comprimentocm",
            "pesobrutokg", "pesoliquidokg", "limite", "novolimite", "quantidade",
            "quantidadedevolvida", "quantidadeperda", "valor", "valorunitario", "precounitario",
            "preco", "custo", "valorrecebido", "descontocustomizado", "descontocupom",
            "descontoglobal", "margemdesejada", "taxaoperadorapercentual", "tarifafixaoperadora",
            "descontoavistapercentual", "taxajurosmensal"
    );

    private static final Set<String> CAMPOS_BOOLEANOS = Set.of(
            "iscliente", "isfornecedor", "permiteavista", "permiteaprazo", "permiteparcelado",
            "ativo", "isprincipal", "apenasestoquevalido", "comestoquevalido", "removerarquivofisico"
    );

    private static final Set<String> CAMPOS_DATA = Set.of(
            "datavalidade", "validade", "datavencimento", "datalimite"
    );

    private static final Set<String> CAMPOS_DATA_HORA = Set.of(
            "datagerada", "datanota", "data", "dataquitacao"
    );

    private final RoteadorController roteador;
    private final LeitorCsv leitorCsv;

    public ImportadorCsvService() {
        this(new RoteadorController());
    }

    public ImportadorCsvService(RoteadorController roteador) {
        this.roteador = roteador != null ? roteador : new RoteadorController();
        this.leitorCsv = new LeitorCsv();
    }

    public RoteadorController getRoteador() {
        return roteador;
    }

    public ResultadoImportacaoCsv importar(Path arquivoCsv, TipoImportacaoCsv tipo) throws IOException {
        return importar(arquivoCsv, tipo, false);
    }

    public ResultadoImportacaoCsv importar(Path arquivoCsv, TipoImportacaoCsv tipo, boolean pararNoPrimeiroErro) throws IOException {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de importação CSV não informado.");
        }
        TabelaCsv tabela = leitorCsv.lerArquivo(arquivoCsv);
        return processarTabelaTipo(arquivoCsv.toString(), tabela, tipo, pararNoPrimeiroErro);
    }

    public ResultadoImportacaoCsv importarTexto(String conteudoCsv, TipoImportacaoCsv tipo) throws IOException {
        return importarTexto(conteudoCsv, tipo, false);
    }

    public ResultadoImportacaoCsv importarTexto(String conteudoCsv, TipoImportacaoCsv tipo, boolean pararNoPrimeiroErro) throws IOException {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de importação CSV não informado.");
        }
        TabelaCsv tabela = leitorCsv.lerTexto(conteudoCsv);
        return processarTabelaTipo("texto-csv", tabela, tipo, pararNoPrimeiroErro);
    }

    /**
     * Importa uma TabelaCsv aplicando um mapeamento explícito de colunas (colunaOriginal -> campoDestino).
     * Colunas mapeadas para vazio ou "IGNORAR" são descartadas antes do despacho nas rotas.
     */
    public ResultadoImportacaoCsv importarTabelaMapeada(
            String origem,
            TabelaCsv tabela,
            TipoImportacaoCsv tipo,
            Map<String, String> mapeamentoColunas,
            boolean pararNoPrimeiroErro
    ) {
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de importação CSV não informado.");
        }
        if (tabela == null || tabela.isVazia()) {
            return new ResultadoImportacaoCsv(origem != null ? origem : "csv-ui", 0, 0, 0, List.of(), List.of());
        }
        if (mapeamentoColunas == null || mapeamentoColunas.isEmpty()) {
            return processarTabelaTipo(origem, tabela, tipo, pararNoPrimeiroErro);
        }

        List<String> novosCabecalhos = new ArrayList<>();
        for (String orig : tabela.cabecalhos()) {
            String destino = mapeamentoColunas.getOrDefault(orig, orig);
            if (destino != null && !destino.isBlank() && !"IGNORAR".equalsIgnoreCase(destino.trim())) {
                novosCabecalhos.add(destino.trim());
            }
        }
        List<Map<String, String>> novasLinhas = new ArrayList<>();
        for (Map<String, String> linha : tabela.linhas()) {
            Map<String, String> novaLinha = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : linha.entrySet()) {
                String destino = mapeamentoColunas.getOrDefault(entry.getKey(), entry.getKey());
                if (destino != null && !destino.isBlank() && !"IGNORAR".equalsIgnoreCase(destino.trim())) {
                    novaLinha.put(destino.trim(), entry.getValue());
                }
            }
            novasLinhas.add(novaLinha);
        }
        TabelaCsv tabelaMapeada = new TabelaCsv(tabela.delimitadorDetectado(), novosCabecalhos, novasLinhas);
        return processarTabelaTipo(origem, tabelaMapeada, tipo, pararNoPrimeiroErro);
    }

    public ResultadoImportacaoCsv importarPorRota(Path arquivoCsv, RotaSistema rota) throws IOException {
        return importarPorRota(arquivoCsv, rota, false);
    }

    public ResultadoImportacaoCsv importarPorRota(Path arquivoCsv, RotaSistema rota, boolean pararNoPrimeiroErro) throws IOException {
        if (rota == null) {
            throw new IllegalArgumentException("Rota de destino não informada.");
        }
        if (rota == RotaSistema.SISTEMA_RESETAR_BANCO_RESTRITO) {
            throw new SecurityException("A rota restrita de reset não pode ser executada em lote via importação CSV.");
        }
        TabelaCsv tabela = leitorCsv.lerArquivo(arquivoCsv);
        return processarTabelaRota(arquivoCsv.toString(), tabela, rota, pararNoPrimeiroErro);
    }

    public ResultadoImportacaoCsv importarPorRota(Path arquivoCsv, String caminhoOuToolName) throws IOException {
        RotaSistema rota = roteador.resolverRota(caminhoOuToolName);
        if (rota == null) {
            throw new IllegalArgumentException("Rota não encontrada: " + caminhoOuToolName);
        }
        return importarPorRota(arquivoCsv, rota, false);
    }

    public ResultadoImportacaoCsv importarTextoPorRota(String conteudoCsv, RotaSistema rota) throws IOException {
        if (rota == null) {
            throw new IllegalArgumentException("Rota de destino não informada.");
        }
        if (rota == RotaSistema.SISTEMA_RESETAR_BANCO_RESTRITO) {
            throw new SecurityException("A rota restrita de reset não pode ser executada em lote via importação CSV.");
        }
        TabelaCsv tabela = leitorCsv.lerTexto(conteudoCsv);
        return processarTabelaRota("texto-csv", tabela, rota, false);
    }

    private ResultadoImportacaoCsv processarTabelaTipo(
            String origem,
            TabelaCsv tabela,
            TipoImportacaoCsv tipo,
            boolean pararNoPrimeiroErro
    ) {
        List<ErroLinhaCsv> erros = new ArrayList<>();
        List<RespostaEndpoint<Object>> respostas = new ArrayList<>();
        int sucessos = 0;

        List<Map<String, String>> linhas = tabela.linhas();
        for (int i = 0; i < linhas.size(); i++) {
            int numeroLinha = i + 2; // +1 cabeçalho, +1 base-1
            Map<String, String> linha = linhas.get(i);
            RotaSistema rota = resolverRotaParaLinha(tipo, linha);
            try {
                Map<String, Object> params = montarParametrosParaTipo(tipo, linha);
                RespostaEndpoint<Object> resp = roteador.executarEndpoint(rota, params);
                respostas.add(resp);
                if (resp.sucesso()) {
                    sucessos++;
                } else {
                    erros.add(new ErroLinhaCsv(numeroLinha, rota.getCaminhoRota(), resp.mensagem()));
                    if (pararNoPrimeiroErro) break;
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                erros.add(new ErroLinhaCsv(numeroLinha, rota.getCaminhoRota(), msg));
                respostas.add(RespostaEndpoint.erro(rota.getCaminhoRota(), msg));
                if (pararNoPrimeiroErro) break;
            }
        }

        return new ResultadoImportacaoCsv(origem, linhas.size(), sucessos, erros.size(), erros, respostas);
    }

    private ResultadoImportacaoCsv processarTabelaRota(
            String origem,
            TabelaCsv tabela,
            RotaSistema rota,
            boolean pararNoPrimeiroErro
    ) {
        List<ErroLinhaCsv> erros = new ArrayList<>();
        List<RespostaEndpoint<Object>> respostas = new ArrayList<>();
        int sucessos = 0;

        List<Map<String, String>> linhas = tabela.linhas();
        for (int i = 0; i < linhas.size(); i++) {
            int numeroLinha = i + 2;
            Map<String, Object> params = converterMapaGenerico(linhas.get(i));
            try {
                RespostaEndpoint<Object> resp = roteador.executarEndpoint(rota, params);
                respostas.add(resp);
                if (resp.sucesso()) {
                    sucessos++;
                } else {
                    erros.add(new ErroLinhaCsv(numeroLinha, rota.getCaminhoRota(), resp.mensagem()));
                    if (pararNoPrimeiroErro) break;
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                erros.add(new ErroLinhaCsv(numeroLinha, rota.getCaminhoRota(), msg));
                respostas.add(RespostaEndpoint.erro(rota.getCaminhoRota(), msg));
                if (pararNoPrimeiroErro) break;
            }
        }

        return new ResultadoImportacaoCsv(origem, linhas.size(), sucessos, erros.size(), erros, respostas);
    }

    private RotaSistema resolverRotaParaLinha(TipoImportacaoCsv tipo, Map<String, String> linha) {
        if (tipo == TipoImportacaoCsv.PESSOAS) {
            String papel = valorIgnorandoCaso(linha, "papel", "").toUpperCase(Locale.ROOT);
            String cpf = valorIgnorandoCaso(linha, "cpf", "");
            String cnpj = valorIgnorandoCaso(linha, "cnpj", "");
            String doc = valorIgnorandoCaso(linha, "documento", "");

            boolean temCnpj = !cnpj.isBlank() || doc.replaceAll("\\D", "").length() == 14;
            boolean temCpf = !cpf.isBlank() || doc.replaceAll("\\D", "").length() == 11;

            if (papel.equals("FORNECEDOR")) {
                if (temCnpj) return RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR;
                if (temCpf) return RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR;
                return RotaSistema.PESSOA_SIMPLES_CADASTRAR;
            }
            if (papel.equals("CLIENTE")) {
                if (temCnpj) return RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR;
                if (temCpf) return RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR;
                return RotaSistema.PESSOA_SIMPLES_CADASTRAR;
            }
            return RotaSistema.PESSOA_SIMPLES_CADASTRAR;
        }

        if (tipo == TipoImportacaoCsv.NOTAS_VENDA) {
            String idCli = valorIgnorandoCaso(linha, "idCliente", "");
            String nomeCli = valorIgnorandoCaso(linha, "nomeCliente", valorIgnorandoCaso(linha, "cliente", ""));
            boolean semCliente = idCli.isBlank() && (nomeCli.isBlank()
                    || nomeCli.equalsIgnoreCase("PADRAO")
                    || nomeCli.equalsIgnoreCase("CONSUMIDOR FINAL"));
            return semCliente
                    ? RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO
                    : RotaSistema.NOTA_VENDA_EMITIR_FIFO;
        }

        return tipo.getRotaPadrao();
    }

    private Map<String, Object> montarParametrosParaTipo(TipoImportacaoCsv tipo, Map<String, String> linha) {
        Map<String, Object> params = converterMapaGenerico(linha);

        switch (tipo) {
            case PESSOAS -> {
                String doc = valorIgnorandoCaso(linha, "documento", "");
                if (!doc.isBlank()) {
                    String digitos = doc.replaceAll("\\D", "");
                    if (digitos.length() == 14 && !params.containsKey("cnpj")) {
                        params.put("cnpj", doc);
                    } else if (digitos.length() == 11 && !params.containsKey("cpf")) {
                        params.put("cpf", doc);
                    }
                }
            }
            case PRODUTOS_DIGITAIS -> {
                String url = valorIgnorandoCaso(linha, "urlLink", valorIgnorandoCaso(linha, "url", ""));
                String titulo = valorIgnorandoCaso(linha, "tituloLink", valorIgnorandoCaso(linha, "titulo", "Acesso Digital"));
                if (!url.isBlank()) {
                    params.put("links", List.of(new LinkProdutoDigital(titulo, url)));
                } else {
                    params.put("links", Collections.emptyList());
                }
            }
            case NOTAS_COMPRA -> {
                Map<String, Object> item = new LinkedHashMap<>();
                copiarSePresente(params, item, "idProduto");
                copiarSePresente(params, item, "nomeProduto");
                copiarSePresente(params, item, "produto");
                if (!item.containsKey("nomeProduto") && !item.containsKey("idProduto") && params.containsKey("nome")) {
                    item.put("nomeProduto", params.get("nome"));
                }
                item.put("quantidade", params.getOrDefault("quantidade", "1"));
                if (params.containsKey("valorUnitario")) {
                    item.put("valorUnitario", params.get("valorUnitario"));
                } else if (params.containsKey("custo")) {
                    item.put("valorUnitario", params.get("custo"));
                } else if (params.containsKey("valor")) {
                    item.put("valorUnitario", params.get("valor"));
                }
                copiarSePresente(params, item, "dataValidade");
                copiarSePresente(params, item, "validade");
                params.put("itens", List.of(item));

                anexarPagamentoSeInformado(params, false);
            }
            case NOTAS_VENDA -> {
                Map<String, Object> item = new LinkedHashMap<>();
                copiarSePresente(params, item, "idProduto");
                copiarSePresente(params, item, "nomeProduto");
                copiarSePresente(params, item, "produto");
                if (!item.containsKey("nomeProduto") && !item.containsKey("idProduto") && params.containsKey("nome")) {
                    item.put("nomeProduto", params.get("nome"));
                }
                item.put("quantidade", params.getOrDefault("quantidade", "1"));
                params.put("itens", List.of(item));

                anexarPagamentoSeInformado(params, true);
            }
            default -> {
                // UNIDADES_MEDIDA, FORMAS_PAGAMENTO, PRODUTOS_FISICOS usam o mapa normalizado diretamente
            }
        }
        return params;
    }

    private void anexarPagamentoSeInformado(Map<String, Object> params, boolean isVenda) {
        if (params.containsKey("pagamentos")) {
            return;
        }
        Object codigoForma = params.getOrDefault("codigoForma", params.get("formaPagamento"));
        if (codigoForma == null || codigoForma.toString().isBlank()) {
            return;
        }
        Map<String, Object> pg = new LinkedHashMap<>();
        pg.put("codigoForma", codigoForma.toString().trim().toUpperCase(Locale.ROOT));
        if (params.containsKey("valorPagamento")) {
            pg.put("valor", normalizarDecimal(params.get("valorPagamento").toString()));
        } else if (params.containsKey("valor")) {
            pg.put("valor", params.get("valor"));
        } else if (params.containsKey("valorUnitario") && params.containsKey("quantidade")) {
            try {
                BigDecimal v = new BigDecimal(params.get("valorUnitario").toString());
                BigDecimal q = new BigDecimal(params.get("quantidade").toString());
                pg.put("valor", v.multiply(q).toPlainString());
            } catch (Exception ignored) {
            }
        } else if (isVenda) {
            BigDecimal totalVenda = calcularTotalVendaEstimado(params);
            if (totalVenda != null) {
                pg.put("valor", totalVenda.toPlainString());
            }
        }
        if (params.containsKey("parcelas")) {
            pg.put("parcelas", params.get("parcelas"));
        }
        if (params.containsKey("dataVencimento")) {
            pg.put("dataVencimento", params.get("dataVencimento"));
        }
        params.put("pagamentos", List.of(pg));
    }

    private BigDecimal calcularTotalVendaEstimado(Map<String, Object> params) {
        try {
            br.com.senac.repository.modal.produto.Produto prod = null;
            if (params.containsKey("idProduto")) {
                prod = roteador.produtos().buscarPorId(Long.parseLong(params.get("idProduto").toString().trim()));
            } else {
                Object nomeObj = params.getOrDefault("nomeProduto", params.getOrDefault("produto", params.get("nome")));
                if (nomeObj != null && !nomeObj.toString().isBlank()) {
                    prod = roteador.produtos().buscarPorNome(nomeObj.toString().trim());
                }
            }
            if (prod != null && prod.getPrecoVenda() != null) {
                BigDecimal qtd = new BigDecimal(params.getOrDefault("quantidade", "1").toString());
                BigDecimal total = prod.getPrecoVenda().multiply(qtd);
                Object descObj = params.getOrDefault("descontoCupom", params.get("descontoGlobal"));
                if (descObj != null && !descObj.toString().isBlank()) {
                    BigDecimal desc = new BigDecimal(descObj.toString());
                    total = total.subtract(desc).max(BigDecimal.ZERO);
                }
                return total;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Map<String, Object> converterMapaGenerico(Map<String, String> linha) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : linha.entrySet()) {
            String chave = entry.getKey();
            String valor = entry.getValue();
            if (chave == null || chave.isBlank() || valor == null || valor.isBlank()) {
                continue;
            }
            String chaveNorm = chave.trim().toLowerCase(Locale.ROOT);
            if (CAMPOS_DECIMAIS.contains(chaveNorm)) {
                resultado.put(chave.trim(), normalizarDecimal(valor));
            } else if (CAMPOS_BOOLEANOS.contains(chaveNorm)) {
                resultado.put(chave.trim(), parsearBooleano(valor));
            } else if (CAMPOS_DATA.contains(chaveNorm)) {
                resultado.put(chave.trim(), parsearData(valor));
            } else if (CAMPOS_DATA_HORA.contains(chaveNorm)) {
                resultado.put(chave.trim(), parsearDataHora(valor));
            } else {
                resultado.put(chave.trim(), valor.trim());
            }
        }
        return resultado;
    }

    private String normalizarDecimal(String bruto) {
        String s = bruto.trim().replace("R$", "").replace(" ", "");
        if (s.contains(",") && s.contains(".")) {
            // Ex.: 1.250,90 -> 1250.90
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",")) {
            s = s.replace(",", ".");
        }
        return s;
    }

    private boolean parsearBooleano(String bruto) {
        String s = bruto.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("sim") || s.equals("s") || s.equals("yes");
    }

    private Object parsearData(String bruto) {
        String s = bruto.trim();
        try {
            return LocalDate.parse(s, DATA_BR);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
        }
        return s;
    }

    private Object parsearDataHora(String bruto) {
        String s = bruto.trim();
        try {
            return LocalDateTime.parse(s, DATA_HORA_SEG_BR);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s, DATA_HORA_BR);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(s, DATA_BR).atStartOfDay();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception ignored) {
        }
        return s;
    }

    private String valorIgnorandoCaso(Map<String, String> linha, String chaveAlvo, String padrao) {
        for (Map.Entry<String, String> e : linha.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(chaveAlvo)) {
                return e.getValue() != null ? e.getValue().trim() : padrao;
            }
        }
        return padrao;
    }

    private void copiarSePresente(Map<String, Object> origem, Map<String, Object> destino, String chave) {
        if (origem.containsKey(chave)) {
            destino.put(chave, origem.get(chave));
        }
    }
}
