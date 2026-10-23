package com.wms.outbound.adapter.in.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.messaging.dedupe.EventDedupePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit test for {@link FulfillmentCancelRequestedConsumer} (TASK-BE-650,
 * ADR-MONO-022 D4 v2(d) X-A). Mocks the {@link CancelOrderUseCase} and the order
 * persistence port; uses a real {@link EventEnvelopeParser} + a hand-rolled
 * {@link EventDedupePort} fake (mirroring {@code ManualShipConfirmConsumerTest}).
 *
 * <p>One test per row of the X-A state table in
 * {@code ecommerce-fulfillment-subscriptions.md} § Ecommerce-initiated cancel, plus
 * redelivery, unknown {@code orderNo}, malformed input, and the WRITE-only role set.
 */
@ExtendWith(MockitoExtension.class)
class FulfillmentCancelRequestedConsumerTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private OrderPersistencePort orderPersistence;
    @Mock
    private CancelOrderUseCase cancelOrderUseCase;

    private FakeEventDedupePort dedupePort;
    private FulfillmentCancelRequestedConsumer consumer;

    private Logger consumerLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        dedupePort = new FakeEventDedupePort();
        consumer = new FulfillmentCancelRequestedConsumer(
                new EventEnvelopeParser(new ObjectMapper()),
                dedupePort,
                orderPersistence,
                cancelOrderUseCase);
        consumerLogger = (Logger) LoggerFactory.getLogger(FulfillmentCancelRequestedConsumer.class);
        logs = new ListAppender<>();
        logs.start();
        consumerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        consumerLogger.detachAppender(logs);
    }

    // ------------------------------------------------------------------
    //  Row 1 — RECEIVED / PICKING: auto-cancel through the existing use-case
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"RECEIVED", "PICKING"})
    void prePickOrderIsCancelledThroughExistingUseCase(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6001"))
                .thenReturn(Optional.of(order(orderId, "ECO-6001", status)));

        consumer.onMessage(event(UUID.randomUUID(), "ECO-6001", "ORDER_CANCELLED"), null);

        ArgumentCaptor<CancelOrderCommand> captor = ArgumentCaptor.forClass(CancelOrderCommand.class);
        verify(cancelOrderUseCase).cancel(captor.capture());
        CancelOrderCommand cmd = captor.getValue();
        assertThat(cmd.orderId()).isEqualTo(orderId);
        assertThat(cmd.reason()).isEqualTo("ORDER_CANCELLED");
        // -1 sentinel => the service skips the early optimistic-lock check for the
        // system trigger (no client If-Match), as ManualShipConfirmConsumer does.
        assertThat(cmd.expectedVersion()).isEqualTo(-1L);
        assertThat(cmd.actorId()).isEqualTo("system:fulfillment-cancel-request");
    }

    /**
     * Failure Scenario 1 of the ticket: the system request must never carry the
     * ADMIN scope. Exactly {@code {ROLE_OUTBOUND_WRITE}} — not "contains WRITE", so
     * an added ADMIN role would fail this test.
     */
    @Test
    void systemCallerCarriesExactlyTheWriteRoleNeverAdmin() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6002"))
                .thenReturn(Optional.of(order(orderId, "ECO-6002", OrderStatus.PICKING)));

        consumer.onMessage(event(UUID.randomUUID(), "ECO-6002", "ORDER_CANCELLED"), null);

        ArgumentCaptor<CancelOrderCommand> captor = ArgumentCaptor.forClass(CancelOrderCommand.class);
        verify(cancelOrderUseCase).cancel(captor.capture());
        assertThat(captor.getValue().callerRoles())
                .containsExactly("ROLE_OUTBOUND_WRITE")
                .doesNotContain("ROLE_OUTBOUND_ADMIN");
    }

    @Test
    void absentReasonDefaultsToOrderCancelled() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6003"))
                .thenReturn(Optional.of(order(orderId, "ECO-6003", OrderStatus.RECEIVED)));

        consumer.onMessage(event(UUID.randomUUID(), "ECO-6003", null), null);

        ArgumentCaptor<CancelOrderCommand> captor = ArgumentCaptor.forClass(CancelOrderCommand.class);
        verify(cancelOrderUseCase).cancel(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("ORDER_CANCELLED");
    }

    /**
     * Race: the consumer read {@code PICKING}, but by the time the service re-reads
     * the order it is {@code PICKED}, so the service's own role guard refuses the
     * WRITE-only caller. The consumer must let that refusal propagate (retry →
     * re-read → ALERT), and must not retry the cancel itself with a wider role.
     */
    @Test
    void serviceRefusalIsPropagatedAndNeverRetriedWithAWiderRole() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6004"))
                .thenReturn(Optional.of(order(orderId, "ECO-6004", OrderStatus.PICKING)));
        when(cancelOrderUseCase.cancel(any()))
                .thenThrow(new AccessDeniedException("Role required: ROLE_OUTBOUND_ADMIN"));

        assertThatThrownBy(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-6004", "ORDER_CANCELLED"), null))
                .isInstanceOf(AccessDeniedException.class);

        ArgumentCaptor<CancelOrderCommand> captor = ArgumentCaptor.forClass(CancelOrderCommand.class);
        verify(cancelOrderUseCase, times(1)).cancel(captor.capture());
        assertThat(captor.getValue().callerRoles()).containsExactly("ROLE_OUTBOUND_WRITE");
    }

    // ------------------------------------------------------------------
    //  Row 2 — PICKED / PACKING / PACKED: no cancel, ALERT
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PICKED", "PACKING", "PACKED"})
    void postPickOrderIsNotCancelledAndRaisesAlert(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6101"))
                .thenReturn(Optional.of(order(orderId, "ECO-6101", status)));

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-6101", "ORDER_CANCELLED"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(cancelOrderUseCase);
        assertThat(logs.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg)
                        .startsWith("ALERT")
                        .contains("orderNo=ECO-6101")
                        .contains("status=" + status.name()));
    }

    // ------------------------------------------------------------------
    //  Row 3 — SHIPPED: logged no-op
    // ------------------------------------------------------------------

    @Test
    void shippedOrderIsLoggedNoOp() {
        when(orderPersistence.findByOrderNo("ECO-6201"))
                .thenReturn(Optional.of(order(UUID.randomUUID(), "ECO-6201", OrderStatus.SHIPPED)));

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-6201", "ORDER_CANCELLED"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(cancelOrderUseCase);
        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg).contains("orderNo=ECO-6201").contains("SHIPPED"));
    }

    // ------------------------------------------------------------------
    //  Row 4 — already terminal / unknown / redelivery: no-op
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "BACKORDERED"})
    void alreadyTerminalOrderIsNoOp(OrderStatus status) {
        when(orderPersistence.findByOrderNo("ECO-6301"))
                .thenReturn(Optional.of(order(UUID.randomUUID(), "ECO-6301", status)));

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-6301", "ORDER_CANCELLED"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    void unknownOrderNoIsNoOpWithoutException() {
        when(orderPersistence.findByOrderNo("ECO-NONE")).thenReturn(Optional.empty());

        assertThatCode(() ->
                consumer.onMessage(event(UUID.randomUUID(), "ECO-NONE", "ORDER_CANCELLED"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    void duplicateEventIdShortCircuitsBeforeApply() {
        UUID eventId = UUID.randomUUID();
        dedupePort.markAlreadySeen(eventId);

        consumer.onMessage(event(eventId, "ECO-6401", "ORDER_CANCELLED"), null);

        // Outer dedupe skips apply() entirely — no port or use-case interaction.
        verifyNoInteractions(orderPersistence);
        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    void redeliveryOfTheSameEnvelopeCancelsOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        when(orderPersistence.findByOrderNo("ECO-6402"))
                .thenReturn(Optional.of(order(orderId, "ECO-6402", OrderStatus.PICKING)));
        UUID eventId = UUID.randomUUID();
        String raw = event(eventId, "ECO-6402", "ORDER_CANCELLED");

        consumer.onMessage(raw, null);
        consumer.onMessage(raw, null);

        verify(cancelOrderUseCase, times(1)).cancel(any());
    }

    // ------------------------------------------------------------------
    //  Malformed input → DLT (non-retryable IllegalArgumentException)
    // ------------------------------------------------------------------

    @Test
    void missingOrderNoIsNonRetryable() {
        String raw = """
                {
                  "eventId": "%s",
                  "eventType": "ecommerce.fulfillment.cancel-requested",
                  "occurredAt": "2026-10-23T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "fulfillment",
                  "payload": { "reason": "ORDER_CANCELLED" }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.onMessage(raw, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orderPersistence);
        verify(cancelOrderUseCase, never()).cancel(any());
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static Order order(UUID id, String orderNo, OrderStatus status) {
        return new Order(
                id,
                orderNo,
                OrderSource.FULFILLMENT_ECOMMERCE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                status,
                0L,
                T0,
                "system",
                T0,
                "system",
                List.of(new OrderLine(UUID.randomUUID(), id, 1, UUID.randomUUID(), null, null, 1)));
    }

    /** Cancel-request envelope in the wms camelCase convention (contract § Event 3). */
    static String event(UUID eventId, String orderNo, String reason) {
        String reasonLine = reason == null ? "" : "\"reason\": \"" + reason + "\",";
        return """
                {
                  "eventId": "%s",
                  "eventType": "ecommerce.fulfillment.cancel-requested",
                  "occurredAt": "2026-10-23T10:00:00.000Z",
                  "aggregateId": "%s",
                  "aggregateType": "fulfillment",
                  "tenantId": "store-acme",
                  "payload": {
                    "orderNo": "%s",
                    "tenantId": "store-acme",
                    %s
                    "requestedAt": "2026-10-23T09:59:59.000Z"
                  }
                }
                """.formatted(eventId, UUID.randomUUID(), orderNo, reasonLine);
    }

    /** Inner-class fake of EventDedupePort — mirrors ManualShipConfirmConsumerTest. */
    static final class FakeEventDedupePort implements EventDedupePort {
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
