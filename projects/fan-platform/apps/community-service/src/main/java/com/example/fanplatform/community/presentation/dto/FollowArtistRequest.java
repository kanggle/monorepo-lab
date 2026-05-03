package com.example.fanplatform.community.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowArtistRequest(
        @NotBlank @Size(max = 36) String artistAccountId
) {
}
