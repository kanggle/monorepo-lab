package com.example.scmplatform.procurement.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Webhook inbound: supplier-issued ASN. Idempotency is enforced by the
 * UNIQUE {@code (tenant_id, supplier_asn_ref)} index — a duplicate webhook
 * delivery returns the previously-stored result.
 */
public record ReceiveAsnCommand(
        String tenantId,
        String poId,
        String supplierAsnRef,
        Instant expectedArrivalAt,
        List<AsnLine> lines
) {
    public record AsnLine(String poLineId, BigDecimal quantityShipped) {
    }
}
