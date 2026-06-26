package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.model.StrandedRefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA mapping for the {@code stranded_refund} table (TASK-BE-438, Flyway V7).
 *
 * <p>Lives in {@code adapter.out.persistence} so it is covered by the package-scoped
 * {@code @EnableJpaRepositories} / {@code @EntityScan} in {@code JpaConfig} (the lib's
 * {@code OutboxJpaConfig} suppresses Boot's default repository auto-scan).
 */
@Entity
@Table(name = "stranded_refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StrandedRefundJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StrandedRefundStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    static StrandedRefundJpaEntity fromDomain(StrandedRefund r) {
        StrandedRefundJpaEntity e = new StrandedRefundJpaEntity();
        e.id = r.getId();
        e.paymentId = r.getPaymentId();
        e.orderId = r.getOrderId();
        e.paymentKey = r.getPaymentKey();
        e.amount = r.getAmount();
        e.reason = r.getReason();
        e.status = r.getStatus();
        e.attempts = r.getAttempts();
        e.nextAttemptAt = r.getNextAttemptAt();
        e.lastError = r.getLastError();
        e.createdAt = r.getCreatedAt();
        e.updatedAt = r.getUpdatedAt();
        e.resolvedAt = r.getResolvedAt();
        return e;
    }

    StrandedRefund toDomain() {
        return StrandedRefund.reconstitute(
                id, paymentId, orderId, paymentKey, amount, reason,
                status, attempts, nextAttemptAt, lastError,
                createdAt, updatedAt, resolvedAt);
    }
}
