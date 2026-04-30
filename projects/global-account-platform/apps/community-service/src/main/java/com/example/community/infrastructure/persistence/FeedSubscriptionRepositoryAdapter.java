package com.example.community.infrastructure.persistence;

import com.example.community.domain.feed.FeedSubscription;
import com.example.community.domain.feed.FeedSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FeedSubscriptionRepositoryAdapter implements FeedSubscriptionRepository {

    private final FeedSubscriptionJpaRepository jpaRepository;

    @Override
    public Optional<FeedSubscription> find(String fanAccountId, String artistAccountId) {
        return jpaRepository.findByFanAccountIdAndArtistAccountId(fanAccountId, artistAccountId);
    }

    @Override
    public FeedSubscription save(FeedSubscription subscription) {
        return jpaRepository.save(subscription);
    }

    @Override
    public void delete(FeedSubscription subscription) {
        jpaRepository.delete(subscription);
    }

    @Override
    public boolean exists(String fanAccountId, String artistAccountId) {
        return jpaRepository.existsByFanAccountIdAndArtistAccountId(fanAccountId, artistAccountId);
    }
}
