package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OrderJpaEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * Outer-axis tenant owning this order (ADR-MONO-030 Step 2, M1). Stamped once
     * at insert from the request tenant context; never mutated afterward, so a
     * saga/recovery update preserves the order's tenant (task §C). Not part of the
     * clean {@code Order} domain model — it lives in the persistence + event layers.
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Embedded
    private ShippingAddressEmbeddable shippingAddress;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "paid_at", columnDefinition = "TIMESTAMP")
    private Instant paidAt;

    @Column(name = "refunded_at", columnDefinition = "TIMESTAMP")
    private Instant refundedAt;

    @Column(name = "stuck_recovery_attempt_count", nullable = false)
    private int stuckRecoveryAttemptCount;

    @Column(name = "stuck_recovery_at", columnDefinition = "TIMESTAMP")
    private Instant stuckRecoveryAt;

    @Version
    private Long version;

    static OrderJpaEntity fromDomain(Order order, String tenantId) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.orderId = order.getOrderId();
        entity.tenantId = tenantId;
        entity.userId = order.getUserId();
        entity.status = order.getStatus();
        entity.totalPrice = order.getTotalPrice();
        entity.shippingAddress = ShippingAddressEmbeddable.fromDomain(order.getShippingAddress());
        entity.createdAt = order.getCreatedAt();
        entity.updatedAt = order.getUpdatedAt();
        entity.paymentId = order.getPaymentId();
        entity.paidAt = order.getPaidAt();
        entity.refundedAt = order.getRefundedAt();
        entity.stuckRecoveryAttemptCount = order.getStuckRecoveryAttemptCount();
        entity.stuckRecoveryAt = order.getStuckRecoveryAt();
        entity.version = order.getVersion();

        for (OrderItem item : order.getItems()) {
            OrderItemJpaEntity itemEntity = OrderItemJpaEntity.fromDomain(item, entity, tenantId);
            entity.items.add(itemEntity);
        }

        return entity;
    }

    void updateFrom(Order order) {
        this.userId = order.getUserId();
        this.status = order.getStatus();
        this.totalPrice = order.getTotalPrice();
        this.shippingAddress = ShippingAddressEmbeddable.fromDomain(order.getShippingAddress());
        this.updatedAt = order.getUpdatedAt();
        this.paymentId = order.getPaymentId();
        this.paidAt = order.getPaidAt();
        this.refundedAt = order.getRefundedAt();
        this.stuckRecoveryAttemptCount = order.getStuckRecoveryAttemptCount();
        this.stuckRecoveryAt = order.getStuckRecoveryAt();
    }
}
