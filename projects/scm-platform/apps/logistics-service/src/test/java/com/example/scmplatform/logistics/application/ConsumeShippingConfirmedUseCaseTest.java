package com.example.scmplatform.logistics.application;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.logistics.application.routing.FulfillmentRouter;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedCommand;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedResult;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedUseCase;
import com.example.scmplatform.logistics.application.usecase.DispatchShipmentUseCase;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConsumeShippingConfirmedUseCase} — the seam use case's two-layer idempotency and the
 * vendor-failure-≠-consume-failure contract, at the unit level (the real {@link FulfillmentRouter}
 * + a real meter registry; the ports mocked). The Testcontainers Kafka IT is the CI-authoritative
 * end-to-end proof (Windows local cannot run it).
 */
@ExtendWith(MockitoExtension.class)
class ConsumeShippingConfirmedUseCaseTest {

    @Mock ProcessedEventPort processedEventPort;
    @Mock DispatchPersistencePort persistencePort;
    @Mock DispatchShipmentUseCase dispatchShipmentUseCase;

    ConsumeShippingConfirmedUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConsumeShippingConfirmedUseCase(
                processedEventPort, persistencePort, new FulfillmentRouter(),
                dispatchShipmentUseCase, new SimpleMeterRegistry());
    }

    private ConsumeShippingConfirmedCommand command(UUID eventId, UUID shipmentId, String carrierCode) {
        return new ConsumeShippingConfirmedCommand(
                eventId, "scm", shipmentId, "SHP-1", UUID.randomUUID(), "ORD-1", carrierCode);
    }

    private Dispatch dispatched(Dispatch pending) {
        pending.recordAck(TrackingNo.of("TRACK-1"), CarrierCode.of("USPS"), Carrier.EASYPOST, Instant.now());
        return pending;
    }

    @Test
    void freshEvent_createsPendingWithCarrierCode_dispatches_andMarksProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(persistencePort.findByShipmentId(shipmentId)).thenReturn(Optional.empty());
        when(dispatchShipmentUseCase.dispatch(any())).thenAnswer(inv -> dispatched(inv.getArgument(0)));

        ConsumeShippingConfirmedResult result = useCase.consume(command(eventId, shipmentId, "CJ-LOGISTICS"));

        assertThat(result.outcome()).isEqualTo(ConsumeShippingConfirmedResult.Outcome.DISPATCHED_OR_FAILED);
        assertThat(result.dispatch().getStatus()).isEqualTo(DispatchStatus.DISPATCHED);

        // The PENDING dispatch carries the seam's carrierCode as the stored routing signal.
        ArgumentCaptor<Dispatch> captor = ArgumentCaptor.forClass(Dispatch.class);
        verify(dispatchShipmentUseCase).dispatch(captor.capture());
        assertThat(captor.getValue().getShipmentId().value()).isEqualTo(shipmentId);
        assertThat(captor.getValue().getRequestedCarrierCode()).isEqualTo("CJ-LOGISTICS");

        // eventId recorded only AFTER dispatch (T8).
        verify(processedEventPort).markProcessed(eq(eventId), eq("scm"), any(Instant.class),
                eq(ConsumeShippingConfirmedUseCase.SOURCE_TOPIC));
    }

    @Test
    void nullCarrierCode_isPassedThroughRaw_notCoerced() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(persistencePort.findByShipmentId(shipmentId)).thenReturn(Optional.empty());
        when(dispatchShipmentUseCase.dispatch(any())).thenAnswer(inv -> dispatched(inv.getArgument(0)));

        useCase.consume(command(eventId, shipmentId, null));

        ArgumentCaptor<Dispatch> captor = ArgumentCaptor.forClass(Dispatch.class);
        verify(dispatchShipmentUseCase).dispatch(captor.capture());
        // The consumer must NOT coerce null → the router owns the null→default+degrade decision.
        assertThat(captor.getValue().getRequestedCarrierCode()).isNull();
    }

    @Test
    void duplicateEventId_layer1_skips_noDispatch_noMutation() {
        UUID eventId = UUID.randomUUID();
        when(processedEventPort.isDuplicate(eventId)).thenReturn(true);

        ConsumeShippingConfirmedResult result = useCase.consume(command(eventId, UUID.randomUUID(), "UPS"));

        assertThat(result.outcome()).isEqualTo(ConsumeShippingConfirmedResult.Outcome.DUPLICATE_EVENT);
        verify(persistencePort, never()).findByShipmentId(any());
        verify(dispatchShipmentUseCase, never()).dispatch(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void newEventId_sameShipmentId_layer2_noOp_noDoubleDispatch() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        Dispatch existing = Dispatch.create(UUID.randomUUID(), ShipmentId.of(shipmentId),
                "SHP-1", UUID.randomUUID(), "ORD-1", "scm", "UPS", Instant.now());
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(persistencePort.findByShipmentId(shipmentId)).thenReturn(Optional.of(existing));

        ConsumeShippingConfirmedResult result = useCase.consume(command(eventId, shipmentId, "UPS"));

        assertThat(result.outcome()).isEqualTo(ConsumeShippingConfirmedResult.Outcome.SHIPMENT_ALREADY_DISPATCHED);
        // No double-dispatch — the existing shipment already has a dispatch row.
        verify(dispatchShipmentUseCase, never()).dispatch(any());
        // But the NEW eventId is still recorded (so it is not reprocessed).
        verify(processedEventPort).markProcessed(eq(eventId), eq("scm"), any(Instant.class),
                eq(ConsumeShippingConfirmedUseCase.SOURCE_TOPIC));
    }

    @Test
    void vendorFailure_isNotAConsumeFailure_marksProcessedAndReturnsNormally() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        when(processedEventPort.isDuplicate(eventId)).thenReturn(false);
        when(persistencePort.findByShipmentId(shipmentId)).thenReturn(Optional.empty());
        // The vendor failed — DispatchShipmentUseCase SWALLOWS it into DISPATCH_FAILED and returns
        // normally (does NOT throw). The consume use case must therefore also return normally.
        when(dispatchShipmentUseCase.dispatch(any())).thenAnswer(inv -> {
            Dispatch pending = inv.getArgument(0);
            pending.recordFailure("EasyPost 503 exhausted", Instant.now());
            return pending;
        });

        ConsumeShippingConfirmedResult result = useCase.consume(command(eventId, shipmentId, "UPS"));

        assertThat(result.outcome()).isEqualTo(ConsumeShippingConfirmedResult.Outcome.DISPATCHED_OR_FAILED);
        assertThat(result.dispatch().getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        // Still marked processed + committed → the consumer acks → the event does NOT go to DLT (S5).
        verify(processedEventPort).markProcessed(eq(eventId), eq("scm"), any(Instant.class),
                eq(ConsumeShippingConfirmedUseCase.SOURCE_TOPIC));
    }
}
