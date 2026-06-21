package com.example.payment.domain.model;

import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.tenant.TenantContext;

import java.time.LocalDateTime;
import java.util.UUID;

public class Payment {

    private String paymentId;
    private String orderId;
    private String userId;
    /** Outer-axis tenant (ADR-MONO-030 Step 4 facet c — TASK-BE-400, M1). */
    private String tenantId;
    private long amount;
    /** Cumulative refunded minor units (0 ≤ refundedAmount ≤ amount). Partial refunds accumulate. */
    private long refundedAmount;
    private PaymentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String paymentKey;
    private String paymentMethod;
    private String receiptUrl;

    private Payment() {
    }

    public static Payment create(String orderId, String userId, long amount) {
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID().toString();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.tenantId = TenantContext.currentTenant();
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    public static Payment reconstitute(String paymentId, String orderId, String userId,
                                        String tenantId,
                                        long amount, long refundedAmount, PaymentStatus status,
                                        LocalDateTime createdAt, LocalDateTime paidAt,
                                        LocalDateTime refundedAt,
                                        String paymentKey, String paymentMethod,
                                        String receiptUrl) {
        Payment payment = new Payment();
        payment.paymentId = paymentId;
        payment.orderId = orderId;
        payment.userId = userId;
        payment.tenantId = tenantId;
        payment.amount = amount;
        payment.refundedAmount = refundedAmount;
        payment.status = status;
        payment.createdAt = createdAt;
        payment.paidAt = paidAt;
        payment.refundedAt = refundedAt;
        payment.paymentKey = paymentKey;
        payment.paymentMethod = paymentMethod;
        payment.receiptUrl = receiptUrl;
        return payment;
    }

    public void confirm(String paymentKey, String paymentMethod, String receiptUrl) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentException("PENDING 상태에서만 결제를 승인할 수 있습니다: " + status);
        }
        this.paymentKey = paymentKey;
        this.paymentMethod = paymentMethod;
        this.receiptUrl = receiptUrl;
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public void fail() {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentException("PENDING 상태에서만 FAILED로 전이할 수 있습니다: " + status);
        }
        this.status = PaymentStatus.FAILED;
    }

    /**
     * Full refund of the remaining refundable amount (the OrderCancelled path).
     * Idempotent: a no-op once already fully {@code REFUNDED}. Refunding the remainder
     * of a {@code PARTIALLY_REFUNDED} payment closes it out to {@code REFUNDED}.
     */
    public void refund() {
        if (this.status == PaymentStatus.REFUNDED) {
            return;
        }
        refund(getRemainingRefundable());
    }

    /**
     * Partial (or full) refund of {@code amount} minor units. Allowed from
     * {@code COMPLETED} or {@code PARTIALLY_REFUNDED}; {@code amount} must be
     * {@code 0 < amount ≤ remaining refundable}. Accumulates {@code refundedAmount};
     * transitions to {@code REFUNDED} when the cumulative reaches the captured total,
     * else {@code PARTIALLY_REFUNDED}.
     */
    public void refund(long amount) {
        if (this.status != PaymentStatus.COMPLETED && this.status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new InvalidPaymentException(
                    "COMPLETED 또는 PARTIALLY_REFUNDED 상태에서만 환불할 수 있습니다: " + status);
        }
        long remaining = getRemainingRefundable();
        if (amount <= 0 || amount > remaining) {
            throw new InvalidPaymentException(
                    "유효하지 않은 환불 금액입니다: " + amount + " (잔여 환불 가능액=" + remaining + ")");
        }
        this.refundedAmount += amount;
        this.refundedAt = LocalDateTime.now();
        this.status = (this.refundedAmount == this.amount)
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }

    public long getRemainingRefundable() {
        return this.amount - this.refundedAmount;
    }

    public boolean isFullyRefunded() {
        return this.refundedAmount == this.amount;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public long getAmount() {
        return amount;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }
}
