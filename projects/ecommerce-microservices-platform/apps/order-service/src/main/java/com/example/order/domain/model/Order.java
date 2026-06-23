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
        // Confirm is reachable from PENDING (the normal reservation saga) or from
        // BACKORDERED (a later replenishment re-reserved the held order — TASK-BE-428).
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.BACKORDERED) {
            throw new InvalidOrderException("Order can only be confirmed in PENDING or BACKORDERED status: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    /**
     * Hold the order for backorder (payment-driven reservation saga, TASK-BE-428): after
     * payment, product-service could not all-or-nothing reserve stock for at least one
     * line, so the order is held — no stock decremented — until a later replenishment
     * re-reserves it ({@code BACKORDERED → CONFIRMED} via {@link #confirm(Clock)}).
     *
     * <p>Idempotent + late-event safe: returns {@code false} (no mutation) when already
     * {@code BACKORDERED} (re-delivery) or when the order has already advanced
     * ({@code CONFIRMED}/{@code SHIPPED}/{@code DELIVERED}/{@code CANCELLED}/
     * {@code STUCK_RECOVERY_FAILED}) — a late {@code OrderReservationFailed} for an
     * already-advanced order is a no-op the caller logs, never a thrown error. Only a
     * {@code PENDING} order actually transitions.
     *
     * @param clock domain clock for the {@code updatedAt} stamp
     * @return {@code true} if the order transitioned {@code PENDING → BACKORDERED};
     *         {@code false} on a no-op
     */
    public boolean markBackordered(Clock clock) {
        if (this.status != OrderStatus.PENDING) {
            return false;
        }
        this.status = OrderStatus.BACKORDERED;
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

    /**
     * Anonymize the order-held PII (the shipping-address snapshot) in reaction to an
     * IAM {@code account.deleted(anonymized=true)} (ADR-MONO-037 P3-B — the standing
     * TASK-BE-258 GDPR consumer obligation for the order store). Only the address
     * snapshot is tombstoned; {@code orderId} / {@code userId} (FK), amounts, line
     * items, status, and payment/refund timestamps are all preserved for audit /
     * finance / settlement integrity.
     *
     * <p>Idempotent: returns {@code false} (no mutation, no version bump) when the
     * address is absent or already anonymized, so Kafka at-least-once re-delivery and
     * the two-phase {@code account.deleted} emission are safe without extra guards.
     *
     * @param clock domain clock for the {@code updatedAt} stamp
     * @return {@code true} if PII was actually masked; {@code false} on a no-op
     */
    public boolean anonymizePii(Clock clock) {
        if (this.shippingAddress == null || this.shippingAddress.isAnonymized()) {
            return false;
        }
        this.shippingAddress = this.shippingAddress.anonymized();
        this.updatedAt = Instant.now(clock);
        return true;
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
