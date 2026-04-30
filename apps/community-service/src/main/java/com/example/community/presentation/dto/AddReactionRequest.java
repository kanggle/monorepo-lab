package com.example.community.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddReactionRequest(
        @NotNull
        @Pattern(regexp = "HEART|FIRE|CLAP|WOW|SAD")
        String emojiCode
) {
}
