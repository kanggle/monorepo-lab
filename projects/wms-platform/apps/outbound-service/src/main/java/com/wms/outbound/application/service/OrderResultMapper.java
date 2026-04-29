package com.wms.outbound.application.service;

import com.wms.outbound.application.result.OrderLineResult;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.List;

/**
 * Translates the {@link Order} aggregate (and the matching saga) into the
 * application-layer {@link OrderResult} returned by use-case services.
 */
public final class OrderResultMapper {

    private OrderResultMapper() {
    }

    public static OrderResult toResult(Order order, OutboundSaga saga) {
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
                saga != null ? saga.status().name() : null);
    }
}
