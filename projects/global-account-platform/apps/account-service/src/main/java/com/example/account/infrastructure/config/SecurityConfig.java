package com.example.account.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.io.IOException;
import java.util.Arrays;

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
     * TASK-BE-317: decoder for GAP-issued {@code client_credentials} access tokens presented on
     * {@code /internal/**}. Validates signature against the GAP JWKS plus the standard timestamp
     * checks and the GAP issuer. Built from the JWKS URI directly (not the issuer's OIDC discovery
     * document) so application startup is not coupled to auth-service availability — the JWKS is
     * fetched lazily on first verification. account-service is multi-tenant, so {@code tenant_id}
     * is intentionally NOT pinned here (unlike community/membership resource servers).
     */
    @Bean
    public JwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtIssuer));
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           InternalApiFilter internalApiFilter,
                                           JwtDecoder internalJwtDecoder) throws Exception {
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
                        // TASK-BE-319b: JWT-only — satisfied solely by a valid GAP client_credentials
                        // JWT (oauth2ResourceServer below); the static X-Internal-Token path was removed.
                        .requestMatchers("/internal/**").authenticated()
                        .requestMatchers("/api/**").permitAll()
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
