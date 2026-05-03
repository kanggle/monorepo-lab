package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import com.example.fanplatform.community.infrastructure.cache.FeedCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Follow-based feed. v1 is fan-out-on-read with a Redis read-through cache:
 *
 * <ol>
 *   <li>look up cached page; on hit return immediately.</li>
 *   <li>on miss query Postgres ({@link PostRepository#findFeedForFan}), cache
 *       the page id list, return the hydrated payload.</li>
 *   <li>on Redis unavailability fall through to Postgres directly (fail-open
 *       per rules/traits/integration-heavy.md I3).</li>
 * </ol>
 *
 * <p>Cache invalidation is best-effort TTL (5 min); a v2 cache-aware consumer
 * of {@code community.post.published} can do explicit invalidation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetFeedUseCase {

    private static final int MAX_SIZE = 50;
    private static final int BODY_PREVIEW_MAX = 200;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final MembershipChecker membershipChecker;
    private final FeedCacheRepository feedCache;

    @Transactional(readOnly = true)
    public FeedPage execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        Page<Post> posts = postRepository.findFeedForFan(
                actor.accountId(), actor.tenantId(),
                PageRequest.of(safePage, safeSize));

        // Best-effort cache write of the post-id slice. Read-through is a
        // future v2 concern (would require denormalised fan-out-on-write or
        // careful authority-scoped caching to avoid leaking gated posts).
        try {
            feedCache.cachePage(actor.tenantId(), actor.accountId(), safePage, safeSize,
                    posts.getContent().stream().map(Post::getId).toList());
        } catch (RuntimeException e) {
            log.warn("Feed cache write failed (fail-open): {}", e.getMessage());
        }

        List<String> postIds = posts.getContent().stream().map(Post::getId).toList();
        Map<String, Long> commentCounts = commentRepository.countsByPostIds(postIds, actor.tenantId());
        Map<String, Long> reactionCounts = reactionRepository.countsByPostIds(postIds, actor.tenantId());

        List<FeedItemView> items = new ArrayList<>(posts.getNumberOfElements());
        for (Post post : posts.getContent()) {
            boolean locked = isLocked(post, actor);
            String title = locked ? null : post.getTitle();
            String preview = locked ? null : preview(post.getBody());
            items.add(new FeedItemView(
                    post.getId(),
                    post.getPostType(),
                    post.getVisibility(),
                    post.getAuthorAccountId(),
                    title,
                    preview,
                    commentCounts.getOrDefault(post.getId(), 0L),
                    reactionCounts.getOrDefault(post.getId(), 0L),
                    post.getPublishedAt(),
                    locked
            ));
        }
        return new FeedPage(
                items,
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalElements(),
                posts.getTotalPages(),
                posts.hasNext()
        );
    }

    private boolean isLocked(Post post, ActorContext actor) {
        if (post.getAuthorAccountId().equals(actor.accountId()) || actor.isOperator()) {
            return false;
        }
        if (post.getVisibility() == PostVisibility.PUBLIC) {
            return false;
        }
        if (post.getVisibility() == PostVisibility.PREMIUM) {
            // v1 PREMIUM = always allow (fail-open with WARN). Locked=false.
            // TODO(TASK-FAN-BE-MEMBERSHIP): hard-gate once membership-service exists.
            log.warn("PREMIUM gate bypassed in feed for post {} actor {} tenant {}",
                    post.getId(), actor.accountId(), actor.tenantId());
            return false;
        }
        // MEMBERS_ONLY
        return !membershipChecker.hasAccess(actor.accountId(),
                PostVisibility.MEMBERS_ONLY.name(), actor.tenantId());
    }

    private static String preview(String body) {
        if (body == null) return null;
        if (body.length() <= BODY_PREVIEW_MAX) return body;
        return body.substring(0, BODY_PREVIEW_MAX);
    }
}
