package br.com.senac.application.terminal;

import br.com.senac.application.terminal.ModelosAnaliseTerminal.TrechoDestacado;
import br.com.senac.repository.dao.BuscaPalavrasParciaisUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contrato extensível (OCP/SRP) para identificadores de entidades, IDs, registros e padrões em texto.
 * Novos tipos de identificação podem ser adicionados sem alterar o motor de análise.
 */
public interface IdentificadorEntidadeTerminal {

    void identificar(ContextoIdentificacao ctx);

    record TokenTexto(String original, String normalizado, int inicio, int fim) {}

    final class ContextoIdentificacao {
        private static final Pattern PADRAO_TOKEN = Pattern.compile("[\\p{L}\\p{N}_.,-]+");

        private final String texto;
        private final FonteEntidadesTerminal fonte;
        private final List<TokenTexto> tokens;
        private final List<TrechoDestacado> trechos = new ArrayList<>();
        private final List<String> entidadesResumo = new ArrayList<>();
        private final Set<String> chavesUnicas = new LinkedHashSet<>();

        public ContextoIdentificacao(String texto, FonteEntidadesTerminal fonte) {
            this.texto = texto != null ? texto : "";
            this.fonte = fonte;
            this.tokens = tokenizar(this.texto);
        }

        public String texto() {
            return texto;
        }

        public FonteEntidadesTerminal fonte() {
            return fonte;
        }

        public List<TokenTexto> tokens() {
            return tokens;
        }

        public List<TrechoDestacado> trechos() {
            return trechos;
        }

        public List<String> entidadesResumo() {
            return entidadesResumo;
        }

        public Set<String> chavesUnicas() {
            return chavesUnicas;
        }

        public boolean sobrepoe(int inicio, int fim) {
            for (TrechoDestacado t : trechos) {
                if (inicio < t.fim() && fim > t.inicio()) {
                    return true;
                }
            }
            return false;
        }

        public void adicionar(int inicio, int fim, String corAnsi, String categoria, String chaveUnica, String resumo) {
            if (inicio >= fim || sobrepoe(inicio, fim)) {
                return;
            }
            trechos.add(new TrechoDestacado(inicio, fim, corAnsi, categoria, resumo));
            if (chaveUnica != null && resumo != null && chavesUnicas.add(chaveUnica)) {
                entidadesResumo.add(resumo);
            }
        }

        private static List<TokenTexto> tokenizar(String s) {
            List<TokenTexto> lista = new ArrayList<>();
            Matcher m = PADRAO_TOKEN.matcher(s);
            while (m.find()) {
                String raw = m.group();
                String norm = BuscaPalavrasParciaisUtil.normalizar(raw);
                if (!norm.isEmpty()) {
                    lista.add(new TokenTexto(raw, norm, m.start(), m.end()));
                }
            }
            return lista;
        }
    }
}
