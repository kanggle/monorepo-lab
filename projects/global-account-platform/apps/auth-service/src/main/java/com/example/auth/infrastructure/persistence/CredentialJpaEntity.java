package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.credentials.Credential;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredentialJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TASK-BE-229: tenant_id added — composite unique (tenant_id, email).
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", nullable = false, unique = true, length = 36)
    private String accountId;

    // TASK-BE-063: Email is denormalized onto auth_db.credentials so auth-service
    // can resolve email → credential without a cross-service call.
    // TASK-BE-229: unique index changed from email alone to (tenant_id, email).
    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "credential_hash", nullable = false)
    private String credentialHash;

    @Column(name = "hash_algorithm", nullable = false, length = 30)
    private String hashAlgorithm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public Credential toDomain() {
        return new Credential(id, accountId,
                tenantId != null ? tenantId : "fan-platform",
                email, credentialHash, hashAlgorithm, createdAt, updatedAt, version);
    }

    public static CredentialJpaEntity fromDomain(Credential credential) {
        CredentialJpaEntity entity = new CredentialJpaEntity();
        entity.id = credential.getId();
        entity.tenantId = credential.getTenantId();
        entity.accountId = credential.getAccountId();
        entity.email = credential.getEmail();
        entity.credentialHash = credential.getCredentialHash();
        entity.hashAlgorithm = credential.getHashAlgorithm();
        entity.createdAt = credential.getCreatedAt();
        entity.updatedAt = credential.getUpdatedAt();
        entity.version = credential.getVersion();
        return entity;
    }
}
