package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class OrderJpaMapper {

    Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toOrderItem)
                .toList();

        ShippingAddress shippingAddress = entity.getShippingAddress() != null
                ? entity.getShippingAddress().toDomain()
                : null;

        return Order.reconstitute(
                entity.getOrderId(),
                entity.getUserId(),
                items,
                entity.getStatus(),
                entity.getTotalPrice(),
                shippingAddress,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getPaymentId(),
                entity.getPaidAt(),
                entity.getRefundedAt(),
                entity.getStuckRecoveryAttemptCount(),
                entity.getStuckRecoveryAt(),
                entity.getVersion()
        );
    }

    OrderJpaEntity toEntity(Order order) {
        // Stamp the owning tenant from the request context (M2 layer 3 — write).
        // Background/standalone paths resolve to the default tenant (net-zero, D8).
        return OrderJpaEntity.fromDomain(order, TenantContext.currentTenant());
    }

    private OrderItem toOrderItem(OrderItemJpaEntity entity) {
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getProductId(),
                entity.getVariantId(),
                entity.getProductName(),
                entity.getOptionName(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getSellerId()
        );
    }
}
