package br.com.senac.service;

import br.com.senac.controller.EstatisticaController;
import br.com.senac.integracao.facade.ConsultaIntegracaoFacade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Serviço de inteligência analítica e correlação entre fatores externos (clima, rio, eventos, calendário)
 * e o volume de vendas / demanda de produtos, consolidando lift estatístico e teste de significância.
 */
public class AnaliseImpactoExternoService {

    public record DiagnosticoImpacto(
            Long idProduto,
            String nomeProduto,
            double liftClimaChuvoso,
            double liftEventosCulturais,
            double liftQuintoDiaUtil,
            double liftRioSeca,
            String fatorMaisImpactante,
            String recomendacaoEstoque
    ) {}

    private final EstatisticaController estatisticaController;
    private final ConsultaIntegracaoFacade integracaoFacade;

    public AnaliseImpactoExternoService() {
        this(new EstatisticaController(), new ConsultaIntegracaoFacade());
    }

    public AnaliseImpactoExternoService(EstatisticaController estatisticaController, ConsultaIntegracaoFacade integracaoFacade) {
        this.estatisticaController = estatisticaController;
        this.integracaoFacade = integracaoFacade;
    }

    /**
     * Consolida o diagnóstico estatístico completo de um produto perante todos os fatores externos.
     */
    public DiagnosticoImpacto diagnosticarProduto(Long idProduto, String nomeProduto) {
        List<Map<String, Object>> fatores = estatisticaController.estatisticaFatoresProduto(idProduto);

        double liftChuva = extrairLift(fatores, "CHUVA", "CLIMA_CHUVOSO");
        double liftEventos = extrairLift(fatores, "EVENTO", "EVENTOS_CULTURAIS");
        double liftQuintoDia = extrairLift(fatores, "QUINTO_DIA_UTIL", "PAGAMENTO");
        double liftSeca = extrairLift(fatores, "SECA", "RIO_SECA");

        // Identifica o fator com maior lift positivo
        Map<String, Double> lifts = new LinkedHashMap<>();
        lifts.put("Chuva / Clima Chuvoso", liftChuva);
        lifts.put("Eventos Culturais", liftEventos);
        lifts.put("5º Dia Útil Comercial", liftQuintoDia);
        lifts.put("Seca dos Rios", liftSeca);

        String campeao = "Demanda Estável";
        double maiorLift = 1.0;
        for (var entry : lifts.entrySet()) {
            if (entry.getValue() > maiorLift) {
                maiorLift = entry.getValue();
                campeao = entry.getKey();
            }
        }

        String recomendacao;
        if (maiorLift > 1.25) {
            double percAumento = (maiorLift - 1.0) * 100.0;
            recomendacao = String.format("Aumentar estoque de segurança em +%.0f%% sob ocorrência de '%s'.", percAumento, campeao);
        } else if (maiorLift < 0.85) {
            recomendacao = "Produto com demanda inelástica ou redução sazonal. Manter reposição básica via FIFO.";
        } else {
            recomendacao = "Demanda equilibrada. Seguir plano de reposição contínua sem necessidade de pulmão adicional.";
        }

        return new DiagnosticoImpacto(
                idProduto,
                nomeProduto != null ? nomeProduto : ("Produto #" + idProduto),
                liftChuva,
                liftEventos,
                liftQuintoDia,
                liftSeca,
                campeao,
                recomendacao
        );
    }

    /**
     * Gera relatório comparativo de impacto externo pronto para renderização na interface gráfica JavaFX.
     */
    public List<Map<String, Object>> gerarDadosGraficoJavaFX(Long idProduto) {
        List<Map<String, Object>> dados = new ArrayList<>();
        DiagnosticoImpacto diag = diagnosticarProduto(idProduto, "Produto");

        dados.add(Map.of("fator", "Chuva", "lift", diag.liftClimaChuvoso(), "cor", "#3498db"));
        dados.add(Map.of("fator", "Eventos", "lift", diag.liftEventosCulturais(), "cor", "#9b59b6"));
        dados.add(Map.of("fator", "5º Dia Útil", "lift", diag.liftQuintoDiaUtil(), "cor", "#2ecc71"));
        dados.add(Map.of("fator", "Nível do Rio", "lift", diag.liftRioSeca(), "cor", "#e67e22"));

        return dados;
    }

    private double extrairLift(List<Map<String, Object>> fatores, String... termos) {
        if (fatores == null || fatores.isEmpty()) return 1.0;
        for (Map<String, Object> f : fatores) {
            String nome = String.valueOf(f.getOrDefault("fator", f.getOrDefault("nome_fator", ""))).toUpperCase(Locale.ROOT);
            for (String t : termos) {
                if (nome.contains(t)) {
                    Object val = f.getOrDefault("lift", f.getOrDefault("efeito_lift", 1.0));
                    if (val instanceof Number n) {
                        return n.doubleValue();
                    }
                }
            }
        }
        return 1.0;
    }
}
