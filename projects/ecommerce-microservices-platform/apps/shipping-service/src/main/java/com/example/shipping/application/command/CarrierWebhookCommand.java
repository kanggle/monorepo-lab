package com.example.shipping.application.command;

/**
 * A carrier-pushed webhook delivery (TASK-BE-294), already authenticated by the inbound
 * adapter. {@code deliveryId} is the carrier's unique delivery identifier (idempotency
 * key); {@code shippingId} is our shipment reference the carrier echoes back; {@code
 * rawStatus} is the carrier-reported status string (mapped by {@code CarrierStatusMapper}).
 */
public record CarrierWebhookCommand(String deliveryId, String shippingId, String rawStatus) {
}
