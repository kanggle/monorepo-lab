package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.domain.reaction.ReactionType;
import jakarta.validation.constraints.NotNull;

public record AddReactionRequest(
        @NotNull ReactionType reactionType
) {
}
