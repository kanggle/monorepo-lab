package com.wms.inbound.domain.model;

import java.util.UUID;

public class InspectionLine {

    private final UUID id;
    private final UUID inspectionId;
    private final UUID asnLineId;
    private final UUID skuId;
    private final UUID lotId;
    private final String lotNo;
    private final int qtyPassed;
    private final int qtyDamaged;
    private final int qtyShort;

    public InspectionLine(UUID id, UUID inspectionId, UUID asnLineId, UUID skuId,
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

    public int totalQty() {
        return qtyPassed + qtyDamaged + qtyShort;
    }

    public UUID getId() { return id; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getAsnLineId() { return asnLineId; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public String getLotNo() { return lotNo; }
    public int getQtyPassed() { return qtyPassed; }
    public int getQtyDamaged() { return qtyDamaged; }
    public int getQtyShort() { return qtyShort; }
}
