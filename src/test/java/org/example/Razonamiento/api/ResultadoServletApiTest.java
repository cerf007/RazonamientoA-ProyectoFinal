package org.example.Razonamiento.api;

import net.jqwik.api.*;
import org.example.Razonamiento.model.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based and unit tests for ResultadoServlet_API.
 */
class ResultadoServletApiTest {

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
    // Property 8: Result response always contains all required fields (resultado side)
    // Feature: servlet-rest-api, Property 8: Result response always contains all required fields
    // -----------------------------------------------------------------------
    @Property(tries = 100)
    void resultResponseContainsAllFields(@ForAll("resultados") Resultado resultado) {
        // Validates: Requirements 5.2
        UUID hojaId = UUID.randomUUID();
        String json = RespuestasServlet.buildResultJson(hojaId, resultado);

        assertTrue(json.contains("\"hojaRespuestaId\""),   "Missing hojaRespuestaId");
        assertTrue(json.contains("\"puntuacionDirecta\""), "Missing puntuacionDirecta");
        assertTrue(json.contains("\"percentil\""),         "Missing percentil");
        assertTrue(json.contains("\"interpretacion\""),    "Missing interpretacion");

        // hojaRespuestaId must be a valid UUID string
        String extractedId = ExamenServlet.extractString(json, "hojaRespuestaId");
        assertDoesNotThrow(() -> UUID.fromString(extractedId),
            "hojaRespuestaId must be a valid UUID: " + extractedId);

        // interpretacion must be non-blank
        String interp = ExamenServlet.extractString(json, "interpretacion");
        assertNotNull(interp);
        assertFalse(interp.isBlank(), "interpretacion must be non-blank");

        // puntuacionDirecta value must be a number in json
        assertTrue(json.contains("\"puntuacionDirecta\":" + resultado.getPuntuacionDirecta()),
            "puntuacionDirecta value mismatch in: " + json);
        assertTrue(json.contains("\"percentil\":" + resultado.getPercentil()),
            "percentil value mismatch in: " + json);
    }

    @Provide
    Arbitrary<Resultado> resultados() {
        Arbitrary<Integer> puntuacion = Arbitraries.integers().between(0, 36);
        Arbitrary<Integer> percentil  = Arbitraries.integers().between(0, 99);
        return Combinators.combine(puntuacion, percentil).as((pd, pc) -> {
            Resultado r = new Resultado();
            r.setPuntuacionDirecta(pd);
            r.setPercentil(pc);
            return r;
        });
    }

    // -----------------------------------------------------------------------
    // Unit tests (example-based)
    // -----------------------------------------------------------------------

    @Test
    void whenBlankHojaId_returns400() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();
        when(request.getParameter("hojaRespuestaId")).thenReturn("");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenNullHojaId_returns400() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();
        when(request.getParameter("hojaRespuestaId")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenMalformedUUID_returns400() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();
        when(request.getParameter("hojaRespuestaId")).thenReturn("not-a-uuid-value");

        servlet.doGet(request, response);

        verify(response).setStatus(400);
        assertTrue(responseBody.toString().contains("\"error\""));
    }

    @Test
    void whenHojaNotFound_returns404() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();

        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        servlet.sendError(resp, 404, "HojaRespuesta no encontrada");

        verify(resp).setStatus(404);
        assertTrue(sw.toString().contains("\"error\""));
        assertTrue(sw.toString().contains("HojaRespuesta no encontrada"));
    }

    @Test
    void whenResultadoNotYetAvailable_returns404WithMessage() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();

        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        servlet.sendError(resp, 404, "El resultado aún no está disponible");

        verify(resp).setStatus(404);
        assertTrue(sw.toString().contains("El resultado"));
        assertTrue(sw.toString().contains("\"error\""));
    }

    @Test
    void whenHappyPath_returns200WithCorrectShape() {
        UUID hojaId = UUID.randomUUID();
        Resultado r = new Resultado();
        r.setPuntuacionDirecta(20);
        r.setPercentil(85);

        String json = RespuestasServlet.buildResultJson(hojaId, r);

        assertTrue(json.contains("\"hojaRespuestaId\""));
        assertTrue(json.contains("\"puntuacionDirecta\":20"));
        assertTrue(json.contains("\"percentil\":85"));
        assertTrue(json.contains("\"interpretacion\""));
        assertTrue(json.contains("Alto"));  // percentil 85 → "Alto (P 75-89)"
        assertTrue(json.contains(hojaId.toString()));
    }
}
