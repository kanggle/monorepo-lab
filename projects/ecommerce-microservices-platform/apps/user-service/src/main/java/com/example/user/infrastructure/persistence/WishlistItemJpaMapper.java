package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.WishlistItem;
import com.example.user.domain.tenant.TenantContext;
import org.springframework.stereotype.Component;

@Component
class WishlistItemJpaMapper {

    WishlistItem toDomain(WishlistItemJpaEntity entity) {
        return WishlistItem.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getAddedAt()
        );
    }

    WishlistItemJpaEntity toEntity(WishlistItem item) {
        return WishlistItemJpaEntity.fromDomain(item, TenantContext.currentTenant());
    }
}
