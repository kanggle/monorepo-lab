package com.wms.admin.readmodel.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Projected from {@code wms.inventory.*}. Composite PK
 * {@code (location_id, sku_id, lot_id)}. The {@code lot_id} slot uses
 * {@link InventorySnapshotId#NULL_SENTINEL} for non-LOT-tracked rows.
 */
@Entity
@Table(name = "admin_inventory_snapshot")
@IdClass(InventorySnapshotId.class)
public class InventorySnapshotEntity {

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Id
    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Id
    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /**
     * 🔴 TASK-MONO-659 — 창고 코드. 형제 셋(location/sku/lot)은 처음부터 비정규화돼 있었고
     * 창고만 빠져 있어서, 콘솔이 그 칸에 raw UUID 를 그렸다. 그 화면은 이 값이 도착하지
     * 않으면 아무것도 할 수 없다(생산자만 실어 줄 수 있다).
     * 🔵 `warehouseId` 는 **그대로 둔다** — UUID 로 조회하는 경로가 있다.
     */
    @Column(name = "warehouse_code", length = 40)
    private String warehouseCode;

    @Column(name = "location_code", length = 80)
    private String locationCode;

    @Column(name = "sku_code", length = 40)
    private String skuCode;

    @Column(name = "lot_no", length = 80)
    private String lotNo;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "damaged_qty", nullable = false)
    private int damagedQty;

    @Column(name = "on_hand_qty", nullable = false)
    private int onHandQty;

    @Column(name = "low_stock_flag", nullable = false)
    private boolean lowStockFlag;

    @Column(name = "last_adjusted_at")
    private Instant lastAdjustedAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventorySnapshotEntity() {
    }

    public InventorySnapshotEntity(UUID locationId, UUID skuId, UUID lotId, UUID warehouseId,
                                   String warehouseCode, String locationCode, String skuCode,
                                   String lotNo,
                                   int availableQty, int reservedQty, int damagedQty,
                                   boolean lowStockFlag, Instant lastAdjustedAt,
                                   Instant lastEventAt) {
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId == null ? InventorySnapshotId.NULL_SENTINEL : lotId;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.locationCode = locationCode;
        this.skuCode = skuCode;
        this.lotNo = lotNo;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.damagedQty = damagedQty;
        this.onHandQty = availableQty + reservedQty + damagedQty;
        this.lowStockFlag = lowStockFlag;
        this.lastAdjustedAt = lastAdjustedAt;
        this.lastEventAt = lastEventAt;
    }

    public void apply(UUID warehouseId, String warehouseCode, String locationCode,
                      String skuCode, String lotNo,
                      int availableQty, int reservedQty, int damagedQty, boolean lowStockFlag,
                      Instant lastAdjustedAt, Instant lastEventAt) {
        if (warehouseId != null) {
            this.warehouseId = warehouseId;
        }
        // 🔵 형제들과 같은 «null 이면 안 덮는다» 규칙 — 참조가 아직 투영되지 않았을 때
        //    이미 채워 둔 코드를 지우지 않기 위해서다.
        if (warehouseCode != null) {
            this.warehouseCode = warehouseCode;
        }
        if (locationCode != null) {
            this.locationCode = locationCode;
        }
        if (skuCode != null) {
            this.skuCode = skuCode;
        }
        if (lotNo != null) {
            this.lotNo = lotNo;
        }
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.damagedQty = damagedQty;
        this.onHandQty = availableQty + reservedQty + damagedQty;
        this.lowStockFlag = lowStockFlag;
        if (lastAdjustedAt != null) {
            this.lastAdjustedAt = lastAdjustedAt;
        }
        this.lastEventAt = lastEventAt;
    }

    public UUID getLocationId() { return locationId; }
    public UUID getSkuId() { return skuId; }
    /** Returns the stored sentinel for non-LOT-tracked rows. Use {@link #getLotIdOrNull()} for the public-facing value. */
    public UUID getLotId() { return lotId; }
    public UUID getLotIdOrNull() {
        return InventorySnapshotId.NULL_SENTINEL.equals(lotId) ? null : lotId;
    }
    public UUID getWarehouseId() { return warehouseId; }
    public String getWarehouseCode() { return warehouseCode; }
    public String getLocationCode() { return locationCode; }
    public String getSkuCode() { return skuCode; }
    public String getLotNo() { return lotNo; }
    public int getAvailableQty() { return availableQty; }
    public int getReservedQty() { return reservedQty; }
    public int getDamagedQty() { return damagedQty; }
    public int getOnHandQty() { return onHandQty; }
    public boolean isLowStockFlag() { return lowStockFlag; }
    public Instant getLastAdjustedAt() { return lastAdjustedAt; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
