package br.com.senac.repository.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class JPAUtil {

    // "sazonalisei" = Seu MySQL local | "sazonalisei-embarcado" = Banco H2 Embarcado (modo MySQL)
    private static final String PERSISTENCE_UNIT_MYSQL = "sazonalisei";
    private static final String PERSISTENCE_UNIT_EMBARCADO = "sazonalisei-embarcado";

    private static EntityManagerFactory emf;

    private JPAUtil() {
    }

    public static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            garantirArquivoPersistence();
            boolean isMySql = false;
            try {
                // 1ª Tentativa: Usa a sua persistência principal ("sazonalisei" - MySQL) se existir no persistence.xml
                emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_MYSQL);
                isMySql = true;
            } catch (Exception e) {
                // Fallback automático: Se quem clonou do Git estiver apenas com o 'sazonalisei-embarcado', inicia o H2 embarcado
                System.out.println("📦 [JPAUtil] Persistência MySQL não encontrada ou indisponível. Iniciando banco embarcado (" + PERSISTENCE_UNIT_EMBARCADO + ")...");
                emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_EMBARCADO);
            }
            inicializarViewsEProceduresNoBanco(isMySql);
        }
        return emf;
    }

    /**
     * Inicializa automaticamente as Views de busca pesada e as Stored Procedures de estoque FIFO/Lote Específico
     * diretamente no banco de dados após o Hibernate criar/atualizar as tabelas.
     */
    private static void inicializarViewsEProceduresNoBanco(boolean isMySql) {
        try (EntityManager em = emf.createEntityManager()) {
            var tx = em.getTransaction();
            tx.begin();

            // View 1: Saldo Consolidado por Produto
            em.createNativeQuery(
                    "CREATE OR REPLACE VIEW vw_saldo_consolidado_estoque AS " +
                    "SELECT p.id AS id_produto, p.nome AS nome_produto, p.is_digital, p.is_conjunto, u.sigla AS unidade, " +
                    "COUNT(i.id) AS total_lotes_ativos, COALESCE(SUM(i.quantidade_disponivel), 0) AS saldo_total_disponivel, " +
                    "MIN(i.data) AS lote_mais_antigo_fifo, MIN(i.data_validade) AS validade_mais_proxima, " +
                    "COALESCE(SUM(i.quantidade_disponivel * i.valor) / NULLIF(SUM(i.quantidade_disponivel), 0), 0) AS custo_medio_ponderado, " +
                    "p.v_venda AS preco_venda " +
                    "FROM produtos p " +
                    "LEFT JOIN unidade_medida u ON u.id = p.um_id " +
                    "LEFT JOIN item i ON i.id_produto = p.id AND i.quantidade_disponivel > 0 " +
                    "AND (i.data_validade IS NULL OR i.data_validade >= CURRENT_DATE) " +
                    "GROUP BY p.id, p.nome, p.is_digital, p.is_conjunto, u.sigla, p.v_venda"
            ).executeUpdate();

            // View 2: Fila FIFO de Lotes Disponíveis
            em.createNativeQuery(
                    "CREATE OR REPLACE VIEW vw_lotes_fifo_disponiveis AS " +
                    "SELECT i.id AS id_lote, i.id_produto, p.nome AS nome_produto, u.sigla AS unidade_medida, " +
                    "i.id_nota AS id_nota_compra, pe.nome AS nome_fornecedor, i.data AS data_entrada_lote, " +
                    "i.data_validade, i.quantidade AS quantidade_original, i.quantidade_disponivel, " +
                    "i.valor AS custo_unitario_lote, p.v_venda AS preco_venda_atual " +
                    "FROM item i " +
                    "INNER JOIN produtos p ON p.id = i.id_produto " +
                    "LEFT JOIN unidade_medida u ON u.id = p.um_id " +
                    "INNER JOIN nota n ON n.id = i.id_nota AND n.tipo_nota = 'COMPRA' " +
                    "LEFT JOIN pessoa pe ON pe.id = n.id_pessoa " +
                    "WHERE i.quantidade_disponivel > 0 AND (i.data_validade IS NULL OR i.data_validade >= CURRENT_DATE)"
            ).executeUpdate();

            // View 3: Análise Sazonal de Vendas
            em.createNativeQuery(
                    "CREATE OR REPLACE VIEW vw_analise_sazonal_vendas AS " +
                    "SELECT v.chave_snapshot, v.data_referencia, v.bloco_horario_6h, v.cidade, v.uf, " +
                    "v.dia_util, v.quinto_dia_util, v.temp_media, v.precipitacao_mm, v.vazao_m3s, v.total_eventos_ativos, " +
                    "COUNT(DISTINCT n.id) AS total_notas_venda, " +
                    "COALESCE(SUM(it.quantidade * it.valor - it.desconto), 0) AS faturamento_liquido_itens " +
                    "FROM vinculo_fator_externo_nota v " +
                    "LEFT JOIN nota n ON n.id_vinculo_fator_externo = v.id AND n.tipo_nota = 'VENDA' " +
                    "LEFT JOIN item it ON it.id_nota = n.id " +
                    "GROUP BY v.id, v.chave_snapshot, v.data_referencia, v.bloco_horario_6h, v.cidade, v.uf, " +
                    "v.dia_util, v.quinto_dia_util, v.temp_media, v.precipitacao_mm, v.vazao_m3s, v.total_eventos_ativos"
            ).executeUpdate();

            // View 4: Perdas por Fornecedor e Produto (Perda Pós-Venda dentro do Período de Validade)
            em.createNativeQuery(
                    "CREATE OR REPLACE VIEW vw_perdas_por_fornecedor AS " +
                    "SELECT np.id AS id_nota_perda, np.data_gerada AS data_perda, np.tipo_perda, np.descricao AS motivo_perda, " +
                    "CASE WHEN np.id_nota_devolucao IS NOT NULL THEN 1 ELSE 0 END AS is_perda_pos_venda, " +
                    "np.id_nota_devolucao, COALESCE(np.id_nota_compra_origem, ic_orig.id_nota) AS id_nota_compra_origem, " +
                    "np.id_nota_compra_abatimento, COALESCE(forn_direto.id, forn_compra.id) AS id_fornecedor, " +
                    "COALESCE(forn_direto.nome, forn_compra.nome) AS nome_fornecedor, COALESCE(forn_direto.cnpj, forn_compra.cnpj) AS cnpj_fornecedor, " +
                    "ip.id AS id_item_perda, p.id AS id_produto, p.nome AS nome_produto, ip.quantidade AS quantidade_perdida, " +
                    "ip.valor AS valor_custo_unitario, (ip.quantidade * ip.valor - ip.desconto) AS valor_total_custo_perda, ip.data_validade, " +
                    "CASE WHEN ip.data_validade IS NULL OR CAST(np.data_gerada AS DATE) <= ip.data_validade THEN 1 ELSE 0 END AS dentro_periodo_validade, " +
                    "CASE WHEN np.id_nota_devolucao IS NOT NULL AND np.id_nota_compra_abatimento IS NULL " +
                    "AND (ip.data_validade IS NULL OR CAST(np.data_gerada AS DATE) <= ip.data_validade) THEN 1 ELSE 0 END AS elegivel_abatimento_pos_venda " +
                    "FROM nota np " +
                    "INNER JOIN item ip ON ip.id_nota = np.id " +
                    "INNER JOIN produtos p ON p.id = ip.id_produto " +
                    "LEFT JOIN pessoa forn_direto ON forn_direto.id = np.id_pessoa " +
                    "LEFT JOIN item ic_orig ON ic_orig.id = ip.id_item_origem " +
                    "LEFT JOIN nota nc_orig ON nc_orig.id = COALESCE(np.id_nota_compra_origem, ic_orig.id_nota) " +
                    "LEFT JOIN pessoa forn_compra ON forn_compra.id = nc_orig.id_pessoa " +
                    "WHERE np.tipo_nota = 'PERDA'"
            ).executeUpdate();

            // View 5: Mapeamento Contábil de Lucro (CMV do lote FIFO, Receita Bruta/Líquida, Lucro Bruto/Líquido e Margem %)
            em.createNativeQuery(
                    "CREATE OR REPLACE VIEW vw_apuracao_lucro_contabil AS " +
                    "SELECT nv.id AS id_nota_venda, nv.data_gerada AS data_venda, cli.nome AS nome_cliente, " +
                    "iv.id AS id_item_venda, p.id AS id_produto, p.nome AS nome_produto, u.sigla AS unidade, " +
                    "iv.id_item_origem AS id_lote_origem_fifo, iv.quantidade AS quantidade_vendida, " +
                    "COALESCE(lote_orig.valor, 0) AS custo_unitario_lote, iv.valor AS preco_venda_unitario, " +
                    "ROUND(iv.quantidade * COALESCE(lote_orig.valor, 0), 2) AS cmv_custo_total_mercadoria, " +
                    "ROUND(iv.quantidade * iv.valor, 2) AS receita_bruta_item, ROUND(iv.desconto, 2) AS desconto_item, " +
                    "ROUND((iv.quantidade * iv.valor) - iv.desconto, 2) AS receita_liquida_item, " +
                    "ROUND((iv.quantidade * iv.valor) - (iv.quantidade * COALESCE(lote_orig.valor, 0)), 2) AS lucro_bruto_item, " +
                    "ROUND(((iv.quantidade * iv.valor) - iv.desconto) - (iv.quantidade * COALESCE(lote_orig.valor, 0)), 2) AS lucro_liquido_item, " +
                    "ROUND(CASE WHEN (iv.quantidade * COALESCE(lote_orig.valor, 0)) > 0 THEN " +
                    "((((iv.quantidade * iv.valor) - iv.desconto) - (iv.quantidade * COALESCE(lote_orig.valor, 0))) " +
                    "/ (iv.quantidade * COALESCE(lote_orig.valor, 0))) * 100 ELSE 100 END, 2) AS margem_lucro_percentual " +
                    "FROM nota nv " +
                    "INNER JOIN item iv ON iv.id_nota = nv.id " +
                    "INNER JOIN produtos p ON p.id = iv.id_produto " +
                    "LEFT JOIN unidade_medida u ON u.id = p.um_id " +
                    "LEFT JOIN item lote_orig ON lote_orig.id = iv.id_item_origem " +
                    "LEFT JOIN pessoa cli ON cli.id = nv.id_pessoa " +
                    "WHERE nv.tipo_nota = 'VENDA'"
            ).executeUpdate();

            // Tabelas de Fatores Externos e Eventos (compatíveis com H2 e MySQL)
            em.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS evento_externo (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "fonte VARCHAR(12) NOT NULL, " +
                    "id_externo VARCHAR(60) NOT NULL, " +
                    "nome VARCHAR(300) NOT NULL, " +
                    "local_nome VARCHAR(200), " +
                    "bairro VARCHAR(100), " +
                    "cidade VARCHAR(80) NOT NULL, " +
                    "uf VARCHAR(5) NOT NULL, " +
                    "data_inicio DATE NULL, " +
                    "data_fim DATE NULL, " +
                    "data_texto VARCHAR(120), " +
                    "distancia_km DOUBLE, " +
                    "url VARCHAR(500), " +
                    "segmento VARCHAR(120), " +
                    "situacao VARCHAR(80), " +
                    "proponente VARCHAR(200), " +
                    "resumo VARCHAR(600), " +
                    "coletado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            ).executeUpdate();

            em.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS vinculo_evento (" +
                    "id_vinculo BIGINT NOT NULL, " +
                    "id_evento BIGINT NOT NULL, " +
                    "PRIMARY KEY (id_vinculo, id_evento)" +
                    ")"
            ).executeUpdate();

            em.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS fila_fator_externo_pendente (" +
                    "id_nota BIGINT NOT NULL PRIMARY KEY, " +
                    "data_referencia DATE NOT NULL, " +
                    "bloco_horario_6h INT NOT NULL, " +
                    "criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")"
            ).executeUpdate();

            em.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS fator_externo_estatistica (" +
                    "id_produto BIGINT NOT NULL, " +
                    "tipo_fator VARCHAR(24) NOT NULL, " +
                    "n_com INT NOT NULL, " +
                    "n_sem INT NOT NULL, " +
                    "media_qtd_com DECIMAL(20,6), " +
                    "media_qtd_sem DECIMAL(20,6), " +
                    "desvio_com DECIMAL(20,6), " +
                    "desvio_sem DECIMAL(20,6), " +
                    "lift_pct DECIMAL(12,2), " +
                    "z_welch DECIMAL(12,4), " +
                    "significancia VARCHAR(14) NOT NULL, " +
                    "atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (id_produto, tipo_fator)" +
                    ")"
            ).executeUpdate();

            // Executa o script de inicialização do sistema (Unidades de Medida, Formas de Pagamento e Cliente Padrão)
            var inicializador = new br.com.senac.service.InicializadorSistemaService();
            inicializador.executarScriptInicializacao(em);

            if (isMySql) {
                em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                    try (var stmt = conn.createStatement()) {
                        stmt.execute("DROP PROCEDURE IF EXISTS sp_consumir_estoque_fifo");
                        stmt.execute(
                                "CREATE PROCEDURE sp_consumir_estoque_fifo(" +
                                "IN p_id_nota_venda BIGINT, IN p_id_produto BIGINT, IN p_quantidade_total DECIMAL(20,6), IN p_preco_venda DECIMAL(20,4)) " +
                                "BEGIN " +
                                "DECLARE v_restante DECIMAL(20,6) DEFAULT p_quantidade_total; " +
                                "DECLARE v_saldo_total DECIMAL(20,6) DEFAULT 0; " +
                                "DECLARE v_id_lote BIGINT; " +
                                "DECLARE v_qtd_disp DECIMAL(20,6); " +
                                "DECLARE v_custo_lote DECIMAL(20,4); " +
                                "DECLARE v_validade DATE; " +
                                "DECLARE v_consumir DECIMAL(20,6); " +
                                "DECLARE v_fim_cursor INT DEFAULT 0; " +
                                "DECLARE cur_fifo CURSOR FOR SELECT id, quantidade_disponivel, valor, data_validade FROM item " +
                                "WHERE id_produto = p_id_produto AND quantidade_disponivel > 0 AND (data_validade IS NULL OR data_validade >= CURDATE()) " +
                                "ORDER BY data ASC, COALESCE(data_validade, '9999-12-31') ASC, id ASC FOR UPDATE; " +
                                "DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fim_cursor = 1; " +
                                "SELECT COALESCE(SUM(quantidade_disponivel), 0) INTO v_saldo_total FROM item " +
                                "WHERE id_produto = p_id_produto AND quantidade_disponivel > 0 AND (data_validade IS NULL OR data_validade >= CURDATE()); " +
                                "IF v_saldo_total < p_quantidade_total THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Estoque insuficiente (FIFO).'; END IF; " +
                                "OPEN cur_fifo; " +
                                "WHILE v_restante > 0 AND v_fim_cursor = 0 DO " +
                                "FETCH cur_fifo INTO v_id_lote, v_qtd_disp, v_custo_lote, v_validade; " +
                                "IF v_fim_cursor = 0 THEN " +
                                "SET v_consumir = LEAST(v_qtd_disp, v_restante); " +
                                "UPDATE item SET quantidade_disponivel = quantidade_disponivel - v_consumir WHERE id = v_id_lote; " +
                                "INSERT INTO item (id_nota, id_produto, id_item_origem, valor, desconto, quantidade, quantidade_disponivel, data, data_validade) " +
                                "VALUES (p_id_nota_venda, p_id_produto, v_id_lote, COALESCE(NULLIF(p_preco_venda, 0), v_custo_lote), 0, v_consumir, 0, NOW(6), v_validade); " +
                                "SET v_restante = v_restante - v_consumir; " +
                                "END IF; " +
                                "END WHILE; " +
                                "CLOSE cur_fifo; " +
                                "END"
                        );

                        stmt.execute("DROP PROCEDURE IF EXISTS sp_consumir_lote_especifico");
                        stmt.execute(
                                "CREATE PROCEDURE sp_consumir_lote_especifico(" +
                                "IN p_id_nota_venda BIGINT, IN p_id_lote_desejado BIGINT, IN p_quantidade DECIMAL(20,6), IN p_preco_venda DECIMAL(20,4)) " +
                                "BEGIN " +
                                "DECLARE v_id_produto BIGINT; " +
                                "DECLARE v_qtd_disp DECIMAL(20,6); " +
                                "DECLARE v_custo_lote DECIMAL(20,4); " +
                                "DECLARE v_validade DATE; " +
                                "SELECT id_produto, quantidade_disponivel, valor, data_validade INTO v_id_produto, v_qtd_disp, v_custo_lote, v_validade " +
                                "FROM item WHERE id = p_id_lote_desejado FOR UPDATE; " +
                                "IF v_id_produto IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lote desejado nao encontrado.'; END IF; " +
                                "IF v_validade IS NOT NULL AND v_validade < CURDATE() THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lote escolhido vencido.'; END IF; " +
                                "IF v_qtd_disp < p_quantidade OR p_quantidade <= 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Saldo insuficiente no lote escolhido.'; END IF; " +
                                "UPDATE item SET quantidade_disponivel = quantidade_disponivel - p_quantidade WHERE id = p_id_lote_desejado; " +
                                "INSERT INTO item (id_nota, id_produto, id_item_origem, valor, desconto, quantidade, quantidade_disponivel, data, data_validade) " +
                                "VALUES (p_id_nota_venda, v_id_produto, p_id_lote_desejado, COALESCE(NULLIF(p_preco_venda, 0), v_custo_lote), 0, p_quantidade, 0, NOW(6), v_validade); " +
                                "END"
                        );
                    }
                });
                inicializador.inicializarViewsEProceduresEstatisticasMySql(em);
                em.unwrap(org.hibernate.Session.class).doWork(conn -> {
                    try {
                        SqlScriptRunner.executarRecurso(conn, "/sql/script_fatores_externos_gatilhos.sql");
                    } catch (Exception e) {
                        System.err.println("[JPAUtil] Aviso ao instalar gatilhos de fatores externos: " + e.getMessage());
                    }
                });
            }

            tx.commit();
        } catch (Exception e) {
            System.err.println("[JPAUtil] Aviso ao inicializar Views/Procedures: " + e.getMessage());
        }
    }

    /**
     * Caso alguém clone o projeto do Git (onde o persistence.xml com senha foi ignorado),
     * copia automaticamente o persistence-embarcado.xml para persistence.xml.
     */
    private static void garantirArquivoPersistence() {
        Path persistenceXml = Paths.get("src/main/resources/META-INF/persistence.xml");
        Path persistenceEmbarcado = Paths.get("src/main/resources/META-INF/persistence-embarcado.xml");
        Path targetPersistenceXml = Paths.get("target/classes/META-INF/persistence.xml");

        try {
            if (!Files.exists(persistenceXml) && Files.exists(persistenceEmbarcado)) {
                Files.copy(persistenceEmbarcado, persistenceXml, StandardCopyOption.REPLACE_EXISTING);
                if (Files.exists(targetPersistenceXml.getParent())) {
                    Files.copy(persistenceEmbarcado, targetPersistenceXml, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static EntityManager getEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static synchronized void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
