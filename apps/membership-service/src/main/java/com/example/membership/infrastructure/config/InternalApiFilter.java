package com.example.membership.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the X-Internal-Token header on {@code /internal/**} paths.
 *
 * <p>Fail-closed semantics: when no token is configured AND bypass is disabled,
 * every {@code /internal/**} request is rejected with 401. The bypass flag is
 * intended solely for {@code @WebMvcTest} slice tests and local development —
 * production deployments must supply {@code INTERNAL_API_TOKEN}.
 */
@Slf4j
public class InternalApiFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final String expectedToken;
    private final boolean bypassWhenUnconfigured;

    public InternalApiFilter(String expectedToken, boolean bypassWhenUnconfigured) {
        this.expectedToken = expectedToken;
        this.bypassWhenUnconfigured = bypassWhenUnconfigured;
        if ((expectedToken == null || expectedToken.isBlank()) && !bypassWhenUnconfigured) {
            log.warn("INTERNAL_API_TOKEN is not configured — /internal/** requests will be rejected with 401 (fail-closed).");
        } else if ((expectedToken == null || expectedToken.isBlank())) {
            log.warn("INTERNAL_API_TOKEN is not configured — bypass is enabled (dev/test only). DO NOT USE IN PRODUCTION.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            boolean tokenConfigured = expectedToken != null && !expectedToken.isBlank();
            if (!tokenConfigured) {
                if (!bypassWhenUnconfigured) {
                    writeUnauthorized(response,
                            "Internal API token is not configured on this server");
                    return;
                }
                // dev/test bypass: fall through without header validation
            } else {
                String token = request.getHeader(INTERNAL_TOKEN_HEADER);
                if (!expectedToken.equals(token)) {
                    writeUnauthorized(response, "Missing or invalid internal token");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
