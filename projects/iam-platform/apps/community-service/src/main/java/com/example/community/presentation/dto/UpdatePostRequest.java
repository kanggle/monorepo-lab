package com.example.community.presentation.dto;

import java.util.List;

public record UpdatePostRequest(
        String title,
        String body,
        List<String> mediaUrls
) {
}
