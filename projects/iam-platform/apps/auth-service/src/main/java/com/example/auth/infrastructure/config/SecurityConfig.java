package com.example.auth.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.Arrays;

/**
 * Default security filter chain for the existing auth-service endpoints.
 *
 * <p>{@code @Order(2)} — runs after the SAS filter chain ({@code @Order(1)} in
 * {@link com.example.auth.infrastructure.oauth2.AuthorizationServerConfig}).
 * The SAS chain covers {@code /oauth2/**} and {@code /.well-known/**};
 * this chain covers all legacy {@code /api/auth/**} and {@code /internal/**} endpoints.
 *
 * <p>TASK-BE-251: SAS filter chain added.
 *
 * <p><b>TASK-BE-487 (ADR-005 단계 4 — auth-service receiver):</b> the credential/action
 * {@code /internal/auth/**} endpoints (served by {@code InternalCredentialController}) flip from
 * {@code permitAll()} to a GAP {@code client_credentials} JWT requirement — closing the last
 * unprotected internal boundary in the IAM workload-identity migration. Mirrors the account-service
 * BE-319b blueprint: an {@code oauth2ResourceServer(jwt)} on this chain plus a {@code .authenticated()}
 * gate, decoded by {@link #internalJwtDecoder()} (self-JWKS, issuer-pinned). The
 * {@link InternalApiFilter} keeps the {@code 'test'}/{@code 'standalone'} bypass so slice tests and
 * local runs need no real JWT; production is fail-closed (401).
 *
 * <p><b>{@code /internal/auth/jwks} stays {@code permitAll}</b> — it is the public key-distribution
 * endpoint the gateway ({@code JwksClient}) fetches to <em>validate</em> tokens, so it cannot itself
 * be gated behind a bearer token (chicken-and-egg). The more-specific matcher is ordered first.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * When {@code true}, the {@link InternalApiFilter} authenticates {@code /internal/**} requests
     * without a JWT (dev/test bypass). Intended for {@code @WebMvcTest} slice tests and
     * standalone/local runs. Production must keep this {@code false} (default) so that the
     * credential/action {@code /internal/auth/**} endpoints are fail-closed — only a valid GAP
     * {@code client_credentials} JWT passes (TASK-BE-487).
     */
    @Value("${internal.api.bypass-when-unconfigured:false}")
    private boolean bypassProperty;

    // TASK-BE-487: GAP JWKS + issuer for verifying client_credentials JWTs on /internal/auth/** .
    // auth-service IS the GAP SAS, so this validates tokens it issued itself; withJwkSetUri (not OIDC
    // discovery) keeps startup decoupled from the SAS being ready — the JWKS is fetched lazily.
    @Value("${internal.api.jwt.jwk-set-uri:http://localhost:8081/oauth2/jwks}")
    private String jwkSetUri;

    @Value("${internal.api.jwt.issuer:http://localhost:8081}")
    private String jwtIssuer;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public InternalApiFilter internalApiFilter() {
        java.util.List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean bypassProfileActive = profiles.contains("test") || profiles.contains("standalone");
        boolean bypass = bypassProperty || bypassProfileActive;
        return new InternalApiFilter(bypass);
    }

    /**
     * TASK-BE-487: decoder for GAP-issued {@code client_credentials} access tokens presented on
     * {@code /internal/auth/**}. Validates signature against the GAP JWKS plus the standard timestamp
     * checks and the GAP issuer. Built from the JWKS URI directly (not the issuer's OIDC discovery
     * document) so application startup is not coupled to the SAS being ready — the JWKS is fetched
     * lazily on first verification.
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtIssuer));
        return decoder;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           InternalApiFilter internalApiFilter,
                                           @Qualifier("internalJwtDecoder") JwtDecoder internalJwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Non-terminal: under the dev/test/standalone bypass it authenticates /internal/**;
                // otherwise it defers entirely to the JWT path / the .authenticated() gate below.
                .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/oauth/**").permitAll()
                        // Password change endpoint — gateway enforces JWT and forwards
                        // X-Account-Id (see PasswordController, auth-api.md PATCH /api/auth/password).
                        .requestMatchers("/api/auth/password").permitAll()
                        .requestMatchers("/api/auth/password-reset/**").permitAll()
                        // Session management endpoints — gateway enforces JWT and forwards
                        // X-Account-Id / X-Device-Id headers (see AccountSessionController).
                        .requestMatchers("/api/accounts/me/sessions/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // TASK-BE-487: public key distribution — the gateway fetches these keys to
                        // VALIDATE tokens and therefore cannot present one. Ordered before the
                        // /internal/** gate below (first match wins).
                        .requestMatchers("/internal/auth/jwks").permitAll()
                        // TASK-BE-487: credential/action /internal/auth/** — JWT-only, satisfied solely
                        // by a valid GAP client_credentials JWT (oauth2ResourceServer below). The static
                        // permitAll is removed (the last unprotected internal boundary in ADR-005).
                        .requestMatchers("/internal/**").authenticated()
                        // TASK-BE-311 — Spring Security's
                        // DefaultLoginPageGeneratingFilter (registered by
                        // WebLoginSecurityConfig's formLogin block) emits HTML
                        // that references `default-ui.css` as a same-origin
                        // subresource. That URL falls outside chain[0]'s
                        // /login + /logout securityMatcher, so it lands on
                        // this chain. Without an explicit permit, the
                        // `.anyRequest().denyAll()` below rejects it with
                        // 403 — the form renders without styling AND the
                        // 403 surfaces in browser dev tools / Playwright
                        // trace as a confusing red herring.
                        .requestMatchers("/default-ui.css").permitAll()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(internalJwtDecoder))
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                );

        return http.build();
    }

    /**
     * Preserves the legacy {@code /internal/**} 401 contract
     * ({@code {"code":"UNAUTHORIZED","message":...}}) when no valid GAP client_credentials JWT is
     * presented (TASK-BE-487 — mirrors the account-service BE-319b entry point).
     */
    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException e) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal credentials\"}");
    }

    /**
     * Registers {@link DeprecatedApiHeaderFilter} as a servlet filter so that RFC 8594
     * {@code Deprecation} and RFC 9745 {@code Sunset} headers are injected on every
     * response to {@code POST /api/auth/login} — including error responses handled by
     * the exception handler.
     *
     * <p>A servlet filter is used (rather than setting headers inside the controller
     * method) because Spring MVC exception handlers can replace the response object,
     * which discards headers set earlier in the controller. Wrapping at the servlet
     * layer avoids this issue.
     */
    @Bean
    public FilterRegistrationBean<DeprecatedApiHeaderFilter> deprecatedApiHeaderFilter() {
        FilterRegistrationBean<DeprecatedApiHeaderFilter> registration =
                new FilterRegistrationBean<>(new DeprecatedApiHeaderFilter());
        registration.addUrlPatterns("/api/auth/login");
        registration.setOrder(Integer.MIN_VALUE); // run before Spring Security
        return registration;
    }
}
