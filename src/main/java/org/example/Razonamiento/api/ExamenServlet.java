package org.example.Razonamiento.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.example.Razonamiento.model.EstadoSesion;
import org.example.Razonamiento.model.Evaluado;
import org.example.Razonamiento.model.HojaRespuesta;
import org.example.Razonamiento.model.Pregunta;
import org.example.Razonamiento.model.PruebaRazonamientoFormaA;
import org.example.Razonamiento.model.RespuestaDetalles;
import org.example.Razonamiento.model.SesionPrueba;
import org.example.Razonamiento.service.AutenticacionService;
import org.example.Razonamiento.util.JPAUTil;

/**
 * Handles GET /api/examen/preguntas and POST /api/examen/iniciar.
 *
 * Requirements: 2.x (GET) and 3.x (POST)
 */
@WebServlet(name = "ExamenServlet",
            urlPatterns = {"/api/examen/preguntas", "/api/examen/iniciar"})
public class ExamenServlet extends HttpServlet {

    AutenticacionService autenticacionService = new AutenticacionService();

    // -----------------------------------------------------------------------
    // GET /api/examen/preguntas
    // -----------------------------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");

            // 1. Validate codigoSesion parameter
            String codigoSesion = req.getParameter("codigoSesion");
            if (isBlank(codigoSesion)) {
                sendError(resp, 400, "El parámetro codigoSesion es obligatorio");
                return;
            }

            // 2. Validate session
            SesionPrueba sesion = autenticacionService.validarCodigoSesion(codigoSesion);
            if (sesion == null) {
                sendError(resp, 404, "Sesión no encontrada o no disponible");
                return;
            }

            // 3. Load prueba
            EntityManager em = JPAUTil.createEntityManager();
            try {
                List<PruebaRazonamientoFormaA> lista = em.createQuery(
                        "SELECT p FROM PruebaRazonamientoFormaA p",
                        PruebaRazonamientoFormaA.class)
                        .setMaxResults(1)
                        .getResultList();

                if (lista.isEmpty()) {
                    sendError(resp, 503, "La prueba no está configurada");
                    return;
                }

                PruebaRazonamientoFormaA prueba = lista.get(0);

                // 4. Serialize response — omit respuestaCorrecta
                String json = serializePreguntasResponse(prueba);
                resp.setStatus(200);
                resp.getWriter().write(json);

            } finally {
                em.close();
            }

        } catch (Throwable t) {
            sendError(resp, 500, "Error interno al obtener preguntas");
        }
    }

    /**
     * Serialises the GET /preguntas response.
     * Package-visible for testing.
     */
    static String serializePreguntasResponse(PruebaRazonamientoFormaA prueba) {
        List<String> items = new ArrayList<>();
        for (Pregunta p : prueba.getPreguntas()) {
            items.add(serializePregunta(p));
        }
        return JsonBuilder.obj(
            JsonBuilder.num("limiteTiempoMinutos", prueba.getLimiteTiempoMinutos()),
            JsonBuilder.array("preguntas", items.toArray(new String[0]))
        );
    }

    /**
     * Serialises a single Pregunta — intentionally omits respuestaCorrecta.
     * Package-visible for testing.
     */
    static String serializePregunta(Pregunta p) {
        return JsonBuilder.obj(
            JsonBuilder.num("numero", p.getNumero()),
            JsonBuilder.str("serieIncompleta", p.getSerieIncompleta()),
            JsonBuilder.str("opcionA", p.getOpcionA()),
            JsonBuilder.str("opcionB", p.getOpcionB()),
            JsonBuilder.str("opcionC", p.getOpcionC()),
            JsonBuilder.str("opcionD", p.getOpcionD())
        );
    }

    // -----------------------------------------------------------------------
    // POST /api/examen/iniciar
    // -----------------------------------------------------------------------
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");

            // 1. Read and parse JSON body
            String body = readBody(req);
            String codigoSesion = extractString(body, "codigoSesion");
            String nombre       = extractString(body, "nombre");
            String apellido     = extractString(body, "apellido");
            String correo       = extractString(body, "correo");

            // 2. Validate required fields
            if (isBlank(codigoSesion) || isBlank(nombre) || isBlank(apellido)) {
                sendError(resp, 400, "Campos obligatorios: codigoSesion, nombre, apellido");
                return;
            }

            // 3. Validate session
            SesionPrueba sesion = autenticacionService.validarCodigoSesion(codigoSesion);
            if (sesion == null) {
                sendError(resp, 404, "Sesión no encontrada o no disponible");
                return;
            }

            EntityManager em = JPAUTil.createEntityManager();
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();

                // 4. Load prueba
                List<PruebaRazonamientoFormaA> lista = em.createQuery(
                        "SELECT p FROM PruebaRazonamientoFormaA p",
                        PruebaRazonamientoFormaA.class)
                        .setMaxResults(1)
                        .getResultList();

                if (lista.isEmpty()) {
                    tx.rollback();
                    sendError(resp, 503, "La prueba no está configurada");
                    return;
                }

                PruebaRazonamientoFormaA prueba = lista.get(0);

                // 5. Re-attach SesionPrueba within this transaction
                SesionPrueba sesionManaged = em.find(SesionPrueba.class, sesion.getId());

                // 6. Find-or-create Evaluado
                Evaluado evaluado = findOrCreateEvaluado(em, correo, nombre, apellido);

                // 7. Create HojaRespuesta
                HojaRespuesta hoja = new HojaRespuesta();
                hoja.setEvaluado(evaluado);
                hoja.setSesionPrueba(sesionManaged);
                hoja.iniciarPrueba();
                em.persist(hoja);

                // 8. Create one RespuestaDetalles per Pregunta (all omitted initially)
                for (Pregunta p : prueba.getPreguntas()) {
                    RespuestaDetalles rd = new RespuestaDetalles();
                    rd.setHojaRespuesta(hoja);
                    rd.setPregunta(p);
                    rd.setEsOmitida(true);
                    hoja.getRespuestasDetalle().add(rd);
                    em.persist(rd);
                }

                // 9. Transition session state
                if (sesionManaged.getEstado() == EstadoSesion.PROGRAMADA) {
                    sesionManaged.setEstado(EstadoSesion.EN_PROGRESO);
                    em.merge(sesionManaged);
                }

                tx.commit();

                // 10. Return 201
                String json = JsonBuilder.obj(
                    JsonBuilder.str("hojaRespuestaId", JsonBuilder.uuid(hoja.getId())),
                    JsonBuilder.str("codigoSesion", sesionManaged.getCodigoSesion()),
                    JsonBuilder.num("limiteTiempoMinutos", prueba.getLimiteTiempoMinutos())
                );
                resp.setStatus(201);
                resp.getWriter().write(json);

            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                sendError(resp, 500, "Error interno al iniciar el examen");
            } finally {
                em.close();
            }

        } catch (Throwable t) {
            sendError(resp, 500, "Error interno al iniciar el examen");
        }
    }

    /**
     * Finds an existing Evaluado by correo or creates a new one.
     * Package-visible for testing.
     */
    static Evaluado findOrCreateEvaluado(EntityManager em, String correo,
                                          String nombre, String apellido) {
        if (correo != null && !correo.isBlank()) {
            try {
                return em.createQuery(
                        "SELECT e FROM Evaluado e WHERE e.correo = :c", Evaluado.class)
                        .setParameter("c", correo.trim())
                        .getSingleResult();
            } catch (NoResultException ignored) { }
        }
        Evaluado e = new Evaluado();
        e.setNombre(nombre.trim());
        e.setApellido(apellido.trim());
        e.setCorreo(correo != null && !correo.isBlank() ? correo.trim() : "sinCorreo@bfa.test");
        em.persist(e);
        return e;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Very lightweight JSON string field extractor — no external library.
     * Handles simple cases: "key":"value" where value contains no unescaped quotes.
     */
    static String extractString(String json, String key) {
        if (json == null || json.isBlank()) return null;
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        // Skip whitespace after colon
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            // String value
            int end = start + 1;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '\\') { end += 2; continue; }
                if (c == '"') break;
                end++;
            }
            return json.substring(start + 1, end);
        }
        // null literal
        if (json.startsWith("null", start)) return null;
        return null;
    }

    /**
     * Extracts a JSON number value as a String for the given key.
     * Returns null if not found.
     */
    static String extractNumber(String json, String key) {
        if (json == null || json.isBlank()) return null;
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '-' || Character.isDigit(first)) {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) {
                end++;
            }
            return json.substring(start, end);
        }
        return null;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
