package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupStatus;
import com.example.fanplatform.artist.domain.group.GroupMembership;
import com.example.fanplatform.artist.domain.group.GroupRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ArtistGroupView(
        String id,
        String tenantId,
        String name,
        LocalDate debutDate,
        String agency,
        String profileImageRef,
        ArtistGroupStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<MemberView> members
) {

    public static ArtistGroupView from(ArtistGroup g, List<GroupMembership> memberships) {
        List<MemberView> mems = memberships.stream()
                .map(m -> new MemberView(
                        m.artistId().value(),
                        m.role(),
                        m.joinedAt(),
                        m.leftAt()))
                .toList();
        return new ArtistGroupView(
                g.getId().value(),
                g.getTenantId(),
                g.getName(),
                g.getDebutDate(),
                g.getAgency(),
                g.getProfileImageRef(),
                g.getStatus(),
                g.getCreatedAt(),
                g.getUpdatedAt(),
                mems
        );
    }

    public record MemberView(
            String artistId,
            GroupRole role,
            Instant joinedAt,
            Instant leftAt
    ) {}
}
