package com.example.auth.infrastructure.security;

import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.ParsedToken;
import com.example.auth.domain.service.TokenParser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenParser tokenParser;
    private final AccessTokenBlocklist accessTokenBlocklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                ParsedToken parsed = tokenParser.parse(token);
                if (isTokenBlocked(token) || isUserBlocked(parsed.userId())) {
                    log.warn("Blocked access token or user received");
                    SecurityContextHolder.clearContext();
                } else {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            parsed.userId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                    authentication.setDetails(parsed.email());
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Invalid JWT token received");
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenBlocked(String token) {
        try {
            return accessTokenBlocklist.isBlocked(token);
        } catch (DataAccessException e) {
            log.error("Blocklist check failed, failing open", e);
            return false;
        }
    }

    private boolean isUserBlocked(UUID userId) {
        try {
            return accessTokenBlocklist.isUserBlocked(userId);
        } catch (DataAccessException e) {
            log.error("User blocklist check failed, failing open", e);
            return false;
        }
    }
}
