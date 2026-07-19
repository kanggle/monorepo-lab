package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderService;
import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the product-service {@code OrderReservationFailed} event
 * ({@code product.product.reservation-failed}, TASK-BE-428): the payment-driven
 * reservation saga could not all-or-nothing reserve stock for a paid order, so no stock
 * was decremented and the order must be held for backorder. Transitions the order
 * {@code PENDING → BACKORDERED} via {@link OrderBackorderService}.
 *
 * <p>Mirrors {@link StockChangedEventConsumer}: group {@code order-service}, dedupe on the
 * envelope {@code event_id}, and binds the order's tenant from the envelope
 * {@code tenant_id} (cleared in {@code finally} so the pooled listener thread leaks no
 * context). A null payload / blank orderId is a warn-and-skip (never fails the pipeline).
 * The {@code BACKORDERED} transition is idempotent + late-event safe in the domain, so
 * Kafka at-least-once re-delivery and an already-advanced order are both no-ops.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class OrderReservationFailedConsumer {

    private final OrderBackorderService orderBackorderService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "product.product.reservation-failed", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        OrderReservationFailedEvent event =
                objectMapper.readValue(payload, OrderReservationFailedEvent.class);
        // Bind the order's tenant from the consumed envelope (M5) so the backorder transition
        // stays within the tenant boundary; a pre-multi-tenant / standalone envelope resolves
        // to the default tenant (net-zero, D8); cleared in finally so the pooled listener
        // thread leaks no context to the next message.
        TenantContext.runWithTenant(event.tenantId(), () -> handle(event));
    }

    void handle(OrderReservationFailedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "OrderReservationFailed")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("OrderReservationFailed event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderReservationFailed event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        orderBackorderService.markBackordered(orderId);
    }
}
