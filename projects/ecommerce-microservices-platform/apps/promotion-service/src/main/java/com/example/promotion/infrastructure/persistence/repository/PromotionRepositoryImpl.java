package com.example.promotion.infrastructure.persistence.repository;

import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import com.example.promotion.domain.promotion.PromotionStatus;
import com.example.promotion.domain.tenant.TenantContext;
import com.example.promotion.infrastructure.persistence.entity.PromotionJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromotionRepositoryImpl implements PromotionRepository {

    private final PromotionJpaRepository jpaRepository;

    @Override
    public Promotion save(Promotion promotion) {
        // Update path is tenant-scoped: a cross-tenant id resolves to empty so the
        // insert branch stamps the CURRENT tenant — but the command service always
        // loads (tenant-scoped) before update, so a cross-tenant write 404s upstream.
        Optional<PromotionJpaEntity> existing = jpaRepository
                .findByPromotionIdAndTenantId(promotion.getPromotionId(), TenantContext.currentTenant());
        if (existing.isPresent()) {
            existing.get().updateFrom(promotion);
            return existing.get().toDomain();
        }
        PromotionJpaEntity entity = PromotionJpaEntity.fromDomain(promotion, TenantContext.currentTenant());
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Promotion> findById(String promotionId) {
        // Tenant-scoped (HTTP admin detail read): cross-tenant id → empty → 404 (M3).
        return jpaRepository.findByPromotionIdAndTenantId(promotionId, TenantContext.currentTenant())
                .map(PromotionJpaEntity::toDomain);
    }

    @Override
    public List<Promotion> findAllByIds(List<String> promotionIds) {
        if (promotionIds.isEmpty()) {
            return List.of();
        }
        // Operator-facing enrichment (coupon list → promotion names): tenant-scope it
        // so it can never surface a cross-tenant promotion's name. The ids already
        // belong to in-tenant coupons, so the filter is net-zero in practice.
        String tenantId = TenantContext.currentTenant();
        return jpaRepository.findAllById(promotionIds).stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .map(PromotionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Promotion> findByIdForUpdate(String promotionId) {
        // PESSIMISTIC_WRITE before issue/update: tenant-scoped so a cross-tenant
        // write cannot reach (or lock) the row → 404 (M3).
        return jpaRepository.findByIdForUpdate(promotionId, TenantContext.currentTenant())
                .map(PromotionJpaEntity::toDomain);
    }

    @Override
    public void deleteById(String promotionId) {
        // The command service loads the promotion tenant-scoped (findById → 404 on a
        // cross-tenant id) before calling delete, so this PK delete can only ever
        // reach an in-tenant row.
        jpaRepository.deleteById(promotionId);
    }

    @Override
    public PageResult<Promotion> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PromotionJpaEntity> result = jpaRepository.findByTenantId(TenantContext.currentTenant(), pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public PageResult<Promotion> findAllByStatus(PromotionStatus status, int page, int size, Clock clock) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Instant now = Instant.now(clock);
        String tenantId = TenantContext.currentTenant();

        Page<PromotionJpaEntity> result = switch (status) {
            case ACTIVE -> jpaRepository.findActive(tenantId, now, pageRequest);
            case SCHEDULED -> jpaRepository.findScheduled(tenantId, now, pageRequest);
            case ENDED -> jpaRepository.findEnded(tenantId, now, pageRequest);
        };

        return toPageResult(result, page, size);
    }

    @Override
    public long countAllForTenant() {
        return jpaRepository.countByTenantId(TenantContext.currentTenant());
    }

    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        return jpaRepository.countByTenantIdAndCreatedAtBetween(TenantContext.currentTenant(), from, to);
    }

    private PageResult<Promotion> toPageResult(Page<PromotionJpaEntity> result, int page, int size) {
        return new PageResult<>(
                result.getContent().stream().map(PromotionJpaEntity::toDomain).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
