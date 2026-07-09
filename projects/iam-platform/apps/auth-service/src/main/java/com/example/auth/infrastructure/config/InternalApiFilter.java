package com.example.auth.infrastructure.config;

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
 * Authenticates auth-service {@code /internal/**} requests <em>only</em> under the dev/test bypass.
 *
 * <p><b>TASK-BE-487 (ADR-005 단계 4 — auth-service receiver):</b> auth-service was the last GAP
 * service whose {@code /internal/**} receiver stayed {@code permitAll()} while every other receiver
 * (account BE-319b / security BE-319a) had already moved to GAP {@code client_credentials} JWT. This
 * filter closes that gap by mirroring the account-service blueprint: in all real profiles the
 * credential/action {@code /internal/auth/**} endpoints are authenticated solely by the OAuth2
 * resource-server ({@code BearerTokenAuthenticationFilter}) against the {@code .authenticated()} gate;
 * this filter no longer participates there. ({@code /internal/auth/jwks} stays {@code permitAll} — it
 * is the public key-distribution endpoint the gateway fetches to <em>validate</em> tokens and cannot
 * itself present one.)
 *
 * <p>This filter remains <em>non-terminal</em> and serves a single purpose: under the
 * {@code bypass} flag (the {@code 'test'}/{@code 'standalone'} profile or an explicit
 * {@code internal.api.bypass-when-unconfigured=true}) it populates the
 * {@link org.springframework.security.core.context.SecurityContext} with an authenticated token so
 * {@code @WebMvcTest} slice tests and local/standalone runs can reach {@code /internal/**} without a
 * real JWT. It never rejects — a request it does not authenticate simply defers to the bearer-token
 * path and the final {@code .authenticated()} gate (which rejects with 401, fail-closed).
 *
 * <p>The bypass is a profile-gated dev/test convenience, not a shared secret — production profiles
 * always require a valid GAP JWT.
 */
@Slf4j
public class InternalApiFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /** Principal name attached to requests authenticated via the dev/test bypass. */
    static final String INTERNAL_PRINCIPAL = "internal-service-token";
    /** Authority granted to bypass-authenticated requests. */
    static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";

    private final boolean bypass;

    public InternalApiFilter(boolean bypass) {
        this.bypass = bypass;
        if (!bypass) {
            log.info("/internal/auth/** authenticate only via GAP client_credentials JWT "
                    + "(Authorization: Bearer); no static-token path (TASK-BE-487, fail-closed).");
        } else {
            log.warn("InternalApiFilter bypass is enabled (dev/test/standalone only) — /internal/** requests "
                    + "are authenticated without a JWT. DO NOT USE IN PRODUCTION.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (bypass && request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            authenticateInternalRequest();
        }
        filterChain.doFilter(request, response);
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
