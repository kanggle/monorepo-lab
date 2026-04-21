package com.example.user.application.result;

import java.util.List;

public record UserListPageResult(
        List<UserProfileSummaryResult> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {}
