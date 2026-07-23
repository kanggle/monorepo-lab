package com.example.scmplatform.logistics.adapter.outbound.persistence;

import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mirror of the {@code Dispatch} aggregate. <b>Package-private</b> — a persistence detail
 * that never leaves the adapter (Hexagonal; the domain {@code Dispatch} is the public model).
 */
@Entity
@Table(name = "dispatch")
@Getter
@Setter
@NoArgsConstructor
class DispatchJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "shipment_no", nullable = false)
    private String shipmentNo;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "carrier_code")
    private String carrierCode;

    @Column(name = "tracking_no")
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DispatchStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "vendor")
    private String vendor;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Assigned UUID id + a real {@code @Version} would make Spring Data's default
     * {@code isNew()} treat a freshly-built entity as existing (id non-null, primitive version
     * 0) → {@code merge()} → UPDATE ... WHERE version=0 → 0 rows → StaleObjectStateException on
     * the first save. Persistable drives isNew off a transient flag (mirrors the sibling
     * demand-planning entity).
     */
    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }
}
