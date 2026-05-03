package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.status.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convenience wrapper around {@link ChangePostStatusUseCase} so the controller
 * has a clean {@code DELETE /api/community/posts/{id}} entry point. Internally
 * routes through the state machine (any DELETE is a status transition to
 * {@link PostStatus#DELETED}).
 */
@Service
@RequiredArgsConstructor
public class DeletePostUseCase {

    private final ChangePostStatusUseCase changePostStatus;

    @Transactional
    public void execute(String postId, ActorContext actor, String reason) {
        changePostStatus.execute(postId, PostStatus.DELETED, actor, reason);
    }
}
