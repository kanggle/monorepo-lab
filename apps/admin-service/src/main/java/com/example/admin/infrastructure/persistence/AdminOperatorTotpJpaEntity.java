package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1:1 row with admin_operators carrying the AES-GCM encrypted TOTP secret,
 * key id, Argon2id-hashed recovery codes JSON array, and enrollment/usage
 * timestamps. Produced by V0013 (TASK-BE-029-2).
 */
@Entity
@Table(name = "admin_operator_totp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOperatorTotpJpaEntity {

    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "secret_encrypted", nullable = false)
    private byte[] secretEncrypted;

    @Column(name = "secret_key_id", length = 64, nullable = false)
    private String secretKeyId;

    @Column(name = "recovery_codes_hashed", columnDefinition = "TEXT", nullable = false)
    private String recoveryCodesHashed;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public static AdminOperatorTotpJpaEntity create(long operatorId,
                                                    byte[] secretEncrypted,
                                                    String secretKeyId,
                                                    String recoveryCodesHashed,
                                                    Instant enrolledAt) {
        AdminOperatorTotpJpaEntity e = new AdminOperatorTotpJpaEntity();
        e.operatorId = operatorId;
        e.secretEncrypted = secretEncrypted;
        e.secretKeyId = secretKeyId;
        e.recoveryCodesHashed = recoveryCodesHashed;
        e.enrolledAt = enrolledAt;
        return e;
    }

    /** Overwrite secret/recovery codes on re-enrollment (allowed per task spec). */
    public void replaceSecret(byte[] secretEncrypted,
                              String secretKeyId,
                              String recoveryCodesHashed,
                              Instant enrolledAt) {
        this.secretEncrypted = secretEncrypted;
        this.secretKeyId = secretKeyId;
        this.recoveryCodesHashed = recoveryCodesHashed;
        this.enrolledAt = enrolledAt;
        this.lastUsedAt = null;
    }

    public void markUsed(Instant at) {
        this.lastUsedAt = at;
    }

    /**
     * Replace the {@code recovery_codes_hashed} JSON array after consuming a
     * recovery code (TASK-BE-029-3). Also updates {@code last_used_at} so the
     * audit trail reflects successful 2FA even on the recovery-code path.
     * Optimistic-lock protection is enforced by the {@link jakarta.persistence.Version}
     * field on save.
     */
    public void replaceRecoveryHashes(String recoveryCodesHashed, Instant usedAt) {
        this.recoveryCodesHashed = recoveryCodesHashed;
        this.lastUsedAt = usedAt;
    }
}
