package com.example.scmplatform.procurement.domain.po;

import java.math.BigDecimal;

/**
 * One ASN line as the {@link PurchaseOrder} aggregate sees it: the PO line it
 * applies to and the shipped quantity to add to that line's received quantity.
 * A plain domain value — the application layer maps its command lines onto it.
 */
public record AsnLineReceipt(String poLineId, BigDecimal quantity) {
}
