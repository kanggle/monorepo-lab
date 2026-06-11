package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reorder_suggestion")
@Getter
@Setter
@NoArgsConstructor
public class ReorderSuggestionJpaEntity {

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
}
