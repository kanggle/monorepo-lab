package com.example.security.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Filter that enforces authentication on /internal/** endpoints.
 *
 * <p><b>TASK-BE-319a (ADR-005 단계 4a — JWT-only):</b> a request is accepted only when it carries a
 * valid GAP {@code client_credentials} JWT ({@code Authorization: Bearer}). The legacy static
 * {@code X-Internal-Token} path (dual-allow, TASK-BE-317) has been removed now that every caller —
 * security's sole internal caller is admin-service — authenticates with a Bearer JWT (TASK-BE-318b).
 * The JWT is verified with a {@link org.springframework.security.oauth2.jwt.JwtDecoder} backed by
 * GAP's JWKS (signature + issuer). security-service has no Spring Security web filter chain, so the
 * JWT verification is performed directly here rather than via {@code oauth2ResourceServer} (see
 * {@link InternalJwtDecoderConfig}). When the JWT is absent or invalid the request is rejected with
 * 403 PERMISSION_DENIED in the standard error format (fail-closed, contract preserved).
 *
 * <p>The 'test' and 'standalone' profiles bypass the check entirely (every internal request passes).
 * This is a profile-gated dev/test convenience, not a shared secret — production profiles always
 * require a valid JWT.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalAuthFilter implements Filter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> BYPASS_PROFILES = Set.of("test", "standalone");

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    public InternalAuthFilter(
            ObjectMapper objectMapper,
            Environment environment,
            org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only apply to /internal/** paths
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // Dev/test bypass: test/standalone profiles accept every internal request.
        if (isBypassProfile()) {
            chain.doFilter(request, response);
            return;
        }

        // TASK-BE-319a: the only accepted credential is a valid GAP client_credentials JWT.
        if (bearerJwtValid(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Unauthorized access attempt to internal endpoint: path={}, remoteAddr={}",
                path, httpRequest.getRemoteAddr());
        writePermissionDenied((HttpServletResponse) response);
    }

    private boolean bearerJwtValid(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return false;
        }
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (org.springframework.security.oauth2.jwt.JwtException e) {
            log.warn("Internal request presented an invalid GAP JWT: {}", e.getMessage());
            return false;
        }
    }

    private boolean isBypassProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(BYPASS_PROFILES::contains);
    }

    private void writePermissionDenied(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "PERMISSION_DENIED");
        body.put("message", "Authentication required for internal endpoints");
        body.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
