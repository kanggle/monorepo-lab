package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.application.exception.UnknownArtistAccountException;
import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.follow.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FollowArtistUseCase {

    private final FollowRepository followRepository;
    private final ArtistAccountChecker artistAccountChecker;

    public record FollowResult(String fanAccountId, String artistAccountId,
                               String tenantId, Instant followedAt) {}

    @Transactional
    public FollowResult execute(String artistAccountId, ActorContext actor) {
        if (artistAccountId.equals(actor.accountId())) {
            throw new SelfFollowForbiddenException();
        }
        // TASK-FAN-BE-045 AC-6. Until this check existed the target was stored
        // verbatim — no existence check, no format check — so the feed join
        // (posts.author_account_id ⋈ follows.artist_account_id) held only by
        // coincidence. Fail-closed: an unreachable artist-service refuses the
        // follow rather than admitting an unverified target.
        if (!artistAccountChecker.isArtistAccount(artistAccountId, actor.tenantId())) {
            throw new UnknownArtistAccountException(artistAccountId);
        }
        if (followRepository.exists(actor.accountId(), artistAccountId, actor.tenantId())) {
            throw new AlreadyFollowingException();
        }
        Follow saved = followRepository.save(
                Follow.create(actor.accountId(), artistAccountId, actor.tenantId()));
        return new FollowResult(saved.getFanAccountId(), saved.getArtistAccountId(),
                saved.getTenantId(), saved.getCreatedAt());
    }
}
