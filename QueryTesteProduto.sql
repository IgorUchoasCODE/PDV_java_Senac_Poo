USE sazonalisei;

SHOW TABLES;

-- Verifica a estrutura criada nas tabelas
DESCRIBE produtos;
DESCRIBE conjunto_produto;
DESCRIBE link_produto_digital;
DESCRIBE unidade_medida;

-- Consulta todos os produtos (is_digital e is_conjunto)
SELECT id, nome, descricao, v_venda, is_digital, is_conjunto, um_id FROM produtos;

-- Consulta os links vinculados aos produtos digitais
SELECT
    p.id AS produto_id,
    p.nome AS produto_nome,
    l.id AS link_id,
    l.titulo,
    l.url
FROM produtos p
INNER JOIN link_produto_digital l ON l.produto_digital_id = p.id;

-- Consulta os conjuntos/receitas (PK composta: id_produto, id_item)
SELECT
    cp.id_produto,
    pai.nome AS nome_produto_conjunto,
    pai.is_conjunto,
    cp.id_item,
    filho.nome AS nome_item_receita,
    cp.quantidade
FROM conjunto_produto cp
INNER JOIN produtos pai ON pai.id = cp.id_produto
INNER JOIN produtos filho ON filho.id = cp.id_item;
