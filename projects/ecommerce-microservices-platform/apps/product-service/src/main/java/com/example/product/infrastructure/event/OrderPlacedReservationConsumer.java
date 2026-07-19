package com.example.product.infrastructure.event;

import com.example.product.application.service.ReservationService;
import com.example.product.domain.model.reservation.StockReservationLine;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.event.ReservationInboundEvents.OrderPlacedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consumes {@code order.order.placed} → records the order's line snapshot on its reservation
 * (TASK-BE-428). If the {@code PaymentCompleted} signal has already arrived (payment-first), the
 * reservation reserves immediately; otherwise it waits for payment. Dedupe on the envelope
 * {@code event_id}; tenant bound from the envelope {@code tenant_id} so the tenant-scoped
 * inventory/reservation reads resolve correctly across the async boundary.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class OrderPlacedReservationConsumer {

    private final ReservationService reservationService;
    private final ReservationEventDedupe dedupe;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.placed", groupId = "product-service-reservation")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderPlacedMessage.class));
    }

    void handle(OrderPlacedMessage event) {
        if (dedupe.isDuplicate(ReservationUuids.parseOrNull(event.eventId()), "order.order.placed")) {
            return;
        }
        if (event.payload() == null || event.payload().orderId() == null
                || event.payload().items() == null || event.payload().items().isEmpty()) {
            log.warn("order.order.placed has null/empty payload, skipping. eventId={}", event.eventId());
            return;
        }
        List<StockReservationLine> lines = toLines(event.payload().items());
        if (lines.isEmpty()) {
            log.warn("order.order.placed had no reservable lines, skipping. orderId={}",
                    event.payload().orderId());
            return;
        }
        String tenantId = event.tenantId();
        TenantContext.runWithTenant(tenantId,
                () -> reservationService.recordOrderPlaced(event.payload().orderId(), tenantId, lines));
    }

    private List<StockReservationLine> toLines(List<OrderPlacedMessage.Item> items) {
        List<StockReservationLine> lines = new ArrayList<>();
        for (OrderPlacedMessage.Item item : items) {
            UUID variantId = ReservationUuids.parseOrNull(item.variantId());
            UUID productId = ReservationUuids.parseOrNull(item.productId());
            if (variantId == null || productId == null || item.quantity() <= 0) {
                // A line without a variant cannot be reserved against variant-level inventory.
                log.warn("Skipping unreservable order line (missing variantId/productId or qty<=0). "
                        + "productId={}, variantId={}, qty={}", item.productId(), item.variantId(), item.quantity());
                continue;
            }
            lines.add(new StockReservationLine(variantId, productId, item.quantity()));
        }
        return lines;
    }
}
