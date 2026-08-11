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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
    //
    // TASK-MONO-512 / ADR-MONO-059 ACCEPTED — A: NOBODY CAN PRESENT ANY OF THESE FOUR HERE,
    // AND THAT IS A DECISION, NOT A GAP.
    // ---------------------------------------------------------------------------------------
    // No token reaching this service can carry an admin-tier role today, so every rule below
    // that names ADMIN_ROLES is unreachable. Two independent reasons, both measured:
    //
    //   * FAN_OPERATOR is DERIVED, at assume-tenant, from the selected tenant's ACTIVE
    //     `tenant_domain_subscription` rows. No tenant subscribes `fan` (0 of 18 rows across
    //     12 tenants) and no operator is assigned to `fan-platform`; reaching the arm needs
    //     BOTH rows. ADMIN/OPERATOR/SUPER_ADMIN are granted roles rather than derived ones,
    //     but nothing grants them in `fan-platform` either.
    //   * ADR-MONO-059 § Decision excluded option B — an operator authoring on an artist's
    //     behalf — and records as BINDING that the "an operator assumes a B2C_CONSUMER tenant"
    //     combination is NOT opened. `fan-platform` is B2C_CONSUMER (account-service V0009),
    //     so supplying those rows is now out of scope BY DECISION, not merely undone.
    //
    // The set is NOT deleted, and that is equally a decision: ADR-MONO-059 offered option D
    // (declare the fan operator plane dead and strip its four acceptors) and the owner did not
    // choose it. ARTIST_POST stays a v1 product feature; what changed is WHO authors it — the
    // artist's own account holding ARTIST (TASK-MONO-512), not an operator through this gate.
    //
    // Consequence, written down so it is not rediscovered as a bug: the artist DIRECTORY
    // (artists, groups, fandoms) consequently has no API caller at all, which is why
    // infra/demo/seed/seed-fan.sh still creates those rows by direct DB write. TASK-MONO-522
    // owns that gap. Re-opening this gate is a role-model decision (a new ADR), not a fix.
    private static final String[] ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };

    /**
     * Order(1): the workload-identity {@code /internal/**} chain (TASK-FAN-BE-045
     * AC-6, ADR-004 A). Authenticates an IAM {@code client_credentials} JWT with
     * {@code internalJwtDecoder} and requires {@code ROLE_INTERNAL}, which
     * {@link WorkloadIdentityAuthoritiesConverter} grants only on a machine scope.
     * No token → 401; a valid end-user token → 403.
     *
     * <p>Hand-assembled rather than routed through {@link ResourceServerChainAssembler}:
     * this is not a stateless <em>end-user</em> resource server. It uses a different
     * decoder (no tenant pin), a different converter, gates on a role rather than on
     * public-vs-authenticated paths, and writes different 401/403 bodies. Same
     * reasoning membership-service's Order(1) chain records.
     *
     * <p>NOT gateway-routed — the gateway maps {@code /api/v1/**} only, so this
     * surface is reachable on the internal docker network alone.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http,
                                                   NimbusJwtDecoder internalJwtDecoder) throws Exception {
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
                        .authenticationEntryPoint(SecurityConfig::onInternalAuthFailure)
                        .accessDeniedHandler(SecurityConfig::onInternalAccessDenied));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
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
        // The decoder IS pinned explicitly now (TASK-FAN-BE-045): this service declares TWO
        // decoders since the Order(1) internal chain arrived, so Spring Security's by-type
        // resolution no longer has a single candidate. Pinning here keeps this chain on the
        // tenant-pinned end-user decoder — same shape as membership-service's Order(2).
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
                .jwtDecoder(jwtDecoder)
                .jwtAuthenticationConverter(
                        new ActorContextJwtAuthenticationConverter<>(ActorContext::new))
                .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                .accessDeniedHandler(SecurityConfig::onAccessDenied)
                .build();
    }

    // ----- internal (workload-identity) chain handlers ---------------------

    static void onInternalAuthFailure(HttpServletRequest request,
                                      HttpServletResponse response,
                                      org.springframework.security.core.AuthenticationException e)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED", "Missing or invalid internal credentials");
    }

    static void onInternalAccessDenied(HttpServletRequest request,
                                       HttpServletResponse response,
                                       org.springframework.security.access.AccessDeniedException e)
            throws IOException {
        writeError(response, HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN", "Workload identity required for /internal/**");
    }

    // ----- end-user chain handlers -----------------------------------------

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
