package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.StockAdjustmentRequest;
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
import java.util.UUID;

/**
 * JPA mapping for the {@code stock_adjustment_request} table (TASK-BE-536, Flyway V17).
 *
 * <p>The {@code uniqueConstraints} declaration mirrors the Flyway constraint. Flyway
 * owns the schema (Hibernate never generates DDL here) — it is declared so the
 * invariant is visible at the mapping a reader lands on.
 */
@Entity
@Table(name = "stock_adjustment_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_stock_adjustment_request_key",
                columnNames = {"variant_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustmentRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false, columnDefinition = "uuid")
    private UUID variantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StockAdjustmentRequestJpaEntity fromDomain(StockAdjustmentRequest r) {
        StockAdjustmentRequestJpaEntity e = new StockAdjustmentRequestJpaEntity();
        e.id = r.getId();
        e.variantId = r.getVariantId();
        e.idempotencyKey = r.getIdempotencyKey();
        e.quantity = r.getQuantity();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    public StockAdjustmentRequest toDomain() {
        return StockAdjustmentRequest.reconstitute(id, variantId, idempotencyKey, quantity, createdAt);
    }
}
