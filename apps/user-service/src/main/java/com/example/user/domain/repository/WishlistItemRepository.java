package com.example.user.domain.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.WishlistItem;

import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository {

    WishlistItem save(WishlistItem item);

    Optional<WishlistItem> findById(UUID id);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);

    PageResult<WishlistItem> findAllByUserId(UUID userId, PageQuery pageQuery);

    void delete(WishlistItem item);

    void deleteAllByUserId(UUID userId);
}
