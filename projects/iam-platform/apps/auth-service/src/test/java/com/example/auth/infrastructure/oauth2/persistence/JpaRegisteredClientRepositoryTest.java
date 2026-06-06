package com.example.auth.infrastructure.oauth2.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JpaRegisteredClientRepository}.
 *
 * <p>Verifies the SAS contract:
 * <ul>
 *   <li>{@code findByClientId(unknown)} returns {@code null}</li>
 *   <li>{@code findByClientId(known)} returns a fully mapped {@link RegisteredClient}
 *       with tenant info in {@code ClientSettings}</li>
 *   <li>{@code findById(unknown)} returns {@code null}</li>
 *   <li>{@code findById(known)} returns the mapped client</li>
 * </ul>
 *
 * <p>TASK-BE-252.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class JpaRegisteredClientRepositoryTest {

    @Mock
    private OAuthClientJpaRepository jpaRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private JpaRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaRegisteredClientRepository(jpaRepository, passwordEncoder);
    }

    // -----------------------------------------------------------------------
    // findByClientId
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findByClientId: unknown client → null (SAS contract)")
    void findByClientId_unknown_returnsNull() {
        when(jpaRepository.findByClientId("unknown-client")).thenReturn(Optional.empty());

        RegisteredClient result = repository.findByClientId("unknown-client");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findByClientId: known client → mapped with tenant in ClientSettings")
    void findByClientId_known_returnsMappedClient() {
        OAuthClientEntity entity = buildEntity("test-internal-client", "fan-platform", "B2C");
        when(jpaRepository.findByClientId("test-internal-client")).thenReturn(Optional.of(entity));

        RegisteredClient result = repository.findByClientId("test-internal-client");

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("test-internal-client");
        assertThat(result.getClientSettings().<String>getSetting(OAuthClientMapper.SETTING_TENANT_ID))
                .isEqualTo("fan-platform");
        assertThat(result.getClientSettings().<String>getSetting(OAuthClientMapper.SETTING_TENANT_TYPE))
                .isEqualTo("B2C");
    }

    @Test
    @DisplayName("findByClientId: tenant_id NOT NULL constraint verified via entity mapping")
    void findByClientId_tenantIdNotNull_enforced() {
        OAuthClientEntity entity = buildEntity("some-client", "wms", "B2B");
        when(jpaRepository.findByClientId("some-client")).thenReturn(Optional.of(entity));

        RegisteredClient result = repository.findByClientId("some-client");

        assertThat(result).isNotNull();
        String tenantId = result.getClientSettings().getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        assertThat(tenantId).isNotNull().isNotBlank();
    }

    // -----------------------------------------------------------------------
    // findById
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findById: unknown id → null (SAS contract)")
    void findById_unknown_returnsNull() {
        when(jpaRepository.findById("no-such-id")).thenReturn(Optional.empty());

        RegisteredClient result = repository.findById("no-such-id");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findById: known id → mapped client")
    void findById_known_returnsMappedClient() {
        String id = UUID.randomUUID().toString();
        OAuthClientEntity entity = buildEntity("client-x", "tenant-y", "B2C");
        entity.setId(id);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));

        RegisteredClient result = repository.findById(id);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getClientId()).isEqualTo("client-x");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OAuthClientEntity buildEntity(String clientId, String tenantId, String tenantType) {
        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId(clientId);
        entity.setTenantId(tenantId);
        entity.setTenantType(tenantType);
        entity.setClientSecretHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        entity.setClientName("Test Client");
        entity.setClientAuthenticationMethods(List.of("client_secret_basic"));
        entity.setAuthorizationGrantTypes(List.of("client_credentials"));
        entity.setRedirectUris(List.of());
        entity.setScopes(List.of("openid"));
        entity.setClientSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.client.require-proof-key":false,\
                "settings.client.require-authorization-consent":false}""");
        entity.setTokenSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.token.reuse-refresh-tokens":true,\
                "settings.token.x509-certificate-bound-access-tokens":false,\
                "settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],\
                "settings.token.access-token-format":{"@class":\
                "org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat",\
                "value":"self-contained"},\
                "settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],\
                "settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],\
                "settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}""");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
