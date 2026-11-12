package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.repository.dao.produto.ProdutoDAO;
import br.com.senac.repository.modal.produto.ConjuntoProduto;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Conferências das instruções de produto: cadastro, nome/descrição, preço, exclusão e kit. */
public class ValidadorProduto implements ValidadorExecucao {

    private static final Set<RotaSistema> ROTAS = EnumSet.of(
            RotaSistema.PRODUTO_FISICO_CADASTRAR,
            RotaSistema.PRODUTO_DIGITAL_CADASTRAR,
            RotaSistema.PRODUTO_CATALOGO_ATUALIZAR,
            RotaSistema.PRODUTO_CATALOGO_ATUALIZAR_PRECO,
            RotaSistema.PRODUTO_CATALOGO_EXCLUIR,
            RotaSistema.PRODUTO_KIT_ADICIONAR_COMPONENTE);

    @Override
    public boolean aplicaA(RotaSistema rota) {
        return ROTAS.contains(rota);
    }

    // ==================== ANTES ====================

    @Override
    public Map<String, Object> capturarEstadoAntes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> foto = new LinkedHashMap<>();
        Produto alvo = localizarAlvo(item.getRota(), item.getParametros(), roteador);
        if (alvo != null) {
            foto.put("id", alvo.getId());
            foto.put("nome", alvo.getNome());
            foto.put("preco", alvo.getPrecoVenda());
            foto.put("componentes", alvo.getItensConjunto() != null ? alvo.getItensConjunto().size() : 0);
        }
        return foto;
    }

    @Override
    public ResultadoValidacao antes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        switch (item.getRota()) {
            case PRODUTO_FISICO_CADASTRAR, PRODUTO_DIGITAL_CADASTRAR -> {
                String nome = LeitorParametros.textoOuNulo(p, "nome");
                if (nome == null) {
                    return ResultadoValidacao.reprovado("Falta o nome do produto.");
                }
                boolean digital = item.getRota() == RotaSistema.PRODUTO_DIGITAL_CADASTRAR;
                String sigla = digital ? "UN" : LeitorParametros.textoOuNulo(p, "siglaUm");
                sigla = sigla != null ? sigla.toUpperCase() : "UN";
                if (!roteador.produtos().unidadeExiste(sigla)) {
                    return ResultadoValidacao.reprovado("A unidade de medida \"" + sigla + "\" não está cadastrada.");
                }
                Produto igual = roteador.produtos().buscarPorChaveUnica(nome, sigla, false);
                if (igual != null) {
                    return ResultadoValidacao.reprovado(ProdutoDAO.montarMensagemDuplicado(nome, sigla, false, igual.getId()));
                }
                return ResultadoValidacao.aprovado("O nome \"" + nome + "\" na unidade " + sigla + " está livre.");
            }
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                Long idKit = LeitorParametros.longOuNulo(p, "idProdutoKit");
                Long idComp = LeitorParametros.longOuNulo(p, "idProdutoComponente");
                BigDecimal qtd = LeitorParametros.decimalOuNulo(p, "quantidade");
                Produto kit = idKit != null ? roteador.produtos().buscarPorId(idKit) : null;
                if (kit == null) {
                    return ResultadoValidacao.reprovado("Não encontrei o produto do conjunto (#" + idKit + ").");
                }
                if (idComp == null || roteador.produtos().buscarPorId(idComp) == null) {
                    return ResultadoValidacao.reprovado("Não encontrei o item que você quer colocar no conjunto (#" + idComp + ").");
                }
                if (idKit.equals(idComp)) {
                    return ResultadoValidacao.reprovado("Um conjunto não pode conter ele mesmo.");
                }
                if (qtd != null && qtd.signum() <= 0) {
                    return ResultadoValidacao.reprovado("A quantidade do item no conjunto precisa ser maior que zero.");
                }
                if (!kit.isConjunto()) {
                    // Ao receber o primeiro componente o produto vira conjunto: não pode colidir com outro conjunto igual.
                    String siglaKit = kit.getUm() != null ? kit.getUm().getSigla() : null;
                    Produto conflito = roteador.produtos().buscarPorChaveUnica(kit.getNome(), siglaKit, true);
                    if (conflito != null && !conflito.getId().equals(kit.getId())) {
                        return ResultadoValidacao.reprovado(ProdutoDAO.montarMensagemDuplicado(
                                kit.getNome(), siglaKit, true, conflito.getId()));
                    }
                }
                return ResultadoValidacao.aprovado("O conjunto e o item existem.");
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                Produto alvo = localizarAlvo(item.getRota(), p, roteador);
                if (alvo == null) {
                    return ResultadoValidacao.reprovado("Não encontrei o produto para mudar o preço.");
                }
                BigDecimal novo = LeitorParametros.decimalOuNulo(p, "novoPreco");
                if (novo == null || novo.signum() < 0) {
                    return ResultadoValidacao.reprovado("O novo preço é inválido.");
                }
                return ResultadoValidacao.aprovado("Hoje \"" + alvo.getNome() + "\" custa R$ " + moeda(alvo.getPrecoVenda()) + ".");
            }
            default -> {
                Produto alvo = localizarAlvo(item.getRota(), p, roteador);
                if (alvo == null) {
                    return ResultadoValidacao.reprovado("Não encontrei o produto indicado.");
                }
                return ResultadoValidacao.aprovado("Produto \"" + alvo.getNome() + "\" encontrado.");
            }
        }
    }

    // ==================== DEPOIS ====================

    @Override
    public ResultadoValidacao depois(ItemBandeja item, Object resultado,
                                     Map<String, Object> antes, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        switch (item.getRota()) {
            case PRODUTO_FISICO_CADASTRAR, PRODUTO_DIGITAL_CADASTRAR -> {
                if (!(resultado instanceof Produto criado) || criado.getId() == null) {
                    return ResultadoValidacao.reprovado("O produto não retornou um código depois de salvar.");
                }
                Produto noBanco = roteador.produtos().buscarPorId(criado.getId());
                return noBanco != null
                        ? ResultadoValidacao.aprovado("Produto #" + noBanco.getId() + " conferido no banco.")
                        : ResultadoValidacao.reprovado("Salvei, mas não achei o produto no banco ao conferir.");
            }
            case PRODUTO_CATALOGO_ATUALIZAR_PRECO -> {
                Produto depois = recarregar(resultado, antes, roteador);
                BigDecimal esperado = LeitorParametros.decimalOuNulo(p, "novoPreco");
                if (depois == null || esperado == null || depois.getPrecoVenda() == null) {
                    return ResultadoValidacao.reprovado("Não consegui conferir o preço depois de salvar.");
                }
                boolean igual = depois.getPrecoVenda().setScale(2, RoundingMode.HALF_EVEN)
                        .compareTo(esperado.setScale(2, RoundingMode.HALF_EVEN)) == 0;
                String antesTxt = antes.get("preco") instanceof BigDecimal b ? "R$ " + moeda(b) : "valor anterior";
                return igual
                        ? ResultadoValidacao.aprovado("Preço conferido: era " + antesTxt + " e agora é R$ " + moeda(depois.getPrecoVenda()) + ".")
                        : ResultadoValidacao.reprovado("O preço no banco (R$ " + moeda(depois.getPrecoVenda()) + ") não bate com o pedido (R$ " + moeda(esperado) + ").");
            }
            case PRODUTO_CATALOGO_ATUALIZAR -> {
                Produto depois = recarregar(resultado, antes, roteador);
                String novoNome = LeitorParametros.textoOuNulo(p, "novoNome", "nome");
                if (depois == null) {
                    return ResultadoValidacao.reprovado("Não achei o produto no banco ao conferir.");
                }
                if (novoNome != null && !novoNome.equalsIgnoreCase(depois.getNome())) {
                    return ResultadoValidacao.reprovado("O nome no banco (\"" + depois.getNome() + "\") não bate com o pedido (\"" + novoNome + "\").");
                }
                return ResultadoValidacao.aprovado("Dados do produto #" + depois.getId() + " conferidos.");
            }
            case PRODUTO_CATALOGO_EXCLUIR -> {
                Object id = antes.get("id");
                if (id instanceof Long idProd && roteador.produtos().buscarPorId(idProd) != null) {
                    return ResultadoValidacao.reprovado("O produto ainda aparece no banco depois da exclusão.");
                }
                return ResultadoValidacao.aprovado("Produto removido e conferido.");
            }
            case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                Long idKit = LeitorParametros.longOuNulo(p, "idProdutoKit");
                Long idComp = LeitorParametros.longOuNulo(p, "idProdutoComponente");
                Produto kit = idKit != null ? roteador.produtos().buscarPorId(idKit) : null;
                if (kit == null) {
                    return ResultadoValidacao.reprovado("Não achei o conjunto no banco ao conferir.");
                }
                boolean contem = kit.getItensConjunto() != null && kit.getItensConjunto().stream()
                        .map(ConjuntoProduto::getItem)
                        .anyMatch(i -> i != null && i.getId() != null && i.getId().equals(idComp));
                return kit.isConjunto() && contem
                        ? ResultadoValidacao.aprovado("O conjunto #" + idKit + " agora tem o item #" + idComp + ".")
                        : ResultadoValidacao.reprovado("O item #" + idComp + " não apareceu dentro do conjunto #" + idKit + ".");
            }
            default -> {
                return ResultadoValidacao.naoSeAplica();
            }
        }
    }

    // ==================== APOIO ====================

    private Produto localizarAlvo(RotaSistema rota, Map<String, Object> p, RoteadorController roteador) {
        Long id = LeitorParametros.longOuNulo(p, "idProduto", "idProdutoKit");
        if (id != null) {
            Produto porId = roteador.produtos().buscarPorId(id);
            if (porId != null) return porId;
        }
        String nome = LeitorParametros.textoOuNulo(p, "nomeProduto", "nomeAtual", "nome");
        if (nome != null && rota != RotaSistema.PRODUTO_FISICO_CADASTRAR && rota != RotaSistema.PRODUTO_DIGITAL_CADASTRAR) {
            return roteador.produtos().buscarPorNome(nome);
        }
        return null;
    }

    private Produto recarregar(Object resultado, Map<String, Object> antes, RoteadorController roteador) {
        Long id = resultado instanceof Produto r && r.getId() != null ? r.getId()
                : (antes.get("id") instanceof Long l ? l : null);
        return id != null ? roteador.produtos().buscarPorId(id) : null;
    }

    private static String moeda(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
