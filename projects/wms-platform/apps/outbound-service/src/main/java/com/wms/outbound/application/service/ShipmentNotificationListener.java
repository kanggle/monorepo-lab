package com.wms.outbound.application.service;

import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
 *   <li>On success: delegate to {@link ShipmentNotificationPersistence#markNotified}
 *       which opens a fresh TX (REQUIRES_NEW) and transitions
 *       {@code Shipment.tms_status} to {@code NOTIFIED}.</li>
 *   <li>On failure: delegate to {@link ShipmentNotificationPersistence#markFailed}
 *       which opens a fresh TX (REQUIRES_NEW) and transitions
 *       {@code Shipment.tms_status} to {@code NOTIFY_FAILED} and advances the
 *       saga to {@code SHIPPED_NOT_NOTIFIED}.</li>
 * </ol>
 *
 * <p>The TX-bound work is in a separate {@code @Component}
 * ({@link ShipmentNotificationPersistence}) so the Spring AOP proxy applies —
 * self-invoked {@code @Transactional} would silently bypass propagation
 * (TASK-BE-040 fix).
 *
 * <p>Even if the listener crashes, stock is already consumed (the
 * {@code outbound.shipping.confirmed} outbox row was committed in the saga TX).
 * Operators recover via {@code POST /shipments/{id}:retry-tms-notify}.
 */
@Component
public class ShipmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationListener.class);

    private final ShipmentNotificationPort shipmentNotification;
    private final ShipmentNotificationPersistence persistence;
    private final Clock clock;

    public ShipmentNotificationListener(ShipmentNotificationPort shipmentNotification,
                                        ShipmentNotificationPersistence persistence,
                                        Clock clock) {
        this.shipmentNotification = shipmentNotification;
        this.persistence = persistence;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipmentNotifyTrigger(ShipmentNotifyTrigger trigger) {
        try {
            TmsAcknowledgement ack = shipmentNotification.notify(trigger.shipmentId());
            if (ack != null && ack.success()) {
                persistence.markNotified(
                        trigger.shipmentId(),
                        clock.instant(),
                        parseUuidQuiet(ack.requestId()));
            } else {
                persistence.markFailed(
                        trigger.sagaId(),
                        trigger.shipmentId(),
                        clock.instant(),
                        "TMS responded without success");
            }
        } catch (RuntimeException ex) {
            log.warn("tms_notify_failed shipmentId={} reason={}",
                    trigger.shipmentId(), ex.toString());
            try {
                persistence.markFailed(
                        trigger.sagaId(),
                        trigger.shipmentId(),
                        clock.instant(),
                        ex.getMessage());
            } catch (RuntimeException persistEx) {
                // Listener TX failed too — saga sweeper will re-emit; manual
                // TMS retry endpoint will recover. Log and swallow so the
                // listener does not propagate to the (already-committed)
                // outer caller.
                log.error("tms_notify_persist_failed shipmentId={} reason={}",
                        trigger.shipmentId(), persistEx.toString());
            }
        }
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
