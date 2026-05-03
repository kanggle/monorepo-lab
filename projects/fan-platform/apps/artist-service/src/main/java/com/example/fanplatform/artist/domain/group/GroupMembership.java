package com.example.fanplatform.artist.domain.group;

import com.example.fanplatform.artist.domain.artist.ArtistId;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing one membership row of an artist within a group.
 *
 * <p>Immutable: lifecycle changes (e.g. leaving the group) produce new
 * instances. Storage maps to {@code group_memberships}; composite PK is
 * {@code (group_id, artist_id, joined_at)} so the same artist can re-join the
 * same group at a later date.
 */
public record GroupMembership(
        ArtistGroupId groupId,
        ArtistId artistId,
        String tenantId,
        GroupRole role,
        Instant joinedAt,
        Instant leftAt
) {

    public GroupMembership {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artistId, "artistId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
        // leftAt is allowed to be null (active membership)
    }

    public static GroupMembership join(ArtistGroupId groupId, ArtistId artistId,
                                       String tenantId, GroupRole role) {
        return new GroupMembership(groupId, artistId, tenantId, role, Instant.now(), null);
    }

    public boolean isActive() {
        return leftAt == null && role != GroupRole.FORMER_MEMBER;
    }

    public GroupMembership leave() {
        return new GroupMembership(groupId, artistId, tenantId, GroupRole.FORMER_MEMBER,
                joinedAt, Instant.now());
    }
}
