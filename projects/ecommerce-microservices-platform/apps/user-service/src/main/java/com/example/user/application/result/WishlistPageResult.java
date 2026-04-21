package com.example.user.application.result;

import java.util.List;

public record WishlistPageResult(
        List<WishlistItemResult> content,
        int page,
        int size,
        long totalElements
) {
}
