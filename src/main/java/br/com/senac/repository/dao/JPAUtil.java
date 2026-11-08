package br.com.senac.repository.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class JPAUtil {

    // "sazonalisei" = Seu MySQL local | "sazonalisei-embarcado" = Banco H2 Embarcado (modo MySQL)
    private static final String PERSISTENCE_UNIT_MYSQL = "sazonalisei";
    private static final String PERSISTENCE_UNIT_EMBARCADO = "sazonalisei-embarcado";

    private static EntityManagerFactory emf;

    private JPAUtil() {
    }

    public static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            garantirArquivoPersistence();
            try {
                // 1ª Tentativa: Usa a sua persistência principal ("sazonalisei" - MySQL) se existir no persistence.xml
                emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_MYSQL);
            } catch (Exception e) {
                // Fallback automático: Se quem clonou do Git estiver apenas com o 'sazonalisei-embarcado', inicia o H2 embarcado
                System.out.println("📦 [JPAUtil] Persistência MySQL não encontrada ou indisponível. Iniciando banco embarcado (" + PERSISTENCE_UNIT_EMBARCADO + ")...");
                emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_EMBARCADO);
            }
        }
        return emf;
    }

    /**
     * Caso alguém clone o projeto do Git (onde o persistence.xml com senha foi ignorado),
     * copia automaticamente o persistence-embarcado.xml para persistence.xml.
     */
    private static void garantirArquivoPersistence() {
        Path persistenceXml = Paths.get("src/main/resources/META-INF/persistence.xml");
        Path persistenceEmbarcado = Paths.get("src/main/resources/META-INF/persistence-embarcado.xml");
        Path targetPersistenceXml = Paths.get("target/classes/META-INF/persistence.xml");

        try {
            if (!Files.exists(persistenceXml) && Files.exists(persistenceEmbarcado)) {
                Files.copy(persistenceEmbarcado, persistenceXml, StandardCopyOption.REPLACE_EXISTING);
                if (Files.exists(targetPersistenceXml.getParent())) {
                    Files.copy(persistenceEmbarcado, targetPersistenceXml, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static EntityManager getEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static synchronized void close() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
