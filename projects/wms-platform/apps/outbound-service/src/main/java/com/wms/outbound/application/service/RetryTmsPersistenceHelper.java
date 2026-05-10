package com.wms.outbound.application.service;

import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import com.wms.outbound.application.saga.OutboundSagaCoordinator;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.exception.TmsRetryNotAllowedException;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.Shipment;
import com.wms.outbound.domain.model.TmsStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper component holding the {@code @Transactional} read + write methods
 * that back {@link RetryTmsNotificationService}. Extracted for the same
 * reason as {@code ShipmentNotificationPersistence} — Spring AOP proxies
 * don't intercept self-invocation, so any {@code @Transactional} method
 * that needs a real proxy must live on a separate bean.
 */
@Component
public class RetryTmsPersistenceHelper {

    private final ShipmentPersistencePort shipmentPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final OutboundSagaCoordinator sagaCoordinator;

    public RetryTmsPersistenceHelper(ShipmentPersistencePort shipmentPersistence,
                                     SagaPersistencePort sagaPersistence,
                                     OutboundSagaCoordinator sagaCoordinator) {
        this.shipmentPersistence = shipmentPersistence;
        this.sagaPersistence = sagaPersistence;
        this.sagaCoordinator = sagaCoordinator;
    }

    /**
     * Read-side snapshot of the shipment + saga before the network call.
     */
    public record ShipmentSnapshot(
            TmsStatus tmsStatus,
            Instant tmsNotifiedAt,
            String trackingNo,
            UUID sagaId,
            SagaStatus sagaStatus) {}

    @Transactional(readOnly = true)
    public ShipmentSnapshot loadAndValidate(UUID shipmentId) {
        Shipment shipment = shipmentPersistence.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
        if (shipment.getTmsStatus() != TmsStatus.NOTIFY_FAILED) {
            throw new TmsRetryNotAllowedException(shipmentId, shipment.getTmsStatus().name());
        }
        OutboundSaga saga = sagaPersistence.findByOrderId(shipment.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(shipment.getOrderId()));
        return new ShipmentSnapshot(
                shipment.getTmsStatus(),
                shipment.getTmsNotifiedAt(),
                shipment.getTrackingNo(),
                saga.sagaId(),
                saga.status());
    }

    /**
     * Successful retry: open a fresh TX, transition shipment to
     * {@code NOTIFIED} and saga {@code SHIPPED_NOT_NOTIFIED → SHIPPED →
     * COMPLETED}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetryTmsNotificationResult markRetrySucceeded(RetryTmsNotificationCommand command,
                                                         TmsAcknowledgement ack,
                                                         Instant now) {
        Shipment shipment = shipmentPersistence.findById(command.shipmentId())
                .orElseThrow(() -> new ShipmentNotFoundException(command.shipmentId()));
        UUID requestId = parseUuidQuiet(ack.requestId());
        shipment.markTmsNotified(now, requestId);
        shipmentPersistence.save(shipment);

        OutboundSaga saga = sagaPersistence.findByOrderId(shipment.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(shipment.getOrderId()));
        // SHIPPED_NOT_NOTIFIED → SHIPPED → COMPLETED. The two-step
        // transition mirrors the happy-path (saga's confirm-shipping fires
        // SHIPPED, inventory.confirmed fires COMPLETED). For the retry the
        // stock was already consumed in the original ConfirmShippingService
        // commit; we don't wait for a fresh inventory.confirmed event.
        saga.recoverFromNotifyFailed(now);
        sagaPersistence.save(saga);
        sagaCoordinator.onInventoryConfirmed(saga.sagaId());

        return new RetryTmsNotificationResult(
                command.shipmentId(),
                shipment.getTmsStatus().name(),
                shipment.getTmsNotifiedAt(),
                shipment.getTrackingNo(),
                SagaStatus.COMPLETED.name(),
                now,
                command.actorId());
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
