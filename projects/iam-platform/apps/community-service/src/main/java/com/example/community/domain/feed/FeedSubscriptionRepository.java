package com.example.community.domain.feed;

import java.util.Optional;

public interface FeedSubscriptionRepository {

    Optional<FeedSubscription> find(String fanAccountId, String artistAccountId);

    FeedSubscription save(FeedSubscription subscription);

    void delete(FeedSubscription subscription);

    boolean exists(String fanAccountId, String artistAccountId);
}
