package com.example.auth.infrastructure.oauth2.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts between {@link OAuthClientEntity} (JPA) and SAS {@link RegisteredClient}.
 *
 * <h3>Option B — tenant_id / tenant_type in ClientSettings</h3>
 * <p>Rather than encoding tenant metadata in {@code clientName} (the Phase 1 placeholder),
 * this mapper stores it as custom keys in {@link ClientSettings}:
 * <ul>
 *   <li>{@code "custom.tenant_id"} — the owning tenant's identifier</li>
 *   <li>{@code "custom.tenant_type"} — e.g. {@code "B2C"} or {@code "B2B"}</li>
 * </ul>
 * {@link com.example.auth.infrastructure.oauth2.TenantClaimTokenCustomizer} and
 * {@link com.example.auth.infrastructure.oauth2.SasRefreshTokenAuthenticationProvider}
 * read these custom settings instead of splitting {@code clientName}.
 *
 * <h3>Secret handling</h3>
 * <p>The mapper prepends {@code "{bcrypt}"} to the stored hash so that Spring Security's
 * {@code DelegatingPasswordEncoder} routes the comparison to BCryptPasswordEncoder.
 * If the entity has no hash (public PKCE client), no clientSecret is set.
 *
 * <p>TASK-BE-252.
 */
@Slf4j
public class OAuthClientMapper {

    /** Custom ClientSettings key for the owning tenant's identifier. */
    public static final String SETTING_TENANT_ID = "custom.tenant_id";

    /** Custom ClientSettings key for the tenant type (e.g. B2C, B2B). */
    public static final String SETTING_TENANT_TYPE = "custom.tenant_type";

    /**
     * SAS-enriched ObjectMapper — used to read/write ClientSettings and TokenSettings.
     *
     * <p>Includes {@link SecurityJackson2Modules} and
     * {@link OAuth2AuthorizationServerJackson2Module} so that:
     * <ul>
     *   <li>{@code Duration} values serialize as {@code ["java.time.Duration", seconds]} arrays
     *       and deserialize back to {@link java.time.Duration} objects.</li>
     *   <li>{@code OAuth2TokenFormat} serializes/deserializes with its {@code @class} marker.</li>
     *   <li>The root {@code Map<String,Object>} is wrapped with
     *       {@code @class":"java.util.Collections$UnmodifiableMap"} on write, satisfying
     *       the polymorphic read requirement on the same mapper.</li>
     * </ul>
     *
     * <p>Both serialization (toEntity) and deserialization (toRegisteredClient) use this mapper,
     * ensuring the stored JSON format is always consistent (always contains @class markers).
     */
    private static final ObjectMapper SAS_MAPPER = buildSasMapper();

    public OAuthClientMapper() {
    }

    private static ObjectMapper buildSasMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register SAS modules first (they may call enableDefaultTyping internally).
        List<Module> modules = SecurityJackson2Modules.getModules(
                OAuthClientMapper.class.getClassLoader());
        mapper.registerModules(modules);
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        // Enable default typing AFTER module registration so that the allowlist-based
        // type resolver is applied if not already set by a module.
        // This enables @class markers for UnmodifiableMap, Duration, OAuth2TokenFormat etc.
        // matching the V0008 seed data format and JdbcOAuth2AuthorizationService expectations.
        SecurityJackson2Modules.enableDefaultTyping(mapper);
        return mapper;
    }

    // -----------------------------------------------------------------------
    // Entity → RegisteredClient
    // -----------------------------------------------------------------------

    /**
     * Converts a persisted {@link OAuthClientEntity} into a SAS {@link RegisteredClient}.
     *
     * <p>The resulting {@code RegisteredClient} carries {@code custom.tenant_id} and
     * {@code custom.tenant_type} in its {@link ClientSettings} so that downstream
     * components (TenantClaimTokenCustomizer, SasRefreshTokenAuthenticationProvider)
     * can extract tenant context without parsing {@code clientName}.
     *
     * @return a fully populated {@link RegisteredClient}; never null
     * @throws OAuthClientMappingException if JSON deserialization fails
     */
    public RegisteredClient toRegisteredClient(OAuthClientEntity entity) {
        ClientSettings clientSettings = deserializeClientSettings(entity);
        TokenSettings tokenSettings = deserializeTokenSettings(entity);

        RegisteredClient.Builder builder = RegisteredClient.withId(entity.getId())
                .clientId(entity.getClientId())
                .clientName(entity.getClientName())
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings);

        // Secret: the stored hash already carries the DelegatingPasswordEncoder prefix
        // (e.g. "{bcrypt}$2a$10$..."). Pass it directly — SAS will use the app's
        // PasswordEncoder (DelegatingPasswordEncoder) for verification.
        // V0008 seed data stores BCrypt hashes without prefix; prepend {bcrypt} in that case.
        if (entity.getClientSecretHash() != null) {
            String hash = entity.getClientSecretHash();
            String secret = hash.startsWith("{") ? hash : "{bcrypt}" + hash;
            builder.clientSecret(secret);
        }

        for (String method : entity.getClientAuthenticationMethods()) {
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method));
        }

        for (String grantType : entity.getAuthorizationGrantTypes()) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }

        for (String uri : entity.getRedirectUris()) {
            builder.redirectUri(uri);
        }

        for (String scope : entity.getScopes()) {
            builder.scope(scope);
        }

        return builder.build();
    }

    // -----------------------------------------------------------------------
    // RegisteredClient → Entity
    // -----------------------------------------------------------------------

    /**
     * Converts a SAS {@link RegisteredClient} into an {@link OAuthClientEntity}.
     *
     * <p>Reads {@code custom.tenant_id} / {@code custom.tenant_type} from
     * {@link ClientSettings} to populate the entity's dedicated columns.
     * Falls back to an empty string if not present (should not occur in practice
     * since all save paths go through JpaRegisteredClientRepository which validates).
     *
     * @throws OAuthClientMappingException if JSON serialization fails
     */
    public OAuthClientEntity toEntity(RegisteredClient client) {
        OAuthClientEntity entity = new OAuthClientEntity();
        entity.setId(client.getId() != null ? client.getId() : UUID.randomUUID().toString());
        entity.setClientId(client.getClientId());
        entity.setClientName(client.getClientName() != null ? client.getClientName() : client.getClientId());

        ClientSettings cs = client.getClientSettings();
        Object rawTenantId = cs.getSetting(SETTING_TENANT_ID);
        Object rawTenantType = cs.getSetting(SETTING_TENANT_TYPE);
        entity.setTenantId(rawTenantId != null ? rawTenantId.toString() : "");
        entity.setTenantType(rawTenantType != null ? rawTenantType.toString() : "");

        // Strip {bcrypt} / {noop} prefix if already encoded by DelegatingPasswordEncoder
        String secret = client.getClientSecret();
        if (secret != null) {
            entity.setClientSecretHash(stripPasswordEncoderPrefix(secret));
        }

        entity.setClientAuthenticationMethods(
                client.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue)
                        .toList());

        entity.setAuthorizationGrantTypes(
                client.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue)
                        .toList());

        entity.setRedirectUris(List.copyOf(client.getRedirectUris()));
        entity.setScopes(List.copyOf(client.getScopes()));

        entity.setClientSettings(serializeSettings(client.getClientSettings().getSettings()));
        entity.setTokenSettings(serializeSettings(client.getTokenSettings().getSettings()));

        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return entity;
    }

    // -----------------------------------------------------------------------
    // JSON deserialization helpers
    // -----------------------------------------------------------------------

    private ClientSettings deserializeClientSettings(OAuthClientEntity entity) {
        try {
            Map<String, Object> rawSettings = SAS_MAPPER.readValue(
                    entity.getClientSettings(), new TypeReference<>() {});

            // Build via SAS builder first to ensure standard keys are typed correctly,
            // then add our custom tenant keys. The builder produces a mutable map internally.
            ClientSettings baseSettings = ClientSettings.withSettings(rawSettings).build();

            // Inject tenant context (Option B): start from base settings + add custom keys
            return ClientSettings.withSettings(baseSettings.getSettings())
                    .setting(SETTING_TENANT_ID, entity.getTenantId())
                    .setting(SETTING_TENANT_TYPE, entity.getTenantType())
                    .build();
        } catch (Exception e) {
            log.error("Failed to deserialize ClientSettings for clientId={}: {}",
                    entity.getClientId(), e.getMessage());
            throw new OAuthClientMappingException(
                    "Cannot deserialize ClientSettings for clientId=" + entity.getClientId(), e);
        }
    }

    private TokenSettings deserializeTokenSettings(OAuthClientEntity entity) {
        try {
            Map<String, Object> settings = SAS_MAPPER.readValue(
                    entity.getTokenSettings(), new TypeReference<>() {});
            return TokenSettings.withSettings(settings).build();
        } catch (Exception e) {
            log.error("Failed to deserialize TokenSettings for clientId={}: {}",
                    entity.getClientId(), e.getMessage());
            throw new OAuthClientMappingException(
                    "Cannot deserialize TokenSettings for clientId=" + entity.getClientId(), e);
        }
    }

    private String serializeSettings(Map<String, Object> settings) {
        try {
            return SAS_MAPPER.writeValueAsString(settings);
        } catch (Exception e) {
            throw new OAuthClientMappingException("Cannot serialize settings map", e);
        }
    }

    private String stripPasswordEncoderPrefix(String encodedSecret) {
        // DelegatingPasswordEncoder format: {encoderId}hash
        if (encodedSecret.startsWith("{")) {
            int end = encodedSecret.indexOf('}');
            if (end > 0) {
                return encodedSecret.substring(end + 1);
            }
        }
        return encodedSecret;
    }

}
