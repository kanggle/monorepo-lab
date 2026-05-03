package com.example.fanplatform.artist.application.service;

import com.example.common.id.UuidV7;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.AlreadyMemberException;
import com.example.fanplatform.artist.application.exception.ArtistGroupNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.GroupNameConflictException;
import com.example.fanplatform.artist.application.port.in.AddGroupMemberUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.GetArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.RemoveGroupMemberUseCase;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher.MemberChangeAction;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import com.example.fanplatform.artist.domain.group.GroupMembership;
import com.example.fanplatform.artist.domain.group.GroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArtistGroupService implements
        CreateArtistGroupUseCase,
        AddGroupMemberUseCase,
        RemoveGroupMemberUseCase,
        GetArtistGroupUseCase {

    private final ArtistGroupRepository groupRepository;
    private final ArtistRepository artistRepository;
    private final ArtistEventPublisher eventPublisher;

    @Override
    @Transactional
    public ArtistGroupView create(CreateArtistGroupCommand cmd) {
        requireAdmin(cmd.actor());
        String tenantId = cmd.actor().tenantId();
        if (groupRepository.existsByTenantIdAndName(tenantId, cmd.name())) {
            throw new GroupNameConflictException(cmd.name());
        }
        ArtistGroup group = ArtistGroup.create(
                ArtistGroupId.of(UuidV7.randomString()),
                tenantId,
                cmd.name(), cmd.debutDate(), cmd.agency(), cmd.profileImageRef());
        ArtistGroup saved = groupRepository.insert(group);
        eventPublisher.publishArtistGroupCreated(saved);
        return ArtistGroupView.from(saved, List.of());
    }

    @Override
    @Transactional
    public ArtistGroupView addMember(ActorContext actor, String groupId, String artistId,
                                     GroupRole role) {
        requireAdmin(actor);
        String tenantId = actor.tenantId();
        ArtistGroup group = loadGroupOrThrow(groupId, tenantId);
        ArtistId aid = parseArtistId(artistId);
        if (!artistRepository.existsInStatus(aid, tenantId, ArtistStatus.PUBLISHED)
                && !artistRepository.existsInStatus(aid, tenantId, ArtistStatus.DRAFT)) {
            throw new ArtistNotFoundException(artistId);
        }
        if (groupRepository.existsActiveMembership(group.getId(), aid, tenantId)) {
            throw new AlreadyMemberException(groupId, artistId);
        }
        GroupMembership membership = group.prepareMembership(aid, role);
        groupRepository.insertMembership(membership);
        eventPublisher.publishArtistGroupMemberChanged(
                group, aid, role, MemberChangeAction.ADDED, Instant.now());
        return ArtistGroupView.from(group, groupRepository.findAllMembers(group.getId(), tenantId));
    }

    @Override
    @Transactional
    public void removeMember(ActorContext actor, String groupId, String artistId) {
        requireAdmin(actor);
        String tenantId = actor.tenantId();
        ArtistGroup group = loadGroupOrThrow(groupId, tenantId);
        ArtistId aid = parseArtistId(artistId);
        if (!groupRepository.existsActiveMembership(group.getId(), aid, tenantId)) {
            throw new ArtistNotFoundException(artistId);
        }
        groupRepository.markMembershipLeft(group.getId(), aid, tenantId);
        eventPublisher.publishArtistGroupMemberChanged(
                group, aid, GroupRole.FORMER_MEMBER, MemberChangeAction.REMOVED, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public ArtistGroupView getById(ActorContext actor, String groupId) {
        String tenantId = actor.tenantId();
        ArtistGroup group = loadGroupOrThrow(groupId, tenantId);
        List<GroupMembership> members = groupRepository.findAllMembers(group.getId(), tenantId);
        return ArtistGroupView.from(group, members);
    }

    private ArtistGroup loadGroupOrThrow(String rawId, String tenantId) {
        ArtistGroupId id;
        try {
            id = ArtistGroupId.of(rawId);
        } catch (IllegalArgumentException e) {
            throw new ArtistGroupNotFoundException(rawId);
        }
        Optional<ArtistGroup> found = groupRepository.findById(id, tenantId);
        return found.orElseThrow(() -> new ArtistGroupNotFoundException(rawId));
    }

    private static ArtistId parseArtistId(String rawId) {
        try {
            return ArtistId.of(rawId);
        } catch (IllegalArgumentException e) {
            throw new ArtistNotFoundException(rawId);
        }
    }

    private static void requireAdmin(ActorContext actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AdminRoleRequiredException();
        }
    }
}
