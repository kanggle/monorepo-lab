package com.example.admin.infrastructure.security;

import com.example.admin.application.exception.InvalidBootstrapTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Enforces {@code Authorization: Bearer <bootstrap-token>} on the 2FA
 * enroll/verify sub-tree. All other paths are skipped — the operator JWT
 * filter (and its unauth bypass list) governs them.
 *
 * <p>On success, stores a {@link BootstrapContext} on the request attribute
 * for {@code AdminAuthController} to read via
 * {@code BootstrapContext.ATTRIBUTE}. On failure, writes a standard
 * {@code INVALID_BOOTSTRAP_TOKEN} 401 envelope directly and short-circuits
 * the chain (no Spring authentication is created — the Security filter chain
 * has the path permitAll'd).
 */
@Slf4j
public class BootstrapAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final BootstrapTokenService tokenService;

    public BootstrapAuthenticationFilter(BootstrapTokenService tokenService) {
        this.tokenService = tokenService;
    }

    private static final String PATH_ENROLL = "/api/admin/auth/2fa/enroll";
    private static final String PATH_VERIFY = "/api/admin/auth/2fa/verify";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !(PATH_ENROLL.equals(path) || PATH_VERIFY.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            unauthorized(response, "Missing bootstrap Authorization header");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        String requiredScope = requiredScopeFor(request.getRequestURI());
        BootstrapContext ctx;
        try {
            ctx = tokenService.verifyAndConsume(token, requiredScope);
        } catch (InvalidBootstrapTokenException e) {
            log.debug("bootstrap token rejected: {}", e.getMessage());
            unauthorized(response, "Bootstrap token invalid or expired");
            return;
        }
        request.setAttribute(BootstrapContext.ATTRIBUTE, ctx);
        filterChain.doFilter(request, response);
    }

    private static String requiredScopeFor(String path) {
        if (PATH_ENROLL.equals(path)) return BootstrapTokenService.SCOPE_ENROLL;
        if (PATH_VERIFY.equals(path)) return BootstrapTokenService.SCOPE_VERIFY;
        return null;
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"INVALID_BOOTSTRAP_TOKEN\",\"message\":\"" + message
                        + "\",\"timestamp\":\"" + Instant.now().toString() + "\"}");
    }
}
