package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.ProductCreateRequest;
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
 * JPA mapping for the {@code product_create_request} table (TASK-BE-536, Flyway V18).
 */
@Entity
@Table(name = "product_create_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_create_request_key",
                columnNames = {"tenant_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCreateRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ProductCreateRequestJpaEntity fromDomain(ProductCreateRequest r) {
        ProductCreateRequestJpaEntity e = new ProductCreateRequestJpaEntity();
        e.id = r.getId();
        e.tenantId = r.getTenantId();
        e.idempotencyKey = r.getIdempotencyKey();
        e.name = r.getName();
        e.productId = r.getProductId();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    public ProductCreateRequest toDomain() {
        return ProductCreateRequest.reconstitute(id, tenantId, idempotencyKey, name, productId, createdAt);
    }
}
