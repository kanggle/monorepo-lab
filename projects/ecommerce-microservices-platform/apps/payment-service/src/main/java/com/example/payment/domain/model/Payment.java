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
                                        long amount, PaymentStatus status,
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

    public void refund() {
        if (this.status == PaymentStatus.REFUNDED) {
            return;
        }
        if (this.status != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentException("COMPLETED 상태에서만 환불할 수 있습니다: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
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
