package org.example.Razonamiento.servelet;

import org.example.Razonamiento.calculator.TemporizadorTest;
import org.example.Razonamiento.model.HojaRespuesta;
import org.example.Razonamiento.model.Pregunta;
import org.example.Razonamiento.model.RespuestaDetalles;
import org.example.Razonamiento.model.Resultado;
import org.example.Razonamiento.service.CorreccionAutomaticaService;
import org.example.Razonamiento.util.JPAUTil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@WebServlet(name = "PruebaServlet", urlPatterns = {"/prueba"})
public class PruebaServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(PruebaServlet.class);
    private final TemporizadorTest temporizador = new TemporizadorTest();
    private final CorreccionAutomaticaService correccionService = new CorreccionAutomaticaService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("hojaRespuestaId") == null) {
            resp.sendRedirect(req.getContextPath() + "/portal");
            return;
        }

        EntityManager em = JPAUTil.createEntityManager();
        try {
            HojaRespuesta hoja = em.find(HojaRespuesta.class,
                    UUID.fromString((String) session.getAttribute("hojaRespuestaId")));

            if (hoja == null) { session.invalidate(); resp.sendRedirect(req.getContextPath() + "/portal"); return; }

            Integer limite = (Integer) session.getAttribute("limiteTiempo");
            int min = (limite != null) ? limite : 10;

            long segundos = temporizador.calcularSegundosRestantes(hoja, min);
            req.setAttribute("segundosRestantes", segundos);

            List<Pregunta> preguntas = em.createQuery(
                    "SELECT p FROM Pregunta p ORDER BY p.numero", Pregunta.class).getResultList();
            req.setAttribute("preguntas", preguntas);
            req.setAttribute("hojaId", hoja.getId().toString());
            req.getRequestDispatcher("/WEB-INF/portal/prueba.jsp").forward(req, resp);
        } finally {
            em.close();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("hojaRespuestaId") == null) {
            resp.sendRedirect(req.getContextPath() + "/portal");
            return;
        }

        UUID hojaId = UUID.fromString((String) session.getAttribute("hojaRespuestaId"));
        EntityManager em = JPAUTil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            HojaRespuesta hoja = em.find(HojaRespuesta.class, hojaId);
            if (hoja == null) { tx.rollback(); resp.sendRedirect(req.getContextPath() + "/portal"); return; }

            if (hoja.getHoraFin() == null) hoja.finalizarPrueba();

            Map<String, String[]> params = req.getParameterMap();
            for (RespuestaDetalles detalle : hoja.getRespuestasDetalle()) {
                String key = "respuesta_" + detalle.getPregunta().getId().toString();
                String[] vals = params.get(key);
                if (vals != null && vals.length > 0 && !vals[0].isBlank()) {
                    char op = vals[0].toUpperCase().charAt(0);
                    detalle.registrarRespuesta(
                            (op=='A'||op=='B'||op=='C'||op=='D') ? String.valueOf(op) : null);
                } else {
                    detalle.registrarRespuesta(null);
                }
                em.merge(detalle);
            }
            tx.commit();

            Resultado resultado = correccionService.procesarHojaRespuesta(hoja);
            if (resultado != null) {
                session.setAttribute("resultadoPD",        resultado.getPuntuacionDirecta());
                session.setAttribute("resultadoPercentil", resultado.getPercentil());
                session.setAttribute("resultadoInterp",    resultado.getInterpretacion());
                session.removeAttribute("hojaRespuestaId");
            }
            resp.sendRedirect(req.getContextPath() + "/resultado");
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            req.setAttribute("error", "Error al guardar respuestas.");
            req.getRequestDispatcher("/WEB-INF/portal/prueba.jsp").forward(req, resp);
        } finally {
            em.close();
        }
    }
}
