package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserProfileSummaryResult;
import com.example.common.page.PageResult;

import java.util.List;

public record AdminUserListResponse(
        List<UserProfileSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AdminUserListResponse from(PageResult<UserProfileSummaryResult> pageResult) {
        List<UserProfileSummaryResponse> content = pageResult.content().stream()
                .map(UserProfileSummaryResponse::from)
                .toList();
        return new AdminUserListResponse(
                content,
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages()
        );
    }
}
