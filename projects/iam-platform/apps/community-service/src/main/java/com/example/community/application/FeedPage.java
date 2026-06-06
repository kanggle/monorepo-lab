package com.example.community.application;

import java.util.List;

public record FeedPage(
        List<FeedItemView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
