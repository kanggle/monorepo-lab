package com.example.community.infrastructure.security;

import com.example.community.application.ActorContext;
import com.gap.security.jwt.JwtVerificationException;
import com.gap.security.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class AccountAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/community/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            unauthorized(response, "Missing or malformed Authorization header");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        Map<String, Object> claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtVerificationException e) {
            unauthorized(response, "Token invalid");
            return;
        }

        Object subObj = claims.get("sub");
        if (subObj == null) {
            unauthorized(response, "sub claim missing");
            return;
        }

        Set<String> roles = extractRoles(claims);
        ActorContext ctx = new ActorContext(subObj.toString(), roles);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        AccountAuthenticationToken auth = new AccountAuthenticationToken(ctx, authorities);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Map<String, Object> claims) {
        Object raw = claims.get("roles");
        if (raw == null) raw = claims.get("role");
        if (raw == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        if (raw instanceof Collection<?> c) {
            for (Object v : c) out.add(String.valueOf(v));
        } else if (raw instanceof String s) {
            for (String part : s.split("[,\\s]+")) {
                if (!part.isBlank()) out.add(part);
            }
        }
        return out;
    }

    private static void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"TOKEN_INVALID\",\"message\":\"" + message
                        + "\",\"timestamp\":\"" + Instant.now().toString() + "\"}");
    }

    public static class AccountAuthenticationToken extends AbstractAuthenticationToken {
        private final ActorContext principal;

        public AccountAuthenticationToken(ActorContext principal,
                                          Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            super(authorities == null ? List.of() : authorities);
            this.principal = principal;
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public ActorContext getPrincipal() {
            return principal;
        }
    }
}
