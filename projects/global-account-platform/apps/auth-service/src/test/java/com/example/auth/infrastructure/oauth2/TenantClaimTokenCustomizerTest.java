package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantClaimTokenCustomizer}.
 *
 * <p>Verifies that {@code tenant_id} and {@code tenant_type} claims are correctly
 * injected into access tokens and ID tokens for the {@code client_credentials} and
 * {@code authorization_code} grants, and that fail-closed behaviour is enforced when
 * tenant metadata is absent or malformed.
 *
 * <p>Phase 2a additions: authorization_code grant + id_token type coverage (TASK-BE-251).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantClaimTokenCustomizerTest {

    private TenantClaimTokenCustomizer customizer;

    @Mock
    private JwtEncodingContext context;

    @Mock
    private Authentication principal;

    @Mock
    private AccountServicePort accountServicePort;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer(accountServicePort);
    }

    // -----------------------------------------------------------------------
    // client_credentials — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("client_credentials: injects tenant_id and tenant_type from clientName")
    void clientCredentials_injectsTenantClaims() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("test-internal-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    @Test
    @DisplayName("client_credentials: tenant with spaces trimmed")
    void clientCredentials_trimsTenantValues() {
        RegisteredClient client = buildClient("  wms-tenant  |  B2B  ");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B");
    }

    // -----------------------------------------------------------------------
    // client_credentials — fail-closed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("client_credentials: missing separator in clientName → IllegalStateException (fail-closed)")
    void clientCredentials_missingSeparator_failsClosed() {
        RegisteredClient client = buildClient("no-separator");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    @Test
    @DisplayName("client_credentials: null clientName → IllegalStateException (fail-closed)")
    void clientCredentials_nullClientName_failsClosed() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    @Test
    @DisplayName("client_credentials: blank tenantId → IllegalStateException (fail-closed)")
    void clientCredentials_blankTenantId_failsClosed() {
        RegisteredClient client = buildClient("|B2C");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id must not be blank");
    }

    @Test
    @DisplayName("client_credentials: blank tenantType → IllegalStateException (fail-closed)")
    void clientCredentials_blankTenantType_failsClosed() {
        RegisteredClient client = buildClient("fan-platform|");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_type must not be blank");
    }

    // -----------------------------------------------------------------------
    // Non-access tokens — customizer must be a no-op
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("refresh token type → no-op (no claims injected, no exceptions)")
    void refreshToken_isNoOp() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.REFRESH_TOKEN);

        // Should not throw and should not call getAuthorizationGrantType()
        customizer.customize(context);
    }

    // -----------------------------------------------------------------------
    // id_token — must receive the same tenant claims as access_token
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("id_token for client_credentials → tenant claims injected")
    void idToken_clientCredentials_injectsTenantClaims() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // id_token has type value "id_token" (not OAuth2TokenType.ACCESS_TOKEN)
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    // -----------------------------------------------------------------------
    // authorization_code grant — tenant from principal details
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authorization_code: tenant_id from principal details → injected into access_token")
    void authorizationCode_tenantFromPrincipalDetails_injected() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "wms-tenant",
                "tenant_type", "B2B"
        );
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B");
    }

    @Test
    @DisplayName("authorization_code: no principal details → fallback to client metadata")
    void authorizationCode_noPrincipalDetails_fallbackToClientMetadata() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // Principal details are null — no tenant in auth session
        when(principal.getDetails()).thenReturn(null);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // Falls back to client metadata
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-329 (ADR-MONO-021 D3) — account_type claim
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authorization_code: account_type from principal details → injected into access_token")
    void authorizationCode_accountType_injectedIntoAccessToken() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "fan-platform",
                "tenant_type", "B2C",
                "account_type", "CONSUMER");
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("account_type")).isEqualTo("CONSUMER");
    }

    @Test
    @DisplayName("authorization_code: account_type=OPERATOR honoured from principal details")
    void authorizationCode_operatorAccountType_injected() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "acme-corp",
                "tenant_type", "B2B_ENTERPRISE",
                "account_type", "OPERATOR");
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("account_type")).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("authorization_code: id_token also receives account_type")
    void authorizationCode_idToken_receivesAccountType() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "fan-platform",
                "tenant_type", "B2C",
                "account_type", "CONSUMER");
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("account_type")).isEqualTo("CONSUMER");
    }

    @Test
    @DisplayName("client_credentials: NO account_type claim (a workload is not an account)")
    void clientCredentials_noAccountType() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.getClaims()).doesNotContainKey("account_type");
    }

    @Test
    @DisplayName("authorization_code: id_token also receives tenant claims")
    void authorizationCode_idToken_receivesTenantClaims() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "fan-platform",
                "tenant_type", "B2C"
        );
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    @Test
    @DisplayName("authorization_code: neither principal details nor client metadata → fail-closed")
    void authorizationCode_noTenantAnywhere_failsClosed() {
        // Client with no clientName metadata
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("no-tenant-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:3000/callback")
                .build();

        when(principal.getDetails()).thenReturn(null);
        when(principal.getName()).thenReturn("no-tenant-client");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    // -----------------------------------------------------------------------
    // refresh_token grant — tenant claims preserved on rotated access_token
    // -----------------------------------------------------------------------

    /**
     * TASK-BE-274 cycle 3: SasRefreshTokenAuthenticationProvider calls
     * tokenGenerator.generate() with authorizationGrantType=REFRESH_TOKEN when
     * producing the rotated access_token. Without the REFRESH_TOKEN branch in
     * TenantClaimTokenCustomizer the tenant claims would be absent.
     */
    @Test
    @DisplayName("refresh_token grant: tenant from ClientSettings injected into rotated access_token")
    void refreshTokenGrant_tenantFromClientSettings_injected() {
        RegisteredClient client = buildClientWithTenantSettings(
                "fan-platform", "B2C", AuthorizationGrantType.REFRESH_TOKEN);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // principal with no details — forces ClientSettings fallback (Option B)
        when(principal.getDetails()).thenReturn(null);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    @Test
    @DisplayName("refresh_token grant: tenant from principal details injected into rotated access_token")
    void refreshTokenGrant_tenantFromPrincipalDetails_injected() {
        RegisteredClient client = buildClientWithTenantSettings(
                "fan-platform", "B2C", AuthorizationGrantType.REFRESH_TOKEN);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of("tenant_id", "wms-tenant", "tenant_type", "B2B");
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // principal details take priority
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-324 — entitled_domains claim populate (ADR-MONO-019 § 3.3 keystone)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authorization_code: account returns [finance] → entitled_domains=[finance] injected")
    void authorizationCode_entitledDomains_injected() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of("tenant_id", "wms-tenant", "tenant_type", "B2B");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("wms-tenant")).thenReturn(List.of("finance"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("finance");
        assertThat(built.getClaims()).containsKey("entitled_domains");
    }

    @Test
    @DisplayName("authorization_code: account returns [] → no entitled_domains claim (net-zero)")
    void authorizationCode_emptyEntitledDomains_omitsClaim() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of("tenant_id", "wms-tenant", "tenant_type", "B2B");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("wms-tenant")).thenReturn(List.of());

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
    }

    @Test
    @DisplayName("authorization_code: account throws → fail-soft, no entitled_domains, no exception")
    void authorizationCode_accountThrows_failSoft() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of("tenant_id", "wms-tenant", "tenant_type", "B2B");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("wms-tenant"))
                .thenThrow(new AccountServiceUnavailableException("account down", new RuntimeException()));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        // must not throw (fail-soft)
        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // token still issued with tenant_id; entitled_domains omitted
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
    }

    @Test
    @DisplayName("client_credentials: listEntitledDomains never called (recursion safety) + no entitled_domains")
    void clientCredentials_entitledDomainsNeverLookedUp() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        verify(accountServicePort, never()).listEntitledDomains(any());
        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-327 — assume-tenant (token-exchange) branch (ADR-MONO-020 D2+D3)
    // -----------------------------------------------------------------------

    private AssumeTenantAuthenticationToken assumeGrant(String tenantId, String tenantType) {
        return new AssumeTenantAuthenticationToken(
                null, "subject", "urn:ietf:params:oauth:token-type:access_token",
                tenantId, tenantType);
    }

    private AssumeTenantAuthenticationToken assumeGrant(
            String tenantId, String tenantType, String operatorAccountType) {
        return new AssumeTenantAuthenticationToken(
                null, "subject", "urn:ietf:params:oauth:token-type:access_token",
                tenantId, tenantType, operatorAccountType);
    }

    @Test
    @DisplayName("assume-tenant: PRESERVES the operator's account_type=OPERATOR on the assumed token")
    void assumeTenant_preservesOperatorAccountType() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE", "OPERATOR"));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("finance"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // The operator stays OPERATOR while acting for a customer (ADR-MONO-021 D3).
        assertThat((String) built.getClaim("account_type")).isEqualTo("OPERATOR");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("assume-tenant: selected tenant_id/tenant_type injected + entitled_domains=selected's subs only")
    void assumeTenant_injectsSelectedTenant_andEntitledDomains() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        // D3: keyed on the SELECTED tenant only.
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("finance", "wms"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B_ENTERPRISE");
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("finance", "wms");
    }

    @Test
    @DisplayName("assume-tenant: account unavailable → token WITHOUT entitled_domains (fail-soft), still tenant_id")
    void assumeTenant_accountUnavailable_failSoft() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp"))
                .thenThrow(new AccountServiceUnavailableException("account down", new RuntimeException()));
        when(context.getClaims()).thenReturn(claimsBuilder);

        // must NOT throw — entitled_domains is fail-soft (token still issued).
        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B_ENTERPRISE");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
    }

    @Test
    @DisplayName("assume-tenant: selected tenant context missing → fail-closed IllegalStateException")
    void assumeTenant_missingSelectedTenant_failsClosed() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant()).thenReturn(assumeGrant(null, "B2B_ENTERPRISE"));

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected tenant_id/tenant_type is required");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisteredClient buildClient(String clientName) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-internal-client")
                .clientSecret("{noop}secret")
                .clientName(clientName)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }

    private RegisteredClient buildClientWithGrantType(String clientName, AuthorizationGrantType grantType) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-spa-client")
                .clientName(clientName)
                .authorizationGrantType(grantType);
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType)) {
            builder.redirectUri("http://localhost:3000/callback");
        } else {
            builder.clientSecret("{noop}secret");
        }
        return builder.build();
    }

    /**
     * Builds a {@link RegisteredClient} carrying {@link OAuthClientMapper#SETTING_TENANT_ID}
     * and {@link OAuthClientMapper#SETTING_TENANT_TYPE} in its {@link ClientSettings}, as
     * produced by {@link OAuthClientMapper#toRegisteredClient} for the real DB-backed path.
     */
    private RegisteredClient buildClientWithTenantSettings(
            String tenantId, String tenantType, AuthorizationGrantType grantType) {
        ClientSettings cs = ClientSettings.builder()
                .setting(OAuthClientMapper.SETTING_TENANT_ID, tenantId)
                .setting(OAuthClientMapper.SETTING_TENANT_TYPE, tenantType)
                .build();
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-spa-client")
                .clientName("Demo SPA")
                .authorizationGrantType(grantType)
                .redirectUri("http://localhost:3000/callback")
                .clientSettings(cs)
                .build();
    }

    private JwtClaimsSet.Builder baseClaimsBuilder() {
        return JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("test-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
    }
}
