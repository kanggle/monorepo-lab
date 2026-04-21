package com.example.promotion.infrastructure.persistence.repository;

import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import com.example.promotion.domain.promotion.PromotionStatus;
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
        Optional<PromotionJpaEntity> existing = jpaRepository.findById(promotion.getPromotionId());
        if (existing.isPresent()) {
            existing.get().updateFrom(promotion);
            return existing.get().toDomain();
        }
        PromotionJpaEntity entity = PromotionJpaEntity.fromDomain(promotion);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Promotion> findById(String promotionId) {
        return jpaRepository.findById(promotionId).map(PromotionJpaEntity::toDomain);
    }

    @Override
    public List<Promotion> findAllByIds(List<String> promotionIds) {
        if (promotionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(promotionIds).stream()
                .map(PromotionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Promotion> findByIdForUpdate(String promotionId) {
        return jpaRepository.findByIdForUpdate(promotionId).map(PromotionJpaEntity::toDomain);
    }

    @Override
    public void deleteById(String promotionId) {
        jpaRepository.deleteById(promotionId);
    }

    @Override
    public PageResult<Promotion> findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PromotionJpaEntity> result = jpaRepository.findAll(pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public PageResult<Promotion> findAllByStatus(PromotionStatus status, int page, int size, Clock clock) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Instant now = Instant.now(clock);

        Page<PromotionJpaEntity> result = switch (status) {
            case ACTIVE -> jpaRepository.findActive(now, pageRequest);
            case SCHEDULED -> jpaRepository.findScheduled(now, pageRequest);
            case ENDED -> jpaRepository.findEnded(now, pageRequest);
        };

        return toPageResult(result, page, size);
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
