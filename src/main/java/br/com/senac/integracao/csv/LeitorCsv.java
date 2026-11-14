package br.com.senac.integracao.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor CSV compatível com RFC-4180 e planilhas brasileiras (UTF-8 com ou sem BOM,
 * detecção automática de delimitador ';', ',' ou '\t', suporte a aspas e escapes).
 */
public class LeitorCsv {

    public record TabelaCsv(
            char delimitadorDetectado,
            List<String> cabecalhos,
            List<Map<String, String>> linhas
    ) {
        public boolean isVazia() {
            return linhas.isEmpty();
        }
    }

    public TabelaCsv lerArquivo(Path caminhoArquivo) throws IOException {
        return lerArquivo(caminhoArquivo, null);
    }

    public TabelaCsv lerArquivo(Path caminhoArquivo, Character delimitadorExplicito) throws IOException {
        if (caminhoArquivo == null) {
            throw new IllegalArgumentException("Caminho do arquivo CSV não informado.");
        }
        String conteudo = Files.readString(caminhoArquivo, StandardCharsets.UTF_8);
        return lerTexto(conteudo, delimitadorExplicito);
    }

    public TabelaCsv lerStream(InputStream inputStream) throws IOException {
        return lerStream(inputStream, null);
    }

    public TabelaCsv lerStream(InputStream inputStream, Character delimitadorExplicito) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream do CSV não informado.");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha).append('\n');
            }
        }
        return lerTexto(sb.toString(), delimitadorExplicito);
    }

    public TabelaCsv lerTexto(String conteudoCsv) throws IOException {
        return lerTexto(conteudoCsv, null);
    }

    public TabelaCsv lerTexto(String conteudoCsv, Character delimitadorExplicito) throws IOException {
        if (conteudoCsv == null || conteudoCsv.isBlank()) {
            return new TabelaCsv(';', Collections.emptyList(), Collections.emptyList());
        }

        String limpo = removerBomUtf8(conteudoCsv);
        List<String> linhasBrutas = lerRegistrosFisicos(limpo);
        if (linhasBrutas.isEmpty()) {
            return new TabelaCsv(';', Collections.emptyList(), Collections.emptyList());
        }

        String primeiraLinha = linhasBrutas.get(0);
        char delimitador = delimitadorExplicito != null ? delimitadorExplicito : detectarDelimitador(primeiraLinha);

        List<String> cabecalhosBrutos = parsearLinha(primeiraLinha, delimitador);
        List<String> cabecalhos = new ArrayList<>(cabecalhosBrutos.size());
        for (String h : cabecalhosBrutos) {
            cabecalhos.add(h != null ? h.trim() : "");
        }

        List<Map<String, String>> linhas = new ArrayList<>();
        for (int i = 1; i < linhasBrutas.size(); i++) {
            String registro = linhasBrutas.get(i);
            if (registro.isBlank()) {
                continue;
            }
            List<String> valores = parsearLinha(registro, delimitador);
            Map<String, String> mapa = new LinkedHashMap<>();
            for (int c = 0; c < cabecalhos.size(); c++) {
                String chave = cabecalhos.get(c);
                if (chave.isEmpty()) {
                    continue;
                }
                String valor = c < valores.size() ? valores.get(c).trim() : "";
                mapa.put(chave, valor);
            }
            linhas.add(Collections.unmodifiableMap(mapa));
        }

        return new TabelaCsv(
                delimitador,
                Collections.unmodifiableList(cabecalhos),
                Collections.unmodifiableList(linhas)
        );
    }

    private String removerBomUtf8(String texto) {
        if (texto.startsWith("\uFEFF")) {
            return texto.substring(1);
        }
        return texto;
    }

    private char detectarDelimitador(String linhaCabecalho) {
        int pontoVirgula = contarForaDeAspas(linhaCabecalho, ';');
        int virgula = contarForaDeAspas(linhaCabecalho, ',');
        int tab = contarForaDeAspas(linhaCabecalho, '\t');

        if (pontoVirgula >= virgula && pontoVirgula >= tab && pontoVirgula > 0) {
            return ';';
        }
        if (virgula >= tab && virgula > 0) {
            return ',';
        }
        if (tab > 0) {
            return '\t';
        }
        return ';';
    }

    private int contarForaDeAspas(String linha, char alvo) {
        boolean emAspas = false;
        int count = 0;
        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (ch == '"') {
                emAspas = !emAspas;
            } else if (!emAspas && ch == alvo) {
                count++;
            }
        }
        return count;
    }

    private List<String> lerRegistrosFisicos(String texto) throws IOException {
        List<String> registros = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean emAspas = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(texto))) {
            int chInt;
            while ((chInt = reader.read()) != -1) {
                char ch = (char) chInt;
                if (ch == '"') {
                    emAspas = !emAspas;
                    atual.append(ch);
                } else if ((ch == '\n' || ch == '\r') && !emAspas) {
                    if (ch == '\r') {
                        reader.mark(1);
                        int prox = reader.read();
                        if (prox != '\n' && prox != -1) {
                            reader.reset();
                        }
                    }
                    if (!atual.isEmpty()) {
                        registros.add(atual.toString());
                        atual.setLength(0);
                    }
                } else {
                    atual.append(ch);
                }
            }
        }
        if (!atual.isEmpty()) {
            registros.add(atual.toString());
        }
        return registros;
    }

    private List<String> parsearLinha(String linha, char delimitador) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean emAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (emAspas) {
                if (ch == '"') {
                    if (i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        emAspas = false;
                    }
                } else {
                    campo.append(ch);
                }
            } else {
                if (ch == '"') {
                    emAspas = true;
                } else if (ch == delimitador) {
                    campos.add(campo.toString());
                    campo.setLength(0);
                } else {
                    campo.append(ch);
                }
            }
        }
        campos.add(campo.toString());
        return campos;
    }
}
