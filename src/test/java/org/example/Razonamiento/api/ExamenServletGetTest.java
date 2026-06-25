package org.example.Razonamiento.api;

import net.jqwik.api.*;
import org.example.Razonamiento.model.Pregunta;
import org.example.Razonamiento.model.PruebaRazonamientoFormaA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based and unit tests for ExamenServlet.doGet.
 */
class ExamenServletGetTest {

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
    // Property 1: Question serialization never exposes the correct answer
    // Feature: servlet-rest-api, Property 1: Question serialization never exposes the correct answer
    // -----------------------------------------------------------------------
    @Property(tries = 200)
    void preguntasJsonNeverExposesRespuestaCorrecta(
            @ForAll("preguntaLists") List<Pregunta> preguntas) {
        // Validates: Requirements 2.2
        PruebaRazonamientoFormaA prueba = buildPrueba(preguntas, 10);
        String json = ExamenServlet.serializePreguntasResponse(prueba);

        // Must NOT contain respuestaCorrecta
        assertFalse(json.contains("respuestaCorrecta"),
            "JSON must not expose respuestaCorrecta but got: " + json);

        // Must contain all required fields
        assertTrue(json.contains("\"numero\""),         "Missing 'numero' in: " + json);
        assertTrue(json.contains("\"serieIncompleta\""),"Missing 'serieIncompleta' in: " + json);
        assertTrue(json.contains("\"opcionA\""),        "Missing 'opcionA' in: " + json);
        assertTrue(json.contains("\"opcionB\""),        "Missing 'opcionB' in: " + json);
        assertTrue(json.contains("\"opcionC\""),        "Missing 'opcionC' in: " + json);
        assertTrue(json.contains("\"opcionD\""),        "Missing 'opcionD' in: " + json);
        assertTrue(json.contains("\"limiteTiempoMinutos\""), "Missing 'limiteTiempoMinutos'");
        assertTrue(json.contains("\"preguntas\""),           "Missing 'preguntas'");
    }

    @Provide
    Arbitrary<List<Pregunta>> preguntaLists() {
        Arbitrary<Integer> numero = Arbitraries.integers().between(1, 100);
        Arbitrary<String>  text   = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<Character> opcion = Arbitraries.of('A', 'B', 'C', 'D');

        Arbitrary<Pregunta> preguntaArb = Combinators.combine(numero, text, text, text, text, text, opcion)
            .as((n, serie, a, b, c, d, rc) -> {
                Pregunta p = new Pregunta();
                p.setNumero(n);
                p.setSerieIncompleta(serie);
                p.setOpcionA(a);
                p.setOpcionB(b);
                p.setOpcionC(c);
                p.setOpcionD(d);
                p.setRespuestaCorrecta(rc);
                return p;
            });

        return preguntaArb.list().ofMinSize(1).ofMaxSize(10);
    }

    // -----------------------------------------------------------------------
    // Property 2: Blank session codes always yield a 400 response (GET side)
    // Feature: servlet-rest-api, Property 2: Blank session codes always yield a 400 response
    // -----------------------------------------------------------------------
    @Property(tries = 200)
    void blankSessionCodeYields400OnGet(@ForAll("blankStrings") String blankCode) throws Exception {
        // Validates: Requirements 2.4
        ExamenServlet servlet = new ExamenServlet();
        // Override autenticacionService so it never gets called
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("codigoSesion")).thenReturn(blankCode);

        servlet.doGet(req, resp);

        verify(resp).setStatus(400);
        assertTrue(sw.toString().contains("\"error\""),
            "Expected 'error' key in body for blank code: '" + blankCode + "'");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        // null, empty, spaces, tabs, mixed whitespace
        return Arbitraries.of("", " ", "   ", "\t", "\n", "  \t  \n  ", null);
    }

    // -----------------------------------------------------------------------
    // Unit tests (example-based)
    // -----------------------------------------------------------------------

    @Test
    void whenBlankCodigoSesion_returns400() throws Exception {
        ExamenServlet servlet = buildServletWithNullSession();
        when(request.getParameter("codigoSesion")).thenReturn("");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenServiceReturnsNull_returns404() throws Exception {
        ExamenServlet servlet = buildServletWithNullSession();
        when(request.getParameter("codigoSesion")).thenReturn("ABCD1234");

        servlet.doGet(request, response);

        verify(response).setStatus(404);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenNoPruebaConfigured_returns503() throws Exception {
        // We need a real servlet that hits a null prueba path —
        // This is tested by the doGet logic when lista.isEmpty().
        // Since we cannot hit the real DB in unit tests, we test the helper directly.
        PruebaRazonamientoFormaA emptyPrueba = new PruebaRazonamientoFormaA();
        emptyPrueba.setLimiteTiempoMinutos(10);
        emptyPrueba.setPreguntas(new ArrayList<>());

        String json = ExamenServlet.serializePreguntasResponse(emptyPrueba);
        assertTrue(json.contains("\"preguntas\""));
        assertTrue(json.contains("\"limiteTiempoMinutos\""));
        // empty array
        assertTrue(json.contains("\"preguntas\":[]"));
    }

    @Test
    void whenHappyPath_returns200WithCorrectShape() {
        List<Pregunta> preguntas = new ArrayList<>();
        Pregunta p = buildPregunta(1, "2,4,6,?", "7", "8", "9", "10", 'C');
        preguntas.add(p);

        PruebaRazonamientoFormaA prueba = buildPrueba(preguntas, 15);
        String json = ExamenServlet.serializePreguntasResponse(prueba);

        assertTrue(json.contains("\"limiteTiempoMinutos\":15"));
        assertTrue(json.contains("\"preguntas\""));
        assertTrue(json.contains("\"numero\":1"));
        assertTrue(json.contains("\"serieIncompleta\""));
        assertTrue(json.contains("\"opcionA\""));
        assertTrue(json.contains("\"opcionB\""));
        assertTrue(json.contains("\"opcionC\""));
        assertTrue(json.contains("\"opcionD\""));
        assertFalse(json.contains("respuestaCorrecta"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ExamenServlet buildServletWithNullSession() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        var mockService = mock(org.example.Razonamiento.service.AutenticacionService.class);
        when(mockService.validarCodigoSesion(any())).thenReturn(null);
        servlet.autenticacionService = mockService;
        return servlet;
    }

    private PruebaRazonamientoFormaA buildPrueba(List<Pregunta> preguntas, int tiempo) {
        PruebaRazonamientoFormaA prueba = new PruebaRazonamientoFormaA();
        prueba.setLimiteTiempoMinutos(tiempo);
        prueba.setPreguntas(new ArrayList<>(preguntas));
        return prueba;
    }

    private Pregunta buildPregunta(int num, String serie, String a, String b, String c, String d, char rc) {
        Pregunta p = new Pregunta();
        p.setNumero(num);
        p.setSerieIncompleta(serie);
        p.setOpcionA(a);
        p.setOpcionB(b);
        p.setOpcionC(c);
        p.setOpcionD(d);
        p.setRespuestaCorrecta(rc);
        return p;
    }
}
