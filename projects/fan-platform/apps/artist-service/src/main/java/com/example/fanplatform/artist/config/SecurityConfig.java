package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.adapter.in.web.security.PublicPaths;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.security.oauth2.TenantClaimValidator;
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
 *
 * <h2>ADR-MONO-058 § D4</h2>
 *
 * The generic tail — CSRF disabled, {@code STATELESS} sessions, the public-path {@code permitAll()}
 * block, the {@code anyRequest()} tail and the {@code oauth2ResourceServer(...)} call sequence — is
 * assembled by {@link ResourceServerChainAssembler}. It is an explicit call from this
 * {@code @Configuration}, not an auto-configuration: the library registers no filter chain of its own,
 * so this file remains the only place this service's authentication path is decided.
 *
 * <p>What did not move: the public-path data ({@code PublicPaths}), <strong>every</strong> rule in the
 * authorize block below including {@code ADMIN_ROLES} and the method-scoped matchers, the
 * {@code anyRequest().denyAll()} tail (stated out loud via {@code anyRequestDenied()} rather than
 * inherited from a default), the {@code ActorContextJwtAuthenticationConverter} composition
 * (ADR-MONO-058 § D1), and the two error writers below — including this service's {@code FORBIDDEN}
 * access-denied code, which differs from its three siblings' {@code PERMISSION_DENIED}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    // FAN_OPERATOR is the assume-tenant operator role iam mints on token-exchange
    // (OperatorRoleDerivation); included so a cross-tenant console operator is admitted on the
    // mutating routes rather than silently 403'd here after passing the gateway (TASK-MONO-417).
    // The JWT converter maps every role to ROLE_<role>, so hasAnyRole("FAN_OPERATOR") matches a
    // FAN_OPERATOR claim. Additive — existing generic operators unaffected.
    private static final String[] ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ADR-MONO-058 § D4. Every rule below stays in artist-service's hands and — critically — in
        // its original registration order: authorizeHttpRequests is first-match-wins, so the
        // method-scoped admin gates MUST still be registered before the GET rules that would
        // otherwise shadow them. The builder registers publicPaths() first and anyRequest() last and
        // hands this block everything in between verbatim, which is exactly the sandwich the
        // hand-written chain had.
        //
        // The GET rules stay here rather than moving to the builder's authenticated(...) list because
        // that list is method-agnostic: a method-agnostic /api/artists/** authenticated() rule would
        // be evaluated in the same slot and would shadow nothing today, but it would silently admit a
        // future PUT the admin gates do not name.
        //
        // No .jwtDecoder(...) call: artist-service declares a single JwtDecoder bean and Spring
        // Security resolves it from the context, as it did before this chain was assembled here.
        return ResourceServerChainAssembler.statelessJwtChain(http)
                .publicPaths(PublicPaths.AS_SET)
                .authorizeRules(auth -> auth
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
                        .requestMatchers(HttpMethod.GET,    "/api/fandoms/**").authenticated())
                .anyRequestDenied()
                .jwtAuthenticationConverter(
                        new ActorContextJwtAuthenticationConverter<>(ActorContext::new))
                .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                .accessDeniedHandler(SecurityConfig::onAccessDenied)
                .build();
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
