package br.com.senac.integracao.ia;

import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.modal.nota.*;
import br.com.senac.repository.modal.pagamento.*;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.pessoa.Registro;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.ProdutoFisico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Records imutáveis (Java 21) e Motor de Contexto de Busca em Sequência/Cascata para o ChatBot e JavaFX.
 *
 * <p>Permite tanto buscas em cascata direta ({@code >>}) quanto **buscas em sequência conversacional**
 * mantendo o foco da última entidade consultada na sessão ({@link FocoBuscaSessao}):
 * <ol>
 *   <li>Passo 1: Buscar a <b>Nota</b> (ex: {@code $$ R= #Nota id> 1;} ou {@code "busca a nota 1"})</li>
 *   <li>Passo 2: Pedir <b>o fornecedor ou cliente dessa nota</b> (ex: {@code "agora o fornecedor dessa nota"} ou {@code $$ R= #Fornecedor da_nota;})</li>
 *   <li>Passo 3: Pedir <b>os contatos dessa pessoa</b> (ex: {@code "os contatos dele"} ou {@code $$ R= #Contato da_pessoa;})</li>
 *   <li>Passo 4: Pedir <b>os produtos dessa nota</b> (ex: {@code "os produtos dessa nota"} ou {@code $$ R= #Produto da_nota;})</li>
 *   <li>Passo 5: Pedir <b>os pagamentos dessa nota</b> (ex: {@code "os pagamentos dessa nota"} ou {@code $$ R= #Pagamento da_nota;})</li>
 * </ol>
 */
public final class RetornoChatBotUI {

    private RetornoChatBotUI() {
    }

    public sealed interface RetornoFormatavel permits
            RetornoNotaDetalhada,
            RetornoPessoaDetalhada,
            RetornoContatosPessoa,
            RetornoProdutosDaNota,
            RetornoPagamentosDaNota,
            RetornoProdutoCascata,
            ComandoJanelaUI,
            ListaRetornosSequenciais {
        String formatarTexto();
    }

    /**
     * Mantém em memória da sessão a última Nota, Pessoa (Cliente/Fornecedor) e Produto consultados,
     * permitindo fazer perguntas em sequência ("agora o fornecedor dessa nota", "os produtos dessa nota",
     * "os contatos dele", "os pagamentos dessa nota") sem precisar repetir o ID!
     */
    public static final class FocoBuscaSessao {
        private volatile Long idUltimaNota;
        private volatile Long idUltimaPessoa;
        private volatile Long idUltimoProduto;
        private final List<RetornoFormatavel> ultimosRecordsGerados = Collections.synchronizedList(new ArrayList<>());

        public void registrarNotaEmFoco(Nota nota) {
            if (nota != null) {
                this.idUltimaNota = nota.getId();
                if (nota.getPessoa() != null && nota.getPessoa().getId() != null) {
                    this.idUltimaPessoa = nota.getPessoa().getId();
                }
            }
        }

        public void registrarPessoaEmFoco(Pessoa pessoa) {
            if (pessoa != null && pessoa.getId() != null) {
                this.idUltimaPessoa = pessoa.getId();
            }
        }

        public void registrarProdutoEmFoco(Produto produto) {
            if (produto != null && produto.getId() != null) {
                this.idUltimoProduto = produto.getId();
            }
        }

        public void registrarRecord(RetornoFormatavel rec) {
            if (rec != null) {
                ultimosRecordsGerados.add(rec);
            }
        }

        public Long getIdUltimaNota() {
            return idUltimaNota;
        }

        public Long getIdUltimaPessoa() {
            return idUltimaPessoa;
        }

        public Long getIdUltimoProduto() {
            return idUltimoProduto;
        }

        public List<RetornoFormatavel> getUltimosRecordsGerados() {
            return List.copyOf(ultimosRecordsGerados);
        }

        public void limpar() {
            this.idUltimaNota = null;
            this.idUltimaPessoa = null;
            this.idUltimoProduto = null;
            this.ultimosRecordsGerados.clear();
        }
    }

    // ==================== 1. RECORD DE NOTA (SIMPLES OU EM CASCATA) ====================

    public record RetornoNotaDetalhada(
            Long idNota,
            String tipoNota,
            String dataEmissao,
            BigDecimal valorTotal,
            String descricao,
            Long idPessoa,
            String nomePessoa,
            String papelPessoa,
            int quantidadeItens,
            List<String> resumoItens,
            List<String> resumoPagamentos,
            List<String> contatosPessoa
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Nota #%d (%s) — Data: %s — Valor Total: R$ %s\n",
                    idNota, tipoNota, dataEmissao, moeda(valorTotal)));
            if (descricao != null && !descricao.isBlank()) {
                sb.append("  Descrição: ").append(descricao).append("\n");
            }
            if (nomePessoa != null && !nomePessoa.isBlank()) {
                sb.append(String.format("  %s da Nota: #%d - %s\n",
                        papelPessoa, idPessoa != null ? idPessoa : 0L, nomePessoa));
            }
            if (contatosPessoa != null && !contatosPessoa.isEmpty()) {
                sb.append("  Contatos de ").append(nomePessoa).append(": ").append(String.join(" | ", contatosPessoa)).append("\n");
            }
            if (resumoItens != null && !resumoItens.isEmpty()) {
                sb.append("  Produtos da Nota #").append(idNota).append(" (").append(resumoItens.size()).append(" ite").append(resumoItens.size() == 1 ? "m" : "ns").append("):\n");
                for (String it : resumoItens) {
                    sb.append("    - ").append(it).append("\n");
                }
            }
            if (resumoPagamentos != null && !resumoPagamentos.isEmpty()) {
                sb.append("  Pagamentos da Nota #").append(idNota).append(" (").append(resumoPagamentos.size()).append("):\n");
                for (String pg : resumoPagamentos) {
                    sb.append("    - ").append(pg).append("\n");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 2. RECORD DE FORNECEDOR / CLIENTE / PESSOA ====================

    public record RetornoPessoaDetalhada(
            Long idPessoa,
            String nome,
            String papel,
            String tipoPessoa,
            String documentoPrincipal,
            BigDecimal limiteCredito,
            Long idNotaOrigemContexto,
            List<String> contatos,
            List<String> notasEmitidas,
            List<String> pagamentos
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            String vinculoNota = idNotaOrigemContexto != null ? " (Vinculado à Nota #" + idNotaOrigemContexto + ")" : "";
            sb.append(String.format("%s #%d - %s%s\n", papel.toUpperCase(Locale.ROOT), idPessoa, nome, vinculoNota));
            sb.append(String.format("  Tipo: %s | Papel: %s | Documento: %s | Limite de Crédito: R$ %s\n",
                    tipoPessoa, papel,
                    documentoPrincipal != null && !documentoPrincipal.isBlank() ? documentoPrincipal : "Não informado",
                    moeda(limiteCredito)));

            if (contatos != null && !contatos.isEmpty()) {
                sb.append("  Contatos e Registros (").append(contatos.size()).append("):\n");
                for (String c : contatos) {
                    sb.append("    - ").append(c).append("\n");
                }
            }
            if (notasEmitidas != null && !notasEmitidas.isEmpty()) {
                sb.append("  Notas Vinculadas a ").append(nome).append(" (").append(notasEmitidas.size()).append("):\n");
                for (String n : notasEmitidas) {
                    sb.append("    - ").append(n).append("\n");
                }
            }
            if (pagamentos != null && !pagamentos.isEmpty()) {
                sb.append("  Pagamentos de ").append(nome).append(" (").append(pagamentos.size()).append("):\n");
                for (String pg : pagamentos) {
                    sb.append("    - ").append(pg).append("\n");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 3. RECORD DE CONTATOS E DOCUMENTOS DA PESSOA ====================

    public record ItemContato(String tipo, String valor) {
        @Override
        public String toString() {
            return tipo + ": " + valor;
        }
    }

    public record RetornoContatosPessoa(
            Long idPessoa,
            String nomePessoa,
            String papelPessoa,
            Long idNotaContexto,
            List<ItemContato> contatos
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            String refNota = idNotaContexto != null ? " (referente à Nota #" + idNotaContexto + ")" : "";
            sb.append(String.format("Contatos e Documentos de #%d - %s (%s)%s:\n",
                    idPessoa, nomePessoa, papelPessoa, refNota));
            if (contatos == null || contatos.isEmpty()) {
                sb.append("  Nenhum contato ou documento adicional registrado para esta pessoa.");
            } else {
                for (ItemContato c : contatos) {
                    sb.append(String.format("  - %s: %s\n", c.tipo(), c.valor()));
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 4. RECORD DOS PRODUTOS DE UMA NOTA ====================

    public record ItemProdutoNota(
            Long idProduto,
            String nomeProduto,
            String unidade,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            BigDecimal subtotal,
            Long idLote,
            BigDecimal saldoDisponivelLote
    ) {
        public String formatarLinha() {
            return String.format("#%d - %s — %s %s x R$ %s = R$ %s%s",
                    idProduto != null ? idProduto : 0L,
                    nomeProduto,
                    quantidade != null ? quantidade.stripTrailingZeros().toPlainString() : "0",
                    unidade,
                    moeda(valorUnitario),
                    moeda(subtotal),
                    idLote != null ? " (Lote #" + idLote + ")" : "");
        }
    }

    public record RetornoProdutosDaNota(
            Long idNota,
            String tipoNota,
            String nomePessoaDaNota,
            BigDecimal valorTotalNota,
            List<ItemProdutoNota> produtos
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Produtos da %s #%d (%s) — Total da Nota: R$ %s (%d ite%s):\n",
                    tipoNota, idNota, nomePessoaDaNota, moeda(valorTotalNota),
                    produtos.size(), produtos.size() == 1 ? "m" : "ns"));
            for (ItemProdutoNota p : produtos) {
                sb.append("  - ").append(p.formatarLinha()).append("\n");
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 5. RECORD DOS PAGAMENTOS DE UMA NOTA OU PESSOA ====================

    public record ItemPagamentoDetalhe(
            String formaPagamento,
            String tipoLancamento,
            BigDecimal valor,
            String dataOuVencimento,
            String status
    ) {
        public String formatarLinha() {
            return String.format("%s (%s) — Valor: R$ %s — Data: %s [%s]",
                    formaPagamento, tipoLancamento, moeda(valor), dataOuVencimento, status);
        }
    }

    public record RetornoPagamentosDaNota(
            Long idNota,
            String tipoNota,
            String nomePessoa,
            BigDecimal valorTotalNota,
            BigDecimal totalPago,
            List<ItemPagamentoDetalhe> pagamentos
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            if (idNota != null) {
                sb.append(String.format("Pagamentos da %s #%d (%s) — Total da Nota: R$ %s:\n",
                        tipoNota, idNota, nomePessoa, moeda(valorTotalNota)));
            } else {
                sb.append(String.format("Pagamentos registrados (%s):\n", nomePessoa != null ? nomePessoa : "Geral"));
            }
            if (pagamentos == null || pagamentos.isEmpty()) {
                sb.append("  - Pagamento integral de R$ ").append(moeda(valorTotalNota)).append(" vinculado à emissão da Nota.");
            } else {
                for (ItemPagamentoDetalhe pg : pagamentos) {
                    sb.append("  - ").append(pg.formatarLinha()).append("\n");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 6. RECORD DE PRODUTO EM CASCATA ====================

    public record RetornoProdutoCascata(
            Long id,
            String nome,
            String unidade,
            BigDecimal precoVenda,
            BigDecimal saldoEstoque,
            String dimensoesTexto,
            List<String> lotesFifo,
            List<String> fornecedores,
            List<String> notasMovimentadas
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Produto #%d - %s (%s) — Preço: R$ %s, Estoque atual: %s %s\n",
                    id, nome, unidade, moeda(precoVenda),
                    saldoEstoque != null ? saldoEstoque.stripTrailingZeros().toPlainString() : "0", unidade));
            if (dimensoesTexto != null && !dimensoesTexto.isBlank()) {
                sb.append("  Dimensões: ").append(dimensoesTexto).append("\n");
            }
            if (lotesFifo != null && !lotesFifo.isEmpty()) {
                sb.append("  Lotes em Estoque FIFO (").append(lotesFifo.size()).append("):\n");
                for (String l : lotesFifo) {
                    sb.append("    - ").append(l).append("\n");
                }
            }
            if (fornecedores != null && !fornecedores.isEmpty()) {
                sb.append("  Fornecedores deste Produto (").append(fornecedores.size()).append("):\n");
                for (String f : fornecedores) {
                    sb.append("    - ").append(f).append("\n");
                }
            }
            if (notasMovimentadas != null && !notasMovimentadas.isEmpty()) {
                sb.append("  Notas Relacionadas (").append(notasMovimentadas.size()).append("):\n");
                for (String n : notasMovimentadas) {
                    sb.append("    - ").append(n).append("\n");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== 7. RECORD DE JANELA JAVAFX (abrir>janela>...) ====================

    public record ComandoJanelaUI(
            String idJanela,
            String titulo,
            String modulo,
            String fxml,
            String rotaPrincipal,
            String comandoOriginal,
            List<Map<String, Object>> itensPrePreenchidosBandeja
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            String extra = (itensPrePreenchidosBandeja != null && !itensPrePreenchidosBandeja.isEmpty())
                    ? String.format(" — Pré-carregada com %d registro(s) da Bandeja de Contexto", itensPrePreenchidosBandeja.size())
                    : "";
            return ContextoIABuilder.formatarPropriedade(
                    "ia.ui.janela.aberta",
                    "Janela JavaFX preparada: [%s] %s (Modulo: %s, FXML: %s, Rota: %s)%s",
                    idJanela, titulo, modulo, fxml, rotaPrincipal, extra);
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    public record ListaRetornosSequenciais(
            List<RetornoFormatavel> itens
    ) implements RetornoFormatavel {
        @Override
        public String formatarTexto() {
            if (itens == null || itens.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < itens.size(); i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(itens.get(i).formatarTexto());
            }
            return sb.toString().trim();
        }

        @Override
        public String toString() {
            return formatarTexto();
        }
    }

    // ==================== CONSTRUTORES DE RECORDS A PARTIR DAS ENTIDADES ====================

    @SuppressWarnings("unchecked")
    public static RetornoNotaDetalhada construirRecordNota(
            Nota nota,
            RoteadorController roteador,
            boolean incluirPessoaContatos,
            boolean incluirProdutos,
            boolean incluirPagamentos) {

        String tipoNota = descreverTipoNota(nota);
        Pessoa p = nota.getPessoa();
        String nomePes = p != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), null) : "Consumidor Padrão";
        String papelPes = nota instanceof NotaCompra ? "Fornecedor" : "Cliente";

        List<String> contatos = new ArrayList<>();
        if (incluirPessoaContatos && p != null) {
            for (ItemContato ic : extrairContatosDaPessoa(p, roteador)) {
                contatos.add(ic.toString());
            }
        }

        List<String> itens = new ArrayList<>();
        if (incluirProdutos && nota.getItens() != null) {
            for (Item<? extends Produto> it : nota.getItens()) {
                Produto prod = it.getProduto();
                String um = prod != null && prod.getUm() != null ? prod.getUm().getSigla() : "UN";
                String nmProd = prod != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(prod.getNome(), um) : "Item";
                itens.add(String.format("#%d %s — %s %s x R$ %s = R$ %s",
                        prod != null ? prod.getId() : 0L,
                        nmProd,
                        it.getQuantidade() != null ? it.getQuantidade().stripTrailingZeros().toPlainString() : "0",
                        um,
                        moeda(it.getValor()),
                        moeda(it.getSubtotal())));
            }
        }

        List<String> pags = new ArrayList<>();
        if (incluirPagamentos) {
            for (ItemPagamentoDetalhe pg : extrairPagamentosDaNota(nota, roteador)) {
                pags.add(pg.formatarLinha());
            }
        }

        return new RetornoNotaDetalhada(
                nota.getId(),
                tipoNota,
                nota.getDataGerada() != null ? nota.getDataGerada().toLocalDate().toString() : java.time.LocalDate.now().toString(),
                nota.getValorTotal(),
                nota.getDescricao(),
                p != null ? p.getId() : null,
                nomePes,
                papelPes,
                nota.getItens() != null ? nota.getItens().size() : 0,
                itens,
                pags,
                contatos
        );
    }

    @SuppressWarnings("unchecked")
    public static RetornoPessoaDetalhada construirRecordPessoa(
            Pessoa p,
            Long idNotaContexto,
            RoteadorController roteador,
            boolean incluirContatos,
            boolean incluirNotas,
            boolean incluirPagamentos) {

        String nomeLimpo = CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), null);
        String papel = (p.isCliente() && p.isFornecedor()) ? "Cliente e Fornecedor" : (p.isFornecedor() ? "Fornecedor" : "Cliente");
        String tipoPes = p instanceof PessoaJuridica ? "Pessoa Jurídica" : "Pessoa Física";
        String docPrincipal = "";
        if (p instanceof PessoaFisica pf && pf.getCpf() != null) docPrincipal = pf.getCpf();
        else if (p instanceof PessoaJuridica pj && pj.getCnpj() != null) docPrincipal = pj.getCnpj();

        List<String> contatos = new ArrayList<>();
        if (incluirContatos) {
            for (ItemContato ic : extrairContatosDaPessoa(p, roteador)) {
                contatos.add(ic.toString());
                if (docPrincipal.isBlank() && (ic.tipo().equalsIgnoreCase("CPF") || ic.tipo().equalsIgnoreCase("CNPJ"))) {
                    docPrincipal = ic.valor();
                }
            }
        }

        List<String> notasTxt = new ArrayList<>();
        List<String> pagsTxt = new ArrayList<>();
        if (incluirNotas || incluirPagamentos) {
            try {
                Map<String, Object> hist = roteador.pessoas().consultarHistoricoCompletoPessoa(p.getId(), p.getNome(), null);
                if (hist != null && hist.get("notas") instanceof List<?> listaNotas) {
                    for (Object o : listaNotas) {
                        if (o instanceof Map<?, ?> nm) {
                            if (incluirNotas) {
                                notasTxt.add(String.format("Nota #%s (%s) em %s — Total: R$ %s",
                                        nm.get("idNota"), nm.get("tipoNota"), nm.get("dataGerada"), nm.get("valorTotal")));
                            }
                            if (incluirPagamentos && nm.get("pagamentos") instanceof List<?> lp) {
                                for (Object po : lp) {
                                    if (po instanceof Map<?, ?> pm) {
                                        pagsTxt.add(String.format("Nota #%s: %s — R$ %s (%s)",
                                                nm.get("idNota"), pm.get("forma"), pm.get("valorFinal"), pm.get("tipo")));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new RetornoPessoaDetalhada(
                p.getId(),
                nomeLimpo,
                papel,
                tipoPes,
                docPrincipal,
                p.getLimite(),
                idNotaContexto,
                contatos,
                notasTxt,
                pagsTxt
        );
    }

    public static RetornoContatosPessoa construirRecordContatos(Pessoa p, Long idNotaContexto, RoteadorController roteador) {
        String nomeLimpo = CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), null);
        String papel = (p.isCliente() && p.isFornecedor()) ? "Cliente e Fornecedor" : (p.isFornecedor() ? "Fornecedor" : "Cliente");
        return new RetornoContatosPessoa(
                p.getId(),
                nomeLimpo,
                papel,
                idNotaContexto,
                extrairContatosDaPessoa(p, roteador)
        );
    }

    public static RetornoProdutosDaNota construirRecordProdutosDaNota(Nota nota) {
        String tipoNota = descreverTipoNota(nota);
        String nomePes = nota.getPessoa() != null
                ? CompiladorInstrucoesCurtaService.limparNomeExibicao(nota.getPessoa().getNome(), null)
                : "Consumidor Padrão";
        List<ItemProdutoNota> itens = new ArrayList<>();
        if (nota.getItens() != null) {
            for (Item<? extends Produto> it : nota.getItens()) {
                Produto prod = it.getProduto();
                String um = prod != null && prod.getUm() != null ? prod.getUm().getSigla() : "UN";
                String nmProd = prod != null ? CompiladorInstrucoesCurtaService.limparNomeExibicao(prod.getNome(), um) : "Produto";
                itens.add(new ItemProdutoNota(
                        prod != null ? prod.getId() : null,
                        nmProd,
                        um,
                        it.getQuantidade(),
                        it.getValor(),
                        it.getSubtotal(),
                        it.getId(),
                        it.getQuantidadeDisponivel()
                ));
            }
        }
        return new RetornoProdutosDaNota(nota.getId(), tipoNota, nomePes, nota.getValorTotal(), itens);
    }

    public static RetornoPagamentosDaNota construirRecordPagamentosDaNota(Nota nota, RoteadorController roteador) {
        String tipoNota = descreverTipoNota(nota);
        String nomePes = nota.getPessoa() != null
                ? CompiladorInstrucoesCurtaService.limparNomeExibicao(nota.getPessoa().getNome(), null)
                : "Consumidor Padrão";
        List<ItemPagamentoDetalhe> pags = extrairPagamentosDaNota(nota, roteador);
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemPagamentoDetalhe d : pags) {
            if (d.valor() != null) soma = soma.add(d.valor());
        }
        return new RetornoPagamentosDaNota(nota.getId(), tipoNota, nomePes, nota.getValorTotal(), soma, pags);
    }

    public static RetornoProdutoCascata construirRecordProdutoCascata(
            Produto p,
            RoteadorController roteador,
            boolean incluirEstoque,
            boolean incluirFornecedores,
            boolean incluirNotas) {

        String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
        String nomeLimpo = CompiladorInstrucoesCurtaService.limparNomeExibicao(p.getNome(), um);
        BigDecimal saldo = roteador.estoque().consultarSaldoTotalProduto(p.getId());
        String dim = "";
        if (p instanceof ProdutoFisico pf && pf.possuiDimensoesValidas()) {
            dim = String.format("%scm x %scm x %scm, Peso Líquido: %skg",
                    pf.getAlturaCm(), pf.getLarguraCm(), pf.getComprimentoCm(), pf.getPesoLiquidoKg());
        }

        List<String> lotes = new ArrayList<>();
        if (incluirEstoque) {
            try {
                for (Item<? extends Produto> l : roteador.estoque().listarLotesDisponiveisFIFO(p.getId())) {
                    lotes.add(String.format("Lote #%d — Saldo FIFO: %s %s, Custo Unit.: R$ %s, Validade: %s",
                            l.getId(),
                            l.getQuantidadeDisponivel() != null ? l.getQuantidadeDisponivel().stripTrailingZeros().toPlainString() : "0",
                            um,
                            moeda(l.getValor()),
                            l.getDataValidade() != null ? l.getDataValidade().toString() : "Indeterminada"));
                }
            } catch (Exception ignored) {
            }
        }

        List<String> fornecedores = new ArrayList<>();
        List<String> notasMov = new ArrayList<>();
        if (incluirFornecedores || incluirNotas) {
            try {
                Set<String> fornsUnicos = new LinkedHashSet<>();
                for (Nota n : roteador.notas().listarTodasNotas()) {
                    if (n.getItens() == null) continue;
                    for (Item<? extends Produto> it : n.getItens()) {
                        if (it.getProduto() != null && Objects.equals(it.getProduto().getId(), p.getId())) {
                            String nmPes = n.getPessoa() != null
                                    ? CompiladorInstrucoesCurtaService.limparNomeExibicao(n.getPessoa().getNome(), null)
                                    : "Consumidor Padrão";
                            if (incluirFornecedores && n instanceof NotaCompra && n.getPessoa() != null) {
                                fornsUnicos.add(String.format("#%d %s (Último custo: R$ %s)",
                                        n.getPessoa().getId(), nmPes, moeda(it.getValor())));
                            }
                            if (incluirNotas) {
                                notasMov.add(String.format("%s #%d (%s) — %s: %s %s x R$ %s",
                                        descreverTipoNota(n), n.getId(),
                                        n.getDataGerada() != null ? n.getDataGerada().toLocalDate() : "Hoje",
                                        nmPes,
                                        it.getQuantidade() != null ? it.getQuantidade().stripTrailingZeros().toPlainString() : "0",
                                        um, moeda(it.getValor())));
                            }
                        }
                    }
                }
                fornecedores.addAll(fornsUnicos);
            } catch (Exception ignored) {
            }
        }

        return new RetornoProdutoCascata(p.getId(), nomeLimpo, um, p.getPrecoVenda(), saldo, dim, lotes, fornecedores, notasMov);
    }

    private static List<ItemContato> extrairContatosDaPessoa(Pessoa p, RoteadorController roteador) {
        List<ItemContato> lista = new ArrayList<>();
        Set<String> jaAdicionados = new LinkedHashSet<>();

        if (p instanceof PessoaFisica pf && pf.getCpf() != null && !pf.getCpf().isBlank()) {
            jaAdicionados.add("CPF:" + pf.getCpf());
            lista.add(new ItemContato("CPF", pf.getCpf()));
        }
        if (p instanceof PessoaJuridica pj && pj.getCnpj() != null && !pj.getCnpj().isBlank()) {
            jaAdicionados.add("CNPJ:" + pj.getCnpj());
            lista.add(new ItemContato("CNPJ", pj.getCnpj()));
        }

        try {
            Pessoa recarregada = roteador.pessoas().buscarPorId(p.getId());
            List<Registro> regs = recarregada != null && recarregada.getRegistros() != null
                    ? recarregada.getRegistros()
                    : p.getRegistros();
            if (regs != null) {
                for (Registro r : regs) {
                    String ch = r.getTipo().toUpperCase(Locale.ROOT) + ":" + r.getRegistro();
                    if (jaAdicionados.add(ch)) {
                        lista.add(new ItemContato(r.getTipo().toUpperCase(Locale.ROOT), r.getRegistro()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return lista;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemPagamentoDetalhe> extrairPagamentosDaNota(Nota nota, RoteadorController roteador) {
        List<ItemPagamentoDetalhe> pags = new ArrayList<>();
        String dataPadrao = nota.getDataGerada() != null
                ? nota.getDataGerada().toLocalDate().toString()
                : java.time.LocalDate.now().toString();

        if (nota.getPagamentos() != null && !nota.getPagamentos().isEmpty()) {
            for (NotaPagamento pag : nota.getPagamentos()) {
                String forma = pag.getFormaPagamento() != null ? pag.getFormaPagamento().getNome() : "PIX";
                BigDecimal val = pag.calcularValorFinal() != null ? pag.calcularValorFinal() : pag.getValor();
                String dt = pag.getData() != null ? pag.getData().toString() : dataPadrao;
                String mod = "À Vista";
                String st = "QUITADO";
                if (pag instanceof PagamentoAPrazo pap) {
                    mod = "A Prazo";
                    if (pap.getDataVencimento() != null) dt = pap.getDataVencimento().toString();
                    st = pap.isLiquidado() ? "QUITADO" : "EM ABERTO";
                } else if (pag instanceof PagamentoParcelado pp) {
                    int nParc = pp.getParcelas() != null ? pp.getParcelas().size() : 1;
                    mod = "Parcelado (" + nParc + "x)";
                    st = (pp.getSaldoDevedorParcelas() != null && pp.getSaldoDevedorParcelas().compareTo(BigDecimal.ZERO) == 0)
                            ? "QUITADO" : "PARCIAL";
                }
                pags.add(new ItemPagamentoDetalhe(forma, mod, val, dt, st));
            }
            return pags;
        }

        try {
            if (nota.getPessoa() != null && nota.getPessoa().getId() != null) {
                Map<String, Object> hist = roteador.pessoas().consultarHistoricoCompletoPessoa(
                        nota.getPessoa().getId(), nota.getPessoa().getNome(), null);
                if (hist != null && hist.get("notas") instanceof List<?> listaNotas) {
                    for (Object o : listaNotas) {
                        if (o instanceof Map<?, ?> nm && Objects.equals(String.valueOf(nm.get("idNota")), String.valueOf(nota.getId()))) {
                            if (nm.get("pagamentos") instanceof List<?> lp) {
                                for (Object po : lp) {
                                    if (po instanceof Map<?, ?> pm) {
                                        Object vfObj = pm.get("valorFinal");
                                        BigDecimal val = vfObj != null
                                                ? new BigDecimal(String.valueOf(vfObj))
                                                : nota.getValorTotal();
                                        Object formaObj = pm.get("forma");
                                        Object tipoObj = pm.get("tipo");
                                        pags.add(new ItemPagamentoDetalhe(
                                                formaObj != null ? String.valueOf(formaObj) : "PIX",
                                                tipoObj != null ? String.valueOf(tipoObj) : "À Vista",
                                                val,
                                                dataPadrao,
                                                "REGISTRADO"
                                        ));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (pags.isEmpty()) {
            List<FormaPagamento> ativas = roteador.pagamentos().listarFormasPagamentoAtivas();
            String formaPadrao = !ativas.isEmpty() ? ativas.get(0).getCodigo() : "PIX";
            pags.add(new ItemPagamentoDetalhe(
                    formaPadrao,
                    "Liquidação da Nota #" + nota.getId(),
                    nota.getValorTotal(),
                    dataPadrao,
                    "QUITADO"
            ));
        }
        return pags;
    }

    public static String descreverTipoNota(Nota nota) {
        if (nota instanceof NotaCompra) return "Nota Fiscal de Compra";
        if (nota instanceof NotaVenda) return "Nota Fiscal de Venda";
        if (nota instanceof NotaPerda) return "Nota de Perda";
        if (nota instanceof NotaDevolucao) return "Nota Fiscal de Devolução";
        return nota != null ? nota.getClass().getSimpleName() : "Nota Operacional";
    }

    // ==================== CATÁLOGO DETERMINÍSTICO DE JANELAS JAVAFX (abrir>janela>...) ====================

    private record DefinicaoJanela(String id, String titulo, String modulo, String fxml, String rota, String palavrasChave) {
    }

    private static final List<DefinicaoJanela> CATALOGO_JANELAS = List.of(
            new DefinicaoJanela("JANELA_REGISTRAR_NOTA", "Emissão e Registro de Notas Operacionais", "NOTA",
                    "/fxml/nota/NotaFiscalForm.fxml", "NOTA_COMPRA_EMITIR / NOTA_VENDA_EMITIR_FIFO",
                    "registrar nota emitir nota lancamento nota fiscal registarr nota"),
            new DefinicaoJanela("JANELA_REGISTRAR_COMPRA", "Entrada de Mercadoria e Nota de Compra", "NOTA",
                    "/fxml/nota/NotaCompraForm.fxml", "NOTA_COMPRA_EMITIR",
                    "registrar compra nota compra entrada mercadoria fornecedor"),
            new DefinicaoJanela("JANELA_REGISTRAR_VENDA", "Frente de Caixa PDV e Nota de Venda FIFO", "NOTA",
                    "/fxml/nota/NotaVendaPdvForm.fxml", "NOTA_VENDA_EMITIR_FIFO",
                    "registrar venda nota venda pdv caixa balcao"),
            new DefinicaoJanela("JANELA_REGISTRAR_DEVOLUCAO", "Registro de Devolução e Baixa de Perdas", "NOTA",
                    "/fxml/nota/NotaDevolucaoPerdaForm.fxml", "NOTA_DEVOLUCAO_REGISTRAR",
                    "registrar devolucao registrar perda avaria baixa perda"),
            new DefinicaoJanela("JANELA_CADASTRAR_PRODUTO", "Cadastro e Edição de Produtos e Dimensões", "PRODUTO",
                    "/fxml/produto/ProdutoForm.fxml", "PRODUTO_FISICO_CADASTRAR",
                    "cadastrar produto registrar produto editar produto novo produto"),
            new DefinicaoJanela("JANELA_CATALOGO_PRODUTOS", "Catálogo Geral de Produtos", "PRODUTO",
                    "/fxml/produto/ProdutoCatalogoView.fxml", "PRODUTO_CATALOGO_LISTAR",
                    "consultar produto listar produtos catalogo produto"),
            new DefinicaoJanela("JANELA_UNIDADES_E_KITS", "Gestão de Unidades de Medida e Kits", "PRODUTO",
                    "/fxml/produto/KitUnidadeForm.fxml", "PRODUTO_KIT_ADICIONAR_COMPONENTE",
                    "cadastrar kit cadastrar um unidade medida conjunto produto"),
            new DefinicaoJanela("JANELA_CADASTRAR_PESSOA", "Cadastro de Clientes, Fornecedores e Contatos", "PESSOA",
                    "/fxml/pessoa/PessoaForm.fxml", "PESSOA_SIMPLES_CADASTRAR",
                    "cadastrar pessoa registrar pessoa cadastrar cliente cadastrar fornecedor cadastrar contato"),
            new DefinicaoJanela("JANELA_DOSSIE_PESSOA", "Dossiê 360º de Clientes, Fornecedores e Contatos", "PESSOA",
                    "/fxml/pessoa/PessoaDossie360View.fxml", "PESSOA_HISTORICO_COMPLETO",
                    "consultar pessoa historico pessoa dossie cliente consultar fornecedor consultar contato"),
            new DefinicaoJanela("JANELA_ESTOQUE_FIFO", "Controle de Estoque e Fila de Lotes FIFO", "ESTOQUE",
                    "/fxml/estoque/EstoqueFifoView.fxml", "ESTOQUE_FILA_LOTES_FIFO",
                    "consultar estoque lotes fifo validade saldo estoque"),
            new DefinicaoJanela("JANELA_FINANCEIRO_PAGAMENTOS", "Gestão Financeira, Formas de Pagamento e Parcelas", "PAGAMENTO",
                    "/fxml/pagamento/FinanceiroPagamentosView.fxml", "PAGAMENTO_QUITAR_TITULO_OU_PARCELA",
                    "financeiro pagamento registrar pagamento quitar parcela forma pagamento"),
            new DefinicaoJanela("JANELA_INTELIGENCIA_SAZONAL", "Inteligência Sazonal, Fatores Externos e Estatística", "ESTATISTICA",
                    "/fxml/estatistica/InteligenciaSazonalView.fxml", "ESTATISTICA_PLANO_ESTRATEGICO_COMPRAS",
                    "analise estatistica fator externo clima rios plano compras relatorio bacen"),
            new DefinicaoJanela("JANELA_BANDEJA_CONTEXTO", "Bandeja de Contexto e Revisão de Instruções", "IA",
                    "/fxml/ia/BandejaContextoDrawer.fxml", "BANDEJA_CONTEXTO_REVISAR",
                    "bandeja contexto lista espera revisar instrucoes"),
            new DefinicaoJanela("JANELA_DASHBOARD", "Painel Executivo Principal Sazonalisei", "SISTEMA",
                    "/fxml/dashboard/DashboardPrincipalView.fxml", "RELATORIO_LUCRO_CONTABIL_BACEN",
                    "painel principal dashboard inicio home")
    );

    public static boolean ehComandoAbrirJanela(String instrucao) {
        if (instrucao == null) return false;
        String s = instrucao.trim();
        if (s.startsWith("$$")) s = s.substring(2).trim();
        String norm = BuscaPalavrasParciaisUtil.normalizar(s.replace(">", " "));
        return norm.startsWith("abrir janela") || norm.startsWith("abrir tela") || norm.startsWith("janela ");
    }

    public static ComandoJanelaUI resolverComandoJanela(String instrucao, BandejaOperacoesContexto bandeja) {
        String limpa = instrucao != null ? instrucao.trim() : "";
        if (limpa.startsWith("$$")) limpa = limpa.substring(2).trim();

        String termos = BuscaPalavrasParciaisUtil.normalizar(
                limpa.replaceAll("(?i)^abrir\\s*>?\\s*(janela|tela)\\s*>?\\s*", "")
                     .replace(">", " "));

        String termosCorrigidos = termos
                .replace("registarr", "registrar")
                .replace("registra ", "registrar ")
                .replace("cadastar", "cadastrar")
                .replace("clinete", "cliente")
                .replace("fornecdor", "fornecedor");

        DefinicaoJanela escolhida = null;
        int melhorScore = 0;

        for (DefinicaoJanela def : CATALOGO_JANELAS) {
            int score = BuscaPalavrasParciaisUtil.calcularPontuacaoMatch(def.palavrasChave() + " " + def.titulo(), termosCorrigidos);
            if (score > melhorScore) {
                melhorScore = score;
                escolhida = def;
            }
        }

        if (escolhida == null) {
            if (termosCorrigidos.contains("compra")) escolhida = CATALOGO_JANELAS.get(1);
            else if (termosCorrigidos.contains("venda") || termosCorrigidos.contains("pdv")) escolhida = CATALOGO_JANELAS.get(2);
            else if (termosCorrigidos.contains("devolucao") || termosCorrigidos.contains("perda")) escolhida = CATALOGO_JANELAS.get(3);
            else if (termosCorrigidos.contains("nota")) escolhida = CATALOGO_JANELAS.get(0);
            else if (termosCorrigidos.contains("produto")) escolhida = CATALOGO_JANELAS.get(4);
            else if (termosCorrigidos.contains("pessoa") || termosCorrigidos.contains("cliente") || termosCorrigidos.contains("fornecedor") || termosCorrigidos.contains("contato")) escolhida = CATALOGO_JANELAS.get(7);
            else if (termosCorrigidos.contains("estoque") || termosCorrigidos.contains("lote")) escolhida = CATALOGO_JANELAS.get(9);
            else if (termosCorrigidos.contains("pagamento") || termosCorrigidos.contains("financeiro")) escolhida = CATALOGO_JANELAS.get(10);
            else if (termosCorrigidos.contains("bandeja")) escolhida = CATALOGO_JANELAS.get(12);
        }

        if (escolhida == null) {
            return null;
        }

        List<Map<String, Object>> prePreenchimento = new ArrayList<>();
        if (bandeja != null && bandeja.temItens()) {
            for (ItemBandeja it : bandeja.getItens()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("idInstrucao", it.getIdItem());
                m.put("rota", it.getRota().name());
                m.put("descricao", it.getDescricaoAmigavel());
                m.put("parametros", new LinkedHashMap<>(it.getParametros()));
                prePreenchimento.add(m);
            }
        }

        return new ComandoJanelaUI(
                escolhida.id(),
                escolhida.titulo(),
                escolhida.modulo(),
                escolhida.fxml(),
                escolhida.rota(),
                instrucao != null ? instrucao.trim() : "",
                Collections.unmodifiableList(prePreenchimento)
        );
    }

    private static String moeda(BigDecimal v) {
        if (v == null) return "0.00";
        return v.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
