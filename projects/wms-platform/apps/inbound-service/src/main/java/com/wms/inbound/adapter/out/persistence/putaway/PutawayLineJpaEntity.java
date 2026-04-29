package com.wms.inbound.adapter.out.persistence.putaway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "putaway_line")
class PutawayLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "putaway_instruction_id", nullable = false)
    private UUID putawayInstructionId;

    @Column(name = "asn_line_id", nullable = false)
    private UUID asnLineId;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "lot_no", length = 40)
    private String lotNo;

    @Column(name = "destination_location_id", nullable = false)
    private UUID destinationLocationId;

    @Column(name = "qty_to_putaway", nullable = false)
    private int qtyToPutaway;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected PutawayLineJpaEntity() {}

    PutawayLineJpaEntity(UUID id, UUID putawayInstructionId, UUID asnLineId,
                          UUID skuId, UUID lotId, String lotNo,
                          UUID destinationLocationId, int qtyToPutaway, String status) {
        this.id = id;
        this.putawayInstructionId = putawayInstructionId;
        this.asnLineId = asnLineId;
        this.skuId = skuId;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.destinationLocationId = destinationLocationId;
        this.qtyToPutaway = qtyToPutaway;
        this.status = status;
    }

    UUID getId() { return id; }
    UUID getPutawayInstructionId() { return putawayInstructionId; }
    UUID getAsnLineId() { return asnLineId; }
    UUID getSkuId() { return skuId; }
    UUID getLotId() { return lotId; }
    String getLotNo() { return lotNo; }
    UUID getDestinationLocationId() { return destinationLocationId; }
    int getQtyToPutaway() { return qtyToPutaway; }
    String getStatus() { return status; }

    void setStatus(String status) { this.status = status; }
}
