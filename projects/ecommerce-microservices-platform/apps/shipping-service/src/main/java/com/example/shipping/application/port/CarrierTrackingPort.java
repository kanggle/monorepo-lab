package com.example.shipping.application.port;

import java.util.Optional;

/**
 * Outbound port to an external carrier's tracking API (TASK-BE-293, the
 * shipping-service v2 carrier integration — overview.md "External carrier API 통합").
 * The v1 baseline is admin-driven manual status transitions; this seam lets a real
 * carrier report a shipment's latest status so it can be reflected automatically.
 *
 * <p>Implementations are <b>best-effort and never throw</b>: a carrier outage /
 * transport error / unparseable response returns {@link Optional#empty()} (the
 * refresh becomes a no-op), so a carrier hiccup never fails the admin request or
 * mutates the shipment. The default {@code mock} adapter returns empty (carrier
 * integration off — net-zero); the real {@code http} adapter calls the provider.
 */
public interface CarrierTrackingPort {

    /**
     * The carrier's latest status for {@code trackingNumber} on {@code carrier}, or
     * {@link Optional#empty()} when unavailable / unknown (best-effort).
     */
    Optional<CarrierTrackingSnapshot> fetchLatest(String carrier, String trackingNumber);

    /** The carrier-reported raw status string (mapped to a domain status by the caller). */
    record CarrierTrackingSnapshot(String rawStatus) {
    }
}
