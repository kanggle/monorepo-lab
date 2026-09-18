package com.example.admin.infrastructure.config;

import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.security.BootstrapAuthenticationFilter;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.OperatorAuthenticationFilter;
import com.example.security.jwt.JwtVerifier;
import com.example.web.security.RequiredScopeValidator;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin-service security configuration.
 *
 * <p>Authorization is enforced exclusively by {@code RequiresPermissionAspect}
 * (see rbac.md "Permission Evaluation Algorithm"). Spring Security handles only
 * authentication (JWT verification via {@link OperatorAuthenticationFilter})
 * and the final fallthrough denyAll. {@code @EnableMethodSecurity} is
 * intentionally NOT present: all authorization decisions flow through the
 * single aspect path (one decision site, one audit write).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * TASK-BE-327: when {@code true}, {@link InternalApiFilter} authenticates
     * {@code /internal/**} without a JWT (dev/test bypass — slice tests +
     * standalone runs). Production keeps this {@code false} so {@code /internal/**}
     * is fail-closed (only a valid GAP client_credentials JWT passes). Mirrors
     * account-service {@code SecurityConfig}.
     */
    @Value("${internal.api.bypass-when-unconfigured:false}")
    private boolean internalBypassProperty;

    /** GAP JWKS + issuer for verifying client_credentials JWTs on {@code /internal/**}. */
    @Value("${internal.api.jwt.jwk-set-uri:http://localhost:8081/oauth2/jwks}")
    private String internalJwkSetUri;

    @Value("${internal.api.jwt.issuer:http://localhost:8081}")
    private String internalJwtIssuer;

    /**
     * The workload scope that admits a token to {@code /internal/**} (TASK-MONO-716).
     *
     * <p>Same value, same property name, same class as the three sibling {@code /internal/**}
     * decoders (account / auth / security — TASK-MONO-422, TASK-BE-514). The sameness is the
     * point: when four chains discriminate on one axis, watching that one axis covers all four,
     * and a fourth chain with a private axis has to be re-read every time someone asks what stops
     * a user token here.
     */
    @Value("${internal.api.jwt.required-scope:internal.invoke}")
    private String internalRequiredScope;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public OperatorAuthenticationFilter operatorAuthenticationFilter(
            JwtVerifier operatorJwtVerifier,
            @Value("${admin.jwt.expected-token-type:admin}") String expectedTokenType,
            TokenBlacklistPort tokenBlacklist) {
        return new OperatorAuthenticationFilter(operatorJwtVerifier, expectedTokenType, tokenBlacklist);
    }

    @Bean
    public BootstrapAuthenticationFilter bootstrapAuthenticationFilter(
            BootstrapTokenService bootstrapTokenService) {
        return new BootstrapAuthenticationFilter(bootstrapTokenService);
    }

    /**
     * TASK-BE-327: non-terminal dev/test bypass for the {@code /internal/**}
     * chain — mirrors account-service. Active under the {@code 'test'} /
     * {@code 'standalone'} profile or the explicit property; production keeps it
     * off (fail-closed JWT path).
     */
    @Bean
    public InternalApiFilter internalApiFilter() {
        java.util.List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean bypassProfileActive = profiles.contains("test") || profiles.contains("standalone");
        boolean bypass = internalBypassProperty || bypassProfileActive;
        return new InternalApiFilter(bypass);
    }

    /**
     * TASK-BE-327: decoder for GAP-issued {@code client_credentials} access
     * tokens presented on {@code /internal/**}. Validates RS256 signature against
     * the GAP JWKS + the standard timestamp checks + the GAP issuer. Built from
     * the JWKS URI directly (lazy fetch) so startup is not coupled to
     * auth-service availability — mirrors account-service
     * {@code internalJwtDecoder}. {@code tenant_id} is intentionally NOT pinned
     * (the caller token is a client_credentials workload token, not tenant-bound).
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(internalJwkSetUri).build();
        decoder.setJwtValidator(internalTokenValidator());
        return decoder;
    }

    /**
     * The validator chain enforced on {@code /internal/**} tokens: the issuer/timestamp default
     * plus the {@code internal.invoke} workload discriminator (TASK-MONO-716).
     *
     * <p><b>Why the scope gate is not optional here.</b> Until MONO-716 this chain pinned the
     * issuer and nothing else, and the chain's authorization gate is a bare
     * {@code .requestMatchers("/internal/**").authenticated()}. The IAM issuer is <em>shared</em> —
     * it mints operator browser access tokens from the same key as workload tokens — so
     * "authenticated" admitted any token the IdP had ever issued, including an operator's own
     * session token. Nothing else covered it: {@code @EnableMethodSecurity} is deliberately absent
     * from this service, neither {@code /internal/**} controller carries
     * {@code @RequiresPermission} (this service's only authorization path), and
     * {@code InternalApiFilter} is non-terminal and never rejects. This method is the
     * discriminator that was missing — the same one the three sibling services already use.
     *
     * <p>Package-private on purpose, copying the sibling shape: a test asserts the <em>actual</em>
     * chain this class composes rather than a re-implementation of it. That matters more here than
     * usual, because the {@code /internal/**} integration tests run under a profile where
     * {@code InternalApiFilter} authenticates the request and <b>the decoder is never reached</b> —
     * a green integration suite is not evidence that this gate works.
     *
     * <p>Rule 5 <i>Behind the edge</i> of {@code platform/contracts/jwt-standard-claims.md} is the
     * contract this satisfies: a chain behind an edge does not need an audience allowlist, but it
     * may not have <b>no</b> discriminator at all.
     */
    OAuth2TokenValidator<Jwt> internalTokenValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(internalJwtIssuer));
        validators.add(new RequiredScopeValidator(internalRequiredScope));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2): the {@code /internal/**}
     * resource-server chain. {@code @Order(0)} so it is consulted before the
     * operator {@code /api/admin/**} chain ({@code @Order(2)}) and matches only
     * {@code /internal/**} via {@code securityMatcher}. Mirrors account-service
     * {@code SecurityConfig.filterChain} — GAP client_credentials JWT only, with a
     * non-terminal dev/test bypass; production fail-closed 401 {@code UNAUTHORIZED}.
     *
     * <p>The operator chain is left byte-unchanged (it already does NOT match
     * {@code /internal/**}); the only edit there is the explicit {@code @Order(2)}.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http,
                                                      InternalApiFilter internalApiFilter,
                                                      JwtDecoder internalJwtDecoder) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Non-terminal: under the dev/test/standalone bypass it authenticates
                // /internal/**; otherwise it defers entirely to the JWT path / the
                // .authenticated() gate below.
                .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(internalJwtDecoder))
                        .authenticationEntryPoint(SecurityConfig::onInternalAuthenticationFailure)
                );
        return http.build();
    }

    /**
     * Preserves the {@code /internal/**} 401 contract
     * ({@code {"code":"UNAUTHORIZED",...}}) when no valid GAP client_credentials
     * JWT is presented (mirrors account-service).
     */
    static void onInternalAuthenticationFailure(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.AuthenticationException e)
            throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid internal credentials\"}");
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OperatorAuthenticationFilter operatorFilter,
                                           BootstrapAuthenticationFilter bootstrapFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bootstrap filter runs first — it only matches the 2FA
                // enroll/verify sub-tree and is a no-op on every other path.
                .addFilterBefore(bootstrapFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(operatorFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // Unauthenticated sub-tree (admin-api.md Authentication Exceptions).
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/.well-known/admin/jwks.json").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/admin/auth/login",
                                // TASK-BE-298 / ADR-MONO-014: token-exchange
                                // runs without an operator JWT (GAP OIDC
                                // subject token in body). OperatorAuthentication
                                // Filter is NOT widened — it only skips this
                                // path; TokenExchangeService validates the GAP
                                // subject token separately and fail-closed.
                                "/api/admin/auth/token-exchange",
                                "/api/admin/auth/2fa/enroll",
                                "/api/admin/auth/2fa/verify",
                                // TASK-BE-040: refresh runs without operator JWT.
                                "/api/admin/auth/refresh",
                                // TASK-BE-474 / ADR-MONO-044: self-service tenant onboarding.
                                // The caller is an ordinary authenticated IAM user (NOT an
                                // operator) — the OIDC subject token is in the body and validated
                                // by OnboardingController (IamOidcSubjectTokenValidator), exactly
                                // like /auth/token-exchange. OperatorAuthenticationFilter skips it
                                // and @SelfServiceEndpoint admits it past the deny-by-default guard.
                                "/api/admin/onboarding/organizations").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, e) -> {
                            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"TOKEN_INVALID\",\"message\":\"Authentication required\""
                                            + ",\"timestamp\":\"" + Instant.now().toString() + "\"}");
                        })
                        .accessDeniedHandler((req, resp, e) -> {
                            resp.setStatus(HttpStatus.FORBIDDEN.value());
                            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            resp.getWriter().write(
                                    "{\"code\":\"PERMISSION_DENIED\",\"message\":\"Operator role insufficient\""
                                            + ",\"timestamp\":\"" + Instant.now().toString() + "\"}");
                        })
                );

        return http.build();
    }
}
