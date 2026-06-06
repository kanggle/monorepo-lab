package com.example.auth.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that injects RFC 8594 {@code Deprecation} and RFC 9745 {@code Sunset}
 * headers for the legacy {@code POST /api/auth/login} endpoint.
 *
 * <p>The headers are injected unconditionally for every response on the deprecated path —
 * including error responses — so that API consumers always receive the deprecation signal
 * regardless of whether the request succeeds or fails.
 *
 * <p>Reason for a dedicated filter (instead of setting headers inside
 * {@link com.example.auth.presentation.LoginController}): Spring MVC exception handlers
 * (e.g. {@code GlobalExceptionHandler}) can reset / replace the response object, which
 * discards any headers that were set earlier in the controller method. A servlet filter
 * that wraps the downstream response chain avoids this problem by adding the headers
 * after the entire controller + exception-handler stack has completed.
 *
 * <p>TASK-BE-251 Phase 2c — {@code POST /api/auth/login} deprecated 2026-05-01,
 * removal target 2026-08-01.
 */
public class DeprecatedApiHeaderFilter extends OncePerRequestFilter {

    /** RFC 8594 Deprecation header. */
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String DEPRECATION_VALUE = "true";

    /** RFC 9745 Sunset header — ISO 8601 date of planned removal. */
    private static final String SUNSET_HEADER = "Sunset";
    private static final String SUNSET_VALUE = "Sun, 01 Aug 2026 00:00:00 GMT";

    private static final String DEPRECATED_PATH = "/api/auth/login";
    private static final String DEPRECATED_METHOD = "POST";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        // Add deprecation headers after the downstream chain completes so that they
        // survive exception-handler response replacement.
        if (DEPRECATED_METHOD.equalsIgnoreCase(request.getMethod())
                && DEPRECATED_PATH.equals(request.getRequestURI())) {
            if (!response.isCommitted()) {
                response.setHeader(DEPRECATION_HEADER, DEPRECATION_VALUE);
                response.setHeader(SUNSET_HEADER, SUNSET_VALUE);
            } else {
                response.addHeader(DEPRECATION_HEADER, DEPRECATION_VALUE);
                response.addHeader(SUNSET_HEADER, SUNSET_VALUE);
            }
        }
    }
}
