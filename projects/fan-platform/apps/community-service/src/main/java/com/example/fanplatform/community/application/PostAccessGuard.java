package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralizes visibility + membership gating for published posts.
 *
 * <p>Visibility tiers (TASK-FAN-BE-002 § In Scope #3):
 * <ul>
 *   <li>{@code PUBLIC} — any authenticated caller within the tenant.</li>
 *   <li>{@code MEMBERS_ONLY} — author + members verified by
 *       {@link MembershipChecker}.</li>
 *   <li>{@code PREMIUM} — author + premium members verified by
 *       {@link MembershipChecker} (TASK-FAN-BE-010 hard fail-close; required tier
 *       {@code PREMIUM}). Tier hierarchy is resolved in membership-service.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PostAccessGuard {

    private final PostRepository postRepository;
    private final MembershipChecker membershipChecker;

    /**
     * Loads a post by id within the actor's tenant and asserts the actor may
     * interact with it as a published post.
     *
     * @return the loaded {@link Post} when access is granted
     * @throws PostNotFoundException       missing OR cross-tenant OR not PUBLISHED
     * @throws MembershipRequiredException MEMBERS_ONLY/PREMIUM gate fails
     */
    public Post requirePublishedAccess(String postId, ActorContext actor) {
        Post post = PostLookup.requireById(postRepository, postId, actor.tenantId());
        if (post.getStatus() != PostStatus.PUBLISHED) {
            // Hide the existence of DRAFT/HIDDEN/DELETED from non-author readers.
            if (!actor.owns(post.getAuthorAccountId())) {
                throw new PostNotFoundException(postId);
            }
        }
        ensureVisibilityAccessible(post, actor);
        return post;
    }

    public void ensureVisibilityAccessible(Post post, ActorContext actor) {
        if (actor.owns(post.getAuthorAccountId())) {
            return;
        }
        switch (post.getVisibility()) {
            case PUBLIC -> {
                // open to all authenticated callers within the tenant
            }
            case MEMBERS_ONLY -> {
                if (!membershipChecker.hasAccess(actor.accountId(),
                        PostVisibility.MEMBERS_ONLY.name(), actor.tenantId())) {
                    throw new MembershipRequiredException(PostVisibility.MEMBERS_ONLY);
                }
            }
            case PREMIUM -> {
                // membership-service now enforces the gate (TASK-FAN-BE-010). The
                // checker is fail-closed: any downstream/auth error denies. The
                // required tier passed is PREMIUM; tier hierarchy (PREMIUM ⊇
                // MEMBERS_ONLY) is resolved server-side in membership-service.
                if (!membershipChecker.hasAccess(actor.accountId(),
                        PostVisibility.PREMIUM.name(), actor.tenantId())) {
                    throw new MembershipRequiredException(PostVisibility.PREMIUM);
                }
            }
        }
    }
}
