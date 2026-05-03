package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.application.exception.GroupNameConflictException;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import com.example.fanplatform.artist.domain.group.GroupMembership;
import com.example.fanplatform.artist.domain.group.GroupRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class ArtistGroupRepositoryAdapter implements ArtistGroupRepository {

    private static final String GROUP_NAME_CONSTRAINT = "uq_artist_groups_tenant_name";

    private final ArtistGroupJpaRepository groupJpa;
    private final GroupMembershipJpaRepository memberJpa;

    ArtistGroupRepositoryAdapter(ArtistGroupJpaRepository groupJpa,
                                 GroupMembershipJpaRepository memberJpa) {
        this.groupJpa = groupJpa;
        this.memberJpa = memberJpa;
    }

    @Override
    public ArtistGroup insert(ArtistGroup g) {
        ArtistGroupJpaEntity entity = new ArtistGroupJpaEntity(
                g.getId().value(), g.getTenantId(), g.getName(), g.getDebutDate(),
                g.getAgency(), g.getProfileImageRef(), g.getStatus(),
                g.getCreatedAt(), g.getUpdatedAt(), g.getArchivedAt(),
                null /* version null → insert */);
        try {
            ArtistGroupJpaEntity saved = groupJpa.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (mentionsConstraint(e, GROUP_NAME_CONSTRAINT)) {
                throw new GroupNameConflictException(g.getName());
            }
            throw e;
        }
    }

    @Override
    public ArtistGroup update(ArtistGroup g) {
        ArtistGroupJpaEntity managed = groupJpa.findById(g.getId().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Group disappeared between load and save: " + g.getId().value()));
        managed.applyMutable(g.getName(), g.getDebutDate(), g.getAgency(),
                g.getProfileImageRef(), g.getStatus(), g.getUpdatedAt(), g.getArchivedAt());
        return toDomain(groupJpa.saveAndFlush(managed));
    }

    @Override
    public Optional<ArtistGroup> findById(ArtistGroupId id, String tenantId) {
        return groupJpa.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndName(String tenantId, String name) {
        return groupJpa.existsByTenantIdAndName(tenantId, name);
    }

    @Override
    public List<GroupMembership> findActiveMembers(ArtistGroupId groupId, String tenantId) {
        return memberJpa.findActiveByGroupId(groupId.value(), tenantId).stream()
                .map(this::toMembershipDomain)
                .toList();
    }

    @Override
    public List<GroupMembership> findAllMembers(ArtistGroupId groupId, String tenantId) {
        return memberJpa.findAllByGroupId(groupId.value(), tenantId).stream()
                .map(this::toMembershipDomain)
                .toList();
    }

    @Override
    public boolean existsActiveMembership(ArtistGroupId groupId, ArtistId artistId, String tenantId) {
        return memberJpa.findActive(groupId.value(), artistId.value(), tenantId).isPresent();
    }

    @Override
    public GroupMembership insertMembership(GroupMembership m) {
        GroupMembershipJpaEntity entity = new GroupMembershipJpaEntity(
                m.groupId().value(), m.artistId().value(), m.joinedAt(),
                m.tenantId(), m.role(), m.leftAt());
        return toMembershipDomain(memberJpa.saveAndFlush(entity));
    }

    @Override
    public void markMembershipLeft(ArtistGroupId groupId, ArtistId artistId, String tenantId) {
        GroupMembershipJpaEntity active = memberJpa
                .findActive(groupId.value(), artistId.value(), tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "no active membership for (group=" + groupId.value()
                                + ", artist=" + artistId.value() + ")"));
        active.markLeft(GroupRole.FORMER_MEMBER, Instant.now());
        memberJpa.saveAndFlush(active);
    }

    private ArtistGroup toDomain(ArtistGroupJpaEntity e) {
        return ArtistGroup.reconstitute(
                ArtistGroupId.of(e.getId()),
                e.getTenantId(),
                e.getName(), e.getDebutDate(), e.getAgency(), e.getProfileImageRef(),
                e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getArchivedAt(),
                e.getVersion() == null ? 0L : e.getVersion()
        );
    }

    private GroupMembership toMembershipDomain(GroupMembershipJpaEntity e) {
        return new GroupMembership(
                ArtistGroupId.of(e.getGroupId()),
                ArtistId.of(e.getArtistId()),
                e.getTenantId(),
                e.getRole(),
                e.getJoinedAt(),
                e.getLeftAt()
        );
    }

    private static boolean mentionsConstraint(Throwable t, String constraint) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains(constraint)) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
