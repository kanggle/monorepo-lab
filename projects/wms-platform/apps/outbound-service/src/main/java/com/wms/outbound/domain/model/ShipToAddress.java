package com.wms.outbound.domain.model;

import java.util.Objects;

/**
 * Drop-ship recipient for a B2C ({@code FULFILLMENT_ECOMMERCE}-origin) outbound
 * {@link Order}.
 *
 * <p>Additive value object (ADR-MONO-022 D2-a). {@code null} for
 * {@code MANUAL} / {@code WEBHOOK_ERP} (B2B — shipped to the customer partner),
 * present only when the order originates from an ecommerce fulfillment request.
 *
 * <p>No PII policy is invoked: per the outbound architecture (§Security)
 * customer contact data inherited for operational fulfillment is internal /
 * operational, not regulated.
 *
 * <p>Authoritative references:
 * {@code specs/contracts/events/ecommerce-fulfillment-subscriptions.md} and
 * {@code specs/contracts/events/outbound-events.md} §1.
 */
public record ShipToAddress(
        String recipientName,
        String address,
        String phone
) {

    public ShipToAddress {
        Objects.requireNonNull(recipientName, "recipientName");
        Objects.requireNonNull(address, "address");
        // phone may be null — not every channel supplies a contact number.
    }
}
