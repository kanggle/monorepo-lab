package com.example.scmplatform.inventoryvisibility.config;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.security.PublicPaths;
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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

/**
 * inventory-visibility-service Spring Security configuration.
 * Read-only API — all GET /api/inventory-visibility/** require bearer token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The actuator permit list and TenantClaimEnforcer's exemption now come from the same
        // object (ADR-MONO-049 § 1.8, TASK-MONO-385). They used to be written out separately
        // here and in the filter, and they had already drifted: this list held three paths while
        // the filter exempted all of /actuator/.
        String[] exact = PublicPaths.EXACT.toArray(new String[0]);
        String[] prefixed = PublicPaths.PREFIXES.stream()
                .map(p -> p + "**")
                .toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(exact).permitAll();
                    // PREFIXES is empty for this service today. Guarded rather than omitted so
                    // that adding one to PublicPaths permits it here automatically — the whole
                    // point is that these two can no longer be edited independently.
                    if (prefixed.length > 0) {
                        auth.requestMatchers(prefixed).permitAll();
                    }
                    // Internal, network-trusted, gateway-blocked (ADR-MONO-027 §D7.1).
                    // No JWT: the demand-planning replenishment batch is unattended (no
                    // operator token). Reachable only on the intra-scm container network —
                    // scm-gateway routes only /api/v1/**, never /internal/**.
                    //
                    // Deliberately NOT in PublicPaths: PublicPaths is the tenant filter's
                    // exemption list, and this path needs no exemption. It carries no JWT, and
                    // TenantClaimEnforcer passes non-JwtAuthenticationToken requests straight
                    // through — so the filter never gates it either way (ADR-MONO-049 § 1.8 C).
                    auth.requestMatchers("/internal/inventory-visibility/**").permitAll();
                    auth.requestMatchers("/api/inventory-visibility/**").authenticated()
                            .anyRequest().denyAll();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> {})
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
        if (oauthError != null && "tenant_mismatch".equals(oauthError.getErrorCode())) {
            code = "TENANT_FORBIDDEN";
            status = HttpStatus.FORBIDDEN.value();
            message = oauthError.getDescription() != null
                    ? oauthError.getDescription() : "Cross-tenant access denied";
        } else if (oauthError != null && oauthError.getDescription() != null) {
            message = oauthError.getDescription();
        }
        writeError(response, status, code, message);
    }

    static void onAccessDenied(HttpServletRequest request,
                                HttpServletResponse response,
                                org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(), "PERMISSION_DENIED", "Access denied");
    }

    private static OAuth2Error extractOAuth2Error(Throwable t) {
        Throwable cur = t;
        OAuth2Error fallback = null;
        while (cur != null) {
            if (cur instanceof JwtValidationException jve) {
                for (OAuth2Error err : jve.getErrors()) {
                    if (err != null && !"invalid_token".equals(err.getErrorCode())) return err;
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
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        }
    }
}
