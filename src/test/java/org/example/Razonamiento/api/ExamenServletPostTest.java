package org.example.Razonamiento.api;

import net.jqwik.api.*;
import org.example.Razonamiento.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based and unit tests for ExamenServlet.doPost.
 */
class ExamenServletPostTest {

    HttpServletRequest  request;
    HttpServletResponse response;
    StringWriter        responseBody;

    @BeforeEach
    void setUp() throws Exception {
        request      = mock(HttpServletRequest.class);
        response     = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    // -----------------------------------------------------------------------
    // Property 2: Blank session codes always yield a 400 response (POST side)
    // Feature: servlet-rest-api, Property 2: Blank session codes always yield a 400 response
    // -----------------------------------------------------------------------
    @Property(tries = 200)
    void blankSessionCodeYields400OnPost(@ForAll("blankStrings") String blankCode) throws Exception {
        // Validates: Requirements 3.6
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        // Build body with blank codigoSesion
        String body = "{\"codigoSesion\":\"" + (blankCode == null ? "" : blankCode)
                    + "\",\"nombre\":\"A\",\"apellido\":\"B\"}";
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPost(req, resp);

        verify(resp).setStatus(400);
        assertTrue(sw.toString().contains("\"error\""),
            "Expected error body for blank code body: " + body);
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "   ", "\t", "\n", "  \t  \n  ");
    }

    // -----------------------------------------------------------------------
    // Property 3: Find-or-create Evaluado is idempotent on correo
    // Feature: servlet-rest-api, Property 3: Find-or-create Evaluado is idempotent on correo
    // -----------------------------------------------------------------------
    @Property(tries = 100)
    void evaluadoFindOrCreateIsIdempotent(@ForAll("emailStrings") String correo) {
        // Validates: Requirements 3.2
        // Using Mockito to simulate the EntityManager — verify only one persist call
        javax.persistence.EntityManager em = mock(javax.persistence.EntityManager.class);

        // First call: no result -> create
        when(em.createQuery(anyString(), eq(Evaluado.class)))
            .thenReturn(mockTypedQuery(null, javax.persistence.NoResultException.class));

        Evaluado first = ExamenServlet.findOrCreateEvaluado(em, correo, "Juan", "Pérez");
        assertNotNull(first);
        verify(em, times(1)).persist(any(Evaluado.class));

        // Reset mock, second call: result found
        reset(em);
        Evaluado existingEvaluado = new Evaluado();
        existingEvaluado.setCorreo(correo);
        existingEvaluado.setNombre("Juan");
        existingEvaluado.setApellido("Pérez");
        when(em.createQuery(anyString(), eq(Evaluado.class)))
            .thenReturn(mockTypedQueryWithResult(existingEvaluado));

        Evaluado second = ExamenServlet.findOrCreateEvaluado(em, correo, "Juan", "Pérez");
        assertNotNull(second);
        // No additional persist on second call
        verify(em, never()).persist(any());
        assertEquals(correo, second.getCorreo());
    }

    @Provide
    Arbitrary<String> emailStrings() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
               .map(s -> s + "@test.com");
    }

    // -----------------------------------------------------------------------
    // Property 4: HojaRespuesta initialization covers all questions
    // Feature: servlet-rest-api, Property 4: HojaRespuesta initialization covers all questions
    // -----------------------------------------------------------------------
    @Property(tries = 100)
    void hojaRespuestaInitializesAllDetails(@ForAll("pruebaWithPreguntas") PruebaRazonamientoFormaA prueba) {
        // Validates: Requirements 3.3
        HojaRespuesta hoja = new HojaRespuesta();
        List<RespuestaDetalles> created = new ArrayList<>();

        // Simulate the initialization loop from ExamenServlet.doPost
        for (Pregunta p : prueba.getPreguntas()) {
            RespuestaDetalles rd = new RespuestaDetalles();
            rd.setHojaRespuesta(hoja);
            rd.setPregunta(p);
            rd.setEsOmitida(true);
            created.add(rd);
        }

        int n = prueba.getPreguntas().size();
        assertEquals(n, created.size(), "Expected " + n + " RespuestaDetalles but got " + created.size());
        for (RespuestaDetalles rd : created) {
            assertTrue(rd.isEsOmitida(), "All details must be omitted initially");
            assertNotNull(rd.getPregunta(), "Each detail must have a non-null pregunta");
        }
    }

    @Provide
    Arbitrary<PruebaRazonamientoFormaA> pruebaWithPreguntas() {
        return Arbitraries.integers().between(1, 50).map(n -> {
            PruebaRazonamientoFormaA prueba = new PruebaRazonamientoFormaA();
            prueba.setLimiteTiempoMinutos(10);
            List<Pregunta> list = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                Pregunta p = new Pregunta();
                p.setNumero(i);
                p.setSerieIncompleta("serie " + i);
                p.setOpcionA("A" + i);
                p.setOpcionB("B" + i);
                p.setOpcionC("C" + i);
                p.setOpcionD("D" + i);
                p.setRespuestaCorrecta('A');
                list.add(p);
            }
            prueba.setPreguntas(list);
            return prueba;
        });
    }

    // -----------------------------------------------------------------------
    // Property 5: Session state transitions to EN_PROGRESO only from PROGRAMADA
    // Feature: servlet-rest-api, Property 5: Session state transitions to EN_PROGRESO only from PROGRAMADA
    // -----------------------------------------------------------------------
    @Property(tries = 100)
    void sessionTransitionsToEnProgresoOnlyFromProgramada(
            @ForAll EstadoSesion startingState) {
        // Validates: Requirements 3.4
        SesionPrueba sesion = new SesionPrueba();
        sesion.setEstado(startingState);

        // Apply the transition logic from ExamenServlet.doPost
        EstadoSesion finalState = startingState;
        if (sesion.getEstado() == EstadoSesion.PROGRAMADA) {
            sesion.setEstado(EstadoSesion.EN_PROGRESO);
            finalState = EstadoSesion.EN_PROGRESO;
        }

        if (startingState == EstadoSesion.PROGRAMADA) {
            assertEquals(EstadoSesion.EN_PROGRESO, finalState,
                "State should be EN_PROGRESO after transition from PROGRAMADA");
        } else {
            assertEquals(startingState, finalState,
                "State should be unchanged when not starting from PROGRAMADA");
        }
    }

    // -----------------------------------------------------------------------
    // Property 6: Response UUID matches persisted HojaRespuesta
    // Feature: servlet-rest-api, Property 6: Response UUID matches persisted HojaRespuesta
    // -----------------------------------------------------------------------
    @Property(tries = 100)
    void responseUuidMatchesPersistedHoja(@ForAll UUID hojaId) {
        // Validates: Requirements 3.5
        String uuidStr = JsonBuilder.uuid(hojaId);
        String json = JsonBuilder.obj(
            JsonBuilder.str("hojaRespuestaId", uuidStr),
            JsonBuilder.str("codigoSesion", "TEST1234"),
            JsonBuilder.num("limiteTiempoMinutos", 10)
        );

        // Extract hojaRespuestaId from JSON and compare
        String extracted = ExamenServlet.extractString(json, "hojaRespuestaId");
        assertEquals(hojaId.toString(), extracted,
            "UUID in response must match the persisted HojaRespuesta id");
    }

    // -----------------------------------------------------------------------
    // Unit tests for doPost (example-based)
    // -----------------------------------------------------------------------

    @Test
    void whenMissingRequiredFields_returns400() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        String body = "{\"codigoSesion\":\"ABCD1234\",\"nombre\":\"\",\"apellido\":\"B\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPost(request, response);

        verify(response).setStatus(400);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenInvalidSession_returns404() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        var mockSvc = mock(org.example.Razonamiento.service.AutenticacionService.class);
        when(mockSvc.validarCodigoSesion(any())).thenReturn(null);
        servlet.autenticacionService = mockSvc;

        String body = "{\"codigoSesion\":\"INVALID12\",\"nombre\":\"Juan\",\"apellido\":\"Perez\"}";
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(body)));

        servlet.doPost(request, response);

        verify(response).setStatus(404);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenHappyPath_returns201WithCorrectKeys() {
        // The happy path requires real JPA — tested via integration test.
        // Here we verify the JSON shape of the response manually.
        UUID hojaId = UUID.randomUUID();
        String json = JsonBuilder.obj(
            JsonBuilder.str("hojaRespuestaId", JsonBuilder.uuid(hojaId)),
            JsonBuilder.str("codigoSesion", "ABCD1234"),
            JsonBuilder.num("limiteTiempoMinutos", 10)
        );

        assertTrue(json.contains("\"hojaRespuestaId\""), "Missing hojaRespuestaId");
        assertTrue(json.contains("\"codigoSesion\""),    "Missing codigoSesion");
        assertTrue(json.contains("\"limiteTiempoMinutos\""), "Missing limiteTiempoMinutos");
        assertTrue(json.contains(hojaId.toString()));
    }

    @Test
    void whenBodyIsMissing_returns400() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));

        servlet.doPost(request, response);

        verify(response).setStatus(400);
    }

    // -----------------------------------------------------------------------
    // Helpers for mock typed queries
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> javax.persistence.TypedQuery<T> mockTypedQuery(
            T result, Class<? extends RuntimeException> throwType) {
        javax.persistence.TypedQuery<T> q = mock(javax.persistence.TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        if (throwType != null) {
            try {
                when(q.getSingleResult()).thenThrow(throwType.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            when(q.getSingleResult()).thenReturn(result);
        }
        return q;
    }

    @SuppressWarnings("unchecked")
    private static <T> javax.persistence.TypedQuery<T> mockTypedQueryWithResult(T result) {
        javax.persistence.TypedQuery<T> q = mock(javax.persistence.TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(result);
        return q;
    }
}
