package com.example.fanplatform.community.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePostRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String body,
        @Size(max = MediaRefRules.MAX_COUNT)
        List<@NotNull @Size(max = 1024) @Pattern(regexp = MediaRefRules.HTTPS_URL) String> mediaRefs
) {
}
