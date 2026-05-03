package com.example.fanplatform.community.presentation.dto;

import com.example.fanplatform.community.application.AddReactionUseCase;

public record ReactionResponse(
        String postId,
        String reactionType,
        long totalReactions
) {
    public static ReactionResponse from(AddReactionUseCase.ReactionResult r) {
        return new ReactionResponse(
                r.postId(), r.reactionType().name(), r.totalReactions());
    }
}
