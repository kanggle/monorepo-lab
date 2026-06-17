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
    @DisplayName("ADR-MONO-040 authorization_code: account_id from principal details → emitted as account_id claim")
    void authorizationCode_accountIdFromPrincipalDetails_emittedAsClaim() {
        RegisteredClient client = buildClientWithGrantType(
                "ecommerce|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // The account UUID rides on the principal details (set by
        // CredentialAuthenticationProvider). ADR-040 D2: the customizer emits it
        // as an additive account_id claim (the SAS sub stays the email).
        Map<String, Object> details = Map.of(
                "tenant_id", "ecommerce",
                "tenant_type", "B2C",
                "account_id", "550e8400-e29b-41d4-a716-446655440000"
        );
        when(principal.getDetails()).thenReturn(details);

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("account_id"))
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("ADR-MONO-040 authorization_code: no account_id in details → no account_id claim (additive, never blank)")
    void authorizationCode_noAccountId_noClaim() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(principal.getDetails()).thenReturn(Map.of("tenant_id", "fan-platform", "tenant_type", "B2C"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        assertThat((Object) claimsBuilder.build().getClaim("account_id")).isNull();
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
    // TASK-MONO-263 (ADR-032 D5 step 4) — account_type claim REMOVED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TASK-MONO-263 authorization_code: NO account_type claim even if principal details carry it")
    void authorizationCode_noAccountTypeClaim() {
        RegisteredClient client = buildClientWithGrantType(
                "fan-platform|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // Even a stale principal detail carrying account_type must NOT surface as a claim.
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
        assertThat(built.getClaims()).doesNotContainKey("account_type");
        // tenant claims still emitted (net-zero apart from account_type removal).
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
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
            String tenantId, String tenantType, List<String> orgScope) {
        return new AssumeTenantAuthenticationToken(
                null, "subject", "urn:ietf:params:oauth:token-type:access_token",
                tenantId, tenantType, orgScope);
    }

    @Test
    @DisplayName("TASK-MONO-263 assume-tenant: NO account_type claim emitted on the assumed token")
    void assumeTenant_noAccountType() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("finance"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // ADR-032 D5 step 4: account_type is removed entirely.
        assertThat(built.getClaims()).doesNotContainKey("account_type");
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

    // -----------------------------------------------------------------------
    // TASK-BE-338 (ADR-MONO-020 D3 amendment) — membership-derived org_scope
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-338: assume-tenant org_scope 미설정(null grant) → org_scope=[*] (net-zero, BE-337 동작 보존)")
    void assumeTenant_nullOrgScope_injectsStar_netZero() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        // 2-arg grant → orgScope is null (the pre-BE-338 net-zero shape).
        when(context.getAuthorizationGrant()).thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("finance"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("*");
    }

    @Test
    @DisplayName("BE-338: assume-tenant 설정된 org_scope → 그 값 verbatim 주입")
    void assumeTenant_populatedOrgScope_injectsValue() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE",
                        List.of("dept-sales", "dept-ops")));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("erp"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("org_scope"))
                .containsExactly("dept-sales", "dept-ops");
    }

    @Test
    @DisplayName("BE-338: assume-tenant org_scope=[] (빈 배열) → [*] (net-zero default)")
    void assumeTenant_emptyOrgScope_injectsStar() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE", List.of()));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("erp"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // An empty list at the claim layer is treated as net-zero → ["*"] (the
        // customizer's safe default; an explicit [] zero-scope is preserved at the
        // admin/claim source, but the assume-tenant injection defaults empty → [*]).
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("*");
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
    // TASK-BE-369 (ADR-MONO-033 S4 base + S3) — roles claim leg
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-369 authorization_code: stored account_roles present → emitted verbatim (no seed)")
    void authorizationCode_storedRoles_emittedVerbatim() {
        RegisteredClient client = buildClientWithTenantSettings(
                "wms", "B2B", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "wms-tenant",
                "tenant_type", "B2B",
                "account_id", "acc-1");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("wms-tenant")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("wms-tenant", "acc-1"))
                .thenReturn(List.of("WMS_OPERATOR", "OUTBOUND_MANAGER"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // stored set emitted verbatim — the explicitly-granted OUTBOUND_MANAGER flows through.
        assertThat(built.<List<String>>getClaim("roles"))
                .containsExactly("WMS_OPERATOR", "OUTBOUND_MANAGER");
        // TASK-MONO-263: no account_type claim.
        assertThat(built.getClaims()).doesNotContainKey("account_type");
    }

    @Test
    @DisplayName("BE-369/MONO-263 authorization_code: ecommerce + empty stored → seed [CUSTOMER] (platform-keyed)")
    void authorizationCode_emptyStored_ecommerce_seedsCustomer() {
        RegisteredClient client = buildClientWithTenantSettings(
                "ecommerce", "B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "ecommerce",
                "tenant_type", "B2C",
                "account_id", "acc-2");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("ecommerce", "acc-2")).thenReturn(List.of());

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("MONO-263 authorization_code: wms + empty stored → NO roles claim (seed is consumer-only, no wms consumer)")
    void authorizationCode_emptyStored_wms_omitsRoles() {
        RegisteredClient client = buildClientWithTenantSettings(
                "wms", "B2B", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "wms-tenant",
                "tenant_type", "B2B",
                "account_id", "acc-4");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("wms-tenant")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("wms-tenant", "acc-4")).thenReturn(List.of());

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // platform-keyed consumer seed has no wms entry → roles omitted (operators
        // get domain roles at assume-tenant, BE-376).
        assertThat(built.getClaims()).doesNotContainKey("roles");
    }

    @Test
    @DisplayName("BE-369/MONO-263 authorization_code: fan-platform + empty stored → seed [FAN]")
    void authorizationCode_emptyStored_fan_seedsFan() {
        RegisteredClient client = buildClientWithTenantSettings(
                "fan-platform", "B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "fan-platform",
                "tenant_type", "B2C",
                "account_id", "acc-5");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("fan-platform")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("fan-platform", "acc-5")).thenReturn(List.of());

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("FAN");
    }

    @Test
    @DisplayName("BE-369/MONO-263 authorization_code: account-service throws → fail-soft falls to seed (ecommerce → CUSTOMER)")
    void authorizationCode_rolesLookupThrows_failSoftToSeed() {
        RegisteredClient client = buildClientWithTenantSettings(
                "ecommerce", "B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        Map<String, Object> details = Map.of(
                "tenant_id", "ecommerce",
                "tenant_type", "B2C",
                "account_id", "acc-6");
        when(principal.getDetails()).thenReturn(details);
        when(accountServicePort.listEntitledDomains("ecommerce")).thenReturn(List.of());
        when(accountServicePort.listAccountRoles("ecommerce", "acc-6"))
                .thenThrow(new AccountServiceUnavailableException("account down", new RuntimeException()));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);

        // must not throw (fail-soft) — falls to the platform-keyed seed (ecommerce → CUSTOMER).
        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("CUSTOMER");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("BE-369 client_credentials: listAccountRoles NEVER called + no roles claim (recursion guard)")
    void clientCredentials_rolesNeverLookedUp() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        verify(accountServicePort, never()).listAccountRoles(any(), any());
        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.getClaims()).doesNotContainKey("roles");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-376 (ADR-MONO-035 O1 / step 4a) — roles DERIVED from the selected
    // tenant's entitled domains (replaces BE-370 preserve-from-base)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-376 assume-tenant: entitled [finance, wms] → roles derived [FINANCE_OPERATOR, WMS_OPERATOR]")
    void assumeTenant_rolesDerivedFromEntitledDomains() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE",
                        List.of("dept-sales")));
        // The selected tenant's ACTIVE entitled domains drive the operator roles.
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("finance", "wms"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // roles derived from the selected tenant's entitled domains (no preserve / no seed).
        assertThat(built.<List<String>>getClaim("roles"))
                .containsExactly("FINANCE_OPERATOR", "WMS_OPERATOR");
        // entitled_domains rides the same fetch.
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("finance", "wms");
        // existing assume-tenant assertions still pass alongside (BE-338 untouched).
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        // TASK-MONO-263: no account_type claim.
        assertThat(built.getClaims()).doesNotContainKey("account_type");
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("BE-376 assume-tenant: entitled [ecommerce] → roles [ADMIN]")
    void assumeTenant_ecommerceEntitled_derivesAdmin() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("ecommerce"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("BE-376 assume-tenant: entitled empty → no roles claim AND no entitled_domains (net-zero)")
    void assumeTenant_emptyEntitled_omitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of());
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.getClaims()).doesNotContainKey("roles");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
        // the existing assume-tenant injection is byte-unchanged.
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        // TASK-MONO-263: no account_type claim.
        assertThat(built.getClaims()).doesNotContainKey("account_type");
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("*");
    }

    @Test
    @DisplayName("BE-376 assume-tenant: entitled only [gap] → no roles claim (net-zero least-privilege)")
    void assumeTenant_gapOnlyEntitled_omitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE",
                        List.of("dept-sales")));
        when(accountServicePort.listEntitledDomains("acme-corp")).thenReturn(List.of("gap"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // gap → no operator role → roles omitted (gateway 403s; correct).
        assertThat(built.getClaims()).doesNotContainKey("roles");
        // entitled_domains itself is still injected (gap IS an active entitlement).
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("gap");
        // org_scope still injected verbatim (the roles omission does not affect BE-338).
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("BE-376 assume-tenant: account-service throws → roles AND entitled_domains omitted (fail-soft, no throw)")
    void assumeTenant_accountThrows_failSoftOmitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrant("acme-corp", "B2B_ENTERPRISE"));
        when(accountServicePort.listEntitledDomains("acme-corp"))
                .thenThrow(new AccountServiceUnavailableException("account down", new RuntimeException()));
        when(context.getClaims()).thenReturn(claimsBuilder);

        // must NOT throw (fail-soft) — both entitled_domains and roles omitted.
        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.getClaims()).doesNotContainKey("roles");
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
        // token still issued with tenant_id/org_scope (net-zero); no account_type (MONO-263).
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        assertThat(built.getClaims()).doesNotContainKey("account_type");
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("*");
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
