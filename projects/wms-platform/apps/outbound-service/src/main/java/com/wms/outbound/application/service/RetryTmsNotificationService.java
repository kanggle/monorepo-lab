package com.wms.outbound.application.service;

import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.port.in.RetryTmsNotificationUseCase;
import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application service implementing {@link RetryTmsNotificationUseCase}
 * (TASK-BE-049).
 *
 * <h2>Flow</h2>
 *
 * <ol>
 *   <li>Authorize: {@code ROLE_OUTBOUND_ADMIN} required.</li>
 *   <li>Load Shipment + Saga via {@link RetryTmsPersistenceHelper#loadAndValidate};
 *       throws {@code TmsRetryNotAllowedException} when {@code tmsStatus !=
 *       NOTIFY_FAILED}.</li>
 *   <li>Re-invoke {@link ShipmentNotificationPort#notify(java.util.UUID)}
 *       — same {@code Idempotency-Key} as the original call so vendor
 *       dedupe holds; the local {@code tms_request_dedupe} short-circuits
 *       if a previous attempt actually reached the vendor.</li>
 *   <li>On success: delegate to
 *       {@link RetryTmsPersistenceHelper#markRetrySucceeded(RetryTmsNotificationCommand,
 *       TmsAcknowledgement, Instant)} which advances state in a fresh TX.</li>
 *   <li>On failure: increment alert metric and return the previously-known
 *       failed snapshot — the caller may retry later.</li>
 * </ol>
 *
 * <p>Per `transactional` T2 the TMS HTTP call is NOT inside the persistence
 * TX. Authorization happens in the application layer (architecture.md §7) —
 * not in the controller — so the role check is the first thing this method
 * does.
 */
@Service
public class RetryTmsNotificationService implements RetryTmsNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetryTmsNotificationService.class);
    private static final String ROLE_OUTBOUND_ADMIN = "ROLE_OUTBOUND_ADMIN";
    private static final String ALERT_METRIC = "outbound.alert.tms.notify.failure";

    private final ShipmentNotificationPort tmsPort;
    private final RetryTmsPersistenceHelper persistenceHelper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public RetryTmsNotificationService(ShipmentNotificationPort tmsPort,
                                       RetryTmsPersistenceHelper persistenceHelper,
                                       MeterRegistry meterRegistry,
                                       Clock clock) {
        this.tmsPort = tmsPort;
        this.persistenceHelper = persistenceHelper;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    public RetryTmsNotificationResult retry(RetryTmsNotificationCommand command) {
        AuthorizationGuards.requireRole(command.callerRoles(), ROLE_OUTBOUND_ADMIN);

        // Pre-check shipment + saga state in a short read TX.
        RetryTmsPersistenceHelper.ShipmentSnapshot snapshot =
                persistenceHelper.loadAndValidate(command.shipmentId());

        Instant now = clock.instant();

        // Invoke TMS adapter — outside any TX (matches T2). On exhaustion
        // the adapter throws ExternalServiceUnavailableException; we catch
        // here so the caller still gets a 200 with the failed snapshot
        // (per outbound-service-api.md §4.3 the contract returns 200 with
        // current shipment state on either path).
        TmsAcknowledgement ack;
        try {
            ack = tmsPort.notify(command.shipmentId());
        } catch (ExternalServiceUnavailableException ex) {
            meterRegistry.counter(ALERT_METRIC, "vendor", "tms").increment();
            log.warn("retry_tms_notify_failed shipmentId={} reason={}",
                    command.shipmentId(), ex.toString());
            return failedResult(command, snapshot, now);
        }

        if (ack == null || !ack.success()) {
            meterRegistry.counter(ALERT_METRIC, "vendor", "tms").increment();
            log.warn("retry_tms_notify_unsuccessful shipmentId={}", command.shipmentId());
            return failedResult(command, snapshot, now);
        }

        log.info("retry_tms_notify_succeeded shipmentId={} requestId={}",
                command.shipmentId(), ack.requestId());
        return persistenceHelper.markRetrySucceeded(command, ack, now);
    }

    private static RetryTmsNotificationResult failedResult(RetryTmsNotificationCommand command,
                                                           RetryTmsPersistenceHelper.ShipmentSnapshot snapshot,
                                                           Instant now) {
        return new RetryTmsNotificationResult(
                command.shipmentId(),
                snapshot.tmsStatus().name(),
                snapshot.tmsNotifiedAt(),
                snapshot.trackingNo(),
                snapshot.sagaStatus().name(),
                now,
                command.actorId());
    }
}
