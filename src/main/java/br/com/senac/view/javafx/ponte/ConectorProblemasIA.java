package br.com.senac.view.javafx.ponte;

import br.com.senac.view.javafx.componentes.DialogoCorporativoModal;
import br.com.senac.view.javafx.componentes.PainelCopilotoIAChat;
import br.com.senac.view.javafx.tema.FormatadorSeguroUI;
import br.com.senac.view.javafx.tema.GerenciadorTemaClimatico;
import javafx.scene.Node;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conector contextual que transforma qualquer indicador, card, gráfico, nota de perda,
 * evento GPS ou tela em solicitação para a IA, transmitindo obrigatoriamente todos os
 * metadados específicos de origem (Aba, Nota, Data, Produto, Lote, Motivo, Entidade e Clima).
 */
public class ConectorProblemasIA {

    private final PonteRoteadorIA ponte;
    private PainelCopilotoIAChat painelCopiloto;
    private Runnable aoAbrirCopiloto;

    public ConectorProblemasIA(PonteRoteadorIA ponte) {
        this.ponte = ponte;
    }

    public void vincularPainelCopiloto(PainelCopilotoIAChat painel, Runnable callbackAbrir) {
        this.painelCopiloto = painel;
        this.aoAbrirCopiloto = callbackAbrir;
    }

    public void perguntarAoCopiloto(String tituloContexto, String promptParaIA) {
        perguntarAoCopiloto(painelCopiloto, tituloContexto, tituloContexto, promptParaIA);
    }

    public void perguntarAoCopiloto(Node owner, String tituloContexto, String resumoDados, String promptParaIA) {
        Node donoReal = owner != null ? owner : painelCopiloto;
        DialogoCorporativoModal.confirmarEnvioIA(
                donoReal,
                tituloContexto,
                resumoDados,
                promptParaIA,
                promptConfirmado -> {
                    if (aoAbrirCopiloto != null) {
                        aoAbrirCopiloto.run();
                    }
                    if (painelCopiloto != null) {
                        painelCopiloto.enviarPromptContextual(tituloContexto, promptConfirmado);
                    }
                }
        );
    }

    /**
     * Regra global: sempre que a IA for explicar qualquer card, nota, perda ou gráfico,
     * transmite o bloco estruturado de origem completo (aba, ID nota, data, produto, motivo, entidade e clima).
     */
    public void explicarComContextoOrigem(
            Node owner,
            String abaOrigem,
            String identificacaoRegistro,
            Map<String, String> metadadosOrigem,
            String perguntaEspecifica) {

        var snap = ponte.getGerenciadorTema().getSnapshotAtual();
        StringBuilder resumoVisual = new StringBuilder();
        resumoVisual.append("Origem: ").append(FormatadorSeguroUI.tratarParaExibicao(abaOrigem, "Sistema ERP"))
                .append(" • ").append(FormatadorSeguroUI.tratarParaExibicao(identificacaoRegistro, "Registro"));

        StringBuilder blocoDados = new StringBuilder();
        blocoDados.append("[DADOS ESPECÍFICOS DE ORIGEM]\n");
        blocoDados.append("- Aba/Módulo de Origem: ").append(FormatadorSeguroUI.tratarParaExibicao(abaOrigem, "ERP")).append("\n");
        blocoDados.append("- Identificação: ").append(FormatadorSeguroUI.tratarParaExibicao(identificacaoRegistro, "Registro")).append("\n");

        if (metadadosOrigem != null) {
            metadadosOrigem.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    blocoDados.append("- ").append(k).append(": ").append(v).append("\n");
                    if (resumoVisual.length() < 180) {
                        resumoVisual.append(" | ").append(k).append(": ").append(v);
                    }
                }
            });
        }
        if (snap != null) {
            blocoDados.append(String.format(FormatadorSeguroUI.LOCALE_BR,
                    "- Contexto Ambiental Atual da Loja (%s/%s): %s (Cota %s: %.1f m • %s)\n",
                    snap.cidade(), snap.uf(), snap.descreverClimaCurto(),
                    snap.bacia(), snap.nivelRioMetros(), snap.regimeRio().getRotulo()));
        }

        String promptCompleto = blocoDados
                + "\n[INSTRUÇÃO OBRIGATÓRIA]\n"
                + FormatadorSeguroUI.tratarParaExibicao(perguntaEspecifica,
                "Explique de forma prática e personalizada este registro específico.")
                + "\nIMPORTANTE: Cite explicitamente em sua resposta os dados reais de origem acima "
                + "(número da nota/registro, data exata, produto, fornecedor/cliente e motivo específico informado).";

        perguntarAoCopiloto(owner, identificacaoRegistro, resumoVisual.toString(), promptCompleto);
    }

    public void explicarClimaDeHoje(GerenciadorTemaClimatico.SnapshotTemaClimatico snap) {
        if (snap == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Cidade/UF", snap.cidade() + "/" + snap.uf());
        meta.put("Cenário Climático", snap.estado().getTitulo());
        meta.put("Temperatura Média", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C",
                snap.clima() != null ? snap.clima().temperaturaMedia() : 28.5));
        meta.put("Precipitação", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f mm",
                snap.clima() != null ? snap.clima().precipitacaoMm() : 0.0));
        meta.put("Rio / Bacia", snap.bacia() + String.format(FormatadorSeguroUI.LOCALE_BR, " (%.1f m • %s)",
                snap.nivelRioMetros(), snap.regimeRio().getRotulo()));

        explicarComContextoOrigem(
                null,
                "Monitor Climático & Hidrológico",
                "Clima Atual de " + snap.cidade() + "/" + snap.uf(),
                meta,
                "Explique como este clima e este nível do rio hoje afetam o fluxo de clientes, quais produtos destacar no balcão e que cuidados tomar no estoque."
        );
    }

    public void explicarPeriodoGrafico(
            String rotuloPeriodo,
            List<PonteRoteadorIA.PontoDiarioSazonal> pontos) {
        if (pontos == null || pontos.isEmpty()) {
            perguntarAoCopiloto("Resumo do Período",
                    "Ainda não há vendas neste período. Como posso começar a registrar minhas compras e vendas para acompanhar o lucro?");
            return;
        }
        double fat = 0, luc = 0, per = 0, sumTemp = 0, sumRio = 0, maxChuva = 0;
        for (var p : pontos) {
            fat += p.faturamento();
            luc += p.lucroLiquido();
            per += p.perdas();
            sumTemp += p.tempMedia();
            sumRio += p.nivelRioMetros();
            maxChuva = Math.max(maxChuva, p.precipitacaoMm());
        }
        double avgTemp = sumTemp / pontos.size();
        double avgRio = sumRio / pontos.size();
        LocalDate ini = pontos.get(0).data();
        LocalDate fim = pontos.get(pontos.size() - 1).data();

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Recorte Analisado", rotuloPeriodo);
        meta.put("Período", FormatadorSeguroUI.formatarDataAmigavel(ini) + " até " + FormatadorSeguroUI.formatarDataAmigavel(fim) + " (" + pontos.size() + " dias)");
        meta.put("Faturamento Total", FormatadorSeguroUI.formatarMoeda(fat));
        meta.put("Lucro Líquido Real", FormatadorSeguroUI.formatarMoeda(luc));
        meta.put("Perdas Apuradas", FormatadorSeguroUI.formatarMoeda(per));
        meta.put("Temperatura Média / Chuva Máx.", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f °C • %.1f mm", avgTemp, maxChuva));
        meta.put("Cota Média do Rio", String.format(FormatadorSeguroUI.LOCALE_BR, "%.1f m", avgRio));

        explicarComContextoOrigem(
                null,
                "Home / Painel Sazonal de Vendas",
                "Série Sazonal (" + rotuloPeriodo + ")",
                meta,
                "Explique em linguagem clara o que causou este desempenho exato neste período e quais ajustes práticos fazer nos próximos dias."
        );
    }

    public void sugerirPromocaoOuReposicao(String nomeProduto, BigDecimal saldo, LocalDate validade) {
        String valTxt = validade != null ? FormatadorSeguroUI.formatarDataAmigavel(validade) : "sem validade definida";
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Produto", FormatadorSeguroUI.limparNomeProduto(nomeProduto));
        meta.put("Saldo em Estoque", FormatadorSeguroUI.formatarQuantidade(saldo, "UN"));
        meta.put("Validade Mais Próxima", valTxt);

        explicarComContextoOrigem(
                null,
                "Estoque & Validade",
                "Diagnóstico de Estoque: " + FormatadorSeguroUI.limparNomeProduto(nomeProduto),
                meta,
                "Sugira de forma prática se devo fazer promoção relâmpago ou repor estoque deste item específico, indicando preço ou combo recomendado."
        );
    }

    public void analisarPerdaERessarcimento(String nomeProduto, String fornecedor, String motivo, BigDecimal valorPerda, boolean dentroValidade) {
        analisarPerdaERessarcimento(null, "Financeiro / Estoque", null, null, nomeProduto, fornecedor, motivo, valorPerda, dentroValidade, null);
    }

    public void analisarPerdaERessarcimento(
            Node owner,
            String abaOrigem,
            Long idNotaPerda,
            String dataPerdaFormatada,
            String nomeProduto,
            String fornecedor,
            String motivoRealNota,
            BigDecimal valorPerda,
            boolean dentroValidade,
            String infoExtraLoteOuNota) {

        String prodLimpo = FormatadorSeguroUI.limparNomeProduto(nomeProduto);
        String fornLimpo = FormatadorSeguroUI.tratarParaExibicao(fornecedor, "Fornecedor não vinculado");
        String motivoLimpo = FormatadorSeguroUI.tratarParaExibicao(motivoRealNota, "Motivo não detalhado na nota");

        Map<String, String> meta = new LinkedHashMap<>();
        if (idNotaPerda != null) meta.put("Nota de Perda", "Nota de Perda #" + idNotaPerda);
        if (dataPerdaFormatada != null && !dataPerdaFormatada.isBlank()) meta.put("Data da Ocorrência", dataPerdaFormatada);
        meta.put("Produto Perdido", prodLimpo);
        meta.put("Fornecedor de Origem", fornLimpo);
        meta.put("Motivo Real Registrado na Nota", motivoLimpo);
        meta.put("Valor de Custo da Perda", FormatadorSeguroUI.formatarMoeda(valorPerda));
        meta.put("Status de Validade", dentroValidade
                ? "Dentro da validade (ELEGÍVEL para ressarcimento/abatimento com " + fornLimpo + ")"
                : "Expirado na prateleira (perda interna sem abatimento)");
        if (infoExtraLoteOuNota != null && !infoExtraLoteOuNota.isBlank()) {
            meta.put("Vínculo Lote / Nota de Entrada", infoExtraLoteOuNota);
        }

        String idReg = (idNotaPerda != null ? "Perda #" + idNotaPerda + " — " : "Perda — ") + prodLimpo;
        explicarComContextoOrigem(
                owner,
                abaOrigem != null ? abaOrigem : "Financeiro / Perdas Vinculadas",
                idReg,
                meta,
                "Analise especificamente ESTA perda"
                        + (idNotaPerda != null ? " da Nota #" + idNotaPerda : "")
                        + (dataPerdaFormatada != null ? " do dia " + dataPerdaFormatada : "")
                        + " referente ao produto '" + prodLimpo + "' com o motivo registrado '" + motivoLimpo + "'. "
                        + "Explique a provável causa operacional/climática, como evitar que esse motivo se repita neste item e se/como abater esse custo de "
                        + FormatadorSeguroUI.formatarMoeda(valorPerda) + " com o fornecedor '" + fornLimpo + "'."
        );
    }

    public void perguntarEstrategiaEventoGps(PonteRoteadorIA.EventoMapaGps evento) {
        if (evento == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Evento", evento.nome());
        meta.put("Categoria / Fonte", evento.categoria() + " (" + evento.fonte() + ")");
        meta.put("Local / Bairro", evento.localNome() + " • " + evento.bairro() + " (" + evento.cidadeUf() + ")");
        meta.put("Distância da Loja", FormatadorSeguroUI.formatarDistanciaKm(evento.distanciaKm()));
        meta.put("Data & Horário", evento.dataHorario());
        meta.put("Previsão do Tempo no Evento", evento.previsaoTempo());
        meta.put("Oportunidade Detectada", evento.resumoOportunidade());

        explicarComContextoOrigem(
                null,
                "Mapa GPS & Eventos Regionais",
                "Estratégia p/ Evento: " + evento.nome(),
                meta,
                "Monte uma estratégia prática de vendas para aproveitar este evento específico considerando a distância e a previsão do tempo informada (ex.: o que vender na frente de caixa, combos e estoque preventivo)."
        );
    }

    public void prepararCompraParaEventoNaBandeja(PonteRoteadorIA.EventoMapaGps evento) {
        if (evento == null) return;
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Evento Alvo", evento.nome());
        meta.put("Data/Horario", evento.dataHorario());
        meta.put("Distância", FormatadorSeguroUI.formatarDistanciaKm(evento.distanciaKm()));
        meta.put("Previsão Meteorológica", evento.previsaoTempo());

        explicarComContextoOrigem(
                null,
                "Mapa GPS & Eventos / Reposição",
                "Reposição p/ Evento: " + evento.nome(),
                meta,
                "Consulte meus produtos no catálogo e meus fornecedores e prepare na Bandeja de Contexto uma sugestão de Nota de Compra para este evento e este clima previsto."
        );
    }

    public void resolverErroOuBloqueioComIA(String contextoOperacao, String erroAmigavel) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("Operação Tentada", FormatadorSeguroUI.tratarParaExibicao(contextoOperacao, "Operação na tela"));
        meta.put("Aviso / Bloqueio Recebido", FormatadorSeguroUI.tratarParaExibicao(erroAmigavel, "Pendência operacional"));
        explicarComContextoOrigem(
                null,
                "Assistente de Resolução",
                "Resolução de Bloqueio Operacional",
                meta,
                "Verifique o estado atual no sistema e me ajude a resolver esta pendência ou prepare na bandeja a solução correta."
        );
    }
}
