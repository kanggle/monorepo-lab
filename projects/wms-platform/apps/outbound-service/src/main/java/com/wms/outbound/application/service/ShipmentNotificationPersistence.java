package com.wms.outbound.application.service;

import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.model.Shipment;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper component holding the {@code REQUIRES_NEW} transactional methods that
 * back {@link ShipmentNotificationListener}.
 *
 * <p>Why a separate bean?
 * Spring's {@code @Transactional} works through AOP proxies. When a method on a
 * bean calls another method on the <em>same</em> bean ({@code this.x()}), the
 * call goes through the raw object reference and bypasses the proxy — so the
 * {@code REQUIRES_NEW} annotation has no effect. The
 * {@link org.springframework.transaction.event.TransactionalEventListener
 * TransactionalEventListener} fires {@code AFTER_COMMIT} with no active
 * transaction, so any TX-bound work invoked from the listener must go through
 * a real proxy. Extracting the helpers into this component makes that proxy
 * boundary explicit and testable.
 *
 * <p>Lifecycle (per {@code external-integrations.md} §2.10):
 * <ol>
 *   <li>{@code ConfirmShippingService} commits {@code SHIPPED} state + outbox row.</li>
 *   <li>{@link ShipmentNotificationListener} fires {@code AFTER_COMMIT}; calls
 *       {@link #markNotified} or {@link #markFailed} on this bean.</li>
 *   <li>Each helper opens a fresh transaction
 *       ({@link Propagation#REQUIRES_NEW}).</li>
 * </ol>
 */
@Component
public class ShipmentNotificationPersistence {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationPersistence.class);

    private final ShipmentPersistencePort shipmentPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;

    public ShipmentNotificationPersistence(ShipmentPersistencePort shipmentPersistence,
                                           OutboundSagaCoordinator sagaCoordinator) {
        this.shipmentPersistence = shipmentPersistence;
        this.sagaCoordinator = sagaCoordinator;
    }

    /**
     * Successful TMS ack: open a fresh TX, transition {@code Shipment.tms_status}
     * to {@code NOTIFIED}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNotified(UUID shipmentId, Instant now, UUID requestId) {
        Shipment shipment = shipmentPersistence.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
        shipment.markTmsNotified(now, requestId);
        shipmentPersistence.save(shipment);
        log.info("tms_notified shipmentId={} requestId={}", shipmentId, requestId);
    }

    /**
     * TMS push exhausted (or threw): open a fresh TX, transition
     * {@code Shipment.tms_status} to {@code NOTIFY_FAILED} and advance the saga
     * to {@code SHIPPED_NOT_NOTIFIED}.
     *
     * <p>Both updates commit together (single TX) so an alert fired on the
     * saga state change is consistent with the shipment record. Stock has
     * already been consumed in the outer ConfirmShipping TX, so the manual
     * retry endpoint can re-attempt only the TMS notification.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID sagaId, UUID shipmentId, Instant now, String reason) {
        Shipment shipment = shipmentPersistence.findById(shipmentId).orElse(null);
        if (shipment != null) {
            shipment.markTmsNotifyFailed(now);
            shipmentPersistence.save(shipment);
        }
        sagaCoordinator.onTmsNotifyFailed(sagaId, reason);
    }
}
