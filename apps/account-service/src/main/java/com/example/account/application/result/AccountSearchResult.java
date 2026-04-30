package com.example.account.application.result;

import java.time.Instant;
import java.util.List;

public record AccountSearchResult(
        List<Item> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {
    public record Item(
            String id,
            String email,
            String status,
            Instant createdAt
    ) {}
}
