package com.example.promotion.application.service;

import com.example.promotion.application.command.CreatePromotionCommand;
import com.example.promotion.application.command.UpdatePromotionCommand;
import com.example.promotion.application.result.CreatePromotionResult;
import com.example.promotion.application.result.UpdatePromotionResult;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionHasIssuedCouponsException;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionCommandService {

    private final PromotionRepository promotionRepository;
    private final CouponRepository couponRepository;
    private final Clock clock;

    @Transactional
    public CreatePromotionResult createPromotion(CreatePromotionCommand command) {
        OperatorRoleGuard.requireOperator(command.userRole());
        DiscountType discountType = DiscountType.valueOf(command.discountType());

        Promotion promotion = Promotion.create(
                command.name(), command.description(),
                discountType, command.discountValue(),
                command.maxDiscountAmount(), command.maxIssuanceCount(),
                command.startDate(), command.endDate(), clock
        );

        promotionRepository.save(promotion);
        log.info("Promotion created: promotionId={}", promotion.getPromotionId());
        return new CreatePromotionResult(promotion.getPromotionId());
    }

    @Transactional
    public UpdatePromotionResult updatePromotion(UpdatePromotionCommand command) {
        OperatorRoleGuard.requireOperator(command.userRole());
        Promotion promotion = promotionRepository.findById(command.promotionId())
                .orElseThrow(() -> new PromotionNotFoundException(command.promotionId()));

        DiscountType discountType = DiscountType.valueOf(command.discountType());

        promotion.update(
                command.name(), command.description(),
                discountType, command.discountValue(),
                command.maxDiscountAmount(), command.maxIssuanceCount(),
                command.startDate(), command.endDate(), clock
        );

        promotionRepository.save(promotion);
        log.info("Promotion updated: promotionId={}", promotion.getPromotionId());
        return new UpdatePromotionResult(promotion.getPromotionId());
    }

    @Transactional
    public void deletePromotion(String promotionId, String userRole) {
        OperatorRoleGuard.requireOperator(userRole);
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new PromotionNotFoundException(promotionId));

        if (promotion.hasIssuedCoupons()) {
            throw new PromotionHasIssuedCouponsException(promotionId);
        }

        promotionRepository.deleteById(promotionId);
        log.info("Promotion deleted: promotionId={}", promotionId);
    }
}
