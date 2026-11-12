package br.com.senac.integracao.ia.nota;

import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.CatalogoMensagens;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.*;

/**
 * Validador de regras de negócio, conciliação financeira, limites de crédito
 * e disponibilidade de estoque (inclusive decomposição de kits/conjuntos) para Notas.
 */
public class ValidadorRegrasNota {

    public ResultadoValidacaoNota validar(MontadorNota montador, RoteadorController roteador) {
        if (montador == null) {
            return ResultadoValidacaoNota.falha(BigDecimal.ZERO, BigDecimal.ZERO, List.of("Nota nula ou não inicializada."));
        }

        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        BigDecimal totalItens = montador.calcularTotalItens();
        BigDecimal totalPago = montador.calcularTotalPagamentos();

        if (montador.getTipo() == MontadorNota.TipoNota.VENDA_FIFO
                || montador.getTipo() == MontadorNota.TipoNota.VENDA_BALCAO
                || montador.getTipo() == MontadorNota.TipoNota.VENDA_LOTE) {

            validarVenda(montador, roteador, totalItens, totalPago, erros, avisos);
        } else if (montador.getTipo() == MontadorNota.TipoNota.COMPRA) {
            validarCompra(montador, roteador, totalItens, totalPago, erros, avisos);
        }

        if (!erros.isEmpty()) {
            return ResultadoValidacaoNota.falha(totalItens, totalPago, erros);
        }

        BigDecimal troco = BigDecimal.ZERO;
        if (totalPago.compareTo(totalItens) > 0) {
            troco = totalPago.subtract(totalItens);
        }

        return ResultadoValidacaoNota.sucesso(totalItens, totalPago, troco, avisos);
    }

    private void validarVenda(
            MontadorNota montador,
            RoteadorController roteador,
            BigDecimal totalItens,
            BigDecimal totalPago,
            List<String> erros,
            List<String> avisos) {

        if (montador.getItens().isEmpty()) {
            erros.add("A venda precisa conter ao menos um item de produto.");
        }

        // 1. Validação de Pagamento e Troco
        if (totalPago.compareTo(totalItens) < 0 && !montador.getPagamentos().isEmpty()) {
            BigDecimal falta = totalItens.subtract(totalPago);
            erros.add(CatalogoMensagens.erroNotaPagamentoInsuficiente(
                    FormatadorMoeda.moeda(totalPago),
                    FormatadorMoeda.moeda(totalItens),
                    FormatadorMoeda.moeda(falta)));
        } else if (totalPago.compareTo(totalItens) > 0) {
            BigDecimal excesso = totalPago.subtract(totalItens);
            BigDecimal totalNaoDinheiro = montador.getPagamentos().stream()
                    .filter(p -> !"DINHEIRO".equalsIgnoreCase(p.codigoForma()))
                    .map(MontadorNota.PagamentoNota::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalNaoDinheiro.compareTo(totalItens) > 0) {
                erros.add(CatalogoMensagens.erroNotaTrocoApenasDinheiro(
                        FormatadorMoeda.moeda(totalPago),
                        FormatadorMoeda.moeda(totalItens)));
            } else {
                avisos.add(CatalogoMensagens.notaTrocoCalculado(FormatadorMoeda.moeda(excesso)));
            }
        }

        // 2. Validação de Estoque (Produtos Simples e Decomposição de Kits)
        if (roteador != null) {
            for (MontadorNota.ItemNota item : montador.getItens()) {
                if (item.idProduto() != null) {
                    Produto p = roteador.produtos().buscarPorId(item.idProduto());
                    if (p != null) {
                        String umSigla = p.getUm() != null ? p.getUm().getSigla() : "UN";
                        BigDecimal saldoTotal = roteador.estoque().consultarSaldoTotalProduto(p.getId());
                        BigDecimal saldoDisp = saldoTotal != null ? saldoTotal : BigDecimal.ZERO;

                        if (saldoDisp.compareTo(item.quantidade()) < 0) {
                            erros.add(CatalogoMensagens.erroNotaEstoqueInsuficiente(
                                    p.getNome(), p.getId(),
                                    item.quantidade().stripTrailingZeros().toPlainString(), umSigla,
                                    saldoDisp.stripTrailingZeros().toPlainString()));
                        }
                    }
                }
            }
        }

        // 3. Validação de Limite de Crédito para Vendas a Prazo
        if (roteador != null && montador.getIdPessoa() != null) {
            BigDecimal totalPrazo = montador.getPagamentos().stream()
                    .filter(p -> p.codigoForma().contains("PRAZO") || p.codigoForma().contains("BOLETO") || p.parcelas() > 1)
                    .map(MontadorNota.PagamentoNota::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalPrazo.compareTo(BigDecimal.ZERO) > 0) {
                Pessoa cliente = roteador.pessoas().buscarPorId(montador.getIdPessoa());
                if (cliente != null && cliente.getLimite() != null) {
                    if (totalPrazo.compareTo(cliente.getLimite()) > 0) {
                        erros.add(CatalogoMensagens.erroNotaLimiteCredito(
                                FormatadorMoeda.moeda(totalPrazo),
                                cliente.getNome(),
                                FormatadorMoeda.moeda(cliente.getLimite())));
                    }
                }
            }
        }
    }

    private void validarCompra(
            MontadorNota montador,
            RoteadorController roteador,
            BigDecimal totalItens,
            BigDecimal totalPago,
            List<String> erros,
            List<String> avisos) {

        if (montador.getItens().isEmpty()) {
            erros.add("A nota de compra precisa conter ao menos um item de produto ou ressarcimento.");
        }
    }
}
