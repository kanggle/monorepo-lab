package com.example.settlement.presentation.dto;

import com.example.settlement.application.view.PeriodView;

import java.util.List;

/** Paged {@code GET /periods} response (settlement-api.md). */
public record PeriodListResponse(
        List<PeriodSummary> items,
        int page,
        int size,
        long totalElements) {

    public static PeriodListResponse of(List<PeriodView> all, int page, int size) {
        List<PeriodSummary> pageContent = paginate(all, page, size).stream()
                .map(PeriodSummary::from)
                .toList();
        return new PeriodListResponse(pageContent, page, size, all.size());
    }

    private static List<PeriodView> paginate(List<PeriodView> all, int page, int size) {
        if (size <= 0 || all.isEmpty()) {
            return List.of();
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    /** One period in the list (no payouts). */
    public record PeriodSummary(
            String periodId,
            String from,
            String to,
            String status,
            String closedAt,
            Integer sellerCount) {

        static PeriodSummary from(PeriodView v) {
            return new PeriodSummary(
                    v.periodId(),
                    v.from() == null ? null : v.from().toString(),
                    v.to() == null ? null : v.to().toString(),
                    v.status(),
                    v.closedAt() == null ? null : v.closedAt().toString(),
                    v.sellerCount());
        }
    }
}
