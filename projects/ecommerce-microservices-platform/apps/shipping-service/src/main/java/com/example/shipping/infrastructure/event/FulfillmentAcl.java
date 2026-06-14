package com.example.shipping.infrastructure.event;

import com.example.shipping.infrastructure.config.FulfillmentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Anti-Corruption Layer (ADR-MONO-022 §D6): translates an ecommerce
 * {@link OrderConfirmedEvent} into the wms-shaped fulfillment-intent message
 * ({@link FulfillmentRequestedMessage}, camelCase envelope).
 *
 * <p>Vocabulary mapping:
 * <ul>
 *   <li>{@code orderNo = orderId}</li>
 *   <li>{@code customerPartnerCode = ECOMMERCE-STORE} (constant, D2-a)</li>
 *   <li>{@code warehouseCode} from config (D3)</li>
 *   <li>per-line {@code skuCode} via the config SKU map (identity default)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentAcl {

    static final String CUSTOMER_PARTNER_CODE = "ECOMMERCE-STORE";
    static final String EVENT_TYPE = "ecommerce.fulfillment.requested";
    static final String AGGREGATE_TYPE = "fulfillment";

    private final FulfillmentProperties properties;
    private final Clock clock;

    /**
     * Builds the fulfillment message for the given confirmed order. {@code tenantId}
     * (bound by the consumer from the OrderConfirmed envelope, default {@code ecommerce})
     * is stamped on the envelope top-level (M5) so the wms outbound consumer binds the
     * originating tenant.
     *
     * @throws UnmappedSkuException when {@code require-sku-mapping=true} and a line
     *                              SKU is absent from the SKU map.
     */
    public FulfillmentRequestedMessage toFulfillmentRequested(String tenantId,
                                                              OrderConfirmedEvent.OrderConfirmedPayload order) {
        List<FulfillmentRequestedMessage.Line> lines = mapLines(order);

        return new FulfillmentRequestedMessage(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                Instant.now(clock).toString(),
                AGGREGATE_TYPE,
                order.orderId(),
                tenantId,
                new FulfillmentRequestedMessage.Payload(
                        order.orderId(),
                        CUSTOMER_PARTNER_CODE,
                        properties.defaultWarehouseCode(),
                        null,
                        mapShipTo(order.shippingAddress()),
                        lines
                )
        );
    }

    private List<FulfillmentRequestedMessage.Line> mapLines(OrderConfirmedEvent.OrderConfirmedPayload order) {
        List<OrderConfirmedEvent.Line> source = order.lines() == null ? List.of() : order.lines();
        List<FulfillmentRequestedMessage.Line> result = new ArrayList<>(source.size());
        int lineNo = 1;
        for (OrderConfirmedEvent.Line line : source) {
            result.add(new FulfillmentRequestedMessage.Line(
                    lineNo++, resolveSkuCode(order.orderId(), line.sku()), null, line.quantity()));
        }
        return result;
    }

    private String resolveSkuCode(String orderId, String ecommerceSku) {
        String mapped = properties.skuMap().get(ecommerceSku);
        if (mapped != null) {
            return mapped;
        }
        if (properties.requireSkuMapping()) {
            throw new UnmappedSkuException(
                    "No wms SKU mapping for ecommerce SKU '" + ecommerceSku + "' (orderId=" + orderId + ")");
        }
        // identity passthrough (default behaviour)
        return ecommerceSku;
    }

    private FulfillmentRequestedMessage.ShipTo mapShipTo(OrderConfirmedEvent.ShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new FulfillmentRequestedMessage.ShipTo(
                address.recipientName(), address.address(), address.phone());
    }
}
