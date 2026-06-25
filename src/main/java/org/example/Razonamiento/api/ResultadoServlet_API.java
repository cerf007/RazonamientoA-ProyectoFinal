package org.example.Razonamiento.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.example.Razonamiento.model.HojaRespuesta;
import org.example.Razonamiento.model.Resultado;
import org.example.Razonamiento.util.JPAUTil;

/**
 * Handles GET /api/examen/resultado.
 *
 * Requirements: 5.x
 */
@WebServlet(name = "ResultadoServlet_API",
            urlPatterns = {"/api/examen/resultado"})
public class ResultadoServlet_API extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");

            // 1. Validate hojaRespuestaId parameter
            String hojaRespuestaIdStr = req.getParameter("hojaRespuestaId");
            if (ExamenServlet.isBlank(hojaRespuestaIdStr)) {
                sendError(resp, 400, "El parámetro hojaRespuestaId es obligatorio");
                return;
            }

            // 2. Parse UUID
            UUID hojaId;
            try {
                hojaId = UUID.fromString(hojaRespuestaIdStr.trim());
            } catch (IllegalArgumentException e) {
                sendError(resp, 400, "hojaRespuestaId con formato inválido");
                return;
            }

            EntityManager em = JPAUTil.createEntityManager();
            try {
                // 3. Find HojaRespuesta
                HojaRespuesta hoja = em.find(HojaRespuesta.class, hojaId);
                if (hoja == null) {
                    sendError(resp, 404, "HojaRespuesta no encontrada");
                    return;
                }

                // 4. Check Resultado
                Resultado resultado = hoja.getResultado();
                if (resultado == null) {
                    sendError(resp, 404, "El resultado aún no está disponible");
                    return;
                }

                // 5. Return 200 with result
                String json = RespuestasServlet.buildResultJson(hojaId, resultado);
                resp.setStatus(200);
                resp.getWriter().write(json);

            } finally {
                em.close();
            }

        } catch (Throwable t) {
            sendError(resp, 500, "Error interno al consultar resultado");
        }
    }

    void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(status);
        PrintWriter writer = resp.getWriter();
        writer.write(JsonBuilder.obj(JsonBuilder.str("error", message)));
    }
}
