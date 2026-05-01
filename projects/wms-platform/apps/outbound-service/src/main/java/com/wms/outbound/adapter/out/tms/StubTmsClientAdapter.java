package com.wms.outbound.adapter.out.tms;

import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link ShipmentNotificationPort}.
 *
 * <p>Per TASK-BE-034 scope: logs the call and returns success immediately
 * with a fresh request id. The real Resilience4j-backed adapter (timeouts,
 * circuit breaker, retry, bulkhead) lands in TASK-BE-037.
 */
@Component
public class StubTmsClientAdapter implements ShipmentNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubTmsClientAdapter.class);

    @Override
    public TmsAcknowledgement notify(UUID shipmentId) {
        String requestId = UUID.randomUUID().toString();
        log.info("TMS notify stub: shipment {} requestId={}", shipmentId, requestId);
        return new TmsAcknowledgement(true, requestId);
    }
}
