package com.example.shipping.infrastructure.event;

import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.domain.model.ShippingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * shipping-service outbox write path (TASK-BE-446, outbox v2).
 *
 * <p>Persists one {@link ShippingOutboxEntity} ({@code shipping_outbox} table)
 * per event inside the caller's transaction. The {@link ShippingOutboxPublisher}
 * relay drains the table to Kafka.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter}. Wire is preserved exactly for all
 * three event types — the row {@code payload} is the byte-identical serialized
 * envelope (or, for the forward fulfillment leg, the already-serialized
 * {@code messageJson} stored verbatim), the routing key {@code eventType} and the
 * {@code aggregate_type}/{@code aggregate_id} are unchanged (so the Kafka record
 * key, which falls back to {@code aggregate_id}, matches v1), and the topics are
 * resolved by the same mixed-convention switch in {@link ShippingOutboxPublisher}.
 *
 * <p>{@code event_id}: the two structured envelopes ({@code ShippingStatusChanged},
 * {@code ManualShipConfirmRequested}) reuse the envelope's own {@code event_id} as
 * the row PK so the Kafka header {@code eventId} matches the payload. The forward
 * fulfillment leg receives an opaque pre-serialized {@code messageJson}, so a fresh
 * UUID is minted for the row PK; the wms outbound-service dedupes on the payload's
 * {@code eventId} (via its {@code EventEnvelopeParser}), not the Kafka header, so
 * this is wire-safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringShippingEventPublisher implements ShippingEventPublisher {

    private static final String AGGREGATE_TYPE_SHIPPING = "Shipping";
    private static final String AGGREGATE_TYPE_FULFILLMENT = "Fulfillment";

    private final ShippingOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void publishShippingStatusChanged(String tenantId, String shippingId, String orderId, String userId,
                                             ShippingStatus previousStatus, ShippingStatus newStatus,
                                             String trackingNumber, String carrier) {
        ShippingStatusChangedMessage message = new ShippingStatusChangedMessage(
                UUID.randomUUID().toString(),
                "ShippingStatusChanged",
                Instant.now(clock).toString(),
                "shipping-service",
                tenantId,
                new ShippingStatusChangedMessage.Payload(
                        shippingId, orderId, userId,
                        previousStatus.name(), newStatus.name(),
                        trackingNumber, carrier,
                        Instant.now(clock).toString()
                )
        );

        outboxRepository.save(new ShippingOutboxEntity(
                UUID.fromString(message.eventId()),
                "ShippingStatusChanged",
                AGGREGATE_TYPE_SHIPPING,
                shippingId,
                null, // partition_key: publisher falls back to aggregateId (shippingId)
                serialize(message),
                Instant.parse(message.occurredAt())));
    }

    @Override
    public void publishFulfillmentRequested(String orderId, String messageJson) {
        // messageJson is the already-serialized wms camelCase envelope (ACL output),
        // stored verbatim. A fresh UUID keys the row (the wms consumer dedupes on the
        // payload eventId, not the Kafka header — wire-safe).
        outboxRepository.save(new ShippingOutboxEntity(
                UUID.randomUUID(),
                "FulfillmentRequested",
                AGGREGATE_TYPE_FULFILLMENT,
                orderId,
                null, // partition_key: publisher falls back to aggregateId (orderId)
                messageJson,
                Instant.now(clock)));
    }

    @Override
    public void publishManualShipConfirmRequested(String tenantId, String orderId,
                                                  String carrier, String trackingNumber) {
        // wms camelCase envelope (ACL convention, same as the forward leg). orderNo ==
        // aggregateId == orderId (D5 correlation key). Stored whole as the outbox payload.
        ManualShipConfirmRequestedMessage message = new ManualShipConfirmRequestedMessage(
                UUID.randomUUID().toString(),
                "ecommerce.shipping.manual-confirm-requested",
                Instant.now(clock).toString(),
                "shipping",
                orderId,
                tenantId,
                new ManualShipConfirmRequestedMessage.Payload(orderId, carrier, trackingNumber)
        );

        outboxRepository.save(new ShippingOutboxEntity(
                UUID.fromString(message.eventId()),
                "ManualShipConfirmRequested",
                AGGREGATE_TYPE_SHIPPING,
                orderId,
                null, // partition_key: publisher falls back to aggregateId (orderId)
                serialize(message),
                Instant.parse(message.occurredAt())));
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
            @JsonProperty("tenant_id") String tenantId,
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
