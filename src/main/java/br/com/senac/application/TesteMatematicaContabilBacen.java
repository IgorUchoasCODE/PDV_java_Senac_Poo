package br.com.senac.application;

import br.com.senac.controller.RoteadorController;
import br.com.senac.interfaces.MoedaContabilBacen;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoDigital;
import br.com.senac.repository.modal.produto.ProdutoFisico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Suíte de Testes Matemáticos e Contábeis (Níveis: FÁCIL, MÉDIO e ALTÍSSIMA COMPLEXIDADE)
 * para validação rigorosa do arredondamento BACEN (ABNT NBR 5891 - RoundingMode.HALF_EVEN),
 * algoritmo FIFO multi-lote, escolha de lote desejável, produtos compostos (Kits),
 * quantidades fracionadas, promoções/cupons, taxas de operadora, juros/mora e abatimento de perdas pós-venda.
 */
public class TesteMatematicaContabilBacen {

    private static int totalAssercoes = 0;
    private static int assercoesAprovadas = 0;
    private static final List<String> logValidacoes = new ArrayList<>();

    public static void main(String[] args) {
        try {
            RoteadorController roteador = new RoteadorController();
            roteador.inicializarSistemaCompleto();

            String sufixoExecucao = String.valueOf(System.currentTimeMillis() % 100000);

            System.out.println("==========================================================================================");
            System.out.println("   AUDITORIA MATEMÁTICA E CONTÁBIL (PADRÃO BACEN - HALF_EVEN) - SISTEMA SAZONALISEI");
            System.out.println("==========================================================================================");

            executarTesteNivel1Facil(roteador, sufixoExecucao);
            executarTesteNivel2Medio(roteador, sufixoExecucao);
            executarTesteNivel3ComplexoMultissimo(roteador, sufixoExecucao);

            System.out.println("\n==========================================================================================");
            System.out.printf("   RESUMO FINAL DA AUDITORIA: %d / %d ASSERÇÕES MATEMÁTICAS APROVADAS (100%% EXATO)%n",
                    assercoesAprovadas, totalAssercoes);
            System.out.println("==========================================================================================");
        } finally {
            JPAUtil.close();
        }
    }

    // ==========================================================================================================
    // NÍVEL 1: FÁCIL (Fracionamento KG, Arredondamento BACEN HALF_EVEN, Desconto Item + Desconto À Vista + Troco)
    // ==========================================================================================================
    private static void executarTesteNivel1Facil(RoteadorController roteador, String sufixo) {
        System.out.println("\n------------------------------------------------------------------------------------------");
        System.out.println(" [NÍVEL 1 - FÁCIL] Produto Fracionado (KG), Cubagem, Desconto Item, PIX/Dinheiro e Troco");
        System.out.println("------------------------------------------------------------------------------------------");

        PessoaJuridica fornecedorCafe = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                "Torrefação Alta Mogiana LTDA " + sufixo, "21.111." + sufixo.substring(0, 3) + "/0001-10");

        ProdutoFisico cafeKg = roteador.produtos().cadastrarProdutoFisico(
                "Café Especial Torrado em Grãos (KG) #" + sufixo,
                "Café 100% Arábica fracionável a cada 1 grama",
                new BigDecimal("89.9000"),
                "KG",
                new BigDecimal("25.0000"), // Altura cm
                new BigDecimal("15.0000"), // Largura cm
                new BigDecimal("10.0000"), // Comprimento cm
                new BigDecimal("1.0500"),  // Peso Bruto kg
                new BigDecimal("1.0000")   // Peso Líquido kg
        );

        // 1. Validação Matemática de Dimensões e Cubagem
        // Volume = 25 * 15 * 10 = 3750.0000 cm³ = 0.003750 m³
        // Peso Cubado Rodoviário (300 kg/m³) = 0.003750 * 300 = 1.1250 kg
        // Peso Taxável = max(1.0500, 1.1250) = 1.1250 kg
        validarIgual("N1.1 Volume Físico Café (cm³)", new BigDecimal("3750.0000"), cafeKg.calcularVolumeCm3());
        validarIgual("N1.2 Volume Físico Café (m³)", new BigDecimal("0.003750"), cafeKg.calcularVolumeMetrosCubicos());
        validarIgual("N1.3 Peso Cubado ANTT 300kg/m³", new BigDecimal("1.1250"), cafeKg.calcularPesoCubadoKg(new BigDecimal("300")));
        validarIgual("N1.4 Peso Taxável de Frete (kg)", new BigDecimal("1.1250"), cafeKg.calcularPesoTaxavelFreteKg());

        // 2. Entrada de Lote de Compra: 10.000000 kg a R$ 52,3333/kg -> Subtotal = 523,333 -> R$ 523,33 (HALF_EVEN)
        NotaCompra compraCafe = new NotaCompra(fornecedorCafe, "Compra Lote Único Café Especial");
        Item<Produto> loteCafe = new Item<>(cafeKg, new BigDecimal("52.3333"), new BigDecimal("10.000000"), LocalDate.now().plusMonths(6));
        compraCafe.addItem(loteCafe);
        FormaPagamento fpPix = roteador.pagamentos().buscarFormaPorCodigo("PIX");
        roteador.notas().emitirNotaCompra(compraCafe, Collections.singletonList(
                new PagamentoAVista(fpPix, new BigDecimal("523.33"), new BigDecimal("523.33"), BigDecimal.ZERO)));

        validarIgual("N1.5 Valor Total NotaCompra Café (BACEN)", new BigDecimal("523.33"), compraCafe.getValorTotal());

        // 3. Venda de Balcão sem cliente registrado (1,375000 kg = 1kg e 375g)
        // Subtotal Bruto = 1.375000 * 89.90 = 123.6125 -> HALF_EVEN = R$ 123.61
        // CMV = 1.375000 * 52.3333 = 71.9582875 -> HALF_EVEN = R$ 71.96
        // Promoção direta no item = R$ 3.61 -> Subtotal Líquido = R$ 120.00
        NotaVenda vendaFacil = new NotaVenda(null, "Venda Nível 1 - Café Fracionado 1,375 kg");
        roteador.getEstoqueManage().consumirEstoqueFIFO(vendaFacil, cafeKg, new BigDecimal("1.375000"));
        vendaFacil.aplicarDescontoProduto(cafeKg, new BigDecimal("3.6100"));

        FormaPagamento fpDinheiro = roteador.pagamentos().buscarFormaPorCodigo("DINHEIRO");
        // Pagamento em Dinheiro: valor nominal R$ 120,00 com 5% de desconto à vista da forma (R$ 6,00 -> líquido R$ 114,00), recebido R$ 150,00 -> Troco R$ 36,00
        PagamentoAVista pagDinheiro = new PagamentoAVista(fpDinheiro, new BigDecimal("120.00"), new BigDecimal("150.00"), null);
        roteador.getManageNotas().emitirNotaVenda(vendaFacil, Collections.singletonList(pagDinheiro));

        Item<? extends Produto> itemVendido = vendaFacil.getItens().get(0);
        validarIgual("N1.6 Subtotal Bruto Item (1,375 kg * 89,90)", new BigDecimal("123.61"), itemVendido.getSubtotalBruto());
        validarIgual("N1.7 Subtotal Líquido c/ Promoção (-R$ 3,61)", new BigDecimal("120.00"), vendaFacil.getValorTotal());
        validarIgual("N1.8 CMV do Item (1,375 kg * 52,3333)", new BigDecimal("71.96"), vendaFacil.getCustoTotalMercadoriaVendida());
        validarIgual("N1.9 Lucro Bruto (123,61 - 71,96)", new BigDecimal("51.65"), vendaFacil.getLucroBrutoVenda());
        validarIgual("N1.10 Lucro Líquido (120,00 - 71,96 - 0 taxa)", new BigDecimal("48.04"), vendaFacil.getLucroLiquidoVenda());
        validarIgual("N1.11 Margem de Lucro % (48,04 / 71,96)", new BigDecimal("66.76"), vendaFacil.getMargemLucroPercentual());
        validarIgual("N1.12 Desconto À Vista 5% Dinheiro", new BigDecimal("6.0000"), pagDinheiro.getDescontoAVista());
        validarIgual("N1.13 Valor Final Desembolsado Caixa", new BigDecimal("114.00"), pagDinheiro.calcularValorFinal());
        validarIgual("N1.14 Troco Calculado (150,00 - 114,00)", new BigDecimal("36.00"), pagDinheiro.calcularTroco());
        validarIgual("N1.15 Saldo Estoque Remanescente (10 - 1,375)", new BigDecimal("8.625000"), roteador.estoque().consultarSaldoTotalProduto(cafeKg.getId()));
        validarIgualTexto("N1.16 Cliente Padrão Vinculado Automaticamente", "000.000.000-00", ((PessoaFisica) vendaFacil.getPessoa()).getCpf());
    }

    // ==========================================================================================================
    // NÍVEL 2: MÉDIO (Kit Composto, FIFO quebrando múltiplos lotes com custos distintos, Promoção de Kit + Cupom
    //                 e Parcelamento em 3x com alocação de resíduo de centavo BACEN na 1ª parcela)
    // ==========================================================================================================
    private static void executarTesteNivel2Medio(RoteadorController roteador, String sufixo) {
        System.out.println("\n------------------------------------------------------------------------------------------");
        System.out.println(" [NÍVEL 2 - MÉDIO] Kit Composto + Quebra Multi-Lote FIFO + Cupom + Resíduo Parcela BACEN");
        System.out.println("------------------------------------------------------------------------------------------");

        PessoaJuridica fornHardware = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                "Importadora Silicon Manaus S.A. " + sufixo, "32.222." + sufixo.substring(0, 3) + "/0001-20");
        PessoaFisica clienteVip = roteador.pessoas().cadastrarClientePessoaFisica(
                "Mariana Costa #" + sufixo, "222.333." + sufixo.substring(0, 3) + "-55", new BigDecimal("10000.00"));

        // Componente 1: Cabo HDMI vendido por METRO (MT - base 0.01m = 1cm)
        ProdutoFisico caboHdmiMt = roteador.produtos().cadastrarProdutoFisico(
                "Cabo HDMI 2.1 Fibra 8K (MT) #" + sufixo,
                "Cabo vendido por metro com precisão de centímetros",
                new BigDecimal("45.5000"),
                "MT",
                new BigDecimal("2.0000"), new BigDecimal("2.0000"), new BigDecimal("100.0000"),
                new BigDecimal("0.1200"), new BigDecimal("0.1000")
        );

        // Componente 2: Hub USB-C vendido por UNIDADE (UN - base 1.0)
        ProdutoFisico hubUsbUn = roteador.produtos().cadastrarProdutoFisico(
                "Hub USB-C 7-em-1 Thunderbolt (UN) #" + sufixo,
                "Hub multiportas alumínio",
                new BigDecimal("249.9000"),
                "UN",
                new BigDecimal("1.5000"), new BigDecimal("4.5000"), new BigDecimal("11.0000"),
                new BigDecimal("0.2200"), new BigDecimal("0.1800")
        );

        // Produto Composto (Kit): 1 UN de Hub USB-C + 1,85 MT de Cabo HDMI
        // Preço avulso dos componentes p/ 1 Kit = 249,90 + (1,85 * 45,50 = 84,175 -> 84,18) = R$ 334,08
        // Preço Promocional do Kit = R$ 289,90
        ProdutoFisico kitHomeOffice = roteador.produtos().cadastrarProdutoFisico(
                "Kit Estação Home Office Pro #" + sufixo,
                "Combo contendo 1 Hub USB-C + 1,85m de Cabo HDMI 8K",
                new BigDecimal("289.9000"),
                "UN",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        roteador.produtos().adicionarComponenteAoKit(kitHomeOffice.getId(), hubUsbUn.getId(), new BigDecimal("1.000000"));
        roteador.produtos().adicionarComponenteAoKit(kitHomeOffice.getId(), caboHdmiMt.getId(), new BigDecimal("1.850000"));
        kitHomeOffice = (ProdutoFisico) roteador.produtos().buscarPorId(kitHomeOffice.getId());

        // Valida soma automática de volume e peso bruto do Kit a partir dos componentes:
        // Volume Hub (1 * 1.5 * 4.5 * 11 = 74.25 cm³) + Volume Cabo (1.85 * 2 * 2 * 100 = 740.00 cm³) = 814.2500 cm³
        // Peso Bruto Hub (1 * 0.22 = 0.22 kg) + Peso Bruto Cabo (1.85 * 0.12 = 0.2220 kg) = 0.4420 kg
        validarIgual("N2.1 Volume Composto Automático do Kit (cm³)", new BigDecimal("814.2500"), kitHomeOffice.calcularVolumeCm3());
        validarIgual("N2.2 Peso Bruto Composto Automático do Kit (kg)", new BigDecimal("0.4420"), kitHomeOffice.calcularPesoBrutoTotalKg());

        FormaPagamento fpPix = roteador.pagamentos().buscarFormaPorCodigo("PIX");

        // Compra Lote 1 (Mais antigo): 2 Hubs a R$ 135,00 e 5,00m de Cabo a R$ 21,15/m
        NotaCompra compra1 = new NotaCompra(fornHardware, "Compra Lote 1 Antigo - Hub e Cabo");
        compra1.addItem(new Item<>(hubUsbUn, new BigDecimal("135.0000"), new BigDecimal("2.000000"), LocalDate.now().plusMonths(8)));
        compra1.addItem(new Item<>(caboHdmiMt, new BigDecimal("21.1500"), new BigDecimal("5.000000"), LocalDate.now().plusMonths(8)));
        roteador.notas().emitirNotaCompra(compra1, Collections.singletonList(
                new PagamentoAVista(fpPix, new BigDecimal("375.75"), new BigDecimal("375.75"), BigDecimal.ZERO)));

        // Compra Lote 2 (Mais recente): 5 Hubs a R$ 148,50 e 10,00m de Cabo a R$ 24,80/m
        NotaCompra compra2 = new NotaCompra(fornHardware, "Compra Lote 2 Recente - Hub e Cabo");
        compra2.addItem(new Item<>(hubUsbUn, new BigDecimal("148.5000"), new BigDecimal("5.000000"), LocalDate.now().plusMonths(12)));
        compra2.addItem(new Item<>(caboHdmiMt, new BigDecimal("24.8000"), new BigDecimal("10.000000"), LocalDate.now().plusMonths(12)));
        roteador.notas().emitirNotaCompra(compra2, Collections.singletonList(
                new PagamentoAVista(fpPix, new BigDecimal("990.50"), new BigDecimal("990.50"), BigDecimal.ZERO)));

        // Venda de 3 Kits + Cupom de R$ 25,00 pagos em 3x sem juros no Cartão de Crédito (MDR 2,99%)
        // Necessidade FIFO p/ 3 Kits:
        // - Hub: 3 un -> 2 un do Lote 1 (R$ 135,00) + 1 un do Lote 2 (R$ 148,50) -> CMV Hub = 270,00 + 148,50 = R$ 418,50
        // - Cabo: 5,55 m -> 5,00 m do Lote 1 (R$ 21,15) + 0,55 m do Lote 2 (R$ 24,80) -> CMV Cabo = 105,75 + 13,64 = R$ 119,39
        // CMV Total Exato = 418,50 + 119,39 = R$ 537,89
        // Receita dos 3 Kits = 3 * 289,90 = R$ 869,70 | Com Cupom R$ 25,00 = R$ 844,70
        Map<Produto, BigDecimal> itensVendaMedio = new LinkedHashMap<>();
        itensVendaMedio.put(kitHomeOffice, new BigDecimal("3.000000"));

        PagamentoParcelado pagCartao3x = roteador.pagamentos().criarPagamentoParcelado(
                "CARTAO_CREDITO", new BigDecimal("844.70"), 3);

        NotaVenda vendaMedia = roteador.notas().emitirVendaFIFO(
                clienteVip,
                "Venda Nível 2 - 3 Kits Home Office c/ Quebra FIFO e Cupom",
                itensVendaMedio,
                Collections.singletonList(pagCartao3x),
                "PROMO_TECH_25",
                new BigDecimal("25.00")
        );

        validarIgualInteiro("N2.3 Quantidade de Sub-Itens FIFO Gerados (2 lotes Hub + 2 lotes Cabo)", 4, vendaMedia.getItens().size());
        validarIgual("N2.4 Soma Bruta Avulsa dos Componentes (499,80 + 249,90 + 227,50 + 25,02)", new BigDecimal("1002.22"), vendaMedia.getValorBruto());

        BigDecimal somaSubtotaisItensKit = vendaMedia.getItens().stream()
                .map(Item::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validarIgual("N2.5 Soma Subtotais Após Rateio Promocional do Kit (3 * 289,90)", new BigDecimal("869.70"), somaSubtotaisItensKit);
        validarIgual("N2.6 Valor Total Líquido da Nota c/ Cupom (-R$ 25,00)", new BigDecimal("844.70"), vendaMedia.getValorTotal());
        validarIgual("N2.7 CMV Total Multi-Lote FIFO (270 + 148,50 + 105,75 + 13,64)", new BigDecimal("537.89"), vendaMedia.getCustoTotalMercadoriaVendida());

        // Validação do Parcelamento BACEN (844,70 / 3 = 281,56 + 281,57 + 281,57 = 844,70)
        List<ParcelaPagamento> parcelas3x = pagCartao3x.getParcelas();
        validarIgual("N2.8 1ª Parcela c/ Ajuste de Resíduo BACEN (-0,01)", new BigDecimal("281.56"), parcelas3x.get(0).getValorTotalParcela());
        validarIgual("N2.9 2ª Parcela Base BACEN", new BigDecimal("281.57"), parcelas3x.get(1).getValorTotalParcela());
        validarIgual("N2.10 3ª Parcela Base BACEN", new BigDecimal("281.57"), parcelas3x.get(2).getValorTotalParcela());
        validarIgual("N2.11 Soma Exata das 3 Parcelas == Valor da Nota", new BigDecimal("844.70"), pagCartao3x.getSaldoDevedorParcelas());

        // Taxa MDR Cartão de Crédito (2,99% de 844,70 = 25,25653 -> R$ 25,26)
        // Lucro Líquido = 844,70 - 537,89 (CMV) - 25,26 (MDR) = R$ 281,55
        // Margem Líquida % = (281,55 * 100) / 537,89 = 52,343415... -> 52,34%
        validarIgual("N2.12 Custo MDR Operadora Cartão (2,99% sobre 844,70)", new BigDecimal("25.26"), vendaMedia.getCustoTaxasPagamento());
        validarIgual("N2.13 Lucro Líquido Contábil Final (844,70 - 537,89 - 25,26)", new BigDecimal("281.55"), vendaMedia.getLucroLiquidoVenda());
        validarIgual("N2.14 Margem de Lucro Líquida % BACEN", new BigDecimal("52.34"), vendaMedia.getMargemLucroPercentual());
        validarIgual("N2.15 Saldo Estoque Hub USB-C (7 - 3)", new BigDecimal("4.000000"), roteador.estoque().consultarSaldoTotalProduto(hubUsbUn.getId()));
        validarIgual("N2.16 Saldo Estoque Cabo HDMI (15,00 - 5,55)", new BigDecimal("9.450000"), roteador.estoque().consultarSaldoTotalProduto(caboHdmiMt.getId()));
    }

    // ==========================================================================================================
    // NÍVEL 3: ALTÍSSIMA COMPLEXIDADE (Super Kit Industrial + Item Fracionado Lote Desejável + Produto Digital
    //          + Promoções Combinadas + Split Payment 3 Formas + Juros Parcelado 7x + Mora Boleto 12 dias
    //          + Devolução Parcial com Avaria Gerando Perda Pós-Venda na Validade + Abatimento em Nova Compra)
    // ==========================================================================================================
    private static void executarTesteNivel3ComplexoMultissimo(RoteadorController roteador, String sufixo) {
        System.out.println("\n------------------------------------------------------------------------------------------");
        System.out.println(" [NÍVEL 3 - ALTÍSSIMA COMPLEXIDADE] Super Kit + Lote Desejável + Digital + Split Payment");
        System.out.println("            + Juros 7x + Mora 12d + Devolução/Perda Pós-Venda + Abatimento Fornecedor");
        System.out.println("------------------------------------------------------------------------------------------");

        PessoaJuridica fornIndustrial = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                "Indústria Química & Automação Amazônia S.A. " + sufixo, "43.333." + sufixo.substring(0, 3) + "/0001-30");
        PessoaJuridica clienteCorporativo = roteador.pessoas().cadastrarClientePessoaJuridica(
                "Polo Industrial Manaus S.A. " + sufixo, "54.444." + sufixo.substring(0, 3) + "/0001-40", new BigDecimal("50000.00"));

        // 1. Cadastro dos Produtos (KG, LT, UN Físico, UN Digital e Super Kit Composto)
        ProdutoFisico insumoAKg = roteador.produtos().cadastrarProdutoFisico(
                "Resina Epóxi Cristal (KG) #" + sufixo, "Fracionável em gramas",
                new BigDecimal("63.7500"), "KG",
                new BigDecimal("20.0000"), new BigDecimal("20.0000"), new BigDecimal("25.0000"),
                new BigDecimal("1.1000"), new BigDecimal("1.0000")
        );
        ProdutoFisico insumoBLt = roteador.produtos().cadastrarProdutoFisico(
                "Catalisador Polimérico (LT) #" + sufixo, "Fracionável em mililitros",
                new BigDecimal("42.4000"), "LT",
                new BigDecimal("22.0000"), new BigDecimal("12.0000"), new BigDecimal("12.0000"),
                new BigDecimal("0.9800"), new BigDecimal("0.9200")
        );
        ProdutoFisico equipCUn = roteador.produtos().cadastrarProdutoFisico(
                "Valvula Dosadora CNC (UN) #" + sufixo, "Unidade inteira de precisão",
                new BigDecimal("399.9000"), "UN",
                new BigDecimal("15.0000"), new BigDecimal("15.0000"), new BigDecimal("20.0000"),
                new BigDecimal("2.4000"), new BigDecimal("2.1500")
        );
        ProdutoDigital softwareD = roteador.produtos().cadastrarProdutoDigital(
                "Licença Firmware Telemetria Industrial #" + sufixo, "Licença digital perpétua",
                new BigDecimal("150.0000"), List.of()
        );

        // Super Kit Industrial: 1,250 kg Insumo A + 0,750 L Insumo B + 1 un Equipamento C
        // Preço Avulso por 1 Kit:
        // - Insumo A: 1,250 * 63,75 = 79,6875 -> R$ 79,69 (HALF_EVEN)
        // - Insumo B: 0,750 * 42,40 = 31,8000 -> R$ 31,80
        // - Equip C : 1,000 * 399,90 = R$ 399,90
        // Soma avulsa de 1 Kit = R$ 511,39 | Preço Promocional do Super Kit = R$ 449,00
        ProdutoFisico superKit = roteador.produtos().cadastrarProdutoFisico(
                "Super Kit Automação Epóxi Industrial #" + sufixo, "Kit composto industrial completo",
                new BigDecimal("449.0000"), "UN",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        roteador.produtos().adicionarComponenteAoKit(superKit.getId(), insumoAKg.getId(), new BigDecimal("1.250000"));
        roteador.produtos().adicionarComponenteAoKit(superKit.getId(), insumoBLt.getId(), new BigDecimal("0.750000"));
        roteador.produtos().adicionarComponenteAoKit(superKit.getId(), equipCUn.getId(), new BigDecimal("1.000000"));
        superKit = (ProdutoFisico) roteador.produtos().buscarPorId(superKit.getId());

        FormaPagamento fpPix = roteador.pagamentos().buscarFormaPorCodigo("PIX");

        // 2. Entrada de 3 Notas de Compra em tempos distintos para formar a fila FIFO e o Lote Desejável
        NotaCompra nc1 = new NotaCompra(fornIndustrial, "Compra Industrial 1 (Lotes A1, B1, C1)");
        nc1.addItem(new Item<>(insumoAKg, new BigDecimal("31.1250"), new BigDecimal("4.000000"), LocalDate.now().plusMonths(4)));
        nc1.addItem(new Item<>(insumoBLt, new BigDecimal("19.3333"), new BigDecimal("3.500000"), LocalDate.now().plusMonths(4)));
        nc1.addItem(new Item<>(equipCUn, new BigDecimal("210.0000"), new BigDecimal("2.000000"), LocalDate.now().plusMonths(4)));
        roteador.notas().emitirNotaCompra(nc1, Collections.singletonList(
                new PagamentoAVista(fpPix, nc1.getValorTotal(), nc1.getValorTotal(), BigDecimal.ZERO)));

        NotaCompra nc2 = new NotaCompra(fornIndustrial, "Compra Industrial 2 (Lotes A2, B2, C2)");
        nc2.addItem(new Item<>(insumoAKg, new BigDecimal("34.8750"), new BigDecimal("6.000000"), LocalDate.now().plusMonths(8)));
        nc2.addItem(new Item<>(insumoBLt, new BigDecimal("22.6667"), new BigDecimal("8.000000"), LocalDate.now().plusMonths(8)));
        Item<Produto> loteC2 = new Item<>(equipCUn, new BigDecimal("235.5000"), new BigDecimal("4.000000"), LocalDate.now().plusMonths(8));
        nc2.addItem(loteC2);
        nc2 = roteador.notas().emitirNotaCompra(nc2, Collections.singletonList(
                new PagamentoAVista(fpPix, nc2.getValorTotal(), nc2.getValorTotal(), BigDecimal.ZERO)));

        NotaCompra nc3 = new NotaCompra(fornIndustrial, "Compra Industrial 3 (Lote Especial A3 p/ Escolha Manual)");
        Item<Produto> loteEspecialA3 = new Item<>(insumoAKg, new BigDecimal("29.5000"), new BigDecimal("5.000000"), LocalDate.now().plusMonths(12));
        nc3.addItem(loteEspecialA3);
        nc3 = roteador.notas().emitirNotaCompra(nc3, Collections.singletonList(
                new PagamentoAVista(fpPix, nc3.getValorTotal(), nc3.getValorTotal(), BigDecimal.ZERO)));
        Long idLoteEspecialA3 = nc3.getItens().get(0).getId();

        // 3. Montagem da NotaVenda Híbrida de Altíssima Complexidade:
        //    - 4,000000 Super Kits via FIFO:
        //      * Insumo A (precisa 5,000 kg): 4,000 kg do Lote A1 (custo 31,1250 -> CMV 124,50) + 1,000 kg do Lote A2 (custo 34,8750 -> CMV 34,88 [34,875 -> 34,88 HALF_EVEN!])
        //        Bruto A = (4 * 63,75 = 255,00) + (1 * 63,75 = 63,75) = R$ 318,75
        //      * Insumo B (precisa 3,000 L): 3,000 L do Lote B1 (custo 19,3333 -> CMV = 3 * 19,3333 = 57,9999 -> R$ 58,00)
        //        Bruto B = 3 * 42,40 = R$ 127,20
        //      * Equip C (precisa 4,000 un): 2 un do Lote C1 (custo 210,00 -> CMV 420,00) + 2 un do Lote C2 (custo 235,50 -> CMV 471,00)
        //        Bruto C = (2 * 399,90 = 799,80) + (2 * 399,90 = 799,80) = R$ 1.599,60
        //      * Soma Bruta dos 4 Kits = 318,75 + 127,20 + 1.599,60 = R$ 2.045,55
        //      * Preço Promocional dos 4 Kits = 4 * 449,00 = R$ 1.796,00 (Desconto rateado de Kit = R$ 249,55)
        //    - + 2,333000 kg do Insumo A escolhidos diretamente do Lote Desejável A3 (custo 29,5000):
        //      * Bruto = 2,333000 * 63,75 = 148,72875 -> R$ 148,73 (HALF_EVEN)
        //      * CMV   = 2,333000 * 29,50 = 68,8235   -> R$ 68,82 (HALF_EVEN)
        //    - + 1,000000 un do Software Digital D (R$ 150,00) com promoção de 15% no item (-R$ 22,50 -> Líquido R$ 127,50 | CMV R$ 0,00)
        //    - + Cupom Global "CORP_PRIME_100" de -R$ 100,00!
        NotaVenda vendaComplexa = new NotaVenda(clienteCorporativo, "Venda Nível 3 - Híbrida FIFO + Lote Desejável + Digital + Split Payment");
        List<Item<? extends Produto>> lotesAlterados = new ArrayList<>();

        lotesAlterados.addAll(roteador.getEstoqueManage().consumirEstoqueFIFO(vendaComplexa, superKit, new BigDecimal("4.000000")));
        lotesAlterados.addAll(roteador.getEstoqueManage().consumirEstoquePorLoteDesejado(vendaComplexa, idLoteEspecialA3, new BigDecimal("2.333000")));
        lotesAlterados.addAll(roteador.getEstoqueManage().consumirEstoqueFIFO(vendaComplexa, softwareD, new BigDecimal("1.000000")));

        // Aplica promoção de 15% (R$ 22,50) exclusivamente no Software Digital e Cupom Global de R$ 100,00
        vendaComplexa.aplicarDescontoProduto(softwareD, new BigDecimal("22.5000"));
        vendaComplexa.aplicarCupomDesconto("CORP_PRIME_100", new BigDecimal("100.0000"));

        // Conferência da Receita e CMV antes dos pagamentos:
        // Valor Bruto Total = 2.045,55 (Kits) + 148,73 (Lote A3) + 150,00 (Software D) = R$ 2.344,28
        // Soma Subtotais Itens (após desconto Kit 249,55 e desconto Software 22,50) = 1.796,00 + 148,73 + 127,50 = R$ 2.072,23
        // Valor Total Líquido da Nota (após Cupom R$ 100,00) = 2.072,23 - 100,00 = R$ 1.972,23
        // CMV Total = 124,50 (A1) + 34,88 (A2) + 58,00 (B1) + 420,00 (C1) + 471,00 (C2) + 68,82 (A3) + 0,00 (Soft D) = R$ 1.177,20
        validarIgual("N3.1 Valor Bruto Total da Venda Complexa", new BigDecimal("2344.28"), vendaComplexa.getValorBruto());
        validarIgual("N3.2 Valor Total Líquido da Nota (após Kit, Promo Item e Cupom)", new BigDecimal("1972.23"), vendaComplexa.getValorTotal());
        validarIgual("N3.3 CMV Total de 6 Lotes Distintos (FIFO + Lote Desejável)", new BigDecimal("1177.20"), vendaComplexa.getCustoTotalMercadoriaVendida());

        // 4. Split Payment (3 Formas de Pagamento cobrindo os R$ 1.972,23):
        //    - Pagamento 1 (PIX À Vista): Nominal R$ 500,00 -> Desconto à vista 5% = R$ 25,00 -> Valor Final = R$ 475,00 | Taxa Op = R$ 0,00
        PagamentoAVista pag1Pix = roteador.pagamentos().criarPagamentoAVista("PIX", new BigDecimal("500.00"), null, null);

        //    - Pagamento 2 (Cartão de Crédito em 7x - com juros de 2,49% a.m. pois > 3x sem juros):
        //      Nominal = R$ 1.000,00 | Encargos (1000 * 2,49 * 7 / 100) = R$ 174,30 -> Valor Final = R$ 1.174,30
        //      Taxa MDR Operadora Cartão (2,99% sobre 1.174,30 = 35,11157) -> R$ 35,11 (HALF_EVEN)
        PagamentoParcelado pag2Cartao7x = roteador.pagamentos().criarPagamentoParcelado("CARTAO_CREDITO", new BigDecimal("1000.00"), 7);

        //    - Pagamento 3 (Boleto A Prazo para o restante: 1.972,23 - 500,00 - 1.000,00 = R$ 472,23):
        //      Vencimento há 12 dias e quitado hoje (12 dias de atraso):
        //      Multa 2,00% sobre 472,23 = 9,4446 (escala contábil 4)
        //      Juros mora 0,0333% * 12 dias = 0,3996% sobre 472,23 = 1,887031 -> 1,8870 (escala contábil 4)
        //      Encargos Atraso = arredondarMoeda(9,4446 + 1,8870 = 11,3316) = R$ 11,33
        //      Valor Final Boleto c/ Mora = 472,23 + 11,33 = R$ 483,56 | Tarifa Fixa Operadora Boleto = R$ 2,50
        PagamentoAPrazo pag3Boleto = roteador.pagamentos().criarPagamentoAPrazo(
                "BOLETO", new BigDecimal("472.23"), LocalDate.now().minusDays(12));

        roteador.getManageNotas().vincularPagamentosNaNota(vendaComplexa, List.of(pag1Pix, pag2Cartao7x, pag3Boleto));
        roteador.getManageNotas().vincularFatoresExternosNaNota(vendaComplexa);
        vendaComplexa.finalizarVenda();
        vendaComplexa = roteador.getManageNotas().emitirNotaVenda(vendaComplexa, List.of());

        // Quita o boleto hoje (com 12 dias de atraso)
        roteador.pagamentos().quitarPagamento(pag3Boleto, LocalDateTime.now());

        // Validações Matemáticas do Split Payment, Parcelas 7x, Mora de 12 dias e Lucro Líquido Final:
        validarIgual("N3.4 Juros Totais Parcelamento 7x Cartão (2,49% * 7)", new BigDecimal("174.30"), pag2Cartao7x.calcularEncargos());
        validarIgual("N3.5 Valor Final Cartão 7x c/ Juros", new BigDecimal("1174.30"), pag2Cartao7x.calcularValorFinal());
        // Em 7x de 1.000,00: base = 142,86 (142,86 * 7 = 1000,02 -> resíduo nominal -0,02 na 1ª parcela = 142,84)
        // Juros 174,30 / 7 = 24,90 exatos por parcela -> 1ª parcela = 142,84 + 24,90 = R$ 167,74 | demais 6 parcelas = 142,86 + 24,90 = R$ 167,76
        // Soma = 167,74 + (6 * 167,76 = 1006,56) = R$ 1.174,30!
        validarIgual("N3.6 1ª Parcela de 7x c/ Resíduo Centavo BACEN", new BigDecimal("167.74"), pag2Cartao7x.getParcelas().get(0).getValorTotalParcela());
        validarIgual("N3.7 2ª à 7ª Parcela de 7x BACEN", new BigDecimal("167.76"), pag2Cartao7x.getParcelas().get(1).getValorTotalParcela());
        validarIgual("N3.8 Soma das 7 Parcelas == R$ 1.174,30", new BigDecimal("1174.30"), pag2Cartao7x.getSaldoDevedorParcelas());

        validarIgualInteiro("N3.9 Dias de Atraso Apurados no Boleto", 12, (int) pag3Boleto.getDiasAtraso(LocalDate.now()));
        validarIgual("N3.10 Encargos Moratórios Boleto (Multa 2% + Juros 12d)", new BigDecimal("11.33"), pag3Boleto.calcularEncargos());
        validarIgual("N3.11 Valor Final Quitado do Boleto Atrasado", new BigDecimal("483.56"), pag3Boleto.calcularValorFinal());

        // Custo Total de Taxas de Operadora na Nota = R$ 0,00 (PIX) + R$ 35,11 (MDR Cartão) + R$ 2,50 (Tarifa Fixa Boleto) = R$ 37,61
        // Lucro Líquido da Venda Complexa = 1.972,23 (Receita Líq) - 1.177,20 (CMV) - 37,61 (Taxas Op) = R$ 757,42
        // Margem Líquida % = (757,42 * 100) / 1.177,20 = 64,340808... -> 64,34%
        validarIgual("N3.12 Total Taxas Operadora (MDR Cartão 35,11 + Tarifa Boleto 2,50)", new BigDecimal("37.61"), vendaComplexa.getCustoTaxasPagamento());
        validarIgual("N3.13 Lucro Líquido Final da Venda Complexa", new BigDecimal("757.42"), vendaComplexa.getLucroLiquidoVenda());
        validarIgual("N3.14 Margem de Lucro Líquida % da Venda Complexa", new BigDecimal("64.34"), vendaComplexa.getMargemLucroPercentual());

        // 5. Pós-Venda: Devolução de 1 unidade do Equipamento C (oriundo do Lote C2 - custo R$ 235,50) por defeito dentro da validade
        Item<? extends Produto> itemVendaEquipLoteC2 = vendaComplexa.getItens().stream()
                .filter(i -> i.getProduto().getId().equals(equipCUn.getId())
                        && i.getItemOrigem() != null
                        && new BigDecimal("235.5000").compareTo(i.getItemOrigem().getValor()) == 0)
                .findFirst()
                .orElseThrow();

        Map<Item<? extends Produto>, BigDecimal> mapaDevolucao = new LinkedHashMap<>();
        mapaDevolucao.put(itemVendaEquipLoteC2, new BigDecimal("1.000000"));

        NotaDevolucao devolucao = roteador.notas().registrarDevolucaoVenda(
                vendaComplexa,
                mapaDevolucao,
                "Defeito de fabricação na Válvula CNC dentro do prazo de validade",
                true // Gera NotaPerda pós-venda vinculada ao fornecedor do Lote C2
        );

        NotaPerda perdaPosVenda = devolucao.getNotaPerdaGerada();
        // Validação Matemática do Estorno Proporcional de Kit + Rateio Proporcional do Cupom Global:
        // No Lote C2 (2 un vendidas), Bruto = 799,80 | Desconto Kit rateado no item = 97,56 -> p/ 1 un devolvida:
        // Bruto Devolvido = 399,90 | Desconto Kit Proporcional (97,56 * 1/2) = 48,78 -> Subtotal c/ Desconto Kit = 351,12
        // Cupom Global Proporcional (100,00 * 351,12 / 2072,23 = 16,944069...) -> R$ 16,94 (HALF_EVEN)
        // Estorno Líquido ao Cliente = 351,12 - 16,94 = R$ 334,18
        validarIgual("N3.15 Desconto Proporcional do Kit na Devolução (1 de 2 un)", new BigDecimal("48.78"), devolucao.getItens().get(0).getDesconto());
        validarIgual("N3.16 Subtotal Devolvido Após Desconto Kit (399,90 - 48,78)", new BigDecimal("351.12"), devolucao.getSubtotalItensComDescontoKit());
        validarIgual("N3.17 Rateio Proporcional do Cupom Global (R$ 100) no Estorno", new BigDecimal("16.94"), devolucao.getDescontoGlobal());
        validarIgual("N3.18 Estorno Líquido Exato ao Cliente (351,12 - 16,94)", new BigDecimal("334.18"), devolucao.calcularValorEstornoCliente());
        validarIgual("N3.19 Saldo Estoque Equip C Não Alterado (Item c/ Defeito)", new BigDecimal("2.000000"), roteador.estoque().consultarSaldoTotalProduto(equipCUn.getId()));
        validarIgual("N3.20 Custo da Perda Pós-Venda Herdado do Lote C2", new BigDecimal("235.50"), perdaPosVenda.getValorCustoPerda());
        validarVerdadeiro("N3.21 Perda Pós-Venda está Dentro da Validade", perdaPosVenda.isPerdaDentroDaValidade());
        validarVerdadeiro("N3.22 Perda Pós-Venda Vinculada ao Fornecedor Correto", perdaPosVenda.pertenceAoFornecedor(fornIndustrial));

        // 6. Devolução Parcial 2 (Produto Íntegro por Arrependimento dentro da validade -> Reintegra ao Lote A3):
        //    Devolve 0,500000 kg dos 2,333000 kg comprados do Lote Especial A3:
        //    Bruto = 0,500000 * 63,75 = 31,875 -> R$ 31,88 (HALF_EVEN)
        //    Cupom Global Proporcional = 100,00 * 31,88 / 2072,23 = 1,538439... -> R$ 1,54
        //    Estorno Líquido = 31,88 - 1,54 = R$ 30,34
        //    Saldo do Lote A3 sobe de 2,667000 kg para 3,167000 kg (e saldo total Resina A sobe de 7,667000 para 8,167000 kg)
        Item<? extends Produto> itemVendaLoteA3 = vendaComplexa.getItens().stream()
                .filter(i -> i.getItemOrigem() != null && idLoteEspecialA3.equals(i.getItemOrigem().getId()))
                .findFirst()
                .orElseThrow();

        Map<Item<? extends Produto>, BigDecimal> mapaDevIntegra = new LinkedHashMap<>();
        mapaDevIntegra.put(itemVendaLoteA3, new BigDecimal("0.500000"));
        NotaDevolucao devolucaoIntegra = roteador.notas().registrarDevolucaoVenda(
                vendaComplexa,
                mapaDevIntegra,
                "Devolução parcial de 0,500 kg íntegros (sobra de projeto dentro da validade)",
                false // Produto íntegro -> reintegra ao lote original A3 sem gerar perda
        );

        validarIgual("N3.23 Estorno Líquido Devolução Fracionada 0,5kg c/ Rateio Cupom", new BigDecimal("30.34"), devolucaoIntegra.calcularValorEstornoCliente());
        validarIgual("N3.24 Saldo Lote A3 Reintegrado (2,667000 + 0,500000 kg)", new BigDecimal("3.167000"), roteador.estoque().buscarLoteEspecificoPorId(idLoteEspecialA3).getQuantidadeDisponivel());
        validarIgual("N3.25 Saldo Total Resina A Após Reintegração (7,667 + 0,500)", new BigDecimal("8.167000"), roteador.estoque().consultarSaldoTotalProduto(insumoAKg.getId()));

        // Valida bloqueio de devolução acima do saldo devolvível restante (2,333 - 0,500 = 1,833 kg restantes; tenta devolver 2,000 kg)
        boolean bloqueouExcessoDevolucao = false;
        try {
            NotaDevolucao devInvalida = new NotaDevolucao(vendaComplexa, "Tentativa de devolução acima do comprado");
            devInvalida.adicionarItemDevolvido(itemVendaLoteA3, new BigDecimal("2.000000"));
        } catch (IllegalArgumentException ex) {
            bloqueouExcessoDevolucao = true;
        }
        validarVerdadeiro("N3.26 Bloqueio de Devolução Acima do Saldo Comprado Restante", bloqueouExcessoDevolucao);

        // 7. Nova Compra de R$ 1.000,00 no mesmo Fornecedor com Abatimento Automático da Perda Pós-Venda (-R$ 235,50)
        NotaCompra novaCompraReposicao = new NotaCompra(fornIndustrial, "Nova Compra com Abatimento de Perda Pós-Venda");
        novaCompraReposicao.addItem(new Item<>(equipCUn, new BigDecimal("250.0000"), new BigDecimal("4.000000"), LocalDate.now().plusMonths(10)));

        BigDecimal totalAbatido = roteador.notas().abaterPerdasPosVendaEmNovaCompra(novaCompraReposicao, equipCUn);
        validarIgual("N3.27 Valor Abatido de Perdas Pós-Venda na Nova Compra", new BigDecimal("235.50"), totalAbatido);
        validarIgual("N3.28 Valor Líquido a Pagar da Nova Compra (1000,00 - 235,50)", new BigDecimal("764.50"), novaCompraReposicao.getValorTotal());
    }

    private static void validarIgual(String descricao, BigDecimal esperado, BigDecimal obtido) {
        totalAssercoes++;
        BigDecimal esp = esperado.stripTrailingZeros();
        BigDecimal obt = obtido != null ? obtido.stripTrailingZeros() : null;
        boolean ok = obt != null && esp.compareTo(obt) == 0;
        if (ok) {
            assercoesAprovadas++;
            String msg = String.format("  [OK] %-58s | Esperado: %12s | Obtido: %12s", descricao, esperado, obtido);
            System.out.println(msg);
            logValidacoes.add(msg);
        } else {
            String msg = String.format("  [ERRO] %-56s | Esperado: %12s | Obtido: %12s", descricao, esperado, obtido);
            System.err.println(msg);
            logValidacoes.add(msg);
            throw new AssertionError(msg);
        }
    }

    private static void validarIgualInteiro(String descricao, int esperado, int obtido) {
        validarIgual(descricao, BigDecimal.valueOf(esperado), BigDecimal.valueOf(obtido));
    }

    private static void validarIgualTexto(String descricao, String esperado, String obtido) {
        totalAssercoes++;
        if (esperado.equals(obtido)) {
            assercoesAprovadas++;
            System.out.printf("  [OK] %-58s | Esperado: %12s | Obtido: %12s%n", descricao, esperado, obtido);
        } else {
            throw new AssertionError("Diferença em " + descricao + ": esperado=" + esperado + ", obtido=" + obtido);
        }
    }

    private static void validarVerdadeiro(String descricao, boolean condicao) {
        validarIgualInteiro(descricao, 1, condicao ? 1 : 0);
    }
}
