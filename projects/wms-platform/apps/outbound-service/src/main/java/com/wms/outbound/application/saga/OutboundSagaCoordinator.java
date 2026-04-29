package com.wms.outbound.application.saga;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Application-layer orchestrator for the outbound saga.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/sagas/outbound-saga.md},
 * {@code specs/services/outbound-service/state-machines/saga-status.md}.
 *
 * <p><b>TASK-BE-034 stub:</b> the methods accept event payloads and log a
 * "not yet implemented" line. The real coordination logic lands in
 * TASK-BE-036 alongside the saga-step consumers.
 */
@Component
public class OutboundSagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(OutboundSagaCoordinator.class);

    /**
     * Stub — reacts to {@code inventory.reserved}. Real implementation in
     * TASK-BE-036.
     */
    public void onInventoryReserved(UUID sagaId) {
        log.info("saga event received, handler not yet implemented: kind=inventory.reserved sagaId={}", sagaId);
    }

    /**
     * Stub — reacts to {@code inventory.released}.
     */
    public void onInventoryReleased(UUID sagaId) {
        log.info("saga event received, handler not yet implemented: kind=inventory.released sagaId={}", sagaId);
    }

    /**
     * Stub — reacts to {@code inventory.confirmed}.
     */
    public void onInventoryConfirmed(UUID sagaId) {
        log.info("saga event received, handler not yet implemented: kind=inventory.confirmed sagaId={}", sagaId);
    }

    /**
     * Stub — reacts to {@code inventory.adjusted{INSUFFICIENT_STOCK}}.
     */
    public void onReserveFailed(UUID sagaId, String reason) {
        log.info("saga event received, handler not yet implemented: kind=reserve_failed sagaId={} reason={}",
                sagaId, reason);
    }
}
