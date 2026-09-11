package com.wms.outbound.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Child entity of {@link Order}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §1 (OrderLine).
 *
 * <p>Once the parent {@code Order} transitions to {@code PICKING}
 * (via {@link Order#startPicking}), an OrderLine becomes immutable —
 * the aggregate enforces this by rejecting any further line mutation.
 */
public final class OrderLine {

    private final UUID id;
    private final UUID orderId;
    private final int lineNo;
    private final UUID skuId;
    /**
     * TASK-MONO-659 — SKU 코드(비정규화). 이 서비스는 인입 시점에 <b>코드로 조회해서</b>
     * UUID 를 얻는다({@code FulfillmentRequestedConsumer} / {@code ReceiveOrderService}) —
     * 즉 코드를 손에 쥐고 있다가 버리고 있었고, 그래서 콘솔이 그 칸에 raw UUID 를 그렸다.
     *
     * <p>Nullable 이다: 이 컬럼이 생기기 전에 만들어진 행은 백필 전까지 {@code null} 이다.
     * {@code skuId} 는 그대로 둔다 — 이것은 <b>더하는</b> 변경이다.
     */
    private final String skuCode;
    private final UUID lotId;
    private final int qtyOrdered;

    public OrderLine(UUID id,
                     UUID orderId,
                     int lineNo,
                     UUID skuId,
                     String skuCode,
                     UUID lotId,
                     int qtyOrdered) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        if (lineNo < 1) {
            throw new IllegalArgumentException("lineNo must be >= 1");
        }
        this.lineNo = lineNo;
        this.skuId = Objects.requireNonNull(skuId, "skuId");
        // 🔵 requireNonNull 을 걸지 않는다 — 백필 전 기존 행이 null 이고, 그것은 결함이
        //    아니라 «아직 안 채워졌다» 이다. 여기서 막으면 옛 주문을 못 읽는다.
        this.skuCode = skuCode;
        this.lotId = lotId;
        if (qtyOrdered <= 0) {
            throw new IllegalArgumentException("qtyOrdered must be > 0");
        }
        this.qtyOrdered = qtyOrdered;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public UUID getLotId() {
        return lotId;
    }

    public int getQtyOrdered() {
        return qtyOrdered;
    }
}
