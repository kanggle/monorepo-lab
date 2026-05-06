package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class InventorySnapshotJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "node_id", nullable = false, length = 36)
    private String nodeId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "last_event_id", nullable = false, length = 36)
    private String lastEventId;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
