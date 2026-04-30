package com.example.community.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotNull @Size(min = 1, max = 2000) String body
) {
}
