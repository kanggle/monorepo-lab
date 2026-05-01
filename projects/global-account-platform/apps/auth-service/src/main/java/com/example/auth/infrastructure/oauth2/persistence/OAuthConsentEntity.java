package com.example.auth.infrastructure.oauth2.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * JPA entity backing the {@code oauth_consent} table.
 *
 * <p>Records what scopes a principal has consented to for a given client.
 * {@code revokedAt = null} means the consent is currently active.
 *
 * <p>TASK-BE-252.
 */
@Entity
@Table(name = "oauth_consent")
@IdClass(OAuthConsentId.class)
@Getter
@Setter
@NoArgsConstructor
public class OAuthConsentEntity {

    @Id
    @Column(name = "principal_id", length = 100, nullable = false)
    private String principalId;

    @Id
    @Column(name = "client_id", length = 100, nullable = false)
    private String clientId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "granted_scopes", nullable = false)
    private List<String> grantedScopes;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /** NULL = consent still active; non-null = consent revoked. */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
