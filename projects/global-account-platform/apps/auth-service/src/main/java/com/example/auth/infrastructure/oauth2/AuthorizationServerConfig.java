package com.example.auth.infrastructure.oauth2;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Spring Authorization Server configuration — Phase 1 (TASK-BE-251).
 *
 * <p>Exposes the OIDC discovery endpoint, JWKS endpoint, and token endpoint
 * for the {@code client_credentials} grant. The SAS filter chain takes
 * {@code @Order(1)} and matches only the SAS-managed request matchers
 * ({@code /oauth2/**}, {@code /.well-known/**}), leaving the existing
 * security filter chain ({@code @Order(2)}) fully intact.
 *
 * <p>The {@link RegisteredClientRepository} is an in-memory placeholder.
 * // PLACEHOLDER: replaced by JpaRegisteredClientRepository in TASK-BE-252
 */
@Configuration
public class AuthorizationServerConfig {

    @Value("${oidc.issuer-url:http://localhost:8081}")
    private String issuerUrl;

    @Value("${auth.jwt.kid:key-2026-04-01}")
    private String kid;

    /**
     * SAS security filter chain — Order(1).
     * Covers: /oauth2/**, /.well-known/openid-configuration
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer ->
                        configurer.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated())
                // Redirect to login page when unauthenticated (for authorization_code flow, Phase 2)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/api/auth/login")));

        return http.build();
    }

    /**
     * In-memory registered client repository with a single test client for
     * Phase 1 {@code client_credentials} validation.
     *
     * <p>The {@code clientName} field is used by {@link TenantClaimTokenCustomizer} to carry
     * tenant metadata in the format {@code "<tenantId>|<tenantType>"}. For this placeholder
     * client the tenant is {@code fan-platform} (type {@code B2C}).
     *
     * // PLACEHOLDER: replaced by JpaRegisteredClientRepository in TASK-BE-252
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient testClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-internal-client")
                // {noop} plain-text secret for in-memory placeholder ONLY.
                // PLACEHOLDER: replaced by JpaRegisteredClientRepository in TASK-BE-252
                // (TASK-BE-252 will store BCrypt-hashed secrets from the DB)
                .clientSecret("{noop}secret")
                // clientName encodes "tenantId|tenantType" — read by TenantClaimTokenCustomizer
                .clientName("fan-platform|B2C")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("account.read")
                .scope(OidcScopes.OPENID)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(false) // client_credentials does not use PKCE
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(testClient);
    }

    /**
     * JWK source backed by the existing RSA key pair from {@link com.example.auth.infrastructure.config.JwtConfig}.
     * Reuses the same private/public key already used by JwtSigner — no separate key generation.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            java.security.PublicKey publicKey,
            java.security.PrivateKey privateKey) {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;

        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * Authorization server settings — issuer URL is externalized via {@code oidc.issuer-url}.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUrl)
                .build();
    }
}
