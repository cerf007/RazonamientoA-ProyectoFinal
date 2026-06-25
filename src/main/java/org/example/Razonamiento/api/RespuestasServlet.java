package org.example.Razonamiento.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.example.Razonamiento.model.HojaRespuesta;
import org.example.Razonamiento.model.RespuestaDetalles;
import org.example.Razonamiento.model.Resultado;
import org.example.Razonamiento.service.CorreccionAutomaticaService;
import org.example.Razonamiento.util.JPAUTil;

/**
 * Handles POST /api/examen/guardar-respuestas.
 *
 * Requirements: 4.x
 */
@WebServlet(name = "RespuestasServlet",
            urlPatterns = {"/api/examen/guardar-respuestas"})
public class RespuestasServlet extends HttpServlet {

    CorreccionAutomaticaService correccionService = new CorreccionAutomaticaService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");

            // 1. Parse body
            String body = readBody(req);

            // 2. Validate hojaRespuestaId
            String hojaRespuestaIdStr = ExamenServlet.extractString(body, "hojaRespuestaId");
            if (ExamenServlet.isBlank(hojaRespuestaIdStr)) {
                sendError(resp, 400, "El campo hojaRespuestaId es obligatorio");
                return;
            }

            // 3. Parse UUID
            UUID hojaId;
            try {
                hojaId = UUID.fromString(hojaRespuestaIdStr);
            } catch (IllegalArgumentException e) {
                sendError(resp, 400, "hojaRespuestaId con formato inválido");
                return;
            }

            EntityManager em = JPAUTil.createEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();

                // 4. Find HojaRespuesta
                HojaRespuesta hoja = em.find(HojaRespuesta.class, hojaId);
                if (hoja == null) {
                    tx.rollback();
                    sendError(resp, 404, "HojaRespuesta no encontrada");
                    return;
                }

                // 5. Process detalles array
                applyDetalles(body, hoja, em);

                // 6. Set horaFin if provided
                String horaFinStr = ExamenServlet.extractString(body, "horaFin");
                if (!ExamenServlet.isBlank(horaFinStr)) {
                    try {
                        hoja.setHoraFin(LocalDateTime.parse(horaFinStr));
                        em.merge(hoja);
                    } catch (Exception ignored) { /* ignore malformed date */ }
                }

                tx.commit();

                // 7. Grade
                Resultado resultado = correccionService.procesarHojaRespuesta(hoja);
                if (resultado == null) {
                    sendError(resp, 500, "Error al calcular el resultado");
                    return;
                }

                // 8. Return 200 with result
                String json = buildResultJson(hojaId, resultado);
                resp.setStatus(200);
                resp.getWriter().write(json);

            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                sendError(resp, 500, "Error interno al guardar respuestas");
            } finally {
                em.close();
            }

        } catch (Throwable t) {
            sendError(resp, 500, "Error interno al guardar respuestas");
        }
    }

    /**
     * Iterates the detalles array in the JSON body and updates each RespuestaDetalles.
     * Handles manual JSON parsing without external libraries.
     */
    private void applyDetalles(String body, HojaRespuesta hoja, EntityManager em) {
        // Find the "detalles" array
        int arrStart = body.indexOf("\"detalles\"");
        if (arrStart < 0) return;
        int bracketOpen = body.indexOf('[', arrStart);
        if (bracketOpen < 0) return;

        // Parse each object { ... } in the array
        int i = bracketOpen + 1;
        while (i < body.length()) {
            // Skip whitespace
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length() || body.charAt(i) == ']') break;
            if (body.charAt(i) != '{') { i++; continue; }

            // Find matching }
            int objEnd = findMatchingBrace(body, i);
            if (objEnd < 0) break;
            String obj = body.substring(i, objEnd + 1);
            i = objEnd + 1;

            // Extract fields
            String numStr    = ExamenServlet.extractNumber(obj, "numeroPregunta");
            String opcionStr = ExamenServlet.extractString(obj, "opcionSeleccionada");
            String omitStr   = extractBoolOrString(obj, "esOmitida");

            if (numStr == null) continue;
            int numeroPregunta;
            try { numeroPregunta = Integer.parseInt(numStr.trim()); }
            catch (NumberFormatException e) { continue; }

            boolean esOmitida = "true".equalsIgnoreCase(omitStr);
            char opcion = (opcionStr != null && !opcionStr.isEmpty()) ? opcionStr.charAt(0) : '\0';

            // Find matching RespuestaDetalles
            for (RespuestaDetalles rd : hoja.getRespuestasDetalle()) {
                if (rd.getPregunta() != null && rd.getPregunta().getNumero() == numeroPregunta) {
                    if (esOmitida) {
                        rd.setEsOmitida(true);
                    } else {
                        rd.registrarRespuesta(opcion);
                    }
                    em.merge(rd);
                    break;
                }
            }
        }
    }

    /** Finds the index of the closing brace } matching the opening brace at start. */
    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (!inStr) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extracts a boolean value (true/false) or a quoted string for the given key.
     * Used for the esOmitida field which is a JSON boolean.
     */
    private String extractBoolOrString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        // Boolean
        if (json.startsWith("true", start)) return "true";
        if (json.startsWith("false", start)) return "false";
        // Quoted string fallback
        return ExamenServlet.extractString(json, key);
    }

    /**
     * Builds the result JSON response.
     * Package-visible for testing.
     */
    static String buildResultJson(UUID hojaRespuestaId, Resultado resultado) {
        return JsonBuilder.obj(
            JsonBuilder.str("hojaRespuestaId", JsonBuilder.uuid(hojaRespuestaId)),
            JsonBuilder.num("puntuacionDirecta", resultado.getPuntuacionDirecta()),
            JsonBuilder.num("percentil", resultado.getPercentil()),
            JsonBuilder.str("interpretacion", resultado.getInterpretacion())
        );
    }

    void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(status);
        PrintWriter writer = resp.getWriter();
        writer.write(JsonBuilder.obj(JsonBuilder.str("error", message)));
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            if (reader == null) return "";
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
