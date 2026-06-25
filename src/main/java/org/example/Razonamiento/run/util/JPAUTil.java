package org.example.Razonamiento.run.util;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger; // Corrección: Usar el Logger de SLF4J directamente

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class JPAUTil {
    private static final Logger log = LoggerFactory.getLogger(JPAUTil.class);

    // Corrección: Cambiado a "default" para que coincida con tu persistence.xml
    private static final String PERSISTENCE_UNIT = "default";
    private static volatile EntityManagerFactory emf;

    private JPAUTil() {}

    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            synchronized (JPAUTil.class) {
                if (emf == null) {
                    log.info("Inicializando EntityManagerFactory para '{}'", PERSISTENCE_UNIT);
                    try {
                        emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT);
                    } catch (Exception e) {
                        log.error("Error crítico al crear EntityManagerFactory: ", e);
                        throw e;
                    }
                }
            }
        }
        return emf;
    }

    public static EntityManager createEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    public static void shutdown() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}