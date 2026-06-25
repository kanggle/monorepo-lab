package com.example.shipping.domain.model;

import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import com.example.shipping.domain.exception.InvalidShippingException;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
public class Shipping {

    private String shippingId;
    private String tenantId;
    private String orderId;
    private String userId;
    private ShippingStatus status;
    private String trackingNumber;
    private String carrier;
    private boolean wmsRouted;
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    private Shipping() {
    }

    public static Shipping create(String tenantId, String orderId, String userId, Clock clock) {
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidShippingException("Order ID must not be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new InvalidShippingException("User ID must not be null or blank");
        }

        Shipping shipping = new Shipping();
        shipping.shippingId = UUID.randomUUID().toString();
        shipping.tenantId = (tenantId == null || tenantId.isBlank())
                ? com.example.shipping.domain.tenant.TenantContext.DEFAULT_TENANT_ID
                : tenantId;
        shipping.orderId = orderId;
        shipping.userId = userId;
        shipping.status = ShippingStatus.PREPARING;
        shipping.wmsRouted = false;
        Instant now = Instant.now(clock);
        shipping.createdAt = now;
        shipping.updatedAt = now;
        shipping.statusHistory.add(new StatusHistoryEntry(ShippingStatus.PREPARING, now));
        return shipping;
    }

    public static Shipping reconstitute(String shippingId, String tenantId, String orderId, String userId,
                                         ShippingStatus status, String trackingNumber, String carrier,
                                         boolean wmsRouted, List<StatusHistoryEntry> statusHistory,
                                         Instant createdAt, Instant updatedAt) {
        Shipping shipping = new Shipping();
        shipping.shippingId = shippingId;
        shipping.tenantId = tenantId;
        shipping.orderId = orderId;
        shipping.userId = userId;
        shipping.status = status;
        shipping.trackingNumber = trackingNumber;
        shipping.carrier = carrier;
        shipping.wmsRouted = wmsRouted;
        shipping.statusHistory = new ArrayList<>(statusHistory);
        shipping.createdAt = createdAt;
        shipping.updatedAt = updatedAt;
        return shipping;
    }

    /**
     * Marks this shipping as routed through the wms warehouse — set once the
     * forward-leg fulfillment-intent event ({@code ecommerce.fulfillment.requested.v1})
     * is actually published for the order (ADR-MONO-022 D4 v2(c)). Idempotent: a
     * re-mark is a no-op. Gates the operator's later manual wms-inventory deduction.
     */
    public void markWmsRouted() {
        this.wmsRouted = true;
    }

    public ShippingStatus transitionTo(ShippingStatus newStatus, String trackingNumber,
                                        String carrier, Clock clock) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }

        if (newStatus == ShippingStatus.SHIPPED) {
            if (trackingNumber == null || trackingNumber.isBlank()) {
                throw new InvalidShippingException("Tracking number is required when status is SHIPPED");
            }
            if (carrier == null || carrier.isBlank()) {
                throw new InvalidShippingException("Carrier is required when status is SHIPPED");
            }
            this.trackingNumber = trackingNumber;
            this.carrier = carrier;
        }

        ShippingStatus previousStatus = this.status;
        this.status = newStatus;
        Instant now = Instant.now(clock);
        this.updatedAt = now;
        this.statusHistory.add(new StatusHistoryEntry(newStatus, now));
        return previousStatus;
    }

    public List<StatusHistoryEntry> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
