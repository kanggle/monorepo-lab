package com.example.fanplatform.membership.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * Cancel request body. {@code reason} is optional (≤ 200 chars).
 */
public record CancelRequest(
        @Size(max = 200, message = "reason must be <= 200 chars")
        String reason) {
}
