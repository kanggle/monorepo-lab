package com.example.shipping.infrastructure.event;

import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Return leg of the storefront→warehouse loop (ADR-MONO-022 §D7): consumes the
 * wms {@code outbound.shipping.confirmed} event and flips the local Shipping
 * {@code PREPARING → SHIPPED} (tracking = {@code shipmentNo}, carrier =
 * {@code carrierCode}), keyed by {@code orderNo == orderId}.
 *
 * <p>Dedupe on the wms camelCase {@code eventId}. Missing {@code orderNo} or an
 * unknown order → {@link IllegalArgumentException} (non-retryable → DLT).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsShippingConfirmedConsumer {

    private final ShippingCommandService shippingCommandService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.outbound.shipping.confirmed.v1", groupId = "shipping-service-wms")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, WmsShippingConfirmedEvent.class));
    }

    void handle(WmsShippingConfirmedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsShippingConfirmed")) {
            return;
        }

        if (event.payload() == null) {
            throw new IllegalArgumentException(
                    "wms shipping.confirmed event has null payload. eventId=" + event.eventId());
        }

        String orderNo = event.payload().orderNo();
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException(
                    "wms shipping.confirmed event has no orderNo (pre-D5 producer?). eventId=" + event.eventId());
        }

        try {
            shippingCommandService.markShippedByOrderId(
                    orderNo, event.payload().shipmentNo(), event.payload().carrierCode());
        } catch (ShippingNotFoundException e) {
            // unknown order → non-retryable → DLT (do not guess; correlation failed)
            throw new IllegalArgumentException(
                    "No local Shipping for wms orderNo=" + orderNo + " (eventId=" + event.eventId() + ")", e);
        }
    }
}
