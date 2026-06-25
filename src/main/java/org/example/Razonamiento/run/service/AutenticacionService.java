package org.example.Razonamiento.run.service;

import org.example.Razonamiento.run.model.EstadoSesion;
import org.example.Razonamiento.run.model.Evaluador;
import org.example.Razonamiento.run.model.SesionPrueba;
import org.example.Razonamiento.run.util.JPAUTil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

public class AutenticacionService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionService.class);
    
    public boolean loginEvaluador(String username, String password) {
        if (username == null || password == null) return false;
        JPAUTil JPAUtil = null;
        EntityManager em = JPAUtil.createEntityManager();
        try {
            em.createQuery(
                            "SELECT e FROM Evaluador e WHERE e.username = :u AND e.password = :p",
                            Evaluador.class)
                    .setParameter("u", username.trim())
                    .setParameter("p", password)
                    .getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        } finally {
            em.close();
        }
    }

    public SesionPrueba validarCodigoSesion(String codigoSesion) {
        if (codigoSesion == null || codigoSesion.isBlank()) return null;
        JPAUTil JPAUtil = null;
        EntityManager em = JPAUtil.createEntityManager();
        try {
            SesionPrueba sesion = em.createQuery(
                            "SELECT s FROM SesionPrueba s WHERE s.codigoSesion = :codigo",
                            SesionPrueba.class)
                    .setParameter("codigo", codigoSesion.trim().toUpperCase())
                    .getSingleResult();

            if (sesion.getEstado() != EstadoSesion.PROGRAMADA
                    && sesion.getEstado() != EstadoSesion.EN_PROGRESO) return null;
            return sesion;
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }
}