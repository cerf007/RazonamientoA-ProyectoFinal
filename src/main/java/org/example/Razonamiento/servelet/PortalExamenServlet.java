package org.example.Razonamiento.servelet;

import org.example.Razonamiento.model.*;
import org.example.Razonamiento.service.AutenticacionService;
import org.example.Razonamiento.util.JPAUTil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@WebServlet(name = "PortalExamenServlet", urlPatterns = {"/portal"})
public class PortalExamenServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(PortalExamenServlet.class);
    private final AutenticacionService autenticacionService = new AutenticacionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String codigoSesion     = req.getParameter("codigoSesion");
        String nombre           = req.getParameter("nombre");
        String apellido         = req.getParameter("apellido");
        String correo           = req.getParameter("correo");
        String telefono         = req.getParameter("telefono");
        String sexo             = req.getParameter("sexo");
        String fechaNacimiento  = req.getParameter("fechaNacimiento");

        if (isBlank(codigoSesion) || isBlank(nombre) || isBlank(apellido) || isBlank(correo)) {
            req.setAttribute("error", "Código de sesión, nombre, apellido y correo son obligatorios.");
            preservarFormulario(req, nombre, apellido, correo, telefono, sexo, fechaNacimiento, codigoSesion);
            req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
            return;
        }

        SesionPrueba sesion = autenticacionService.validarCodigoSesion(codigoSesion);
        if (sesion == null) {
            req.setAttribute("error", "Código de sesión inválido o no disponible.");
            preservarFormulario(req, nombre, apellido, correo, telefono, sexo, fechaNacimiento, codigoSesion);
            req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
            return;
        }

        LocalDate fechaNac;
        try {
            fechaNac = parseFechaNacimiento(fechaNacimiento);
        } catch (DateTimeParseException e) {
            req.setAttribute("error", "La fecha de nacimiento no es válida.");
            preservarFormulario(req, nombre, apellido, correo, telefono, sexo, fechaNacimiento, codigoSesion);
            req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
            return;
        }

        EntityManager em = JPAUTil.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            SesionPrueba sesionManaged = em.find(SesionPrueba.class, sesion.getId());
            Evaluado evaluado = buscarOCrearEvaluado(em, nombre, apellido, correo, telefono, sexo, fechaNac);

            HojaRespuesta hoja = new HojaRespuesta();
            hoja.setEvaluado(evaluado);
            hoja.setSesionPrueba(sesionManaged);
            hoja.iniciarPrueba();
            em.persist(hoja);

            PruebaRazonamientoFormaA prueba = obtenerPrueba(em);
            if (prueba == null) {
                tx.rollback();
                req.setAttribute("error", "La prueba no está configurada. Contacte al administrador.");
                preservarFormulario(req, nombre, apellido, correo, telefono, sexo, fechaNacimiento, codigoSesion);
                req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
                return;
            }

            for (Pregunta p : prueba.getPreguntas()) {
                RespuestaDetalles d = new RespuestaDetalles();
                d.setHojaRespuesta(hoja);
                d.setPregunta(p);
                d.setEsOmitida(true);
                hoja.getRespuestasDetalle().add(d);
                em.persist(d);
            }

            if (sesionManaged.getEstado() == EstadoSesion.PROGRAMADA) {
                sesionManaged.setEstado(EstadoSesion.EN_PROGRESO);
                em.merge(sesionManaged);
            }

            tx.commit();

            HttpSession s = req.getSession();
            s.setAttribute("hojaRespuestaId", hoja.getId().toString());
            s.setAttribute("evaluadoNombre",  nombre + " " + apellido);
            s.setAttribute("limiteTiempo",    prueba.getLimiteTiempoMinutos());
            s.setAttribute("preguntas",       prueba.getPreguntas());

            resp.sendRedirect(req.getContextPath() + "/prueba");
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            log.error("Error al registrar evaluado en portal", e);
            req.setAttribute("error", "Error interno. Intente nuevamente.");
            preservarFormulario(req, nombre, apellido, correo, telefono, sexo, fechaNacimiento, codigoSesion);
            req.getRequestDispatcher("/WEB-INF/portal/index.jsp").forward(req, resp);
        } finally {
            em.close();
        }
    }

    private Evaluado buscarOCrearEvaluado(EntityManager em, String nombre, String apellido,
                                          String correo, String telefono, String sexo,
                                          LocalDate fechaNacimiento) {
        Evaluado e = null;
        if (correo != null && !correo.isBlank()) {
            try {
                e = em.createQuery("SELECT ev FROM Evaluado ev WHERE ev.correo = :c", Evaluado.class)
                        .setParameter("c", correo.trim()).getSingleResult();
            } catch (NoResultException ignored) {}
        }
        if (e == null) {
            e = new Evaluado();
            e.setNombre(nombre.trim());
            e.setApellido(apellido.trim());
            e.setCorreo(correo.trim());
            em.persist(e);
        } else {
            e.setNombre(nombre.trim());
            e.setApellido(apellido.trim());
        }
        e.setTelefono(telefono != null ? telefono.trim() : "");
        e.setSexo(normalizarSexo(sexo));
        e.setFechaNacimiento(fechaNacimiento);
        return e;
    }

    private void preservarFormulario(HttpServletRequest req, String nombre, String apellido,
                                     String correo, String telefono, String sexo,
                                     String fechaNacimiento, String codigoSesion) {
        req.setAttribute("nombre", nombre);
        req.setAttribute("apellido", apellido);
        req.setAttribute("correo", correo);
        req.setAttribute("telefono", telefono);
        req.setAttribute("sexo", sexo);
        req.setAttribute("fechaNacimiento", fechaNacimiento);
        req.setAttribute("codigoSesion", codigoSesion);
    }

    private LocalDate parseFechaNacimiento(String fecha) {
        if (fecha == null || fecha.isBlank()) return null;
        return LocalDate.parse(fecha.trim());
    }

    private String normalizarSexo(String sexo) {
        if (sexo == null || sexo.isBlank()) return null;
        return sexo.trim().toUpperCase();
    }

    private PruebaRazonamientoFormaA obtenerPrueba(EntityManager em) {
        List<PruebaRazonamientoFormaA> lista = em.createQuery(
                        "SELECT p FROM PruebaRazonamientoFormaA p", PruebaRazonamientoFormaA.class)
                .setMaxResults(1).getResultList();
        return lista.isEmpty() ? null : lista.get(0);
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
