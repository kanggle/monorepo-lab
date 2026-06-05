package com.example.community.infrastructure.persistence;

import com.example.community.domain.feed.FeedSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedSubscriptionJpaRepository extends JpaRepository<FeedSubscription, FeedSubscription.FeedSubscriptionId> {

    Optional<FeedSubscription> findByFanAccountIdAndArtistAccountId(String fanAccountId, String artistAccountId);

    boolean existsByFanAccountIdAndArtistAccountId(String fanAccountId, String artistAccountId);
}
