package com.example.shipping.infrastructure.event;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.tenant.TenantContext;
import com.example.shipping.infrastructure.config.FulfillmentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventConsumer {

    private final ShippingCommandService shippingCommandService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ShippingEventPublisher shippingEventPublisher;
    private final FulfillmentAcl fulfillmentAcl;
    private final FulfillmentProperties fulfillmentProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "order.order.confirmed", groupId = "shipping-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, OrderConfirmedEvent.class));
    }

    void handle(OrderConfirmedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "OrderConfirmed")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("OrderConfirmed event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        if (orderId == null || orderId.isBlank()) {
            log.warn("OrderConfirmed event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null || userId.isBlank()) {
            log.warn("OrderConfirmed event has no userId, skipping. eventId={}", event.eventId());
            return;
        }

        // Bind the order's tenant from the consumed OrderConfirmed envelope (M4). A
        // producer that predates BE-357 (null/blank tenant) resolves to the default tenant
        // (net-zero, D8). The consumer is not an HTTP request, so the tenant is passed
        // explicitly (CreateShippingCommand / FulfillmentAcl) rather than via TenantContext.
        String tenantId = (event.tenantId() == null || event.tenantId().isBlank())
                ? TenantContext.DEFAULT_TENANT_ID
                : event.tenantId();

        shippingCommandService.createShipping(new CreateShippingCommand(tenantId, orderId, userId));

        // ADR-MONO-022 D4 v2(c): the order is wmsRouted iff the forward-leg fulfillment
        // event was actually published. Mark it in the SAME transaction so a later manual
        // ship-confirm can offer the wms-deduct option (one-tx consistency).
        if (publishFulfillmentRequested(tenantId, event.payload())) {
            shippingCommandService.markShippingWmsRouted(orderId);
        }
    }

    /**
     * Forward leg of the storefront→warehouse loop (ADR-MONO-022 §D7): publish
     * the wms-shaped fulfillment-intent event. Gated on {@code fulfillment.enabled}
     * (D8 standalone degradation). An unmapped SKU under {@code require-sku-mapping}
     * blocks publish for this order with an ops alert (no silent drop).
     *
     * @return {@code true} iff the fulfillment event was actually written to the
     *         outbox (i.e. the order is wms-routed). {@code false} when publish is
     *         disabled or blocked by an unmapped SKU.
     */
    private boolean publishFulfillmentRequested(String tenantId, OrderConfirmedEvent.OrderConfirmedPayload order) {
        if (!fulfillmentProperties.enabled()) {
            log.debug("Fulfillment publish disabled (fulfillment.enabled=false), skipping. orderId={}",
                    order.orderId());
            return false;
        }

        try {
            FulfillmentRequestedMessage message = fulfillmentAcl.toFulfillmentRequested(tenantId, order);
            shippingEventPublisher.publishFulfillmentRequested(
                    order.orderId(), objectMapper.writeValueAsString(message));
            log.info("Fulfillment requested published: orderId={}, lines={}",
                    order.orderId(), message.payload().lines().size());
            return true;
        } catch (UnmappedSkuException e) {
            // ALERT: ecommerce SKU with no wms mapping while require-sku-mapping=true.
            log.error("ALERT fulfillment blocked — unmapped SKU. orderId={}, reason={}",
                    order.orderId(), e.getMessage());
            return false;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize fulfillment event for orderId=" + order.orderId(), e);
        }
    }
}
