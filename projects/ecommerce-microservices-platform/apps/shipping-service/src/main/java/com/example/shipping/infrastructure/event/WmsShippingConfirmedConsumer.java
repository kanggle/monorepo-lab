package com.example.shipping.infrastructure.event;

import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.exception.ShippingNotFoundException;
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

/**
 * Return leg of the storefront→warehouse loop (ADR-MONO-022 §D7): consumes the
 * wms {@code outbound.shipping.confirmed} event and flips the local Shipping
 * {@code PREPARING → SHIPPED} (tracking = {@code shipmentNo}, carrier =
 * {@code carrierCode}), keyed by {@code orderNo == orderId}.
 *
 * <p>Dedupe on the wms camelCase {@code eventId}. Missing {@code orderNo} or an
 * unknown order → {@link IllegalArgumentException} (non-retryable → DLT).
 *
 * <p><b>Tenant binding (ADR-MONO-022 facet d, TASK-MONO-296).</b> Binds
 * {@code TenantContext} from the envelope {@code tenantId} (echoed by wms) for the
 * duration of the {@code markShipped} work — with a local-Shipping-row fallback by
 * {@code orderNo} when the envelope field is absent (older wms / standalone) — so
 * the emitted {@code ShippingStatusChanged} resolves the correct tenant rather than
 * the default. Cleared in a {@code finally} (pooled Kafka listener thread — AC-4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsShippingConfirmedConsumer {

    private final ShippingCommandService shippingCommandService;
    private final ShippingRepository shippingRepository;
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

        // Bind the originating tenant: envelope tenantId (facet d), else fall back to
        // the local Shipping row's tenant by orderNo (older wms / standalone — D8).
        TenantContext.set(resolveTenant(event.tenantId(), orderNo));
        try {
            shippingCommandService.markShippedByOrderId(
                    orderNo, event.payload().shipmentNo(), event.payload().carrierCode());
        } catch (ShippingNotFoundException e) {
            // unknown order → non-retryable → DLT (do not guess; correlation failed)
            throw new IllegalArgumentException(
                    "No local Shipping for wms orderNo=" + orderNo + " (eventId=" + event.eventId() + ")", e);
        } finally {
            // Pooled Kafka listener thread — clear so the binding cannot leak into
            // the next message (AC-4 failure scenario).
            TenantContext.clear();
        }
    }

    /**
     * Envelope {@code tenantId} when present; otherwise the tenant on the local
     * Shipping row resolved by {@code orderNo} (tenant-agnostic lookup, the row
     * keeps its own tenant). {@code null} when neither is available — then
     * {@link TenantContext} resolves to the default tenant (net-zero, D8).
     */
    private String resolveTenant(String envelopeTenantId, String orderNo) {
        if (envelopeTenantId != null && !envelopeTenantId.isBlank()) {
            return envelopeTenantId;
        }
        return shippingRepository.findByOrderId(orderNo)
                .map(s -> s.getTenantId())
                .orElse(null);
    }
}
