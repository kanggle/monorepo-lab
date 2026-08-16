package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.follow.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Am I following this artist?" — TASK-FAN-BE-049.
 *
 * <p>The predicate already existed ({@link FollowRepository#exists}); what was
 * missing was a way to <em>ask</em> it. Until this landed, community exposed
 * follow as write-only (POST/DELETE), so a screen could act on the relationship
 * but never render it — which is the defect TASK-FAN-FE-017 describes.
 *
 * <p><b>Deliberately does not check that the artist exists.</b> The sibling
 * {@link FollowArtistUseCase} is fail-closed against artist-service by
 * {@code ADR-004}: an unreachable validator refuses the follow, because a wrong
 * target there corrupts the feed join. A read of the caller's own row has no such
 * consequence, and inheriting the fail-closed behaviour would mean an
 * artist-service outage blanks the follow button on every artist page. See
 * {@code community-api.md} § "This read does not validate that the artist exists"
 * — the second reason is that not validating keeps this endpoint from becoming the
 * existence oracle {@code ADR-004} spends its § Drivers avoiding.
 *
 * <p>Scoped to the actor's own account AND tenant. Both halves matter: dropping
 * the account would answer "does anyone in this tenant follow the artist", which
 * is a different — and leaking — question.
 */
@Service
@RequiredArgsConstructor
public class IsFollowingArtistUseCase {

    private final FollowRepository followRepository;

    public record FollowStatus(String artistAccountId, boolean following) {}

    @Transactional(readOnly = true)
    public FollowStatus execute(String artistAccountId, ActorContext actor) {
        boolean following = followRepository.exists(
                actor.accountId(), artistAccountId, actor.tenantId());
        return new FollowStatus(artistAccountId, following);
    }
}
