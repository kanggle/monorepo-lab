package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublishPostRequest(
        @NotNull PostType postType,
        @NotNull PostVisibility visibility,
        @Size(max = 200) String title,
        @NotNull @Size(min = 1, max = 10000) String body,
        List<@Size(max = 1024) String> mediaRefs
) {
}
