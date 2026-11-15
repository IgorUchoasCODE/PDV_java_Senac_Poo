package br.com.senac.application.terminal;

import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.modal.nota.Nota;
import br.com.senac.repository.modal.pagamento.FormaPagamento;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.produto.Produto;
import br.com.senac.repository.modal.produto.Um;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação de {@link FonteEntidadesTerminal} apoiada nos controllers reais do projeto.
 */
public class CatalogoControladoresTerminal implements FonteEntidadesTerminal {

    private final RoteadorController roteador;
    private final List<ProdutoResumo> produtos = Collections.synchronizedList(new ArrayList<>());
    private final List<PessoaResumo> pessoas = Collections.synchronizedList(new ArrayList<>());
    private final List<NotaResumo> notas = Collections.synchronizedList(new ArrayList<>());
    private final List<String> siglasUm = Collections.synchronizedList(new ArrayList<>());
    private final List<FormaPagamentoResumo> formasPagamento = Collections.synchronizedList(new ArrayList<>());

    private final Map<Long, ProdutoResumo> produtosPorId = new ConcurrentHashMap<>();
    private final Map<Long, PessoaResumo> pessoasPorId = new ConcurrentHashMap<>();
    private final Map<Long, NotaResumo> notasPorId = new ConcurrentHashMap<>();
    private final Map<String, Optional<PessoaResumo>> cacheRegistros = new ConcurrentHashMap<>();

    public CatalogoControladoresTerminal(RoteadorController roteador) {
        this.roteador = roteador;
    }

    @Override
    public void recarregar() {
        if (roteador == null) return;
        carregarProdutos();
        carregarPessoas();
        carregarNotas();
        carregarUnidades();
        carregarFormasPagamento();
        cacheRegistros.clear();
    }

    private void carregarProdutos() {
        try {
            List<Produto> lista = roteador.produtos().listarTodosProdutos();
            List<ProdutoResumo> novos = new ArrayList<>();
            produtosPorId.clear();
            if (lista != null) {
                for (Produto p : lista) {
                    if (p == null || p.getNome() == null || p.getNome().isBlank()) continue;
                    String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
                    ProdutoResumo resumo = new ProdutoResumo(
                            p.getId(),
                            p.getNome().trim(),
                            BuscaPalavrasParciaisUtil.normalizar(p.getNome()),
                            p.getPrecoVenda(),
                            um,
                            p.isConjunto()
                    );
                    novos.add(resumo);
                    if (resumo.id() != null) produtosPorId.put(resumo.id(), resumo);
                }
            }
            synchronized (produtos) {
                produtos.clear();
                produtos.addAll(novos);
            }
        } catch (Exception ignored) {
        }
    }

    private void carregarPessoas() {
        try {
            List<Pessoa> lista = roteador.pessoas().listarTodasPessoas();
            List<PessoaResumo> novas = new ArrayList<>();
            pessoasPorId.clear();
            if (lista != null) {
                for (Pessoa p : lista) {
                    if (p == null || p.getNome() == null || p.getNome().isBlank()) continue;
                    String doc = extrairDocumento(p);
                    PessoaResumo resumo = converterPessoa(p, doc);
                    novas.add(resumo);
                    if (resumo.id() != null) pessoasPorId.put(resumo.id(), resumo);
                }
            }
            synchronized (pessoas) {
                pessoas.clear();
                pessoas.addAll(novas);
            }
        } catch (Exception ignored) {
        }
    }

    private void carregarNotas() {
        try {
            List<Nota> lista = roteador.notas().listarTodasNotas();
            List<NotaResumo> novas = new ArrayList<>();
            notasPorId.clear();
            if (lista != null) {
                for (Nota n : lista) {
                    if (n == null || n.getId() == null) continue;
                    NotaResumo resumo = new NotaResumo(n.getId(), n.getClass().getSimpleName(), n.getValorBruto());
                    novas.add(resumo);
                    notasPorId.put(n.getId(), resumo);
                }
            }
            synchronized (notas) {
                notas.clear();
                notas.addAll(novas);
            }
        } catch (Exception ignored) {
        }
    }

    private void carregarUnidades() {
        try {
            List<Um> lista = roteador.produtos().listarUnidadesMedida();
            List<String> novas = new ArrayList<>();
            if (lista != null) {
                for (Um um : lista) {
                    if (um != null && um.getSigla() != null && !um.getSigla().isBlank()) {
                        novas.add(um.getSigla().trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
            synchronized (siglasUm) {
                siglasUm.clear();
                siglasUm.addAll(novas);
            }
        } catch (Exception ignored) {
        }
    }

    private void carregarFormasPagamento() {
        try {
            List<FormaPagamento> lista = roteador.pagamentos().listarFormasPagamentoAtivas();
            List<FormaPagamentoResumo> novas = new ArrayList<>();
            if (lista != null) {
                for (FormaPagamento fp : lista) {
                    if (fp == null || fp.getNome() == null) continue;
                    novas.add(new FormaPagamentoResumo(
                            fp.getCodigo() != null ? fp.getCodigo().trim() : "",
                            fp.getNome().trim(),
                            BuscaPalavrasParciaisUtil.normalizar(fp.getNome())
                    ));
                }
            }
            synchronized (formasPagamento) {
                formasPagamento.clear();
                formasPagamento.addAll(novas);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<ProdutoResumo> listarProdutos() {
        synchronized (produtos) {
            return new ArrayList<>(produtos);
        }
    }

    @Override
    public List<PessoaResumo> listarPessoas() {
        synchronized (pessoas) {
            return new ArrayList<>(pessoas);
        }
    }

    @Override
    public List<NotaResumo> listarNotas() {
        synchronized (notas) {
            return new ArrayList<>(notas);
        }
    }

    @Override
    public List<String> listarSiglasUm() {
        synchronized (siglasUm) {
            return new ArrayList<>(siglasUm);
        }
    }

    @Override
    public List<FormaPagamentoResumo> listarFormasPagamento() {
        synchronized (formasPagamento) {
            return new ArrayList<>(formasPagamento);
        }
    }

    @Override
    public Optional<ProdutoResumo> buscarProdutoPorId(Long id) {
        if (id == null) return Optional.empty();
        ProdutoResumo emCache = produtosPorId.get(id);
        if (emCache != null) return Optional.of(emCache);
        try {
            Produto p = roteador.produtos().buscarPorId(id);
            if (p != null) {
                String um = p.getUm() != null ? p.getUm().getSigla() : "UN";
                ProdutoResumo resumo = new ProdutoResumo(
                        p.getId(), p.getNome(), BuscaPalavrasParciaisUtil.normalizar(p.getNome()),
                        p.getPrecoVenda(), um, p.isConjunto());
                produtosPorId.put(id, resumo);
                return Optional.of(resumo);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    @Override
    public Optional<PessoaResumo> buscarPessoaPorId(Long id) {
        if (id == null) return Optional.empty();
        PessoaResumo emCache = pessoasPorId.get(id);
        if (emCache != null) return Optional.of(emCache);
        try {
            Pessoa p = roteador.pessoas().buscarPorId(id);
            if (p != null) {
                PessoaResumo resumo = converterPessoa(p, extrairDocumento(p));
                pessoasPorId.put(id, resumo);
                return Optional.of(resumo);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    @Override
    public Optional<NotaResumo> buscarNotaPorId(Long id) {
        if (id == null) return Optional.empty();
        NotaResumo emCache = notasPorId.get(id);
        if (emCache != null) return Optional.of(emCache);
        try {
            Nota n = roteador.notas().buscarNotaPorId(id);
            if (n != null) {
                NotaResumo resumo = new NotaResumo(n.getId(), n.getClass().getSimpleName(), n.getValorBruto());
                notasPorId.put(id, resumo);
                return Optional.of(resumo);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    @Override
    public Optional<PessoaResumo> buscarPessoaPorRegistro(String valorRegistro) {
        if (valorRegistro == null || valorRegistro.isBlank()) return Optional.empty();
        String limpo = valorRegistro.trim();
        String digitos = limpo.replaceAll("[^0-9]", "");

        // 1. Verifica documentos já carregados em memória (CPF/CNPJ)
        for (PessoaResumo p : listarPessoas()) {
            if (p.documento() != null && !p.documento().isEmpty()) {
                String docDigitos = p.documento().replaceAll("[^0-9]", "");
                if (p.documento().equalsIgnoreCase(limpo)
                        || (!digitos.isEmpty() && digitos.equals(docDigitos))) {
                    return Optional.of(p);
                }
            }
        }

        // 2. Consulta registro único real no banco (ex.: RG, inscrição estadual, telefone, e-mail, etc.)
        return cacheRegistros.computeIfAbsent(limpo.toLowerCase(Locale.ROOT), k -> {
            try {
                Pessoa encontrada = roteador.pessoas().buscarPorRegistroUnico(limpo);
                if (encontrada == null && !digitos.isEmpty() && !digitos.equals(limpo)) {
                    encontrada = roteador.pessoas().buscarPorRegistroUnico(digitos);
                }
                if (encontrada != null) {
                    return Optional.of(converterPessoa(encontrada, limpo));
                }
            } catch (Exception ignored) {
            }
            return Optional.empty();
        });
    }

    private static String extrairDocumento(Pessoa p) {
        if (p instanceof PessoaFisica pf && pf.getCpf() != null) {
            return pf.getCpf();
        }
        if (p instanceof PessoaJuridica pj && pj.getCnpj() != null) {
            return pj.getCnpj();
        }
        return "";
    }

    private static PessoaResumo converterPessoa(Pessoa p, String documento) {
        return new PessoaResumo(
                p.getId(),
                p.getNome().trim(),
                BuscaPalavrasParciaisUtil.normalizar(p.getNome()),
                p.isCliente(),
                p.isFornecedor(),
                documento != null ? documento : ""
        );
    }
}
