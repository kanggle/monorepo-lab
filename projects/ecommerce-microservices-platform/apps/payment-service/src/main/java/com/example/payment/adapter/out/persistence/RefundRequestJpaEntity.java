package com.example.payment.adapter.out.persistence;

import com.example.payment.domain.model.RefundRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA mapping for the {@code payment_refund_request} table (TASK-BE-535, Flyway V9).
 *
 * <p>Lives in {@code adapter.out.persistence} so it is covered by the package-scoped
 * {@code @EnableJpaRepositories} / {@code @EntityScan} in {@code JpaConfig}.
 *
 * <p>The {@code uniqueConstraints} declaration mirrors the Flyway V9 constraint. Flyway
 * owns the schema (Hibernate never generates DDL here) — it is declared so the invariant
 * is visible at the mapping a reader lands on.
 */
@Entity
@Table(name = "payment_refund_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payment_refund_request_key",
                columnNames = {"payment_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RefundRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static RefundRequestJpaEntity fromDomain(RefundRequest r) {
        RefundRequestJpaEntity e = new RefundRequestJpaEntity();
        e.id = r.getId();
        e.paymentId = r.getPaymentId();
        e.idempotencyKey = r.getIdempotencyKey();
        e.amount = r.getAmount();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    RefundRequest toDomain() {
        return RefundRequest.reconstitute(id, paymentId, idempotencyKey, amount, createdAt);
    }
}
