package com.example.user.presentation.dto.response;

import com.example.user.application.result.WishlistPageResult;

import java.util.List;

public record WishlistPageResponse(
        List<WishlistItemResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static WishlistPageResponse from(WishlistPageResult result) {
        List<WishlistItemResponse> content = result.content().stream()
                .map(WishlistItemResponse::from)
                .toList();
        return new WishlistPageResponse(content, result.page(), result.size(), result.totalElements());
    }
}
