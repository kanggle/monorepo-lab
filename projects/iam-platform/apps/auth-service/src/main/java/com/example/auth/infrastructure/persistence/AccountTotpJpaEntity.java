package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.infrastructure.oauth2.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-771 — JPA mapping of {@code account_totp} (V0043). {@code recovery_codes_hashed} is a MySQL
 * {@code JSON} column mapped through {@link StringListJsonConverter} — the same mapping the OAuth client tables
 * use, so it validates on MySQL and builds on the H2 slice.
 *
 * <p>Restricted columns ({@code secret_encrypted}, {@code recovery_codes_hashed}) — no {@code toString}, never
 * logged.
 */
@Entity
@Table(name = "account_totp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountTotpJpaEntity {

    @Id
    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "secret_encrypted", nullable = false, length = 255)
    private byte[] secretEncrypted;

    @Column(name = "secret_key_id", nullable = false, length = 64)
    private String secretKeyId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "recovery_codes_hashed")
    private List<String> recoveryCodesHashed;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public AccountTotp toDomain() {
        // The converter reads a NULL column as an empty list; a pending row has no recovery codes at all.
        return new AccountTotp(accountId, tenantId, secretEncrypted, secretKeyId, confirmedAt,
                confirmedAt == null ? null : recoveryCodesHashed, lastUsedStep, lastUsedAt,
                createdAt, updatedAt, version);
    }

    /** A new row (no version yet). */
    public static AccountTotpJpaEntity fromDomain(AccountTotp totp) {
        AccountTotpJpaEntity entity = new AccountTotpJpaEntity();
        entity.accountId = totp.getAccountId();
        entity.version = totp.getVersion();
        entity.apply(totp);
        return entity;
    }

    /** Copies the mutable state of {@code totp} onto this managed row (version stays Hibernate's). */
    public void apply(AccountTotp totp) {
        this.tenantId = totp.getTenantId();
        this.secretEncrypted = totp.getSecretCiphertext();
        this.secretKeyId = totp.getSecretKeyId();
        this.confirmedAt = totp.getConfirmedAt();
        this.recoveryCodesHashed = totp.hasNoRecoveryCodeColumn()
                ? null : new java.util.ArrayList<>(totp.getRecoveryCodeHashes());
        this.lastUsedStep = totp.getLastUsedStep();
        this.lastUsedAt = totp.getLastUsedAt();
        this.createdAt = totp.getCreatedAt();
        this.updatedAt = totp.getUpdatedAt();
    }
}
