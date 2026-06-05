package com.example.community.infrastructure.config;

import com.example.community.infrastructure.security.ActorContextJwtAuthenticationConverter;
import com.example.community.infrastructure.security.TenantClaimValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;

/**
 * community-service Spring Security configuration (TASK-BE-253).
 *
 * <p>Replaces the legacy {@code AccountAuthenticationFilter} (custom JwtVerifier-based)
 * with a standard OAuth2 Resource Server filter chain backed by GAP's JWKS URI.
 *
 * <p>Authorization rules:
 * <ul>
 *   <li>{@code /actuator/**} — public</li>
 *   <li>{@code /internal/**} — denied (community-service does not expose internal endpoints)</li>
 *   <li>{@code /api/community/**} — bearer token required</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p>Cross-tenant rejection (TENANT_FORBIDDEN, HTTP 403): the {@link TenantClaimValidator}
 * fails the {@link org.springframework.security.oauth2.jwt.Jwt} during decoding when
 * {@code tenant_id} differs from {@code fan-platform}. This surfaces as a 401 from the
 * Resource Server filter; we map the specific {@code tenant_mismatch} error code to 403
 * via a custom {@link BearerTokenAuthenticationEntryPoint}-style handler.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers("/api/community/**").authenticated()
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

    /**
     * Maps token-validation failures to 401 (default) or 403 (when the token decoding
     * failed because of {@link TenantClaimValidator}).
     */
    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException e)
            throws IOException {
        String code = "TOKEN_INVALID";
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
            } else {
                message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Authentication required";
            }
        }
        writeError(response, status, code, message);
    }

    static void onAccessDenied(HttpServletRequest request,
                               HttpServletResponse response,
                               org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response,
                HttpStatus.FORBIDDEN.value(),
                "PERMISSION_DENIED",
                "Access denied");
    }

    private static OAuth2Error extractOAuth2Error(Throwable t) {
        Throwable cur = t;
        OAuth2Error fallback = null;
        while (cur != null) {
            // JwtValidationException carries the granular validator errors
            // (tenant_mismatch, invalid_issuer, ...). Prefer those over the
            // generic invalid_token code that wraps them.
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
            // Should never happen — fall back to a minimal payload.
            response.getWriter().write(
                    "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
