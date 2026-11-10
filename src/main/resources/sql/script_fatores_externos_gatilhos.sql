-- =====================================================================================================================
-- FATORES EXTERNOS: EVENTOS PERSISTIDOS, GATILHOS DE ATRIBUIÇÃO, FILA DE PENDÊNCIAS E ESTATÍSTICA DE CAUSAS (MySQL 8)
-- Relações cobertas:
--   * 1 fator externo (vinculo_fator_externo_nota) -> N notas              (nota.id_vinculo_fator_externo)
--   * 1 fator externo -> N eventos reais (Sympla/MinC)                      (vinculo_evento -> evento_externo)
--   * 1 nota -> N produtos                                                  (item)
--   * 1 produto -> N produtos (kit)                                         (conjunto_produto)
--   * perda -> compra de origem / compra que ressarciu / devolução / venda (colunas de nota)
-- O arquivo é lido pelo Java (SqlScriptRunner) e suporta DELIMITER $$.
-- =====================================================================================================================

CREATE TABLE IF NOT EXISTS evento_externo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fonte VARCHAR(12) NOT NULL,
    id_externo VARCHAR(60) NOT NULL,
    nome VARCHAR(300) NOT NULL,
    local_nome VARCHAR(200),
    bairro VARCHAR(100),
    cidade VARCHAR(80) NOT NULL,
    uf VARCHAR(5) NOT NULL,
    data_inicio DATE NULL,
    data_fim DATE NULL,
    data_texto VARCHAR(120),
    distancia_km DOUBLE,
    url VARCHAR(500),
    segmento VARCHAR(120),
    situacao VARCHAR(80),
    proponente VARCHAR(200),
    resumo VARCHAR(600),
    coletado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_evento_fonte_id (fonte, id_externo),
    INDEX idx_evento_local_coleta (cidade, uf, coletado_em),
    INDEX idx_evento_data (data_inicio, data_fim)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vinculo_evento (
    id_vinculo BIGINT NOT NULL,
    id_evento BIGINT NOT NULL,
    PRIMARY KEY (id_vinculo, id_evento),
    INDEX idx_vinculo_evento_evento (id_evento),
    CONSTRAINT fk_ve_vinculo FOREIGN KEY (id_vinculo) REFERENCES vinculo_fator_externo_nota(id) ON DELETE CASCADE,
    CONSTRAINT fk_ve_evento FOREIGN KEY (id_evento) REFERENCES evento_externo(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fila_fator_externo_pendente (
    id_nota BIGINT NOT NULL PRIMARY KEY,
    data_referencia DATE NOT NULL,
    bloco_horario_6h INT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fila_chave (data_referencia, bloco_horario_6h),
    CONSTRAINT fk_fila_nota FOREIGN KEY (id_nota) REFERENCES nota(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fator_externo_estatistica (
    id_produto BIGINT NOT NULL,
    tipo_fator VARCHAR(24) NOT NULL,
    n_com INT NOT NULL,
    n_sem INT NOT NULL,
    media_qtd_com DECIMAL(20,6),
    media_qtd_sem DECIMAL(20,6),
    desvio_com DECIMAL(20,6),
    desvio_sem DECIMAL(20,6),
    lift_pct DECIMAL(12,2),
    z_welch DECIMAL(12,4),
    significancia VARCHAR(14) NOT NULL,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_produto, tipo_fator),
    INDEX idx_fee_fator (tipo_fator, z_welch)
) ENGINE=InnoDB;

-- Fatores ativos por snapshot. Seca/cheia são relativas à média do próprio rio (razão vazão/vazão média),
-- independente da unidade ou do rio monitorado.
CREATE OR REPLACE VIEW vw_vinculo_fatores_ativos AS
SELECT v.id AS id_vinculo, 'CALOR' AS tipo_fator, v.temp_media AS intensidade
  FROM vinculo_fator_externo_nota v WHERE v.temp_media >= 30.0
UNION ALL
SELECT v.id, 'CHUVA', v.precipitacao_mm FROM vinculo_fator_externo_nota v WHERE v.precipitacao_mm > 10.0
UNION ALL
SELECT v.id, 'EVENTO', v.total_eventos_ativos FROM vinculo_fator_externo_nota v WHERE v.total_eventos_ativos > 0
UNION ALL
SELECT v.id, 'QUINTO_DIA_UTIL', 1 FROM vinculo_fator_externo_nota v WHERE v.quinto_dia_util = b'1'
UNION ALL
SELECT v.id, 'FIM_SEMANA_FERIADO', 1 FROM vinculo_fator_externo_nota v WHERE v.dia_util = b'0'
UNION ALL
SELECT v.id, 'SECA_RIO', ROUND(v.vazao_m3s / v.vazao_media_m3s, 4)
  FROM vinculo_fator_externo_nota v WHERE v.vazao_media_m3s > 0 AND v.vazao_m3s / v.vazao_media_m3s < 0.85
UNION ALL
SELECT v.id, 'CHEIA_RIO', ROUND(v.vazao_m3s / v.vazao_media_m3s, 4)
  FROM vinculo_fator_externo_nota v WHERE v.vazao_media_m3s > 0 AND v.vazao_m3s / v.vazao_media_m3s > 1.15;

DROP TRIGGER IF EXISTS trg_nota_atribuir_fator_externo;
DROP TRIGGER IF EXISTS trg_nota_fila_fator_pendente;
DROP PROCEDURE IF EXISTS sp_recalcular_estatisticas_fatores;
DROP PROCEDURE IF EXISTS sp_causas_externas_por_demanda;

DELIMITER $$

-- GATILHO 1: toda nota inserida sem fator externo recebe, SEM nenhuma requisição externa, o snapshot já salvo
-- (cache persistente) da mesma data e janela de 6h.
CREATE TRIGGER trg_nota_atribuir_fator_externo BEFORE INSERT ON nota FOR EACH ROW
BEGIN
    IF NEW.id_vinculo_fator_externo IS NULL THEN
        SET NEW.id_vinculo_fator_externo = (
            SELECT v.id FROM vinculo_fator_externo_nota v
            WHERE v.data_referencia = DATE(NEW.data_gerada)
              AND v.bloco_horario_6h = FLOOR(HOUR(NEW.data_gerada) / 6)
            ORDER BY v.id LIMIT 1
        );
    END IF;
END$$

-- GATILHO 2: se não houver snapshot salvo para a janela, a nota entra na fila. O Java resolve a fila fazendo
-- UMA única requisição por janela (data + bloco de 6h), nunca uma por nota.
CREATE TRIGGER trg_nota_fila_fator_pendente AFTER INSERT ON nota FOR EACH ROW
BEGIN
    IF NEW.id_vinculo_fator_externo IS NULL THEN
        INSERT IGNORE INTO fila_fator_externo_pendente (id_nota, data_referencia, bloco_horario_6h)
        VALUES (NEW.id, DATE(NEW.data_gerada), FLOOR(HOUR(NEW.data_gerada) / 6));
    END IF;
END$$

-- Recalcula (set-based) o efeito de cada fator externo sobre a quantidade vendida de cada produto.
-- Observação = quantidade do produto vendida em um snapshot (janela de 6h). Teste de Welch (com x sem o fator).
CREATE PROCEDURE sp_recalcular_estatisticas_fatores()
BEGIN
    DROP TEMPORARY TABLE IF EXISTS tmp_obs_fator;
    CREATE TEMPORARY TABLE tmp_obs_fator AS
        SELECT iv.id_produto, nv.id_vinculo_fator_externo AS id_vinculo, SUM(iv.quantidade) AS qtd
        FROM nota nv
        INNER JOIN item iv ON iv.id_nota = nv.id
        WHERE nv.tipo_nota = 'VENDA' AND nv.id_vinculo_fator_externo IS NOT NULL
        GROUP BY iv.id_produto, nv.id_vinculo_fator_externo;

    DELETE FROM fator_externo_estatistica;

    INSERT INTO fator_externo_estatistica
        (id_produto, tipo_fator, n_com, n_sem, media_qtd_com, media_qtd_sem, desvio_com, desvio_sem, lift_pct, z_welch, significancia)
    SELECT
        s.id_produto, s.tipo_fator, s.n_com, s.n_sem, s.m_com, s.m_sem, s.d_com, s.d_sem,
        ROUND(CASE WHEN s.m_sem > 0 THEN (s.m_com - s.m_sem) / s.m_sem * 100.0 END, 2) AS lift_pct,
        ROUND(CASE WHEN s.n_com > 1 AND s.n_sem > 1 AND (s.v_com / s.n_com + s.v_sem / s.n_sem) > 0
                   THEN (s.m_com - s.m_sem) / SQRT(s.v_com / s.n_com + s.v_sem / s.n_sem) END, 4) AS z_welch,
        CASE
            WHEN s.n_com < 2 OR s.n_sem < 2 THEN 'INSUFICIENTE'
            WHEN ABS((s.m_com - s.m_sem) / NULLIF(SQRT(s.v_com / s.n_com + s.v_sem / s.n_sem), 0)) >= 1.96 THEN 'ALTA'
            WHEN ABS((s.m_com - s.m_sem) / NULLIF(SQRT(s.v_com / s.n_com + s.v_sem / s.n_sem), 0)) >= 1.28 THEN 'MEDIA'
            ELSE 'BAIXA'
        END AS significancia
    FROM (
        SELECT
            o.id_produto, f.tipo_fator,
            SUM(a.id_vinculo IS NOT NULL) AS n_com,
            SUM(a.id_vinculo IS NULL) AS n_sem,
            AVG(CASE WHEN a.id_vinculo IS NOT NULL THEN o.qtd END) AS m_com,
            AVG(CASE WHEN a.id_vinculo IS NULL THEN o.qtd END) AS m_sem,
            COALESCE(STDDEV_SAMP(CASE WHEN a.id_vinculo IS NOT NULL THEN o.qtd END), 0) AS d_com,
            COALESCE(STDDEV_SAMP(CASE WHEN a.id_vinculo IS NULL THEN o.qtd END), 0) AS d_sem,
            COALESCE(VAR_SAMP(CASE WHEN a.id_vinculo IS NOT NULL THEN o.qtd END), 0) AS v_com,
            COALESCE(VAR_SAMP(CASE WHEN a.id_vinculo IS NULL THEN o.qtd END), 0) AS v_sem
        FROM tmp_obs_fator o
        CROSS JOIN (SELECT 'CALOR' AS tipo_fator UNION ALL SELECT 'CHUVA' UNION ALL SELECT 'EVENTO'
                    UNION ALL SELECT 'QUINTO_DIA_UTIL' UNION ALL SELECT 'FIM_SEMANA_FERIADO'
                    UNION ALL SELECT 'SECA_RIO' UNION ALL SELECT 'CHEIA_RIO') f
        LEFT JOIN vw_vinculo_fatores_ativos a ON a.id_vinculo = o.id_vinculo AND a.tipo_fator = f.tipo_fator
        GROUP BY o.id_produto, f.tipo_fator
    ) s
    WHERE s.n_com > 0;

    DROP TEMPORARY TABLE IF EXISTS tmp_obs_fator;
END$$

-- Causas externas POR DEMANDA de uma nota (e opcionalmente de um produto). Devolve 4 resultados:
--   1) fatores ativos + efeito estatístico por produto da nota; 2) eventos reais do snapshot;
--   3) notas relacionadas (origem, devolução, perda, ressarcimento, mesma janela); 4) produtos relacionados (kits).
CREATE PROCEDURE sp_causas_externas_por_demanda(IN p_id_nota BIGINT, IN p_id_produto BIGINT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM fator_externo_estatistica WHERE atualizado_em > NOW() - INTERVAL 1 DAY) THEN
        CALL sp_recalcular_estatisticas_fatores();
    END IF;

    -- 1) Fatores ativos x efeito estatístico no produto
    SELECT n.id AS id_nota, n.tipo_nota, n.id_vinculo_fator_externo AS id_vinculo,
           p.id AS id_produto, p.nome AS nome_produto, a.tipo_fator, a.intensidade,
           e.n_com, e.n_sem, e.lift_pct, e.z_welch, COALESCE(e.significancia, 'SEM_HISTORICO') AS significancia,
           CASE
               WHEN e.lift_pct IS NULL OR e.significancia IN ('INSUFICIENTE', 'BAIXA') THEN 'CAUSA_POSSIVEL_SEM_EVIDENCIA_ESTATISTICA'
               WHEN e.lift_pct > 0 THEN 'CAUSA_PROVAVEL_AUMENTO_DE_DEMANDA'
               ELSE 'CAUSA_PROVAVEL_QUEDA_DE_DEMANDA'
           END AS leitura
    FROM nota n
    INNER JOIN vw_vinculo_fatores_ativos a ON a.id_vinculo = n.id_vinculo_fator_externo
    INNER JOIN (SELECT DISTINCT id_nota, id_produto FROM item) i ON i.id_nota = n.id
    INNER JOIN produtos p ON p.id = i.id_produto
    LEFT JOIN fator_externo_estatistica e ON e.id_produto = p.id AND e.tipo_fator = a.tipo_fator
    WHERE n.id = p_id_nota AND (p_id_produto IS NULL OR p.id = p_id_produto)
    ORDER BY p.id, ABS(COALESCE(e.z_welch, 0)) DESC;

    -- 2) Eventos reais ligados ao snapshot da nota
    SELECT ev.id AS id_evento, ev.fonte, ev.nome, ev.local_nome, ev.bairro, ev.cidade, ev.uf,
           ev.data_inicio, ev.data_fim, ev.data_texto, ev.distancia_km, ev.url
    FROM nota n
    INNER JOIN vinculo_evento ve ON ve.id_vinculo = n.id_vinculo_fator_externo
    INNER JOIN evento_externo ev ON ev.id = ve.id_evento
    WHERE n.id = p_id_nota
    ORDER BY ev.distancia_km IS NULL, ev.distancia_km, ev.id;

    -- 3) Notas relacionadas
    SELECT 'COMPRA_ORIGEM_DO_LOTE' AS relacao, nc.id AS id_nota_relacionada, nc.tipo_nota, nc.data_gerada
    FROM nota n INNER JOIN item i ON i.id_nota = n.id INNER JOIN item io ON io.id = i.id_item_origem INNER JOIN nota nc ON nc.id = io.id_nota
    WHERE n.id = p_id_nota
    UNION
    SELECT 'VENDA_ORIGINAL_DA_DEVOLUCAO', nv.id, nv.tipo_nota, nv.data_gerada
    FROM nota n INNER JOIN nota nv ON nv.id = n.id_nota_venda WHERE n.id = p_id_nota
    UNION
    SELECT 'DEVOLUCAO_QUE_GEROU_A_PERDA', nd.id, nd.tipo_nota, nd.data_gerada
    FROM nota n INNER JOIN nota nd ON nd.id = n.id_nota_devolucao WHERE n.id = p_id_nota
    UNION
    SELECT 'COMPRA_DE_ORIGEM_DA_PERDA', nc.id, nc.tipo_nota, nc.data_gerada
    FROM nota n INNER JOIN nota nc ON nc.id = n.id_nota_compra_origem WHERE n.id = p_id_nota
    UNION
    SELECT 'COMPRA_QUE_RESSARCIU_A_PERDA', nr.id, nr.tipo_nota, nr.data_gerada
    FROM nota n INNER JOIN nota nr ON nr.id = n.id_nota_compra_abatimento WHERE n.id = p_id_nota
    UNION
    SELECT 'PERDA_RESSARCIDA_POR_ESTA_COMPRA', np.id, np.tipo_nota, np.data_gerada
    FROM nota np WHERE np.id_nota_compra_abatimento = p_id_nota
    UNION
    SELECT 'MESMA_JANELA_EXTERNA', no.id, no.tipo_nota, no.data_gerada
    FROM nota n INNER JOIN nota no ON no.id_vinculo_fator_externo = n.id_vinculo_fator_externo AND no.id <> n.id
    WHERE n.id = p_id_nota AND n.id_vinculo_fator_externo IS NOT NULL
    ORDER BY relacao, id_nota_relacionada
    LIMIT 200;

    -- 4) Produtos relacionados (componentes de kit e kits que usam o produto)
    SELECT 'COMPONENTE_DO_KIT' AS relacao, kit.id AS id_produto_base, kit.nome AS nome_base,
           comp.id AS id_produto_relacionado, comp.nome AS nome_relacionado, cp.quantidade
    FROM (SELECT DISTINCT id_produto FROM item WHERE id_nota = p_id_nota) i
    INNER JOIN conjunto_produto cp ON cp.id_produto = i.id_produto
    INNER JOIN produtos kit ON kit.id = cp.id_produto
    INNER JOIN produtos comp ON comp.id = cp.id_item
    UNION ALL
    SELECT 'USADO_NO_KIT', base.id, base.nome, kit.id, kit.nome, cp.quantidade
    FROM (SELECT DISTINCT id_produto FROM item WHERE id_nota = p_id_nota) i
    INNER JOIN conjunto_produto cp ON cp.id_item = i.id_produto
    INNER JOIN produtos base ON base.id = cp.id_item
    INNER JOIN produtos kit ON kit.id = cp.id_produto;
END$$

DELIMITER ;
