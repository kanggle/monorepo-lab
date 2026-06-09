package com.example.fanplatform.membership.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Subscribe idempotency record. Composite PK = (tenant_id, account_id,
 * idempotency_key). A replay with the same key + same {@code requestFingerprint}
 * returns the stored {@code membershipId} (idempotent); same key + different
 * fingerprint → 409 IDEMPOTENCY_KEY_CONFLICT.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Id
    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Id
    @Column(name = "idempotency_key", length = 80, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 128, nullable = false)
    private String requestFingerprint;

    @Column(name = "membership_id", length = 36, nullable = false)
    private String membershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IdempotencyKey create(String tenantId, String accountId, String idempotencyKey,
                                        String requestFingerprint, String membershipId, Instant createdAt) {
        IdempotencyKey k = new IdempotencyKey();
        k.tenantId = tenantId;
        k.accountId = accountId;
        k.idempotencyKey = idempotencyKey;
        k.requestFingerprint = requestFingerprint;
        k.membershipId = membershipId;
        k.createdAt = createdAt;
        return k;
    }

    public static class Pk implements Serializable {
        private String tenantId;
        private String accountId;
        private String idempotencyKey;

        public Pk() {
        }

        public Pk(String tenantId, String accountId, String idempotencyKey) {
            this.tenantId = tenantId;
            this.accountId = accountId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk that)) return false;
            return Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(accountId, that.accountId)
                    && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, accountId, idempotencyKey);
        }
    }
}
