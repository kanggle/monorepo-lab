package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_login_succeeded_at")
    private Instant lastLoginSucceededAt;

    /**
     * TASK-BE-114: email verification timestamp. NULL until the user completes
     * the verify-email flow. Existing rows on V0008 migration are NULL.
     */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Version
    @Column(nullable = false)
    private int version;

    public static AccountJpaEntity fromDomain(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.id = account.getId();
        entity.tenantId = account.getTenantId().value();
        entity.email = account.getEmail();
        entity.emailHash = account.getEmailHash();
        entity.status = account.getStatus();
        entity.createdAt = account.getCreatedAt();
        entity.updatedAt = account.getUpdatedAt();
        entity.deletedAt = account.getDeletedAt();
        entity.lastLoginSucceededAt = account.getLastLoginSucceededAt();
        entity.emailVerifiedAt = account.getEmailVerifiedAt();
        entity.version = account.getVersion();
        return entity;
    }

    public Account toDomain() {
        return Account.reconstitute(id, new TenantId(tenantId), email, emailHash, status,
                createdAt, updatedAt, deletedAt, lastLoginSucceededAt, emailVerifiedAt, version);
    }
}
