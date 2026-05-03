package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.NotFollowingException;
import com.example.fanplatform.community.domain.follow.Follow;
import com.example.fanplatform.community.domain.follow.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnfollowArtistUseCase {

    private final FollowRepository followRepository;

    @Transactional
    public void execute(String artistAccountId, ActorContext actor) {
        Follow follow = followRepository.find(actor.accountId(), artistAccountId, actor.tenantId())
                .orElseThrow(NotFollowingException::new);
        followRepository.delete(follow);
    }
}
