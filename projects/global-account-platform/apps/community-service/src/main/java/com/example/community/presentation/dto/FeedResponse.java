package com.example.community.presentation.dto;

import com.example.community.application.FeedPage;

import java.util.List;

public record FeedResponse(
        List<FeedItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static FeedResponse from(FeedPage p) {
        return new FeedResponse(
                p.content().stream().map(FeedItemResponse::from).toList(),
                p.page(),
                p.size(),
                p.totalElements(),
                p.totalPages(),
                p.hasNext()
        );
    }
}
