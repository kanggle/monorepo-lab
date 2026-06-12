package com.example.shipping.interfaces.rest.dto.request;

/**
 * Inbound carrier webhook payload (TASK-BE-294). {@code deliveryId} is the carrier's
 * unique delivery identifier (idempotency key); {@code shippingId} is the client reference
 * the carrier echoes back; {@code status} is the carrier-reported raw status string.
 * Parsed from the raw (signature-verified) body; field presence is validated by the
 * controller.
 */
public record CarrierWebhookRequest(String deliveryId, String shippingId, String status) {
}
