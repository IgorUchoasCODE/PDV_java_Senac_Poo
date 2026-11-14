package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Teste contábil e de estoque para produto composto em 3 níveis:
 * Caixa de Ovos (Nível 1) -> 12 Cartelas (Nível 2) -> 30 Ovos (Nível 3).
 * Valida custo obrigatório na entrada, preço de venda configurável, rateio BACEN
 * e recálculo automático de CMV ao alterar componentes do produto composto.
 */
public class TesteProduto3NiveisContabil {

    private static int totalTestes = 0;
    private static int aprovados = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   TESTE CONTABIL DE PRODUTO COMPOSTO 3 NIVEIS (CAIXA -> 12 CARTELAS -> 30 OVOS)");
        System.out.println("================================================================================");

        RoteadorController roteador = null;
        String sufixo = String.valueOf(System.currentTimeMillis() % 1_000_000);

        try {
            roteador = new RoteadorController();
            roteador.executarEndpoint(RotaSistema.SISTEMA_INICIALIZAR_DADOS_PADRAO, Map.of());

            // 1. Cadastra Fornecedor e Cliente
            Pessoa fornecedor = (Pessoa) roteador.executarEndpoint(RotaSistema.PESSOA_SIMPLES_CADASTRAR, Map.of(
                    "nome", "Granja 3N " + sufixo,
                    "papel", "FORNECEDOR"
            )).dados();
            Pessoa cliente = (Pessoa) roteador.executarEndpoint(RotaSistema.PESSOA_SIMPLES_CADASTRAR, Map.of(
                    "nome", "Mercado 3N " + sufixo,
                    "papel", "CLIENTE"
            )).dados();

            // 2. Cria os 3 níveis de produto (Folha: Ovo -> Intermediário: Cartela -> Raiz: Caixa)
            Produto ovo = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Ovo 3N " + sufixo,
                    "precoVenda", "0.80",
                    "siglaUm", "UN"
            )).dados();
            Produto cartela = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Cartela 30 Ovos 3N " + sufixo,
                    "precoVenda", "21.00",
                    "siglaUm", "UN"
            )).dados();
            Produto caixa = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Caixa 12 Cartelas 3N " + sufixo,
                    "precoVenda", "240.00",
                    "siglaUm", "CX"
            )).dados();

            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cartela.getId(),
                    "idProdutoComponente", ovo.getId(),
                    "quantidade", "30"
            ));
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", caixa.getId(),
                    "idProdutoComponente", cartela.getId(),
                    "quantidade", "12"
            ));

            afirmar(ovo.getId() != null && cartela.getId() != null && caixa.getId() != null,
                    "Os 3 níveis de produto foram cadastrados e vinculados (Caixa -> 12 Cartelas -> 30 Ovos).");

            // 3. Entrada de custo obrigatório no Nível 3 (720 ovos a R$ 0.45/UN = R$ 324.00)
            NotaCompra compra = (NotaCompra) roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "idFornecedor", fornecedor.getId(),
                    "descricao", "Compra lote inicial 720 ovos",
                    "itens", List.of(Map.of(
                            "idProduto", ovo.getId(),
                            "quantidade", "720",
                            "custo", "0.45",
                            "dataValidade", LocalDate.now().plusDays(20).toString()
                    ))
            )).dados();
            afirmar(compra != null && new BigDecimal("324.00").compareTo(compra.getValorTotal()) == 0,
                    "NotaCompra registrou custo obrigatório de R$ 324.00 (720 x R$ 0.45).");

            // 4. Venda de 1 Caixa (Nível 1) -> deve consumir 360 Ovos (12 x 30), CMV = 162.00, Venda = 240.00, Lucro = 78.00
            NotaVenda venda1 = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "idCliente", cliente.getId(),
                    "descricao", "Venda 1 Caixa 3 Níveis",
                    "itens", List.of(Map.of("idProduto", caixa.getId(), "quantidade", "1")),
                    "pagamentos", List.of(Map.of("codigoForma", "DINHEIRO", "valor", "240.00"))
            )).dados();

            afirmar(venda1 != null, "NotaVenda da Caixa de 3 níveis emitida com sucesso.");
            afirmar(new BigDecimal("162.00").compareTo(venda1.getCustoTotalMercadoriaVendida()) == 0,
                    "CMV calculado corretamente no nível raiz: 360 x R$ 0.45 = R$ 162.00 (obtido: " + venda1.getCustoTotalMercadoriaVendida() + ").");
            afirmar(new BigDecimal("240.00").compareTo(venda1.getValorTotal()) == 0,
                    "Valor de venda da Caixa preservado via rateio BACEN: R$ 240.00 (obtido: " + venda1.getValorTotal() + ").");
            afirmar(new BigDecimal("78.00").compareTo(venda1.getLucroLiquidoVenda()) == 0,
                    "Lucro líquido contábil exato (ValorTotal - CMV - Taxas): R$ 78.00 (obtido: " + venda1.getLucroLiquidoVenda() + ").");

            // Verifica saldo restante do Ovo (720 - 360 = 360)
            BigDecimal saldoRestante = (BigDecimal) roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", ovo.getId())).dados();
            afirmar(new BigDecimal("360").compareTo(saldoRestante) == 0,
                    "Saldo FIFO restante do Ovo após vender 1 Caixa: 360 UN (obtido: " + saldoRestante + ").");

            // 5. Reajuste de preço de venda (regra modificável) + troca de item composto para Ovo Especial (custo R$ 0.60)
            Produto ovoEspecial = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Ovo Especial 3N " + sufixo,
                    "precoVenda", "1.00",
                    "siglaUm", "UN"
            )).dados();
            roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "idFornecedor", fornecedor.getId(),
                    "descricao", "Compra lote Ovo Especial",
                    "itens", List.of(Map.of(
                            "idProduto", ovoEspecial.getId(),
                            "quantidade", "360",
                            "custo", "0.60",
                            "dataValidade", LocalDate.now().plusDays(25).toString()
                    ))
            ));

            // Substitui o componente da Cartela para usar o Ovo Especial e reajusta preço de venda da Caixa para R$ 270.00
            Produto cartelaRecarregada = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_BUSCAR_UNICO, Map.of("id", cartela.getId())).dados();
            List<ConjuntoProduto> antigos = new java.util.ArrayList<>(cartelaRecarregada.getItensConjunto());
            for (ConjuntoProduto cp : antigos) {
                cartelaRecarregada.removerItemConjunto(cp);
            }
            new br.com.senac.repository.dao.produto.ProdutoDAO().atualizar(cartelaRecarregada);
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cartela.getId(),
                    "idProdutoComponente", ovoEspecial.getId(),
                    "quantidade", "30"
            ));
            roteador.executarEndpoint(RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO, Map.of(
                    "idProduto", caixa.getId(),
                    "novoPreco", "270.00"
            ));

            NotaVenda venda2 = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "idCliente", cliente.getId(),
                    "descricao", "Venda 1 Caixa após troca de componente e preço",
                    "itens", List.of(Map.of("idProduto", caixa.getId(), "quantidade", "1")),
                    "pagamentos", List.of(Map.of("codigoForma", "DINHEIRO", "valor", "270.00"))
            )).dados();

            afirmar(new BigDecimal("216.00").compareTo(venda2.getCustoTotalMercadoriaVendida()) == 0,
                    "Após trocar o componente da Cartela, o CMV da nova venda reajustou automaticamente para R$ 216.00 (360 x R$ 0.60) (obtido: " + venda2.getCustoTotalMercadoriaVendida() + ").");
            afirmar(new BigDecimal("270.00").compareTo(venda2.getValorTotal()) == 0,
                    "Novo preço de venda da Caixa aplicado: R$ 270.00.");
            afirmar(new BigDecimal("54.00").compareTo(venda2.getLucroLiquidoVenda()) == 0,
                    "Lucro líquido da nova venda recalculado (ValorTotal - CMV - Taxas): R$ 54.00 (obtido: " + venda2.getLucroLiquidoVenda() + ").");

            // Confirma que a venda passada manteve o snapshot histórico intacto
            NotaVenda venda1Historica = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_BUSCAR_UNICA, Map.of("idNota", venda1.getId())).dados();
            afirmar(new BigDecimal("162.00").compareTo(venda1Historica.getCustoTotalMercadoriaVendida()) == 0,
                    "Snapshot histórico da venda anterior preservado (CMV = R$ 162.00).");

            // 6. Cesta Básica (itens variados em 3 níveis) + Preservação de custo quando há estoque próprio
            Produto arroz = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Arroz 5kg Cesta " + sufixo,
                    "precoVenda", "30.00",
                    "siglaUm", "UN"
            )).dados();
            Produto feijao = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Feijao 1kg Cesta " + sufixo,
                    "precoVenda", "12.00",
                    "siglaUm", "UN"
            )).dados();

            roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "idFornecedor", fornecedor.getId(),
                    "descricao", "Compra itens variados da Cesta Básica",
                    "itens", List.of(
                            Map.of("idProduto", arroz.getId(), "quantidade", "10", "custo", "22.00",
                                    "dataValidade", LocalDate.now().plusDays(90).toString()),
                            Map.of("idProduto", feijao.getId(), "quantidade", "10", "custo", "8.50",
                                    "dataValidade", LocalDate.now().plusDays(90).toString()),
                            Map.of("idProduto", ovoEspecial.getId(), "quantidade", "60", "custo", "0.60",
                                    "dataValidade", LocalDate.now().plusDays(25).toString())
                    )
            ));

            Produto cesta = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Cesta Basica 3N " + sufixo,
                    "precoVenda", "120.00",
                    "siglaUm", "UN"
            )).dados();
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cesta.getId(), "idProdutoComponente", arroz.getId(), "quantidade", "2"
            ));
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cesta.getId(), "idProdutoComponente", feijao.getId(), "quantidade", "3"
            ));
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cesta.getId(), "idProdutoComponente", cartela.getId(), "quantidade", "1"
            ));

            Produto cestaRecarregada = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_BUSCAR_UNICO, Map.of("id", cesta.getId())).dados();
            br.com.senac.service.EstoqueManage estoqueManage = new br.com.senac.service.EstoqueManage();
            BigDecimal custoCalculadoSemEstoque = estoqueManage.calcularCustoCompostoFIFO(cestaRecarregada, BigDecimal.ONE);
            // 2x 22.00 (44.00) + 3x 8.50 (25.50) + 30x 0.60 (18.00) = 87.50
            afirmar(new BigDecimal("87.50").compareTo(custoCalculadoSemEstoque) == 0,
                    "Custo da Cesta Básica sem estoque próprio soma todos os itens variados abaixo: R$ 87.50 (obtido: " + custoCalculadoSemEstoque + ").");

            // Entrada de 1 Cesta pronta em estoque com custo próprio de R$ 70.00 (não pode ser alterado pelos componentes)
            roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "idFornecedor", fornecedor.getId(),
                    "descricao", "Compra 1 Cesta pronta em estoque",
                    "itens", List.of(Map.of(
                            "idProduto", cesta.getId(),
                            "quantidade", "1",
                            "custo", "70.00",
                            "dataValidade", LocalDate.now().plusDays(60).toString()
                    ))
            ));

            BigDecimal custo1CestaComEstoque = estoqueManage.calcularCustoCompostoFIFO(cestaRecarregada, BigDecimal.ONE);
            afirmar(new BigDecimal("70.00").compareTo(custo1CestaComEstoque) == 0,
                    "Com estoque próprio da Cesta, seu custo não é alterado (preservado R$ 70.00; obtido: " + custo1CestaComEstoque + ").");

            BigDecimal custo2CestasMisto = estoqueManage.calcularCustoCompostoFIFO(cestaRecarregada, new BigDecimal("2"));
            afirmar(new BigDecimal("157.50").compareTo(custo2CestasMisto) == 0,
                    "Para 2 Cestas (1 do estoque próprio + 1 dos níveis abaixo), custo total = R$ 157.50 (obtido: " + custo2CestasMisto + ").");

            NotaVenda vendaCestaMista = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "idCliente", cliente.getId(),
                    "descricao", "Venda 2 Cestas Básicas (1 estoque próprio + 1 desmembrada)",
                    "itens", List.of(Map.of("idProduto", cesta.getId(), "quantidade", "2")),
                    "pagamentos", List.of(Map.of("codigoForma", "DINHEIRO", "valor", "240.00"))
            )).dados();
            afirmar(new BigDecimal("157.50").compareTo(vendaCestaMista.getCustoTotalMercadoriaVendida()) == 0,
                    "NotaVenda mista preservou R$ 70.00 da Cesta em estoque + somou R$ 87.50 dos níveis abaixo = R$ 157.50 (obtido: " + vendaCestaMista.getCustoTotalMercadoriaVendida() + ").");
            afirmar(new BigDecimal("240.00").compareTo(vendaCestaMista.getValorTotal()) == 0,
                    "Valor total da venda das 2 Cestas preservado: R$ 240.00 (obtido: " + vendaCestaMista.getValorTotal() + ").");
            afirmar(new BigDecimal("82.50").compareTo(vendaCestaMista.getLucroLiquidoVenda()) == 0,
                    "Lucro líquido contábil das 2 Cestas exato: R$ 82.50 (obtido: " + vendaCestaMista.getLucroLiquidoVenda() + ").");

            // 7. Opção 2 (Fracionamento de embalagem superior): 5 Caixas (R$ 180.00 cada) -> 60 Cartelas -> 1800 Ovos (R$ 0.50 cada)
            Produto ovoFrac = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Ovo Fracionado 3N " + sufixo,
                    "precoVenda", "0.80",
                    "siglaUm", "UN"
            )).dados();
            Produto cartelaFrac = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Cartela Fracionada 3N " + sufixo,
                    "precoVenda", "22.00",
                    "siglaUm", "UN"
            )).dados();
            Produto caixaFrac = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_FISICO_CADASTRAR, Map.of(
                    "nome", "Caixa Fracionada 3N " + sufixo,
                    "precoVenda", "240.00",
                    "siglaUm", "UN"
            )).dados();
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", cartelaFrac.getId(), "idProdutoComponente", ovoFrac.getId(), "quantidade", "30"
            ));
            roteador.executarEndpoint(RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE, Map.of(
                    "idProdutoKit", caixaFrac.getId(), "idProdutoComponente", cartelaFrac.getId(), "quantidade", "12"
            ));

            // Compra 5 Caixas diretamente a R$ 180.00 cada (0 lotes diretos em Cartela e Ovo)
            roteador.executarEndpoint(RotaSistema.NOTA_COMPRA_EMITIR, Map.of(
                    "idFornecedor", fornecedor.getId(),
                    "descricao", "Compra 5 Caixas para fracionamento",
                    "itens", List.of(Map.of(
                            "idProduto", caixaFrac.getId(),
                            "quantidade", "5",
                            "custo", "180.00",
                            "dataValidade", LocalDate.now().plusDays(20).toString()
                    ))
            ));

            BigDecimal saldoEquivCartela = (BigDecimal) roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", cartelaFrac.getId())).dados();
            BigDecimal saldoEquivOvo = (BigDecimal) roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", ovoFrac.getId())).dados();
            afirmar(new BigDecimal("60").compareTo(saldoEquivCartela) == 0,
                    "5 Caixas equivalem a 60 Cartelas no saldo consolidado (obtido: " + saldoEquivCartela + ").");
            afirmar(new BigDecimal("1800").compareTo(saldoEquivOvo) == 0,
                    "5 Caixas x 12 Cartelas x 30 Ovos = 1800 Ovos no saldo consolidado (obtido: " + saldoEquivOvo + ").");

            Produto ovoFracRecarregado = (Produto) roteador.executarEndpoint(RotaSistema.PRODUTO_BUSCAR_UNICO, Map.of("id", ovoFrac.getId())).dados();
            BigDecimal custoUnitarioOvo = estoqueManage.calcularCustoCompostoFIFO(ovoFracRecarregado, BigDecimal.ONE);
            afirmar(new BigDecimal("0.50").compareTo(custoUnitarioOvo) == 0,
                    "Custo calculado por Ovo a partir da Caixa (180.00 / 360) = R$ 0.50 (obtido: " + custoUnitarioOvo + ").");

            // Venda de 10 Ovos avulsos -> abre automaticamente 1 Caixa -> 12 Cartelas -> 30 Ovos
            NotaVenda vendaOvosAvulsos = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "idCliente", cliente.getId(),
                    "descricao", "Venda 10 Ovos avulsos abrindo Caixa/Cartela",
                    "itens", List.of(Map.of("idProduto", ovoFrac.getId(), "quantidade", "10")),
                    "pagamentos", List.of(Map.of("codigoForma", "DINHEIRO", "valor", "8.00"))
            )).dados();
            afirmar(new BigDecimal("5.00").compareTo(vendaOvosAvulsos.getCustoTotalMercadoriaVendida()) == 0,
                    "Venda de 10 Ovos avulsos via fracionamento apurou CMV = R$ 5.00 (10 x R$ 0.50) (obtido: " + vendaOvosAvulsos.getCustoTotalMercadoriaVendida() + ").");

            BigDecimal saldoEquivOvoPosVenda = (BigDecimal) roteador.executarEndpoint(
                    RotaSistema.ESTOQUE_SALDO_CONSOLIDADO, Map.of("idProduto", ovoFrac.getId())).dados();
            afirmar(new BigDecimal("1790").compareTo(saldoEquivOvoPosVenda) == 0,
                    "Saldo consolidado de Ovos após vender 10 UN: 1790 Ovos (4 Caixas + 11 Cartelas + 20 Ovos) (obtido: " + saldoEquivOvoPosVenda + ").");

            // Venda de 1 Cartela (consome 1 das 11 Cartelas abertas mantendo 4 Caixas fechadas)
            NotaVenda vendaCartelaAberta = (NotaVenda) roteador.executarEndpoint(RotaSistema.NOTA_VENDA_EMITIR_FIFO, Map.of(
                    "idCliente", cliente.getId(),
                    "descricao", "Venda 1 Cartela das abertas",
                    "itens", List.of(Map.of("idProduto", cartelaFrac.getId(), "quantidade", "1")),
                    "pagamentos", List.of(Map.of("codigoForma", "DINHEIRO", "valor", "22.00"))
            )).dados();
            afirmar(new BigDecimal("15.00").compareTo(vendaCartelaAberta.getCustoTotalMercadoriaVendida()) == 0,
                    "Venda de 1 Cartela fracionada apurou CMV = R$ 15.00 (obtido: " + vendaCartelaAberta.getCustoTotalMercadoriaVendida() + ").");

            System.out.println("================================================================================");
            System.out.printf(" RESULTADO 3 NIVEIS CONTABIL: %d / %d TESTES APROVADOS!%n", aprovados, totalTestes);
            System.out.println("================================================================================");

            if (aprovados != totalTestes) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ ERRO NO TESTE 3 NIVEIS CONTABIL: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (roteador != null) {
                try { JPAUtil.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void afirmar(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            aprovados++;
            System.out.println(" [OK] " + mensagem);
        } else {
            System.err.println(" [FALHA] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }
}
