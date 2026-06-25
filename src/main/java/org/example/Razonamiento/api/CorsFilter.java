package org.example.Razonamiento.api;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Global CORS filter for all /api/* endpoints.
 * Adds the required headers and short-circuits OPTIONS preflight requests.
 */
@WebFilter("/api/*")
public class CorsFilter implements Filter {

    static final String ALLOW_ORIGIN  = "Access-Control-Allow-Origin";
    static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";

    static final String ORIGIN_VALUE  = "http://localhost:5173";
    static final String METHODS_VALUE = "GET, POST, OPTIONS";
    static final String HEADERS_VALUE = "Content-Type, Authorization";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Always add CORS headers
        resp.setHeader(ALLOW_ORIGIN,  ORIGIN_VALUE);
        resp.setHeader(ALLOW_METHODS, METHODS_VALUE);
        resp.setHeader(ALLOW_HEADERS, HEADERS_VALUE);

        // Short-circuit OPTIONS (preflight)
        if ("OPTIONS".equals(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // no-op
    }
}
