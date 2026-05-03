package com.example.fanplatform.artist.application.service;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
import com.example.fanplatform.artist.application.exception.FandomAlreadyExistsException;
import com.example.fanplatform.artist.application.exception.FandomNotFoundException;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase.CreateFandomCommand;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase.UpdateFandomCommand;
import com.example.fanplatform.artist.application.port.out.ArtistRepository;
import com.example.fanplatform.artist.application.port.out.FandomRepository;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistProfile;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.fandom.Fandom;
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
class FandomServiceTest {

    @Mock FandomRepository fandomRepo;
    @Mock ArtistRepository artistRepo;
    @InjectMocks FandomService service;

    private ActorContext admin;
    private ActorContext fan;

    @BeforeEach
    void setUp() {
        admin = new ActorContext("admin-1", "fan-platform", Set.of("ADMIN"));
        fan = new ActorContext("fan-1", "fan-platform", Set.of("FAN"));
    }

    private static Artist publishedArtist() {
        Artist a = Artist.register(ArtistId.of("a-1"), "fan-platform", ArtistType.SOLO,
                new ArtistProfile("STAGE", null, null, null, null, null));
        a.publish();
        return a;
    }

    private static Artist draftArtist() {
        return Artist.register(ArtistId.of("a-1"), "fan-platform", ArtistType.SOLO,
                new ArtistProfile("STAGE", null, null, null, null, null));
    }

    // -- create ----------------------------------------------------------

    @Test
    @DisplayName("create: non-admin rejected")
    void create_nonAdmin() {
        var cmd = new CreateFandomCommand(fan, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(AdminRoleRequiredException.class);
    }

    @Test
    @DisplayName("create: missing artist -> 404")
    void create_missingArtist() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform"))).thenReturn(Optional.empty());
        var cmd = new CreateFandomCommand(admin, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("create: DRAFT artist -> 422 ARTIST_NOT_PUBLISHED")
    void create_draftRejected() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(draftArtist()));
        var cmd = new CreateFandomCommand(admin, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(ArtistNotPublishedException.class);
    }

    @Test
    @DisplayName("create: happy path inserts new fandom")
    void create_happy() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(publishedArtist()));
        when(fandomRepo.findByArtistId(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.empty());
        when(fandomRepo.insert(any(Fandom.class))).thenAnswer(i -> i.getArgument(0));

        var cmd = new CreateFandomCommand(admin, "a-1", "Hearts", "#FFAA00",
                LocalDate.of(2020, 1, 1), "Forever");
        var view = service.create(cmd);

        assertThat(view.fandomName()).isEqualTo("Hearts");
        verify(fandomRepo, times(1)).insert(any(Fandom.class));
        verify(fandomRepo, never()).update(any(Fandom.class));
    }

    @Test
    @DisplayName("create: existing fandom -> 422 FANDOM_ALREADY_EXISTS")
    void create_secondFandomRejected() {
        Fandom existing = Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Old", null, null, null);
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(publishedArtist()));
        when(fandomRepo.findByArtistId(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(existing));

        var cmd = new CreateFandomCommand(admin, "a-1", "New", null, null, null);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(FandomAlreadyExistsException.class);
        verify(fandomRepo, never()).insert(any(Fandom.class));
    }

    // -- update ----------------------------------------------------------

    @Test
    @DisplayName("update: non-admin rejected")
    void update_nonAdmin() {
        var cmd = new UpdateFandomCommand(fan, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(AdminRoleRequiredException.class);
    }

    @Test
    @DisplayName("update: missing artist -> 404 ARTIST_NOT_FOUND")
    void update_missingArtist() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform"))).thenReturn(Optional.empty());
        var cmd = new UpdateFandomCommand(admin, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(ArtistNotFoundException.class);
    }

    @Test
    @DisplayName("update: DRAFT artist -> 422 ARTIST_NOT_PUBLISHED")
    void update_draftRejected() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(draftArtist()));
        var cmd = new UpdateFandomCommand(admin, "a-1", "Hearts", null, null, null);
        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(ArtistNotPublishedException.class);
    }

    @Test
    @DisplayName("update: missing fandom -> 404 FANDOM_NOT_FOUND")
    void update_missingFandom() {
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(publishedArtist()));
        when(fandomRepo.findByArtistId(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.empty());

        var cmd = new UpdateFandomCommand(admin, "a-1", "New", null, null, null);
        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(FandomNotFoundException.class);
        verify(fandomRepo, never()).update(any(Fandom.class));
    }

    @Test
    @DisplayName("update: happy path updates existing fandom")
    void update_happy() {
        Fandom existing = Fandom.create(ArtistId.of("a-1"), "fan-platform",
                "Old", null, null, null);
        when(artistRepo.findById(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(publishedArtist()));
        when(fandomRepo.findByArtistId(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.of(existing));
        when(fandomRepo.update(any(Fandom.class))).thenAnswer(i -> i.getArgument(0));

        var cmd = new UpdateFandomCommand(admin, "a-1", "New Name", null, null, null);
        var view = service.update(cmd);

        assertThat(view.fandomName()).isEqualTo("New Name");
        verify(fandomRepo, times(1)).update(any(Fandom.class));
        verify(fandomRepo, never()).insert(any(Fandom.class));
    }

    // -- get -------------------------------------------------------------

    @Test
    @DisplayName("getByArtistId: missing -> 404")
    void get_missing() {
        when(fandomRepo.findByArtistId(any(ArtistId.class), eq("fan-platform")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByArtistId(fan, "a-1"))
                .isInstanceOf(FandomNotFoundException.class);
    }
}
