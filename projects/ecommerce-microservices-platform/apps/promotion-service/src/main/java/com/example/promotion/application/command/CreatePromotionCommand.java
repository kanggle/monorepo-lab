package com.example.promotion.application.command;

import java.time.Instant;

public record CreatePromotionCommand(
        String name,
        String description,
        String discountType,
        long discountValue,
        long maxDiscountAmount,
        int maxIssuanceCount,
        Instant startDate,
        Instant endDate,
        String userRole
) {
}
