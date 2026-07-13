package com.example.fanplatform.membership.testsupport;

import com.example.fanplatform.membership.infrastructure.security.ActorContextJwtAuthenticationConverter;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.fanplatform.membership.infrastructure.security.SecurityConfig;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.fanplatform.membership.infrastructure.security.WorkloadIdentityAuthoritiesConverter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Slice-test security wiring mirroring the production {@link SecurityConfig} two
 * ordered chains (internal workload-identity + end-user), but with a
 * {@link NimbusJwtDecoder} keyed off the local {@link JwtTestHelper} keypair. The
 * 401/403 handlers are reused from production {@link SecurityConfig}.
 */
@TestConfiguration
@EnableWebSecurity
public class SliceTestSecurityConfig {

    private static JwtTestHelper fixture;

    public static void useFixture(JwtTestHelper f) {
        fixture = f;
    }

    public static JwtTestHelper fixture() {
        if (fixture == null) {
            fixture = new JwtTestHelper();
        }
        return fixture;
    }

    private static RSAPublicKey publicKey() {
        try {
            RSAKey jwk = (RSAKey) com.nimbusds.jose.jwk.JWKSet.parse(fixture().jwksJson()).getKeys().get(0);
            return jwk.toRSAPublicKey();
        } catch (java.text.ParseException | JOSEException e) {
            throw new IllegalStateException("Failed to build slice-test public key", e);
        }
    }

    @Bean
    public NimbusJwtDecoder endUserJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new AllowedIssuersValidator(List.of(JwtTestHelper.SAS_ISSUER, JwtTestHelper.LEGACY_ISSUER)),
                TenantClaimValidator.forTenant(JwtTestHelper.DEFAULT_TENANT_ID)
                        .allowSuperAdminWildcard()
                        .build());
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public NimbusJwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTestHelper.SAS_ISSUER));
        return decoder;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt
                                .decoder(internalJwtDecoder())
                                .jwtAuthenticationConverter(new WorkloadIdentityAuthoritiesConverter()))
                        .authenticationEntryPoint(SecurityConfig::onInternalAuthFailure)
                        .accessDeniedHandler(SecurityConfig::onInternalAccessDenied));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain endUserFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/fan/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt
                                .decoder(endUserJwtDecoder())
                                .jwtAuthenticationConverter(new ActorContextJwtAuthenticationConverter()))
                        .authenticationEntryPoint(SecurityConfig::onAuthenticationFailure)
                        .accessDeniedHandler(SecurityConfig::onAccessDenied));
        return http.build();
    }
}
