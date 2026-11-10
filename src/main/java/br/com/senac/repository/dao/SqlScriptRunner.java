package br.com.senac.repository.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Executa scripts .sql do classpath com suporte a comentários "--" e troca de DELIMITER (procedures/triggers). */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    public static void executarRecurso(Connection conn, String recurso) throws SQLException, IOException {
        for (String comando : dividir(lerRecurso(recurso))) {
            try (Statement st = conn.createStatement()) {
                st.execute(comando);
            }
        }
    }

    static String lerRecurso(String recurso) throws IOException {
        try (InputStream in = SqlScriptRunner.class.getResourceAsStream(recurso)) {
            if (in == null) throw new IOException("Recurso SQL não encontrado: " + recurso);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String linha;
                while ((linha = r.readLine()) != null) sb.append(linha).append('\n');
                return sb.toString();
            }
        }
    }

    static List<String> dividir(String script) {
        List<String> comandos = new ArrayList<>();
        String delimitador = ";";
        StringBuilder atual = new StringBuilder();
        for (String linha : script.split("\n")) {
            String t = linha.trim();
            if (atual.length() == 0 && (t.isEmpty() || t.startsWith("--"))) continue;
            if (t.toUpperCase().startsWith("DELIMITER ")) {
                delimitador = t.substring(10).trim();
                continue;
            }
            if (t.startsWith("--")) continue;
            atual.append(linha).append('\n');
            if (t.endsWith(delimitador)) {
                String cmd = atual.toString().trim();
                cmd = cmd.substring(0, cmd.length() - delimitador.length()).trim();
                if (!cmd.isEmpty()) comandos.add(cmd);
                atual.setLength(0);
            }
        }
        if (atual.toString().trim().length() > 0) comandos.add(atual.toString().trim());
        return comandos;
    }
}
