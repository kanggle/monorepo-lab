package com.example.community.presentation.dto;

public record ReactionResponse(
        String postId,
        String emojiCode,
        long totalReactions
) {
}
