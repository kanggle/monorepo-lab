package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.StageNameConflictException;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase.RegisterArtistCommand;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.example.fanplatform.artist.application.port.out.ArtistEventPublisher;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ArtistManagementServiceTest {

    @Mock ArtistRepository artistRepository;
    @Mock ArtistEventPublisher eventPublisher;
    @Mock ArtistDirectoryCache directoryCache;

    @InjectMocks ArtistManagementService service;

    private ActorContext admin;
    private ActorContext fan;

    @BeforeEach
    void setUp() {
        admin = new ActorContext("admin-1", "fan-platform", Set.of("ADMIN"));
        fan = new ActorContext("fan-1", "fan-platform", Set.of("FAN"));
    }

    private static Artist sampleArtist() {
        ArtistProfile profile = new ArtistProfile("STAGE", null, LocalDate.of(2020, 1, 1),
                null, null, null);
        return Artist.register(ArtistId.of("a-1"), "fan-platform", ArtistType.SOLO, profile);
    }

    @Test
    @DisplayName("register: non-admin rejected")
    void register_nonAdminRejected() {
        RegisterArtistCommand cmd = new RegisterArtistCommand(
                fan, ArtistType.SOLO, "STAGE", null, null, null, null, null);

        assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(AdminRoleRequiredException.class);

        verify(artistRepository, never()).insert(any());
    }

    @Test
    @DisplayName("register: stage_name conflict surfaces 409")
    void register_stageNameConflict() {
        when(artistRepository.existsByTenantIdAndStageName("fan-platform", "STAGE")).thenReturn(true);
        RegisterArtistCommand cmd = new RegisterArtistCommand(
                admin, ArtistType.SOLO, "STAGE", null, null, null, null, null);

        assertThatThrownBy(() -> service.register(cmd))
                .isInstanceOf(StageNameConflictException.class);

        verify(artistRepository, never()).insert(any());
    }

    @Test
    @DisplayName("register: happy path inserts and emits artist.registered")
    void register_happyPath() {
        when(artistRepository.existsByTenantIdAndStageName("fan-platform", "STAGE")).thenReturn(false);
        when(artistRepository.insert(any(Artist.class))).thenAnswer(i -> i.getArgument(0));

        RegisterArtistCommand cmd = new RegisterArtistCommand(
                admin, ArtistType.SOLO, "STAGE", null, null, null, null, null);
        ArtistView view = service.register(cmd);

        assertThat(view.status()).isEqualTo(ArtistStatus.DRAFT);
        assertThat(view.tenantId()).isEqualTo("fan-platform");
        verify(eventPublisher, times(1)).publishArtistRegistered(any(Artist.class), eq("admin-1"));
        verify(directoryCache, never()).invalidateAll(anyString());
    }

    @Test
    @DisplayName("publish: emits artist.published and invalidates cache")
    void publish_invalidatesCache() {
        Artist a = sampleArtist();
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));
        when(artistRepository.update(any(Artist.class))).thenAnswer(i -> i.getArgument(0));

        ArtistView view = service.publish(admin, "a-1");

        assertThat(view.status()).isEqualTo(ArtistStatus.PUBLISHED);
        verify(eventPublisher, times(1)).publishArtistPublished(any(Artist.class));
        verify(directoryCache, times(1)).invalidateAll("fan-platform");
    }

    @Test
    @DisplayName("getById: DRAFT artist invisible to non-admin -> 404")
    void getById_draftHiddenFromFan() {
        Artist a = sampleArtist(); // DRAFT
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.getById(fan, "a-1"))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("getById: DRAFT visible to admin")
    void getById_draftVisibleToAdmin() {
        Artist a = sampleArtist(); // DRAFT
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));

        ArtistView view = service.getById(admin, "a-1");
        assertThat(view.status()).isEqualTo(ArtistStatus.DRAFT);
    }

    @Test
    @DisplayName("getById: PUBLISHED visible to fan")
    void getById_publishedVisibleToFan() {
        Artist a = sampleArtist();
        a.publish();
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));

        ArtistView view = service.getById(fan, "a-1");
        assertThat(view.status()).isEqualTo(ArtistStatus.PUBLISHED);
    }

    @Test
    @DisplayName("getById: cross-tenant -> 404 (no leak)")
    void getById_crossTenant() {
        when(artistRepository.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(admin, "a-1"))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("update: emits artist.updated with changedFields and invalidates cache when published")
    void update_publishedEmitsAndInvalidates() {
        Artist a = sampleArtist();
        a.publish();
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));
        when(artistRepository.update(any(Artist.class))).thenAnswer(i -> i.getArgument(0));

        var cmd = new com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase.UpdateArtistCommand(
                admin, "a-1", null, "newReal", null, null, null, null);
        service.update(cmd);

        verify(eventPublisher, times(1)).publishArtistUpdated(
                any(), eq("fan-platform"), anyList(), eq("admin-1"), any(Instant.class));
        verify(directoryCache, times(1)).invalidateAll("fan-platform");
    }

    @Test
    @DisplayName("update: no changes -> no event published")
    void update_noChangesNoEvent() {
        Artist a = sampleArtist();
        when(artistRepository.findById(eq(a.getId()), eq("fan-platform"))).thenReturn(Optional.of(a));
        when(artistRepository.update(any(Artist.class))).thenAnswer(i -> i.getArgument(0));

        var cmd = new com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase.UpdateArtistCommand(
                admin, "a-1", null, null, null, null, null, null);
        service.update(cmd);

        verify(eventPublisher, never()).publishArtistUpdated(any(), anyString(), anyList(), anyString(), any());
    }
}
