package com.wms.outbound.application.service;

import com.wms.outbound.application.result.OrderLineResult;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OutboundSaga;
import java.time.Instant;
import java.util.List;

/**
 * Translates the {@link Order} aggregate (and the matching saga) into the
 * application-layer {@link OrderResult} returned by use-case services.
 */
public final class OrderResultMapper {

    private OrderResultMapper() {
    }

    public static OrderResult toResult(Order order, OutboundSaga saga) {
        return toResult(order, saga, null, null, null, null);
    }

    /**
     * Cancel-aware variant: populates the §1.4 cancel response fields
     * ({@code previousStatus}, {@code cancelledReason}, {@code cancelledAt},
     * {@code cancelledBy}) when the caller is {@code CancelOrderService}.
     */
    public static OrderResult toResult(Order order,
                                       OutboundSaga saga,
                                       String previousStatus,
                                       String cancelledReason,
                                       Instant cancelledAt,
                                       String cancelledBy) {
        List<OrderLineResult> lines = order.getLines().stream()
                .map(l -> new OrderLineResult(
                        l.getId(),
                        l.getLineNo(),
                        l.getSkuId(),
                        l.getLotId(),
                        l.getQtyOrdered()))
                .toList();
        return new OrderResult(
                order.getId(),
                order.getOrderNo(),
                order.getSource().name(),
                order.getCustomerPartnerId(),
                order.getWarehouseId(),
                order.getRequiredShipDate(),
                order.getNotes(),
                order.getStatus().name(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getCreatedBy(),
                order.getUpdatedAt(),
                order.getUpdatedBy(),
                lines,
                saga != null ? saga.sagaId() : null,
                saga != null ? saga.status().name() : null,
                previousStatus,
                cancelledReason,
                cancelledAt,
                cancelledBy);
    }
}
