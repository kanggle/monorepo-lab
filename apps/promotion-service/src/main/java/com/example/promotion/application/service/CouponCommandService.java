package com.example.promotion.application.service;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.event.CouponUsedEvent;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.promotion.domain.coupon.CouponStatus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponCommandService {

    private final CouponRepository couponRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public IssueCouponsResult issueCoupons(IssueCouponsCommand command) {
        validateAdminRole(command.userRole());
        Promotion promotion = promotionRepository.findByIdForUpdate(command.promotionId())
                .orElseThrow(() -> new PromotionNotFoundException(command.promotionId()));

        promotion.validateCanIssue(command.userIds().size(), clock);

        List<Coupon> coupons = new ArrayList<>();
        for (String userId : command.userIds()) {
            Coupon coupon = Coupon.issue(
                    promotion.getPromotionId(),
                    userId,
                    promotion.getEndDate(),
                    clock
            );
            coupons.add(coupon);
        }

        couponRepository.saveAll(coupons);
        promotion.incrementIssuedCount(coupons.size());
        promotionRepository.save(promotion);

        log.info("Coupons issued: promotionId={}, count={}", command.promotionId(), coupons.size());
        return new IssueCouponsResult(coupons.size());
    }

    @Transactional
    public ApplyCouponResult applyCoupon(ApplyCouponCommand command) {
        Coupon coupon = couponRepository.findByIdForUpdate(command.couponId())
                .orElseThrow(() -> new CouponNotFoundException(command.couponId()));

        coupon.apply(command.orderId(), command.userId(), clock);

        Promotion promotion = promotionRepository.findById(coupon.getPromotionId())
                .orElseThrow(() -> new PromotionNotFoundException(coupon.getPromotionId()));

        long discountAmount = promotion.calculateDiscount(command.orderAmount());
        long finalAmount = command.orderAmount() - discountAmount;

        couponRepository.save(coupon);

        CouponUsedEvent event = CouponUsedEvent.of(
                coupon.getCouponId(), coupon.getPromotionId(),
                coupon.getUserId(), command.orderId(), discountAmount, clock
        );
        eventPublisher.publishCouponUsed(event);

        log.info("Coupon applied: couponId={}, orderId={}, discount={}",
                coupon.getCouponId(), command.orderId(), discountAmount);
        return new ApplyCouponResult(coupon.getCouponId(), discountAmount, finalAmount);
    }

    private void validateAdminRole(String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException();
        }
    }

    @Transactional
    public void restoreCouponsByOrderId(String orderId) {
        List<Coupon> usedCoupons = couponRepository.findByOrderIdAndStatus(orderId, CouponStatus.USED);
        if (usedCoupons.isEmpty()) {
            log.info("No USED coupons found for orderId={}, skipping restore", orderId);
            return;
        }

        for (Coupon coupon : usedCoupons) {
            coupon.restore();
            couponRepository.save(coupon);
            log.info("Coupon restored: couponId={}, orderId={}", coupon.getCouponId(), orderId);
        }
    }
}
