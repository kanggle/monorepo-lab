package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdatePostUseCase {

    /**
     * Edit window after PUBLISHED. Past this point only operators can update
     * content (TASK-FAN-BE-002 § Edge Cases — PUBLISHED 본문 수정).
     */
    static final Duration EDIT_WINDOW = Duration.ofMinutes(5);

    private final PostRepository postRepository;
    private final PostMediaRefSerializer mediaRefSerializer;

    @Transactional
    public PostView execute(String postId, ActorContext actor,
                            String title, String body, List<String> mediaRefs) {
        Post post = postRepository.findById(postId, actor.tenantId())
                .orElseThrow(() -> new PostNotFoundException(postId));
        boolean isAuthor = post.getAuthorAccountId().equals(actor.accountId());
        if (!isAuthor && !actor.isOperator()) {
            throw new PermissionDeniedException("Only the author can update this post");
        }
        if (post.getStatus() == PostStatus.PUBLISHED && isAuthor && !actor.isOperator()) {
            Instant cutoff = post.getPublishedAt() == null
                    ? null
                    : post.getPublishedAt().plus(EDIT_WINDOW);
            if (cutoff != null && Instant.now().isAfter(cutoff)) {
                throw new EditWindowExpiredException(post.getId());
            }
        }
        String mediaRefsJson = mediaRefSerializer.serialize(mediaRefs);
        post.updateContent(title, body, mediaRefsJson);
        Post saved = postRepository.save(post);
        return PublishPostUseCase.view(saved, 0L, 0L);
    }

    /**
     * Thrown when the author tries to edit a PUBLISHED post past the
     * grace window. Mapped to HTTP 422 {@code EDIT_WINDOW_EXPIRED}.
     */
    public static class EditWindowExpiredException extends RuntimeException {
        public EditWindowExpiredException(String postId) {
            super("Edit window expired for post: " + postId);
        }
    }
}
