package com.example.shipping.infrastructure.event;

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
 * Backorder/cancel path of the storefront→warehouse loop (ADR-MONO-022 §D4):
 * consumes the wms {@code outbound.order.cancelled} event and raises an ops alert.
 *
 * <p>This consumer stays <b>alert-only</b> and intentionally leaves the Shipping in
 * {@code PREPARING} (flagged via the alert log): at backorder time no Shipping row
 * typically exists yet, and {@code ShippingStatus} has no terminal CANCELLED state.
 *
 * <p>As of ADR-022 §D4 <b>v2(a)</b> (TASK-MONO-197) the order cancel + refund is owned
 * by <b>order-service</b>'s own consumer of this topic (group {@code order-service-wms}),
 * which transitions the Order to CANCELLED and triggers the existing refund saga. This
 * shipping-side handler remains the ops-signal half and is unchanged. Dedupe on the wms
 * camelCase {@code eventId}.
 *
 * <p><b>Tenant binding (ADR-MONO-022 facet d, TASK-MONO-296).</b> Binds
 * {@code TenantContext} from the envelope {@code tenantId} (echoed by wms) before the
 * tenant-scoped read/log — with a local-Shipping-row fallback by {@code orderNo} when
 * the envelope field is absent — and clears it in a {@code finally} (pooled Kafka
 * listener thread, AC-4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsOutboundCancelledConsumer {

    private final ShippingRepository shippingRepository;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.outbound.order.cancelled.v1", groupId = "shipping-service-wms")
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

        TenantContext.set(resolveTenant(event.tenantId(), event.payload().orderNo()));
        try {
            // ALERT: warehouse could not fulfill — backorder/cancel. v1 leaves Shipping in PREPARING.
            log.error("ALERT wms outbound cancelled — backorder/manual intervention needed. "
                            + "tenantId={}, orderNo={}, previousStatus={}, reason={}",
                    TenantContext.currentTenant(), event.payload().orderNo(),
                    event.payload().previousStatus(), event.payload().reason());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Envelope {@code tenantId} when present; otherwise the local Shipping row's
     * tenant by {@code orderNo} (a row may not exist yet at backorder time → then
     * {@code null} → default tenant, net-zero D8).
     */
    private String resolveTenant(String envelopeTenantId, String orderNo) {
        if (envelopeTenantId != null && !envelopeTenantId.isBlank()) {
            return envelopeTenantId;
        }
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return shippingRepository.findByOrderId(orderNo)
                .map(s -> s.getTenantId())
                .orElse(null);
    }
}
