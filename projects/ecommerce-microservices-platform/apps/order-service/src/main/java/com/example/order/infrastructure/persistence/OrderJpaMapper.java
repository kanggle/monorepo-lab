package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.ShippingAddress;
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
                entity.getVersion()
        );
    }

    OrderJpaEntity toEntity(Order order) {
        return OrderJpaEntity.fromDomain(order);
    }

    private OrderItem toOrderItem(OrderItemJpaEntity entity) {
        return OrderItem.reconstitute(
                entity.getId(),
                entity.getProductId(),
                entity.getVariantId(),
                entity.getProductName(),
                entity.getOptionName(),
                entity.getQuantity(),
                entity.getUnitPrice()
        );
    }
}
