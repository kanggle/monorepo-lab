package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderCancellationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backorder/cancel path of the storefront→warehouse loop (ADR-MONO-022 §D4 v2(a),
 * TASK-MONO-197): order-service consumes the wms {@code outbound.order.cancelled} event
 * and auto-cancels + refunds the order.
 *
 * <p>Distinct from the shipping-service consumer of the same topic (which is alert-only):
 * this one uses group {@code order-service-wms} (independent offsets from the
 * {@code order-service} group used for ecommerce-internal topics) and drives the
 * <b>existing</b> {@code order.cancelled} → payment refund + coupon restore fan-out via
 * {@link OrderBackorderCancellationService}. Dedupe on the wms camelCase {@code eventId}.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class WmsOutboundCancelledConsumer {

    private final OrderBackorderCancellationService backorderCancellationService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.outbound.order.cancelled.v1", groupId = "order-service-wms")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, WmsOutboundCancelledEvent.class));
    }

    void handle(WmsOutboundCancelledEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("wms outbound.cancelled event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderNo = event.payload().orderNo();
        if (EventFieldParser.isBlank(orderNo)) {
            log.warn("wms outbound.cancelled event has no orderNo, skipping. eventId={}", event.eventId());
            return;
        }

        // orderNo == ecommerce orderId (ADR-022 §D5 correlation).
        backorderCancellationService.cancelForBackorder(orderNo, event.payload().reason());
    }
}
