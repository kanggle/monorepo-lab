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
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
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

    /**
     * TASK-MONO-750 (ADR-MONO-079 ACCEPTED — A, D4-A · ADR-MONO-059 § 부분 개정): the role a
     * <b>platform operator</b> carries after assuming {@code fan-platform} — derived at
     * assume-tenant from that tenant's {@code fan} subscription (iam {@code OperatorRoleDerivation}).
     */
    static final String ASSUME_TENANT_OPERATOR_ROLE = "FAN_OPERATOR";

    /**
     * The end-user rule: authenticated, <b>and not</b> an assume-tenant operator.
     *
     * <h2>Why this service refuses the operator, and why the refusal has to live here</h2>
     *
     * ADR-MONO-079 D4-A opened ONE fan surface to the platform operator — artist-service's
     * directory (artists, groups, fandoms, agencies). Everything else stays closed, and this
     * service is in the "everything else": the notification inbox. The operator's token carries
     * {@code tenant_id=fan-platform}, so the tenant gate admits it by plain equality — fan's
     * refusal of {@code entitled_domains} does not touch it. Without this rule the token would be
     * an ordinary authenticated caller on every path here; in community it would also satisfy
     * {@code ActorContext.isOperator()}, which is exactly the side door ADR-MONO-059 excluded
     * (option B: an operator publishing {@code ARTIST_POST} and owning every author's content).
     *
     * <p>So the refusal is a role rule on the chain, evaluated before any handler: a request whose
     * token carries {@code FAN_OPERATOR} is 403 here on every end-user path. Composed from Spring
     * Security's own managers rather than written as a lambda, so the decision for an anonymous
     * caller is unchanged — {@code authenticated()} still denies it first and the entry point
     * still answers 401.
     */
    static final AuthorizationManager<RequestAuthorizationContext> END_USER_NOT_OPERATOR =
            AuthorizationManagers.<RequestAuthorizationContext>allOf(
                    AuthenticatedAuthorizationManager.<RequestAuthorizationContext>authenticated(),
                    AuthorizationManagers.<RequestAuthorizationContext>not(
                            AuthorityAuthorizationManager.<RequestAuthorizationContext>hasRole(
                                    ASSUME_TENANT_OPERATOR_ROLE)));

    @Bean
    public SecurityFilterChain endUserFilterChain(HttpSecurity http,
                                                  JwtDecoder endUserJwtDecoder) throws Exception {
        return ResourceServerChainAssembler.statelessJwtChain(http)
                .publicPaths(PublicPaths.AS_SET)
                // TASK-MONO-750: was .authenticated("/api/fan/**") — same paths, plus the
                // operator refusal (END_USER_NOT_OPERATOR above).
                .authorizeRules(auth -> auth
                        .requestMatchers("/api/fan/**").access(END_USER_NOT_OPERATOR))
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
