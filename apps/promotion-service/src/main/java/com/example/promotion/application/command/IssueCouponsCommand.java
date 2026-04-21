package com.example.promotion.application.command;

import java.util.List;

public record IssueCouponsCommand(
        String promotionId,
        List<String> userIds,
        String userRole
) {
}
