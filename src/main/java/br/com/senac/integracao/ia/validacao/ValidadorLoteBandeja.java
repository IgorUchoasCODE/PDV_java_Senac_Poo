package br.com.senac.integracao.ia.validacao;

import br.com.senac.controller.RotaSistema;
import br.com.senac.controller.RoteadorController;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.Um;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validador estrutural e relacional de lote para a {@link BandejaOperacoesContexto}.
 *
 * <p>Simula a sequência completa das instruções na bandeja antes da confirmação do usuário,
 * confrontando cada operação com o estado real do banco e com os efeitos das operações anteriores
 * (incluindo detecção de conflitos de chave única, unidades inexistentes e propagação de
 * dependências quebradas entre OP-N, kits e itens de notas fiscais).</p>
 */
public final class ValidadorLoteBandeja {

    private ValidadorLoteBandeja() {
    }

    public record RelatorioValidacaoLote(
            Map<String, List<String>> conflitosPorOp,
            Map<String, List<String>> avisosPorOp,
            Map<String, Set<String>> dependenciasPorOp,
            Map<String, String> opParaRotulo
    ) {
        public boolean todoValido() {
            return conflitosPorOp.isEmpty();
        }

        public int totalConflitos() {
            return conflitosPorOp.size();
        }

        public String primeiroConflito(String idItem) {
            if (idItem == null) return null;
            List<String> lista = conflitosPorOp.get(idItem.toUpperCase(Locale.ROOT));
            return (lista != null && !lista.isEmpty()) ? lista.get(0) : null;
        }

        public String formatarParaUsuario() {
            if (conflitosPorOp.isEmpty() && avisosPorOp.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (!conflitosPorOp.isEmpty()) {
                sb.append("PRÉ-VALIDAÇÃO DETECTOU ").append(conflitosPorOp.size())
                  .append(" INSTRUÇÃO(ÕES) COM CONFLITO OU DEPENDÊNCIA QUEBRADA:\n");
                for (Map.Entry<String, List<String>> e : conflitosPorOp.entrySet()) {
                    for (String msg : e.getValue()) {
                        sb.append("  [!] ").append(e.getKey()).append(": ").append(msg).append("\n");
                    }
                }
            }
            if (!avisosPorOp.isEmpty()) {
                sb.append("OBSERVAÇÕES DA PRÉ-VALIDAÇÃO:\n");
                for (Map.Entry<String, List<String>> e : avisosPorOp.entrySet()) {
                    for (String msg : e.getValue()) {
                        sb.append("  [i] ").append(e.getKey()).append(": ").append(msg).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        }

        public String formatarParaIA() {
            StringBuilder sb = new StringBuilder();
            sb.append("[AUDITORIA DE PRÉ-VALIDAÇÃO DA BANDEJA]\n");
            if (conflitosPorOp.isEmpty()) {
                sb.append("STATUS GERAL: APROVADA (0 conflitos ou dependências quebradas; todas as entidades/unidades/chaves foram conferidas no banco e na ordem do lote).\n");
            } else {
                sb.append("STATUS GERAL: REPROVADA / COM CONFLITOS (").append(conflitosPorOp.size())
                  .append(" operação(ões) bloqueada(s)). NUNCA diga que está tudo correto enquanto houver conflitos abaixo!\n");
                for (Map.Entry<String, List<String>> e : conflitosPorOp.entrySet()) {
                    String rotulo = opParaRotulo.getOrDefault(e.getKey(), "");
                    sb.append("  - ").append(e.getKey());
                    if (!rotulo.isBlank()) sb.append(" (").append(rotulo).append(")");
                    sb.append(": ").append(String.join(" | ", e.getValue())).append("\n");
                }
            }
            if (!avisosPorOp.isEmpty()) {
                sb.append("ALERTAS INFORMATIVOS:\n");
                for (Map.Entry<String, List<String>> e : avisosPorOp.entrySet()) {
                    sb.append("  - ").append(e.getKey()).append(": ").append(String.join(" | ", e.getValue())).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }

    private record ProdSimulado(String opId, String nomeLower, String siglaUm, boolean conjunto) {}
    private record PessoaSimulada(String opId, String nomeLower, boolean fornecedor, boolean cliente) {}

    public static RelatorioValidacaoLote validar(BandejaOperacoesContexto bandeja, RoteadorController roteador) {
        Map<String, List<String>> conflitos = new LinkedHashMap<>();
        Map<String, List<String>> avisos = new LinkedHashMap<>();
        Map<String, Set<String>> depsPorOp = new LinkedHashMap<>();
        Map<String, String> rotulos = new LinkedHashMap<>();

        if (bandeja == null || roteador == null || bandeja.getItens().isEmpty()) {
            return new RelatorioValidacaoLote(conflitos, avisos, depsPorOp, rotulos);
        }

        // Estado inicial do banco de dados
        Map<String, String> unidadesSiglaParaNome = new LinkedHashMap<>();
        Map<String, String> unidadesNomeLowerParaSigla = new LinkedHashMap<>();
        try {
            List<Um> umsDb = roteador.produtos().listarUnidadesMedida();
            if (umsDb != null) {
                for (Um um : umsDb) {
                    if (um.getSigla() != null) {
                        unidadesSiglaParaNome.put(um.getSigla().toUpperCase(Locale.ROOT), um.getNome());
                    }
                    if (um.getNome() != null && um.getSigla() != null) {
                        unidadesNomeLowerParaSigla.put(um.getNome().trim().toLowerCase(Locale.ROOT),
                                um.getSigla().toUpperCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception ignored) {}

        Set<String> formasPagamentoDb = new LinkedHashSet<>();
        Set<String> formasPagamentoNomesDb = new LinkedHashSet<>();
        try {
            List<FormaPagamento> fps = roteador.pagamentos().listarTodasFormasPagamento();
            if (fps != null) {
                for (FormaPagamento fp : fps) {
                    if (fp.getCodigo() != null) {
                        formasPagamentoDb.add(fp.getCodigo().trim().toUpperCase(Locale.ROOT));
                    }
                    if (fp.getNome() != null) {
                        formasPagamentoNomesDb.add(fp.getNome().trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception ignored) {}

        // Estado simulado ao longo das operações na bandeja
        Map<String, String> opFalhouMotivo = new LinkedHashMap<>();
        Map<String, String> unidadeSiglaFalhaPorOp = new LinkedHashMap<>();
        Map<String, String> prodNomeFalhoPorOp = new LinkedHashMap<>();
        Map<String, String> pessoaNomeFalhoPorOp = new LinkedHashMap<>();
        Map<String, ProdSimulado> produtosLotePorOp = new LinkedHashMap<>();
        Map<String, ProdSimulado> produtosLotePorNome = new LinkedHashMap<>();
        Map<String, PessoaSimulada> pessoasLotePorOp = new LinkedHashMap<>();
        Map<String, PessoaSimulada> pessoasLotePorNome = new LinkedHashMap<>();
        Map<String, BigDecimal> comprasPreviasLotePorProdutoNome = new LinkedHashMap<>();

        for (ItemBandeja item : bandeja.getItens()) {
            if (item == null || item.getRota() == null) continue;
            if (item.getStatus() == ItemBandeja.StatusItem.PULADO
                    || item.getStatus() == ItemBandeja.StatusItem.EXECUTADO_SUCESSO) {
                continue;
            }
            String opId = item.getIdItem().toUpperCase(Locale.ROOT);
            rotulos.put(opId, item.getDescricaoAmigavel());
            if (item.getStatus() == ItemBandeja.StatusItem.FALHA_EXECUCAO) {
                String msg = item.getMensagemErro() != null ? item.getMensagemErro() : "Falha anterior na execução.";
                opFalhouMotivo.put(opId, msg);
                conflitos.computeIfAbsent(opId, k -> new ArrayList<>()).add(msg);
                continue;
            }
            if (item.isPendente()) {
                String msg = "Faltam parâmetros obrigatórios: " + item.getCamposFaltantes();
                opFalhouMotivo.put(opId, msg);
                conflitos.computeIfAbsent(opId, k -> new ArrayList<>()).add(msg);
                continue;
            }

            Map<String, Object> p = item.getParametros();
            List<String> errosItem = new ArrayList<>();
            Set<String> depsItem = new LinkedHashSet<>();

            // 1. Verifica referências explícitas OP-N
            Set<String> refsOp = new LinkedHashSet<>();
            coletarRefsOp(p, refsOp);
            for (String ref : refsOp) {
                if (ref.equals(opId)) continue;
                depsItem.add(ref);
                if (opFalhouMotivo.containsKey(ref)) {
                    errosItem.add("Dependência " + ref + " inválida: " + opFalhouMotivo.get(ref));
                }
            }

            // 2. Validações específicas da rota e propagação por nome/sigla
            switch (item.getRota()) {
                case PRODUTO_UNIDADE_CADASTRAR -> {
                    String sigla = LeitorParametros.textoOuNulo(p, "sigla");
                    String nome = LeitorParametros.textoOuNulo(p, "nome");
                    if (sigla != null) {
                        String sUp = sigla.trim().toUpperCase(Locale.ROOT);
                        if (sUp.length() > 4) {
                            errosItem.add("Sigla de unidade \"" + sUp + "\" excede o limite de 4 caracteres.");
                        } else if (unidadesSiglaParaNome.containsKey(sUp)) {
                            errosItem.add("Já existe unidade com sigla \"" + sUp + "\" ("
                                    + unidadesSiglaParaNome.get(sUp) + ").");
                        } else if (nome != null) {
                            String nLow = nome.trim().toLowerCase(Locale.ROOT);
                            if (unidadesNomeLowerParaSigla.containsKey(nLow)) {
                                String siglaExistente = unidadesNomeLowerParaSigla.get(nLow);
                                errosItem.add("Já existe unidade com o nome \"" + nome.trim()
                                        + "\" (sigla \"" + siglaExistente + "\"). Use \""
                                        + siglaExistente + "\" nos produtos ou altere o nome da unidade.");
                            }
                        }
                        if (errosItem.isEmpty()) {
                            unidadesSiglaParaNome.put(sUp, nome != null ? nome.trim() : sUp);
                            if (nome != null) {
                                unidadesNomeLowerParaSigla.put(nome.trim().toLowerCase(Locale.ROOT), sUp);
                            }
                        } else {
                            unidadeSiglaFalhaPorOp.putIfAbsent(sUp, opId);
                        }
                    }
                }
                case PAGAMENTO_FORMA_CADASTRAR -> {
                    String codigo = LeitorParametros.textoOuNulo(p, "codigo");
                    String nomeForma = LeitorParametros.textoOuNulo(p, "nome");
                    if (codigo != null) {
                        String cUp = codigo.trim().toUpperCase(Locale.ROOT);
                        if (formasPagamentoDb.contains(cUp)) {
                            errosItem.add("Já existe uma forma de pagamento cadastrada com o código \"" + cUp + "\".");
                        } else if (nomeForma != null && formasPagamentoNomesDb.contains(nomeForma.trim().toLowerCase(Locale.ROOT))) {
                            errosItem.add("Já existe uma forma de pagamento cadastrada com o nome \"" + nomeForma.trim() + "\".");
                        } else {
                            formasPagamentoDb.add(cUp);
                            if (nomeForma != null) {
                                formasPagamentoNomesDb.add(nomeForma.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
                case PESSOA_SIMPLES_CADASTRAR,
                     PESSOA_CLIENTE_PF_CADASTRAR,
                     PESSOA_CLIENTE_PJ_CADASTRAR,
                     PESSOA_FORNECEDOR_PF_CADASTRAR,
                     PESSOA_FORNECEDOR_PJ_CADASTRAR -> {
                    String nome = LeitorParametros.textoOuNulo(p, "nome", "razaoSocial", "nomeFantasia");
                    if (nome == null) {
                        errosItem.add("Falta o nome/razão social para o cadastro.");
                    } else {
                        String doc = LeitorParametros.textoOuNulo(p, "cpf", "cnpj", "documento");
                        try {
                            if (doc != null) {
                                boolean jaExisteDoc = (roteador.pessoas().buscarPorCpf(doc) != null)
                                        || (roteador.pessoas().buscarPorCnpj(doc) != null);
                                if (jaExisteDoc) {
                                    avisos.computeIfAbsent(opId, k -> new ArrayList<>())
                                          .add("Documento \"" + doc + "\" já cadastrado; os dados/papel serão atualizados automaticamente.");
                                }
                            }
                        } catch (Exception ignored) {}
                        boolean isForn = item.getRota().name().contains("FORNECEDOR")
                                || item.getRota() == RotaSistema.PESSOA_SIMPLES_CADASTRAR;
                        boolean isCli = item.getRota().name().contains("CLIENTE")
                                || item.getRota() == RotaSistema.PESSOA_SIMPLES_CADASTRAR;
                        PessoaSimulada ps = new PessoaSimulada(opId, nome.toLowerCase(Locale.ROOT), isForn, isCli);
                        pessoasLotePorOp.put(opId, ps);
                        pessoasLotePorNome.put(ps.nomeLower(), ps);
                    }
                }
                case PRODUTO_FISICO_CADASTRAR, PRODUTO_DIGITAL_CADASTRAR -> {
                    String nome = LeitorParametros.textoOuNulo(p, "nome");
                    boolean digital = item.getRota() == RotaSistema.PRODUTO_DIGITAL_CADASTRAR;
                    String sigla = digital ? "UN" : LeitorParametros.textoOuNulo(p, "siglaUm");
                    sigla = (sigla != null && !sigla.isBlank()) ? sigla.trim().toUpperCase(Locale.ROOT) : "UN";
                    if (nome == null) {
                        errosItem.add("Falta o nome do produto.");
                    } else {
                        String opUnidadeFalha = unidadeSiglaFalhaPorOp.get(sigla);
                        if (opUnidadeFalha != null && !unidadesSiglaParaNome.containsKey(sigla)) {
                            depsItem.add(opUnidadeFalha);
                            errosItem.add("Depende da " + opUnidadeFalha + " (a unidade \"" + sigla
                                    + "\" não será criada: " + opFalhouMotivo.get(opUnidadeFalha) + ").");
                        } else if (!unidadesSiglaParaNome.containsKey(sigla)) {
                            errosItem.add("Unidade de medida \"" + sigla + "\" não está cadastrada no sistema nem no lote.");
                        }

                        String chaveProd = nome.trim().toLowerCase(Locale.ROOT);
                        ProdSimulado dupLote = produtosLotePorNome.get(chaveProd);
                        if (dupLote != null && dupLote.siglaUm().equalsIgnoreCase(sigla) && !dupLote.conjunto()) {
                            errosItem.add("Produto \"" + nome + "\" (" + sigla + ") já foi incluído na " + dupLote.opId() + ".");
                        } else {
                            try {
                                Produto igualDb = roteador.produtos().buscarPorChaveUnica(nome, sigla, false);
                                if (igualDb != null) {
                                    errosItem.add("Já existe o produto #" + igualDb.getId() + " \"" + igualDb.getNome()
                                            + "\" (" + sigla + ") cadastrado no banco.");
                                }
                            } catch (Exception ignored) {}
                        }

                        if (errosItem.isEmpty()) {
                            ProdSimulado ps = new ProdSimulado(opId, chaveProd, sigla, false);
                            produtosLotePorOp.put(opId, ps);
                            produtosLotePorNome.put(chaveProd, ps);
                        } else {
                            prodNomeFalhoPorOp.putIfAbsent(chaveProd, opId);
                        }
                    }
                }
                case PRODUTO_KIT_ADICIONAR_COMPONENTE -> {
                    ProdSimulado kitLote = null;
                    String nomeKit = LeitorParametros.textoOuNulo(p, "nomeKit", "idProdutoKit");
                    Long idKit = LeitorParametros.longOuNulo(p, "idProdutoKit");
                    if (idKit != null) {
                        if (roteador.produtos().buscarPorId(idKit) == null) {
                            errosItem.add("Conjunto/kit #" + idKit + " não encontrado no banco.");
                        }
                    } else if (nomeKit != null && !BandejaOperacoesContexto.ehReferenciaOp(nomeKit)) {
                        String nLow = nomeKit.trim().toLowerCase(Locale.ROOT);
                        String opFalhaKit = prodNomeFalhoPorOp.get(nLow);
                        if (opFalhaKit != null) {
                            depsItem.add(opFalhaKit);
                            errosItem.add("Conjunto \"" + nomeKit + "\" depende da " + opFalhaKit + " que falhou (" + opFalhouMotivo.get(opFalhaKit) + ").");
                        } else {
                            kitLote = produtosLotePorNome.get(nLow);
                            if (kitLote != null) {
                                depsItem.add(kitLote.opId());
                            } else if (localizarProdutoBanco(nomeKit, roteador) == null) {
                                errosItem.add("Conjunto/kit \"" + nomeKit + "\" não encontrado no banco nem em operação anterior válida.");
                            }
                        }
                    }

                    String nomeComp = LeitorParametros.textoOuNulo(p, "nomeComponente", "idProdutoComponente");
                    Long idComp = LeitorParametros.longOuNulo(p, "idProdutoComponente");
                    if (idComp != null) {
                        if (roteador.produtos().buscarPorId(idComp) == null) {
                            errosItem.add("Componente #" + idComp + " não encontrado no banco.");
                        }
                    } else if (nomeComp != null && !BandejaOperacoesContexto.ehReferenciaOp(nomeComp)) {
                        String cLow = nomeComp.trim().toLowerCase(Locale.ROOT);
                        String opFalhaComp = prodNomeFalhoPorOp.get(cLow);
                        if (opFalhaComp != null) {
                            depsItem.add(opFalhaComp);
                            errosItem.add("Componente \"" + nomeComp + "\" depende da " + opFalhaComp + " que falhou (" + opFalhouMotivo.get(opFalhaComp) + ").");
                        } else {
                            ProdSimulado compLote = produtosLotePorNome.get(cLow);
                            if (compLote != null) {
                                depsItem.add(compLote.opId());
                            } else if (localizarProdutoBanco(nomeComp, roteador) == null) {
                                errosItem.add("Componente \"" + nomeComp + "\" não encontrado no banco nem em operação anterior válida.");
                            }
                        }
                    }
                    BigDecimal qtd = LeitorParametros.decimalOuNulo(p, "quantidade");
                    if (qtd != null && qtd.signum() <= 0) {
                        errosItem.add("A quantidade do componente no kit deve ser maior que zero.");
                    }
                    if (nomeKit != null && nomeComp != null && nomeKit.equalsIgnoreCase(nomeComp)) {
                        errosItem.add("Um conjunto não pode conter ele mesmo.");
                    }
                }
                case NOTA_COMPRA_EMITIR -> {
                    // Fornecedor
                    String refForn = LeitorParametros.textoOuNulo(p, "nomeFornecedor", "idFornecedor", "fornecedor", "idPessoa");
                    Long idForn = LeitorParametros.longOuNulo(p, "idFornecedor", "idPessoa");
                    if (idForn != null) {
                        if (roteador.pessoas().buscarPorId(idForn) == null) {
                            errosItem.add("Fornecedor #" + idForn + " não encontrado no banco.");
                        }
                    } else if (refForn != null && !BandejaOperacoesContexto.ehReferenciaOp(refForn)) {
                        String limpo = refForn.startsWith("#") ? refForn.substring(1).trim() : refForn.trim();
                        String fLow = limpo.toLowerCase(Locale.ROOT);
                        String opFornFalha = pessoaNomeFalhoPorOp.get(fLow);
                        if (opFornFalha != null) {
                            depsItem.add(opFornFalha);
                            errosItem.add("Fornecedor \"" + limpo + "\" depende da " + opFornFalha + " que falhou.");
                        } else {
                            PessoaSimulada ps = pessoasLotePorNome.get(fLow);
                            if (ps != null) {
                                depsItem.add(ps.opId());
                            } else if (!existePessoaBanco(limpo, true, roteador)) {
                                errosItem.add("Fornecedor \"" + limpo + "\" não encontrado no banco nem em cadastro anterior válido.");
                            }
                        }
                    }
                    validarItensNotaNoLote(p, false, roteador, prodNomeFalhoPorOp, produtosLotePorOp,
                            produtosLotePorNome, comprasPreviasLotePorProdutoNome, opFalhouMotivo, errosItem, depsItem);
                }
                case NOTA_VENDA_EMITIR_FIFO, NOTA_VENDA_EMITIR_BALCAO_CLIENTE_PADRAO -> {
                    if (item.getRota() == RotaSistema.NOTA_VENDA_EMITIR_FIFO) {
                        String refCli = LeitorParametros.textoOuNulo(p, "nomeCliente", "idCliente", "cliente");
                        Long idCli = LeitorParametros.longOuNulo(p, "idCliente");
                        if (idCli != null) {
                            if (roteador.pessoas().buscarPorId(idCli) == null) {
                                errosItem.add("Cliente #" + idCli + " não encontrado no banco.");
                            }
                        } else if (refCli != null && !BandejaOperacoesContexto.ehReferenciaOp(refCli)) {
                            String cLow = refCli.trim().toLowerCase(Locale.ROOT);
                            PessoaSimulada ps = pessoasLotePorNome.get(cLow);
                            if (ps != null) {
                                depsItem.add(ps.opId());
                            } else if (!existePessoaBanco(refCli, false, roteador)) {
                                errosItem.add("Cliente \"" + refCli + "\" não encontrado no banco nem no lote.");
                            }
                        }
                    }
                    validarItensNotaNoLote(p, true, roteador, prodNomeFalhoPorOp, produtosLotePorOp,
                            produtosLotePorNome, comprasPreviasLotePorProdutoNome, opFalhouMotivo, errosItem, depsItem);
                }
                default -> {
                    // Demais rotas utilizam validação pontual nos seus respectivos validadores/controllers.
                }
            }

            if (!depsItem.isEmpty()) {
                depsPorOp.put(opId, depsItem);
            }
            if (!errosItem.isEmpty()) {
                conflitos.put(opId, errosItem);
                opFalhouMotivo.put(opId, String.join("; ", errosItem));
                if (item.getRota().name().startsWith("PESSOA_") && item.getRota().name().endsWith("_CADASTRAR")) {
                    String n = LeitorParametros.textoOuNulo(p, "nome", "razaoSocial", "nomeFantasia");
                    if (n != null) pessoaNomeFalhoPorOp.putIfAbsent(n.toLowerCase(Locale.ROOT), opId);
                }
            }
        }

        return new RelatorioValidacaoLote(conflitos, avisos, depsPorOp, rotulos);
    }

    @SuppressWarnings("unchecked")
    private static void validarItensNotaNoLote(
            Map<String, Object> p,
            boolean ehVenda,
            RoteadorController roteador,
            Map<String, String> prodNomeFalhoPorOp,
            Map<String, ProdSimulado> produtosLotePorOp,
            Map<String, ProdSimulado> produtosLotePorNome,
            Map<String, BigDecimal> comprasPreviasLotePorProdutoNome,
            Map<String, String> opFalhouMotivo,
            List<String> errosItem,
            Set<String> depsItem) {
        Object itensObj = p.get("itens");
        if (itensObj == null) itensObj = p.get("produtos");
        if (itensObj == null && (p.containsKey("idProduto") || p.containsKey("nomeProduto") || p.containsKey("nome"))) {
            itensObj = List.of(p);
        }
        if (!(itensObj instanceof List<?> list) || list.isEmpty()) {
            if (!(itensObj instanceof Map<?, ?> map && !map.isEmpty())) {
                errosItem.add("A nota não possui itens informados.");
            }
            return;
        }
        for (Object el : list) {
            if (!(el instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> itemMap = (Map<String, Object>) rawMap;
            Long idProd = LeitorParametros.longOuNulo(itemMap, "idProduto", "id");
            String nomeProd = LeitorParametros.textoOuNulo(itemMap, "nomeProduto", "nome", "idProduto");
            BigDecimal qtd = LeitorParametros.decimalOuNulo(itemMap, "quantidade");
            if (qtd != null && qtd.signum() <= 0) {
                errosItem.add("Quantidade inválida no item \"" + (nomeProd != null ? nomeProd : idProd) + "\".");
            }
            Produto prodDb = null;
            String prodNomeResolvidoLower = null;
            if (idProd != null) {
                prodDb = roteador.produtos().buscarPorId(idProd);
                if (prodDb == null) {
                    errosItem.add("Produto #" + idProd + " do item da nota não encontrado.");
                    continue;
                }
                prodNomeResolvidoLower = prodDb.getNome() != null ? prodDb.getNome().toLowerCase(Locale.ROOT) : null;
            } else if (nomeProd != null) {
                if (BandejaOperacoesContexto.ehReferenciaOp(nomeProd)) {
                    String refUp = nomeProd.trim().toUpperCase(Locale.ROOT);
                    depsItem.add(refUp);
                    ProdSimulado sim = produtosLotePorOp.get(refUp);
                    if (sim != null) {
                        prodNomeResolvidoLower = sim.nomeLower();
                    }
                    continue;
                }
                String nLow = nomeProd.trim().toLowerCase(Locale.ROOT);
                String opFalhaProd = prodNomeFalhoPorOp.get(nLow);
                if (opFalhaProd != null) {
                    depsItem.add(opFalhaProd);
                    errosItem.add("Item \"" + nomeProd + "\" depende da " + opFalhaProd
                            + " que falhou (" + opFalhouMotivo.get(opFalhaProd) + ").");
                    continue;
                }
                ProdSimulado sim = produtosLotePorNome.get(nLow);
                if (sim != null) {
                    depsItem.add(sim.opId());
                    prodNomeResolvidoLower = sim.nomeLower();
                } else {
                    prodDb = localizarProdutoBanco(nomeProd, roteador);
                    if (prodDb == null) {
                        errosItem.add("Produto \"" + nomeProd + "\" do item da nota não encontrado no banco nem em operação anterior válida.");
                        continue;
                    }
                    prodNomeResolvidoLower = prodDb.getNome() != null ? prodDb.getNome().toLowerCase(Locale.ROOT) : nLow;
                }
            }
            if (prodNomeResolvidoLower != null && qtd != null && qtd.signum() > 0) {
                if (!ehVenda) {
                    comprasPreviasLotePorProdutoNome.merge(prodNomeResolvidoLower, qtd, BigDecimal::add);
                } else if (prodDb != null && prodDb.getId() != null) {
                    BigDecimal saldoDb = roteador.estoque().consultarSaldoTotalProduto(prodDb.getId());
                    BigDecimal saldoPrevio = (saldoDb != null ? saldoDb : BigDecimal.ZERO)
                            .add(comprasPreviasLotePorProdutoNome.getOrDefault(prodNomeResolvidoLower, BigDecimal.ZERO));
                    if (saldoPrevio.compareTo(qtd) < 0) {
                        errosItem.add("Estoque insuficiente para vender \"" + prodDb.getNome() + "\": disponível "
                                + saldoPrevio + ", solicitado " + qtd + ".");
                    }
                }
            }
        }
    }

    private static Produto localizarProdutoBanco(String nome, RoteadorController roteador) {
        if (nome == null || nome.isBlank()) return null;
        Produto exato = roteador.produtos().buscarPorNome(nome);
        if (exato != null) return exato;
        List<Produto> candidatos = roteador.produtos().buscarPorTermo(nome);
        return candidatos != null && !candidatos.isEmpty() ? candidatos.get(0) : null;
    }

    private static boolean existePessoaBanco(String termo, boolean fornecedor, RoteadorController roteador) {
        if (termo == null || termo.isBlank()) return false;
        String limpo = termo.startsWith("#") ? termo.substring(1).trim() : termo.trim();
        List<Pessoa> lista = fornecedor ? roteador.pessoas().listarFornecedores() : roteador.pessoas().listarClientes();
        if (lista != null) {
            for (Pessoa p : lista) {
                if (p.getNome() != null && (p.getNome().equalsIgnoreCase(limpo)
                        || p.getNome().toLowerCase(Locale.ROOT).contains(limpo.toLowerCase(Locale.ROOT)))) {
                    return true;
                }
            }
        }
        List<Pessoa> gerais = roteador.pessoas().buscarPessoasPorTermo(limpo, null);
        return gerais != null && !gerais.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void coletarRefsOp(Object obj, Set<String> refs) {
        if (obj instanceof String s) {
            String t = s.trim().toUpperCase(Locale.ROOT);
            if (BandejaOperacoesContexto.ehReferenciaOp(t)) {
                refs.add(t);
            }
        } else if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) coletarRefsOp(v, refs);
        } else if (obj instanceof Collection<?> col) {
            for (Object v : col) coletarRefsOp(v, refs);
        }
    }
}
