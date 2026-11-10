package br.com.senac.controller;

/**
 * Enumeração que mapeia todas as rotas/opções possíveis de controle do Sistema de Vendas Sazonalisei.
 */
public enum RotaSistema {

    // 1. Inicialização do Sistema
    SISTEMA_INICIALIZAR_DADOS_PADRAO("SISTEMA", "/sistema/inicializar", "Executa script de carga inicial (Unidades de Medida, Formas de Pagamento e Cliente Padrão)"),

    // 2. Rotas de Produtos, Unidades de Medida, Dimensões e Kits
    PRODUTO_UNIDADE_LISTAR("PRODUTO", "/produtos/unidades/listar", "Lista todas as Unidades de Medida (Um) cadastradas"),
    PRODUTO_UNIDADE_CADASTRAR("PRODUTO", "/produtos/unidades/cadastrar", "Cadastra nova Unidade de Medida com fator de fracionamento"),
    PRODUTO_UNIDADE_ATUALIZAR("PRODUTO", "/produtos/unidades/atualizar", "Atualiza os dados de uma Unidade de Medida existente"),
    PRODUTO_UNIDADE_EXCLUIR("PRODUTO", "/produtos/unidades/excluir", "Exclui definitivamente uma Unidade de Medida cadastrada pela sigla"),
    PRODUTO_FISICO_CADASTRAR("PRODUTO", "/produtos/fisico/cadastrar", "Cadastra Produto Físico com dimensões (Altura, Largura, Comprimento, Peso Bruto/Líquido e Cubagem)"),
    PRODUTO_FISICO_ATUALIZAR_DIMENSOES("PRODUTO", "/produtos/fisico/dimensoes", "Atualiza dimensões físicas e recalcula volume (cm³/m³) e peso taxável"),
    PRODUTO_DIGITAL_CADASTRAR("PRODUTO", "/produtos/digital/cadastrar", "Cadastra Produto Digital com links de entrega/acesso"),
    PRODUTO_KIT_ADICIONAR_COMPONENTE("PRODUTO", "/produtos/kit/componente", "Adiciona item componente e proporção a um Kit/Conjunto"),
    PRODUTO_CATALOGO_LISTAR("PRODUTO", "/produtos/catalogo/listar", "Lista todos os produtos do catálogo"),
    PRODUTO_CATALOGO_BUSCAR("PRODUTO", "/produtos/catalogo/buscar", "Busca produto por ID ou Nome"),
    PRODUTO_CATALOGO_ATUALIZAR("PRODUTO", "/produtos/catalogo/atualizar", "Atualiza o nome e descrição cadastrais de um produto no catálogo"),
    PRODUTO_CATALOGO_ATUALIZAR_PRECO("PRODUTO", "/produtos/catalogo/atualizar-preco", "Atualiza o preço de venda de um produto no catálogo"),
    PRODUTO_CATALOGO_EXCLUIR("PRODUTO", "/produtos/catalogo/excluir", "Exclui definitivamente um produto do catálogo pelo ID"),
    PRODUTO_IMAGEM_ADICIONAR("PRODUTO", "/produtos/imagens/adicionar", "Adiciona imagem ao produto com nome único e validação de formatos (PNG, JPG, JPEG, WEBP, GIF, SVG, BMP)"),
    PRODUTO_IMAGEM_LISTAR("PRODUTO", "/produtos/imagens/listar", "Lista todas as imagens de um produto ordenadas por principal e galeria"),
    PRODUTO_IMAGEM_PRINCIPAL_OBTER("PRODUTO", "/produtos/imagens/principal", "Obtém a imagem principal de exibição do produto para JavaFX"),
    PRODUTO_IMAGEM_DEFINIR_PRINCIPAL("PRODUTO", "/produtos/imagens/definir-principal", "Define uma imagem específica como principal do produto"),
    PRODUTO_IMAGEM_REMOVER("PRODUTO", "/produtos/imagens/remover", "Remove a imagem do banco e exclui o arquivo físico no disco"),

    // 3. Rotas de Pessoas (Cliente Padrão, Clientes PF/PJ, Fornecedores e Limites)
    PESSOA_CLIENTE_PADRAO_OBTER("PESSOA", "/pessoas/clientes/padrao", "Obtém o Cliente Padrão (CONSUMIDOR FINAL - CPF 000.000.000-00) para vendas sem cadastro"),
    PESSOA_SIMPLES_CADASTRAR("PESSOA", "/pessoas/cadastrar", "Cadastra pessoa somente com o nome e a opção de ser cliente, fornecedor ou ambos (documento opcional)"),
    PESSOA_CLIENTE_PF_CADASTRAR("PESSOA", "/pessoas/clientes/pf/cadastrar", "Cadastra ou atualiza Cliente Pessoa Física com limite de crédito"),
    PESSOA_CLIENTE_PJ_CADASTRAR("PESSOA", "/pessoas/clientes/pj/cadastrar", "Cadastra ou atualiza Cliente Pessoa Jurídica com limite de crédito"),
    PESSOA_CLIENTE_ATUALIZAR_LIMITE("PESSOA", "/pessoas/clientes/limite", "Atualiza limite de crédito do cliente registrado"),
    PESSOA_DADOS_ATUALIZAR("PESSOA", "/pessoas/atualizar", "Atualiza os dados cadastrais (nome/razão social) de uma pessoa pelo ID"),
    PESSOA_FORNECEDOR_PJ_CADASTRAR("PESSOA", "/pessoas/fornecedores/pj/cadastrar", "Cadastra Fornecedor Pessoa Jurídica (CNPJ)"),
    PESSOA_FORNECEDOR_PF_CADASTRAR("PESSOA", "/pessoas/fornecedores/pf/cadastrar", "Cadastra Fornecedor Produtor Rural / Pessoa Física (CPF)"),
    PESSOA_REGISTRO_ADICIONAR("PESSOA", "/pessoas/registros/adicionar", "Adiciona documento auxiliar (IE, IM, RG, CNH, Título Eleitoral) à Pessoa"),
    PESSOA_LISTAR_CLIENTES("PESSOA", "/pessoas/clientes/listar", "Lista todos os clientes cadastrados"),
    PESSOA_LISTAR_FORNECEDORES("PESSOA", "/pessoas/fornecedores/listar", "Lista todos os fornecedores cadastrados"),
    PESSOA_BUSCAR("PESSOA", "/pessoas/buscar", "Busca clientes e/ou fornecedores por termo (nome, CPF ou CNPJ)"),
    PESSOA_HISTORICO_COMPLETO("PESSOA", "/pessoas/historico-completo", "Consulta histórico 360º de cliente/fornecedor: cadastro, notas vinculadas, itens de produtos e pagamentos"),
    PESSOA_CLIENTE_EXCLUIR("PESSOA", "/pessoas/clientes/excluir", "Exclui cliente/pessoa do sistema pelo ID"),

    // 4. Rotas de Formas de Pagamento Customizáveis e Títulos Financeiros
    PAGAMENTO_FORMA_LISTAR_ATIVAS("PAGAMENTO", "/pagamentos/formas/ativas", "Lista todas as Formas de Pagamento ativas na tabela forma_pagamento"),
    PAGAMENTO_FORMA_CADASTRAR("PAGAMENTO", "/pagamentos/formas/cadastrar", "Cadastra nova Forma de Pagamento no sistema com parâmetros contábeis"),
    PAGAMENTO_FORMA_CUSTOMIZAR_REGRAS("PAGAMENTO", "/pagamentos/formas/customizar", "Customiza taxas MDR %, tarifa fixa, descontos à vista, limite de parcelas e juros na tabela forma_pagamento"),
    PAGAMENTO_FORMA_ALTERAR_STATUS("PAGAMENTO", "/pagamentos/formas/status", "Ativa ou desativa uma Forma de Pagamento na tabela forma_pagamento"),
    PAGAMENTO_FORMA_EXCLUIR("PAGAMENTO", "/pagamentos/formas/excluir", "Exclui uma Forma de Pagamento cadastrada pelo código"),
    PAGAMENTO_CRIAR_A_VISTA("PAGAMENTO", "/pagamentos/criar/a-vista", "Cria Pagamento À Vista aplicando desconto parametrizado e cálculo de troco BACEN"),
    PAGAMENTO_CRIAR_A_PRAZO("PAGAMENTO", "/pagamentos/criar/a-prazo", "Cria Pagamento A Prazo com vencimento, juros moratórios e multa parametrizados"),
    PAGAMENTO_CRIAR_PARCELADO("PAGAMENTO", "/pagamentos/criar/parcelado", "Cria Pagamento Parcelado validando mínimo por parcela e gerando parcelas BACEN (HALF_EVEN)"),
    PAGAMENTO_QUITAR_TITULO_OU_PARCELA("PAGAMENTO", "/pagamentos/quitar", "Registra a quitação/liquidação de título a prazo ou parcela específica"),

    // 5. Rotas de Controle de Estoque e Lotes FIFO
    ESTOQUE_SALDO_CONSOLIDADO("ESTOQUE", "/estoque/saldo-consolidado", "Consulta saldo total disponível na View vw_saldo_consolidado_estoque"),
    ESTOQUE_FILA_LOTES_FIFO("ESTOQUE", "/estoque/lotes-fifo", "Lista a fila de lotes disponíveis na ordem FIFO (vw_lotes_fifo_disponiveis)"),
    ESTOQUE_BUSCAR_LOTE_ESPECIFICO("ESTOQUE", "/estoque/lote/buscar", "Busca um lote específico pelo ID para conferência ou venda dirigida"),
    ESTOQUE_LOTES_PROXIMOS_VALIDADE("ESTOQUE", "/estoque/lotes/validade", "Lista lotes com saldo disponível próximos da data de vencimento"),
    ESTOQUE_BAIXAR_PERDA_DIRETA("ESTOQUE", "/estoque/perda/baixar", "Baixa perda física diretamente do saldo disponível de um lote em estoque"),

    // 6. Rotas de Notas Operacionais (Compra, Venda FIFO, Venda Lote Desejável, Venda Balcão, Devolução e Perda)
    NOTA_COMPRA_EMITIR("NOTA", "/notas/compra/emitir", "Emite NotaCompra liberando novos lotes na fila FIFO e registrando snapshot sazonal"),
    NOTA_COMPRA_ABATER_PERDAS_FORNECEDOR("NOTA", "/notas/compra/abater-perdas", "Abate em nova NotaCompra as perdas pós-venda dentro da validade do mesmo fornecedor"),
    NOTA_COMPRA_SUGERIR_PRECO_VENDA("NOTA", "/notas/compra/sugerir-preco", "Calcula sugestão de preço de venda sobre o custo dos lotes comprados"),
    NOTA_VENDA_EMITIR_FIFO("NOTA", "/notas/venda/emitir-fifo", "Emite NotaVenda consumindo lotes mais antigos via Algoritmo FIFO"),
    NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO("NOTA", "/notas/venda/emitir-balcao-padrao", "Emite NotaVenda de balcão atribuindo automaticamente o Cliente Padrão não registrado"),
    NOTA_VENDA_EMITIR_LOTE_DESEJAVEL("NOTA", "/notas/venda/emitir-lote-desejavel", "Emite NotaVenda escolhendo manualmente o Lote Desejável específico"),
    NOTA_VENDA_PROCEDURE_MYSQL_FIFO("NOTA", "/notas/venda/procedure-fifo", "Executa baixa de estoque via Stored Procedure MySQL sp_consumir_estoque_fifo"),
    NOTA_VENDA_PROCEDURE_MYSQL_LOTE("NOTA", "/notas/venda/procedure-lote", "Executa baixa de lote específico via Stored Procedure MySQL sp_consumir_lote_especifico"),
    NOTA_DEVOLUCAO_REGISTRAR("NOTA", "/notas/devolucao/registrar", "Registra NotaDevolucao reintegrando lote válido ou gerando NotaPerda pós-venda para o fornecedor"),

    // 7. Rotas de Relatórios Contábeis (Padrão BACEN) e Inteligência Sazonal
    RELATORIO_LUCRO_CONTABIL_BACEN("RELATORIO", "/relatorios/lucro-contabil-bacen", "Consulta View vw_apuracao_lucro_contabil (CMV, Receita Líquida, Taxas, Lucro Líquido e Margem % BACEN)"),
    RELATORIO_PERDAS_POR_FORNECEDOR("RELATORIO", "/relatorios/perdas-fornecedor", "Consulta View vw_perdas_por_fornecedor (perdas pós-venda dentro da validade por fornecedor e produto)"),
    // 8. Inteligência Estatística e Estratégica (processada em Stored Procedures MySQL)
    ESTATISTICA_DEMANDA_MENSAL_CLIENTE("ESTATISTICA", "/estatisticas/demanda-mensal-cliente", "Prevê a demanda do mês do cliente por produto (média, tendência, sazonalidade, desvio-padrão)"),
    ESTATISTICA_PERFIL_CLIENTE("ESTATISTICA", "/estatisticas/perfil-cliente", "Perfil do cliente: quando, em qual clima/evento/5º dia útil e turno consome mais"),
    ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS("ESTATISTICA", "/estatisticas/impacto-externo", "Custo conforme nível dos rios e produtos afetados por clima, eventos e dias"),
    ESTATISTICA_RANKING_FORNECEDORES("ESTATISTICA", "/estatisticas/ranking-fornecedores", "Ranking de fornecedores do mesmo produto por custo, qualidade, perda e reposição"),
    ESTATISTICA_MOTIVOS_PERDA_PREMATURA("ESTATISTICA", "/estatisticas/motivos-perda", "Diagnostica prováveis motivos de produto estragar antes da validade"),
    ESTATISTICA_DESTAQUES_SAZONAIS("ESTATISTICA", "/estatisticas/destaques-sazonais", "Produtos que se destacam por evento, clima, rio, 5º dia útil e fim de semana"),
    ESTATISTICA_PICOS_DIAS_EVENTOS("ESTATISTICA", "/estatisticas/picos-dias", "Dias de maior pico de venda (5º dia útil, eventos, fim de semana)"),
    ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS("ESTATISTICA", "/estatisticas/plano-estrategico", "Datas ideais de compra, quantidade, fornecedor campeão e preço de venda estratégico"),
    ESTATISTICA_CAUSAS_EXTERNAS_NOTA("ESTATISTICA", "/estatisticas/causas-externas-nota", "Causas externas por demanda de uma nota: fatores ativos, eventos reais, efeito estatístico por produto, notas e produtos relacionados"),
    ESTATISTICA_RECALCULAR_FATORES("ESTATISTICA", "/estatisticas/recalcular-fatores", "Recalcula no banco o efeito estatístico (lift e teste de Welch) de cada fator externo por produto"),
    ESTATISTICA_FATORES_PRODUTO("ESTATISTICA", "/estatisticas/fatores-produto", "Lista o efeito estatístico dos fatores externos sobre a demanda de cada produto"),
    FATOR_EXTERNO_EVENTOS_LISTAR("FATOR_EXTERNO", "/fatores-externos/eventos", "Lista os eventos reais (Sympla/MinC) gravados no banco e quantos snapshots os citam"),
    FATOR_EXTERNO_RESOLVER_PENDENCIAS("FATOR_EXTERNO", "/fatores-externos/resolver-pendencias", "Resolve a fila de notas sem fator externo com uma única obtenção por janela de 6h"),

    RELATORIO_ANALISE_SAZONAL_VENDAS("RELATORIO", "/relatorios/analise-sazonal", "Consulta View vw_analise_sazonal_vendas (Clima 6h, Rios 6h, Eventos 7d e Faturamento)");

    private final String modulo;
    private final String caminhoRota;
    private final String descricaoOperacao;

    RotaSistema(String modulo, String caminhoRota, String descricaoOperacao) {
        this.modulo = modulo;
        this.caminhoRota = caminhoRota;
        this.descricaoOperacao = descricaoOperacao;
    }

    public String getModulo() {
        return modulo;
    }

    public String getCaminhoRota() {
        return caminhoRota;
    }

    public String getDescricaoOperacao() {
        return descricaoOperacao;
    }

    /**
     * Retorna o identificador snake_case compatível com Function Calling / Tool Declarations da API Gemini.
     */
    public String getNomeFuncaoGemini() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return String.format("[%s] %-38s -> %s", modulo, caminhoRota, descricaoOperacao);
    }
}
