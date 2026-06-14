package com.example.shipping.application.command;

/**
 * Command to create a Shipping from a confirmed order (M4). {@code tenantId} is bound
 * from the consumed OrderConfirmed envelope (default {@code ecommerce} when the producer
 * predates BE-357 or omits it) and stamped on the new row — the consumer is not an HTTP
 * request, so the tenant is passed explicitly rather than via TenantContext.
 */
public record CreateShippingCommand(
        String tenantId,
        String orderId,
        String userId
) {}
