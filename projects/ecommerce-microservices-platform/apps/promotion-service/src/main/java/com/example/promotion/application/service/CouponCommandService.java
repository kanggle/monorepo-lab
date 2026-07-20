package com.example.promotion.application.service;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.event.CouponUsedEvent;
import com.example.promotion.application.exception.IdempotencyKeyConflictException;
import com.example.promotion.application.exception.IdempotencyKeyRequiredException;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponIssueRequest;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.promotion.domain.coupon.CouponStatus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponCommandService {

    private final CouponRepository couponRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionEventPublisher eventPublisher;
    /** Idempotency store for this admin write path (TASK-BE-536). */
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final Clock clock;

    /**
     * Issues one coupon per {@code userId} in the batch. {@code Idempotency-Key} is
     * <b>required</b> (TASK-BE-536): {@link Promotion#validateCanIssue} caps only
     * the <em>total</em> issued count, not "this exact batch was already issued",
     * and {@link Coupon#issue} mints a fresh id every call — so a replay mints an
     * entire second batch of coupons until the cap is hit.
     *
     * <ul>
     *   <li><b>Absent / blank key → {@link IdempotencyKeyRequiredException}</b> (400).</li>
     *   <li><b>Same key, same user batch → replay.</b> Returns the ALREADY-issued
     *       count without minting a second batch.</li>
     *   <li><b>Same key, different user batch → {@link IdempotencyKeyConflictException}</b>
     *       (409).</li>
     *   <li><b>Different key → proceeds</b> (AC-2), composing with the cap: a
     *       replay must issue NOTHING, not "issue up to the cap" (Edge Case).</li>
     * </ul>
     *
     * <p><b>Concurrency.</b> The arbiter is {@code UNIQUE (promotion_id,
     * idempotency_key)}: the claim insert happens BEFORE any {@code Coupon.issue}
     * call, so a concurrent duplicate that also missed the replay lookup loses the
     * insert race and never mints coupons.
     */
    @Transactional
    public IssueCouponsResult issueCoupons(IssueCouponsCommand command) {
        OperatorRoleGuard.requireOperator(command.userRole());
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key 헤더는 쿠폰 발급 요청에 필수입니다");
        }

        Promotion promotion = promotionRepository.findByIdForUpdate(command.promotionId())
                .orElseThrow(() -> new PromotionNotFoundException(command.promotionId()));

        Optional<CouponIssueRequest> replayed =
                couponIssueRequestRepository.find(command.promotionId(), command.idempotencyKey());
        if (replayed.isPresent()) {
            if (!replayed.get().matchesUserIds(command.userIds())) {
                throw new IdempotencyKeyConflictException(
                        "동일한 Idempotency-Key 가 다른 사용자 배치로 재사용되었습니다: promotionId="
                                + command.promotionId());
            }
            // Replay: this batch was already issued under this key. Do NOT mint a
            // second batch — return the already-issued count.
            log.info("Idempotent coupon-issue replay: promotionId={}, issuedCount={}",
                    command.promotionId(), replayed.get().getIssuedCount());
            return new IssueCouponsResult(replayed.get().getIssuedCount());
        }

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

        // Claim the key BEFORE any coupon is minted. A concurrent duplicate that
        // also missed the lookup above loses this insert and never issues a coupon.
        try {
            couponIssueRequestRepository.insert(CouponIssueRequest.of(
                    command.promotionId(), command.idempotencyKey(), command.userIds(),
                    coupons.size(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyConflictException(
                    "동일한 Idempotency-Key 의 쿠폰 발급 요청이 이미 처리 중이거나 처리되었습니다: promotionId="
                            + command.promotionId(), e);
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
