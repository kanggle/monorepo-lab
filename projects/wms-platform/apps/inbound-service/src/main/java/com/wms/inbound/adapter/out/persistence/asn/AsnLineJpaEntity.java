package com.wms.inbound.adapter.out.persistence.asn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asn_line")
class AsnLineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "asn_id", nullable = false)
    private UUID asnId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "expected_qty", nullable = false)
    private int expectedQty;

    protected AsnLineJpaEntity() {}

    AsnLineJpaEntity(UUID id, UUID asnId, int lineNo, UUID skuId, UUID lotId, int expectedQty) {
        this.id = id;
        this.asnId = asnId;
        this.lineNo = lineNo;
        this.skuId = skuId;
        this.lotId = lotId;
        this.expectedQty = expectedQty;
    }

    UUID getId() { return id; }
    UUID getAsnId() { return asnId; }
    int getLineNo() { return lineNo; }
    UUID getSkuId() { return skuId; }
    UUID getLotId() { return lotId; }
    int getExpectedQty() { return expectedQty; }
}
