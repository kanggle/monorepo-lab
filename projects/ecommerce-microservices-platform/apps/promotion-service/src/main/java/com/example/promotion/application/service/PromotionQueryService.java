package com.example.promotion.application.service;

import com.example.promotion.application.result.PromotionDetail;
import com.example.promotion.application.result.PromotionSummary;
import com.example.common.page.PageResult;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import com.example.promotion.domain.promotion.PromotionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionQueryService {

    private final PromotionRepository promotionRepository;
    private final Clock clock;

    public PromotionDetail getPromotion(String promotionId, String userRole) {
        OperatorRoleGuard.requireOperator(userRole);
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new PromotionNotFoundException(promotionId));
        return PromotionDetail.from(promotion, clock);
    }

    public PageResult<PromotionSummary> getPromotions(int page, int size, PromotionStatus status, String userRole) {
        OperatorRoleGuard.requireOperator(userRole);
        PageResult<Promotion> result;
        if (status != null) {
            result = promotionRepository.findAllByStatus(status, page, size, clock);
        } else {
            result = promotionRepository.findAll(page, size);
        }

        return new PageResult<>(
                result.content().stream()
                        .map(p -> PromotionSummary.from(p, clock))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    public PeriodSummary getPeriodSummary(String userRole) {
        OperatorRoleGuard.requireOperator(userRole);

        KstPeriodBounds b = KstPeriodBounds.from(clock);

        long total = promotionRepository.countAllForTenant();
        long today = promotionRepository.countCreatedBetween(b.todayStartInstant(), b.nowInstant());
        long week  = promotionRepository.countCreatedBetween(b.weekStartInstant(), b.nowInstant());
        long month = promotionRepository.countCreatedBetween(b.monthStartInstant(), b.nowInstant());

        return new PeriodSummary(today, week, month, total);
    }
}
