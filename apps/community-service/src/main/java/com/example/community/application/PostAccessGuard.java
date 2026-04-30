package com.example.community.application;

import com.example.community.application.exception.MembershipRequiredException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared access-control helper for use-cases that operate on already-published posts.
 *
 * <p>Centralizes the duplicated nine-line block previously inlined in {@code AddCommentUseCase}
 * and {@code AddReactionUseCase}: load the post, ensure it is {@link PostStatus#PUBLISHED}, and
 * (for {@link PostVisibility#MEMBERS_ONLY} posts) verify the actor either owns the post or
 * holds the required membership plan.
 *
 * <p>Package-private by design — only application-layer use-cases in this package consume it.
 */
@Component
@RequiredArgsConstructor
class PostAccessGuard {

    private final PostRepository postRepository;
    private final ContentAccessChecker contentAccessChecker;

    /**
     * Loads a post and asserts the actor is allowed to interact with it as a published post.
     *
     * @param postId post identifier
     * @param actor  caller context (account id and roles)
     * @return the loaded {@link Post} when access is granted
     * @throws PostNotFoundException       if the post does not exist or is not in PUBLISHED state
     * @throws MembershipRequiredException if the post is MEMBERS_ONLY and the non-author actor
     *                                     does not hold the required plan (fail-closed)
     */
    Post requirePublishedAccess(String postId, ActorContext actor) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new PostNotFoundException(postId);
        }
        if (post.getVisibility() == PostVisibility.MEMBERS_ONLY
                && !post.getAuthorAccountId().equals(actor.accountId())) {
            boolean allowed = contentAccessChecker.check(
                    actor.accountId(), GetPostUseCase.REQUIRED_PLAN_LEVEL);
            if (!allowed) {
                throw new MembershipRequiredException();
            }
        }
        return post;
    }
}
