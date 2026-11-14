package br.com.senac.application;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.FormatadorSaidaIA;
import br.com.senac.integracao.ia.FormatadorSaidaIA.ConfiguracaoFormatacaoSaida;
import br.com.senac.integracao.ia.FormatadorSaidaIA.RespostaFormatadaIA;
import br.com.senac.integracao.ia.FormatadorSaidaIA.TipoEntidadeDestaque;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valida o tratamento completo de saídas da IA:
 * - Eliminação de dados brutos (List<Map> e strings [{chave=valor}])
 * - Contador de palavras e quebra de linha configurável por espaço
 * - Segmentação de fonte, tamanho e cores sutis por entidade (Cliente, Fornecedor, Pessoa, Produto, Nota)
 */
public class TesteFormatadorSaidaIA {

    public static void main(String[] args) {
        System.out.println("=== TESTE DO FORMATADOR DE SAÍDA DA IA ===");

        // 1. Teste com dados brutos em String (exemplo real de perda prematura e impacto externo)
        String brutoPerda = "[{id_nota_perda=13498, data_perda=2026-01-08T16:00, id_produto=414, nome_produto=OVO BRANCO A, "
                + "id_fornecedor=226, nome_fornecedor=Gema De Ouro, quantidade_perdida=4.000000, prejuizo_contabil_bacen=66.67, "
                + "data_validade=2026-01-23, dias_antes_de_vencer=15, dias_permanencia_apos_compra=5, "
                + "temperatura_exposicao_c=25.5, precipitacao_exposicao_mm=12.2, vazao_rio_transporte_m3s=1.94, "
                + "peso_causa_calor_termico_pct=10.0, peso_causa_umidade_chuva_pct=20.0, peso_causa_transporte_rio_pct=5.0, "
                + "peso_causa_qualidade_fornecedor_pct=50.0, motivo_principal_diagnosticado=FALHA_QUALIDADE_INTRINSECA_DO_FORNECEDOR_OU_MANUSEIO, "
                + "acao_preventiva_recomendada=ANTECIPAR GIRO VIA PROMOCAO DE LOTE DESEJAVEL E REVISAR EMBALAGEM/FORNECEDOR}]";

        String formatadoPerda = FormatadorSaidaIA.formatarTextoParaIA(brutoPerda);
        afirmar(!formatadoPerda.contains("id_nota_perda="), "Não deve conter chave bruta id_nota_perda=");
        afirmar(formatadoPerda.contains("Nota de Perda #13498"), "Deve exibir título amigável Nota de Perda #13498");
        afirmar(formatadoPerda.contains("R$ 66,67"), "Deve formatar prejuízo em R$ 66,67");
        afirmar(formatadoPerda.contains("08/01/2026 16:00"), "Deve formatar data_perda no padrão BR");
        System.out.println(" [OK] Sanitização de string bruta de perda prematura.");

        // 2. Teste com List<Map<String, Object>> direto (como retornado por EstatisticaController)
        Map<String, Object> impacto = new LinkedHashMap<>();
        impacto.put("id_produto", 417);
        impacto.put("nome_produto", "CAIXA OVO BRANCO C");
        impacto.put("custo_medio_geral", 176.1905);
        impacto.put("custo_seca_rios_vazante", 205.0000);
        impacto.put("custo_nivel_normal_rios", 173.0556);
        impacto.put("custo_cheia_rios", 175.0000);
        impacto.put("sobrecusto_logistico_rios_pct", 18.46);
        impacto.put("giro_medio_geral", 0.0);
        impacto.put("giro_medio_calor_30c", 0.0);
        impacto.put("giro_medio_chuva_forte", 0.0);
        impacto.put("giro_medio_eventos_ativos", 0.0);
        impacto.put("giro_medio_quinto_dia_util", 0.0);
        impacto.put("qtd_total_perdida", 0.0);
        impacto.put("qtd_perdida_clima_adverso", 0.0);
        impacto.put("diagnostico_impacto_externo", "ALTA_SENSIBILIDADE_SECA_RIOS (FRETE FLUVIAL ENCARECE CUSTO)");

        String formatadoImpacto = FormatadorSaidaIA.formatarDadoEstruturado(
                RotaSistema.ESTATISTICA_IMPACTO_EXTERNO_PRODUTOS, List.of(impacto));
        afirmar(!formatadoImpacto.contains("custo_medio_geral="), "Não deve conter chave bruta custo_medio_geral=");
        afirmar(formatadoImpacto.contains("Produto #417 — CAIXA OVO BRANCO C"), "Deve formatar Produto #417");
        afirmar(formatadoImpacto.contains("R$ 176,19"), "Deve formatar custo médio em R$ 176,19");
        System.out.println(" [OK] Formatação estruturada de List<Map> de impacto externo.");

        // 3. Teste do contador de palavras e quebra de linha (\n) por espaço
        String fraseLonga = "O Cliente #12 comprou o Produto #414 do Fornecedor #226 na Nota Fiscal #99 "
                + "com entrega programada para evitar estresse térmico durante o transporte fluvial na vazante";
        int totalPalavras = FormatadorSaidaIA.contarPalavras(fraseLonga);
        afirmar(totalPalavras == 27, "Contador de palavras deve contar 27 palavras (atual: " + totalPalavras + ")");

        String quebrada = FormatadorSaidaIA.quebrarLinhasPorPalavras(fraseLonga, 8);
        String[] linhas = quebrada.split("\n");
        afirmar(linhas.length >= 4, "Com 27 palavras e limite 8, deve gerar pelo menos 4 linhas");
        for (String l : linhas) {
            int pLinha = FormatadorSaidaIA.contarPalavras(l);
            afirmar(pLinha <= 8, "Cada linha deve ter no máximo 8 palavras (encontrado: " + pLinha + ")");
        }
        System.out.println(" [OK] Contador de palavras e quebra de linhas (max 8 palavras/linha) validado.");

        // 4. Teste de destaque de entidades (Cliente, Fornecedor, Pessoa, Produto, Nota) com fonte, tamanho e cor
        String textoEntidades = "Resumo: Cliente #10, Fornecedor #22, Pessoa #5, Produto #414 e Nota #88 no valor de R$ 150,00.";
        RespostaFormatadaIA pacote = FormatadorSaidaIA.formatar(textoEntidades, ConfiguracaoFormatacaoSaida.comPalavrasPorLinha(10));
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.CLIENTE), "Deve destacar Cliente");
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.FORNECEDOR), "Deve destacar Fornecedor");
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.PESSOA), "Deve destacar Pessoa");
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.PRODUTO), "Deve destacar Produto");
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.NOTA), "Deve destacar Nota");
        afirmar(pacote.segmentos().stream().anyMatch(s -> s.tipo() == TipoEntidadeDestaque.VALOR_MONETARIO), "Deve destacar Valor Monetário");
        afirmar(pacote.htmlFormatado().contains("font-family") && pacote.htmlFormatado().contains("font-size"),
                "HTML gerado deve conter metadados de fonte e tamanho");
        afirmar(pacote.textoAnsiColorido().contains("\u001B["), "Saída ANSI deve conter códigos de cor para terminal");
        System.out.println(" [OK] Destaque de entidades, fontes, tamanhos, ANSI e HTML validados.");

        System.out.println("\nPreview ANSI no Terminal:\n" + FormatadorSaidaIA.formatarParaTerminalAnsi(brutoPerda, 12));
        System.out.println("\n=== TODOS OS TESTES DO FORMATADOR PASSARAM! ===");
    }

    private static void afirmar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }
}
