package com.example.auth.infrastructure.oauth2.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * JPA entity backing the {@code oauth_clients} table.
 *
 * <p>JSON columns (client_authentication_methods, authorization_grant_types,
 * redirect_uris, scopes) are mapped to {@code List<String>} via
 * {@link StringListJsonConverter} (works on both MySQL native JSON and H2 VARCHAR).
 *
 * <p>client_settings and token_settings are stored as JSON strings (raw serialized
 * SAS {@code ClientSettings} / {@code TokenSettings} maps) and handled by
 * {@link OAuthClientMapper} using the SAS Jackson-based serializer.
 *
 * <p>TASK-BE-252 — replaces in-memory placeholder clients.
 */
@Entity
@Table(name = "oauth_clients")
@Getter
@Setter
@NoArgsConstructor
public class OAuthClientEntity {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "client_id", length = 100, nullable = false, unique = true)
    private String clientId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "tenant_type", length = 32, nullable = false)
    private String tenantType;

    /** BCrypt hash; NULL for public PKCE clients. */
    @Column(name = "client_secret_hash", length = 200)
    private String clientSecretHash;

    @Column(name = "client_name", length = 200, nullable = false)
    private String clientName;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "client_authentication_methods", nullable = false)
    private List<String> clientAuthenticationMethods;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "authorization_grant_types", nullable = false)
    private List<String> authorizationGrantTypes;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "redirect_uris", nullable = false)
    private List<String> redirectUris;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "scopes", nullable = false)
    private List<String> scopes;

    /**
     * SAS {@code ClientSettings} serialized to JSON by
     * {@link org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module}.
     * Stored as raw String; deserialized by {@link OAuthClientMapper}.
     */
    @Column(name = "client_settings", nullable = false, length = 2000)
    private String clientSettings;

    /**
     * SAS {@code TokenSettings} serialized to JSON.
     * Stored as raw String; deserialized by {@link OAuthClientMapper}.
     */
    @Column(name = "token_settings", nullable = false, length = 2000)
    private String tokenSettings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
