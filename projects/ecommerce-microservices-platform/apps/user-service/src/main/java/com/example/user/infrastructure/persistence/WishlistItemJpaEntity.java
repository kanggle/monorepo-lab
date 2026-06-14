package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.WishlistItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wishlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class WishlistItemJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Owning tenant (ADR-MONO-030 Step 4, M1; TASK-BE-367) — denormalized from the
     * parent user profile. Stamped once at insert; immutable afterward. Not part of
     * the clean {@code WishlistItem} domain model — persistence/event layers only.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    static WishlistItemJpaEntity fromDomain(WishlistItem item, String tenantId) {
        WishlistItemJpaEntity entity = new WishlistItemJpaEntity();
        entity.id = item.getId();
        entity.tenantId = tenantId;
        entity.userId = item.getUserId();
        entity.productId = item.getProductId();
        entity.addedAt = item.getAddedAt();
        return entity;
    }
}
