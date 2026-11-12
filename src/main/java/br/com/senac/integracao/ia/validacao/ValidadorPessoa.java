package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.repository.modal.pessoa.Pessoa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Conferências das instruções de pessoa: cadastro, dados, limite de crédito, documento e exclusão. */
public class ValidadorPessoa implements ValidadorExecucao {

    private static final Set<RotaSistema> CADASTROS = EnumSet.of(
            RotaSistema.PESSOA_SIMPLES_CADASTRAR,
            RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR,
            RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR,
            RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR,
            RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR);

    private static final Set<RotaSistema> SOBRE_PESSOA_EXISTENTE = EnumSet.of(
            RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE,
            RotaSistema.PESSOA_DADOS_ATUALIZAR,
            RotaSistema.PESSOA_CLIENTE_EXCLUIR,
            RotaSistema.PESSOA_REGISTRO_ADICIONAR);

    @Override
    public boolean aplicaA(RotaSistema rota) {
        return CADASTROS.contains(rota) || SOBRE_PESSOA_EXISTENTE.contains(rota);
    }

    @Override
    public Map<String, Object> capturarEstadoAntes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> foto = new LinkedHashMap<>();
        Long id = LeitorParametros.longOuNulo(item.getParametros(), "idPessoa");
        Pessoa alvo = id != null ? roteador.pessoas().buscarPorId(id) : null;
        if (alvo != null) {
            foto.put("id", alvo.getId());
            foto.put("nome", alvo.getNome());
            foto.put("limite", alvo.getLimite());
        }
        return foto;
    }

    @Override
    public ResultadoValidacao antes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        if (CADASTROS.contains(item.getRota())) {
            String nome = LeitorParametros.textoOuNulo(p, "nome", "razaoSocial");
            return nome == null
                    ? ResultadoValidacao.reprovado("Falta o nome da pessoa.")
                    : ResultadoValidacao.aprovado("Dados de \"" + nome + "\" completos para o cadastro.");
        }
        Long id = LeitorParametros.longOuNulo(p, "idPessoa");
        Pessoa alvo = id != null ? roteador.pessoas().buscarPorId(id) : null;
        if (alvo == null) {
            return ResultadoValidacao.reprovado("Não encontrei a pessoa #" + id + ".");
        }
        if (item.getRota() == RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE) {
            BigDecimal novo = LeitorParametros.decimalOuNulo(p, "novoLimite");
            if (novo == null || novo.signum() < 0) {
                return ResultadoValidacao.reprovado("O novo limite de crédito é inválido.");
            }
        }
        if (item.getRota() == RotaSistema.PESSOA_REGISTRO_ADICIONAR
                && LeitorParametros.textoOuNulo(p, "numeroRegistro") == null) {
            return ResultadoValidacao.reprovado("Falta o número do documento.");
        }
        return ResultadoValidacao.aprovado("Pessoa \"" + alvo.getNome() + "\" encontrada.");
    }

    @Override
    public ResultadoValidacao depois(ItemBandeja item, Object resultado,
                                     Map<String, Object> antes, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        RotaSistema rota = item.getRota();

        if (CADASTROS.contains(rota)) {
            if (!(resultado instanceof Pessoa criada) || criada.getId() == null) {
                return ResultadoValidacao.reprovado("O cadastro não retornou um código depois de salvar.");
            }
            return roteador.pessoas().buscarPorId(criada.getId()) != null
                    ? ResultadoValidacao.aprovado("Pessoa #" + criada.getId() + " conferida no banco.")
                    : ResultadoValidacao.reprovado("Salvei, mas não achei a pessoa no banco ao conferir.");
        }

        Long id = LeitorParametros.longOuNulo(p, "idPessoa");
        Pessoa depois = id != null ? roteador.pessoas().buscarPorId(id) : null;

        if (rota == RotaSistema.PESSOA_CLIENTE_EXCLUIR) {
            return depois == null
                    ? ResultadoValidacao.aprovado("Pessoa removida e conferida.")
                    : ResultadoValidacao.reprovado("A pessoa ainda aparece no banco depois da exclusão.");
        }
        if (depois == null) {
            return ResultadoValidacao.reprovado("Não achei a pessoa no banco ao conferir.");
        }
        if (rota == RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE) {
            BigDecimal esperado = LeitorParametros.decimalOuNulo(p, "novoLimite");
            boolean igual = esperado != null && depois.getLimite() != null
                    && depois.getLimite().setScale(2, RoundingMode.HALF_EVEN)
                    .compareTo(esperado.setScale(2, RoundingMode.HALF_EVEN)) == 0;
            return igual
                    ? ResultadoValidacao.aprovado("Limite conferido: agora é R$ " + depois.getLimite().setScale(2, RoundingMode.HALF_EVEN) + ".")
                    : ResultadoValidacao.reprovado("O limite no banco não bate com o pedido.");
        }
        if (rota == RotaSistema.PESSOA_DADOS_ATUALIZAR) {
            String esperado = LeitorParametros.textoOuNulo(p, "nome", "razaoSocial", "novoNome");
            return esperado == null || esperado.equalsIgnoreCase(depois.getNome())
                    ? ResultadoValidacao.aprovado("Nome conferido: \"" + depois.getNome() + "\".")
                    : ResultadoValidacao.reprovado("O nome no banco (\"" + depois.getNome() + "\") não bate com o pedido.");
        }
        return ResultadoValidacao.naoSeAplica();
    }
}
