package com.example.fanplatform.community.domain.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Follow aggregate. Asymmetric relationship — fan account follows artist
 * account. Composite PK = (fan_account_id, artist_account_id). tenant_id is
 * carried for query-scoping convenience and partition-key alignment with the
 * surrounding aggregates.
 */
@Entity
@Table(name = "follows")
@IdClass(Follow.FollowId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id
    @Column(name = "fan_account_id", length = 36, nullable = false)
    private String fanAccountId;

    @Id
    @Column(name = "artist_account_id", length = 36, nullable = false)
    private String artistAccountId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Follow create(String fanAccountId, String artistAccountId, String tenantId) {
        Follow f = new Follow();
        f.fanAccountId = fanAccountId;
        f.artistAccountId = artistAccountId;
        f.tenantId = tenantId;
        f.createdAt = Instant.now();
        return f;
    }

    public static class FollowId implements Serializable {
        private String fanAccountId;
        private String artistAccountId;

        public FollowId() {}

        public FollowId(String fanAccountId, String artistAccountId) {
            this.fanAccountId = fanAccountId;
            this.artistAccountId = artistAccountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FollowId that)) return false;
            return Objects.equals(fanAccountId, that.fanAccountId)
                    && Objects.equals(artistAccountId, that.artistAccountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fanAccountId, artistAccountId);
        }
    }
}
