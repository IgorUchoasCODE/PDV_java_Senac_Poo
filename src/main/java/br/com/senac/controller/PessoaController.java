package br.com.senac.controller;

import br.com.senac.repository.dao.nota.NotaDAO;
import br.com.senac.repository.dao.pessoa.PessoaDAO;
import br.com.senac.repository.modal.nota.Item;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pagamento.NotaPagamento;
import br.com.senac.repository.modal.pagamento.PagamentoAPrazo;
import br.com.senac.repository.modal.pagamento.PagamentoParcelado;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.Produto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller responsável pelas rotas de Clientes (incluindo o Cliente Padrão não registrado),
 * Fornecedores (PF/PJ), Limite de Crédito, Documentos/Registros Auxiliares e Histórico/Dossiê 360º.
 */
public class PessoaController {

    private final PessoaDAO pessoaDAO = new PessoaDAO();
    private final NotaDAO notaDAO = new NotaDAO();

    /**
     * Rota que retorna o Cliente Padrão ("CONSUMIDOR FINAL (NÃO REGISTRADO)" - CPF 000.000.000-00)
     * utilizado automaticamente quando uma venda é realizada sem cliente identificado.
     */
    public PessoaFisica obterClientePadraoNaoRegistrado() {
        return pessoaDAO.obterOuCriarClientePadrao();
    }

    public PessoaFisica cadastrarClientePessoaFisica(String nome, String cpf, BigDecimal limiteCredito) {
        String cpfLimpo = cpf != null && !cpf.isBlank() ? cpf.trim() : null;
        if (cpfLimpo != null) {
            PessoaFisica existente = pessoaDAO.buscarPorCpf(cpfLimpo);
            if (existente != null) {
                existente.definirComoCliente(limiteCredito);
                return (PessoaFisica) pessoaDAO.salvar(existente);
            }
        }
        PessoaFisica pf = new PessoaFisica(nome, cpfLimpo);
        pf.definirComoCliente(limiteCredito);
        return (PessoaFisica) pessoaDAO.salvar(pf);
    }

    public PessoaJuridica cadastrarClientePessoaJuridica(String razaoSocial, String cnpj, BigDecimal limiteCredito) {
        String cnpjLimpo = cnpj != null && !cnpj.isBlank() ? cnpj.trim() : null;
        if (cnpjLimpo != null) {
            PessoaJuridica existente = pessoaDAO.buscarPorCnpj(cnpjLimpo);
            if (existente != null) {
                existente.definirComoCliente(limiteCredito);
                return (PessoaJuridica) pessoaDAO.salvar(existente);
            }
        }
        PessoaJuridica pj = new PessoaJuridica(razaoSocial, cnpjLimpo);
        pj.definirComoCliente(limiteCredito);
        return (PessoaJuridica) pessoaDAO.salvar(pj);
    }

    public PessoaJuridica cadastrarFornecedorPessoaJuridica(String razaoSocial, String cnpj) {
        String cnpjLimpo = cnpj != null && !cnpj.isBlank() ? cnpj.trim() : null;
        if (cnpjLimpo != null) {
            PessoaJuridica existente = pessoaDAO.buscarPorCnpj(cnpjLimpo);
            if (existente != null) {
                existente.definirComoFornecedor();
                return (PessoaJuridica) pessoaDAO.salvar(existente);
            }
        }
        PessoaJuridica pj = new PessoaJuridica(razaoSocial, cnpjLimpo);
        pj.definirComoFornecedor();
        return (PessoaJuridica) pessoaDAO.salvar(pj);
    }

    public PessoaFisica cadastrarFornecedorPessoaFisica(String nomeProdutorRural, String cpf) {
        String cpfLimpo = cpf != null && !cpf.isBlank() ? cpf.trim() : null;
        if (cpfLimpo != null) {
            PessoaFisica existente = pessoaDAO.buscarPorCpf(cpfLimpo);
            if (existente != null) {
                existente.definirComoFornecedor();
                return (PessoaFisica) pessoaDAO.salvar(existente);
            }
        }
        PessoaFisica pf = new PessoaFisica(nomeProdutorRural, cpfLimpo);
        pf.definirComoFornecedor();
        return (PessoaFisica) pessoaDAO.salvar(pf);
    }

    /**
     * Cadastra uma pessoa definindo se é Cliente, Fornecedor ou Ambos, informando apenas o nome obrigatório.
     * Documento (CPF ou CNPJ) e limite são opcionais.
     */
    public Pessoa cadastrarPessoaSimples(String nome, boolean isCliente, boolean isFornecedor, String documentoOpcional, BigDecimal limiteCredito) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome ou razão social é obrigatório para cadastrar.");
        }
        String docLimpo = documentoOpcional != null && !documentoOpcional.isBlank() ? documentoOpcional.trim() : null;

        if (docLimpo != null) {
            String soDigitos = docLimpo.replaceAll("\\D", "");
            if (soDigitos.length() > 11) {
                PessoaJuridica pj = pessoaDAO.buscarPorCnpj(docLimpo);
                if (pj != null) {
                    if (isCliente) pj.definirComoCliente(limiteCredito);
                    if (isFornecedor) pj.definirComoFornecedor();
                    return pessoaDAO.salvar(pj);
                }
            } else if (!soDigitos.isEmpty()) {
                PessoaFisica pf = pessoaDAO.buscarPorCpf(docLimpo);
                if (pf != null) {
                    if (isCliente) pf.definirComoCliente(limiteCredito);
                    if (isFornecedor) pf.definirComoFornecedor();
                    return pessoaDAO.salvar(pf);
                }
            }
        }

        Pessoa nova;
        if (docLimpo != null && docLimpo.replaceAll("\\D", "").length() > 11) {
            nova = new PessoaJuridica(nome, docLimpo);
        } else if (docLimpo != null && !docLimpo.isBlank()) {
            nova = new PessoaFisica(nome, docLimpo);
        } else if (isFornecedor && !isCliente) {
            nova = new PessoaJuridica(nome, null);
        } else {
            nova = new PessoaFisica(nome, null);
        }

        if (isCliente) {
            nova.definirComoCliente(limiteCredito != null ? limiteCredito : BigDecimal.ZERO);
        }
        if (isFornecedor) {
            nova.definirComoFornecedor();
        }
        return pessoaDAO.salvar(nova);
    }

    public Pessoa atualizarLimiteCreditoCliente(Long idPessoa, BigDecimal novoLimite) {
        Pessoa p = pessoaDAO.buscarPorId(idPessoa);
        if (p == null) {
            throw new IllegalArgumentException("Pessoa não encontrada (ID " + idPessoa + ").");
        }
        if (pessoaDAO.isClientePadrao(p)) {
            throw new IllegalStateException("O Cliente Padrão não registrado não pode ter limite de crediário alterado.");
        }
        p.definirComoCliente(novoLimite);
        return pessoaDAO.salvar(p);
    }

    public Pessoa atualizarDadosCadastrais(Long idPessoa, String novoNome) {
        Pessoa p = pessoaDAO.buscarPorId(idPessoa);
        if (p == null) {
            throw new IllegalArgumentException("Pessoa não encontrada (ID " + idPessoa + ").");
        }
        if (pessoaDAO.isClientePadrao(p)) {
            throw new IllegalStateException("O Cliente Padrão não registrado não pode ter seus dados alterados.");
        }
        if (novoNome != null && !novoNome.isBlank()) {
            p.setNome(novoNome);
        }
        return pessoaDAO.salvar(p);
    }

    public Pessoa adicionarDocumentoRegistro(Long idPessoa, String tipoDocumento, String numeroRegistro) {
        Pessoa p = pessoaDAO.buscarPorId(idPessoa);
        if (p == null) {
            throw new IllegalArgumentException("Pessoa não encontrada (ID " + idPessoa + ").");
        }
        p.adicionarRegistro(tipoDocumento, numeroRegistro);
        return pessoaDAO.salvar(p);
    }

    public Pessoa buscarPorId(Long id) {
        return pessoaDAO.buscarPorId(id);
    }

    public PessoaFisica buscarPorCpf(String cpf) {
        return pessoaDAO.buscarPorCpf(cpf);
    }

    public PessoaJuridica buscarPorCnpj(String cnpj) {
        return pessoaDAO.buscarPorCnpj(cnpj);
    }

    public List<Pessoa> listarClientes() {
        return pessoaDAO.listarClientes();
    }

    public List<Pessoa> listarFornecedores() {
        return pessoaDAO.listarFornecedores();
    }

    public List<Pessoa> listarTodasPessoas() {
        return pessoaDAO.listarTodos();
    }

    public boolean excluirPessoa(Long idPessoa) {
        Pessoa p = pessoaDAO.buscarPorId(idPessoa);
        if (p == null) {
            throw new IllegalArgumentException("Pessoa/Cliente não encontrado com ID: " + idPessoa);
        }
        if (idPessoa.equals(1L)) {
            throw new IllegalArgumentException("O Cliente Padrão (Consumidor Final) não pode ser excluído.");
        }
        pessoaDAO.deletar(idPessoa);
        return true;
    }

    /**
     * Busca clientes ou fornecedores por parte do nome, CPF ou CNPJ.
     */
    public List<Pessoa> buscarPessoasPorTermo(String termo, String papel) {
        return pessoaDAO.buscarPorTermo(termo, papel);
    }

    public Pessoa buscarUnicaPessoa(Long id, String termoOuNome) {
        return pessoaDAO.buscarUnicaPessoa(id, termoOuNome);
    }

    /**
     * Busca um cliente ou fornecedor específico por ID, documento ou palavras parciais/iniciais.
     * Retorna a {@link Pessoa} única se identificada especificamente, ou uma {@link List} de pessoas
     * caso existam múltiplos candidatos compatíveis para o usuário escolher.
     */
    public Object buscarEspecificaOuListarParaEscolha(Long id, String termoOuNome, String papelOpcional) {
        return pessoaDAO.buscarEspecificaOuListarParaEscolha(id, termoOuNome, papelOpcional);
    }

    public Pessoa buscarPorRegistroUnico(String valorRegistro) {
        return pessoaDAO.buscarPorRegistroUnico(valorRegistro);
    }

    public Pessoa buscarPessoaPorRegistroUnico(String tipoOpcional, String valorRegistro) {
        return pessoaDAO.buscarPorRegistroUnico(tipoOpcional, valorRegistro);
    }

    /**
     * Consulta o histórico 360º (dossiê completo) de um cliente ou fornecedor:
     * dados cadastrais, todas as notas emitidas vinculadas a ele, itens de produtos e pagamentos.
     */
    public Map<String, Object> consultarHistoricoCompletoPessoa(Long idPessoa, String termoOpcional, String papelOpcional) {
        Pessoa p = null;
        if (idPessoa != null) {
            p = pessoaDAO.buscarPorId(idPessoa);
        }
        if (p == null && termoOpcional != null && !termoOpcional.isBlank()) {
            List<Pessoa> encontradas = pessoaDAO.buscarPorTermo(termoOpcional, papelOpcional);
            if (!encontradas.isEmpty()) {
                p = encontradas.get(0);
            }
        }
        if (p == null) {
            throw new IllegalArgumentException("Nenhuma pessoa/cliente/fornecedor encontrado para os parâmetros informados.");
        }

        List<Nota> notas = notaDAO.listarNotasPorPessoa(p.getId());

        Map<String, Object> dossie = new LinkedHashMap<>();

        // 1. Dados Cadastrais da Pessoa
        Map<String, Object> dadosPessoa = new LinkedHashMap<>();
        dadosPessoa.put("id", p.getId());
        dadosPessoa.put("nome", p.getNome());
        dadosPessoa.put("isCliente", p.isCliente());
        dadosPessoa.put("isFornecedor", p.isFornecedor());
        dadosPessoa.put("limiteCredito", p.getLimite());

        if (p instanceof PessoaFisica pf) {
            dadosPessoa.put("tipoPessoa", "PF");
            dadosPessoa.put("documento", pf.getCpf() != null ? pf.getCpf() : "NÃO INFORMADO");
        } else if (p instanceof PessoaJuridica pj) {
            dadosPessoa.put("tipoPessoa", "PJ");
            dadosPessoa.put("documento", pj.getCnpj() != null ? pj.getCnpj() : "NÃO INFORMADO");
        }

        List<String> contatos = new ArrayList<>();
        if (p.getRegistros() != null) {
            p.getRegistros().forEach(r -> contatos.add(r.getTipo() + ": " + r.getRegistro()));
        }
        dadosPessoa.put("contatosRegistros", contatos);
        dossie.put("pessoa", dadosPessoa);

        // 2. Detalhes das Notas, Itens e Pagamentos
        BigDecimal totalValorNotas = BigDecimal.ZERO;
        BigDecimal totalItensQtd = BigDecimal.ZERO;
        BigDecimal totalPagamentosValor = BigDecimal.ZERO;
        List<Map<String, Object>> listaNotas = new ArrayList<>();

        for (Nota n : notas) {
            Map<String, Object> nMap = new LinkedHashMap<>();
            nMap.put("idNota", n.getId());
            nMap.put("tipoNota", n.getClass().getSimpleName());
            nMap.put("dataGerada", n.getDataGerada() != null ? n.getDataGerada().toString() : "");
            nMap.put("descricao", n.getDescricao());
            nMap.put("valorBruto", n.getValorBruto());
            nMap.put("descontoGlobal", n.getDescontoGlobal());
            BigDecimal vTotal = n.getValorTotal();
            nMap.put("valorTotal", vTotal);
            totalValorNotas = totalValorNotas.add(vTotal != null ? vTotal : BigDecimal.ZERO);

            // Itens de Produtos
            List<Map<String, Object>> itensList = new ArrayList<>();
            if (n.getItens() != null) {
                for (Item<? extends Produto> it : n.getItens()) {
                    Map<String, Object> itMap = new LinkedHashMap<>();
                    itMap.put("idItem", it.getId());
                    if (it.getProduto() != null) {
                        itMap.put("idProduto", it.getProduto().getId());
                        itMap.put("nomeProduto", it.getProduto().getNome());
                        itMap.put("unidade", it.getProduto().getUm() != null ? it.getProduto().getUm().getSigla() : "UN");
                    }
                    itMap.put("quantidade", it.getQuantidade());
                    itMap.put("precoUnitario", it.getValor());
                    itMap.put("subtotal", it.getSubtotal());
                    if (it.getDataValidade() != null) {
                        itMap.put("dataValidade", it.getDataValidade().toString());
                    }
                    totalItensQtd = totalItensQtd.add(it.getQuantidade() != null ? it.getQuantidade() : BigDecimal.ZERO);
                    itensList.add(itMap);
                }
            }
            nMap.put("itensProdutos", itensList);

            // Pagamentos
            List<Map<String, Object>> pagsList = new ArrayList<>();
            if (n.getPagamentos() != null) {
                for (NotaPagamento pag : n.getPagamentos()) {
                    Map<String, Object> pMap = new LinkedHashMap<>();
                    pMap.put("idPagamento", pag.getId());
                    pMap.put("forma", pag.getFormaPagamento() != null ? pag.getFormaPagamento().getNome() : "PADRÃO");
                    pMap.put("valorNominal", pag.getValor());
                    pMap.put("valorFinal", pag.calcularValorFinal());
                    pMap.put("data", pag.getData() != null ? pag.getData().toString() : "");

                    if (pag instanceof PagamentoAPrazo pap) {
                        pMap.put("tipo", "A_PRAZO");
                        pMap.put("vencimento", pap.getDataVencimento() != null ? pap.getDataVencimento().toString() : "");
                        pMap.put("quitado", pap.isLiquidado());
                    } else if (pag instanceof PagamentoParcelado pp) {
                        pMap.put("tipo", "PARCELADO");
                        pMap.put("quantidadeParcelas", pp.getParcelas() != null ? pp.getParcelas().size() : 0);
                        pMap.put("totalQuitado", pp.getTotalQuitadoParcelas());
                        pMap.put("saldoDevedor", pp.getSaldoDevedorParcelas());
                    } else {
                        pMap.put("tipo", "A_VISTA");
                        pMap.put("quitado", true);
                    }
                    totalPagamentosValor = totalPagamentosValor.add(pag.calcularValorFinal());
                    pagsList.add(pMap);
                }
            }
            nMap.put("pagamentos", pagsList);
            listaNotas.add(nMap);
        }

        dossie.put("notas", listaNotas);

        // 3. Resumo Financeiro
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("totalNotasEmitidas", notas.size());
        resumo.put("valorTotalMovimentado", totalValorNotas);
        resumo.put("quantidadeTotalItens", totalItensQtd);
        resumo.put("valorTotalPagamentosRegistrados", totalPagamentosValor);
        dossie.put("resumoFinanceiro", resumo);

        return dossie;
    }
}
