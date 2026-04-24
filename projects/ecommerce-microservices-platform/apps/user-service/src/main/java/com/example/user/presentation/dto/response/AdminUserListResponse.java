package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserListPageResult;

import java.util.List;

public record AdminUserListResponse(
        List<UserProfileSummaryResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static AdminUserListResponse from(UserListPageResult pageResult) {
        List<UserProfileSummaryResponse> content = pageResult.content().stream()
                .map(UserProfileSummaryResponse::from)
                .toList();
        return new AdminUserListResponse(
                content,
                pageResult.pageNumber(),
                pageResult.pageSize(),
                pageResult.totalElements()
        );
    }
}
