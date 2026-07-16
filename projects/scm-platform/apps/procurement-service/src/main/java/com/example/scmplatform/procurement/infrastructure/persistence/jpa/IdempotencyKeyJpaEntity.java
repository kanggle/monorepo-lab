package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity backing the {@code idempotency_keys} table (TASK-BE-445, S2
 * idempotency dedupe — {@code rules/traits/transactional.md} T1). Consumes the
 * table declared in {@code V1__init.sql} that had no entity/repository/consumer
 * before this task, leaving every JWT-authed PO mutation un-deduped.
 *
 * <p>Composite PK {@code (idempotency_key, endpoint, tenant_id)} — the tenant is
 * part of the key so a key reused across tenants never collides. On the first
 * request the row caches the response ({@code response_status} +
 * {@code response_body}); a replay with a matching {@code payload_hash} returns
 * the cached response, a mismatch is rejected 422 by the caller.
 *
 * <p>Lives under {@code infrastructure.persistence.jpa} so it is picked up by the
 * application's {@code @EntityScan} / {@code @EnableJpaRepositories} (same as
 * {@link ProcurementOutboxJpaEntity}).
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyJpaEntity {

    @EmbeddedId
    private Id id;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKeyJpaEntity() {
        // JPA
    }

    public IdempotencyKeyJpaEntity(String idempotencyKey, String endpoint, String tenantId,
                                   String payloadHash, int responseStatus, String responseBody,
                                   Instant createdAt, Instant expiresAt) {
        this.id = new Id(idempotencyKey, endpoint, tenantId);
        this.payloadHash = payloadHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Composite primary key {@code (idempotency_key, endpoint, tenant_id)}. */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "idempotency_key", nullable = false, length = 80)
        private String idempotencyKey;

        @Column(name = "endpoint", nullable = false, length = 120)
        private String endpoint;

        @Column(name = "tenant_id", nullable = false, length = 64)
        private String tenantId;

        protected Id() {
            // JPA
        }

        public Id(String idempotencyKey, String endpoint, String tenantId) {
            this.idempotencyKey = idempotencyKey;
            this.endpoint = endpoint;
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id other)) {
                return false;
            }
            return Objects.equals(idempotencyKey, other.idempotencyKey)
                    && Objects.equals(endpoint, other.endpoint)
                    && Objects.equals(tenantId, other.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idempotencyKey, endpoint, tenantId);
        }
    }
}
