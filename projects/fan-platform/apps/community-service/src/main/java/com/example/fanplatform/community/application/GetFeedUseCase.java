package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Follow-based feed. v1 is fan-out-on-read with a Redis read-through cache:
 *
 * <ol>
 *   <li>look up the cached page; on hit skip the database entirely.</li>
 *   <li>on miss query Postgres ({@link PostRepository#findFeedForFan}), batch
 *       comment/reaction counts, build the page, write it back to cache.</li>
 *   <li>on Redis unavailability fall through to Postgres directly (fail-open
 *       per rules/traits/integration-heavy.md I3).</li>
 *   <li><strong>either way</strong>, apply the membership gate on the way out
 *       ({@link #applyEntitlement}).</li>
 * </ol>
 *
 * <p><strong>What is cached, and what is not (TASK-FAN-BE-046).</strong> The cache holds a
 * {@link FeedItemSnapshot} page — facts about posts — and never a {@code locked} decision.
 * Step 4 is not an optimisation and not a second check: it is the ONLY place the gate is
 * evaluated, and it runs on the hit path and the miss path alike. Before this, the rendered
 * {@link FeedItemView} was cached, so the authorization decision outlived the entitlement that
 * produced it for up to the TTL — measured: cancel a membership and the detail route returns
 * 403 at once while the feed still serves the gated title and body preview.
 *
 * <p><strong>Invalidation</strong> is still TTL-only (5 minutes — see {@link FeedCache}), and
 * that remains correct for what TTL is actually for: <em>freshness</em>. A new post or a new
 * follow becomes visible after at most that window. Entitlement is deliberately NOT handled by
 * invalidation — an event-driven DEL would make the lock depend on an event arriving, and a
 * lost or late event fails OPEN. (This repository has a worked precedent for events silently
 * not arriving at all: {@code TASK-MONO-511}.) Recomputing fails CLOSED instead, because
 * {@link MembershipChecker} does.
 *
 * <p><strong>Cost.</strong> The gate needs at most one {@link MembershipChecker} call per
 * distinct required tier on the page — at most two, regardless of page size, and zero when the
 * page holds nothing gated. That is not a new cost so much as a relocated one: the previous
 * miss path called the checker once per gated post (up to 50 per page, un-memoized), so the
 * miss path gets cheaper here and only the hit path gains calls it did not make before.
 */
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
    public PageResult<FeedItemView> execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        // 1) Read-through: on a hit the database is not touched at all.
        Optional<PageResult<FeedItemSnapshot>> cached = feedCache.readPage(
                actor.tenantId(), actor.accountId(), safePage, safeSize);

        PageResult<FeedItemSnapshot> snapshot;
        if (cached.isPresent()) {
            snapshot = cached.get();
        } else {
            // 2) Miss: query Postgres + build the page.
            snapshot = queryAndBuild(actor, safePage, safeSize);
            // 3) Best-effort cache write. The repository swallows + counts
            //    failures so the caller's response is unaffected.
            feedCache.cachePage(actor.tenantId(), actor.accountId(), safePage, safeSize, snapshot);
        }

        // 4) Gate LAST, on both paths — the cache never answers this question.
        return applyEntitlement(snapshot, actor);
    }

    private PageResult<FeedItemSnapshot> queryAndBuild(ActorContext actor, int safePage, int safeSize) {
        PageResult<Post> posts = postRepository.findFeedForFan(
                actor.accountId(), actor.tenantId(), safePage, safeSize);

        List<String> postIds = posts.content().stream().map(Post::getId).toList();
        Map<String, Long> commentCounts = commentRepository.countsByPostIds(postIds, actor.tenantId());
        Map<String, Long> reactionCounts = reactionRepository.countsByPostIds(postIds, actor.tenantId());

        return posts.map(post -> new FeedItemSnapshot(
                post.getId(),
                post.getPostType(),
                post.getVisibility(),
                post.getAuthorAccountId(),
                post.getTitle(),
                preview(post.getBody()),
                commentCounts.getOrDefault(post.getId(), 0L),
                reactionCounts.getOrDefault(post.getId(), 0L),
                post.getPublishedAt()));
    }

    /**
     * Renders the cached projection for one actor, right now. Locked items surrender their
     * title and body preview here — they are present in the snapshot precisely so that this
     * decision can be made (and re-made) at read time rather than baked in at cache-fill time.
     */
    private PageResult<FeedItemView> applyEntitlement(PageResult<FeedItemSnapshot> snapshot,
                                                      ActorContext actor) {
        TierAccess access = new TierAccess(actor);
        return snapshot.map(item -> {
            boolean locked = isLocked(item, actor, access);
            return new FeedItemView(
                    item.postId(),
                    item.postType(),
                    item.visibility(),
                    item.authorAccountId(),
                    locked ? null : item.title(),
                    locked ? null : item.bodyPreview(),
                    item.commentCount(),
                    item.reactionCount(),
                    item.publishedAt(),
                    locked);
        });
    }

    private boolean isLocked(FeedItemSnapshot item, ActorContext actor, TierAccess access) {
        if (actor.owns(item.authorAccountId())) {
            return false;
        }
        if (item.visibility() == PostVisibility.PUBLIC) {
            return false;
        }
        // membership-service enforces the gate (mirrors PostAccessGuard FAN-BE-010).
        // The checker is fail-closed: any downstream/auth error returns false (locked).
        // Tier hierarchy (PREMIUM ⊇ MEMBERS_ONLY) is resolved server-side in
        // membership-service — the client passes the required tier only.
        return !access.allows(item.visibility());
    }

    /**
     * One membership check per distinct required tier, for the duration of one request.
     *
     * <p>Without this, gating on the way out would mean one remote call per gated item — the
     * shape the previous code already had on its miss path, and the reason moving the gate
     * here does not cost what it looks like it costs. The memo is deliberately request-scoped:
     * caching an entitlement answer for longer would put the decision back into a cache, which
     * is the defect this class is fixing.
     */
    private final class TierAccess {
        private final ActorContext actor;
        private final Map<PostVisibility, Boolean> memo = new EnumMap<>(PostVisibility.class);

        private TierAccess(ActorContext actor) {
            this.actor = actor;
        }

        private boolean allows(PostVisibility required) {
            return memo.computeIfAbsent(required, tier ->
                    membershipChecker.hasAccess(actor.accountId(), tier.name(), actor.tenantId()));
        }
    }

    private static String preview(String body) {
        if (body == null) return null;
        if (body.length() <= BODY_PREVIEW_MAX) return body;
        return body.substring(0, BODY_PREVIEW_MAX);
    }
}
