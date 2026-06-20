package com.example.settlement.presentation.dto;

import com.example.settlement.application.view.PayoutView;

import java.util.List;

/**
 * Response for {@code GET /api/admin/settlements/periods/{periodId}/payouts} and
 * {@code POST …/payouts/execute} (settlement-api.md § Period close + payout).
 * Mirrors the accrual list shape ({@link AccrualListResponse}) — paginated items
 * with page/size/totalElements.
 */
public record PayoutListResponse(
        List<PayoutResponse> items,
        int page,
        int size,
        long totalElements) {

    public static PayoutListResponse of(List<PayoutView> views, int page, int size) {
        List<PayoutResponse> items = views.stream().map(PayoutResponse::from).toList();
        int fromIdx = Math.min(page * size, items.size());
        int toIdx = Math.min(fromIdx + size, items.size());
        List<PayoutResponse> paged = items.subList(fromIdx, toIdx);
        return new PayoutListResponse(paged, page, size, items.size());
    }
}
