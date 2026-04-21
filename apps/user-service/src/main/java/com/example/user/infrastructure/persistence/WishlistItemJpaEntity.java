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

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    static WishlistItemJpaEntity fromDomain(WishlistItem item) {
        WishlistItemJpaEntity entity = new WishlistItemJpaEntity();
        entity.id = item.getId();
        entity.userId = item.getUserId();
        entity.productId = item.getProductId();
        entity.addedAt = item.getAddedAt();
        return entity;
    }
}
