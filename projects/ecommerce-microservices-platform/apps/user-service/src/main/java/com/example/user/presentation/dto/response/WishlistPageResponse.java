package com.example.user.presentation.dto.response;

import com.example.user.application.result.WishlistItemResult;
import com.example.common.page.PageResult;

import java.util.List;

public record WishlistPageResponse(
        List<WishlistItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static WishlistPageResponse from(PageResult<WishlistItemResult> result) {
        List<WishlistItemResponse> content = result.content().stream()
                .map(WishlistItemResponse::from)
                .toList();
        return new WishlistPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
