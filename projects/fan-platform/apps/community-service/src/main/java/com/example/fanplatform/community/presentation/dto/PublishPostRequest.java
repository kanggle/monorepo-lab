package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublishPostRequest(
        @NotNull PostType postType,
        @NotNull PostVisibility visibility,
        @Size(max = 200) String title,
        @NotNull @Size(min = 1, max = 10000) String body,
        @Size(max = MediaRefRules.MAX_COUNT)
        List<@NotNull @Size(max = 1024) @Pattern(regexp = MediaRefRules.HTTPS_URL) String> mediaRefs
) {
}
