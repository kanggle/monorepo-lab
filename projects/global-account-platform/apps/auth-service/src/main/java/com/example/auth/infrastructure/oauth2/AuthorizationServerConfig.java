package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.TokenReuseDetector;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Spring Authorization Server configuration — Phase 2c (TASK-BE-251).
 *
 * <p>Exposes the full OIDC discovery endpoint, JWKS endpoint, token endpoint,
 * userinfo endpoint, revocation endpoint, and introspection endpoint.
 * The SAS filter chain takes {@code @Order(1)} and matches only the SAS-managed
 * request matchers ({@code /oauth2/**}, {@code /.well-known/**}),
 * leaving the existing security filter chain ({@code @Order(2)}) fully intact.
 *
 * <p>Phase 2c additions over Phase 2b:
 * <ul>
 *   <li>Token revocation endpoint ({@code POST /oauth2/revoke}, RFC 7009) — enabled explicitly.
 *       When a token is revoked, {@link DomainSyncOAuth2AuthorizationService#remove} is called,
 *       which in turn marks the corresponding domain {@link RefreshToken} as revoked in the JPA
 *       store. Access tokens are also removed from the SAS in-memory store.</li>
 *   <li>Token introspection endpoint ({@code POST /oauth2/introspect}, RFC 7662) — enabled
 *       explicitly with a custom {@link TenantIntrospectionCustomizer} that appends
 *       {@code tenant_id} and {@code tenant_type} to the standard response payload.</li>
 * </ul>
 *
 * <p>Phase 2b additions over Phase 2a:
 * <ul>
 *   <li>{@link SasRefreshTokenAuthenticationProvider} — custom refresh_token grant provider
 *       that integrates with the existing domain {@link RefreshTokenRepository} and
 *       {@link TokenReuseDetector}. Replaces SAS's default
 *       {@code OAuth2RefreshTokenAuthenticationProvider}.</li>
 *   <li>{@link DomainSyncOAuth2AuthorizationService} — wraps
 *       {@link InMemoryOAuth2AuthorizationService} to synchronise SAS-issued refresh tokens
 *       into the JPA domain store on every {@code save()} call.</li>
 * </ul>
 *
 * <p><b>Bean ordering note (Phase 2b):</b> {@code OAuth2TokenGenerator} is an internal SAS bean
 * created during the SAS configurer's {@code init()} phase (part of
 * {@link OAuth2AuthorizationServerConfigurer}). It is available as a shared object on
 * {@link HttpSecurity} only <em>after</em> SAS configurer initialization. Therefore
 * {@link SasRefreshTokenAuthenticationProvider} is NOT registered as a standalone {@code @Bean}
 * — doing so would cause a {@code NoSuchBeanDefinitionException} at context load time because
 * Spring tries to resolve {@code OAuth2TokenGenerator} before the SAS configurers have run.
 * Instead, the provider is created lazily within
 * {@link #authorizationServerSecurityFilterChain} using the shared object after SAS init.
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

    // Injected fields for SasRefreshTokenAuthenticationProvider construction
    // (cannot use constructor injection in @Configuration class without @ComponentScan)
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TokenReuseDetector tokenReuseDetector;
    @Autowired
    private BulkInvalidationStore bulkInvalidationStore;
    @Autowired
    private DeviceSessionRepository deviceSessionRepository;
    @Autowired
    private AuthEventPublisher authEventPublisher;

    /**
     * SAS security filter chain — Order(1).
     * Covers: /oauth2/**, /.well-known/openid-configuration
     *
     * <p>Phase 2a: wires {@link OidcUserInfoMapper} into the OIDC userinfo configurer.
     *
     * <p>Phase 2b: after SAS configurer init, retrieves {@link OAuth2TokenGenerator} from the
     * {@link HttpSecurity} shared objects and creates a {@link SasRefreshTokenAuthenticationProvider}
     * that integrates domain reuse-detection. The provider is added to the token endpoint
     * authentication manager; it takes priority over SAS's built-in refresh_token provider
     * because it is added first.
     *
     * <p>Phase 2c: explicitly enables token revocation ({@code POST /oauth2/revoke}, RFC 7009)
     * and token introspection ({@code POST /oauth2/introspect}, RFC 7662). The introspection
     * endpoint is configured with a {@link TenantIntrospectionCustomizer} that appends
     * {@code tenant_id} and {@code tenant_type} extension claims to the standard response.
     * Revocation is handled by the SAS built-in revocation provider which calls
     * {@link OAuth2AuthorizationService#remove}, triggering
     * {@link DomainSyncOAuth2AuthorizationService#remove} to revoke the token in the JPA store.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OidcUserInfoMapper oidcUserInfoMapper,
            OAuth2AuthorizationService oAuth2AuthorizationService) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        TenantIntrospectionCustomizer introspectionCustomizer = new TenantIntrospectionCustomizer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer ->
                        configurer
                                .oidc(oidc ->
                                        oidc.userInfoEndpoint(userInfo ->
                                                userInfo.userInfoMapper(oidcUserInfoMapper)))
                                // Phase 2b: add custom refresh_token provider.
                                // OAuth2TokenGenerator is available as a shared object on HttpSecurity
                                // after the SAS configurer has been applied via .with(). We use a
                                // Customizer that captures http to resolve the generator lazily.
                                .tokenEndpoint(tokenEndpoint ->
                                        tokenEndpoint.authenticationProvider(
                                                buildRefreshTokenProvider(http, oAuth2AuthorizationService)))
                                // Phase 2c: token revocation endpoint (RFC 7009).
                                // SAS default revocation provider calls authorizationService.remove(),
                                // which triggers DomainSyncOAuth2AuthorizationService to revoke the
                                // token in the JPA domain store. No custom provider needed.
                                .tokenRevocationEndpoint(revocation -> { /* use SAS defaults */ })
                                // Phase 2c: token introspection endpoint (RFC 7662).
                                // Custom introspectionResponseHandler enriches the response with
                                // tenant_id and tenant_type extension claims.
                                .tokenIntrospectionEndpoint(introspection ->
                                        introspection.introspectionResponseHandler(introspectionCustomizer)))
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
     * Builds the {@link SasRefreshTokenAuthenticationProvider} using the
     * {@link OAuth2TokenGenerator} shared object from the {@link HttpSecurity} configurer chain.
     *
     * <p>This method is called from inside the SAS configurer's Customizer lambda, which means
     * SAS has already applied its own configurers and populated the shared objects map with
     * {@link OAuth2TokenGenerator}. Accessing it here is safe and avoids the circular
     * dependency that would occur if we declared it as a standalone {@code @Bean}.
     */
    @SuppressWarnings("unchecked")
    private SasRefreshTokenAuthenticationProvider buildRefreshTokenProvider(
            HttpSecurity http,
            OAuth2AuthorizationService oAuth2AuthorizationService) {
        // SAS stores OAuth2TokenGenerator in HttpSecurity's shared objects under its own type.
        // This is the standard SAS extension pattern used by the built-in grant providers.
        OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator =
                http.getSharedObject(OAuth2TokenGenerator.class);
        return new SasRefreshTokenAuthenticationProvider(
                oAuth2AuthorizationService,
                tokenGenerator,
                refreshTokenRepository,
                tokenReuseDetector,
                bulkInvalidationStore,
                deviceSessionRepository,
                authEventPublisher);
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
     * {@link OAuth2AuthorizationService} — wraps the SAS in-memory store with a decorator that
     * synchronises refresh token issuance and revocation into the domain JPA
     * {@link RefreshTokenRepository}.
     *
     * <p>Declared as a {@code @Bean} so that Spring does not auto-configure the default
     * {@link InMemoryOAuth2AuthorizationService}. SAS picks up this bean automatically.
     *
     * <p>Phase 2b — dual-store strategy for reuse detection.
     */
    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            RefreshTokenRepository refreshTokenRepository) {
        return new DomainSyncOAuth2AuthorizationService(
                new InMemoryOAuth2AuthorizationService(),
                refreshTokenRepository);
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
