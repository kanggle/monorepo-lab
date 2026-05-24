package com.example.order.presentation;

import com.example.order.domain.model.OrderStatus;
import com.example.common.page.PageQuery;
import com.example.order.application.exception.InvalidOrderStatusException;

public final class OrderControllerUtils {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private OrderControllerUtils() {
    }

    static PageQuery buildPageQuery(int page, int size, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return new PageQuery(safePage, safeSize, "createdAt", "DESC");
    }

    static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderStatusException(status);
        }
    }
}
