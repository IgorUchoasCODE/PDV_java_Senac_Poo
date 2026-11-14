package br.com.senac.view.javafx.abas;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.clima.ClimaDiario;
import br.com.senac.integracao.rios.NivelRioDiario;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import br.com.senac.view.javafx.componentes.CardKpiContainer;
import br.com.senac.view.javafx.componentes.FormatadorDadosUI;
import br.com.senac.view.javafx.gateway.SistemaGateway;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Primeira aba (Visão Geral Clean): KPIs essenciais, Previsão do Tempo & Nível do Rio,
 * Insights Urgentes (Estratégia de Compra/Venda e Alertas de Validade/Perdas)
 * e Relatórios BACEN organizados em sub-abas limpas.
 */
public class AbaEstatisticaFinanceiroContainer extends ScrollPane {

    private final SistemaGateway gateway;
    private final Consumer<String> aoPesquisarNoChat;

    private final CardKpiContainer kpiReceita = new CardKpiContainer("Receita Bruta", "R$ 0,00", "Apuração BACEN", "card-kpi-emerald");
    private final CardKpiContainer kpiCmv = new CardKpiContainer("CMV (Custo FIFO)", "R$ 0,00", "Custo real das mercadorias", "card-kpi-cyan");
    private final CardKpiContainer kpiLucro = new CardKpiContainer("Lucro Líquido", "R$ 0,00", "Líquido de taxas e descontos", "card-kpi-amber");
    private final CardKpiContainer kpiAlertas = new CardKpiContainer("Alertas Urgentes", "0", "Validade, perdas e clima", "card-kpi-crimson");

    // Previsão do Tempo & Rio em lista de contêineres (com atmosfera dinâmica por temperatura)
    private VBox cardPrevisao;
    private final FlowPane listaPrevisaoContainers = new FlowPane(12, 12);
    private final Label lblPrevisaoResumo = new Label("Sincronizando leitura climática e hidrológica...");

    // Insights Urgentes (Estratégia & Alertas) em lista de contêineres
    private final VBox listaEstrategiaUrgente = new VBox(8);
    private final VBox listaAlertasUrgentes = new VBox(8);

    // Relatórios detalhados em sub-abas limpas
    private final TableView<String[]> tabelaLucroBacen = new TableView<>();
    private final TableView<String[]> tabelaPerdasFornecedor = new TableView<>();

    public AbaEstatisticaFinanceiroContainer(SistemaGateway gateway, Consumer<String> aoPesquisarNoChat) {
        this.gateway = gateway;
        this.aoPesquisarNoChat = aoPesquisarNoChat;
        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox conteudo = new VBox(16);
        conteudo.setPadding(new Insets(18));

        // 1. Cabeçalho limpo
        HBox topo = new HBox(12);
        topo.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("VISÃO GERAL • CLIMA, ESTRATÉGIA & ALERTAS URGENTES");
        titulo.getStyleClass().add("section-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnAtualizar = new Button("Atualizar Painel");
        btnAtualizar.getStyleClass().add("btn-primary");
        btnAtualizar.setOnAction(e -> recarregarDados());
        topo.getChildren().addAll(titulo, spacer, btnAtualizar);

        // 2. KPIs essenciais
        FlowPane linhaKpis = new FlowPane(14, 14, kpiReceita, kpiCmv, kpiLucro, kpiAlertas);

        // 3. Faixa Clean e Humanizada de Previsão do Tempo & Nível do Rio
        cardPrevisao = criarCardPrevisaoTempoRio();

        // 4. Insights e Alertas Urgentes empilhados (um debaixo do outro, largura total)
        VBox secaoUrgencias = criarLinhaInsightsUrgentes();

        // 5. Relatórios financeiros organizados em sub-abas (sem poluir a visão principal)
        configurarTabelaArray(tabelaLucroBacen, new String[]{"Nota / Ref.", "Cliente / Data", "Receita", "CMV FIFO", "Taxas/Desc.", "Lucro Líquido"});
        tabelaLucroBacen.setPrefHeight(210);

        configurarTabelaArray(tabelaPerdasFornecedor, new String[]{"Fornecedor", "Produto", "Qtd. Perdida", "Prejuízo Est.", "Motivo / Status"});
        tabelaPerdasFornecedor.setPrefHeight(210);

        TabPane abasRelatorios = new TabPane(
                new Tab("Lucro Contábil BACEN", tabelaLucroBacen),
                new Tab("Perdas por Fornecedor", tabelaPerdasFornecedor)
        );
        abasRelatorios.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abasRelatorios.setPrefHeight(255);

        VBox cardRelatorios = new VBox(8, new Label("Demonstrativos Contábeis (Detalhamento)"), abasRelatorios);
        cardRelatorios.getStyleClass().add("card-container");

        conteudo.getChildren().addAll(topo, linhaKpis, cardPrevisao, secaoUrgencias, cardRelatorios);
        setContent(conteudo);

        recarregarDados();
    }

    private VBox criarCardPrevisaoTempoRio() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card-container");
        card.setPadding(new Insets(16));

        Label lblHeader = new Label("CLIMA HOJE & MONITORAMENTO DO RIO");
        lblHeader.getStyleClass().add("section-title");

        listaPrevisaoContainers.setPrefWrapLength(960);
        lblPrevisaoResumo.setStyle("-fx-font-size: 13px; -fx-text-fill: #CBD5E1;");
        lblPrevisaoResumo.setWrapText(true);

        card.getChildren().addAll(lblHeader, listaPrevisaoContainers, lblPrevisaoResumo);
        return card;
    }

    private VBox criarLinhaInsightsUrgentes() {
        Button btnEstrategia = new Button("Consultar Estratégia na IA");
        btnEstrategia.getStyleClass().add("btn-primary");
        btnEstrategia.setOnAction(e -> {
            if (aoPesquisarNoChat != null) {
                aoPesquisarNoChat.accept("Com base na previsão do tempo, nível do rio e sazonalidade, o que devo comprar e vender com urgência esta semana?");
            }
        });

        Label titEstrat = new Label("INSIGHTS & ESTRATÉGIA • COMPRA E VENDA");
        titEstrat.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px; -fx-text-fill: #2DD4BF;");
        VBox boxEstrategia = new VBox(12, titEstrat, listaEstrategiaUrgente, btnEstrategia);
        boxEstrategia.getStyleClass().add("insight-box");
        boxEstrategia.setPadding(new Insets(16));
        boxEstrategia.setMaxWidth(Double.MAX_VALUE);

        Button btnAlertas = new Button("Resolver Alertas na IA");
        btnAlertas.getStyleClass().add("btn-secondary");
        btnAlertas.setOnAction(e -> {
            if (aoPesquisarNoChat != null) {
                aoPesquisarNoChat.accept("Quais são os alertas urgentes de validade, perdas por fornecedor e riscos climáticos que preciso resolver agora?");
            }
        });

        Label titAlertas = new Label("ALERTAS URGENTES • VALIDADE, PERDAS & CLIMA");
        titAlertas.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px; -fx-text-fill: #F59E0B;");
        VBox boxAlertas = new VBox(12, titAlertas, listaAlertasUrgentes, btnAlertas);
        boxAlertas.getStyleClass().add("insight-warning-box");
        boxAlertas.setPadding(new Insets(16));
        boxAlertas.setMaxWidth(Double.MAX_VALUE);

        VBox empilhado = new VBox(16, boxEstrategia, boxAlertas);
        empilhado.setFillWidth(true);
        return empilhado;
    }

    private void configurarTabelaArray(TableView<String[]> tabela, String[] colunas) {
        tabela.getColumns().clear();
        for (int i = 0; i < colunas.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(colunas[i]);
            col.setCellValueFactory(cell -> {
                String[] row = cell.getValue();
                return new SimpleStringProperty(row != null && idx < row.length ? row[idx] : "-");
            });
            col.setPrefWidth(140);
            tabela.getColumns().add(col);
        }
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    public void recarregarDados() {
        gateway.executarEmBackground(() -> {
            RespostaEndpoint<Object> respLucro = gateway.executarRota(RotaSistema.RELATORIO_LUCRO_CONTABIL_BACEN, Map.of());
            RespostaEndpoint<Object> respPerdasForn = gateway.executarRota(RotaSistema.RELATORIO_PERDAS_POR_FORNECEDOR, Map.of());
            RespostaEndpoint<Object> respDestaques = gateway.executarRota(RotaSistema.ESTATISTICA_DESTAQUES_SAZONAIS, Map.of());
            RespostaEndpoint<Object> respPlano = gateway.executarRota(RotaSistema.ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS, Map.of());
            RespostaEndpoint<Object> respMotivos = gateway.executarRota(RotaSistema.ESTATISTICA_MOTIVOS_PERDA_PREMATURA, Map.of());
            RespostaEndpoint<Object> respLotesVenc = gateway.executarRota(RotaSistema.ESTOQUE_LOTES_PROXIMOS_VALIDADE,
                    Map.of("dataLimite", LocalDate.now().plusDays(15)));
            RespostaEndpoint<Object> respNotas = gateway.executarRota(RotaSistema.NOTA_LISTAR_PADRAO, Map.of("limite", 25));

            Platform.runLater(() -> atualizarInterface(
                    respLucro, respPerdasForn, respDestaques, respPlano, respMotivos, respLotesVenc, respNotas
            ));
        }, err -> Platform.runLater(() -> lblPrevisaoResumo.setText("Erro ao carregar visão geral: " + FormatadorDadosUI.texto(err.getMessage(), "Erro desconhecido"))));
    }

    private void atualizarInterface(
            RespostaEndpoint<Object> respLucro,
            RespostaEndpoint<Object> respPerdasForn,
            RespostaEndpoint<Object> respDestaques,
            RespostaEndpoint<Object> respPlano,
            RespostaEndpoint<Object> respMotivos,
            RespostaEndpoint<Object> respLotesVenc,
            RespostaEndpoint<Object> respNotas
    ) {
        // 1. Financeiro BACEN formatado
        ObservableList<String[]> linhasBacen = FXCollections.observableArrayList();
        BigDecimal receita = BigDecimal.ZERO;
        BigDecimal cmv = BigDecimal.ZERO;
        BigDecimal lucro = BigDecimal.ZERO;

        if (respLucro != null && respLucro.sucesso() && respLucro.dados() instanceof List<?> lista) {
            for (Object rowObj : lista) {
                if (rowObj instanceof Object[] row && row.length >= 6) {
                    BigDecimal r = FormatadorDadosUI.toBigDecimal(row[2]);
                    BigDecimal c = FormatadorDadosUI.toBigDecimal(row[3]);
                    BigDecimal t = FormatadorDadosUI.toBigDecimal(row[4]);
                    BigDecimal l = FormatadorDadosUI.toBigDecimal(row[5]);
                    receita = receita.add(r);
                    cmv = cmv.add(c);
                    lucro = lucro.add(l);
                    linhasBacen.add(new String[]{
                            FormatadorDadosUI.texto(row[0], "-"),
                            FormatadorDadosUI.dataHora(row[1], FormatadorDadosUI.texto(row[1], "-")),
                            FormatadorDadosUI.moeda(r),
                            FormatadorDadosUI.moeda(c),
                            FormatadorDadosUI.moeda(t),
                            FormatadorDadosUI.moeda(l)
                    });
                }
            }
            kpiReceita.atualizar(FormatadorDadosUI.moeda(receita), lista.size() + " registro(s) BACEN");
            kpiCmv.atualizar(FormatadorDadosUI.moeda(cmv), "Custo real FIFO");
            kpiLucro.atualizar(FormatadorDadosUI.moeda(lucro), "Após taxas e descontos");
        }
        tabelaLucroBacen.setItems(linhasBacen);

        // 2. Perdas por Fornecedor formatado
        ObservableList<String[]> linhasPerdas = FXCollections.observableArrayList();
        if (respPerdasForn != null && respPerdasForn.sucesso() && respPerdasForn.dados() instanceof List<?> listaPerdas) {
            for (Object rowObj : listaPerdas) {
                if (rowObj instanceof Object[] row) {
                    linhasPerdas.add(new String[]{
                            row.length > 0 ? FormatadorDadosUI.texto(row[0], "-") : "-",
                            row.length > 1 ? FormatadorDadosUI.texto(row[1], "-") : "-",
                            row.length > 2 ? FormatadorDadosUI.quantidade(row[2]) : "0",
                            row.length > 3 ? FormatadorDadosUI.moeda(row[3]) : "R$ 0,00",
                            row.length > 4 ? FormatadorDadosUI.texto(row[4], "Monitorado") : "Monitorado"
                    });
                }
            }
        }
        tabelaPerdasFornecedor.setItems(linhasPerdas);

        // 3. Previsão do Tempo & Nível do Rio (extraído do histórico mais recente vinculado ou tendência padrão)
        double tempMax = 28.5;
        double chuvaMm = 4.2;
        double rioM3s = 132.0;
        boolean dadoClimaticoReal = false;

        if (respNotas != null && respNotas.sucesso() && respNotas.dados() instanceof List<?> notas) {
            for (Object obj : notas) {
                if (obj instanceof Nota n && n.getVinculoFatorExterno() != null) {
                    VinculoFatorExternoNota v = n.getVinculoFatorExterno();
                    ClimaDiario clima = v.getClimaRegistrado();
                    NivelRioDiario rio = v.getNivelRioRegistrado();
                    if (clima != null) {
                        tempMax = clima.temperaturaMax() > 0 ? clima.temperaturaMax() : clima.temperaturaMedia();
                        chuvaMm = clima.precipitacaoMm();
                        dadoClimaticoReal = true;
                    }
                    if (rio != null) {
                        rioM3s = rio.vazaoM3s();
                    }
                    if (dadoClimaticoReal) break;
                }
            }
        }

        String condicao = chuvaMm >= 10.0 ? "Chuva Forte" : (tempMax >= 30.0 ? "Dia Quente" : (tempMax <= 19.0 ? "Clima Frio" : "Clima Agradável"));
        String statusRio = rioM3s >= 175.0 ? "ALERTA ALAGAÇÃO" : (rioM3s < 95.0 ? "ESTIAGEM / SECA" : "Nível Normal");
        String corTemp = tempMax >= 30.0 ? "#F97316" : (tempMax <= 19.0 ? "#38BDF8" : "#F59E0B");

        aplicarAtmosferaTemperatura(tempMax, chuvaMm);

        listaPrevisaoContainers.getChildren().setAll(
                criarContainerPrevisao("CLIMA • TEMPERATURA", FormatadorDadosUI.temperatura(tempMax), condicao, corTemp),
                criarContainerPrevisao("CLIMA • PRECIPITAÇÃO", FormatadorDadosUI.milimetros(chuvaMm),
                        chuvaMm >= 10.0 ? "Atenção com umidade e entregas" : "Tempo firme para entregas", "#38BDF8"),
                criarContainerPrevisao("HIDROLOGIA • RIO", FormatadorDadosUI.vazaoRio(rioM3s), statusRio,
                        rioM3s >= 175.0 ? "#F87171" : "#2DD4BF"),
                criarContainerPrevisao("SINCRONIZAÇÃO", dadoClimaticoReal ? "Sensor Ativo" : "Previsão Base",
                        dadoClimaticoReal ? "Dados reais das últimas notas" : "Aguardando nova nota regional", "#A78BFA")
        );

        String orientacaoHumana = tempMax >= 30.0
                ? "Hoje faz calor (" + FormatadorDadosUI.temperatura(tempMax) + ") — ótimo momento para itens de giro rápido, mas mantenha perecíveis bem refrigerados."
                : (tempMax <= 19.0
                ? "Temperatura mais amena (" + FormatadorDadosUI.temperatura(tempMax) + ") — clima favorável à conservação do estoque e maior demanda por itens de conforto."
                : "Clima confortável (" + FormatadorDadosUI.temperatura(tempMax) + ") — condição equilibrada para vendas e conservação dos lotes.");
        lblPrevisaoResumo.setText(orientacaoHumana);

        // 4. Estratégia Urgente (lista de contêineres empilhados, 100% humanizados)
        listaEstrategiaUrgente.getChildren().clear();
        if (respDestaques != null && respDestaques.sucesso() && respDestaques.dados() instanceof List<?> destaques && !destaques.isEmpty()) {
            int max = Math.min(3, destaques.size());
            for (int i = 0; i < max; i++) {
                if (destaques.get(i) instanceof Object[] r && r.length >= 7) {
                    adicionarContainerItem(listaEstrategiaUrgente,
                            "PICO SAZONAL",
                            FormatadorDadosUI.texto(r[0], "Produto"),
                            "Demanda em alta prevista para: " + FormatadorDadosUI.texto(r[6], "Período sazonal"),
                            "#2DD4BF");
                }
            }
        } else {
            adicionarContainerItem(listaEstrategiaUrgente,
                    "ESTRATÉGIA",
                    "Antecipar estoque sazonal",
                    "Reforçar reposição antes de picos de Eventos Locais e 5º Dia Útil.",
                    "#2DD4BF");
        }
        if (respPlano != null && respPlano.sucesso() && respPlano.dados() instanceof List<?> planoLista && !planoLista.isEmpty()) {
            int maxPlano = Math.min(4, planoLista.size());
            for (int i = 0; i < maxPlano; i++) {
                Object itemObj = planoLista.get(i);
                if (itemObj instanceof Map<?, ?> m) {
                    String prod = FormatadorDadosUI.texto(m.get("nome_produto"), "Produto");
                    String qtdRec = FormatadorDadosUI.quantidade(m.get("quantidade_recomendada_compra"));
                    String dataIdeal = FormatadorDadosUI.data(m.get("data_ideal_compra"), "Em breve");
                    String dataPico = FormatadorDadosUI.data(m.get("data_proximo_pico_venda"), "Próximo ciclo");
                    String forn = FormatadorDadosUI.texto(m.get("nome_fornecedor_campeao"), "Fornecedor padrão");
                    String precoSug = FormatadorDadosUI.moeda(m.get("preco_venda_estrategico_bacen"));
                    String custoEfetivo = FormatadorDadosUI.moeda(m.get("custo_efetivo_qualidade"));

                    String detalheHumanizado = String.format(
                            "Comprar %s un. até %s (para pico em %s) • Fornecedor: %s • Custo efetivo: %s • Venda sugerida: %s",
                            qtdRec, dataIdeal, dataPico, forn, custoEfetivo, precoSug);

                    adicionarContainerItem(listaEstrategiaUrgente,
                            "PLANO DE COMPRA",
                            prod,
                            detalheHumanizado,
                            "#38BDF8");
                } else {
                    String resumido = FormatadorDadosUI.formatarRegistroArray(itemObj);
                    if (!resumido.equals("-")) {
                        adicionarContainerItem(listaEstrategiaUrgente,
                                "PLANO DE COMPRA",
                                "Sugestão de reposição",
                                resumido,
                                "#38BDF8");
                    }
                }
            }
        }

        // 5. Alertas Urgentes (lista de contêineres: validade 15 dias + perdas + clima/rio)
        listaAlertasUrgentes.getChildren().clear();
        int totalAlertas = 0;

        if (respLotesVenc != null && respLotesVenc.sucesso() && respLotesVenc.dados() instanceof List<?> lotes && !lotes.isEmpty()) {
            totalAlertas += lotes.size();
            adicionarContainerItem(listaAlertasUrgentes,
                    "CRÍTICO • 15 DIAS",
                    lotes.size() + " lote(s) FIFO próximos da validade",
                    "Priorizar venda imediata ou promoção de giro rápido.",
                    "#F87171");
            int max = Math.min(2, lotes.size());
            for (int i = 0; i < max; i++) {
                if (lotes.get(i) instanceof Item<?> it) {
                    String prodNome = it.getProduto() != null ? FormatadorDadosUI.texto(it.getProduto().getNome(), "Produto") : "Produto";
                    adicionarContainerItem(listaAlertasUrgentes,
                            "LOTE #" + it.getId(),
                            prodNome,
                            "Qtd: " + FormatadorDadosUI.quantidade(it.getQuantidadeDisponivel())
                                    + " • Validade: " + FormatadorDadosUI.data(it.getDataValidade(), "-"),
                            "#FCA5A5");
                }
            }
        }
        if (tempMax >= 32.0 || rioM3s >= 175.0) {
            totalAlertas++;
            adicionarContainerItem(listaAlertasUrgentes,
                    "RISCO CLIMA/RIO",
                    condicao + " • " + statusRio,
                    "Proteja estoque perecível e revise rotas de entrega.",
                    "#F59E0B");
        }
        if (respMotivos != null && respMotivos.sucesso() && respMotivos.dados() instanceof List<?> motivos && !motivos.isEmpty()) {
            totalAlertas += motivos.size();
            for (int i = 0; i < Math.min(2, motivos.size()); i++) {
                if (motivos.get(i) instanceof Object[] r && r.length >= 2) {
                    adicionarContainerItem(listaAlertasUrgentes,
                            "PERDA RECORRENTE",
                            FormatadorDadosUI.texto(r[0], "Motivo"),
                            FormatadorDadosUI.quantidade(r[1]) + " ocorrência(s) registrada(s)",
                            "#FBBF24");
                }
            }
        }
        if (totalAlertas == 0) {
            adicionarContainerItem(listaAlertasUrgentes,
                    "ESTÁVEL",
                    "Nenhum alerta crítico no momento",
                    "Lotes FIFO, validade e clima sob controle operacional.",
                    "#2DD4BF");
        }
        kpiAlertas.atualizar(String.valueOf(totalAlertas), totalAlertas > 0 ? "Requerem atenção" : "Operação estável");
    }

    private void aplicarAtmosferaTemperatura(double tempMax, double chuvaMm) {
        if (cardPrevisao == null) return;
        String gradiente;
        String borda;
        if (tempMax >= 30.0) {
            gradiente = "linear-gradient(to right, rgba(249, 115, 22, 0.16), rgba(245, 158, 11, 0.08))";
            borda = "rgba(249, 115, 22, 0.45)";
        } else if (tempMax <= 19.0 || chuvaMm >= 10.0) {
            gradiente = "linear-gradient(to right, rgba(56, 189, 248, 0.16), rgba(99, 102, 241, 0.08))";
            borda = "rgba(56, 189, 248, 0.45)";
        } else {
            gradiente = "linear-gradient(to right, rgba(45, 212, 191, 0.12), rgba(245, 158, 11, 0.08))";
            borda = "rgba(45, 212, 191, 0.40)";
        }
        cardPrevisao.setStyle("-fx-background-color: " + gradiente + "; -fx-background-radius: 12; "
                + "-fx-border-color: " + borda + "; -fx-border-radius: 12; -fx-border-width: 1;");
    }

    private VBox criarContainerPrevisao(String badge, String valorPrincipal, String detalhe, String corHex) {
        VBox box = new VBox(5);
        box.setMinWidth(205);
        box.setPrefWidth(225);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle("-fx-background-color: rgba(15, 23, 42, 0.62); -fx-background-radius: 10; "
                + "-fx-border-color: " + corHex + "44; -fx-border-radius: 10; -fx-border-width: 1 1 1 3;");

        Label lblBadge = new Label(badge);
        lblBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + corHex + ";");
        Label lblValor = new Label(FormatadorDadosUI.texto(valorPrincipal, "-"));
        lblValor.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");
        Label lblDet = new Label(FormatadorDadosUI.texto(detalhe, "-"));
        lblDet.setWrapText(true);
        lblDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1;");

        box.getChildren().addAll(lblBadge, lblValor, lblDet);
        return box;
    }

    private void adicionarContainerItem(VBox destino, String badge, String titulo, String detalhe, String corHex) {
        VBox item = new VBox(4);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setPadding(new Insets(11, 14, 11, 14));
        item.setStyle("-fx-background-color: rgba(15, 23, 42, 0.56); -fx-background-radius: 10; "
                + "-fx-border-color: " + corHex + "44; -fx-border-radius: 10; -fx-border-width: 1 1 1 3;");

        Label lblBadge = new Label(FormatadorDadosUI.texto(badge, "INFO"));
        lblBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + corHex + ";");

        Label lblTitulo = new Label(FormatadorDadosUI.texto(titulo, "-"));
        lblTitulo.setWrapText(true);
        lblTitulo.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label lblDet = new Label(FormatadorDadosUI.texto(detalhe, "-"));
        lblDet.setWrapText(true);
        lblDet.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1;");

        item.getChildren().addAll(lblBadge, lblTitulo, lblDet);
        destino.getChildren().add(item);
    }
}
