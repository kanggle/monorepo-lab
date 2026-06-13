package com.example.settlement.infrastructure.event;

import com.example.settlement.application.service.AccruePaymentCommand;
import com.example.settlement.application.service.RecordOrderSnapshotCommand;
import com.example.settlement.application.service.ReversePaymentCommand;
import com.example.settlement.application.service.SettlementService;
import com.example.settlement.domain.repository.ProcessedEventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementConsumersTest {

    @Mock
    private SettlementService settlementService;
    @Mock
    private ProcessedEventStore processedEventStore;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    // ── OrderPlaced ────────────────────────────────────────────────────────

    @Test
    void orderPlaced_derives_tenant_from_envelope_and_grosses_lines() {
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-1", "OrderPlaced")).thenReturn(false);

        consumer.handle(new OrderPlacedEvent("evt-1", "OrderPlaced", "tenantA",
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
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-1", "OrderPlaced")).thenReturn(false);

        consumer.handle(new OrderPlacedEvent("evt-1", "OrderPlaced", null,
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
        var consumer = new OrderPlacedSnapshotConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-1", "OrderPlaced")).thenReturn(true);

        consumer.handle(new OrderPlacedEvent("evt-1", "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-1", List.of())));

        verify(settlementService, never()).recordSnapshot(any());
    }

    // ── PaymentCompleted ───────────────────────────────────────────────────

    @Test
    void paymentCompleted_accrues_with_no_tenant_in_payment_event() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-2", "PaymentCompleted")).thenReturn(false);

        consumer.handle(new PaymentEvent("evt-2", "PaymentCompleted",
                new PaymentEvent.Payload("order-1", "pay-1", "2026-06-13T00:00:00Z", null)));

        ArgumentCaptor<AccruePaymentCommand> captor = ArgumentCaptor.forClass(AccruePaymentCommand.class);
        verify(settlementService).accrue(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().paymentId()).isEqualTo("pay-1");
    }

    @Test
    void paymentCompleted_duplicate_is_skipped() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-2", "PaymentCompleted")).thenReturn(true);

        consumer.handle(new PaymentEvent("evt-2", "PaymentCompleted",
                new PaymentEvent.Payload("order-1", "pay-1", null, null)));

        verify(settlementService, never()).accrue(any());
    }

    @Test
    void paymentCompleted_missing_ids_is_skipped() {
        var consumer = new PaymentCompletedAccrualConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate(anyString(), anyString())).thenReturn(false);

        consumer.handle(new PaymentEvent("evt-2", "PaymentCompleted",
                new PaymentEvent.Payload(null, "pay-1", null, null)));

        verify(settlementService, never()).accrue(any());
    }

    // ── PaymentRefunded ────────────────────────────────────────────────────

    @Test
    void paymentRefunded_reverses() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-3", "PaymentRefunded")).thenReturn(false);

        consumer.handle(new PaymentEvent("evt-3", "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", null, "2026-06-13T01:00:00Z")));

        ArgumentCaptor<ReversePaymentCommand> captor = ArgumentCaptor.forClass(ReversePaymentCommand.class);
        verify(settlementService).reverse(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().paymentId()).isEqualTo("refund-1");
    }

    @Test
    void paymentRefunded_duplicate_is_skipped() {
        var consumer = new PaymentRefundedReversalConsumer(settlementService, processedEventStore, objectMapper);
        when(processedEventStore.isDuplicate("evt-3", "PaymentRefunded")).thenReturn(true);

        consumer.handle(new PaymentEvent("evt-3", "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", null, null)));

        verify(settlementService, never()).reverse(any());
    }
}
