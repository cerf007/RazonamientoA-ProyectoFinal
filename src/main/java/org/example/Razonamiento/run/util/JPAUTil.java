package org.example.Razonamiento.run.util;

import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import java.util.logging.Logger;

public class JPAUTil {
    private static final Logger log = (Logger) LoggerFactory.getLogger(JPAUTil.class);
    private static final String PERSISTENCE_UNIT = "bfa-pu";
    private static volatile EntityManager emf;

    private JPAUTil() {}

    public static EntityManager getEntityManagerFactory() {
        if (emf == null) {
            synchronized (JPAUTil.class) {
                if (emf == null) {
                    log.info("Inicializando EntityManagerFactory para '{}'");
                    emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT).createEntityManager();
                }
            }
        }
        return emf;
    }

    public static EntityManager createEntityManager() {
        return getEntityManagerFactory().getEntityManagerFactory().createEntityManager();
    }

    public static void shutdown() {
        if (emf != null && emf.isOpen()) emf.close();
    }
}