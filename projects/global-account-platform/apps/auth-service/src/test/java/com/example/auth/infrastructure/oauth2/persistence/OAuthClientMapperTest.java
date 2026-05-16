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
    // TASK-BE-297 — array-valued custom ClientSettings under SAS default typing
    // -----------------------------------------------------------------------

    /**
     * Regression for TASK-BE-297.
     *
     * <p>V0011 / V0012 seeded {@code settings.client.post-logout-redirect-uris}
     * as a <b>plain JSON array</b> {@code ["http://localhost:3000/","http://fan-platform.local/"]}.
     * {@code OAuthClientMapper}'s SAS-enriched ObjectMapper has
     * {@code SecurityJackson2Modules.enableDefaultTyping} active: a collection
     * value must carry a {@code [typeId, value]} wrapper-array envelope, where
     * {@code typeId} is an allow-listed class (e.g. {@code java.util.ArrayList}).
     * A plain 2-element string array is read as {@code [typeId="http://localhost:3000/", value=...]}
     * → {@code InvalidTypeIdException} → {@code OAuthClientMappingException}.
     *
     * <p>This test asserts the CORRECTIVE typed form (the form produced by
     * {@code V0016}) deserializes cleanly and that the effective
     * {@code post-logout-redirect-uris} setting is the exact same
     * {@code List<String>}.
     */
    @Test
    @DisplayName("toRegisteredClient: SAS-typed array custom setting (post-logout-redirect-uris) round-trips")
    void toRegisteredClient_typedArrayCustomSetting_deserializesCleanly() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");
        entity.setClientId("fan-platform-user-flow-client");
        // The corrective V0016 form: array wrapped as [typeId, value] with an
        // allow-listed concrete collection type id (java.util.ArrayList).
        entity.setClientSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.client.require-proof-key":true,\
                "settings.client.require-authorization-consent":false,\
                "settings.client.post-logout-redirect-uris":\
                ["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]}""");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        List<String> postLogout = client.getClientSettings()
                .getSetting("settings.client.post-logout-redirect-uris");
        assertThat(postLogout)
                .as("post-logout-redirect-uris must deserialize as the exact List<String>")
                .containsExactly("http://localhost:3000/", "http://fan-platform.local/");
        // Standard SAS keys still correct alongside the custom array setting.
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
    }

    /**
     * Negative control for TASK-BE-297: proves the ORIGINAL (defective) V0011 /
     * V0012 form — a plain JSON array with no {@code [typeId, value]} envelope —
     * is exactly what throws, justifying the corrective migration. If SAS ever
     * changes its default-typing behaviour so a plain array deserializes
     * cleanly, this test fails and the migration can be revisited.
     */
    @Test
    @DisplayName("toRegisteredClient: ORIGINAL plain-array post-logout-redirect-uris throws (defect proof)")
    void toRegisteredClient_plainArrayCustomSetting_throws() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");
        entity.setClientId("fan-platform-user-flow-client");
        entity.setClientSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.client.require-proof-key":true,\
                "settings.client.require-authorization-consent":false,\
                "settings.client.post-logout-redirect-uris":\
                ["http://localhost:3000/","http://fan-platform.local/"]}""");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> mapper.toRegisteredClient(entity))
                .isInstanceOf(OAuthClientMappingException.class)
                .hasMessageContaining("fan-platform-user-flow-client");
    }

    /**
     * TASK-BE-297: the corrective typed form must SERIALIZE back (toEntity)
     * without loss — i.e. a clean save path keeps the array typed. This pins
     * the exact wrapper-array shape the V0016 migration must write so future
     * mapper changes that would silently alter it are caught.
     */
    @Test
    @DisplayName("round-trip: typed array custom setting survives entity → RegisteredClient → entity")
    void roundTrip_typedArrayCustomSetting_preserved() {
        OAuthClientEntity original = buildPkceClientEntity("fan-platform", "B2C");
        original.setClientId("fan-platform-user-flow-client");
        original.setClientSettings("""
                {"@class":"java.util.Collections$UnmodifiableMap",\
                "settings.client.require-proof-key":true,\
                "settings.client.require-authorization-consent":false,\
                "settings.client.post-logout-redirect-uris":\
                ["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]}""");

        RegisteredClient client = mapper.toRegisteredClient(original);
        OAuthClientEntity restored = mapper.toEntity(client);

        // Re-read the re-serialized settings: the array must STILL be present
        // and STILL deserialize to the same List<String> (no envelope loss).
        OAuthClientEntity reloaded = new OAuthClientEntity();
        reloaded.setId(restored.getId());
        reloaded.setClientId(restored.getClientId());
        reloaded.setTenantId(restored.getTenantId());
        reloaded.setTenantType(restored.getTenantType());
        reloaded.setClientName(restored.getClientName());
        reloaded.setClientAuthenticationMethods(restored.getClientAuthenticationMethods());
        reloaded.setAuthorizationGrantTypes(restored.getAuthorizationGrantTypes());
        reloaded.setRedirectUris(restored.getRedirectUris());
        reloaded.setScopes(restored.getScopes());
        reloaded.setClientSettings(restored.getClientSettings());
        reloaded.setTokenSettings(restored.getTokenSettings());

        RegisteredClient roundTripped = mapper.toRegisteredClient(reloaded);
        List<String> postLogout = roundTripped.getClientSettings()
                .getSetting("settings.client.post-logout-redirect-uris");
        assertThat(postLogout)
                .as("post-logout-redirect-uris must survive a full mapper round-trip")
                .containsExactly("http://localhost:3000/", "http://fan-platform.local/");
    }

    /**
     * TASK-BE-297 (PR #571 corrective): pins the value as the mapper reads it
     * back <b>through a MySQL {@code JSON} column</b>, not as a hand-built
     * pre-normalization string.
     *
     * <p>{@code oauth_clients.client_settings} is a MySQL native {@code JSON}
     * column. After the corrected V0016
     * ({@code JSON_SET(cs, '$."..."', JSON_ARRAY('java.util.ArrayList',
     * JSON_EXTRACT(cs, '$."..."')))}) MySQL stores — and on read renders — the
     * value in its <b>normalized</b> canonical form: object members re-ordered,
     * a space reinserted after every {@code :} and {@code ,}. This test feeds
     * the mapper exactly that normalized rendering (the real stored shape, the
     * thing the previous hand-built-string tests never exercised) and proves it
     * still deserializes to the exact {@code List<String>}.
     *
     * <p>The companion {@code OAuthClientPostLogoutRedirectUriSeedIntegrationTest}
     * proves the same end-to-end against a real MySQL Testcontainer; this unit
     * test is the non-Docker early-warning that the corrective envelope is
     * read-compatible with MySQL's normalized JSON rendering.
     */
    @Test
    @DisplayName("toRegisteredClient: MySQL-JSON-normalized corrective form (reordered + spaced) round-trips")
    void toRegisteredClient_mysqlNormalizedCorrectiveForm_deserializesCleanly() {
        OAuthClientEntity entity = buildPkceClientEntity("fan-platform", "B2C");
        entity.setClientId("fan-platform-user-flow-client");
        // Exactly how MySQL renders the JSON column AFTER the corrected V0016:
        //  - JSON_SET wrapped the array as ["java.util.ArrayList", [ ...uris ]]
        //  - MySQL re-orders object members and inserts a space after every
        //    ':' and ',' in its canonical JSON->text rendering. Member order
        //    below is MySQL 8.0 ordering (by key length, then bytewise) — the
        //    point is that the mapper's Jackson reader is order/space tolerant.
        entity.setClientSettings(
                "{\"@class\": \"java.util.Collections$UnmodifiableMap\", "
                        + "\"settings.client.require-proof-key\": true, "
                        + "\"settings.client.require-authorization-consent\": false, "
                        + "\"settings.client.post-logout-redirect-uris\": "
                        + "[\"java.util.ArrayList\", "
                        + "[\"http://localhost:3000/\", \"http://fan-platform.local/\"]]}");

        RegisteredClient client = mapper.toRegisteredClient(entity);

        List<String> postLogout = client.getClientSettings()
                .getSetting("settings.client.post-logout-redirect-uris");
        assertThat(postLogout)
                .as("MySQL-normalized corrective form must deserialize to the exact List<String>")
                .containsExactly("http://localhost:3000/", "http://fan-platform.local/");
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
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
