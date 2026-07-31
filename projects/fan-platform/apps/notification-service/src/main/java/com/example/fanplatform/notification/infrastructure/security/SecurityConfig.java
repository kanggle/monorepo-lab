package com.example.fanplatform.notification.infrastructure.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import com.example.security.servlet.actor.ActorContextJwtAuthenticationConverter;

import com.example.fanplatform.notification.application.ActorContext;
import com.example.fanplatform.notification.presentation.security.PublicPaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

/**
 * notification-service Spring Security configuration — a single end-user chain.
 *
 * <p>Unlike membership-service there is NO {@code /internal/**} surface (this
 * service is reached only by end users for the inbox, and via Kafka for the
 * consume path which is not an HTTP route). The inbox routes
 * ({@code /api/fan/**}) require a tenant-pinned bearer token; public actuator
 * paths are permitted. Cross-tenant → 403 TENANT_FORBIDDEN (re-checked by the
 * tenant-pinned {@code endUserJwtDecoder} validators + {@code TenantClaimEnforcer}).
 *
 * <h2>ADR-MONO-058 § D4</h2>
 *
 * The generic tail — CSRF disabled, {@code STATELESS} sessions, public-vs-authenticated routing and
 * the {@code oauth2ResourceServer(...)} call sequence — is assembled by
 * {@link ResourceServerChainAssembler}. It is an explicit call from this {@code @Configuration}, not an
 * auto-configuration: the library registers no filter chain of its own, so this file remains the only
 * place this service's authentication path is decided.
 *
 * <p>What did not move: the public-path data ({@code PublicPaths}), the {@code /api/fan/**} pattern,
 * the {@code anyRequest().denyAll()} tail (stated out loud via {@code anyRequestDenied()} rather than
 * inherited from a default), the {@code ActorContextJwtAuthenticationConverter} composition
 * (ADR-MONO-058 § D1), and the two error writers below.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    public SecurityFilterChain endUserFilterChain(HttpSecurity http,
                                                  JwtDecoder endUserJwtDecoder) throws Exception {
        return ResourceServerChainAssembler.statelessJwtChain(http)
                .publicPaths(PublicPaths.AS_SET)
                .authenticated("/api/fan/**")
                .anyRequestDenied()
                .jwtDecoder(endUserJwtDecoder)
                .jwtAuthenticationConverter(
                        new ActorContextJwtAuthenticationConverter<>(ActorContext::new))
                .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                .accessDeniedHandler(SecurityConfig::onAccessDenied)
                .build();
    }

    public static void onAuthenticationFailure(HttpServletRequest request,
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

    public static void onAccessDenied(HttpServletRequest request,
                                      HttpServletResponse response,
                                      org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(),
                "PERMISSION_DENIED", "Access denied");
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
