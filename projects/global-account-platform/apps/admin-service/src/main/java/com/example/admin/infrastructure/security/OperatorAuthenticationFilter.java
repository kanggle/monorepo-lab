package com.example.admin.infrastructure.security;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.port.TokenBlacklistPort;
import com.gap.security.jwt.JwtVerificationException;
import com.gap.security.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Verifies the operator JWT on {@code /api/admin/**} requests:
 *   - RS256 signature via shared {@link JwtVerifier}
 *   - required claim {@code token_type == "admin"} (rbac.md D4)
 *   - extracts {@code sub} (operator UUID v7) and {@code jti} (session identifier)
 *
 * <p>No {@code roles} claim is consumed: per rbac.md D5 authorization is
 * resolved via DB lookup on every request (see {@code PermissionEvaluator}).
 */
@Slf4j
public class OperatorAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    /** Request attribute holding the verified access JWT exp (Instant). */
    public static final String ACCESS_EXP_ATTRIBUTE = "admin.access.exp";

    private final JwtVerifier jwtVerifier;
    private final String expectedTokenType;
    private final TokenBlacklistPort blacklist;

    public OperatorAuthenticationFilter(JwtVerifier jwtVerifier, String expectedTokenType) {
        this(jwtVerifier, expectedTokenType, null);
    }

    public OperatorAuthenticationFilter(JwtVerifier jwtVerifier,
                                        String expectedTokenType,
                                        TokenBlacklistPort blacklist) {
        this.jwtVerifier = jwtVerifier;
        this.expectedTokenType = expectedTokenType;
        this.blacklist = blacklist;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // Unauthenticated sub-tree per specs/contracts/http/admin-api.md
        // "Authentication Exceptions". These endpoints either predate the
        // operator JWT (login, 2FA bootstrap) or expose only public keys
        // (JWKS). X-Operator-Reason and RBAC aspect are also skipped for
        // these paths — see SecurityConfig.filterChain and
        // RequiresPermissionAspect pointcuts.
        if ("POST".equalsIgnoreCase(method) && (
                "/api/admin/auth/login".equals(path)
                        || "/api/admin/auth/2fa/enroll".equals(path)
                        || "/api/admin/auth/2fa/verify".equals(path)
                        // TASK-BE-040: refresh runs without an access token —
                        // it presents a refresh JWT in the body instead.
                        || "/api/admin/auth/refresh".equals(path))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/.well-known/admin/jwks.json".equals(path)) {
            return true;
        }
        return !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            unauthorized(response, "TOKEN_INVALID", "Missing or malformed Authorization header");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        Map<String, Object> claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtVerificationException e) {
            unauthorized(response, "TOKEN_INVALID", "Operator token invalid");
            return;
        }

        Object tokenType = claims.get("token_type");
        if (!expectedTokenType.equals(tokenType)) {
            unauthorized(response, "TOKEN_INVALID", "Operator token_type missing or mismatched");
            return;
        }

        Object subObj = claims.get("sub");
        if (subObj == null) {
            unauthorized(response, "TOKEN_INVALID", "Operator subject missing");
            return;
        }

        Object jtiObj = claims.get("jti");
        String jti = jtiObj != null ? jtiObj.toString() : null;

        // TASK-BE-040: post-logout blacklist check. Adapter is fail-closed —
        // any infra error returns true so the request is rejected with
        // TOKEN_REVOKED rather than admitted with unknown status.
        if (jti != null && blacklist != null && blacklist.isBlacklisted(jti)) {
            unauthorized(response, "TOKEN_REVOKED", "Operator token has been revoked");
            return;
        }

        Object expObj = claims.get("exp");
        Instant exp = null;
        if (expObj instanceof Number num) {
            exp = Instant.ofEpochSecond(num.longValue());
        } else if (expObj instanceof Instant ins) {
            exp = ins;
        }
        if (exp != null) {
            request.setAttribute(ACCESS_EXP_ATTRIBUTE, exp);
        }

        OperatorContext ctx = new OperatorContext(subObj.toString(), jti);
        OperatorAuthenticationToken auth = new OperatorAuthenticationToken(ctx);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void unauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message
                        + "\",\"timestamp\":\"" + Instant.now().toString() + "\"}");
    }

    public static class OperatorAuthenticationToken extends AbstractAuthenticationToken {
        private final OperatorContext principal;

        public OperatorAuthenticationToken(OperatorContext principal) {
            super(List.of());
            this.principal = principal;
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public OperatorContext getPrincipal() {
            return principal;
        }
    }
}
