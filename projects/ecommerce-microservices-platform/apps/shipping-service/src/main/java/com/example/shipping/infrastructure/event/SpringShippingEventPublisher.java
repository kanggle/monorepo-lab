package com.example.shipping.infrastructure.event;

import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringShippingEventPublisher implements ShippingEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void publishShippingStatusChanged(String shippingId, String orderId, String userId,
                                              ShippingStatus previousStatus, ShippingStatus newStatus,
                                              String trackingNumber, String carrier) {
        ShippingStatusChangedMessage message = new ShippingStatusChangedMessage(
                UUID.randomUUID().toString(),
                "ShippingStatusChanged",
                Instant.now(clock).toString(),
                "shipping-service",
                new ShippingStatusChangedMessage.Payload(
                        shippingId, orderId, userId,
                        previousStatus.name(), newStatus.name(),
                        trackingNumber, carrier,
                        Instant.now(clock).toString()
                )
        );

        String payload = serialize(message);
        outboxWriter.save("Shipping", shippingId, "ShippingStatusChanged", payload);
    }

    @Override
    public void publishFulfillmentRequested(String orderId, String messageJson) {
        outboxWriter.save("Fulfillment", orderId, "FulfillmentRequested", messageJson);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    record ShippingStatusChangedMessage(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("occurred_at") String occurredAt,
            String source,
            Payload payload
    ) {
        record Payload(
                String shippingId,
                String orderId,
                String userId,
                String previousStatus,
                String newStatus,
                String trackingNumber,
                String carrier,
                String changedAt
        ) {}
    }
}
