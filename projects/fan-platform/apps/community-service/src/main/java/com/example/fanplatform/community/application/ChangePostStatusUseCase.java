package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
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
        Post post = postRepository.findById(postId, actor.tenantId())
                .orElseThrow(() -> new PostNotFoundException(postId));
        ActorType actorType = resolveActor(post, actor);
        if (actorType == ActorType.AUTHOR
                && !post.getAuthorAccountId().equals(actor.accountId())) {
            throw new PermissionDeniedException("Only the author can change this post's status");
        }
        PostStatus previous = post.changeStatus(target, actorType);
        Post saved = postRepository.save(post);
        historyRepository.save(PostStatusHistoryEntry.record(
                saved.getId(), saved.getTenantId(), previous, target,
                actorType, actor.accountId(), reason));
        eventPublisher.publishPostStatusChanged(
                saved.getId(), saved.getTenantId(),
                previous, target, actor.accountId(), saved.getUpdatedAt());
    }

    private static ActorType resolveActor(Post post, ActorContext actor) {
        if (actor.isOperator()) return ActorType.OPERATOR;
        if (post.getAuthorAccountId().equals(actor.accountId())) return ActorType.AUTHOR;
        return ActorType.AUTHOR; // will fail the AUTHOR-self check above
    }
}
