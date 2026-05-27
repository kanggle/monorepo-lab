package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;

/**
 * Static post-load helper shared by the community application layer.
 *
 * <p>Centralizes the 5-copy {@code postRepository.findById(postId, tenantId)
 * .orElseThrow(() -> new PostNotFoundException(postId))} pattern that appeared
 * across {@link PostAccessGuard}, {@code ChangePostStatusUseCase},
 * {@code GetPostUseCase}, {@code RemoveReactionUseCase}, and
 * {@code UpdatePostUseCase}. Mirrors the {@code artist-service} {@code ActorGuard}
 * precedent (TASK-FAN-BE-008 L6 de-duplication shape).
 *
 * <p>The repository is passed in (rather than wrapped behind a {@code @Component})
 * so callers reuse their already-injected {@link PostRepository} field and the
 * helper stays framework-free.
 */
final class PostLookup {

    private PostLookup() {
    }

    /**
     * Loads a {@link Post} within the actor's tenant, raising
     * {@link PostNotFoundException} (HTTP 404) when missing or cross-tenant.
     */
    static Post requireById(PostRepository repository, String postId, String tenantId) {
        return repository.findById(postId, tenantId)
                .orElseThrow(() -> new PostNotFoundException(postId));
    }
}
