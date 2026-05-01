package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DomainSyncOAuth2AuthorizationService}.
 *
 * <p>Verifies that refresh token issuance and revocation in SAS's in-memory store
 * is synchronised to the domain {@link RefreshTokenRepository}.
 *
 * <p>TASK-BE-251 Phase 2b.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DomainSyncOAuth2AuthorizationServiceTest {

    @Mock
    private InMemoryOAuth2AuthorizationService delegate;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private DomainSyncOAuth2AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new DomainSyncOAuth2AuthorizationService(delegate, refreshTokenRepository);
    }

    // -----------------------------------------------------------------------
    // save() — new refresh token → persisted to domain store
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save: new authorization with refresh token → persisted to domain store")
    void save_newAuthorizationWithRefreshToken_persistedToDomainStore() {
        RegisteredClient client = buildClient("demo-spa-client", "fan-platform|B2C");
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(2592000);
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("rt-value-001", issuedAt, expiresAt);

        OAuth2Authorization authorization = buildAuthorization(client, "account-123", refreshToken);

        // Token not yet in domain store
        when(refreshTokenRepository.findByJti("rt-value-001")).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.save(authorization);

        verify(delegate).save(authorization);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken persisted = captor.getValue();
        assertThat(persisted.getJti()).isEqualTo("rt-value-001");
        assertThat(persisted.getAccountId()).isEqualTo("account-123");
        assertThat(persisted.getTenantId()).isEqualTo("fan-platform");
        assertThat(persisted.isRevoked()).isFalse();
        assertThat(persisted.getRotatedFrom()).isNull();
    }

    // -----------------------------------------------------------------------
    // save() — already-persisted token → idempotent (no duplicate save)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save: already-persisted refresh token → idempotent, no duplicate save")
    void save_alreadyPersistedRefreshToken_idempotent() {
        RegisteredClient client = buildClient("demo-spa-client", "fan-platform|B2C");
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "rt-already-persisted", Instant.now(), Instant.now().plusSeconds(3600));

        OAuth2Authorization authorization = buildAuthorization(client, "account-999", refreshToken);

        RefreshToken existingDomainToken = RefreshToken.create(
                "rt-already-persisted", "account-999", "fan-platform",
                Instant.now(), Instant.now().plusSeconds(3600), null, null, null);

        // Already in domain store
        when(refreshTokenRepository.findByJti("rt-already-persisted"))
                .thenReturn(Optional.of(existingDomainToken));

        service.save(authorization);

        verify(delegate).save(authorization);
        // save() must NOT be called again for an already-persisted token
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    // -----------------------------------------------------------------------
    // save() — authorization without refresh token → no domain interaction
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save: authorization without refresh token → no domain store write")
    void save_authorizationWithoutRefreshToken_noDomainWrite() {
        RegisteredClient client = buildClient("test-internal-client", "fan-platform|B2C");
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("service-account")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizedScopes(Set.of("account.read"))
                .build();

        service.save(authorization);

        verify(delegate).save(authorization);
        // No refresh token → no domain store interaction
        verifyNoInteractions(refreshTokenRepository);
    }

    // -----------------------------------------------------------------------
    // remove() — triggers revocation in domain store
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("remove: authorization with refresh token → domain store record revoked")
    void remove_authorizationWithRefreshToken_domainRecordRevoked() {
        RegisteredClient client = buildClient("demo-spa-client", "fan-platform|B2C");
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "rt-to-revoke", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = buildAuthorization(client, "account-revoke", refreshToken);

        RefreshToken domainToken = RefreshToken.create(
                "rt-to-revoke", "account-revoke", "fan-platform",
                Instant.now(), Instant.now().plusSeconds(3600), null, null, null);

        when(refreshTokenRepository.findByJti("rt-to-revoke")).thenReturn(Optional.of(domainToken));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.remove(authorization);

        verify(delegate).remove(authorization);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    // -----------------------------------------------------------------------
    // findByToken / findById — delegated directly
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findByToken: delegates to in-memory store")
    void findByToken_delegatesToInMemory() {
        when(delegate.findByToken("some-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);

        service.findByToken("some-token", OAuth2TokenType.ACCESS_TOKEN);

        verify(delegate).findByToken("some-token", OAuth2TokenType.ACCESS_TOKEN);
    }

    @Test
    @DisplayName("findById: delegates to in-memory store")
    void findById_delegatesToInMemory() {
        when(delegate.findById("some-id")).thenReturn(null);

        service.findById("some-id");

        verify(delegate).findById("some-id");
    }

    // -----------------------------------------------------------------------
    // save() — missing clientName metadata → falls back to "fan-platform"
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save: client with no tenant metadata in clientName → tenantId defaults to fan-platform")
    void save_noTenantMetadata_defaultsTenantId() {
        // clientName without "|" separator → extractTenantId returns null → fallback applied
        RegisteredClient client = buildClient("no-tenant-client", "no-metadata-here");
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "rt-no-tenant", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = buildAuthorization(client, "account-nt", refreshToken);

        when(refreshTokenRepository.findByJti("rt-no-tenant")).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.save(authorization);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        // DomainSyncOAuth2AuthorizationService uses access token claims for tenantId;
        // since there's no access token in this authorization, it falls back to "fan-platform"
        assertThat(captor.getValue().getTenantId()).isEqualTo("fan-platform");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisteredClient buildClient(String clientId, String clientName) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .clientName(clientName)
                .build();
    }

    private OAuth2Authorization buildAuthorization(RegisteredClient client, String principalName,
                                                    OAuth2RefreshToken refreshToken) {
        return OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(principalName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .token(refreshToken)
                .build();
    }
}
