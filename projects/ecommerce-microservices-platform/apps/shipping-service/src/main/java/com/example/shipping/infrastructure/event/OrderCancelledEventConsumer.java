package com.example.shipping.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.application.command.CancelShippingCommand;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Ecommerce-initiated cancel of the storefront→warehouse loop (ADR-MONO-022 D4 v2(d), X-A /
 * Y-A, TASK-BE-649): consumes {@code order.order.cancelled} and cancels a Shipping that has not
 * left yet. Until this consumer, an order cancelled after {@code CONFIRMED} was refunded while
 * its Shipping stayed {@code PREPARING} and wms kept picking.
 *
 * <p>The per-status decision lives in
 * {@link ShippingCommandService#cancelByOrderId(CancelShippingCommand)}: {@code PREPARING} →
 * {@code CANCELLED} (+ {@code ecommerce.fulfillment.cancel-requested.v1} in the same
 * transaction when {@code wmsRouted}); {@code SHIPPED}+, already-{@code CANCELLED} and unknown
 * orders are no-ops. This class only parses, dedupes and binds the tenant.
 *
 * <p><b>Conventions copied from the siblings.</b> Group {@code shipping-service} and the
 * snake_case envelope, as {@link OrderConfirmedEventConsumer} (the other order-service topic);
 * dedupe on {@code event_id} through {@link EventDedupePort}; a null payload or blank
 * {@code orderId} is skipped with a WARN (no DLQ), as {@code OrderConfirmed} is. JSON / UUID
 * parse failures are non-retryable and go to {@code order.order.cancelled.DLT} through the
 * shared {@code KafkaConsumerConfig} error handler.
 *
 * <p><b>Tenant binding.</b> As {@link WmsShippingConfirmedConsumer}: the envelope
 * {@code tenant_id} is bound into {@link TenantContext} for the work, falling back to the local
 * Shipping row's tenant by {@code orderId}, and cleared in a {@code finally} (pooled Kafka
 * listener thread). The outbox envelope itself carries the row's own tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventConsumer {

    private final ShippingCommandService shippingCommandService;
    private final ShippingRepository shippingRepository;
    private final EventDedupePort eventDedupePort;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.cancelled", groupId = "shipping-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderCancelledEvent.class));
    }

    void handle(OrderCancelledEvent event) {
        eventDedupePort.process(
                EventIds.parseOrNull(event.eventId()), "OrderCancelled", () -> handleWork(event));
    }

    private void handleWork(OrderCancelledEvent event) {
        if (event.payload() == null) {
            log.warn("OrderCancelled event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderCancelled event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        TenantContext.set(resolveTenant(event.tenantId(), orderId));
        try {
            shippingCommandService.cancelByOrderId(
                    new CancelShippingCommand(orderId, parseInstantOrNull(event.payload().cancelledAt())));
        } finally {
            // Pooled Kafka listener thread — clear so the binding cannot leak into the next message.
            TenantContext.clear();
        }
    }

    /**
     * Envelope {@code tenant_id} when present; otherwise the tenant on the local Shipping row
     * (tenant-agnostic lookup by the globally-unique {@code orderId}). {@code null} when neither
     * exists — {@link TenantContext} then resolves to the default tenant (net-zero, D8).
     */
    private String resolveTenant(String envelopeTenantId, String orderId) {
        if (envelopeTenantId != null && !envelopeTenantId.isBlank()) {
            return envelopeTenantId;
        }
        return shippingRepository.findByOrderId(orderId)
                .map(s -> s.getTenantId())
                .orElse(null);
    }

    /**
     * {@code payload.cancelledAt} is informational on the wms side ({@code requestedAt}), so an
     * absent or malformed value must not block the cancel: it becomes {@code null} and the
     * service uses its own clock.
     */
    private static Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("OrderCancelled cancelledAt is not an ISO-8601 instant ({}), using service clock", value);
            return null;
        }
    }
}
