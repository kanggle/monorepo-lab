package com.example.scmplatform.logistics.adapter.inbound.messaging;

import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedCommand;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedResult;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ShippingConfirmedConsumer} — the ack/DLT branching at the adapter level.
 *
 * <ul>
 *   <li><b>Success (incl. a DISPATCH_FAILED outcome):</b> {@code consume} returns normally →
 *       {@code ack.acknowledge()} is called (the offset advances; NOT DLT). This is the ack site
 *       the vendor-failure-≠-consume-failure invariant depends on.</li>
 *   <li><b>Malformed envelope</b> (null value / unparseable / null eventId): a
 *       {@link NonRetryableConsumerException} propagates (→ {@code DefaultErrorHandler} DLT) and the
 *       record is NOT ack'd here.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ShippingConfirmedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock ConsumeShippingConfirmedUseCase useCase;
    @Mock Acknowledgment ack;

    private ShippingConfirmedConsumer consumer() {
        return new ShippingConfirmedConsumer(useCase, objectMapper);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(ShippingConfirmedConsumer.TOPIC, 0, 0L, "key", value);
    }

    private String envelope(UUID eventId, UUID shipmentId, String carrierCode) {
        Map<String, Object> env = new LinkedHashMap<>();
        if (eventId != null) {
            env.put("eventId", eventId.toString());
        }
        env.put("eventType", "outbound.shipping.confirmed");
        env.put("eventVersion", 1);
        env.put("occurredAt", Instant.now().toString());
        env.put("producer", "outbound-service");
        env.put("aggregateType", "shipment");
        env.put("aggregateId", shipmentId == null ? null : shipmentId.toString());
        env.put("tenantId", "scm");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipmentId", shipmentId == null ? null : shipmentId.toString());
        payload.put("shipmentNo", "SHP-20260429-0001");
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNo", "ORD-1");
        payload.put("warehouseId", UUID.randomUUID().toString());
        if (carrierCode != null) {
            payload.put("carrierCode", carrierCode);
        }
        payload.put("shippedAt", Instant.now().toString());
        // An unknown field must be ignored (forward-compat).
        payload.put("someFutureField", "ignore-me");
        env.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void validEnvelope_mapsCommand_consumes_andAcks() {
        UUID eventId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();

        consumer().consume(record(envelope(eventId, shipmentId, "CJ-LOGISTICS")), ack);

        ArgumentCaptor<ConsumeShippingConfirmedCommand> captor =
                ArgumentCaptor.forClass(ConsumeShippingConfirmedCommand.class);
        verify(useCase).consume(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        assertThat(captor.getValue().shipmentId()).isEqualTo(shipmentId);
        assertThat(captor.getValue().carrierCode()).isEqualTo("CJ-LOGISTICS");
        assertThat(captor.getValue().tenantId()).isEqualTo("scm");
        // ACK SITE — reached because consume() returned normally (this is also the vendor-failure path).
        verify(ack).acknowledge();
    }

    @Test
    void nullCarrierCode_isMappedAsNull_stillConsumesAndAcks() {
        consumer().consume(record(envelope(UUID.randomUUID(), UUID.randomUUID(), null)), ack);

        ArgumentCaptor<ConsumeShippingConfirmedCommand> captor =
                ArgumentCaptor.forClass(ConsumeShippingConfirmedCommand.class);
        verify(useCase).consume(captor.capture());
        assertThat(captor.getValue().carrierCode()).isNull();
        verify(ack).acknowledge();
    }

    @Test
    void nullMessageValue_nonRetryable_notAcked_notConsumed() {
        assertThatThrownBy(() -> consumer().consume(record(null), ack))
                .isInstanceOf(NonRetryableConsumerException.class);
        verify(useCase, never()).consume(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void malformedJson_nonRetryable_notAcked_notConsumed() {
        assertThatThrownBy(() -> consumer().consume(record("{ this is not json"), ack))
                .isInstanceOf(NonRetryableConsumerException.class);
        verify(useCase, never()).consume(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void nullEventId_nonRetryable_notAcked_notConsumed() {
        // Structurally valid JSON but missing the T8 idempotency key → non-retryable DLT.
        String value = envelope(null, UUID.randomUUID(), "UPS");
        assertThatThrownBy(() -> consumer().consume(record(value), ack))
                .isInstanceOf(NonRetryableConsumerException.class);
        verify(useCase, never()).consume(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void nullShipmentId_nonRetryable_notAcked_notConsumed() {
        // No dispatch identity / shipment_id dedup key → non-retryable DLT.
        String value = envelope(UUID.randomUUID(), null, "UPS");
        assertThatThrownBy(() -> consumer().consume(record(value), ack))
                .isInstanceOf(NonRetryableConsumerException.class);
        verify(useCase, never()).consume(any());
        verify(ack, never()).acknowledge();
    }
}
