package org.example.Razonamiento.api;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests that all error responses across the API layer carry the "error" key.
 *
 * Feature: servlet-rest-api, Property 11: All error responses carry the "error" key
 * Validates: Requirements 6.4
 */
class ErrorUniformityTest {

    // -----------------------------------------------------------------------
    // Property 11: All error responses carry the "error" key
    // Feature: servlet-rest-api, Property 11: All error responses carry the "error" key
    // -----------------------------------------------------------------------

    /**
     * ExamenServlet GET — error triggering inputs.
     */
    @ParameterizedTest
    @CsvSource({
        "''",      // blank codigoSesion
        "' '",     // whitespace
    })
    void examenServletGetErrorsCarryErrorKey(String codigoSesion) throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("codigoSesion")).thenReturn(codigoSesion.trim());

        servlet.doGet(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * ExamenServlet GET — null codigoSesion.
     */
    @Test
    void examenServletGetNullCodeCarriesErrorKey() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("codigoSesion")).thenReturn(null);

        servlet.doGet(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * ExamenServlet GET — null session (404).
     */
    @Test
    void examenServletGetNullSessionCarriesErrorKey() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        var mockSvc = mock(org.example.Razonamiento.service.AutenticacionService.class);
        when(mockSvc.validarCodigoSesion(any())).thenReturn(null);
        servlet.autenticacionService = mockSvc;

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("codigoSesion")).thenReturn("VALID123");

        servlet.doGet(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * ExamenServlet POST — missing required fields (400).
     */
    @Test
    void examenServletPostMissingFieldsCarriesErrorKey() throws Exception {
        ExamenServlet servlet = new ExamenServlet();
        servlet.autenticacionService = mock(
            org.example.Razonamiento.service.AutenticacionService.class);

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        servlet.doPost(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * RespuestasServlet — missing hojaRespuestaId (400).
     */
    @Test
    void respuestasServletMissingIdCarriesErrorKey() throws Exception {
        RespuestasServlet servlet = new RespuestasServlet();

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        servlet.doPost(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * RespuestasServlet — malformed UUID (400).
     */
    @Test
    void respuestasServletMalformedUUIDCarriesErrorKey() throws Exception {
        RespuestasServlet servlet = new RespuestasServlet();

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(
            "{\"hojaRespuestaId\":\"bad-uuid\"}")));

        servlet.doPost(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * ResultadoServlet_API — blank hojaRespuestaId (400).
     */
    @Test
    void resultadoServletBlankIdCarriesErrorKey() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("hojaRespuestaId")).thenReturn(null);

        servlet.doGet(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    /**
     * ResultadoServlet_API — malformed UUID (400).
     */
    @Test
    void resultadoServletMalformedUUIDCarriesErrorKey() throws Exception {
        ResultadoServlet_API servlet = new ResultadoServlet_API();

        HttpServletRequest  req  = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        when(req.getParameter("hojaRespuestaId")).thenReturn("not-a-uuid");

        servlet.doGet(req, resp);

        assertErrorResponseShape(resp, sw.toString());
    }

    // -----------------------------------------------------------------------
    // Property 11 (jqwik): arbitrary status codes and messages produce valid error JSON
    // Feature: servlet-rest-api, Property 11: All error responses carry the "error" key
    // -----------------------------------------------------------------------
    @Property(tries = 200)
    void allErrorMessagesProduceValidErrorJson(
            @ForAll @net.jqwik.api.constraints.IntRange(min = 400, max = 599) int status,
            @ForAll @net.jqwik.api.constraints.StringLength(min = 1, max = 80) String message) throws Exception {
        // Validates: Requirements 6.4
        ExamenServlet servlet = new ExamenServlet();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        servlet.sendError(resp, status, message);

        String body = sw.toString();
        // Must contain "error" key
        assertTrue(body.contains("\"error\""),
            "Missing 'error' key in: " + body);
        // Must contain the message (after escaping)
        // Content-Type must be set
        verify(resp).setContentType("application/json; charset=UTF-8");
        verify(resp).setStatus(status);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------
    private void assertErrorResponseShape(HttpServletResponse resp, String body) {
        // Content-Type must be application/json
        verify(resp).setContentType("application/json; charset=UTF-8");
        // Body must contain "error" key with a non-blank string value
        assertTrue(body.contains("\"error\""),
            "Response body must contain 'error' key but was: " + body);
        // Must not be empty error value
        assertFalse(body.equals("{\"error\":\"\"}"),
            "Error message must not be empty");
    }
}
