package com.example.fanplatform.community.presentation.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePostRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String body,
        List<@Size(max = 1024) String> mediaRefs
) {
}
