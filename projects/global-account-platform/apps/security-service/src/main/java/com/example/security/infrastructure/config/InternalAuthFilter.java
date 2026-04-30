package com.example.security.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Filter that enforces authentication on /internal/** endpoints.
 * Checks for X-Internal-Token header and validates it against the configured token.
 * Returns 403 PERMISSION_DENIED in the standard error format if missing or invalid.
 *
 * <p>A blank token is only permitted when running under 'test' or 'standalone' profiles.
 * In all other profiles a blank token logs a WARN at startup and every internal request
 * is rejected with 403.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalAuthFilter implements Filter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final Set<String> BLANK_TOKEN_ALLOWED_PROFILES = Set.of("test", "standalone");

    private final String expectedToken;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public InternalAuthFilter(
            @Value("${security-service.internal-token:}") String expectedToken,
            ObjectMapper objectMapper,
            Environment environment) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @PostConstruct
    void validateTokenConfiguration() {
        if (expectedToken == null || expectedToken.isBlank()) {
            if (isBlankTokenAllowedProfile()) {
                log.info("Internal auth token is blank; permitted in current profile");
            } else {
                log.warn("security-service.internal-token is blank — all /internal/** requests will be rejected. "
                        + "Set the token property or run with 'test'/'standalone' profile to bypass.");
            }
        }
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

        // Allow blank token only in test/standalone profiles
        if (expectedToken == null || expectedToken.isBlank()) {
            if (isBlankTokenAllowedProfile()) {
                chain.doFilter(request, response);
                return;
            }
            log.warn("Rejecting internal request — no token configured: path={}", path);
            writePermissionDenied((HttpServletResponse) response);
            return;
        }

        String providedToken = httpRequest.getHeader(TOKEN_HEADER);
        if (providedToken == null || !expectedToken.equals(providedToken)) {
            log.warn("Unauthorized access attempt to internal endpoint: path={}, remoteAddr={}",
                    path, httpRequest.getRemoteAddr());
            writePermissionDenied((HttpServletResponse) response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isBlankTokenAllowedProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(BLANK_TOKEN_ALLOWED_PROFILES::contains);
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
