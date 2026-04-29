package com.wms.outbound.application.port.out;

import java.util.UUID;

/**
 * Out-port for outbound TMS notification (vendor-agnostic).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/architecture.md} § TMS Integration
 * and {@code specs/services/outbound-service/external-integrations.md} §5.
 *
 * <p>The TASK-BE-034 stub implementation logs and returns success immediately;
 * the real Resilience4j-backed adapter (with timeout, circuit breaker, retry,
 * bulkhead) lands in TASK-BE-037.
 */
public interface ShipmentNotificationPort {

    /**
     * Push a shipment-ready notification to TMS.
     *
     * @param shipmentId  the WMS-side shipment identifier (also used as
     *                    vendor's {@code Idempotency-Key})
     * @return acknowledgement with vendor's {@code requestId} echoed back
     */
    TmsAcknowledgement notify(UUID shipmentId);
}
