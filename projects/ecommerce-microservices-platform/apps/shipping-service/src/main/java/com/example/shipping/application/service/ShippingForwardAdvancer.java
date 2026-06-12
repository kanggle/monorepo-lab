package com.example.shipping.application.service;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;

import java.time.Clock;
import java.util.Optional;

/**
 * Advances a {@link Shipping} forward through the linear
 * {@code PREPARING → SHIPPED → IN_TRANSIT → DELIVERED} chain toward a goal status, one
 * valid step at a time (each step appends domain status history via
 * {@link Shipping#transitionTo}). Shared by the carrier-pull refresh (TASK-BE-293,
 * {@link RefreshTrackingService}) and the carrier-push webhook (TASK-BE-294,
 * {@link ProcessCarrierWebhookService}) so both reach a carrier-reported status the same way.
 *
 * <p><b>Forward-only.</b> A goal at or behind the current status leaves the shipment
 * unchanged (shipments never regress). Returns the <em>original</em> status when a net
 * change occurred (so the caller can publish one consolidated {@code original → final}
 * event), or {@link Optional#empty()} when nothing changed.
 */
public final class ShippingForwardAdvancer {

    private ShippingForwardAdvancer() {
    }

    public static Optional<ShippingStatus> advanceForward(Shipping shipping, ShippingStatus goal, Clock clock) {
        ShippingStatus original = shipping.getStatus();
        ShippingStatus[] chain = ShippingStatus.values();
        while (shipping.getStatus().ordinal() < goal.ordinal()) {
            ShippingStatus next = chain[shipping.getStatus().ordinal() + 1];
            shipping.transitionTo(next, shipping.getTrackingNumber(), shipping.getCarrier(), clock);
        }
        return shipping.getStatus() == original ? Optional.empty() : Optional.of(original);
    }
}
