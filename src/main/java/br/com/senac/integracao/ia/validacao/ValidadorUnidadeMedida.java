package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.repository.modal.produto.Um;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conferências pré e pós-execução das rotas de Unidade de Medida ({@link Um}),
 * garantindo prevenção contra duplicidade por sigla OU por nome (unique constraint).
 */
public class ValidadorUnidadeMedida implements ValidadorExecucao {

    private static final Set<RotaSistema> ROTAS = EnumSet.of(
            RotaSistema.PRODUTO_UNIDADE_CADASTRAR,
            RotaSistema.PRODUTO_UNIDADE_ATUALIZAR,
            RotaSistema.PRODUTO_UNIDADE_EXCLUIR);

    @Override
    public boolean aplicaA(RotaSistema rota) {
        return ROTAS.contains(rota);
    }

    @Override
    public Map<String, Object> capturarEstadoAntes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> foto = new LinkedHashMap<>();
        String sigla = LeitorParametros.textoOuNulo(item.getParametros(), "sigla", "siglaAtual");
        if (sigla != null) {
            Um existente = roteador.produtos().buscarUnidadePorSigla(sigla);
            if (existente != null) {
                foto.put("sigla", existente.getSigla());
                foto.put("nome", existente.getNome());
            }
        }
        return foto;
    }

    @Override
    public ResultadoValidacao antes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        String sigla = LeitorParametros.textoOuNulo(p, "sigla", "siglaAtual");
        if (sigla == null) {
            return ResultadoValidacao.reprovado("Informe a sigla da unidade de medida.");
        }

        if (item.getRota() == RotaSistema.PRODUTO_UNIDADE_CADASTRAR) {
            if (sigla.trim().length() > 4) {
                return ResultadoValidacao.reprovado("A sigla da unidade deve ter no máximo 4 caracteres (informada: \"" + sigla + "\").");
            }
            String nome = LeitorParametros.textoOuNulo(p, "nome");
            if (nome == null) {
                return ResultadoValidacao.reprovado("Informe o nome completo da unidade de medida.");
            }
            List<Um> todas = roteador.produtos().listarUnidadesMedida();
            if (todas != null) {
                for (Um um : todas) {
                    if (um.getSigla() != null && um.getSigla().equalsIgnoreCase(sigla.trim())) {
                        return ResultadoValidacao.reprovado(
                                "Já existe uma unidade de medida com a sigla \"" + um.getSigla()
                                        + "\" (" + um.getNome() + "). Use a sigla existente.");
                    }
                    if (um.getNome() != null && um.getNome().equalsIgnoreCase(nome.trim())) {
                        return ResultadoValidacao.reprovado(
                                "Já existe uma unidade de medida com o nome \"" + um.getNome()
                                        + "\" (sigla \"" + um.getSigla() + "\"). Use \"" + um.getSigla()
                                        + "\" ou altere o nome da unidade.");
                    }
                }
            }
            return ResultadoValidacao.aprovado("Sigla \"" + sigla + "\" e nome \"" + nome + "\" disponíveis.");
        }

        Um existente = roteador.produtos().buscarUnidadePorSigla(sigla);
        if (existente == null) {
            return ResultadoValidacao.reprovado("Unidade de medida \"" + sigla + "\" não encontrada.");
        }
        return ResultadoValidacao.aprovado("Unidade \"" + existente.getSigla() + "\" encontrada.");
    }

    @Override
    public ResultadoValidacao depois(ItemBandeja item, Object resultado,
                                     Map<String, Object> antes, RoteadorController roteador) {
        String sigla = LeitorParametros.textoOuNulo(item.getParametros(), "sigla", "siglaAtual");
        if (item.getRota() == RotaSistema.PRODUTO_UNIDADE_EXCLUIR) {
            boolean aindaExiste = sigla != null && roteador.produtos().unidadeExiste(sigla);
            return aindaExiste
                    ? ResultadoValidacao.reprovado("A unidade \"" + sigla + "\" ainda aparece no banco após exclusão.")
                    : ResultadoValidacao.aprovado("Unidade \"" + sigla + "\" removida.");
        }
        if (sigla != null && roteador.produtos().unidadeExiste(sigla)) {
            return ResultadoValidacao.aprovado("Unidade \"" + sigla + "\" conferida no banco.");
        }
        return ResultadoValidacao.reprovado("Não encontrei a unidade \"" + sigla + "\" no banco após salvar.");
    }
}
