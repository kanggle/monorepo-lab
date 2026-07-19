package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderCancellationService;
import com.example.order.domain.repository.OrderRepository;
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
 * Backorder/cancel path of the storefront→warehouse loop (ADR-MONO-022 §D4 v2(a),
 * TASK-MONO-197): order-service consumes the wms {@code outbound.order.cancelled} event
 * and auto-cancels + refunds the order.
 *
 * <p>Distinct from the shipping-service consumer of the same topic (which is alert-only):
 * this one uses group {@code order-service-wms} (independent offsets from the
 * {@code order-service} group used for ecommerce-internal topics) and drives the
 * <b>existing</b> {@code order.cancelled} → payment refund + coupon restore fan-out via
 * {@link OrderBackorderCancellationService}. Dedupe on the wms camelCase {@code eventId}.
 *
 * <p><b>Tenant binding (ADR-MONO-022 facet d, TASK-MONO-296).</b> The cancel + the
 * re-emitted {@code order.cancelled} envelope must carry the order's real tenant. The
 * emitted event stamps {@code TenantContext.currentTenant()} (see
 * {@code OrderCancelledEvent.of}), so this consumer binds {@code TenantContext} from the
 * envelope {@code tenantId} (echoed by wms) — with a local-Order-row fallback by
 * {@code orderNo} when absent — before delegating, and clears it in a {@code finally}
 * (pooled Kafka listener thread, AC-4). Without this the emitted refund/coupon-restore
 * fan-out would default to the {@code ecommerce} tenant rather than the order's tenant.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class WmsOutboundCancelledConsumer {

    private final OrderBackorderCancellationService backorderCancellationService;
    private final OrderRepository orderRepository;
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

        // Bind the order's tenant so the auto-cancel + emitted order.cancelled resolve the
        // correct tenant (not the default): envelope tenantId (facet d), else the local Order
        // row's tenant by orderNo (older wms / standalone — D8). Cleared in finally (pooled
        // Kafka listener thread, AC-4). orderNo == ecommerce orderId (ADR-022 §D5 correlation).
        TenantContext.runWithTenant(resolveTenant(event.tenantId(), orderNo),
                () -> backorderCancellationService.cancelForBackorder(orderNo, event.payload().reason()));
    }

    /**
     * Envelope {@code tenantId} when present; otherwise the tenant stored on the
     * local Order row resolved by {@code orderNo} (tenant-agnostic lookup). {@code null}
     * when neither is available → {@link TenantContext} resolves to the default tenant
     * (net-zero, D8).
     */
    private String resolveTenant(String envelopeTenantId, String orderNo) {
        if (envelopeTenantId != null && !envelopeTenantId.isBlank()) {
            return envelopeTenantId;
        }
        return orderRepository.findTenantIdByOrderId(orderNo).orElse(null);
    }
}
