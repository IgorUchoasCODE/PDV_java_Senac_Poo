package br.com.senac.application;

import br.com.senac.controller.RespostaEndpoint;
import br.com.senac.controller.RoteadorController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Smoke test das 8 rotas estatÃ­sticas (procedures MySQL) sobre os dados existentes em sazonalisei. */
public class TesteEstatisticasInteligencia {
    public static void main(String[] args) {
        RoteadorController r = new RoteadorController();
        String[] rotas = {
                "estatistica_demanda_mensal_cliente", "estatistica_perfil_cliente", "estatistica_impacto_externo_produtos",
                "estatistica_ranking_fornecedores", "estatistica_motivos_perda_prematura", "estatistica_destaques_sazonais",
                "estatistica_picos_dias_eventos", "estatistica_plano_estrategico_compras"
        };
        int ok = 0;
        for (String rota : rotas) {
            long t = System.currentTimeMillis();
            RespostaEndpoint<Object> resp = r.executarEndpoint(rota, Map.of("dataReferencia", LocalDate.now()));
            Object dados = resp.dados();
            int n = dados instanceof List<?> l ? l.size() : -1;
            System.out.printf("%s sucesso=%s linhas=%d %dms %s%n", rota, resp.sucesso(), n,
                    System.currentTimeMillis() - t, resp.sucesso() ? "" : resp.mensagem());
            if (resp.sucesso()) ok++;
            if (n > 0) System.out.println("   ex: " + ((List<?>) dados).get(0));
        }
        System.out.println("RESULTADO: " + ok + "/" + rotas.length);
        br.com.senac.repository.dao.JPAUtil.close();
    }
}

