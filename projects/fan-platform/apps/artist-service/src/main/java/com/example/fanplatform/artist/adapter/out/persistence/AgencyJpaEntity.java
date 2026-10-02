package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.domain.agency.AgencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** JPA entity for {@code agencies} (V4 — TASK-MONO-748). Package-private. */
@Entity
@Table(name = "agencies")
class AgencyJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AgencyStatus status;

    @Column(name = "store_seller_id", length = 64)
    private String storeSellerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AgencyJpaEntity() {}

    AgencyJpaEntity(String id, String tenantId, String name, AgencyStatus status,
                    String storeSellerId, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.status = status;
        this.storeSellerId = storeSellerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    String getId() { return id; }
    String getTenantId() { return tenantId; }
    String getName() { return name; }
    AgencyStatus getStatus() { return status; }
    String getStoreSellerId() { return storeSellerId; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Long getVersion() { return version; }

    void applyMutable(String name, AgencyStatus status, String storeSellerId, Instant updatedAt) {
        this.name = name;
        this.status = status;
        this.storeSellerId = storeSellerId;
        this.updatedAt = updatedAt;
    }
}
