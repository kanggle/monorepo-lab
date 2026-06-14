package com.example.user.infrastructure.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.WishlistItem;
import com.example.user.domain.repository.WishlistItemRepository;
import com.example.user.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class WishlistItemRepositoryImpl implements WishlistItemRepository {

    private final WishlistItemJpaRepository jpaRepository;
    private final WishlistItemJpaMapper mapper;

    @Override
    public WishlistItem save(WishlistItem item) {
        WishlistItemJpaEntity entity = mapper.toEntity(item);
        WishlistItemJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WishlistItem> findById(UUID id) {
        return jpaRepository.findByIdAndTenantId(id, TenantContext.currentTenant()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserIdAndProductId(UUID userId, UUID productId) {
        return jpaRepository.existsByUserIdAndProductIdAndTenantId(userId, productId, TenantContext.currentTenant());
    }

    @Override
    public Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId) {
        return jpaRepository.findByUserIdAndProductIdAndTenantId(userId, productId, TenantContext.currentTenant()).map(mapper::toDomain);
    }

    @Override
    public PageResult<WishlistItem> findAllByUserId(UUID userId, PageQuery pageQuery) {
        Sort sort = Sort.by(Sort.Direction.fromString(pageQuery.sortDirection()), pageQuery.sortBy());
        PageRequest pageRequest = PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
        Page<WishlistItemJpaEntity> page = jpaRepository.findAllByUserIdAndTenantId(userId, TenantContext.currentTenant(), pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    public void delete(WishlistItem item) {
        jpaRepository.findById(item.getId())
                .ifPresent(jpaRepository::delete);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        jpaRepository.deleteAllByUserIdAndTenantId(userId, TenantContext.currentTenant());
    }
}
