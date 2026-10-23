package com.example.shipping.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
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

import java.util.Optional;

/**
 * Backorder/cancel path of the storefront→warehouse loop (ADR-MONO-022 §D4):
 * consumes the wms {@code outbound.order.cancelled} event and raises an ops alert.
 *
 * <p>This consumer stays <b>alert-only</b>: it never mutates the Shipping (at backorder time
 * no Shipping row typically exists yet; the order cancel is order-service's, below).
 *
 * <p><b>Echo suppression (ADR-MONO-022 D4 v2(d), TASK-BE-649).</b> When ecommerce cancels an
 * order, {@link OrderCancelledEventConsumer} moves the Shipping {@code PREPARING → CANCELLED}
 * and asks wms to cancel; a wms auto-cancel then emits this very event back. If the local
 * Shipping row is already {@code CANCELLED}, the event is that echo (or a wms admin finishing
 * the cancel we asked for after put-back), not a warehouse problem, so the {@code ALERT} is
 * replaced by an INFO line. Every other case — no row, {@code PREPARING} (backorder),
 * {@code SHIPPED}+ — still raises the {@code ALERT}. The decision reads the Shipping status, not
 * the echoed {@code reason}.
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
    private final EventDedupePort eventDedupePort;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.outbound.order.cancelled.v1", groupId = "shipping-service-wms")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, WmsOutboundCancelledEvent.class));
    }

    void handle(WmsOutboundCancelledEvent event) {
        eventDedupePort.process(
                EventIds.parseOrNull(event.eventId()), "WmsOutboundCancelled", () -> handleWork(event));
    }

    private void handleWork(WmsOutboundCancelledEvent event) {
        if (event.payload() == null) {
            log.warn("wms outbound.cancelled event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderNo = event.payload().orderNo();
        Optional<Shipping> row = (orderNo == null || orderNo.isBlank())
                ? Optional.empty()
                : shippingRepository.findByOrderId(orderNo);

        TenantContext.set(resolveTenant(event.tenantId(), row));
        try {
            if (row.isPresent() && row.get().getStatus() == ShippingStatus.CANCELLED) {
                // Echo of the cancel shipping-service requested (D4 v2(d)) — not a warehouse alert.
                log.info("wms outbound.cancelled for an already-CANCELLED shipping — echo of our own "
                                + "cancel request, ALERT suppressed. tenantId={}, orderNo={}, previousStatus={}, reason={}",
                        TenantContext.currentTenant(), orderNo,
                        event.payload().previousStatus(), event.payload().reason());
                return;
            }
            // ALERT: warehouse could not fulfill — backorder/cancel. The Shipping is not mutated.
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
     * tenant (looked up by {@code orderNo}; a row may not exist yet at backorder time → then
     * {@code null} → default tenant, net-zero D8).
     */
    private static String resolveTenant(String envelopeTenantId, Optional<Shipping> row) {
        if (envelopeTenantId != null && !envelopeTenantId.isBlank()) {
            return envelopeTenantId;
        }
        return row.map(Shipping::getTenantId).orElse(null);
    }
}
