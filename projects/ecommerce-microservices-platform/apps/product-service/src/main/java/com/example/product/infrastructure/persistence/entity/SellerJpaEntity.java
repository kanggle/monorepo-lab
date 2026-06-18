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
    @Column(name = "status", nullable = false, length = 24)
    private SellerStatus status;

    /** Backing IAM seller-operator account id (ADR-MONO-042 D2). Nullable until provisioned. */
    @Column(name = "account_id", length = 64)
    private String accountId;

    /** Born-unified central identity id (ADR-MONO-042 D5). Nullable until provisioned. */
    @Column(name = "identity_id", length = 64)
    private String identityId;

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
        entity.accountId = seller.getAccountId();
        entity.identityId = seller.getIdentityId();
        entity.createdAt = seller.getCreatedAt();
        entity.updatedAt = seller.getUpdatedAt();
        entity.isNew = true;
        return entity;
    }

    /**
     * Applies a mutated {@link Seller} aggregate's lifecycle fields onto a loaded entity
     * (ADR-MONO-042 — provisioning / suspend / close update an existing managed row).
     * The composite key + creation timestamp are immutable.
     */
    public void applyLifecycle(Seller seller) {
        this.status = seller.getStatus();
        this.accountId = seller.getAccountId();
        this.identityId = seller.getIdentityId();
        this.displayName = seller.getDisplayName();
        this.updatedAt = seller.getUpdatedAt();
    }

    public Seller toDomain() {
        return Seller.reconstitute(sellerId, displayName, status, accountId, identityId,
                createdAt, updatedAt);
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
