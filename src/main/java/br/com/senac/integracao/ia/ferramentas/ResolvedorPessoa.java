package br.com.senac.integracao.ia.ferramentas;

import br.com.senac.controller.RoteadorController;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;
import br.com.senac.repository.modal.pessoa.Pessoa;
import br.com.senac.repository.modal.pessoa.PessoaFisica;
import br.com.senac.repository.modal.pessoa.PessoaJuridica;
import br.com.senac.repository.modal.pessoa.Registro;

import java.util.*;

/**
 * Utilitário para localização, descrição e desduplicação de clientes e fornecedores.
 */
public final class ResolvedorPessoa {

    private ResolvedorPessoa() {}

    public static Pessoa localizarPessoaUnica(RoteadorController roteador, Long id, String termo) {
        if (id != null) {
            return roteador.pessoas().buscarPorId(id);
        }
        if (termo == null || termo.isBlank()) return null;
        String t = termo.trim();
        if (t.matches("^#?\\d+$")) {
            try {
                Pessoa porId = roteador.pessoas().buscarPorId(Long.parseLong(t.replace("#", "")));
                if (porId != null) return porId;
            } catch (Exception ignored) {}
        }
        String digitos = t.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            Pessoa pf = roteador.pessoas().buscarPorCpf(t);
            if (pf != null) return pf;
        } else if (digitos.length() == 14) {
            Pessoa pj = roteador.pessoas().buscarPorCnpj(t);
            if (pj != null) return pj;
        }
        List<Pessoa> todas = new ArrayList<>();
        todas.addAll(roteador.pessoas().listarFornecedores());
        for (Pessoa c : roteador.pessoas().listarClientes()) {
            if (todas.stream().noneMatch(x -> Objects.equals(x.getId(), c.getId()))) {
                todas.add(c);
            }
        }
        List<Pessoa> filtradas = BuscaPalavrasParciaisUtil.filtrarEOrdenar(todas, Pessoa::getNome, termo);
        return filtradas.isEmpty() ? null : filtradas.get(0);
    }

    public static String descreverPapelPessoa(Pessoa p) {
        if (p == null) return "Pessoa";
        if (p.isCliente() && p.isFornecedor()) return "Cliente e Fornecedor";
        if (p.isFornecedor()) return "Fornecedor";
        return "Cliente";
    }

    public static String limparNomeExibicao(String nome, String sigla) {
        return ResolvedorProduto.limparNomeExibicao(nome, sigla);
    }

    public static List<Pessoa> filtrarPessoasExibicaoLimpa(List<Pessoa> pessoas) {
        if (pessoas == null) return Collections.emptyList();
        Map<String, Pessoa> canonicos = new LinkedHashMap<>();
        for (Pessoa p : pessoas) {
            String nomeLimpo = limparNomeExibicao(p.getNome(), null);
            String chave = nomeLimpo.toLowerCase(Locale.ROOT);
            if (!canonicos.containsKey(chave)) {
                canonicos.put(chave, p);
            }
        }
        return new ArrayList<>(canonicos.values());
    }

    /**
     * Formata de maneira rica, humana e completa todas as informações da entidade Pessoa:
     * identificação, nome/razão social, natureza jurídica (PF com CPF ou PJ com CNPJ),
     * papel comercial (Cliente, Fornecedor ou Ambos), limite de crédito e documentos/contatos registrados.
     */
    public static String formatarDetalhesCompletosPessoa(Pessoa p, RoteadorController roteador) {
        if (p == null) return "Nenhuma pessoa selecionada.";
        String nomeLimpo = limparNomeExibicao(p.getNome(), null);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#%d - %s%n", p.getId(), nomeLimpo));

        // 1. Natureza Jurídica: PF vs PJ
        if (p instanceof PessoaJuridica pj) {
            sb.append("• Natureza: Pessoa Jurídica (PJ)\n");
            sb.append(String.format("  CNPJ: %s\n", pj.getCnpj() != null && !pj.getCnpj().isBlank() ? pj.getCnpj() : "Não informado"));
        } else if (p instanceof PessoaFisica pf) {
            sb.append("• Natureza: Pessoa Física (PF)\n");
            sb.append(String.format("  CPF: %s\n", pf.getCpf() != null && !pf.getCpf().isBlank() ? pf.getCpf() : "Não informado"));
        } else {
            sb.append("• Natureza: Pessoa\n");
        }

        // 2. Papel Comercial
        String papel = descreverPapelPessoa(p);
        sb.append(String.format("• Papel comercial: %s\n", papel));

        // 3. Limite de Crédito
        String limite = p.getLimite() != null
                ? p.getLimite().setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()
                : "0.00";
        sb.append(String.format("• Limite de crédito: R$ %s\n", limite));

        // 4. Documentos e Contatos Adicionais
        List<Registro> registros = p.getRegistros();
        if (registros != null && !registros.isEmpty()) {
            sb.append(String.format("• Documentos e contatos adicionais (%d):\n", registros.size()));
            for (Registro reg : registros) {
                sb.append(String.format("  - %s: %s\n", reg.getTipo(), reg.getRegistro()));
            }
        } else {
            sb.append("• Documentos/contatos adicionais: Nenhum registro adicional cadastrado.\n");
        }

        return sb.toString().trim();
    }
}
