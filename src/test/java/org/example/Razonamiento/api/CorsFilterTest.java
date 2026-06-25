package org.example.Razonamiento.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.*;

/**
 * Unit tests for CorsFilter.
 * Requirements: 1.2, 1.3, 1.4, 1.5
 */
class CorsFilterTest {

    private CorsFilter filter;
    private HttpServletRequest  request;
    private HttpServletResponse response;
    private FilterChain         chain;

    @BeforeEach
    void setUp() {
        filter   = new CorsFilter();
        request  = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        chain    = Mockito.mock(FilterChain.class);
    }

    // -----------------------------------------------------------------------
    // Test 1 — normal GET: CORS headers set, chain continues
    // -----------------------------------------------------------------------

    @Test
    void getRequest_setCorsHeaders_andContinuesChain() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("GET");

        // Act
        filter.doFilter(request, response, chain);

        // Assert — all three required CORS headers must be present
        verify(response).setHeader(CorsFilter.ALLOW_ORIGIN,  CorsFilter.ORIGIN_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_METHODS, CorsFilter.METHODS_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_HEADERS, CorsFilter.HEADERS_VALUE);

        // Chain must be called exactly once for non-OPTIONS requests
        verify(chain, times(1)).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Test 2 — OPTIONS preflight: CORS headers set, 200 returned, chain NOT called
    // -----------------------------------------------------------------------

    @Test
    void optionsRequest_setCorsHeaders_returns200_andDoesNotContinueChain() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("OPTIONS");

        // Act
        filter.doFilter(request, response, chain);

        // Assert — all three required CORS headers must still be set
        verify(response).setHeader(CorsFilter.ALLOW_ORIGIN,  CorsFilter.ORIGIN_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_METHODS, CorsFilter.METHODS_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_HEADERS, CorsFilter.HEADERS_VALUE);

        // Must respond 200 for preflight
        verify(response).setStatus(HttpServletResponse.SC_OK);

        // Chain must NOT be invoked for OPTIONS
        verify(chain, never()).doFilter(any(), any());
    }

    // -----------------------------------------------------------------------
    // Test 3 — POST also gets headers and continues chain
    // -----------------------------------------------------------------------

    @Test
    void postRequest_setCorsHeaders_andContinuesChain() throws Exception {
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(CorsFilter.ALLOW_ORIGIN,  CorsFilter.ORIGIN_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_METHODS, CorsFilter.METHODS_VALUE);
        verify(response).setHeader(CorsFilter.ALLOW_HEADERS, CorsFilter.HEADERS_VALUE);

        verify(chain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }
}
