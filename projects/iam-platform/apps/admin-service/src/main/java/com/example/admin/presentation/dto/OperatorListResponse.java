package com.example.admin.presentation.dto;

import java.util.List;

/**
 * Page envelope for {@code GET /api/admin/operators}. Matches the
 * pagination shape used elsewhere in admin-service
 * (see {@code AccountServiceClient.AccountSearchResponse}).
 */
public record OperatorListResponse(
        List<OperatorSummaryResponse> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {}
