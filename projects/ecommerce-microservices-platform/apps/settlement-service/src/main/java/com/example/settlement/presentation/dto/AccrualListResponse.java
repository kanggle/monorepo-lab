package com.example.settlement.presentation.dto;

import com.example.settlement.domain.model.CommissionAccrual;
import com.example.common.page.PageResult;

import java.util.List;

/** Paged {@code GET /accruals} response (settlement-api.md). */
public record AccrualListResponse(
        List<AccrualResponse> items,
        int page,
        int size,
        long totalElements) {

    public static AccrualListResponse from(PageResult<CommissionAccrual> result) {
        List<AccrualResponse> items = result.content().stream()
                .map(AccrualResponse::from)
                .toList();
        return new AccrualListResponse(items, result.page(), result.size(), result.totalElements());
    }
}
