package com.example.order.domain.model;

import com.example.order.domain.exception.OrderCannotBeCancelledException;
import com.example.order.domain.exception.InvalidOrderException;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
public class Order {

    private String orderId;
    private String userId;
    private List<OrderItem> items = new ArrayList<>();
    private OrderStatus status;
    private long totalPrice;
    private ShippingAddress shippingAddress;
    private Instant createdAt;
    private Instant updatedAt;
    private String paymentId;
    private Instant paidAt;
    private Instant refundedAt;
    private int stuckRecoveryAttemptCount;
    private Instant stuckRecoveryAt;
    private Long version;

    private Order() {
    }

    public static Order create(String userId, List<OrderItemData> itemDataList,
                                ShippingAddress shippingAddress, Clock clock) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidOrderException("User ID must not be null or blank");
        }
        if (itemDataList == null || itemDataList.isEmpty()) {
            throw new InvalidOrderException("Order items must not be empty");
        }
        if (shippingAddress == null) {
            throw new InvalidOrderException("Shipping address must not be null");
        }

        Order order = new Order();
        order.orderId = UUID.randomUUID().toString();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.shippingAddress = shippingAddress;
        Instant now = Instant.now(clock);
        order.createdAt = now;
        order.updatedAt = now;

        long total = 0L;
        for (OrderItemData data : itemDataList) {
            OrderItem item = new OrderItem(
                    UUID.randomUUID().toString(),
                    data.productId(), data.variantId(),
                    data.productName(), data.optionName(),
                    data.quantity(), data.unitPrice(),
                    data.sellerId()
            );
            order.items.add(item);
            total += item.subtotal();
        }
        order.totalPrice = total;

        return order;
    }

    public static Order reconstitute(String orderId, String userId, List<OrderItem> items,
                                      OrderStatus status, long totalPrice,
                                      ShippingAddress shippingAddress,
                                      Instant createdAt, Instant updatedAt,
                                      String paymentId, Instant paidAt,
                                      Instant refundedAt,
                                      int stuckRecoveryAttemptCount,
                                      Instant stuckRecoveryAt,
                                      Long version) {
        Order order = new Order();
        order.orderId = orderId;
        order.userId = userId;
        order.items = new ArrayList<>(items);
        order.status = status;
        order.totalPrice = totalPrice;
        order.shippingAddress = shippingAddress;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        order.paymentId = paymentId;
        order.paidAt = paidAt;
        order.refundedAt = refundedAt;
        order.stuckRecoveryAttemptCount = stuckRecoveryAttemptCount;
        order.stuckRecoveryAt = stuckRecoveryAt;
        order.version = version;
        return order;
    }

    public boolean confirm(Clock clock) {
        if (this.status == OrderStatus.CONFIRMED) {
            return false;
        }
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderException("Order can only be confirmed in PENDING status: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    public boolean ship(Clock clock) {
        if (this.status == OrderStatus.SHIPPED) {
            return false;
        }
        if (this.status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderException("Order can only be shipped in CONFIRMED status: " + status);
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    public boolean deliver(Clock clock) {
        if (this.status == OrderStatus.DELIVERED) {
            return false;
        }
        if (this.status != OrderStatus.SHIPPED) {
            throw new InvalidOrderException("Order can only be delivered in SHIPPED status: " + status);
        }
        this.status = OrderStatus.DELIVERED;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    public void cancel(Clock clock) {
        if (!status.isCancellable()) {
            throw new OrderCannotBeCancelledException(
                    "Order cannot be cancelled in current status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now(clock);
    }

    public void markPaymentCompleted(String paymentId, Instant paidAt, Clock clock) {
        if (this.paymentId != null) {
            return; // 멱등: 이미 결제 완료 처리됨
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Cannot apply payment to cancelled order: " + orderId);
        }
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        this.updatedAt = Instant.now(clock);
    }

    public boolean isPaymentCompleted() {
        return this.paymentId != null;
    }

    public void markRefunded(Instant refundedAt, Clock clock) {
        if (this.refundedAt != null) {
            return; // 멱등: 이미 환불 처리됨
        }
        if (this.status != OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Refund is only allowed in CANCELLED status: " + status);
        }
        this.refundedAt = refundedAt;
        this.updatedAt = Instant.now(clock);
    }

    public boolean isRefunded() {
        return this.refundedAt != null;
    }

    public void recordStuckRecoveryAttempt(Instant now) {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderException(
                    "Stuck recovery attempt only allowed in PENDING status: " + status);
        }
        this.stuckRecoveryAttemptCount += 1;
        this.stuckRecoveryAt = now;
        this.updatedAt = now;
    }

    public void markStuckRecoveryFailed(Instant now) {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderException(
                    "Stuck recovery terminal transition only allowed in PENDING status: " + status);
        }
        this.status = OrderStatus.STUCK_RECOVERY_FAILED;
        this.stuckRecoveryAt = now;
        this.updatedAt = now;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public record OrderItemData(
            String productId,
            String variantId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice,
            String sellerId
    ) {
        /** Backward-compatible (no seller) — line is attributed to the default seller (D8). */
        public OrderItemData(String productId, String variantId, String productName,
                             String optionName, int quantity, long unitPrice) {
            this(productId, variantId, productName, optionName, quantity, unitPrice,
                    OrderItem.DEFAULT_SELLER_ID);
        }
    }
}
