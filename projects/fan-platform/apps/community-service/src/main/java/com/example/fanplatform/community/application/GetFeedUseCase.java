package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Follow-based feed. v1 is fan-out-on-read with a Redis read-through cache:
 *
 * <ol>
 *   <li>look up cached page; on hit return immediately (zero DB round-trips).</li>
 *   <li>on miss query Postgres ({@link PostRepository#findFeedForFan}), batch
 *       comment/reaction counts, build the page, write the result back to
 *       cache, return it.</li>
 *   <li>on Redis unavailability fall through to Postgres directly (fail-open
 *       per rules/traits/integration-heavy.md I3).</li>
 * </ol>
 *
 * <p><strong>Invalidation</strong> is TTL-only (5 minutes — see
 * {@link FeedCache}). A new post or follow/unfollow becomes visible
 * to the fan after at most that window. v2 may add explicit invalidation by
 * subscribing to {@code community.post.published} / a future
 * {@code community.follow.changed} topic if sub-minute freshness becomes a
 * requirement.
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
    private final FeedCache feedCache;

    @Transactional(readOnly = true)
    public FeedPage execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        // 1) Read-through: fast path — return immediately from Redis on hit.
        Optional<FeedPage> cached = feedCache.readPage(
                actor.tenantId(), actor.accountId(), safePage, safeSize);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) Miss: query Postgres + build the page.
        FeedPage built = queryAndBuild(actor, safePage, safeSize);

        // 3) Best-effort cache write of the hydrated page. The repository
        //    swallows + counts failures so the caller's response is unaffected.
        feedCache.cachePage(actor.tenantId(), actor.accountId(), safePage, safeSize, built);
        return built;
    }

    private FeedPage queryAndBuild(ActorContext actor, int safePage, int safeSize) {
        Page<Post> posts = postRepository.findFeedForFan(
                actor.accountId(), actor.tenantId(),
                PageRequest.of(safePage, safeSize));

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
