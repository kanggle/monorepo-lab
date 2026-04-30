package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Stores the canonical response body for a previously executed bulk-lock
 * request so that a retry with the same {@code (operatorId, idempotencyKey)}
 * pair can return byte-identical output without re-executing any downstream
 * call or writing any additional {@code admin_actions} rows.
 *
 * <p>Rows are written once per successful first-execution and then read-only.
 * TTL / retention enforcement is out-of-scope for TASK-BE-030.
 */
@Entity
@Table(name = "admin_bulk_lock_idempotency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulkLockIdempotencyJpaEntity {

    @EmbeddedId
    private Key id;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "TEXT", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static BulkLockIdempotencyJpaEntity create(Long operatorId,
                                                     String idempotencyKey,
                                                     String requestHash,
                                                     String responseBody,
                                                     Instant createdAt) {
        BulkLockIdempotencyJpaEntity e = new BulkLockIdempotencyJpaEntity();
        e.id = new Key(operatorId, idempotencyKey);
        e.requestHash = requestHash;
        e.responseBody = responseBody;
        e.createdAt = createdAt;
        return e;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "operator_id", nullable = false)
        private Long operatorId;

        @Column(name = "idempotency_key", length = 64, nullable = false)
        private String idempotencyKey;

        public Key(Long operatorId, String idempotencyKey) {
            this.operatorId = operatorId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(operatorId, k.operatorId)
                    && Objects.equals(idempotencyKey, k.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operatorId, idempotencyKey);
        }
    }
}
