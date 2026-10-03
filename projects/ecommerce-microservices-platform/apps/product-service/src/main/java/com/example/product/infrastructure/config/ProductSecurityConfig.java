package com.example.product.infrastructure.config;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.web.security.RequiredScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.List;

/**
 * product-service security wiring — TASK-MONO-759 (ADR-MONO-079 D2; owner decisions 2026-10-03:
 * 갈래 A + reach path R1). <b>This is the service's first JWT-validating surface.</b>
 *
 * <p>Copied from the same project's {@code OrderSecurityConfig} (TASK-BE-412), which solved the
 * same problem first: a gateway-fronted, header-trusting service that grows one surface which
 * must validate a {@code client_credentials} token itself. Two chains:
 *
 * <ol>
 *   <li><b>{@code /internal/**} — resource server, fail-closed.</b> The token must verify
 *       (JWKS signature, {@code exp}/{@code nbf}/{@code iat}, issuer) <b>and</b> carry the
 *       machine-only scope {@value #REQUIRED_SCOPE} <b>and</b> {@code tenant_id ==}
 *       {@value #REQUIRED_TENANT}. All three are checked <em>in the decoder</em>, not in a
 *       filter — {@code platform/security-rules.md} § "A verified token proves authentication,
 *       not authorization": "Enforce it where the token is validated". Any failure → 401.
 *       Only {@code GET /internal/sellers/*} is admitted; every other method or path under
 *       {@code /internal/**} → 403, so the read credential cannot reach a write.</li>
 *   <li><b>everything else — permit all, header trust unchanged.</b> The gateway strips and
 *       re-injects {@code X-User-*} / {@code X-Tenant-Id}; the controllers keep gating on them
 *       exactly as before this class existed. This chain only turns off the framework
 *       defaults (CSRF, form login, HTTP basic) that adding Spring Security would otherwise
 *       switch on.</li>
 * </ol>
 *
 * <h2>Why the scope AND the tenant, when the gateway already checked both</h2>
 *
 * The ecommerce gateway admits {@code /internal/sellers/**} on the same two facts
 * ({@code AccountTypeEnforcementFilter}). It is re-checked here because product-service is
 * reachable on {@code ecommerce-net} without the gateway, and because the issuer also mints
 * end-user tokens: signature + issuer alone would admit any shopper's token
 * ({@code security-rules.md}). The tenant pin means a token assumed for another tenant
 * cannot read this tenant's sellers even if a future catalog change let some client assume it.
 *
 * <h2>Why a required scope and not a subject allow-list</h2>
 *
 * {@code security-rules.md} asks for exactly one machine-only discriminator. The scope is the
 * one the IdP already scopes to this caller ({@code V0042} grants {@code store.seller.read} to
 * {@code artist-service-client} alone), and it is the same fact the gateway reads — one
 * discriminator on both layers instead of two that could drift. No audience allowlist here:
 * {@code jwt-standard-claims.md} § JWT Validation rule 5, <i>Behind the edge</i>.
 */
@Configuration
@EnableWebSecurity
public class ProductSecurityConfig {

    /** The machine-only scope that opens {@code /internal/**} (IdP V0042). Policy, not config. */
    public static final String REQUIRED_SCOPE = "store.seller.read";

    /** The store tenant the seller read is scoped to (ADR-MONO-079 D2). Policy, not config. */
    public static final String REQUIRED_TENANT = "ecommerce";

    @Value("${product.internal.oauth2.jwk-set-uri:http://localhost:8081/oauth2/jwks}")
    private String jwkSetUri;

    @Value("${product.internal.oauth2.issuer:http://localhost:8081}")
    private String issuer;

    /**
     * Decoder for {@code /internal/**}. Built from the JWKS URI directly (lazy fetch — startup
     * is not coupled to auth-service availability), like the order-service sibling.
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(internalTokenValidator(issuer));
        return decoder;
    }

    /**
     * The validator chain, exposed so a test can run it against a signed token without a JWKS
     * server. Timestamps + issuer, then the machine discriminator, then the tenant pin —
     * {@link TenantClaimValidator#forTenant} with no relaxation: no {@code *} wildcard, no
     * entitlement trust. A workload token for this surface is assumed for exactly one tenant.
     */
    public static OAuth2TokenValidator<Jwt> internalTokenValidator(String issuer) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                JwtValidators.createDefaultWithIssuer(issuer),
                new RequiredScopeValidator(REQUIRED_SCOPE),
                TenantClaimValidator.forTenant(REQUIRED_TENANT).build()));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http,
                                                   JwtDecoder internalJwtDecoder) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The one read this credential exists for. Nothing else under /internal/**
                        // — in particular no write — is reachable with it (TASK-MONO-759 AC-3).
                        .requestMatchers(HttpMethod.GET, "/internal/sellers/*").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(internalJwtDecoder))
                        .authenticationEntryPoint(ProductSecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(ProductSecurityConfig::onAccessDenied))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(ProductSecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(ProductSecurityConfig::onAccessDenied));
        return http.build();
    }

    /**
     * Every other route: product-service trusts the gateway-injected headers and its
     * controllers gate on them, exactly as before Spring Security was on the classpath.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    static void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException e) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Missing or invalid internal credentials");
    }

    static void onAccessDenied(HttpServletRequest request,
                               HttpServletResponse response,
                               AccessDeniedException e) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "This internal credential does not open this operation");
    }

    private static void write(HttpServletResponse response, HttpStatus status,
                              String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
