package com.example.admin.infrastructure.config;

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
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2): authenticates {@code /internal/**}
 * requests <em>only</em> under the dev/test bypass — mirrors account-service
 * {@code InternalApiFilter} (TASK-BE-319b) verbatim in intent.
 *
 * <p>In all real profiles {@code /internal/**} is authenticated solely by the
 * OAuth2 resource-server ({@code BearerTokenAuthenticationFilter}) against the
 * {@code @Order(0)} {@code /internal/**} chain's {@code .authenticated()} gate;
 * this filter no longer participates (it is <em>non-terminal</em>).
 *
 * <p>Under the {@code bypass} flag (the {@code 'test'}/{@code 'standalone'}
 * profile or an explicit {@code internal.api.bypass-when-unconfigured=true}) it
 * populates an authenticated token so {@code @WebMvcTest} slice tests and
 * local/standalone runs can reach {@code /internal/**} without a real JWT. It
 * never rejects — a request it does not authenticate defers to the bearer-token
 * path and the final {@code .authenticated()} gate (which rejects 401,
 * fail-closed).
 *
 * <p>The bypass is a profile-gated dev/test convenience, not a shared secret —
 * production profiles always require a valid GAP JWT.
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
            log.info("/internal/** authenticate only via GAP client_credentials JWT (Authorization: Bearer); "
                    + "no static-token path (TASK-BE-327, fail-closed).");
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
