-- ==========================================================================================
-- SCRIPT DE INICIALIZAÇÃO DO SISTEMA (BANCO PADRÃO: sazonalisei - MySQL)
-- 1. Unidades de Medida Padrão (tabela: unidade_medida)
-- 2. Formas de Pagamento Customizáveis (tabela: forma_pagamento)
-- 3. Cliente Padrão para Vendas sem Cliente Registrado (tabela: pessoa / registro)
-- ==========================================================================================

USE sazonalisei;

-- 1. Carga Inicial das Unidades de Medida (Um)
INSERT IGNORE INTO unidade_medida (sigla, nome, descricao, base) VALUES
('UN',  'Unidade',    'Unidade padrão inteira indivisível',                1.000000),
('KG',  'Quilograma', 'Peso em quilogramas (fracionável a cada 1 grama)',  0.001000),
('G',   'Grama',      'Peso em gramas inteiras',                           1.000000),
('LT',  'Litro',      'Volume em litros (fracionável a cada 1 mililitro)', 0.001000),
('ML',  'Mililitro',  'Volume em mililitros inteiros',                     1.000000),
('MT',  'Metro',      'Comprimento em metros (fracionável a cada 1 cm)',   0.010000),
('CX',  'Caixa',      'Caixa fechada inteira',                             1.000000),
('FD',  'Fardo',      'Fardo fechado inteiro',                             1.000000),
('PCT', 'Pacote',     'Pacote fechado inteiro',                            1.000000),
('PAR', 'Par',        'Par indivisível (2 peças)',                         1.000000);

-- 2. Carga Inicial das Formas de Pagamento Customizáveis (forma_pagamento)
INSERT IGNORE INTO forma_pagamento (
    codigo, nome, descricao, ativo,
    permite_a_vista, permite_a_prazo, permite_parcelado,
    maximo_parcelas, maximo_parcelas_sem_juros, dias_intervalo_parcelas, dias_compensacao,
    taxa_operadora_percentual, tarifa_fixa_operadora, desconto_a_vista_percentual,
    taxa_juros_mensal_parcelamento, taxa_juros_diario_atraso, percentual_multa_atraso,
    valor_minimo_parcela, exige_limite_cliente
) VALUES
('DINHEIRO',         'Dinheiro em Espécie',            'Pagamento em espécie no caixa com troco',                1, 1, 0, 0,  1, 1, 30,  0, 0.0000, 0.0000, 5.0000, 0.0000, 0.0000, 0.0000,  0.0000, 0),
('PIX',              'PIX Instantâneo (BACEN)',        'Transferência instantânea via QR Code / Chave PIX',      1, 1, 0, 0,  1, 1, 30,  0, 0.0000, 0.0000, 5.0000, 0.0000, 0.0000, 0.0000,  0.0000, 0),
('CARTAO_DEBITO',    'Cartão de Débito',               'Débito em conta via maquininha TEF/POS',                 1, 1, 0, 0,  1, 1, 30,  1, 1.3900, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000,  5.0000, 0),
('CARTAO_CREDITO',   'Cartão de Crédito',              'Crédito à vista (1x) ou parcelado até 12x (3x s/juros)', 1, 1, 0, 1, 12, 3, 30, 30, 2.9900, 0.0000, 0.0000, 2.4900, 0.0333, 2.0000, 20.0000, 0),
('BOLETO',           'Boleto Bancário Registrado',     'Boleto bancário a prazo ou parcelado com tarifa fixa',   1, 1, 1, 1,  6, 1, 30,  2, 0.0000, 2.5000, 0.0000, 1.9900, 0.0333, 2.0000, 50.0000, 1),
('CREDIARIO',        'Crediário / Faturado no Limite', 'Venda faturada a prazo ou parcelada no limite da loja',  1, 0, 1, 1, 10, 2, 30, 30, 0.0000, 0.0000, 0.0000, 2.9900, 0.0333, 2.0000, 30.0000, 1),
('VALE_ALIMENTACAO', 'Vale Alimentação / Refeição',    'Voucher de benefício alimentação/refeição',              1, 1, 0, 0,  1, 1, 30, 30, 3.5000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000,  5.0000, 0),
('TRANSFERENCIA',    'Transferência Bancária TED/DOC', 'Transferência bancária corporativa direta',              1, 1, 1, 0,  1, 1, 30,  1, 0.0000, 0.0000, 3.0000, 0.0000, 0.0333, 2.0000,  0.0000, 0);

-- 3. Cliente Padrão para todas as vendas sem cliente identificado/registrado
INSERT IGNORE INTO pessoa (tipo_pessoa, nome, cpf, cnpj, limite, is_cliente, is_fornecedor)
VALUES ('PF', 'CONSUMIDOR FINAL (NÃO REGISTRADO)', '000.000.000-00', NULL, 0.00, 1, 0);
