package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
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

    public record FollowResult(String fanAccountId, String artistAccountId,
                               String tenantId, Instant followedAt) {}

    @Transactional
    public FollowResult execute(String artistAccountId, ActorContext actor) {
        if (artistAccountId.equals(actor.accountId())) {
            throw new SelfFollowForbiddenException();
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
