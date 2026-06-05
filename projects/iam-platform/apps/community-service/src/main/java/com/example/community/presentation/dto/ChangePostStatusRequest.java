package com.example.community.presentation.dto;

import com.example.community.domain.post.status.PostStatus;
import jakarta.validation.constraints.NotNull;

public record ChangePostStatusRequest(
        @NotNull PostStatus status,
        String reason
) {
}
