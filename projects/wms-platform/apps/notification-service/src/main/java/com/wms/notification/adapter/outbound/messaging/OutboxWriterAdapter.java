package com.wms.notification.adapter.outbound.messaging;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaEntity;
import com.wms.notification.adapter.outbound.persistence.jpa.outbox.NotificationOutboxJpaRepository;
import com.wms.notification.application.port.out.OutboxPort;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one row to {@code notification_outbox} per delivery lifecycle
 * event. Caller's transaction (T3 — atomic with the delivery row).
 *
 * <h2>Event types</h2>
 *
 * <ul>
 *   <li>{@code notification.delivery.scheduled} — published when a new
 *       PENDING delivery is created.</li>
 *   <li>{@code notification.delivered} — published when a delivery reaches
 *       a terminal state ({@code SUCCEEDED} or {@code FAILED}). Schema
 *       cross-linked from {@code specs/contracts/events/notification-events.md}.</li>
 * </ul>
 */
@Component
public class OutboxWriterAdapter implements OutboxPort {

    private static final String AGGREGATE_TYPE = "notification.delivery";
    // TASK-BE-144 (refactor-spec audit): wire envelope `eventVersion` is an int
    // (`1`), matching the 5 sibling WMS event contracts (master/inventory/inbound/
    // outbound/admin). DB column `notification_outbox.event_version` is still
    // VARCHAR(10); we store the string repr ("1") to avoid a schema migration —
    // the column is local-only and not queried/filtered on its value.
    private static final int EVENT_VERSION = 1;
    private static final String EVENT_VERSION_DB = String.valueOf(EVENT_VERSION);
    private static final String EVENT_DELIVERY_SCHEDULED = "notification.delivery.scheduled";
    private static final String EVENT_DELIVERED = "notification.delivered";

    private final NotificationOutboxJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriterAdapter(NotificationOutboxJpaRepository repository,
                         ObjectMapper objectMapper,
                         Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeDeliveryScheduled(NotificationDelivery delivery) {
        appendRow(delivery, EVENT_DELIVERY_SCHEDULED, deliveryEnvelope(delivery, "PENDING", null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeDeliveryCompleted(NotificationDelivery delivery, String outcomeCode) {
        appendRow(delivery, EVENT_DELIVERED,
                deliveryEnvelope(delivery, delivery.status().name(), outcomeCode));
    }

    private void appendRow(NotificationDelivery delivery, String eventType, Map<String, Object> envelope) {
        try {
            String payloadJson = objectMapper.writeValueAsString(envelope);
            NotificationOutboxJpaEntity row = new NotificationOutboxJpaEntity(
                    UuidV7.randomUuid(),
                    AGGREGATE_TYPE,
                    delivery.id().toString(),
                    eventType,
                    EVENT_VERSION_DB,
                    payloadJson,
                    delivery.eventId().toString(),
                    clock.instant());
            repository.save(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload for delivery " + delivery.id(), e);
        }
    }

    private Map<String, Object> deliveryEnvelope(NotificationDelivery delivery,
                                                 String status,
                                                 String outcomeCode) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UuidV7.randomString());
        envelope.put("eventType", "notification.delivered");
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("aggregateId", delivery.id().toString());
        envelope.put("occurredAt", clock.instant().toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryId", delivery.id().toString());
        payload.put("sourceEventId", delivery.eventId().toString());
        payload.put("sourceTopic", delivery.sourceTopic());
        payload.put("channelId", delivery.channelId());
        payload.put("status", status);
        payload.put("attemptCount", delivery.attemptCount());
        if (outcomeCode != null) {
            payload.put("outcome", outcomeCode);
        }
        delivery.lastError().ifPresent(err -> payload.put("lastError", err));
        envelope.put("payload", payload);
        return envelope;
    }
}
