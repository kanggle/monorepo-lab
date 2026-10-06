package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;

import java.time.Instant;
import java.util.List;

public record PostView(
        String postId,
        String tenantId,
        PostType postType,
        PostVisibility visibility,
        PostStatus status,
        String authorAccountId,
        String title,
        String body,
        /** Absolute https URLs, never {@code null} (TASK-MONO-679). */
        List<String> mediaRefs,
        long commentCount,
        long reactionCount,
        /**
         * The caller's own reaction on this post, or {@code null} if they have not
         * reacted (TASK-FAN-BE-051). Populated only by {@link GetPostUseCase} —
         * every other producer of this view (publish / update / mine) fixes this to
         * {@code null}; see {@code community-api.md} § "Why PostView is not shared
         * for this field".
         */
        ReactionType myReaction,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
