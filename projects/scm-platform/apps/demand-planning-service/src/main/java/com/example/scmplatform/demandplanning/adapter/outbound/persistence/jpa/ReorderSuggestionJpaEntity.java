package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
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

@Entity
@Table(name = "reorder_suggestion")
@Getter
@Setter
@NoArgsConstructor
public class ReorderSuggestionJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "suggested_qty", nullable = false)
    private int suggestedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private SuggestionSource source;

    @Column(name = "trigger_event_id")
    private UUID triggerEventId;

    @Column(name = "trigger_available_qty")
    private Integer triggerAvailableQty;

    @Column(name = "materialized_po_id")
    private UUID materializedPoId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Assigned UUID id + a real {@code @Version} would make Spring Data's default
     * {@code isNew()} treat a freshly-built entity as existing (id is non-null,
     * primitive version defaults to 0) → {@code merge()} → UPDATE ... WHERE version
     * → 0 rows → StaleObjectStateException on the first save. Persistable drives
     * isNew off a transient flag so a new entity is persisted (INSERT) and a
     * loaded one is merged.
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
