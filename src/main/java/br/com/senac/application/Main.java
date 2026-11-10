package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.service.EstoqueManage;
import br.com.senac.service.ManageNotas;
import br.com.senac.integracao.calendario.CalendarioService;
import br.com.senac.integracao.calendario.DiaNaoUtil;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.clima.ClimaService;
import br.com.senac.integracao.eventos.EventoSympla;
import br.com.senac.integracao.eventos.MinisterioCulturaService;
import br.com.senac.integracao.eventos.ProjetoCulturalMinC;
import br.com.senac.integracao.eventos.SymplaService;
import br.com.senac.integracao.geolocalizacao.Coordenada;
import br.com.senac.integracao.geolocalizacao.GeolocalizacaoService;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.integracao.rios.NivelRioService;
import br.com.senac.repository.dao.JPAUtil;
import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.pagamento.FormaPagamentoDAO;
import br.com.senac.repository.dao.pessoa.PessoaDAO;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.dao.produto.UmDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAVista;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {


        //iniciar();



    }


    public static void iniciar() {
        try {
            RoteadorController roteador = new RoteadorController();
            roteador.inicializarSistemaCompleto();

            ProdutoDAO pDao = new ProdutoDAO();
            UmDAO uDao = new UmDAO();

            // Se o banco (embarcado H2 ou MySQL) estiver vazio, cria os registros iniciais de teste
            if (pDao.listarTodos().isEmpty()) {
                Um unidade = uDao.buscarPorSigla("UN");
                if (unidade == null) {
                    unidade = new Um("UN", "Unidade", "Unidade padrão inteira", BigDecimal.ONE);
                    uDao.salvar(unidade);
                }

                ProdutoFisico mouse = new ProdutoFisico(
                        "Mouse Gamer Sem Fio",
                        unidade,
                        new BigDecimal("4.2000"),
                        new BigDecimal("6.5000"),
                        new BigDecimal("12.5000"),
                        new BigDecimal("0.1800"),
                        new BigDecimal("0.1100")
                );
                mouse.setDescricao("Mouse óptico 16000 DPI com receptor USB");
                mouse.setPrecoVenda(new BigDecimal("199.90"));
                pDao.salvar(mouse);

                ProdutoDigital curso = new ProdutoDigital("Curso Completo de Java e JPA", unidade);
                curso.setDescricao("Acesso vitalício às aulas e materiais em PDF");
                curso.setPrecoVenda(new BigDecimal("297.00"));
                curso.adicionarLink(new LinkProdutoDigital("Plataforma de Aulas", "https://senac.com.br/aulas/java-jpa"));
                curso.adicionarLink(new LinkProdutoDigital("Apostila em PDF", "https://senac.com.br/materiais/apostila-jpa.pdf"));
                pDao.salvar(curso);

                ProdutoFisico teclado = new ProdutoFisico(
                        "Teclado Mecânico RGB",
                        unidade,
                        new BigDecimal("3.8000"),
                        new BigDecimal("14.0000"),
                        new BigDecimal("44.0000"),
                        new BigDecimal("0.9500"),
                        new BigDecimal("0.8200")
                );
                teclado.setDescricao("Teclado mecânico switch blue ABNT2");
                teclado.setPrecoVenda(new BigDecimal("289.90"));
                pDao.salvar(teclado);

                ProdutoFisico kitGamer = new ProdutoFisico("Kit Gamer Completo (Teclado + 2 Mouses)", unidade);
                kitGamer.setDescricao("Combo promocional contendo teclado mecânico e mouses");
                kitGamer.setPrecoVenda(new BigDecimal("599.90"));
                kitGamer.adicionarItemConjunto(teclado, new BigDecimal("1.000000"));
                kitGamer.adicionarItemConjunto(mouse, new BigDecimal("2.000000"));
                pDao.salvar(kitGamer);
            } else {
                // Atualiza dimensões caso os produtos físicos já existam sem dimensões definidas
                Produto m = pDao.buscarPorNome("Mouse Gamer Sem Fio");
                if (m instanceof ProdutoFisico pfMouse && pfMouse.calcularVolumeCm3().compareTo(BigDecimal.ZERO) == 0) {
                    roteador.produtos().atualizarDimensoesProdutoFisico(
                            pfMouse.getId(),
                            new BigDecimal("4.2000"), new BigDecimal("6.5000"), new BigDecimal("12.5000"),
                            new BigDecimal("0.1800"), new BigDecimal("0.1100"));
                }
                Produto t = pDao.buscarPorNome("Teclado Mecânico RGB");
                if (t instanceof ProdutoFisico pfTeclado && pfTeclado.calcularVolumeCm3().compareTo(BigDecimal.ZERO) == 0) {
                    roteador.produtos().atualizarDimensoesProdutoFisico(
                            pfTeclado.getId(),
                            new BigDecimal("3.8000"), new BigDecimal("14.0000"), new BigDecimal("44.0000"),
                            new BigDecimal("0.9500"), new BigDecimal("0.8200"));
                }
            }

            System.out.println(pDao.buscarPorId(1L));
            System.out.println(pDao.buscarPorId(2L));
            System.out.println(pDao.buscarPorId(4L));

            FormaPagamentoDAO fpDao = new FormaPagamentoDAO();
            System.out.println("\n--- Cliente Padrão para Vendas sem Cliente Registrado ---");
            PessoaFisica clientePadrao = roteador.pessoas().obterClientePadraoNaoRegistrado();
            System.out.println(clientePadrao);

            System.out.println("\n--- Unidades de Medida Cadastradas no MySQL (sazonalisei) ---");
            uDao.listarTodos().forEach(System.out::println);

            System.out.println("\n--- Formas de Pagamento Customizáveis (Tabela forma_pagamento) ---");
            fpDao.listarAtivas().forEach(System.out::println);

            // Demonstração do Controle de Estoque FIFO, Escolha de Lote Desejável e Venda para Cliente Padrão
            EstoqueManage estoqueManage = roteador.getEstoqueManage();
            ManageNotas manageNotas = roteador.getManageNotas();
            PessoaDAO pessoaDAO = new PessoaDAO();
            NotaDAO notaDAO = new NotaDAO();

            Produto mouse = pDao.buscarPorNome("Mouse Gamer Sem Fio");
            FormaPagamento fpPix = fpDao.buscarPorCodigo("PIX");
            FormaPagamento fpCartaoCredito = fpDao.buscarPorCodigo("CARTAO_CREDITO");

            if (mouse != null && estoqueManage.consultarSaldoEstoque(mouse).compareTo(BigDecimal.ZERO) == 0) {
                PessoaJuridica fornecedor = roteador.pessoas().cadastrarFornecedorPessoaJuridica(
                        "Distribuidora Tech Manaus LTDA", "12.345.678/0001-99");
                PessoaFisica cliente = roteador.pessoas().cadastrarClientePessoaFisica(
                        "Carlos Silva", "111.222.333-44", new BigDecimal("5000.00"));

                // Entrada do Lote 1 (Mais antigo - 5 unidades)
                NotaCompra compraLote1 = new NotaCompra(fornecedor, "Compra Lote 1 (Mais antigo - FIFO)");
                Item<Produto> itemLote1 = new Item<>(mouse, new BigDecimal("110.0000"), new BigDecimal("5.000000"), LocalDate.now().plusMonths(6));
                compraLote1.addItem(itemLote1);
                manageNotas.emitirNotaCompra(compraLote1, Collections.singletonList(
                        new PagamentoAVista(fpPix, new BigDecimal("550.0000"), new BigDecimal("550.0000"), BigDecimal.ZERO)));

                // Entrada do Lote 2 (Mais recente - 10 unidades)
                NotaCompra compraLote2 = new NotaCompra(fornecedor, "Compra Lote 2 (Mais recente)");
                Item<Produto> itemLote2 = new Item<>(mouse, new BigDecimal("120.0000"), new BigDecimal("10.000000"), LocalDate.now().plusMonths(12));
                compraLote2.addItem(itemLote2);
                manageNotas.emitirNotaCompra(compraLote2, Collections.singletonList(
                        new PagamentoAVista(fpPix, new BigDecimal("1200.0000"), new BigDecimal("1200.0000"), BigDecimal.ZERO)));

                // 1. Venda via Algoritmo FIFO automático (6 unidades -> consome 5 do Lote 1 + 1 do Lote 2)
                NotaVenda vendaFIFO = new NotaVenda(cliente, "Venda Automática via Algoritmo FIFO");
                Map<Produto, BigDecimal> itensFIFO = new LinkedHashMap<>();
                itensFIFO.put(mouse, new BigDecimal("6.000000"));
                manageNotas.emitirNotaVendaFIFO(vendaFIFO, itensFIFO, Collections.singletonList(
                        new PagamentoAVista(fpCartaoCredito, new BigDecimal("1199.4000"), new BigDecimal("1199.4000"), BigDecimal.ZERO)));

                // 2. Venda escolhendo o Lote Desejável para cliente NÃO registrado (cliente == null -> Cliente Padrão automático)
                Long idLoteDesejado = compraLote2.getItens().get(0).getId();
                Map<Long, BigDecimal> mapaLoteDesejado = new LinkedHashMap<>();
                mapaLoteDesejado.put(idLoteDesejado, new BigDecimal("2.000000"));
                roteador.notas().emitirVendaPorLoteDesejado(
                        null,
                        "Venda Balcão Sem Cadastro (Lote Desejável ID " + idLoteDesejado + ")",
                        mapaLoteDesejado,
                        Collections.singletonList(new PagamentoAVista(fpPix, new BigDecimal("399.8000")))
                );
            }

            if (mouse != null && estoqueManage.consultarSaldoEstoque(mouse).compareTo(new BigDecimal("7.000000")) == 0) {
                Map<Produto, BigDecimal> itensBalcao = new LinkedHashMap<>();
                itensBalcao.put(mouse, new BigDecimal("1.000000"));
                NotaVenda vendaSemCadastro = roteador.notas().emitirVendaBalcaoClienteNaoRegistradoFIFO(
                        itensBalcao,
                        Collections.singletonList(new PagamentoAVista(fpPix, new BigDecimal("199.9000")))
                );
                System.out.println("\n--- Venda Emitida Sem Cliente Registrado (Vinculada Automaticamente ao Cliente Padrão) ---");
                System.out.println("NotaVenda #" + vendaSemCadastro.getId()
                        + " -> Cliente vinculado: " + vendaSemCadastro.getPessoa().getNome()
                        + " (CPF: " + ((PessoaFisica) vendaSemCadastro.getPessoa()).getCpf() + ")");
            }

            if (mouse != null) {
                System.out.println("\n--- Apuração Contábil de Lucro na View (vw_apuracao_lucro_contabil - Padrão BACEN) ---");
                for (Object[] l : notaDAO.consultarViewApuracaoLucroContabil(null)) {
                    System.out.printf("Venda #%s | %s | Qtd: %s | Custo Lote: R$ %s | Venda Unit: R$ %s | CMV: R$ %s | Receita Líq: R$ %s | Lucro Líq: R$ %s (%s%%)%n",
                            l[0], l[1], l[2], l[3], l[4], l[5], l[6], l[8], l[9]);
                }

                System.out.println("\n--- Saldo Consolidado na View (vw_saldo_consolidado_estoque) ---");
                System.out.println("Saldo atual de '" + mouse.getNome() + "': " + estoqueManage.consultarSaldoEstoque(mouse));

                System.out.println("\n--- Fila de Lotes Disponíveis na View (vw_lotes_fifo_disponiveis) ---");
                for (Object[] linha : notaDAO.consultarViewFilaLotesFIFO(mouse.getId())) {
                    System.out.printf("Lote ID: %s | Produto: %s | Entrada: %s | Validade: %s | Qtd Disp: %s | Custo: %s%n",
                            linha[0], linha[1], linha[2], linha[3], linha[4], linha[5]);
                }
            }

            System.out.println("\n--- Mapeamento Completo de Rotas dos Controllers (" + RotaSistema.values().length + " rotas) ---");
            roteador.listarMapaRotasDisponiveis().values().forEach(System.out::println);
        } finally {
            JPAUtil.close();
        }
    }
}
