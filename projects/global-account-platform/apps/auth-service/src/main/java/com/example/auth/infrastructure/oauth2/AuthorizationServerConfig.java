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
 * Spring Authorization Server configuration — Phase 2a (TASK-BE-251).
 *
 * <p>Exposes the full OIDC discovery endpoint, JWKS endpoint, token endpoint,
 * and userinfo endpoint. The SAS filter chain takes {@code @Order(1)} and matches
 * only the SAS-managed request matchers ({@code /oauth2/**}, {@code /.well-known/**}),
 * leaving the existing security filter chain ({@code @Order(2)}) fully intact.
 *
 * <p>Phase 2a additions over Phase 1:
 * <ul>
 *   <li>{@code authorization_code} grant with PKCE (S256 required)</li>
 *   <li>{@code demo-spa-client} in-memory placeholder for B2C fan-platform</li>
 *   <li>{@link OidcUserInfoMapper} wired as SAS userinfo mapper</li>
 *   <li>ID token support — {@link TenantClaimTokenCustomizer} now also
 *       customizes {@code id_token} (not just access tokens)</li>
 * </ul>
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
     *
     * <p>Phase 2a: wires {@link OidcUserInfoMapper} into the OIDC userinfo configurer.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OidcUserInfoMapper oidcUserInfoMapper) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer ->
                        configurer.oidc(oidc ->
                                oidc.userInfoEndpoint(userInfo ->
                                        userInfo.userInfoMapper(oidcUserInfoMapper))))
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated())
                // Redirect to /api/auth/login when unauthenticated — for authorization_code flow
                // the user will need an active session or must authenticate first.
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/api/auth/login")));

        return http.build();
    }

    /**
     * In-memory registered client repository with two in-memory placeholder clients:
     * <ol>
     *   <li>{@code test-internal-client} — Phase 1 {@code client_credentials} client</li>
     *   <li>{@code demo-spa-client} — Phase 2a {@code authorization_code} + PKCE B2C SPA client</li>
     * </ol>
     *
     * <p>The {@code clientName} field carries tenant metadata in the format
     * {@code "<tenantId>|<tenantType>"}. For authorization_code clients this is the fallback
     * if the principal's authentication details do not carry tenant attributes.
     *
     * <p>// PLACEHOLDER: replaced by JpaRegisteredClientRepository in TASK-BE-252
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        // Phase 1: client_credentials client (service-to-service)
        RegisteredClient testInternalClient = RegisteredClient.withId(UUID.randomUUID().toString())
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

        // Phase 2a: authorization_code + PKCE SPA client (B2C fan-platform placeholder)
        // PLACEHOLDER: redirect URI will be updated in TASK-BE-253 (fan-platform integration)
        RegisteredClient demoPkceClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-spa-client")
                // Public client (SPA) — no client secret; authentication is via PKCE only
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope(OidcScopes.OPENID)
                .scope("profile")
                .scope("email")
                // clientName encodes tenant metadata for fallback in TenantClaimTokenCustomizer
                .clientName("fan-platform|B2C")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)         // PKCE mandatory — S256 only
                        .requireAuthorizationConsent(false) // B2C pre-approved scopes
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false) // rotation enabled
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(testInternalClient, demoPkceClient);
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
