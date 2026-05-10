package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.result.RetryTmsNotificationResult;

/**
 * In-port for {@code POST /api/v1/outbound/shipments/{id}:retry-tms-notify}
 * (per {@code outbound-service-api.md} §4.3).
 *
 * <p>Re-invokes {@code ShipmentNotificationPort.notify(shipmentId)} for a
 * shipment whose {@code tms_status} is {@code NOTIFY_FAILED} (saga in
 * {@code SHIPPED_NOT_NOTIFIED}). On vendor success the shipment moves to
 * {@code NOTIFIED} and the saga to {@code COMPLETED}.
 *
 * <p>Naturally idempotent — calling twice on an already-{@code NOTIFIED}
 * shipment returns the cached ack via the adapter's
 * {@code tms_request_dedupe} short-circuit (still: the use-case rejects
 * with {@code STATE_TRANSITION_INVALID} for non-{@code NOTIFY_FAILED}
 * states to keep the contract crisp).
 */
public interface RetryTmsNotificationUseCase {

    RetryTmsNotificationResult retry(RetryTmsNotificationCommand command);
}
