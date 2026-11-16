package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.nota.NotaCompra;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conferências pré e pós-execução das notas fiscais (compra e venda), validando existência real
 * de fornecedor/cliente e de todos os produtos referenciados por ID ou nome antes da persistência.
 */
public class ValidadorNota implements ValidadorExecucao {

    private static final Set<RotaSistema> ROTAS = EnumSet.of(
            RotaSistema.NOTA_COMPRA_EMITIR,
            RotaSistema.NOTA_VENDA_EMITIR_FIFO,
            RotaSistema.NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO);

    @Override
    public boolean aplicaA(RotaSistema rota) {
        return ROTAS.contains(rota);
    }

    @Override
    public Map<String, Object> capturarEstadoAntes(ItemBandeja item, RoteadorController roteador) {
        return new LinkedHashMap<>();
    }

    @Override
    public ResultadoValidacao antes(ItemBandeja item, RoteadorController roteador) {
        Map<String, Object> p = item.getParametros();
        if (item.getRota() == RotaSistema.NOTA_COMPRA_EMITIR) {
            if (p.get("notaCompra") instanceof NotaCompra || p.containsKey("idNotaCompra") || p.containsKey("idNota")) {
                return ResultadoValidacao.aprovado("Nota de compra direta informada.");
            }
            Pessoa fornecedor = localizarPessoa(p, "idFornecedor", "nomeFornecedor", "fornecedor", true, roteador);
            if (fornecedor == null) {
                String ref = LeitorParametros.textoOuNulo(p, "nomeFornecedor", "idFornecedor", "fornecedor", "idPessoa");
                return ResultadoValidacao.reprovado("Fornecedor não encontrado para a nota de compra ("
                        + (ref != null ? ref : "não informado") + ").");
            }
            String erroItens = validarListaItensNota(p, false, roteador);
            if (erroItens != null) {
                return ResultadoValidacao.reprovado(erroItens);
            }
            return ResultadoValidacao.aprovado("Fornecedor e itens da nota de compra conferidos.");
        }

        if (item.getRota() == RotaSistema.NOTA_VENDA_EMITIR_FIFO) {
            String refCli = LeitorParametros.textoOuNulo(p, "nomeCliente", "idCliente", "cliente");
            if (refCli != null) {
                Pessoa cli = localizarPessoa(p, "idCliente", "nomeCliente", "cliente", false, roteador);
                if (cli == null) {
                    return ResultadoValidacao.reprovado("Cliente não encontrado para a venda (" + refCli + ").");
                }
            }
        }

        String erroItens = validarListaItensNota(p, true, roteador);
        if (erroItens != null) {
            return ResultadoValidacao.reprovado(erroItens);
        }
        return ResultadoValidacao.aprovado("Itens e estoque da nota de venda conferidos.");
    }

    @Override
    public ResultadoValidacao depois(ItemBandeja item, Object resultado,
                                     Map<String, Object> antes, RoteadorController roteador) {
        if (resultado instanceof Nota nota && nota.getId() != null) {
            return ResultadoValidacao.aprovado("Nota #" + nota.getId() + " emitida e conferida no banco.");
        }
        return ResultadoValidacao.reprovado("A emissão da nota não retornou um número de nota gravado no banco.");
    }

    private Pessoa localizarPessoa(Map<String, Object> p, String kId, String kNome, String kObj,
                                   boolean fornecedor, RoteadorController roteador) {
        if (p.get(kObj) instanceof Pessoa pes) return pes;
        Long id = LeitorParametros.longOuNulo(p, kId, "idPessoa");
        if (id != null) {
            Pessoa pes = roteador.pessoas().buscarPorId(id);
            if (pes != null) return pes;
        }
        String termo = LeitorParametros.textoOuNulo(p, kNome, kObj, kId, "idPessoa");
        if (termo == null) {
            if (fornecedor) {
                List<Pessoa> lista = roteador.pessoas().listarFornecedores();
                return lista != null && !lista.isEmpty() ? lista.get(0) : null;
            }
            return null;
        }
        String limpo = termo.startsWith("#") ? termo.substring(1).trim() : termo.trim();
        List<Pessoa> base = fornecedor ? roteador.pessoas().listarFornecedores() : roteador.pessoas().listarClientes();
        if (base != null) {
            for (Pessoa pes : base) {
                if (pes.getNome() != null && pes.getNome().equalsIgnoreCase(limpo)) return pes;
            }
            for (Pessoa pes : base) {
                if (pes.getNome() != null && pes.getNome().toLowerCase(Locale.ROOT).contains(limpo.toLowerCase(Locale.ROOT))) {
                    return pes;
                }
            }
        }
        List<Pessoa> porTermo = roteador.pessoas().buscarPessoasPorTermo(limpo, null);
        return porTermo != null && !porTermo.isEmpty() ? porTermo.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    private String validarListaItensNota(Map<String, Object> p, boolean verificarEstoque, RoteadorController roteador) {
        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }
        if (!(itensObj instanceof List<?> list) || list.isEmpty()) {
            if (itensObj instanceof Map<?, ?> map && !map.isEmpty()) {
                return null;
            }
            return "A nota precisa conter ao menos 1 item válido.";
        }
        for (Object el : list) {
            if (el instanceof Map<?, ?> rawMap) {
                Map<String, Object> itemMap = (Map<String, Object>) rawMap;
                Produto prod = localizarProdutoItem(itemMap, roteador);
                if (prod == null) {
                    String ref = LeitorParametros.textoOuNulo(itemMap, "nomeProduto", "nome", "idProduto");
                    return "Produto não encontrado no item da nota (" + (ref != null ? ref : "não informado") + ").";
                }
                BigDecimal qtd = LeitorParametros.decimalOuNulo(itemMap, "quantidade");
                if (qtd != null && qtd.signum() <= 0) {
                    return "Quantidade inválida para o produto \"" + prod.getNome() + "\".";
                }
                if (verificarEstoque && prod.getId() != null && qtd != null) {
                    BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(prod.getId());
                    if (saldo == null || saldo.compareTo(qtd) < 0) {
                        return "Estoque insuficiente para \"" + prod.getNome() + "\": disponível "
                                + (saldo != null ? saldo : "0") + ", solicitado " + qtd + ".";
                    }
                }
            }
        }
        return null;
    }

    private Produto localizarProdutoItem(Map<String, Object> map, RoteadorController roteador) {
        Long id = LeitorParametros.longOuNulo(map, "idProduto", "id");
        if (id != null) {
            Produto p = roteador.produtos().buscarPorId(id);
            if (p != null) return p;
        }
        String nome = LeitorParametros.textoOuNulo(map, "nomeProduto", "nome", "idProduto");
        if (nome == null) return null;
        Produto exato = roteador.produtos().buscarPorNome(nome);
        if (exato != null) return exato;
        List<Produto> candidatos = roteador.produtos().buscarPorTermo(nome);
        return candidatos != null && !candidatos.isEmpty() ? candidatos.get(0) : null;
    }
}
