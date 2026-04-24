package com.example.user.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class WishlistItem {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private Instant addedAt;

    private WishlistItem() {
    }

    public static WishlistItem create(UUID userId, UUID productId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }

        WishlistItem item = new WishlistItem();
        item.id = UUID.randomUUID();
        item.userId = userId;
        item.productId = productId;
        item.addedAt = Instant.now();
        return item;
    }

    public static WishlistItem reconstitute(UUID id, UUID userId, UUID productId, Instant addedAt) {
        WishlistItem item = new WishlistItem();
        item.id = id;
        item.userId = userId;
        item.productId = productId;
        item.addedAt = addedAt;
        return item;
    }
}
