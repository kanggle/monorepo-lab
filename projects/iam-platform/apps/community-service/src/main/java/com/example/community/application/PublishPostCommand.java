package com.example.community.application;

import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;

import java.util.List;

public record PublishPostCommand(
        ActorContext actor,
        PostType type,
        PostVisibility visibility,
        String title,
        String body,
        List<String> mediaUrls
) {
}
