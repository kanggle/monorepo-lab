package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;

import java.time.Instant;

/**
 * What the feed cache is allowed to hold: the entitlement-INDEPENDENT projection of a feed
 * page (TASK-FAN-BE-046).
 *
 * <p>The distinction from {@link FeedItemView} is the whole point of this type. A
 * {@code FeedItemView} is a rendered answer for one actor at one moment — it carries
 * {@code locked}, and it carries {@code title}/{@code bodyPreview} only when that actor was
 * entitled at the moment it was built. A {@code FeedItemSnapshot} carries no decision: every
 * field here is a fact about the post, true for every reader, and none of it changes when a
 * membership starts, ends, expires, or is downgraded.
 *
 * <p><strong>Why this type exists.</strong> The cache used to store {@code FeedItemView}, so
 * an authorization decision was persisted for the TTL (5 minutes) and survived the entitlement
 * that justified it. Measured: after {@code POST /memberships/{id}/cancel} the detail route
 * returned 403 immediately while the feed kept serving {@code locked:false} with the gated
 * title and the first 200 characters of the body, and deleting the Redis key alone flipped it
 * to {@code locked:true}. Caching a projection instead makes that class of bug unreachable
 * rather than merely unlikely: there is no decision in the cache to go stale, in EITHER
 * direction (a fan who subscribes is also unlocked immediately — see
 * {@code GetFeedUseCase#applyEntitlement}).
 *
 * <p><strong>What this means for the cached bytes.</strong> {@code title} and
 * {@code bodyPreview} are stored unconditionally, including for posts the reader may not be
 * entitled to read. That is a deliberate trade: the alternative — storing them only when the
 * reader is entitled — is exactly the decision we are trying to keep out of the cache, and it
 * would make a cache hit unable to answer at all once entitlement changed. The exposure is
 * bounded: the key is per-account, the value never leaves the process without passing through
 * the gate, the content is a title plus 200 characters, and the entry expires in 5 minutes.
 * The gate itself is applied on the way out on both the hit and the miss path, by one method.
 */
public record FeedItemSnapshot(
        String postId,
        PostType postType,
        PostVisibility visibility,
        String authorAccountId,
        String title,
        String bodyPreview,
        long commentCount,
        long reactionCount,
        Instant publishedAt
) {
}
