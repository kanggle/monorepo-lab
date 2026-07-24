package com.example.scmplatform.logistics.application.usecase;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.logistics.application.routing.FulfillmentRouter;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The seam use case (ADR-053 §D4): turn one {@code outbound.shipping.confirmed} fact event into a
 * carrier dispatch, in <b>one transaction</b>, with <b>two-layer idempotency</b>.
 *
 * <ol>
 *   <li><b>Layer 1 — eventId (T8).</b> {@code processed_events}: a duplicate {@code eventId}
 *       (Kafka redelivery or wms outbox retry) is skipped with no mutation.</li>
 *   <li><b>Layer 2 — shipment_id.</b> A redelivery under a <b>new</b> {@code eventId} but the
 *       <b>same</b> {@code shipmentId} (wms republish) finds the existing dispatch and no-ops —
 *       the {@code dispatch.shipment_id} unique key is the business guard against double-dispatch.
 *       Both layers are required (task Failure Scenario C).</li>
 * </ol>
 *
 * A fresh event creates a {@code Dispatch(PENDING)} carrying the seam's {@code carrierCode} as the
 * stored routing signal ({@code requestedCarrierCode}), routes through the {@link FulfillmentRouter}
 * (Phase 1 → {@link FulfillmentRouter.FulfillmentMode#SELF} → carrier dispatch via
 * {@link DispatchShipmentUseCase}), and records {@code DISPATCHED} / {@code DISPATCH_FAILED}.
 *
 * <p><b>A vendor failure is NOT a consume failure (S5, Cat C).</b> {@link DispatchShipmentUseCase}
 * swallows a vendor {@code ShipmentDispatchException} into {@code DISPATCH_FAILED} and returns
 * normally — so this use case commits and the caller acks. Only a DB/infra fault (which throws) or
 * a malformed envelope (rejected at the adapter) reaches the retry→DLT path. Recovery for a failed
 * carrier is the operator {@code :retry} endpoint (a failed carrier never DLTs or blocks the seam).
 */
@Service
public class ConsumeShippingConfirmedUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsumeShippingConfirmedUseCase.class);

    /** Source topic recorded on {@code processed_events}. */
    public static final String SOURCE_TOPIC = "wms.outbound.shipping.confirmed.v1";
    /** Dedup-hit counter (architecture.md § Observability), labelled by which layer absorbed the redelivery. */
    static final String DEDUP_METRIC = "logistics_event_dedup_hits_total";

    private final ProcessedEventPort processedEventPort;
    private final DispatchPersistencePort persistencePort;
    private final FulfillmentRouter fulfillmentRouter;
    private final DispatchShipmentUseCase dispatchShipmentUseCase;
    private final MeterRegistry meterRegistry;

    public ConsumeShippingConfirmedUseCase(ProcessedEventPort processedEventPort,
                                           DispatchPersistencePort persistencePort,
                                           FulfillmentRouter fulfillmentRouter,
                                           DispatchShipmentUseCase dispatchShipmentUseCase,
                                           MeterRegistry meterRegistry) {
        this.processedEventPort = processedEventPort;
        this.persistencePort = persistencePort;
        this.fulfillmentRouter = fulfillmentRouter;
        this.dispatchShipmentUseCase = dispatchShipmentUseCase;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ConsumeShippingConfirmedResult consume(ConsumeShippingConfirmedCommand command) {
        // Layer 1 — eventId (T8). Duplicate delivery → skip, no mutation.
        if (processedEventPort.isDuplicate(command.eventId())) {
            meterRegistry.counter(DEDUP_METRIC, "layer", "event_id").increment();
            log.debug("Duplicate eventId={} on {} — skipping (T8)", command.eventId(), SOURCE_TOPIC);
            return ConsumeShippingConfirmedResult.duplicateEvent();
        }

        // Layer 2 — shipment_id. A redelivery under a NEW eventId for the SAME shipment no-ops.
        Optional<Dispatch> existing = persistencePort.findByShipmentId(command.shipmentId());
        if (existing.isPresent()) {
            meterRegistry.counter(DEDUP_METRIC, "layer", "shipment_id").increment();
            log.debug("shipmentId={} already has a dispatch — no double-dispatch; recording eventId={}",
                    command.shipmentId(), command.eventId());
            processedEventPort.markProcessed(command.eventId(), command.tenantId(), Instant.now(), SOURCE_TOPIC);
            return ConsumeShippingConfirmedResult.alreadyDispatched(existing.get());
        }

        // Fresh event → PENDING dispatch carrying the seam's carrierCode as the stored routing signal
        // (nullable, passed through raw: null → CarrierRouter default vendor + CARRIER_UNROUTABLE degrade).
        Dispatch dispatch = Dispatch.create(
                UUID.randomUUID(),
                ShipmentId.of(command.shipmentId()),
                command.shipmentNo(),
                command.orderId(),
                command.orderNo(),
                command.tenantId(),
                command.carrierCode(),
                Instant.now());

        // FulfillmentRouter seam (ADR-053 §D4). Phase 1 always resolves SELF → carrier dispatch.
        FulfillmentRouter.FulfillmentMode mode = fulfillmentRouter.route(dispatch);
        Dispatch result = switch (mode) {
            case SELF -> dispatchShipmentUseCase.dispatch(dispatch);
            // Phase 2, ADR-052 §D8-3 — guarded extension point, NOT active in Phase 1 (never reached,
            // since route() resolves SELF). If this ever fires, the router grew active 3PL routing:
            // pull it back to a separate Phase-2 task (task Failure Scenario A).
            case THIRD_PARTY_LOGISTICS -> throw new UnsupportedOperationException(
                    "3PL fulfillment routing is Phase 2 (ADR-052 §D8-3); the FulfillmentRouter 3PL "
                            + "arm is a documented extension point, not active in Phase 1");
        };

        processedEventPort.markProcessed(command.eventId(), command.tenantId(), Instant.now(), SOURCE_TOPIC);
        return ConsumeShippingConfirmedResult.dispatched(result);
    }
}
