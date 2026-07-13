package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OperatorAssignmentPort;
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
    @DisplayName("ADR-MONO-040 Phase 3 part B authorization_code: account_id from principal details → sub OVERRIDDEN to account UUID, transitional account_id claim NO LONGER emitted")
    void authorizationCode_accountIdFromPrincipalDetails_overridesSubNoClaim() {
        RegisteredClient client = buildClientWithGrantType(
                "ecommerce|B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // The account UUID rides on the principal details (set by
        // CredentialAuthenticationProvider). ADR-040 Phase 3 part B (MONO-299): the
        // customizer OVERRIDES `sub` to the account UUID (full jwt-standard-claims
        // compliance, X-User-Id ← sub) and NO LONGER emits the transitional
        // `account_id` claim (every gateway reads `sub` directly).
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
        // AC-2: sub is the account UUID (was the framework default "test-client").
        assertThat(built.getSubject()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        // The transitional account_id claim is removed (Phase 3 part B).
        assertThat((Object) built.getClaim("account_id")).isNull();
    }

    @Test
    @DisplayName("ADR-MONO-040 Phase 2 authorization_code: no account_id in details → sub NOT overridden, no account_id claim (graceful net-zero)")
    void authorizationCode_noAccountId_subUnchanged_noClaim() {
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

        JwtClaimsSet built = claimsBuilder.build();
        // No account_id to substitute → sub keeps the framework default.
        assertThat(built.getSubject()).isEqualTo("test-client");
        assertThat((Object) built.getClaim("account_id")).isNull();
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

    // ── TASK-MONO-381 — the seed is a CROSS-TENANT guard ──────────────────────────────
    // The seed used to be keyed on the client alone, so every principal who authenticated
    // through the storefront client got CUSTOMER — operators and SUPER_ADMIN included. A
    // CUSTOMER-less token was unconstructible on that path, so ADR-MONO-035 §4b-iii's role
    // guard could never fire. Nothing asserted the roles of a CROSS-TENANT principal on the
    // ecommerce client; these are those assertions.

    private void givenEcommerceClientWith(String principalTenant, JwtClaimsSet.Builder claimsBuilder) {
        RegisteredClient client = buildClientWithTenantSettings(
                "ecommerce", "B2C", AuthorizationGrantType.AUTHORIZATION_CODE);
        when(principal.getDetails()).thenReturn(Map.of(
                "tenant_id", principalTenant,
                "tenant_type", "B2C",
                "account_id", "acc-x"));
        when(accountServicePort.listEntitledDomains(principalTenant)).thenReturn(List.of());
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getClaims()).thenReturn(claimsBuilder);
    }

    @Test
    @DisplayName("MONO-381: 타 tenant operator(wms)가 storefront 클라이언트로 로그인 → seed 미발화, roles 클레임 없음 (web-store 가드가 비로소 문다)")
    void authorizationCode_crossTenantOperator_onEcommerceClient_omitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();
        givenEcommerceClientWith("wms", claimsBuilder);
        when(accountServicePort.listAccountRoles("wms", "acc-x")).thenReturn(List.of());

        customizer.customize(context);

        assertThat(claimsBuilder.build().<List<String>>getClaim("roles")).isNull();
    }

    @Test
    // The mechanism this name used to cite no longer exists: TASK-MONO-388 removed
    // acceptAnyWellFormedTenant from the shared validator. The ecommerce gate admits '*' via
    // allowSuperAdminWildcard now — deliberately, not incidentally. The assertion is unchanged;
    // only the reason the token gets that far is named correctly.
    @DisplayName("MONO-381: SUPER_ADMIN('*')이 storefront 클라이언트로 로그인 → roles 클레임 없음 (게이트웨이가 allowSuperAdminWildcard 로 '*' 를 통과시켜도 가드가 막는다)")
    void authorizationCode_superAdminWildcard_onEcommerceClient_omitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();
        givenEcommerceClientWith("*", claimsBuilder);
        when(accountServicePort.listAccountRoles("*", "acc-x")).thenReturn(List.of());

        customizer.customize(context);

        assertThat(claimsBuilder.build().<List<String>>getClaim("roles")).isNull();
    }

    @Test
    @DisplayName("MONO-381: BE-507 이전 레거시 소비자(fan-platform)가 storefront 로 로그인 → roles 없음. 로그인은 되지만 스토어프론트는 거부 — MONO-386 의 forcing function")
    void authorizationCode_legacyFanPlatformConsumer_onEcommerceClient_omitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();
        givenEcommerceClientWith("fan-platform", claimsBuilder);
        when(accountServicePort.listAccountRoles("fan-platform", "acc-x")).thenReturn(List.of());

        customizer.customize(context);

        // 의도된 결과다. 이 계정은 BE-507 의 cross-tenant credential 폴백으로 *인증*은 되지만,
        // 자기 tenant 가 storefront 의 platform 이 아니므로 소비자 role 을 받지 못한다.
        assertThat(claimsBuilder.build().<List<String>>getClaim("roles")).isNull();
    }

    @Test
    @DisplayName("MONO-381: fail-soft 가 seed 를 되살리지 못한다 — account-service 장애 + cross-tenant principal → 여전히 roles 없음")
    void authorizationCode_crossTenantPrincipal_lookupThrows_stillOmitsRoles() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();
        givenEcommerceClientWith("wms", claimsBuilder);
        when(accountServicePort.listAccountRoles("wms", "acc-x"))
                .thenThrow(new RuntimeException("account-service down"));

        customizer.customize(context);

        // fail-soft(ADR-033 S5)는 유지되지만, 그 폴백이 cross-tenant principal 에게
        // CUSTOMER 를 주는 뒷문이 되어서는 안 된다.
        assertThat(claimsBuilder.build().<List<String>>getClaim("roles")).isNull();
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
    @DisplayName("BE-376/433 assume-tenant: entitled [finance, wms] → roles derived [FINANCE_OPERATOR + granular wms operator-tier set]")
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
        // TASK-BE-433: the wms entitlement expands to the granular operator-tier service roles.
        assertThat(built.<List<String>>getClaim("roles"))
                .containsExactly("FINANCE_OPERATOR",
                        "WMS_OPERATOR",
                        "OUTBOUND_READ", "OUTBOUND_WRITE",
                        "INBOUND_READ", "INBOUND_WRITE",
                        "INVENTORY_READ", "INVENTORY_WRITE",
                        "MASTER_READ");
        // entitled_domains rides the same fetch.
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("finance", "wms");
        // existing assume-tenant assertions still pass alongside (BE-338 untouched).
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("acme-corp");
        // TASK-MONO-263: no account_type claim.
        assertThat(built.getClaims()).doesNotContainKey("account_type");
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("BE-376 assume-tenant: entitled [ecommerce] → roles [ECOMMERCE_OPERATOR]")
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
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("ECOMMERCE_OPERATOR");
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
    // TASK-BE-478 (ADR-MONO-045 §3.4 step 2b) — cross-org partnership cap: when the
    // assignment is partnership-derived host reach (delegatedScope present), the
    // token's entitled_domains is capped to host ∩ delegated.domains and roles is the
    // delegated roles VERBATIM (never re-derived). Admin scope is never expressed.
    // -----------------------------------------------------------------------

    private AssumeTenantAuthenticationToken assumeGrantCrossOrg(
            String tenantId, String tenantType, List<String> orgScope,
            OperatorAssignmentPort.DelegatedScope delegatedScope) {
        return new AssumeTenantAuthenticationToken(
                null, "subject", "urn:ietf:params:oauth:token-type:access_token",
                tenantId, tenantType, orgScope, delegatedScope);
    }

    @Test
    @DisplayName("BE-478 cross-org: host entitled [wms,ecommerce,scm] ∩ delegated [wms] → entitled_domains=[wms]; roles=delegated VERBATIM (not derived)")
    void assumeTenant_crossOrg_capsEntitledAndUsesDelegatedRolesVerbatim() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // delegated.roles is a STRICT SUBSET of what OperatorRoleDerivation("wms")
        // would produce (WMS_OPERATOR + the granular set). Asserting exactly this set
        // proves the customizer uses the delegated roles verbatim, NOT the derivation.
        OperatorAssignmentPort.DelegatedScope delegated =
                new OperatorAssignmentPort.DelegatedScope(
                        List.of("wms"), List.of("OUTBOUND_WRITE", "OUTBOUND_READ"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrantCrossOrg("host-corp", "B2B_ENTERPRISE", null, delegated));
        // host A's ACTIVE entitled domains — a SUPERSET of the delegated slice.
        when(accountServicePort.listEntitledDomains("host-corp"))
                .thenReturn(List.of("wms", "ecommerce", "scm"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        // entitled_domains capped to host ∩ delegated.domains (host-order preserved).
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("wms");
        // roles = delegated.roles verbatim (NOT the derived WMS_OPERATOR_ROLES set).
        assertThat(built.<List<String>>getClaim("roles"))
                .containsExactly("OUTBOUND_WRITE", "OUTBOUND_READ");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("host-corp");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B_ENTERPRISE");
        // partnership does not confine by department → org_scope net-zero [*].
        assertThat(built.<List<String>>getClaim("org_scope")).containsExactly("*");
    }

    @Test
    @DisplayName("BE-478 cross-org: host does NOT subscribe any delegated domain → entitled_domains omitted (least-privilege); roles still verbatim")
    void assumeTenant_crossOrg_emptyIntersection_omitsEntitledDomains() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        OperatorAssignmentPort.DelegatedScope delegated =
                new OperatorAssignmentPort.DelegatedScope(
                        List.of("wms"), List.of("OUTBOUND_WRITE"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrantCrossOrg("host-corp", "B2B_ENTERPRISE", null, delegated));
        // host is entitled only to ecommerce → intersection with [wms] is empty.
        when(accountServicePort.listEntitledDomains("host-corp")).thenReturn(List.of("ecommerce"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.getClaims()).doesNotContainKey("entitled_domains");
        // roles are still emitted verbatim (the gateway 403s anyway — no entitled domain).
        assertThat(built.<List<String>>getClaim("roles")).containsExactly("OUTBOUND_WRITE");
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("host-corp");
    }

    @Test
    @DisplayName("BE-478 cross-org: delegated roles empty → roles claim omitted (capped to nothing)")
    void assumeTenant_crossOrg_emptyRoles_omitsRolesClaim() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        OperatorAssignmentPort.DelegatedScope delegated =
                new OperatorAssignmentPort.DelegatedScope(List.of("wms"), List.of());

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrantCrossOrg("host-corp", "B2B_ENTERPRISE", null, delegated));
        when(accountServicePort.listEntitledDomains("host-corp")).thenReturn(List.of("wms"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("entitled_domains")).containsExactly("wms");
        // no delegated role → roles omitted → the gateway 403s (least-privilege).
        assertThat(built.getClaims()).doesNotContainKey("roles");
    }

    @Test
    @DisplayName("BE-478 cross-org (AC-5): the roles claim carries only domain-operating roles — never an admin role; no admin-scope claim exists")
    void assumeTenant_crossOrg_neverEmitsAdminRoleOrAdminScope() {
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        // The admin-service strips admin roles at invite time (ScopeSet.containsAdminRole
        // → 422); the delegated roles reaching auth-service are domain-operating only.
        OperatorAssignmentPort.DelegatedScope delegated =
                new OperatorAssignmentPort.DelegatedScope(
                        List.of("wms"), List.of("WMS_OPERATOR", "OUTBOUND_WRITE"));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.TOKEN_EXCHANGE);
        when(context.getAuthorizationGrant())
                .thenReturn(assumeGrantCrossOrg("host-corp", "B2B_ENTERPRISE", null, delegated));
        when(accountServicePort.listEntitledDomains("host-corp")).thenReturn(List.of("wms"));
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat(built.<List<String>>getClaim("roles"))
                .doesNotContain("SUPER_ADMIN", "TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        // auth-service never expresses admin scope on any token (the crux invariant:
        // partnership widens domain-operating reach, never admin scope).
        assertThat(built.getClaims()).doesNotContainKey("admin_scope");
        assertThat(built.getClaims()).doesNotContainKey("effective_admin_scope");
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
