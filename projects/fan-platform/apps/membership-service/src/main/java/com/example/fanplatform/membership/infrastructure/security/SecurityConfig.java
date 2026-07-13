package com.example.fanplatform.membership.infrastructure.security;

import com.example.security.oauth2.TenantClaimValidator;

import com.example.fanplatform.membership.presentation.security.PublicPaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

/**
 * membership-service Spring Security configuration — TWO ordered filter chains.
 *
 * <ul>
 *   <li><b>Order(1) internal chain</b> ({@code securityMatcher("/internal/**")}) —
 *       workload-identity. Validates an IAM {@code client_credentials} JWT via
 *       {@code internalJwtDecoder} and requires {@code ROLE_INTERNAL} (granted by
 *       {@link WorkloadIdentityAuthoritiesConverter} only to a recognized machine
 *       client). End-user token → 403; no token → 401 (ADR-MONO-005, AC-5).</li>
 *   <li><b>Order(2) end-user chain</b> — {@code /api/fan/**} bearer required,
 *       tenant-pinned {@code endUserJwtDecoder}, {@link ActorContextJwtAuthenticationConverter}.
 *       Public actuator paths permitted. Cross-tenant → 403 TENANT_FORBIDDEN.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Order(1): the workload-identity {@code /internal/**} chain. Separate
     * {@code SecurityFilterChain} from the end-user chain (AC-5).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http,
                                                   JwtDecoder internalJwtDecoder) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("INTERNAL"))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt
                                .decoder(internalJwtDecoder)
                                .jwtAuthenticationConverter(new WorkloadIdentityAuthoritiesConverter()))
                        // No token → 401.
                        .authenticationEntryPoint(SecurityConfig::onInternalAuthFailure)
                        // Valid token but not a workload identity (end-user) → 403.
                        .accessDeniedHandler(SecurityConfig::onInternalAccessDenied)
                );
        return http.build();
    }

    /**
     * Order(2): the end-user chain for {@code /api/fan/**} + public actuator.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain endUserFilterChain(HttpSecurity http,
                                                  JwtDecoder endUserJwtDecoder) throws Exception {
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
                        .requestMatchers("/api/fan/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt
                                .decoder(endUserJwtDecoder)
                                .jwtAuthenticationConverter(new ActorContextJwtAuthenticationConverter()))
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(SecurityConfig::onAccessDenied)
                );
        return http.build();
    }

    // ----- internal chain handlers -----------------------------------------

    public static void onInternalAuthFailure(HttpServletRequest request,
                                             HttpServletResponse response,
                                             org.springframework.security.core.AuthenticationException e)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED", "Missing or invalid internal credentials");
    }

    public static void onInternalAccessDenied(HttpServletRequest request,
                                              HttpServletResponse response,
                                              org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN", "Workload identity required for /internal/**");
    }

    // ----- end-user chain handlers -----------------------------------------

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
