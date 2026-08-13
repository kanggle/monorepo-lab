package com.example.fanplatform.membership.infrastructure.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import com.example.security.servlet.WorkloadIdentityAuthoritiesConverter;
import com.example.security.servlet.actor.ActorContextJwtAuthenticationConverter;

import com.example.fanplatform.membership.application.ActorContext;
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
 *
 * <h2>ADR-MONO-058 § D4 — one of these two chains is shared-assembled, the other is not</h2>
 *
 * The {@code @Order(2)} end-user chain's generic tail — CSRF disabled, {@code STATELESS} sessions,
 * public-vs-authenticated routing and the {@code oauth2ResourceServer(...)} call sequence — comes from
 * {@link ResourceServerChainAssembler}, called explicitly from this {@code @Configuration}. The
 * library registers no filter chain of its own, so this file remains the only place either
 * authentication path is decided.
 *
 * <p>The {@code @Order(1)} {@code /internal/**} chain stays <strong>hand-assembled</strong>. It is not
 * a stateless <em>end-user</em> resource server: it authenticates a workload identity with a different
 * decoder (no tenant pin), converts with {@link WorkloadIdentityAuthoritiesConverter} instead of the
 * actor converter, gates on {@code hasRole("INTERNAL")} rather than on public-vs-authenticated paths,
 * and writes different 401/403 bodies. Pushing it through the same builder would flatten exactly the
 * distinctions TASK-FAN-BE-029/030 established.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The workload scope IAM grants {@code community-service-client} for this service's
     * {@code /internal/membership/**} surface (seed migration V0009). Only a machine token issued
     * for that client carries it, which is what makes it usable as a positive discriminator: it
     * joins the machine scope family ({@code account.read}, {@code artist.read}) that IAM grants
     * only to {@code client_credentials} clients, never to a web client on a user token.
     *
     * <p>This constant is <b>policy and stays here</b>. The matching mechanism was promoted to
     * {@link WorkloadIdentityAuthoritiesConverter} (TASK-MONO-521); the scope was not, so a change
     * to what opens this service's internal surface cannot silently change a sibling's.
     */
    public static final String REQUIRED_WORKLOAD_SCOPE = "membership.read";

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
                                .jwtAuthenticationConverter(
                                        new WorkloadIdentityAuthoritiesConverter(REQUIRED_WORKLOAD_SCOPE)))
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
        // ADR-MONO-058 § D4 — only THIS chain is assembled by the shared builder.
        //
        // The @Order(2) above is unchanged and is load-bearing: this chain declares no
        // securityMatcher, so it is the catch-all, and the Order(1) chain must keep winning
        // /internal/**. The builder does not touch bean ordering — it never registers a chain of its
        // own, it only assembles the one this method returns — but the ordering is asserted anyway,
        // by message rather than by status, in SecurityChainAssemblySliceTest.Ordering: the two
        // chains' 401/403 writers say different things, so a swap could not pass silently.
        //
        // The decoder IS pinned explicitly here (unlike community/artist, which declare one decoder
        // bean): this service declares two, and .jwtDecoder(...) is what keeps this chain on the
        // tenant-pinned end-user one.
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
