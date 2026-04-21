package com.example.promotion.domain.promotion;

import java.time.Instant;

public enum PromotionStatus {
    SCHEDULED,
    ACTIVE,
    ENDED;

    public static PromotionStatus resolve(Instant startDate, Instant endDate, Instant now) {
        if (now.isBefore(startDate)) {
            return SCHEDULED;
        }
        if (now.isAfter(endDate)) {
            return ENDED;
        }
        return ACTIVE;
    }
}
