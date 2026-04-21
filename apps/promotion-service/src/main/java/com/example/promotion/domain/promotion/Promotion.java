package com.example.promotion.domain.promotion;

import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Getter
public class Promotion {

    private String promotionId;
    private String name;
    private String description;
    private DiscountType discountType;
    private long discountValue;
    private long maxDiscountAmount;
    private int maxIssuanceCount;
    private int issuedCount;
    private Instant startDate;
    private Instant endDate;
    private Instant createdAt;
    private Instant updatedAt;

    private Promotion() {
    }

    public static Promotion create(String name, String description,
                                    DiscountType discountType, long discountValue,
                                    long maxDiscountAmount, int maxIssuanceCount,
                                    Instant startDate, Instant endDate, Clock clock) {
        validateDates(startDate, endDate);
        validateDiscountValue(discountType, discountValue);

        Promotion promotion = new Promotion();
        promotion.promotionId = UUID.randomUUID().toString();
        promotion.name = name;
        promotion.description = description;
        promotion.discountType = discountType;
        promotion.discountValue = discountValue;
        promotion.maxDiscountAmount = maxDiscountAmount;
        promotion.maxIssuanceCount = maxIssuanceCount;
        promotion.issuedCount = 0;
        promotion.startDate = startDate;
        promotion.endDate = endDate;
        Instant now = Instant.now(clock);
        promotion.createdAt = now;
        promotion.updatedAt = now;
        return promotion;
    }

    public static Promotion reconstitute(String promotionId, String name, String description,
                                          DiscountType discountType, long discountValue,
                                          long maxDiscountAmount, int maxIssuanceCount,
                                          int issuedCount, Instant startDate, Instant endDate,
                                          Instant createdAt, Instant updatedAt) {
        Promotion promotion = new Promotion();
        promotion.promotionId = promotionId;
        promotion.name = name;
        promotion.description = description;
        promotion.discountType = discountType;
        promotion.discountValue = discountValue;
        promotion.maxDiscountAmount = maxDiscountAmount;
        promotion.maxIssuanceCount = maxIssuanceCount;
        promotion.issuedCount = issuedCount;
        promotion.startDate = startDate;
        promotion.endDate = endDate;
        promotion.createdAt = createdAt;
        promotion.updatedAt = updatedAt;
        return promotion;
    }

    public void update(String name, String description,
                       DiscountType discountType, long discountValue,
                       long maxDiscountAmount, int maxIssuanceCount,
                       Instant startDate, Instant endDate, Clock clock) {
        if (getStatus(clock) == PromotionStatus.ENDED) {
            throw new PromotionAlreadyEndedException(this.promotionId);
        }
        validateDates(startDate, endDate);
        validateDiscountValue(discountType, discountValue);

        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.maxIssuanceCount = maxIssuanceCount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = Instant.now(clock);
    }

    public PromotionStatus getStatus(Clock clock) {
        return PromotionStatus.resolve(startDate, endDate, Instant.now(clock));
    }

    public boolean isActive(Clock clock) {
        return getStatus(clock) == PromotionStatus.ACTIVE;
    }

    public void validateCanIssue(int requestedCount, Clock clock) {
        if (!isActive(clock)) {
            throw new PromotionNotActiveException(this.promotionId);
        }
        if (this.issuedCount + requestedCount > this.maxIssuanceCount) {
            throw new CouponLimitExceededException(
                    this.promotionId, this.maxIssuanceCount, this.issuedCount, requestedCount);
        }
    }

    public void incrementIssuedCount(int count) {
        this.issuedCount += count;
    }

    public boolean hasIssuedCoupons() {
        return this.issuedCount > 0;
    }

    public long calculateDiscount(long orderAmount) {
        long discount = switch (discountType) {
            case FIXED -> discountValue;
            case PERCENTAGE -> orderAmount * discountValue / 100;
        };
        if (maxDiscountAmount > 0 && discount > maxDiscountAmount) {
            discount = maxDiscountAmount;
        }
        if (discount > orderAmount) {
            discount = orderAmount;
        }
        return discount;
    }

    private static void validateDates(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
    }

    private static void validateDiscountValue(DiscountType discountType, long discountValue) {
        if (discountValue <= 0) {
            throw new IllegalArgumentException("Discount value must be positive");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue > 100) {
            throw new IllegalArgumentException("Percentage discount value must not exceed 100");
        }
    }
}
