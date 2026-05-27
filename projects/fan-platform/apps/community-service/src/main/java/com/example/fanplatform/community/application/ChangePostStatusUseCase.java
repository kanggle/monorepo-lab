package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryEntry;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePostStatusUseCase {

    private final PostRepository postRepository;
    private final PostStatusHistoryRepository historyRepository;
    private final CommunityEventPublisher eventPublisher;

    @Transactional
    public void execute(String postId, PostStatus target, ActorContext actor, String reason) {
        Post post = PostLookup.requireById(postRepository, postId, actor.tenantId());
        ActorType actorType = resolveActor(post, actor);
        PostStatus previous = post.changeStatus(target, actorType);
        Post saved = postRepository.save(post);
        historyRepository.save(PostStatusHistoryEntry.record(
                saved.getId(), saved.getTenantId(), previous, target,
                actorType, actor.accountId(), reason));
        eventPublisher.publishPostStatusChanged(
                saved.getId(), saved.getTenantId(),
                previous, target, actor.accountId(), saved.getUpdatedAt());
    }

    /**
     * Resolves the actor type, fail-closed: callers who are neither the post's
     * author nor an OPERATOR are rejected with {@link PermissionDeniedException}.
     * Returning a sentinel here would force the caller to do a redundant
     * self-check; we throw eagerly instead so the access-control invariant lives
     * in one place.
     */
    private static ActorType resolveActor(Post post, ActorContext actor) {
        if (post.getAuthorAccountId().equals(actor.accountId())) {
            return ActorType.AUTHOR;
        }
        if (actor.isOperator()) {
            return ActorType.OPERATOR;
        }
        throw new PermissionDeniedException(
                "Only the author or an operator can change this post's status");
    }
}
