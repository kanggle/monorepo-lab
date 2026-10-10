package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.domain.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-614 — the issuer-side gate: {@code tenant_id = consumer-pool} is never minted, on any
 * grant, on either token type (jwt-standard-claims.md {@code tenant_id} row; multi-tenancy.md
 * § 소비자 계정 풀 § 1). The control in each pair is the same grant with a site tenant, which mints.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantClaimTokenCustomizer — consumer-pool 은 어떤 토큰에도 실리지 않는다 (TASK-BE-614)")
class TenantClaimConsumerPoolRefusalTest {

    @Mock private JwtEncodingContext context;
    @Mock private Authentication principal;
    @Mock private AccountServicePort accountServicePort;

    private TenantClaimTokenCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort, ConsoleEligibilityStubs.NOT_ASKED);
    }

    private static RegisteredClient client(String clientName, AuthorizationGrantType grantType) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-client")
                .clientName(clientName)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(grantType)
                .redirectUri("http://localhost/cb")
                .build();
    }

    private static JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("test-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
    }

    private static void assertInvalidGrant(Throwable t) {
        assertThat(t).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(((OAuth2AuthenticationException) t).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    /** principal-details path (form login / SSO session) for authorization_code and refresh_token. */
    private JwtClaimsSet.Builder principalPath(String detailsTenant, AuthorizationGrantType grant,
                                               OAuth2TokenType tokenType) {
        JwtClaimsSet.Builder builder = claims();
        when(principal.getDetails()).thenReturn(Map.of(
                "tenant_id", detailsTenant, "tenant_type", "B2C_CONSUMER",
                "account_id", "00000000-0000-0000-0000-000000000614"));
        when(context.getTokenType()).thenReturn(tokenType);
        when(context.getAuthorizationGrantType()).thenReturn(grant);
        when(context.getRegisteredClient()).thenReturn(
                client("ecommerce|B2C_CONSUMER", AuthorizationGrantType.AUTHORIZATION_CODE));
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(builder);
        return builder;
    }

    @Test
    @DisplayName("authorization_code: principal 의 tenant_id 가 consumer-pool 이면 invalid_grant — 토큰 없음")
    void authorizationCode_poolPrincipal_refused() {
        principalPath(TenantContext.CONSUMER_POOL_TENANT_ID,
                AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> customizer.customize(context));

        assertInvalidGrant(thrown);
    }

    @Test
    @DisplayName("refresh_token: 같은 principal 경로 — consumer-pool 이면 invalid_grant")
    void refresh_poolPrincipal_refused() {
        principalPath(TenantContext.CONSUMER_POOL_TENANT_ID,
                AuthorizationGrantType.REFRESH_TOKEN, OAuth2TokenType.ACCESS_TOKEN);

        assertInvalidGrant(org.assertj.core.api.Assertions.catchThrowable(() -> customizer.customize(context)));
    }

    @Test
    @DisplayName("id_token 도 같다 — consumer-pool 이면 invalid_grant")
    void idToken_poolPrincipal_refused() {
        principalPath(TenantContext.CONSUMER_POOL_TENANT_ID,
                AuthorizationGrantType.AUTHORIZATION_CODE, new OAuth2TokenType("id_token"));

        assertInvalidGrant(org.assertj.core.api.Assertions.catchThrowable(() -> customizer.customize(context)));
    }

    @Test
    @DisplayName("대조군: 같은 경로, 사이트 테넌트(ecommerce) → 정상 발급")
    void authorizationCode_siteTenant_mints() {
        JwtClaimsSet.Builder builder = principalPath("ecommerce",
                AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN);

        customizer.customize(context);

        assertThat((String) builder.build().getClaim("tenant_id")).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("client_credentials: client 테넌트가 consumer-pool 이면 invalid_grant")
    void clientCredentials_poolClient_refused() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(
                client("consumer-pool|B2C_CONSUMER", AuthorizationGrantType.CLIENT_CREDENTIALS));
        when(context.getClaims()).thenReturn(claims());

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(t -> assertInvalidGrant(t));
    }
}
