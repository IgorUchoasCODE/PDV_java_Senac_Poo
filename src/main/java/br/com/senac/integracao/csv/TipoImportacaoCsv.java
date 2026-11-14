package br.com.senac.integracao.csv;

import br.com.senac.controller.RotaSistema;

/**
 * Tipos pré-configurados de importação de dados reais via CSV,
 * sempre despachados pelas rotas oficiais do {@link br.com.senac.controller.RoteadorController}.
 */
public enum TipoImportacaoCsv {

    UNIDADES_MEDIDA(
            RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
            "Unidades de Medida (colunas: sigla, nome, descricao, base)"
    ),
    FORMAS_PAGAMENTO(
            RotaSistema.PAGAMENTO_FORMA_CADASTRAR,
            "Formas de Pagamento (colunas: codigo, nome, descricao, permiteAVista, permiteAPrazo, permiteParcelado, maxParcelas, maxParcelasSemJuros, taxaOperadoraPercentual, tarifaFixaOperadora, descontoAVistaPercentual, taxaJurosMensal)"
    ),
    PESSOAS(
            RotaSistema.PESSOA_SIMPLES_CADASTRAR,
            "Clientes e Fornecedores PF/PJ (colunas: nome/razaoSocial, papel [CLIENTE/FORNECEDOR/AMBOS], cpf/cnpj/documento, limite)"
    ),
    PRODUTOS_FISICOS(
            RotaSistema.PRODUTO_FISICO_CADASTRAR,
            "Produtos Físicos (colunas: nome, descricao, precoVenda, siglaUm, alturaCm, larguraCm, comprimentoCm, pesoBrutoKg, pesoLiquidoKg)"
    ),
    PRODUTOS_DIGITAIS(
            RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
            "Produtos Digitais (colunas: nome, descricao, precoVenda, tituloLink, urlLink)"
    ),
    NOTAS_COMPRA(
            RotaSistema.NOTA_COMPRA_EMITIR,
            "Notas de Compra / Entrada de Estoque FIFO (colunas: fornecedor/idFornecedor/nomeFornecedor, produto/idProduto/nomeProduto, quantidade, valorUnitario/custo, dataValidade, dataGerada, codigoForma, descricao)"
    ),
    NOTAS_VENDA(
            RotaSistema.NOTA_VENDA_EMITIR_FIFO,
            "Notas de Venda FIFO (colunas: cliente/idCliente/nomeCliente, produto/idProduto/nomeProduto, quantidade, dataGerada, codigoForma, parcelas, descontoCupom, cupom, descricao)"
    );

    private final RotaSistema rotaPadrao;
    private final String descricaoColunas;

    TipoImportacaoCsv(RotaSistema rotaPadrao, String descricaoColunas) {
        this.rotaPadrao = rotaPadrao;
        this.descricaoColunas = descricaoColunas;
    }

    public RotaSistema getRotaPadrao() {
        return rotaPadrao;
    }

    public String getDescricaoColunas() {
        return descricaoColunas;
    }
}
