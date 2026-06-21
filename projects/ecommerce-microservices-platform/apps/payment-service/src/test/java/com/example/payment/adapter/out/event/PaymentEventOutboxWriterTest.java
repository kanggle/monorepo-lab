package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.OutboxWriter;
import com.example.payment.application.event.PaymentCompletedEvent;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventOutboxWriter 단위 테스트")
class PaymentEventOutboxWriterTest {

    @Mock
    private OutboxWriter outboxWriter;

    private PaymentEventOutboxWriter writer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        writer = new PaymentEventOutboxWriter(outboxWriter, objectMapper);
    }

    @Test
    @DisplayName("publishPaymentCompleted 호출 시 outbox 에 Payment/PaymentCompleted/직렬화된 envelope 가 저장된다")
    void publishPaymentCompleted_savesToOutbox() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                "PaymentCompleted",
                Instant.parse("2026-03-25T00:00:00Z").toString(),
                "payment-service",
                "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L,
                        Instant.parse("2026-03-25T00:00:00Z").toString())
        );

        writer.publishPaymentCompleted(event);

        String expectedPayload = objectMapper.writeValueAsString(event);
        verify(outboxWriter).save(eq("Payment"), eq("pay-1"), eq("PaymentCompleted"), eq(expectedPayload));
    }

    @Test
    @DisplayName("publishPaymentRefunded 호출 시 outbox 에 Payment/PaymentRefunded/직렬화된 envelope 가 저장된다")
    void publishPaymentRefunded_savesToOutbox() throws Exception {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefunded",
                Instant.parse("2026-03-25T01:00:00Z").toString(),
                "payment-service",
                "ecommerce",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 30000L, 30000L, true,
                        Instant.parse("2026-03-25T01:00:00Z").toString())
        );

        writer.publishPaymentRefunded(event);

        String expectedPayload = objectMapper.writeValueAsString(event);
        verify(outboxWriter).save(eq("Payment"), eq("pay-1"), eq("PaymentRefunded"), eq(expectedPayload));
    }

    @Test
    @DisplayName("저장된 envelope 은 event_id / event_type / occurred_at / source / tenant_id / payload 필드를 유지한다")
    void serializedEnvelope_preservesContractFields() throws Exception {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "evt-1", "PaymentCompleted",
                Instant.parse("2026-03-25T00:00:00Z").toString(),
                "payment-service",
                "ecommerce",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L,
                        Instant.parse("2026-03-25T00:00:00Z").toString())
        );

        writer.publishPaymentCompleted(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Payment"), eq("pay-1"), eq("PaymentCompleted"), payloadCaptor.capture());

        String envelope = payloadCaptor.getValue();
        assertThat(envelope).contains("\"event_id\":\"evt-1\"");
        assertThat(envelope).contains("\"event_type\":\"PaymentCompleted\"");
        assertThat(envelope).contains("\"source\":\"payment-service\"");
        assertThat(envelope).contains("\"tenant_id\":\"ecommerce\"");
        assertThat(envelope).contains("\"payload\":");
    }
}
