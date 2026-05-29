package com.example.account.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code /internal/**} requests carrying the static {@code X-Internal-Token}
 * header (TASK-BE-142 fail-closed semantics).
 *
 * <p><b>TASK-BE-317 (ADR-005 단계 2 — dual-allow):</b> this filter is now
 * <em>non-terminal</em>. Instead of writing 401 itself, it merely populates the
 * {@link org.springframework.security.core.context.SecurityContext} with an authenticated
 * token when a valid {@code X-Internal-Token} is present (or when running unconfigured under
 * the dev/test bypass). The final authorization decision is made by the Spring Security
 * filter chain, where {@code /internal/**} is {@code .authenticated()}. This lets a request
 * authenticate via <em>either</em> a valid {@code X-Internal-Token} (this filter) <em>or</em>
 * a valid GAP {@code client_credentials} JWT (the OAuth2 resource-server
 * {@code BearerTokenAuthenticationFilter}, which runs after this one). When neither produces
 * an {@code Authentication}, the chain rejects with 401 (fail-closed preserved).
 *
 * <p>The filter is registered before
 * {@code org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}.
 * It does not reject on a missing/invalid token — it simply leaves the context untouched so
 * the bearer-token path (or the final {@code .authenticated()} gate) can act.
 *
 * <p>Fail-closed: when no token is configured AND the bypass is disabled, the filter sets no
 * authentication, so every {@code /internal/**} request is rejected by the {@code .authenticated()}
 * rule with 401. The bypass flag is intended solely for {@code @WebMvcTest} slice tests and local
 * development — production deployments must supply {@code INTERNAL_API_TOKEN}.
 */
@Slf4j
public class InternalApiFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /** Principal name attached to requests authenticated via the static internal token. */
    static final String INTERNAL_PRINCIPAL = "internal-service-token";
    /** Authority granted to internal-token-authenticated requests. */
    static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";

    private final String expectedToken;
    private final boolean bypassWhenUnconfigured;

    public InternalApiFilter(String expectedToken, boolean bypassWhenUnconfigured) {
        this.expectedToken = expectedToken;
        this.bypassWhenUnconfigured = bypassWhenUnconfigured;
        if ((expectedToken == null || expectedToken.isBlank()) && !bypassWhenUnconfigured) {
            log.warn("INTERNAL_API_TOKEN is not configured — /internal/** requests authenticate only via "
                    + "GAP JWT; X-Internal-Token requests are rejected with 401 (fail-closed).");
        } else if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("INTERNAL_API_TOKEN is not configured — bypass is enabled (dev/test only). DO NOT USE IN PRODUCTION.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX) && internalTokenAccepted(request)) {
            authenticateInternalRequest();
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return {@code true} when this request should be treated as an authenticated internal call
     *         based on the static token (or the dev/test bypass). Never rejects — a {@code false}
     *         result simply defers to the bearer-token path / final authorization rule.
     */
    private boolean internalTokenAccepted(HttpServletRequest request) {
        boolean tokenConfigured = expectedToken != null && !expectedToken.isBlank();
        if (!tokenConfigured) {
            // Unconfigured: authenticate only under the dev/test bypass; otherwise leave unauthenticated.
            return bypassWhenUnconfigured;
        }
        return expectedToken.equals(request.getHeader(INTERNAL_TOKEN_HEADER));
    }

    private void authenticateInternalRequest() {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            // A JWT (or prior mechanism) already authenticated this request — don't override.
            return;
        }
        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                INTERNAL_PRINCIPAL, "n/a", List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
