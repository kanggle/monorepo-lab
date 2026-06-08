package com.example.product.infrastructure.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Reverse-identity snapshot: wms {@code skuId} (uuid) → {@code skuCode}, built from
 * {@code wms.master.sku.v1} (ADR-MONO-022 §D4 v2(b)). The resolution wms inventory
 * events need but do not carry.
 */
@Entity
@Table(name = "wms_sku_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WmsSkuSnapshotEntity {

    @Id
    @Column(name = "sku_id", columnDefinition = "uuid")
    private UUID skuId;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WmsSkuSnapshotEntity of(UUID skuId, String skuCode, long version, Instant now) {
        WmsSkuSnapshotEntity e = new WmsSkuSnapshotEntity();
        e.skuId = skuId;
        e.skuCode = skuCode;
        e.version = version;
        e.updatedAt = now;
        return e;
    }

    public void update(String skuCode, long version, Instant now) {
        this.skuCode = skuCode;
        this.version = version;
        this.updatedAt = now;
    }
}
