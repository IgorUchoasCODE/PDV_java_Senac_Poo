package br.com.senac.integracao.gemini;

import br.com.senac.controller.RotaSistema;

import java.util.*;

/**
 * Registro e Gerador dos Schemas de Ferramentas (Tool Declarations / Function Calling) da IA Gemini.
 * Mapeia 100% das rotas do RotaSistema em declarações formais com tipagem estrita JSON Schema,
 * descrições ricas, parâmetros obrigatórios e classificação de risco (READ, WRITE, DESTRUCTIVE).
 */
public class GeminiToolRegistry {

    public enum NivelRiscoOperacao {
        READ_ONLY,       // Execução imediata em paralelo
        WRITE_SEGURO,    // Criação/Edição regular
        DESTRUTIVO       // Exige confirmação explícita do usuário (Human-in-the-Loop)
    }

    /**
     * Retorna a lista completa de Function Declarations compatíveis com a especificação da API Gemini.
     */
    public static List<Map<String, Object>> gerarDeclaracoesTodasTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        for (RotaSistema rota : RotaSistema.values()) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", rota.getNomeFuncaoGemini());
            func.put("description", construirDescricaoEnriquecida(rota));
            func.put("parameters", mapearParametrosRota(rota));
            tools.add(func);
        }

        return tools;
    }

    /**
     * Retorna a lista encapsulada dentro da chave 'tools' pronta para envio ao endpoint do Gemini.
     */
    public static List<Map<String, Object>> gerarToolsWrapper() {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("function_declarations", gerarDeclaracoesTodasTools());
        return Collections.singletonList(wrapper);
    }

    /**
     * Classifica o nível de risco de uma rota para governança e confirmação em 2 etapas.
     */
    public static NivelRiscoOperacao classificarRisco(RotaSistema rota) {
        if (rota == null) return NivelRiscoOperacao.READ_ONLY;

        String nome = rota.name();
        if (nome.contains("REMOVER") || nome.contains("DELETAR") || nome.contains("EXCLUIR") || nome.contains("BAIXAR_PERDA") || nome.contains("CANCELAR")) {
            return NivelRiscoOperacao.DESTRUTIVO;
        }
        if (nome.contains("CADASTRAR") || nome.contains("EMITIR") || nome.contains("ATUALIZAR") ||
                nome.contains("ADICIONAR") || nome.contains("QUITAR") || nome.contains("DEFINIR_PRINCIPAL") ||
                nome.contains("ALTERAR_STATUS") || nome.contains("CUSTOMIZAR")) {
            return NivelRiscoOperacao.WRITE_SEGURO;
        }
        return NivelRiscoOperacao.READ_ONLY;
    }

    /**
     * Gera o System Instruction padrão com as diretrizes contábeis BACEN, concorrência e confirmação.
     */
    public static Map<String, Object> gerarSystemInstruction() {
        GeminiConfig cfg = GeminiConfig.getInstancia();
        StringBuilder sb = new StringBuilder();
        sb.append("Você é a Inteligência Artificial e Assistente Operacional do Sistema de Vendas Sazonalisei.\n\n");

        if (cfg.getPersonalidade() != null && !cfg.getPersonalidade().isBlank()) {
            sb.append("🎭 SUA PERSONALIDADE E TOM DE VOZ (DEFINIDOS PELO USUÁRIO):\n")
              .append(cfg.getPersonalidade().trim())
              .append("\n\n");
        }

        if (cfg.getInstrucoesCustomizadas() != null && !cfg.getInstrucoesCustomizadas().isBlank()) {
            sb.append("📋 INSTRUÇÕES ESPECÍFICAS ADICIONAIS DO USUÁRIO:\n")
              .append(cfg.getInstrucoesCustomizadas().trim())
              .append("\n\n");
        }

        sb.append("""
                DIRETRIZES OPERACIONAIS OBRIGATÓRIAS DO SISTEMA:
                1. COMUNICAÇÃO NATURAL E EXECUTIVA: Fale sempre em português claro, direto, amigável e educado, aderindo à personalidade definida acima. Explique sempre o que você fez ou encontrou de forma resumida e profissional. NUNCA cuspa objetos técnicos brutos ou códigos incompreensíveis.
                2. CONSULTAS E BUSCAS: Ao buscar produtos ou clientes, apresente os itens encontrados com ID, Nome e Preço (ex: 'Encontrei o produto #86: Monitor Gamer 27 (R$ 1.250,00)').
                3. MODIFICAÇÕES (PREÇO, DADOS): Ao atualizar qualquer informação, confirme o novo valor com clareza (ex: 'Preço do produto #86 atualizado para R$ 1.100,00 com sucesso!').
                4. AÇÕES DESTRUTIVAS (DELETAR, APAGAR, BAIXAR PERDAS): Ao identificar uma solicitação de exclusão, acione a ferramenta correspondente. O sistema de proteção exibirá automaticamente a solicitação de confirmação para o usuário.
                5. PADRÃO CONTÁBIL BACEN: Todas as operações financeiras e margens seguem o padrão Banco Central do Brasil (HALF_EVEN, 2 casas decimais).
                """);

        Map<String, Object> part = new HashMap<>();
        part.put("text", sb.toString());

        Map<String, Object> sys = new HashMap<>();
        sys.put("parts", Collections.singletonList(part));
        return sys;
    }

    private static String construirDescricaoEnriquecida(RotaSistema rota) {
        NivelRiscoOperacao risco = classificarRisco(rota);
        String tagRisco = switch (risco) {
            case DESTRUTIVO -> "[ALTO RISCO - EXIGE CONFIRMAÇÃO DO USUÁRIO] ";
            case WRITE_SEGURO -> "[ESCRITA/MUTATIVO] ";
            case READ_ONLY -> "[LEITURA SEGURA] ";
        };
        return tagRisco + "(" + rota.getModulo() + ") " + rota.getDescricaoOperacao();
    }

    private static Map<String, Object> mapearParametrosRota(RotaSistema rota) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        switch (rota) {
            case SISTEMA_INICIALIZAR_DADOS_PADRAO -> {
            }
            case PRODUTO_UNIDADE_CADASTRAR -> {
                props.put("sigla", prop("STRING", "Sigla da unidade de medida (ex: UN, KG, LT, CX)"));
                props.put("nome", prop("STRING", "Nome por extenso da unidade (ex: Quilograma)"));
                props.put("descricao", prop("STRING", "Descrição da unidade"));
                props.put("base", prop("STRING", "Base de fracionamento decimal (ex: 1.000000)"));
                required.addAll(Arrays.asList("sigla", "nome"));
            }
            case PRODUTO_UNIDADE_ATUALIZAR -> {
                props.put("sigla", prop("STRING", "Sigla da unidade de medida a ser atualizada"));
                props.put("nome", prop("STRING", "Novo nome por extenso da unidade (opcional)"));
                props.put("descricao", prop("STRING", "Nova descrição da unidade (opcional)"));
                props.put("base", prop("STRING", "Nova base de fracionamento decimal (opcional)"));
                required.add("sigla");
            }
            case PRODUTO_UNIDADE_EXCLUIR -> {
                props.put("sigla", prop("STRING", "Sigla da unidade de medida a ser excluída permanentemente"));
                required.add("sigla");
            }
            case PRODUTO_FISICO_CADASTRAR -> {
                props.put("nome", prop("STRING", "Nome completo do produto físico"));
                props.put("descricao", prop("STRING", "Descrição detalhada do produto"));
                props.put("precoVenda", prop("STRING", "Preço de venda sugerido em R$ (ex: '199.90')"));
                props.put("siglaUm", prop("STRING", "Sigla da Unidade de Medida (padrão: 'UN')"));
                props.put("alturaCm", prop("STRING", "Altura em centímetros (opcional, padrão: 0.00)"));
                props.put("larguraCm", prop("STRING", "Largura em centímetros (opcional, padrão: 0.00)"));
                props.put("comprimentoCm", prop("STRING", "Comprimento em centímetros (opcional, padrão: 0.00)"));
                props.put("pesoBrutoKg", prop("STRING", "Peso bruto em kg (opcional, padrão: 0.00)"));
                props.put("pesoLiquidoKg", prop("STRING", "Peso líquido em kg (opcional, padrão: 0.00)"));
                required.add("nome");
            }
            case PRODUTO_FISICO_ATUALIZAR_DIMENSOES -> {
                props.put("idProduto", prop("INTEGER", "ID do produto físico"));
                props.put("alturaCm", prop("STRING", "Altura em cm"));
                props.put("larguraCm", prop("STRING", "Largura em cm"));
                props.put("comprimentoCm", prop("STRING", "Comprimento em cm"));
                props.put("pesoBrutoKg", prop("STRING", "Peso bruto em kg"));
                props.put("pesoLiquidoKg", prop("STRING", "Peso líquido em kg"));
                required.addAll(Arrays.asList("idProduto", "alturaCm", "larguraCm", "comprimentoCm", "pesoBrutoKg", "pesoLiquidoKg"));
            }
            case PRODUTO_DIGITAL_CADASTRAR -> {
                props.put("nome", prop("STRING", "Nome do produto digital"));
                props.put("descricao", prop("STRING", "Descrição do produto"));
                props.put("precoVenda", prop("STRING", "Preço de venda"));
                required.add("nome");
            }
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                props.put("idProdutoKit", prop("INTEGER", "ID do produto kit/conjunto"));
                props.put("idProdutoComponente", prop("INTEGER", "ID do produto componente"));
                props.put("quantidade", prop("STRING", "Quantidade fracionada do componente"));
                required.addAll(Arrays.asList("idProdutoKit", "idProdutoComponente", "quantidade"));
            }
            case PRODUTO_CATALOGO_BUSCAR -> {
                props.put("id", prop("INTEGER", "ID do produto (opcional)"));
                props.put("nome", prop("STRING", "Nome exato ou parcial do produto (opcional)"));
            }
            case PRODUTO_CATALOGO_ATUALIZAR -> {
                props.put("idProduto", prop("INTEGER", "ID do produto no catálogo"));
                props.put("nome", prop("STRING", "Novo nome do produto (opcional)"));
                props.put("descricao", prop("STRING", "Nova descrição detalhada do produto (opcional)"));
                required.add("idProduto");
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                props.put("idProduto", prop("INTEGER", "ID do produto no catálogo"));
                props.put("novoPreco", prop("STRING", "Novo preço de venda em R$ (ex: '49.90')"));
                required.addAll(Arrays.asList("idProduto", "novoPreco"));
            }
            case PRODUTO_CATALOGO_EXCLUIR -> {
                props.put("idProduto", prop("INTEGER", "ID do produto a ser excluído permanentemente"));
                required.add("idProduto");
            }
            case PRODUTO_IMAGEM_ADICIONAR -> {
                props.put("idProduto", prop("INTEGER", "ID do produto"));
                props.put("caminhoArquivo", prop("STRING", "Caminho do arquivo local (opcional)"));
                props.put("base64", prop("STRING", "Conteúdo binário codificado em Base64 (opcional)"));
                props.put("nomeOriginal", prop("STRING", "Nome original do arquivo (ex: 'foto1.png')"));
                props.put("isPrincipal", prop("BOOLEAN", "Se deve ser definida como imagem capa"));
                required.add("idProduto");
            }
            case PRODUTO_IMAGEM_LISTAR, PRODUTO_IMAGEM_PRINCIPAL_OBTER -> {
                props.put("idProduto", prop("INTEGER", "ID do produto"));
                required.add("idProduto");
            }
            case PRODUTO_IMAGEM_DEFINIR_PRINCIPAL -> {
                props.put("idProduto", prop("INTEGER", "ID do produto"));
                props.put("idImagem", prop("INTEGER", "ID da imagem a ser promovida a principal"));
                required.addAll(Arrays.asList("idProduto", "idImagem"));
            }
            case PRODUTO_IMAGEM_REMOVER -> {
                props.put("idImagem", prop("INTEGER", "ID da imagem a excluir"));
                props.put("removerArquivoFisico", prop("BOOLEAN", "Se deve deletar o arquivo do disco (padrão: true)"));
                required.add("idImagem");
            }
            case PESSOA_SIMPLES_CADASTRAR -> {
                props.put("nome", prop("STRING", "Nome ou Razão Social do cliente ou fornecedor"));
                props.put("papel", prop("STRING", "Papel da pessoa no sistema: 'FORNECEDOR', 'CLIENTE' ou 'AMBOS' (padrão: FORNECEDOR)"));
                props.put("isCliente", prop("BOOLEAN", "Indica se a pessoa pode ser cliente (opcional)"));
                props.put("isFornecedor", prop("BOOLEAN", "Indica se a pessoa é fornecedor (opcional, padrão true se fornecedor)"));
                props.put("documento", prop("STRING", "CPF ou CNPJ (TOTALMENTE OPCIONAL)"));
                props.put("limite", prop("STRING", "Limite de crédito em R$ (opcional, padrão: 0.00)"));
                required.add("nome");
            }
            case PESSOA_CLIENTE_PF_CADASTRAR -> {
                props.put("nome", prop("STRING", "Nome completo do cliente"));
                props.put("cpf", prop("STRING", "CPF no formato 000.000.000-00 (opcional)"));
                props.put("limite", prop("STRING", "Limite de crédito em R$ (padrão: 0.00)"));
                required.add("nome");
            }
            case PESSOA_CLIENTE_PJ_CADASTRAR -> {
                props.put("razaoSocial", prop("STRING", "Razão Social ou Nome Fantasia"));
                props.put("cnpj", prop("STRING", "CNPJ no formato 00.000.000/0000-00 (opcional)"));
                props.put("limite", prop("STRING", "Limite de crédito em R$ (padrão: 0.00)"));
                required.add("razaoSocial");
            }
            case PESSOA_CLIENTE_ATUALIZAR_LIMITE -> {
                props.put("idPessoa", prop("INTEGER", "ID da pessoa cliente"));
                props.put("novoLimite", prop("STRING", "Novo limite de crédito em R$"));
                required.addAll(Arrays.asList("idPessoa", "novoLimite"));
            }
            case PESSOA_DADOS_ATUALIZAR -> {
                props.put("idPessoa", prop("INTEGER", "ID da pessoa (cliente ou fornecedor)"));
                props.put("nome", prop("STRING", "Novo nome completo ou razão social"));
                required.addAll(Arrays.asList("idPessoa", "nome"));
            }
            case PESSOA_FORNECEDOR_PJ_CADASTRAR -> {
                props.put("razaoSocial", prop("STRING", "Razão Social do fornecedor"));
                props.put("cnpj", prop("STRING", "CNPJ do fornecedor (TOTALMENTE OPCIONAL - NÃO OBRIGATÓRIO)"));
                required.add("razaoSocial");
            }
            case PESSOA_FORNECEDOR_PF_CADASTRAR -> {
                props.put("nome", prop("STRING", "Nome do produtor/fornecedor físico"));
                props.put("cpf", prop("STRING", "CPF do fornecedor (opcional)"));
                required.add("nome");
            }
            case PESSOA_REGISTRO_ADICIONAR -> {
                props.put("idPessoa", prop("INTEGER", "ID da pessoa"));
                props.put("tipoDocumento", prop("STRING", "Tipo de documento (RG, IE, IM, CNH)"));
                props.put("numeroRegistro", prop("STRING", "Número do registro"));
                required.addAll(Arrays.asList("idPessoa", "tipoDocumento", "numeroRegistro"));
            }
            case PESSOA_BUSCAR -> {
                props.put("termo", prop("STRING", "Termo de busca: nome/razão social, CPF ou CNPJ"));
                props.put("papel", prop("STRING", "Filtro opcional: 'CLIENTE', 'FORNECEDOR' ou 'TODOS'"));
                required.add("termo");
            }
            case PESSOA_HISTORICO_COMPLETO -> {
                props.put("idPessoa", prop("INTEGER", "ID da pessoa (opcional se informar termo)"));
                props.put("termo", prop("STRING", "Nome, CPF ou CNPJ da pessoa para localizar seu dossiê completo (opcional se informar idPessoa)"));
                props.put("papel", prop("STRING", "Filtro opcional: 'CLIENTE' ou 'FORNECEDOR'"));
            }
            case PESSOA_CLIENTE_EXCLUIR -> {
                props.put("idPessoa", prop("INTEGER", "ID do cliente/pessoa a ser excluído permanentemente"));
                required.add("idPessoa");
            }
            case PAGAMENTO_FORMA_CADASTRAR -> {
                props.put("codigo", prop("STRING", "Código identificador único da forma (ex: PIX, DINHEIRO, VALE_REFEICAO)"));
                props.put("nome", prop("STRING", "Nome legível da forma de pagamento"));
                props.put("descricao", prop("STRING", "Descrição da forma"));
                props.put("permiteAVista", prop("BOOLEAN", "Permite pagamento à vista (padrão: true)"));
                props.put("permiteAPrazo", prop("BOOLEAN", "Permite pagamento a prazo (padrão: false)"));
                props.put("permiteParcelado", prop("BOOLEAN", "Permite pagamento parcelado (padrão: false)"));
                props.put("maxParcelas", prop("INTEGER", "Máximo de parcelas permitidas (padrão: 1)"));
                props.put("maxParcelasSemJuros", prop("INTEGER", "Máximo de parcelas sem juros (padrão: 1)"));
                props.put("taxaOperadoraPercentual", prop("STRING", "Taxa MDR % da operadora (padrão: 0.0000)"));
                props.put("tarifaFixaOperadora", prop("STRING", "Tarifa fixa por transação em R$ (padrão: 0.0000)"));
                props.put("descontoAVistaPercentual", prop("STRING", "Desconto à vista em % (padrão: 0.0000)"));
                props.put("taxaJurosMensal", prop("STRING", "Taxa de juros mensal % (padrão: 0.0000)"));
                required.addAll(Arrays.asList("codigo", "nome"));
            }
            case PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS -> {
                props.put("codigo", prop("STRING", "Código da forma de pagamento (PIX, DINHEIRO, CARTAO_CREDITO, etc.)"));
                props.put("taxaOperadoraPercentual", prop("STRING", "Taxa MDR % da operadora"));
                props.put("tarifaFixaOperadora", prop("STRING", "Tarifa fixa por transação em R$"));
                props.put("descontoAVistaPercentual", prop("STRING", "Desconto à vista em %"));
                props.put("maxParcelas", prop("INTEGER", "Número máximo de parcelas permitidas"));
                props.put("maxParcelasSemJuros", prop("INTEGER", "Parcelas sem juros"));
                props.put("taxaJurosMensal", prop("STRING", "Taxa de juros mensal %"));
                required.add("codigo");
            }
            case PAGAMENTO_FORMA_ALTERAR_STATUS -> {
                props.put("codigo", prop("STRING", "Código da forma de pagamento"));
                props.put("ativo", prop("BOOLEAN", "True para ativar, False para desativar"));
                required.addAll(Arrays.asList("codigo", "ativo"));
            }
            case PAGAMENTO_FORMA_EXCLUIR -> {
                props.put("codigo", prop("STRING", "Código da forma de pagamento a ser excluída permanentemente"));
                required.add("codigo");
            }
            case PAGAMENTO_CRIAR_A_VISTA -> {
                props.put("idNota", prop("INTEGER", "ID da nota de venda"));
                props.put("codigoForma", prop("STRING", "Código da forma (PIX ou DINHEIRO)"));
                props.put("valorRecebido", prop("STRING", "Valor em dinheiro entregue pelo cliente"));
                required.addAll(Arrays.asList("idNota", "codigoForma", "valorRecebido"));
            }
            case PAGAMENTO_CRIAR_A_PRAZO -> {
                props.put("idNota", prop("INTEGER", "ID da nota de venda"));
                props.put("codigoForma", prop("STRING", "Código da forma (BOLETO_BANCARIO)"));
                props.put("diasVencimento", prop("INTEGER", "Prazo em dias até o vencimento"));
                required.addAll(Arrays.asList("idNota", "codigoForma", "diasVencimento"));
            }
            case PAGAMENTO_CRIAR_PARCELADO -> {
                props.put("idNota", prop("INTEGER", "ID da nota de venda"));
                props.put("codigoForma", prop("STRING", "Código da forma (CARTAO_CREDITO)"));
                props.put("quantidadeParcelas", prop("INTEGER", "Número de parcelas"));
                required.addAll(Arrays.asList("idNota", "codigoForma", "quantidadeParcelas"));
            }
            case PAGAMENTO_QUITAR_TITULO_OU_PARCELA -> {
                props.put("idNotaPagamento", prop("INTEGER", "ID do pagamento"));
                props.put("numeroParcela", prop("INTEGER", "Número da parcela específica (opcional se for título único)"));
                props.put("dataQuitacao", prop("STRING", "Data da liquidação no formato YYYY-MM-DD"));
                required.add("idNotaPagamento");
            }
            case ESTOQUE_BUSCAR_LOTE_ESPECIFICO -> {
                props.put("idItemLote", prop("INTEGER", "ID do lote específico em estoque"));
                required.add("idItemLote");
            }
            case ESTOQUE_BAIXAR_PERDA_DIRETA -> {
                props.put("idItemLote", prop("INTEGER", "ID do lote que sofreu perda"));
                props.put("quantidadePerda", prop("STRING", "Quantidade física perdida"));
                props.put("motivoPerda", prop("STRING", "Motivo detalhado (validade, quebra, avaria térmica)"));
                required.addAll(Arrays.asList("idItemLote", "quantidadePerda", "motivoPerda"));
            }
            case NOTA_COMPRA_EMITIR -> {
                props.put("idFornecedor", prop("INTEGER", "ID do fornecedor"));
                props.put("descricao", prop("STRING", "Descrição da nota de compra"));
                props.put("itens", propArrayItens("Lista de itens comprados contendo idProduto, valorUnitario, quantidade e validade", true, false));
                required.addAll(Arrays.asList("idFornecedor", "itens"));
            }
            case NOTA_VENDA_EMITIR_FIFO, NOTA_VENDA_PROCEDURE_MYSQL_FIFO -> {
                props.put("idCliente", prop("INTEGER", "ID do cliente (ou omita para balcão padrão)"));
                props.put("descricao", prop("STRING", "Descrição da venda"));
                props.put("descontoGlobal", prop("STRING", "Desconto global na nota em R$"));
                props.put("itens", propArrayItens("Lista de itens vendidos contendo idProduto, quantidade e desconto", false, true));
                required.add("itens");
            }
            case NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> {
                props.put("descricao", prop("STRING", "Descrição da venda"));
                props.put("itens", propArrayItens("Lista de itens a vender no balcão contendo idProduto, quantidade e desconto", false, true));
                required.add("itens");
            }
            case NOTA_VENDA_EMITIR_LOTE_DESEJAVEL, NOTA_VENDA_PROCEDURE_MYSQL_LOTE -> {
                props.put("idCliente", prop("INTEGER", "ID do cliente"));
                props.put("idItemLoteDesejavel", prop("INTEGER", "ID do lote específico"));
                props.put("quantidade", prop("STRING", "Quantidade vendida deste lote"));
                required.addAll(Arrays.asList("idItemLoteDesejavel", "quantidade"));
            }
            case NOTA_DEVOLUCAO_REGISTRAR -> {
                props.put("idNotaVenda", prop("INTEGER", "ID da nota de venda de origem"));
                props.put("idItemVenda", prop("INTEGER", "ID do item devolvido"));
                props.put("quantidadeDevolvida", prop("STRING", "Quantidade devolvida"));
                props.put("motivo", prop("STRING", "Motivo da devolução"));
                required.addAll(Arrays.asList("idNotaVenda", "idItemVenda", "quantidadeDevolvida"));
            }
            case ESTATISTICA_DEMANDA_MENSAL_CLIENTE -> {
                props.put("idCliente", prop("INTEGER", "ID do cliente"));
                props.put("mesReferencia", prop("INTEGER", "Mês de referência (1 a 12, padrão: mês atual)"));
                required.add("idCliente");
            }
            case ESTATISTICA_PERFIL_CLIENTE -> {
                props.put("idCliente", prop("INTEGER", "ID do cliente (opcional para analisar todos)"));
            }
            case ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS, ESTATISTICA_RANKING_FORNECEDORES -> {
                props.put("idProduto", prop("INTEGER", "ID do produto (opcional para ranking geral)"));
            }
            case ESTATISTICA_MOTIVOS_PERDA_PREMATURA -> {
                props.put("idProduto", prop("INTEGER", "ID do produto (opcional)"));
                props.put("diasRetroativos", prop("INTEGER", "Janela histórica de análise em dias (padrão: 365)"));
            }
            case ESTATISTICA_CAUSAS_EXTERNAS_NOTA -> {
                props.put("idNota", prop("INTEGER", "ID da nota gerada"));
                required.add("idNota");
            }
            case ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS -> {
                props.put("idProduto", prop("INTEGER", "ID do produto para planejamento (opcional)"));
            }
            default -> {
                // Rotas de listagem / relatórios sem parâmetros obrigatórios
            }
        }

        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> propArrayItens(String description, boolean incluirValidade, boolean incluirDesconto) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "ARRAY");
        p.put("description", description);

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "OBJECT");

        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("idProduto", prop("INTEGER", "ID do produto no catálogo"));
        itemProps.put("quantidade", prop("STRING", "Quantidade fracionada ou inteira (ex: '1.000000')"));
        itemProps.put("valorUnitario", prop("STRING", "Preço unitário em R$"));
        if (incluirDesconto) {
            itemProps.put("desconto", prop("STRING", "Desconto específico no item em R$"));
        }
        if (incluirValidade) {
            itemProps.put("dataValidade", prop("STRING", "Data de validade do lote (YYYY-MM-DD)"));
        }

        itemSchema.put("properties", itemProps);
        itemSchema.put("required", Arrays.asList("idProduto", "quantidade"));

        p.put("items", itemSchema);
        return p;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
