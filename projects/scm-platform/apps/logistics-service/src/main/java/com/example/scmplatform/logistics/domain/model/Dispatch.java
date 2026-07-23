package com.example.scmplatform.logistics.domain.model;

import com.example.scmplatform.logistics.domain.error.IllegalDispatchTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Dispatch aggregate (ADR-053 §D2). A confirmed shipment's journey onto a carrier.
 *
 * <p>Status machine (S1, Tx-guarded + idempotent at the use-case boundary):
 * <pre>
 *   PENDING ──recordAck──> DISPATCHED
 *      │                       ▲
 *      └─recordFailure─> DISPATCH_FAILED ──recordAck (operator :retry)──┘
 * </pre>
 *
 * <ul>
 *   <li>{@link #recordAck} from PENDING/DISPATCH_FAILED → DISPATCHED (sets tracking + carrier
 *       + vendor). From DISPATCHED it is an <b>idempotent no-op</b> — the shipment already
 *       dispatched; the cached ack stands and there is no version bump.</li>
 *   <li>{@link #recordFailure} from PENDING/DISPATCH_FAILED → DISPATCH_FAILED. From DISPATCHED
 *       it is an <b>illegal transition</b> and is rejected — a completed dispatch cannot fail.</li>
 * </ul>
 *
 * Framework-free (Hexagonal): no JPA / Spring annotations. A separate JPA entity mirrors this.
 */
public class Dispatch {

    private final UUID id;
    private final ShipmentId shipmentId;
    private final String shipmentNo;
    private final UUID orderId;          // correlation only (nullable)
    private final String orderNo;        // correlation only (nullable)
    private final String tenantId;       // echoed from the seam (nullable for B2B)

    private CarrierCode carrierCode;     // set on DISPATCHED
    private TrackingNo trackingNo;       // set on DISPATCHED
    private DispatchStatus status;
    private String failureReason;        // set on DISPATCH_FAILED
    private Carrier vendor;              // set on DISPATCHED

    private int version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Dispatch(UUID id, ShipmentId shipmentId, String shipmentNo, UUID orderId,
                     String orderNo, String tenantId, CarrierCode carrierCode, TrackingNo trackingNo,
                     DispatchStatus status, String failureReason, Carrier vendor, int version,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.shipmentId = Objects.requireNonNull(shipmentId, "shipmentId");
        this.shipmentNo = Objects.requireNonNull(shipmentNo, "shipmentNo");
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.tenantId = tenantId;
        this.carrierCode = carrierCode;
        this.trackingNo = trackingNo;
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.vendor = vendor;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Create a new PENDING dispatch for a confirmed shipment. In Phase 1 this is invoked by the
     * seam consumer (BE-044) and by IT seeding; there is no create-dispatch REST endpoint.
     */
    public static Dispatch create(UUID id, ShipmentId shipmentId, String shipmentNo,
                                  UUID orderId, String orderNo, String tenantId, Instant now) {
        return new Dispatch(id, shipmentId, shipmentNo, orderId, orderNo, tenantId,
                null, null, DispatchStatus.PENDING, null, null, 0, now, now);
    }

    /** Reconstruct from persistence. */
    public static Dispatch reconstitute(UUID id, ShipmentId shipmentId, String shipmentNo,
                                        UUID orderId, String orderNo, String tenantId,
                                        CarrierCode carrierCode, TrackingNo trackingNo,
                                        DispatchStatus status, String failureReason, Carrier vendor,
                                        int version, Instant createdAt, Instant updatedAt) {
        return new Dispatch(id, shipmentId, shipmentNo, orderId, orderNo, tenantId,
                carrierCode, trackingNo, status, failureReason, vendor, version, createdAt, updatedAt);
    }

    /**
     * Record a successful vendor ack. Idempotent: on an already-DISPATCHED dispatch this is a
     * no-op (the cached ack stands, no state change, no version bump) — the property the
     * {@code :retry}-already-DISPATCHED path relies on.
     */
    public void recordAck(TrackingNo trackingNo, CarrierCode carrierCode, Carrier vendor, Instant now) {
        if (status == DispatchStatus.DISPATCHED) {
            return; // idempotent no-op — already dispatched
        }
        this.trackingNo = Objects.requireNonNull(trackingNo, "trackingNo");
        this.carrierCode = Objects.requireNonNull(carrierCode, "carrierCode");
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.failureReason = null;
        this.status = DispatchStatus.DISPATCHED;
        this.updatedAt = now;
        this.version++;
    }

    /**
     * Record a vendor dispatch failure → DISPATCH_FAILED. Illegal (rejected) from DISPATCHED:
     * a completed dispatch cannot fail.
     */
    public void recordFailure(String reason, Instant now) {
        if (status == DispatchStatus.DISPATCHED) {
            throw new IllegalDispatchTransitionException(
                    "Cannot record failure on an already DISPATCHED dispatch " + id);
        }
        this.failureReason = reason;
        this.status = DispatchStatus.DISPATCH_FAILED;
        this.updatedAt = now;
        this.version++;
    }

    public UUID getId() { return id; }
    public ShipmentId getShipmentId() { return shipmentId; }
    public String getShipmentNo() { return shipmentNo; }
    public UUID getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public String getTenantId() { return tenantId; }
    public CarrierCode getCarrierCode() { return carrierCode; }
    public TrackingNo getTrackingNo() { return trackingNo; }
    public DispatchStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Carrier getVendor() { return vendor; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
