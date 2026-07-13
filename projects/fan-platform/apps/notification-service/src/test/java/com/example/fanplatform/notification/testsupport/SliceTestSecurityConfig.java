package com.example.fanplatform.notification.testsupport;

import com.example.fanplatform.notification.infrastructure.security.ServiceLevelOAuth2Config;
import com.example.security.servlet.TenantClaimEnforcer;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.fanplatform.notification.infrastructure.security.ActorContextJwtAuthenticationConverter;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.fanplatform.notification.infrastructure.security.SecurityConfig;
import com.example.security.oauth2.TenantClaimValidator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Slice-test security wiring mirroring the production {@link SecurityConfig}
 * end-user chain, but with a {@link NimbusJwtDecoder} keyed off the local
 * {@link JwtTestHelper} keypair. The 401/403 handlers are reused from production
 * {@link SecurityConfig}.
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


    /**
     * The tenant filter, taken from the <strong>production</strong> config rather than rebuilt
     * here.
     *
     * <p>The filter used to arrive as {@code @Import(TenantClaimEnforcer.class)} — it was a
     * {@code @Component} in this service's own tree. It is a shared class now (ADR-MONO-049
     * § D5-6) with a private constructor, so it has to come from a bean method. It comes from
     * <em>that</em> bean method, and not a copy of it, deliberately: the three switches are
     * fan's tenant policy, and a slice test that re-stated them would keep passing while the
     * real gate changed underneath it.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        ServiceLevelOAuth2Config production = new ServiceLevelOAuth2Config();
        ReflectionTestUtils.setField(
                production, "requiredTenantId", JwtTestHelper.DEFAULT_TENANT_ID);
        return production.tenantClaimEnforcer();
    }
    @Bean
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
