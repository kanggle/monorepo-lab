package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.domain.group.GroupRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for {@code group_memberships}. Composite PK
 * {@code (group_id, artist_id, joined_at)} matches the Flyway schema and lets
 * the same artist re-join the same group with a fresh row.
 */
@Entity
@Table(name = "group_memberships")
class GroupMembershipJpaEntity {

    @EmbeddedId
    private GroupMembershipKey key;

    @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private GroupRole role;

    @Column(name = "left_at")
    private Instant leftAt;

    protected GroupMembershipJpaEntity() {}

    GroupMembershipJpaEntity(String groupId, String artistId, Instant joinedAt,
                             String tenantId, GroupRole role, Instant leftAt) {
        this.key = new GroupMembershipKey(groupId, artistId, joinedAt);
        this.tenantId = tenantId;
        this.role = role;
        this.leftAt = leftAt;
    }

    GroupMembershipKey getKey() { return key; }
    String getGroupId() { return key.groupId; }
    String getArtistId() { return key.artistId; }
    Instant getJoinedAt() { return key.joinedAt; }
    String getTenantId() { return tenantId; }
    GroupRole getRole() { return role; }
    Instant getLeftAt() { return leftAt; }

    void markLeft(GroupRole formerRole, Instant leftAt) {
        this.role = formerRole;
        this.leftAt = leftAt;
    }

    @Embeddable
    static class GroupMembershipKey implements Serializable {

        @Column(name = "group_id", length = 36, nullable = false, updatable = false)
        private String groupId;

        @Column(name = "artist_id", length = 36, nullable = false, updatable = false)
        private String artistId;

        @Column(name = "joined_at", nullable = false, updatable = false)
        private Instant joinedAt;

        protected GroupMembershipKey() {}

        GroupMembershipKey(String groupId, String artistId, Instant joinedAt) {
            this.groupId = groupId;
            this.artistId = artistId;
            this.joinedAt = joinedAt;
        }

        String getGroupId() { return groupId; }
        String getArtistId() { return artistId; }
        Instant getJoinedAt() { return joinedAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupMembershipKey k)) return false;
            return Objects.equals(groupId, k.groupId)
                    && Objects.equals(artistId, k.artistId)
                    && Objects.equals(joinedAt, k.joinedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artistId, joinedAt);
        }
    }
}
