package com.example.fanplatform.community.presentation.dto;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.FeedItemView;

import java.util.List;

public record FeedResponse(
        List<FeedItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static FeedResponse from(PageResult<FeedItemView> p) {
        return new FeedResponse(
                p.content().stream().map(FeedItemResponse::from).toList(),
                p.page(),
                p.size(),
                p.totalElements(),
                p.totalPages(),
                // hasNext convenience — FeedPage's original derivation, now computed
                // at this call site since com.example.common.page.PageResult has no
                // such method (ADR-MONO-058 § D3 adoption; TASK-FAN-BE-043).
                p.page() + 1 < p.totalPages());
    }
}
