package com.example.settlement.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.settlement.application.service.AccruePaymentCommand;
import com.example.settlement.application.service.RecordOrderSnapshotCommand;
import com.example.settlement.application.service.ReversePaymentCommand;
import com.example.settlement.application.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for settlement-service's 3 event consumers (ADR-MONO-058 D7, TASK-BE-569).
 * The service-local {@code ProcessedEventStore} domain port has been removed — consumers
 * now depend on the shared {@link EventDedupePort} directly, backed by
 * {@code SettlementEventDedupe}. Each event's {@code eventId} is a real UUID string
 * (settlement-events wire, matching production) since the consumer now runs the real
 * {@code EventFieldParser.parseUuidOrNull} parse path, not just the mocked dedupe.
 */
@ExtendWith(MockitoExtension.class)
class SettlementConsumersTest {

    @Mock
    private SettlementService settlementService;
    @Mock
    private EventDedupePort eventDedupePort;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @BeforeEach
    void stubDedupeAppliesByDefault() {
        lenient().when(eventDedupePort.process(any(), any(), any())).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return EventDedupePort.Outcome.APPLIED;
        });
    }

    // ── OrderPlaced ────────────────────────────────────────────────────────

    @Test
    void orderPlaced_derives_tenant_from_envelope_and_grosses_lines() {
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new OrderPlacedEvent(UUID.randomUUID().toString(), "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-1", List.of(
                        new OrderPlacedEvent.Item(15_000L, 2, "seller-1")))));

        ArgumentCaptor<RecordOrderSnapshotCommand> captor =
                ArgumentCaptor.forClass(RecordOrderSnapshotCommand.class);
        verify(settlementService).recordSnapshot(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("tenantA");
        assertThat(captor.getValue().lines()).singleElement()
                .satisfies(l -> {
                    assertThat(l.sellerId()).isEqualTo("seller-1");
                    assertThat(l.grossMinor()).isEqualTo(30_000L); // 15000 × 2
                });
    }

    @Test
    void orderPlaced_blank_envelope_tenant_falls_back_to_default() {
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new OrderPlacedEvent(UUID.randomUUID().toString(), "OrderPlaced", null,
                new OrderPlacedEvent.Payload("order-1", List.of(
                        new OrderPlacedEvent.Item(1000L, 1, null)))));

        ArgumentCaptor<RecordOrderSnapshotCommand> captor =
                ArgumentCaptor.forClass(RecordOrderSnapshotCommand.class);
        verify(settlementService).recordSnapshot(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("ecommerce");
        assertThat(captor.getValue().lines()).singleElement()
                .satisfies(l -> assertThat(l.sellerId()).isEqualTo("default"));
    }

    @Test
    void orderPlaced_duplicate_is_skipped() {
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, eventDedupePort, objectMapper);
        // doReturn(...).when(mock)... (not when(mock...).thenReturn(...)) — the latter would
        // invoke the real process() call to record the stub target, which would run straight
        // into the @BeforeEach thenAnswer's side-effecting Runnable.run() with a null argument.
        doReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE)
                .when(eventDedupePort).process(any(), eq("OrderPlaced"), any());

        consumer.handle(new OrderPlacedEvent(UUID.randomUUID().toString(), "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-1", List.of())));

        verify(settlementService, never()).recordSnapshot(any());
    }

    // ── PaymentCompleted ───────────────────────────────────────────────────

    @Test
    void paymentCompleted_accrues_with_no_tenant_in_payment_event() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentCompleted",
                new PaymentEvent.Payload("order-1", "pay-1", 30_000L, null, "2026-06-13T00:00:00Z", null)));

        ArgumentCaptor<AccruePaymentCommand> captor = ArgumentCaptor.forClass(AccruePaymentCommand.class);
        verify(settlementService).accrue(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().paymentId()).isEqualTo("pay-1");
    }

    @Test
    void paymentCompleted_duplicate_is_skipped() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, eventDedupePort, objectMapper);
        doReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE)
                .when(eventDedupePort).process(any(), eq("PaymentCompleted"), any());

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentCompleted",
                new PaymentEvent.Payload("order-1", "pay-1", 0L, null, null, null)));

        verify(settlementService, never()).accrue(any());
    }

    @Test
    void paymentCompleted_missing_ids_is_skipped() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentCompleted",
                new PaymentEvent.Payload(null, "pay-1", 0L, null, null, null)));

        verify(settlementService, never()).accrue(any());
    }

    // ── PaymentRefunded ────────────────────────────────────────────────────

    @Test
    void paymentRefunded_partial_propagates_amount_and_fullyRefunded_false() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", 12_000L, false, null, "2026-06-13T01:00:00Z")));

        ArgumentCaptor<ReversePaymentCommand> captor = ArgumentCaptor.forClass(ReversePaymentCommand.class);
        verify(settlementService).reverse(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().paymentId()).isEqualTo("refund-1");
        // refundAmount + fullyRefunded must flow through from the event payload.
        assertThat(captor.getValue().refundAmount()).isEqualTo(12_000L);
        assertThat(captor.getValue().fullyRefunded()).isFalse();
    }

    @Test
    void paymentRefunded_full_propagates_fullyRefunded_true() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", 30_000L, true, null, "2026-06-13T01:00:00Z")));

        ArgumentCaptor<ReversePaymentCommand> captor = ArgumentCaptor.forClass(ReversePaymentCommand.class);
        verify(settlementService).reverse(captor.capture());
        assertThat(captor.getValue().refundAmount()).isEqualTo(30_000L);
        assertThat(captor.getValue().fullyRefunded()).isTrue();
    }

    @Test
    void paymentRefunded_legacy_null_fullyRefunded_treated_as_full() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, eventDedupePort, objectMapper);

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", 30_000L, null, null, "2026-06-13T01:00:00Z")));

        ArgumentCaptor<ReversePaymentCommand> captor = ArgumentCaptor.forClass(ReversePaymentCommand.class);
        verify(settlementService).reverse(captor.capture());
        assertThat(captor.getValue().fullyRefunded()).isTrue();
    }

    @Test
    void paymentRefunded_duplicate_is_skipped() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, eventDedupePort, objectMapper);
        doReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE)
                .when(eventDedupePort).process(any(), eq("PaymentRefunded"), any());

        consumer.handle(new PaymentEvent(UUID.randomUUID().toString(), "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", 0L, null, null, null)));

        verify(settlementService, never()).reverse(any());
    }
}
