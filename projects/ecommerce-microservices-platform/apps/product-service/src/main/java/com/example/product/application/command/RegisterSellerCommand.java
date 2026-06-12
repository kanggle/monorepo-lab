package com.example.product.application.command;

/**
 * Registers a marketplace seller within the current tenant (ADR-MONO-030 Step 3
 * §3.1). The owning {@code tenant_id} is derived from the request context, not
 * this command.
 */
public record RegisterSellerCommand(
        String sellerId,
        String displayName
) {
}
