package com.wms.inbound.domain.model;

import java.util.UUID;

public class AsnLine {

    private final UUID id;
    private final UUID asnId;
    private final int lineNo;
    private final UUID skuId;
    private final UUID lotId;
    private final int expectedQty;

    public AsnLine(UUID id, UUID asnId, int lineNo, UUID skuId, UUID lotId, int expectedQty) {
        this.id = id;
        this.asnId = asnId;
        this.lineNo = lineNo;
        this.skuId = skuId;
        this.lotId = lotId;
        this.expectedQty = expectedQty;
    }

    public UUID getId() { return id; }
    public UUID getAsnId() { return asnId; }
    public int getLineNo() { return lineNo; }
    public UUID getSkuId() { return skuId; }
    public UUID getLotId() { return lotId; }
    public int getExpectedQty() { return expectedQty; }
}
