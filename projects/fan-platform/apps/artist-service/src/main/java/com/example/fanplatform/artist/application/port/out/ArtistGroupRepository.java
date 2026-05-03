package com.example.fanplatform.artist.application.port.out;

import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import com.example.fanplatform.artist.domain.group.GroupMembership;

import java.util.List;
import java.util.Optional;

public interface ArtistGroupRepository {

    ArtistGroup insert(ArtistGroup group);

    ArtistGroup update(ArtistGroup group);

    Optional<ArtistGroup> findById(ArtistGroupId id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);

    /** Active memberships (left_at IS NULL AND role != FORMER_MEMBER). */
    List<GroupMembership> findActiveMembers(ArtistGroupId groupId, String tenantId);

    /** All memberships including former. Used by the GET endpoint. */
    List<GroupMembership> findAllMembers(ArtistGroupId groupId, String tenantId);

    boolean existsActiveMembership(ArtistGroupId groupId, ArtistId artistId, String tenantId);

    GroupMembership insertMembership(GroupMembership membership);

    /** Marks the active membership as left (sets left_at + role=FORMER_MEMBER). */
    void markMembershipLeft(ArtistGroupId groupId, ArtistId artistId, String tenantId);
}
