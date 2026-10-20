package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.presentation.security.PublicPaths;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.FirewallRejections;
import com.example.security.servlet.ResourceServerChainAssembler;
import com.example.security.servlet.actor.ActorContextJwtAuthenticationConverter;
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
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * procurement-service Spring Security configuration.
 *
 * <ul>
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus} — public</li>
 *   <li>{@code /api/procurement/webhooks/**} — public (HMAC-SHA256 + timestamp + replay verified by {@link WebhookSignatureFilter})</li>
 *   <li>{@code /api/procurement/**} — bearer token + business role (TASK-MONO-792): {@code GET} =
 *       {@code SCM_PROCUREMENT_READ ∨ _WRITE}; {@code POST /po/from-suggestion} =
 *       {@code SCM_PROCUREMENT_WRITE ∨ SCM_DEMAND_PLANNING_WRITE}; every other method =
 *       {@code SCM_PROCUREMENT_WRITE}</li>
 *   <li>everything else — denied</li>
 * </ul>
 *
 * <p>The generic tail — CSRF-disabled, {@code STATELESS}, the permit/authenticate/deny sequence and
 * the {@code oauth2ResourceServer} wiring — is assembled by
 * {@link ResourceServerChainAssembler#statelessJwtChain(HttpSecurity)} (ADR-MONO-058 § D4). The
 * closed {@code anyRequest().denyAll()} tail is stated explicitly below because it is this
 * service's own answer, not a default worth inheriting silently.
 *
 * <p>The webhook prefix reaches the controllers through {@link PublicPaths}' {@code permitAll} —
 * it never enters the JWT decoder path. Its own authentication is
 * {@link WebhookSignatureFilter} (HMAC + timestamp + replay), registered ahead of this chain by
 * {@link WebhookSecurityConfig}, and the D4 adoption does not move it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** TASK-MONO-792 — scm iam-integration.md § 업무 역할 (= iam DomainRoleCatalog scm rows). */
    static final String PROCUREMENT_READ = "SCM_PROCUREMENT_READ";
    static final String PROCUREMENT_WRITE = "SCM_PROCUREMENT_WRITE";
    static final String DEMAND_PLANNING_WRITE = "SCM_DEMAND_PLANNING_WRITE";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return ResourceServerChainAssembler.statelessJwtChain(http)
                .publicPaths(PublicPaths.AS_SET)
                // TASK-MONO-792 (ADR-MONO-083 P5) — business roles, URL × method (scm iam-integration.md
                // § 업무 역할). Registered after the public webhook prefix (HMAC, unchanged) and before the
                // closed tail; first match wins, so the narrow from-suggestion rule precedes the catch-all.
                // The actor model (isOperator → PO confirm · supplier registration) still applies on top —
                // this gate never widens it. SCM_OPERATOR / OPERATOR alone open nothing here.
                .authorizeRules(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/procurement/**")
                        .hasAnyRole(PROCUREMENT_READ, PROCUREMENT_WRITE)
                        // owner decision Q2: demand-planning approval drafts a PO with the caller's token
                        .requestMatchers(HttpMethod.POST, "/api/procurement/po/from-suggestion")
                        .hasAnyRole(PROCUREMENT_WRITE, DEMAND_PLANNING_WRITE)
                        .requestMatchers("/api/procurement/**")
                        .hasRole(PROCUREMENT_WRITE))
                .anyRequestDenied()
                // ADR-MONO-058 § D1: the claim-lifting mechanism is the shared one; scm's own
                // ActorContext record — isOperator(), actorType(), its role literals — stays here
                // and is reached only through this constructor reference (Ownership Rule).
                .jwtAuthenticationConverter(
                        new ActorContextJwtAuthenticationConverter<>(ActorContext::new))
                .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                .accessDeniedHandler(SecurityConfig::onAccessDenied)
                .build();
    }

    // TASK-MONO-801: a path the firewall rejects up front → 400 BAD_REQUEST here, not denyAll's 401 on /error.
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return FirewallRejections.handler();
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
