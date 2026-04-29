package com.wms.outbound.application.service;

import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.model.Shipment;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link ShipmentNotifyTrigger} application events emitted by
 * {@link ConfirmShippingService} and invokes the TMS push <strong>after</strong>
 * the saga TX commits (per {@code external-integrations.md} §2.10 and T2 — no
 * distributed TX).
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@code ConfirmShippingService} commits {@code SHIPPED} state + outbox row.</li>
 *   <li>This listener fires; it calls {@code ShipmentNotificationPort.notify()}.</li>
 *   <li>On success: open a fresh TX, transition {@code Shipment.tms_status} to
 *       {@code NOTIFIED}.</li>
 *   <li>On failure: open a fresh TX, transition {@code Shipment.tms_status} to
 *       {@code NOTIFY_FAILED} and advance the saga to {@code SHIPPED_NOT_NOTIFIED}.</li>
 * </ol>
 *
 * <p>Even if the listener crashes, stock is already consumed (the
 * {@code outbound.shipping.confirmed} outbox row was committed in the saga TX).
 * Operators recover via {@code POST /shipments/{id}:retry-tms-notify}.
 */
@Component
public class ShipmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationListener.class);

    private final ShipmentNotificationPort shipmentNotification;
    private final ShipmentPersistencePort shipmentPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;
    private final Clock clock;

    public ShipmentNotificationListener(ShipmentNotificationPort shipmentNotification,
                                        ShipmentPersistencePort shipmentPersistence,
                                        OutboundSagaCoordinator sagaCoordinator,
                                        Clock clock) {
        this.shipmentNotification = shipmentNotification;
        this.shipmentPersistence = shipmentPersistence;
        this.sagaCoordinator = sagaCoordinator;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentNotifyTrigger(ShipmentNotifyTrigger trigger) {
        try {
            TmsAcknowledgement ack = shipmentNotification.notify(trigger.shipmentId());
            if (ack != null && ack.success()) {
                markNotified(trigger.shipmentId(), ack.requestId());
            } else {
                markFailed(trigger.sagaId(), trigger.shipmentId(),
                        "TMS responded without success");
            }
        } catch (RuntimeException ex) {
            log.warn("tms_notify_failed shipmentId={} reason={}",
                    trigger.shipmentId(), ex.toString());
            markFailed(trigger.sagaId(), trigger.shipmentId(), ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNotified(UUID shipmentId, String requestId) {
        Shipment shipment = shipmentPersistence.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
        Instant now = clock.instant();
        UUID requestUuid = parseUuidQuiet(requestId);
        shipment.markTmsNotified(now, requestUuid);
        shipmentPersistence.save(shipment);
        log.info("tms_notified shipmentId={} requestId={}", shipmentId, requestId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID sagaId, UUID shipmentId, String reason) {
        Shipment shipment = shipmentPersistence.findById(shipmentId).orElse(null);
        if (shipment != null) {
            Instant now = clock.instant();
            shipment.markTmsNotifyFailed(now);
            shipmentPersistence.save(shipment);
        }
        sagaCoordinator.onTmsNotifyFailed(sagaId, reason);
    }

    private static UUID parseUuidQuiet(String s) {
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
