package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.OrderItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OrderItemJpaEntity {

    @Id
    @Column(name = "id")
    private String id;

    /** Owning tenant (ADR-MONO-030 Step 2, M1) — denormalized from the parent order. */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "variant_id", nullable = false)
    private String variantId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_name")
    private String optionName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    static OrderItemJpaEntity fromDomain(OrderItem item, OrderJpaEntity orderEntity, String tenantId) {
        OrderItemJpaEntity entity = new OrderItemJpaEntity();
        entity.id = item.getId();
        entity.tenantId = tenantId;
        entity.order = orderEntity;
        entity.productId = item.getProductId();
        entity.variantId = item.getVariantId();
        entity.productName = item.getProductName();
        entity.optionName = item.getOptionName();
        entity.quantity = item.getQuantity();
        entity.unitPrice = item.getUnitPrice();
        return entity;
    }
}
