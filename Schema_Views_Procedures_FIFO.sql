-- =========================================================================================
-- SCRIPT COMPLETO DDL + ÍNDICES DE ALTA PERFORMANCE + VIEWS + PROCEDURES FIFO E LOTE ESPECÍFICO
-- Banco: sazonalisei (MySQL 8.0+)
-- =========================================================================================

CREATE DATABASE IF NOT EXISTS sazonalisei
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE sazonalisei;

-- 1. TABELA: unidade_medida (Um)
CREATE TABLE IF NOT EXISTS unidade_medida (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sigla VARCHAR(4) NOT NULL UNIQUE,
    nome VARCHAR(50) NOT NULL UNIQUE,
    descricao VARCHAR(500) NOT NULL,
    base DECIMAL(19, 6) NOT NULL
) ENGINE=InnoDB;

-- 2. TABELA: produtos (Herança SINGLE_TABLE: ProdutoFisico = 0, ProdutoDigital = 1)
CREATE TABLE IF NOT EXISTS produtos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    is_digital TINYINT(1) NOT NULL DEFAULT 0,
    nome VARCHAR(120) NOT NULL UNIQUE,
    descricao VARCHAR(1200),
    v_venda DECIMAL(20, 4) DEFAULT 0.0000,
    is_conjunto TINYINT(1) NOT NULL DEFAULT 0,
    um_id BIGINT,
    CONSTRAINT fk_produto_um FOREIGN KEY (um_id) REFERENCES unidade_medida(id),
    INDEX idx_produto_tipo_conjunto (is_digital, is_conjunto)
) ENGINE=InnoDB;

-- 3. TABELA: link_produto_digital
CREATE TABLE IF NOT EXISTS link_produto_digital (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(100) NOT NULL,
    url VARCHAR(2800) NOT NULL,
    produto_digital_id BIGINT NOT NULL,
    CONSTRAINT fk_link_produto_digital FOREIGN KEY (produto_digital_id) REFERENCES produtos(id) ON DELETE CASCADE,
    INDEX idx_link_produto (produto_digital_id)
) ENGINE=InnoDB;

-- 4. TABELA: conjunto_produto (Kits / Receitas com PK Composta)
CREATE TABLE IF NOT EXISTS conjunto_produto (
    id_produto BIGINT NOT NULL,
    id_item BIGINT NOT NULL,
    quantidade DECIMAL(20, 6) NOT NULL,
    PRIMARY KEY (id_produto, id_item),
    CONSTRAINT fk_conjunto_pai FOREIGN KEY (id_produto) REFERENCES produtos(id) ON DELETE CASCADE,
    CONSTRAINT fk_conjunto_item FOREIGN KEY (id_item) REFERENCES produtos(id)
) ENGINE=InnoDB;

-- 5. TABELA: pessoa (Herança SINGLE_TABLE: PF e PJ)
CREATE TABLE IF NOT EXISTS pessoa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_pessoa VARCHAR(2) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    cpf VARCHAR(14) UNIQUE,
    cnpj VARCHAR(18) UNIQUE,
    limite DECIMAL(20, 2) DEFAULT 0.00,
    is_cliente BIT(1) NOT NULL DEFAULT b'0',
    is_fornecedor BIT(1) NOT NULL DEFAULT b'0',
    INDEX idx_pessoa_papel (is_cliente, is_fornecedor)
) ENGINE=InnoDB;

-- 6. TABELA: registro (Contatos / Documentos auxiliares da Pessoa)
CREATE TABLE IF NOT EXISTS registro (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_pessoa BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    registro VARCHAR(250) NOT NULL,
    CONSTRAINT fk_registro_pessoa FOREIGN KEY (id_pessoa) REFERENCES pessoa(id) ON DELETE CASCADE,
    INDEX idx_registro_pessoa (id_pessoa)
) ENGINE=InnoDB;

-- 7. TABELA: vinculo_fator_externo_nota (Snapshot compartilhado N:1 de 6h para Clima/Rios e 7d Eventos)
CREATE TABLE IF NOT EXISTS vinculo_fator_externo_nota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chave_snapshot VARCHAR(120) NOT NULL UNIQUE,
    data_referencia DATE NOT NULL,
    bloco_horario_6h INT NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    precisao_metros DOUBLE,
    cidade VARCHAR(80),
    uf VARCHAR(5),
    bairro VARCHAR(100),
    logradouro VARCHAR(180),
    dia_util BIT(1) NOT NULL DEFAULT b'1',
    quinto_dia_util BIT(1) NOT NULL DEFAULT b'0',
    resumo_calendario VARCHAR(255),
    temp_max DOUBLE,
    temp_min DOUBLE,
    temp_media DOUBLE,
    precipitacao_mm DOUBLE,
    vento_max_kmh DOUBLE,
    vazao_m3s DOUBLE,
    vazao_media_m3s DOUBLE,
    vazao_max_m3s DOUBLE,
    vazao_min_m3s DOUBLE,
    total_eventos_ativos INT NOT NULL DEFAULT 0,
    resumo_eventos_json VARCHAR(2000),
    INDEX idx_fator_data_cidade (data_referencia, cidade, uf)
) ENGINE=InnoDB;

-- 8. TABELA: nota (Herança SINGLE_TABLE: COMPRA, VENDA, DEVOLUCAO, PERDA)
CREATE TABLE IF NOT EXISTS nota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_nota VARCHAR(15) NOT NULL,
    id_pessoa BIGINT,
    descricao VARCHAR(500),
    data_gerada DATETIME(6) NOT NULL,
    data_recebida DATETIME(6),
    desconto_global DECIMAL(20, 4) DEFAULT 0.0000,
    cupom_aplicado VARCHAR(60),
    id_vinculo_fator_externo BIGINT,
    id_nota_venda BIGINT,
    motivo VARCHAR(300),
    tipo_perda VARCHAR(60),
    id_nota_devolucao BIGINT,
    id_nota_compra_origem BIGINT,
    id_nota_compra_abatimento BIGINT,
    CONSTRAINT fk_nota_pessoa FOREIGN KEY (id_pessoa) REFERENCES pessoa(id),
    CONSTRAINT fk_nota_vinculo_externo FOREIGN KEY (id_vinculo_fator_externo) REFERENCES vinculo_fator_externo_nota(id),
    CONSTRAINT fk_devolucao_venda FOREIGN KEY (id_nota_venda) REFERENCES nota(id),
    CONSTRAINT fk_perda_devolucao FOREIGN KEY (id_nota_devolucao) REFERENCES nota(id),
    CONSTRAINT fk_perda_compra_origem FOREIGN KEY (id_nota_compra_origem) REFERENCES nota(id),
    CONSTRAINT fk_perda_compra_abatimento FOREIGN KEY (id_nota_compra_abatimento) REFERENCES nota(id),
    INDEX idx_nota_tipo_data (tipo_nota, data_gerada),
    INDEX idx_nota_pessoa (id_pessoa),
    INDEX idx_nota_vinculo (id_vinculo_fator_externo)
) ENGINE=InnoDB;

-- 9. TABELA: item (Com Índice Composto Covering para Busca FIFO O(log N))
CREATE TABLE IF NOT EXISTS item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_nota BIGINT NOT NULL,
    id_produto BIGINT NOT NULL,
    id_item_origem BIGINT,
    valor DECIMAL(20, 4) NOT NULL,
    desconto DECIMAL(20, 4) NOT NULL DEFAULT 0.0000,
    quantidade DECIMAL(20, 6) NOT NULL,
    quantidade_disponivel DECIMAL(20, 6) NOT NULL DEFAULT 0.000000,
    data DATETIME(6) NOT NULL,
    data_validade DATE,
    CONSTRAINT fk_item_nota FOREIGN KEY (id_nota) REFERENCES nota(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_produto FOREIGN KEY (id_produto) REFERENCES produtos(id),
    CONSTRAINT fk_item_origem FOREIGN KEY (id_item_origem) REFERENCES item(id),
    INDEX idx_item_fifo (id_produto, quantidade_disponivel, data, data_validade),
    INDEX idx_item_nota (id_nota),
    INDEX idx_item_origem (id_item_origem)
) ENGINE=InnoDB;

-- 10. TABELA: nota_pagamento (Herança SINGLE_TABLE: 0=À Vista, 1=A Prazo, 2=Parcelado)
CREATE TABLE IF NOT EXISTS nota_pagamento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    modo_pagamento TINYINT NOT NULL,
    id_nota BIGINT NOT NULL,
    valor DECIMAL(20, 4) NOT NULL,
    data DATETIME(6) NOT NULL,
    desconto_a_vista DECIMAL(20, 4),
    valor_recebido DECIMAL(20, 4),
    data_confirmacao DATETIME(6),
    data_vencimento DATE,
    data_quitacao DATETIME(6),
    taxa_juros_diario DECIMAL(10, 4),
    percentual_multa DECIMAL(10, 4),
    quantidade_parcelas INT,
    taxa_juros_mensal DECIMAL(10, 4),
    intervalo_dias INT,
    primeiro_vencimento DATE,
    CONSTRAINT fk_pagamento_nota FOREIGN KEY (id_nota) REFERENCES nota(id) ON DELETE CASCADE,
    INDEX idx_pagamento_nota (id_nota),
    INDEX idx_pagamento_vencimento (modo_pagamento, data_vencimento, data_quitacao)
) ENGINE=InnoDB;

-- 11. TABELA: parcela_pagamento
CREATE TABLE IF NOT EXISTS parcela_pagamento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_pagamento_parcelado BIGINT NOT NULL,
    numero_parcela INT NOT NULL,
    valor_nominal DECIMAL(20, 4) NOT NULL,
    acrescimo_juros DECIMAL(20, 4) NOT NULL DEFAULT 0.0000,
    data_vencimento DATE NOT NULL,
    data_quitacao DATETIME(6),
    CONSTRAINT fk_parcela_pagamento FOREIGN KEY (id_pagamento_parcelado) REFERENCES nota_pagamento(id) ON DELETE CASCADE,
    INDEX idx_parcela_vencimento (data_vencimento, data_quitacao)
) ENGINE=InnoDB;

-- =========================================================================================
-- VIEWS OTIMIZADAS PARA BUSCAS PESADAS (ESTOQUE FIFO, SALDOS E SAZONALIDADE)
-- =========================================================================================

-- VIEW 1: Fila FIFO de Lotes Disponíveis (Permite listar a fila FIFO ou escolher o lote desejável)
CREATE OR REPLACE VIEW vw_lotes_fifo_disponiveis AS
SELECT
    i.id AS id_lote,
    i.id_produto,
    p.nome AS nome_produto,
    u.sigla AS unidade_medida,
    i.id_nota AS id_nota_compra,
    pe.nome AS nome_fornecedor,
    i.data AS data_entrada_lote,
    i.data_validade,
    DATEDIFF(i.data_validade, CURDATE()) AS dias_para_vencer,
    i.quantidade AS quantidade_original,
    i.quantidade_disponivel,
    i.valor AS custo_unitario_lote,
    p.v_venda AS preco_venda_atual,
    ROW_NUMBER() OVER (
        PARTITION BY i.id_produto
        ORDER BY i.data ASC, COALESCE(i.data_validade, '9999-12-31') ASC, i.id ASC
    ) AS posicao_fila_fifo
FROM item i
INNER JOIN produtos p ON p.id = i.id_produto
LEFT JOIN unidade_medida u ON u.id = p.um_id
INNER JOIN nota n ON n.id = i.id_nota AND n.tipo_nota = 'COMPRA'
LEFT JOIN pessoa pe ON pe.id = n.id_pessoa
WHERE i.quantidade_disponivel > 0
  AND (i.data_validade IS NULL OR i.data_validade >= CURDATE());

-- VIEW 2: Saldo Consolidado por Produto (Busca rápida de estoque sem varrer itens esgotados)
CREATE OR REPLACE VIEW vw_saldo_consolidado_estoque AS
SELECT
    p.id AS id_produto,
    p.nome AS nome_produto,
    p.is_digital,
    p.is_conjunto,
    u.sigla AS unidade,
    COUNT(i.id) AS total_lotes_ativos,
    COALESCE(SUM(i.quantidade_disponivel), 0) AS saldo_total_disponivel,
    MIN(i.data) AS lote_mais_antigo_fifo,
    MIN(i.data_validade) AS validade_mais_proxima,
    COALESCE(
        SUM(i.quantidade_disponivel * i.valor) / NULLIF(SUM(i.quantidade_disponivel), 0),
        0
    ) AS custo_medio_ponderado,
    p.v_venda AS preco_venda
FROM produtos p
LEFT JOIN unidade_medida u ON u.id = p.um_id
LEFT JOIN item i ON i.id_produto = p.id
    AND i.quantidade_disponivel > 0
    AND (i.data_validade IS NULL OR i.data_validade >= CURDATE())
GROUP BY p.id, p.nome, p.is_digital, p.is_conjunto, u.sigla, p.v_venda;

-- VIEW 3: Análise Sazonal de Vendas x Fatores Externos (Clima, Rios, Feriados, Eventos)
CREATE OR REPLACE VIEW vw_analise_sazonal_vendas AS
SELECT
    v.chave_snapshot,
    v.data_referencia,
    v.bloco_horario_6h,
    v.cidade,
    v.uf,
    v.dia_util,
    v.quinto_dia_util,
    v.temp_media,
    v.precipitacao_mm,
    v.vazao_m3s,
    v.total_eventos_ativos,
    COUNT(DISTINCT n.id) AS total_notas_venda,
    COALESCE(SUM(it.quantidade * it.valor - it.desconto), 0) AS faturamento_liquido_itens
FROM vinculo_fator_externo_nota v
LEFT JOIN nota n ON n.id_vinculo_fator_externo = v.id AND n.tipo_nota = 'VENDA'
LEFT JOIN item it ON it.id_nota = n.id
GROUP BY
    v.id, v.chave_snapshot, v.data_referencia, v.bloco_horario_6h,
    v.cidade, v.uf, v.dia_util, v.quinto_dia_util,
    v.temp_media, v.precipitacao_mm, v.vazao_m3s, v.total_eventos_ativos;

-- VIEW 4: Perdas por Fornecedor e Produto (Destaque para Perda Pós-Venda dentro do Período de Validade)
CREATE OR REPLACE VIEW vw_perdas_por_fornecedor AS
SELECT
    np.id AS id_nota_perda,
    np.data_gerada AS data_perda,
    np.tipo_perda,
    np.descricao AS motivo_perda,
    CASE WHEN np.id_nota_devolucao IS NOT NULL THEN 1 ELSE 0 END AS is_perda_pos_venda,
    np.id_nota_devolucao,
    COALESCE(np.id_nota_compra_origem, ic_orig.id_nota) AS id_nota_compra_origem,
    np.id_nota_compra_abatimento,
    COALESCE(forn_direto.id, forn_compra.id) AS id_fornecedor,
    COALESCE(forn_direto.nome, forn_compra.nome) AS nome_fornecedor,
    COALESCE(forn_direto.cnpj, forn_compra.cnpj) AS cnpj_fornecedor,
    ip.id AS id_item_perda,
    p.id AS id_produto,
    p.nome AS nome_produto,
    ip.quantidade AS quantidade_perdida,
    ip.valor AS valor_custo_unitario,
    (ip.quantidade * ip.valor - ip.desconto) AS valor_total_custo_perda,
    ip.data_validade,
    CASE
        WHEN ip.data_validade IS NULL OR CAST(np.data_gerada AS DATE) <= ip.data_validade THEN 1
        ELSE 0
    END AS dentro_periodo_validade,
    CASE
        WHEN np.id_nota_devolucao IS NOT NULL
         AND np.id_nota_compra_abatimento IS NULL
         AND (ip.data_validade IS NULL OR CAST(np.data_gerada AS DATE) <= ip.data_validade) THEN 1
        ELSE 0
    END AS elegivel_abatimento_pos_venda
FROM nota np
INNER JOIN item ip ON ip.id_nota = np.id
INNER JOIN produtos p ON p.id = ip.id_produto
LEFT JOIN pessoa forn_direto ON forn_direto.id = np.id_pessoa
LEFT JOIN item ic_orig ON ic_orig.id = ip.id_item_origem
LEFT JOIN nota nc_orig ON nc_orig.id = COALESCE(np.id_nota_compra_origem, ic_orig.id_nota)
LEFT JOIN pessoa forn_compra ON forn_compra.id = nc_orig.id_pessoa
WHERE np.tipo_nota = 'PERDA';

-- =========================================================================================
-- STORED PROCEDURES TRANSACIONAIS (COM BLOQUEIO PESSIMISTA FOR UPDATE PARA CONFIABILIDADE)
-- =========================================================================================

DROP PROCEDURE IF EXISTS sp_consumir_estoque_fifo;
DROP PROCEDURE IF EXISTS sp_consumir_lote_especifico;

DELIMITER $$

-- PROCEDURE 1: Baixa automática de estoque pelo algoritmo FIFO com bloqueio de linha (FOR UPDATE)
CREATE PROCEDURE sp_consumir_estoque_fifo(
    IN p_id_nota_venda BIGINT,
    IN p_id_produto BIGINT,
    IN p_quantidade_total DECIMAL(20, 6),
    IN p_preco_venda DECIMAL(20, 4)
)
BEGIN
    DECLARE v_restante DECIMAL(20, 6) DEFAULT p_quantidade_total;
    DECLARE v_saldo_total DECIMAL(20, 6) DEFAULT 0;
    DECLARE v_id_lote BIGINT;
    DECLARE v_qtd_disp DECIMAL(20, 6);
    DECLARE v_custo_lote DECIMAL(20, 4);
    DECLARE v_validade DATE;
    DECLARE v_consumir DECIMAL(20, 6);
    DECLARE v_fim_cursor INT DEFAULT 0;

    DECLARE cur_fifo CURSOR FOR
        SELECT id, quantidade_disponivel, valor, data_validade
        FROM item
        WHERE id_produto = p_id_produto
          AND quantidade_disponivel > 0
          AND (data_validade IS NULL OR data_validade >= CURDATE())
        ORDER BY data ASC, COALESCE(data_validade, '9999-12-31') ASC, id ASC
        FOR UPDATE;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fim_cursor = 1;

    IF p_quantidade_total <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A quantidade solicitada para baixa FIFO deve ser maior que zero.';
    END IF;

    SELECT COALESCE(SUM(quantidade_disponivel), 0) INTO v_saldo_total
    FROM item
    WHERE id_produto = p_id_produto
      AND quantidade_disponivel > 0
      AND (data_validade IS NULL OR data_validade >= CURDATE());

    IF v_saldo_total < p_quantidade_total THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Estoque insuficiente (FIFO) para atender a quantidade solicitada.';
    END IF;

    OPEN cur_fifo;

    loop_lotes: LOOP
        IF v_restante <= 0 THEN
            LEAVE loop_lotes;
        END IF;

        FETCH cur_fifo INTO v_id_lote, v_qtd_disp, v_custo_lote, v_validade;
        IF v_fim_cursor = 1 THEN
            LEAVE loop_lotes;
        END IF;

        SET v_consumir = LEAST(v_qtd_disp, v_restante);

        UPDATE item
        SET quantidade_disponivel = quantidade_disponivel - v_consumir
        WHERE id = v_id_lote;

        INSERT INTO item (
            id_nota, id_produto, id_item_origem, valor, desconto,
            quantidade, quantidade_disponivel, data, data_validade
        ) VALUES (
            p_id_nota_venda,
            p_id_produto,
            v_id_lote,
            COALESCE(NULLIF(p_preco_venda, 0), v_custo_lote),
            0.0000,
            v_consumir,
            0.000000,
            NOW(6),
            v_validade
        );

        SET v_restante = v_restante - v_consumir;
    END LOOP;

    CLOSE cur_fifo;
END$$

-- PROCEDURE 2: Baixa de estoque permitindo escolher o Lote Desejável com validação e FOR UPDATE
CREATE PROCEDURE sp_consumir_lote_especifico(
    IN p_id_nota_venda BIGINT,
    IN p_id_lote_desejado BIGINT,
    IN p_quantidade DECIMAL(20, 6),
    IN p_preco_venda DECIMAL(20, 4)
)
BEGIN
    DECLARE v_id_produto BIGINT;
    DECLARE v_qtd_disp DECIMAL(20, 6);
    DECLARE v_custo_lote DECIMAL(20, 4);
    DECLARE v_validade DATE;

    SELECT id_produto, quantidade_disponivel, valor, data_validade
    INTO v_id_produto, v_qtd_disp, v_custo_lote, v_validade
    FROM item
    WHERE id = p_id_lote_desejado
    FOR UPDATE;

    IF v_id_produto IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lote desejado não encontrado.';
    END IF;

    IF v_validade IS NOT NULL AND v_validade < CURDATE() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O lote escolhido está vencido e não pode ser vendido.';
    END IF;

    IF v_qtd_disp < p_quantidade OR p_quantidade <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Saldo insuficiente no lote específico escolhido.';
    END IF;

    UPDATE item
    SET quantidade_disponivel = quantidade_disponivel - p_quantidade
    WHERE id = p_id_lote_desejado;

    INSERT INTO item (
        id_nota, id_produto, id_item_origem, valor, desconto,
        quantidade, quantidade_disponivel, data, data_validade
    ) VALUES (
        p_id_nota_venda,
        v_id_produto,
        p_id_lote_desejado,
        COALESCE(NULLIF(p_preco_venda, 0), v_custo_lote),
        0.0000,
        p_quantidade,
        0.000000,
        NOW(6),
        v_validade
    );
END$$

DELIMITER ;
