package com.example.fanplatform.community.application;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.PostVisibility;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Follow-based feed. v1 is fan-out-on-read with a Redis read-through cache:
 *
 * <ol>
 *   <li>look up the cached page; on a hit the page query is skipped.</li>
 *   <li>on miss query Postgres ({@link FeedReader#buildPage}: the page plus batched
 *       comment/reaction counts), write the page back to cache.</li>
 *   <li>on Redis unavailability fall through to Postgres directly (fail-open
 *       per rules/traits/integration-heavy.md I3).</li>
 *   <li>on a hit, overlay live comment/reaction counts ({@link #overlayLiveCounts}).</li>
 *   <li><strong>either way</strong>, apply the membership gate on the way out
 *       ({@link #render}).</li>
 * </ol>
 *
 * <p><strong>What is cached, and what is not (TASK-FAN-BE-046).</strong> The cache holds a
 * {@link FeedItemSnapshot} page — facts about posts — and never a {@code locked} decision.
 * The gate is not an optimisation and not a second check: it is the ONLY place the gate is
 * evaluated, and it runs on the hit path and the miss path alike. Before this, the rendered
 * {@link FeedItemView} was cached, so the authorization decision outlived the entitlement that
 * produced it for up to the TTL — measured: cancel a membership and the detail route returns
 * 403 at once while the feed still serves the gated title and body preview.
 *
 * <p><strong>Counts are not served from the cache either (TASK-FAN-BE-053).</strong> The snapshot
 * still carries {@code commentCount}/{@code reactionCount}, but on a hit they are replaced by the
 * values read now. Measured before this: right after a reaction PUT the detail route said
 * {@code reactionCount 1} while the feed said {@code 0}, with 297 seconds left on the Redis key.
 * The miss path needs no overlay — it read the counts in this request — so a page costs 2
 * aggregate queries on either path, never 4. If the overlay query fails, the snapshot's counts
 * (at most one TTL old) are served instead and {@code community_feed_live_counts_fallback_total}
 * is incremented: counts are display information, and a 500 over them would empty the whole feed.
 *
 * <p><strong>This method is deliberately not {@code @Transactional}.</strong> A query that fails
 * inside a JPA transaction marks it rollback-only, so a surrounding transaction would turn the
 * caught overlay failure back into a 500 at commit. The reads are in {@link FeedReader}, each in
 * its own read-only transaction, and the remote membership calls run outside all of them.
 *
 * <p><strong>Invalidation</strong> is still TTL-only (5 minutes — see {@link FeedCache}), and
 * that remains correct for what TTL is actually for: <em>composition</em>. A new post or a new
 * follow becomes visible after at most that window. Neither entitlement nor counts are handled by
 * invalidation — an event-driven DEL would make the answer depend on an event arriving, and a
 * lost or late event leaves it silently wrong (for entitlement: OPEN). (This repository has a
 * worked precedent for events silently not arriving at all: {@code TASK-MONO-511}.) For counts
 * there is a second reason: the key is {@code account × page × size}, so one reaction would have
 * to delete every page of every follower that shows the post, and there is no reverse index to
 * find them. Recomputing at read time needs neither.
 *
 * <p><strong>Cost.</strong> The gate needs at most one {@link MembershipChecker} call per
 * distinct required tier on the page — at most two, regardless of page size, and zero when the
 * page holds nothing gated. The count overlay is two grouped queries per non-empty cached page,
 * and none for an empty one.
 */
@Slf4j
@Service
public class GetFeedUseCase {

    private static final int MAX_SIZE = 50;

    private final FeedReader feedReader;
    private final MembershipChecker membershipChecker;
    private final FeedCache feedCache;
    private final Counter liveCountsFallback;

    public GetFeedUseCase(FeedReader feedReader,
                          MembershipChecker membershipChecker,
                          FeedCache feedCache,
                          MeterRegistry meterRegistry) {
        this.feedReader = feedReader;
        this.membershipChecker = membershipChecker;
        this.feedCache = feedCache;
        this.liveCountsFallback = Counter.builder("community_feed_live_counts_fallback_total")
                .description("Feed cache hits served with the cached (up to TTL-old) comment/reaction "
                        + "counts because the live count query failed.")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true) // MUTATION — CI-only red-before proof, do not merge
    public PageResult<FeedItemView> execute(ActorContext actor, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        // 1) Read-through: on a hit the page query is skipped.
        Optional<PageResult<FeedItemSnapshot>> cached = feedCache.readPage(
                actor.tenantId(), actor.accountId(), safePage, safeSize);

        if (cached.isPresent()) {
            // 4) Hit: the page composition is up to one TTL old, the counts must not be.
            return render(overlayLiveCounts(cached.get(), actor), actor);
        }

        // 2) Miss: query Postgres + build the page. Its counts were read just now.
        PageResult<FeedItemSnapshot> snapshot = feedReader.buildPage(
                actor.accountId(), actor.tenantId(), safePage, safeSize);
        // 3) Best-effort cache write. The repository swallows + counts
        //    failures so the caller's response is unaffected.
        feedCache.cachePage(actor.tenantId(), actor.accountId(), safePage, safeSize, snapshot);

        // 5) Gate LAST, on both paths — the cache never answers this question.
        return render(snapshot, actor);
    }

    /**
     * Replaces the cached counts with the values as of now. On failure the cached counts are kept
     * (they are at most one TTL old) — the page itself was already in hand, and losing it over a
     * display number is the worse outcome. Only database failures are absorbed; anything else is a
     * bug and propagates.
     */
    private PageResult<FeedItemSnapshot> overlayLiveCounts(PageResult<FeedItemSnapshot> snapshot,
                                                           ActorContext actor) {
        if (snapshot.content().isEmpty()) {
            return snapshot;
        }
        List<String> postIds = snapshot.content().stream().map(FeedItemSnapshot::postId).toList();
        FeedReader.LiveCounts live;
        try {
            live = feedReader.liveCounts(postIds, actor.tenantId());
        } catch (DataAccessException | TransactionException e) {
            liveCountsFallback.increment();
            log.warn("feed live counts unavailable, serving cached counts: tenant={} account={} posts={} reason={}",
                    actor.tenantId(), actor.accountId(), postIds.size(), e.getMessage());
            return snapshot;
        }
        return snapshot.map(item -> new FeedItemSnapshot(
                item.postId(),
                item.postType(),
                item.visibility(),
                item.authorAccountId(),
                item.title(),
                item.bodyPreview(),
                item.mediaRefs(),
                live.commentsOf(item.postId()),
                live.reactionsOf(item.postId()),
                item.publishedAt()));
    }

    /**
     * Renders the projection for one actor, right now. Locked items surrender their
     * title, body preview and media refs here — they are present in the snapshot precisely so that
     * this decision can be made (and re-made) at read time rather than baked in at cache-fill time.
     *
     * <p>🔴 {@code mediaRefs} goes through the same {@code locked} ternary as the title
     * (TASK-MONO-679). A photo URL is not metadata: whoever holds it can fetch the photo.
     */
    private PageResult<FeedItemView> render(PageResult<FeedItemSnapshot> snapshot, ActorContext actor) {
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
                    locked || item.mediaRefs() == null ? List.of() : item.mediaRefs(),
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
}
