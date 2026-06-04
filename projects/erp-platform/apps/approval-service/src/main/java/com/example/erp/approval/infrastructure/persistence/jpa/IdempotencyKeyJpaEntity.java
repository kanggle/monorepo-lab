package com.example.erp.approval.infrastructure.persistence.jpa;

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
 * Persistent idempotency-key row (architecture.md § Idempotency, FIN-BE-004
 * final form). Key = {@code (idempotency_key, endpoint, tenant_id)} — the
 * composite PK is the authoritative concurrency gate.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyJpaEntity.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "idempotency_key", length = 80, nullable = false)
    private String idempotencyKey;

    @Id
    @Column(name = "endpoint", length = 120, nullable = false)
    private String endpoint;

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "payload_hash", length = 64, nullable = false)
    private String payloadHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static class Pk implements Serializable {
        private String idempotencyKey;
        private String endpoint;
        private String tenantId;

        public Pk() {
        }

        public Pk(String idempotencyKey, String endpoint, String tenantId) {
            this.idempotencyKey = idempotencyKey;
            this.endpoint = endpoint;
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(idempotencyKey, pk.idempotencyKey)
                    && Objects.equals(endpoint, pk.endpoint)
                    && Objects.equals(tenantId, pk.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idempotencyKey, endpoint, tenantId);
        }
    }
}
