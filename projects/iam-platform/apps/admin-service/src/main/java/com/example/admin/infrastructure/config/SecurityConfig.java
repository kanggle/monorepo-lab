package com.example.admin.infrastructure.config;

import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.security.BootstrapAuthenticationFilter;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.OperatorAuthenticationFilter;
import com.example.security.jwt.JwtVerifier;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.Arrays;

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
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(internalJwtIssuer));
        return decoder;
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
                                "/api/admin/auth/refresh").permitAll()
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
