package com.wms.inbound.adapter.out.persistence.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "inspection_line")
class InspectionLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "asn_line_id", nullable = false)
    private UUID asnLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "lot_no", length = 40)
    private String lotNo;

    @Column(name = "qty_passed", nullable = false)
    private int qtyPassed;

    @Column(name = "qty_damaged", nullable = false)
    private int qtyDamaged;

    @Column(name = "qty_short", nullable = false)
    private int qtyShort;

    protected InspectionLineJpaEntity() {}

    InspectionLineJpaEntity(UUID id, UUID inspectionId, UUID asnLineId, UUID skuId,
                             UUID lotId, String lotNo,
                             int qtyPassed, int qtyDamaged, int qtyShort) {
        this.id = id;
        this.inspectionId = inspectionId;
        this.asnLineId = asnLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.qtyPassed = qtyPassed;
        this.qtyDamaged = qtyDamaged;
        this.qtyShort = qtyShort;
    }

    UUID getId() { return id; }
    UUID getInspectionId() { return inspectionId; }
    UUID getAsnLineId() { return asnLineId; }
    UUID getSkuId() { return skuId; }
    UUID getLotId() { return lotId; }
    String getLotNo() { return lotNo; }
    int getQtyPassed() { return qtyPassed; }
    int getQtyDamaged() { return qtyDamaged; }
    int getQtyShort() { return qtyShort; }
}
