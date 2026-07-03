package com.example.user.infrastructure.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository jpaRepository;
    private final UserProfileJpaMapper mapper;

    @Override
    public UserProfile save(UserProfile userProfile) {
        UserProfileJpaEntity entity = mapper.toEntity(userProfile);
        UserProfileJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndTenantId(userId, TenantContext.currentTenant()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return jpaRepository.existsByUserId(userId);
    }

    @Override
    public PageResult<UserProfile> findByStatusAndEmailContaining(ProfileStatus status, String email, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<UserProfileJpaEntity> page = jpaRepository.findByTenantIdAndStatusAndEmailContaining(TenantContext.currentTenant(), status, email, pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    public PageResult<UserProfile> findByStatus(ProfileStatus status, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<UserProfileJpaEntity> page = jpaRepository.findByTenantIdAndStatus(TenantContext.currentTenant(), status, pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    public PageResult<UserProfile> findByEmailContaining(String email, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<UserProfileJpaEntity> page = jpaRepository.findByTenantIdAndEmailContaining(TenantContext.currentTenant(), email, pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    public PageResult<UserProfile> findAll(PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<UserProfileJpaEntity> page = jpaRepository.findByTenantId(TenantContext.currentTenant(), pageable);
        return toPageResult(page, pageQuery);
    }

    @Override
    public long countAllForTenant() {
        return jpaRepository.countByTenantId(TenantContext.currentTenant());
    }

    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        return jpaRepository.countByTenantIdAndCreatedAtBetween(TenantContext.currentTenant(), from, to);
    }

    private Pageable toPageable(PageQuery pageQuery) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(pageQuery.sortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, pageQuery.sortBy());
        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
    }

    private PageResult<UserProfile> toPageResult(Page<UserProfileJpaEntity> page, PageQuery pageQuery) {
        List<UserProfile> content = page.getContent().stream()
                .map(mapper::toDomain)
                .toList();
        return new PageResult<>(
                content,
                page.getNumber(),
                pageQuery.size(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
