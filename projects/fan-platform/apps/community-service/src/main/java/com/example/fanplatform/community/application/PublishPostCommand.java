package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;

import java.util.List;

public record PublishPostCommand(
        ActorContext actor,
        PostType postType,
        PostVisibility visibility,
        String title,
        String body,
        List<String> mediaRefs
) {
}
