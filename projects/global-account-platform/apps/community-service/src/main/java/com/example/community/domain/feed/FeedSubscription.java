package com.example.community.domain.feed;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "feed_subscriptions")
@IdClass(FeedSubscription.FeedSubscriptionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedSubscription {

    @Id
    @Column(name = "fan_account_id", length = 36, nullable = false)
    private String fanAccountId;

    @Id
    @Column(name = "artist_account_id", length = 36, nullable = false)
    private String artistAccountId;

    @Column(name = "followed_at", nullable = false)
    private Instant followedAt;

    public static FeedSubscription create(String fanAccountId, String artistAccountId, Instant followedAt) {
        FeedSubscription fs = new FeedSubscription();
        fs.fanAccountId = fanAccountId;
        fs.artistAccountId = artistAccountId;
        fs.followedAt = followedAt;
        return fs;
    }

    public static class FeedSubscriptionId implements Serializable {
        private String fanAccountId;
        private String artistAccountId;

        public FeedSubscriptionId() {}

        public FeedSubscriptionId(String fanAccountId, String artistAccountId) {
            this.fanAccountId = fanAccountId;
            this.artistAccountId = artistAccountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FeedSubscriptionId that)) return false;
            return Objects.equals(fanAccountId, that.fanAccountId)
                    && Objects.equals(artistAccountId, that.artistAccountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fanAccountId, artistAccountId);
        }
    }
}
