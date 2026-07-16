package com.example.order.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * order-service security wiring (TASK-BE-412).
 *
 * <p>order-service is gateway-fronted for its user/admin surface: the gateway strips and
 * re-injects {@code X-User-*} / {@code X-Tenant-Id} headers, and the service trusts them
 * (no per-request JWT on {@code /api/orders/**} or {@code /api/admin/orders/**}). That model
 * is preserved by the permissive {@link #defaultFilterChain(HttpSecurity)} below.
 *
 * <p>The single exception is the gateway-EXCLUDED internal route {@code /api/internal/**}
 * (TASK-BE-412): it is never fronted by the gateway, so it validates the inbound
 * {@code client_credentials} Bearer JWT itself as a resource server (JWKS signature +
 * {@code exp}/{@code nbf}/{@code iat} + issuer + audience + <b>system-client subject</b>),
 * <b>fail-closed</b> — a missing / expired / malformed / wrong-issuer / wrong-audience token,
 * <b>or a valid token whose {@code sub} is not an allow-listed internal client-id</b>
 * ({@link SystemClientSubjectValidator}, TASK-BE-505) → {@code 401 UNAUTHORIZED} and the sweep
 * never executes. The subject pin is required because the ecommerce issuer is shared with
 * ordinary CUSTOMER access tokens, so signature + issuer alone do not distinguish a system
 * credential from a user token. Mirrors the IAM account-service {@code /internal/**}
 * resource-server shape (TASK-BE-317/319b, product-to-account.md / BE-402 precedent): decoder
 * built directly from the JWKS URI (lazy fetch — startup is not coupled to auth-service
 * availability), issuer + audience + allowed client-ids pinned via env-overridable properties.
 *
 * <p>Two chains, ordered so the {@code /api/internal/**}-scoped resource-server chain matches
 * first and the permissive chain catches everything else.
 */
@Configuration
@EnableWebSecurity
public class OrderSecurityConfig {

    @Value("${order.internal.oauth2.jwk-set-uri:http://localhost:8081/oauth2/jwks}")
    private String jwkSetUri;

    @Value("${order.internal.oauth2.issuer:http://localhost:8081}")
    private String issuer;

    @Value("${order.internal.oauth2.audience:}")
    private String audience;

    /**
     * Comma-separated allow-list of {@code client_credentials} client-ids permitted on
     * {@code /api/internal/**}. A token's {@code sub} must be one of these (TASK-BE-505).
     * Defaults to the sole reserved internal client; overridable per environment.
     */
    @Value("${order.internal.oauth2.allowed-client-ids:ecommerce-internal-services-client}")
    private String allowedClientIds;

    /**
     * Decoder for the {@code client_credentials} access tokens presented on
     * {@code /api/internal/**}. Built from the JWKS URI directly (not OIDC discovery) so
     * startup is not coupled to auth-service availability — the JWKS is fetched lazily on
     * first verification. Validates signature plus timestamps ({@code exp}/{@code nbf}/{@code iat}),
     * issuer, and (when configured) audience.
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(JwtValidators.createDefaultWithIssuer(issuer));
        validators.add(new AudienceValidator(audience));
        // TASK-BE-505: pin the subject to the reserved internal system client(s). The
        // issuer is shared with ordinary CUSTOMER access tokens, so signature+issuer alone
        // does not distinguish a system credential — without this an ordinary token passes
        // .authenticated(), contradicting the "system credential only" contract.
        validators.add(new SystemClientSubjectValidator(parseAllowedClientIds()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** Parse the comma-separated {@code allowedClientIds} into a trimmed, non-blank set. */
    private java.util.Set<String> parseAllowedClientIds() {
        if (allowedClientIds == null || allowedClientIds.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(allowedClientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Resource-server chain scoped to {@code /api/internal/**}. Matches first (@Order(1)).
     * Fail-closed: any request here must carry a valid client_credentials JWT.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http,
                                                   JwtDecoder internalJwtDecoder) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(internalJwtDecoder))
                        .authenticationEntryPoint(OrderSecurityConfig::onAuthenticationFailure));
        return http.build();
    }

    /**
     * Permissive chain for every other route. order-service trusts the gateway-injected
     * {@code X-User-*} / {@code X-Tenant-Id} headers on {@code /api/orders/**} and
     * {@code /api/admin/orders/**}; authorization is enforced upstream at the gateway and by
     * the header-based controller logic, so this chain only disables the framework defaults
     * (CSRF / form login) and permits all — leaving the existing behavior unchanged.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Emits the {@code {"code":"UNAUTHORIZED","message":...}} 401 envelope (per
     * {@code platform/error-handling.md}) when no valid client_credentials JWT is presented
     * on {@code /api/internal/**} — fail-closed.
     */
    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException e) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal credentials\"}");
    }
}
