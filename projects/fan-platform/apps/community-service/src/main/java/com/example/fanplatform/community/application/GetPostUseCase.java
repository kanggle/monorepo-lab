package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Also resolves the caller's own reaction on the post ({@code myReaction},
 * TASK-FAN-BE-051) via the same {@link ReactionRepository#find} lookup
 * {@code AddReactionUseCase}/{@code RemoveReactionUseCase} already use for their
 * upsert decision — the data was already reachable, only the read was missing.
 */
@Service
@RequiredArgsConstructor
public class GetPostUseCase {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final PostAccessGuard postAccessGuard;

    @Transactional(readOnly = true)
    public PostView execute(String postId, ActorContext actor) {
        Post post = PostLookup.requireById(postRepository, postId, actor.tenantId());
        if (post.getStatus() == PostStatus.DELETED) {
            throw new PostNotFoundException(postId);
        }
        boolean accessible = actor.owns(post.getAuthorAccountId());
        if (post.getStatus() == PostStatus.HIDDEN && !accessible) {
            throw new PostNotFoundException(postId);
        }
        if (post.getStatus() == PostStatus.DRAFT && !accessible) {
            throw new PostNotFoundException(postId);
        }
        postAccessGuard.ensureVisibilityAccessible(post, actor);

        long commentCount = commentRepository.countByPostId(postId, actor.tenantId());
        long reactionCount = reactionRepository.countByPostId(postId, actor.tenantId());
        // Scoped to (postId, caller's own accountId, caller's own tenantId) — the same
        // three-part key AddReactionUseCase/RemoveReactionUseCase upsert against. Dropping
        // the accountId half would answer "did anyone react", which leaks another fan's
        // reaction into this caller's answer (AC-4).
        Optional<Reaction> myReaction = reactionRepository.find(postId, actor.accountId(), actor.tenantId());
        ReactionType myReactionType = myReaction.map(Reaction::getReactionType).orElse(null);
        return PublishPostUseCase.view(post, commentCount, reactionCount, myReactionType);
    }
}
