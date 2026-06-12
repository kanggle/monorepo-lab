package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA mapping for the marketplace {@link Seller} aggregate (ADR-MONO-030 Step 3).
 * Composite primary key {@code (tenant_id, seller_id)} via {@link SellerId}: the
 * seller is a participant inside a tenant, so the tenant is part of its identity.
 */
@Entity
@Table(name = "sellers")
@IdClass(SellerJpaEntity.SellerId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerJpaEntity implements Persistable<SellerJpaEntity.SellerId> {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "seller_id", nullable = false, updatable = false, length = 64)
    private String sellerId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public SellerId getId() {
        return new SellerId(tenantId, sellerId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static SellerJpaEntity from(Seller seller, String tenantId) {
        SellerJpaEntity entity = new SellerJpaEntity();
        entity.tenantId = tenantId;
        entity.sellerId = seller.getSellerId();
        entity.displayName = seller.getDisplayName();
        entity.status = seller.getStatus();
        entity.createdAt = seller.getCreatedAt();
        entity.updatedAt = seller.getUpdatedAt();
        entity.isNew = true;
        return entity;
    }

    public Seller toDomain() {
        return Seller.reconstitute(sellerId, displayName, status, createdAt, updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellerJpaEntity e)) return false;
        return Objects.equals(tenantId, e.tenantId) && Objects.equals(sellerId, e.sellerId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Composite identity {@code (tenant_id, seller_id)}. */
    public record SellerId(String tenantId, String sellerId) implements Serializable {
    }
}
