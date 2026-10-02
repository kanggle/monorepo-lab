package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupStatus;
import com.example.fanplatform.artist.domain.group.GroupMembership;
import com.example.fanplatform.artist.domain.group.GroupRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ArtistGroupView(
        String id,
        String tenantId,
        String name,
        LocalDate debutDate,
        // Display name — from the agency entity when agencyId is set (TASK-MONO-748).
        String agency,
        String agencyId,
        String profileImageRef,
        ArtistGroupStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<MemberView> members
) {

    public static ArtistGroupView from(ArtistGroup g, List<GroupMembership> memberships,
                                       Map<String, String> agencyNames) {
        String agencyId = g.getAgencyId() == null ? null : g.getAgencyId().value();
        String display = agencyId == null
                ? g.getAgency()
                : agencyNames.getOrDefault(agencyId, g.getAgency());
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
                display,
                agencyId,
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
