package com.example.scmplatform.procurement.application.command;

/**
 * Webhook inbound: supplier ack of a previously-submitted PO. Trusted at the
 * service edge by the {@code SupplierAckWebhookController} signature check
 * (v1: shared secret; v2: HMAC).
 */
public record AcknowledgePurchaseOrderCommand(
        String tenantId,
        String poId,
        String supplierAckRef
) {
}
