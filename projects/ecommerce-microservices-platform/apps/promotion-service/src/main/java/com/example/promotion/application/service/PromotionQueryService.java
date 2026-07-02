package com.example.promotion.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.result.PromotionCountSummary;
import com.example.promotion.application.result.PromotionDetail;
import com.example.promotion.application.result.PromotionSummary;
import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import com.example.promotion.domain.promotion.PromotionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionQueryService {

    private final PromotionRepository promotionRepository;
    private final Clock clock;

    public PromotionDetail getPromotion(String promotionId, String userRole) {
        validateAdminRole(userRole);
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new PromotionNotFoundException(promotionId));
        return PromotionDetail.from(promotion, clock);
    }

    public PageResult<PromotionSummary> getPromotions(int page, int size, PromotionStatus status, String userRole) {
        validateAdminRole(userRole);
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

    public PromotionCountSummary getPromotionSummary(String userRole) {
        validateAdminRole(userRole);

        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(kst);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(kst);
        ZonedDateTime weekStart  = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(kst);
        ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(kst);

        long total = promotionRepository.countAll();
        long today = promotionRepository.countByCreatedAtBetween(todayStart.toInstant(), now.toInstant());
        long week  = promotionRepository.countByCreatedAtBetween(weekStart.toInstant(), now.toInstant());
        long month = promotionRepository.countByCreatedAtBetween(monthStart.toInstant(), now.toInstant());

        return new PromotionCountSummary(today, week, month, total);
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if ("ADMIN".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
