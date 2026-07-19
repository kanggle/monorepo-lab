package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "inventory_nodes")
@Getter
@Setter
@NoArgsConstructor
public class InventoryNodeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "node_external_id", nullable = false, length = 100)
    private String nodeExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 30)
    private NodeTypeJpa nodeType;

    @Column(name = "name", length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NodeStatusJpa status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_info", columnDefinition = "jsonb")
    private String contactInfo;

    /** Nullable business warehouse code from wms mutation events (ADR-MONO-050 D9). */
    @Column(name = "warehouse_code", length = 100)
    private String warehouseCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum NodeTypeJpa {
        WMS_WAREHOUSE, SUPPLIER, THIRD_PARTY_LOGISTICS, IN_TRANSIT
    }

    public enum NodeStatusJpa {
        ACTIVE, SUSPENDED, DECOMMISSIONED
    }
}
