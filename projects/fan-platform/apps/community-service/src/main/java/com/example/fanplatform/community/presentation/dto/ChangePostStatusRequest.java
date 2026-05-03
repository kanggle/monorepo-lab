package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.domain.post.status.PostStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangePostStatusRequest(
        @NotNull PostStatus status,
        @Size(max = 200) String reason
) {
}
