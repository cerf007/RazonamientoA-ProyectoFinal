package org.example.Razonamiento.service;

import org.example.Razonamiento.calculator.CalculadoraPuntaje;
import org.example.Razonamiento.util.JPAUTil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.Razonamiento.model.*;

import javax.persistence.*;
import java.util.List;

public class CorreccionAutomaticaService {

    private static final Logger log = LoggerFactory.getLogger(CorreccionAutomaticaService.class);
    private final CalculadoraPuntaje calculadora = new CalculadoraPuntaje();

    public Resultado procesarHojaRespuesta(HojaRespuesta hoja) {
        if (hoja == null) throw new IllegalArgumentException("La hoja no puede ser nula");

        EntityManager em = JPAUTil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            HojaRespuesta hojaManaged = em.find(HojaRespuesta.class, hoja.getId());
            if (hojaManaged == null) return null;

            int pd = calculadora(hojaManaged.getRespuestasDetalle());

            List<BaremoNacional> baremo = em.createQuery(
                    "SELECT b FROM BaremoNacional b ORDER BY b.puntuacionDirecta",
                    BaremoNacional.class).getResultList();

            int percentil = baremo.isEmpty()
                    ? calculadora.obtenerPercentilEstatico(pd)
                    : calculadora.obtenerPercentil(pd, baremo);

            Resultado resultado = new Resultado();
            resultado.setPuntuacionDirecta(pd);
            resultado.setPercentil(percentil);
            resultado.setHojaRespuesta(hojaManaged);
            hojaManaged.setResultado(resultado);
            em.persist(resultado);

            if (hojaManaged.getHoraFin() == null) hojaManaged.finalizarPrueba();

            SesionPrueba sesion = hojaManaged.getSesionPrueba();
            if (sesion != null) {
                sesion.setEstado(EstadoSesion.CORREGIDA);
                em.merge(sesion);
            }

            tx.commit();
            return resultado;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            log.error("Error al procesar hoja", e);
            return null;
        } finally {
            em.close();
        }
    }

    private int calculadora(List<RespuestaDetalles> respuestasDetalle) {
        return 0;
    }
}
