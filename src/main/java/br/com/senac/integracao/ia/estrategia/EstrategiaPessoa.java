package br.com.senac.integracao.ia.estrategia;

import br.com.senac.controller.RotaSistema;
import br.com.senac.integracao.ia.BandejaOperacoesContexto;
import br.com.senac.integracao.ia.ContextoIABuilder;
import br.com.senac.integracao.ia.ItemBandeja;
import br.com.senac.integracao.ia.RetornoChatBotUI;
import br.com.senac.integracao.ia.ferramentas.ExtratorAtributos;
import br.com.senac.integracao.ia.ferramentas.FormatadorMoeda;
import br.com.senac.integracao.ia.ferramentas.ResolvedorPessoa;
import br.com.senac.integracao.ia.ferramentas.ResolvedorProduto;
import br.com.senac.integracao.ia.parser.NoInstrucao;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.dao.PadraoDAO;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pessoa.Pessoa;

import java.util.*;

/**
 * Estratégia para cadastro, alteração e consulta de Pessoas (Clientes, Fornecedores, Contatos e Registros).
 */
public class EstrategiaPessoa implements EstrategiaInstrucao {

    private static final Set<String> ENTIDADES = Set.of(
            "PESSOA", "PESSOAFISICA", "PESSOAJURIDICA", "CLIENTE", "CLIENTES",
            "FORNECEDOR", "FORNECEDORES", "REGISTRO", "CONTATO", "CONTATOS"
    );

    @Override
    public Set<String> entidadesSuportadas() {
        return ENTIDADES;
    }

    @Override
    public String compilarCriacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String nome = ExtratorAtributos.obter(attrs, "nm", "");

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            if (nome.isBlank()) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.campo.obrigatorio",
                        "Campo obrigatorio '%s' nao informado para #%s (ex: %s).", "#" + entidade, "nm", "nm> Nome");
            }
            String papelPadrao = "FORNECEDOR".equals(entidade) ? "FORNECEDOR" : ("CLIENTE".equals(entidade) ? "CLIENTE" : "AMBOS");
            String papel = ExtratorAtributos.obter(attrs, "tp", papelPadrao).toUpperCase(Locale.ROOT);
            String doc = ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", ""));
            String limite = ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00"));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("nome", nome);
            params.put("razaoSocial", nome);
            params.put("papel", papel);
            if (!doc.isBlank()) {
                params.put("documento", doc);
                params.put("cpf", doc);
                params.put("cnpj", doc);
            }
            params.put("limite", limite);

            RotaSistema rota = RotaSistema.PESSOA_SIMPLES_CADASTRAR;
            if ("PESSOAFISICA".equals(entidade) && !doc.isBlank()) {
                rota = papel.contains("FORN") ? RotaSistema.PESSOA_FORNECEDOR_PF_CADASTRAR : RotaSistema.PESSOA_CLIENTE_PF_CADASTRAR;
            } else if ("PESSOAJURIDICA".equals(entidade) && !doc.isBlank()) {
                rota = papel.contains("FORN") ? RotaSistema.PESSOA_FORNECEDOR_PJ_CADASTRAR : RotaSistema.PESSOA_CLIENTE_PJ_CADASTRAR;
            }

            ItemBandeja item = bandeja.adicionarOuAtualizar(rota, params,
                    String.format("Cadastrar %s '%s'", papel.toLowerCase(Locale.ROOT), nome));
            return String.format("[%s] Preparei o cadastro de %s como %s%s.",
                    item.getIdItem(), nome, papel.toLowerCase(Locale.ROOT),
                    !doc.isBlank() ? " (Documento: " + doc + ")" : "");
        }

        if ("REGISTRO".equals(entidade) || "CONTATO".equals(entidade) || "CONTATOS".equals(entidade)) {
            String alvoPes = ExtratorAtributos.obter(attrs, "pes", nome);
            Pessoa p = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id_pes", null)), alvoPes);
            Long idPessoa = p != null ? p.getId() : FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", "1"));
            String tpPadrao = "REGISTRO".equals(entidade) ? "RG" : "WHATSAPP";
            String tpDoc = ExtratorAtributos.obter(attrs, "tp", tpPadrao).toUpperCase(Locale.ROOT);
            String numDoc = ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", ExtratorAtributos.obter(attrs, "val", "")));
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("idPessoa", idPessoa);
            params.put("tipoRegistro", tpDoc);
            params.put("valorRegistro", numDoc);
            ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_REGISTRO_ADICIONAR, params,
                    String.format("Adicionar registro %s: %s para a pessoa #%d", tpDoc, numDoc, idPessoa));
            return String.format("[%s] Adicionar %s (%s) à pessoa #%d.", item.getIdItem(), tpDoc, numDoc, idPessoa);
        }

        return "Instrução adicionada à lista de espera.";
    }

    @Override
    public String compilarAtualizacao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            Pessoa p = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), id, termo);
            if (p == null) {
                return "Não encontrei a pessoa '" + (id != null ? "#" + id : termo) + "'.";
            }
            String nomePessoaLimpo = ResolvedorProduto.limparNomeExibicao(p.getNome(), null);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "PessoaDAO", p.getId(), nomePessoaLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }
            StringBuilder sb = new StringBuilder();
            if (attrs.containsKey("novo_nm")) {
                String novoNm = attrs.get("novo_nm");
                Map<String, Object> params = Map.of("idPessoa", p.getId(), "nome", novoNm);
                ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_DADOS_ATUALIZAR, params,
                        "Alterar nome de '" + nomePessoaLimpo + "' para '" + novoNm + "'");
                sb.append(String.format("[%s] Alterar nome de %s para %s.\n",
                        item.getIdItem(), nomePessoaLimpo, novoNm));
            }
            if (attrs.containsKey("val") || attrs.containsKey("vl")) {
                String novoLim = ExtratorAtributos.obter(attrs, "val", ExtratorAtributos.obter(attrs, "vl", "0.00"));
                Map<String, Object> params = Map.of("idPessoa", p.getId(), "novoLimite", novoLim);
                ItemBandeja item = bandeja.adicionarOuAtualizar(RotaSistema.PESSOA_CLIENTE_ATUALIZAR_LIMITE, params,
                        "Alterar limite de crédito de '" + nomePessoaLimpo + "' para R$ " + novoLim);
                sb.append(String.format("[%s] Alterar limite de crédito de %s para R$ %s.\n",
                        item.getIdItem(), nomePessoaLimpo, novoLim));
            }
            return sb.toString().trim();
        }

        return "Atualização adicionada à lista de espera.";
    }

    @Override
    public String compilarExclusao(NoInstrucao no, BandejaOperacoesContexto bandeja, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        String termo = ExtratorAtributos.obter(attrs, "nm", "");
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));

        if (entidade.startsWith("PESSOA") || "FORNECEDOR".equals(entidade) || "CLIENTE".equals(entidade)) {
            Pessoa p = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), id, termo);
            if (p == null) {
                return "Não encontrei a pessoa '" + (id != null ? "#" + id : termo) + "' para exclusão.";
            }
            String nomePessoaLimpo = ResolvedorProduto.limparNomeExibicao(p.getNome(), null);
            String erroTrancado = verificarBloqueioMutacaoOutraSessao(
                    "PessoaDAO", p.getId(), nomePessoaLimpo, bandeja != null ? bandeja.getIdSessao() : null);
            if (erroTrancado != null) {
                return erroTrancado;
            }
            Map<String, Object> params = Map.of("idPessoa", p.getId());
            ItemBandeja item = bandeja.adicionarOuAtualizar(
                    RotaSistema.PESSOA_CLIENTE_EXCLUIR,
                    params,
                    "Excluir cadastro de '" + nomePessoaLimpo + "' (#" + p.getId() + ")"
            );
            return String.format("[%s] Excluir o cadastro de #%d - %s.", item.getIdItem(), p.getId(), nomePessoaLimpo);
        }

        return "Exclusão não configurada para " + entidade;
    }

    @Override
    public String processarConsulta(NoInstrucao no, ContextoCompilacao ctx) {
        String entidade = no.getEntidade();
        Map<String, String> attrs = no.getAtributos();
        List<String> alvosCascata = no.getCascata();
        String termo = ExtratorAtributos.obter(attrs, "nm", ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", "")));
        Long id = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id", null));
        Long idNotaRef = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id_nota", ExtratorAtributos.obter(attrs, "nota", null)));
        String ctxFoco = ExtratorAtributos.obter(attrs, "contexto_foco", "");

        if ("CONTATO".equals(entidade) || "CONTATOS".equals(entidade) || "REGISTRO".equals(entidade)) {
            String docBusca = ExtratorAtributos.obter(attrs, "doc", ExtratorAtributos.obter(attrs, "reg", ""));
            if ("REGISTRO".equals(entidade) && !docBusca.isBlank() && !attrs.containsKey("pes") && !attrs.containsKey("id_pes")) {
                Pessoa pDoc = ctx.roteador().pessoas().buscarPessoaPorRegistroUnico(ExtratorAtributos.obter(attrs, "tp", null), docBusca);
                if (pDoc != null) {
                    if (ctx.foco() != null) {
                        ctx.foco().registrarPessoaEmFoco(pDoc);
                        ctx.foco().registrarRecord(RetornoChatBotUI.construirRecordContatos(pDoc, ctx.foco().getIdUltimaNota(), ctx.roteador()));
                    }
                    return String.format("Documento %s pertence a #%d - %s.%s",
                            docBusca, pDoc.getId(), ResolvedorProduto.limparNomeExibicao(pDoc.getNome(), null),
                            sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", pDoc.getId()));
                }
                return "Nenhuma pessoa encontrada para o documento " + docBusca + ".";
            }

            Long idPesBusca = FormatadorMoeda.parseLongSafe(ExtratorAtributos.obter(attrs, "id_pes", ExtratorAtributos.obter(attrs, "id", null)));
            String nmPesBusca = ExtratorAtributos.obter(attrs, "pes", ExtratorAtributos.obter(attrs, "nm", ""));
            Pessoa pAlvo = null;
            if (idPesBusca != null || !nmPesBusca.isBlank()) {
                pAlvo = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), idPesBusca, nmPesBusca);
                if (pAlvo == null) {
                    return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                            "Não encontrei nenhuma pessoa correspondente a '%s'.", idPesBusca != null ? "#" + idPesBusca : nmPesBusca);
                }
            } else if (ctx.foco() != null && ctx.foco().getIdUltimaPessoa() != null) {
                pAlvo = ctx.roteador().pessoas().buscarPorId(ctx.foco().getIdUltimaPessoa());
            } else if (ctx.foco() != null && ctx.foco().getIdUltimaNota() != null) {
                Nota nf = ctx.roteador().notas().buscarNotaPorId(ctx.foco().getIdUltimaNota());
                if (nf != null) pAlvo = nf.getPessoa();
            }

            if (pAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.pessoa.ausente",
                        "Nenhuma pessoa/fornecedor/cliente está em foco na sessão atual.");
            }
            if (ctx.foco() != null) {
                ctx.foco().registrarPessoaEmFoco(pAlvo);
            }
            Long idNotaContexto = ctx.foco() != null ? ctx.foco().getIdUltimaNota() : null;
            var recContatos = RetornoChatBotUI.construirRecordContatos(pAlvo, idNotaContexto, ctx.roteador());
            if (ctx.foco() != null) {
                ctx.foco().registrarRecord(recContatos);
            }
            return recContatos.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", pAlvo.getId());
        }

        boolean semFiltroExplicito = (id == null && termo.isBlank());
        boolean pedirDaNota = "da_nota".equals(ctxFoco)
                || idNotaRef != null
                || (semFiltroExplicito && ctx.foco() != null && ctx.foco().getIdUltimaNota() != null);

        if (pedirDaNota) {
            Long idNotaAlvo = idNotaRef != null ? idNotaRef : (ctx.foco() != null ? ctx.foco().getIdUltimaNota() : null);
            if (idNotaAlvo == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.foco.nota.ausente",
                        "Nenhuma nota está em foco na sessão atual. Consulte uma nota primeiro (ex: $$ R= #Nota id> 1;).");
            }
            Nota notaFoco = ctx.roteador().notas().buscarNotaPorId(idNotaAlvo);
            if (notaFoco == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.nota.nao.encontrada",
                        "Nota #%s não encontrada.", idNotaAlvo);
            }
            Pessoa p = notaFoco.getPessoa();
            if (p == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                        "Não encontrei pessoa vinculada à Nota #%s.", idNotaAlvo);
            }
            if (ctx.foco() != null) {
                ctx.foco().registrarPessoaEmFoco(p);
            }
            boolean incContatos = alvosCascata.isEmpty() || cascataContem(alvosCascata, "CONTATO", "REGISTRO");
            boolean incNotas = cascataContem(alvosCascata, "NOTA");
            boolean incPags = cascataContem(alvosCascata, "PAGAMENTO");
            RetornoChatBotUI.RetornoPessoaDetalhada recPes = RetornoChatBotUI.construirRecordPessoa(
                    p, notaFoco.getId(), ctx.roteador(), incContatos, incNotas, incPags);
            if (ctx.foco() != null) {
                ctx.foco().registrarRecord(recPes);
            }
            return recPes.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", p.getId());
        }

        if (id != null || !termo.isBlank()) {
            Pessoa p = ResolvedorPessoa.localizarPessoaUnica(ctx.roteador(), id, termo);
            if (p == null) {
                return ContextoIABuilder.formatarPropriedade("ia.erro.pessoa.nao.encontrada",
                        "Não encontrei nenhuma pessoa correspondente a '%s'.", id != null ? "#" + id : termo);
            }
            if (ctx.foco() != null) {
                ctx.foco().registrarPessoaEmFoco(p);
            }
            if (!alvosCascata.isEmpty()) {
                boolean incContatos = alvosCascata.isEmpty() || cascataContem(alvosCascata, "CONTATO", "REGISTRO");
                boolean incNotas = alvosCascata.isEmpty() || cascataContem(alvosCascata, "NOTA");
                boolean incPags = cascataContem(alvosCascata, "PAGAMENTO");
                RetornoChatBotUI.RetornoPessoaDetalhada recPes = RetornoChatBotUI.construirRecordPessoa(
                        p, null, ctx.roteador(), incContatos, incNotas, incPags);
                if (ctx.foco() != null) {
                    ctx.foco().registrarRecord(recPes);
                }
                return recPes.formatarTexto() + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", p.getId());
            }
            return ResolvedorPessoa.formatarDetalhesCompletosPessoa(p, ctx.roteador())
                    + sufixoAvisoRegistroTrancadoLeitura("PessoaDAO", p.getId());
        }

        List<Pessoa> todas = new ArrayList<>();
        todas.addAll(ctx.roteador().pessoas().listarClientes());
        for (Pessoa f : ctx.roteador().pessoas().listarFornecedores()) {
            if (todas.stream().noneMatch(x -> Objects.equals(x.getId(), f.getId()))) {
                todas.add(f);
            }
        }
        if (id != null) {
            Pessoa p = ctx.roteador().pessoas().buscarPorId(id);
            return p != null
                    ? String.format("#%d - %s (%s)", p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), null), ResolvedorPessoa.descreverPapelPessoa(p))
                    : "Nenhuma pessoa encontrada com o código #" + id + ".";
        }
        if (!termo.isBlank()) {
            List<Pessoa> filtradas = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todas, Pessoa::getNome, termo);
            if (filtradas.isEmpty()) {
                return "Não encontrei nenhuma pessoa correspondente a '" + termo + "'.";
            }
            if (filtradas.size() > 1 && ResolvedorProduto.temEmpateDeSigla(filtradas, Pessoa::getNome, termo)) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Encontrei %d pessoas para a sigla '%s'. Qual delas você procura?\n",
                        filtradas.size(), termo));
                for (Pessoa p : filtradas) {
                    sb.append(String.format("  #%d - %s (%s)\n", p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), null), ResolvedorPessoa.descreverPapelPessoa(p)));
                }
                return sb.toString().trim();
            }
            Pessoa p = filtradas.get(0);
            return String.format("#%d - %s (%s)", p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), null), ResolvedorPessoa.descreverPapelPessoa(p));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Pessoas cadastradas (%d registros):\n", todas.size()));
        for (Pessoa p : todas.stream().limit(15).toList()) {
            sb.append(String.format("  #%d - %s (%s)\n", p.getId(), ResolvedorProduto.limparNomeExibicao(p.getNome(), null), ResolvedorPessoa.descreverPapelPessoa(p)));
        }
        return sb.toString().trim();
    }

    private static boolean cascataContem(List<String> alvos, String... prefixos) {
        if (alvos == null || alvos.isEmpty()) return false;
        for (String a : alvos) {
            if (a == null) continue;
            String u = a.trim().toUpperCase(Locale.ROOT);
            for (String p : prefixos) {
                if (u.startsWith(p)) return true;
            }
        }
        return false;
    }

    private static String sufixoAvisoRegistroTrancadoLeitura(String prefixoDao, Long id) {
        if (id == null) return "";
        PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo()) {
            return String.format("\n  [Status Concorrência: Registro trancado para alteração de estado pela sessão '%s' — Consulta liberada]",
                    info.idSessaoBandeja());
        }
        return "";
    }

    private static String verificarBloqueioMutacaoOutraSessao(String prefixoDao, Long id, String nomeExibicao, String idSessaoAtual) {
        if (id == null) return null;
        PadraoDAO.limparBloqueiosExpiradosBandeja();
        var info = PadraoDAO.BLOQUEIOS_BANDEJA_IA.get(prefixoDao + ":" + id);
        if (info != null && info.isAtivo() && (idSessaoAtual == null || !info.idSessaoBandeja().equals(idSessaoAtual))) {
            return ContextoIABuilder.formatarPropriedade(
                    "ia.erro.registro.trancado",
                    "Operacao bloqueada: o registro #%s (%s) esta trancado pela sessao '%s' e nao pode ter seu estado alterado agora (apenas consultas R= sao permitidas).",
                    id, nomeExibicao, info.idSessaoBandeja());
        }
        return null;
    }
}
