package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.port.in.ConfirmShippingUseCase;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link ManualShipConfirmConsumer} (TASK-MONO-305, ADR-MONO-022
 * D4 v2(c)). Mocks the {@link ConfirmShippingUseCase} and the persistence ports;
 * uses a real {@link EventEnvelopeParser} + a hand-rolled {@link EventDedupePort}
 * fake (mirroring {@code FulfillmentRequestedConsumerTest}'s outer-dedupe style).
 */
@ExtendWith(MockitoExtension.class)
class ManualShipConfirmConsumerTest {

    private static final Instant T0 = Instant.parse("2026-06-24T10:00:00Z");

    @Mock
    private OrderPersistencePort orderPersistence;
    @Mock
    private SagaPersistencePort sagaPersistence;
    @Mock
    private ConfirmShippingUseCase confirmShippingUseCase;

    private FakeEventDedupePort dedupePort;
    private ManualShipConfirmConsumer consumer;

    @BeforeEach
    void setUp() {
        dedupePort = new FakeEventDedupePort();
        consumer = new ManualShipConfirmConsumer(
                new EventEnvelopeParser(new ObjectMapper()),
                dedupePort,
                orderPersistence,
                sagaPersistence,
                confirmShippingUseCase);
    }

    @Test
    void packingConfirmedSagaTriggersConfirmShipping() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, "ECO-5001");
        when(orderPersistence.findByOrderNo("ECO-5001")).thenReturn(Optional.of(order));
        when(sagaPersistence.findByOrderId(orderId))
                .thenReturn(Optional.of(saga(orderId, SagaStatus.PACKING_CONFIRMED)));

        consumer.onMessage(event(UUID.randomUUID(), "ECO-5001", "CJ-LOGISTICS", "TRK-99"), null);

        ArgumentCaptor<ConfirmShippingCommand> captor =
                ArgumentCaptor.forClass(ConfirmShippingCommand.class);
        verify(confirmShippingUseCase).confirm(captor.capture());
        ConfirmShippingCommand cmd = captor.getValue();
        assertThat(cmd.orderId()).isEqualTo(orderId);
        // -1 sentinel => the service skips the optimistic-lock version check for
        // the system trigger while still enforcing the PACKED status invariant.
        assertThat(cmd.expectedVersion()).isEqualTo(-1L);
        assertThat(cmd.carrierCode()).isEqualTo("CJ-LOGISTICS");
        // Explicit write role so the use-case role guard passes for the system trigger.
        assertThat(cmd.callerRoles()).contains("ROLE_OUTBOUND_WRITE");
        assertThat(cmd.actorId()).isEqualTo("system:manual-ship-confirm");
    }

    @Test
    void blankCarrierFallsBackToWmsDefault() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-5002"))
                .thenReturn(Optional.of(order(orderId, "ECO-5002")));
        when(sagaPersistence.findByOrderId(orderId))
                .thenReturn(Optional.of(saga(orderId, SagaStatus.PACKING_CONFIRMED)));

        // carrierCode null in the payload.
        consumer.onMessage(event(UUID.randomUUID(), "ECO-5002", null, null), null);

        ArgumentCaptor<ConfirmShippingCommand> captor =
                ArgumentCaptor.forClass(ConfirmShippingCommand.class);
        verify(confirmShippingUseCase).confirm(captor.capture());
        assertThat(captor.getValue().carrierCode()).isEqualTo("WMS-MANUAL");
    }

    @Test
    void earlierSagaStateIsWarnNoOpWithoutException() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-5003"))
                .thenReturn(Optional.of(order(orderId, "ECO-5003")));
        when(sagaPersistence.findByOrderId(orderId))
                .thenReturn(Optional.of(saga(orderId, SagaStatus.RESERVED)));

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-5003", "CJ", "T1"), null))
                .doesNotThrowAnyException();

        verify(confirmShippingUseCase, never()).confirm(any());
    }

    @Test
    void alreadyShippedSagaIsTerminalNoOp() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-5004"))
                .thenReturn(Optional.of(order(orderId, "ECO-5004")));
        when(sagaPersistence.findByOrderId(orderId))
                .thenReturn(Optional.of(saga(orderId, SagaStatus.SHIPPED)));

        consumer.onMessage(event(UUID.randomUUID(), "ECO-5004", "CJ", "T1"), null);

        verify(confirmShippingUseCase, never()).confirm(any());
    }

    @Test
    void unknownOrderNoIsNoOpWithoutException() {
        when(orderPersistence.findByOrderNo("ECO-NONE")).thenReturn(Optional.empty());

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-NONE", "CJ", "T1"), null))
                .doesNotThrowAnyException();

        // The order never routed through wms — no saga lookup, no confirm.
        verifyNoInteractions(sagaPersistence);
        verify(confirmShippingUseCase, never()).confirm(any());
    }

    @Test
    void duplicateEventIdShortCircuitsBeforeApply() {
        UUID eventId = UUID.randomUUID();
        dedupePort.markAlreadySeen(eventId);

        consumer.onMessage(event(eventId, "ECO-5005", "CJ", "T1"), null);

        // Outer dedupe skips apply() entirely — no port or use-case interaction.
        verifyNoInteractions(orderPersistence);
        verifyNoInteractions(sagaPersistence);
        verify(confirmShippingUseCase, never()).confirm(any());
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static Order order(UUID id, String orderNo) {
        return new Order(
                id,
                orderNo,
                OrderSource.FULFILLMENT_ECOMMERCE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                OrderStatus.PACKED,
                0L,
                T0,
                "system",
                T0,
                "system",
                List.of(new OrderLine(UUID.randomUUID(), id, 1, UUID.randomUUID(), null, 1)));
    }

    private static OutboundSaga saga(UUID orderId, SagaStatus status) {
        return new OutboundSaga(
                UUID.randomUUID(),
                orderId,
                status,
                UUID.randomUUID(),
                null,
                T0,
                T0,
                0L);
    }

    /** Manual ship-confirm envelope in the wms camelCase convention. */
    private static String event(UUID eventId, String orderNo, String carrierCode, String trackingNo) {
        String carrierLine = carrierCode == null
                ? "\"carrierCode\": null," : "\"carrierCode\": \"" + carrierCode + "\",";
        String trackingLine = trackingNo == null
                ? "\"trackingNo\": null" : "\"trackingNo\": \"" + trackingNo + "\"";
        return """
                {
                  "eventId": "%s",
                  "eventType": "ecommerce.shipping.manual-confirm-requested",
                  "occurredAt": "2026-06-24T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "shipping",
                  "tenantId": "store-acme",
                  "payload": {
                    "orderNo": "%s",
                    %s
                    %s
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), orderNo, carrierLine, trackingLine);
    }

    /** Inner-class fake of EventDedupePort — mirrors FulfillmentRequestedConsumerTest. */
    private static final class FakeEventDedupePort implements EventDedupePort {
        private final Set<UUID> seen = new HashSet<>();

        void markAlreadySeen(UUID eventId) {
            seen.add(eventId);
        }

        @Override
        public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (seen.contains(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            seen.add(eventId);
            work.run();
            return Outcome.APPLIED;
        }
    }
}
