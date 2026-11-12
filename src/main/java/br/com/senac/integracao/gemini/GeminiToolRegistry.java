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

    private static List<Map<String, Object>> cacheDeclaracoesTools;
    private static List<Map<String, Object>> cacheToolsWrapper;

    /**
     * Retorna a lista completa de Function Declarations compatíveis com a especificação da API Gemini.
     */
    public static synchronized List<Map<String, Object>> gerarDeclaracoesTodasTools() {
        if (cacheDeclaracoesTools == null) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (RotaSistema rota : RotaSistema.values()) {
                Map<String, Object> func = new LinkedHashMap<>();
                func.put("name", rota.getNomeFuncaoGemini());
                func.put("description", construirDescricaoEnriquecida(rota));
                func.put("parameters", mapearParametrosRota(rota));
                tools.add(func);
            }
            cacheDeclaracoesTools = Collections.unmodifiableList(tools);
        }
        return cacheDeclaracoesTools;
    }

    /**
     * Retorna a lista encapsulada dentro da chave 'tools' pronta para envio ao endpoint do Gemini.
     */
    public static synchronized List<Map<String, Object>> gerarToolsWrapper() {
        if (cacheToolsWrapper == null) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("function_declarations", gerarDeclaracoesTodasTools());
            cacheToolsWrapper = Collections.singletonList(wrapper);
        }
        return cacheToolsWrapper;
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
                nome.contains("DEFINIR_DATA") || nome.contains("ALTERAR_STATUS") || nome.contains("CUSTOMIZAR")) {
            return NivelRiscoOperacao.WRITE_SEGURO;
        }
        return NivelRiscoOperacao.READ_ONLY;
    }

    /**
     * Gera o System Instruction padrão a partir do ContextoIABuilder (config/ia-contexto.properties),
     * respeitando o Princípio da Responsabilidade Única (SRP).
     */
    public static Map<String, Object> gerarSystemInstruction() {
        return br.com.senac.integracao.ia.ContextoIABuilder.construirSystemInstruction();
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
            case BUSCA_PADRAO_IA -> {
                props.put("entidade", prop("STRING", "Entidade/DAO alvo da busca padrão: 'PRODUTO', 'PRODUTO_ESTOQUE_VALIDO', 'PRODUTOS_ESTOQUE_VALIDO', 'PESSOA', 'REGISTRO', 'LOTE_VALIDO', 'LOTES_VALIDOS', 'NOTA', 'UNIDADE_MEDIDA', 'FORMA_PAGAMENTO', 'FATOR_EXTERNO_NOTAS'"));
                props.put("id", prop("INTEGER", "ID numérico único do registro (opcional se informar termo/nome/documento)"));
                props.put("termo", prop("STRING", "Termo de busca: nome do produto, nome da pessoa, número de registro/CPF/CNPJ, sigla ou código (opcional)"));
                props.put("unico", prop("BOOLEAN", "True para retornar informação única (1 registro com fallback), False para listagem (padrão: true quando houver id/termo)"));
                props.put("apenasEstoqueValido", prop("BOOLEAN", "True para filtrar apenas produtos/lotes com estoque válido disponível e dentro da validade"));
                required.add("entidade");
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
                props.put("descricao", prop("STRING", "Descrição detalhada do produto. Ao cadastrar um kit composto, chame também produto_kit_adicionar_componente para incluir os itens componentes na mesma resposta"));
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
                props.put("idProdutoKit", prop("INTEGER", "ID numérico do produto kit/conjunto (ou 'OP-1' se o kit foi agendado para criação no mesmo lote)"));
                props.put("idProdutoComponente", prop("INTEGER", "ID numérico do produto componente"));
                props.put("quantidade", prop("STRING", "Quantidade fracionada do componente"));
                required.addAll(Arrays.asList("idProdutoKit", "idProdutoComponente", "quantidade"));
            }
            case PRODUTO_CATALOGO_LISTAR -> {
                props.put("apenasEstoqueValido", prop("BOOLEAN", "Se true, lista apenas produtos que possuem estoque válido (saldo FIFO > 0 e dentro da validade)"));
            }
            case PRODUTO_CATALOGO_BUSCAR -> {
                props.put("id", prop("INTEGER", "ID do produto (opcional)"));
                props.put("nome", prop("STRING", "Nome exato ou parcial do produto (opcional)"));
                props.put("termo", prop("STRING", "Termo livre de busca no nome/descrição do produto (opcional)"));
                props.put("unico", prop("BOOLEAN", "Se true, retorna estritamente um único produto (opcional)"));
                props.put("apenasEstoqueValido", prop("BOOLEAN", "Se true, busca somente produto com estoque válido disponível (opcional)"));
            }
            case PRODUTO_BUSCAR_UNICO -> {
                props.put("id", prop("INTEGER", "ID do produto único (opcional se informar nome/termo)"));
                props.put("idProduto", prop("INTEGER", "Alias para ID do produto (opcional)"));
                props.put("nome", prop("STRING", "Nome único ou parcial indexado do produto (opcional se informar id)"));
                props.put("termo", prop("STRING", "Termo de busca do produto (opcional)"));
            }
            case PRODUTO_ESTOQUE_VALIDO_BUSCAR_UNICO -> {
                props.put("id", prop("INTEGER", "ID do produto com estoque válido (opcional se informar nome/termo)"));
                props.put("idProduto", prop("INTEGER", "Alias para ID do produto (opcional)"));
                props.put("nome", prop("STRING", "Nome do produto com estoque válido (opcional se informar id)"));
                props.put("termo", prop("STRING", "Termo de busca para localizar um único produto com estoque válido (opcional)"));
            }
            case PRODUTO_ESTOQUE_VALIDO_LISTAR -> {
            }
            case PRODUTO_CATALOGO_ATUALIZAR -> {
                props.put("idProduto", prop("INTEGER", "ID numérico do produto no catálogo (opcional se informar nomeAtual)"));
                props.put("nomeAtual", prop("STRING", "Nome atual ou parcial do produto para identificação (opcional se informar idProduto)"));
                props.put("novoNome", prop("STRING", "Novo nome cadastral a ser gravado no produto (opcional)"));
                props.put("nome", prop("STRING", "Novo nome do produto (alias para novoNome)"));
                props.put("descricao", prop("STRING", "Nova descrição detalhada do produto (opcional)"));
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                props.put("idProduto", prop("INTEGER", "ID do produto no catálogo (opcional se informar nomeProduto)"));
                props.put("nomeProduto", prop("STRING", "Nome atual ou parcial do produto para identificação (opcional se informar idProduto)"));
                props.put("novoPreco", prop("STRING", "Novo preço de venda em R$ (ex: '49.90')"));
                required.add("novoPreco");
            }
            case PRODUTO_CATALOGO_EXCLUIR -> {
                props.put("idProduto", prop("INTEGER", "ID do produto a ser excluído permanentemente (opcional se informar nomeProduto)"));
                props.put("nomeProduto", prop("STRING", "Nome do produto a ser excluído (opcional se informar idProduto)"));
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
                props.put("idPessoa", prop("INTEGER", "ID da pessoa (opcional se informar termo)"));
                props.put("termo", prop("STRING", "Termo de busca: nome/razão social, CPF, CNPJ ou RG"));
                props.put("papel", prop("STRING", "Filtro opcional: 'CLIENTE', 'FORNECEDOR' ou 'TODOS'"));
                props.put("unico", prop("BOOLEAN", "Se true, retorna apenas uma única pessoa (opcional)"));
            }
            case PESSOA_BUSCAR_UNICA -> {
                props.put("idPessoa", prop("INTEGER", "ID numérico da pessoa (opcional se informar termo/nome/documento)"));
                props.put("id", prop("INTEGER", "Alias para ID da pessoa (opcional)"));
                props.put("termo", prop("STRING", "Nome, CPF, CNPJ ou número de registro único para localizar uma única pessoa"));
                props.put("nome", prop("STRING", "Nome da pessoa (opcional)"));
                props.put("documento", prop("STRING", "Documento único CPF/CNPJ/RG (opcional)"));
            }
            case PESSOA_REGISTRO_BUSCAR_UNICO -> {
                props.put("tipoDocumento", prop("STRING", "Tipo de registro (ex: CPF, CNPJ, RG, CNH, IE - opcional)"));
                props.put("numeroRegistro", prop("STRING", "Número único do documento/registro para localizar a pessoa"));
                props.put("documento", prop("STRING", "Alias para número do documento/registro (opcional)"));
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
            case ESTOQUE_SALDO_CONSOLIDADO -> {
                props.put("idProduto", prop("INTEGER", "ID do produto para consultar saldo total consolidado"));
                required.add("idProduto");
            }
            case ESTOQUE_FILA_LOTES_FIFO -> {
                props.put("idProduto", prop("INTEGER", "ID do produto (opcional; se omitido lista todos os lotes válidos)"));
                props.put("unico", prop("BOOLEAN", "Se true, retorna apenas o primeiro lote FIFO com estoque válido"));
            }
            case ESTOQUE_LOTES_VALIDOS_LISTAR -> {
                props.put("idProduto", prop("INTEGER", "ID do produto para filtrar lotes válidos (opcional; se omitido lista todos os lotes com estoque válido)"));
            }
            case ESTOQUE_LOTE_VALIDO_BUSCAR_UNICO -> {
                props.put("idProduto", prop("INTEGER", "ID do produto para buscar o primeiro lote único com estoque válido (opcional se informar idLote)"));
                props.put("idLote", prop("INTEGER", "ID específico do lote (opcional)"));
            }
            case ESTOQUE_BUSCAR_LOTE_ESPECIFICO -> {
                props.put("idItemLote", prop("INTEGER", "ID do lote específico em estoque (opcional se informar idLote ou idProduto)"));
                props.put("idLote", prop("INTEGER", "Alias para ID do lote específico em estoque"));
            }
            case ESTOQUE_LOTES_PROXIMOS_VALIDADE -> {
                props.put("dataLimite", prop("STRING", "Data limite de vencimento no formato YYYY-MM-DD (opcional)"));
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
            case NOTA_BUSCAR_UNICA -> {
                props.put("idNota", prop("INTEGER", "ID numérico da nota operacional (Compra, Venda, Devolução ou Perda)"));
                props.put("id", prop("INTEGER", "Alias para ID da nota (opcional)"));
            }
            case NOTA_LISTAR_PADRAO -> {
                props.put("idPessoa", prop("INTEGER", "ID do cliente ou fornecedor para filtrar notas (opcional)"));
                props.put("tipoNota", prop("STRING", "Filtro opcional por tipo: 'COMPRA', 'VENDA', 'DEVOLUCAO', 'PERDA' ou 'TODAS'"));
            }
            case NOTA_DEFINIR_DATA -> {
                props.put("idNota", prop("INTEGER", "ID da nota operacional que terá a data definida/atualizada"));
                props.put("data", prop("STRING", "Nova data/hora da nota no formato YYYY-MM-DD ou YYYY-MM-DDTHH:mm:ss"));
                required.addAll(Arrays.asList("idNota", "data"));
            }
            case FATOR_EXTERNO_RELATORIO_INTEGRADO_NOTAS -> {
                props.put("idNota", prop("INTEGER", "ID de uma nota específica para citar no relatório integrado (opcional; se omitido consolida todas as notas)"));
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
