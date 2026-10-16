package com.example.account.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import com.example.web.security.RequiredScopeValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * When {@code true}, the {@link InternalApiFilter} authenticates {@code /internal/**}
     * requests without a JWT (dev/test bypass). Intended for {@code @WebMvcTest} slice tests and
     * standalone/local runs. Production must keep this {@code false} (default) so that {@code /internal/**}
     * is fail-closed — only a valid GAP client_credentials JWT passes (TASK-BE-319b).
     */
    @Value("${internal.api.bypass-when-unconfigured:false}")
    private boolean bypassProperty;

    // TASK-BE-317/319b: GAP JWKS + issuer for verifying client_credentials JWTs on /internal/** (sole auth path).
    @Value("${internal.api.jwt.jwk-set-uri:http://localhost:8081/oauth2/jwks}")
    private String jwkSetUri;

    @Value("${internal.api.jwt.issuer:http://localhost:8081}")
    private String jwtIssuer;

    /**
     * TASK-BE-514: the workload scope a {@code /internal/**} token must carry. The IAM SAS is a shared
     * issuer that mints both system ({@code client_credentials}) and user ({@code authorization_code})
     * tokens, so signature + issuer alone do not distinguish a system credential — this scope
     * ({@code internal.invoke}, seeded to the workload clients in {@code V0019}) is the discriminator.
     * Blank → {@link RequiredScopeValidator} fails closed (rejects all).
     */
    @Value("${internal.api.jwt.required-scope:internal.invoke}")
    private String requiredScope;

    /**
     * TASK-BE-640: client ids ({@code sub}) allowed on the tenant lifecycle WRITES and LIST
     * ({@code POST}/{@code GET /internal/tenants}, {@code PATCH /internal/tenants/{tenantId}}).
     * Comma-separated. No code default on purpose: absent or blank → empty → every JWT caller is refused
     * (fail-closed). {@code application.yml} supplies the value admin-service sends.
     */
    @Value("${internal.api.tenant-lifecycle.write-callers:}")
    private String tenantLifecycleWriteCallers;

    /**
     * TASK-BE-640: client ids ({@code sub}) allowed on the single-tenant READ
     * ({@code GET}/{@code HEAD /internal/tenants/{tenantId}}). Same fail-closed rule as above;
     * {@code application.yml} supplies admin-service's and auth-service's values.
     */
    @Value("${internal.api.tenant-lifecycle.read-callers:}")
    private String tenantLifecycleReadCallers;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /** The dev/test bypass: the explicit property, or a {@code test}/{@code standalone} profile. */
    boolean bypassActive() {
        java.util.List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean bypassProfileActive = profiles.contains("test") || profiles.contains("standalone");
        return bypassProperty || bypassProfileActive;
    }

    @Bean
    public InternalApiFilter internalApiFilter() {
        return new InternalApiFilter(bypassActive());
    }

    private TenantLifecycleCallerPolicy tenantLifecyclePolicy(String key, String csv) {
        TenantLifecycleCallerPolicy policy = TenantLifecycleCallerPolicy.fromCsv(csv, bypassActive());
        if (policy.allowedClientIds().isEmpty()) {
            log.error("internal.api.tenant-lifecycle.{} is empty — every JWT caller of that part of the tenant "
                    + "lifecycle API will be refused with 403 (fail-closed, TASK-BE-640)", key);
        } else {
            log.info("Tenant lifecycle {} allowed for client ids {} (TASK-BE-640)", key, policy.allowedClientIds());
        }
        return policy;
    }

    /**
     * TASK-BE-317: decoder for GAP-issued {@code client_credentials} access tokens presented on
     * {@code /internal/**}. Validates signature against the GAP JWKS plus the standard timestamp
     * checks and the GAP issuer. Built from the JWKS URI directly (not the issuer's OIDC discovery
     * document) so application startup is not coupled to auth-service availability — the JWKS is
     * fetched lazily on first verification. account-service is multi-tenant, so {@code tenant_id}
     * is intentionally NOT pinned here (unlike community/membership resource servers).
     *
     * <p>TASK-BE-514: additionally pins the {@code internal.invoke} workload scope via
     * {@link #internalTokenValidator()} — signature + issuer alone do not distinguish a system
     * credential from a user token on the shared IAM issuer (see {@link RequiredScopeValidator}).
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(internalTokenValidator());
        return decoder;
    }

    /**
     * The validator chain enforced on {@code /internal/**} tokens: the issuer/timestamp default plus
     * the {@code internal.invoke} scope discriminator (TASK-BE-514). Package-private so a test can
     * assert the <em>actual</em> chain the decoder uses (not a re-implemented copy) — a scope-less
     * token must be rejected here.
     */
    OAuth2TokenValidator<Jwt> internalTokenValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(jwtIssuer));
        validators.add(new RequiredScopeValidator(requiredScope));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           InternalApiFilter internalApiFilter,
                                           JwtDecoder internalJwtDecoder) throws Exception {
        TenantLifecycleCallerPolicy lifecycleWriters =
                tenantLifecyclePolicy("write-callers", tenantLifecycleWriteCallers);
        TenantLifecycleCallerPolicy lifecycleReaders =
                tenantLifecyclePolicy("read-callers", tenantLifecycleReadCallers);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Non-terminal: under the dev/test/standalone bypass it authenticates /internal/**;
                // otherwise it defers entirely to the JWT path / the .authenticated() gate below.
                .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/accounts/signup").permitAll()
                        // TASK-BE-114: token in body is the auth — no JWT required.
                        .requestMatchers("/api/accounts/signup/verify-email").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // TASK-BE-640: the tenant LIFECYCLE API admits only its named callers (token sub).
                        // Exactly one segment after /internal/tenants — the tenant-scoped sub-resources
                        // (/internal/tenants/{id}/accounts …) are not matched and keep the rule below.
                        // Read first (GET/HEAD of one row: admin + auth), then every other method on the
                        // two lifecycle paths (writes + list: admin only). Not authenticated → the
                        // entry point's 401; authenticated but not named → 403 FORBIDDEN.
                        .requestMatchers(HttpMethod.GET, "/internal/tenants/*").access(lifecycleReaders)
                        .requestMatchers(HttpMethod.HEAD, "/internal/tenants/*").access(lifecycleReaders)
                        .requestMatchers("/internal/tenants", "/internal/tenants/*").access(lifecycleWriters)
                        // TASK-BE-319b: JWT-only — satisfied solely by a valid GAP client_credentials
                        // JWT (oauth2ResourceServer below); the static X-Internal-Token path was removed.
                        // TASK-BE-514: the decoder additionally requires the internal.invoke scope, so
                        // .authenticated() here means "a system workload credential", not merely any
                        // valid-issuer token (the IAM issuer is shared with user tokens).
                        .requestMatchers("/internal/**").authenticated()
                        .requestMatchers("/api/**").permitAll()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler(SecurityConfig::onAccessDenied))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(internalJwtDecoder))
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(SecurityConfig::onAccessDenied)
                );

        return http.build();
    }

    /**
     * TASK-BE-640: an authenticated caller the chain refuses (today: a workload not named on the tenant
     * lifecycle allow-list) → {@code 403 FORBIDDEN} in the platform error envelope
     * ({@code platform/error-handling.md}: «Authenticated caller lacks the required role/permission»).
     * Replaces the resource server's default (an empty 403 with a {@code WWW-Authenticate} header) so the
     * caller can read the code.
     */
    static void onAccessDenied(HttpServletRequest request,
                               HttpServletResponse response,
                               AccessDeniedException e) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"FORBIDDEN\",\"message\":\"The caller is not allowed on this internal resource\"}");
    }

    /**
     * Preserves the legacy {@code /internal/**} 401 contract
     * ({@code {"code":"UNAUTHORIZED","message":...}}) when no valid GAP client_credentials JWT is
     * presented (TASK-BE-319b — the static X-Internal-Token path was removed).
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
