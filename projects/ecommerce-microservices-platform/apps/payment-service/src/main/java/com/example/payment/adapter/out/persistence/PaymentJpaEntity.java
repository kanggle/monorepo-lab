package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PaymentJpaEntity {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Outer-axis tenant owning this payment (ADR-MONO-030 Step 4 facet c — TASK-BE-400, M1).
     * Stamped once at insert from the request/event tenant context; immutable afterward.
     * Not part of the clean {@code Payment} domain model — persistence/event layers only.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "receipt_url")
    private String receiptUrl;

    static PaymentJpaEntity fromDomain(com.example.payment.domain.model.Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.paymentId = payment.getPaymentId();
        entity.orderId = payment.getOrderId();
        entity.userId = payment.getUserId();
        entity.tenantId = payment.getTenantId();
        entity.amount = payment.getAmount();
        entity.status = payment.getStatus();
        entity.createdAt = payment.getCreatedAt();
        entity.paidAt = payment.getPaidAt();
        entity.refundedAt = payment.getRefundedAt();
        entity.paymentKey = payment.getPaymentKey();
        entity.paymentMethod = payment.getPaymentMethod();
        entity.receiptUrl = payment.getReceiptUrl();
        return entity;
    }
}
