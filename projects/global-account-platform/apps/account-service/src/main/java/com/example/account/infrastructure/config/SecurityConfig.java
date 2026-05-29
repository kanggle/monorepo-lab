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

    @Value("${internal.api.token:}")
    private String internalApiToken;

    /**
     * When {@code true}, the {@link InternalApiFilter} authenticates {@code /internal/**}
     * requests without checking the token (the token is unconfigured). Intended for
     * {@code @WebMvcTest} slice tests. Production must keep this {@code false} (default) so that an
     * unconfigured token produces a fail-closed 401 (only a valid GAP JWT would then pass).
     */
    @Value("${internal.api.bypass-when-unconfigured:false}")
    private boolean bypassProperty;

    // TASK-BE-317: GAP JWKS + issuer for verifying client_credentials JWTs on /internal/**.
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
        boolean testProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("test");
        boolean bypass = bypassProperty || testProfileActive;
        return new InternalApiFilter(internalApiToken, bypass);
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
                // Runs before the bearer-token filter: a valid X-Internal-Token authenticates the
                // request; otherwise it defers to the JWT path / the .authenticated() gate below.
                .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/accounts/signup").permitAll()
                        // TASK-BE-114: token in body is the auth — no JWT required.
                        .requestMatchers("/api/accounts/signup/verify-email").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // TASK-BE-317: dual-allow — satisfied by X-Internal-Token (InternalApiFilter)
                        // OR a valid GAP client_credentials JWT (oauth2ResourceServer below).
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
     * ({@code {"code":"UNAUTHORIZED","message":...}}) when neither a valid X-Internal-Token nor a
     * valid GAP JWT is presented.
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
