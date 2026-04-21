package com.example.promotion.application.service;

import com.example.promotion.application.result.CouponDetail;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.common.page.PageResult;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponQueryService {

    private final CouponRepository couponRepository;
    private final PromotionRepository promotionRepository;

    public PageResult<CouponDetail> getMyCoupons(String userId, int page, int size, CouponStatus status) {
        PageResult<Coupon> result;
        if (status != null) {
            result = couponRepository.findByUserIdAndStatus(userId, status, page, size);
        } else {
            result = couponRepository.findByUserId(userId, page, size);
        }

        List<String> promotionIds = result.content().stream()
                .map(Coupon::getPromotionId)
                .distinct()
                .toList();

        Map<String, Promotion> promotionMap = promotionRepository.findAllByIds(promotionIds).stream()
                .collect(Collectors.toMap(Promotion::getPromotionId, Function.identity()));

        return new PageResult<>(
                result.content().stream()
                        .map(coupon -> toCouponDetail(coupon, promotionMap.get(coupon.getPromotionId())))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    private CouponDetail toCouponDetail(Coupon coupon, Promotion promotion) {
        String promotionName = promotion != null ? promotion.getName() : "Unknown";
        var discountType = promotion != null ? promotion.getDiscountType() : null;
        long discountValue = promotion != null ? promotion.getDiscountValue() : 0;
        long maxDiscountAmount = promotion != null ? promotion.getMaxDiscountAmount() : 0;

        return CouponDetail.from(coupon, promotionName, discountType, discountValue, maxDiscountAmount);
    }
}
