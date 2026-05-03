package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.AlreadyMemberException;
import com.example.fanplatform.artist.application.exception.ArtistArchivedException;
import com.example.fanplatform.artist.application.exception.ArtistGroupNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase.CreateArtistGroupCommand;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistGroupRepository;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.ArtistGroupId;
import com.example.fanplatform.artist.domain.group.GroupMembership;
import com.example.fanplatform.artist.domain.group.GroupRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ArtistGroupServiceTest {

    @Mock ArtistGroupRepository groupRepo;
    @Mock ArtistRepository artistRepo;
    @Mock ArtistEventPublisher eventPublisher;
    @InjectMocks ArtistGroupService service;

    private ActorContext admin;
    private ActorContext fan;

    @BeforeEach
    void setUp() {
        admin = new ActorContext("admin-1", "fan-platform", Set.of("ADMIN"));
        fan = new ActorContext("fan-1", "fan-platform", Set.of("FAN"));
    }

    @Test
    @DisplayName("create: non-admin rejected")
    void create_nonAdminRejected() {
        CreateArtistGroupCommand cmd = new CreateArtistGroupCommand(
                fan, "Group X", null, null, null);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(AdminRoleRequiredException.class);
    }

    @Test
    @DisplayName("create: emits artist.group_created")
    void create_happy() {
        when(groupRepo.existsByTenantIdAndName("fan-platform", "Group X")).thenReturn(false);
        when(groupRepo.insert(any(ArtistGroup.class))).thenAnswer(i -> i.getArgument(0));

        var cmd = new CreateArtistGroupCommand(admin, "Group X", LocalDate.of(2020, 1, 1), null, null);
        service.create(cmd);

        verify(eventPublisher, times(1)).publishArtistGroupCreated(any(ArtistGroup.class));
    }

    @Test
    @DisplayName("addMember: non-admin rejected")
    void addMember_nonAdmin() {
        assertThatThrownBy(() -> service.addMember(fan, "g-1", "a-1", GroupRole.MEMBER))
                .isInstanceOf(AdminRoleRequiredException.class);
    }

    @Test
    @DisplayName("addMember: missing group -> 404")
    void addMember_missingGroup() {
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER))
                .isInstanceOf(ArtistGroupNotFoundException.class);
    }

    @Test
    @DisplayName("addMember: missing artist -> 404")
    void addMember_missingArtist() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.ARCHIVED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.PUBLISHED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.DRAFT)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("addMember: ARCHIVED artist -> 422 ARTIST_ARCHIVED")
    void addMember_archivedArtist() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.ARCHIVED)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER))
                .isInstanceOf(ArtistArchivedException.class);
        verify(groupRepo, never()).insertMembership(any());
    }

    @Test
    @DisplayName("addMember: DRAFT artist -> happy (admin may pre-stage roster)")
    void addMember_draftArtist() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.ARCHIVED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.PUBLISHED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.DRAFT)))
                .thenReturn(true);
        when(groupRepo.existsActiveMembership(any(ArtistGroupId.class), any(ArtistId.class), eq("fan-platform")))
                .thenReturn(false);
        when(groupRepo.insertMembership(any(GroupMembership.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(groupRepo.findAllMembers(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(List.of());

        service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER);

        verify(groupRepo, times(1)).insertMembership(any(GroupMembership.class));
        verify(eventPublisher, times(1)).publishArtistGroupMemberChanged(
                any(ArtistGroup.class), any(ArtistId.class), eq(GroupRole.MEMBER),
                eq(ArtistEventPublisher.MemberChangeAction.ADDED), any());
    }

    @Test
    @DisplayName("addMember: already active -> 422 ALREADY_MEMBER")
    void addMember_alreadyMember() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.ARCHIVED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.PUBLISHED)))
                .thenReturn(true);
        when(groupRepo.existsActiveMembership(any(ArtistGroupId.class), any(ArtistId.class), eq("fan-platform")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER))
                .isInstanceOf(AlreadyMemberException.class);
        verify(groupRepo, never()).insertMembership(any());
    }

    @Test
    @DisplayName("addMember: happy path -> insertMembership + event")
    void addMember_happy() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.ARCHIVED)))
                .thenReturn(false);
        when(artistRepo.existsInStatus(any(ArtistId.class), eq("fan-platform"), eq(ArtistStatus.PUBLISHED)))
                .thenReturn(true);
        when(groupRepo.existsActiveMembership(any(ArtistGroupId.class), any(ArtistId.class), eq("fan-platform")))
                .thenReturn(false);
        when(groupRepo.insertMembership(any(GroupMembership.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(groupRepo.findAllMembers(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(List.of());

        service.addMember(admin, "g-1", "a-1", GroupRole.MEMBER);

        verify(groupRepo, times(1)).insertMembership(any(GroupMembership.class));
        verify(eventPublisher, times(1)).publishArtistGroupMemberChanged(
                any(ArtistGroup.class), any(ArtistId.class), eq(GroupRole.MEMBER),
                eq(ArtistEventPublisher.MemberChangeAction.ADDED), any());
    }

    @Test
    @DisplayName("removeMember: missing active membership -> 404")
    void removeMember_missing() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(groupRepo.existsActiveMembership(any(ArtistGroupId.class), any(ArtistId.class), eq("fan-platform")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeMember(admin, "g-1", "a-1"))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("removeMember: happy path -> markLeft + event")
    void removeMember_happy() {
        ArtistGroup g = ArtistGroup.create(ArtistGroupId.of("g-1"), "fan-platform",
                "Group X", null, null, null);
        when(groupRepo.findById(any(ArtistGroupId.class), eq("fan-platform")))
                .thenReturn(Optional.of(g));
        when(groupRepo.existsActiveMembership(any(ArtistGroupId.class), any(ArtistId.class), eq("fan-platform")))
                .thenReturn(true);

        service.removeMember(admin, "g-1", "a-1");

        verify(groupRepo, times(1)).markMembershipLeft(any(), any(), eq("fan-platform"));
        verify(eventPublisher, times(1)).publishArtistGroupMemberChanged(
                any(), any(), eq(GroupRole.FORMER_MEMBER),
                eq(ArtistEventPublisher.MemberChangeAction.REMOVED), any());
    }
}
