package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command for {@code POST /api/procurement/po} — create a DRAFT PO.
 */
public record DraftPurchaseOrderCommand(
        ActorContext actor,
        String supplierId,
        String currency,
        List<Line> lines
) {
    public record Line(int lineNo, String sku, String supplierSku, BigDecimal quantity, BigDecimal unitPrice) {
    }
}
