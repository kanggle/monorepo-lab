package com.example.user.application.service;

import com.example.user.application.command.AddWishlistItemCommand;
import com.example.user.application.result.AddWishlistItemResult;
import com.example.user.application.result.WishlistCheckResult;
import com.example.user.application.result.WishlistItemResult;
import com.example.user.application.result.WishlistPageResult;
import com.example.user.domain.exception.AlreadyInWishlistException;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.exception.WishlistItemNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.WishlistItem;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.repository.WishlistItemRepository;
import com.example.user.domain.service.ProductInfoProvider;
import com.example.user.domain.service.ProductInfoProvider.ProductInfo;
import com.example.user.domain.exception.WishlistAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProductInfoProvider productInfoProvider;

    @Transactional
    public AddWishlistItemResult addItem(AddWishlistItemCommand command) {
        if (!userProfileRepository.existsByUserId(command.userId())) {
            throw new UserProfileNotFoundException(command.userId());
        }

        if (wishlistItemRepository.existsByUserIdAndProductId(command.userId(), command.productId())) {
            throw new AlreadyInWishlistException(command.productId());
        }

        WishlistItem item = WishlistItem.create(command.userId(), command.productId());
        WishlistItem saved = wishlistItemRepository.save(item);
        log.info("Wishlist item added: wishlistItemId={}, userId={}, productId={}",
                saved.getId(), command.userId(), command.productId());
        return AddWishlistItemResult.from(saved);
    }

    public WishlistPageResult getWishlist(UUID userId, int page, int size) {
        PageQuery pageQuery = PageQuery.of(page, size, "addedAt", "DESC");

        PageResult<WishlistItem> pageResult = wishlistItemRepository.findAllByUserId(userId, pageQuery);

        Set<UUID> productIds = pageResult.content().stream()
                .map(WishlistItem::getProductId)
                .collect(Collectors.toSet());

        Map<UUID, ProductInfo> productInfos = productIds.isEmpty()
                ? Map.of()
                : productInfoProvider.getProductInfos(productIds);

        List<WishlistItemResult> content = pageResult.content().stream()
                .map(item -> toWishlistItemResult(item, productInfos.get(item.getProductId())))
                .toList();

        return new WishlistPageResult(content, pageQuery.page(), pageQuery.size(), pageResult.totalElements());
    }

    @Transactional
    public void removeItem(UUID userId, UUID wishlistItemId) {
        WishlistItem item = wishlistItemRepository.findById(wishlistItemId)
                .orElseThrow(() -> new WishlistItemNotFoundException(wishlistItemId));

        if (!item.getUserId().equals(userId)) {
            throw new WishlistAccessDeniedException(wishlistItemId);
        }

        wishlistItemRepository.delete(item);
        log.info("Wishlist item removed: wishlistItemId={}, userId={}", wishlistItemId, userId);
    }

    private WishlistItemResult toWishlistItemResult(WishlistItem item, ProductInfo info) {
        if (info != null) {
            return new WishlistItemResult(
                    item.getId(),
                    item.getProductId(),
                    info.name(),
                    info.price(),
                    info.status(),
                    item.getAddedAt()
            );
        }
        return new WishlistItemResult(
                item.getId(),
                item.getProductId(),
                null,
                0,
                "DELETED",
                item.getAddedAt()
        );
    }

    public WishlistCheckResult checkItem(UUID userId, UUID productId) {
        return wishlistItemRepository.findByUserIdAndProductId(userId, productId)
                .map(item -> new WishlistCheckResult(productId, true, item.getId()))
                .orElseGet(() -> new WishlistCheckResult(productId, false, null));
    }
}
