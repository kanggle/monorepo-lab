package com.example.promotion.domain.promotion;

import com.example.common.page.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository {

    Promotion save(Promotion promotion);

    Optional<Promotion> findById(String promotionId);

    List<Promotion> findAllByIds(List<String> promotionIds);

    Optional<Promotion> findByIdForUpdate(String promotionId);

    void deleteById(String promotionId);

    PageResult<Promotion> findAll(int page, int size);

    PageResult<Promotion> findAllByStatus(PromotionStatus status, int page, int size, java.time.Clock clock);

    /** Total promotion count for the current tenant. */
    long countAll();

    /** Count promotions created within [from, to) for the current tenant. */
    long countByCreatedAtBetween(Instant from, Instant to);
}
