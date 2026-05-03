package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.adapter.in.web.security.ActorContextJwtAuthenticationConverter;
import com.example.fanplatform.artist.adapter.in.web.security.PublicPaths;
import com.example.fanplatform.artist.adapter.in.web.security.TenantClaimValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

/**
 * artist-service Spring Security configuration.
 *
 * <p>Authorization rules (per task spec § Implementation Notes — admin role):
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus} — public</li>
 *   <li>{@code GET /api/artists/**}, {@code GET /api/artist-groups/**}, {@code GET /api/fandoms/**}
 *       — bearer token required, any role</li>
 *   <li>{@code POST/PATCH/DELETE} on {@code /api/artists/**}, {@code /api/artist-groups/**},
 *       {@code /api/fandoms/**} — admin-tier role required
 *       ({@code ROLE_ADMIN}, {@code ROLE_OPERATOR}, {@code ROLE_SUPER_ADMIN})</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p>Cross-tenant rejection: the {@link TenantClaimValidator} fails the JWT
 * during decoding when {@code tenant_id} differs from {@code fan-platform}
 * (and is not the {@code "*"} wildcard). The Resource Server filter surfaces
 * that as a 401 by default; we map the granular {@code tenant_mismatch} error
 * code to 403 {@code TENANT_FORBIDDEN}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String[] ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN" };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(exact).permitAll()
                        .requestMatchers(prefixed).permitAll()
                        // Admin-only mutating endpoints across the three resource families.
                        .requestMatchers(HttpMethod.POST,   "/api/artists/**",       "/api/artists").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PATCH,  "/api/artists/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/artists/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.POST,   "/api/artist-groups/**", "/api/artist-groups").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PATCH,  "/api/artist-groups/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/artist-groups/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.POST,   "/api/fandoms/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PATCH,  "/api/fandoms/**").hasAnyRole(ADMIN_ROLES)
                        // Reads — any authenticated caller in the same tenant.
                        .requestMatchers(HttpMethod.GET,    "/api/artists/**",       "/api/artists").authenticated()
                        .requestMatchers(HttpMethod.GET,    "/api/artist-groups/**", "/api/artist-groups").authenticated()
                        .requestMatchers(HttpMethod.GET,    "/api/fandoms/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ActorContextJwtAuthenticationConverter()))
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(SecurityConfig::onAccessDenied)
                );
        return http.build();
    }

    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException e)
            throws IOException {
        String code = "UNAUTHORIZED";
        int status = HttpStatus.UNAUTHORIZED.value();
        String message = "Authentication required";

        OAuth2Error oauthError = extractOAuth2Error(e);
        if (oauthError != null) {
            if (TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(oauthError.getErrorCode())) {
                code = "TENANT_FORBIDDEN";
                status = HttpStatus.FORBIDDEN.value();
                message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Cross-tenant access denied";
            } else if (oauthError.getDescription() != null) {
                message = oauthError.getDescription();
            }
        }
        writeError(response, status, code, message);
    }

    static void onAccessDenied(HttpServletRequest request,
                               HttpServletResponse response,
                               org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN", "Access denied");
    }

    private static OAuth2Error extractOAuth2Error(Throwable t) {
        Throwable cur = t;
        OAuth2Error fallback = null;
        while (cur != null) {
            if (cur instanceof JwtValidationException jve) {
                for (OAuth2Error err : jve.getErrors()) {
                    if (err != null && err.getErrorCode() != null
                            && !"invalid_token".equals(err.getErrorCode())) {
                        return err;
                    }
                }
            }
            if (cur instanceof InvalidBearerTokenException ibte) {
                OAuth2Error err = ibte.getError();
                if (err != null) fallback = err;
            }
            cur = cur.getCause();
        }
        return fallback;
    }

    private static void writeError(HttpServletResponse response, int status,
                                   String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ObjectNode node = JSON.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        node.put("timestamp", Instant.now().toString());
        try {
            response.getWriter().write(JSON.writeValueAsString(node));
        } catch (JsonProcessingException ex) {
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
