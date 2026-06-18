package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.OrderEntity;
import com.wms.outbound.adapter.out.persistence.entity.OrderLineEntity;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.ShipToAddress;
import java.util.List;

/**
 * Package-private mapper between {@link OrderEntity}/{@link OrderLineEntity}
 * and the domain model. Lives in the same package as
 * {@link OrderPersistenceAdapter} per the Hexagonal rule "mappers are
 * package-private inside persistence adapter" (architecture.md §Layer Rules
 * item 5).
 */
final class OrderMapper {

    private OrderMapper() {
    }

    static Order toDomain(OrderEntity e, List<OrderLineEntity> lineEntities) {
        List<OrderLine> lines = lineEntities.stream()
                .map(OrderMapper::toLineDomain)
                .toList();
        return new Order(
                e.getId(),
                e.getOrderNo(),
                OrderSource.valueOf(e.getSource()),
                e.getCustomerPartnerId(),
                e.getWarehouseId(),
                e.getRequestedShipDate(),
                e.getNotes(),
                toShipTo(e),
                e.getTenantId(),
                OrderStatus.valueOf(e.getStatus()),
                e.getVersion(),
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getUpdatedAt(),
                e.getUpdatedBy(),
                lines);
    }

    /**
     * Reconstructs the {@link ShipToAddress} value object from the entity's
     * three nullable columns. Returns {@code null} when no recipient name is
     * stored (B2B order).
     */
    private static ShipToAddress toShipTo(OrderEntity e) {
        if (e.getShipToName() == null) {
            return null;
        }
        return new ShipToAddress(e.getShipToName(), e.getShipToAddress(), e.getShipToPhone());
    }

    static OrderLine toLineDomain(OrderLineEntity e) {
        return new OrderLine(
                e.getId(),
                e.getOrderId(),
                e.getLineNumber(),
                e.getSkuId(),
                e.getLotId(),
                e.getRequestedQty());
    }
}
