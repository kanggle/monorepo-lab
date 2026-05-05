package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.TokenReuseDetector;
import com.example.auth.infrastructure.oauth2.persistence.JpaOAuth2AuthorizationService;
import org.springframework.jdbc.core.JdbcOperations;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Spring Authorization Server configuration — Phase 2c (TASK-BE-251) + TASK-BE-252.
 *
 * <p>Exposes the full OIDC discovery endpoint, JWKS endpoint, token endpoint,
 * userinfo endpoint, revocation endpoint, and introspection endpoint.
 * The SAS filter chain takes {@code @Order(1)} and matches only the SAS-managed
 * request matchers ({@code /oauth2/**}, {@code /.well-known/**}),
 * leaving the existing security filter chain ({@code @Order(2)}) fully intact.
 *
 * <p>TASK-BE-252 changes:
 * <ul>
 *   <li>Removed in-memory {@code RegisteredClientRepository} placeholder bean.
 *       {@link com.example.auth.infrastructure.oauth2.persistence.JpaRegisteredClientRepository}
 *       is picked up automatically as a {@code @Component}.</li>
 *   <li>{@link DomainSyncOAuth2AuthorizationService} now wraps
 *       {@link com.example.auth.infrastructure.oauth2.persistence.JpaOAuth2AuthorizationService}
 *       (JDBC-backed) instead of {@code InMemoryOAuth2AuthorizationService}.
 *       Server restart no longer drops in-flight tokens.</li>
 * </ul>
 *
 * <p><b>Bean ordering note (Phase 2b):</b> {@code OAuth2TokenGenerator} is an internal SAS bean
 * created during the SAS configurer's {@code init()} phase. It is available as a shared object on
 * {@link HttpSecurity} only <em>after</em> SAS configurer initialization. Therefore
 * {@link SasRefreshTokenAuthenticationProvider} is NOT registered as a standalone {@code @Bean}
 * — it is created lazily within {@link #authorizationServerSecurityFilterChain} after SAS init.
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
                // TASK-MONO-046-1: scope the LoginUrlAuthenticationEntryPoint redirect
                // to browser (text/html) requests only. Programmatic API requests (POST
                // /oauth2/token, /oauth2/revoke, /oauth2/introspect) must NOT be
                // redirected — the SAS configurer's own filters short-circuit before
                // this entry point is consulted, but the {@code oauth2ResourceServer().jwt()}
                // BearerTokenAuthenticationFilter installed below sets a bearer-aware
                // entry point on top of {@code authorizeHttpRequests}. Without scoping
                // this default to text/html, public-client refresh_token grant requests
                // (no Authorization header, no session) get a 302 redirect instead of
                // reaching the SAS token endpoint filter.
                .exceptionHandling(exceptions ->
                        exceptions.defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/api/auth/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // TASK-MONO-046-1 (Cluster B): the OIDC userinfo endpoint requires the
                // bearer access token to be authenticated as a JWT. Without an
                // {@code oauth2ResourceServer().jwt()} configurer, SAS's userinfo filter
                // sees no Authentication on the SecurityContext and returns 403 Access Denied.
                // We delegate JWT decoding to the same {@link JwtDecoder} bean (built from
                // our JWKSource) so userinfo accepts tokens issued by this same AS.
                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(Customizer.withDefaults()));

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
     * {@link OAuth2AuthorizationService} bean — wraps the JPA-backed JDBC delegate with
     * {@link DomainSyncOAuth2AuthorizationService} that synchronises SAS refresh-token
     * issuance and revocation into the domain {@link RefreshTokenRepository}.
     *
     * <p>Declared explicitly so Spring does not auto-configure the default
     * {@code InMemoryOAuth2AuthorizationService}. SAS picks up this bean automatically.
     *
     * <p>TASK-BE-252: delegate changed from in-memory to {@link JpaOAuth2AuthorizationService}.
     */
    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository,
            RefreshTokenRepository refreshTokenRepository) {
        JpaOAuth2AuthorizationService jpaDelegate =
                new JpaOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        return new DomainSyncOAuth2AuthorizationService(jpaDelegate, refreshTokenRepository);
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

    /**
     * {@link JwtDecoder} for the resource server filter chain that protects
     * {@code /oauth2/userinfo}. Uses the same JWK source as the JWT issuance side
     * so tokens issued by this AS can be validated locally without a network call.
     *
     * <p>TASK-MONO-046-1 (Cluster B): without this bean SAS's
     * {@code oauth2ResourceServer().jwt()} configurer cannot resolve a decoder and
     * the userinfo filter chain rejects every bearer token with 403.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Custom {@link OAuth2TokenGenerator} that overrides SAS's auto-configured default.
     *
     * <p>Composes:
     * <ol>
     *   <li>{@link JwtGenerator} — JWT access tokens + ID tokens, applies our
     *       {@link TenantClaimTokenCustomizer} so {@code tenant_id} / {@code tenant_type}
     *       claims appear in every issued token.</li>
     *   <li>{@link OAuth2AccessTokenGenerator} — opaque access-token fallback (kept for
     *       parity with SAS defaults; not used in our flows but harmless).</li>
     *   <li>{@link PublicClientRefreshTokenGenerator} — issues refresh tokens for public
     *       PKCE clients (TASK-MONO-046-1 Cluster A). Replaces SAS's default
     *       {@code OAuth2RefreshTokenGenerator}, which returns {@code null} for
     *       {@code authorization_code} grants whose client uses {@code ClientAuthenticationMethod.NONE}.
     *       Without this override, {@code demo-spa-client} (a public PKCE SPA) cannot
     *       receive a refresh_token and the entire SAS rotation flow is unreachable.</li>
     * </ol>
     *
     * <p>Registered as a {@code @Bean} so SAS picks it up automatically via
     * {@code OAuth2ConfigurerUtils.getTokenGenerator(http)}.
     */
    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> oAuth2TokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        PublicClientRefreshTokenGenerator refreshTokenGenerator = new PublicClientRefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                accessTokenGenerator,
                refreshTokenGenerator);
    }
}
