package com.example.shipping.application.command;

import java.time.Instant;

/**
 * Command to cancel the Shipping of a cancelled order (ADR-MONO-022 D4 v2(d), TASK-BE-649),
 * built from a consumed {@code OrderCancelled}.
 *
 * @param orderId     the ecommerce order id (== wms {@code orderNo}, D5 correlation key)
 * @param cancelledAt when ecommerce accepted the order cancel ({@code payload.cancelledAt});
 *                    {@code null} when absent or unparseable — the service then uses its clock.
 *                    Carried to wms as {@code requestedAt}.
 */
public record CancelShippingCommand(
        String orderId,
        Instant cancelledAt
) {}
