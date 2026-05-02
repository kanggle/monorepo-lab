package com.example.auth.infrastructure.oauth2.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OAuthClientMapper}.
 *
 * <p>Verifies that {@link OAuthClientEntity} ↔ {@link RegisteredClient} round-trip
 * is lossless and that {@code custom.tenant_id} / {@code custom.tenant_type} land in
 * {@link ClientSettings} — not in {@code clientName}.
 *
 * <p>TASK-BE-252.
 */
class OAuthClientMapperTest {

    private OAuthClientMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OAuthClientMapper();
    }

    // -----------------------------------------------------------------------
    // entity → RegisteredClient
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toRegisteredClient: tenant_id and tenant_type land in ClientSettings, not clientName")
    void toRegisteredClient_tenantInfoInClientSettings() {
        OAuthClientEntity entity = buildClientCredentialsEntity("fan-platform", "B2C");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        // Option B: tenant info must be in ClientSettings
        assertThat(client.getClientSettings().<String>getSetting(OAuthClientMapper.SETTING_TENANT_ID))
                .isEqualTo("fan-platform");
        assertThat(client.getClientSettings().<String>getSetting(OAuthClientMapper.SETTING_TENANT_TYPE))
                .isEqualTo("B2C");

        // clientName must NOT carry the old "tenantId|tenantType" encoding
        assertThat(client.getClientName()).doesNotContain("|");
    }

    @Test
    @DisplayName("toRegisteredClient: clientId, scopes, grant types mapped correctly")
    void toRegisteredClient_coreFieldsMapped() {
        OAuthClientEntity entity = buildClientCredentialsEntity("wms", "B2B");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        assertThat(client.getId()).isEqualTo(entity.getId());
        assertThat(client.getClientId()).isEqualTo("test-internal-client");
        assertThat(client.getScopes()).containsExactlyInAnyOrder("account.read", "openid");
        assertThat(client.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }

    @Test
    @DisplayName("toRegisteredClient: BCrypt secret prefixed with {bcrypt} for DelegatingPasswordEncoder")
    void toRegisteredClient_secretPrefixedWithBcrypt() {
        OAuthClientEntity entity = buildClientCredentialsEntity("fan-platform", "B2C");
        entity.setClientSecretHash("$2a$10$somehash");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        assertThat(client.getClientSecret()).startsWith("{bcrypt}");
        assertThat(client.getClientSecret()).contains("$2a$10$somehash");
    }

    @Test
    @DisplayName("toRegisteredClient: public client (null secret hash) has no clientSecret")
    void toRegisteredClient_publicClient_noSecret() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");
        entity.setClientSecretHash(null);

        RegisteredClient client = mapper.toRegisteredClient(entity);

        assertThat(client.getClientSecret()).isNull();
    }

    @Test
    @DisplayName("toRegisteredClient: redirectUris preserved for authorization_code client")
    void toRegisteredClient_redirectUrisPreserved() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        assertThat(client.getRedirectUris()).containsExactly("http://localhost:3000/callback");
    }

    @Test
    @DisplayName("toRegisteredClient: PKCE client has requireProofKey=true in ClientSettings")
    void toRegisteredClient_pkceClientSettings() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
    }

    // -----------------------------------------------------------------------
    // RegisteredClient → entity
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toEntity: tenant_id and tenant_type columns populated from ClientSettings")
    void toEntity_tenantColumnsFromClientSettings() {
        RegisteredClient client = buildRegisteredClient("fan-platform", "B2C");

        OAuthClientEntity entity = mapper.toEntity(client);

        assertThat(entity.getTenantId()).isEqualTo("fan-platform");
        assertThat(entity.getTenantType()).isEqualTo("B2C");
    }

    @Test
    @DisplayName("toEntity: {noop} secret stripped to raw hash value in entity")
    void toEntity_noopSecretStripped() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("strip-test-client")
                .clientSecret("{noop}plain-text")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientMapper.SETTING_TENANT_ID, "tenant-x")
                        .setting(OAuthClientMapper.SETTING_TENANT_TYPE, "B2B")
                        .build())
                .tokenSettings(TokenSettings.builder().build())
                .build();

        OAuthClientEntity entity = mapper.toEntity(client);

        // {noop} prefix stripped; raw value stored (caller hashes it on save)
        assertThat(entity.getClientSecretHash()).isEqualTo("plain-text");
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("round-trip entity → RegisteredClient → entity preserves tenant and scope fields")
    void roundTrip_preservesFields() {
        OAuthClientEntity original = buildClientCredentialsEntity("acme", "B2B");

        RegisteredClient client = mapper.toRegisteredClient(original);
        OAuthClientEntity restored = mapper.toEntity(client);

        assertThat(restored.getClientId()).isEqualTo(original.getClientId());
        assertThat(restored.getTenantId()).isEqualTo("acme");
        assertThat(restored.getTenantType()).isEqualTo("B2B");
        assertThat(restored.getScopes()).containsExactlyInAnyOrderElementsOf(original.getScopes());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OAuthClientEntity buildClientCredentialsEntity(String tenantId, String tenantType) {
        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId("test-internal-client");
        entity.setTenantId(tenantId);
        entity.setTenantType(tenantType);
        entity.setClientSecretHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        entity.setClientName("Test Internal Client");
        entity.setClientAuthenticationMethods(List.of("client_secret_basic"));
        entity.setAuthorizationGrantTypes(List.of("client_credentials"));
        entity.setRedirectUris(List.of());
        entity.setScopes(List.of("account.read", "openid"));
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
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        return entity;
    }

    private OAuthClientEntity buildPkceClientEntity(String tenantId, String tenantType) {
        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId("demo-spa-client");
        entity.setTenantId(tenantId);
        entity.setTenantType(tenantType);
        entity.setClientSecretHash(null);
        entity.setClientName("Demo SPA Client");
        entity.setClientAuthenticationMethods(List.of("none"));
        entity.setAuthorizationGrantTypes(List.of("authorization_code", "refresh_token"));
        entity.setRedirectUris(List.of("http://localhost:3000/callback"));
        entity.setScopes(List.of("openid", "profile", "email"));
        entity.setClientSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.client.require-proof-key":true,\
                "settings.client.require-authorization-consent":false}""");
        entity.setTokenSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.token.reuse-refresh-tokens":false,\
                "settings.token.x509-certificate-bound-access-tokens":false,\
                "settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],\
                "settings.token.access-token-format":{"@class":\
                "org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat",\
                "value":"self-contained"},\
                "settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],\
                "settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],\
                "settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}""");
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        return entity;
    }

    private RegisteredClient buildRegisteredClient(String tenantId, String tenantType) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("round-trip-client")
                .clientName("Round Trip Client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("openid")
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientMapper.SETTING_TENANT_ID, tenantId)
                        .setting(OAuthClientMapper.SETTING_TENANT_TYPE, tenantType)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .build())
                .build();
    }
}
