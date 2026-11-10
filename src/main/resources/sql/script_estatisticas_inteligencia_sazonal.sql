-- =====================================================================================================================
-- MOTOR ESTATÍSTICO, CORRELACIONAL E DE INTELIGÊNCIA ESTRATÉGICA NO BANCO DE DADOS (MySQL 8.0+ / sazonalisei)
-- Todo o processamento pesado (médias, desvios-padrão, elasticidades climáticas/hidrológicas, score de fornecedores,
-- diagnóstico de perda prematura e calendário ótimo de compras/vendas) ocorre dentro das Views e Stored Procedures.
-- =====================================================================================================================

USE sazonalisei;

-- =====================================================================================================================
-- 1. VIEWS ANALÍTICAS DE SUPORTE ESTATÍSTICO (OTIMIZADAS PARA AGREGAÇÃO EM SET-BASED SQL)
-- =====================================================================================================================

-- VIEW 6: Perfil Estatístico Consolidado do Cliente (Consumo Histórico, Clima, Eventos, 5º Dia Útil e Horários)
CREATE OR REPLACE VIEW vw_perfil_estatistico_cliente AS
SELECT
    cli.id AS id_cliente,
    cli.nome AS nome_cliente,
    cli.tipo_pessoa,
    COALESCE(cli.cpf, cli.cnpj, 'N/A') AS documento,
    COUNT(DISTINCT nv.id) AS total_compras,
    COALESCE(SUM(iv.quantidade), 0) AS quantidade_total_itens,
    ROUND(COALESCE(SUM((iv.quantidade * iv.valor) - iv.desconto), 0), 2) AS faturamento_liquido_total,
    ROUND(
        COALESCE(SUM((iv.quantidade * iv.valor) - iv.desconto) / NULLIF(COUNT(DISTINCT nv.id), 0), 0),
        2
    ) AS ticket_medio_bacen,
    ROUND(COALESCE(STDDEV_POP((iv.quantidade * iv.valor) - iv.desconto), 0), 2) AS desvio_padrao_item_bacen,
    -- Distribuição por Calendário (5º Dia Útil vs Fim de Semana/Feriado vs Dia Útil Comum)
    ROUND(SUM(CASE WHEN vf.quinto_dia_util = b'1' THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_quinto_dia_util,
    ROUND(SUM(CASE WHEN vf.dia_util = b'0' THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_fim_semana_feriado,
    ROUND(SUM(CASE WHEN vf.dia_util = b'1' AND COALESCE(vf.quinto_dia_util, b'0') = b'0' THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_dia_util_comum,
    -- Distribuição por Clima (Calor >= 30°C, Chuva > 10mm, Clima Ameno/Estável)
    ROUND(SUM(CASE WHEN vf.temp_media >= 30.0 AND COALESCE(vf.precipitacao_mm, 0) <= 10.0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_clima_calor,
    ROUND(SUM(CASE WHEN vf.precipitacao_mm > 10.0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_clima_chuva,
    ROUND(SUM(CASE WHEN COALESCE(vf.temp_media, 27.0) < 30.0 AND COALESCE(vf.precipitacao_mm, 0) <= 10.0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_clima_estavel,
    -- Distribuição por Eventos Regionais (Sympla / MinC)
    ROUND(SUM(CASE WHEN COALESCE(vf.total_eventos_ativos, 0) > 0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_em_eventos,
    -- Distribuição por Nível dos Rios
    ROUND(SUM(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_epoca_seca_rios,
    ROUND(SUM(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s > 1.15) THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS gasto_epoca_cheia_rios,
    MAX(nv.data_gerada) AS ultima_compra_em,
    DATEDIFF(CURDATE(), CAST(MAX(nv.data_gerada) AS DATE)) AS dias_sem_comprar
FROM pessoa cli
INNER JOIN nota nv ON nv.id_pessoa = cli.id AND nv.tipo_nota = 'VENDA'
INNER JOIN item iv ON iv.id_nota = nv.id
LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
WHERE cli.is_cliente = b'1'
GROUP BY cli.id, cli.nome, cli.tipo_pessoa, cli.cpf, cli.cnpj;

-- VIEW 7: Score Estatístico de Fornecedor por Produto (Preço, Durabilidade, Perda na Validade e Reposição)
CREATE OR REPLACE VIEW vw_score_fornecedor_produto AS
WITH compras_forn AS (
    SELECT
        ic.id_produto,
        p.nome AS nome_produto,
        nc.id_pessoa AS id_fornecedor,
        forn.nome AS nome_fornecedor,
        COUNT(DISTINCT nc.id) AS total_notas_compra,
        COUNT(ic.id) AS total_lotes_comprados,
        SUM(ic.quantidade) AS qtd_total_comprada,
        ROUND(SUM(ic.quantidade * ic.valor) / NULLIF(SUM(ic.quantidade), 0), 4) AS custo_unitario_medio_ponderado,
        ROUND(MIN(ic.valor), 4) AS menor_custo_unitario,
        ROUND(MAX(ic.valor), 4) AS maior_custo_unitario,
        ROUND(COALESCE(STDDEV_POP(ic.valor), 0), 4) AS desvio_padrao_custo,
        ROUND(AVG(COALESCE(DATEDIFF(ic.data_validade, CAST(ic.data AS DATE)), 180)), 1) AS durabilidade_media_dias,
        -- Custo por condição dos rios no momento da compra
        ROUND(AVG(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN ic.valor END), 4) AS custo_medio_seca_rio,
        ROUND(AVG(CASE WHEN vf.vazao_media_m3s IS NULL OR vf.vazao_media_m3s = 0 OR vf.vazao_m3s / vf.vazao_media_m3s BETWEEN 0.85 AND 1.15 THEN ic.valor END), 4) AS custo_medio_rio_normal,
        ROUND(AVG(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s > 1.15) THEN ic.valor END), 4) AS custo_medio_cheia_rio,
        -- Custo por condição climática
        ROUND(AVG(CASE WHEN vf.temp_media >= 30.0 THEN ic.valor END), 4) AS custo_medio_calor,
        ROUND(AVG(CASE WHEN vf.precipitacao_mm > 10.0 THEN ic.valor END), 4) AS custo_medio_chuva
    FROM nota nc
    INNER JOIN item ic ON ic.id_nota = nc.id
    INNER JOIN produtos p ON p.id = ic.id_produto
    INNER JOIN pessoa forn ON forn.id = nc.id_pessoa
    LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nc.id_vinculo_fator_externo
    WHERE nc.tipo_nota = 'COMPRA'
    GROUP BY ic.id_produto, p.nome, nc.id_pessoa, forn.nome
),
perdas_forn AS (
    SELECT
        vpf.id_produto,
        vpf.id_fornecedor,
        COUNT(DISTINCT vpf.id_nota_perda) AS total_ocorrencias_perda,
        COALESCE(SUM(CASE WHEN vpf.dentro_periodo_validade = 1 THEN vpf.quantidade_perdida ELSE 0 END), 0) AS qtd_perdida_dentro_validade,
        COALESCE(SUM(CASE WHEN vpf.dentro_periodo_validade = 1 THEN vpf.valor_total_custo_perda ELSE 0 END), 0) AS valor_perda_dentro_validade,
        COALESCE(SUM(CASE WHEN vpf.dentro_periodo_validade = 1 AND vpf.id_nota_compra_abatimento IS NOT NULL THEN vpf.quantidade_perdida ELSE 0 END), 0) AS qtd_perda_reposta_abatida,
        COALESCE(SUM(CASE WHEN vpf.elegivel_abatimento_pos_venda = 1 THEN vpf.valor_total_custo_perda ELSE 0 END), 0) AS credito_pendente_abatimento
    FROM vw_perdas_por_fornecedor vpf
    WHERE vpf.id_fornecedor IS NOT NULL
    GROUP BY vpf.id_produto, vpf.id_fornecedor
),
min_custo_prod AS (
    SELECT id_produto, MIN(custo_unitario_medio_ponderado) AS melhor_custo_mercado
    FROM compras_forn
    GROUP BY id_produto
)
SELECT
    cf.id_produto,
    cf.nome_produto,
    cf.id_fornecedor,
    cf.nome_fornecedor,
    cf.total_notas_compra,
    cf.total_lotes_comprados,
    cf.qtd_total_comprada,
    cf.custo_unitario_medio_ponderado,
    cf.menor_custo_unitario,
    cf.maior_custo_unitario,
    cf.desvio_padrao_custo,
    cf.durabilidade_media_dias,
    cf.custo_medio_seca_rio,
    cf.custo_medio_rio_normal,
    cf.custo_medio_cheia_rio,
    cf.custo_medio_calor,
    cf.custo_medio_chuva,
    COALESCE(pf.qtd_perdida_dentro_validade, 0) AS qtd_perdida_dentro_validade,
    ROUND(COALESCE(pf.valor_perda_dentro_validade, 0), 2) AS valor_perda_dentro_validade,
    COALESCE(pf.qtd_perda_reposta_abatida, 0) AS qtd_perda_reposta_abatida,
    ROUND(COALESCE(pf.credito_pendente_abatimento, 0), 2) AS credito_pendente_abatimento,
    -- Taxa de perda prematura (%) antes da validade
    ROUND(
        LEAST(100.0, (COALESCE(pf.qtd_perdida_dentro_validade, 0) / NULLIF(cf.qtd_total_comprada, 0)) * 100.0),
        2
    ) AS taxa_perda_prematura_pct,
    -- Taxa de eficiência de reposição/abatimento pelo fornecedor (%)
    ROUND(
        CASE
            WHEN COALESCE(pf.qtd_perdida_dentro_validade, 0) = 0 THEN 100.00
            ELSE LEAST(100.0, (COALESCE(pf.qtd_perda_reposta_abatida, 0) / pf.qtd_perdida_dentro_validade) * 100.0)
        END,
        2
    ) AS taxa_reposicao_fornecedor_pct,
    -- Custo Efetivo Ajustado à Qualidade (penaliza perdas não repostas pelo fornecedor)
    ROUND(
        cf.custo_unitario_medio_ponderado /
        GREATEST(0.10, (1.0 - (GREATEST(0, COALESCE(pf.qtd_perdida_dentro_validade, 0) - COALESCE(pf.qtd_perda_reposta_abatida, 0)) / NULLIF(cf.qtd_total_comprada, 0)))),
        4
    ) AS custo_efetivo_ajustado_qualidade,
    -- Coeficiente Global Preço-Produto-Qualidade-Reposição (0 a 100 pontos)
    ROUND(
        (40.0 * COALESCE(mcp.melhor_custo_mercado / NULLIF(cf.custo_unitario_medio_ponderado, 0), 1.0)) +
        (30.0 * GREATEST(0.0, 1.0 - (COALESCE(pf.qtd_perdida_dentro_validade, 0) / NULLIF(cf.qtd_total_comprada, 0)))) +
        (15.0 * LEAST(1.0, cf.durabilidade_media_dias / 90.0)) +
        (15.0 * CASE
            WHEN COALESCE(pf.qtd_perdida_dentro_validade, 0) = 0 THEN 1.0
            ELSE LEAST(1.0, COALESCE(pf.qtd_perda_reposta_abatida, 0) / pf.qtd_perdida_dentro_validade)
        END),
        2
    ) AS coeficiente_score_fornecedor
FROM compras_forn cf
LEFT JOIN perdas_forn pf ON pf.id_produto = cf.id_produto AND pf.id_fornecedor = cf.id_fornecedor
LEFT JOIN min_custo_prod mcp ON mcp.id_produto = cf.id_produto;

-- VIEW 8: Análise Comparativa de Picos de Venda (5º Dia Útil vs Eventos vs Fim de Semana vs Dia Útil Comum)
CREATE OR REPLACE VIEW vw_picos_dias_eventos_quinto_dia_util AS
WITH base_vendas_dias AS (
    SELECT
        CASE
            WHEN COALESCE(vf.quinto_dia_util, b'0') = b'1' AND COALESCE(vf.total_eventos_ativos, 0) > 0
                THEN '1_SUPER_PICO_5_DIA_UTIL_COM_EVENTO'
            WHEN COALESCE(vf.quinto_dia_util, b'0') = b'1'
                THEN '2_PICO_5_DIA_UTIL'
            WHEN COALESCE(vf.total_eventos_ativos, 0) > 0
                THEN '3_PICO_EVENTOS_REGIONAIS'
            WHEN COALESCE(vf.dia_util, b'1') = b'0'
                THEN '4_FIM_DE_SEMANA_OU_FERIADO'
            ELSE '5_DIA_UTIL_COMUM'
        END AS categoria_dia_sazonal,
        COUNT(DISTINCT COALESCE(vf.data_referencia, CAST(nv.data_gerada AS DATE))) AS dias_observados,
        COUNT(DISTINCT nv.id) AS total_notas_venda,
        COALESCE(SUM(iv.quantidade), 0) AS qtd_total_vendida,
        ROUND(COALESCE(SUM((iv.quantidade * iv.valor) - iv.desconto), 0), 2) AS faturamento_liquido_total,
        ROUND(
            COALESCE(SUM(((iv.quantidade * iv.valor) - iv.desconto) - (iv.quantidade * COALESCE(lote.valor, 0))), 0),
            2
        ) AS lucro_liquido_total_bacen
    FROM nota nv
    INNER JOIN item iv ON iv.id_nota = nv.id
    LEFT JOIN item lote ON lote.id = iv.id_item_origem
    LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
    WHERE nv.tipo_nota = 'VENDA'
    GROUP BY categoria_dia_sazonal
),
media_dia_comum AS (
    SELECT
        COALESCE(
            MAX(CASE WHEN categoria_dia_sazonal = '5_DIA_UTIL_COMUM' THEN faturamento_liquido_total / NULLIF(dias_observados, 0) END),
            AVG(faturamento_liquido_total / NULLIF(dias_observados, 0)),
            1.0
        ) AS fat_diario_base
    FROM base_vendas_dias
)
SELECT
    b.categoria_dia_sazonal,
    b.dias_observados,
    b.total_notas_venda,
    b.qtd_total_vendida,
    b.faturamento_liquido_total,
    b.lucro_liquido_total_bacen,
    ROUND(b.faturamento_liquido_total / NULLIF(b.dias_observados, 0), 2) AS faturamento_medio_por_dia,
    ROUND(b.faturamento_liquido_total / NULLIF(b.total_notas_venda, 0), 2) AS ticket_medio_nota_bacen,
    ROUND(
        (((b.faturamento_liquido_total / NULLIF(b.dias_observados, 0)) - m.fat_diario_base) / NULLIF(m.fat_diario_base, 0)) * 100.0,
        2
    ) AS variacao_pico_vs_dia_comum_pct
FROM base_vendas_dias b
CROSS JOIN media_dia_comum m
ORDER BY faturamento_medio_por_dia DESC;

-- =====================================================================================================================
-- 2. STORED PROCEDURES ESTATÍSTICAS E ESTRATÉGICAS (PROCESSAMENTO PESADO 100% NO BANCO DE DADOS)
-- =====================================================================================================================

DROP PROCEDURE IF EXISTS sp_prever_demanda_mensal_cliente;
DROP PROCEDURE IF EXISTS sp_analisar_perfil_consumo_cliente;
DROP PROCEDURE IF EXISTS sp_analisar_impacto_externo_rios_clima_produtos;
DROP PROCEDURE IF EXISTS sp_ranking_fornecedores_custo_qualidade;
DROP PROCEDURE IF EXISTS sp_diagnosticar_motivos_perda_prematura;
DROP PROCEDURE IF EXISTS sp_analisar_destaques_e_picos_sazonais;
DROP PROCEDURE IF EXISTS sp_plano_estrategico_compras_vendas_inteligente;

DELIMITER $$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 1: Previsão de Demanda Mensal do Cliente por Produto baseada no Consumo Passado, Tendência e Sazonalidade
-- Responde: "Qual é a demanda do meu cliente para esse mês de acordo com o consumo dele passado?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_prever_demanda_mensal_cliente(
    IN p_id_cliente BIGINT,
    IN p_id_produto BIGINT,
    IN p_mes_alvo INT
)
BEGIN
    DECLARE v_mes INT DEFAULT COALESCE(NULLIF(p_mes_alvo, 0), MONTH(CURDATE()));

    WITH consumo_mensal_cliente AS (
        SELECT
            nv.id_pessoa AS id_cliente,
            cli.nome AS nome_cliente,
            iv.id_produto,
            prod.nome AS nome_produto,
            u.sigla AS unidade,
            YEAR(nv.data_gerada) AS ano_compra,
            MONTH(nv.data_gerada) AS mes_compra,
            SUM(iv.quantidade) AS qtd_no_mes,
            SUM((iv.quantidade * iv.valor) - iv.desconto) AS valor_liquido_no_mes,
            AVG(iv.valor) AS preco_medio_praticado,
            AVG(
                1.0
                + (CASE WHEN vf.quinto_dia_util = b'1' THEN 0.20 ELSE 0.0 END)
                + (CASE WHEN COALESCE(vf.total_eventos_ativos, 0) > 0 THEN 0.15 ELSE 0.0 END)
                + (CASE WHEN COALESCE(vf.temp_media, 27) >= 30.0 THEN 0.10 ELSE 0.0 END)
            ) AS multiplicador_sazonal_observado
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        INNER JOIN pessoa cli ON cli.id = nv.id_pessoa
        INNER JOIN produtos prod ON prod.id = iv.id_produto
        LEFT JOIN unidade_medida u ON u.id = prod.um_id
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
        WHERE nv.tipo_nota = 'VENDA'
          AND (p_id_cliente IS NULL OR nv.id_pessoa = p_id_cliente)
          AND (p_id_produto IS NULL OR iv.id_produto = p_id_produto)
        GROUP BY
            nv.id_pessoa, cli.nome, iv.id_produto, prod.nome, u.sigla,
            YEAR(nv.data_gerada), MONTH(nv.data_gerada)
    ),
    estatisticas_cliente AS (
        SELECT
            id_cliente,
            nome_cliente,
            id_produto,
            nome_produto,
            unidade,
            COUNT(*) AS meses_com_consumo,
            ROUND(SUM(qtd_no_mes), 6) AS qtd_total_historica,
            ROUND(AVG(qtd_no_mes), 6) AS media_mensal_historica,
            ROUND(COALESCE(STDDEV_POP(qtd_no_mes), 0), 6) AS desvio_padrao_mensal,
            ROUND(AVG(CASE WHEN mes_compra = v_mes THEN qtd_no_mes END), 6) AS media_mes_alvo_historico,
            ROUND(AVG(CASE WHEN (ano_compra * 12 + mes_compra) >= (YEAR(CURDATE()) * 12 + MONTH(CURDATE()) - 2) THEN qtd_no_mes END), 6) AS media_recente_ultimos_meses,
            ROUND(AVG(preco_medio_praticado), 4) AS preco_unitario_medio,
            ROUND(AVG(multiplicador_sazonal_observado), 4) AS indice_sazonal_cliente
        FROM consumo_mensal_cliente
        GROUP BY id_cliente, nome_cliente, id_produto, nome_produto, unidade
    )
    SELECT
        id_cliente,
        nome_cliente,
        id_produto,
        nome_produto,
        COALESCE(unidade, 'UN') AS unidade,
        v_mes AS mes_referencia_previsao,
        meses_com_consumo,
        qtd_total_historica,
        media_mensal_historica,
        desvio_padrao_mensal,
        ROUND(
            CASE WHEN media_mensal_historica > 0 THEN (desvio_padrao_mensal / media_mensal_historica) * 100.0 ELSE 0.0 END,
            2
        ) AS coeficiente_variacao_pct,
        -- Demanda Projetada ponderando Média Histórica (40%), Tendência Recente (35%) e Sazonalidade do Mês Alvo (25%)
        ROUND(
            (0.40 * media_mensal_historica)
            + (0.35 * COALESCE(media_recente_ultimos_meses, media_mensal_historica))
            + (0.25 * COALESCE(media_mes_alvo_historico, media_mensal_historica * indice_sazonal_cliente)),
            6
        ) AS demanda_prevista_mes,
        -- Limite Inferior Conservador e Demanda de Pico (Margem de Segurança 90% = +1.28 desvios-padrão)
        ROUND(GREATEST(0, media_mensal_historica - (0.50 * desvio_padrao_mensal)), 6) AS demanda_minima_esperada,
        ROUND(
            ((0.40 * media_mensal_historica)
            + (0.35 * COALESCE(media_recente_ultimos_meses, media_mensal_historica))
            + (0.25 * COALESCE(media_mes_alvo_historico, media_mensal_historica * indice_sazonal_cliente)))
            + (1.28 * desvio_padrao_mensal),
            6
        ) AS demanda_pico_seguranca,
        -- Faturamento Estimado no Padrão BACEN (2 casas decimais)
        ROUND(
            ((0.40 * media_mensal_historica)
            + (0.35 * COALESCE(media_recente_ultimos_meses, media_mensal_historica))
            + (0.25 * COALESCE(media_mes_alvo_historico, media_mensal_historica * indice_sazonal_cliente)))
            * preco_unitario_medio,
            2
        ) AS faturamento_estimado_mes_bacen,
        CASE
            WHEN COALESCE(media_recente_ultimos_meses, media_mensal_historica) > media_mensal_historica * 1.15
                THEN 'TENDENCIA_CRESCIMENTO_FORTE'
            WHEN desvio_padrao_mensal <= media_mensal_historica * 0.25
                THEN 'CONSUMO_RECORRENTE_ALTA_PREVISIBILIDADE'
            WHEN indice_sazonal_cliente >= 1.20
                THEN 'CONSUMO_IMPULSIONADO_POR_EVENTOS_E_5_DIA_UTIL'
            ELSE 'CONSUMO_VARIAVEL_MODERADO'
        END AS diagnostico_tendencia_demanda
    FROM estatisticas_cliente
    ORDER BY faturamento_estimado_mes_bacen DESC, demanda_prevista_mes DESC;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 2: Perfil Comportamental e Sazonal do Cliente (Quando consome mais, Clima, Eventos, Rios e Produto Favorito)
-- Responde: "Qual é o perfil do meu cliente, quando ele consome mais, em que clima e em quais eventos?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_analisar_perfil_consumo_cliente(
    IN p_id_cliente BIGINT
)
BEGIN
    WITH prod_favorito AS (
        SELECT
            nv.id_pessoa AS id_cliente,
            p.nome AS nome_produto_favorito,
            SUM(iv.quantidade) AS qtd_comprada,
            ROW_NUMBER() OVER (PARTITION BY nv.id_pessoa ORDER BY SUM((iv.quantidade * iv.valor) - iv.desconto) DESC) AS rn
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        INNER JOIN produtos p ON p.id = iv.id_produto
        WHERE nv.tipo_nota = 'VENDA'
        GROUP BY nv.id_pessoa, p.id, p.nome
    ),
    horario_favorito AS (
        SELECT
            nv.id_pessoa AS id_cliente,
            COALESCE(vf.bloco_horario_6h, FLOOR(HOUR(nv.data_gerada) / 6)) AS bloco_6h,
            ROW_NUMBER() OVER (PARTITION BY nv.id_pessoa ORDER BY COUNT(*) DESC) AS rn
        FROM nota nv
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
        WHERE nv.tipo_nota = 'VENDA'
        GROUP BY nv.id_pessoa, COALESCE(vf.bloco_horario_6h, FLOOR(HOUR(nv.data_gerada) / 6))
    )
    SELECT
        vp.id_cliente,
        vp.nome_cliente,
        vp.documento,
        vp.total_compras,
        vp.quantidade_total_itens,
        vp.faturamento_liquido_total,
        vp.ticket_medio_bacen,
        vp.desvio_padrao_item_bacen,
        COALESCE(pf.nome_produto_favorito, 'N/A') AS produto_principal,
        -- Quando consome mais (Dia / Turno)
        CASE
            WHEN vp.gasto_quinto_dia_util >= vp.gasto_fim_semana_feriado AND vp.gasto_quinto_dia_util >= vp.gasto_dia_util_comum
                THEN 'PICO_5_DIA_UTIL (RECEBIMENTO SALARIAL)'
            WHEN vp.gasto_fim_semana_feriado >= vp.gasto_dia_util_comum
                THEN 'FIM_DE_SEMANA_E_FERIADOS'
            ELSE 'DIAS_UTEIS_REGULARES'
        END AS quando_consome_mais_calendario,
        CASE COALESCE(hf.bloco_6h, 2)
            WHEN 0 THEN 'MADRUGADA (00h-06h)'
            WHEN 1 THEN 'MANHA (06h-12h)'
            WHEN 2 THEN 'TARDE (12h-18h)'
            ELSE 'NOITE (18h-24h)'
        END AS turno_maior_consumo,
        -- Em que clima consome mais
        CASE
            WHEN vp.gasto_clima_calor >= vp.gasto_clima_chuva AND vp.gasto_clima_calor >= vp.gasto_clima_estavel AND vp.gasto_clima_calor > 0
                THEN 'CALOR_FORTE (>= 30°C)'
            WHEN vp.gasto_clima_chuva >= vp.gasto_clima_calor AND vp.gasto_clima_chuva >= vp.gasto_clima_estavel AND vp.gasto_clima_chuva > 0
                THEN 'DIAS_CHUVOSOS (> 10mm)'
            ELSE 'CLIMA_AMENO_ESTAVEL'
        END AS clima_maior_consumo,
        vp.gasto_clima_calor,
        vp.gasto_clima_chuva,
        vp.gasto_clima_estavel,
        -- Em quais eventos consome mais
        vp.gasto_em_eventos,
        ROUND((vp.gasto_em_eventos / NULLIF(vp.faturamento_liquido_total, 0)) * 100.0, 2) AS participacao_eventos_pct,
        ROUND((vp.gasto_quinto_dia_util / NULLIF(vp.faturamento_liquido_total, 0)) * 100.0, 2) AS participacao_quinto_dia_util_pct,
        -- Classificação de Perfil Estratégico do Cliente
        CASE
            WHEN (vp.gasto_quinto_dia_util / NULLIF(vp.faturamento_liquido_total, 0)) >= 0.45
                THEN 'PERFIL_SALARIAL_5_DIA_UTIL'
            WHEN (vp.gasto_em_eventos / NULLIF(vp.faturamento_liquido_total, 0)) >= 0.40
                THEN 'PERFIL_MOVIDO_A_EVENTOS_CULTURAIS_E_FESTAS'
            WHEN vp.gasto_clima_calor > vp.gasto_clima_chuva * 1.5 AND vp.gasto_clima_calor > 0
                THEN 'PERFIL_SAZONAL_VERAO_CALOR'
            WHEN vp.gasto_clima_chuva > vp.gasto_clima_calor * 1.5 AND vp.gasto_clima_chuva > 0
                THEN 'PERFIL_CONFORTO_CHUVA_ABASTECIMENTO'
            ELSE 'PERFIL_FIEL_CONSUMO_CONTINUO'
        END AS classificacao_perfil_cliente
    FROM vw_perfil_estatistico_cliente vp
    LEFT JOIN prod_favorito pf ON pf.id_cliente = vp.id_cliente AND pf.rn = 1
    LEFT JOIN horario_favorito hf ON hf.id_cliente = vp.id_cliente AND hf.rn = 1
    WHERE (p_id_cliente IS NULL OR vp.id_cliente = p_id_cliente)
    ORDER BY vp.faturamento_liquido_total DESC;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 3: Custo Logístico de Acordo com os Rios e Sensibilidade dos Produtos às Condições Externas
-- Responde: "Qual é o custo de acordo com os rios e quais produtos são afetados pelas condições externas?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_analisar_impacto_externo_rios_clima_produtos(
    IN p_id_produto BIGINT
)
BEGIN
    WITH custos_rios AS (
        SELECT
            ic.id_produto,
            ROUND(AVG(ic.valor), 4) AS custo_medio_geral,
            ROUND(AVG(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN ic.valor END), 4) AS custo_medio_seca_severa,
            ROUND(AVG(CASE WHEN vf.vazao_media_m3s IS NULL OR vf.vazao_media_m3s = 0 OR vf.vazao_m3s / vf.vazao_media_m3s BETWEEN 0.85 AND 1.15 THEN ic.valor END), 4) AS custo_medio_rio_normal,
            ROUND(AVG(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s > 1.15) THEN ic.valor END), 4) AS custo_medio_cheia_rio
        FROM nota nc
        INNER JOIN item ic ON ic.id_nota = nc.id
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nc.id_vinculo_fator_externo
        WHERE nc.tipo_nota = 'COMPRA'
        GROUP BY ic.id_produto
    ),
    vendas_condicoes AS (
        SELECT
            iv.id_produto,
            COALESCE(SUM(iv.quantidade), 0) AS qtd_total_vendida,
            ROUND(AVG(iv.quantidade), 4) AS giro_medio_por_venda,
            ROUND(AVG(CASE WHEN vf.temp_media >= 30.0 THEN iv.quantidade END), 4) AS giro_medio_calor,
            ROUND(AVG(CASE WHEN vf.precipitacao_mm > 10.0 THEN iv.quantidade END), 4) AS giro_medio_chuva,
            ROUND(AVG(CASE WHEN COALESCE(vf.total_eventos_ativos, 0) > 0 THEN iv.quantidade END), 4) AS giro_medio_eventos,
            ROUND(AVG(CASE WHEN vf.quinto_dia_util = b'1' THEN iv.quantidade END), 4) AS giro_medio_quinto_dia_util,
            ROUND(AVG(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN iv.quantidade END), 4) AS giro_medio_seca_rio
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
        WHERE nv.tipo_nota = 'VENDA'
        GROUP BY iv.id_produto
    ),
    perdas_condicoes AS (
        SELECT
            ip.id_produto,
            COALESCE(SUM(ip.quantidade), 0) AS qtd_total_perdida,
            COALESCE(SUM(CASE WHEN vf.temp_media >= 30.0 OR vf.precipitacao_mm > 12.0 THEN ip.quantidade ELSE 0 END), 0) AS qtd_perdida_clima_adverso
        FROM nota np
        INNER JOIN item ip ON ip.id_nota = np.id
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = np.id_vinculo_fator_externo
        WHERE np.tipo_nota = 'PERDA'
        GROUP BY ip.id_produto
    )
    SELECT
        p.id AS id_produto,
        p.nome AS nome_produto,
        COALESCE(cr.custo_medio_geral, 0.0000) AS custo_medio_geral,
        COALESCE(cr.custo_medio_seca_severa, cr.custo_medio_geral, 0.0000) AS custo_seca_rios_vazante,
        COALESCE(cr.custo_medio_rio_normal, cr.custo_medio_geral, 0.0000) AS custo_nivel_normal_rios,
        COALESCE(cr.custo_medio_cheia_rio, cr.custo_medio_geral, 0.0000) AS custo_cheia_rios,
        -- Impacto percentual dos rios sobre o custo de aquisição (Seca/Cheia vs Normal)
        ROUND(
            CASE
                WHEN COALESCE(cr.custo_medio_rio_normal, 0) > 0 THEN
                    ((GREATEST(COALESCE(cr.custo_medio_seca_severa, cr.custo_medio_rio_normal), COALESCE(cr.custo_medio_cheia_rio, cr.custo_medio_rio_normal))
                      - cr.custo_medio_rio_normal) / cr.custo_medio_rio_normal) * 100.0
                ELSE 0.00
            END,
            2
        ) AS sobrecusto_logistico_rios_pct,
        -- Sensibilidade do giro de vendas a Clima, Eventos e 5º Dia Útil
        COALESCE(vc.giro_medio_por_venda, 0) AS giro_medio_geral,
        COALESCE(vc.giro_medio_calor, 0) AS giro_medio_calor_30c,
        COALESCE(vc.giro_medio_chuva, 0) AS giro_medio_chuva_forte,
        COALESCE(vc.giro_medio_eventos, 0) AS giro_medio_eventos_ativos,
        COALESCE(vc.giro_medio_quinto_dia_util, 0) AS giro_medio_quinto_dia_util,
        COALESCE(pc.qtd_total_perdida, 0) AS qtd_total_perdida,
        COALESCE(pc.qtd_perdida_clima_adverso, 0) AS qtd_perdida_clima_adverso,
        -- Diagnóstico de sensibilidade externa do produto
        CASE
            WHEN COALESCE(cr.custo_medio_rio_normal, 0) > 0 AND COALESCE(cr.custo_medio_seca_severa, 0) > cr.custo_medio_rio_normal * 1.10
                THEN 'ALTA_SENSIBILIDADE_SECA_RIOS (FRETE FLUVIAL ENCARECE CUSTO)'
            WHEN COALESCE(pc.qtd_perdida_clima_adverso, 0) > 0 AND COALESCE(pc.qtd_perdida_clima_adverso, 0) >= COALESCE(pc.qtd_total_perdida, 0) * 0.50
                THEN 'PERECIVEL_VULNERAVEL_CALOR_E_UMIDADE (RISCO DE PERDA PREMATURA)'
            WHEN COALESCE(vc.giro_medio_calor, 0) > COALESCE(vc.giro_medio_por_venda, 0) * 1.20
                THEN 'PRODUTO_IMPULSIONADO_POR_CALOR_E_EVENTOS'
            WHEN COALESCE(vc.giro_medio_chuva, 0) > COALESCE(vc.giro_medio_por_venda, 0) * 1.20
                THEN 'PRODUTO_ALTA_PROCURA_EM_DIAS_CHUVOSOS'
            WHEN COALESCE(vc.giro_medio_quinto_dia_util, 0) > COALESCE(vc.giro_medio_por_venda, 0) * 1.20
                THEN 'PRODUTO_DE_ABASTECIMENTO_PICO_5_DIA_UTIL'
            ELSE 'PRODUTO_ESTAVEL_BAIXA_VOLATILIDADE_EXTERNA'
        END AS diagnostico_impacto_externo
    FROM produtos p
    LEFT JOIN custos_rios cr ON cr.id_produto = p.id
    LEFT JOIN vendas_condicoes vc ON vc.id_produto = p.id
    LEFT JOIN perdas_condicoes pc ON pc.id_produto = p.id
    WHERE (p_id_produto IS NULL OR p.id = p_id_produto)
    ORDER BY sobrecusto_logistico_rios_pct DESC, COALESCE(vc.qtd_total_vendida, 0) DESC;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 4: Ranking de Fornecedores por Produto (Melhor Custo-Qualidade, Reposição e Condição Mais Vantajosa)
-- Responde: "Tenho vários fornecedores que fornecem o mesmo produto, quais deles têm melhor custo-qualidade e em quais condições se torna mais vantajoso?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_ranking_fornecedores_custo_qualidade(
    IN p_id_produto BIGINT,
    IN p_condicao_clima VARCHAR(30),
    IN p_regime_rio VARCHAR(30)
)
BEGIN
    SELECT
        vs.id_produto,
        vs.nome_produto,
        vs.id_fornecedor,
        vs.nome_fornecedor,
        DENSE_RANK() OVER (
            PARTITION BY vs.id_produto
            ORDER BY vs.coeficiente_score_fornecedor DESC, vs.custo_efetivo_ajustado_qualidade ASC
        ) AS posicao_ranking,
        vs.custo_unitario_medio_ponderado,
        vs.custo_efetivo_ajustado_qualidade,
        vs.durabilidade_media_dias,
        vs.taxa_perda_prematura_pct,
        vs.taxa_reposicao_fornecedor_pct,
        vs.credito_pendente_abatimento,
        vs.coeficiente_score_fornecedor,
        -- Custo específico para o filtro climático/hidrológico consultado
        ROUND(
            COALESCE(
                CASE
                    WHEN UPPER(COALESCE(p_regime_rio, 'TODOS')) = 'SECA' THEN vs.custo_medio_seca_rio
                    WHEN UPPER(COALESCE(p_regime_rio, 'TODOS')) = 'CHEIA' THEN vs.custo_medio_cheia_rio
                    WHEN UPPER(COALESCE(p_condicao_clima, 'TODOS')) = 'CALOR' THEN vs.custo_medio_calor
                    WHEN UPPER(COALESCE(p_condicao_clima, 'TODOS')) = 'CHUVA' THEN vs.custo_medio_chuva
                    ELSE vs.custo_unitario_medio_ponderado
                END,
                vs.custo_unitario_medio_ponderado
            ),
            4
        ) AS custo_esperado_na_condicao,
        -- Determinação automática de em quais condições o fornecedor é mais vantajoso
        CASE
            WHEN vs.taxa_perda_prematura_pct = 0 AND vs.durabilidade_media_dias >= 90
                THEN 'VANTAJOSO_EM_CALOR_E_ESTOQUE_LONGO (ZERO PERDA PREMATURA + ALTA VALIDADE)'
            WHEN vs.credito_pendente_abatimento > 0 OR vs.taxa_reposicao_fornecedor_pct >= 80.0
                THEN 'VANTAJOSO_COM_ABATIMENTO_DE_PERDAS_GARANTIDO (REPOSICAO IMEDIATA)'
            WHEN vs.custo_medio_seca_rio IS NOT NULL AND vs.custo_medio_seca_rio <= vs.custo_unitario_medio_ponderado
                THEN 'VANTAJOSO_NA_SECA_DOS_RIOS (ESTABILIDADE LOGISTICA DE PRECO)'
            WHEN vs.custo_unitario_medio_ponderado = vs.menor_custo_unitario AND vs.taxa_perda_prematura_pct > 5.0
                THEN 'VANTAJOSO_APENAS_PARA_GIRO_RAPIDO_IMEDIATO (PRECO BAIXO MAS MENOR DURABILIDADE)'
            ELSE 'VANTAJOSO_EM_CONDICOES_NORMAIS_DE_ABASTECIMENTO'
        END AS condicao_mais_vantajosa_compra
    FROM vw_score_fornecedor_produto vs
    WHERE (p_id_produto IS NULL OR vs.id_produto = p_id_produto)
    ORDER BY vs.id_produto ASC, posicao_ranking ASC;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 5: Diagnóstico Estatístico dos Motivos de um Produto ter se Estragado Antes da Validade
-- Responde: "Quais os possíveis motivos de um produto ter se estragado antes da validade?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_diagnosticar_motivos_perda_prematura(
    IN p_id_produto BIGINT,
    IN p_id_fornecedor BIGINT
)
BEGIN
    SELECT
        vpf.id_nota_perda,
        vpf.data_perda,
        vpf.id_produto,
        vpf.nome_produto,
        vpf.id_fornecedor,
        COALESCE(vpf.nome_fornecedor, 'FORNECEDOR NAO VINCULADO') AS nome_fornecedor,
        vpf.quantidade_perdida,
        ROUND(vpf.valor_total_custo_perda, 2) AS prejuizo_contabil_bacen,
        vpf.data_validade,
        DATEDIFF(vpf.data_validade, CAST(vpf.data_perda AS DATE)) AS dias_antes_de_vencer,
        COALESCE(DATEDIFF(CAST(vpf.data_perda AS DATE), CAST(nc.data_gerada AS DATE)), 0) AS dias_permanencia_apos_compra,
        COALESCE(vf_perda.temp_media, vf_compra.temp_media, 28.0) AS temperatura_exposicao_c,
        COALESCE(vf_perda.precipitacao_mm, vf_compra.precipitacao_mm, 0.0) AS precipitacao_exposicao_mm,
        COALESCE(vf_compra.vazao_m3s, vf_perda.vazao_m3s, 20000.0) AS vazao_rio_transporte_m3s,
        -- Probabilidades atribuídas a cada causa-raiz (%)
        ROUND(
            CASE
                WHEN COALESCE(vf_perda.temp_media, vf_compra.temp_media, 28.0) >= 31.0 THEN 45.0
                WHEN COALESCE(vf_perda.temp_media, vf_compra.temp_media, 28.0) >= 29.5 THEN 25.0
                ELSE 10.0
            END, 1
        ) AS peso_causa_calor_termico_pct,
        ROUND(
            CASE
                WHEN COALESCE(vf_perda.precipitacao_mm, vf_compra.precipitacao_mm, 0.0) >= 15.0 THEN 35.0
                WHEN COALESCE(vf_perda.precipitacao_mm, vf_compra.precipitacao_mm, 0.0) >= 8.0 THEN 20.0
                ELSE 5.0
            END, 1
        ) AS peso_causa_umidade_chuva_pct,
        ROUND(
            CASE
                WHEN (vf_compra.vazao_media_m3s > 0 AND vf_compra.vazao_m3s / vf_compra.vazao_media_m3s < 0.85) THEN 30.0
                WHEN (vf_compra.vazao_media_m3s > 0 AND vf_compra.vazao_m3s / vf_compra.vazao_media_m3s > 1.15) THEN 20.0
                ELSE 5.0
            END, 1
        ) AS peso_causa_transporte_rio_pct,
        ROUND(
            CASE
                WHEN COALESCE(DATEDIFF(CAST(vpf.data_perda AS DATE), CAST(nc.data_gerada AS DATE)), 0) <= 10 THEN 50.0
                WHEN COALESCE(sf.taxa_perda_prematura_pct, 0) >= 10.0 THEN 40.0
                ELSE 15.0
            END, 1
        ) AS peso_causa_qualidade_fornecedor_pct,
        -- Diagnóstico consolidado do motivo principal da perda antes da validade
        CASE
            WHEN COALESCE(DATEDIFF(CAST(vpf.data_perda AS DATE), CAST(nc.data_gerada AS DATE)), 0) <= 7
                 AND vpf.is_perda_pos_venda = 1
                THEN 'VICIO_OCULTO_LOTE_FORNECEDOR (DEFEITO PRECOCE POS-VENDA LOGO APOS ENTRADA)'
            WHEN COALESCE(vf_perda.temp_media, vf_compra.temp_media, 28.0) >= 31.0
                 AND COALESCE(vf_perda.precipitacao_mm, 0.0) >= 12.0
                THEN 'CHOQUE_TERMICO_E_ALTA_UMIDADE_ TROPICAL (CALOR >= 31C + CHUVA FORTE)'
            WHEN COALESCE(vf_perda.temp_media, vf_compra.temp_media, 28.0) >= 31.0
                THEN 'DEGRADACAO_POR_ESTRESSE_TERMICO (TEMPERATURA MEDIA ELEVADA NO ARMAZENAMENTO/TRANSPORTE)'
            WHEN COALESCE(vf_perda.precipitacao_mm, vf_compra.precipitacao_mm, 0.0) >= 15.0
                THEN 'INFILTRACAO_OU_UMIDADE_EXCESSIVA_EM_PERIODO_CHUVOSO (> 15mm)'
            WHEN (vf_compra.vazao_media_m3s > 0 AND vf_compra.vazao_m3s / vf_compra.vazao_media_m3s < 0.85)
                THEN 'FADIGA_LOGISTICA_NA_SECA_DOS_RIOS (MAIOR TEMPO DE TRANSITO FLUVIAL REDUZIU VIDA UTIL REAL)'
            WHEN COALESCE(DATEDIFF(CAST(vpf.data_perda AS DATE), CAST(nc.data_gerada AS DATE)), 0) > 45
                THEN 'TEMPO_EXCESSIVO_DE_ESTOCAGEM_ANTES_DO_GIRO (BAIXA ROTATIVIDADE DO LOTE)'
            ELSE 'FALHA_QUALIDADE_INTRINSECA_DO_FORNECEDOR_OU_MANUSEIO'
        END AS motivo_principal_diagnosticado,
        -- Recomendação prática para contornar a perda prematura
        CASE
            WHEN vpf.elegivel_abatimento_pos_venda = 1
                THEN 'ACIONAR ABATIMENTO AUTOMATICO OU REPOSICAO SEM CUSTO NA PROXIMA NOTA DE COMPRA DO FORNECEDOR E FRACIONAR LOTES'
            WHEN COALESCE(vf_perda.temp_media, 28.0) >= 30.5
                THEN 'COMPRAR MAIS PROXIMO AO DIA DE PICO (REDUZIR ESTOCAGEM EM DIAS QUENTES) E PRIORIZAR FORNECEDOR DE MAIOR SCORE'
            ELSE 'ANTECIPAR GIRO VIA PROMOCAO DE LOTE DESEJAVEL E REVISAR EMBALAGEM/FORNECEDOR'
        END AS acao_preventiva_recomendada
    FROM vw_perdas_por_fornecedor vpf
    LEFT JOIN nota np ON np.id = vpf.id_nota_perda
    LEFT JOIN vinculo_fator_externo_nota vf_perda ON vf_perda.id = np.id_vinculo_fator_externo
    LEFT JOIN nota nc ON nc.id = vpf.id_nota_compra_origem
    LEFT JOIN vinculo_fator_externo_nota vf_compra ON vf_compra.id = nc.id_vinculo_fator_externo
    LEFT JOIN vw_score_fornecedor_produto sf ON sf.id_produto = vpf.id_produto AND sf.id_fornecedor = vpf.id_fornecedor
    WHERE vpf.dentro_periodo_validade = 1
      AND (p_id_produto IS NULL OR vpf.id_produto = p_id_produto)
      AND (p_id_fornecedor IS NULL OR vpf.id_fornecedor = p_id_fornecedor)
    ORDER BY vpf.valor_total_custo_perda DESC, dias_antes_de_vencer DESC;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 6: Produtos que Mais se Destacam em Eventos, Climas, Nível dos Rios e Dias de Maior Pico (5º Dia Útil)
-- Responde: "Quais produtos se destacam mais em certos eventos, climas, nível dos rios e dias? Quais dias têm maior pico?"
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_analisar_destaques_e_picos_sazonais(
    IN p_limite_top INT
)
BEGIN
    DECLARE v_lim INT DEFAULT COALESCE(NULLIF(p_limite_top, 0), 25);

    WITH vendas_produto_cenario AS (
        SELECT
            p.id AS id_produto,
            p.nome AS nome_produto,
            SUM(iv.quantidade) AS qtd_total_vendida,
            ROUND(SUM((iv.quantidade * iv.valor) - iv.desconto), 2) AS receita_liquida_total,
            ROUND(SUM(((iv.quantidade * iv.valor) - iv.desconto) - (iv.quantidade * COALESCE(lote.valor, 0))), 2) AS lucro_liquido_bacen,
            -- Faturamento segmentado por condição externa
            ROUND(SUM(CASE WHEN vf.quinto_dia_util = b'1' THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_5_dia_util,
            ROUND(SUM(CASE WHEN COALESCE(vf.total_eventos_ativos, 0) > 0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_em_eventos,
            ROUND(SUM(CASE WHEN vf.temp_media >= 30.0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_clima_calor,
            ROUND(SUM(CASE WHEN vf.precipitacao_mm > 10.0 THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_clima_chuva,
            ROUND(SUM(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_seca_rios,
            ROUND(SUM(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s > 1.15) THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_cheia_rios,
            ROUND(SUM(CASE WHEN vf.dia_util = b'0' THEN (iv.quantidade * iv.valor) - iv.desconto ELSE 0 END), 2) AS receita_fim_de_semana
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        INNER JOIN produtos p ON p.id = iv.id_produto
        LEFT JOIN item lote ON lote.id = iv.id_item_origem
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
        WHERE nv.tipo_nota = 'VENDA'
        GROUP BY p.id, p.nome
    )
    SELECT
        id_produto,
        nome_produto,
        qtd_total_vendida,
        receita_liquida_total,
        lucro_liquido_bacen,
        receita_5_dia_util,
        receita_em_eventos,
        receita_clima_calor,
        receita_clima_chuva,
        receita_seca_rios,
        receita_cheia_rios,
        receita_fim_de_semana,
        -- Identifica em qual condição externa/dia o produto mais se destaca
        CASE
            WHEN receita_5_dia_util >= GREATEST(receita_em_eventos, receita_clima_calor, receita_clima_chuva, receita_fim_de_semana)
                 AND receita_5_dia_util > 0
                THEN 'DESTAQUE_PICO_5_DIA_UTIL'
            WHEN receita_em_eventos >= GREATEST(receita_5_dia_util, receita_clima_calor, receita_clima_chuva, receita_fim_de_semana)
                 AND receita_em_eventos > 0
                THEN 'DESTAQUE_EVENTOS_CULTURAIS_E_SHOWS'
            WHEN receita_clima_calor >= GREATEST(receita_clima_chuva, receita_fim_de_semana)
                 AND receita_clima_calor > 0
                THEN 'DESTAQUE_DIAS_DE_CALOR_ACIMA_30C'
            WHEN receita_clima_chuva >= receita_fim_de_semana AND receita_clima_chuva > 0
                THEN 'DESTAQUE_DIAS_CHUVOSOS'
            WHEN receita_fim_de_semana > 0
                THEN 'DESTAQUE_FIM_DE_SEMANA_E_FERIADOS'
            ELSE 'DESTAQUE_CONSUMO_DIARIO_CONTINUO'
        END AS cenario_maior_destaque
    FROM vendas_produto_cenario
    ORDER BY lucro_liquido_bacen DESC, receita_liquida_total DESC
    LIMIT v_lim;
END$$

-- ---------------------------------------------------------------------------------------------------------------------
-- PROCEDURE 7 (MASTER): Plano Estratégico de Compras, Vendas, Datas Ideais Anti-Perda Prematura e Seleção de Fornecedor
-- Responde:
-- 1. "Quais datas devo comprar para vender próximo nos dias baseado na durabilidade do produto e contornando padrões
--     consolidados que podem causar perda prematura dos produtos?"
-- 2. "Selecionar os fornecedores com melhor coeficiente de preço, produto e reposição."
-- 3. "Quero insights de estratégia de compra, venda e análise de consumo."
-- ---------------------------------------------------------------------------------------------------------------------
CREATE PROCEDURE sp_plano_estrategico_compras_vendas_inteligente(
    IN p_data_referencia DATE,
    IN p_horizonte_dias INT
)
BEGIN
    DECLARE v_data_ref DATE DEFAULT COALESCE(p_data_referencia, CURDATE());
    DECLARE v_horizonte INT DEFAULT COALESCE(NULLIF(p_horizonte_dias, 0), 30);
    DECLARE v_proximo_5_dia_util DATE;

    -- Calcula o próximo 5º dia útil de referência para ancorar o pico de vendas
    IF DAY(v_data_ref) <= 7 THEN
        SET v_proximo_5_dia_util = DATE_ADD(DATE_SUB(v_data_ref, INTERVAL DAY(v_data_ref) - 1 DAY), INTERVAL 6 DAY);
    ELSE
        SET v_proximo_5_dia_util = DATE_ADD(LAST_DAY(v_data_ref), INTERVAL 7 DAY);
    END IF;

    WITH melhor_fornecedor_por_produto AS (
        SELECT
            vs.*,
            ROW_NUMBER() OVER (
                PARTITION BY vs.id_produto
                ORDER BY vs.coeficiente_score_fornecedor DESC, vs.custo_efetivo_ajustado_qualidade ASC
            ) AS rk
        FROM vw_score_fornecedor_produto vs
    ),
    demanda_produto AS (
        SELECT
            iv.id_produto,
            COUNT(DISTINCT CAST(nv.data_gerada AS DATE)) AS dias_com_venda,
            SUM(iv.quantidade) AS qtd_total_vendida,
            ROUND(SUM(iv.quantidade) / GREATEST(1, COUNT(DISTINCT CAST(nv.data_gerada AS DATE))), 6) AS demanda_diaria_media,
            ROUND(COALESCE(STDDEV_POP(iv.quantidade), 0), 6) AS desvio_padrao_venda,
            ROUND(AVG(iv.valor), 4) AS preco_venda_medio_praticado,
            MAX(CASE WHEN vf.quinto_dia_util = b'1' OR COALESCE(vf.total_eventos_ativos, 0) > 0 THEN 1 ELSE 0 END) AS possui_pico_sazonal
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = nv.id_vinculo_fator_externo
        WHERE nv.tipo_nota = 'VENDA'
        GROUP BY iv.id_produto
    ),
    risco_perda_produto AS (
        SELECT
            vpf.id_produto,
            COUNT(*) AS qtd_eventos_perda_validade,
            ROUND(AVG(COALESCE(DATEDIFF(CAST(vpf.data_perda AS DATE), CAST(nc.data_gerada AS DATE)), 15)), 0) AS dias_medios_ate_estragar,
            MAX(CASE WHEN COALESCE(vf.temp_media, 28) >= 30.5 OR COALESCE(vf.precipitacao_mm, 0) > 12.0 THEN 1 ELSE 0 END) AS sensivel_calor_umidade,
            MAX(CASE WHEN (vf.vazao_media_m3s > 0 AND vf.vazao_m3s / vf.vazao_media_m3s < 0.85) THEN 1 ELSE 0 END) AS sensivel_seca_rio
        FROM vw_perdas_por_fornecedor vpf
        LEFT JOIN nota np ON np.id = vpf.id_nota_perda
        LEFT JOIN nota nc ON nc.id = vpf.id_nota_compra_origem
        LEFT JOIN vinculo_fator_externo_nota vf ON vf.id = COALESCE(np.id_vinculo_fator_externo, nc.id_vinculo_fator_externo)
        WHERE vpf.dentro_periodo_validade = 1
        GROUP BY vpf.id_produto
    )
    SELECT
        p.id AS id_produto,
        p.nome AS nome_produto,
        COALESCE(est.saldo_total_disponivel, 0) AS saldo_estoque_atual,
        COALESCE(mf.durabilidade_media_dias, 90.0) AS durabilidade_media_dias,
        -- Janela segura de compra antes do pico para evitar perda prematura por calor/umidade
        CASE
            WHEN COALESCE(rp.sensivel_calor_umidade, 0) = 1 OR COALESCE(mf.durabilidade_media_dias, 90) <= 30
                THEN LEAST(COALESCE(rp.dias_medios_ate_estragar, 7) - 2, 4)
            WHEN COALESCE(rp.sensivel_seca_rio, 0) = 1
                THEN 6
            ELSE 5
        END AS antecedencia_ideal_compra_dias,
        -- Data do próximo pico de venda e Data Exata Recomendada para Compra (próxima ao pico e dentro da janela segura)
        v_proximo_5_dia_util AS data_proximo_pico_venda,
        GREATEST(
            v_data_ref,
            DATE_SUB(
                v_proximo_5_dia_util,
                INTERVAL (
                    CASE
                        WHEN COALESCE(rp.sensivel_calor_umidade, 0) = 1 OR COALESCE(mf.durabilidade_media_dias, 90) <= 30 THEN 3
                        WHEN COALESCE(rp.sensivel_seca_rio, 0) = 1 THEN 6
                        ELSE 4
                    END
                ) DAY
            )
        ) AS data_ideal_compra,
        -- Demanda projetada no horizonte + Pico Sazonal (1.35x) e Quantidade Recomendada de Compra
        ROUND(
            COALESCE(dp.demanda_diaria_media, 1.0) * (v_horizonte / 5.0) * 1.35
            + (1.28 * COALESCE(dp.desvio_padrao_venda, 0)),
            6
        ) AS demanda_projetada_ciclo,
        ROUND(
            GREATEST(
                0.000000,
                (COALESCE(dp.demanda_diaria_media, 1.0) * (v_horizonte / 5.0) * 1.35 + (1.28 * COALESCE(dp.desvio_padrao_venda, 0)))
                - COALESCE(est.saldo_total_disponivel, 0)
            ),
            6
        ) AS quantidade_recomendada_compra,
        -- Fornecedor Selecionado com Melhor Coeficiente Preço + Qualidade + Reposição
        mf.id_fornecedor AS id_fornecedor_campeao,
        COALESCE(mf.nome_fornecedor, 'CADASTRAR FORNECEDOR') AS nome_fornecedor_campeao,
        COALESCE(mf.coeficiente_score_fornecedor, 0.00) AS coeficiente_fornecedor_score,
        COALESCE(mf.custo_unitario_medio_ponderado, 0.0000) AS custo_unitario_fornecedor,
        COALESCE(mf.custo_efetivo_ajustado_qualidade, 0.0000) AS custo_efetivo_qualidade,
        COALESCE(mf.taxa_perda_prematura_pct, 0.00) AS taxa_perda_fornecedor_pct,
        COALESCE(mf.taxa_reposicao_fornecedor_pct, 100.00) AS taxa_reposicao_fornecedor_pct,
        COALESCE(mf.credito_pendente_abatimento, 0.00) AS credito_abatimento_disponivel_bacen,
        -- Preço de Venda Estratégico Sugerido no Padrão BACEN
        ROUND(
            GREATEST(
                p.v_venda,
                COALESCE(mf.custo_efetivo_ajustado_qualidade, p.v_venda * 0.70) * 1.45
            ),
            2
        ) AS preco_venda_estrategico_bacen,
        -- Insight Estratégico Integrado (Compra + Venda + Prevenção de Perda + Fornecedor)
        CONCAT(
            'COMPRAR EM ',
            DATE_FORMAT(
                GREATEST(
                    v_data_ref,
                    DATE_SUB(
                        v_proximo_5_dia_util,
                        INTERVAL (CASE WHEN COALESCE(rp.sensivel_calor_umidade, 0) = 1 THEN 3 ELSE 4 END) DAY
                    )
                ),
                '%d/%m/%Y'
            ),
            ' PARA VENDER NO PICO DE ', DATE_FORMAT(v_proximo_5_dia_util, '%d/%m/%Y'),
            ' | FORNECEDOR RECOMENDADO: ', COALESCE(mf.nome_fornecedor, 'N/A'),
            ' (Score: ', COALESCE(mf.coeficiente_score_fornecedor, 0), '/100, Custo Efetivo: R$ ', ROUND(COALESCE(mf.custo_efetivo_ajustado_qualidade, 0), 2), ')',
            CASE
                WHEN COALESCE(mf.credito_pendente_abatimento, 0) > 0
                    THEN CONCAT(' | ABATER R$ ', ROUND(mf.credito_pendente_abatimento, 2), ' DE PERDAS POS-VENDA NA COMPRA')
                ELSE ''
            END,
            CASE
                WHEN COALESCE(rp.sensivel_calor_umidade, 0) = 1
                    THEN ' | ALERTA ANTI-PERDA: Lote sensivel a calor/umidade; compre fracionado 3 dias antes do pico para girar 100% fresco.'
                ELSE ' | GIRO SEGURO: Durabilidade compativel com o ciclo de pico.'
            END
        ) AS insight_estrategico_compra_venda
    FROM produtos p
    LEFT JOIN vw_saldo_consolidado_estoque est ON est.id_produto = p.id
    LEFT JOIN melhor_fornecedor_por_produto mf ON mf.id_produto = p.id AND mf.rk = 1
    LEFT JOIN demanda_produto dp ON dp.id_produto = p.id
    LEFT JOIN risco_perda_produto rp ON rp.id_produto = p.id
    WHERE p.is_digital = 0
    ORDER BY COALESCE(mf.coeficiente_score_fornecedor, 0) DESC, demanda_projetada_ciclo DESC;
END$$

DELIMITER ;
