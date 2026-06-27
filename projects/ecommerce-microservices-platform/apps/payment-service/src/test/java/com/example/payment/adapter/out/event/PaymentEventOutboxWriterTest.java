package com.example.payment.adapter.out.event;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the {@link PaymentEventOutboxWriter} write path (TASK-BE-449,
 * outbox v2). Asserts each event persists a {@code payment_outbox} row whose
 * wire-relevant fields are preserved exactly: the row {@code event_id} reuses the
 * envelope {@code event_id}, the payload is the byte-identical serialized envelope,
 * {@code aggregate_id} is the {@code paymentId} (Kafka key source), and the routing
 * key {@code eventType} is the literal event-type name.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventOutboxWriter 단위 테스트 (outbox v2)")
class PaymentEventOutboxWriterTest {

    @Mock
    private PaymentOutboxRepository outboxRepository;

    private PaymentEventOutboxWriter writer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        writer = new PaymentEventOutboxWriter(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("publishPaymentCompleted → payment_outbox v2 행 (Payment/PaymentCompleted/직렬화 envelope)")
    void publishPaymentCompleted_savesV2Row() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId, "PaymentCompleted",
                Instant.parse("2026-03-25T00:00:00Z").toString(),
                "payment-service", "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L,
                        Instant.parse("2026-03-25T00:00:00Z").toString()));

        writer.publishPaymentCompleted(event);

        PaymentOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(row.getEventType()).isEqualTo("PaymentCompleted");
        assertThat(row.getAggregateType()).isEqualTo("Payment");
        assertThat(row.getAggregateId()).isEqualTo("pay-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-03-25T00:00:00Z"));
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("publishPaymentRefunded → payment_outbox v2 행")
    void publishPaymentRefunded_savesV2Row() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                eventId, "PaymentRefunded",
                Instant.parse("2026-03-25T01:00:00Z").toString(),
                "payment-service", "ecommerce",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 30000L, 30000L, true,
                        Instant.parse("2026-03-25T01:00:00Z").toString()));

        writer.publishPaymentRefunded(event);

        PaymentOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(row.getEventType()).isEqualTo("PaymentRefunded");
        assertThat(row.getAggregateId()).isEqualTo("pay-1");
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
    }

    @Test
    @DisplayName("publishPaymentRefundStranded → payment_outbox v2 행 (TASK-BE-437)")
    void publishPaymentRefundStranded_savesV2Row() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PaymentRefundStrandedEvent event = new PaymentRefundStrandedEvent(
                eventId, "PaymentRefundStranded",
                Instant.parse("2026-06-25T02:00:00Z").toString(),
                "payment-service", "ecommerce",
                new PaymentRefundStrandedEvent.Payload("pay-1", "order-1", "pk_test_123", 30000L,
                        "PgGatewayUnavailableException", Instant.parse("2026-06-25T02:00:00Z").toString()));

        writer.publishPaymentRefundStranded(event);

        PaymentOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("PaymentRefundStranded");
        assertThat(row.getAggregateId()).isEqualTo("pay-1");
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(eventId));
    }

    @Test
    @DisplayName("publishPaymentRefundUnresolved → payment_outbox v2 행 (TASK-BE-438)")
    void publishPaymentRefundUnresolved_savesV2Row() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PaymentRefundUnresolvedEvent event = new PaymentRefundUnresolvedEvent(
                eventId, "PaymentRefundUnresolved",
                Instant.parse("2026-06-26T03:00:00Z").toString(),
                "payment-service", "ecommerce",
                new PaymentRefundUnresolvedEvent.Payload("pay-1", "order-1", "pk_test_123", 30000L,
                        "PgGatewayUnavailableException", 8, "attempt cap exhausted",
                        Instant.parse("2026-06-26T03:00:00Z").toString()));

        writer.publishPaymentRefundUnresolved(event);

        PaymentOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("PaymentRefundUnresolved");
        assertThat(row.getAggregateId()).isEqualTo("pay-1");
    }

    @Test
    @DisplayName("저장된 envelope 은 event_id / event_type / source / tenant_id / payload 필드를 유지한다 (wire 보존)")
    void serializedEnvelope_preservesContractFields() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId, "PaymentCompleted",
                Instant.parse("2026-03-25T00:00:00Z").toString(),
                "payment-service", "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L,
                        Instant.parse("2026-03-25T00:00:00Z").toString()));

        writer.publishPaymentCompleted(event);

        String envelope = capturedRow().getPayload();
        assertThat(envelope).contains("\"event_id\":\"" + eventId + "\"");
        assertThat(envelope).contains("\"event_type\":\"PaymentCompleted\"");
        assertThat(envelope).contains("\"source\":\"payment-service\"");
        assertThat(envelope).contains("\"tenant_id\":\"ecommerce\"");
        assertThat(envelope).contains("\"payload\":");
    }

    private PaymentOutboxEntity capturedRow() {
        ArgumentCaptor<PaymentOutboxEntity> captor = ArgumentCaptor.forClass(PaymentOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
