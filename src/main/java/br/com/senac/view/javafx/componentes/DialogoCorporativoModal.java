package br.com.senac.view.javafx.componentes;

import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.nota.NotaDevolucao;
import br.com.senac.repository.modal.nota.NotaPerda;
import br.com.senac.repository.modal.nota.NotaVenda;
import br.com.senac.repository.modal.nota.VinculoFatorExternoNota;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAPrazo;
import br.com.senac.repository.modal.pagamento.PagamentoAVista;
import br.com.senac.repository.modal.pagamento.PagamentoParcelado;
import br.com.senac.repository.modal.pagamento.ParcelaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gerenciador central de Cards Modais Corporativos:
 * - Confirmação interativa antes de enviar análises ao Assistente IA;
 * - Confirmação explícita de ações manuais em todo o sistema;
 * - Exibição de Cards completos de detalhes ao clicar em qualquer registro;
 * - Comprovante visual de Notas (Venda, Entrada/Compra, Devolução, Perda e Pagamentos) com impressão e exportação.
 */
public final class DialogoCorporativoModal {

    private DialogoCorporativoModal() {
    }

    /**
     * Abre popup antes de acionar a IA perguntando se o usuário deseja que o Assistente explique/analise os dados.
     */
    public static void confirmarEnvioIA(
            Node owner,
            String tituloContexto,
            String resumoDados,
            String promptSugerido,
            Consumer<String> aoConfirmarPrompt) {

        Stage dialog = criarStageModal(owner, "Assistente IA — Confirmação de Análise", 560, 440);

        VBox header = criarCabecalhoModal(
                "🤖",
                "Deseja que o Assistente IA explique este contexto?",
                FormatadorSeguroUI.tratarParaExibicao(tituloContexto, "Análise sob demanda"),
                "#0F172A"
        );

        Label lblPergunta = FormatadorSeguroUI.criarLabel(
                "Revise o resumo abaixo. Você pode confirmar o envio imediato para a IA explicar ou ajustar a pergunta:",
                12, false, "#334155");

        VBox boxResumo = new VBox(4,
                FormatadorSeguroUI.criarLabel("Contexto selecionado na tela:", 11, true, "#64748B"),
                FormatadorSeguroUI.criarLabel(FormatadorSeguroUI.tratarParaExibicao(resumoDados, tituloContexto), 12, true, "#0F172A"));
        boxResumo.setPadding(new Insets(10, 12, 10, 12));
        boxResumo.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));

        Label lblEdit = FormatadorSeguroUI.criarLabel("Instrução que será enviada ao Assistente (editável):", 11, true, "#475569");
        TextArea txtPrompt = FormatadorSeguroUI.criarAreaTextoCartoon("Digite ou ajuste a solicitação...", "#CBD5E1", "#0F172A");
        txtPrompt.setText(promptSugerido != null ? promptSugerido : "");
        txtPrompt.setPrefRowCount(4);

        Button btnCancelar = FormatadorSeguroUI.criarBotaoCartoon("Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnCancelar.setOnAction(e -> dialog.close());

        Button btnConfirmar = FormatadorSeguroUI.criarBotaoCartoon("✔ Sim, Explicar com IA", "#1D4ED8", "#1E40AF", "#FFFFFF");
        btnConfirmar.setOnAction(e -> {
            String finalPrompt = txtPrompt.getText() != null ? txtPrompt.getText().trim() : "";
            dialog.close();
            if (aoConfirmarPrompt != null && !finalPrompt.isBlank()) {
                aoConfirmarPrompt.accept(finalPrompt);
            }
        });

        HBox rodape = new HBox(10, btnCancelar, btnConfirmar);
        rodape.setAlignment(Pos.CENTER_RIGHT);

        VBox corpo = new VBox(10, lblPergunta, boxResumo, lblEdit, txtPrompt);
        corpo.setPadding(new Insets(14, 16, 10, 16));
        VBox.setVgrow(txtPrompt, Priority.ALWAYS);

        VBox root = montarContainerModal(header, corpo, rodape);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    /**
     * Exibe Card Modal de Confirmação antes de efetivar qualquer ação manual do usuário no sistema.
     */
    public static void confirmarAcaoManual(
            Node owner,
            String tituloAcao,
            String descricaoResumo,
            Map<String, String> camposChaveValor,
            Runnable aoConfirmar) {

        Stage dialog = criarStageModal(owner, "Confirmação de Operação — Sazonalisei ERP", 560, 420);

        VBox header = criarCabecalhoModal(
                "🛡️",
                FormatadorSeguroUI.tratarParaExibicao(tituloAcao, "Confirmar Operação"),
                "Confira os dados antes de gravar no banco de dados",
                "#0F172A"
        );

        VBox corpo = new VBox(10);
        corpo.setPadding(new Insets(14, 16, 10, 16));
        if (descricaoResumo != null && !descricaoResumo.isBlank()) {
            corpo.getChildren().add(FormatadorSeguroUI.criarLabel(descricaoResumo, 12, false, "#334155"));
        }
        if (camposChaveValor != null && !camposChaveValor.isEmpty()) {
            corpo.getChildren().add(FormatadorSeguroUI.criarGradeMetadadosCards(camposChaveValor));
        }

        ScrollPane scroll = FormatadorSeguroUI.envolverScrollSuave(corpo);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button btnCancelar = FormatadorSeguroUI.criarBotaoCartoon("✕ Revisar / Cancelar", "#F1F5F9", "#CBD5E1", "#334155");
        btnCancelar.setOnAction(e -> dialog.close());

        Button btnConfirmar = FormatadorSeguroUI.criarBotaoCartoon("✔ Confirmar e Gravar", "#047857", "#065F46", "#FFFFFF");
        btnConfirmar.setOnAction(e -> {
            dialog.close();
            if (aoConfirmar != null) {
                aoConfirmar.run();
            }
        });

        HBox rodape = new HBox(10, btnCancelar, btnConfirmar);
        rodape.setAlignment(Pos.CENTER_RIGHT);

        VBox root = montarContainerModal(header, scroll, rodape);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    /**
     * Abre Card Modal rico com todas as informações formatadas ao clicar em qualquer registro ou KPI card.
     */
    public static void exibirCardDetalhes(
            Node owner,
            String icone,
            String titulo,
            String badgeTexto,
            String corBadgeHex,
            Map<String, String> metadadosFormatados,
            Node conteudoExtraOpcional,
            Runnable acaoImprimirOpcional,
            Runnable acaoIAOpcional) {
        exibirCardDetalhes(owner, icone, titulo, badgeTexto, corBadgeHex, metadadosFormatados,
                conteudoExtraOpcional, acaoImprimirOpcional, null, null, acaoIAOpcional);
    }

    /**
     * Abre Card Modal rico com botão opcional de navegação direta para a aba onde resolver o item.
     */
    public static void exibirCardDetalhes(
            Node owner,
            String icone,
            String titulo,
            String badgeTexto,
            String corBadgeHex,
            Map<String, String> metadadosFormatados,
            Node conteudoExtraOpcional,
            Runnable acaoImprimirOpcional,
            String rotuloAcaoExtra,
            Runnable acaoExtra,
            Runnable acaoIAOpcional) {

        Stage dialog = criarStageModal(owner, FormatadorSeguroUI.tratarParaExibicao(titulo, "Detalhes do Registro"), 730, 580);

        VBox header = criarCabecalhoModal(
                icone != null ? icone : "📋",
                FormatadorSeguroUI.tratarParaExibicao(titulo, "Ficha Completa"),
                badgeTexto != null ? badgeTexto : "Inspeção detalhada",
                corBadgeHex != null ? corBadgeHex : "#0F172A"
        );

        VBox corpo = new VBox(12);
        corpo.setPadding(new Insets(14, 16, 12, 16));
        corpo.setMaxWidth(680);
        if (metadadosFormatados != null && !metadadosFormatados.isEmpty()) {
            corpo.getChildren().add(FormatadorSeguroUI.criarGradeMetadadosCards(metadadosFormatados));
        }
        if (conteudoExtraOpcional != null) {
            corpo.getChildren().add(conteudoExtraOpcional);
        }

        ScrollPane scroll = FormatadorSeguroUI.envolverScrollSuave(corpo);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        HBox rodape = new HBox(8);
        rodape.setAlignment(Pos.CENTER_RIGHT);

        Label lblFeedbackPrint = FormatadorSeguroUI.criarLabel("", 11, true, "#047857");
        lblFeedbackPrint.setWrapText(true);
        HBox.setHgrow(lblFeedbackPrint, Priority.ALWAYS);
        rodape.getChildren().add(lblFeedbackPrint);

        if (rotuloAcaoExtra != null && !rotuloAcaoExtra.isBlank() && acaoExtra != null) {
            Button btnExtra = FormatadorSeguroUI.criarBotaoCartoon(rotuloAcaoExtra, "#EFF6FF", "#2563EB", "#1E3A8A");
            btnExtra.setOnAction(e -> {
                dialog.close();
                acaoExtra.run();
            });
            rodape.getChildren().add(btnExtra);
        }

        String htmlFicha = gerarHtmlFichaDetalhes(titulo, badgeTexto, metadadosFormatados);
        if (acaoImprimirOpcional != null) {
            Button btnPrint = FormatadorSeguroUI.criarBotaoCartoon("🖨️ Imprimir Ficha A4", "#FFFFFF", "#CBD5E1", "#0F172A");
            btnPrint.setOnAction(e -> {
                try {
                    acaoImprimirOpcional.run();
                } catch (Exception ex) {
                    lblFeedbackPrint.setText("⚠️ Erro na impressão: " + ex.getMessage());
                }
            });
            rodape.getChildren().add(btnPrint);
        } else {
            Button btnPrintFicha = FormatadorSeguroUI.criarBotaoCartoon("🖨️ Imprimir Ficha A4", "#FFFFFF", "#CBD5E1", "#0F172A");
            btnPrintFicha.setOnAction(e -> imprimirOuExportarNode(dialog, corpo, "ficha_registro", htmlFicha, lblFeedbackPrint));
            rodape.getChildren().add(btnPrintFicha);
        }

        if (acaoIAOpcional != null) {
            Button btnIA = FormatadorSeguroUI.criarBotaoIA("🤖 Analisar com IA", () -> {
                dialog.close();
                acaoIAOpcional.run();
            });
            rodape.getChildren().add(btnIA);
        }

        Button btnFechar = FormatadorSeguroUI.criarBotaoCartoon("Fechar / Voltar", "#0F172A", "#0F172A", "#FFFFFF");
        btnFechar.setOnAction(e -> dialog.close());
        rodape.getChildren().add(btnFechar);

        VBox root = montarContainerModal(header, scroll, rodape);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    /**
     * Exibe o Card Corporativo Completo da Nota emitida com todos os itens, pagamentos/parcelas e opção de impressão.
     */
    public static void exibirComprovanteNotaImprimivel(Node owner, Nota nota, String tituloOperacao) {
        if (nota == null) return;
        exibirComprovanteLoteNotasImprimivel(owner, List.of(nota), tituloOperacao);
    }

    /**
     * Exibe uma ou várias Notas geradas (ex.: lote de Notas de Entrada + suas Notas de Pagamento)
     * em abas corporativas limpas com impressão direta e exportação HTML.
     */
    public static void exibirComprovanteLoteNotasImprimivel(Node owner, List<? extends Nota> notas, String tituloLote) {
        if (notas == null || notas.isEmpty()) return;

        String tituloJanela = notas.size() == 1
                ? "Comprovante Fiscal/Operacional — Nota #" + notas.get(0).getId()
                : "Comprovante em Lote (" + notas.size() + " Notas) — " + FormatadorSeguroUI.tratarParaExibicao(tituloLote, "Operações");

        Stage dialog = criarStageModal(owner, tituloJanela, 740, 620);

        VBox header = criarCabecalhoModal(
                "🧾",
                FormatadorSeguroUI.tratarParaExibicao(tituloLote, "Documento Operacional Gerado"),
                notas.size() == 1 ? "Nota #" + notas.get(0).getId() + " emitida e sincronizada" : notas.size() + " notas emitidas com sucesso",
                "#0F172A"
        );

        Node conteudoCentral;
        VBox[] primeiroDocumentoImpressao = new VBox[1];

        if (notas.size() == 1) {
            VBox doc = construirCorpoComprovanteNota(notas.get(0));
            primeiroDocumentoImpressao[0] = doc;
            conteudoCentral = FormatadorSeguroUI.envolverScrollSuave(doc);
        } else {
            TabPane tp = FormatadorSeguroUI.criarTabPaneCorporativo();
            VBox dossieUnificado = new VBox(14);
            dossieUnificado.setPadding(new Insets(10));
            dossieUnificado.setMaxWidth(565);
            for (Nota n : notas) {
                dossieUnificado.getChildren().add(construirCorpoComprovanteNota(n));
            }
            primeiroDocumentoImpressao[0] = dossieUnificado;
            Tab tabUnificada = new Tab("📄 Dossiê Unificado (" + notas.size() + " Notas)");
            tabUnificada.setContent(FormatadorSeguroUI.envolverScrollSuave(dossieUnificado));
            tp.getTabs().add(tabUnificada);

            for (Nota n : notas) {
                VBox docNota = construirCorpoComprovanteNota(n);
                Tab t = new Tab(traduzirTipoNota(n) + " #" + (n.getId() != null ? n.getId() : "Nova"));
                t.setContent(FormatadorSeguroUI.envolverScrollSuave(docNota));
                tp.getTabs().add(t);
            }
            conteudoCentral = tp;
        }

        if (conteudoCentral instanceof Region r) {
            VBox.setVgrow(r, Priority.ALWAYS);
        }

        Label lblFeedbackPrint = FormatadorSeguroUI.criarLabel("", 11, true, "#047857");
        HBox.setHgrow(lblFeedbackPrint, Priority.ALWAYS);

        Button btnImprimir = FormatadorSeguroUI.criarBotaoCartoon("🖨️ Imprimir Comprovante", "#047857", "#065F46", "#FFFFFF");
        btnImprimir.setOnAction(e -> {
            VBox alvo = primeiroDocumentoImpressao[0];
            if (conteudoCentral instanceof TabPane tp && tp.getSelectionModel().getSelectedItem() != null) {
                Node selContent = tp.getSelectionModel().getSelectedItem().getContent();
                if (selContent instanceof ScrollPane sp && sp.getContent() instanceof VBox vb) {
                    alvo = vb;
                }
            }
            Nota notaAlvo = notas.get(0);
            if (conteudoCentral instanceof TabPane tp) {
                int idx = tp.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < notas.size()) notaAlvo = notas.get(idx);
            }
            String arqNome = "nota_" + (notaAlvo.getId() != null ? notaAlvo.getId() : System.currentTimeMillis());
            imprimirOuExportarNode(dialog, alvo, arqNome, gerarHtmlComprovante(notas), lblFeedbackPrint);
        });

        Button btnExportarHtml = FormatadorSeguroUI.criarBotaoCartoon("📄 Exportar HTML/Recibo", "#EFF6FF", "#93C5FD", "#1E40AF");
        btnExportarHtml.setOnAction(e -> {
            Path p = salvarComprovanteHtml(gerarHtmlComprovante(notas), "comprovante_notas");
            if (p != null) {
                lblFeedbackPrint.setText("✔ Recibo salvo em: " + p.toString());
            } else {
                lblFeedbackPrint.setText("⚠️ Não foi possível salvar o arquivo HTML.");
            }
        });

        Button btnFechar = FormatadorSeguroUI.criarBotaoCartoon("Fechar", "#0F172A", "#0F172A", "#FFFFFF");
        btnFechar.setOnAction(e -> dialog.close());

        HBox rodape = new HBox(8, lblFeedbackPrint, btnExportarHtml, btnImprimir, btnFechar);
        rodape.setAlignment(Pos.CENTER_RIGHT);

        VBox root = montarContainerModal(header, conteudoCentral, rodape);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    private static VBox construirCorpoComprovanteNota(Nota nota) {
        VBox doc = new VBox(10);
        doc.setPadding(new Insets(16));
        doc.setPrefWidth(545);
        doc.setMaxWidth(560); // Largura segura A4 com margens de 12mm sem estouro horizontal
        doc.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;");

        String tipo = traduzirTipoNota(nota);
        String pessoa = nota.getPessoa() != null ? nota.getPessoa().getNome() : "Operação Interna / Consumidor Final";

        Label titLoja = FormatadorSeguroUI.criarLabel("SAZONALISEI COMÉRCIO & GESTÃO SAZONAL", 14, true, "#0F172A");
        titLoja.setWrapText(true);
        Label subNota = FormatadorSeguroUI.criarLabel(
                String.format("%s #%s • Emissão: %s", tipo,
                        nota.getId() != null ? nota.getId().toString() : "Nova",
                        FormatadorSeguroUI.formatarDataHoraAmigavel(nota.getDataGerada())),
                12, true, "#1D4ED8");
        subNota.setWrapText(true);

        Label lblEntidade = FormatadorSeguroUI.criarLabel("Entidade: " + pessoa, 12, false, "#334155");
        lblEntidade.setWrapText(true);
        Label lblObs = FormatadorSeguroUI.criarLabel("Observação / Motivo: " + FormatadorSeguroUI.tratarParaExibicao(nota.getDescricao(), "Operação regular"), 11, false, "#64748B");
        lblObs.setWrapText(true);

        VBox cab = new VBox(2, titLoja, subNota, lblEntidade, lblObs);

        // Vínculos encadeados (Pós-Venda: Venda -> Devolução -> Perda -> Entrada)
        VBox boxCadeia = montarBlocoCadeiaNota(nota);

        VBox boxItens = new VBox(4);
        boxItens.setPadding(new Insets(8));
        boxItens.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
        boxItens.getChildren().add(FormatadorSeguroUI.criarLabel("📦 Itens Movimentados", 12, true, "#0F172A"));

        if (nota.getItens() != null && !nota.getItens().isEmpty()) {
            for (Item<? extends Produto> it : nota.getItens()) {
                Produto prod = it.getProduto();
                String nomeProd = prod != null ? FormatadorSeguroUI.limparNomeProduto(prod.getNome()) : "Mercadoria";
                String um = prod != null && prod.getUm() != null ? prod.getUm().getSigla() : "UN";
                String valTxt = it.getDataValidade() != null ? " • Val: " + FormatadorSeguroUI.formatarDataAmigavel(it.getDataValidade()) : "";
                String loteRef = it.getItemOrigem() != null && it.getItemOrigem().getId() != null
                        ? " • Lote #" + it.getItemOrigem().getId()
                        : (it.getId() != null && nota instanceof NotaCompra ? " • Lote gerado #" + it.getId() : "");

                Label lNome = FormatadorSeguroUI.criarLabel(nomeProd + loteRef + valTxt, 11, true, "#0F172A");
                lNome.setWrapText(true);
                lNome.setMaxWidth(315);
                HBox.setHgrow(lNome, Priority.ALWAYS);
                Label lValores = FormatadorSeguroUI.criarLabel(String.format("%s × %s = %s",
                        FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), um),
                        FormatadorSeguroUI.formatarMoeda(it.getValor()),
                        FormatadorSeguroUI.formatarMoeda(it.getSubtotal())), 11, false, "#334155");
                lValores.setWrapText(true);
                HBox row = new HBox(8, lNome, lValores);
                row.setAlignment(Pos.CENTER_LEFT);
                boxItens.getChildren().add(row);
            }
        } else {
            boxItens.getChildren().add(FormatadorSeguroUI.criarLabel("Sem itens detalhados vinculados.", 11, false, "#64748B"));
        }

        VBox boxPagamentos = new VBox(4);
        boxPagamentos.setPadding(new Insets(8));
        boxPagamentos.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#F8FAFC", "#E2E8F0"));
        boxPagamentos.getChildren().add(FormatadorSeguroUI.criarLabel("💳 Notas de Pagamento & Liquidação", 12, true, "#0F172A"));

        if (nota.getPagamentos() != null && !nota.getPagamentos().isEmpty()) {
            for (NotaPagamento np : nota.getPagamentos()) {
                String forma = np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "Pagamento";
                String mod = (np instanceof PagamentoParcelado) ? "Parcelado"
                        : (np instanceof PagamentoAPrazo ? "A Prazo" : "À Vista");
                String st = np.isLiquidado() ? "QUITADO" : "EM ABERTO";
                Label lPg = FormatadorSeguroUI.criarLabel(
                        String.format("%s (%s) • Valor Final: %s • %s",
                                forma, mod, FormatadorSeguroUI.formatarMoeda(np.calcularValorFinal()), st),
                        11, true, np.isLiquidado() ? "#047857" : "#B45309");
                lPg.setWrapText(true);
                boxPagamentos.getChildren().add(lPg);

                if (np instanceof PagamentoAVista vista && vista.calcularTroco().compareTo(BigDecimal.ZERO) > 0) {
                    Label lT = FormatadorSeguroUI.criarLabel(
                            "   ↳ Recebido: " + FormatadorSeguroUI.formatarMoeda(vista.getValorRecebido())
                                    + " | Troco: " + FormatadorSeguroUI.formatarMoeda(vista.calcularTroco()),
                            11, false, "#475569");
                    lT.setWrapText(true);
                    boxPagamentos.getChildren().add(lT);
                } else if (np instanceof PagamentoAPrazo prazo) {
                    Label lPr = FormatadorSeguroUI.criarLabel(
                            "   ↳ Vencimento: " + FormatadorSeguroUI.formatarDataAmigavel(prazo.getDataVencimento()),
                            11, false, "#475569");
                    lPr.setWrapText(true);
                    boxPagamentos.getChildren().add(lPr);
                } else if (np instanceof PagamentoParcelado parc && parc.getParcelas() != null) {
                    for (ParcelaPagamento p : parc.getParcelas()) {
                        Label lPa = FormatadorSeguroUI.criarLabel(
                                String.format("   ↳ Parcela %d/%d: %s • Venc.: %s • %s",
                                        p.getNumeroParcela(), parc.getParcelas().size(),
                                        FormatadorSeguroUI.formatarMoeda(p.getValorTotalParcela()),
                                        FormatadorSeguroUI.formatarDataAmigavel(p.getDataVencimento()),
                                        p.getDataQuitacao() != null ? "Paga" : "Pendente"),
                                11, false, "#475569");
                        lPa.setWrapText(true);
                        boxPagamentos.getChildren().add(lPa);
                    }
                }
            }
        } else {
            boxPagamentos.getChildren().add(FormatadorSeguroUI.criarLabel("Sem lançamentos financeiros acoplados.", 11, false, "#64748B"));
        }

        // Eventos externos, clima e potenciais motivos associados à nota
        VBox boxEventos = montarBlocoEventosExternosNota(nota);

        HBox totais = new HBox(10,
                FormatadorSeguroUI.criarMiniCardInfo("Valor Bruto", FormatadorSeguroUI.formatarMoeda(nota.getValorBruto()), "#F8FAFC", "#CBD5E1", "#334155"),
                FormatadorSeguroUI.criarMiniCardInfo("Descontos", FormatadorSeguroUI.formatarMoeda(nota.getDescontoGlobal()), "#F8FAFC", "#CBD5E1", "#B45309"),
                FormatadorSeguroUI.criarMiniCardInfo("TOTAL DA NOTA", FormatadorSeguroUI.formatarMoeda(nota.getValorTotal()), "#ECFDF5", "#6EE7B7", "#047857")
        );
        for (Node n : totais.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        doc.getChildren().add(cab);
        if (boxCadeia != null) doc.getChildren().add(boxCadeia);
        doc.getChildren().addAll(new Separator(), boxItens, boxPagamentos);
        if (boxEventos != null) doc.getChildren().add(boxEventos);
        doc.getChildren().add(totais);
        return doc;
    }

    private static VBox montarBlocoCadeiaNota(Nota nota) {
        List<String> linhas = new ArrayList<>();
        if (nota instanceof NotaDevolucao dev && dev.getNotaVenda() != null) {
            linhas.add("• Origem Pós-Venda: Nota de Venda #" + dev.getNotaVenda().getId()
                    + (dev.getMotivo() != null ? " — Motivo: " + FormatadorSeguroUI.tratarParaExibicao(dev.getMotivo(), "-") : ""));
        } else if (nota instanceof NotaPerda np) {
            if (np.getNotaDevolucao() != null) {
                linhas.add("• Fluxo Pós-Venda Encadeado: Nota de Devolução #" + np.getNotaDevolucao().getId()
                        + (np.getNotaDevolucao().getNotaVenda() != null
                        ? " ← Venda #" + np.getNotaDevolucao().getNotaVenda().getId() : ""));
            }
            if (np.getNotaCompraOrigem() != null) {
                linhas.add("• Nota de Entrada Original: Compra #" + np.getNotaCompraOrigem().getId());
            }
            if (np.getNotaCompraAbatimento() != null) {
                linhas.add("• Nota de Abatimento Fornecedor: Compra #" + np.getNotaCompraAbatimento().getId());
            }
            linhas.add("• Enquadramento Fiscal/Contratual: "
                    + (np.isPerdaDentroDaValidade() ? "Dentro da validade (elegível a crédito/abatimento)" : "Perda interna operacional"));
        }
        if (linhas.isEmpty()) return null;

        VBox box = new VBox(3);
        box.setPadding(new Insets(8));
        box.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#FFFBEB", "#FCD34D"));
        box.getChildren().add(FormatadorSeguroUI.criarLabel("🔗 Rastreamento Encadeado da Operação", 11, true, "#92400E"));
        for (String l : linhas) {
            Label lbl = FormatadorSeguroUI.criarLabel(l, 11, false, "#78350F");
            lbl.setWrapText(true);
            box.getChildren().add(lbl);
        }
        return box;
    }

    private static VBox montarBlocoEventosExternosNota(Nota nota) {
        VinculoFatorExternoNota v = nota.getVinculoFatorExterno();
        if (v == null) return null;

        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.setStyle(FormatadorSeguroUI.estiloCartaoSuaveInterno("#EFF6FF", "#93C5FD"));
        box.getChildren().add(FormatadorSeguroUI.criarLabel("🌦️ Eventos Externos Relacionados, Clima & Potenciais Motivos", 11, true, "#1E40AF"));

        var clima = v.getClimaRegistrado();
        var rio = v.getNivelRioRegistrado();
        String padraoAmigavel = traduzirPadraoConsumoAmigavel(v.determinarPadraoConsumo());
        String resumoFator = String.format(FormatadorSeguroUI.LOCALE_BR,
                "%s • Temp %.1f°C • Chuva %.1fmm • Vazão Rio %.0f m³/s • Eventos próximos: %d",
                padraoAmigavel, clima.temperaturaMedia(), clima.precipitacaoMm(), rio.vazaoM3s(), v.getTotalEventosAtivos());
        Label lFator = FormatadorSeguroUI.criarLabel(resumoFator, 11, true, "#1E3A8A");
        lFator.setWrapText(true);
        box.getChildren().add(lFator);

        if (v.getResumoCalendario() != null && !v.getResumoCalendario().isBlank()) {
            Label lCal = FormatadorSeguroUI.criarLabel("Calendário Comercial: " + v.getResumoCalendario(), 11, false, "#334155");
            lCal.setWrapText(true);
            box.getChildren().add(lCal);
        }

        String eventosLimpos = resumirTextoEventosJson(v.getResumoEventosJson());
        if (!eventosLimpos.isBlank()) {
            Label lEv = FormatadorSeguroUI.criarLabel("Eventos/Ocorrências Regionais: " + eventosLimpos, 11, false, "#1E40AF");
            lEv.setWrapText(true);
            box.getChildren().add(lEv);
        }

        String potencialMotivo = inferirPotencialMotivoComercial(nota, v);
        Label lMotivo = FormatadorSeguroUI.criarLabel("Potenciais Motivos Mapeados: " + potencialMotivo, 11, false, "#334155");
        lMotivo.setWrapText(true);
        box.getChildren().add(lMotivo);
        return box;
    }

    private static String traduzirPadraoConsumoAmigavel(String raw) {
        if (raw == null || raw.isBlank()) return "Padrão Sazonal Regular";
        return raw.replace("CHUVOSO_DELIVERY_ALTO", "Chuva / Alta em Entregas")
                .replace("CALOR_ALTA_DEMANDA_BEBIDAS", "Calor Intenso / Bebidas & Gelo")
                .replace("CLIMA_ESTAVEL", "Clima Estável")
                .replace("PICO_5_DIA_UTIL", "Pico Salarial (5º Dia Útil)")
                .replace("FERIADO_OU_FIM_DE_SEMANA", "Feriado ou Fim de Semana")
                .replace("DIA_UTIL", "Dia Útil")
                .replace("EVENTOS_ATIVOS", "Eventos Próximos");
    }

    private static String resumirTextoEventosJson(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) return "";
        String limpo = raw.replace("[", "").replace("]", "")
                .replace("{", "").replace("}", "")
                .replace("\"", "").replace("titulo:", "")
                .replace("nome:", "").replace("tipo:", "")
                .replaceAll("\\s+", " ").trim();
        return limpo.length() > 260 ? limpo.substring(0, 257) + "..." : limpo;
    }

    private static String inferirPotencialMotivoComercial(Nota nota, VinculoFatorExternoNota v) {
        var clima = v.getClimaRegistrado();
        String padrao = traduzirPadraoConsumoAmigavel(v.determinarPadraoConsumo());
        if (nota instanceof NotaPerda || nota instanceof NotaDevolucao) {
            if (clima.precipitacaoMm() > 5.0 || clima.temperaturaMedia() >= 31.0) {
                return String.format(FormatadorSeguroUI.LOCALE_BR,
                        "Condição térmica/chuva no período (%.1f°C • %.1fmm) pode acelerar perecimento, umidade de carga ou avaria logística.",
                        clima.temperaturaMedia(), clima.precipitacaoMm());
            }
            if (v.getTotalEventosAtivos() > 0) {
                return "Pico logístico gerado por " + v.getTotalEventosAtivos() + " evento(s) ativo(s) próximos e giro atípico.";
            }
            return "Verificar lote de entrada, armazenamento e correlação com o cenário '" + padrao + "'.";
        }
        if (v.getTotalEventosAtivos() > 0) {
            return "Demanda impulsionada por " + v.getTotalEventosAtivos() + " evento(s) ativo(s) e contexto (" + padrao + ").";
        }
        return "Movimentação associada ao cenário '" + padrao + "' em " + v.getCoordenada().cidade() + "/" + v.getCoordenada().uf() + ".";
    }

    private static void imprimirOuExportarNode(
            Stage ownerStage,
            Node node,
            String nomeBaseArquivo,
            String conteudoHtmlOpcional,
            Label lblStatus) {
        try {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(ownerStage)) {
                Printer printer = job.getPrinter() != null ? job.getPrinter() : Printer.getDefaultPrinter();
                PageLayout pageLayout = printer != null
                        ? printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT)
                        : job.getJobSettings().getPageLayout();

                // Snapshot isolado sem alterar nem remover o node do modal ao voltar
                SnapshotParameters sp = new SnapshotParameters();
                WritableImage snapshot = node.snapshot(sp, null);
                ImageView iv = new ImageView(snapshot);
                iv.setPreserveRatio(true);
                double maxW = pageLayout != null ? pageLayout.getPrintableWidth() : 515.0;
                double maxH = pageLayout != null ? pageLayout.getPrintableHeight() : 750.0;
                if (snapshot.getWidth() > maxW) {
                    iv.setFitWidth(maxW);
                }
                if (snapshot.getHeight() * (iv.getFitWidth() > 0 ? iv.getFitWidth() / Math.max(1.0, snapshot.getWidth()) : 1.0) > maxH) {
                    iv.setFitHeight(maxH);
                }
                boolean ok = pageLayout != null ? job.printPage(pageLayout, iv) : job.printPage(iv);
                if (ok) {
                    job.endJob();
                    lblStatus.setText("✔ Enviado em folha A4 para a impressora!");
                    return;
                }
                job.cancelJob();
            }
        } catch (Exception ignored) {
        }
        String html = (conteudoHtmlOpcional != null && !conteudoHtmlOpcional.isBlank())
                ? conteudoHtmlOpcional
                : "<html><body style='font-family:Segoe UI,sans-serif;padding:24px;'><h2>Sazonalisei ERP — Comprovante A4</h2><p>Gerado em "
                  + FormatadorSeguroUI.formatarDataHoraAmigavel(LocalDateTime.now()) + ".</p></body></html>";
        Path salvo = salvarComprovanteHtml(html, nomeBaseArquivo);
        if (salvo != null) {
            lblStatus.setText("ℹ️ Sem impressora ativa — arquivo A4 salvo em: " + salvo.toAbsolutePath());
        } else {
            lblStatus.setText("⚠️ Nenhuma impressora configurada no sistema.");
        }
    }

    private static String gerarHtmlFichaDetalhes(String titulo, String badgeTexto, Map<String, String> campos) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'><title>")
          .append(FormatadorSeguroUI.tratarParaExibicao(titulo, "Ficha Detalhada")).append("</title>");
        sb.append("<style>@page{size:A4 portrait;margin:12mm;}body{font-family:'Segoe UI',sans-serif;color:#0F172A;margin:0;padding:12mm;}");
        sb.append(".card{width:100%;max-width:185mm;border:1px solid #CBD5E1;border-radius:8px;padding:16px;box-sizing:border-box;}");
        sb.append("table{width:100%;border-collapse:collapse;table-layout:fixed;margin-top:10px;}th,td{border-bottom:1px solid #E2E8F0;padding:7px;text-align:left;font-size:12px;word-wrap:break-word;vertical-align:top;}</style></head><body><div class='card'>");
        sb.append("<h2>").append(FormatadorSeguroUI.tratarParaExibicao(titulo, "Ficha de Registro")).append("</h2>");
        if (badgeTexto != null) sb.append("<p><strong>").append(badgeTexto).append("</strong></p>");
        sb.append("<table>");
        if (campos != null) {
            for (var e : campos.entrySet()) {
                sb.append("<tr><th style='width:34%;'>").append(e.getKey()).append("</th><td>")
                  .append(FormatadorSeguroUI.tratarParaExibicao(e.getValue(), "-")).append("</td></tr>");
            }
        }
        sb.append("</table></div></body></html>");
        return sb.toString();
    }

    private static Path salvarComprovanteHtml(String html, String prefixo) {
        try {
            Path dir = Paths.get("storage", "comprovantes");
            Files.createDirectories(dir);
            String nome = prefixo + "_" + System.currentTimeMillis() + ".html";
            Path arq = dir.resolve(nome);
            Files.writeString(arq, html, StandardCharsets.UTF_8);
            return arq;
        } catch (Exception e) {
            return null;
        }
    }

    private static String gerarHtmlComprovante(List<? extends Nota> notas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'><title>Comprovante A4 Sazonalisei ERP</title>");
        sb.append("<style>@page{size:A4 portrait;margin:12mm;}");
        sb.append("body{font-family:'Segoe UI',sans-serif;background:#FFFFFF;color:#0F172A;margin:0;padding:10mm;}");
        sb.append(".card{background:#FFF;border:1px solid #CBD5E1;border-radius:8px;padding:14px;margin-bottom:14px;width:100%;max-width:185mm;box-sizing:border-box;page-break-inside:avoid;}");
        sb.append(".box{background:#F8FAFC;border:1px solid #E2E8F0;border-radius:6px;padding:10px;margin:8px 0;font-size:12px;}");
        sb.append("h2{margin:0 0 6px 0;font-size:16px;}table{width:100%;border-collapse:collapse;table-layout:fixed;margin:8px 0;}");
        sb.append("th,td{border-bottom:1px solid #E2E8F0;padding:6px;text-align:left;font-size:12px;word-wrap:break-word;}");
        sb.append(".tot{font-size:15px;font-weight:bold;color:#047857;margin-top:8px;}</style></head><body>");
        for (Nota n : notas) {
            sb.append("<div class='card'>");
            sb.append("<h2>").append(traduzirTipoNota(n)).append(" #").append(n.getId() != null ? n.getId() : "Nova").append("</h2>");
            Pessoa p = n.getPessoa();
            sb.append("<p><strong>Emissão:</strong> ").append(FormatadorSeguroUI.formatarDataHoraAmigavel(n.getDataGerada()))
              .append(" | <strong>Entidade:</strong> ").append(p != null ? p.getNome() : "Consumidor Final").append("<br/>")
              .append("<strong>Observação / Motivo:</strong> ").append(FormatadorSeguroUI.tratarParaExibicao(n.getDescricao(), "Operação regular")).append("</p>");
            if (n instanceof NotaDevolucao dev && dev.getNotaVenda() != null) {
                sb.append("<div class='box'><strong>Pós-Venda:</strong> Origem Venda #").append(dev.getNotaVenda().getId()).append("</div>");
            } else if (n instanceof NotaPerda np) {
                sb.append("<div class='box'><strong>Cadeia de Perda:</strong> ");
                if (np.getNotaDevolucao() != null) sb.append("Devolução #").append(np.getNotaDevolucao().getId()).append(" | ");
                if (np.getNotaCompraOrigem() != null) sb.append("Entrada #").append(np.getNotaCompraOrigem().getId()).append(" | ");
                sb.append(np.isPerdaDentroDaValidade() ? "Elegível p/ Abatimento Fornecedor" : "Perda Interna");
                sb.append("</div>");
            }
            sb.append("<table><tr><th style='width:46%;'>Produto</th><th style='width:16%;'>Qtd</th><th style='width:19%;'>Unit.</th><th style='width:19%;'>Subtotal</th></tr>");
            if (n.getItens() != null) {
                for (Item<? extends Produto> it : n.getItens()) {
                    String np = it.getProduto() != null ? FormatadorSeguroUI.limparNomeProduto(it.getProduto().getNome()) : "Item";
                    sb.append("<tr><td>").append(np).append("</td><td>")
                      .append(FormatadorSeguroUI.formatarQuantidade(it.getQuantidade(), ""))
                      .append("</td><td>").append(FormatadorSeguroUI.formatarMoeda(it.getValor()))
                      .append("</td><td>").append(FormatadorSeguroUI.formatarMoeda(it.getSubtotal())).append("</td></tr>");
                }
            }
            sb.append("</table>");
            if (n.getPagamentos() != null && !n.getPagamentos().isEmpty()) {
                sb.append("<p><strong>Pagamentos:</strong><br/>");
                for (NotaPagamento np : n.getPagamentos()) {
                    String f = np.getFormaPagamento() != null ? np.getFormaPagamento().getNome() : "Forma";
                    sb.append("• ").append(f).append(": ").append(FormatadorSeguroUI.formatarMoeda(np.calcularValorFinal()))
                      .append(" (").append(np.isLiquidado() ? "Quitado" : "Em Aberto").append(")<br/>");
                }
                sb.append("</p>");
            }
            if (n.getVinculoFatorExterno() != null) {
                var v = n.getVinculoFatorExterno();
                var clima = v.getClimaRegistrado();
                sb.append("<div class='box'><strong>🌦️ Eventos Externos, Clima & Potenciais Motivos:</strong><br/>")
                  .append("Cenário: ").append(traduzirPadraoConsumoAmigavel(v.determinarPadraoConsumo()))
                  .append(String.format(Locale.US, " | Temp: %.1f°C | Chuva: %.1fmm", clima.temperaturaMedia(), clima.precipitacaoMm()))
                  .append(" | Eventos próximos: ").append(v.getTotalEventosAtivos()).append("<br/>")
                  .append("Motivos Mapeados: ").append(inferirPotencialMotivoComercial(n, v)).append("</div>");
            }
            sb.append("<p class='tot'>Total da Nota: ").append(FormatadorSeguroUI.formatarMoeda(n.getValorTotal())).append("</p>");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String traduzirTipoNota(Nota n) {
        if (n instanceof NotaCompra) return "NOTA DE ENTRADA (COMPRA)";
        if (n instanceof NotaVenda) return "NOTA DE VENDA";
        if (n instanceof NotaDevolucao) return "NOTA DE DEVOLUÇÃO";
        if (n instanceof NotaPerda) return "NOTA DE PERDA";
        return "NOTA OPERACIONAL";
    }

    private static Stage criarStageModal(Node owner, String tituloJanela, double width, double height) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null && owner.getScene() != null) {
            Window w = owner.getScene().getWindow();
            if (w != null) dialog.initOwner(w);
        }
        dialog.setTitle(tituloJanela);
        dialog.setWidth(width);
        dialog.setHeight(height);
        dialog.setMinWidth(440);
        dialog.setMinHeight(340);
        return dialog;
    }

    private static VBox criarCabecalhoModal(String icone, String titulo, String subtitulo, String bgHex) {
        Label lTit = FormatadorSeguroUI.criarLabel((icone != null ? icone + " " : "") + titulo, 14, true, "#FFFFFF");
        Label lSub = FormatadorSeguroUI.criarLabel(subtitulo, 11, false, "#CBD5E1");
        VBox header = new VBox(2, lTit, lSub);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: " + (bgHex != null ? bgHex : "#0F172A") + ";");
        return header;
    }

    private static VBox montarContainerModal(VBox header, Node centro, HBox rodape) {
        rodape.setPadding(new Insets(10, 16, 12, 16));
        rodape.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-width: 1px 0 0 0;");
        VBox root = new VBox(header, centro, rodape);
        root.setStyle("-fx-background-color: #FFFFFF;");
        return root;
    }
}
